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
// 他テーブル参照の E2E（190・195）の前提を API で作る（Intent 261029-reference-lookup-cleanup の U3、D39、F8）。
// - 共有の DSL（businessDataDsl.ts）を毎回投入して適用する（適用済みかを判定しない。R-02）。
// - 自作のロール（注文は FULL・作成あり、顧客・商品は READ）と、招待から登録を済ませた自作の利用者を作って割り当てる。
//   初期管理者・見本の行・見本の DSL は変えない（team.md）。
// - 利用者の値・実行の印・参照先のラベルを値のファイルに書く（報告に漏れたら見つける）。資格情報はプロセスの環境変数で受ける。
// - 記録と失敗の文に値を出さない（状態コードと道の名前だけ）。
import { expect, type APIRequestContext } from '@playwright/test'
import { businessDataDsl, REFERENCE_TABLES, referenceLabels } from './businessDataDsl'
import { requestAdminAccessToken } from './invitationSeed'
import {
  createRegisteredUser,
  REGISTERED_DISPLAY_NAME,
  registrationPrerequisites,
  type RegisteredUser,
} from './registeredUser'
import { BUSINESS_VALUE_MARK, recordSecretValues } from './secretValues'
import { newUserAdminRunTag } from './userAdminRun'

/** 参照先の行の値（businessDataSchema.sql と同じ） */
export const REFERENCE_ROWS = {
  firstCustomer: { value: 'CUST-001', label: `${BUSINESS_VALUE_MARK} 顧客 01` },
  secondCustomer: { value: 'CUST-002', label: `${BUSINESS_VALUE_MARK} 顧客 02` },
  fifthProduct: { value: 'PROD-005', label: `${BUSINESS_VALUE_MARK} 商品 05` },
  longProduct: {
    value: 'PROD-024',
    label: `とても長い名前の架空の商品 <${'あいうえおかきくけこ'.repeat(10)}`,
  },
  twoWordProduct: { value: 'PROD-025', label: '青い 木製の椅子' },
} as const

/** 作った前提 */
export interface ReferenceRun {
  schema: string
  tag: string
  roleId: number
  user: RegisteredUser
  labels: ReturnType<typeof referenceLabels> & { itemTable: string; itemMenu: string }
}

async function expectStatus(
  promise: Promise<{ status(): number }>,
  expected: number,
  what: string,
): Promise<void> {
  const response = await promise
  expect(response.status(), `${what}の状態コード`).toBe(expected)
}

/**
 * 共有の DSL を適用し、自作のロールと利用者を作る。登録の前提（招待・Mailpit）が無ければ理由を返す。
 *
 * @param request API の要求
 * @param roleTag ロールの名前に入れる短い印（190・195 で分ける）
 */
export async function prepareReferenceRun(
  request: APIRequestContext,
  roleTag: string,
): Promise<ReferenceRun | string> {
  const schema = process.env.E2E_BIZ_SCHEMA ?? ''
  expect(schema.startsWith('e2e_biz_'), '実行のスキーマ（./gradlew e2eTest が渡す）').toBe(true)
  const runTag = newUserAdminRunTag()
  const tag = runTag.slice(-12).toUpperCase()
  const labels = {
    ...referenceLabels(tag),
    itemTable: `E2ER 品目 ${tag}`,
    itemMenu: `E2ER 品目のメニュー ${tag}`,
  }
  recordSecretValues([
    { kind: 'runTag', value: runTag },
    { kind: 'displayName', value: REGISTERED_DISPLAY_NAME },
    ...Object.values(REFERENCE_ROWS).flatMap((row) => [
      { kind: 'businessValue' as const, value: row.label },
    ]),
  ])
  const adminToken = await requestAdminAccessToken(request)
  const auth = { Authorization: `Bearer ${adminToken}` }

  const preview = await request.post('/api/admin/dsl/preview?source=PASTE', {
    headers: { ...auth, 'Content-Type': 'application/yaml' },
    data: businessDataDsl(schema, labels, tag),
  })
  expect(preview.status(), 'DSL の投入の状態コード').toBe(201)
  const { previewId } = (await preview.json()) as { previewId: string }
  await expectStatus(
    request.post('/api/admin/dsl/apply', { headers: auth, data: { previewId } }),
    200,
    'DSL の適用',
  )

  const created = await request.post('/api/admin/roles', {
    headers: auth,
    data: { name: `E2ER_${roleTag}_${tag}` },
  })
  expect(created.status(), 'ロールの作成の状態コード').toBe(201)
  const { roleId } = (await created.json()) as { roleId: number }
  const grants = [
    { table: REFERENCE_TABLES.order, main: 'FULL', create: true },
    { table: REFERENCE_TABLES.customer, main: 'READ', create: false },
    { table: REFERENCE_TABLES.product, main: 'READ', create: false },
  ]
  for (const grant of grants) {
    await expectStatus(
      request.put(`/api/admin/roles/${roleId}/permissions`, {
        headers: auth,
        data: {
          scope: { schemaName: schema, tableName: grant.table },
          entries: [
            {
              schemaName: schema,
              tableName: grant.table,
              columnName: null,
              main: grant.main,
              create: grant.create,
              delete: false,
            },
          ],
        },
      }),
      204,
      '権限の保存',
    )
  }

  const unavailable = await registrationPrerequisites(request, adminToken)
  if (unavailable !== undefined) {
    return unavailable
  }
  const user = await createRegisteredUser(request, runTag)
  recordSecretValues([
    { kind: 'email', value: user.email },
    { kind: 'password', value: user.password },
  ])
  const list = await request.get(`/api/admin/users?page=1&q=${encodeURIComponent(user.email)}`, {
    headers: auth,
  })
  expect(list.status(), '利用者の一覧の状態コード').toBe(200)
  const userId = ((await list.json()) as { items: { userId: number }[] }).items[0]?.userId ?? 0
  expect(userId > 0, '作った利用者が一覧にある').toBe(true)
  await expectStatus(
    request.post(`/api/admin/roles/${roleId}/assignments`, { headers: auth, data: { userId } }),
    204,
    '割り当て',
  )
  return { schema, tag, roleId, user, labels }
}

/** 注文の表の作成の画面の道 */
export function orderNewPath(schema: string): string {
  return `/tables/new?${new URLSearchParams({ schema, table: REFERENCE_TABLES.order }).toString()}`
}
