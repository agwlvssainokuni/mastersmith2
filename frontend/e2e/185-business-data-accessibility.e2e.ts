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
// ビルドした WAR で、業務データの画面（S1 一覧・S2 詳細・S3 作成・S4 更新・S5 削除の確かめ・S6 開けないとき・離れる確かめ・
// 保存されたか分からない知らせ）を表示の設定の組ごとに実際のブラウザで確かめ、
// 画面の時間を測って記録する（Intent 261016-business-data-views の U3 の L10、計画 Step 13・14、security-design.md の 7節、
// performance-design.md の 4節・6節、U3-NFR1.7・1.8・5.6〜5.8・7.13）。
// - 流れの E2E ではないため、team.md の「機能の Intent ごとに代表の流れを1本まで」に数えない（流れは 180）。
// - 業務データの API と骨組みの API は差し替えの口（support/businessDataApiRoute.ts・frameApiRoute.ts）で受け、見本は型を
//   付けた1つの見本（support/businessDataFixtures.ts）。適用中の DSL と見本の PG に頼らない（180 より後に流れても同じ。
//   基盤の設計のレビュー R-07）。
// - 規則・要素は外さない（project.md の Forbidden）。記録には選択子を出さず、組と状態の名前と件数・時間だけを残す。
// - test.step の題・注記・添付に、行の値・主キー・氏名・メールアドレス・パスワード・トークンを入れない。
import { expect, test, type ConsoleMessage, type Page } from '@playwright/test'
import { loginAsAdmin } from './support/adminLogin'
import { missingRequiredRules, runAxe } from './support/axe'
import {
  describeAxeNode,
  labelContentNameProblems,
  type LabelContentNameCategory,
} from './support/axeRuleChecks'
import {
  fixturePage,
  longDetail,
  WIDE_SCHEMA,
  WIDE_TABLE,
  wideDefinition,
  wideNavigation,
  writableDefinition,
} from './support/businessDataFixtures'
import {
  expectBusinessDataRouteClean,
  isBusinessDataApiPath,
  routeBusinessDataApi,
  tableResponder,
  type BusinessDataApiRoute,
} from './support/businessDataApiRoute'
import {
  armBusinessDataMeasure,
  BUSINESS_DATA_TARGETS,
  installBusinessDataDone,
  readBusinessDataMeasure,
  summarizePaintRuns,
  type BusinessDataDoneName,
  type PaintRecord,
  type PaintRun,
} from './support/businessDataMeasure'
import {
  DISPLAY_COMBOS,
  expectComboApplied,
  prepareCombo,
  type DisplayCombo,
} from './support/displayCombos'
import {
  isFrameApiPath,
  jsonReply,
  routeFrameApi,
  type FrameApiRoute,
} from './support/frameApiRoute'
import { routeLoginPreferences } from './support/loginPreferences'
import {
  describeContainment,
  describeOverflow,
  measureHorizontalOverflow,
  measureViewportContainment,
} from './support/overflow'
import { THREE_ROLES, workRoleWire } from './support/navTreeFixtures'
import { watchPage, type PageWatch } from './support/pageProblems'
import { e2eTraceMode } from './support/traceMode'

test.use({ trace: e2eTraceMode() })

const SALES = THREE_ROLES[0] as (typeof THREE_ROLES)[0]

/** 広い表（100 カラム）の見本。全体は 250 行 */
const WIDE = wideDefinition(100, SALES.roleId)
const WIDE_TOTAL = 250

/** 書き込みのできる狭い表（8 カラム。B6 の作成・更新・削除の状態）と、書き込みのできる広い表（formShown100） */
const WRITABLE = writableDefinition(wideDefinition(8, SALES.roleId))
const WRITABLE_WIDE = writableDefinition(WIDE)

/** 作成の画面の道 */
const NEW_PATH = `/tables/new?${new URLSearchParams({ schema: WIDE_SCHEMA, table: WIDE_TABLE, item: '0' }).toString()}`

/**
 * 書き込みのできる表の答え。作成は 502（本文なし）を返し、「保存されたか分かりません」の状態にする（D4）。
 * 更新・削除は送らない（状態を見るだけ）。
 */
function writableResponder(definition = WRITABLE) {
  const base = tableResponder(
    definition,
    (number, size) => fixturePage(definition, number, size, WIDE_TOTAL),
    () => longDetail(definition, 1),
  )
  return (call: Parameters<typeof base>[0], body: unknown) =>
    call === 'create' ? { status: 502, body: '' } : base(call, body)
}

/** 広い表の一覧の道（item はメニューの位置の道） */
const WIDE_PATH = `/tables?${new URLSearchParams({ schema: WIDE_SCHEMA, table: WIDE_TABLE, item: '0' }).toString()}`

/** 広い表の既定の答え（250 行・詳細は 10,000 字の値を含む行） */
function defaultResponder() {
  return tableResponder(
    WIDE,
    (number, size) => fixturePage(WIDE, number, size, WIDE_TOTAL),
    () => longDetail(WIDE, 1),
  )
}

/** 骨組みと業務データの見本の口を張り、管理者でログインする */
async function startBusinessData(
  page: Page,
  combo: DisplayCombo,
): Promise<{
  watch: PageWatch
  frame: FrameApiRoute
  business: BusinessDataApiRoute
  excludedTexts: string[]
}> {
  const { baseURL } = test.info().project.use
  const watch = await watchPage(page)
  const excludedTexts = watchExcludedConsole(page)
  const frame = await routeFrameApi(page, baseURL ?? '')
  frame.setWorkRole(jsonReply(200, workRoleWire(THREE_ROLES, SALES)))
  frame.setNavigation(jsonReply(200, wideNavigation()))
  const business = await routeBusinessDataApi(page, baseURL ?? '', defaultResponder())
  await prepareCombo(page, combo)
  // ログインの利用者は 130 と同じ形で2語の氏名にする（初期管理者の氏名は変えない。NFR 設計のレビュー R-09）。
  await routeLoginPreferences(page, {
    theme: combo.theme,
    fontSize: combo.fontSize,
    displayName: TWO_WORD_NAME,
  })
  await loginAsAdmin(page)
  await expectComboApplied(page, combo)
  return { watch, frame, business, excludedTexts }
}

/** 130 と同じ2語の氏名（secretValues の SECRET_FIXED_VALUES に同じ値がある） */
const TWO_WORD_NAME = 'Probe Sample'

/** 見てよくないテーブルの一覧の道 */
const FORBIDDEN_PATH = `/tables?${new URLSearchParams({ schema: WIDE_SCHEMA, table: 'SECRET_T' }).toString()}`

/** 差し替えた失敗に出る読み込みの失敗の表示を集める（watchPage の後に張る。160 と同じ扱い） */
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
 * 1つの状態を検査し、注記に件数と規則の名前だけを残す（160 と同じ形）。labelCategories を渡した状態では、規則
 * label-content-name-mismatch の区分ごとの passes を確かめる（B7、U4-NFR5.3・U3-NFR5.8）。
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
async function expectContained(page: Page, name: string): Promise<void> {
  const locator = page.getByRole('menu').last()
  await expect(locator).toBeVisible()
  const measured = await measureViewportContainment(locator)
  test.info().annotations.push({ type: 'rect', description: JSON.stringify({ name, ...measured }) })
  expect(measured.contained, describeContainment(name, measured)).toBe(true)
}

/** 終わりの確かめ（口・コンソール・CSP） */
async function finish(
  page: Page,
  started: Awaited<ReturnType<typeof startBusinessData>>,
  label: string,
): Promise<void> {
  const { watch, frame, business, excludedTexts } = started
  await page.waitForLoadState('networkidle')
  const remaining = withoutExcluded(watch.problems, excludedTexts)
  test.info().annotations.push({
    type: 'u3-problems',
    description: JSON.stringify({
      combo: label,
      businessSeen: business.seen,
      businessFailed: business.failedReplies,
      frameFailed: frame.failedReplies,
      excludedConsole: excludedTexts.length,
      remainingProblems: remaining.length,
      cspViolations: watch.cspViolations.length,
    }),
  })
  expectBusinessDataRouteClean(business, label)
  expect(frame.blocked, `${label}: 見本の無い骨組みの書き込み`).toEqual([])
  expect(excludedTexts.length, `${label}: 除いた表示の件数`).toBeLessThanOrEqual(
    frame.failedReplies + business.failedReplies,
  )
  expect(watch.cspViolations, `${label}: CSP の違反`).toEqual([])
  expect(remaining, `${label}: 画面の問題`).toEqual([])
}

/** 一覧・詳細・作成・更新・削除・離れる確かめの状態を順にたどり、状態ごとに axe を流す（作成・更新・削除は B6 で足した） */
async function walkStates(
  page: Page,
  started: Awaited<ReturnType<typeof startBusinessData>>,
  label: string,
): Promise<void> {
  const { business } = started
  const check = (state: string, labelCategories?: readonly LabelContentNameCategory[]) =>
    inspect(page, label, state, labelCategories)

  await test.step('list with 100 columns, NULL rows and long names', async () => {
    await openWideList(page)
    await expect(page.locator('[data-testid^="table-row-r"]')).toHaveCount(20)
    await check('list', ['C8', 'C9'])
  })

  await test.step('open search fields with an error', async () => {
    const toggle = page.getByTestId('tables-search-toggle')
    if ((await toggle.getAttribute('aria-expanded')) !== 'true') {
      await toggle.click()
    }
    await page.getByRole('textbox', { name: /商品名/ }).fill('あ'.repeat(1001))
    await page.getByRole('button', { name: '検索', exact: true }).click()
    await expect(page.getByText('長すぎます。')).toBeVisible()
    await check('search-error')
    await page.getByRole('button', { name: '条件をクリア' }).click()
  })

  await test.step('open row actions menu at the right edge', async () => {
    const trigger = page.getByTestId('tables-row-actions-r19').getByRole('button')
    await trigger.scrollIntoViewIfNeeded()
    await trigger.click()
    await check('row-actions-menu')
    await expectContained(page, `${label} row-actions-menu`)
    await page.keyboard.press('Escape')
    await expect(page.getByRole('menu')).toHaveCount(0)
  })

  await test.step('loading the next page', async () => {
    const release = business.holdSearch()
    await page.getByRole('button', { name: '次へ' }).click()
    await expect(page.getByTestId('tables-page')).toHaveAttribute('aria-busy', 'true')
    await check('loading')
    release()
    await expect(page.getByTestId('tables-page')).toHaveAttribute('aria-busy', 'false')
  })

  await test.step('detail with a 10,000 character value', async () => {
    await page.getByTestId('tables-row-link-r0').click()
    await expect(page.getByTestId('tables-detail-heading')).toBeFocused()
    await check('detail')
  })

  await test.step('create form with errors in the summary', async () => {
    business.setResponder(writableResponder())
    await page.goto(NEW_PATH)
    await expect(page.getByTestId('tables-form-heading')).toBeFocused()
    await page.getByTestId('tables-form-field-c2').getByRole('textbox').fill('x')
    await page.getByTestId('tables-form-save').click()
    await expect(page.getByTestId('tables-form-summary')).toBeFocused()
    await check('create-errors')
  })

  await test.step('leave confirmation', async () => {
    await page.getByTestId('tables-form-cancel').click()
    await expect(page.getByTestId('tables-leave-dialog')).toBeVisible()
    await check('leave-confirm')
    await page.getByTestId('tables-leave-stay').click()
    await expect(page.getByTestId('tables-leave-dialog')).toHaveCount(0)
  })

  await test.step('save result unknown', async () => {
    await page.getByTestId('tables-form-field-c0').getByRole('textbox').fill('N-185')
    await page.getByTestId('tables-form-save').click()
    await expect(page.getByTestId('tables-form-unknown')).toBeVisible()
    await check('save-unknown')
    await page.getByTestId('tables-form-cancel').click()
    await page.getByTestId('tables-leave-go').click()
    await expect(page.getByTestId('tables-count')).toContainText(String(WIDE_TOTAL))
  })

  await test.step('update form', async () => {
    await page.getByTestId('tables-row-actions-r0').getByRole('button').click()
    await page.getByRole('menuitem', { name: '更新' }).click()
    await expect(page.getByTestId('tables-form-heading')).toBeFocused()
    await check('update')
  })

  await test.step('delete confirmation', async () => {
    await page.getByTestId('tables-form-back').click()
    await expect(page.getByTestId('tables-detail-heading')).toBeFocused()
    await page.getByTestId('tables-detail-delete').click()
    await expect(page.getByTestId('tables-delete-dialog')).toBeVisible()
    await check('delete-confirm')
    await page.getByTestId('tables-delete-cancel').click()
    await expect(page.getByTestId('tables-delete-dialog')).toHaveCount(0)
    business.setResponder(defaultResponder())
  })

  await test.step('empty list', async () => {
    business.setResponder(
      tableResponder(WIDE, (number, size) => fixturePage(WIDE, number, size, 0)),
    )
    await page.goto(WIDE_PATH)
    await expect(page.getByTestId('tables-empty-table')).toBeVisible()
    await check('empty')
    business.setResponder(defaultResponder())
  })

  await test.step('forbidden table', async () => {
    await page.goto(FORBIDDEN_PATH)
    await expect(page.getByTestId('tables-heading')).toHaveText(
      'このテーブルを見る権限がありません。作業ロールを確かめてください。',
    )
    await check('forbidden')
  })
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

/** 200% の拡大を、既定の幅 1280×720 の半分の CSS ピクセル（640×360）で代える組（既定の組と dark・lg） */
const ZOOM_COMBOS: readonly DisplayCombo[] = [
  {
    name: '(z) light md blue 640x360',
    theme: 'light',
    fontSize: 'md',
    brandColor: 'blue',
    viewport: { width: 640, height: 360 },
  },
  {
    name: '(z) dark lg blue 640x360',
    theme: 'dark',
    fontSize: 'lg',
    brandColor: 'blue',
    viewport: { width: 640, height: 360 },
  },
]

/** 一覧を開き、件数が出るまで待つ */
async function openWideList(page: Page): Promise<void> {
  await page.goto(WIDE_PATH)
  await expect(page.getByTestId('tables-count')).toContainText(String(WIDE_TOTAL))
  await expect(page.getByTestId('tables-page')).toHaveAttribute('aria-busy', 'false')
}

test.describe('185 business data accessibility on the built WAR', () => {
  for (const combo of DISPLAY_COMBOS) {
    test(`business data ${combo.name}`, async ({ page }) => {
      // 12 の状態ごとに axe を流し、100 カラムの表を含むため、既定の 30 秒を超える。
      test.setTimeout(180_000)
      const started = await startBusinessData(page, combo)
      await walkStates(page, started, combo.name)
      await finish(page, started, combo.name)
    })
  }

  for (const combo of ZOOM_COMBOS) {
    test(`business data 200% zoom ${combo.name}`, async ({ page }) => {
      test.setTimeout(180_000)
      const started = await startBusinessData(page, combo)
      await walkStates(page, started, combo.name)
      await finish(page, started, combo.name)
    })
  }

  test('measure business data screen time with the default combo', async ({ page }) => {
    // 5つの判定を 5 回ずつ、画面を読み込み直して測る。100 カラム × 100 行の表を描くため長くかかる。
    test.setTimeout(600_000)
    const { watch, frame, business } = await startBusinessData(page, defaultCombo())
    const RUNS = 5
    const records: PaintRecord[] = []
    const params = { lastColumnKey: 'actions', detailItems: WIDE.detailColumns.length }
    const measure = async (
      name: BusinessDataDoneName,
      prepare: () => Promise<void>,
      startSelector: string,
      act: () => Promise<void>,
    ) => {
      const runs: PaintRun[] = []
      let note: string | undefined
      for (let run = 0; run < RUNS; run += 1) {
        try {
          await prepare()
          await installBusinessDataDone(page, params)
          await armBusinessDataMeasure(page, startSelector, name)
          await act()
          runs.push(await readBusinessDataMeasure(page))
        } catch (error) {
          note = `測れない回: ${error instanceof Error ? error.name : 'unknown'}`
        }
      }
      records.push(summarizePaintRuns(name, runs, BUSINESS_DATA_TARGETS[name], RUNS, note))
    }

    await test.step('listShown20 open the table from the menu', async () => {
      await measure(
        'listShown20',
        async () => {
          await page.goto('/')
          await expect(page.getByTestId('home-page')).toBeVisible()
          await expect(page.getByTestId('sidebar-nav-biz:0')).toBeVisible()
        },
        '[data-testid="sidebar-nav-biz:0"]',
        () => page.getByTestId('sidebar-nav-biz:0').click(),
      )
    })

    await test.step('listShown100 change the page size to 100', async () => {
      await measure(
        'listShown100',
        () => openWideList(page),
        '[data-testid="page-size-select"]',
        async () => {
          await page.getByTestId('page-size-select').selectOption('100')
        },
      )
    })

    await test.step('detailFocused open a row with 100 columns and a 10,000 character value', async () => {
      await measure(
        'detailFocused',
        () => openWideList(page),
        '[data-testid="tables-row-link-r0"]',
        () => page.getByTestId('tables-row-link-r0').click(),
      )
    })

    await test.step('listShown100Back go back to the list with 100 rows', async () => {
      await measure(
        'listShown100Back',
        async () => {
          await openWideList(page)
          await page.getByTestId('page-size-select').selectOption('100')
          await expect(page.locator('[data-testid^="table-row-r"]')).toHaveCount(100)
          await page.getByTestId('tables-row-link-r0').click()
          await expect(page.getByTestId('tables-detail-heading')).toBeFocused()
        },
        '[data-testid="tables-back-to-list"]',
        () => page.getByTestId('tables-back-to-list').click(),
      )
    })

    await test.step('formShown100 open the create form with 100 columns', async () => {
      await measure(
        'formShown100',
        async () => {
          business.setResponder(writableResponder(WRITABLE_WIDE))
          await openWideList(page)
        },
        '[data-testid="tables-create"]',
        () => page.getByTestId('tables-create').click(),
      )
      business.setResponder(defaultResponder())
    })

    test.info().annotations.push({ type: 'u3-screen-ms', description: JSON.stringify(records) })
    await test.info().attach('u3-screen-ms', {
      body: JSON.stringify(records, null, 2),
      contentType: 'application/json',
    })
    expectBusinessDataRouteClean(business, 'screen time')
    expect(frame.blocked, 'screen time: 見本の無い骨組みの書き込み').toEqual([])
    expect(watch.cspViolations, 'screen time: CSP の違反').toEqual([])
  })
})
