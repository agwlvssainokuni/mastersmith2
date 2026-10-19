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
// S6 グループの一覧のテスト（useGroupList を通す。Intent 261004-role-menu の U6、functional-spec.md の 3.1・4.1・4.2・5節・
// 8.1、D5〜D9・D29・D30、NFR2.10・NFR2.11）。API の関数を差し替え、答えを返さない約束で止めて途中の表示を見る。
import { act, configure, screen, waitFor, within } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import type { GroupPage } from './api/types'
import { GroupListPage } from './GroupListPage'
import {
  apiError,
  deferred,
  DETAIL_MARKER,
  fakeGroupAdminApi,
  IMG_NAME,
  LONG_GROUP_NAME,
  NETWORK_ERROR,
  groupRowOf,
  groupPageOf,
  groupRowsOf,
  sampleGroupRows,
  SCRIPT_NAME,
  type FakeGroupAdminApi,
} from './testing/fixtures'
import { renderGroupAdmin } from './testing/renderGroupAdmin'

async function renderPage(api: FakeGroupAdminApi) {
  const view = renderGroupAdmin(<GroupListPage api={api} />)
  await screen.findByTestId('groupadmin-list-page')
  return view
}

function listOf(page: GroupPage) {
  return fakeGroupAdminApi({ listGroups: () => Promise.resolve(page) })
}

async function openMenu(user: UserEvent, name: string) {
  await user.click(await screen.findByRole('button', { name: `操作: ${name}` }))
  return screen.findByRole('menu')
}

describe('GroupListPage', () => {
  afterEach(() => {
    configure({ reactStrictMode: false })
    resetDisplayTestState()
    vi.useRealTimers()
    vi.restoreAllMocks()
  })

  it('names the row actions with the visible label without extra list requests', async () => {
    const api = listOf(groupPageOf(groupRowsOf(3)))
    await renderPage(api)
    await waitFor(() =>
      expect(screen.getByRole('button', { name: '操作: グループ2' })).toBeInTheDocument(),
    )
    expect(screen.getAllByRole('button', { name: /^操作: / })).toHaveLength(3)
    expect(api.listGroups).toHaveBeenCalledTimes(1)
  })

  it('shows the rows in server order with counts, and names that look like markup as text', async () => {
    const api = listOf(groupPageOf(sampleGroupRows()))
    await renderPage(api)
    const table = await screen.findByRole('table', { name: 'グループの一覧' })
    const rows = within(table).getAllByRole('row').slice(1)
    expect(rows).toHaveLength(7)
    expect(rows[0]).toHaveTextContent('営業部')
    expect(rows[0]).toHaveTextContent('2 人')
    expect(within(table).getByText(SCRIPT_NAME)).toBeInTheDocument()
    expect(within(table).getByText(IMG_NAME)).toBeInTheDocument()
    expect(document.querySelector('script')).toBeNull()
    expect(document.querySelector('img')).toBeNull()
    expect(screen.getByTestId('groupadmin-status')).toHaveTextContent('1〜7 件目 / 全 7 件')
    expect(api.listGroups).toHaveBeenCalledWith(1)
  })

  it('shows the empty guide with a create button when there are no roles', async () => {
    const user = userEvent.setup()
    await renderPage(listOf(groupPageOf([])))
    expect(await screen.findByTestId('groupadmin-empty')).toHaveTextContent(
      'グループがありません。',
    )
    await user.click(screen.getByTestId('groupadmin-empty-create'))
    expect(await screen.findByRole('dialog', { name: 'グループを作る' })).toBeInTheDocument()
  })

  it('shows the load failure and reloads with "retry", moving focus to the heading', async () => {
    const user = userEvent.setup()
    const api = fakeGroupAdminApi()
    api.listGroups
      .mockRejectedValueOnce(NETWORK_ERROR)
      .mockResolvedValueOnce(groupPageOf([groupRowOf()]))
    await renderPage(api)
    expect(await screen.findByTestId('groupadmin-load-error')).toHaveTextContent(
      '読み込めませんでした。',
    )
    await user.click(screen.getByTestId('groupadmin-retry'))
    expect(await screen.findByRole('table', { name: 'グループの一覧' })).toBeInTheDocument()
    await waitFor(() => expect(screen.getByTestId('groupadmin-list-heading')).toHaveFocus())
  })

  it('pages forward, drops a second press while reloading, and moves focus when the button disappears', async () => {
    const user = userEvent.setup()
    const api = fakeGroupAdminApi()
    const second = deferred<GroupPage>()
    api.listGroups
      .mockResolvedValueOnce(groupPageOf(groupRowsOf(20), { total: 25 }))
      .mockReturnValueOnce(second.promise)
    await renderPage(api)
    await screen.findByRole('table', { name: 'グループの一覧' })
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await user.click(screen.getByRole('button', { name: '次へ' }))
    expect(api.listGroups.mock.calls.map(([page]) => page)).toEqual([1, 2])
    await act(async () => {
      second.resolve(groupPageOf(groupRowsOf(5, 21), { page: 2, total: 25 }))
      await second.promise
    })
    await waitFor(() =>
      expect(screen.getByTestId('groupadmin-status')).toHaveTextContent('21〜25 件目 / 全 25 件'),
    )
    await waitFor(() => expect(screen.getByTestId('groupadmin-list-heading')).toHaveFocus())
  })

  it('reads the last page once when the requested page has become empty', async () => {
    const user = userEvent.setup()
    const api = fakeGroupAdminApi()
    api.listGroups
      .mockResolvedValueOnce(groupPageOf(groupRowsOf(20), { total: 25 }))
      .mockResolvedValueOnce(groupPageOf([], { page: 2, total: 20 }))
      .mockResolvedValueOnce(groupPageOf(groupRowsOf(20), { page: 1, total: 20 }))
    await renderPage(api)
    await screen.findByRole('table', { name: 'グループの一覧' })
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await waitFor(() =>
      expect(screen.getByTestId('groupadmin-status')).toHaveTextContent('1〜20 件目 / 全 20 件'),
    )
    expect(api.listGroups.mock.calls.map(([page]) => page)).toEqual([1, 2, 1])
  })

  it('creates a role, closes the dialog, announces it and reloads the current page', async () => {
    const user = userEvent.setup()
    const api = listOf(groupPageOf([groupRowOf()]))
    api.createGroup.mockResolvedValue({
      groupId: 9,
      name: '総務',
      createdAt: '2026-10-05T00:00:00Z',
      updatedAt: '2026-10-05T00:00:00Z',
    })
    await renderPage(api)
    await user.click(await screen.findByTestId('groupadmin-create'))
    await user.type(await screen.findByTestId('groupadmin-name-input'), '総務')
    await user.click(screen.getByTestId('groupadmin-name-submit'))
    expect(api.createGroup).toHaveBeenCalledWith('総務')
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(await screen.findByTestId('groupadmin-notice')).toHaveTextContent(
      'グループ「総務」を作りました。',
    )
    await waitFor(() => expect(api.listGroups).toHaveBeenCalledTimes(2))
  })

  it('keeps the dialog and the input on a duplicate name or BUSY, and never shows the detail', async () => {
    const user = userEvent.setup()
    const api = listOf(groupPageOf([groupRowOf()]))
    api.createGroup
      .mockRejectedValueOnce(apiError(409, 'GROUP_NAME_DUPLICATE'))
      .mockRejectedValueOnce(apiError(409, 'GROUP_BUSY'))
    await renderPage(api)
    await user.click(await screen.findByTestId('groupadmin-create'))
    const input = await screen.findByTestId('groupadmin-name-input')
    await user.type(input, '営業部')
    await user.click(screen.getByTestId('groupadmin-name-submit'))
    expect(await screen.findByText('同じ名前のグループがあります。')).toBeInTheDocument()
    await user.click(screen.getByTestId('groupadmin-name-submit'))
    expect(await screen.findByTestId('groupadmin-name-alert')).toHaveTextContent(
      'ほかの操作と重なりました。',
    )
    expect(screen.getByTestId('groupadmin-name-input')).toHaveValue('営業部')
    expect(document.body).not.toHaveTextContent(DETAIL_MARKER)
  })

  it('sends only once when submit is pressed twice while the request is in flight', async () => {
    const user = userEvent.setup()
    const api = listOf(groupPageOf([groupRowOf()]))
    const answer = deferred<void>()
    api.renameGroup.mockReturnValue(answer.promise)
    await renderPage(api)
    const menu = await openMenu(user, '営業部')
    await user.click(within(menu).getByRole('menuitem', { name: '名前を変える' }))
    const input = await screen.findByTestId('groupadmin-name-input')
    await user.clear(input)
    await user.type(input, '営業部門')
    await user.click(screen.getByTestId('groupadmin-name-submit'))
    await user.click(screen.getByTestId('groupadmin-name-submit'))
    expect(api.renameGroup).toHaveBeenCalledTimes(1)
    expect(api.renameGroup).toHaveBeenCalledWith(1, '営業部門')
    await act(async () => {
      answer.resolve()
      await answer.promise
    })
    expect(await screen.findByTestId('groupadmin-notice')).toHaveTextContent(
      'グループの名前を「営業部門」に変えました。',
    )
    await waitFor(() => expect(screen.getByRole('button', { name: '操作: 営業部' })).toHaveFocus())
  })

  it('shows "no change" under the field, and closes and reloads on GROUP_NOT_FOUND', async () => {
    const user = userEvent.setup()
    const api = listOf(groupPageOf([groupRowOf()]))
    api.renameGroup
      .mockRejectedValueOnce(apiError(409, 'GROUP_NO_CHANGE'))
      .mockRejectedValueOnce(apiError(404, 'GROUP_NOT_FOUND'))
    await renderPage(api)
    let menu = await openMenu(user, '営業部')
    await user.click(within(menu).getByRole('menuitem', { name: '名前を変える' }))
    await user.click(await screen.findByTestId('groupadmin-name-submit'))
    expect(await screen.findByText('今と同じ名前です。')).toBeInTheDocument()
    await user.click(screen.getByTestId('groupadmin-name-submit'))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(await screen.findByTestId('groupadmin-notice')).toHaveTextContent(
      'このグループはありません。',
    )
    await waitFor(() => expect(api.listGroups).toHaveBeenCalledTimes(2))
    menu = await openMenu(user, '営業部')
    expect(menu).toBeInTheDocument()
  })

  it('sends nothing when the delete is cancelled, and deletes after confirmation', async () => {
    const user = userEvent.setup()
    const api = listOf(groupPageOf([groupRowOf()]))
    api.deleteGroup.mockResolvedValue()
    await renderPage(api)
    let menu = await openMenu(user, '営業部')
    await user.click(within(menu).getByRole('menuitem', { name: '削除' }))
    await user.click(await screen.findByTestId('groupadmin-delete-cancel'))
    expect(api.deleteGroup).not.toHaveBeenCalled()
    await waitFor(() => expect(screen.getByRole('button', { name: '操作: 営業部' })).toHaveFocus())
    menu = await openMenu(user, '営業部')
    await user.click(within(menu).getByRole('menuitem', { name: '削除' }))
    await user.click(await screen.findByTestId('groupadmin-delete-submit'))
    expect(api.deleteGroup).toHaveBeenCalledWith(1)
    expect(await screen.findByTestId('groupadmin-notice')).toHaveTextContent(
      'グループ「営業部」を削除しました。',
    )
    await waitFor(() => expect(screen.getByTestId('groupadmin-list-heading')).toHaveFocus())
  })

  it('explains GROUP_IN_USE with the counts, or without counts when they cannot be read', async () => {
    const user = userEvent.setup()
    const api = listOf(groupPageOf([groupRowOf({ groupId: 5 })]))
    api.deleteGroup
      .mockRejectedValueOnce(apiError(409, 'GROUP_IN_USE', { members: 3, assignedRoles: 1 }))
      .mockRejectedValueOnce(apiError(409, 'GROUP_IN_USE', { members: '3' }))
    await renderPage(api)
    for (const expected of [
      'グループ「営業部」はメンバー 3 人とロール 1 つが残っているため削除できません。',
      'グループ「営業部」はメンバーかロールが残っているため削除できません。',
    ]) {
      const menu = await openMenu(user, '営業部')
      await user.click(within(menu).getByRole('menuitem', { name: '削除' }))
      await user.click(await screen.findByTestId('groupadmin-delete-submit'))
      await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
      expect(await screen.findByTestId('groupadmin-notice')).toHaveTextContent(expected)
      expect(screen.queryByTestId('groupadmin-inuse-link')).toBeNull()
    }
    expect(api.listGroups).toHaveBeenCalledTimes(1)
    expect(document.body).not.toHaveTextContent(DETAIL_MARKER)
  })

  it('keeps the delete confirmation open with the busy message on GROUP_BUSY', async () => {
    const user = userEvent.setup()
    const api = listOf(groupPageOf([groupRowOf()]))
    api.deleteGroup.mockRejectedValueOnce(apiError(409, 'GROUP_BUSY'))
    await renderPage(api)
    const menu = await openMenu(user, '営業部')
    await user.click(within(menu).getByRole('menuitem', { name: '削除' }))
    await user.click(await screen.findByTestId('groupadmin-delete-submit'))
    expect(await screen.findByTestId('groupadmin-delete-alert')).toHaveTextContent(
      'ほかの操作と重なりました。少し待ってからもう一度お試しください。',
    )
    expect(screen.getByRole('alertdialog')).toBeInTheDocument()
  })

  it('draws only the latest answer when two loads answer in reverse order', async () => {
    // StrictMode（Testing Library の reactStrictMode で根を包む）は開いたときの効果を2回走らせるため、一覧の読み込みが
    // 2つ重なる。
    configure({ reactStrictMode: true })
    const api = fakeGroupAdminApi()
    const first = deferred<GroupPage>()
    const second = deferred<GroupPage>()
    api.listGroups.mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise)
    await renderPage(api)
    // 開いたときの効果で読み込みが始まるため、同期で数えずに待つ（team.md の「描画の後に反映される値は waitFor」）。
    await waitFor(() => expect(api.listGroups).toHaveBeenCalledTimes(2))
    await act(async () => {
      second.resolve(groupPageOf([groupRowOf({ groupId: 2, name: '新しい答え' })]))
      await second.promise
    })
    expect(await screen.findByText('新しい答え')).toBeInTheDocument()
    await act(async () => {
      first.resolve(groupPageOf([groupRowOf({ groupId: 1, name: '古い答え' })]))
      await first.promise
    })
    expect(screen.queryByText('古い答え')).toBeNull()
    expect(screen.getByText('新しい答え')).toBeInTheDocument()
  })

  it('ignores an answer after leaving the screen and never reloads on its own', async () => {
    const api = fakeGroupAdminApi()
    const answer = deferred<GroupPage>()
    api.listGroups.mockReturnValueOnce(answer.promise)
    const errors = vi.spyOn(console, 'error')
    const view = await renderPage(api)
    view.unmount()
    await act(async () => {
      answer.resolve(groupPageOf([groupRowOf()]))
      await answer.promise
    })
    expect(errors).not.toHaveBeenCalled()

    const again = listOf(groupPageOf([groupRowOf()]))
    await renderPage(again)
    await screen.findByRole('table', { name: 'グループの一覧' })
    vi.useFakeTimers()
    act(() => {
      vi.advanceTimersByTime(10 * 60 * 1000)
    })
    expect(again.listGroups).toHaveBeenCalledTimes(1)
  })

  it('has no accessibility violations in the loading, failed and populated states', async () => {
    const loading = fakeGroupAdminApi({ listGroups: () => new Promise<GroupPage>(() => {}) })
    const loadingView = await renderPage(loading)
    expect(await axe(loadingView.container)).toHaveNoViolations()
    loadingView.unmount()

    const failedView = await renderPage(fakeGroupAdminApi())
    await screen.findByTestId('groupadmin-load-error')
    expect(await axe(failedView.container)).toHaveNoViolations()
    failedView.unmount()

    const shownView = await renderPage(
      listOf(groupPageOf([groupRowOf({ name: LONG_GROUP_NAME }), ...sampleGroupRows().slice(1)])),
    )
    await screen.findByRole('table', { name: 'グループの一覧' })
    expect(await axe(shownView.container)).toHaveNoViolations()
  })
})
