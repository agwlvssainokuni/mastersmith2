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
// S3 ロールの一覧のテスト（useRoleList を通す。Intent 261004-role-menu の U6、functional-spec.md の 3.1・4.1・4.2・5節・
// 8.1、D5〜D9・D29・D30、NFR2.10・NFR2.11）。API の関数を差し替え、答えを返さない約束で止めて途中の表示を見る。
import { act, configure, screen, waitFor, within } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import type { RolePage } from './api/types'
import { RoleListPage } from './RoleListPage'
import {
  apiError,
  deferred,
  DETAIL_MARKER,
  fakeRoleAdminApi,
  IMG_NAME,
  LONG_ROLE_NAME,
  NETWORK_ERROR,
  roleRowOf,
  rolePageOf,
  roleRowsOf,
  sampleRoleRows,
  SCRIPT_NAME,
  type FakeRoleAdminApi,
} from './testing/fixtures'
import { renderRoleAdmin } from './testing/renderRoleAdmin'

async function renderPage(api: FakeRoleAdminApi) {
  const view = renderRoleAdmin(<RoleListPage api={api} />)
  await screen.findByTestId('roleadmin-list-page')
  return view
}

function listOf(page: RolePage) {
  return fakeRoleAdminApi({ listRoles: () => Promise.resolve(page) })
}

async function openMenu(user: UserEvent, name: string) {
  await user.click(await screen.findByRole('button', { name: `${name}の操作` }))
  return screen.findByRole('menu')
}

describe('RoleListPage', () => {
  afterEach(() => {
    configure({ reactStrictMode: false })
    resetDisplayTestState()
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('shows the rows in server order with counts, and names that look like markup as text', async () => {
    const api = listOf(rolePageOf(sampleRoleRows()))
    await renderPage(api)
    const table = await screen.findByRole('table', { name: 'ロールの一覧' })
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(7)
    expect(rows[0]).toHaveTextContent('営業')
    expect(rows[0]).toHaveTextContent('2 人')
    expect(within(table).getByText(SCRIPT_NAME)).toBeInTheDocument()
    expect(within(table).getByText(IMG_NAME)).toBeInTheDocument()
    expect(document.querySelector('script')).toBeNull()
    expect(document.querySelector('img')).toBeNull()
    expect(screen.getByTestId('roleadmin-status')).toHaveTextContent('1〜7 件目 / 全 7 件')
    expect(api.listRoles).toHaveBeenCalledWith(1)
  })

  it('shows the empty guide with a create button when there are no roles', async () => {
    const user = userEvent.setup()
    await renderPage(listOf(rolePageOf([])))
    expect(await screen.findByTestId('roleadmin-empty')).toHaveTextContent('ロールがありません。')
    await user.click(screen.getByTestId('roleadmin-empty-create'))
    expect(await screen.findByRole('dialog', { name: 'ロールを作る' })).toBeInTheDocument()
  })

  it('shows the load failure and reloads with "retry", moving focus to the heading', async () => {
    const user = userEvent.setup()
    const api = fakeRoleAdminApi()
    api.listRoles
      .mockRejectedValueOnce(NETWORK_ERROR)
      .mockResolvedValueOnce(rolePageOf([roleRowOf()]))
    await renderPage(api)
    expect(await screen.findByTestId('roleadmin-load-error')).toHaveTextContent(
      '読み込めませんでした。',
    )
    await user.click(screen.getByTestId('roleadmin-retry'))
    expect(await screen.findByRole('table', { name: 'ロールの一覧' })).toBeInTheDocument()
    await waitFor(() => expect(screen.getByTestId('roleadmin-list-heading')).toHaveFocus())
  })

  it('pages forward, drops a second press while reloading, and moves focus when the button disappears', async () => {
    const user = userEvent.setup()
    const api = fakeRoleAdminApi()
    const second = deferred<RolePage>()
    api.listRoles
      .mockResolvedValueOnce(rolePageOf(roleRowsOf(20), { total: 25 }))
      .mockReturnValueOnce(second.promise)
    await renderPage(api)
    await screen.findByRole('table', { name: 'ロールの一覧' })
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await user.click(screen.getByRole('button', { name: '次へ' }))
    expect(api.listRoles.mock.calls.map(([page]) => page)).toEqual([1, 2])
    await act(async () => {
      second.resolve(rolePageOf(roleRowsOf(5, 21), { page: 2, total: 25 }))
      await second.promise
    })
    await waitFor(() =>
      expect(screen.getByTestId('roleadmin-status')).toHaveTextContent('21〜25 件目 / 全 25 件'),
    )
    await waitFor(() => expect(screen.getByTestId('roleadmin-list-heading')).toHaveFocus())
  })

  it('reads the last page once when the requested page has become empty', async () => {
    const user = userEvent.setup()
    const api = fakeRoleAdminApi()
    api.listRoles
      .mockResolvedValueOnce(rolePageOf(roleRowsOf(20), { total: 25 }))
      .mockResolvedValueOnce(rolePageOf([], { page: 2, total: 20 }))
      .mockResolvedValueOnce(rolePageOf(roleRowsOf(20), { page: 1, total: 20 }))
    await renderPage(api)
    await screen.findByRole('table', { name: 'ロールの一覧' })
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await waitFor(() =>
      expect(screen.getByTestId('roleadmin-status')).toHaveTextContent('1〜20 件目 / 全 20 件'),
    )
    expect(api.listRoles.mock.calls.map(([page]) => page)).toEqual([1, 2, 1])
  })

  it('creates a role, closes the dialog, announces it and reloads the current page', async () => {
    const user = userEvent.setup()
    const api = listOf(rolePageOf([roleRowOf()]))
    api.createRole.mockResolvedValue({
      roleId: 9,
      name: '総務',
      createdAt: '2026-10-05T00:00:00Z',
      updatedAt: '2026-10-05T00:00:00Z',
    })
    await renderPage(api)
    await user.click(await screen.findByTestId('roleadmin-create'))
    await user.type(await screen.findByTestId('roleadmin-name-input'), '総務')
    await user.click(screen.getByTestId('roleadmin-name-submit'))
    expect(api.createRole).toHaveBeenCalledWith('総務')
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(await screen.findByTestId('roleadmin-notice')).toHaveTextContent(
      'ロール「総務」を作りました。',
    )
    await waitFor(() => expect(api.listRoles).toHaveBeenCalledTimes(2))
  })

  it('keeps the dialog and the input on a duplicate name or BUSY, and never shows the detail', async () => {
    const user = userEvent.setup()
    const api = listOf(rolePageOf([roleRowOf()]))
    api.createRole
      .mockRejectedValueOnce(apiError(409, 'ROLE_NAME_DUPLICATE'))
      .mockRejectedValueOnce(apiError(409, 'ROLE_BUSY'))
    await renderPage(api)
    await user.click(await screen.findByTestId('roleadmin-create'))
    const input = await screen.findByTestId('roleadmin-name-input')
    await user.type(input, '営業')
    await user.click(screen.getByTestId('roleadmin-name-submit'))
    expect(await screen.findByText('同じ名前のロールがあります。')).toBeInTheDocument()
    await user.click(screen.getByTestId('roleadmin-name-submit'))
    expect(await screen.findByTestId('roleadmin-name-alert')).toHaveTextContent(
      'ほかの操作と重なりました。',
    )
    expect(screen.getByTestId('roleadmin-name-input')).toHaveValue('営業')
    expect(document.body).not.toHaveTextContent(DETAIL_MARKER)
  })

  it('sends only once when submit is pressed twice while the request is in flight', async () => {
    const user = userEvent.setup()
    const api = listOf(rolePageOf([roleRowOf()]))
    const answer = deferred<void>()
    api.renameRole.mockReturnValue(answer.promise)
    await renderPage(api)
    const menu = await openMenu(user, '営業')
    await user.click(within(menu).getByRole('menuitem', { name: '名前を変える' }))
    const input = await screen.findByTestId('roleadmin-name-input')
    await user.clear(input)
    await user.type(input, '営業部')
    await user.click(screen.getByTestId('roleadmin-name-submit'))
    await user.click(screen.getByTestId('roleadmin-name-submit'))
    expect(api.renameRole).toHaveBeenCalledTimes(1)
    expect(api.renameRole).toHaveBeenCalledWith(1, '営業部')
    await act(async () => {
      answer.resolve()
      await answer.promise
    })
    expect(await screen.findByTestId('roleadmin-notice')).toHaveTextContent(
      'ロールの名前を「営業部」に変えました。',
    )
    await waitFor(() => expect(screen.getByRole('button', { name: '営業の操作' })).toHaveFocus())
  })

  it('shows "no change" under the field, and closes and reloads on ROLE_NOT_FOUND', async () => {
    const user = userEvent.setup()
    const api = listOf(rolePageOf([roleRowOf()]))
    api.renameRole
      .mockRejectedValueOnce(apiError(409, 'ROLE_NO_CHANGE'))
      .mockRejectedValueOnce(apiError(404, 'ROLE_NOT_FOUND'))
    await renderPage(api)
    let menu = await openMenu(user, '営業')
    await user.click(within(menu).getByRole('menuitem', { name: '名前を変える' }))
    await user.click(await screen.findByTestId('roleadmin-name-submit'))
    expect(await screen.findByText('今と同じ名前です。')).toBeInTheDocument()
    await user.click(screen.getByTestId('roleadmin-name-submit'))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(await screen.findByTestId('roleadmin-notice')).toHaveTextContent(
      'このロールはありません。',
    )
    await waitFor(() => expect(api.listRoles).toHaveBeenCalledTimes(2))
    menu = await openMenu(user, '営業')
    expect(menu).toBeInTheDocument()
  })

  it('sends nothing when the delete is cancelled, and deletes after confirmation', async () => {
    const user = userEvent.setup()
    const api = listOf(rolePageOf([roleRowOf()]))
    api.deleteRole.mockResolvedValue()
    await renderPage(api)
    let menu = await openMenu(user, '営業')
    await user.click(within(menu).getByRole('menuitem', { name: '削除' }))
    await user.click(await screen.findByTestId('roleadmin-delete-cancel'))
    expect(api.deleteRole).not.toHaveBeenCalled()
    await waitFor(() => expect(screen.getByRole('button', { name: '営業の操作' })).toHaveFocus())
    menu = await openMenu(user, '営業')
    await user.click(within(menu).getByRole('menuitem', { name: '削除' }))
    await user.click(await screen.findByTestId('roleadmin-delete-submit'))
    expect(api.deleteRole).toHaveBeenCalledWith(1)
    expect(await screen.findByTestId('roleadmin-notice')).toHaveTextContent(
      'ロール「営業」を削除しました。',
    )
    await waitFor(() => expect(screen.getByTestId('roleadmin-list-heading')).toHaveFocus())
  })

  it('explains ROLE_IN_USE with the counts and a link to the assignments, or without counts', async () => {
    const user = userEvent.setup()
    const api = listOf(rolePageOf([roleRowOf({ roleId: 5 })]))
    api.deleteRole
      .mockRejectedValueOnce(apiError(409, 'ROLE_IN_USE', { assignedUsers: 2, assignedGroups: 1 }))
      .mockRejectedValueOnce(apiError(409, 'ROLE_IN_USE', { assignedUsers: '2' }))
    await renderPage(api)
    for (const expected of [
      'ロール「営業」は利用者 2 人とグループ 1 つに割り当てられているため削除できません。先に割り当てを外してください。',
      'ロール「営業」は割り当てられているため削除できません。先に割り当てを外してください。',
    ]) {
      const menu = await openMenu(user, '営業')
      await user.click(within(menu).getByRole('menuitem', { name: '削除' }))
      await user.click(await screen.findByTestId('roleadmin-delete-submit'))
      await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
      expect(await screen.findByTestId('roleadmin-notice')).toHaveTextContent(expected)
      expect(screen.getByTestId('roleadmin-inuse-link')).toHaveAttribute(
        'href',
        '/admin/roles/5/assignments',
      )
    }
    expect(api.listRoles).toHaveBeenCalledTimes(1)
    expect(document.body).not.toHaveTextContent(DETAIL_MARKER)
  })

  it('keeps the delete confirmation open with the busy message on ROLE_BUSY', async () => {
    const user = userEvent.setup()
    const api = listOf(rolePageOf([roleRowOf()]))
    api.deleteRole.mockRejectedValueOnce(apiError(409, 'ROLE_BUSY'))
    await renderPage(api)
    const menu = await openMenu(user, '営業')
    await user.click(within(menu).getByRole('menuitem', { name: '削除' }))
    await user.click(await screen.findByTestId('roleadmin-delete-submit'))
    expect(await screen.findByTestId('roleadmin-delete-alert')).toHaveTextContent(
      'ほかの操作と重なりました。少し待ってからもう一度お試しください。',
    )
    expect(screen.getByRole('alertdialog')).toBeInTheDocument()
  })

  it('draws only the latest answer when two loads answer in reverse order', async () => {
    // StrictMode（Testing Library の reactStrictMode で根を包む）は開いたときの効果を2回走らせるため、一覧の読み込みが
    // 2つ重なる。
    configure({ reactStrictMode: true })
    const api = fakeRoleAdminApi()
    const first = deferred<RolePage>()
    const second = deferred<RolePage>()
    api.listRoles.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    await renderPage(api)
    // 開いたときの効果で読み込みが始まるため、同期で数えずに待つ（team.md の「描画の後に反映される値は waitFor」）。
    await waitFor(() => expect(api.listRoles).toHaveBeenCalledTimes(2))
    await act(async () => {
      second.resolve(rolePageOf([roleRowOf({ roleId: 2, name: '新しい答え' })]))
      await second.promise
    })
    expect(await screen.findByText('新しい答え')).toBeInTheDocument()
    await act(async () => {
      first.resolve(rolePageOf([roleRowOf({ roleId: 1, name: '古い答え' })]))
      await first.promise
    })
    expect(screen.queryByText('古い答え')).toBeNull()
    expect(screen.getByText('新しい答え')).toBeInTheDocument()
  })

  it('ignores an answer after leaving the screen and never reloads on its own', async () => {
    const api = fakeRoleAdminApi()
    const answer = deferred<RolePage>()
    api.listRoles.mockReturnValueOnce(answer.promise)
    const errors = vi.spyOn(console, 'error')
    const view = await renderPage(api)
    view.unmount()
    await act(async () => {
      answer.resolve(rolePageOf([roleRowOf()]))
      await answer.promise
    })
    expect(errors).not.toHaveBeenCalled()

    const again = listOf(rolePageOf([roleRowOf()]))
    await renderPage(again)
    await screen.findByRole('table', { name: 'ロールの一覧' })
    vi.useFakeTimers()
    act(() => {
      vi.advanceTimersByTime(10 * 60 * 1000)
    })
    expect(again.listRoles).toHaveBeenCalledTimes(1)
  })

  it('has no accessibility violations in the loading, failed and populated states', async () => {
    const loading = fakeRoleAdminApi({ listRoles: () => new Promise<RolePage>(() => {}) })
    const loadingView = await renderPage(loading)
    expect(await axe(loadingView.container)).toHaveNoViolations()
    loadingView.unmount()

    const failedView = await renderPage(fakeRoleAdminApi())
    await screen.findByTestId('roleadmin-load-error')
    expect(await axe(failedView.container)).toHaveNoViolations()
    failedView.unmount()

    const shownView = await renderPage(
      listOf(rolePageOf([roleRowOf({ name: LONG_ROLE_NAME }), ...sampleRoleRows().slice(1)])),
    )
    await screen.findByRole('table', { name: 'ロールの一覧' })
    expect(await axe(shownView.container)).toHaveNoViolations()
  })
})
