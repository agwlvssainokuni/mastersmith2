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
// API 呼び出しの共通部分（WF8、BR8.5、ADR-005、ADR-007）。AuthUi・AppFrame に依存せず、登録された手段を使う。
// - 同じオリジンの API を呼び、アクセストークンがあれば Authorization: Bearer を付ける。
// - 401 / AUTHENTICATION_REQUIRED を受けたら更新を1回だけ行い（同時の 401 は1つの更新にまとめる）、
//   成功したら元の要求を1回だけ送り直す。送り直しでまた 401、または更新の失敗なら onUnauthenticated を呼ぶ。
// - トークンを付けないパス（認証の API と公開の API。TOKENLESS_API_PATHS）は、トークンの付与も更新と送り直しも
//   行わない（D10）。トークンを付けないかを決めるのは、この一覧と判定の関数の1か所だけ。
// - すべての要求に、登録された関数が返す画面の言語を Accept-Language として付ける（D9、ADR-005）。
//   呼び出し側が明示した Accept-Language は上書きしない。関数が無ければ付けない。
// - ファイルの受け取り（apiDownload）も同じ道を通し、本文のバイト列と Content-Disposition を返す（DSL の管理画面の BR2.4）。
import { networkError, readErrorCode, toApiError, type ApiError } from './apiError'

/** ApiClient が使う、ログイン状態の側の手段 */
export interface AuthHandlers {
  /** 今のアクセストークン（無ければ null） */
  getAccessToken: () => string | null
  /** トークンの更新を1回行う。成功したら true */
  refresh: () => Promise<boolean>
  /** 未ログインになったことの知らせ */
  onUnauthenticated: () => void
}

/**
 * トークンを付けず、401 でも更新と送り直しをしない API のパス（D10、NFR9.1）。
 * 判定は問い合わせの部分を除いた完全な一致で、前方一致・大文字小文字・末尾の `/` の揺れは直さない。
 */
export const TOKENLESS_API_PATHS = [
  // 認証の API（BR8.5）
  '/api/auth/login',
  '/api/auth/session/refresh',
  '/api/auth/session/logout',
  // 公開の API（契約 C7・C6）
  '/api/appearance',
  '/api/registration/verify',
  '/api/registration/complete',
] as const

/** Accept-Language として付けてよい言語（ADR-005、NFR9.2） */
const ACCEPTED_LANGUAGES: readonly string[] = ['ja', 'en']

/** 画面の言語を返す関数 */
export type LanguageResolver = () => 'ja' | 'en'

/** 更新を促す 401 の code */
export const AUTHENTICATION_REQUIRED = 'AUTHENTICATION_REQUIRED'

let handlers: AuthHandlers | null = null
let pendingRefresh: Promise<boolean> | null = null
let languageResolver: LanguageResolver | null = null

/** ログイン状態の側の手段を登録する（画面の起動時に1回）。 */
export function registerAuthHandlers(next: AuthHandlers): void {
  handlers = next
}

/** 画面の言語を返す関数を登録する（AppFrame が画面を開いたときに登録する。W12）。 */
export function registerLanguageResolver(resolver: LanguageResolver): void {
  languageResolver = resolver
}

/** 登録と更新中の状態を消す（テストで使う）。 */
export function resetApiClient(): void {
  handlers = null
  pendingRefresh = null
  languageResolver = null
}

/** トークンを付けないパスかどうか（問い合わせの部分を除いた完全な一致）。 */
export function isTokenlessApiPath(path: string): boolean {
  const withoutQuery = path.split('?')[0]
  return TOKENLESS_API_PATHS.some((tokenlessPath) => tokenlessPath === withoutQuery)
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

/** 要求を送る（アクセストークンがあれば付ける。画面の言語を Accept-Language として付ける）。 */
async function send(path: string, init: RequestInit, withToken: boolean): Promise<Response> {
  const headers = new Headers(init.headers)
  const token = withToken ? handlers?.getAccessToken() : null
  if (token) {
    headers.set('Authorization', `Bearer ${token}`)
  }
  const language = languageResolver?.()
  if (!headers.has('Accept-Language') && language && ACCEPTED_LANGUAGES.includes(language)) {
    headers.set('Accept-Language', language)
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
 * 同じオリジンの API を呼ぶ。トークンを付けないパス以外では、401 / AUTHENTICATION_REQUIRED のときに更新を1回行い、
 * 成功したら元の要求を1回だけ送り直す。
 */
export async function apiFetch(path: string, init: RequestInit = {}): Promise<Response> {
  if (isTokenlessApiPath(path)) {
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
    const error: ApiError = await toApiError(response)
    throw error
  }
  return response
}

/** ダウンロードで受け取った本文と、ファイル名の指定 */
export interface ApiDownload {
  /** 本文のバイト列 */
  blob: Blob
  /** 応答の `Content-Disposition`（無ければ null） */
  contentDisposition: string | null
}

/**
 * API からファイルを受け取る。アクセストークンの付与と 401 での更新は {@link apiRequest} と同じ。
 * 応答が成功でなければ {@link ApiError} を投げる。トークンは URL に入れない。
 */
export async function apiDownload(path: string, init: RequestInit = {}): Promise<ApiDownload> {
  const response = await apiRequest(path, init)
  let blob: Blob
  try {
    blob = await response.blob()
  } catch {
    throw networkError()
  }
  return { blob, contentDisposition: response.headers.get('Content-Disposition') }
}
