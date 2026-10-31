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
// ビルドした WAR で、作成・更新の画面の他テーブル参照の部品と参照ピッカーを、表示の設定の組ごとに実際のブラウザの axe で
// 確かめる（Intent 261029-reference-lookup-cleanup の U3、D38、security-design.md 3.4、NFR2.43・NFR4.44・NFR4.45、R-03・R-04）。
// - 流れの E2E ではないため、team.md の「機能の Intent ごとに代表の流れを1本まで」に数えない（流れは 190）。
// - 前提は自分で作る（共有の DSL の投入と適用・自作のロールと利用者。190 の残した状態に頼らない。X-3）。
// - 本物の状態（ピッカーの初期・結果・0 件・長い名前・2語以上）は見本の PG の実行のスキーマの行で作り、失敗・入力不可・誤りの
//   状態は定義・検索・保存の答えを差し替えて作る。差し替えで作る状態は保存を起こさない（R-04）。
// - 規則・要素は外さない（project.md の Forbidden）。記録には組と状態の名前と件数だけを残し、行の値・利用者の値を出さない。
import { expect, test, type APIRequestContext, type Page, type Route } from '@playwright/test'
import { runAxe, missingRequiredRules } from './support/axe'
import { describeAxeNode } from './support/axeRuleChecks'
import { REFERENCE_TABLES } from './support/businessDataDsl'
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
import {
  orderColumns,
  orderDefinition,
  problemBody,
  productPage,
  productRow,
  type CustomerVariant,
} from './support/referenceFixtures'
import {
  orderNewPath,
  prepareReferenceRun,
  REFERENCE_ROWS,
  type ReferenceRun,
} from './support/referenceSetup'
import { shapeCompare } from './support/shapeCompare'
import { loginWithForm } from './support/registeredUser'
import { e2eTraceMode } from './support/traceMode'

test.use({ trace: e2eTraceMode() })

/** 前提（beforeAll で作る。登録の前提が無ければ理由の文字） */
let run: ReferenceRun | string | undefined

test.beforeAll(async ({ playwright }, testInfo) => {
  testInfo.setTimeout(180_000)
  const request = await playwright.request.newContext({ baseURL: testInfo.project.use.baseURL })
  try {
    run = await prepareReferenceRun(request, 'AXE')
    if (typeof run === 'object') {
      await expectFixturesShapedLikeRealResponses(request, run)
    }
  } finally {
    await request.dispose()
  }
})

/** null を許す項目（見本と本物のどちらかが null でもよい。180 と同じ並びに参照の項目を足した） */
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
  'reference',
  'label',
])

/**
 * 差し替えの見本の形を、この中で取った本物の応答と比べる（X-3。190 の残した状態に頼らない）。違いは項目の道だけを出す。
 */
async function expectFixturesShapedLikeRealResponses(
  request: APIRequestContext,
  current: ReferenceRun,
): Promise<void> {
  const login = await request.post('/api/auth/login', {
    data: { email: current.user.email, password: current.user.password },
  })
  expect(login.status(), '利用者のログインの状態コード').toBe(200)
  const { accessToken } = (await login.json()) as { accessToken: string }
  const headers = { Authorization: `Bearer ${accessToken}` }
  const definitionResponse = await request.post('/api/tables/definition', {
    headers,
    data: { schema: current.schema, table: REFERENCE_TABLES.order, purpose: 'CREATE' },
  })
  expect(definitionResponse.status(), '定義の状態コード').toBe(200)
  const real = (await definitionResponse.json()) as { detailColumns: { column: string }[] }
  const search = await request.post('/api/tables/reference/rows', {
    headers,
    data: {
      schema: current.schema,
      table: REFERENCE_TABLES.order,
      column: 'product_code',
      conditions: [{ column: 'product_name', value: '商品' }],
      page: 1,
    },
  })
  expect(search.status(), '検索の状態コード').toBe(200)
  const label = await request.post('/api/tables/reference/rows', {
    headers,
    data: {
      schema: current.schema,
      table: REFERENCE_TABLES.order,
      column: 'product_code',
      value: REFERENCE_ROWS.fifthProduct.value,
    },
  })
  expect(label.status(), 'ラベルの1件読みの状態コード').toBe(200)
  const sample = orderDefinition(current.schema, current.roleId)
  const columnOf = (body: { detailColumns: { column: string }[] }, name: string) =>
    body.detailColumns.find((column) => column.column === name)
  const differences: Record<string, string[]> = {
    definition: shapeCompare(real, sample, NULLABLE),
    customer: shapeCompare(columnOf(real, 'customer_code'), orderColumns()[1], NULLABLE),
    product: shapeCompare(columnOf(real, 'product_code'), orderColumns()[2], NULLABLE),
    rows: shapeCompare(await search.json(), productPage([productRow(1)], 1, true), NULLABLE),
    label: shapeCompare(
      await label.json(),
      { rows: [{ value: 'x', label: 'x', cells: [] }], page: null, hasNext: false },
      NULLABLE,
    ),
  }
  const found = Object.fromEntries(
    Object.entries(differences).filter(([, list]) => list.length > 0),
  )
  expect(found, '見本と本物の応答の項目の名前と型の違い').toEqual({})
}

/** 答えの差し替え（定義の形・検索の失敗・保存の 409）と、受けた保存の要求の数 */
interface Overrides {
  /** 注文の表の定義（null は本物へ通す） */
  variant: CustomerVariant | null
  /** 次の検索を 503 にする */
  failSearch: boolean
  /** 保存の要求（作成・更新）を受けた数。すべて差し替えで答え、本物へ通さない（R-04） */
  saves: number
}

/** 注文の表の定義・参照先の検索・保存の口を張る */
async function routeOverrides(page: Page, current: ReferenceRun): Promise<Overrides> {
  const override: Overrides = { variant: null, failSearch: false, saves: 0 }
  await page.route(
    (url) => url.pathname === '/api/tables/reference/rows',
    async (route: Route) => {
      if (!override.failSearch) {
        return route.fallback()
      }
      override.failSearch = false
      return route.fulfill({
        status: 503,
        contentType: 'application/problem+json',
        body: JSON.stringify(problemBody(503, 'BUSINESS_TARGET_DB_UNAVAILABLE')),
      })
    },
  )
  await page.route(
    (url) => url.pathname === '/api/tables/rows',
    async (route: Route) => {
      const method = route.request().method()
      if (method !== 'POST' && method !== 'PATCH') {
        return route.fallback()
      }
      override.saves += 1
      return route.fulfill({
        status: 409,
        contentType: 'application/problem+json',
        body: JSON.stringify(
          problemBody(409, 'BUSINESS_REFERENCE_NOT_FOUND', [
            { field: 'customer_code', reason: 'REFERENCE_NOT_FOUND' },
          ]),
        ),
      })
    },
  )
  await page.route(
    (url) => url.pathname === '/api/tables/definition',
    async (route: Route) => {
      let body: unknown = null
      try {
        body = route.request().postDataJSON()
      } catch {
        body = null
      }
      const table = (body as { table?: unknown } | null)?.table
      if (override.variant === null || table !== REFERENCE_TABLES.order) {
        return route.fallback()
      }
      return route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify(orderDefinition(current.schema, current.roleId, override.variant)),
      })
    },
  )
  return override
}

/** 1つの状態を検査し、注記に件数と規則の名前だけを残す */
async function inspect(page: Page, combo: DisplayCombo, state: string): Promise<void> {
  const summary = await runAxe(page)
  const overflow = await measureHorizontalOverflow(page)
  const violationRules = [...new Set(summary.violations.map((violation) => violation.id))]
  test.info().annotations.push({
    type: 'axe',
    description: JSON.stringify({ combo: combo.name, state, violations: violationRules, overflow }),
  })
  const name = `${combo.name} ${state}`
  expect(missingRequiredRules(summary), `${name}: 流れなかった規則`).toEqual([])
  const targets = summary.violations.flatMap((violation) =>
    violation.nodes.slice(0, 3).map((node) => describeAxeNode(violation.id, node.key)),
  )
  expect(violationRules, `${name}: 違反（${targets.join(' / ')}）`).toEqual([])
  expect(overflow.overflows, describeOverflow(name, overflow)).toBe(false)
}

/** 狭い幅の組では、開いたダイアログが表示の中に収まることを確かめる */
async function expectDialogContained(
  page: Page,
  combo: DisplayCombo,
  state: string,
): Promise<void> {
  if (combo.viewport === undefined) {
    return
  }
  const measured = await measureViewportContainment(page.getByRole('dialog'))
  test.info().annotations.push({
    type: 'rect',
    description: JSON.stringify({ combo: combo.name, state, contained: measured.contained }),
  })
  expect(measured.contained, describeContainment(`${combo.name} ${state}`, measured)).toBe(true)
}

/** ピッカーの条件を入れて検索する（入れる前に条件を空にする） */
async function searchPicker(
  page: Page,
  field: '商品コード（一致）' | '品名（部分一致）',
  value: string,
) {
  const dialog = page.getByRole('dialog')
  await dialog.getByRole('textbox', { name: '商品コード（一致）' }).fill('')
  await dialog.getByRole('textbox', { name: '品名（部分一致）' }).fill('')
  await dialog.getByRole('textbox', { name: field }).fill(value)
  await dialog.getByTestId('tables-picker-search').click()
}

/** 注文の表の作成の画面を開く */
async function openOrderNew(page: Page, current: ReferenceRun): Promise<void> {
  await page.goto(orderNewPath(current.schema))
  await expect(page.getByTestId('tables-form-heading')).toBeVisible()
}

/** 組を当てて自作の利用者でログインする */
async function start(page: Page, combo: DisplayCombo, current: ReferenceRun): Promise<void> {
  await prepareCombo(page, combo)
  await routeLoginPreferences(page, { theme: combo.theme, fontSize: combo.fontSize })
  await loginWithForm(page, current.user.email, current.user.password)
  await expectComboApplied(page, combo)
}

test.describe('195 reference picker accessibility on the built WAR', () => {
  for (const combo of DISPLAY_COMBOS) {
    test(`reference fields ${combo.name}`, async ({ page }) => {
      test.setTimeout(120_000)
      if (typeof run !== 'object') {
        test.skip(true, `registration prerequisites are not available: ${String(run)}`)
        return
      }
      const current = run
      page.on('dialog', (dialog) => void dialog.accept())
      const overrides = await routeOverrides(page, current)
      await start(page, combo, current)
      await openOrderNew(page, current)
      const dialog = page.getByRole('dialog')

      await test.step('1. the picker right after opening', async () => {
        await page.getByRole('button', { name: '選ぶ…（商品）' }).click()
        await expect(dialog.getByTestId('tables-picker-status')).toBeVisible()
        await inspect(page, combo, 'pickerOpened')
        await expectDialogContained(page, combo, 'pickerOpened')
      })

      await test.step('2. results with the next page', async () => {
        await searchPicker(page, '品名（部分一致）', '商品')
        await expect(dialog.getByRole('table')).toBeVisible()
        await expect(dialog.getByRole('button', { name: '次へ' })).toBeEnabled()
        await inspect(page, combo, 'pickerResults')
        await expectDialogContained(page, combo, 'pickerResults')
      })

      await test.step('3. no results', async () => {
        await searchPicker(page, '品名（部分一致）', 'ZZZ-NONE')
        await expect(dialog.getByTestId('tables-picker-status')).toHaveText(
          '該当する行がありません',
        )
        await inspect(page, combo, 'pickerNoResults')
      })

      await test.step('10. a long name over 100 characters with <', async () => {
        await searchPicker(page, '商品コード（一致）', REFERENCE_ROWS.longProduct.value)
        await expect(dialog.getByRole('table')).toBeVisible()
        await inspect(page, combo, 'pickerLongName')
        await expectDialogContained(page, combo, 'pickerLongName')
      })

      await test.step('11. a realistic two-word name', async () => {
        await searchPicker(page, '商品コード（一致）', REFERENCE_ROWS.twoWordProduct.value)
        await expect(dialog.getByRole('table')).toBeVisible()
        await inspect(page, combo, 'pickerTwoWords')
      })

      await test.step('4. a failed search', async () => {
        overrides.failSearch = true
        await searchPicker(page, '品名（部分一致）', '商品')
        await expect(dialog.getByRole('alert')).toBeVisible()
        await inspect(page, combo, 'pickerFailed')
        await page.keyboard.press('Escape')
        await expect(dialog).toHaveCount(0)
      })

      await test.step('5. no permission to read the referenced table', async () => {
        overrides.variant = 'no-permission'
        await openOrderNew(page, current)
        await expect(page.getByRole('textbox', { name: '顧客' })).toHaveAttribute('readonly', '')
        await inspect(page, combo, 'readonlyPermission')
      })

      await test.step('6. too many options', async () => {
        overrides.variant = 'too-many'
        await openOrderNew(page, current)
        await expect(page.getByRole('textbox', { name: '顧客' })).toHaveAttribute('readonly', '')
        await inspect(page, combo, 'readonlyTooMany')
      })

      await test.step('7. a required reference that cannot be filled, right after opening', async () => {
        overrides.variant = 'blocked-required'
        await openOrderNew(page, current)
        const customer = page.getByRole('textbox', { name: '顧客' })
        await expect(customer).toHaveAttribute('readonly', '')
        await expect(customer).not.toHaveAttribute('aria-invalid', 'true')
        await expect(page.getByRole('alert')).toHaveCount(0)
        await inspect(page, combo, 'requiredBlockedOpened')
      })

      await test.step('8. the same field after pressing save', async () => {
        await page.getByRole('textbox', { name: /^注文番号/ }).fill('AXE-1')
        await page.getByTestId('tables-form-save').click()
        await expect(page.getByTestId('tables-form-summary')).toBeVisible()
        await expect(page.getByRole('textbox', { name: '顧客' })).toHaveAttribute(
          'aria-invalid',
          'true',
        )
        await inspect(page, combo, 'requiredBlockedSaved')
      })

      await test.step('9. a 409 on save', async () => {
        overrides.variant = 'available'
        await openOrderNew(page, current)
        await page.getByRole('textbox', { name: /^注文番号/ }).fill('AXE-2')
        await page
          .getByRole('combobox', { name: '顧客' })
          .selectOption(REFERENCE_ROWS.firstCustomer.value)
        await page.getByTestId('tables-form-save').click()
        await expect(page.getByTestId('tables-form-summary')).toBeVisible()
        await inspect(page, combo, 'saveReferenceNotFound')
      })

      await test.step('12. 200 radio options', async () => {
        overrides.variant = 'radio-200'
        await openOrderNew(page, current)
        await expect(page.getByRole('radio')).toHaveCount(200)
        await inspect(page, combo, 'radio200')
      })

      // 差し替えで作った状態は保存を起こさない（保存の要求は状態 9 の1回だけで、すべて差し替えで答えた。R-04）
      expect(overrides.saves, `${combo.name}: 差し替えで答えた保存の要求の数`).toBe(1)
    })
  }
})
