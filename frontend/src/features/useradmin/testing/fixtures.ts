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
// テストの値（契約 C3 の形）を組み立てる（テストからだけ使う。画面からは読み込まない）。メールアドレスは予約されたドメイン
// （example.com）の下の値だけ、氏名は架空の2語の値と、記号（< > & " '）を含む値を使う。日時は ISO 8601 の UTC の固定の値で、
// ロックしているかは応答の locked で決める（画面の時計で比べない）。E2E の 110 が作る値の形（u7-perf-・計測 花子）は使わない。
import type { AdminUser, AdminUserPage } from '../api/types'

/** 利用者の1行（既定は管理者でない有効な利用者） */
export function userOf(overrides: Partial<AdminUser> = {}): AdminUser {
  return {
    userId: 1,
    email: 'yamada.taro@example.com',
    displayName: '山田 太郎',
    language: 'ja',
    admin: false,
    suspended: false,
    locked: false,
    resettable: false,
    registeredAt: '2026-09-25T03:00:00Z',
    self: false,
    ...overrides,
  }
}

/** 自分の行（管理者） */
export function selfRow(overrides: Partial<AdminUser> = {}): AdminUser {
  return userOf({
    userId: 10,
    email: 'kanri.ichiro@example.com',
    displayName: '管理 一郎',
    admin: true,
    self: true,
    ...overrides,
  })
}

/** 長いメールアドレス（折り返しの確かめ） */
export const LONG_EMAIL = `${'very.long.mail.address.for.wrapping.check'.repeat(3)}@example.com`

/** 記号を含む氏名（HTML として解釈されないことの確かめ） */
export const SYMBOL_NAME = '<b>記号</b> & "引用" \'名\''

/** 見本の5行（自分の行・ほかの管理者・ロック中・利用停止・記号の氏名と長いメールアドレス） */
export function sampleRows(): AdminUser[] {
  return [
    selfRow(),
    userOf({
      userId: 11,
      email: 'sato.hanako@example.com',
      displayName: '佐藤 花子',
      language: 'en',
      admin: true,
    }),
    userOf({
      userId: 12,
      email: 'suzuki.jiro@example.com',
      displayName: '鈴木 次郎',
      locked: true,
      lockedUntil: '2026-10-03T01:30:00Z',
      resettable: true,
    }),
    userOf({
      userId: 13,
      email: 'takahashi.saburo@example.com',
      displayName: '高橋 三郎',
      suspended: true,
      resettable: true,
    }),
    userOf({ userId: 14, email: LONG_EMAIL, displayName: SYMBOL_NAME }),
  ]
}

/** n 件の行（userId は start から） */
export function rowsOf(count: number, start = 1): AdminUser[] {
  return Array.from({ length: count }, (_, index) =>
    userOf({
      userId: start + index,
      email: `user-${String(start + index).padStart(2, '0')}@example.com`,
      displayName: `利用 ${String(start + index).padStart(2, '0')}`,
    }),
  )
}

/** 一覧の1ページ */
export function pageOf(overrides: Partial<AdminUserPage> = {}): AdminUserPage {
  const items = overrides.items ?? sampleRows()
  return { items, page: 1, size: 20, total: items.length, ...overrides }
}
