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
// 英語の表示と漏えいの横断のテスト（Intent 261004-role-menu の U6 の D28・D3、NFR4.3・NFR4.6・NFR1.6）。
// - en で S3・S4・S5 の文言がすべて英語で、make-you-chic-ui の部品の日本語の既定（Table の「前へ」・Modal の「閉じる」など）が
//   出ない。名前（ロール・スキーマ・氏名）は訳さない。
// - 操作の後に console が呼ばれず、ブラウザの保存に書かれず、window.location に検索の文字が入らない。
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { AssignmentPanel } from './AssignmentPanel'
import { PermissionPanel } from './PermissionPanel'
import { RoleListPage } from './RoleListPage'
import {
  apiError,
  assignedUserOf,
  assignmentsOf,
  columnNodeOf,
  fakeRoleAdminApi,
  nodeOf,
  rolePageOf,
  roleRowOf,
  roleRowsOf,
  tableNodeOf,
} from './testing/fixtures'
import { renderRoleAdmin, renderRoleAdminDataRouter } from './testing/renderRoleAdmin'

const EN = ['en-US']
const JAPANESE = /[぀-ヿ㐀-鿿＀-￯]/u

/** 名前（訳さない値）を除いた画面の文字に日本語が無いこと */
function expectEnglishOnly(names: readonly string[]): void {
  let text = document.body.textContent ?? ''
  for (const name of names) {
    text = text.split(name).join('')
  }
  expect(text).not.toMatch(JAPANESE)
}

describe('roleadmin in English and without leaks', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('shows the role list, its pager, menu and dialogs in English', async () => {
    const user = userEvent.setup()
    const api = fakeRoleAdminApi()
    api.listRoles.mockResolvedValue(
      rolePageOf(
        [
          roleRowOf({ name: '営業' }),
          ...roleRowsOf(19, 2).map((row) => ({ ...row, name: `role-${row.roleId}` })),
        ],
        {
          total: 25,
        },
      ),
    )
    api.deleteRole.mockRejectedValue(
      apiError(409, 'ROLE_IN_USE', { assignedUsers: 2, assignedGroups: 1 }),
    )
    renderRoleAdmin(<RoleListPage api={api} />, { languages: EN })
    expect(await screen.findByRole('heading', { level: 1, name: 'Roles' })).toBeInTheDocument()
    expect(await screen.findByRole('button', { name: 'Next' })).toBeInTheDocument()
    expect(screen.queryByText('前へ')).toBeNull()
    await user.click(screen.getByRole('button', { name: 'Actions: 営業' }))
    const menu = await screen.findByRole('menu')
    expectEnglishOnly(['営業'])
    await user.click(within(menu).getByRole('menuitem', { name: 'Delete' }))
    expect(await screen.findByRole('alertdialog')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Close' })).toBeInTheDocument()
    expectEnglishOnly(['営業'])
    await user.click(screen.getByTestId('roleadmin-delete-submit'))
    expect(await screen.findByTestId('roleadmin-notice')).toHaveTextContent('2 users and 1 groups')
    expectEnglishOnly(['営業'])
    await user.click(screen.getByTestId('roleadmin-create'))
    await user.click(await screen.findByTestId('roleadmin-name-submit'))
    expect(await screen.findByRole('alert')).toBeInTheDocument()
    expectEnglishOnly(['営業'])
  })

  it('shows the permission settings in English, keeping the target names', async () => {
    const user = userEvent.setup()
    const api = fakeRoleAdminApi()
    api.listSchemas.mockResolvedValue([
      nodeOf({ inheritedFrom: 'EXPLICIT', explicit: { main: 'READ', create: true, delete: null } }),
    ])
    api.listTables.mockResolvedValue([tableNodeOf({ inheritedFrom: 'SCHEMA' })])
    api.listColumns.mockResolvedValue([
      columnNodeOf({ inheritedFrom: 'TABLE', inCurrentDsl: false }),
    ])
    renderRoleAdminDataRouter(
      [
        {
          path: '/admin/roles/:roleId',
          element: <PermissionPanel roleId={7} api={api} onMissing={vi.fn()} />,
        },
      ],
      { route: '/admin/roles/7', languages: EN },
    )
    expect(
      await screen.findByRole('heading', { name: 'Schema settings: 販売DB' }),
    ).toBeInTheDocument()
    await user.click(await screen.findByRole('button', { name: '受注明細' }))
    await screen.findByTestId('roleadmin-column-table')
    await user.selectOptions(
      screen.getByRole('combobox', { name: 'Main permission of 受注明細' }),
      'FULL',
    )
    expectEnglishOnly(['販売DB', '受注明細', '受注番号'])
  })

  it('shows the assignments and the candidate dialog in English', async () => {
    const user = userEvent.setup()
    const api = fakeRoleAdminApi()
    api.getAssignments.mockResolvedValue(
      assignmentsOf({
        users: [
          assignedUserOf({
            sources: [{ kind: 'DIRECT' }, { kind: 'GROUP', groupId: 3, name: '営業部' }],
          }),
        ],
        groups: [{ groupId: 3, name: '営業部' }],
      }),
    )
    api.searchUsers.mockResolvedValue([])
    renderRoleAdmin(<AssignmentPanel roleId={7} roleName="営業" api={api} onMissing={vi.fn()} />, {
      languages: EN,
    })
    await screen.findByTestId('roleadmin-assigned-user-21')
    await user.click(screen.getByTestId('roleadmin-add-user'))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByTestId('roleadmin-candidate-input'), '田中{Enter}')
    await waitFor(() =>
      expect(within(dialog).getByTestId('roleadmin-candidate-status')).toHaveTextContent(
        'Nothing was found.',
      ),
    )
    expectEnglishOnly(['田中 一郎', '営業部', '田中'])
  })

  it('writes nothing to the console or browser storage and keeps search text out of the URL', async () => {
    const user = userEvent.setup()
    const consoleSpies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((name) =>
      vi.spyOn(console, name),
    )
    const api = fakeRoleAdminApi()
    api.getAssignments.mockResolvedValue(assignmentsOf())
    api.searchUsers.mockResolvedValue([
      { userId: 30, displayName: '検索 太郎', email: 'kensaku.taro@example.com', suspended: false },
    ])
    api.assignUser.mockRejectedValue(apiError(409, 'ROLE_BUSY'))
    renderRoleAdmin(<AssignmentPanel roleId={7} roleName="営業" api={api} onMissing={vi.fn()} />)
    await screen.findByTestId('roleadmin-assign')
    const setItem = vi.spyOn(Storage.prototype, 'setItem')
    await user.click(screen.getByTestId('roleadmin-add-user'))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByTestId('roleadmin-candidate-input'), 'kensaku{Enter}')
    const row = await within(dialog).findByTestId('roleadmin-candidate-row-30')
    await user.click(within(row).getByRole('button', { name: '検索 太郎を足す' }))
    await within(dialog).findByText(
      'ほかの操作と重なりました。少し待ってからもう一度お試しください。',
    )
    for (const spy of consoleSpies) {
      expect(spy).not.toHaveBeenCalled()
    }
    expect(setItem).not.toHaveBeenCalled()
    expect(window.location.href).not.toContain('kensaku')
    expect(screen.getByTestId('location')).not.toHaveTextContent('kensaku')
  })
})
