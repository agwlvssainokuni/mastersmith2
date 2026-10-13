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
// F の流れ（Intent 261004-role-menu の U6、計画 9.2、tech-stack-decisions.md NFR6.3・NFR6.10・9節の R-01）。この Intent の
// 流れの E2E の2本までの1本（team.md）。前提を自分で作り、前のテストの状態に頼らない。後始末はしない（Mailpit のメールも
// 消さない）。名前は走らせるごとに一意（E2EF_ と runTag）。初期管理者の状態とロールは変えない。
// 1. 初期管理者の資格情報（プロセスの環境変数）で API のトークンを取り、E2EF_ の版 2 の DSL（スキーマ1つ・テーブル2つ・
//    カラム数個・テーブルを指すメニューの項目1つ）を投入して適用する（照合の警告のまま適用できる）。
// 2. createRegisteredUser で招待から登録まで済ませた利用者を作り、値を recordSecretValues で値のファイルに書く。
// 3〜5. 初期管理者で画面からログインし、S3 でロールを作り、S4 でテーブルの主権限を READ にして保存し、S5 で 2 の利用者を
//    割り当てる。
// 6. 2 の利用者で API にログインし、作業ロールが作ったロールで、業務のメニューに 1 の項目が出ることを確かめる。
// 7. 140 の見本のすべての形を本物の応答と項目の名前と型で比べる（NFR6.10、13節 Q6: A）。グループは自分の E2EF_ のグループを
//    API で作って読み、確かめの応答は自分の E2EF_ の新しいロールを作る YAML を check にだけ送って読む（適用しない）。
// 8. test.step の題・注記・添付に値を入れない。初期管理者の資格情報を webServer.env に置かない（既存のとおり）。
// 6a〜6e（Intent 261004-role-menu の U7、計画 10.3、logical-components.md 6.2。本数は1本のまま）: 2つ目のロール（T を設定しない）を
//    API で作って同じ利用者に割り当て、利用者で画面からログインし、トップバーで2つ目へ切り替えるとサイドバーから T の項目が
//    消え、1つ目に戻すと出ることを確かめる。GET /api/me/work-role の本物の応答と 160 の見本の形を比べる。2つ目のロールの作成と
//    割り当ては API で行う（画面の S3・S5 は 3〜5 で確かめ済み。計画 12節 D-10）。
import { expect, test, type APIRequestContext } from '@playwright/test'
import { loginAsAdmin, openSidebarItem } from './support/adminLogin'
import { requestAdminAccessToken } from './support/invitationSeed'
import { THREE_ROLES, workRoleWire } from './support/navTreeFixtures'
import {
  createRegisteredUser,
  loginWithForm,
  REGISTERED_DISPLAY_NAME,
  registrationPrerequisites,
} from './support/registeredUser'
import {
  assignmentsWire,
  candidateUsersWire,
  columnNodesWire,
  groupDetailWire,
  groupListWire,
  groupRolesWire,
  roleListWire,
  roleWire,
  schemaNodesWire,
  tableNodesWire,
  transferCheckWire,
  userRolesWire,
} from './support/roleAdminFixtures'
import { recordSecretValues } from './support/secretValues'
import { shapeCompare } from './support/shapeCompare'
import { e2eTraceMode } from './support/traceMode'
import { newUserAdminRunTag } from './support/userAdminRun'
import { openUserMenuItem } from './support/userMenu'

test.use({ trace: e2eTraceMode() })

/** null を許す項目（見本と本物のどちらかが null でもよい） */
const NULLABLE = new Set([
  'tableName',
  'columnName',
  'main',
  'create',
  'delete',
  'groupId',
  'name',
  'roleId',
  'lockedUntil',
  'line',
  'column',
  'path',
])

/** 版 2 の DSL（スキーマ1つ・テーブル2つ・カラム数個・テーブルを指すメニューの項目1つ） */
function flowDsl(schema: string, table: string, other: string): string {
  const column = (name: string, order: number) => `          ${name}:
            label: { ja: ${name}, en: ${name} }
            dbType: { name: VARCHAR, length: 20, precision: null, scale: null, nullable: true }
            formPart: text
            search: { enabled: false, collapsed: false }
            list: { visible: true, order: ${order}, sortable: true }
            detail: { visible: true }
            validations: []
`
  const tableDef = (name: string) => `      ${name}:
        label: { ja: ${name}, en: ${name} }
        view: false
        primaryKey: [CODE]
        foreignKeys: []
        columns:
${column('CODE', 1)}${column('NAME', 2)}${column('NOTE', 3)}`
  return `version: 2
menus:
  - label: { ja: ${table}, en: ${table} }
    table: { schema: ${schema}, name: ${table} }
schemas:
  ${schema}:
    label: { ja: ${schema}, en: ${schema} }
    tables:
${tableDef(table)}${tableDef(other)}`
}

/** トークンつきで JSON を読む（状態コードを確かめる） */
async function getJson(request: APIRequestContext, token: string, path: string): Promise<unknown> {
  const response = await request.get(path, { headers: { Authorization: `Bearer ${token}` } })
  expect(
    response.status(),
    `GET の状態コード（${path.replace(/\d+/g, '{id}').split('?')[0]}）`,
  ).toBe(200)
  return response.json()
}

/** 木の中に、テーブルを指す項目があるか */
function hasTableItem(items: unknown, schema: string, table: string): boolean {
  if (!Array.isArray(items)) {
    return false
  }
  return items.some((item) => {
    const node = item as {
      table?: { schemaName?: unknown; tableName?: unknown } | null
      items?: unknown
    }
    return (
      (node.table?.schemaName === schema && node.table.tableName === table) ||
      hasTableItem(node.items, schema, table)
    )
  })
}

test.describe('150 role permission flow (F)', () => {
  test('create a role, set a table permission, assign a user and see the menu', async ({
    page,
    request,
  }) => {
    test.setTimeout(240_000)
    const runTag = newUserAdminRunTag()
    const tag = runTag.toUpperCase()
    const schema = `E2EF_${tag}`
    const table = `E2EF_T1_${tag}`
    const otherTable = `E2EF_T2_${tag}`
    const roleName = `E2EF_ROLE_${tag}`
    const email = `u7-perf-${runTag}@example.com`
    recordSecretValues([
      { kind: 'runTag', value: runTag },
      { kind: 'email', value: email },
      { kind: 'displayName', value: REGISTERED_DISPLAY_NAME },
    ])

    const adminToken = await requestAdminAccessToken(request)
    const auth = { Authorization: `Bearer ${adminToken}` }

    await test.step('1. apply the E2EF DSL', async () => {
      const preview = await request.post('/api/admin/dsl/preview?source=PASTE', {
        headers: { ...auth, 'Content-Type': 'application/yaml' },
        data: flowDsl(schema, table, otherTable),
      })
      expect(preview.status(), 'DSL の投入の状態コード').toBe(201)
      const { previewId } = (await preview.json()) as { previewId: string }
      const applied = await request.post('/api/admin/dsl/apply', {
        headers: auth,
        data: { previewId },
      })
      expect(applied.status(), 'DSL の適用の状態コード').toBe(200)
    })

    const user = await test.step('2. prepare a registered user', async () => {
      const unavailable = await registrationPrerequisites(request, adminToken)
      if (unavailable !== undefined) {
        test.info().annotations.push({ type: 'skip-reason', description: unavailable })
        return undefined
      }
      const created = await createRegisteredUser(request, runTag)
      recordSecretValues([{ kind: 'password', value: created.password }])
      expect(created.email === email, '利用者の宛先が runTag から作った形').toBe(true)
      return created
    })
    if (user === undefined) {
      test.skip(true, 'registration prerequisites are not available')
      return
    }

    let roleId = 0
    await test.step('3. create the role on S3', async () => {
      await loginAsAdmin(page)
      await openSidebarItem(page, 'ロール')
      await page.getByTestId('roleadmin-create').click()
      await page.getByTestId('roleadmin-name-input').fill(roleName)
      await page.getByTestId('roleadmin-name-submit').click()
      await expect(page.getByTestId('roleadmin-notice')).toContainText('を作りました')
      // 作ったロールの ID を一覧の API でたどる（ロールの ID の順で、作ったロールは最後のページにある）。
      for (let listPage = 1; roleId === 0; listPage += 1) {
        const body = (await getJson(request, adminToken, `/api/admin/roles?page=${listPage}`)) as {
          items: { roleId: number; name: string }[]
          total: number
        }
        roleId = body.items.find((item) => item.name === roleName)?.roleId ?? 0
        expect(body.items.length > 0 || roleId !== 0, '作ったロールが一覧にある').toBe(true)
      }
    })

    await test.step('4. set READ on the table on S4', async () => {
      await page.goto(`/admin/roles/${roleId}`)
      await page.getByRole('button', { name: table, exact: true }).click()
      await page.getByRole('combobox', { name: `${table}の主権限` }).selectOption('READ')
      await page.getByTestId('roleadmin-save').click()
      await expect(page.getByTestId('roleadmin-perm-notice')).toContainText('保存しました')
      await expect(page.getByTestId('roleadmin-node-row')).toContainText('主権限: READ（明示）')
    })

    await test.step('5. assign the user on S5', async () => {
      await page.getByRole('tab', { name: '割り当て' }).click()
      await page.getByTestId('roleadmin-add-user').click()
      await page.getByTestId('roleadmin-candidate-input').fill(email)
      await page.keyboard.press('Enter')
      const add = page.getByRole('button', { name: `${REGISTERED_DISPLAY_NAME}を足す` })
      await expect(add).toHaveCount(1)
      await add.click()
      await expect(page.getByTestId('roleadmin-candidate-status')).toContainText('に割り当てました')
      await page.getByTestId('roleadmin-candidate-close').click()
      await expect(page.getByTestId('roleadmin-assigned-users')).toContainText('直接')
    })

    await test.step('6. the user gets the working role and the menu item', async () => {
      const login = await request.post('/api/auth/login', {
        data: { email: user.email, password: user.password },
      })
      expect(login.status(), '利用者のログインの状態コード').toBe(200)
      const { accessToken } = (await login.json()) as { accessToken: string }
      const workRole = (await getJson(request, accessToken, '/api/me/work-role')) as {
        current: { roleId: number } | null
      }
      expect(workRole.current?.roleId, '作業ロールが作ったロール').toBe(roleId)
      const navigation = (await getJson(request, accessToken, '/api/me/navigation')) as {
        items: unknown
      }
      expect(
        hasTableItem(navigation.items, schema, table),
        '業務のメニューにテーブルの項目が出る',
      ).toBe(true)
    })

    const role2Name = `E2EF_ROLE2_${tag}`
    await test.step('6a. create a second role without T and assign the user', async () => {
      const created = await request.post('/api/admin/roles', {
        headers: auth,
        data: { name: role2Name },
      })
      expect(created.status(), '2つ目のロールの作成の状態コード').toBe(201)
      const { roleId: role2Id } = (await created.json()) as { roleId: number }
      const userList = (await getJson(
        request,
        adminToken,
        `/api/admin/users?page=1&q=${encodeURIComponent(email)}`,
      )) as { items: { userId: number }[] }
      const assigned = await request.post(`/api/admin/roles/${role2Id}/assignments`, {
        headers: auth,
        data: { userId: userList.items[0]?.userId ?? 0 },
      })
      expect(assigned.status(), '2つ目のロールの割り当ての状態コード').toBe(204)
    })

    const workRoleTrigger = () => page.getByTestId('work-role-switcher').getByRole('button')
    const tableItem = page.getByTestId('sidebar-nav-biz:0')

    await test.step('6b. the user sees two roles on the topbar and the T item', async () => {
      await openUserMenuItem(page, 'ログアウト')
      await expect(page.getByTestId('login-layout')).toBeVisible()
      await loginWithForm(page, user.email, user.password)
      await expect(workRoleTrigger()).toHaveAccessibleName(`作業ロール: ${roleName}`)
      await expect(tableItem).toBeVisible()
      await workRoleTrigger().click()
      await expect(
        page.getByRole('menuitem', { name: `${roleName}（使用中）`, exact: true }),
      ).toBeVisible()
      await expect(page.getByRole('menuitem', { name: role2Name, exact: true })).toBeVisible()
    })

    await test.step('6c. switching to the second role removes the T item', async () => {
      await page.getByRole('menuitem', { name: role2Name, exact: true }).click()
      await expect(page.getByTestId('toast-container')).toContainText(
        `作業ロールを${role2Name}に切り替えました`,
      )
      await expect(workRoleTrigger()).toHaveAccessibleName(`作業ロール: ${role2Name}`)
      await expect(tableItem).toHaveCount(0)
    })

    await test.step('6d. switching back to the first role shows the T item', async () => {
      await workRoleTrigger().click()
      await page.getByRole('menuitem', { name: roleName, exact: true }).click()
      await expect(workRoleTrigger()).toHaveAccessibleName(`作業ロール: ${roleName}`)
      await expect(tableItem).toBeVisible()
    })

    await test.step('6e. the work role sample of 160 has the shape of the real response', async () => {
      const login = await request.post('/api/auth/login', {
        data: { email: user.email, password: user.password },
      })
      expect(login.status(), '利用者のログインの状態コード').toBe(200)
      const { accessToken } = (await login.json()) as { accessToken: string }
      const real = await getJson(request, accessToken, '/api/me/work-role')
      const found = shapeCompare(real, workRoleWire(THREE_ROLES, THREE_ROLES[0] ?? null), NULLABLE)
      test.info().annotations.push({
        type: 'work-role-shape-differences',
        description: JSON.stringify(found),
      })
      expect(found, '作業ロールの見本と本物の応答の項目の名前と型の違い').toEqual([])
    })

    await test.step('7. the samples of 140 have the shape of the real responses', async () => {
      const differences: Record<string, string[]> = {}
      const compare = (name: string, actual: unknown, sample: unknown) => {
        const found = shapeCompare(actual, sample, NULLABLE)
        if (found.length > 0) {
          differences[name] = found
        }
      }
      const read = (path: string) => getJson(request, adminToken, path)
      const rolePath = `/api/admin/roles/${roleId}`
      const query = (names: Record<string, string>) => new URLSearchParams(names).toString()
      compare('roles', await read('/api/admin/roles?page=1'), roleListWire())
      compare('role', await read(rolePath), roleWire())
      compare('schemas', await read(`${rolePath}/permissions/schemas`), schemaNodesWire())
      compare(
        'tables',
        await read(`${rolePath}/permissions/tables?${query({ schema })}`),
        tableNodesWire(),
      )
      compare(
        'columns',
        await read(`${rolePath}/permissions/columns?${query({ schema, table })}`),
        columnNodesWire(),
      )
      const userList = (await read(`/api/admin/users?page=1&q=${encodeURIComponent(email)}`)) as {
        items: { userId: number }[]
      }
      compare('users', userList, candidateUsersWire())
      const userId = userList.items[0]?.userId ?? 0

      const created = await request.post('/api/admin/groups', {
        headers: auth,
        data: { name: `E2EF_GROUP_${tag}` },
      })
      expect(created.status(), 'グループの作成の状態コード').toBe(201)
      const { groupId } = (await created.json()) as { groupId: number }
      const member = await request.post(`/api/admin/groups/${groupId}/members`, {
        headers: auth,
        data: { userId },
      })
      expect(member.status(), 'メンバーを足す状態コード').toBe(204)
      const assigned = await request.post(`${rolePath}/assignments`, {
        headers: auth,
        data: { groupId },
      })
      expect(assigned.status(), 'グループへの割り当ての状態コード').toBe(204)
      compare('assignments', await read(`${rolePath}/assignments`), assignmentsWire())
      compare('userRoles', await read(`/api/admin/users/${userId}/roles`), userRolesWire())
      compare('groups', await read('/api/admin/groups?page=1'), groupListWire())
      compare('group', await read(`/api/admin/groups/${groupId}`), groupDetailWire())
      compare('groupRoles', await read(`/api/admin/groups/${groupId}/roles`), groupRolesWire())

      const yaml = `version: 1\nroles:\n  E2EF_NEW_${tag}:\n    schemas:\n      ${schema}:\n        main: READ\n        tables:\n          ${table}:\n            main: FULL\n`
      const checked = await request.post('/api/admin/role-transfer/check', {
        headers: { ...auth, 'Content-Type': 'application/yaml' },
        data: yaml,
      })
      expect(checked.status(), '確かめの状態コード').toBe(200)
      compare('check', await checked.json(), transferCheckWire(1, 3))

      test
        .info()
        .annotations.push({ type: 'shape-differences', description: JSON.stringify(differences) })
      expect(differences, '見本と本物の応答の項目の名前と型の違い').toEqual({})
    })
  })
})
