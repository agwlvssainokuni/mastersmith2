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
// ブラウザの保存（4.1・4.2・D5〜D7、NFR2.2）。U4 の鍵と make-you-chic-ui の写しの鍵を読み書きするのは、このモジュールだけ。
// - 書く値は3つの軸だけからその都度 JSON を作る（ログイン状態・応答の本文をそのまま書かない）。
// - 読み書きの例外は外へ出さない。読めなければ無いものとし、書けなければ保存せずに続ける。
// - make-you-chic-ui のブランドとフォントファミリーの鍵は書かない（make-you-chic-ui の setBrand・setFontFamily が書く）。
import {
  DEFAULT_FONT_SIZE,
  DEFAULT_THEME,
  type DisplayLanguage,
  type DisplaySettings,
  type PartialDisplaySettings,
} from './displaySettingsTypes'
import { parseStoredDisplaySettings, resolveTheme } from './resolveDisplaySettings'

/** U4 の鍵（1つの鍵に3つの値をまとめる） */
export const DISPLAY_SETTINGS_KEY = 'mastersmith.display-settings'

/** make-you-chic-ui のテーマの写しの鍵 */
export const DESIGN_SYSTEM_THEME_KEY = 'design-system-theme'

/** make-you-chic-ui の文字の大きさの写しの鍵 */
export const DESIGN_SYSTEM_FONT_SIZE_KEY = 'design-system-font-size'

function safeGet(key: string): string | null {
  try {
    return window.localStorage.getItem(key)
  } catch {
    return null
  }
}

function safeSet(key: string, value: string): void {
  try {
    window.localStorage.setItem(key, value)
  } catch {
    // ブラウザの保存が使えないときは保存せずに続ける（D7）。
  }
}

/** 3つの軸だけから保存の JSON を作る（無い軸は書かない）。 */
function toStoredJson(settings: PartialDisplaySettings): string {
  return JSON.stringify({
    language: settings.language,
    theme: settings.theme,
    fontSize: settings.fontSize,
  })
}

/** ブラウザの保存の値を読む（項目ごとに検証済み）。 */
export function readStoredDisplaySettings(): PartialDisplaySettings {
  return parseStoredDisplaySettings(safeGet(DISPLAY_SETTINGS_KEY))
}

/** 3つをまとめてブラウザに保存する（D6 の (a)〜(c)）。 */
export function writeStoredDisplaySettings(settings: DisplaySettings): void {
  safeSet(DISPLAY_SETTINGS_KEY, toStoredJson(settings))
}

/**
 * 言語だけを保存する（D6 の (d)）。読み直した今の値の言語だけを置き換え、ほかの2つは保存済みのまま（無い項目は無いまま）。
 * 保存した後の値を返す。
 */
export function writeStoredLanguage(language: DisplayLanguage): PartialDisplaySettings {
  const next: PartialDisplaySettings = { ...readStoredDisplaySettings(), language }
  safeSet(DISPLAY_SETTINGS_KEY, toStoredJson(next))
  return next
}

/**
 * make-you-chic-ui の写しの鍵を、U4 の鍵の値で書き直す（W1 の2・D5）。make-you-chic-ui の ThemeProvider が
 * 保存の値を読むより前に1回だけ呼ぶ。テーマは U4 の鍵の選択を解いた値（無ければ OS の配色で解いた値）、
 * 文字の大きさは U4 の鍵の値（無ければ md）。
 */
export function rewriteDesignSystemCopies(prefersDark: boolean): void {
  const stored = readStoredDisplaySettings()
  safeSet(DESIGN_SYSTEM_THEME_KEY, resolveTheme(stored.theme ?? DEFAULT_THEME, prefersDark))
  safeSet(DESIGN_SYSTEM_FONT_SIZE_KEY, stored.fontSize ?? DEFAULT_FONT_SIZE)
}
