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
// プリファレンスの応答の見本（契約 C4 の Preferences。Intent 260925-user-management の B5 の共用の手伝い、U7 の NFR 設計
// logical-components.md 5.3）。E2E で GET /api/me/preferences の答えを差し替えるときは、この見本だけを使い、画面の側の型
// （src/features/preferences/preferencesApi.ts の Preferences）を付ける。本物の応答と項目の名前・型が一致することを、
// 080 の測りで毎回確かめる（hasPreferencesShape。値は比べない。project.md の Corrections）。
// 氏名は固定のテストの値だけで、実在の個人に関する値を使わない。
import type { Preferences } from '../../src/features/preferences/preferencesApi'

/** 差し替えの答えの氏名（固定のテストの値） */
export const SAMPLE_DISPLAY_NAME = '検査 太郎'

/** 組のテーマと文字の大きさを持つ見本（言語は ja） */
export function preferencesSample(
  theme: Preferences['theme'],
  fontSize: Preferences['fontSize'],
): Preferences {
  return { displayName: SAMPLE_DISPLAY_NAME, language: 'ja', theme, fontSize }
}

const LANGUAGES: readonly string[] = ['ja', 'en']
const THEMES: readonly string[] = ['light', 'dark', 'system']
const FONT_SIZES: readonly string[] = ['sm', 'md', 'lg']

/**
 * 本物の応答の項目の名前と値の型が見本と一致するか（項目は4つちょうど、氏名は文字列、3つの列挙は決めた値の中）。
 * 値そのものは返さない。
 */
export function hasPreferencesShape(body: unknown): boolean {
  if (typeof body !== 'object' || body === null || Array.isArray(body)) {
    return false
  }
  const record = body as Record<string, unknown>
  const sampleKeys = Object.keys(preferencesSample('light', 'md')).sort()
  return (
    JSON.stringify(Object.keys(record).sort()) === JSON.stringify(sampleKeys) &&
    typeof record.displayName === 'string' &&
    LANGUAGES.includes(String(record.language)) &&
    THEMES.includes(String(record.theme)) &&
    FONT_SIZES.includes(String(record.fontSize))
  )
}
