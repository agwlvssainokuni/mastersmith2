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
// ビルドした WAR で、DSL の管理の画面（S10）の書式の版 2 で足した状態を、実際のブラウザの axe とはみ出しで確かめる
// （Intent 261004-role-menu の U2 dsl-v2。計画の 10節 Q2: A・9節 D-13、機能設計の読み直し R-04、NFR4.1）。
// - 流れの E2E ではないため、team.md の「機能の Intent ごとに代表の流れを1本まで」に数えない（project.md の学び）。
// - 組は、ブランドカラー（4つ）× テーマ（2つ）× 幅（360・768・1280）の 24 組。文字の大きさは md。
// - 状態は2つ。(1) F2（今の状態の「適用中の DSL は使われていません」の注意）と F3（読めないプレビューの誤りの一覧と案内）を
//   同時に出す。(2) F2 と F5（長いスキーマ名・表示名の見出しの行、「すべて表示」と開いた行）を出す。
// - DSL の管理の API は、GET /api/admin/dsl/status と GET /api/admin/dsl/preview だけを、この中の見本（画面の側の型を付けた
//   1つの見本）に差し替える。本物の応答と項目の名前・型が合うことは 040 の流れで確かめる（project.md の学び）。GET 以外は
//   送らないことを確かめる。内部DB に書かず、初期管理者の状態は変えない。
// - 差し替えた 422 に Chrome が出す「Failed to load resource:」の表示は、位置の URL がプレビューの API のものだけを数えて引く
//   （この中だけの扱い。共有の support/ は変えない。130 と同じ扱い）。
// - test.step の題・注記・添付に、メールアドレス・パスワード・トークンを入れない（組と状態の名前と件数だけ）。
import { expect, test, type Page } from '@playwright/test'
import type { BrandColor } from '../src/app/display-settings/displaySettingsTypes'
import type { DslErrorReport, DslStatus, Preview } from '../src/features/dsl/api/types'
import { loginAsAdmin, openSidebarItem } from './support/adminLogin'
import { appearanceWith } from './support/appearanceFixture'
import { missingRequiredRules, runAxe } from './support/axe'
import { routeLoginPreferences } from './support/loginPreferences'
import { describeOverflow, measureHorizontalOverflow } from './support/overflow'
import { watchPage } from './support/pageProblems'

/** 今の状態の API */
const STATUS_PATH = '/api/admin/dsl/status'
/** プレビューの API */
const PREVIEW_PATH = '/api/admin/dsl/preview'
/** 管理の API の根 */
const ADMIN_API_PREFIX = '/api/admin/'
/** サイドバーの項目 */
const SIDEBAR_ITEM = 'DSL'
/** 長いスキーマ名（60 文字） */
const LONG_SCHEMA = 'sales_' + 'long_schema_name_'.repeat(3).slice(0, 54)
/** 長い表示名 */
const LONG_LABEL = '販売の業務で使うマスタのスキーマの長い表示名'.repeat(2)

const BRANDS: readonly BrandColor[] = ['blue', 'green', 'purple', 'orange']
const THEMES = ['light', 'dark'] as const
const WIDTHS = [360, 768, 1280] as const

/** 検査の組 */
interface DslCombo {
  name: string
  brandColor: BrandColor
  theme: 'light' | 'dark'
  width: number
}

const COMBOS: readonly DslCombo[] = BRANDS.flatMap((brandColor) =>
  THEMES.flatMap((theme) =>
    WIDTHS.map((width) => ({
      name: `${brandColor} ${theme} ${width}px`,
      brandColor,
      theme,
      width,
    })),
  ),
)

const HASH = 'a'.repeat(64)
const USER = { userId: '1', email: null }

/** 今の状態の見本（適用中の DSL を今の書式で読めない） */
const STATUS_SAMPLE: DslStatus = {
  applied: {
    revisionId: 'revision-a11y',
    dslHash: HASH,
    source: 'UPLOAD',
    by: USER,
    at: '2026-10-01T00:00:00Z',
  },
  preview: {
    previewId: 'preview-a11y',
    dslHash: HASH,
    source: 'UPLOAD',
    by: USER,
    at: '2026-10-01T00:00:00Z',
  },
  appliedUnreadable: true,
}

/** 読めないプレビューの誤りの一覧の見本（422 DSL_INVALID の本文の total・errors） */
const INVALID_SAMPLE: DslErrorReport = {
  total: 2,
  errors: [
    {
      kind: 'UNSUPPORTED_VERSION',
      line: 1,
      column: 1,
      path: 'version',
      message:
        '書式の版 1 は使えません。書式の版 2（スキーマの階層あり）で書いてください。既定の DSL を生成し直すと版 2 で得られます。',
    },
    {
      kind: 'SEMANTIC',
      line: 12,
      column: 23,
      path: `menus.0.items.0.items.0.items.0.items.0.items.0`,
      message: 'メニューの深さが上限（5 段）を超えています。',
    },
  ],
}

/** 長いスキーマ名・表示名の見出しの行を持つプレビューの見本 */
const PREVIEW_SAMPLE: Preview = {
  previewId: 'preview-a11y',
  dslHash: HASH,
  source: 'UPLOAD',
  by: USER,
  at: '2026-10-01T00:00:00Z',
  summary: {
    schemaCount: 1,
    tableCount: 2,
    viewCount: 0,
    columnCount: 3,
    menuTree: [
      {
        label: { ja: LONG_LABEL, en: 'Long label' },
        table: { schema: LONG_SCHEMA, name: 'dept_mst' },
        children: [],
      },
    ],
    missingDisplayNames: [],
    missingDisplayNameTotal: 0,
  },
  diff: {
    appliedExists: true,
    schemas: [
      {
        name: LONG_SCHEMA,
        label: { ja: LONG_LABEL, en: 'Long label' },
        change: 'ADDED',
        tables: [
          {
            name: 'dept_mst',
            change: 'ADDED',
            columns: [{ name: 'dept_code', change: 'ADDED', changedItems: [] }],
          },
        ],
      },
      {
        name: 'public',
        label: { ja: '公開', en: 'Public' },
        change: 'UNCHANGED',
        tables: [
          {
            name: 'same_mst',
            change: 'UNCHANGED',
            columns: [],
          },
        ],
      },
      {
        name: 'old',
        label: { ja: '旧', en: 'Old' },
        change: 'REMOVED',
        tables: [
          {
            name: 'old_mst',
            change: 'REMOVED',
            columns: [{ name: 'code', change: 'REMOVED', changedItems: [] }],
          },
        ],
      },
    ],
  },
  warnings: [
    { kind: 'SCHEMA_MISMATCH', path: `schemas.${LONG_SCHEMA}`, message: 'スキーマが違います。' },
  ],
}

/** 解けない URL は空のパスとする。 */
function pathOf(url: string): string {
  try {
    return new URL(url).pathname
  } catch {
    return ''
  }
}

/** 差し替えの状態（プレビューを読めないか、長い名前のプレビューか） */
interface DslRoute {
  previewMode: 'invalid' | 'long'
  answered: number
}

/** DSL の管理の GET の2本だけを見本に差し替える。 */
async function routeDslApi(page: Page): Promise<DslRoute> {
  const state: DslRoute = { previewMode: 'invalid', answered: 0 }
  await page.route(
    (url) => url.pathname === STATUS_PATH || url.pathname === PREVIEW_PATH,
    (route) => {
      if (route.request().method() !== 'GET') {
        return route.fallback()
      }
      state.answered += 1
      if (pathOf(route.request().url()) === STATUS_PATH) {
        return route.fulfill({ status: 200, json: STATUS_SAMPLE })
      }
      if (state.previewMode === 'invalid') {
        return route.fulfill({
          status: 422,
          contentType: 'application/problem+json',
          body: JSON.stringify({
            type: 'about:blank',
            title: 'Unprocessable Entity',
            status: 422,
            code: 'DSL_INVALID',
            ...INVALID_SAMPLE,
          }),
        })
      }
      return route.fulfill({ status: 200, json: PREVIEW_SAMPLE })
    },
  )
  return state
}

/** 差し替えた 422 に出る読み込みの失敗の表示を集める（watchPage の後に張る）。 */
function watchExcludedConsole(page: Page): string[] {
  const excludedTexts: string[] = []
  page.on('console', (message) => {
    if (
      message.type() === 'error' &&
      pathOf(message.location().url) === PREVIEW_PATH &&
      message.text().startsWith('Failed to load resource:')
    ) {
      excludedTexts.push(`console: ${message.text()}`)
    }
  })
  return excludedTexts
}

/** 管理の API への GET 以外の要求を数える。 */
function countAdminNonGet(page: Page): { count: number } {
  const counter = { count: 0 }
  page.on('request', (request) => {
    if (request.method() !== 'GET' && pathOf(request.url()).startsWith(ADMIN_API_PREFIX)) {
      counter.count += 1
    }
  })
  return counter
}

/** 組の表示の設定をページに仕込む（support/displayCombos.ts の prepareCombo と同じ道。幅だけをこの中で決める）。 */
async function prepare(page: Page, combo: DslCombo): Promise<void> {
  await page.setViewportSize({ width: combo.width, height: 900 })
  await page.addInitScript(({ key, value }) => window.localStorage.setItem(key, value), {
    key: 'mastersmith.display-settings',
    value: JSON.stringify({ theme: combo.theme, fontSize: 'md' }),
  })
  await page.route('**/api/appearance', (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(appearanceWith(combo.brandColor)),
    }),
  )
}

/** 組が当たったことを <html> の属性で確かめる。 */
async function expectApplied(page: Page, combo: DslCombo): Promise<void> {
  const html = page.locator('html')
  await expect(html).toHaveAttribute('data-brand', combo.brandColor)
  await expect(html).toHaveAttribute('data-font-size', 'md')
  await expect
    .poll(() => html.getAttribute('data-theme'))
    .toBe(combo.theme === 'light' ? null : combo.theme)
}

/** axe とはみ出しを確かめ、注記に残す。 */
async function inspect(page: Page, combo: DslCombo, state: string): Promise<void> {
  const summary = await runAxe(page)
  const overflow = await measureHorizontalOverflow(page)
  const violationRules = [...new Set(summary.violations.map((violation) => violation.id))]
  const violationNodes = summary.violations.flatMap((violation) =>
    violation.nodes.map((node) => `${violation.id} ${node.testId ?? node.target}`),
  )
  test.info().annotations.push({
    type: 'axe',
    description: JSON.stringify({
      combo: combo.name,
      state,
      violationRules,
      violationNodes,
      overflow,
    }),
  })
  expect(missingRequiredRules(summary), `${combo.name} ${state}: 流れなかった規則`).toEqual([])
  expect(violationRules, `${combo.name} ${state}: 違反`).toEqual([])
  expect(overflow.overflows, describeOverflow(`${combo.name} ${state}`, overflow)).toBe(false)
}

test.describe('135 DSL administration accessibility on the built WAR', () => {
  for (const combo of COMBOS) {
    test(`dsl admin ${combo.name}`, async ({ page }) => {
      const watch = await watchPage(page)
      const excludedTexts = watchExcludedConsole(page)
      const nonGet = countAdminNonGet(page)
      await prepare(page, combo)
      await routeLoginPreferences(page, { theme: combo.theme, fontSize: 'md' })
      const dsl = await routeDslApi(page)

      await test.step('log in and open the DSL administration', async () => {
        await loginAsAdmin(page)
        await expectApplied(page, combo)
        await openSidebarItem(page, SIDEBAR_ITEM)
        await expect(page.getByTestId('dsl-admin-page')).toBeVisible()
      })

      await test.step('unreadable applied DSL and unreadable preview', async () => {
        await expect(page.getByTestId('dsl-status-unreadable')).toBeVisible()
        await expect(page.getByTestId('dsl-preview-invalid')).toBeVisible()
        await expect(page.getByTestId('dsl-preview-invalid-errors')).toBeVisible()
        await expect(page.getByTestId('dsl-preview-apply')).toHaveCount(0)
        await inspect(page, combo, 'F2-F3')
      })

      await test.step('schema headings with long names', async () => {
        dsl.previewMode = 'long'
        await page.reload()
        await expect(page.getByTestId('dsl-admin-page')).toBeVisible()
        await expectApplied(page, combo)
        await expect(page.getByTestId('dsl-status-unreadable')).toBeVisible()
        await expect(page.getByTestId(`dsl-diff-schema-heading-${LONG_SCHEMA}`)).toBeVisible()
        await page.getByText('すべて表示', { exact: true }).click()
        await expect(page.getByRole('switch', { name: 'すべて表示' })).toBeChecked()
        await expect(page.getByTestId('dsl-diff-schema-heading-public')).toBeVisible()
        await page.getByTestId(`dsl-diff-toggle-${LONG_SCHEMA}/dept_mst`).click()
        await expect(page.getByTestId(`dsl-diff-columns-${LONG_SCHEMA}/dept_mst`)).toBeVisible()
        await inspect(page, combo, 'F2-F5')
      })

      await page.waitForLoadState('networkidle')
      const remaining = [...watch.problems]
      for (const text of excludedTexts) {
        const index = remaining.indexOf(text)
        if (index >= 0) {
          remaining.splice(index, 1)
        }
      }
      test.info().annotations.push({
        type: 'dsl-admin-problems',
        description: JSON.stringify({
          combo: combo.name,
          answered: dsl.answered,
          excludedConsole: excludedTexts.length,
          remainingProblems: remaining.length,
          adminNonGetRequests: nonGet.count,
        }),
      })
      expect(dsl.answered, `${combo.name}: 差し替えの口が受けた件数`).toBeGreaterThanOrEqual(4)
      expect(watch.cspViolations, `${combo.name}: CSP の違反`).toEqual([])
      expect(remaining, `${combo.name}: 画面の問題`).toEqual([])
      expect(nonGet.count, `${combo.name}: 管理の API への GET 以外の要求`).toBe(0)
    })
  }
})
