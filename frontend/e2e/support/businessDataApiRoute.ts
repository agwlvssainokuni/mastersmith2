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
// 業務データの API の差し替えの口（Intent 261016-business-data-views の U3 の L10、NFR 要件 Q2: A、
// logical-components.md の L10）。170・185・160 で、アプリと同じオリジンの POST・PATCH の `/api/tables/*` を受ける。
// - 見本は型を付けた1つの見本（businessDataFixtures.ts）。本文は前もって直列化した文字列で返す（測りの中に直列化の時間を
//   入れない。frameApiRoute.ts と同じ形）。
// - 要求の種類（定義・一覧・詳細・作成・更新・削除）は道とメソッドで決め、答えは種類と本文で決める関数で返す。
// - 記録と失敗の知らせには、メソッドと道だけを出す（本文の値・主キー・スキーマ名・テーブル名を出さない）。
// - 答えの無い要求は本物へ通さず打ち切る（通信の失敗）。
import { expect, type Page } from '@playwright/test'
import type { RowPage, TableDefinition } from '../../src/features/tables/businessDataTypes'
import type { FrameReply } from './frameApiRoute'
import { accessDeniedProblem } from './navTreeFixtures'

/** 業務データの API の道（backend の BusinessDataPaths） */
export const BUSINESS_DATA_API = {
  definition: '/api/tables/definition',
  search: '/api/tables/rows/search',
  get: '/api/tables/rows/get',
  rows: '/api/tables/rows',
  delete: '/api/tables/rows/delete',
} as const

/** 要求の種類 */
export type BusinessDataCall = 'definition' | 'search' | 'get' | 'create' | 'update' | 'delete'

/** 業務データの API の道か（問い合わせを除いたパス） */
export function isBusinessDataApiPath(pathname: string): boolean {
  return pathname.startsWith('/api/tables/')
}

/** 道とメソッドから要求の種類を決める（当たらなければ null） */
export function classifyBusinessDataRequest(
  method: string,
  pathname: string,
): BusinessDataCall | null {
  if (method === 'POST') {
    switch (pathname) {
      case BUSINESS_DATA_API.definition:
        return 'definition'
      case BUSINESS_DATA_API.search:
        return 'search'
      case BUSINESS_DATA_API.get:
        return 'get'
      case BUSINESS_DATA_API.rows:
        return 'create'
      case BUSINESS_DATA_API.delete:
        return 'delete'
      default:
        return null
    }
  }
  if (method === 'PATCH' && pathname === BUSINESS_DATA_API.rows) {
    return 'update'
  }
  return null
}

/** 答えを決める関数（答えが無ければ undefined。打ち切る） */
export type BusinessDataResponder = (
  call: BusinessDataCall,
  body: unknown,
) => FrameReply | undefined

/** 差し替えの口 */
export interface BusinessDataApiRoute {
  /** 口が受けた要求の件数 */
  readonly seen: number
  /** 受けた要求（メソッドと道） */
  readonly calls: readonly string[]
  /** 答えが無く打ち切った要求（メソッドと道） */
  readonly blocked: readonly string[]
  /** 失敗で返した件数（400 以上の見本・打ち切り） */
  readonly failedReplies: number
  /** 答えの関数を替える */
  setResponder(responder: BusinessDataResponder): void
  /** 一覧の要求の答えを止める（読み込み中の状態を見るため）。戻り値は止めた答えを返す関数 */
  holdSearch(): () => void
}

/** 本文から schema・table を読む（形が違えば空の文字） */
export function requestedTable(body: unknown): { schema: string; table: string } {
  const value = typeof body === 'object' && body !== null ? (body as Record<string, unknown>) : {}
  return {
    schema: typeof value.schema === 'string' ? value.schema : '',
    table: typeof value.table === 'string' ? value.table : '',
  }
}

/** 本文から page・size を読む（形が違えば 1・20） */
export function requestedPage(body: unknown): { page: number; size: 20 | 50 | 100 } {
  const value = typeof body === 'object' && body !== null ? (body as Record<string, unknown>) : {}
  const page = typeof value.page === 'number' && value.page >= 1 ? value.page : 1
  const size = value.size === 50 || value.size === 100 ? value.size : 20
  return { page, size }
}

/**
 * 1つのテーブルの見本を答える関数を作る。ほかのテーブルの定義の要求には 403 ACCESS_DENIED を返す。
 * 本文は前もって直列化して覚えておく（同じ page・size の2回目からは直列化しない）。
 *
 * @param definition 見本の定義
 * @param page 一覧の見本を作る関数
 * @param detail 詳細の見本を作る関数（無ければ詳細の要求は打ち切る）
 */
export function tableResponder(
  definition: TableDefinition,
  page: (pageNumber: number, size: 20 | 50 | 100) => RowPage,
  detail?: (key: unknown) => unknown,
): BusinessDataResponder {
  const definitionBody = JSON.stringify(definition)
  const forbiddenBody = JSON.stringify(accessDeniedProblem())
  const pages = new Map<string, string>()
  return (call, body) => {
    const target = requestedTable(body)
    if (target.schema !== definition.schema || target.table !== definition.table) {
      return { status: 403, body: forbiddenBody }
    }
    switch (call) {
      case 'definition':
        return { status: 200, body: definitionBody }
      case 'search': {
        const { page: number, size } = requestedPage(body)
        const key = `${number}/${size}`
        let cached = pages.get(key)
        if (cached === undefined) {
          cached = JSON.stringify(page(number, size))
          pages.set(key, cached)
        }
        return { status: 200, body: cached }
      }
      case 'get':
        return detail === undefined
          ? undefined
          : { status: 200, body: JSON.stringify(detail((body as { key?: unknown } | null)?.key)) }
      default:
        return undefined
    }
  }
}

/** 差し替えの口を張る（page.goto とログインより前に呼ぶ）。 */
export async function routeBusinessDataApi(
  page: Page,
  baseURL: string,
  initial: BusinessDataResponder,
): Promise<BusinessDataApiRoute> {
  const origin = new URL(baseURL).origin
  const state = { seen: 0, failedReplies: 0, responder: initial }
  let gate: Promise<void> | null = null
  const calls: string[] = []
  const blocked: string[] = []
  await page.route(
    (url) => url.origin === origin && isBusinessDataApiPath(url.pathname),
    async (route) => {
      state.seen += 1
      const request = route.request()
      const method = request.method()
      const pathname = new URL(request.url()).pathname
      calls.push(`${method} ${pathname}`)
      const call = classifyBusinessDataRequest(method, pathname)
      let body: unknown = null
      try {
        body = request.postDataJSON()
      } catch {
        body = null
      }
      if (call === 'search' && gate !== null) {
        await gate
      }
      const reply = call === null ? undefined : state.responder(call, body)
      if (reply === undefined || 'abort' in reply) {
        blocked.push(`${method} ${pathname}`)
        state.failedReplies += 1
        await route.abort('failed')
        return
      }
      if (reply.status >= 400) {
        state.failedReplies += 1
      }
      const contentType = reply.status >= 400 ? 'application/problem+json' : 'application/json'
      await route.fulfill({ status: reply.status, contentType, body: reply.body ?? '' })
    },
  )
  return {
    get seen() {
      return state.seen
    },
    get calls() {
      return calls
    },
    get blocked() {
      return blocked
    },
    get failedReplies() {
      return state.failedReplies
    },
    setResponder(responder) {
      state.responder = responder
    },
    holdSearch() {
      let release: () => void = () => {}
      gate = new Promise<void>((resolve) => {
        release = () => {
          gate = null
          resolve()
        }
      })
      return release
    },
  }
}

/** 口が働いていて（受けた件数 1 以上）、答えの無い要求が 0 件であることを確かめる。 */
export function expectBusinessDataRouteClean(route: BusinessDataApiRoute, label: string): void {
  expect(route.seen, `${label}: 業務データの差し替えの口が受けた要求の件数`).toBeGreaterThanOrEqual(
    1,
  )
  expect(route.blocked, `${label}: 答えの無い業務データの要求`).toEqual([])
}
