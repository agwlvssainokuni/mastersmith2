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
// Problem Details の本文全体（problem）も渡し、呼び出し元が型を決めて追加の項目を読めるようにする
// （DSL の管理画面の BR2.6・BR2.7）。problem は列挙されない項目として足すため、kind・status・code の形は変わらない。

/**
 * Problem Details（RFC 9457）の本文。`type`・`title`・`status`・`detail`・`instance`・`code`・`traceId` に加えて、
 * API ごとの追加の項目（例: `errors`・`total`）を持つことがある。値の形は呼び出し元が確かめてから使う。
 */
export type ProblemDetails = Readonly<Record<string, unknown>>

/** エラー応答（サーバーが応答を返した） */
export interface ApiResponseError {
  kind: 'response'
  status: number
  code?: string
  /** 本文が Problem Details のときだけ持つ（列挙されない項目） */
  readonly problem?: ProblemDetails
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

/** Problem Details の標準の項目（どれか1つがあれば Problem Details とみなす） */
const PROBLEM_MEMBERS: Readonly<Record<string, 'string' | 'number'>> = {
  type: 'string',
  title: 'string',
  status: 'number',
  detail: 'string',
  instance: 'string',
  code: 'string',
}

/** 本文が Problem Details かどうか（配列でないオブジェクトで、標準の項目を正しい型で1つ以上持つ）。 */
function isProblemDetails(body: unknown): body is ProblemDetails {
  if (typeof body !== 'object' || body === null || Array.isArray(body)) {
    return false
  }
  const record = body as Record<string, unknown>
  return Object.entries(PROBLEM_MEMBERS).some(
    ([name, type]) => name in record && typeof record[name] === type,
  )
}

/** 応答の本文を Problem Details として読む。JSON として読めない・Problem Details でなければ undefined。 */
export async function readProblem(response: Response): Promise<ProblemDetails | undefined> {
  try {
    const body: unknown = await response.clone().json()
    return isProblemDetails(body) ? body : undefined
  } catch {
    return undefined
  }
}

/**
 * エラー応答に Problem Details の本文を列挙されない項目として付ける。
 * 列挙されないため、エラーの値の比べ（kind・status・code）や JSON にしたときの形は変わらない。
 */
function attachProblem(error: ApiResponseError, problem: ProblemDetails | undefined): void {
  if (problem !== undefined) {
    Object.defineProperty(error, 'problem', { value: problem, enumerable: false })
  }
}

/** エラー応答を状態コードと code の形にする。本文が Problem Details なら problem も付ける。 */
export async function toApiError(response: Response): Promise<ApiResponseError> {
  const code = await readErrorCode(response)
  const error: ApiResponseError =
    code === undefined
      ? { kind: 'response', status: response.status }
      : { kind: 'response', status: response.status, code }
  attachProblem(error, await readProblem(response))
  return error
}
