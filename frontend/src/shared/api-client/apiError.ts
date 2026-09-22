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
// API のエラー応答を、画面が扱いやすい形（状態コードと code）に変える（U1 の frontend-components.md、BR8.5）。
// 本文が Problem Details でなければ code は持たない。通信の失敗は別の種類にする。

/** エラー応答（サーバーが応答を返した） */
export interface ApiResponseError {
  kind: 'response'
  status: number
  code?: string
}

/** 通信の失敗（応答が返らなかった） */
export interface ApiNetworkError {
  kind: 'network'
}

/** API の呼び出しの失敗 */
export type ApiError = ApiResponseError | ApiNetworkError

/** 通信の失敗を表す値を返す。 */
export function networkError(): ApiNetworkError {
  return { kind: 'network' }
}

/** 応答の本文（Problem Details）から code を読む。読めなければ undefined。 */
export async function readErrorCode(response: Response): Promise<string | undefined> {
  try {
    const body: unknown = await response.clone().json()
    if (typeof body === 'object' && body !== null && 'code' in body) {
      const code = (body as { code: unknown }).code
      return typeof code === 'string' ? code : undefined
    }
    return undefined
  } catch {
    return undefined
  }
}

/** エラー応答を状態コードと code の形にする。 */
export async function toApiError(response: Response): Promise<ApiResponseError> {
  const code = await readErrorCode(response)
  return code === undefined
    ? { kind: 'response', status: response.status }
    : { kind: 'response', status: response.status, code }
}
