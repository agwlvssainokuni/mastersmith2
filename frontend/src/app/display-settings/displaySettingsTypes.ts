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
// 表示の設定の型と許される値の一覧（D1・D12、契約 C7・C9）。
// 許される値は `as const` の一覧に1回だけ書き、型はそこから作る（enum は使わない）。
import type { DisplayLanguage } from '../i18n/resolveLanguage'

export type { DisplayLanguage }

/** 画面の言語として許される値 */
export const DISPLAY_LANGUAGES = ['ja', 'en'] as const satisfies readonly DisplayLanguage[]

/** テーマの選択として許される値（`system` は OS の配色で解く。D4） */
export const THEME_CHOICES = ['light', 'dark', 'system'] as const

/** テーマの選択 */
export type ThemeChoice = (typeof THEME_CHOICES)[number]

/** 画面に当てるテーマ（テーマの選択を解いた値） */
export type ResolvedTheme = 'light' | 'dark'

/** 文字の大きさとして許される値 */
export const FONT_SIZES = ['sm', 'md', 'lg'] as const

/** 文字の大きさ */
export type FontSize = (typeof FONT_SIZES)[number]

/** インスタンスのブランドカラーとして許される値（契約 C7） */
export const BRAND_COLORS = ['blue', 'green', 'purple', 'orange'] as const

/** ブランドカラー */
export type BrandColor = (typeof BRAND_COLORS)[number]

/** インスタンスのフォントファミリーとして許される値（契約 C7） */
export const FONT_FAMILIES = ['sans', 'serif'] as const

/** フォントファミリー */
export type FontFamily = (typeof FONT_FAMILIES)[number]

/** 表示の設定の3つの軸 */
export interface DisplaySettings {
  language: DisplayLanguage
  theme: ThemeChoice
  fontSize: FontSize
}

/** 軸が欠けてよい表示の設定（ブラウザの保存の値・見せ方など） */
export type PartialDisplaySettings = Partial<DisplaySettings>

/** 利用者の設定（3つの軸と氏名。契約 C3・C4） */
export interface UserDisplaySettings extends DisplaySettings {
  displayName: string
}

/** 見た目の設定のうち当てる値（許される値の項目だけを持つ。契約 C7） */
export interface Appearance {
  brandColor?: BrandColor
  fontFamily?: FontFamily
}

/** 言語の選択肢の名前（それぞれの言語の名前で示し、訳さない。D12・NFR8.2） */
export const LANGUAGE_NAMES: Readonly<Record<DisplayLanguage, string>> = {
  ja: '日本語',
  en: 'English',
}

/** ブラウザの保存の値が無いときのテーマの選択（D1） */
export const DEFAULT_THEME: ThemeChoice = 'system'

/** ブラウザの保存の値が無いときの文字の大きさ（D1） */
export const DEFAULT_FONT_SIZE: FontSize = 'md'
