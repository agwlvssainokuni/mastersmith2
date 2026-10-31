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
// S2 行の詳細のテスト（Intent 261016-business-data-views の U3 の L7、D2・D9・D15・D18・F3、計画 Step 12、
// AC1.4.1〜AC1.4.7・AC1.5.1〜AC1.5.8、U3-NFR2.11・4.2・7.9）。一覧から行を開いて詳細へ移る。
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { BUSINESS_DATA_PATHS } from './businessDataApi'
import { clearBusinessDataStore } from './businessDataStore'
import {
  wireColumn,
  wireDefinition,
  wireDetail,
  wirePage,
  wireRow,
} from './testing/businessDataSamples'
import { renderTablesScreen, type Responder } from './testing/renderTablesScreen'
import { referenceDefinitionOverrides } from './testing/sampleDefinition'

const ROUTE = '/tables?schema=shop&table=item&item=0.1'
const LONG = '長い値'.repeat(3334)

const DETAIL_COLUMNS = [
  wireColumn('code', { displayName: { ja: '商品コード', en: 'Code' } }),
  wireColumn('name', { displayName: { ja: '商品名', en: 'Name' } }),
  wireColumn('note', { displayName: { ja: '備考', en: 'Note' } }),
  wireColumn('secret', { displayName: { ja: '暗証番号', en: 'PIN' }, masked: true }),
  wireColumn('updated', {
    displayName: { ja: '更新の日時', en: 'Updated' },
    valueType: 'DATETIME_TZ',
  }),
]

function responder(detail: (code: string) => { status: number; body: unknown }): Responder {
  return (path, body) => {
    if (path === BUSINESS_DATA_PATHS.definition) {
      return { status: 200, body: wireDefinition({ detailColumns: DETAIL_COLUMNS }) }
    }
    if (path === BUSINESS_DATA_PATHS.search) {
      return { status: 200, body: wirePage([wireRow('A01')]) }
    }
    if (path === BUSINESS_DATA_PATHS.get) {
      return detail((body.key as { code: string }).code)
    }
    return { status: 404, body: {} }
  }
}

const found = (code: string) => ({
  status: 200,
  body: wireDetail(code, {
    values: {
      code,
      name: '<script>alert(1)</script>',
      note: LONG,
      updated: '2026-10-18T00:30:00+00:00',
    },
    truncatedColumns: ['note'],
  }),
})

async function openRow() {
  const user = userEvent.setup()
  await user.click(await screen.findByRole('link', { name: '詳細: 商品 A01' }))
  return user
}

beforeEach(() => clearBusinessDataStore())
afterEach(() => {
  vi.unstubAllGlobals()
  clearBusinessDataStore()
  resetDisplayTestState()
})

describe('BusinessDataDetailPage', () => {
  it('shows the detail columns as a description list and moves the focus to the heading', async () => {
    renderTablesScreen(ROUTE, responder(found))
    await openRow()
    const heading = await screen.findByRole('heading', { level: 1, name: '商品: A01' })
    await waitFor(() => expect(heading).toHaveFocus())
    const list = screen.getByTestId('tables-detail')
    expect(list.tagName).toBe('DL')
    expect(within(list).getByText('<script>alert(1)</script>')).toBeInTheDocument()
    expect(document.querySelector('script')).toBeNull()
    expect(within(list).getByText('2026-10-18 00:30:00+00:00')).toBeInTheDocument()
    expect(within(list).getByText('••••••')).toBeInTheDocument()
    expect(within(list).getByText('伏せた値')).toBeInTheDocument()
  })

  it('shows the whole received long value as text with the truncated note', async () => {
    renderTablesScreen(ROUTE, responder(found))
    await openRow()
    await screen.findByRole('heading', { level: 1, name: '商品: A01' })
    expect(screen.getByText(LONG)).toBeInTheDocument()
    expect(screen.getByText('長いため先頭だけを表示しています')).toBeInTheDocument()
  })

  it('goes back to the list when the token is not in the store, without asking for a row', async () => {
    const { calls } = renderTablesScreen(
      '/tables/row?schema=shop&table=item&item=0.1',
      responder(found),
    )
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/tables$/))
    expect(calls.some((call) => call.path === BUSINESS_DATA_PATHS.get)).toBe(false)
  })

  it('shows a missing row with a way back to the list', async () => {
    renderTablesScreen(
      ROUTE,
      responder(() => ({ status: 404, body: { status: 404, code: 'BUSINESS_ROW_NOT_FOUND' } })),
    )
    const user = await openRow()
    expect(
      await screen.findByRole('heading', {
        level: 1,
        name: '行が見つかりません（ほかの人が削除した可能性があります）。',
      }),
    ).toBeInTheDocument()
    await user.click(screen.getByRole('link', { name: '一覧へ戻る' }))
    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent(/^\/tables$/))
  })

  it('shows the mismatch text for a 409 and offers a retry for an unreachable database', async () => {
    let answer = { status: 409, body: { status: 409, code: 'BUSINESS_TARGET_DB_MISMATCH' } }
    renderTablesScreen(
      ROUTE,
      responder(() => answer),
    )
    const user = await openRow()
    expect(
      await screen.findByRole('heading', {
        level: 1,
        name: 'テーブルの定義とデータベースが食い違っています。管理者に確かめてください。',
      }),
    ).toBeInTheDocument()
    answer = { status: 503, body: { status: 503, code: 'BUSINESS_TARGET_DB_UNAVAILABLE' } }
    await user.click(screen.getByRole('link', { name: '一覧へ戻る' }))
    await user.click(await screen.findByRole('link', { name: '詳細: 商品 A01' }))
    await user.click(await screen.findByRole('button', { name: 'もう一度' }))
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(
      '業務データのデータベースとつながりません。',
    )
  })

  it('round-trips a composite key with special characters through the store only', async () => {
    const user = userEvent.setup()
    const key = { code: 'A/1?#', branch: '% "x\' y' }
    const { calls } = renderTablesScreen(ROUTE, (path) => {
      if (path === BUSINESS_DATA_PATHS.definition) {
        return {
          status: 200,
          body: wireDefinition({
            primaryKeyColumns: ['code', 'branch'],
            rowLabelColumns: ['code', 'branch'],
            listColumns: [wireColumn('code'), wireColumn('branch')],
            detailColumns: [wireColumn('code'), wireColumn('branch')],
          }),
        }
      }
      if (path === BUSINESS_DATA_PATHS.search) {
        return { status: 200, body: wirePage([wireRow('x', { key, values: key })]) }
      }
      return { status: 200, body: wireDetail('x', { key, values: key }) }
    })
    await user.click(
      await screen.findByRole('link', { name: `詳細: 商品 ${key.code} ${key.branch}` }),
    )
    await screen.findByRole('heading', { level: 1, name: `商品: ${key.code} ${key.branch}` })
    expect(calls.find((call) => call.path === BUSINESS_DATA_PATHS.get)?.body.key).toEqual(key)
    const location = screen.getByTestId('location').textContent ?? ''
    expect(location).not.toContain('A/1')
  })

  it('does not put the row label in the document title', async () => {
    const before = document.title
    renderTablesScreen(ROUTE, responder(found))
    await openRow()
    await screen.findByRole('heading', { level: 1, name: '商品: A01' })
    expect(document.title).toBe(before)
    expect(document.title).not.toContain('A01')
  })

  it('has no accessibility violations', async () => {
    const { view } = renderTablesScreen(ROUTE, responder(found))
    await openRow()
    await screen.findByRole('heading', { level: 1, name: '商品: A01' })
    expect(await axe(view.container)).toHaveNoViolations()
  })
})

// Intent 261029-reference-lookup-cleanup の U3（AC3.4.3・AC3.4.5）: 参照の項目を含む定義でも詳細は今のまま
describe('BusinessDataDetailPage with references', () => {
  it('shows a definition with reference columns without the readonly look or label requests', async () => {
    const { calls } = renderTablesScreen(ROUTE, (path, body) => {
      if (path === BUSINESS_DATA_PATHS.definition) {
        return { status: 200, body: wireDefinition(referenceDefinitionOverrides()) }
      }
      if (path === BUSINESS_DATA_PATHS.search) {
        return { status: 200, body: wirePage([wireRow('A01')]) }
      }
      if (path === BUSINESS_DATA_PATHS.get) {
        return {
          status: 200,
          body: wireDetail((body.key as { code: string }).code, {
            values: {
              code: 'A01',
              name: '椅子',
              price: '1200',
              customer: 'C001',
              product: 'P001',
              region: 'R1',
            },
          }),
        }
      }
      return { status: 404, body: {} }
    })
    await openRow()
    const list = await screen.findByTestId('tables-detail')
    expect(within(list).getByText('P001')).toBeInTheDocument()
    expect(document.querySelector('[readonly], [data-testid^="tables-lookup"]')).toBeNull()
    expect(calls.some((call) => call.path === BUSINESS_DATA_PATHS.referenceRows)).toBe(false)
    expect(calls.every((call) => call.body.purpose === undefined)).toBe(true)
  })
})
