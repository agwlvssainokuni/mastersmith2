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
// 業務データの代表の流れ（Intent 261016-business-data-views の U3 の B6、計画 Step 34、D30、U3-NFR5.10・7.13、
// logical-components.md の L10、基盤の設計 cicd-pipeline.md 3.4）。この Intent の業務データの E2E の1本（team.md）。
// - 対象DB は本物の見本の PostgreSQL の実行のスキーマ（./gradlew e2eTest が E2E_BIZ_SCHEMA を作り、表 e2e_item と架空の
//   行 25 件を置き、一時の読み取りと書き込みのロールを WAR に渡す。frontend/e2e/support/businessDataSchema.sql）。
// - 前提を自分で作り、前のテストの状態に頼らない。DSL（E2EB_ の版 2。実行のスキーマの表とメニュー。190・195 と共有する
//   support/businessDataDsl.ts。Intent 261029-reference-lookup-cleanup の U3 で移した）を投入して適用し、
//   ロール（E2EB_ROLE_<印>。表は FULL・作成・削除あり）と利用者を作って割り当てる。初期管理者の状態とロールは変えない。
//   後始末はしない（適用した DSL も残る。180 より後のファイルは適用中の DSL に頼らない。計画 1.4）。
// - 流れ: ログイン → メニュー → 一覧（25 件。ページ送りで 21〜25 件）→ 検索 → 作成 → 詳細 → 更新 → 削除の確かめ →
//   一覧。作成・更新・削除の操作と保存はキーボード（Enter）で行う。保存の後に未保存の確かめが出ないことを確かめる（D25）。
// - 業務データの見本（businessDataFixtures.ts）の形を、本物の応答と shapeCompare で比べる（行の値の組は名前が違うため、
//   値が文字か null の組であることだけを確かめて比べから外す）。
// - 行の値は架空の値だけ。作った行の値は目印（E2E-BIZ-MARK）を付け、値のファイル（businessValue）に書く。test.step の題・
//   注記・添付に値を入れない。資格情報はプロセスの環境変数で受け、webServer.env に置かない。
import { expect, test, type APIRequestContext, type Page } from '@playwright/test'
import { businessDataDsl, ITEM_TABLE } from './support/businessDataDsl'
import { fixturePage, longDetail, tableDefinitionFor } from './support/businessDataFixtures'
import { requestAdminAccessToken } from './support/invitationSeed'
import {
  createRegisteredUser,
  loginWithForm,
  REGISTERED_DISPLAY_NAME,
  registrationPrerequisites,
} from './support/registeredUser'
import {
  BUSINESS_VALUE_MARK,
  readSecretValues,
  recordSecretValues,
  secretValuesFilePath,
} from './support/secretValues'
import { shapeCompare } from './support/shapeCompare'
import { e2eTraceMode } from './support/traceMode'
import { newUserAdminRunTag } from './support/userAdminRun'

test.use({ trace: e2eTraceMode() })

/** 表の名前（businessDataSchema.sql と同じ） */
const TABLE = ITEM_TABLE

/** null を許す項目（見本と本物のどちらかが null でもよい） */
const NULLABLE = new Set([
  'workRoleId',
  'restrictionReason',
  'listFormat',
  'width',
  'formPart',
  'minLength',
  'maxLength',
  'precision',
  'scale',
  'min',
  'max',
  'defaultValue',
  'key',
])

/** 行の値の組（名前はテーブルごとに違う）を、値が文字か null の組であることを確かめてから決まった形に置き換える */
function withoutColumnMaps(value: unknown, broken: string[], at = ''): unknown {
  if (Array.isArray(value)) {
    return value.map((item) => withoutColumnMaps(item, broken, `${at}[]`))
  }
  if (typeof value !== 'object' || value === null) {
    return value
  }
  return Object.fromEntries(
    Object.entries(value as Record<string, unknown>).map(([name, item]) => {
      if ((name === 'key' || name === 'values') && typeof item === 'object' && item !== null) {
        const entries = Object.values(item as Record<string, unknown>)
        if (!entries.every((entry) => typeof entry === 'string' || entry === null)) {
          broken.push(at === '' ? name : `${at}.${name}`)
        }
        return [name, { columns: 'string-or-null' }]
      }
      return [name, withoutColumnMaps(item, broken, at === '' ? name : `${at}.${name}`)]
    }),
  )
}

/** トークンつきで POST し、JSON を読む（状態コードを確かめる） */
async function postJson(
  request: APIRequestContext,
  token: string,
  path: string,
  data: unknown,
): Promise<unknown> {
  const response = await request.post(path, { headers: { Authorization: `Bearer ${token}` }, data })
  expect(response.status(), `POST の状態コード（${path}）`).toBe(200)
  return response.json()
}

/** 要素にフォーカスを移して Enter で押す（キーボードの操作） */
async function pressEnter(page: Page, testId: string): Promise<void> {
  await page.getByTestId(testId).focus()
  await page.keyboard.press('Enter')
}

test.describe('180 business data flow', () => {
  test('list, search, create, view, edit and delete rows of the sample PostgreSQL with the keyboard', async ({
    page,
    request,
  }) => {
    test.setTimeout(240_000)
    const schema = process.env.E2E_BIZ_SCHEMA ?? ''
    expect(schema.startsWith('e2e_biz_'), '実行のスキーマ（./gradlew e2eTest が渡す）').toBe(true)
    const runTag = newUserAdminRunTag()
    const tag = runTag.slice(-12).toUpperCase()
    const tableLabel = `E2EB 品目 ${tag}`
    const menuLabel = `E2EB 品目のメニュー ${tag}`
    const roleName = `E2EB_ROLE_${tag}`
    const email = `u7-perf-${runTag}@example.com`
    const newCode = `N-${tag}`
    const newName = `${BUSINESS_VALUE_MARK} 新しい品目 ${tag}`
    const editedName = `${BUSINESS_VALUE_MARK} 直した品目 ${tag}`
    recordSecretValues([
      { kind: 'runTag', value: runTag },
      { kind: 'email', value: email },
      { kind: 'displayName', value: REGISTERED_DISPLAY_NAME },
      { kind: 'businessValue', value: newCode },
      { kind: 'businessValue', value: newName },
      { kind: 'businessValue', value: editedName },
    ])

    const adminToken = await requestAdminAccessToken(request)
    const auth = { Authorization: `Bearer ${adminToken}` }

    await test.step('1. apply the DSL of the run schema', async () => {
      const preview = await request.post('/api/admin/dsl/preview?source=PASTE', {
        headers: { ...auth, 'Content-Type': 'application/yaml' },
        data: businessDataDsl(schema, { itemTable: tableLabel, itemMenu: menuLabel }, tag),
      })
      expect(preview.status(), 'DSL の投入の状態コード').toBe(201)
      const { previewId } = (await preview.json()) as { previewId: string }
      const applied = await request.post('/api/admin/dsl/apply', {
        headers: auth,
        data: { previewId },
      })
      expect(applied.status(), 'DSL の適用の状態コード').toBe(200)
    })

    const roleId = await test.step('2. create the role with FULL, create and delete', async () => {
      const created = await request.post('/api/admin/roles', {
        headers: auth,
        data: { name: roleName },
      })
      expect(created.status(), 'ロールの作成の状態コード').toBe(201)
      const { roleId: id } = (await created.json()) as { roleId: number }
      const saved = await request.put(`/api/admin/roles/${id}/permissions`, {
        headers: auth,
        data: {
          scope: { schemaName: schema, tableName: TABLE },
          entries: [
            {
              schemaName: schema,
              tableName: TABLE,
              columnName: null,
              main: 'FULL',
              create: true,
              delete: true,
            },
          ],
        },
      })
      expect(saved.status(), '権限の保存の状態コード').toBe(204)
      return id
    })

    const user = await test.step('3. prepare a registered user with the role', async () => {
      const unavailable = await registrationPrerequisites(request, adminToken)
      if (unavailable !== undefined) {
        test.info().annotations.push({ type: 'skip-reason', description: unavailable })
        return undefined
      }
      const created = await createRegisteredUser(request, runTag)
      recordSecretValues([{ kind: 'password', value: created.password }])
      const written = readSecretValues(secretValuesFilePath())
      expect(
        written.some((entry) => entry.kind === 'password' && entry.value === created.password),
        '値のファイルにパスワードがある',
      ).toBe(true)
      const list = await request.get(
        `/api/admin/users?page=1&q=${encodeURIComponent(created.email)}`,
        {
          headers: auth,
        },
      )
      expect(list.status(), '利用者の一覧の状態コード').toBe(200)
      const userId = ((await list.json()) as { items: { userId: number }[] }).items[0]?.userId ?? 0
      expect(userId > 0, '作った利用者が一覧にある').toBe(true)
      const assigned = await request.post(`/api/admin/roles/${roleId}/assignments`, {
        headers: auth,
        data: { userId },
      })
      expect(assigned.status(), '割り当ての状態コード').toBe(204)
      return created
    })
    if (user === undefined) {
      test.skip(true, 'registration prerequisites are not available')
      return
    }

    await test.step('4. open the list from the menu and page with the keyboard', async () => {
      await loginWithForm(page, user.email, user.password)
      const menu = page.getByRole('link', { name: menuLabel })
      await menu.focus()
      await page.keyboard.press('Enter')
      await expect(page.getByTestId('tables-heading')).toHaveText(tableLabel)
      await expect(page.getByTestId('tables-count')).toHaveText('全 25 件（1〜20 件を表示）')
      await pressEnter(page, 'table-next-page')
      await expect(page.getByTestId('tables-count')).toHaveText('全 25 件（21〜25 件を表示）')
    })

    await test.step('5. search with the keyboard', async () => {
      const field = page.getByRole('textbox', { name: '品目名（部分一致）' })
      await field.fill('品目 2')
      await field.press('Enter')
      // 架空の行の「品目 20」〜「品目 25」の 6 件。
      await expect(page.getByTestId('tables-count')).toHaveText('全 6 件（1〜6 件を表示）')
      await page.getByRole('button', { name: '条件をクリア' }).press('Enter')
      await expect(page.getByTestId('tables-count')).toHaveText('全 25 件（1〜20 件を表示）')
    })

    await test.step('6. the samples have the shape of the real responses', async () => {
      const login = await request.post('/api/auth/login', {
        data: { email: user.email, password: user.password },
      })
      expect(login.status(), '利用者のログインの状態コード').toBe(200)
      const { accessToken } = (await login.json()) as { accessToken: string }
      const definition = (await postJson(request, accessToken, '/api/tables/definition', {
        schema,
        table: TABLE,
      })) as { definitionId: unknown }
      const rows = (await postJson(request, accessToken, '/api/tables/rows/search', {
        schema,
        table: TABLE,
        definitionId: definition.definitionId,
        conditions: [],
        page: 1,
        size: 20,
      })) as { items: { key: unknown }[] }
      const detail = await postJson(request, accessToken, '/api/tables/rows/get', {
        schema,
        table: TABLE,
        definitionId: definition.definitionId,
        key: rows.items[0]?.key,
      })
      const sample = tableDefinitionFor({
        schema,
        table: TABLE,
        displayName: { ja: 'x', en: 'x' },
        workRoleId: roleId,
      })
      const broken: string[] = []
      const differences: Record<string, string[]> = {}
      const compare = (name: string, actual: unknown, expected: unknown) => {
        const found = shapeCompare(
          withoutColumnMaps(actual, broken),
          withoutColumnMaps(expected, broken),
          NULLABLE,
        )
        if (found.length > 0) {
          differences[name] = found
        }
      }
      compare('definition', definition, sample)
      compare('rowPage', rows, fixturePage(sample, 1, 20, 3))
      compare('rowDetail', detail, longDetail(sample, 1))
      test
        .info()
        .annotations.push({ type: 'shape-differences', description: JSON.stringify(differences) })
      expect(broken, '行の値の組が文字か null でない所').toEqual([])
      expect(differences, '見本と本物の応答の項目の名前と型の違い').toEqual({})
    })

    await test.step('7. create a row with the keyboard and move to its detail without the leave dialog', async () => {
      await pressEnter(page, 'tables-create')
      await expect(page.getByTestId('tables-form-heading')).toHaveText(`${tableLabel}を作成`)
      await page.getByRole('textbox', { name: '品目コード *必須' }).fill(newCode)
      await page.getByRole('textbox', { name: '品目名 *必須' }).fill(newName)
      await page.getByRole('spinbutton', { name: '数量' }).fill('7')
      await pressEnter(page, 'tables-form-save')
      await expect(page.getByTestId('tables-detail-heading')).toHaveText(
        `${tableLabel}: ${newCode}`,
      )
      await expect(page.getByTestId('tables-detail-heading')).toBeFocused()
      await expect(page.getByText('作成しました。')).toBeVisible()
      await expect(page.getByTestId('tables-leave-dialog')).toHaveCount(0)
      await expect(page.getByTestId('tables-detail')).toContainText(newName)
    })

    await test.step('8. edit the row with the keyboard and come back to the detail', async () => {
      await pressEnter(page, 'tables-detail-update')
      const name = page.getByRole('textbox', { name: '品目名 *必須' })
      await expect(name).toHaveValue(newName)
      await name.fill(editedName)
      await pressEnter(page, 'tables-form-save')
      await expect(page.getByTestId('tables-detail-heading')).toBeFocused()
      await expect(page.getByText('更新しました。')).toBeVisible()
      await expect(page.getByTestId('tables-leave-dialog')).toHaveCount(0)
      await expect(page.getByTestId('tables-detail')).toContainText(editedName)
    })

    await test.step('9. delete the row after the confirmation and go back to the list', async () => {
      await pressEnter(page, 'tables-detail-delete')
      const dialog = page.getByRole('alertdialog')
      await expect(dialog).toBeVisible()
      await expect(page.getByTestId('tables-delete-cancel')).toBeFocused()
      await page.keyboard.press('Tab')
      await expect(page.getByTestId('tables-delete-confirm')).toBeFocused()
      await page.keyboard.press('Enter')
      await expect(page.getByText('削除しました。')).toBeVisible()
      await expect(page).toHaveURL(/\/tables\?/)
      await expect(page.getByTestId('tables-count')).toHaveText('全 25 件（1〜20 件を表示）')
    })
  })
})
