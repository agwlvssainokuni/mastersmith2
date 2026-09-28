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
// 画面の時間の測定の準備（U5 の NFR 設計 performance-design.md 4.2。Intent 260925-user-management の B5 の共用の手伝い）。
// Playwright の要求の口（APIRequestContext）で、初期管理者のアクセストークンを取り、一覧を読み、招待の API で招待を置く。
// アクセストークンはテストの変数だけに持ち、注記・添付・標準出力に出さない。測定の招待のメールアドレスは実行ごとに重ならない
// `u5-perf-<印>-<番号>@example.com`（印は時刻と乱数）で、題・注記・添付に入れない。招待はメールを Mailpit へ送る。
import { randomBytes } from 'node:crypto'
import { expect, type APIRequestContext } from '@playwright/test'
import { adminEmail, adminPassword } from '../../playwright.config'

/** 招待の管理の API の根 */
const INVITATIONS = '/api/admin/invitations'

/** 実行ごとに重ならない印（時刻と乱数） */
export function newRunTag(): string {
  return `${Date.now().toString(36)}${randomBytes(3).toString('hex')}`
}

/** 初期管理者でログインの API を呼び、アクセストークンを返す。 */
export async function requestAdminAccessToken(request: APIRequestContext): Promise<string> {
  const response = await request.post('/api/auth/login', {
    data: { email: adminEmail, password: adminPassword },
  })
  expect(response.status(), 'ログインの API の状態コード').toBe(200)
  const body = (await response.json()) as { accessToken?: unknown }
  expect(typeof body.accessToken, 'アクセストークンの型').toBe('string')
  return body.accessToken as string
}

/** 招待中の一覧の1ページを読む（本文は型を決めずに返す。呼び出し元が確かめる）。 */
export async function readInvitationList(
  request: APIRequestContext,
  token: string,
  page = 1,
): Promise<unknown> {
  const response = await request.get(`${INVITATIONS}?page=${page}`, {
    headers: { Authorization: `Bearer ${token}` },
  })
  expect(response.status(), '一覧の API の状態コード').toBe(200)
  return response.json()
}

/** 置いた招待の送信の結果の内訳 */
export interface SeedResult {
  created: number
  sent: number
  failed: number
}

/** 招待の API で count 件の招待を置く（言語は ja）。どれも 201 であることを確かめる。 */
export async function seedInvitations(
  request: APIRequestContext,
  token: string,
  count: number,
  runTag: string,
): Promise<SeedResult> {
  const result: SeedResult = { created: 0, sent: 0, failed: 0 }
  for (let index = 1; index <= count; index += 1) {
    const response = await request.post(INVITATIONS, {
      headers: { Authorization: `Bearer ${token}` },
      data: {
        email: `u5-perf-${runTag}-${String(index).padStart(2, '0')}@example.com`,
        language: 'ja',
      },
    })
    expect(response.status(), `招待 ${index} 件目の状態コード`).toBe(201)
    const body = (await response.json()) as { sendResult?: unknown }
    result.created += 1
    if (body.sendResult === 'SENT') {
      result.sent += 1
    } else {
      result.failed += 1
    }
  }
  return result
}
