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
// 登録の完了の画面の状態を持つフック（frontend-components.md の 4節、functional-spec.md の W2〜W12・D1〜D12）。
// - トークンは最初の描画の状態の初期値を作る関数の中でフラグメントから取り出し（StrictMode の二重の描画でも、どちらも
//   消す前に読む）、描画の確定の後に同じパス（問い合わせを含む）への置き換えの移動でフラグメントを消す（D1・D2）。
//   トークンは部品の状態（メモリ）だけに持ち、ブラウザの保存・URL・history.state・console に出さない。
// - ログインしたまま開いたら確かめを送らずに案内を出し、ログアウトして未ログインになったら確かめる（W3、D4）。
// - 確かめ・完了は最後に送った要求の答えだけで状態を移し、部品が外れた後の答えは捨てる（番号と印）。
// - 選んだ軸だけを U4 の口で画面に当て（D7）、完了の 204 で保存 → 受け渡し → /login へ置き換えの移動（D11）。
// - 部品が外れるときは見せ方をやめる（D12）。
import { useCallback, useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { useDisplaySettings } from '../../app/display-settings/DisplaySettingsProvider'
import { saveBrowserDisplaySettings } from '../../app/display-settings/displaySettingsStore'
import type {
  DisplayLanguage,
  FontSize,
  ThemeChoice,
} from '../../app/display-settings/displaySettingsTypes'
import { handOffToLogin } from '../../app/login-handoff/loginHandoff'
import { useLoginState, useLogout } from '../../app/login-state/LoginStateGate'
import { completeFailureKind, verifyFailureKind } from './failureKind'
import {
  checkRegistrationForm,
  type RegistrationFailure,
  type RegistrationFocusTarget,
  type RegistrationProblems,
  type RegistrationValues,
} from './formProblems'
import {
  completeRegistration,
  verifyRegistration,
  type VerifiedInvitation,
} from './registrationApi'
import { readRegistrationToken } from './registrationToken'

/** 画面の状態（functional-spec.md の 3節） */
export type RegistrationPhase =
  | 'loggedIn'
  | 'loggingOut'
  | 'verifying'
  | 'loadFailed'
  | 'unavailable'
  | 'ready'
  | 'submitting'
  | 'completed'

/** ログインの画面の URL（AuthUi の登録と同じ値） */
const LOGIN_PATH = '/login'

/** ホームの URL */
const HOME_PATH = '/'

interface RegistrationState {
  phase: RegistrationPhase
  /** メモリだけに持つトークン */
  token: string | undefined
  /** 確かめの 200 の値 */
  invitation: VerifiedInvitation | undefined
  /** フォームの値（確かめの 200 で初期値を入れる） */
  values: RegistrationValues | undefined
  problems: RegistrationProblems
  failure: RegistrationFailure | undefined
  focusTarget: RegistrationFocusTarget | undefined
  /** 確かめの回（「もう一度読み込む」で増やし、同じトークンで確かめ直す） */
  verifyAttempt: number
}

/** フックが返す値と操作 */
export interface RegistrationController {
  phase: RegistrationPhase
  invitation: VerifiedInvitation | undefined
  values: RegistrationValues | undefined
  problems: RegistrationProblems
  failure: RegistrationFailure | undefined
  focusTarget: RegistrationFocusTarget | undefined
  clearFocusTarget: () => void
  logoutAndContinue: () => void
  goHome: () => void
  reload: () => void
  setDisplayName: (value: string) => void
  setPassword: (value: string) => void
  setPasswordConfirmation: (value: string) => void
  selectLanguage: (language: DisplayLanguage) => void
  selectTheme: (theme: ThemeChoice) => void
  selectFontSize: (fontSize: FontSize) => void
  submit: () => void
}

/** 確かめの 200 の後のフォームの初期値（氏名はメールアドレス、言語は招待の言語、system・md。FR4.2） */
function initialValues(invitation: VerifiedInvitation): RegistrationValues {
  return {
    displayName: invitation.email,
    password: '',
    passwordConfirmation: '',
    language: invitation.language,
    theme: 'system',
    fontSize: 'md',
  }
}

/** 登録の完了の画面の状態と操作 */
export function useRegistration(): RegistrationController {
  const location = useLocation()
  const navigate = useNavigate()
  const loginState = useLoginState()
  const logout = useLogout()
  const display = useDisplaySettings()

  const [state, setState] = useState<RegistrationState>(() => {
    const token = readRegistrationToken(location.hash)
    let phase: RegistrationPhase = 'verifying'
    if (token === undefined) {
      phase = 'unavailable'
    } else if (loginState.loggedIn) {
      phase = 'loggedIn'
    }
    return {
      phase,
      token,
      invitation: undefined,
      values: undefined,
      problems: {},
      failure: undefined,
      focusTarget: undefined,
      verifyAttempt: 0,
    }
  })

  const mounted = useRef(true)
  const requestSeq = useRef(0)
  /** 送信中か（同じ時点の2回目の操作を防ぐため、状態とは別に同期で持つ） */
  const busy = useRef(false)
  /** ログアウトの途中か */
  const loggingOut = useRef(false)
  /** 応答を受けた後（描画の外）で使う U4 の口（最新のログイン状態に結び付いたもの） */
  const displayRef = useRef(display)

  useEffect(() => {
    displayRef.current = display
  }, [display])

  // 部品が付いているかの印と、外れるときの見せ方の取りやめ（D12）。
  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      displayRef.current.clearPreview()
    }
  }, [])

  // 描画の確定の後に、フラグメントを同じパス（問い合わせを含む）への置き換えの移動で消す（D2、W2 の2）。
  useEffect(() => {
    if (location.hash === '') {
      return
    }
    void navigate({ pathname: location.pathname, search: location.search }, { replace: true })
  }, [location.hash, location.pathname, location.search, navigate])

  // ログイン中の案内の間に未ログインになったら（ログアウトの完了、または更新の失敗などの知らせ）、確かめ中として扱う
  // （W3 の4）。状態の移り変わりは確かめの答えで行い、ここでは描画の中で決めるだけにする。
  const waitingForLogout = state.phase === 'loggedIn' || state.phase === 'loggingOut'
  const phase: RegistrationPhase =
    waitingForLogout && !loginState.loggedIn ? 'verifying' : state.phase

  // 確かめ中になったら確かめを送る（W4）。答えは、この回の副作用がまだ有効なとき（部品が付いていて、
  // 次の確かめ・状態の移り変わりが起きていない）だけ使う。開発時の StrictMode で2回送られても害は無い。
  const { token, verifyAttempt } = state
  useEffect(() => {
    if (phase !== 'verifying' || token === undefined) {
      return undefined
    }
    let active = true
    verifyRegistration(token).then(
      (invitation) => {
        if (!active) {
          return
        }
        displayRef.current.setLanguage(invitation.language)
        setState((current) => ({
          ...current,
          phase: 'ready',
          invitation,
          values: initialValues(invitation),
          problems: {},
          failure: undefined,
        }))
      },
      (error: unknown) => {
        if (!active) {
          return
        }
        setState((current) => ({ ...current, phase: verifyFailureKind(error) }))
      },
    )
    return () => {
      active = false
    }
  }, [phase, token, verifyAttempt])

  const logoutAndContinue = () => {
    if (phase !== 'loggedIn' || loggingOut.current) {
      return
    }
    loggingOut.current = true
    setState((current) => ({ ...current, phase: 'loggingOut' }))
    // ログアウトは骨組みの useLogout から行う（BR3.2）。提供元の logout は API の失敗でも画面の側の破棄を行い、例外を
    // 外へ出さない。未ログインの知らせで確かめへ移る。提供元が logout を持たず呼べなかったときは、案内に戻して押し直せる
    // ようにする（計画 D-7）。
    void logout().then((called) => {
      if (called || !mounted.current) {
        return
      }
      loggingOut.current = false
      setState((current) =>
        current.phase === 'loggingOut' ? { ...current, phase: 'loggedIn' } : current,
      )
    })
  }

  const goHome = () => {
    void navigate(HOME_PATH)
  }

  const reload = () => {
    if (state.phase === 'loadFailed') {
      setState((current) => ({
        ...current,
        phase: 'verifying',
        verifyAttempt: current.verifyAttempt + 1,
      }))
    }
  }

  const updateValues = (change: Partial<RegistrationValues>): boolean => {
    if (busy.current || state.values === undefined) {
      return false
    }
    setState((current) =>
      current.values === undefined
        ? current
        : { ...current, values: { ...current.values, ...change } },
    )
    return true
  }

  const selectLanguage = (language: DisplayLanguage) => {
    if (updateValues({ language })) {
      display.setLanguage(language)
    }
  }

  const selectTheme = (theme: ThemeChoice) => {
    if (updateValues({ theme })) {
      display.setPreview(theme)
    }
  }

  const selectFontSize = (fontSize: FontSize) => {
    if (updateValues({ fontSize })) {
      display.setPreview(undefined, fontSize)
    }
  }

  const submit = () => {
    const { invitation, values } = state
    if (
      busy.current ||
      phase !== 'ready' ||
      token === undefined ||
      invitation === undefined ||
      values === undefined
    ) {
      return
    }
    const check = checkRegistrationForm(values)
    if (check.firstInvalid !== undefined) {
      setState((current) => ({
        ...current,
        problems: check.problems,
        failure: undefined,
        focusTarget: check.firstInvalid,
      }))
      return
    }
    busy.current = true
    requestSeq.current += 1
    const seq = requestSeq.current
    const isLatest = () => mounted.current && seq === requestSeq.current
    setState((current) => ({ ...current, phase: 'submitting', problems: {}, failure: undefined }))
    completeRegistration({ token, ...values }).then(
      () => {
        if (!isLatest()) {
          return
        }
        busy.current = false
        // 保存 → 受け渡し → ログインの画面へ置き換えの移動（この順。D11）。自動ではログインしない。
        saveBrowserDisplaySettings({
          language: values.language,
          theme: values.theme,
          fontSize: values.fontSize,
        })
        handOffToLogin(invitation.email)
        setState((current) => ({
          ...current,
          phase: 'completed',
          token: undefined,
          values: undefined,
        }))
        void navigate(LOGIN_PATH, { replace: true })
      },
      (error: unknown) => {
        if (!isLatest()) {
          return
        }
        busy.current = false
        const kind = completeFailureKind(error)
        if (kind === 'unavailable') {
          // 使えないリンクの表示へ移り、フォームと入れた値（パスワードを含む）を捨てる（D5・D9）。
          setState((current) => ({
            ...current,
            phase: 'unavailable',
            token: undefined,
            values: undefined,
            problems: {},
            failure: undefined,
          }))
          return
        }
        setState((current) => ({
          ...current,
          phase: 'ready',
          failure: kind,
          focusTarget: kind === 'validationFailed' ? 'failure' : undefined,
        }))
      },
    )
  }

  const clearFocusTarget = useCallback(() => {
    setState((current) =>
      current.focusTarget === undefined ? current : { ...current, focusTarget: undefined },
    )
  }, [])

  return {
    phase,
    invitation: state.invitation,
    values: state.values,
    problems: state.problems,
    failure: state.failure,
    focusTarget: state.focusTarget,
    clearFocusTarget,
    logoutAndContinue,
    goHome,
    reload,
    setDisplayName: (displayName) => {
      updateValues({ displayName })
    },
    setPassword: (password) => {
      updateValues({ password })
    },
    setPasswordConfirmation: (passwordConfirmation) => {
      updateValues({ passwordConfirmation })
    },
    selectLanguage,
    selectTheme,
    selectFontSize,
    submit,
  }
}
