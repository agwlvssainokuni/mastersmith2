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
// 表示の設定の土台（部品 3.1・3.3、W2〜W6・W10〜W12、D2〜D6・D8・D11・D14、契約 C9）。
// - 見た目の設定の答えが出るまで子を描かない（ログイン状態の最初の答えは外側の LoginStateGate が待つ）。
// - 画面の値は描画の中で決める（ログイン状態が変わった描画で、そのまま言語の文言が切り替わる）。
// - 描画の確定の直後（useLayoutEffect）に、make-you-chic-ui・<html lang>・要求の言語へ反映する。
// 機能（features/*）は表示の設定をこの口だけで扱い、make-you-chic-ui の useTheme と localStorage を直接触らない。
import { useTheme } from 'make-you-chic-ui'
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useLayoutEffect,
  useMemo,
  useRef,
  useState,
  useSyncExternalStore,
  type ReactNode,
} from 'react'
import { useLoginState } from '../login-state/LoginStateGate'
import { peekAppearance, type AppearanceResult } from './appearanceLoad'
import { getPrefersDark, subscribePrefersDark } from './colorScheme'
import {
  applyUserPreferencesFor,
  browserLanguages,
  clearPreview,
  discardStaleBindings,
  getDisplaySettingsSnapshot,
  installLanguageResolver,
  rememberUserSettings,
  setAppliedLanguage,
  setLanguagePreviewFor,
  setPreviewFor,
  subscribeDisplaySettings,
} from './displaySettingsStore'
import type {
  DisplayLanguage,
  FontSize,
  ResolvedTheme,
  ThemeChoice,
  UserDisplaySettings,
} from './displaySettingsTypes'
import {
  decideScreenSettings,
  validateUserDisplaySettings,
  type ScreenSettingsInput,
} from './resolveDisplaySettings'

/** useDisplaySettings() が返す値（契約 C9） */
export interface DisplaySettingsValue {
  /** 画面の言語（言語の見せ方を含む） */
  language: DisplayLanguage
  /** テーマの画面の値（選択。見せ方を含む） */
  theme: ThemeChoice
  /** 文字の大きさの画面の値（見せ方を含む） */
  fontSize: FontSize
  /** theme を OS の配色で解いた値 */
  resolvedTheme: ResolvedTheme
  /** ログインの後の氏名（ログインの前は undefined） */
  displayName: string | undefined
  /** テーマ・文字の大きさの見せ方を置く（渡した軸だけ。保存しない） */
  setPreview: (theme?: ThemeChoice, fontSize?: FontSize) => void
  /** 見せ方をすべてやめ、当てている値に戻す */
  clearPreview: () => void
  /** 保存の後の利用者の設定を当てる（ログインの後だけ有効） */
  applyUserPreferences: (prefs: UserDisplaySettings) => void
  /** 言語の見せ方を置く（保存しない） */
  setLanguage: (language: DisplayLanguage) => void
}

const DisplaySettingsContext = createContext<DisplaySettingsValue | null>(null)

export interface DisplaySettingsProviderProps {
  /** 見た目の設定の読み取りの約束（画面の入口で描画の外に1回だけ作る） */
  appearance: Promise<AppearanceResult>
  children: ReactNode
}

/** テーマの選択が system の間だけ OS の配色の変化を購読する（W11）。 */
function usePrefersDark(enabled: boolean): boolean {
  const subscribe = useCallback(
    (listener: () => void) => (enabled ? subscribePrefersDark(listener) : () => undefined),
    [enabled],
  )
  return useSyncExternalStore(subscribe, getPrefersDark)
}

/** <html> のテーマと文字の大きさの属性を置く（make-you-chic-ui と同じ形。light は属性なし）。 */
function applyHtmlAttributes(theme: ResolvedTheme, fontSize: FontSize): void {
  const html = document.documentElement
  if (theme === 'light') {
    html.removeAttribute('data-theme')
  } else {
    html.setAttribute('data-theme', theme)
  }
  html.setAttribute('data-font-size', fontSize)
}

/** 表示の設定を決めて画面に当て、口を子へ渡す。 */
export function DisplaySettingsProvider({ appearance, children }: DisplaySettingsProviderProps) {
  const [appearanceResult, setAppearanceResult] = useState(() => peekAppearance(appearance))
  const loginState = useLoginState()
  const store = useSyncExternalStore(subscribeDisplaySettings, getDisplaySettingsSnapshot)
  const {
    theme: shownTheme,
    fontSize: shownFontSize,
    setTheme,
    setFontSize,
    setBrand,
    setFontFamily,
  } = useTheme()

  useEffect(() => {
    if (appearanceResult !== undefined) {
      return undefined
    }
    let active = true
    void appearance.then((result) => {
      if (active) {
        setAppearanceResult(result)
      }
    })
    return () => {
      active = false
    }
  }, [appearance, appearanceResult])

  const userPreferences = useMemo(
    () => (loginState.loggedIn ? validateUserDisplaySettings(loginState.preferences) : undefined),
    [loginState],
  )
  const input: Omit<ScreenSettingsInput, 'prefersDark'> = {
    loggedIn: loginState.loggedIn,
    binding: loginState,
    userPreferences,
    savedUser: store.savedUser,
    stored: store.stored,
    preview: store.preview,
    browserLanguages: browserLanguages(),
  }
  const followsOs = decideScreenSettings({ ...input, prefersDark: false }).theme === 'system'
  const prefersDark = usePrefersDark(followsOs)
  const settings = decideScreenSettings({ ...input, prefersDark })
  const { language, theme, fontSize, resolvedTheme } = settings
  let displayName: string | undefined
  if (loginState.loggedIn) {
    displayName =
      store.savedUser !== null && store.savedUser.binding === loginState
        ? store.savedUser.value.displayName
        : loginState.displayName
  }

  // make-you-chic-ui に最後に渡した値（最初は make-you-chic-ui が今持つ値）。このタブの画面の値が変わったときだけ渡し、
  // ほかのタブで起きた make-you-chic-ui の変化を打ち消さない（4.2、Q3 A）。
  const lastPassed = useRef<{ theme: ResolvedTheme; fontSize: FontSize } | null>(null)
  if (lastPassed.current === null) {
    lastPassed.current = { theme: shownTheme, fontSize: shownFontSize }
  }

  // ログイン状態が新しくなったら、古いログイン状態に結び付いた見せ方を捨て、利用者の設定をブラウザに保存する（D2・D6 の (a)）。
  useLayoutEffect(() => {
    discardStaleBindings(loginState)
    if (userPreferences !== undefined) {
      rememberUserSettings(userPreferences)
    }
  }, [loginState, userPreferences])

  // テーマと文字の大きさを、画面に出る前に make-you-chic-ui に渡す（D3・D4）。
  useLayoutEffect(() => {
    const last = lastPassed.current
    if (last === null || (last.theme === resolvedTheme && last.fontSize === fontSize)) {
      return
    }
    if (last.theme !== resolvedTheme) {
      setTheme(resolvedTheme)
    }
    if (last.fontSize !== fontSize) {
      setFontSize(fontSize)
    }
    // make-you-chic-ui は <html> の属性を描画の後（useEffect）で置くため、同じ時点でここでも置き、
    // ログインの後の最初の画面に前の利用者の見た目が出ないようにする（部品 3.3 が許した選択）。
    applyHtmlAttributes(resolvedTheme, fontSize)
    lastPassed.current = { theme: resolvedTheme, fontSize }
  }, [resolvedTheme, fontSize, setTheme, setFontSize])

  // 文言と同じ時点で <html lang> と要求の言語を切り替える（D14・W12）。
  useLayoutEffect(() => {
    document.documentElement.lang = language
    setAppliedLanguage(language)
    installLanguageResolver()
  }, [language])

  // 見た目の設定は答えが出たときに1回だけ当てる。許される値の項目だけ（W3、FR8.1）。
  useLayoutEffect(() => {
    if (appearanceResult?.brandColor !== undefined) {
      setBrand(appearanceResult.brandColor)
    }
    if (appearanceResult?.fontFamily !== undefined) {
      setFontFamily(appearanceResult.fontFamily)
    }
  }, [appearanceResult, setBrand, setFontFamily])

  const value = useMemo<DisplaySettingsValue>(
    () => ({
      language,
      theme,
      fontSize,
      resolvedTheme,
      displayName,
      setPreview: (nextTheme, nextFontSize) => setPreviewFor(loginState, nextTheme, nextFontSize),
      clearPreview,
      applyUserPreferences: (prefs) => {
        if (loginState.loggedIn) {
          applyUserPreferencesFor(loginState, prefs)
        }
      },
      setLanguage: (nextLanguage) => setLanguagePreviewFor(loginState, nextLanguage),
    }),
    [language, theme, fontSize, resolvedTheme, displayName, loginState],
  )

  if (appearanceResult === undefined) {
    return null
  }
  return <DisplaySettingsContext.Provider value={value}>{children}</DisplaySettingsContext.Provider>
}

/** 表示の設定の口を返す（DisplaySettingsProvider の外では使えない）。 */
export function useDisplaySettings(): DisplaySettingsValue {
  const value = useContext(DisplaySettingsContext)
  if (value === null) {
    throw new Error('useDisplaySettings は DisplaySettingsProvider の中で使ってください')
  }
  return value
}
