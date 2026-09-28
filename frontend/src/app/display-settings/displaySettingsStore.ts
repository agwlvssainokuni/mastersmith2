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
// 表示の設定の置き場（部品 3.2・3.3、D2・D6・D8）。フックの外から呼べる口の関数のため、
// 「ブラウザの保存の値」「見せ方」「保存の後の利用者の設定」をモジュールの中の1つの購読できる状態に持つ。
// 見せ方と保存の後の利用者の設定は、置いたときのログイン状態（の値そのもの）に結び付けて持ち、
// ログイン状態が新しくなったら捨てたものとして扱う。
import { registerLanguageResolver } from '../../shared/api-client/apiClient'
import { resolveLanguage } from '../i18n/resolveLanguage'
import {
  readStoredDisplaySettings,
  writeStoredDisplaySettings,
  writeStoredLanguage,
} from './browserStorage'
import {
  DEFAULT_FONT_SIZE,
  DEFAULT_THEME,
  type DisplayLanguage,
  type DisplaySettings,
  type FontSize,
  type PartialDisplaySettings,
  type ThemeChoice,
  type UserDisplaySettings,
} from './displaySettingsTypes'
import { isDisplayLanguage, isFontSize, isThemeChoice, type Bound } from './resolveDisplaySettings'

/** 置き場の状態 */
export interface DisplaySettingsState {
  /** ブラウザの保存の値 */
  stored: PartialDisplaySettings
  /** 見せ方（未保存） */
  preview: Bound<PartialDisplaySettings> | null
  /** 保存の後の利用者の設定 */
  savedUser: Bound<UserDisplaySettings> | null
}

const listeners = new Set<() => void>()

let state: DisplaySettingsState | null = null

/** 画面に当てている言語（DisplaySettingsProvider が描画の確定の直後に置く） */
let appliedLanguage: DisplayLanguage | null = null

function current(): DisplaySettingsState {
  state ??= { stored: readStoredDisplaySettings(), preview: null, savedUser: null }
  return state
}

function setState(next: DisplaySettingsState): void {
  state = next
  listeners.forEach((listener) => listener())
}

/** ブラウザの言語設定を優先順に返す。 */
export function browserLanguages(): readonly string[] {
  if (typeof navigator === 'undefined') {
    return []
  }
  if (navigator.languages && navigator.languages.length > 0) {
    return navigator.languages
  }
  return navigator.language ? [navigator.language] : []
}

/** 今の状態を返す（React の useSyncExternalStore が使う）。 */
export function getDisplaySettingsSnapshot(): DisplaySettingsState {
  return current()
}

/** 状態の変化を受け取る。戻り値は受け取りをやめる関数。 */
export function subscribeDisplaySettings(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

/** 3つの軸を許される値にそろえる（外れた軸は D1 の既定。言語はブラウザの言語設定）。 */
function completeSettings(settings: PartialDisplaySettings): DisplaySettings {
  return {
    language: isDisplayLanguage(settings.language)
      ? settings.language
      : resolveLanguage(browserLanguages()),
    theme: isThemeChoice(settings.theme) ? settings.theme : DEFAULT_THEME,
    fontSize: isFontSize(settings.fontSize) ? settings.fontSize : DEFAULT_FONT_SIZE,
  }
}

/**
 * 3つをブラウザに保存し、見せ方を捨てる（登録の完了。W8・D6 の (c)、契約 C9）。
 * ログインの前は当てている値がこの値になる。ログインの後に呼んでも当てている値（利用者の設定）は変わらない。
 */
export function saveBrowserDisplaySettings(settings: DisplaySettings): void {
  const next = completeSettings(settings)
  writeStoredDisplaySettings(next)
  setState({ ...current(), stored: next, preview: null })
}

/** ブラウザの保存の値の言語だけを書き換え、言語の見せ方を捨てる（ログインの画面の言語の切り替え。W7・D6 の (d)）。 */
export function saveBrowserLanguage(language: DisplayLanguage): void {
  if (!isDisplayLanguage(language)) {
    return
  }
  writeStoredLanguage(language)
  const { preview, stored } = current()
  let nextPreview: Bound<PartialDisplaySettings> | null = null
  if (preview) {
    const rest: PartialDisplaySettings = { ...preview.value }
    delete rest.language
    nextPreview = Object.keys(rest).length > 0 ? { binding: preview.binding, value: rest } : null
  }
  setState({ ...current(), stored: { ...stored, language }, preview: nextPreview })
}

/** テーマ・文字の大きさの見せ方を置く。渡した軸だけを置き換え、渡さない軸は前の見せ方のまま（保存しない）。 */
export function setPreviewFor(binding: unknown, theme?: ThemeChoice, fontSize?: FontSize): void {
  const { preview } = current()
  const base = preview && preview.binding === binding ? preview.value : {}
  const value: PartialDisplaySettings = { ...base }
  if (isThemeChoice(theme)) {
    value.theme = theme
  }
  if (isFontSize(fontSize)) {
    value.fontSize = fontSize
  }
  setState({ ...current(), preview: { binding, value } })
}

/** 言語の見せ方を置く（保存しない）。 */
export function setLanguagePreviewFor(binding: unknown, language: DisplayLanguage): void {
  if (!isDisplayLanguage(language)) {
    return
  }
  const { preview } = current()
  const base = preview && preview.binding === binding ? preview.value : {}
  setState({ ...current(), preview: { binding, value: { ...base, language } } })
}

/** 見せ方（テーマ・文字の大きさ・言語）をすべてやめる。 */
export function clearPreview(): void {
  if (current().preview !== null) {
    setState({ ...current(), preview: null })
  }
}

/**
 * 保存の後の利用者の設定を当てる（プリファレンスの保存の成功。W10・D6 の (b)・D8）。
 * 今のログイン状態に結び付け、見せ方を捨て、ブラウザにも保存する。
 */
export function applyUserPreferencesFor(binding: unknown, prefs: UserDisplaySettings): void {
  const settings = completeSettings(prefs)
  writeStoredDisplaySettings(settings)
  setState({
    stored: settings,
    preview: null,
    savedUser: { binding, value: { ...settings, displayName: prefs.displayName } },
  })
}

/** ログイン状態の利用者の設定をブラウザに保存する（ログインの成功・復元・更新の応答。D6 の (a)）。 */
export function rememberUserSettings(prefs: PartialDisplaySettings): void {
  const settings = completeSettings(prefs)
  writeStoredDisplaySettings(settings)
  setState({ ...current(), stored: settings })
}

/** ログイン状態が新しくなったとき、ほかのログイン状態に結び付いた見せ方と保存の後の利用者の設定を捨てる（D2・D8）。 */
export function discardStaleBindings(binding: unknown): void {
  const { preview, savedUser } = current()
  const stalePreview = preview !== null && preview.binding !== binding
  const staleUser = savedUser !== null && savedUser.binding !== binding
  if (stalePreview || staleUser) {
    setState({
      ...current(),
      preview: stalePreview ? null : preview,
      savedUser: staleUser ? null : savedUser,
    })
  }
}

/** 画面に当てている言語を置く（DisplaySettingsProvider が使う）。 */
export function setAppliedLanguage(language: DisplayLanguage): void {
  appliedLanguage = language
}

/**
 * 今の画面の言語を返す（要求の言語。W12）。画面を描く前は、ブラウザの保存の値とブラウザの言語設定で決める
 * （ログインの前の画面の言語と同じ決め方）。
 */
export function currentScreenLanguage(): DisplayLanguage {
  if (appliedLanguage !== null) {
    return appliedLanguage
  }
  const { stored } = current()
  return isDisplayLanguage(stored.language) ? stored.language : resolveLanguage(browserLanguages())
}

/** ApiClient に要求の言語の関数を登録する（画面の入口と DisplaySettingsProvider が呼ぶ。W12 の1）。 */
export function installLanguageResolver(): void {
  registerLanguageResolver(currentScreenLanguage)
}

/** 状態を初めに戻す（テストで使う。次に読むときにブラウザの保存の値を読み直す）。 */
export function resetDisplaySettings(): void {
  state = null
  appliedLanguage = null
  listeners.forEach((listener) => listener())
}
