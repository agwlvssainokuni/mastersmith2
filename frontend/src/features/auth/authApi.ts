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
// 認証の API の呼び出し（WF2、WF4、WF5）。ApiClient を通して同じオリジンの API を呼ぶ。
// リフレッシュトークンは Cookie でやり取りするため、画面では扱わない（BR8.3）。
import { apiRequest } from '../../shared/api-client/apiClient'

/** ログイン中の利用者（CurrentUserView） */
export interface CurrentUser {
  email: string
  admin: boolean
}

/** ログインと更新の応答 */
export interface TokenResult {
  accessToken: string
  expiresAt: string
  user: CurrentUser
}

/** ログインの API のパス */
export const LOGIN_PATH = '/api/auth/login'

/** トークンの更新の API のパス */
export const REFRESH_PATH = '/api/auth/session/refresh'

/** ログアウトの API のパス */
export const LOGOUT_PATH = '/api/auth/session/logout'

/** メールアドレスとパスワードでログインする。失敗は ApiError を投げる。 */
export async function requestLogin(email: string, password: string): Promise<TokenResult> {
  const response = await apiRequest(LOGIN_PATH, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email, password }),
  })
  return (await response.json()) as TokenResult
}

/** Cookie のリフレッシュトークンで更新する。失敗は ApiError を投げる。 */
export async function requestRefresh(): Promise<TokenResult> {
  const response = await apiRequest(REFRESH_PATH, { method: 'POST' })
  return (await response.json()) as TokenResult
}

/** ログアウトする。失敗は ApiError を投げる。 */
export async function requestLogout(): Promise<void> {
  await apiRequest(LOGOUT_PATH, { method: 'POST' })
}
