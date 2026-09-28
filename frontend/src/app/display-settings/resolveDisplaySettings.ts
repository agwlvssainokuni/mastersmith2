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
// 表示の設定の解き方（D1・D2・D4・D7・D8、W3・W5、部品 3.3）。
// React・ブラウザの保存・window に触れない純粋な関数だけを置き、性質ベースのテストの対象にする。
import { resolveLanguage } from '../i18n/resolveLanguage'
import {
  BRAND_COLORS,
  DEFAULT_FONT_SIZE,
  DEFAULT_THEME,
  DISPLAY_LANGUAGES,
  FONT_FAMILIES,
  FONT_SIZES,
  THEME_CHOICES,
  type Appearance,
  type BrandColor,
  type DisplayLanguage,
  type FontFamily,
  type FontSize,
  type PartialDisplaySettings,
  type ResolvedTheme,
  type ThemeChoice,
} from './displaySettingsTypes'

/** 値が一覧のどれかと文字どおり一致するか（大文字・前後の空白の揺れは直さない）。 */
function isOneOf<T extends string>(list: readonly T[], value: unknown): value is T {
  return typeof value === 'string' && (list as readonly string[]).includes(value)
}

/** 画面の言語として許される値か（NFR9.2）。 */
export function isDisplayLanguage(value: unknown): value is DisplayLanguage {
  return isOneOf(DISPLAY_LANGUAGES, value)
}

/** テーマの選択として許される値か。 */
export function isThemeChoice(value: unknown): value is ThemeChoice {
  return isOneOf(THEME_CHOICES, value)
}

/** 文字の大きさとして許される値か。 */
export function isFontSize(value: unknown): value is FontSize {
  return isOneOf(FONT_SIZES, value)
}

/** 配列でも null でもないオブジェクトか。 */
function isPlainObject(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

/** オブジェクトから3つの軸を項目ごとに検証して取り出す。外れた項目と知らない項目は捨てる（D7）。 */
function pickDisplaySettings(value: Record<string, unknown>): PartialDisplaySettings {
  const picked: PartialDisplaySettings = {}
  if (isDisplayLanguage(value.language)) {
    picked.language = value.language
  }
  if (isThemeChoice(value.theme)) {
    picked.theme = value.theme
  }
  if (isFontSize(value.fontSize)) {
    picked.fontSize = value.fontSize
  }
  return picked
}

/**
 * ブラウザに保存した U4 の鍵の値を読む（4.1・D7）。JSON として読めない・オブジェクトでなければ全体を無いものとし、
 * 読めたら項目ごとに許される値だけを受け入れる。例外は出さない。
 */
export function parseStoredDisplaySettings(raw: string | null): PartialDisplaySettings {
  if (raw === null) {
    return {}
  }
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    return {}
  }
  return isPlainObject(parsed) ? pickDisplaySettings(parsed) : {}
}

/** テーマの選択を画面に当てる値に解く。`system` は OS の配色で解き、`light`・`dark` は OS によらない（D4）。 */
export function resolveTheme(choice: ThemeChoice, prefersDark: boolean): ResolvedTheme {
  if (choice === 'system') {
    return prefersDark ? 'dark' : 'light'
  }
  return choice === 'dark' ? 'dark' : 'light'
}

/**
 * ログイン状態の表示の設定を項目ごとに検証する（W5 の6）。オブジェクトでなければ undefined（表示の設定が無い）、
 * 外れた項目はその項目だけを無いものにする。
 */
export function validateUserDisplaySettings(prefs: unknown): PartialDisplaySettings | undefined {
  return isPlainObject(prefs) ? pickDisplaySettings(prefs) : undefined
}

/** ログイン状態に結び付けた値（結び付けの印はログイン状態の値そのもの。D2・D8） */
export interface Bound<T> {
  binding: unknown
  value: T
}

/** 画面の値を決める入力 */
export interface ScreenSettingsInput {
  /** ログイン中か */
  loggedIn: boolean
  /** 今のログイン状態の印（ログイン状態が新しくなるたびに変わる） */
  binding: unknown
  /** ログイン状態の表示の設定（検証済み。無ければ undefined） */
  userPreferences?: PartialDisplaySettings
  /** 保存の後の利用者の設定 */
  savedUser?: Bound<PartialDisplaySettings> | null
  /** ブラウザの保存の値 */
  stored: PartialDisplaySettings
  /** 見せ方（未保存） */
  preview?: Bound<PartialDisplaySettings> | null
  /** ブラウザの言語設定（優先順） */
  browserLanguages: readonly string[]
  /** OS の配色が dark か */
  prefersDark: boolean
}

/** 画面に当たる値 */
export interface ScreenSettings {
  language: DisplayLanguage
  theme: ThemeChoice
  fontSize: FontSize
  resolvedTheme: ResolvedTheme
}

/** 当てている値の土台を選ぶ（部品 3.3 の (1)）。 */
function chooseBase(input: ScreenSettingsInput): PartialDisplaySettings {
  if (input.loggedIn) {
    if (input.savedUser && input.savedUser.binding === input.binding) {
      return input.savedUser.value
    }
    if (input.userPreferences !== undefined) {
      // ログインの後は前の利用者のブラウザの保存の値を使わない。外れた項目は D1 の既定で解く（D2、W5 の6）。
      return input.userPreferences
    }
  }
  return input.stored
}

/**
 * 画面の値を決める（部品 3.3）。(1) 当てている値（ログインの後は利用者の設定、ログインの前はブラウザの保存の値。
 * 無い軸は D1 の既定）、(2) 今のログイン状態に結び付いた見せ方の軸だけを置き換え、(3) テーマを解く。
 * どの入力でも、3つの軸は許される値になる。
 */
export function decideScreenSettings(input: ScreenSettingsInput): ScreenSettings {
  const base = chooseBase(input)
  let language: DisplayLanguage = isDisplayLanguage(base.language)
    ? base.language
    : resolveLanguage(input.browserLanguages)
  let theme: ThemeChoice = isThemeChoice(base.theme) ? base.theme : DEFAULT_THEME
  let fontSize: FontSize = isFontSize(base.fontSize) ? base.fontSize : DEFAULT_FONT_SIZE
  const preview = input.preview
  if (preview && preview.binding === input.binding) {
    if (isDisplayLanguage(preview.value.language)) {
      language = preview.value.language
    }
    if (isThemeChoice(preview.value.theme)) {
      theme = preview.value.theme
    }
    if (isFontSize(preview.value.fontSize)) {
      fontSize = preview.value.fontSize
    }
  }
  return { language, theme, fontSize, resolvedTheme: resolveTheme(theme, input.prefersDark) }
}

/**
 * 見た目の設定の応答の本文を検証する（W3、NFR9.3）。項目ごとに契約 C7 の許される値だけを返し、
 * 余計な項目は読まない。オブジェクトでなければ（配列・数・null など）当てる値なし。
 */
export function parseAppearance(body: unknown): Appearance {
  if (!isPlainObject(body)) {
    return {}
  }
  const appearance: Appearance = {}
  if (isOneOf<BrandColor>(BRAND_COLORS, body.brandColor)) {
    appearance.brandColor = body.brandColor
  }
  if (isOneOf<FontFamily>(FONT_FAMILIES, body.fontFamily)) {
    appearance.fontFamily = body.fontFamily
  }
  return appearance
}
