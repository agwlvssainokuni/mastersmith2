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
// ビルドした WAR で、U7 の骨組みの画面（作業ロールの切り替え・N 階層のサイドバー・S2 の置き場・S8 の自分の権限・ホームの
// 案内）を表示の設定の組ごとに実際のブラウザで確かめ、画面の時間を測って記録する（Intent 261004-role-menu の U7、
// 計画 10.1、logical-components.md 5節、performance-design.md 4節、tech-stack-decisions.md NFR4.3〜NFR4.5）。
// - 流れの E2E ではないため、team.md の「機能の Intent ごとに代表の流れを1本まで」に数えない（流れは 170・150）。
// - 組ごとに1つのテスト（support/displayCombos.ts の 20 組）で、状態を順にたどり、axe の違反 0 件・REQUIRED_RULES が流れた・
//   画面全体の横のはみ出しなし・開いた Dropdown と 5 段のサイドバーの矩形が表示の中に収まることを確かめる。
// - Intent 261015-deps-ui-carryover（make-you-chic-ui a9309c4）: 畳んだ状態で今の項目を含む1段目のまとまりの印（クラスと読み上げの
//   名前）を組ごとに確かめ、axe は畳んだ状態（印が出ている）と開いた状態の両方で流す。英語の表示でトップバーの文言が英語になる。
//   作業ロールの一覧の幅は make-you-chic-ui の Dropdown の既定に任せた（アプリの CSS の上書きを外した後も expectContained が通る）。
// - 骨組みの API はすべて差し替えの口（support/frameApiRoute.ts）で受け、内部DB に書かない。見本は画面の側の型で縛った
//   support/navTreeFixtures.ts（ロールとテーブルの名前だけ）。差し替えた失敗に Chrome が出す「Failed to load resource:」は、
//   位置が骨組みの API・業務データの API の道のものだけを数えて引く（140 と同じ扱い）。
// - Intent 261016-business-data-views の U3（B5）: テーブルの置き場の画面を業務データの一覧の画面に置き換えたため、末端の
//   テーブルは業務データの一覧（S1）で開き、業務データの API（/api/tables/*）は型を付けた1つの見本
//   （support/businessDataFixtures.ts・businessDataApiRoute.ts）で差し替える。見てよくないテーブルの定義には 403 を返す。
//   本数に数えない書き換え。画面の時間の判定 placeholderShown は、一覧の見出しが出た判定 tableListShown に置き換えた。
// - 768px 未満では、作業ロールの切り替えは内容の領域の先頭に置く（依頼者の決定 G2: A。機能設計 D9 との差）。
// - 幅（360px・768px・1280px と悪い側の lg・dark・360px）、英語、画面の時間の測りは既定の1組（light・md・blue）を基にする。
//   測りの値は記録だけで成否にしない（NFR2.8）。
// - test.step の題・注記・添付に、氏名・メールアドレス・パスワード・トークンを入れない（組と状態の名前と件数・時間だけ）。
import { expect, test, type ConsoleMessage, type Page } from '@playwright/test'
import { loginAsAdmin } from './support/adminLogin'
import { missingRequiredRules, runAxe } from './support/axe'
import {
  describeAxeNode,
  labelContentNameProblems,
  type LabelContentNameCategory,
} from './support/axeRuleChecks'
import {
  DISPLAY_COMBOS,
  expectComboApplied,
  prepareCombo,
  type DisplayCombo,
} from './support/displayCombos'
import { fixturePage, leafDefinition } from './support/businessDataFixtures'
import {
  isBusinessDataApiPath,
  routeBusinessDataApi,
  tableResponder,
  type BusinessDataApiRoute,
} from './support/businessDataApiRoute'
import {
  expectFrameRouteClean,
  FRAME_API_PATHS,
  isFrameApiPath,
  jsonReply,
  routeFrameApi,
  type FrameApiRoute,
} from './support/frameApiRoute'
import { routeLoginPreferences } from './support/loginPreferences'
import {
  chainIds,
  emptyNavigation,
  FRAME_SCHEMA,
  inspectionTree,
  LEAF_LABEL,
  LEAF_TABLE,
  LONG_WORK_ROLE,
  myColumnsWire,
  mySchemasWire,
  myTablesWire,
  THREE_ROLES,
  TYPICAL_MARKER_ID,
  typicalTree,
  workRoleWire,
  worstMarkerId,
  worstTree,
} from './support/navTreeFixtures'
import {
  describeContainment,
  describeOverflow,
  measureHorizontalOverflow,
  measureViewportContainment,
} from './support/overflow'
import { watchPage, type PageWatch } from './support/pageProblems'
import { e2eTraceMode } from './support/traceMode'
import { summarizeRuns, type MeasureRecord } from './support/u6Measure'
import { armU7Measure, installU7Done, readU7Measure, type U7DoneName } from './support/u7Measure'

test.use({ trace: e2eTraceMode() })

/** 検査の状態の名前 */
type InspectionState =
  | 'sidebar-5-levels-and-list'
  | 'work-role-menu-3-roles'
  | 'list-forbidden'
  | 'my-permissions-columns'
  | 'home-not-configured'
  | 'home-nothing-visible'
  | 'home-load-error'
  | 'work-role-menu-single'
  | 'work-role-menu-none'
  | 'sidebar-collapsed'

const [SALES, ACCOUNTING] = THREE_ROLES as [(typeof THREE_ROLES)[0], (typeof THREE_ROLES)[1]]

/** 末端の業務データの一覧の道（item は末端の位置の道） */
const LEAF_PATH = `/tables?${new URLSearchParams({
  schema: FRAME_SCHEMA,
  table: LEAF_TABLE,
  item: chainIds().at(-1) ?? '',
}).toString()}`

/** 見てよくないテーブルの一覧の道 */
const FORBIDDEN_PATH = `/tables?${new URLSearchParams({ schema: FRAME_SCHEMA, table: 'SECRET_T' }).toString()}`

/** 業務データの差し替えの口（finish で失敗の件数を読むため、page ごとに覚える） */
const businessRoutes = new WeakMap<Page, BusinessDataApiRoute>()

/** 末端のテーブルの見本の定義（作業ロールは営業） */
const LEAF_DEFINITION = leafDefinition(SALES.roleId)

/** 見本を張る（検査の既定: ロール3つ・検査の木・自分の権限） */
function mockFrame(route: FrameApiRoute): void {
  route.setWorkRole(jsonReply(200, workRoleWire(THREE_ROLES, SALES)))
  route.setNavigation(jsonReply(200, inspectionTree()))
  route.setReply('GET', `${FRAME_API_PATHS.permissions}/schemas`, jsonReply(200, mySchemasWire()))
  route.setReply('GET', `${FRAME_API_PATHS.permissions}/tables`, jsonReply(200, myTablesWire()))
  route.setReply('GET', `${FRAME_API_PATHS.permissions}/columns`, jsonReply(200, myColumnsWire()))
}

/** 差し替えた失敗に出る読み込みの失敗の表示を集める（watchPage の後に張る）。 */
function watchExcludedConsole(page: Page): string[] {
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
    if (isFrameApiPath(pathname) || isBusinessDataApiPath(pathname)) {
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

/**
 * 1つの状態を検査し、注記に件数と規則の名前だけを残す。labelCategories を渡した状態では、規則
 * label-content-name-mismatch の区分ごとの passes を確かめる（Intent 261016-business-data-views の B7、U4-NFR5.3）。
 */
async function inspect(
  page: Page,
  label: string,
  state: string,
  labelCategories: readonly LabelContentNameCategory[] = [],
): Promise<void> {
  const summary = await runAxe(page)
  const overflow = await measureHorizontalOverflow(page)
  const violationRules = [...new Set(summary.violations.map((violation) => violation.id))]
  test.info().annotations.push({
    type: 'axe',
    description: JSON.stringify({ combo: label, state, violations: violationRules, overflow }),
  })
  const name = `${label} ${state}`
  expect(missingRequiredRules(summary), `${name}: 流れなかった規則`).toEqual([])
  const targets = summary.violations.flatMap((violation) =>
    violation.nodes.slice(0, 3).map((node) => describeAxeNode(violation.id, node.key)),
  )
  expect(violationRules, `${name}: 違反（${targets.join(' / ')}）`).toEqual([])
  expect(
    labelContentNameProblems(summary, labelCategories),
    `${name}: label-content-name-mismatch の passes`,
  ).toEqual([])
  expect(overflow.overflows, describeOverflow(name, overflow)).toBe(false)
}

/** 開いたメニューの矩形が表示の中に収まることを確かめる。 */
async function expectContained(page: Page, name: string) {
  const locator = page.getByRole('menu').last()
  await expect(locator).toBeVisible()
  const measured = await measureViewportContainment(locator)
  test.info().annotations.push({ type: 'rect', description: JSON.stringify({ name, ...measured }) })
  expect(measured.contained, describeContainment(name, measured)).toBe(true)
}

/**
 * 5 段まで開いたサイドバーと、その末端の項目が表示の横の範囲に収まることを確かめる。サイドバーは項目の数に応じて縦に
 * 伸び、画面ごと縦に送るため、縦は見ない（横のはみ出しを確かめる）。
 */
async function expectSidebarContained(page: Page, name: string) {
  const nav = page.getByTestId('app-shell').locator('nav').first()
  const leaf = page.getByTestId(`sidebar-nav-biz:${chainIds().at(-1)}`)
  await expect(leaf).toBeVisible()
  const measured = await page.evaluate(() => window.innerWidth)
  const navBox = await nav.boundingBox()
  const leafBox = await leaf.boundingBox()
  const rect = {
    navLeft: Math.round(navBox?.x ?? -1),
    navRight: Math.round((navBox?.x ?? 0) + (navBox?.width ?? 0)),
    leafLeft: Math.round(leafBox?.x ?? -1),
    leafRight: Math.round((leafBox?.x ?? 0) + (leafBox?.width ?? 0)),
    innerWidth: measured,
  }
  test.info().annotations.push({ type: 'rect', description: JSON.stringify({ name, ...rect }) })
  const contained =
    rect.navLeft >= 0 &&
    rect.navRight <= rect.innerWidth &&
    rect.leafLeft >= rect.navLeft &&
    rect.leafRight <= rect.navRight
  expect(
    contained,
    `${name}: サイドバーか末端の項目が横にはみ出しています（${JSON.stringify(rect)}）`,
  ).toBe(true)
}

/**
 * 畳んだサイドバーで、今の項目（末端）を子孫に持つ1段目のまとまりに印（クラス contains-current と読み上げの名前）が付き、
 * 今の項目を含まない1段目の項目には付かないことを確かめる（make-you-chic-ui a9309c4。Intent 261015-deps-ui-carryover の FR2.3）。
 */
async function expectContainsCurrentMark(page: Page, suffix: RegExp): Promise<void> {
  const group = page.getByTestId(`sidebar-nav-biz:${chainIds()[0] ?? ''}`)
  await expect(group).toHaveClass(/contains-current/)
  await expect(group).toHaveAccessibleName(suffix)
  const other = page.getByTestId('sidebar-nav-biz:1')
  await expect(other).not.toHaveClass(/contains-current/)
  await expect(other).not.toHaveAccessibleName(suffix)
}

/** 作業ロールのトリガー（作業ロールの入れ物の中の1つ） */
function workRoleTrigger(page: Page) {
  return page.getByTestId('work-role-switcher').getByRole('button')
}

/** 終わりの確かめ（口・コンソール・CSP） */
async function finish(
  page: Page,
  route: FrameApiRoute,
  watch: PageWatch,
  excludedTexts: readonly string[],
  label: string,
): Promise<void> {
  await page.waitForLoadState('networkidle')
  const business = businessRoutes.get(page)
  const remaining = withoutExcluded(watch.problems, excludedTexts)
  test.info().annotations.push({
    type: 'u7-problems',
    description: JSON.stringify({
      combo: label,
      routeSeen: route.seen,
      blocked: route.blocked.length,
      failedReplies: route.failedReplies,
      businessFailedReplies: business?.failedReplies ?? 0,
      excludedConsole: excludedTexts.length,
      remainingProblems: remaining.length,
      cspViolations: watch.cspViolations.length,
    }),
  })
  expectFrameRouteClean(route, label)
  expect(excludedTexts.length, `${label}: 除いた表示の件数`).toBeLessThanOrEqual(
    route.failedReplies + (business?.failedReplies ?? 0),
  )
  expect(business?.blocked ?? [], `${label}: 答えの無い業務データの要求`).toEqual([])
  expect(watch.cspViolations, `${label}: CSP の違反`).toEqual([])
  expect(remaining, `${label}: 画面の問題`).toEqual([])
}

/** 管理者でログインし、見本の口を張った状態にする（language を渡せばログインの応答の言語も替える）。 */
async function startU7(page: Page, combo: DisplayCombo, language?: 'ja' | 'en') {
  const { baseURL } = test.info().project.use
  const watch = await watchPage(page)
  const excludedTexts = watchExcludedConsole(page)
  const route = await routeFrameApi(page, baseURL ?? '')
  mockFrame(route)
  businessRoutes.set(
    page,
    await routeBusinessDataApi(
      page,
      baseURL ?? '',
      tableResponder(LEAF_DEFINITION, (number, size) =>
        fixturePage(LEAF_DEFINITION, number, size, 45),
      ),
    ),
  )
  await prepareCombo(page, combo)
  // loginPreferences.ts は渡した項目をそのまま重ねる（language も同じ形で当たる）。
  const preferences = { theme: combo.theme, fontSize: combo.fontSize, language }
  await routeLoginPreferences(page, preferences)
  await loginAsAdmin(page)
  await expectComboApplied(page, combo)
  return { watch, excludedTexts, route }
}

/** 既定の組（light・md・blue・既定の幅） */
function defaultCombo(): DisplayCombo {
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

/** 末端のテーブルの一覧を開き、5 段の祖先が開いて末端が今の項目になるのを待つ。 */
async function openLeaf(page: Page): Promise<void> {
  await page.goto(LEAF_PATH)
  await expect(page.getByTestId('tables-heading')).toHaveText(LEAF_LABEL.ja)
  await expect(page.getByTestId(`sidebar-nav-biz:${chainIds().at(-1)}`)).toHaveAttribute(
    'aria-current',
    'page',
  )
}

test.describe('160 app frame accessibility on the built WAR', () => {
  for (const combo of DISPLAY_COMBOS) {
    test(`app frame ${combo.name}`, async ({ page }) => {
      // 10 の状態ごとに axe を流すため、既定の 30 秒を超える。
      test.setTimeout(180_000)
      const { watch, excludedTexts, route } = await startU7(page, combo)
      const check = (
        state: InspectionState,
        labelCategories?: readonly LabelContentNameCategory[],
      ) => inspect(page, combo.name, state, labelCategories)

      await test.step('sidebar with five open levels, long names and the business data list', async () => {
        await openLeaf(page)
        for (const id of chainIds().slice(0, -1)) {
          await expect(page.getByTestId(`sidebar-toggle-biz:${id}`)).toHaveAttribute(
            'aria-expanded',
            'true',
          )
        }
        await check('sidebar-5-levels-and-list', ['C5', 'C8', 'C9'])
        await expectSidebarContained(page, `${combo.name} sidebar`)
      })

      await test.step('work role menu with three roles', async () => {
        await workRoleTrigger(page).click()
        await expect(page.getByRole('menuitem', { name: LONG_WORK_ROLE })).toBeVisible()
        await check('work-role-menu-3-roles', ['C6'])
        await expectContained(page, `${combo.name} work-role-menu`)
        await page.keyboard.press('Escape')
        await expect(page.getByRole('menu')).toHaveCount(0)
      })

      await test.step('business data list without permission', async () => {
        await page.goto(FORBIDDEN_PATH)
        await expect(page.getByTestId('tables-heading')).toHaveText(
          'このテーブルを見る権限がありません。作業ロールを確かめてください。',
        )
        await check('list-forbidden')
      })

      await test.step('my permissions with the column table', async () => {
        await page.goto('/me/permissions')
        await page.getByRole('button', { name: '販売の長い表示名を開く' }).click()
        await page.getByRole('button', { name: LEAF_LABEL.ja, exact: true }).click()
        await expect(page.getByTestId('mypermissions-columns')).toBeVisible()
        // テーブルの節に開閉のボタンが無い（木はスキーマとテーブルの2段。ボタンはスキーマの1つだけ）
        await expect(page.getByTestId('shared-tree-toggle')).toHaveCount(1)
        await check('my-permissions-columns')
      })

      await test.step('home notices', async () => {
        route.setNavigation(jsonReply(200, emptyNavigation('NOT_CONFIGURED')))
        await page.goto('/')
        await expect(page.getByTestId('home-page')).toContainText(
          '業務のメニューが設定されていません',
        )
        await check('home-not-configured')
        route.setNavigation(jsonReply(200, emptyNavigation('NOTHING_VISIBLE')))
        await page.goto('/')
        await expect(page.getByRole('status').filter({ hasText: '表示できる' })).toBeVisible()
        await check('home-nothing-visible')
        route.setNavigation(jsonReply(500, { status: 500, code: 'INTERNAL_ERROR' }))
        await page.goto('/')
        await expect(page.getByRole('button', { name: 'もう一度' })).toBeVisible()
        await check('home-load-error')
        route.setNavigation(jsonReply(200, inspectionTree()))
      })

      await test.step('work role menu with one role and with no role', async () => {
        route.setWorkRole(jsonReply(200, workRoleWire([SALES], SALES)))
        await page.goto('/')
        await workRoleTrigger(page).click()
        await expect(page.getByRole('menuitem', { name: '切り替える先はありません' })).toBeVisible()
        await check('work-role-menu-single')
        await expectContained(page, `${combo.name} work-role-single`)
        await page.keyboard.press('Escape')
        route.setWorkRole(jsonReply(200, workRoleWire([], null)))
        await page.goto('/')
        await expect(workRoleTrigger(page)).toHaveAccessibleName('作業ロール: なし')
        await workRoleTrigger(page).click()
        await expect(page.getByRole('menuitem', { name: '作業ロールを選べません' })).toBeVisible()
        await check('work-role-menu-none')
        await page.keyboard.press('Escape')
        route.setWorkRole(jsonReply(200, workRoleWire(THREE_ROLES, SALES)))
      })

      await test.step('collapsed sidebar with the contains-current mark', async () => {
        await openLeaf(page)
        await page.getByTestId('sidebar-toggle').click()
        await expect(page.getByTestId('app-shell').locator('nav')).toHaveClass(/collapsed/)
        await expect(page.getByTestId('sidebar-toggle')).toHaveAccessibleName('サイドバーを開く')
        await expectContainsCurrentMark(page, /（今のページを含む）$/)
        await check('sidebar-collapsed')
        // 次のテストの状態に残さないよう開き直す（畳んだ状態は localStorage に覚えられる）
        await page.getByTestId('sidebar-toggle').click()
        await expect(page.getByTestId('app-shell').locator('nav')).not.toHaveClass(/collapsed/)
      })

      await finish(page, route, watch, excludedTexts, combo.name)
    })
  }
})

/** 幅の確かめの組（既定の1組の 360px・768px・1280px と、悪い側の lg・dark・360px） */
const WIDTH_CASES: readonly { label: string; width: number; combo: DisplayCombo }[] = [
  { label: '360px', width: 360, combo: defaultCombo() },
  { label: '768px', width: 768, combo: defaultCombo() },
  { label: '1280px', width: 1280, combo: defaultCombo() },
  {
    label: 'lg dark 360px',
    width: 360,
    combo: { ...defaultCombo(), name: 'lg dark 360px', theme: 'dark', fontSize: 'lg' },
  },
]

test.describe('160 app frame widths, English and screen time', () => {
  for (const { label, width, combo } of WIDTH_CASES) {
    test(`app frame width ${label}`, async ({ page }) => {
      test.setTimeout(120_000)
      await page.setViewportSize({ width, height: 900 })
      const { watch, excludedTexts, route } = await startU7(page, combo)
      const narrow = width < 768

      await openLeaf(page)
      const switcher = page.getByTestId('work-role-switcher')
      const header = page.getByTestId('app-shell').locator('header')
      if (narrow) {
        // 768px 未満は内容の領域の先頭（G2: A）。トップバーにはユーザーメニューだけが残る。
        await expect(
          page.getByTestId('shell-work-role-bar').getByTestId('work-role-switcher'),
        ).toHaveCount(1)
        await expect(header.getByTestId('work-role-switcher')).toHaveCount(0)
      } else {
        await expect(header.getByTestId('work-role-switcher')).toHaveCount(1)
        await expect(page.getByTestId('shell-work-role-bar')).toHaveCount(0)
      }
      await expect(switcher.getByRole('button')).toHaveAccessibleName('作業ロール: 営業')
      await inspect(page, label, 'sidebar-5-levels-and-list')
      await expectSidebarContained(page, `${label} sidebar`)
      await workRoleTrigger(page).click()
      await inspect(page, label, 'work-role-menu-3-roles')
      await expectContained(page, `${label} work-role-menu`)
      await page.keyboard.press('Escape')

      await page.goto('/me/permissions')
      await page.getByRole('button', { name: '販売の長い表示名を開く' }).click()
      await page.getByRole('button', { name: LEAF_LABEL.ja, exact: true }).click()
      await expect(page.getByTestId('mypermissions-columns')).toBeVisible()
      const tree = await page.getByTestId('shared-tree').boundingBox()
      const detail = await page.getByTestId('mypermissions-target').boundingBox()
      if (narrow) {
        expect(
          (tree?.y ?? 0) + (tree?.height ?? 0),
          `${label}: 木と表が縦に積まれる`,
        ).toBeLessThanOrEqual(detail?.y ?? 0)
      } else {
        expect(tree?.x ?? 0, `${label}: 木が表の左にある`).toBeLessThan(detail?.x ?? 0)
      }
      await inspect(page, label, 'my-permissions-columns')

      await finish(page, route, watch, excludedTexts, label)
    })
  }

  test('app frame in English', async ({ page }) => {
    test.setTimeout(120_000)
    const { watch, excludedTexts, route } = await startU7(page, defaultCombo(), 'en')
    await page.goto(LEAF_PATH)
    await expect(page.getByTestId('tables-heading')).toHaveText(LEAF_LABEL.en)
    await expect(page.getByRole('navigation', { name: 'Main menu' })).toBeVisible()
    const first = chainIds()[0] ?? ''
    await expect(page.getByTestId(`sidebar-toggle-biz:${first}`)).toHaveAccessibleName(/^Collapse /)
    await page.getByTestId(`sidebar-toggle-biz:${first}`).click()
    await expect(page.getByTestId(`sidebar-toggle-biz:${first}`)).toHaveAccessibleName(/^Expand /)
    await expect(workRoleTrigger(page)).toHaveAccessibleName('Work role: 営業')
    // トップバーの文言も英語（make-you-chic-ui a9309c4 の topbarLabels。Intent 261015-deps-ui-carryover の FR2.4）。
    // ユーザーメニューの名前は頭文字で始める（Intent 261016-business-data-views の U4 の D6）
    await expect(page.getByTestId('sidebar-toggle')).toHaveAccessibleName('Collapse the sidebar')
    await expect(page.getByRole('button', { name: /: Menu for / })).toHaveCount(1)
    await inspect(page, 'English', 'sidebar-and-list')
    await page.getByTestId('sidebar-toggle').click()
    await expect(page.getByTestId('sidebar-toggle')).toHaveAccessibleName('Expand the sidebar')
    await expectContainsCurrentMark(page, / \(contains the current page\)$/)
    await inspect(page, 'English', 'sidebar-collapsed')
    await page.getByTestId('sidebar-toggle').click()
    await finish(page, route, watch, excludedTexts, 'English')
  })

  test('app frame screen time with the default combo', async ({ page }) => {
    // 項目ごとに 5 回、画面を読み込み直して測る。悪い側の木（1,000 項目）を含むため長くかかる。
    test.setTimeout(600_000)
    const { watch, route } = await startU7(page, defaultCombo())
    const records: MeasureRecord[] = []
    const RUNS = 5
    const before = JSON.stringify(workRoleWire(THREE_ROLES, SALES))
    const after = JSON.stringify(workRoleWire(THREE_ROLES, ACCOUNTING))
    route.setWorkRole({ status: 200, body: before }, { status: 200, body: after })
    const bodies = {
      typical: JSON.stringify(typicalTree(false)),
      typicalAfter: JSON.stringify(typicalTree(true)),
      worst: JSON.stringify(worstTree(false)),
      worstAfter: JSON.stringify(worstTree(true)),
    }
    const leafId = chainIds().at(-1) ?? ''
    const fourthId = chainIds()[3] ?? ''
    const doneParams = (markerId: string) => ({
      switchedRoleName: ACCOUNTING.name,
      markerTestId: `sidebar-nav-biz:${markerId}`,
      groupToggleTestId: `sidebar-toggle-biz:${fourthId}`,
      listHeading: LEAF_LABEL.ja,
    })
    /** 開閉の保存を決めた状態にして読み込み直す（ids は biz: を除いた位置の道） */
    const reloadWith = async (expanded: readonly string[], markerId: string) => {
      route.reset()
      await page.evaluate(
        (ids) => sessionStorage.setItem('mastersmith.nav.expanded', JSON.stringify(ids)),
        expanded.map((id) => `biz:${id}`),
      )
      await page.goto('/')
      await expect(workRoleTrigger(page)).toHaveAccessibleName('作業ロール: 営業')
      await installU7Done(page, doneParams(markerId))
    }
    const measure = async (
      id: string,
      runs: number,
      targetMs: number | null,
      prepare: () => Promise<void>,
      startSelector: string,
      done: U7DoneName,
      act: () => Promise<void>,
    ) => {
      const values: number[] = []
      let note: string | undefined
      for (let run = 0; run < runs; run += 1) {
        try {
          await prepare()
          await armU7Measure(page, startSelector, done)
          await act()
          values.push(await readU7Measure(page, 30_000))
        } catch (error) {
          note = `測れない回: ${error instanceof Error ? error.name : 'unknown'}`
        }
      }
      records.push(summarizeRuns(id, 'click', values, targetMs, runs, note))
    }
    await test.step('NFR2.5 switch the work role (typical tree, and the worst tree once)', async () => {
      route.setNavigation(
        { status: 200, body: bodies.typical },
        { status: 200, body: bodies.typicalAfter },
      )
      const prepareSwitch = async (markerId: string) => {
        await reloadWith([], markerId)
        await workRoleTrigger(page).click()
        await expect(
          page.getByRole('menuitem', { name: ACCOUNTING.name, exact: true }),
        ).toBeVisible()
      }
      await measure(
        'NFR2.5 switch (typical tree)',
        RUNS,
        500,
        () => prepareSwitch(TYPICAL_MARKER_ID),
        '[role="menuitem"]',
        'workRoleSwitched',
        () => page.getByRole('menuitem', { name: ACCOUNTING.name, exact: true }).click(),
      )
      route.setNavigation(
        { status: 200, body: bodies.worst },
        { status: 200, body: bodies.worstAfter },
      )
      await measure(
        'NFR2.5 switch (worst tree)',
        1,
        null,
        () => prepareSwitch(worstMarkerId()),
        '[role="menuitem"]',
        'workRoleSwitched',
        () => page.getByRole('menuitem', { name: ACCOUNTING.name, exact: true }).click(),
      )
    })

    await test.step('NFR2.6 open the fourth level group of the worst tree', async () => {
      route.setNavigation({ status: 200, body: bodies.worst })
      await measure(
        'NFR2.6 open a group',
        RUNS,
        200,
        async () => {
          await reloadWith(chainIds().slice(0, 3), worstMarkerId())
          await expect(page.getByTestId(`sidebar-toggle-biz:${fourthId}`)).toHaveAttribute(
            'aria-expanded',
            'false',
          )
        },
        `[data-testid="sidebar-toggle-biz:${fourthId}"]`,
        'groupOpened',
        () => page.getByTestId(`sidebar-toggle-biz:${fourthId}`).click(),
      )
    })

    await test.step('NFR2.7 select the fifth level leaf of the worst tree', async () => {
      await measure(
        'NFR2.7 open the business data list',
        RUNS,
        500,
        async () => {
          await reloadWith(chainIds().slice(0, 4), worstMarkerId())
          await expect(page.getByTestId(`sidebar-nav-biz:${leafId}`)).toBeVisible()
        },
        `[data-testid="sidebar-nav-biz:${leafId}"]`,
        'tableListShown',
        () => page.getByTestId(`sidebar-nav-biz:${leafId}`).click(),
      )
    })

    test.info().annotations.push({ type: 'u7-screen-ms', description: JSON.stringify(records) })
    await test.info().attach('u7-screen-ms', {
      body: JSON.stringify(records, null, 2),
      contentType: 'application/json',
    })
    expectFrameRouteClean(route, 'screen time')
    expect(watch.cspViolations, 'screen time: CSP の違反').toEqual([])
  })
})
