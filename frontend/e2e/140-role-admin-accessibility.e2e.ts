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
// ビルドした WAR で、U6 の画面（S3〜S7・S9）を表示の設定の組ごとに実際のブラウザで確かめ、画面の時間を測って記録する
// （Intent 261004-role-menu の U6、計画 9.1、tech-stack-decisions.md NFR4.4・NFR4.5、performance-design.md 4節、
// cicd-pipeline.md 7節）。
// - 流れの E2E ではないため、team.md の「機能の Intent ごとに代表の流れを1本まで」に数えない（流れは 150）。
// - 組ごとに1つのテスト（support/displayCombos.ts の 20 組）で、S3 の一覧・開いた Dropdown・名前の誤りの Modal、S4 の保存の
//   誤り（BUSY）・未保存の確かめ・今の DSL に無い印、S5 の候補の Modal（利用停止・割り当て済み）・外すときの確かめ（D19）、
//   S6 の詳細とメンバーの外しの Alert（D20）、S7 の結果の行を開いた状態と誤りの一覧、S9 の Modal を順に作り、axe の違反 0 件・
//   REQUIRED_RULES が流れた・画面全体の横のはみ出しなし・開いた Dropdown と Modal が表示の中に収まることを確かめる。
//   見本は 2語の氏名・64 文字のロール名・長いメールアドレス（example.com）・< と & を含む名前を持つ。
// - 管理の API はすべて差し替えの口（support/adminApiRoute.ts の inspect）で受け、本物の利用者・ロール・グループを作らず、
//   書き換えをサーバーへ届けない。各テストの終わりに expectNoBlockedWrites。差し替えた失敗に Chrome が出す
//   「Failed to load resource:」は、位置が管理の API の道のものだけを数えて引く（120 と同じ扱い）。
// - 幅（360px・768px・1280px）、道の扱い（深い道の読み込み直し・戻る・進む・未保存の戻る）、画面の時間の測りは既定の1組
//   （light・md・blue）だけ。測りの値は記録だけで成否にしない（NFR2.9）。書き出しは押せる形だけを見る（13節 Q2: A）。
// - test.step の題・注記・添付に、氏名・メールアドレス・パスワード・トークンを入れない（組と状態の名前と件数・時間だけ）。
import { expect, test, type ConsoleMessage, type Page } from '@playwright/test'
import { loginAsAdmin, openSidebarItem } from './support/adminLogin'
import {
  expectNoBlockedWrites,
  isAdminApiPath,
  normalizeAdminPath,
  routeAdminApi,
  type AdminApiRoute,
} from './support/adminApiRoute'
import { missingRequiredRules, runAxe } from './support/axe'
import {
  DISPLAY_COMBOS,
  expectComboApplied,
  prepareCombo,
  type DisplayCombo,
} from './support/displayCombos'
import { routeLoginPreferences } from './support/loginPreferences'
import {
  describeContainment,
  describeOverflow,
  measureHorizontalOverflow,
  measureViewportContainment,
} from './support/overflow'
import { watchPage, type PageWatch } from './support/pageProblems'
import {
  assignmentsWire,
  candidateUsersWire,
  columnName,
  columnNodesWire,
  conflictWire,
  DIRECT_USER_ID,
  groupDetailWire,
  groupListWire,
  groupRolesWire,
  LONG_ROLE_NAME,
  roleListWire,
  roleWire,
  SAMPLE_GROUP_ID,
  SAMPLE_ROLE_ID,
  schemaNodesWire,
  SAMPLE_SCHEMA,
  SAMPLE_TABLE,
  SYMBOL_NAME,
  tableNodesWire,
  transferCheckWire,
  transferInvalidProblem,
  TWO_WORD_NAME,
  userRolesWire,
  type PermissionNodesWire,
  type UserRoleWire,
} from './support/roleAdminFixtures'
import { e2eTraceMode } from './support/traceMode'
import {
  armMeasure,
  readHeapBytes,
  readMeasure,
  summarizeRuns,
  type MeasureRecord,
  type MeasureStart,
} from './support/u6Measure'

test.use({ trace: e2eTraceMode() })

/** 検査の状態の名前 */
type InspectionState =
  | 'S3-list'
  | 'S3-menu-open'
  | 'S3-name-error'
  | 'S4-save-busy'
  | 'S4-leave-confirm'
  | 'S4-orphan'
  | 'S5-candidates'
  | 'S5-removal-confirm'
  | 'S6-member-removed'
  | 'S7-result-open'
  | 'S7-errors'
  | 'S9-roles'

/** 見本の状態（テストの中で替える） */
interface MockState {
  /** 利用者のロールの応答（S6 の D20 の確かめで替える） */
  userRoles: UserRoleWire[]
  /** 保存の応答 */
  save: { status: number; json?: unknown }
  /** 確かめの応答 */
  check: { status: number; json: unknown }
}

/** 管理の API の見本を張る（S3〜S7・S9 で使うすべて）。 */
export function mockU6Api(route: AdminApiRoute, state: MockState): void {
  route.setMock('GET', '/api/admin/roles', { status: 200, json: roleListWire() })
  route.setMock('GET', '/api/admin/roles/{id}', { status: 200, json: roleWire() })
  route.setMock('GET', '/api/admin/roles/{id}/permissions/schemas', {
    status: 200,
    json: schemaNodesWire(),
  })
  route.setMock('GET', '/api/admin/roles/{id}/permissions/tables', {
    status: 200,
    json: tableNodesWire(),
  })
  route.setMock('GET', '/api/admin/roles/{id}/permissions/columns', (url) => ({
    status: 200,
    json: columnNodesWire(5, url.searchParams.get('table') !== 'OLD_TABLE'),
  }))
  route.setMock('PUT', '/api/admin/roles/{id}/permissions', () => state.save)
  route.setMock('GET', '/api/admin/roles/{id}/assignments', {
    status: 200,
    json: assignmentsWire(),
  })
  route.setMock('GET', '/api/admin/users', { status: 200, json: candidateUsersWire() })
  route.setMock('GET', '/api/admin/users/{id}/roles', () => ({
    status: 200,
    json: state.userRoles,
  }))
  route.setMock('GET', '/api/admin/groups', { status: 200, json: groupListWire() })
  route.setMock('GET', '/api/admin/groups/{id}', { status: 200, json: groupDetailWire() })
  route.setMock('GET', '/api/admin/groups/{id}/roles', { status: 200, json: groupRolesWire() })
  route.setMock('DELETE', '/api/admin/groups/{id}/members/{id}', { status: 204 })
  route.setMock('POST', '/api/admin/role-transfer/check', () => state.check)
}

/** 見本の状態の初め */
export function initialMockState(): MockState {
  return {
    userRoles: userRolesWire(),
    save: { status: 409, json: conflictWire('ROLE_BUSY') },
    check: { status: 200, json: transferCheckWire() },
  }
}

/** 差し替えた失敗に出る読み込みの失敗の表示を集める（watchPage の後に張る。120 と同じ扱い）。 */
export function watchExcludedConsole(page: Page): string[] {
  const excludedTexts: string[] = []
  page.on('console', (message: ConsoleMessage) => {
    if (message.type() !== 'error' || !message.text().startsWith('Failed to load resource:')) {
      return
    }
    let pathname = ''
    try {
      pathname = new URL(message.location().url).pathname
    } catch {
      return
    }
    if (isAdminApiPath(normalizeAdminPath(pathname))) {
      excludedTexts.push(`console: ${message.text()}`)
    }
  })
  return excludedTexts
}

/** 問題の一覧の写しから、除く表示を1件ずつ取り除いた残りを返す。 */
function withoutExcluded(problems: readonly string[], excluded: readonly string[]): string[] {
  const rest = [...problems]
  for (const text of excluded) {
    const index = rest.indexOf(text)
    if (index >= 0) {
      rest.splice(index, 1)
    }
  }
  return rest
}

/** 1つの状態を検査し、注記に件数と規則の名前だけを残す。 */
async function inspect(page: Page, label: string, state: string): Promise<void> {
  const summary = await runAxe(page)
  const overflow = await measureHorizontalOverflow(page)
  const violationRules = [...new Set(summary.violations.map((violation) => violation.id))]
  test.info().annotations.push({
    type: 'axe',
    description: JSON.stringify({ combo: label, state, violations: violationRules, overflow }),
  })
  const name = `${label} ${state}`
  expect(missingRequiredRules(summary), `${name}: 流れなかった規則`).toEqual([])
  expect(violationRules, `${name}: 違反`).toEqual([])
  expect(overflow.overflows, describeOverflow(name, overflow)).toBe(false)
}

/** 開いたメニュー・表示（Modal）の矩形が表示の中に収まることを確かめる。 */
async function expectContained(page: Page, role: 'menu' | 'dialog' | 'alertdialog', name: string) {
  const target = page.getByRole(role).last()
  await expect(target).toBeVisible()
  const measured = await measureViewportContainment(target)
  test.info().annotations.push({ type: 'rect', description: JSON.stringify({ name, ...measured }) })
  expect(measured.contained, describeContainment(name, measured)).toBe(true)
}

/** 終わりの確かめ（口・コンソール・CSP） */
async function finish(
  page: Page,
  route: AdminApiRoute,
  watch: PageWatch,
  excludedTexts: readonly string[],
  label: string,
): Promise<void> {
  await page.waitForLoadState('networkidle')
  const remaining = withoutExcluded(watch.problems, excludedTexts)
  test.info().annotations.push({
    type: 'u6-problems',
    description: JSON.stringify({
      combo: label,
      routeSeen: route.seen,
      blocked: route.blocked.length,
      failedReplies: route.failedReplies,
      excludedConsole: excludedTexts.length,
      remainingProblems: remaining.length,
      cspViolations: watch.cspViolations.length,
    }),
  })
  expectNoBlockedWrites(route, label)
  expect(excludedTexts.length, `${label}: 除いた表示の件数`).toBeLessThanOrEqual(
    route.failedReplies,
  )
  expect(watch.cspViolations, `${label}: CSP の違反`).toEqual([])
  expect(remaining, `${label}: 画面の問題`).toEqual([])
}

/** 管理者でログインし、見本の口を張った状態にする。 */
async function startU6(page: Page, combo: DisplayCombo) {
  const { baseURL } = test.info().project.use
  const watch = await watchPage(page)
  const excludedTexts = watchExcludedConsole(page)
  const route = await routeAdminApi(page, baseURL ?? '', 'inspect')
  const state = initialMockState()
  mockU6Api(route, state)
  await prepareCombo(page, combo)
  await routeLoginPreferences(page, { theme: combo.theme, fontSize: combo.fontSize })
  await loginAsAdmin(page)
  await expectComboApplied(page, combo)
  return { watch, excludedTexts, route, state }
}

/** S7 で選ぶファイル（本文は確かめの見本で答えるため中身を問わない） */
export const TRANSFER_FILE = {
  name: 'roles.yaml',
  mimeType: 'application/yaml',
  buffer: Buffer.from('version: 1\nroles: []\n', 'utf8'),
}

/** 既定の組（light・md・blue・既定の幅） */
export function defaultCombo(): DisplayCombo {
  const combo = DISPLAY_COMBOS.find(
    (candidate) =>
      candidate.theme === 'light' &&
      candidate.fontSize === 'md' &&
      candidate.brandColor === 'blue' &&
      candidate.viewport === undefined,
  )
  if (combo === undefined) {
    throw new Error('既定の組（light・md・blue・既定の幅）が無い')
  }
  return combo
}

test.describe('140 role admin accessibility on the built WAR', () => {
  for (const combo of DISPLAY_COMBOS) {
    test(`role admin ${combo.name}`, async ({ page }) => {
      // 12 の状態ごとに axe を流すため、既定の 30 秒を超える。
      test.setTimeout(180_000)
      const { watch, excludedTexts, route, state } = await startU6(page, combo)
      const check = (s: InspectionState) => inspect(page, combo.name, s)

      await test.step('S3 list, menu and name error', async () => {
        await openSidebarItem(page, 'ロール')
        await expect(page.getByTestId('roleadmin-table').locator('tbody tr')).toHaveCount(3)
        await check('S3-list')
        await page.getByRole('button', { name: `操作: ${LONG_ROLE_NAME}` }).click()
        await check('S3-menu-open')
        await expectContained(page, 'menu', `${combo.name} S3-menu-open`)
        await page.keyboard.press('Escape')
        await page.getByTestId('roleadmin-create').click()
        await page.getByTestId('roleadmin-name-submit').click()
        await expect(page.getByText('名前を入れてください。')).toBeVisible()
        await check('S3-name-error')
        await expectContained(page, 'dialog', `${combo.name} S3-name-error`)
        await page.getByTestId('roleadmin-name-cancel').click()
        await expect(page.getByRole('dialog')).toHaveCount(0)
      })

      await test.step('S4 save busy, leave confirmation and orphan', async () => {
        await page.getByRole('link', { name: '営業を開く' }).click()
        await page.getByRole('button', { name: '受注明細', exact: true }).click()
        await expect(page.getByTestId('roleadmin-column-table')).toBeVisible()
        await page.getByRole('combobox', { name: '受注明細の主権限' }).selectOption('FULL')
        await page.getByTestId('roleadmin-save').click()
        await expect(page.getByTestId('roleadmin-perm-notice')).toContainText(
          'ほかの操作と重なりました',
        )
        await check('S4-save-busy')
        await page.getByRole('tab', { name: '割り当て' }).click()
        await expect(page.getByRole('alertdialog', { name: '保存していない変更' })).toBeVisible()
        await check('S4-leave-confirm')
        await expectContained(page, 'alertdialog', `${combo.name} S4-leave-confirm`)
        await page.getByTestId('roleadmin-leave-stay').click()
        await expect(page.getByRole('alertdialog')).toHaveCount(0)
        await page.getByTestId('roleadmin-discard').click()
        await page.getByRole('button', { name: 'OLD_TABLE', exact: true }).click()
        await expect(page.getByTestId('roleadmin-node-row')).toContainText(
          'この設定は今の DSL に無いため',
        )
        await check('S4-orphan')
      })

      await test.step('S5 candidates and removal confirmation', async () => {
        await page.getByRole('tab', { name: '割り当て' }).click()
        await expect(page.getByTestId('roleadmin-assigned-users')).toBeVisible()
        await page.getByTestId('roleadmin-add-user').click()
        await page.getByTestId('roleadmin-candidate-input').fill('kensa')
        await page.keyboard.press('Enter')
        await expect(page.getByTestId(`roleadmin-candidate-row-${DIRECT_USER_ID}`)).toContainText(
          '割り当て済み',
        )
        await check('S5-candidates')
        await expectContained(page, 'dialog', `${combo.name} S5-candidates`)
        await page.getByTestId('roleadmin-candidate-close').click()
        await expect(page.getByRole('dialog')).toHaveCount(0)
        await page.getByRole('button', { name: `${TWO_WORD_NAME}を外す` }).click()
        await expect(page.getByRole('alertdialog', { name: '割り当てを外す' })).toContainText(
          '経理',
        )
        await check('S5-removal-confirm')
        await page.getByTestId('roleadmin-removal-cancel').click()
        await expect(page.getByRole('alertdialog')).toHaveCount(0)
      })

      await test.step('S6 detail and member removal notice', async () => {
        await openSidebarItem(page, 'グループ')
        await page.getByRole('link', { name: `${SYMBOL_NAME}を開く` }).click()
        await expect(page.getByTestId('groupadmin-members')).toBeVisible()
        state.userRoles = userRolesWire().filter((role) => role.roleId !== SAMPLE_ROLE_ID)
        await page.getByRole('button', { name: `${TWO_WORD_NAME}を外す` }).click()
        await expect(page.getByTestId('groupadmin-detail-notice')).toContainText(
          'グループ経由のロール: 営業',
        )
        await check('S6-member-removed')
        state.userRoles = userRolesWire()
      })

      await test.step('S7 opened result and error list', async () => {
        await openSidebarItem(page, '権限の受け渡し')
        await expect(page.getByTestId('roletransfer-export')).toBeEnabled()
        await page.getByLabel('ファイルを選ぶ').setInputFiles(TRANSFER_FILE)
        await page.getByTestId('roletransfer-check').click()
        await page.getByRole('button', { name: `${SYMBOL_NAME}の変わる点を開く` }).click()
        await expect(page.getByTestId('roletransfer-change-row').first()).toBeVisible()
        await check('S7-result-open')
        state.check = { status: 400, json: transferInvalidProblem() }
        await page.getByTestId('roletransfer-check').click()
        await expect(page.getByTestId('roletransfer-errors-alert')).toBeFocused()
        await check('S7-errors')
        state.check = { status: 200, json: transferCheckWire() }
      })

      await test.step('S9 roles of a user', async () => {
        await openSidebarItem(page, '利用者の管理')
        await page.getByTestId('useradmin-row-actions-30').getByRole('button').click()
        await page.getByRole('menuitem', { name: 'ロールを見る', exact: true }).click()
        await expect(page.getByRole('list', { name: 'ロールの一覧' })).toBeVisible()
        await check('S9-roles')
        await expectContained(page, 'dialog', `${combo.name} S9-roles`)
        await page.getByTestId('useradmin-roles-close').click()
        await expect(page.getByTestId('useradmin-row-actions-30').getByRole('button')).toBeFocused()
      })

      await finish(page, route, watch, excludedTexts, combo.name)
    })
  }
})

/** 画面の幅（NFR4.5） */
const WIDTHS = [360, 768, 1280] as const

test.describe('140 role admin widths, paths and screen time (default combo)', () => {
  for (const width of WIDTHS) {
    test(`role admin width ${width}px`, async ({ page }) => {
      test.setTimeout(120_000)
      const combo = defaultCombo()
      await page.setViewportSize({ width, height: 900 })
      const { watch, excludedTexts, route } = await startU6(page, combo)
      const label = `${width}px`
      const narrow = width < 768

      await openSidebarItem(page, 'ロール')
      await expect(page.getByTestId('roleadmin-table').locator('tbody tr').first()).toBeVisible()
      await inspect(page, label, 'S3-list')
      // 表の右端の行の操作のメニューが、360px を含む幅で画面に収まる（make-you-chic-ui a9309c4 の Dropdown の既定の
      // max-width。Intent 261015-deps-ui-carryover の FR2.2・FR2.5）。
      await page.getByRole('button', { name: `操作: ${LONG_ROLE_NAME}` }).click()
      await expectContained(page, 'menu', `${label} S3-menu-open`)
      await page.keyboard.press('Escape')
      await expect(page.getByRole('menu')).toHaveCount(0)
      await page.getByTestId('roleadmin-create').click()
      await expectContained(page, 'dialog', `${label} S3-name-dialog`)
      if (narrow) {
        // 768px 未満では Modal は幅いっぱい（左右 16px）。
        const box = await page.getByRole('dialog').boundingBox()
        expect(box, `${label}: Modal の矩形`).not.toBeNull()
        expect(Math.round(box?.x ?? 0), `${label}: Modal の左の余白`).toBeLessThanOrEqual(16)
        expect(
          Math.round(width - (box?.x ?? 0) - (box?.width ?? 0)),
          `${label}: Modal の右の余白`,
        ).toBeLessThanOrEqual(16)
      }
      await page.getByTestId('roleadmin-name-cancel').click()

      await page.getByRole('link', { name: '営業を開く' }).click()
      await page.getByRole('button', { name: '受注明細', exact: true }).click()
      await expect(page.getByTestId('roleadmin-column-table')).toBeVisible()
      const tree = await page.getByTestId('shared-tree').boundingBox()
      const table = await page.getByTestId('roleadmin-perm-table').boundingBox()
      if (narrow) {
        await expect(page.getByRole('heading', { name: 'テーブルの設定: 受注明細' })).toBeFocused()
        expect(
          (tree?.y ?? 0) + (tree?.height ?? 0),
          `${label}: 木と表が縦に積まれる`,
        ).toBeLessThanOrEqual(table?.y ?? 0)
      } else {
        expect(tree?.x ?? 0, `${label}: 木が表の左にある`).toBeLessThan(table?.x ?? 0)
      }
      await inspect(page, label, 'S4-table')

      await openSidebarItem(page, 'グループ')
      await page.getByRole('link', { name: `${SYMBOL_NAME}を開く` }).click()
      await expect(page.getByTestId('groupadmin-members')).toBeVisible()
      await inspect(page, label, 'S6-detail')

      await openSidebarItem(page, '権限の受け渡し')
      await page.getByLabel('ファイルを選ぶ').setInputFiles(TRANSFER_FILE)
      await page.getByTestId('roletransfer-check').click()
      await page.getByRole('button', { name: `${SYMBOL_NAME}の変わる点を開く` }).click()
      await expect(page.getByTestId('roletransfer-change-row').first()).toBeVisible()
      // 一覧と結果の表の1列目（ロールの名前）が見える。
      await expect(page.getByTestId('roletransfer-plan-0').locator('th')).toBeInViewport()
      await inspect(page, label, 'S7-result-open')

      await finish(page, route, watch, excludedTexts, label)
    })
  }

  test('role admin paths: reload deep paths, back and forward, and back with unsaved changes', async ({
    page,
  }) => {
    test.setTimeout(120_000)
    const { watch, excludedTexts, route } = await startU6(page, defaultCombo())

    await page.goto(`/admin/roles/${SAMPLE_ROLE_ID}`)
    await expect(page.getByRole('heading', { level: 1, name: 'ロール: 営業' })).toBeVisible()
    await page.reload()
    await expect(page.getByRole('heading', { level: 1, name: 'ロール: 営業' })).toBeVisible()
    await page.goto(`/admin/groups/${SAMPLE_GROUP_ID}`)
    await expect(page.getByTestId('groupadmin-members')).toBeVisible()
    await page.reload()
    await expect(page.getByTestId('groupadmin-members')).toBeVisible()

    await openSidebarItem(page, 'ロール')
    await page.getByRole('link', { name: '営業を開く' }).click()
    await expect(page.getByRole('tab', { name: '権限の設定' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    await page.getByRole('tab', { name: '割り当て' }).click()
    await expect(page).toHaveURL(new RegExp(`/admin/roles/${SAMPLE_ROLE_ID}/assignments$`))
    await page.goBack()
    await expect(page).toHaveURL(new RegExp(`/admin/roles/${SAMPLE_ROLE_ID}$`))
    await expect(page.getByRole('tab', { name: '権限の設定' })).toHaveAttribute(
      'aria-selected',
      'true',
    )
    await page.goBack()
    await expect(page).toHaveURL(/\/admin\/roles$/)
    await expect(page.getByTestId('roleadmin-list-page')).toBeVisible()
    await page.goForward()
    await expect(page.getByRole('heading', { level: 1, name: 'ロール: 営業' })).toBeVisible()

    await page.getByRole('combobox', { name: '販売DBの主権限' }).selectOption('FULL')
    await page.goBack()
    await expect(page.getByRole('alertdialog', { name: '保存していない変更' })).toBeVisible()
    await page.getByTestId('roleadmin-leave-stay').click()
    await expect(page).toHaveURL(new RegExp(`/admin/roles/${SAMPLE_ROLE_ID}$`))
    await page.getByTestId('roleadmin-discard').click()

    await finish(page, route, watch, excludedTexts, 'paths')
  })

  test('role admin screen time with the default combo', async ({ page }) => {
    // 項目ごとに 5 回、画面を読み込み直して測る。大きな見本（25 ロール × 1万）を含むため長くかかる。
    test.setTimeout(900_000)
    const { watch, route } = await startU6(page, defaultCombo())
    const records: MeasureRecord[] = []
    const RUNS = 5
    const columns100 = JSON.stringify(columnNodesWire(100, false))
    const columns1000 = JSON.stringify(columnNodesWire(1000, false))
    const bigCheck = JSON.stringify(transferCheckWire(25, 10_000))
    let columnsBody = columns100
    let savedMain: 'NONE' | 'READ' | 'FULL' | null = null
    let pendingMain: 'NONE' | 'READ' | 'FULL' | null = null
    const tablesWithMain = (): PermissionNodesWire => {
      const wire = tableNodesWire()
      const first = wire.items[0]
      if (first !== undefined && savedMain !== null) {
        first.explicit = { main: savedMain, create: null, delete: null }
        first.effective = { main: savedMain, create: false, delete: false }
        first.inheritedFrom = 'EXPLICIT'
      }
      return wire
    }
    route.setMock('GET', '/api/admin/roles/{id}/permissions/columns', () => ({
      status: 200,
      body: columnsBody,
    }))
    route.setMock('GET', '/api/admin/roles/{id}/permissions/tables', () => ({
      status: 200,
      json: tablesWithMain(),
    }))
    route.setMock('PUT', '/api/admin/roles/{id}/permissions', () => {
      savedMain = pendingMain
      return { status: 204 }
    })
    route.setMock('POST', '/api/admin/role-transfer/check', () => ({ status: 200, body: bigCheck }))

    const openRole = async () => {
      await page.goto(`/admin/roles/${SAMPLE_ROLE_ID}`)
      await expect(page.getByRole('button', { name: '受注明細', exact: true })).toBeVisible()
    }
    const measureOnce = async (
      start: MeasureStart,
      ends: Parameters<typeof armMeasure>[2],
      act: () => Promise<void>,
      timeout?: number,
    ) => {
      await armMeasure(page, start, ends)
      await act()
      return readMeasure(page, timeout)
    }
    const tableSelect = '[data-testid="shared-tree-select"]'

    await test.step('NFR2.5 select a table with 100 columns (and 1,000 recorded once)', async () => {
      const runs: number[] = []
      for (let run = 0; run < RUNS; run += 1) {
        await openRole()
        runs.push(
          await measureOnce(
            { kind: 'click', selector: tableSelect },
            [{ testId: `roleadmin-column-${columnName(99)}` }],
            () => page.getByRole('button', { name: '受注明細', exact: true }).click(),
          ),
        )
      }
      records.push(summarizeRuns('NFR2.5 100 columns', 'click', runs, 1000, RUNS))
      columnsBody = columns1000
      await openRole()
      const big = await measureOnce(
        { kind: 'click', selector: tableSelect },
        [{ testId: `roleadmin-column-${columnName(999)}` }],
        () => page.getByRole('button', { name: '受注明細', exact: true }).click(),
        60_000,
      )
      records.push(summarizeRuns('NFR2.5 1,000 columns', 'click', [big], null, 1))
    })

    await test.step('NFR2.12 change a select among 1,000 columns (once)', async () => {
      const time = await measureOnce(
        { kind: 'change', selector: 'select[aria-label="受注番号の主権限"]' },
        [{ testId: 'roleadmin-unsaved-count', text: '1 件' }],
        async () => {
          await page.getByRole('combobox', { name: '受注番号の主権限' }).selectOption('FULL')
        },
      )
      records.push(summarizeRuns('NFR2.12 1,000 columns change', 'change', [time], null, 1))
      await page.getByTestId('roleadmin-discard').click()
      columnsBody = columns100
    })

    await test.step('NFR2.6 save and reload with 100 columns', async () => {
      const values = ['NONE', 'READ', 'FULL', 'NONE', 'READ'] as const
      const runs: number[] = []
      let startKind: MeasureStart['kind'] = 'responseEnd'
      let note: string | undefined
      for (const value of values) {
        await openRole()
        await page.getByRole('button', { name: '受注明細', exact: true }).click()
        await expect(page.getByTestId('roleadmin-column-table')).toBeVisible()
        await page.getByRole('combobox', { name: '受注明細の主権限' }).selectOption(value)
        pendingMain = value
        const ends = [
          { testId: 'roleadmin-perm-notice', text: '保存しました' },
          {
            testId: `roleadmin-effective-["${SAMPLE_SCHEMA}","${SAMPLE_TABLE}"]`,
            text: `主権限: ${value}（明示）`,
          },
        ]
        const start: MeasureStart =
          startKind === 'responseEnd'
            ? { kind: 'responseEnd', path: `/api/admin/roles/${SAMPLE_ROLE_ID}/permissions` }
            : { kind: 'click', selector: '[data-testid="roleadmin-save"]' }
        try {
          runs.push(
            await measureOnce(
              start,
              ends,
              () => page.getByTestId('roleadmin-save').click(),
              15_000,
            ),
          )
        } catch (error) {
          if (startKind !== 'responseEnd' || runs.length > 0) {
            throw error
          }
          // 差し替えた応答で resource の項目が得られないときは、操作（保存の click）から測る形に切り替える（cicd 7節 (iv)）。
          startKind = 'click'
          note = 'responseEnd が得られず、保存の click から測った'
          savedMain = value
          continue
        }
      }
      records.push(
        summarizeRuns(
          'NFR2.6 save',
          startKind,
          runs,
          1000,
          startKind === 'click' ? RUNS - 1 : RUNS,
          note,
        ),
      )
    })

    await test.step('NFR2.7 check 25 roles x 10,000 changes, NFR2.8 next page of an opened role', async () => {
      const checkRuns: number[] = []
      const nextRuns: number[] = []
      const heaps: (number | null)[] = []
      for (let run = 0; run < RUNS; run += 1) {
        await page.goto('/admin/role-transfer')
        await page.getByLabel('ファイルを選ぶ').setInputFiles(TRANSFER_FILE)
        checkRuns.push(
          await measureOnce(
            { kind: 'responseEnd', path: '/api/admin/role-transfer/check' },
            [{ testId: 'roletransfer-plan-24' }],
            () => page.getByTestId('roletransfer-check').click(),
            120_000,
          ),
        )
        heaps.push(await readHeapBytes(page))
        await page.getByRole('button', { name: `${SYMBOL_NAME}の変わる点を開く` }).click()
        await expect(page.getByTestId('roletransfer-change-row').first()).toBeVisible()
        nextRuns.push(
          await measureOnce(
            { kind: 'click', selector: '[data-testid="roletransfer-changes-next"]' },
            [{ testId: 'roletransfer-change-row', text: 'T00100' }],
            () => page.getByTestId('roletransfer-changes-next').first().click(),
          ),
        )
      }
      records.push(
        summarizeRuns(
          'NFR2.7 check',
          'responseEnd',
          checkRuns,
          3000,
          RUNS,
          `heapBytes=${JSON.stringify(heaps)}`,
        ),
      )
      records.push(summarizeRuns('NFR2.8 next page', 'click', nextRuns, 500, RUNS))
    })

    test.info().annotations.push({ type: 'u6-screen-ms', description: JSON.stringify(records) })
    await test.info().attach('u6-screen-ms', {
      body: JSON.stringify(records, null, 2),
      contentType: 'application/json',
    })
    expectNoBlockedWrites(route, 'screen time')
    expect(watch.cspViolations, 'screen time: CSP の違反').toEqual([])
  })
})
