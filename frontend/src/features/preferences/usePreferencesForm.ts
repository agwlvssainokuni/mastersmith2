/*
 * Copyright 2026 agwlvssainokuni
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
//
// プリファレンスの画面の状態を持つフック（frontend-components.md の 3.1・3.3、functional-spec.md の W2〜W8・W11〜W13、
// D1〜D13、performance-design.md の 2節・3節）。
// - 部品が付いたときの副作用で GET を送り、この回の副作用が有効な（部品が付いていて、次の読み込みが始まっていない）ときだけ
//   答えで状態を移す。副作用の中で同期に状態を変えない（StrictMode の二重の実行でも使う答えは1つ）。
// - 読み込みの成功で、読んだ値が当たっている値と違えば applyUserPreferences でそろえる（D2。知らせない）。
// - テーマ・文字の大きさを選ぶとフォームの2つで setPreview（D4）。言語・氏名は値だけを変える。送信中の変更は受け付けない。
// - 保存は送信中の印（useRef）で二重の送信を防ぐ（NFR6.5）。200 で applyUserPreferences と今の設定の置き換え、次の描画で
//   その描画の文言の Toast（D12）。400 VALIDATION_FAILED は項目ごとの誤り、ほかは画面の知らせ（値と見せ方は残す）。
// - 部品が外れるとき clearPreview（D6）。外れた後の保存の 200 は applyUserPreferences だけを行い、失敗は捨てる（W6 の3）。
// - 誤りと知らせは文言の鍵で持つ。氏名・応答の値を console・ブラウザの保存・URL に出さない（NFR2.1）。
import { useToast } from 'make-you-chic-ui'
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { useDisplaySettings } from '../../app/display-settings/DisplaySettingsProvider'
import type {
  DisplayLanguage,
  FontSize,
  ThemeChoice,
} from '../../app/display-settings/displaySettingsTypes'
import { useMessages } from '../../app/i18n/I18nProvider'
import { fieldMessageKey, FORM_INVALID_KEY, SAVE_FAILED_KEY, toFieldReason } from './errorMessages'
import { badRequestCode, isApiResponseError } from './failure'
import { readFieldErrors } from './fieldErrors'
import { checkPreferencesForm, PREFERENCES_FIELDS } from './formChecks'
import type { FocusRequest, PreferencesFocusTarget } from './PreferencesForm'
import {
  getPreferences,
  savePreferences,
  type Preferences,
  type PreferencesField,
} from './preferencesApi'

/** 画面の状態（functional-spec.md の 4.1） */
export type PreferencesPhase = 'loading' | 'loadFailed' | 'ready' | 'saving'

/** 項目の誤りの文言の鍵 */
export type PreferencesFieldErrors = Partial<Record<PreferencesField, string>>

/** フォーカスを移す先（フォームの中の要素か「もう一度読み込む」） */
type FocusTarget = PreferencesFocusTarget | 'retry'

interface PreferencesState {
  phase: PreferencesPhase
  /** 今の設定（最後の読み込みか保存の成功の値） */
  current: Preferences | undefined
  /** フォームの値 */
  form: Preferences | undefined
  fieldErrors: PreferencesFieldErrors
  /** 画面の知らせの文言の鍵 */
  alert: string | undefined
  /** 読み込みの回（「もう一度読み込む」で増やす） */
  loadAttempt: number
  /** 保存の成功の回（増えた描画の後に Toast を出す） */
  savedCount: number
  /** フォーカスの要求（同じ先を続けて求められるよう、回で区別する） */
  focus: { target: FocusTarget; seq: number } | undefined
}

/** フックが返す値と操作 */
export interface PreferencesController {
  phase: PreferencesPhase
  form: Preferences | undefined
  fieldErrors: PreferencesFieldErrors
  alert: string | undefined
  dirty: boolean
  /** フォームの中の要素へのフォーカスの要求 */
  focusRequest: FocusRequest<PreferencesFocusTarget> | undefined
  /** 読み直しの失敗の後に「もう一度読み込む」へフォーカスを移す回 */
  retryFocusSeq: number | undefined
  setDisplayName: (value: string) => void
  setLanguage: (value: DisplayLanguage) => void
  setTheme: (value: ThemeChoice) => void
  setFontSize: (value: FontSize) => void
  reset: () => void
  save: () => void
  reload: () => void
  dismissAlert: () => void
}

/** 4つが同じか（文字列として比べる）。 */
function samePreferences(a: Preferences, b: Preferences): boolean {
  return (
    a.displayName === b.displayName &&
    a.language === b.language &&
    a.theme === b.theme &&
    a.fontSize === b.fontSize
  )
}

/** プリファレンスの画面の状態と操作 */
export function usePreferencesForm(): PreferencesController {
  const display = useDisplaySettings()
  const t = useMessages()
  const toast = useToast()

  const [state, setState] = useState<PreferencesState>({
    phase: 'loading',
    current: undefined,
    form: undefined,
    fieldErrors: {},
    alert: undefined,
    loadAttempt: 0,
    savedCount: 0,
    focus: undefined,
  })

  const mounted = useRef(true)
  /** 送信中か（描画の前の2回目の操作を止めるため、状態とは別に同期で持つ） */
  const busy = useRef(false)
  /** 最後に Toast を出した保存の回 */
  const shownSavedCount = useRef(0)
  /** 応答を受けた後（描画の外）で使う U4 の口（最新のログイン状態に結び付いたもの） */
  const displayRef = useRef(display)

  useEffect(() => {
    displayRef.current = display
  }, [display])

  // 部品が付いているかの印と、外れるときの見せ方の取りやめ（D6）。
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      displayRef.current.clearPreview()
    }
  }, [])

  // 読み込み（D1・W2・W3）。答えは、この回の副作用がまだ有効なときだけ使う。
  const { loadAttempt } = state
  useEffect(() => {
    let active = true
    getPreferences().then(
      (loaded) => {
        if (!active) {
          return
        }
        const applied = displayRef.current
        if (
          applied.displayName !== loaded.displayName ||
          applied.language !== loaded.language ||
          applied.theme !== loaded.theme ||
          applied.fontSize !== loaded.fontSize
        ) {
          // 内部DB の値が正。開いた時点の値に画面をそろえる（D2。利用者の操作ではないため知らせない）。
          applied.applyUserPreferences(loaded)
        }
        setState((current) => ({
          ...current,
          phase: 'ready',
          current: loaded,
          form: loaded,
          fieldErrors: {},
          alert: undefined,
          focus:
            loadAttempt > 0
              ? { target: 'displayName', seq: (current.focus?.seq ?? 0) + 1 }
              : current.focus,
        }))
      },
      () => {
        if (!active) {
          return
        }
        setState((current) => ({
          ...current,
          phase: 'loadFailed',
          focus:
            loadAttempt > 0
              ? { target: 'retry', seq: (current.focus?.seq ?? 0) + 1 }
              : current.focus,
        }))
      },
    )
    return () => {
      active = false
    }
  }, [loadAttempt])

  // 保存の成功の後、新しい言語で描いた後に、その描画の文言で Toast を出す（D12）。フォーカスは動かさない（D10）。
  const { savedCount } = state
  useEffect(() => {
    if (savedCount > shownSavedCount.current) {
      shownSavedCount.current = savedCount
      toast.show({ message: t('preferences.saved'), variant: 'success' })
    }
  }, [savedCount, t, toast])

  const nextFocus = (current: PreferencesState, target: FocusTarget) => ({
    target,
    seq: (current.focus?.seq ?? 0) + 1,
  })

  const editable = (): boolean => !busy.current && state.phase === 'ready'

  const updateForm = (change: Partial<Preferences>): Preferences | undefined => {
    if (!editable() || state.form === undefined) {
      return undefined
    }
    const next = { ...state.form, ...change }
    setState((current) =>
      current.form === undefined ? current : { ...current, form: { ...current.form, ...change } },
    )
    return next
  }

  const setTheme = (theme: ThemeChoice) => {
    const next = updateForm({ theme })
    if (next !== undefined) {
      display.setPreview(next.theme, next.fontSize)
    }
  }

  const setFontSize = (fontSize: FontSize) => {
    const next = updateForm({ fontSize })
    if (next !== undefined) {
      display.setPreview(next.theme, next.fontSize)
    }
  }

  const reset = () => {
    if (!editable() || state.current === undefined) {
      return
    }
    const { current: saved } = state
    setState((current) => ({
      ...current,
      form: saved,
      fieldErrors: {},
      alert: undefined,
      focus: nextFocus(current, 'save'),
    }))
    display.clearPreview()
  }

  const reload = () => {
    if (state.phase !== 'loadFailed') {
      return
    }
    setState((current) => ({
      ...current,
      phase: 'loading',
      alert: undefined,
      loadAttempt: current.loadAttempt + 1,
    }))
  }

  /** 保存の失敗を画面の状態にする。 */
  const failedState = (current: PreferencesState, error: unknown): PreferencesState => {
    if (badRequestCode(error) === 'VALIDATION_FAILED' && isApiResponseError(error)) {
      const errors = readFieldErrors(error.problem, PREFERENCES_FIELDS)
      if (errors.length > 0) {
        const fieldErrors: PreferencesFieldErrors = {}
        for (const { field, reason } of errors) {
          fieldErrors[field] = fieldMessageKey(field, toFieldReason(reason))
        }
        const first = PREFERENCES_FIELDS.find((field) => fieldErrors[field] !== undefined)
        return {
          ...current,
          phase: 'ready',
          fieldErrors,
          focus: first === undefined ? current.focus : nextFocus(current, first),
        }
      }
      return { ...current, phase: 'ready', alert: FORM_INVALID_KEY }
    }
    return { ...current, phase: 'ready', alert: SAVE_FAILED_KEY }
  }

  const save = () => {
    const { form } = state
    if (!editable() || form === undefined) {
      return
    }
    const check = checkPreferencesForm(form)
    if (check.firstInvalid !== undefined) {
      const fieldErrors: PreferencesFieldErrors = {}
      for (const field of PREFERENCES_FIELDS) {
        const reason = check.reasons[field]
        if (reason !== undefined) {
          fieldErrors[field] = fieldMessageKey(field, reason)
        }
      }
      const first = check.firstInvalid
      setState((current) => ({
        ...current,
        fieldErrors,
        alert: undefined,
        focus: nextFocus(current, first),
      }))
      return
    }
    busy.current = true
    setState((current) => ({ ...current, phase: 'saving', fieldErrors: {}, alert: undefined }))
    savePreferences(form).then(
      (saved) => {
        busy.current = false
        // 内部DB は保存された。画面が無くても画面の値を内部DB にそろえる（W6 の3）。
        displayRef.current.applyUserPreferences(saved)
        if (!mounted.current) {
          return
        }
        setState((current) => ({
          ...current,
          phase: 'ready',
          current: saved,
          form: saved,
          fieldErrors: {},
          alert: undefined,
          savedCount: current.savedCount + 1,
        }))
      },
      (error: unknown) => {
        busy.current = false
        if (!mounted.current) {
          return
        }
        setState((current) => failedState(current, error))
      },
    )
  }

  // フォームへのフォーカスの要求は、要求が変わったときだけ新しい値にする（描画ごとに作ると副作用が毎回動くため）。
  const { focus } = state
  const focusRequest = useMemo(
    () =>
      focus === undefined || focus.target === 'retry'
        ? undefined
        : { target: focus.target, seq: focus.seq },
    [focus],
  )

  const dismissAlert = useCallback(() => {
    setState((current) => ({ ...current, alert: undefined }))
  }, [])

  return {
    phase: state.phase,
    form: state.form,
    fieldErrors: state.fieldErrors,
    alert: state.alert,
    dirty:
      state.form !== undefined &&
      state.current !== undefined &&
      !samePreferences(state.form, state.current),
    focusRequest,
    retryFocusSeq: focus?.target === 'retry' ? focus.seq : undefined,
    setDisplayName: (displayName) => {
      updateForm({ displayName })
    },
    setLanguage: (language) => {
      updateForm({ language })
    },
    setTheme,
    setFontSize,
    reset,
    save,
    reload,
    dismissAlert,
  }
}
