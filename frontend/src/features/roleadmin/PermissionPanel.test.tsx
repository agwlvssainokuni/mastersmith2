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
// S4 権限の設定のテスト（useRolePermissions・useUnsavedGuard を通す。Intent 261004-role-menu の U6、functional-spec.md の
// 3.2・4.3・7節・8.1、D10〜D15、NFR1.4）。useBlocker を使うため data router で描き、API は差し替える。
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import type { PermissionNode } from './api/types'
import { PermissionPanel } from './PermissionPanel'
import {
  apiError,
  columnNodeOf,
  deferred,
  DETAIL_MARKER,
  explicitOf,
  fakeRoleAdminApi,
  nodeOf,
  tableNodeOf,
  type FakeRoleAdminApi,
} from './testing/fixtures'
import { renderRoleAdminDataRouter } from './testing/renderRoleAdmin'

const SCHEMA = nodeOf({
  explicit: explicitOf({ main: 'READ', create: true }),
  effective: { main: 'READ', create: true, delete: false },
  inheritedFrom: 'EXPLICIT',
})

const TABLE = tableNodeOf({
  effective: { main: 'READ', create: true, delete: false },
  inheritedFrom: 'SCHEMA',
})

const COLUMN = columnNodeOf({
  effective: { main: 'READ', create: false, delete: false },
  inheritedFrom: 'TABLE',
})

function panelApi(): FakeRoleAdminApi {
  const api = fakeRoleAdminApi()
  api.listSchemas.mockResolvedValue([SCHEMA])
  api.listTables.mockResolvedValue([TABLE])
  api.listColumns.mockResolvedValue([COLUMN])
  return api
}

function renderPanel(api: FakeRoleAdminApi, onMissing = vi.fn()) {
  const view = renderRoleAdminDataRouter(
    [
      {
        path: '/admin/roles/:roleId',
        element: <PermissionPanel roleId={7} api={api} onMissing={onMissing} />,
      },
      { path: '/admin/roles', element: <p data-testid="list-screen">list</p> },
    ],
    { route: '/admin/roles/7' },
  )
  return { ...view, onMissing }
}

function treeButton(name: string): HTMLElement {
  return within(screen.getByTestId('shared-tree')).getByRole('button', { name })
}

async function selectTable(user: ReturnType<typeof userEvent.setup>) {
  await user.click(await screen.findByRole('button', { name: '受注明細' }))
  await screen.findByTestId('roleadmin-column-table')
}

describe('PermissionPanel', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('opens and selects the only schema, and loads its tables with the schema as a query value', async () => {
    const api = panelApi()
    renderPanel(api)
    expect(
      await screen.findByRole('heading', { name: 'スキーマの設定: 販売DB' }),
    ).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '販売DBを閉じる' })).toHaveAttribute(
      'aria-expanded',
      'true',
    )
    expect(treeButton('販売DB')).toHaveAttribute('aria-current', 'true')
    expect(await screen.findByRole('button', { name: '受注明細' })).toBeInTheDocument()
    expect(api.listTables).toHaveBeenCalledWith(7, 'SALES')
    expect(api.listTables).toHaveBeenCalledTimes(1)
  })

  it('shows the table settings with the menu state, and columns with the main permission only', async () => {
    const user = userEvent.setup()
    const api = panelApi()
    renderPanel(api)
    await selectTable(user)
    expect(treeButton('受注明細')).toHaveAttribute('aria-current', 'true')
    expect(screen.getByRole('heading', { name: 'テーブルの設定: 受注明細' })).toBeInTheDocument()
    expect(screen.getByTestId('roleadmin-node-row')).toHaveTextContent('業務のメニュー: 出る')
    expect(api.listColumns).toHaveBeenCalledWith(7, 'SALES', 'ORDER_LINE')
    const columns = screen.getByTestId('roleadmin-column-table')
    expect(within(columns).getByRole('combobox', { name: '受注番号の主権限' })).toBeInTheDocument()
    expect(within(columns).queryByRole('combobox', { name: '受注番号の CREATE' })).toBeNull()
    expect(
      within(columns)
        .getAllByRole('columnheader')
        .map((cell) => cell.textContent),
    ).toEqual(['対象', '主権限', '実効の値'])
  })

  it('describes the effective values with their sources in text', async () => {
    const user = userEvent.setup()
    renderPanel(panelApi())
    const schemaRow = await screen.findByTestId('roleadmin-node-row')
    expect(schemaRow).toHaveTextContent('主権限: READ（明示）')
    expect(schemaRow).toHaveTextContent('CREATE: 可（明示）')
    expect(schemaRow).toHaveTextContent('DELETE: 不可')
    expect(schemaRow).not.toHaveTextContent('不可（')
    await selectTable(user)
    expect(screen.getByTestId('roleadmin-node-row')).toHaveTextContent(
      '主権限: READ（スキーマ 販売DB から継承）',
    )
    expect(screen.getByTestId('roleadmin-column-ORDER_NO')).toHaveTextContent(
      '主権限: READ（テーブル 受注明細 から継承）',
    )
  })

  it('enables saving only with changes, explains why it is disabled, and forgets a value set back', async () => {
    const user = userEvent.setup()
    renderPanel(panelApi())
    const save = await screen.findByTestId('roleadmin-save')
    expect(save).toBeDisabled()
    expect(save).toHaveAccessibleDescription('変更はありません')
    const select = screen.getByRole('combobox', { name: '販売DBの主権限' })
    await user.selectOptions(select, 'FULL')
    expect(save).toBeEnabled()
    expect(screen.getByTestId('roleadmin-unsaved-count')).toHaveTextContent(
      '保存していない変更: 1 件',
    )
    expect(screen.getByTestId('roleadmin-node-row')).toHaveTextContent('（未保存）')
    // 実効の値の表示は保存まで変えない。
    expect(screen.getByTestId('roleadmin-node-row')).toHaveTextContent('主権限: READ（明示）')
    await user.selectOptions(select, 'READ')
    expect(save).toBeDisabled()
    expect(screen.getByTestId('roleadmin-unsaved-count')).toHaveTextContent(
      '保存していない変更: 0 件',
    )
  })

  it('saves only the changed targets in the selected scope, then reloads and keeps focus on save', async () => {
    const user = userEvent.setup()
    const api = panelApi()
    api.savePermissions.mockResolvedValue()
    renderPanel(api)
    await selectTable(user)
    await user.selectOptions(screen.getByRole('combobox', { name: '受注明細の DELETE' }), 'true')
    await user.selectOptions(screen.getByRole('combobox', { name: '受注番号の主権限' }), 'NONE')
    await user.click(screen.getByTestId('roleadmin-save'))
    expect(api.savePermissions).toHaveBeenCalledWith(
      7,
      { schemaName: 'SALES', tableName: 'ORDER_LINE' },
      [
        // 鍵（名前の JSON の配列）の順に並ぶ。
        {
          schemaName: 'SALES',
          tableName: 'ORDER_LINE',
          columnName: 'ORDER_NO',
          main: 'NONE',
          create: null,
          delete: null,
        },
        {
          schemaName: 'SALES',
          tableName: 'ORDER_LINE',
          columnName: null,
          main: null,
          create: null,
          delete: true,
        },
      ],
    )
    expect(await screen.findByTestId('roleadmin-perm-notice')).toHaveTextContent('保存しました。')
    await waitFor(() => expect(api.listSchemas).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(api.listColumns).toHaveBeenCalledTimes(2))
    expect(screen.getByTestId('roleadmin-unsaved-count')).toHaveTextContent(
      '保存していない変更: 0 件',
    )
    expect(screen.getByTestId('roleadmin-save')).toHaveFocus()
    // 開いている木の節は読み直した一覧を使い、同じテーブルの一覧を二重に読まない。
    expect(api.listTables).toHaveBeenCalledTimes(2)
  })

  it('keeps the draft on ROLE_BUSY and shows the busy message without the detail', async () => {
    const user = userEvent.setup()
    const api = panelApi()
    api.savePermissions.mockRejectedValue(apiError(409, 'ROLE_BUSY'))
    renderPanel(api)
    await user.selectOptions(
      await screen.findByRole('combobox', { name: '販売DBの主権限' }),
      'FULL',
    )
    await user.click(screen.getByTestId('roleadmin-save'))
    expect(await screen.findByTestId('roleadmin-perm-notice')).toHaveTextContent(
      'ほかの操作と重なりました。少し待ってからもう一度お試しください。',
    )
    expect(screen.getByRole('combobox', { name: '販売DBの主権限' })).toHaveValue('FULL')
    expect(screen.getByTestId('roleadmin-unsaved-count')).toHaveTextContent('1 件')
    expect(document.body).not.toHaveTextContent(DETAIL_MARKER)
  })

  it('drops the draft and reloads on ROLE_NO_CHANGE', async () => {
    const user = userEvent.setup()
    const api = panelApi()
    api.savePermissions.mockRejectedValue(apiError(409, 'ROLE_NO_CHANGE'))
    renderPanel(api)
    await user.selectOptions(
      await screen.findByRole('combobox', { name: '販売DBの主権限' }),
      'FULL',
    )
    await user.click(screen.getByTestId('roleadmin-save'))
    expect(await screen.findByTestId('roleadmin-perm-notice')).toHaveTextContent(
      '変わる点がありませんでした。',
    )
    await waitFor(() => expect(api.listSchemas).toHaveBeenCalledTimes(2))
    expect(screen.getByRole('combobox', { name: '販売DBの主権限' })).toHaveValue('READ')
  })

  it('keeps only the draft of targets still in the DSL on PERMISSION_TARGET_NOT_IN_DSL', async () => {
    const user = userEvent.setup()
    const api = panelApi()
    api.savePermissions.mockRejectedValue(apiError(409, 'PERMISSION_TARGET_NOT_IN_DSL'))
    renderPanel(api)
    await selectTable(user)
    await user.selectOptions(screen.getByRole('combobox', { name: '受注明細の主権限' }), 'FULL')
    await user.selectOptions(screen.getByRole('combobox', { name: '受注番号の主権限' }), 'NONE')
    api.listColumns.mockResolvedValue([{ ...COLUMN, inCurrentDsl: false }])
    await user.click(screen.getByTestId('roleadmin-save'))
    expect(await screen.findByTestId('roleadmin-perm-notice')).toHaveTextContent(
      '保存しようとした対象が今の DSL にありません。',
    )
    await waitFor(() =>
      expect(screen.getByTestId('roleadmin-unsaved-count')).toHaveTextContent('1 件'),
    )
    expect(screen.getByRole('combobox', { name: '受注明細の主権限' })).toHaveValue('FULL')
    expect(screen.getByTestId('roleadmin-column-ORDER_NO')).toHaveTextContent('⚠ 今の DSL に無い')
  })

  it('shows the guide to the DSL screen when no DSL is applied', async () => {
    const api = panelApi()
    api.listSchemas.mockRejectedValue(apiError(409, 'DSL_NOT_APPLIED'))
    renderPanel(api)
    expect(await screen.findByTestId('roleadmin-nodsl')).toHaveTextContent(
      '権限を設定する対象がありません。DSL の管理の画面で DSL を適用してください。',
    )
    expect(screen.getByRole('link', { name: 'DSL の管理へ' })).toHaveAttribute('href', '/admin/dsl')
  })

  it('reports a missing role when saving answers ROLE_NOT_FOUND', async () => {
    const user = userEvent.setup()
    const api = panelApi()
    api.savePermissions.mockRejectedValue(apiError(404, 'ROLE_NOT_FOUND'))
    const { onMissing } = renderPanel(api)
    await user.selectOptions(
      await screen.findByRole('combobox', { name: '販売DBの主権限' }),
      'NONE',
    )
    await user.click(screen.getByTestId('roleadmin-save'))
    await waitFor(() => expect(onMissing).toHaveBeenCalledTimes(1))
  })

  it('offers only "clear" for an orphan table and clears it with its set columns', async () => {
    const user = userEvent.setup()
    const api = panelApi()
    const orphan: PermissionNode = {
      ...TABLE,
      inCurrentDsl: false,
      explicit: explicitOf({ main: 'READ' }),
    }
    api.listTables.mockResolvedValue([orphan])
    api.listColumns.mockResolvedValue([
      { ...COLUMN, inCurrentDsl: false, explicit: explicitOf({ main: 'NONE' }) },
      columnNodeOf({ columnName: 'NOTE', displayName: '備考', inCurrentDsl: false }),
    ])
    api.clearPermissions.mockResolvedValue()
    renderPanel(api)
    await selectTable(user)
    const row = screen.getByTestId('roleadmin-node-row')
    expect(row).toHaveTextContent('この設定は今の DSL に無いため使われていません。')
    expect(within(row).queryByRole('combobox')).toBeNull()
    await user.click(within(row).getByRole('button', { name: '設定を消す' }))
    expect(api.clearPermissions).toHaveBeenCalledWith(7, [
      { schemaName: 'SALES', tableName: 'ORDER_LINE', columnName: null },
      { schemaName: 'SALES', tableName: 'ORDER_LINE', columnName: 'ORDER_NO' },
    ])
    expect(await screen.findByTestId('roleadmin-perm-notice')).toHaveTextContent(
      '設定を消しました。',
    )
  })

  it('asks before selecting another tree node with unsaved changes; stay keeps, leave discards', async () => {
    const user = userEvent.setup()
    renderPanel(panelApi())
    await user.selectOptions(
      await screen.findByRole('combobox', { name: '販売DBの主権限' }),
      'FULL',
    )
    await user.click(await screen.findByRole('button', { name: '受注明細' }))
    let dialog = await screen.findByRole('alertdialog', { name: '保存していない変更' })
    await user.click(within(dialog).getByRole('button', { name: '留まる' }))
    await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
    expect(treeButton('販売DB')).toHaveAttribute('aria-current', 'true')
    expect(screen.getByRole('combobox', { name: '販売DBの主権限' })).toHaveValue('FULL')
    await user.click(treeButton('受注明細'))
    dialog = await screen.findByRole('alertdialog', { name: '保存していない変更' })
    await user.click(within(dialog).getByRole('button', { name: '移る' }))
    expect(
      await screen.findByRole('heading', { name: 'テーブルの設定: 受注明細' }),
    ).toBeInTheDocument()
    expect(screen.getByTestId('roleadmin-unsaved-count')).toHaveTextContent('0 件')
  })

  it('blocks browser back navigation with unsaved changes', async () => {
    const user = userEvent.setup()
    const { router } = renderPanel(panelApi())
    await user.selectOptions(
      await screen.findByRole('combobox', { name: '販売DBの主権限' }),
      'FULL',
    )
    await act(async () => {
      await router.navigate('/admin/roles')
    })
    expect(
      await screen.findByRole('alertdialog', { name: '保存していない変更' }),
    ).toBeInTheDocument()
    expect(router.state.location.pathname).toBe('/admin/roles/7')
  })

  it('shows the column load failure with retry, and discards changes with the discard button', async () => {
    const user = userEvent.setup()
    const api = panelApi()
    api.listColumns.mockRejectedValueOnce(apiError(500))
    renderPanel(api)
    await user.click(await screen.findByRole('button', { name: '受注明細' }))
    const error = await screen.findByTestId('roleadmin-columns-error')
    await user.click(within(error).getByRole('button', { name: 'もう一度' }))
    expect(await screen.findByTestId('roleadmin-column-table')).toBeInTheDocument()
    await user.selectOptions(screen.getByRole('combobox', { name: '受注番号の主権限' }), 'FULL')
    await user.click(screen.getByTestId('roleadmin-discard'))
    expect(screen.getByRole('combobox', { name: '受注番号の主権限' })).toHaveValue('')
  })

  it('ignores the save button while saving and shows the inputs as busy', async () => {
    const user = userEvent.setup()
    const api = panelApi()
    const answer = deferred<void>()
    api.savePermissions.mockReturnValue(answer.promise)
    renderPanel(api)
    await user.selectOptions(
      await screen.findByRole('combobox', { name: '販売DBの主権限' }),
      'FULL',
    )
    await user.click(screen.getByTestId('roleadmin-save'))
    await user.click(screen.getByTestId('roleadmin-save'))
    expect(api.savePermissions).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('combobox', { name: '販売DBの主権限' })).toBeDisabled()
    await act(async () => {
      answer.resolve()
      await answer.promise
    })
  })

  it('has no accessibility violations with a table, its columns and an orphan mark', async () => {
    const user = userEvent.setup()
    const api = panelApi()
    api.listColumns.mockResolvedValue([
      COLUMN,
      { ...COLUMN, columnName: 'X', displayName: '<b>x</b>', inCurrentDsl: false },
    ])
    const view = renderPanel(api)
    await selectTable(user)
    expect(screen.getByText('<b>x</b>')).toBeInTheDocument()
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
