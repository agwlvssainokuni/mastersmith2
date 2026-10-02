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
// 120 の検査と測りで返す管理の API の見本と、110 で見本と本物の応答を照らし合わせる関数（Intent 260930-user-admin の U5、
// security-design.md 5節、NFR9.9、計画 8節の D-8）。見本は画面の側の型（useradmin/api/types.ts の AdminUserPage・AdminUser）を
// 付けて置く。E2E から画面のコードを読むのは型だけ（import type）で、実行時に画面のコードを読まない。
// - 見本のメールアドレスは example.com の値だけ。110 が作る値の形（u7-perf- で始まる宛先、e2e-u7-pw- で始まるパスワード、
//   氏名「計測 花子」）を使わない（報告の部品の値の形での探し方と重ならないため）。氏名は架空の2語を含める。
// - 本物の応答は、ロックしていない行の lockedUntil を null で返す（計画 8節の D-4）。見本も同じ形（null）で返す。
// - 照らし合わせは項目の名前と型だけを比べ、違いは項目の名前だけで返す（値を出さない）。lockedUntil は文字列か null を同じとみる。
import type { AdminUser, AdminUserPage } from '../../src/features/useradmin/api/types'

/** 応答の1行（本物と同じく、lockedUntil はロックしていなければ null） */
export type AdminUserWire = Omit<AdminUser, 'lockedUntil'> & { lockedUntil: string | null }

/** 応答の1ページ */
export type AdminUserPageWire = Omit<AdminUserPage, 'items'> & { items: AdminUserWire[] }

/** 業務の失敗（409）の code（契約 C3 の5つ） */
export const USER_ADMIN_CONFLICT_CODES = [
  'USER_ADMIN_SELF_OPERATION',
  'USER_ADMIN_TARGET_SUSPENDED',
  'USER_ADMIN_NO_CHANGE',
  'USER_ADMIN_LAST_ADMIN',
  'USER_ADMIN_BUSY',
] as const

/** 見本の detail の目印（個人に関する値でも秘密でもない固定の文字。画面に出ないことを確かめる） */
export const USER_ADMIN_DETAIL_MARKER = 'user-admin-detail-marker'

/** 見本の traceId（架空の 32 文字の16進） */
const SAMPLE_TRACE_ID = '0123456789abcdef0123456789abcdef'

/** 見本の問題の種類の URL の元 */
const SAMPLE_PROBLEM_BASE = 'http://localhost/api/problems/'

/** 画面の型の行を応答の形にする（lockedUntil が無ければ null）。 */
function toWire(row: AdminUser): AdminUserWire {
  return { ...row, lockedUntil: row.lockedUntil ?? null }
}

/** 見本の1行（既定は管理者でない有効な利用者） */
function rowOf(
  overrides: Partial<AdminUser> & Pick<AdminUser, 'userId' | 'email' | 'displayName'>,
): AdminUser {
  return {
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

/** 検査の見本の自分の行（管理者） */
export const SELF_ROW_ID = 1

/** 検査の見本の、確かめ・失敗・成功の操作に使う行（ロック中の利用者） */
export const TARGET_ROW_ID = 3

/** 長いメールアドレス（折り返しの確かめ） */
const LONG_EMAIL = `${'very.long.mail.address.for.wrapping.check'.repeat(3)}@example.com`

/** 検査の一覧の行（2語の氏名・長いメールアドレス・ロック中・利用停止・管理者・「あなた」の行・記号の氏名） */
function inspectionRows(): AdminUser[] {
  return [
    rowOf({
      userId: SELF_ROW_ID,
      email: 'kanri.ichiro@example.com',
      displayName: '管理 一郎',
      admin: true,
      self: true,
    }),
    rowOf({
      userId: 2,
      email: 'sato.hanako@example.com',
      displayName: '佐藤 花子',
      language: 'en',
      admin: true,
    }),
    rowOf({
      userId: TARGET_ROW_ID,
      email: 'suzuki.jiro@example.com',
      displayName: '鈴木 次郎',
      locked: true,
      lockedUntil: '2026-09-25T04:30:00Z',
      resettable: true,
    }),
    rowOf({
      userId: 4,
      email: 'takahashi.saburo@example.com',
      displayName: '高橋 三郎',
      suspended: true,
      resettable: true,
    }),
    rowOf({ userId: 5, email: LONG_EMAIL, displayName: '田中 四郎' }),
    rowOf({ userId: 6, email: 'kigou@example.com', displayName: '<b>記号</b> & "引用" \'名\'' }),
  ]
}

/** 検査の一覧（1ページ） */
export function inspectionListPage(): AdminUserPageWire {
  const items = inspectionRows().map(toWire)
  return { items, page: 1, size: 20, total: items.length }
}

/** 検索に当たらない一覧 */
export function emptyListPage(): AdminUserPageWire {
  return { items: [], page: 1, size: 20, total: 0 }
}

/** 測りの見本の全件数（1ページ目 20 件、2ページ目 5 件） */
const MEASURE_TOTAL = 25

/** 測りの見本の一覧（2ページ分のうちの1ページ） */
export function measurementListPage(page: 1 | 2): AdminUserPageWire {
  const start = page === 1 ? 1 : 21
  const count = page === 1 ? 20 : MEASURE_TOTAL - 20
  const items = Array.from({ length: count }, (_, index) => {
    const number = String(start + index).padStart(2, '0')
    return toWire(
      rowOf({
        userId: 100 + start + index,
        email: `sokutei.${number}@example.com`,
        displayName: `測定 ${number}`,
      }),
    )
  })
  return { items, page, size: 20, total: MEASURE_TOTAL }
}

/** 業務の失敗（409）の見本 */
export function conflictProblem(
  instance = `/api/admin/users/${TARGET_ROW_ID}/suspend`,
): Readonly<Record<string, unknown>> {
  return {
    type: `${SAMPLE_PROBLEM_BASE}user-admin-no-change`,
    title: 'Conflict',
    status: 409,
    detail: USER_ADMIN_DETAIL_MARKER,
    instance,
    code: 'USER_ADMIN_NO_CHANGE',
    traceId: SAMPLE_TRACE_ID,
  }
}

/** 検索の文字の誤り（400）の見本 */
export function searchValidationProblem(): Readonly<Record<string, unknown>> {
  return {
    type: `${SAMPLE_PROBLEM_BASE}validation-failed`,
    title: 'Bad Request',
    status: 400,
    detail: USER_ADMIN_DETAIL_MARKER,
    instance: '/api/admin/users',
    fieldErrors: [{ field: 'q', reason: 'TOO_LONG' }],
    code: 'VALIDATION_FAILED',
    traceId: SAMPLE_TRACE_ID,
  }
}

/** 型の名前（照らし合わせ用） */
function typeName(value: unknown): string {
  if (value === null) {
    return 'null'
  }
  if (Array.isArray(value)) {
    return 'array'
  }
  return typeof value
}

/** 文字列か null を同じとみる項目（ロックしていなければ null） */
const STRING_OR_NULL_FIELDS = new Set(['lockedUntil'])

/**
 * 本物の応答と見本の、項目の名前と型の違いを返す（違いは項目の名前だけ。値を出さない）。
 * 配列は、見本の最初の要素の形と、本物のすべての要素を比べる。
 */
export function shapeDifferences(actual: unknown, sample: unknown, at = ''): string[] {
  const actualType = typeName(actual)
  const sampleType = typeName(sample)
  const name = at === '' ? '(本文)' : at
  if (actualType !== sampleType) {
    return [name]
  }
  if (Array.isArray(actual) && Array.isArray(sample)) {
    if (sample.length === 0) {
      return []
    }
    const differences = new Set<string>()
    for (const item of actual) {
      for (const difference of shapeDifferences(item, sample[0], `${at}[]`)) {
        differences.add(difference)
      }
    }
    return [...differences]
  }
  if (actualType !== 'object') {
    return []
  }
  const actualRecord = actual as Record<string, unknown>
  const sampleRecord = sample as Record<string, unknown>
  const keys = new Set([...Object.keys(actualRecord), ...Object.keys(sampleRecord)])
  const differences: string[] = []
  for (const key of [...keys].sort()) {
    const path = at === '' ? key : `${at}.${key}`
    if (!(key in actualRecord) || !(key in sampleRecord)) {
      differences.push(path)
      continue
    }
    if (STRING_OR_NULL_FIELDS.has(key)) {
      const ok = (value: unknown) => value === null || typeof value === 'string'
      if (!ok(actualRecord[key]) || !ok(sampleRecord[key])) {
        differences.push(path)
      }
      continue
    }
    differences.push(...shapeDifferences(actualRecord[key], sampleRecord[key], path))
  }
  return differences
}
