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
// 登録の完了の API（契約 C6 の確かめと完了）を呼ぶ関数（frontend-components.md の 3節、NFR1.1・NFR9.1、
// security-design.md の 3.1）。パスは ApiClient のトークンを付けないパス（TOKENLESS_API_PATHS）と同じ値で、
// アクセストークン・401 での更新をしない。Accept-Language は ApiClient が画面の言語で付けるため、ここでは付けない。
// トークンは要求の本文だけに入れ、パス・問い合わせ・見出しに入れない（D3）。失敗は ApiClient の ApiError をそのまま投げる。
// 確かめの成功の本文は決まった項目だけの値にし、形が違えば通信の失敗として投げる（loadFailed と同じ扱い）。
// 応答の値・失敗の値をコンソール・ブラウザの保存に出さない。
import type {
  DisplayLanguage,
  FontSize,
  ThemeChoice,
} from '../../app/display-settings/displaySettingsTypes'
import { apiRequest } from '../../shared/api-client/apiClient'
import { networkError } from '../../shared/api-client/apiError'

/** リンクの確かめの API のパス */
export const REGISTRATION_VERIFY_PATH = '/api/registration/verify'

/** 登録の完了の API のパス */
export const REGISTRATION_COMPLETE_PATH = '/api/registration/complete'

/** 確かめの 200 の値（招待のメールアドレスと言語） */
export interface VerifiedInvitation {
  email: string
  language: DisplayLanguage
}

/** 登録の完了の要求の本文（契約 C6 の CompleteRequest の7項目） */
export interface CompleteRegistrationRequest {
  token: string
  displayName: string
  password: string
  passwordConfirmation: string
  language: DisplayLanguage
  theme: ThemeChoice
  fontSize: FontSize
}

/** JSON の本文を送る要求の設定 */
function jsonPost(body: unknown): RequestInit {
  return {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  }
}

/** 確かめの成功の本文を、決まった項目だけの値にする。形が違えば undefined。 */
function toVerifiedInvitation(body: unknown): VerifiedInvitation | undefined {
  if (typeof body !== 'object' || body === null || Array.isArray(body)) {
    return undefined
  }
  const { email, language } = body as Record<string, unknown>
  if (typeof email !== 'string' || (language !== 'ja' && language !== 'en')) {
    return undefined
  }
  return { email, language }
}

/**
 * リンクを確かめる（`POST /api/registration/verify`、本文 `{ token }`）。招待を消費しない。
 * 成功なら招待のメールアドレスと言語を返す。失敗は ApiError を投げる（本文の形の誤りは通信の失敗）。
 */
export async function verifyRegistration(token: string): Promise<VerifiedInvitation> {
  const response = await apiRequest(REGISTRATION_VERIFY_PATH, jsonPost({ token }))
  let body: unknown
  try {
    body = await response.json()
  } catch {
    throw networkError()
  }
  const invitation = toVerifiedInvitation(body)
  if (invitation === undefined) {
    throw networkError()
  }
  return invitation
}

/** 登録を完了する（`POST /api/registration/complete`、本文は7項目だけ）。204 の本文は読まない。失敗は ApiError を投げる。 */
export async function completeRegistration(request: CompleteRegistrationRequest): Promise<void> {
  const body: CompleteRegistrationRequest = {
    token: request.token,
    displayName: request.displayName,
    password: request.password,
    passwordConfirmation: request.passwordConfirmation,
    language: request.language,
    theme: request.theme,
    fontSize: request.fontSize,
  }
  await apiRequest(REGISTRATION_COMPLETE_PATH, jsonPost(body))
}
