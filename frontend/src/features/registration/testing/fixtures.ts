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
// 登録の完了の画面のテストの値と、fetch の差し替えの組み立て（テストからだけ使う。unit-test-instructions.md の 5節・6節）。
// メールアドレスは予約されたドメイン（example.test）の下の値だけ、トークンは見分けやすい低いエントロピーの値を使う。
import { vi } from 'vitest'
import type { VerifiedInvitation } from '../registrationApi'

/** テストのトークン（見分けやすい低いエントロピーの値。どの招待にも当たらない） */
export const TEST_TOKEN = 'registration-test-token-value'

/** 応答の detail に入れる目印（画面に出ないことを見る） */
export const DETAIL_MARKER = 'server-detail-marker'

/** 確かめの 200 の見本（言語 ja の招待） */
export const VERIFIED_JA: VerifiedInvitation = {
  email: 'registration-leak-check@example.test',
  language: 'ja',
}

/** 確かめの 200 の見本（言語 en の招待） */
export const VERIFIED_EN: VerifiedInvitation = {
  email: 'registration-leak-check@example.test',
  language: 'en',
}

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
export function problemResponse(status: number, code?: string, detail = DETAIL_MARKER): Response {
  return new Response(
    JSON.stringify({ status, code, detail, title: detail, type: 'about:blank' }),
    {
      status,
      headers: { 'Content-Type': 'application/problem+json' },
    },
  )
}

/** 本文の無い 204 */
export function noContent(): Response {
  return new Response(null, { status: 204 })
}

/** 答えを後で決められる約束 */
export interface Deferred {
  promise: Promise<Response>
  resolve: (response: Response) => void
  reject: (error: Error) => void
}

/** 答えを後で決められる約束を作る（確かめ中・送信中の表示を見るため）。 */
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
  /** パスごとの要求 */
  requestsTo: (path: string) => RecordedRequest[]
  /** パスへの次の答えを足す（最後の1つは繰り返し使う） */
  answer: (path: string, ...answers: FakeAnswer[]) => void
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
 * fetch を差し替え、パスごとに決めた答えを返す（vi.unstubAllGlobals で戻る）。答えの無いパスは 404 を返す。
 */
export function installFakeServer(initial: Record<string, FakeAnswer[]> = {}): FakeServer {
  const queues = new Map<string, FakeAnswer[]>()
  const requests: RecordedRequest[] = []
  const answer = (path: string, ...answers: FakeAnswer[]) => {
    queues.set(path, [...(queues.get(path) ?? []), ...answers])
  }
  for (const [path, answers] of Object.entries(initial)) {
    answer(path, ...answers)
  }
  const fetchMock = vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
    const path =
      typeof input === 'string' ? input : input instanceof URL ? input.pathname : input.url
    requests.push({
      path,
      method: init?.method ?? 'GET',
      headers: new Headers(init?.headers),
      body: readBody(init),
    })
    const queue = queues.get(path) ?? []
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
    requestsTo: (path) => requests.filter((request) => request.path === path),
    answer,
  }
}
