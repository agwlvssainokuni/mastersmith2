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
// S5 割り当てのテスト（useRoleAssignments を通す。Intent 261004-role-menu の U6、functional-spec.md の 3.3・4.4・8.1、
// D16〜D19、AC2.2.2・AC2.2.7〜AC2.2.9・AC2.2.15・AC2.2.16、計画 2.3 の A11）。API は差し替える。
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { AssignmentPanel } from './AssignmentPanel'
import {
  apiError,
  assignedUserOf,
  assignmentsOf,
  DETAIL_MARKER,
  fakeRoleAdminApi,
  type FakeRoleAdminApi,
} from './testing/fixtures'
import { renderRoleAdmin } from './testing/renderRoleAdmin'

const MIXED = assignedUserOf({
  sources: [{ kind: 'DIRECT' }, { kind: 'GROUP', groupId: 3, name: '営業部' }],
})
const VIA_GROUP = assignedUserOf({
  userId: 22,
  displayName: '佐藤 花子',
  email: 'sato.hanako@example.com',
  suspended: true,
  sources: [{ kind: 'GROUP', groupId: 3, name: '営業部' }],
})

function panelApi(): FakeRoleAdminApi {
  const api = fakeRoleAdminApi()
  api.getAssignments.mockResolvedValue(
    assignmentsOf({ users: [MIXED, VIA_GROUP], groups: [{ groupId: 3, name: '営業部' }] }),
  )
  return api
}

async function renderPanel(api: FakeRoleAdminApi, onMissing = vi.fn()) {
  const view = renderRoleAdmin(
    <AssignmentPanel roleId={7} roleName="営業" api={api} onMissing={onMissing} />,
    { route: '/admin/roles/7/assignments' },
  )
  await screen.findByTestId('roleadmin-assign')
  return { ...view, onMissing }
}

describe('AssignmentPanel', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('lists users with their sources in text and offers removal only for direct assignments', async () => {
    await renderPanel(panelApi())
    const mixed = screen.getByTestId('roleadmin-assigned-user-21')
    expect(mixed).toHaveTextContent('直接・グループ 営業部')
    expect(within(mixed).getByRole('button', { name: '田中 一郎を外す' })).toBeInTheDocument()
    const viaGroup = screen.getByTestId('roleadmin-assigned-user-22')
    expect(viaGroup).toHaveTextContent('グループ 営業部')
    expect(viaGroup).toHaveTextContent('（利用停止）')
    expect(within(viaGroup).queryByRole('button')).toBeNull()
    expect(screen.getByTestId('roleadmin-assigned-group-3')).toHaveTextContent('営業部')
  })

  it('shows the empty message when nothing is assigned, and the load failure with retry', async () => {
    const user = userEvent.setup()
    const api = fakeRoleAdminApi()
    api.getAssignments.mockRejectedValueOnce(apiError(500)).mockResolvedValueOnce(assignmentsOf())
    renderRoleAdmin(<AssignmentPanel roleId={7} roleName="営業" api={api} onMissing={vi.fn()} />)
    const error = await screen.findByTestId('roleadmin-assign-error')
    await user.click(within(error).getByRole('button', { name: 'もう一度' }))
    expect(await screen.findByTestId('roleadmin-assign-empty')).toHaveTextContent(
      '割り当てはありません。',
    )
  })

  it('removes a direct assignment kept through a group without asking, and reloads', async () => {
    const user = userEvent.setup()
    const api = panelApi()
    api.getUserRoles.mockResolvedValue([{ roleId: 7, name: '営業', sources: MIXED.sources }])
    api.unassignUser.mockResolvedValue()
    await renderPanel(api)
    await user.click(screen.getByRole('button', { name: '田中 一郎を外す' }))
    await waitFor(() => expect(api.unassignUser).toHaveBeenCalledWith(7, 21))
    expect(screen.queryByRole('alertdialog')).toBeNull()
    expect(await screen.findByTestId('roleadmin-assign-notice')).toHaveTextContent(
      '田中 一郎さんから営業を外しました。',
    )
    await waitFor(() => expect(api.getAssignments).toHaveBeenCalledTimes(2))
  })

  it('asks with the next working role when only a direct assignment gives the role', async () => {
    const user = userEvent.setup()
    const api = panelApi()
    api.getAssignments.mockResolvedValue(assignmentsOf({ users: [assignedUserOf()] }))
    api.getUserRoles.mockResolvedValue([
      { roleId: 9, name: '総務', sources: [{ kind: 'DIRECT' }] },
      { roleId: 7, name: '営業', sources: [{ kind: 'DIRECT' }] },
      { roleId: 8, name: '経理', sources: [{ kind: 'GROUP', groupId: 1, name: '経理部' }] },
    ])
    api.unassignUser.mockResolvedValue()
    await renderPanel(api)
    await user.click(screen.getByRole('button', { name: '田中 一郎を外す' }))
    const dialog = await screen.findByRole('alertdialog', { name: '割り当てを外す' })
    expect(dialog).toHaveTextContent(
      '田中 一郎さんの作業ロールがこのロールなら、外した後は 経理 に変わります。',
    )
    await user.click(within(dialog).getByRole('button', { name: 'やめる' }))
    expect(api.unassignUser).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: '田中 一郎を外す' }))
    await user.click(await screen.findByTestId('roleadmin-removal-submit'))
    await waitFor(() => expect(api.unassignUser).toHaveBeenCalledWith(7, 21))
  })

  it('asks without a next role when none remains, and with the conditional text when roles cannot be read', async () => {
    const user = userEvent.setup()
    const api = panelApi()
    api.getAssignments.mockResolvedValue(assignmentsOf({ users: [assignedUserOf()] }))
    api.getUserRoles
      .mockResolvedValueOnce([{ roleId: 7, name: '営業', sources: [{ kind: 'DIRECT' }] }])
      .mockRejectedValueOnce(apiError(500))
    await renderPanel(api)
    await user.click(screen.getByRole('button', { name: '田中 一郎を外す' }))
    expect(await screen.findByRole('alertdialog')).toHaveTextContent(
      '田中 一郎さんの作業ロールがこのロールなら、外した後は作業ロールが無くなります。',
    )
    await user.click(screen.getByTestId('roleadmin-removal-cancel'))
    await user.click(screen.getByRole('button', { name: '田中 一郎を外す' }))
    expect(await screen.findByRole('alertdialog')).toHaveTextContent(
      '外した後は別のロールに変わるか、作業ロールが無くなります。',
    )
  })

  it('treats ROLE_NO_CHANGE on removal (including a user who no longer exists) as already removed', async () => {
    const user = userEvent.setup()
    const api = panelApi()
    api.unassignGroup.mockRejectedValue(apiError(409, 'ROLE_NO_CHANGE'))
    await renderPanel(api)
    await user.click(screen.getByRole('button', { name: '営業部を外す' }))
    expect(await screen.findByTestId('roleadmin-assign-notice')).toHaveTextContent(
      'すでに外れています。',
    )
    await waitFor(() => expect(api.getAssignments).toHaveBeenCalledTimes(2))
    expect(document.body).not.toHaveTextContent(DETAIL_MARKER)
  })

  it('keeps the table on BUSY and reports a missing role on ROLE_NOT_FOUND', async () => {
    const user = userEvent.setup()
    const api = panelApi()
    api.unassignGroup
      .mockRejectedValueOnce(apiError(409, 'GROUP_BUSY'))
      .mockRejectedValueOnce(apiError(404, 'ROLE_NOT_FOUND'))
    const { onMissing } = await renderPanel(api)
    await user.click(screen.getByRole('button', { name: '営業部を外す' }))
    expect(await screen.findByTestId('roleadmin-assign-notice')).toHaveTextContent(
      'ほかの操作と重なりました。',
    )
    expect(api.getAssignments).toHaveBeenCalledTimes(1)
    await user.click(screen.getByRole('button', { name: '営業部を外す' }))
    await waitFor(() => expect(onMissing).toHaveBeenCalledTimes(1))
  })

  it('adds a user from the picker, marks taken candidates, keeps it open and reloads after closing', async () => {
    const user = userEvent.setup()
    const api = panelApi()
    api.searchUsers.mockResolvedValue([
      {
        userId: 21,
        displayName: '田中 一郎',
        email: 'tanaka.ichiro@example.com',
        suspended: false,
      },
      { userId: 22, displayName: '佐藤 花子', email: 'sato.hanako@example.com', suspended: true },
    ])
    api.assignUser.mockResolvedValue()
    await renderPanel(api)
    await user.click(screen.getByTestId('roleadmin-add-user'))
    const dialog = await screen.findByRole('dialog', { name: '利用者を足す' })
    await user.type(within(dialog).getByTestId('roleadmin-candidate-input'), '田{Enter}')
    expect(api.searchUsers).toHaveBeenCalledWith('田')
    const taken = await within(dialog).findByTestId('roleadmin-candidate-row-21')
    expect(taken).toHaveTextContent('割り当て済み')
    expect(within(taken).getByRole('button', { name: '田中 一郎を足す' })).toBeDisabled()
    const viaGroup = within(dialog).getByTestId('roleadmin-candidate-row-22')
    expect(viaGroup).toHaveTextContent('（利用停止）')
    await user.click(within(viaGroup).getByRole('button', { name: '佐藤 花子を足す' }))
    expect(api.assignUser).toHaveBeenCalledWith(7, 22)
    expect(await within(dialog).findByTestId('roleadmin-candidate-status')).toHaveTextContent(
      '佐藤 花子さんに割り当てました。',
    )
    expect(within(viaGroup).getByRole('button', { name: '佐藤 花子を足す' })).toBeDisabled()
    await user.click(within(dialog).getByTestId('roleadmin-candidate-close'))
    await waitFor(() => expect(api.getAssignments).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(screen.getByTestId('roleadmin-add-user')).toHaveFocus())
  })

  it('adds a group from the paged picker', async () => {
    const user = userEvent.setup()
    const api = panelApi()
    api.listGroups.mockResolvedValue({
      items: [
        { groupId: 3, name: '営業部' },
        { groupId: 4, name: '経理部' },
      ],
      page: 1,
      size: 20,
      total: 2,
    })
    api.assignGroup.mockResolvedValue()
    await renderPanel(api)
    await user.click(screen.getByTestId('roleadmin-add-group'))
    const dialog = await screen.findByRole('dialog', { name: 'グループを足す' })
    const row = await within(dialog).findByTestId('roleadmin-candidate-row-4')
    expect(within(dialog).getByTestId('roleadmin-candidate-row-3')).toHaveTextContent(
      '割り当て済み',
    )
    await user.click(within(row).getByRole('button', { name: '経理部を足す' }))
    expect(api.assignGroup).toHaveBeenCalledWith(7, 4)
    expect(await within(dialog).findByTestId('roleadmin-candidate-status')).toHaveTextContent(
      '経理部に割り当てました。',
    )
  })

  it('has no accessibility violations with users, groups and a long email address', async () => {
    const api = panelApi()
    api.getAssignments.mockResolvedValue(
      assignmentsOf({
        users: [
          assignedUserOf({ email: `${'very.long.mail.address'.repeat(4)}@example.com` }),
          VIA_GROUP,
        ],
        groups: [{ groupId: 3, name: '営業部' }],
      }),
    )
    const view = await renderPanel(api)
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
