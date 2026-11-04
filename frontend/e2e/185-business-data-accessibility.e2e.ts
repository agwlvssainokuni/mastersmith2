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
// - 時刻の状態（Intent 261102-timezone-ui-polish の U4、計画 Step 14、security-design.md 2.6）は、組ごとの別のテストで
//   たどる（既存のテストの状態と時間の上限は変えない）。表示のタイムゾーンは GET /api/me/preferences の答えを型を付けた見本
//   （support/preferencesFixtures.ts）に差し替えて決め、読み込みの失敗は 503 で作る。表の見本は businessDataFixtures.ts の
//   時刻の表（本物の応答と同じ形。形の一致は流れ 200 で確かめる）。
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
  TIME_TABLE,
  timeDefinition,
  timeDetail,
  timeNavigation,
  timePage,
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
  type BusinessDataResponder,
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
import type { NavigationResponse } from '../src/app/navigation/navigationApi'
import { routeLoginPreferences } from './support/loginPreferences'
import { preferencesSample } from './support/preferencesFixtures'
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

/** プリファレンスの読み取りの道（表示のタイムゾーンの提供元が読む） */
const PREFERENCES_API_PATH = '/api/me/preferences'

/** 表示のタイムゾーンの答え（保存済みの名前・未設定の null・読み込みの失敗の 'error'） */
type DisplayTimeZoneReply = string | null | 'error'

/** GET /api/me/preferences の差し替えの口（時刻の状態） */
interface DisplayTimeZoneRoute {
  /** 次の読み取りの答えを替える（提供元はログインと読み込み直しのときに読む） */
  set(reply: DisplayTimeZoneReply): void
  /** 失敗で返した件数 */
  readonly failedReplies: number
}

/**
 * GET /api/me/preferences だけを型を付けた見本の答えに差し替える（PUT は差し替えない。080 と同じ形）。組のテーマと文字の
 * 大きさは見本にも入れる（ログインの応答の書き換えと同じ値）。失敗は 503 の Problem Details（値を持たない）。
 */
async function routeDisplayTimeZone(
  page: Page,
  combo: DisplayCombo,
  initial: DisplayTimeZoneReply,
): Promise<DisplayTimeZoneRoute> {
  const state = { reply: initial, failedReplies: 0 }
  await page.route(
    (url) => url.pathname === PREFERENCES_API_PATH,
    (route) => {
      if (route.request().method() !== 'GET') {
        return route.fallback()
      }
      if (state.reply === 'error') {
        state.failedReplies += 1
        return route.fulfill({
          status: 503,
          contentType: 'application/problem+json',
          body: JSON.stringify({ status: 503, code: 'SERVICE_UNAVAILABLE' }),
        })
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(preferencesSample(combo.theme, combo.fontSize, state.reply)),
      })
    },
  )
  return {
    set(reply) {
      state.reply = reply
    },
    get failedReplies() {
      return state.failedReplies
    },
  }
}

/** 見本の口と表示のタイムゾーンの差し替えの選び方（省略すると広い表の既定のまま。時刻の状態だけが渡す） */
interface StartOptions {
  navigation?: NavigationResponse
  responder?: BusinessDataResponder
  /** 渡したときだけ GET /api/me/preferences を差し替える */
  displayTimeZone?: DisplayTimeZoneReply
}

/** 骨組みと業務データの見本の口を張り、管理者でログインする */
async function startBusinessData(
  page: Page,
  combo: DisplayCombo,
  options: StartOptions = {},
): Promise<{
  watch: PageWatch
  frame: FrameApiRoute
  business: BusinessDataApiRoute
  excludedTexts: string[]
  preferences: DisplayTimeZoneRoute | null
}> {
  const { baseURL } = test.info().project.use
  const watch = await watchPage(page)
  const excludedTexts = watchExcludedConsole(page)
  const frame = await routeFrameApi(page, baseURL ?? '')
  frame.setWorkRole(jsonReply(200, workRoleWire(THREE_ROLES, SALES)))
  frame.setNavigation(jsonReply(200, options.navigation ?? wideNavigation()))
  const business = await routeBusinessDataApi(
    page,
    baseURL ?? '',
    options.responder ?? defaultResponder(),
  )
  const preferences =
    options.displayTimeZone === undefined
      ? null
      : await routeDisplayTimeZone(page, combo, options.displayTimeZone)
  await prepareCombo(page, combo)
  // ログインの利用者は 130 と同じ形で2語の氏名にする（初期管理者の氏名は変えない。NFR 設計のレビュー R-09）。
  await routeLoginPreferences(page, {
    theme: combo.theme,
    fontSize: combo.fontSize,
    displayName: TWO_WORD_NAME,
  })
  await loginAsAdmin(page)
  await expectComboApplied(page, combo)
  return { watch, frame, business, excludedTexts, preferences }
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
    // プリファレンスの読み取りは、時刻の状態で差し替えた 503 だけが失敗する（ほかのテストは差し替えず失敗しない）
    if (
      isFrameApiPath(pathname) ||
      isBusinessDataApiPath(pathname) ||
      pathname === PREFERENCES_API_PATH
    ) {
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
  const { watch, frame, business, excludedTexts, preferences } = started
  const preferencesFailed = preferences?.failedReplies ?? 0
  await page.waitForLoadState('networkidle')
  const remaining = withoutExcluded(watch.problems, excludedTexts)
  test.info().annotations.push({
    type: 'u3-problems',
    description: JSON.stringify({
      combo: label,
      businessSeen: business.seen,
      businessFailed: business.failedReplies,
      frameFailed: frame.failedReplies,
      preferencesFailed,
      excludedConsole: excludedTexts.length,
      remainingProblems: remaining.length,
      cspViolations: watch.cspViolations.length,
    }),
  })
  expectBusinessDataRouteClean(business, label)
  expect(frame.blocked, `${label}: 見本の無い骨組みの書き込み`).toEqual([])
  expect(excludedTexts.length, `${label}: 除いた表示の件数`).toBeLessThanOrEqual(
    frame.failedReplies + business.failedReplies + preferencesFailed,
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

// --- 時刻の状態（Intent 261102-timezone-ui-polish の U4、計画 Step 14、security-design.md 2.6） ---

/** 時刻の表（書き込みのできる形。時点・時差を持たない日時の欄は datetime の部品）。全体は 25 行 */
const TIME = writableDefinition(timeDefinition(SALES.roleId))
const TIME_TOTAL = 25

/** 時刻の表の道（item はメニューの位置の道） */
const TIME_QUERY = new URLSearchParams({
  schema: WIDE_SCHEMA,
  table: TIME_TABLE,
  item: '0',
}).toString()
const TIME_LIST_PATH = `/tables?${TIME_QUERY}`
const TIME_NEW_PATH = `/tables/new?${TIME_QUERY}`

/** 時刻の表の答え（作成・更新は送らない。状態を見るだけ） */
function timeResponder(): BusinessDataResponder {
  return tableResponder(
    TIME,
    (number, size) => timePage(TIME, number, size, TIME_TOTAL),
    () => timeDetail(TIME, 1),
  )
}

/** 表示のタイムゾーン（+05:45 の端数の時差。夏時間は無い） */
const KATHMANDU = 'Asia/Kathmandu'
/** 表示のタイムゾーン（夏時間のある地域） */
const NEW_YORK = 'America/New_York'
/** 今のブラウザが解釈できない保存済みの名前（案内の「解釈できない」の文を出す） */
const UNRESOLVABLE_ZONE = 'Etc/E2E_Unresolvable'

/** 時刻の表の一覧を開き、件数が出るまで待つ（表示のタイムゾーンを読み直すため読み込み直す） */
async function openTimeList(page: Page): Promise<void> {
  await page.goto(TIME_LIST_PATH)
  await expect(page.getByTestId('tables-count')).toContainText(String(TIME_TOTAL))
  await expect(page.getByTestId('tables-page')).toHaveAttribute('aria-busy', 'false')
}

/**
 * 時刻の状態を順にたどり、状態ごとに axe を流す（一覧・詳細の案内の3つの文、端数 6 桁・+05:45 の値・長い名前・読めない値、
 * 作成・更新の読めない値・存在しない時刻・2択の未選択・選択済み、検索の時刻の条件の誤り、読み込みの失敗の帯）。
 */
async function walkTimeStates(
  page: Page,
  started: Awaited<ReturnType<typeof startBusinessData>>,
  label: string,
): Promise<void> {
  const { preferences } = started
  if (preferences === null) {
    throw new Error('時刻の状態は表示のタイムゾーンの差し替えが要る')
  }
  const check = (state: string) => inspect(page, label, state)
  const dateTime = () => page.getByTestId('tables-form-datetime-c2')
  const dateTimeInput = () => dateTime().getByRole('textbox')

  await test.step('time list with +05:45, 6 digit fractions, long names and unreadable values', async () => {
    await openTimeList(page)
    await expect(page.getByTestId('tables-time-zone-notice')).toContainText(KATHMANDU)
    await check('time-list-saved')
  })

  await test.step('time detail', async () => {
    await page.getByTestId('tables-row-link-r0').click()
    await expect(page.getByTestId('tables-detail-heading')).toBeFocused()
    await expect(page.getByTestId('tables-time-zone-notice')).toBeVisible()
    await check('time-detail')
  })

  await test.step('time search condition with an error', async () => {
    await openTimeList(page)
    const toggle = page.getByTestId('tables-search-toggle')
    if ((await toggle.getAttribute('aria-expanded')) !== 'true') {
      await toggle.click()
    }
    const range = page.getByTestId('tables-search-field-f1')
    await range.getByRole('textbox').first().fill('2026-13-01 00:00:00')
    await page.getByRole('button', { name: '検索', exact: true }).click()
    await expect(range.getByRole('alert')).toBeVisible()
    await check('time-search-error')
    await page.getByRole('button', { name: '条件をクリア' }).click()
  })

  await test.step('time list with the browser time zone notice', async () => {
    preferences.set(null)
    await openTimeList(page)
    await expect(page.getByTestId('tables-time-zone-notice')).toContainText('Asia/Tokyo')
    await check('time-list-browser')
  })

  await test.step('time list with an unresolvable saved time zone notice', async () => {
    preferences.set(UNRESOLVABLE_ZONE)
    await openTimeList(page)
    await expect(page.getByTestId('tables-time-zone-notice')).toBeVisible()
    await check('time-list-unresolvable')
  })

  await test.step('time create form with a format error', async () => {
    preferences.set(NEW_YORK)
    await page.goto(TIME_NEW_PATH)
    await expect(page.getByTestId('tables-form-heading')).toBeFocused()
    await dateTimeInput().fill('2026-11-01 25:00:00')
    await dateTimeInput().press('Tab')
    await expect(dateTimeInput()).toHaveAttribute('aria-invalid', 'true')
    await check('time-form-invalid')
  })

  await test.step('time create form with a skipped time error', async () => {
    await dateTimeInput().fill('2026-03-08 02:30:00')
    await dateTimeInput().press('Tab')
    await expect(dateTimeInput()).toHaveAccessibleDescription(
      `${NEW_YORK} では、この時刻はありません（夏時間の切り替えで飛ばされる時刻です）`,
    )
    await check('time-form-gap')
  })

  await test.step('time create form with an overlapping time not chosen yet', async () => {
    await dateTimeInput().fill('2026-11-01 01:30:00')
    await dateTimeInput().press('Tab')
    await expect(dateTime().getByRole('radio')).toHaveCount(2)
    await page.getByTestId('tables-form-save').click()
    await expect(page.getByTestId('tables-form-summary')).toBeFocused()
    await expect(page.getByTestId('tables-form-summary')).toContainText(
      'どちらの時刻かを選んでください',
    )
    await check('time-form-overlap-pending')
  })

  await test.step('time create form with the overlapping time chosen', async () => {
    const later = dateTime().getByRole('radio', { name: '後（-05:00）' })
    await later.check()
    await expect(later).toBeChecked()
    await check('time-form-overlap-chosen')
    await page.getByTestId('tables-form-cancel').click()
    await page.getByTestId('tables-leave-go').click()
    await expect(page.getByTestId('tables-count')).toContainText(String(TIME_TOTAL))
  })

  await test.step('time update form with an unreadable stored value kept as text', async () => {
    await page.getByTestId('tables-row-actions-r0').getByRole('button').click()
    await page.getByRole('menuitem', { name: '更新' }).click()
    await expect(page.getByTestId('tables-form-heading')).toBeFocused()
    await check('time-update')
  })

  await test.step('time zone failure on the list', async () => {
    preferences.set('error')
    await page.goto(TIME_LIST_PATH)
    await expect(page.getByTestId('display-time-zone-error')).toBeVisible()
    await expect(page.getByTestId('tables-page')).toHaveAttribute('aria-busy', 'false')
    await check('time-zone-error-list')
  })

  await test.step('time zone failure on the create form', async () => {
    await page.goto(TIME_NEW_PATH)
    await expect(page.getByTestId('display-time-zone-reload')).toBeVisible()
    await expect(page.getByTestId('tables-form-save')).toHaveCount(0)
    await check('time-zone-error-form')
    preferences.set(NEW_YORK)
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

  // 時刻の状態（Intent 261102-timezone-ui-polish の U4）。20 組（375px の組を含む）のすべてで、既存の組のテストとは別に流す。
  for (const combo of DISPLAY_COMBOS) {
    test(`business data time ${combo.name}`, async ({ page }) => {
      // 12 の状態ごとに axe を流し、状態ごとに画面を読み込み直すため、既定の 30 秒を超える（既存の組のテストと同じ上限）。
      test.setTimeout(180_000)
      const started = await startBusinessData(page, combo, {
        navigation: timeNavigation(),
        responder: timeResponder(),
        displayTimeZone: KATHMANDU,
      })
      await walkTimeStates(page, started, `${combo.name} time`)
      await finish(page, started, `${combo.name} time`)
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
