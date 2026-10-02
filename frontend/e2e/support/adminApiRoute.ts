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
// 120 の差し替えの口（Intent 260930-user-admin の U5、security-design.md 4節、NFR3.5）。page ごとに page.route の口を
// 1つだけ張り、アプリと同じオリジンで、パスを正規化（続くスラッシュを1つにまとめ、% の符号を一度だけ戻す）した後に
// /api/admin ちょうどか /api/admin/ の下に当たる要求をすべて受ける（context.route は使わない）。
// - 見本があれば見本で返す。測りの前半（measure-first）だけ GET を本物へ通す。見本の無い GET は本物へ通さず、決まった失敗
//   （通信の失敗の見本 = 打ち切り）で返す。GET 以外で見本の無いものは、メソッドと道の型だけを記録して打ち切る。
// - 各テストの終わりに expectNoBlockedWrites で、口が受けた件数が 1 以上（口が働いている）と、打ち切りの記録が 0 件を確かめる。
// - 口は page.goto とログインより前に張る（ログインの /api/auth/ の下は道の形に当たらず、口を素通りして本物へ通る）。
// - 記録と失敗の知らせには、メソッドと道の型（利用者 ID は {id}、問い合わせは載せない）だけを出す。
// - Playwright の request の口（APIRequestContext）の要求は page.route に乗らないため、120 はそれで /api/admin/ の下へ送らない。
import { expect, type Page, type Route } from '@playwright/test'

/** 口のモード */
export type AdminRouteMode = 'inspect' | 'measure-first' | 'measure-second'

/** 見本の応答（abort は通信の失敗の見本） */
export type MockReply = { status: number; json?: unknown } | { abort: true }

/** 見本を返す関数（問い合わせで返す見本を変えるとき） */
export type MockHandler = (url: URL) => MockReply

/** 打ち切った要求の記録 */
export interface BlockedRequest {
  method: string
  path: string
}

/** 差し替えの口 */
export interface AdminApiRoute {
  /** 口が受けた管理の API の要求の件数 */
  readonly seen: number
  /** 打ち切った GET 以外の要求 */
  readonly blocked: readonly BlockedRequest[]
  /** 失敗で返した件数（400 以上の見本・通信の失敗の見本・打ち切り。ブラウザの読み込みの失敗の表示の上限） */
  readonly failedReplies: number
  /** モードを切り替える */
  setMode(mode: AdminRouteMode): void
  /** 見本を置く（handler が undefined なら外す）。templatePath は道の型（利用者 ID は {id}） */
  setMock(method: string, templatePath: string, handler: MockReply | MockHandler | undefined): void
  /** すべての見本を外す */
  clearMocks(): void
}

/** パスを正規化する（続くスラッシュを1つにまとめ、% の符号を一度だけ戻す）。 */
export function normalizeAdminPath(pathname: string): string {
  const collapsed = pathname.replace(/\/{2,}/g, '/')
  let decoded = collapsed
  try {
    decoded = decodeURIComponent(collapsed)
  } catch {
    decoded = collapsed
  }
  return decoded.replace(/\/{2,}/g, '/')
}

/** 管理の API の道の形か（正規化した後のパス） */
export function isAdminApiPath(normalized: string): boolean {
  return normalized === '/api/admin' || normalized.startsWith('/api/admin/')
}

/** 道の型（数字だけの部分を {id} にする。問い合わせは含めない） */
export function templatePath(pathname: string): string {
  return normalizeAdminPath(pathname)
    .split('/')
    .map((part) => (/^\d+$/.test(part) ? '{id}' : part))
    .join('/')
}

/** 差し替えの口を張る（page.goto とログインより前に呼ぶ）。 */
export async function routeAdminApi(
  page: Page,
  baseURL: string,
  mode: AdminRouteMode,
): Promise<AdminApiRoute> {
  const origin = new URL(baseURL).origin
  const mocks = new Map<string, MockReply | MockHandler>()
  const blocked: BlockedRequest[] = []
  const state = { mode, seen: 0, failedReplies: 0 }

  const reply = async (route: Route, answer: MockReply): Promise<void> => {
    if ('abort' in answer) {
      state.failedReplies += 1
      await route.abort('failed')
      return
    }
    if (answer.status >= 400) {
      state.failedReplies += 1
    }
    if (answer.json === undefined) {
      await route.fulfill({ status: answer.status })
      return
    }
    await route.fulfill({
      status: answer.status,
      contentType: answer.status >= 400 ? 'application/problem+json' : 'application/json',
      body: JSON.stringify(answer.json),
    })
  }

  await page.route(
    (url) => url.origin === origin && isAdminApiPath(normalizeAdminPath(url.pathname)),
    async (route) => {
      state.seen += 1
      const request = route.request()
      const method = request.method()
      const url = new URL(request.url())
      const key = `${method} ${templatePath(url.pathname)}`
      const mock = mocks.get(key)
      if (mock !== undefined) {
        await reply(route, typeof mock === 'function' ? mock(url) : mock)
        return
      }
      if (method === 'GET' && state.mode === 'measure-first') {
        await route.continue()
        return
      }
      if (method === 'GET') {
        await reply(route, { abort: true })
        return
      }
      blocked.push({ method, path: templatePath(url.pathname) })
      state.failedReplies += 1
      await route.abort('blockedbyclient')
    },
  )

  return {
    get seen() {
      return state.seen
    },
    get blocked() {
      return blocked
    },
    get failedReplies() {
      return state.failedReplies
    },
    setMode(next) {
      state.mode = next
    },
    setMock(method, path, handler) {
      const key = `${method} ${path}`
      if (handler === undefined) {
        mocks.delete(key)
      } else {
        mocks.set(key, handler)
      }
    },
    clearMocks() {
      mocks.clear()
    },
  }
}

/** 口が働いていて（受けた件数 1 以上）、GET 以外の打ち切りの記録が 0 件であることを確かめる。 */
export function expectNoBlockedWrites(route: AdminApiRoute, label: string): void {
  expect(
    route.seen,
    `${label}: 差し替えの口が受けた管理の API の要求の件数`,
  ).toBeGreaterThanOrEqual(1)
  expect(
    route.blocked.map((entry) => `${entry.method} ${entry.path}`),
    `${label}: 打ち切った GET 以外の管理の API の要求`,
  ).toEqual([])
}
