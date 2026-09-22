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
// API 呼び出しの共通部分（WF8、BR8.5、ADR-007）。AuthUi に依存せず、登録されたトークンの取得と更新の手段を使う。
// - 同じオリジンの API を呼び、アクセストークンがあれば Authorization: Bearer を付ける。
// - 401 / AUTHENTICATION_REQUIRED を受けたら更新を1回だけ行い（同時の 401 は1つの更新にまとめる）、
//   成功したら元の要求を1回だけ送り直す。送り直しでまた 401、または更新の失敗なら onUnauthenticated を呼ぶ。
// - 認証の API（ログイン・更新・ログアウト）は、トークンの付与も更新と送り直しも行わない。
import { networkError, readErrorCode, type ApiError } from './apiError'

/** ApiClient が使う、ログイン状態の側の手段 */
export interface AuthHandlers {
  /** 今のアクセストークン（無ければ null） */
  getAccessToken: () => string | null
  /** トークンの更新を1回行う。成功したら true */
  refresh: () => Promise<boolean>
  /** 未ログインになったことの知らせ */
  onUnauthenticated: () => void
}

/** 更新と送り直しの対象外にする認証の API のパス（BR8.5） */
export const AUTH_API_PATHS = [
  '/api/auth/login',
  '/api/auth/session/refresh',
  '/api/auth/session/logout',
] as const

/** 更新を促す 401 の code */
export const AUTHENTICATION_REQUIRED = 'AUTHENTICATION_REQUIRED'

let handlers: AuthHandlers | null = null
let pendingRefresh: Promise<boolean> | null = null

/** ログイン状態の側の手段を登録する（画面の起動時に1回）。 */
export function registerAuthHandlers(next: AuthHandlers): void {
  handlers = next
}

/** 登録と更新中の状態を消す（テストで使う）。 */
export function resetApiClient(): void {
  handlers = null
  pendingRefresh = null
}

/** 認証の API かどうか（問い合わせの部分は見ない）。 */
export function isAuthApiPath(path: string): boolean {
  const withoutQuery = path.split('?')[0]
  return AUTH_API_PATHS.some((authPath) => authPath === withoutQuery)
}

/** 同時の 401 をまとめて、更新を1回だけ行う。 */
async function refreshOnce(): Promise<boolean> {
  if (!handlers) {
    return false
  }
  pendingRefresh ??= handlers.refresh().finally(() => {
    pendingRefresh = null
  })
  return pendingRefresh
}

/** 要求を送る（アクセストークンがあれば付ける）。 */
async function send(path: string, init: RequestInit, withToken: boolean): Promise<Response> {
  const headers = new Headers(init.headers)
  const token = withToken ? handlers?.getAccessToken() : null
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  return fetch(path, { ...init, credentials: 'same-origin', headers })
}

/** 更新が必要な 401 かどうか。 */
async function needsRefresh(response: Response): Promise<boolean> {
  if (response.status !== 401) {
    return false
  }
  return (await readErrorCode(response)) === AUTHENTICATION_REQUIRED
}

/**
 * 同じオリジンの API を呼ぶ。認証の API 以外では、401 / AUTHENTICATION_REQUIRED のときに更新を1回行い、
 * 成功したら元の要求を1回だけ送り直す。
 */
export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  if (isAuthApiPath(path)) {
    return send(path, init, false)
  }
  const response = await send(path, init, true)
  if (!(await needsRefresh(response))) {
    return response
  }
  const refreshed = await refreshOnce()
  if (!refreshed) {
    handlers?.onUnauthenticated()
    return response
  }
  const retried = await send(path, init, true)
  if (await needsRefresh(retried)) {
    handlers?.onUnauthenticated()
  }
  return retried
}

/** API を呼び、応答が成功でなければ {@link ApiError} を投げる。 */
export async function apiRequest(path: string, init: RequestInit = {}): Promise<Response> {
  let response: Response
  try {
    response = await apiFetch(path, init)
  } catch {
    throw networkError()
  }
  if (!response.ok) {
    const code = await readErrorCode(response)
    const error: ApiError =
      code === undefined
        ? { kind: 'response', status: response.status }
        : { kind: 'response', status: response.status, code }
    throw error
  }
  return response
}
