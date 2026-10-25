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
// U7 の骨組みの API の差し替えの口（Intent 261004-role-menu の U7 の L13、performance-design.md 4.1、計画 12節 D-12）。
// page ごとに page.route の口を1つ張り、アプリと同じオリジンの `/api/me/work-role`・`/api/me/navigation`・
// `/api/me/permissions/` の下の要求を受ける（/api/me そのものと認証の道は受けない）。業務データの API は
// businessDataApiRoute.ts が受ける（置き場の問い合わせの道 `/api/me/table-access` はバックエンドから消えた）。
// - 見本の本文は前もって JSON.stringify した文字列で持ち、口は直列化せずに route.fulfill の body に渡す（測りの中に直列化の
//   時間を入れない。U6 の読み直しの R-01）。
// - 状態を1つ持つ: PUT /api/me/work-role を受けて 204 を返したら、その後の GET /api/me/work-role・GET /api/me/navigation には
//   切り替えの後の見本を返す。reset() で切り替えの前に戻す（測りの1回ごとに呼ぶ。NFR 設計の読み直し R-09）。
// - 見本の無い GET は本物へ通さず打ち切る（通信の失敗）。GET 以外で見本の無いものは、メソッドと道だけを記録して打ち切る。
// - 記録と失敗の知らせには、メソッドと道（問い合わせを含めない）だけを出す。
import { expect, type Page, type Route } from '@playwright/test'

/** 見本の応答（body は JSON.stringify した文字列。abort は通信の失敗） */
export type FrameReply = { status: number; body?: string } | { abort: true }

/** 問い合わせで見本を変えるときの関数 */
export type FrameHandler = (url: URL) => FrameReply

/** 受ける道 */
export const FRAME_API_PATHS = {
  workRole: '/api/me/work-role',
  navigation: '/api/me/navigation',
  permissions: '/api/me/permissions',
} as const

/** 受ける道か（問い合わせを除いたパス） */
export function isFrameApiPath(pathname: string): boolean {
  return (
    pathname === FRAME_API_PATHS.workRole ||
    pathname === FRAME_API_PATHS.navigation ||
    pathname.startsWith(`${FRAME_API_PATHS.permissions}/`)
  )
}

/** 差し替えの口 */
export interface FrameApiRoute {
  /** 口が受けた要求の件数 */
  readonly seen: number
  /** 受けた PUT /api/me/work-role の件数 */
  readonly switchRequests: number
  /** 失敗で返した件数（400 以上の見本・通信の失敗の見本・打ち切り。ブラウザの読み込みの失敗の表示の上限） */
  readonly failedReplies: number
  /** 見本が無く打ち切った要求（メソッドと道） */
  readonly blocked: readonly string[]
  /** 切り替えの後の見本を返しているか */
  readonly switched: boolean
  /** 作業ロールの見本（切り替えの前と後の本文） */
  setWorkRole(before: FrameReply, after?: FrameReply): void
  /** 業務のメニューの見本（切り替えの前と後） */
  setNavigation(before: FrameReply, after?: FrameReply): void
  /** 切り替えの答え（既定は 204） */
  setSwitchReply(reply: FrameReply): void
  /** そのほかの道の見本（method と問い合わせを除いたパス） */
  setReply(method: string, path: string, reply: FrameReply | FrameHandler | undefined): void
  /** 切り替えの前の状態に戻す */
  reset(): void
}

/** 見本の本文を作る（前もって直列化する） */
export function jsonReply(status: number, value: unknown): FrameReply {
  return { status, body: JSON.stringify(value) }
}

/** 差し替えの口を張る（page.goto とログインより前に呼ぶ）。 */
export async function routeFrameApi(page: Page, baseURL: string): Promise<FrameApiRoute> {
  const origin = new URL(baseURL).origin
  const replies = new Map<string, FrameReply | FrameHandler>()
  const blocked: string[] = []
  const state = {
    seen: 0,
    failedReplies: 0,
    switchRequests: 0,
    switched: false,
    workRole: {
      before: undefined as FrameReply | undefined,
      after: undefined as FrameReply | undefined,
    },
    navigation: {
      before: undefined as FrameReply | undefined,
      after: undefined as FrameReply | undefined,
    },
    switchReply: { status: 204 } as FrameReply,
  }

  const fulfill = async (route: Route, reply: FrameReply): Promise<void> => {
    if ('abort' in reply) {
      state.failedReplies += 1
      await route.abort('failed')
      return
    }
    if (reply.status >= 400) {
      state.failedReplies += 1
    }
    if (reply.body === undefined) {
      await route.fulfill({ status: reply.status })
      return
    }
    const contentType = reply.status >= 400 ? 'application/problem+json' : 'application/json'
    await route.fulfill({ status: reply.status, contentType, body: reply.body })
  }

  const pick = (pair: { before?: FrameReply; after?: FrameReply }): FrameReply | undefined =>
    state.switched ? (pair.after ?? pair.before) : pair.before

  await page.route(
    (url) => url.origin === origin && isFrameApiPath(url.pathname),
    async (route) => {
      state.seen += 1
      const request = route.request()
      const method = request.method()
      const url = new URL(request.url())
      let reply: FrameReply | undefined
      if (url.pathname === FRAME_API_PATHS.workRole && method === 'PUT') {
        state.switchRequests += 1
        reply = state.switchReply
        if (!('abort' in reply) && reply.status === 204) {
          state.switched = true
        }
      } else if (url.pathname === FRAME_API_PATHS.workRole && method === 'GET') {
        reply = pick(state.workRole)
      } else if (url.pathname === FRAME_API_PATHS.navigation && method === 'GET') {
        reply = pick(state.navigation)
      } else {
        const found = replies.get(`${method} ${url.pathname}`)
        reply = typeof found === 'function' ? found(url) : found
      }
      if (reply !== undefined) {
        await fulfill(route, reply)
        return
      }
      if (method !== 'GET') {
        blocked.push(`${method} ${url.pathname}`)
        state.failedReplies += 1
        await route.abort('blockedbyclient')
        return
      }
      state.failedReplies += 1
      await route.abort('failed')
    },
  )

  return {
    get seen() {
      return state.seen
    },
    get failedReplies() {
      return state.failedReplies
    },
    get switchRequests() {
      return state.switchRequests
    },
    get blocked() {
      return blocked
    },
    get switched() {
      return state.switched
    },
    setWorkRole(before, after) {
      state.workRole = { before, after }
    },
    setNavigation(before, after) {
      state.navigation = { before, after }
    },
    setSwitchReply(reply) {
      state.switchReply = reply
    },
    setReply(method, path, reply) {
      const key = `${method} ${path}`
      if (reply === undefined) {
        replies.delete(key)
      } else {
        replies.set(key, reply)
      }
    },
    reset() {
      state.switched = false
    },
  }
}

/** 口が働いていて（受けた件数 1 以上）、見本の無い書き込みの打ち切りが 0 件であることを確かめる。 */
export function expectFrameRouteClean(route: FrameApiRoute, label: string): void {
  expect(route.seen, `${label}: 差し替えの口が受けた要求の件数`).toBeGreaterThanOrEqual(1)
  expect(route.blocked, `${label}: 見本の無い書き込みの要求`).toEqual([])
}
