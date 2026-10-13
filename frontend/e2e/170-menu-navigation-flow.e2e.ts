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
// I の流れ（Intent 261004-role-menu の U7、計画 10.2、logical-components.md 6.1、security-design.md 5.3・6節）。
// この Intent の流れの E2E の2本までの1本（team.md）。前提を自分で作り、前のテストの状態に頼らない。後始末はしない
// （Mailpit のメールも消さない）。名前は走らせるごとに一意（E2EI_ と runTag）。初期管理者の状態とロールは変えない。
// 1. 初期管理者の資格情報（プロセスの環境変数）で API のトークンを取り、E2EI_ の版 2 の DSL（スキーマ1つ・テーブル T1・T2、
//    メニューは5段の入れ子で各段 40 文字の名前・末端が T1、T2 を指す項目も置く）を投入して適用する。
// 2. ロール（E2EI_ROLE_<印>）を API で作り、T1 を READ、T2 を NONE にする。
// 3. createRegisteredUser で利用者を作り、メールアドレス・パスワード・氏名を recordSecretValues で書いて読み戻し、
//    3つが揃わなければ流れの始めで失敗にする（NFR1.9、R-07）。そのロールを API で割り当てる。
// 4〜7. その利用者で画面からログインし、5段を開閉のボタンで開いて末端の T1 を選び、置き場の見出し・aria-current・祖先の展開、
//    再読み込みでも同じこと、T2 の置き場の道を直接開くと権限なしの表示で T2 の表示名が出ないことを確かめる。
// 8. 利用者のトークンで GET /api/me/navigation・/api/me/table-access・/api/me/permissions/schemas の本物の応答を読み、
//    160 の見本（navTreeFixtures.ts）と項目の名前と型が一致することを確かめる。
// 9. ログアウトの確かめ（security-design.md 5.3）。
// test.step の題・注記・添付に値を入れない。初期管理者の資格情報を webServer.env に置かない（既存のとおり）。
import { expect, test, type APIRequestContext } from '@playwright/test'
import { requestAdminAccessToken } from './support/invitationSeed'
import { inspectionTree, mySchemasWire, tableAccessWire } from './support/navTreeFixtures'
import {
  createRegisteredUser,
  loginWithForm,
  REGISTERED_DISPLAY_NAME,
  registrationPrerequisites,
} from './support/registeredUser'
import { readSecretValues, recordSecretValues, secretValuesFilePath } from './support/secretValues'
import { shapeCompare } from './support/shapeCompare'
import { e2eTraceMode } from './support/traceMode'
import { openUserMenuItem } from './support/userMenu'
import { newUserAdminRunTag } from './support/userAdminRun'

test.use({ trace: e2eTraceMode() })

/** null を許す項目（見本と本物のどちらかが null でもよい） */
const NULLABLE = new Set(['table', 'emptyReason', 'workRole', 'tableName', 'columnName'])

/** 段の数 */
const DEPTH = 5

/** 40 文字の名前 */
function label40(prefix: string): string {
  return prefix.padEnd(40, 'ー')
}

/** YAML の文字列（二重引用符。名前は英数字・日本語・記号の _ だけ） */
function quoted(value: string): string {
  return JSON.stringify(value)
}

/** 5段の入れ子のメニュー（末端が T1）と、T2 を指す1段目の項目 */
function menusYaml(schema: string, t1: string, t2: string, levelLabels: readonly string[]): string {
  const lines: string[] = ['menus:']
  levelLabels.forEach((labelText, index) => {
    const indent = '  '.repeat(index * 2 + 1)
    const label = `{ ja: ${quoted(labelText)}, en: ${quoted(labelText)} }`
    lines.push(`${indent}- label: ${label}`)
    if (index === levelLabels.length - 1) {
      lines.push(`${indent}  table: { schema: ${schema}, name: ${t1} }`)
    } else {
      lines.push(`${indent}  items:`)
    }
  })
  lines.push(`  - label: { ja: ${quoted(`${t2}の項目`)}, en: ${quoted(t2)} }`)
  lines.push(`    table: { schema: ${schema}, name: ${t2} }`)
  return lines.join('\n')
}

/** 版 2 の DSL（スキーマ1つ・テーブル2つ） */
function flowDsl(
  schema: string,
  tables: readonly { name: string; label: string }[],
  levelLabels: readonly string[],
): string {
  const column = (name: string, order: number) => `          ${name}:
            label: { ja: ${name}, en: ${name} }
            dbType: { name: VARCHAR, length: 20, precision: null, scale: null, nullable: true }
            formPart: text
            search: { enabled: false, collapsed: false }
            list: { visible: true, order: ${order}, sortable: true }
            detail: { visible: true }
            validations: []
`
  const tableDef = ({ name, label }: { name: string; label: string }) => `      ${name}:
        label: { ja: ${quoted(label)}, en: ${quoted(label)} }
        view: false
        primaryKey: [CODE]
        foreignKeys: []
        columns:
${column('CODE', 1)}${column('NAME', 2)}`
  const [t1, t2] = tables
  return `version: 2
${menusYaml(schema, t1?.name ?? '', t2?.name ?? '', levelLabels)}
schemas:
  ${schema}:
    label: { ja: ${schema}, en: ${schema} }
    tables:
${tables.map(tableDef).join('')}`
}

/** トークンつきで JSON を読む（状態コードを確かめる） */
async function getJson(request: APIRequestContext, token: string, path: string): Promise<unknown> {
  const response = await request.get(path, { headers: { Authorization: `Bearer ${token}` } })
  expect(response.status(), `GET の状態コード（${path.split('?')[0]}）`).toBe(200)
  return response.json()
}

test.describe('170 menu navigation flow (I)', () => {
  test('open a five-level menu item, see the placeholder, the forbidden table and log out', async ({
    page,
    request,
  }) => {
    test.setTimeout(240_000)
    const runTag = newUserAdminRunTag()
    const tag = runTag.toUpperCase()
    const schema = `E2EI_${tag}`
    const t1 = { name: `E2EI_T1_${tag}`, label: label40('E2EI 受注明細の表示名') }
    const t2 = { name: `E2EI_T2_${tag}`, label: label40('E2EI 見てはいけない表の表示名') }
    const levelLabels = Array.from({ length: DEPTH }, (_, index) =>
      label40(`E2EI 段${index + 1}のまとまりの名前`),
    )
    const roleName = `E2EI_ROLE_${tag}`
    const email = `u7-perf-${runTag}@example.com`
    recordSecretValues([
      { kind: 'runTag', value: runTag },
      { kind: 'email', value: email },
      { kind: 'displayName', value: REGISTERED_DISPLAY_NAME },
    ])

    const adminToken = await requestAdminAccessToken(request)
    const auth = { Authorization: `Bearer ${adminToken}` }

    await test.step('1. apply the E2EI DSL', async () => {
      const preview = await request.post('/api/admin/dsl/preview?source=PASTE', {
        headers: { ...auth, 'Content-Type': 'application/yaml' },
        data: flowDsl(schema, [t1, t2], levelLabels),
      })
      expect(preview.status(), 'DSL の投入の状態コード').toBe(201)
      const { previewId } = (await preview.json()) as { previewId: string }
      const applied = await request.post('/api/admin/dsl/apply', {
        headers: auth,
        data: { previewId },
      })
      expect(applied.status(), 'DSL の適用の状態コード').toBe(200)
    })

    const roleId = await test.step('2. create the role with T1 READ and T2 NONE', async () => {
      const created = await request.post('/api/admin/roles', {
        headers: auth,
        data: { name: roleName },
      })
      expect(created.status(), 'ロールの作成の状態コード').toBe(201)
      const { roleId: id } = (await created.json()) as { roleId: number }
      const entry = (table: string, main: 'READ' | 'NONE') => ({
        schemaName: schema,
        tableName: table,
        columnName: null,
        main,
        create: null,
        delete: null,
      })
      // 保存の範囲はテーブルごと（範囲の外の対象は 400）。
      for (const [table, main] of [
        [t1.name, 'READ'],
        [t2.name, 'NONE'],
      ] as const) {
        const saved = await request.put(`/api/admin/roles/${id}/permissions`, {
          headers: auth,
          data: { scope: { schemaName: schema, tableName: table }, entries: [entry(table, main)] },
        })
        expect(saved.status(), '権限の保存の状態コード').toBe(204)
      }
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
      // 書き忘れを失敗にする（R-07）: メールアドレス・パスワード・氏名の3つが値のファイルに揃っている。
      const written = readSecretValues(secretValuesFilePath())
      const has = (kind: string, value: string) =>
        written.some((entry) => entry.kind === kind && entry.value === value)
      expect(
        has('email', created.email) &&
          has('password', created.password) &&
          has('displayName', REGISTERED_DISPLAY_NAME),
        '値のファイルにメールアドレス・パスワード・氏名が揃っている',
      ).toBe(true)
      const list = (await getJson(
        request,
        adminToken,
        `/api/admin/users?page=1&q=${encodeURIComponent(created.email)}`,
      )) as { items: { userId: number }[] }
      const userId = list.items[0]?.userId ?? 0
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

    const levelIds = Array.from({ length: DEPTH }, (_, index) =>
      Array.from({ length: index + 1 }, () => '0').join('.'),
    )
    const leafId = levelIds.at(-1) ?? ''

    await test.step('4. open the five levels with the toggle buttons and choose T1', async () => {
      await loginWithForm(page, user.email, user.password)
      for (const id of levelIds.slice(0, -1)) {
        const toggle = page.getByTestId(`sidebar-toggle-biz:${id}`)
        await expect(toggle).toHaveAttribute('aria-expanded', 'false')
        await toggle.click()
        await expect(toggle).toHaveAttribute('aria-expanded', 'true')
      }
      await page.getByTestId(`sidebar-nav-biz:${leafId}`).click()
    })

    const expectLeafShown = async () => {
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(t1.label)
      await expect(page.getByTestId(`sidebar-nav-biz:${leafId}`)).toHaveAttribute(
        'aria-current',
        'page',
      )
      for (const id of levelIds.slice(0, -1)) {
        await expect(page.getByTestId(`sidebar-toggle-biz:${id}`)).toHaveAttribute(
          'aria-expanded',
          'true',
        )
      }
      // T2 は NONE のため、業務のメニューに出ない。
      await expect(page.getByRole('link', { name: `${t2.name}の項目` })).toHaveCount(0)
    }

    await test.step('5. the placeholder shows T1 with the current leaf and open ancestors', async () => {
      await expect(page).toHaveURL(/\/tables\?/)
      await expectLeafShown()
    })

    await test.step('6. the same after reloading', async () => {
      await page.reload()
      await expectLeafShown()
    })

    await test.step('7. the T2 placeholder opened directly shows no permission', async () => {
      await page.goto(`/tables?${new URLSearchParams({ schema, table: t2.name }).toString()}`)
      await expect(page.getByRole('heading', { level: 1 })).toHaveText(
        'この画面を表示する権限がありません',
      )
      await expect(page.getByTestId('tables-page')).not.toContainText(t2.label)
      await expect(page.getByTestId('tables-page')).not.toContainText(t2.name)
    })

    await test.step('8. the samples of 160 have the shape of the real responses', async () => {
      const login = await request.post('/api/auth/login', {
        data: { email: user.email, password: user.password },
      })
      expect(login.status(), '利用者のログインの状態コード').toBe(200)
      const { accessToken } = (await login.json()) as { accessToken: string }
      const differences: Record<string, string[]> = {}
      const compare = (name: string, actual: unknown, sample: unknown) => {
        const found = shapeCompare(actual, sample, NULLABLE)
        if (found.length > 0) {
          differences[name] = found
        }
      }
      compare(
        'navigation',
        await getJson(request, accessToken, '/api/me/navigation'),
        inspectionTree(),
      )
      compare(
        'tableAccess',
        await getJson(
          request,
          accessToken,
          `/api/me/table-access?${new URLSearchParams({ schema, table: t1.name }).toString()}`,
        ),
        tableAccessWire(),
      )
      compare(
        'permissionsSchemas',
        await getJson(request, accessToken, '/api/me/permissions/schemas'),
        mySchemasWire(),
      )
      test
        .info()
        .annotations.push({ type: 'shape-differences', description: JSON.stringify(differences) })
      expect(differences, '見本と本物の応答の項目の名前と型の違い').toEqual({})
    })

    await test.step('9. log out through the logout path', async () => {
      // (a) ログインしたまま /logout を直接開くと、ホームに戻り、ログインしたまま。
      await page.goto('/logout')
      await expect(page.getByTestId('home-page')).toBeVisible()
      await expect(page).toHaveURL(/\/$/)
      // (b) メニューからログアウトを選び、この時点からログアウトの要求を数える。
      let logoutRequests = 0
      page.on('request', (sent) => {
        if (
          sent.method() === 'POST' &&
          new URL(sent.url()).pathname === '/api/auth/session/logout'
        ) {
          logoutRequests += 1
        }
      })
      await openUserMenuItem(page, 'ログアウト')
      // (c) ログインの画面が出る。
      await expect(page.getByTestId('login-layout')).toBeVisible()
      // (d) 戻る・再読み込みでもログインの画面のまま。
      await page.goBack()
      await expect(page.getByTestId('login-layout')).toBeVisible()
      await page.reload()
      await expect(page.getByTestId('login-layout')).toBeVisible()
      // (e) ログアウトの要求は1回。
      expect(logoutRequests, 'ログアウトの要求の数').toBe(1)
      // (f) 開閉の保存の鍵が無い。
      const stored = await page.evaluate(() => sessionStorage.getItem('mastersmith.nav.expanded'))
      expect(stored, '開閉の保存の鍵').toBeNull()
    })
  })
})
