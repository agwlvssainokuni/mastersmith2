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
