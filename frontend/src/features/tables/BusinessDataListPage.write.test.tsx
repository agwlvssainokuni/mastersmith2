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
// S1 一覧と S2 詳細の作成・更新・削除の入口のテスト（Intent 261016-business-data-views の U3 の L7、計画 Step 33、D13（N-5）・
// D18・D26、AC2.1.1・AC2.2.6・AC2.3.1・AC3.3.9）。data router で描く。
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { BUSINESS_DATA_PATHS } from './businessDataApi'
import { clearBusinessDataStore } from './businessDataStore'
import {
  SAMPLE_DEFINITION_ID,
  wireColumn,
  wireDefinition,
  wireDetail,
  wirePage,
  wireRow,
} from './testing/businessDataSamples'
import { renderTablesDataRouter, type RecordedRequest } from './testing/renderTablesDataRouter'

const ROUTE = '/tables?schema=shop&table=item&item=0.1'
const EDITABLE = [
  wireColumn('code', { displayName: { ja: '商品コード', en: 'Code' } }),
  wireColumn('name', {
    displayName: { ja: '商品名', en: 'Name' },
    editableOnCreate: true,
    editableOnUpdate: true,
    formPart: 'text',
  }),
]

function render(definition: Record<string, unknown>, items = [wireRow('A01'), wireRow('B02')]) {
  return renderTablesDataRouter(ROUTE, (request: RecordedRequest) => {
    if (request.path === BUSINESS_DATA_PATHS.definition) {
      return { status: 200, body: wireDefinition({ detailColumns: EDITABLE, ...definition }) }
    }
    if (request.path === BUSINESS_DATA_PATHS.search) {
      return { status: 200, body: wirePage(items) }
    }
    if (request.path === BUSINESS_DATA_PATHS.get) {
      return { status: 200, body: wireDetail('A01') }
    }
    if (request.path === BUSINESS_DATA_PATHS.delete) {
      return { status: 200, body: { definitionId: SAMPLE_DEFINITION_ID } }
    }
    return { status: 404, body: {} }
  })
}

const ALL = { operations: ['LIST', 'DETAIL', 'CREATE', 'UPDATE', 'DELETE'], writeConfigured: true }

beforeEach(() => clearBusinessDataStore())

afterEach(() => {
  vi.unstubAllGlobals()
  resetDisplayTestState()
})

describe('BusinessDataListPage writes', () => {
  it('shows the create button only when CREATE is allowed and opens the create screen', async () => {
    const user = userEvent.setup()
    const { router } = render(ALL)
    await user.click(await screen.findByTestId('tables-create'))
    await waitFor(() => expect(router.state.location.pathname).toBe('/tables/new'))
    expect(router.state.location.search).toBe('?schema=shop&table=item&item=0.1')
  })

  it('hides the create button and the write notice for someone who can only read (N-5)', async () => {
    render({
      operations: ['LIST', 'DETAIL'],
      writeConfigured: false,
      detailColumns: [wireColumn('code'), wireColumn('name')],
    })
    expect(await screen.findByRole('link', { name: '詳細: 商品 A01' })).toBeInTheDocument()
    expect(screen.queryByTestId('tables-create')).toBeNull()
    expect(screen.queryByText(/書き込みの設定がありません/)).toBeNull()
  })

  it('shows the write notice to someone who could write when writing is not configured (N-5)', async () => {
    render({ operations: ['LIST', 'DETAIL'], writeConfigured: false })
    expect(await screen.findByText(/書き込みの設定がありません/)).toBeInTheDocument()
    expect(screen.queryByTestId('tables-create')).toBeNull()
  })

  it('adds the create entry to an empty table', async () => {
    render(ALL, [])
    expect(await screen.findByTestId('tables-empty-table')).toBeInTheDocument()
    expect(screen.getAllByTestId('tables-create')).toHaveLength(2)
  })

  it('opens the edit screen from the row actions without the key in the path', async () => {
    const user = userEvent.setup()
    const { router } = render(ALL)
    await user.click(await screen.findByRole('button', { name: '操作: A01' }))
    await user.click(screen.getByRole('menuitem', { name: '更新' }))
    await waitFor(() => expect(router.state.location.pathname).toBe('/tables/edit'))
    expect(router.state.location.search).not.toContain('A01')
    expect(JSON.stringify(router.state.location.state)).not.toContain('A01')
  })

  it('deletes from the row actions and reads the list again', async () => {
    const user = userEvent.setup()
    const { calls } = render(ALL)
    await user.click(await screen.findByRole('button', { name: '操作: B02' }))
    await user.click(screen.getByRole('menuitem', { name: '削除' }))
    await user.click(await screen.findByTestId('tables-delete-confirm'))
    expect(await screen.findByText('削除しました。')).toBeInTheDocument()
    const deleted = calls.filter((call) => call.path === BUSINESS_DATA_PATHS.delete)
    expect(deleted[0]?.body.key).toEqual({ code: 'B02' })
    await waitFor(() =>
      expect(calls.filter((call) => call.path === BUSINESS_DATA_PATHS.search)).toHaveLength(2),
    )
  })

  it('shows edit and delete on the detail only when they are allowed', async () => {
    const user = userEvent.setup()
    const first = render({ operations: ['LIST', 'DETAIL', 'DELETE'], writeConfigured: true })
    await user.click(await screen.findByRole('link', { name: '詳細: 商品 A01' }))
    expect(await screen.findByTestId('tables-detail-delete')).toBeInTheDocument()
    expect(screen.queryByTestId('tables-detail-update')).toBeNull()
    first.view.unmount()
    clearBusinessDataStore()
    render(ALL)
    await user.click(await screen.findByRole('link', { name: '詳細: 商品 A01' }))
    expect(await screen.findByTestId('tables-detail-update')).toHaveTextContent('更新')
  })
})
