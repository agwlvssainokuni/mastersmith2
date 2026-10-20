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
// S1 業務データの一覧のテスト（Intent 261016-business-data-views の U3 の L7、D3・D7〜D14・F1〜F3・F6・F8、計画 Step 12、
// AC1.1.1〜AC1.3.15、U3-NFR1.9・4.2・5.9・7.9）。API は fetch を差し替えて ApiClient を通す。
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { ACCOUNTING, SALES } from '../../app/testing/frameTestSupport'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { BUSINESS_DATA_PATHS } from './businessDataApi'
import { clearBusinessDataStore } from './businessDataStore'
import {
  wireColumn,
  wireDefinition,
  wireDetail,
  wirePage,
  wireRow,
  wireSearchField,
} from './testing/businessDataSamples'
import { countCalls, renderTablesScreen, type Responder } from './testing/renderTablesScreen'

const ROUTE = '/tables?schema=shop&table=item&item=0.1'

beforeEach(() => clearBusinessDataStore())
afterEach(() => {
  vi.unstubAllGlobals()
  clearBusinessDataStore()
  resetDisplayTestState()
})

/** 定義・一覧・詳細を答える（一覧は page ごとに行を作る） */
function responder(
  overrides: { definition?: Record<string, unknown>; total?: number } = {},
): Responder {
  return (path, body) => {
    if (path === BUSINESS_DATA_PATHS.definition) {
      return { status: 200, body: wireDefinition(overrides.definition) }
    }
    if (path === BUSINESS_DATA_PATHS.search) {
      const page = Number(body.page)
      const total = overrides.total ?? 45
      const size = Number(body.size)
      const count = Math.max(0, Math.min(size, total - (page - 1) * size))
      const items = Array.from({ length: count }, (_, i) => wireRow(`P${page}-${i}`))
      return { status: 200, body: wirePage(items, { page, size, total }) }
    }
    if (path === BUSINESS_DATA_PATHS.get) {
      const key = body.key as { code: string }
      return { status: 200, body: wireDetail(key.code) }
    }
    return { status: 404, body: { status: 404 } }
  }
}

describe('BusinessDataListPage', () => {
  it('shows the table name, the rows and the count, sending the definition and one search', async () => {
    const { calls } = renderTablesScreen(ROUTE, responder())
    expect(await screen.findByRole('heading', { level: 1, name: '商品' })).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByTestId('tables-count')).toHaveTextContent('全 45 件（1〜20 件を表示）'),
    )
    await waitFor(() =>
      expect(screen.getByTestId('tables-live')).toHaveTextContent('全 45 件（1〜20 件を表示）'),
    )
    expect(screen.getByTestId('tables-live')).toHaveAttribute('aria-live', 'polite')
    expect(screen.getAllByRole('row')).toHaveLength(21)
    expect(countCalls(calls, BUSINESS_DATA_PATHS.definition)).toBe(1)
    expect(countCalls(calls, BUSINESS_DATA_PATHS.search)).toBe(1)
    expect(calls[0].body).toEqual({ schema: 'shop', table: 'item' })
    expect(screen.getByRole('region', { name: '商品の一覧の表' })).toBeInTheDocument()
  })

  it('shows the forbidden view without names or rows after a 403 and reloads the menu', async () => {
    const { frame, calls } = renderTablesScreen(ROUTE, (path) =>
      path === BUSINESS_DATA_PATHS.definition
        ? { status: 403, body: { status: 403, code: 'ACCESS_DENIED' } }
        : { status: 500, body: {} },
    )
    expect(
      await screen.findByRole('heading', {
        level: 1,
        name: 'このテーブルを見る権限がありません。作業ロールを確かめてください。',
      }),
    ).toBeInTheDocument()
    const page = screen.getByTestId('tables-page')
    expect(page).not.toHaveTextContent('商品')
    expect(page).not.toHaveTextContent('item')
    expect(screen.queryByRole('table')).toBeNull()
    await waitFor(() => expect(frame.navCalls.count).toBe(2))
    expect(countCalls(calls, BUSINESS_DATA_PATHS.search)).toBe(0)
  })

  it('tells empty search results, an empty table and a page beyond the rows apart', async () => {
    const user = userEvent.setup()
    const first = renderTablesScreen(ROUTE, responder({ total: 0 }))
    expect(await screen.findByTestId('tables-empty-table')).toHaveTextContent(
      'このテーブルには行がありません。',
    )
    first.view.unmount()
    clearBusinessDataStore()

    renderTablesScreen(
      ROUTE,
      responder({
        total: 0,
        definition: { searchFields: [wireSearchField('code', { defaultValue: 'X' })] },
      }),
    )
    const filtered = await screen.findByTestId('tables-empty-filtered')
    expect(filtered).toHaveTextContent('条件に合う行がありません。')
    expect(within(filtered).getByRole('button', { name: '条件をクリア' })).toBeInTheDocument()
    clearBusinessDataStore()

    const overflow = renderTablesScreen(ROUTE, (path, body) =>
      path === BUSINESS_DATA_PATHS.search && Number(body.page) === 2
        ? { status: 200, body: wirePage([], { page: 2, total: 3 }) }
        : responder()(path, body),
    )
    await user.click(await screen.findByRole('button', { name: '次へ' }))
    expect(await screen.findByTestId('tables-page-overflow')).toHaveTextContent(
      'このページには行がありません。',
    )
    expect(countCalls(overflow.calls, BUSINESS_DATA_PATHS.search)).toBe(2)
    await user.click(screen.getByRole('button', { name: '前のページへ' }))
    await waitFor(() => expect(countCalls(overflow.calls, BUSINESS_DATA_PATHS.search)).toBe(3))
    expect(overflow.calls.at(-1)?.body.page).toBe(1)
  })

  it('keeps the previous table with aria-busy while the next page is loading', async () => {
    const user = userEvent.setup()
    let release: (() => void) | undefined
    const base = responder()
    const { fetchMock } = renderTablesScreen(ROUTE, base)
    await waitFor(() =>
      expect(screen.getByTestId('tables-count')).toHaveTextContent('全 45 件（1〜20 件を表示）'),
    )
    fetchMock.mockImplementationOnce(
      (path: string, init?: RequestInit) =>
        new Promise((resolve) => {
          release = () => {
            const answer = base(path, JSON.parse(String(init?.body)) as Record<string, unknown>)
            resolve(
              new Response(JSON.stringify(answer.body), {
                status: answer.status,
                headers: { 'Content-Type': 'application/json' },
              }),
            )
          }
        }),
    )
    await user.click(screen.getByRole('button', { name: '次へ' }))
    expect(screen.getByTestId('tables-page')).toHaveAttribute('aria-busy', 'true')
    expect(screen.getAllByRole('row')).toHaveLength(21)
    await act(async () => release?.())
    await waitFor(() =>
      expect(screen.getByTestId('tables-page')).toHaveAttribute('aria-busy', 'false'),
    )
    expect(screen.getByTestId('tables-count')).toHaveTextContent('全 45 件（21〜40 件を表示）')
  })

  it('opens a row without putting the key in the URL or the history state and keeps the list on back', async () => {
    const user = userEvent.setup()
    const key = 'A/1?#% "x"'
    const { calls } = renderTablesScreen(ROUTE, (path, body) =>
      path === BUSINESS_DATA_PATHS.search
        ? {
            status: 200,
            body: wirePage([wireRow(key)], { page: Number(body.page), size: 20, total: 45 }),
          }
        : responder()(path, body),
    )
    await user.click(await screen.findByRole('button', { name: '次へ' }))
    await waitFor(() => expect(screen.getByTestId('tables-count')).toHaveTextContent('21〜21'))
    await user.click(screen.getByRole('link', { name: `詳細: 商品 ${key}` }))
    expect(await screen.findByRole('heading', { level: 1, name: `商品: ${key}` })).toHaveFocus()
    expect(screen.getByTestId('location')).toHaveTextContent('/tables/row')
    expect(window.location.href).not.toContain('A%2F1')
    const getCall = calls.find((call) => call.path === BUSINESS_DATA_PATHS.get)
    expect(getCall?.body.key).toEqual({ code: key })
    await user.click(screen.getByRole('button', { name: '戻る' }))
    await waitFor(() => expect(screen.getByTestId('tables-count')).toHaveTextContent('21〜21'))
    expect(calls.filter((call) => call.path === BUSINESS_DATA_PATHS.search).at(-1)?.body.page).toBe(
      2,
    )
    await waitFor(() =>
      expect(screen.getByRole('link', { name: `詳細: 商品 ${key}` })).toHaveFocus(),
    )
  })

  it('starts from the default state again after a reload (the store is gone)', async () => {
    const user = userEvent.setup()
    const first = renderTablesScreen(ROUTE, responder())
    await user.click(await screen.findByRole('button', { name: '次へ' }))
    await waitFor(() => expect(screen.getByTestId('tables-count')).toHaveTextContent('21〜40'))
    first.view.unmount()
    clearBusinessDataStore()
    const second = renderTablesScreen(ROUTE, responder())
    await waitFor(() =>
      expect(screen.getByTestId('tables-count')).toHaveTextContent('全 45 件（1〜20 件を表示）'),
    )
    expect(second.calls.find((call) => call.path === BUSINESS_DATA_PATHS.search)?.body.page).toBe(1)
  })

  it('shows the restriction and write notices and hides row actions for a table without a key', async () => {
    renderTablesScreen(
      ROUTE,
      responder({
        definition: {
          operations: ['LIST'],
          restrictionReason: 'NO_PRIMARY_KEY',
          rowLabelColumns: [],
          primaryKeyColumns: [],
          hasPrimaryKey: false,
        },
      }),
    )
    expect(
      await screen.findByText(
        'このテーブルは行を1件に決められないため、詳細・更新・削除はできません。',
      ),
    ).toBeInTheDocument()
    expect(screen.getByText(/書き込みの設定がありません/)).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByTestId('tables-count')).toHaveTextContent('全 45 件（1〜20 件を表示）'),
    )
    expect(screen.queryByRole('link', { name: /^詳細:/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /^操作/ })).toBeNull()
  })

  it('sends the search conditions and does not send when the input cannot be read', async () => {
    const user = userEvent.setup()
    const { calls } = renderTablesScreen(
      ROUTE,
      responder({
        definition: {
          searchFields: [
            wireSearchField('name', {
              displayName: { ja: '商品名', en: 'Name' },
              operator: 'CONTAINS',
            }),
            wireSearchField('price', {
              displayName: { ja: '価格', en: 'Price' },
              operator: 'RANGE',
              valueType: 'DECIMAL',
            }),
          ],
        },
      }),
    )
    await waitFor(() =>
      expect(screen.getByTestId('tables-count')).toHaveTextContent('全 45 件（1〜20 件を表示）'),
    )
    await user.type(screen.getByRole('textbox', { name: '商品名（部分一致）' }), '50%_')
    await user.click(screen.getByRole('button', { name: '検索' }))
    await waitFor(() => expect(countCalls(calls, BUSINESS_DATA_PATHS.search)).toBe(2))
    expect(calls.at(-1)?.body.conditions).toEqual([{ column: 'name', value: '50%_' }])
    await user.type(screen.getByRole('textbox', { name: '価格の下限' }), 'abc')
    await user.click(screen.getByRole('button', { name: '検索' }))
    expect(
      await screen.findByText('値を読めませんでした。形を確かめてください。'),
    ).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '価格の下限' })).toHaveFocus()
    expect(countCalls(calls, BUSINESS_DATA_PATHS.search)).toBe(2)
  })

  it('starts over with a new definition after switching the work role', async () => {
    const user = userEvent.setup()
    const roles = [
      { roles: [SALES, ACCOUNTING], current: SALES },
      { roles: [SALES, ACCOUNTING], current: ACCOUNTING },
    ]
    const { calls } = renderTablesScreen(ROUTE, responder(), { roles })
    await user.click(await screen.findByRole('button', { name: '次へ' }))
    await waitFor(() => expect(screen.getByTestId('tables-count')).toHaveTextContent('21〜40'))
    await user.click(screen.getByRole('button', { name: '作業ロール: 営業' }))
    await user.click(screen.getByRole('menuitem', { name: '経理' }))
    await waitFor(() => expect(countCalls(calls, BUSINESS_DATA_PATHS.definition)).toBe(2))
    await waitFor(() => expect(screen.getByTestId('tables-count')).toHaveTextContent('1〜20'))
  })

  it('uses the English texts', async () => {
    renderTablesScreen(ROUTE, responder(), { languages: ['en-US'] })
    expect(await screen.findByRole('heading', { level: 1, name: 'Items' })).toBeInTheDocument()
    await waitFor(() =>
      expect(screen.getByTestId('tables-count')).toHaveTextContent('45 rows (1–20 shown)'),
    )
    expect(screen.getByRole('link', { name: 'Details: Items P1-0' })).toBeInTheDocument()
  })

  it('has no accessibility violations with rows, NULL values and long names', async () => {
    const { view } = renderTablesScreen(
      ROUTE,
      responder({
        definition: {
          displayName: { ja: `長い表示名${'あ'.repeat(60)}`, en: 'Long' },
          listColumns: [wireColumn('code'), wireColumn('name'), wireColumn('note')],
        },
      }),
    )
    await screen.findAllByRole('link', { name: /^詳細:/ })
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
