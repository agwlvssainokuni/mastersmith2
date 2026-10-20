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
// 業務データの一覧の表のテスト（Intent 261016-business-data-views の U3 の L7、D7〜D10・D14〜D17、計画 Step 12、
// U3-NFR2.11・4.20・4.21・5.9、U4 の R-02）。
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { renderWithProviders, resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { parseRowPage } from './businessDataParse'
import { BusinessDataTable, type BusinessDataTableProps } from './BusinessDataTable'
import type { RowPage } from './businessDataTypes'
import { registration } from './registration'
import { wireColumn, wirePage, wireRow } from './testing/businessDataSamples'
import { sampleDefinition } from './testing/sampleDefinition'
import { JA_TEXTS } from './testing/valueTexts'

const SCRIPT_URL = ['javascript', 'alert(1)'].join(':')
const TRICKY = ['<script>alert(1)</script>', '<img src=x onerror=alert(1)>', '"', "'", SCRIPT_URL]

afterEach(() => resetDisplayTestState())

function page(items: Record<string, unknown>[], overrides: Record<string, unknown> = {}): RowPage {
  const parsed = parseRowPage(wirePage(items, overrides))
  if (parsed === undefined) throw new Error('broken sample page')
  return parsed
}

const DEFINITION = sampleDefinition({
  listColumns: [
    wireColumn('code', { displayName: { ja: '商品コード', en: 'Code' }, width: 120 }),
    wireColumn('name', { displayName: { ja: '<b>商品名</b>', en: 'Name' } }),
    wireColumn('price', { valueType: 'DECIMAL', listFormat: 'NUMBER_GROUPED' }),
    wireColumn('secret', { masked: true }),
  ],
  sortableColumns: ['code'],
  operations: ['LIST', 'DETAIL'],
})

function renderTable(overrides: Partial<BusinessDataTableProps> = {}) {
  const props: BusinessDataTableProps = {
    definition: DEFINITION,
    page: page([
      wireRow('A01', { values: { code: 'A01', name: TRICKY[0], price: '1234567', secret: null } }),
      wireRow('A02', {
        values: { code: 'A02', name: null, price: '5' },
        truncatedColumns: ['name'],
      }),
    ]),
    sort: null,
    size: 20,
    busy: false,
    language: 'ja',
    tableName: '商品',
    texts: JA_TEXTS,
    detailHref: '/tables/row?schema=shop&table=item',
    onSortChange: vi.fn(),
    onPageChange: vi.fn(),
    onOpenRow: vi.fn(),
    ...overrides,
  }
  const view = renderWithProviders(
    <main>
      <BusinessDataTable {...props} />
    </main>,
    {
      registrations: [registration],
    },
  )
  return { props, view }
}

describe('BusinessDataTable', () => {
  it('shows the list columns in order with formatted values and the NULL text', async () => {
    renderTable()
    const headers = (await screen.findAllByRole('columnheader')).map((th) => th.textContent)
    expect(headers).toEqual(['商品コード', '<b>商品名</b>', 'priceの名前', 'secretの名前', '操作'])
    expect(screen.getByText('1,234,567')).toBeInTheDocument()
    expect(screen.getAllByText('••••••')).toHaveLength(2)
    expect(screen.getAllByRole('cell', { name: /^値なし/ }).length).toBeGreaterThan(0)
  })

  it('never puts column names, keys or values into ids and test ids', async () => {
    const { view } = renderTable()
    await screen.findAllByRole('row')
    const marks = Array.from(view.container.querySelectorAll('[id], [data-testid]')).flatMap(
      (el) => [el.getAttribute('id') ?? '', el.getAttribute('data-testid') ?? ''],
    )
    for (const word of ['code', 'name', 'price', 'secret', 'A01', 'A02', 'shop', 'item']) {
      expect(marks.some((mark) => mark.includes(word))).toBe(false)
    }
    expect(screen.getByTestId('tables-row-link-r0')).toBeInTheDocument()
    expect(screen.getByTestId('tables-row-actions-r1')).toBeInTheDocument()
  })

  it('shows markup and script URLs in values as plain text and links only to the detail path', async () => {
    renderTable({
      page: page(
        TRICKY.map((value, i) => wireRow(`K${i}`, { values: { code: value, name: value } })),
      ),
    })
    for (const value of TRICKY) {
      expect((await screen.findAllByText(value)).length).toBeGreaterThan(0)
    }
    expect(document.querySelector('script, img')).toBeNull()
    for (const link of screen.getAllByRole('link')) {
      expect(link).toHaveAttribute('href', '/tables/row?schema=shop&table=item')
    }
  })

  it('names the row link and the actions with the visible text and the row label', async () => {
    const user = userEvent.setup()
    const { props } = renderTable()
    const link = await screen.findByRole('link', { name: '詳細: 商品 A01' })
    expect(link).toHaveTextContent('A01')
    await user.click(link)
    expect(props.onOpenRow).toHaveBeenCalledWith(0, 'link')
    expect(screen.getByRole('button', { name: '操作: A02' })).toHaveTextContent('操作')
  })

  it('marks truncated cells and keeps the full value in the DOM', async () => {
    const long = 'あ'.repeat(100)
    renderTable({
      page: page([
        wireRow('A01', { values: { code: 'A01', name: long }, truncatedColumns: ['name'] }),
      ]),
    })
    expect(await screen.findByText(long)).toBeInTheDocument()
    expect(screen.getByText('（省略）')).toBeInTheDocument()
  })

  it('keeps the full value of a truncated cell in the tooltip', async () => {
    const user = userEvent.setup()
    const long = `${'い'.repeat(99)}終`
    renderTable({
      page: page([
        wireRow('A01', { values: { code: 'A01', name: long }, truncatedColumns: ['name'] }),
      ]),
    })
    await user.hover(await screen.findByText(long))
    expect(await screen.findByRole('tooltip')).toHaveTextContent(long)
  })

  it('cycles the sort of sortable columns and ignores clicks while busy', async () => {
    const user = userEvent.setup()
    const onSortChange = vi.fn()
    const { view } = renderTable({ onSortChange })
    const button = await screen.findByRole('button', { name: '商品コード' })
    await user.click(button)
    expect(onSortChange).toHaveBeenLastCalledWith({ column: 'code', direction: 'ASC' })
    view.unmount()
    renderTable({ onSortChange, sort: { column: 'code', direction: 'ASC' } })
    await user.click(await screen.findByRole('button', { name: '商品コード' }))
    expect(onSortChange).toHaveBeenLastCalledWith({ column: 'code', direction: 'DESC' })
    expect(screen.queryByRole('button', { name: '<b>商品名</b>' })).toBeNull()
  })

  it('ignores the page buttons while busy and passes the next page otherwise', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    const many = page([wireRow('A01')], { total: 45 })
    const first = renderTable({ onPageChange, busy: true, page: many })
    await user.click(await screen.findByRole('button', { name: '次へ' }))
    expect(onPageChange).not.toHaveBeenCalled()
    first.view.unmount()
    renderTable({ onPageChange, page: many })
    await user.click(await screen.findByRole('button', { name: '次へ' }))
    expect(onPageChange).toHaveBeenCalledWith(2)
    expect(within(screen.getByRole('table')).getAllByRole('row')).toHaveLength(2)
  })

  it('has no accessibility violations', async () => {
    const { view } = renderTable()
    await screen.findAllByRole('row')
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
