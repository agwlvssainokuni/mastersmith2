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
// S5 削除の確かめのテスト（Intent 261016-business-data-views の U3 の L7、計画 Step 33、D26・D4・D3、AC2.3.1〜AC2.3.5）。
// 部品だけの確かめと、詳細の画面から削除する流れ（useRowDeleteFlow を通す）を data router で確かめる。
import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ModalStackProvider } from 'make-you-chic-ui'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { SALES } from '../../app/testing/frameTestSupport'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { BUSINESS_DATA_PATHS } from './businessDataApi'
import { clearBusinessDataStore, putRowKey } from './businessDataStore'
import { DeleteConfirmModal } from './DeleteConfirmModal'
import {
  SAMPLE_DEFINITION_ID,
  jsonResponse,
  wireDefinition,
  wireDetail,
  wirePage,
  wireRow,
} from './testing/businessDataSamples'
import { renderTablesDataRouter, type RecordedRequest } from './testing/renderTablesDataRouter'

const QUERY = '?schema=shop&table=item&item=0.1'

type DeleteAnswer = () => { status: number; body: unknown } | Response

function renderDetail(answer: DeleteAnswer) {
  const rowToken = putRowKey({ code: 'A01' }, SALES.roleId)
  return renderTablesDataRouter(
    `/tables/row${QUERY}`,
    (request: RecordedRequest) => {
      if (request.path === BUSINESS_DATA_PATHS.definition) {
        return {
          status: 200,
          body: wireDefinition({ operations: ['LIST', 'DETAIL', 'DELETE'], writeConfigured: true }),
        }
      }
      if (request.path === BUSINESS_DATA_PATHS.get) {
        return { status: 200, body: wireDetail('A01') }
      }
      if (request.path === BUSINESS_DATA_PATHS.search) {
        return { status: 200, body: wirePage([wireRow('B02')]) }
      }
      if (request.path === BUSINESS_DATA_PATHS.delete) {
        return answer()
      }
      return { status: 404, body: {} }
    },
    { state: { rowToken } },
  )
}

function deletes(calls: readonly RecordedRequest[]) {
  return calls.filter((call) => call.path === BUSINESS_DATA_PATHS.delete)
}

beforeEach(() => clearBusinessDataStore())

afterEach(() => {
  vi.unstubAllGlobals()
  resetDisplayTestState()
})

describe('DeleteConfirmModal', () => {
  it('is an alertdialog with the row in the title and the first focus on cancel', async () => {
    const user = userEvent.setup()
    renderDetail(() => ({ status: 200, body: { definitionId: SAMPLE_DEFINITION_ID } }))
    await user.click(await screen.findByTestId('tables-detail-delete'))
    const dialog = await screen.findByRole('alertdialog', { name: '商品 A01 を削除しますか' })
    expect(dialog).toHaveTextContent('この操作は元に戻せません。')
    await waitFor(() => expect(within(dialog).getByTestId('tables-delete-cancel')).toHaveFocus())
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
  })

  it('deletes, goes back to the list and tells it by a toast', async () => {
    const user = userEvent.setup()
    const { calls, router } = renderDetail(() => ({
      status: 200,
      body: { definitionId: SAMPLE_DEFINITION_ID },
    }))
    await user.click(await screen.findByTestId('tables-detail-delete'))
    await user.click(await screen.findByTestId('tables-delete-confirm'))
    await waitFor(() => expect(router.state.location.pathname).toBe('/tables'))
    expect(deletes(calls)[0]?.body).toMatchObject({
      key: { code: 'A01' },
      definitionId: SAMPLE_DEFINITION_ID,
    })
    expect(await screen.findByText('削除しました。')).toBeInTheDocument()
  })

  it.each([
    [409, 'BUSINESS_SAVE_REJECTED', 'ほかのデータから使われているため削除できません。'],
    [404, 'BUSINESS_ROW_NOT_FOUND', '行が見つかりません（ほかの人が削除した可能性があります）。'],
    [503, 'BUSINESS_TARGET_DB_UNAVAILABLE', '削除されていません。時間をおいてやり直してください。'],
  ])('shows %d %s inside the dialog', async (status, code, text) => {
    const user = userEvent.setup()
    renderDetail(() => jsonResponse(status, { status, code }))
    await user.click(await screen.findByTestId('tables-detail-delete'))
    await user.click(await screen.findByTestId('tables-delete-confirm'))
    expect(await screen.findByTestId('tables-delete-alert')).toHaveTextContent(text)
    expect(screen.getByRole('alertdialog')).toBeInTheDocument()
  })

  it('says it is unknown and asks again before sending again (D4)', async () => {
    const user = userEvent.setup()
    let count = 0
    const { calls } = renderDetail(() => {
      count += 1
      return count === 1
        ? new Response('', { status: 504 })
        : jsonResponse(404, { status: 404, code: 'BUSINESS_ROW_NOT_FOUND' })
    })
    await user.click(await screen.findByTestId('tables-detail-delete'))
    await user.click(await screen.findByTestId('tables-delete-confirm'))
    expect(await screen.findByTestId('tables-delete-unknown')).toHaveTextContent(
      '削除されたか分かりません。',
    )
    await user.click(screen.getByTestId('tables-delete-confirm'))
    expect(
      await screen.findByRole('alertdialog', { name: '保存済みかもしれません' }),
    ).toBeInTheDocument()
    expect(deletes(calls)).toHaveLength(1)
    await user.click(screen.getByTestId('tables-delete-confirm'))
    await waitFor(() => expect(deletes(calls)).toHaveLength(2))
    expect(await screen.findByTestId('tables-delete-alert')).toHaveTextContent('行が見つかりません')
  })

  it('replaces the detail with the delete 403 text (D3)', async () => {
    const user = userEvent.setup()
    renderDetail(() => jsonResponse(403, { status: 403, code: 'ACCESS_DENIED' }))
    await user.click(await screen.findByTestId('tables-detail-delete'))
    await user.click(await screen.findByTestId('tables-delete-confirm'))
    expect(
      await screen.findByRole('heading', {
        name: '権限が変わったため、削除できませんでした。作業ロールを確かめてください。',
      }),
    ).toBeInTheDocument()
    expect(screen.queryByTestId('tables-detail')).toBeNull()
  })

  it('shows the title as text and has no accessibility violations', async () => {
    const { container } = render(
      <ModalStackProvider>
        <DeleteConfirmModal
          title={'<img src=x onerror=alert(1)> を削除しますか'}
          phase="confirm"
          busy={false}
          slow={false}
          unknown
          onCheck={() => {}}
          onCancel={() => {}}
          onConfirm={() => {}}
        />
      </ModalStackProvider>,
    )
    expect(
      await screen.findByRole('alertdialog', {
        name: '<img src=x onerror=alert(1)> を削除しますか',
      }),
    ).toBeInTheDocument()
    expect(container.ownerDocument.querySelector('img')).toBeNull()
    expect(await axe(document.body)).toHaveNoViolations()
  })
})
