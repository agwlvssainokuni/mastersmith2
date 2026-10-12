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
// 英語の表示と漏えいの横断のテスト（Intent 261004-role-menu の U6 の D28・D3、NFR4.3・NFR4.6・NFR1.6）。en で S6 の
// 一覧・詳細・候補の文言がすべて英語で、部品の日本語の既定が出ない（名前と氏名は訳さない）。操作の後に console が
// 呼ばれず、ブラウザの保存に書かれず、URL に検索の文字が入らない。
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { GroupDetailPage } from './GroupDetailPage'
import { GroupListPage } from './GroupListPage'
import {
  apiError,
  detailOf,
  fakeGroupAdminApi,
  groupPageOf,
  groupRowOf,
  groupRowsOf,
  memberOf,
} from './testing/fixtures'
import { renderGroupAdmin } from './testing/renderGroupAdmin'

const EN = ['en-US']
const JAPANESE = /[぀-ヿ㐀-鿿＀-￯]/u

function expectEnglishOnly(names: readonly string[]): void {
  let text = document.body.textContent ?? ''
  for (const name of names) {
    text = text.split(name).join('')
  }
  expect(text).not.toMatch(JAPANESE)
}

describe('groupadmin in English and without leaks', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('shows the group list, pager, menu and dialogs in English', async () => {
    const user = userEvent.setup()
    const api = fakeGroupAdminApi()
    api.listGroups.mockResolvedValue(
      groupPageOf(
        [
          groupRowOf(),
          ...groupRowsOf(19, 2).map((row) => ({ ...row, name: `group-${row.groupId}` })),
        ],
        { total: 25 },
      ),
    )
    api.deleteGroup.mockRejectedValue(
      apiError(409, 'GROUP_IN_USE', { members: 3, assignedRoles: 1 }),
    )
    renderGroupAdmin(<GroupListPage api={api} />, { languages: EN })
    expect(await screen.findByRole('button', { name: 'Next' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Actions for 営業部' }))
    await user.click(
      within(await screen.findByRole('menu')).getByRole('menuitem', { name: 'Delete' }),
    )
    expectEnglishOnly(['営業部'])
    await user.click(await screen.findByTestId('groupadmin-delete-submit'))
    expect(await screen.findByTestId('groupadmin-notice')).toHaveTextContent(
      '3 members and 1 roles',
    )
    await user.click(screen.getByTestId('groupadmin-create'))
    await user.click(await screen.findByTestId('groupadmin-name-submit'))
    expect(await screen.findByText('Enter a name.')).toBeInTheDocument()
    expectEnglishOnly(['営業部'])
  })

  it('shows the detail and the candidate dialog in English, and leaks nothing', async () => {
    const user = userEvent.setup()
    const consoleSpies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((name) =>
      vi.spyOn(console, name),
    )
    const api = fakeGroupAdminApi()
    api.getGroup.mockResolvedValue(detailOf({ members: [memberOf({ suspended: true })] }))
    api.getGroupRoles.mockResolvedValue([{ roleId: 5, name: '営業' }])
    api.searchUsers.mockResolvedValue([])
    renderGroupAdmin(
      <Routes>
        <Route path="/admin/groups/:groupId" element={<GroupDetailPage api={api} />} />
      </Routes>,
      { route: '/admin/groups/3', languages: EN },
    )
    expect(
      await screen.findByRole('heading', { level: 1, name: 'Group: 営業部' }),
    ).toBeInTheDocument()
    const setItem = vi.spyOn(Storage.prototype, 'setItem')
    await user.click(screen.getByTestId('groupadmin-add-member'))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByTestId('groupadmin-candidate-input'), 'kensaku{Enter}')
    await waitFor(() =>
      expect(within(dialog).getByTestId('groupadmin-candidate-status')).toHaveTextContent(
        'Nothing was found.',
      ),
    )
    expectEnglishOnly(['営業部', '営業', '田中 一郎'])
    for (const spy of consoleSpies) {
      expect(spy).not.toHaveBeenCalled()
    }
    expect(setItem).not.toHaveBeenCalled()
    expect(window.location.href).not.toContain('kensaku')
    expect(screen.getByTestId('location')).not.toHaveTextContent('kensaku')
  })
})
