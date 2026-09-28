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
// プリファレンスとパスワードの変更の画面のテストの値と、fetch の差し替えの組み立て（テストからだけ使う。
// unit-test-instructions.md の 5節・6節）。メールアドレスは予約されたドメイン（example.test）の下の値、氏名は架空の値だけを使う。
// 偽のサーバーは U6 の testing/fixtures.ts と同じ形（機能どうしで読み込まないため、ここに持つ）。
import { vi } from 'vitest'
import type { Preferences } from '../preferencesApi'

/** 応答の detail に入れる目印（画面に出ないことを見る） */
export const DETAIL_MARKER = 'server-detail-marker'

/** 初期値の形の利用者（氏名はメールアドレス、ja・system・md。AC4.1.10） */
export const INITIAL_PREFERENCES: Preferences = {
  displayName: 'preferences-leak-check@example.test',
  language: 'ja',
  theme: 'system',
  fontSize: 'md',
}

/** 名前を付けた利用者の見本 */
export const NAMED_PREFERENCES: Preferences = {
  displayName: '検査 太郎',
  language: 'ja',
  theme: 'light',
  fontSize: 'md',
}

/** トークンの更新（ログイン状態の復元）の API のパス */
export const REFRESH_PATH = '/api/auth/session/refresh'

/** ログアウトの API のパス */
export const LOGOUT_PATH = '/api/auth/session/logout'

/** 送った要求の記録 */
export interface RecordedRequest {
  path: string
  method: string
  headers: Headers
  /** JSON として読めた本文（読めなければ undefined） */
  body: unknown
}

/** 1つの要求への答え（Error は通信の失敗として拒否する） */
export type FakeAnswer = Response | Error | Promise<Response>

/** JSON の応答 */
export function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

/** Problem Details の応答（detail に目印を入れる） */
export function problemResponse(
  status: number,
  code?: string,
  extra: Record<string, unknown> = {},
): Response {
  return new Response(
    JSON.stringify({ status, code, detail: DETAIL_MARKER, title: DETAIL_MARKER, ...extra }),
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

/** 400 VALIDATION_FAILED と項目ごとの誤り */
export function validationFailed(fieldErrors?: unknown): Response {
  return problemResponse(400, 'VALIDATION_FAILED', fieldErrors === undefined ? {} : { fieldErrors })
}

/** 本文の無い 204 */
export function noContent(): Response {
  return new Response(null, { status: 204 })
}

/** トークンの更新の成功の応答（ログインした利用者と、その表示の設定。契約 C3） */
export function sessionResponse(prefs: Preferences, admin = false): Response {
  return jsonResponse(200, {
    accessToken: 'access-token-marker',
    expiresAt: '2099-01-01T00:00:00Z',
    user: { email: 'preferences-leak-check@example.test', admin, ...prefs },
  })
}

/** 答えを後で決められる約束 */
export interface Deferred {
  promise: Promise<Response>
  resolve: (response: Response) => void
  reject: (error: Error) => void
}

/** 答えを後で決められる約束を作る（読み込み中・送信中の表示を見るため）。 */
export function deferred(): Deferred {
  let resolve: (response: Response) => void = () => undefined
  let reject: (error: Error) => void = () => undefined
  const promise = new Promise<Response>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

/** 偽のサーバー */
export interface FakeServer {
  /** 送られた要求（送った順） */
  requests: RecordedRequest[]
  /** パスとメソッドごとの要求 */
  requestsTo: (path: string, method?: string) => RecordedRequest[]
  /** パス（とメソッド）への次の答えを足す（最後の1つは繰り返し使う） */
  answer: (key: string, ...answers: FakeAnswer[]) => void
}

function readBody(init: RequestInit | undefined): unknown {
  if (typeof init?.body !== 'string') {
    return undefined
  }
  try {
    return JSON.parse(init.body) as unknown
  } catch {
    return undefined
  }
}

/**
 * fetch を差し替え、`"<METHOD> <path>"` か `"<path>"` ごとに決めた答えを返す（vi.unstubAllGlobals で戻る）。
 * メソッドつきの鍵を先に見る。答えの無い要求は 404 を返す。
 */
export function installFakeServer(initial: Record<string, FakeAnswer[]> = {}): FakeServer {
  const queues = new Map<string, FakeAnswer[]>()
  const requests: RecordedRequest[] = []
  const answer = (key: string, ...answers: FakeAnswer[]) => {
    queues.set(key, [...(queues.get(key) ?? []), ...answers])
  }
  for (const [key, answers] of Object.entries(initial)) {
    answer(key, ...answers)
  }
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path =
      typeof input === 'string' ? input : input instanceof URL ? input.pathname : input.url
    const method = init?.method ?? 'GET'
    requests.push({ path, method, headers: new Headers(init?.headers), body: readBody(init) })
    const key = queues.has(`${method} ${path}`) ? `${method} ${path}` : path
    const queue = queues.get(key) ?? []
    const next = queue.length > 1 ? queue.shift() : queue[0]
    if (next === undefined) {
      return Promise.resolve(problemResponse(404, 'NOT_FOUND'))
    }
    if (next instanceof Error) {
      return Promise.reject(next)
    }
    // 同じ Response の本文は1回しか読めないため、繰り返し使うときは複製を返す。
    return Promise.resolve(next).then((response) => response.clone())
  })
  vi.stubGlobal('fetch', fetchMock)
  return {
    requests,
    requestsTo: (path, method) =>
      requests.filter(
        (request) => request.path === path && (method === undefined || request.method === method),
      ),
    answer,
  }
}
