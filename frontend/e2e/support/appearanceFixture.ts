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
// 見た目の設定の応答の見本（契約 C7）。E2E で API の答えを差し替えるときは、この見本1つだけを使い、
// 画面の側の型を付ける。本物の応答と項目の名前・型が一致することを、本物の応答を使う測定のテストで毎回確かめる
// （project.md の Corrections）。
import type { BrandColor, FontFamily } from '../../src/app/display-settings/displaySettingsTypes'

/** GET /api/appearance の応答の形（契約 C7） */
export interface AppearanceResponse {
  brandColor: BrandColor
  fontFamily: FontFamily
}

/** 見本（既定の見た目の設定と同じ値） */
export const APPEARANCE_SAMPLE: AppearanceResponse = { brandColor: 'blue', fontFamily: 'sans' }

/** ブランドカラーだけを変えた見本の応答 */
export function appearanceWith(brandColor: BrandColor): AppearanceResponse {
  return { ...APPEARANCE_SAMPLE, brandColor }
}

/** 本物の応答の項目の名前と値の型が、見本と一致するか。 */
export function hasSampleShape(body: unknown): boolean {
  if (typeof body !== 'object' || body === null || Array.isArray(body)) {
    return false
  }
  const record = body as Record<string, unknown>
  const sampleKeys = Object.keys(APPEARANCE_SAMPLE).sort()
  const keys = Object.keys(record).sort()
  return (
    JSON.stringify(keys) === JSON.stringify(sampleKeys) &&
    sampleKeys.every(
      (key) => typeof record[key] === typeof APPEARANCE_SAMPLE[key as keyof AppearanceResponse],
    )
  )
}
