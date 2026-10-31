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
// S3 行の作成・S4 行の更新のテスト（Intent 261016-business-data-views の U3 の L7、計画 Step 33、D19〜D25a・D2〜D4・F4・F6・F7、
// U3-NFR4.21・7.9・7.11）。data router で描き、API は fetch を差し替えて ApiClient を通す。
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { ACCOUNTING, SALES } from '../../app/testing/frameTestSupport'
import type { WorkRoleState } from '../../app/work-role/workRoleApi'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { BUSINESS_DATA_PATHS } from './businessDataApi'
import { clearBusinessDataStore, putRowKey } from './businessDataStore'
import {
  SAMPLE_DEFINITION_ID,
  jsonResponse,
  wireColumn,
  wireDefinition,
  wireDetail,
} from './testing/businessDataSamples'
import {
  renderTablesDataRouter,
  type DataRouterResponder,
  type RecordedRequest,
} from './testing/renderTablesDataRouter'
import {
  wireBlocked,
  wireLabel,
  wireLookup,
  wireReference,
  wireRowsPage,
  pickerRow,
} from './testing/referenceSamples'

const QUERY = '?schema=shop&table=item&item=0.1'
const NEW_ROUTE = `/tables/new${QUERY}`
const EDIT_ROUTE = `/tables/edit${QUERY}`

const COLUMNS = [
  wireColumn('code', {
    displayName: { ja: '商品コード', en: 'Code' },
    editableOnCreate: true,
    formPart: 'text',
    required: true,
    nullable: false,
    maxLength: 20,
  }),
  wireColumn('name', {
    displayName: { ja: '商品名', en: 'Name' },
    editableOnCreate: true,
    editableOnUpdate: true,
    formPart: 'text',
    required: true,
    nullable: false,
    maxLength: 20,
  }),
  wireColumn('price', {
    displayName: { ja: '価格', en: 'Price' },
    valueType: 'DECIMAL',
    editableOnCreate: true,
    editableOnUpdate: true,
    formPart: 'text',
    precision: 7,
    scale: 2,
  }),
  wireColumn('note', {
    displayName: { ja: '備考', en: 'Note' },
    editableOnCreate: true,
    editableOnUpdate: true,
    formPart: 'textarea',
  }),
  wireColumn('owner', { displayName: { ja: '登録者', en: 'Owner' } }),
]

const WRITABLE = {
  operations: ['LIST', 'DETAIL', 'CREATE', 'UPDATE', 'DELETE'],
  writeConfigured: true,
  detailColumns: COLUMNS,
}

const DETAIL_VALUES = {
  code: 'A/1 #',
  name: '<script>x</script>',
  price: '1200.50',
  note: null,
  owner: '佐藤',
}

type WriteAnswer = (
  request: RecordedRequest,
) => { status: number; body: unknown } | Response | Promise<Response>

function responder(
  write: WriteAnswer,
  definition: Record<string, unknown> = {},
): DataRouterResponder {
  return (request) => {
    if (request.path === BUSINESS_DATA_PATHS.definition) {
      return { status: 200, body: wireDefinition({ ...WRITABLE, ...definition }) }
    }
    if (request.path === BUSINESS_DATA_PATHS.get) {
      return {
        status: 200,
        body: wireDetail('A/1 #', { key: { code: 'A/1 #' }, values: DETAIL_VALUES }),
      }
    }
    if (request.path === BUSINESS_DATA_PATHS.search) {
      return {
        status: 200,
        body: {
          definitionId: SAMPLE_DEFINITION_ID,
          definitionChanged: false,
          items: [],
          page: 1,
          size: 20,
          total: 0,
        },
      }
    }
    if (request.path === BUSINESS_DATA_PATHS.rows || request.path === BUSINESS_DATA_PATHS.delete) {
      return write(request)
    }
    return { status: 404, body: {} }
  }
}

const created = (key?: Record<string, string>) => () => ({
  status: 201,
  body:
    key === undefined
      ? { definitionId: SAMPLE_DEFINITION_ID }
      : { definitionId: SAMPLE_DEFINITION_ID, key },
})
const written = () => ({ status: 200, body: { definitionId: SAMPLE_DEFINITION_ID } })

function writes(calls: readonly RecordedRequest[]): RecordedRequest[] {
  return calls.filter((call) => call.path === BUSINESS_DATA_PATHS.rows)
}

function renderEdit(
  write: WriteAnswer,
  options: { roles?: WorkRoleState[]; languages?: string[] } = {},
) {
  const rowToken = putRowKey({ code: 'A/1 #' }, SALES.roleId)
  return renderTablesDataRouter(EDIT_ROUTE, responder(write), { state: { rowToken }, ...options })
}

beforeEach(() => {
  clearBusinessDataStore()
})

afterEach(() => {
  vi.unstubAllGlobals()
  resetDisplayTestState()
})

describe('BusinessDataFormPage create', () => {
  it('shows the editable columns with required marks, aria-required and the length hint', async () => {
    renderTablesDataRouter(NEW_ROUTE, responder(created()))
    expect(await screen.findByRole('heading', { name: '商品を作成' })).toBeInTheDocument()
    const code = screen.getByRole('textbox', { name: '商品コード *必須' })
    expect(code).toHaveAttribute('aria-required', 'true')
    expect(code).toHaveAttribute('autocomplete', 'off')
    expect(screen.getAllByText('20 文字まで').length).toBeGreaterThan(0)
    expect(screen.queryByText('登録者')).toBeNull()
    expect(screen.getByTestId('tables-form-save')).toBeDisabled()
  })

  it('validates on save, focuses the summary and keeps the input', async () => {
    const user = userEvent.setup()
    const { calls } = renderTablesDataRouter(NEW_ROUTE, responder(created()))
    await user.type(await screen.findByRole('textbox', { name: '価格' }), '1.234')
    await user.click(screen.getByTestId('tables-form-save'))
    const summary = await screen.findByTestId('tables-form-summary')
    await waitFor(() => expect(summary).toHaveFocus())
    expect(summary).toHaveTextContent('商品コード: 入力してください。')
    expect(summary).toHaveTextContent('価格: 桁が多すぎます。')
    expect(screen.getByRole('textbox', { name: '価格' })).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('textbox', { name: '価格' })).toHaveAccessibleDescription(
      '桁が多すぎます。',
    )
    expect(screen.getByRole('textbox', { name: '価格' })).toHaveValue('1.234')
    expect(writes(calls)).toHaveLength(0)
  })

  it('creates with only non empty values and moves to the detail without the leave dialog', async () => {
    const user = userEvent.setup()
    const { calls, router } = renderTablesDataRouter(NEW_ROUTE, responder(created({ code: 'N/1' })))
    await user.type(await screen.findByRole('textbox', { name: '商品コード *必須' }), 'N/1')
    await user.type(screen.getByRole('textbox', { name: '商品名 *必須' }), '<b>新しい</b>')
    await user.click(screen.getByTestId('tables-form-save'))
    await waitFor(() => expect(router.state.location.pathname).toBe('/tables/row'))
    expect(screen.queryByTestId('tables-leave-dialog')).toBeNull()
    expect(writes(calls)[0]?.method).toBe('POST')
    expect(writes(calls)[0]?.body).toMatchObject({
      schema: 'shop',
      table: 'item',
      definitionId: SAMPLE_DEFINITION_ID,
      values: { code: 'N/1', name: '<b>新しい</b>' },
    })
    expect(await screen.findByText('作成しました。')).toBeInTheDocument()
    expect(router.state.location.search).toBe(QUERY)
    expect(JSON.stringify(router.state.location.state)).not.toContain('N/1')
  })

  it('goes back to the list when the created row has no key', async () => {
    const user = userEvent.setup()
    const { router } = renderTablesDataRouter(NEW_ROUTE, responder(created()))
    await user.type(await screen.findByRole('textbox', { name: '商品コード *必須' }), 'N1')
    await user.type(screen.getByRole('textbox', { name: '商品名 *必須' }), '名')
    await user.click(screen.getByTestId('tables-form-save'))
    await waitFor(() => expect(router.state.location.pathname).toBe('/tables'))
  })

  it('does not send one character over the limit, counting a surrogate pair as one', async () => {
    const user = userEvent.setup()
    const { calls } = renderTablesDataRouter(NEW_ROUTE, responder(created()))
    const name = await screen.findByRole('textbox', { name: '商品名 *必須' })
    await user.type(screen.getByRole('textbox', { name: '商品コード *必須' }), 'N1')
    await user.click(name)
    await user.paste('𠮷'.repeat(21))
    await user.click(screen.getByTestId('tables-form-save'))
    expect(await screen.findByTestId('tables-form-summary')).toHaveTextContent(
      '商品名: 長すぎます。',
    )
    expect(writes(calls)).toHaveLength(0)
    await user.clear(name)
    await user.paste('𠮷'.repeat(20))
    await user.click(screen.getByTestId('tables-form-save'))
    await waitFor(() => expect(writes(calls)).toHaveLength(1))
  })

  it('asks before leaving with changes and stays or leaves', async () => {
    const user = userEvent.setup()
    const { router } = renderTablesDataRouter(NEW_ROUTE, responder(created()))
    await user.type(await screen.findByRole('textbox', { name: '商品名 *必須' }), 'x')
    await user.click(screen.getByTestId('tables-form-cancel'))
    const dialog = await screen.findByRole('alertdialog', { name: '保存していない変更があります' })
    await waitFor(() => expect(within(dialog).getByTestId('tables-leave-stay')).toHaveFocus())
    await user.click(within(dialog).getByTestId('tables-leave-stay'))
    expect(router.state.location.pathname).toBe('/tables/new')
    expect(screen.getByRole('textbox', { name: '商品名 *必須' })).toHaveValue('x')
    await user.click(screen.getByTestId('tables-form-cancel'))
    await user.click(await screen.findByTestId('tables-leave-go'))
    await waitFor(() => expect(router.state.location.pathname).toBe('/tables'))
  })

  it('has no accessibility violations with errors shown', async () => {
    const user = userEvent.setup()
    const { view } = renderTablesDataRouter(NEW_ROUTE, responder(created()))
    await user.type(await screen.findByRole('textbox', { name: '価格' }), 'x')
    await user.click(screen.getByTestId('tables-form-save'))
    await screen.findByTestId('tables-form-summary')
    expect(await axe(view.container)).toHaveNoViolations()
  })
})

describe('BusinessDataFormPage update', () => {
  it('reads the current values, shows key and read-only columns as text and sends only changed columns', async () => {
    const user = userEvent.setup()
    const { calls, router } = renderEdit(written)
    expect(await screen.findByRole('heading', { name: '商品を更新: A/1 #' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '商品名 *必須' })).toHaveValue('<script>x</script>')
    expect(screen.queryByRole('textbox', { name: /商品コード/ })).toBeNull()
    expect(screen.getByText('佐藤')).toBeInTheDocument()
    expect(screen.getAllByText('（変えられません）').length).toBe(2)
    const ids = [...document.querySelectorAll('[data-testid], [id]')].flatMap((element) => [
      element.getAttribute('data-testid') ?? '',
      element.id,
    ])
    expect(ids.some((id) => id.includes('A/1') || id.includes('script'))).toBe(false)
    const price = screen.getByRole('textbox', { name: '価格' })
    await user.clear(price)
    await user.click(screen.getByTestId('tables-form-save'))
    await waitFor(() => expect(router.state.location.pathname).toBe('/tables/row'))
    expect(writes(calls)[0]?.method).toBe('PATCH')
    expect(writes(calls)[0]?.body).toMatchObject({
      key: { code: 'A/1 #' },
      values: { price: null },
    })
    expect(Object.keys((writes(calls)[0]?.body.values ?? {}) as object)).toEqual(['price'])
    expect(screen.queryByTestId('tables-leave-dialog')).toBeNull()
    expect(await screen.findByText('更新しました。')).toBeInTheDocument()
  })

  it('shows the server errors on the inputs and at the top for errors without a field', async () => {
    const user = userEvent.setup()
    renderEdit(() =>
      jsonResponse(409, {
        status: 409,
        code: 'BUSINESS_SAVE_REJECTED',
        fieldErrors: [
          { field: 'name', reason: 'NOT_NULL' },
          { field: '', reason: 'NOT_EDITABLE_NOT_NULL' },
        ],
      }),
    )
    await user.type(await screen.findByRole('textbox', { name: '商品名 *必須' }), '!')
    await user.click(screen.getByTestId('tables-form-save'))
    expect(await screen.findByTestId('tables-form-summary')).toHaveTextContent(
      '商品名: 値を空にできません。',
    )
    expect(screen.getByTestId('tables-form-notice')).toHaveTextContent(
      'このテーブルの作成には、入力できない項目の値が要ります。',
    )
    expect(screen.getByRole('textbox', { name: '商品名 *必須' })).toHaveValue('<script>x</script>!')
  })

  it.each([
    [503, 'BUSINESS_TARGET_DB_UNAVAILABLE', '保存されていません。時間をおいてやり直してください。'],
    [409, 'BUSINESS_SAVE_REJECTED', '保存できませんでした（保存はされていません）。'],
    [409, 'BUSINESS_DUPLICATE_VALUE', '同じ値がすでにあります。'],
    [404, 'BUSINESS_ROW_NOT_FOUND', '行が見つかりません'],
    [413, 'PAYLOAD_TOO_LARGE', '入力が大きすぎるため送れませんでした。'],
  ])('shows the text for %d %s and keeps the input', async (status, code, text) => {
    const user = userEvent.setup()
    renderEdit(() => jsonResponse(status, { status, code }))
    await user.type(await screen.findByRole('textbox', { name: '備考' }), 'a')
    await user.click(screen.getByTestId('tables-form-save'))
    expect(await screen.findByTestId('tables-form-notice')).toHaveTextContent(text)
    expect(screen.getByRole('textbox', { name: '備考' })).toHaveValue('a')
  })

  it('says it is unknown, offers to reopen the row and asks before sending again (D4)', async () => {
    const user = userEvent.setup()
    let count = 0
    const { calls } = renderEdit(() => {
      count += 1
      return count === 1 ? new Response('<html>', { status: 502 }) : written()
    })
    await user.type(await screen.findByRole('textbox', { name: '備考' }), 'a')
    await user.click(screen.getByTestId('tables-form-save'))
    const unknown = await screen.findByTestId('tables-form-unknown')
    expect(unknown).toHaveTextContent('保存されたか分かりません。')
    expect(
      within(unknown).getByRole('link', { name: 'この行を開き直して確かめる' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '備考' })).toHaveValue('a')
    await user.click(screen.getByTestId('tables-form-save'))
    const confirm = await screen.findByRole('alertdialog', { name: '保存済みかもしれません' })
    await waitFor(() =>
      expect(within(confirm).getByTestId('tables-maybe-saved-cancel')).toHaveFocus(),
    )
    await user.click(within(confirm).getByTestId('tables-maybe-saved-send'))
    await waitFor(() => expect(writes(calls)).toHaveLength(2))
  })

  it('replaces the form with the write 403 text and discards the input (D3)', async () => {
    const user = userEvent.setup()
    renderEdit(() => jsonResponse(403, { status: 403, code: 'ACCESS_DENIED' }))
    await user.type(await screen.findByRole('textbox', { name: '備考' }), 'a')
    await user.click(screen.getByTestId('tables-form-save'))
    expect(
      await screen.findByRole('heading', {
        name: '権限が変わったため、保存できませんでした。作業ロールを確かめてください。',
      }),
    ).toBeInTheDocument()
    expect(screen.queryByRole('textbox')).toBeNull()
  })

  it('asks before switching the work role with changes, stays, then leaves and moves to the list (D25a・D2)', async () => {
    const user = userEvent.setup()
    const roles = { roles: [SALES, ACCOUNTING], current: SALES }
    const { router, frame } = renderEdit(written, {
      roles: [roles, { ...roles, current: ACCOUNTING }],
    })
    await user.type(await screen.findByRole('textbox', { name: '備考' }), 'a')
    await user.click(screen.getByRole('button', { name: '作業ロール: 営業' }))
    await user.click(screen.getByRole('menuitem', { name: '経理' }))
    await user.click(await screen.findByTestId('tables-leave-stay'))
    expect(frame.roleCalls.put).toEqual([])
    expect(router.state.location.pathname).toBe('/tables/edit')
    await user.click(screen.getByRole('button', { name: '作業ロール: 営業' }))
    await user.click(screen.getByRole('menuitem', { name: '経理' }))
    await user.click(await screen.findByTestId('tables-leave-go'))
    await waitFor(() => expect(frame.roleCalls.put).toEqual([2]))
    await waitFor(() => expect(router.state.location.pathname).toBe('/tables'))
  })

  it('moves to the list when the row token is not in the store', async () => {
    const { router } = renderTablesDataRouter(EDIT_ROUTE, responder(written), {
      state: { rowToken: 'none' },
    })
    await waitFor(() => expect(router.state.location.pathname).toBe('/tables'))
  })

  it('keeps only the inputs that can still be edited when the definition changes (F6)', async () => {
    const user = userEvent.setup()
    let definitionCalls = 0
    const base = responder(() =>
      jsonResponse(409, { status: 409, code: 'BUSINESS_DEFINITION_CHANGED' }),
    )
    const rowToken = putRowKey({ code: 'A/1 #' }, SALES.roleId)
    renderTablesDataRouter(
      EDIT_ROUTE,
      (request) => {
        if (request.path === BUSINESS_DATA_PATHS.definition) {
          definitionCalls += 1
          return {
            status: 200,
            body: wireDefinition({
              ...WRITABLE,
              definitionId: { dslHash: definitionCalls === 1 ? 'hash-1' : 'hash-2', workRoleId: 1 },
              detailColumns:
                definitionCalls === 1
                  ? COLUMNS
                  : COLUMNS.map((column) =>
                      column.column === 'note'
                        ? {
                            ...column,
                            editableOnCreate: false,
                            editableOnUpdate: false,
                            formPart: null,
                          }
                        : column,
                    ),
            }),
          }
        }
        return base(request)
      },
      { state: { rowToken } },
    )
    await user.type(await screen.findByRole('textbox', { name: '備考' }), 'a')
    await user.type(screen.getByRole('textbox', { name: '価格' }), '0')
    await user.click(screen.getByTestId('tables-form-save'))
    expect(await screen.findByText(/入力できなくなった項目の変更は捨てました/)).toBeInTheDocument()
    expect(screen.queryByRole('textbox', { name: '備考' })).toBeNull()
    expect(screen.getByRole('textbox', { name: '価格' })).toHaveValue('1200.500')
  })

  it('shows English text', async () => {
    renderEdit(written, { languages: ['en-US'] })
    expect(await screen.findByRole('heading', { name: 'Edit Items: A/1 #' })).toBeInTheDocument()
    expect(screen.getByTestId('tables-form-save')).toHaveTextContent('Save')
    expect(screen.getByText('No fields were changed.')).toBeInTheDocument()
  })
})

/** 解決を後に回せる応答（保存の最中に画面を離れる確かめ） */
function deferredResponse() {
  let resolve: (response: Response) => void = () => {}
  const promise = new Promise<Response>((settle) => {
    resolve = settle
  })
  return { promise, resolve }
}

function searchCount(calls: readonly RecordedRequest[]): number {
  return calls.filter((call) => call.path === BUSINESS_DATA_PATHS.search).length
}

describe('BusinessDataFormPage leaving while saving (R-01)', () => {
  const LIST_ROUTE = `/tables${QUERY}`

  /** 画面を離れた後に答えを返し、引き戻されないこと・Toast が出ないことを確かめる */
  async function answerAfterLeaving(
    answer: ReturnType<typeof deferredResponse>,
    router: ReturnType<typeof renderTablesDataRouter>['router'],
    calls: readonly RecordedRequest[],
    body: { status: number; body: unknown },
    toastText: string,
  ) {
    await waitFor(() => expect(router.state.location.pathname).toBe('/tables'))
    // 一覧の画面の副作用が出す読み込みの要求を待つ。React は前の画面の後片付けを先に流すため、作成・更新の画面が
    // 外れた後に答えが届く順が確実になる（PasswordChangePage のテストと同じ形）。
    await waitFor(() => expect(searchCount(calls)).toBeGreaterThanOrEqual(1))
    const keyAfterLeaving = router.state.location.key
    await act(async () => {
      answer.resolve(jsonResponse(body.status, body.body))
      await answer.promise
    })
    // 届いた答えの処理（then）が流れ終わるまで待つ
    await act(async () => {
      await new Promise((settle) => setTimeout(settle, 50))
    })
    expect(router.state.location.pathname).toBe('/tables')
    expect(router.state.location.key).toBe(keyAfterLeaving)
    expect(screen.queryByText(toastText)).toBeNull()
  }

  it('does not move or show a toast when a create answer arrives after leaving by the sidebar', async () => {
    const user = userEvent.setup()
    const answer = deferredResponse()
    const { router, calls } = renderTablesDataRouter(
      NEW_ROUTE,
      responder(() => answer.promise),
    )
    await user.type(await screen.findByRole('textbox', { name: '商品コード *必須' }), 'N/1')
    await user.type(screen.getByRole('textbox', { name: '商品名 *必須' }), '名')
    await user.click(screen.getByTestId('tables-form-save'))
    await waitFor(() => expect(writes(calls)).toHaveLength(1))
    // サイドバーのテーブルの項目と同じく、一覧の道へ移る
    void router.navigate(LIST_ROUTE)
    await user.click(await screen.findByTestId('tables-leave-go'))
    await answerAfterLeaving(
      answer,
      router,
      calls,
      { status: 201, body: { definitionId: SAMPLE_DEFINITION_ID, key: { code: 'N/1' } } },
      '作成しました。',
    )
  })

  it('does not move or show a toast when an update answer arrives after going back', async () => {
    const user = userEvent.setup()
    const answer = deferredResponse()
    const rowToken = putRowKey({ code: 'A/1 #' }, SALES.roleId)
    const { router, calls } = renderTablesDataRouter(
      EDIT_ROUTE,
      responder(() => answer.promise),
      { state: { rowToken }, before: [LIST_ROUTE] },
    )
    await user.type(await screen.findByRole('textbox', { name: '備考' }), 'a')
    await user.click(screen.getByTestId('tables-form-save'))
    await waitFor(() => expect(writes(calls)).toHaveLength(1))
    const searchesBefore = searchCount(calls)
    void router.navigate(-1)
    await user.click(await screen.findByTestId('tables-leave-go'))
    await waitFor(() => expect(searchCount(calls)).toBeGreaterThan(searchesBefore))
    await answerAfterLeaving(answer, router, calls, written(), '更新しました。')
  })

  it('does not move back to the detail when an update answer arrives after switching the work role', async () => {
    const user = userEvent.setup()
    const answer = deferredResponse()
    const roles = { roles: [SALES, ACCOUNTING], current: SALES }
    const rowToken = putRowKey({ code: 'A/1 #' }, SALES.roleId)
    const { router, calls } = renderTablesDataRouter(
      EDIT_ROUTE,
      responder(() => answer.promise),
      { state: { rowToken }, roles: [roles, { ...roles, current: ACCOUNTING }] },
    )
    await user.type(await screen.findByRole('textbox', { name: '備考' }), 'a')
    await user.click(screen.getByTestId('tables-form-save'))
    await waitFor(() => expect(writes(calls)).toHaveLength(1))
    await user.click(screen.getByRole('button', { name: '作業ロール: 営業' }))
    await user.click(screen.getByRole('menuitem', { name: '経理' }))
    await user.click(await screen.findByTestId('tables-leave-go'))
    await answerAfterLeaving(answer, router, calls, written(), '更新しました。')
  })
})

// ---- 他テーブル参照（Intent 261029-reference-lookup-cleanup の U3、frontend-components.md 7節、D1・D4・D5・D13〜D15・D18・
// D19・D31〜D35、AC2.5.4、R-05、security-design.md 2.4）----

const REF_COLUMNS = [
  wireColumn('code', {
    displayName: { ja: '注文番号', en: 'Order' },
    editableOnCreate: true,
    formPart: 'text',
    required: true,
    nullable: false,
    maxLength: 20,
  }),
  wireColumn('customer', {
    displayName: { ja: '顧客', en: 'Customer' },
    editableOnCreate: true,
    editableOnUpdate: true,
    formPart: 'select',
    reference: wireReference(),
  }),
  wireColumn('product', {
    displayName: { ja: '商品', en: 'Product' },
    editableOnCreate: true,
    editableOnUpdate: true,
    formPart: 'lookup',
    reference: wireLookup(),
  }),
  wireColumn('region', {
    displayName: { ja: '地域', en: 'Region' },
    editableOnCreate: true,
    editableOnUpdate: true,
    formPart: 'select',
    nullable: false,
    reference: wireBlocked('REFERENCE', 'NO_PERMISSION'),
  }),
  wireColumn('owner', {
    displayName: { ja: '担当', en: 'Owner' },
    editableOnCreate: false,
    editableOnUpdate: false,
    reference: wireBlocked('REFERENCE', 'NO_PERMISSION'),
  }),
]

const REF_VALUES = { code: 'O1', customer: 'C001', product: 'P001', region: 'R9', owner: 'U1' }

function refResponder(
  write: WriteAnswer,
  options: {
    definition?: (request: RecordedRequest) => { status: number; body: unknown }
    columns?: unknown[]
    label?: (request: RecordedRequest) => { status: number; body: unknown }
  } = {},
): DataRouterResponder {
  return (request) => {
    if (request.path === BUSINESS_DATA_PATHS.definition) {
      return (
        options.definition?.(request) ?? {
          status: 200,
          body: wireDefinition({ ...WRITABLE, detailColumns: options.columns ?? REF_COLUMNS }),
        }
      )
    }
    if (request.path === BUSINESS_DATA_PATHS.get) {
      return {
        status: 200,
        body: wireDetail('O1', { key: { code: 'O1' }, values: REF_VALUES }),
      }
    }
    if (request.path === BUSINESS_DATA_PATHS.referenceRows) {
      if (request.body.value !== undefined) {
        return (
          options.label?.(request) ?? { status: 200, body: wireLabel('P001', '青い 木製の椅子') }
        )
      }
      return { status: 200, body: wireRowsPage([pickerRow(4)], 1, false) }
    }
    if (request.path === BUSINESS_DATA_PATHS.rows) {
      return write(request)
    }
    return { status: 404, body: {} }
  }
}

function renderRefEdit(write: WriteAnswer, options: Parameters<typeof refResponder>[1] = {}) {
  const rowToken = putRowKey({ code: 'O1' }, SALES.roleId)
  return renderTablesDataRouter(EDIT_ROUTE, refResponder(write, options), { state: { rowToken } })
}

const BLOCKED_PERMISSION =
  '必須の参照項目に値を入れられません。参照先を読む権限がないためです。管理者に権限を依頼してください'

describe('BusinessDataFormPage references', () => {
  it('reads the definition with CREATE on the create screen and UPDATE on the update screen', async () => {
    const first = renderTablesDataRouter(NEW_ROUTE, refResponder(created()))
    await screen.findByRole('heading', { name: '商品を作成' })
    const definitionCalls = first.calls.filter((c) => c.path === BUSINESS_DATA_PATHS.definition)
    expect(definitionCalls.map((c) => c.body.purpose)).toEqual(['CREATE'])
    first.view.unmount()
    clearBusinessDataStore()
    const second = renderRefEdit(written)
    await screen.findByTestId('tables-form-heading')
    expect(
      second.calls
        .filter((c) => c.path === BUSINESS_DATA_PATHS.definition)
        .map((c) => c.body.purpose),
    ).toEqual(['UPDATE'])
  })

  it('shows a column that cannot be entered as the existing readonly display without the permission text (R-05)', async () => {
    renderRefEdit(written)
    await screen.findByRole('combobox', { name: '顧客' })
    const owner = screen.getByText('担当').closest('div')
    expect(owner).toHaveClass('tables-form-readonly')
    expect(owner).toHaveTextContent('U1')
    expect(screen.queryAllByText('参照先を読む権限がないため変更できません')).toHaveLength(1)
  })

  it('shows the problem view and no form when the definition fails with 503 (D4)', async () => {
    renderTablesDataRouter(
      NEW_ROUTE,
      refResponder(created(), {
        definition: () => ({
          status: 503,
          body: { status: 503, code: 'BUSINESS_TARGET_DB_UNAVAILABLE' },
        }),
      }),
    )
    expect(await screen.findByTestId('tables-problem')).toBeInTheDocument()
    expect(screen.queryByTestId('tables-form-fields')).toBeNull()
  })

  it('shows the blocked required text as a helper on opening, then blocks the save and focuses the summary', async () => {
    const user = userEvent.setup()
    const { calls, view } = renderTablesDataRouter(NEW_ROUTE, refResponder(created()))
    const region = await screen.findByRole('textbox', { name: '地域' })
    expect(region).toHaveAccessibleDescription(BLOCKED_PERMISSION)
    expect(region).not.toHaveAttribute('aria-invalid')
    expect(screen.queryByRole('alert')).toBeNull()
    expect(await axe(view.container)).toHaveNoViolations()

    await user.type(screen.getByRole('textbox', { name: /^注文番号/ }), 'O9')
    await user.click(screen.getByTestId('tables-form-save'))
    const summary = await screen.findByTestId('tables-form-summary')
    await waitFor(() => expect(summary).toHaveFocus())
    expect(summary).toHaveTextContent(`地域: ${BLOCKED_PERMISSION}`)
    expect(region).toHaveAttribute('aria-invalid', 'true')
    expect(writes(calls)).toHaveLength(0)
    expect(await axe(view.container)).toHaveNoViolations()
  })

  it('does not show the blocked text on the update screen and does not send readonly references', async () => {
    const user = userEvent.setup()
    const { calls } = renderRefEdit(written)
    const region = await screen.findByRole('textbox', { name: '地域' })
    expect(region).toHaveValue('R9')
    expect(region).toHaveAccessibleDescription('参照先を読む権限がないため変更できません')
    await user.selectOptions(screen.getByRole('combobox', { name: '顧客' }), 'C003')
    await user.click(screen.getByTestId('tables-form-save'))
    await waitFor(() => expect(writes(calls)).toHaveLength(1))
    expect(writes(calls)[0]?.body.values).toEqual({ customer: 'C003' })
  })

  it.each([
    [
      'REFERENCE_NOT_FOUND on the select',
      409,
      'BUSINESS_REFERENCE_NOT_FOUND',
      'customer',
      'REFERENCE_NOT_FOUND',
      '顧客: 選べない値です。選び直してください',
    ],
    [
      'NOT_EDITABLE_NOT_NULL on a readonly reference',
      409,
      'BUSINESS_REFERENCE_NOT_FOUND',
      'region',
      'NOT_EDITABLE_NOT_NULL',
      `地域: ${BLOCKED_PERMISSION}`,
    ],
    [
      'REQUIRED on a readonly reference',
      400,
      'VALIDATION_FAILED',
      'region',
      'REQUIRED',
      `地域: ${BLOCKED_PERMISSION}`,
    ],
  ])('shows %s bound to the field', async (_name, status, code, field, reason, text) => {
    const user = userEvent.setup()
    renderRefEdit(() => ({
      status,
      body: { status, code, fieldErrors: [{ field, reason }] },
    }))
    await user.selectOptions(await screen.findByRole('combobox', { name: '顧客' }), 'C003')
    await user.click(screen.getByTestId('tables-form-save'))
    const summary = await screen.findByTestId('tables-form-summary')
    expect(summary).toHaveTextContent(text)
    await waitFor(() => expect(summary).toHaveFocus())
  })

  it('keeps the existing unsaved text for a 503 save', async () => {
    const user = userEvent.setup()
    renderRefEdit(() => ({
      status: 503,
      body: { status: 503, code: 'BUSINESS_TARGET_DB_UNAVAILABLE' },
    }))
    await user.selectOptions(await screen.findByRole('combobox', { name: '顧客' }), 'C003')
    await user.click(screen.getByTestId('tables-form-save'))
    expect(await screen.findByTestId('tables-form-notice')).toHaveTextContent('保存されていません')
  })

  it('reads the stored lookup label, then picks and clears with the picker and sends the value', async () => {
    const user = userEvent.setup()
    const { calls } = renderRefEdit(written)
    const product = await screen.findByRole('textbox', { name: '商品' })
    await waitFor(() => expect(product).toHaveValue('青い 木製の椅子 (P001)'))
    const labelCall = calls.find((c) => c.path === BUSINESS_DATA_PATHS.referenceRows)
    expect(labelCall?.body).toEqual({
      schema: 'shop',
      table: 'item',
      column: 'product',
      value: 'P001',
    })

    await user.click(screen.getByRole('button', { name: '選ぶ…（商品）' }))
    await user.type(await screen.findByRole('textbox', { name: '商品コード（一致）' }), 'P{Enter}')
    await user.click(await screen.findByRole('button', { name: '商品 5を選ぶ' }))
    await waitFor(() => expect(product).toHaveValue('商品 5 (P005)'))
    await user.click(screen.getByTestId('tables-form-save'))
    await waitFor(() => expect(writes(calls)).toHaveLength(1))
    expect(writes(calls)[0]?.body.values).toEqual({ product: 'P005' })
  })

  it('shows the value alone when the label cannot be read', async () => {
    renderRefEdit(written, {
      label: () => ({ status: 403, body: { status: 403, code: 'ACCESS_DENIED' } }),
    })
    const product = await screen.findByRole('textbox', { name: '商品' })
    await waitFor(() => expect(product).not.toHaveAttribute('aria-busy'))
    expect(product).toHaveValue('P001')
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('shows a readonly reference with the value alone and the reason (AC2.5.4)', async () => {
    renderRefEdit(written, {
      columns: [
        REF_COLUMNS[0],
        wireColumn('product', {
          displayName: { ja: '商品', en: 'Product' },
          editableOnCreate: true,
          editableOnUpdate: true,
          formPart: 'lookup',
          reference: wireBlocked('LOOKUP', 'TOO_MANY'),
        }),
      ],
    })
    const product = await screen.findByRole('textbox', { name: '商品' })
    expect(product).toHaveValue('P001')
    expect(product).toHaveAttribute('readonly')
    expect(product).toHaveAccessibleDescription(
      '候補が多いため選べません。管理者に、検索（ピッカー）への切り替えを依頼してください',
    )
    expect(screen.queryByRole('button', { name: /選ぶ…/ })).toBeNull()
  })

  it('discards the input of a reference that became readonly after the definition changed (D35)', async () => {
    const user = userEvent.setup()
    let definitionCount = 0
    renderRefEdit(
      () => ({
        status: 409,
        body: { status: 409, code: 'BUSINESS_DEFINITION_CHANGED', definitionChanged: true },
      }),
      {
        definition: (request) => {
          definitionCount += 1
          const columns =
            definitionCount === 1
              ? REF_COLUMNS
              : REF_COLUMNS.map((column) =>
                  column.column === 'customer'
                    ? { ...column, reference: wireBlocked('REFERENCE', 'TOO_MANY') }
                    : column,
                )
          expect(request.body.purpose).toBe('UPDATE')
          return {
            status: 200,
            body: wireDefinition({
              ...WRITABLE,
              detailColumns: columns,
              definitionId: { dslHash: `h${definitionCount}`, workRoleId: 1 },
            }),
          }
        },
      },
    )
    await user.selectOptions(await screen.findByRole('combobox', { name: '顧客' }), 'C003')
    await user.click(screen.getByTestId('tables-form-save'))
    const customer = await screen.findByRole('textbox', { name: '顧客' })
    expect(customer).toHaveValue('C001')
    expect(await screen.findByTestId('tables-form-notice')).toBeInTheDocument()
    expect(definitionCount).toBe(2)
  })
})

// 直しの Bolt B7（U3 R-01）: NULL を許さない参照の項目は、REQUIRED の規則が無くても画面で必須として止める
const NOT_NULL_REF_COLUMNS = [
  REF_COLUMNS[0],
  wireColumn('customer', {
    displayName: { ja: '顧客', en: 'Customer' },
    editableOnCreate: true,
    editableOnUpdate: true,
    formPart: 'select',
    required: false,
    nullable: false,
    reference: wireReference(),
  }),
  wireColumn('memo', {
    displayName: { ja: 'メモ', en: 'Memo' },
    editableOnCreate: true,
    editableOnUpdate: true,
    formPart: 'text',
    required: false,
    nullable: false,
  }),
]

const NOT_NULL_LOOKUP_COLUMNS = [
  REF_COLUMNS[0],
  wireColumn('product', {
    displayName: { ja: '商品', en: 'Product' },
    editableOnCreate: true,
    editableOnUpdate: true,
    formPart: 'lookup',
    required: false,
    nullable: false,
    reference: wireLookup(),
  }),
]

describe('BusinessDataFormPage NOT NULL references without the REQUIRED rule', () => {
  it('binds REQUIRED to the select when it is left without a choice', async () => {
    const user = userEvent.setup()
    renderTablesDataRouter(NEW_ROUTE, refResponder(created(), { columns: NOT_NULL_REF_COLUMNS }))
    const customer = await screen.findByRole('combobox', { name: '顧客 *必須' })
    expect(customer).toHaveAttribute('aria-required', 'true')
    await user.click(customer)
    await user.tab()
    await waitFor(() => expect(customer).toHaveAttribute('aria-invalid', 'true'))
    expect(customer).toHaveAccessibleDescription('入力してください。')
  })

  it('does not send a create without a choice and focuses the summary', async () => {
    const user = userEvent.setup()
    const { calls } = renderTablesDataRouter(
      NEW_ROUTE,
      refResponder(created(), { columns: NOT_NULL_REF_COLUMNS }),
    )
    await user.type(await screen.findByRole('textbox', { name: /^注文番号/ }), 'O9')
    await user.click(screen.getByTestId('tables-form-save'))
    const summary = await screen.findByTestId('tables-form-summary')
    await waitFor(() => expect(summary).toHaveFocus())
    expect(summary).toHaveTextContent('顧客: 入力してください。')
    expect(summary).not.toHaveTextContent('メモ')
    expect(writes(calls)).toHaveLength(0)
  })

  it('sends the chosen value and keeps the old behavior of a NOT NULL column that is not a reference', async () => {
    const user = userEvent.setup()
    const { calls } = renderTablesDataRouter(
      NEW_ROUTE,
      refResponder(created({ code: 'O9' }), { columns: NOT_NULL_REF_COLUMNS }),
    )
    const memo = await screen.findByRole('textbox', { name: 'メモ' })
    expect(memo).not.toHaveAttribute('aria-required')
    await user.type(screen.getByRole('textbox', { name: /^注文番号/ }), 'O9')
    await user.selectOptions(screen.getByRole('combobox', { name: '顧客 *必須' }), 'C003')
    await user.click(screen.getByTestId('tables-form-save'))
    await waitFor(() => expect(writes(calls)).toHaveLength(1))
    expect(writes(calls)[0]?.body.values).toEqual({ code: 'O9', customer: 'C003' })
  })

  it('does not send a create without a picked lookup value and focuses the summary', async () => {
    const user = userEvent.setup()
    const { calls } = renderTablesDataRouter(
      NEW_ROUTE,
      refResponder(created(), { columns: NOT_NULL_LOOKUP_COLUMNS }),
    )
    const product = await screen.findByRole('textbox', { name: '商品 *必須' })
    expect(product).toHaveAttribute('aria-required', 'true')
    expect(screen.queryByRole('button', { name: /クリア/ })).toBeNull()
    await user.type(screen.getByRole('textbox', { name: /^注文番号/ }), 'O9')
    await user.click(screen.getByTestId('tables-form-save'))
    const summary = await screen.findByTestId('tables-form-summary')
    await waitFor(() => expect(summary).toHaveFocus())
    expect(summary).toHaveTextContent('商品: 入力してください。')
    expect(writes(calls)).toHaveLength(0)
  })
})
