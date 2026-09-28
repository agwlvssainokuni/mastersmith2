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
// 自分の設定の API（契約 C4 の GET・PUT /api/me/preferences と POST /api/me/password）を呼ぶ関数と型
// （frontend-components.md の 5節、security-design.md の 2節・3.1、NFR6.4・NFR9.1〜NFR9.3）。
// - 既存の ApiClient（apiRequest）を通す。Authorization・401 での更新と送り直し・Accept-Language は ApiClient に任せ、
//   ここでは付けない。失敗は ApiClient の ApiError をそのまま投げる。
// - 成功の本文の形の確かめはこのファイルの1か所に置く。決めた値の外なら PreferencesShapeError（ApiError とは別の種類）を投げ、
//   決めた値の外の文字列を U4 の口へ渡さない。知らない項目は捨てる。
// - 要求の本文は決めた項目だけにする。要求と応答の値をコンソール・ブラウザの保存・URL に出さない。
import type {
  DisplayLanguage,
  FontSize,
  ThemeChoice,
} from '../../app/display-settings/displaySettingsTypes'
import {
  isDisplayLanguage,
  isFontSize,
  isThemeChoice,
} from '../../app/display-settings/resolveDisplaySettings'
import { apiRequest } from '../../shared/api-client/apiClient'

/** 自分の氏名と表示の設定の API のパス */
export const ME_PREFERENCES_PATH = '/api/me/preferences'

/** 自分のパスワードの変更の API のパス */
export const ME_PASSWORD_PATH = '/api/me/password'

/** 氏名と表示の設定の4つ（契約 C4 の Preferences） */
export interface Preferences {
  displayName: string
  language: DisplayLanguage
  theme: ThemeChoice
  fontSize: FontSize
}

/** パスワードの変更の要求の3つ（契約 C4 の PasswordChangeRequest） */
export interface PasswordChangeInput {
  currentPassword: string
  newPassword: string
  newPasswordConfirmation: string
}

/** プリファレンスの画面の項目の名前（C4 の項目名） */
export type PreferencesField = 'displayName' | 'language' | 'theme' | 'fontSize'

/** パスワードの変更の画面の項目の名前（C4 の項目名） */
export type PasswordField = 'currentPassword' | 'newPassword' | 'newPasswordConfirmation'

/** 成功の応答の本文が決めた形でない（ApiError とは別の失敗の種類。値は持たない） */
export class PreferencesShapeError extends Error {
  constructor() {
    super('プリファレンスの応答の形が違います')
    this.name = 'PreferencesShapeError'
  }
}

/** 本文を決めた4つだけの値にする。形が違えば PreferencesShapeError を投げる。 */
function toPreferences(body: unknown): Preferences {
  if (typeof body !== 'object' || body === null || Array.isArray(body)) {
    throw new PreferencesShapeError()
  }
  const { displayName, language, theme, fontSize } = body as Record<string, unknown>
  if (
    typeof displayName !== 'string' ||
    !isDisplayLanguage(language) ||
    !isThemeChoice(theme) ||
    !isFontSize(fontSize)
  ) {
    throw new PreferencesShapeError()
  }
  return { displayName, language, theme, fontSize }
}

/** 成功の応答の本文を JSON として読み、形を確かめる。読めなければ PreferencesShapeError を投げる。 */
async function readPreferences(response: Response): Promise<Preferences> {
  let body: unknown
  try {
    body = await response.json()
  } catch {
    throw new PreferencesShapeError()
  }
  return toPreferences(body)
}

/** 自分の氏名と表示の設定を読む（`GET /api/me/preferences`）。 */
export async function getPreferences(): Promise<Preferences> {
  return readPreferences(await apiRequest(ME_PREFERENCES_PATH, { method: 'GET' }))
}

/** 自分の氏名と表示の設定の4つをまとめて保存する（`PUT /api/me/preferences`）。保存した4つを返す。 */
export async function savePreferences(prefs: Preferences): Promise<Preferences> {
  const body: Preferences = {
    displayName: prefs.displayName,
    language: prefs.language,
    theme: prefs.theme,
    fontSize: prefs.fontSize,
  }
  const response = await apiRequest(ME_PREFERENCES_PATH, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  return readPreferences(response)
}

/** 自分のパスワードを変える（`POST /api/me/password`、本文は3つだけ）。204 の本文は読まない。 */
export async function changePassword(input: PasswordChangeInput): Promise<void> {
  const body: PasswordChangeInput = {
    currentPassword: input.currentPassword,
    newPassword: input.newPassword,
    newPasswordConfirmation: input.newPasswordConfirmation,
  }
  await apiRequest(ME_PASSWORD_PATH, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}
