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
// S6 グループの詳細のテスト（useGroupDetail を通す。Intent 261004-role-menu の U6、functional-spec.md の 3.4 W6.2〜W6.4・
// 4.4・8.1、D17・D20・D21・D32、AC2.1.8〜AC2.1.10）。API は差し替える。道の :groupId を読むため Routes の中で描く。
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { GroupDetailPage } from './GroupDetailPage'
import {
  apiError,
  DETAIL_MARKER,
  detailOf,
  fakeGroupAdminApi,
  LONG_GROUP_NAME,
  memberOf,
  type FakeGroupAdminApi,
} from './testing/fixtures'
import { renderGroupAdmin } from './testing/renderGroupAdmin'

function detailApi(): FakeGroupAdminApi {
  const api = fakeGroupAdminApi()
  api.getGroup.mockResolvedValue(
    detailOf({
      members: [
        memberOf(),
        memberOf({
          userId: 22,
          displayName: '佐藤 花子',
          email: 'sato.hanako@example.com',
          suspended: true,
        }),
      ],
    }),
  )
  api.getGroupRoles.mockResolvedValue([
    { roleId: 5, name: '営業' },
    { roleId: 6, name: '経理' },
  ])
  return api
}

function renderDetail(api: FakeGroupAdminApi, route = '/admin/groups/3') {
  return renderGroupAdmin(
    <Routes>
      <Route path="/admin/groups/:groupId" element={<GroupDetailPage api={api} />} />
    </Routes>,
    { route },
  )
}

describe('GroupDetailPage', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('shows the heading, members with the suspended mark, and role links to their assignments', async () => {
    const api = detailApi()
    renderDetail(api)
    expect(
      await screen.findByRole('heading', { level: 1, name: 'グループ: 営業部' }),
    ).toBeInTheDocument()
    expect(screen.getByTestId('groupadmin-back')).toHaveAttribute('href', '/admin/groups')
    expect(screen.getByTestId('groupadmin-member-22')).toHaveTextContent('（利用停止）')
    expect(screen.getByRole('link', { name: '経理' })).toHaveAttribute(
      'href',
      '/admin/roles/6/assignments',
    )
    expect(api.getGroup).toHaveBeenCalledWith(3)
    expect(api.getGroupRoles).toHaveBeenCalledWith(3)
  })

  it.each(['x', '0', '-3'])(
    'shows the missing message and reads nothing for the path id %s',
    async (id) => {
      const api = detailApi()
      renderDetail(api, `/admin/groups/${id}`)
      expect(await screen.findByTestId('groupadmin-detail-missing')).toHaveTextContent(
        'このグループはありません。',
      )
      expect(screen.getByRole('link', { name: 'グループの一覧へ' })).toHaveAttribute(
        'href',
        '/admin/groups',
      )
      expect(api.getGroup).not.toHaveBeenCalled()
    },
  )

  it('shows the missing message on GROUP_NOT_FOUND, and the load failure with retry', async () => {
    const user = userEvent.setup()
    const missing = detailApi()
    missing.getGroup.mockRejectedValue(apiError(404, 'GROUP_NOT_FOUND'))
    const view = renderDetail(missing)
    expect(await screen.findByTestId('groupadmin-detail-missing')).toBeInTheDocument()
    view.unmount()
    const failing = detailApi()
    failing.getGroup.mockRejectedValueOnce(apiError(500))
    renderDetail(failing)
    await user.click(
      within(await screen.findByTestId('groupadmin-detail-error')).getByRole('button', {
        name: 'もう一度',
      }),
    )
    expect(
      await screen.findByRole('heading', { level: 1, name: 'グループ: 営業部' }),
    ).toBeInTheDocument()
  })

  it('shows the members even when the assigned roles cannot be read, and "none" when there are none', async () => {
    const failing = detailApi()
    failing.getGroupRoles.mockRejectedValue(apiError(500))
    const view = renderDetail(failing)
    expect(await screen.findByTestId('groupadmin-roles-failed')).toHaveTextContent(
      '割り当てたロールを読み込めませんでした。',
    )
    expect(screen.getByTestId('groupadmin-member-21')).toBeInTheDocument()
    view.unmount()
    const empty = detailApi()
    empty.getGroupRoles.mockResolvedValue([])
    renderDetail(empty)
    expect(await screen.findByTestId('groupadmin-roles-empty')).toHaveTextContent('ありません。')
  })

  it('removes a member and lists the roles lost through the group (D20)', async () => {
    const user = userEvent.setup()
    const api = detailApi()
    api.removeMember.mockResolvedValue()
    api.getUserRoles.mockResolvedValue([{ roleId: 5, name: '営業', sources: [{ kind: 'DIRECT' }] }])
    renderDetail(api)
    await user.click(await screen.findByRole('button', { name: '田中 一郎を外す' }))
    expect(api.removeMember).toHaveBeenCalledWith(3, 21)
    expect(await screen.findByTestId('groupadmin-detail-notice')).toHaveTextContent(
      '田中 一郎さんは営業部から外れました。グループ経由のロール: 経理 も外れます。',
    )
    await waitFor(() => expect(api.getGroup).toHaveBeenCalledTimes(2))
  })

  it('shows only the first sentence when no role is lost or the roles cannot be read', async () => {
    const user = userEvent.setup()
    const api = detailApi()
    api.removeMember.mockResolvedValue()
    api.getUserRoles
      .mockResolvedValueOnce([
        { roleId: 5, name: '営業', sources: [{ kind: 'DIRECT' }] },
        { roleId: 6, name: '経理', sources: [{ kind: 'DIRECT' }] },
      ])
      .mockRejectedValueOnce(apiError(500))
    renderDetail(api)
    await user.click(await screen.findByRole('button', { name: '田中 一郎を外す' }))
    const notice = await screen.findByTestId('groupadmin-detail-notice')
    await waitFor(() => expect(notice).toHaveTextContent('田中 一郎さんは営業部から外れました。'))
    expect(notice).not.toHaveTextContent('グループ経由')
    await waitFor(() => expect(api.getGroup).toHaveBeenCalledTimes(2))
    await user.click(await screen.findByRole('button', { name: '佐藤 花子を外す' }))
    await waitFor(() =>
      expect(screen.getByTestId('groupadmin-detail-notice')).toHaveTextContent(
        '佐藤 花子さんは営業部から外れました。',
      ),
    )
    expect(screen.getByTestId('groupadmin-detail-notice')).not.toHaveTextContent('グループ経由')
  })

  it('treats GROUP_NO_CHANGE as already removed and keeps the table on GROUP_BUSY', async () => {
    const user = userEvent.setup()
    const api = detailApi()
    api.removeMember
      .mockRejectedValueOnce(apiError(409, 'GROUP_BUSY'))
      .mockRejectedValueOnce(apiError(409, 'GROUP_NO_CHANGE'))
    renderDetail(api)
    await user.click(await screen.findByRole('button', { name: '田中 一郎を外す' }))
    expect(await screen.findByTestId('groupadmin-detail-notice')).toHaveTextContent(
      'ほかの操作と重なりました。',
    )
    expect(api.getGroup).toHaveBeenCalledTimes(1)
    await user.click(screen.getByRole('button', { name: '田中 一郎を外す' }))
    await waitFor(() =>
      expect(screen.getByTestId('groupadmin-detail-notice')).toHaveTextContent(
        'すでに外れています。',
      ),
    )
    await waitFor(() => expect(api.getGroup).toHaveBeenCalledTimes(2))
    expect(document.body).not.toHaveTextContent(DETAIL_MARKER)
  })

  it('adds a member from the picker, marks members as taken, and reloads after closing', async () => {
    const user = userEvent.setup()
    const api = detailApi()
    api.searchUsers.mockResolvedValue([
      {
        userId: 21,
        displayName: '田中 一郎',
        email: 'tanaka.ichiro@example.com',
        suspended: false,
      },
      {
        userId: 30,
        displayName: '鈴木 三郎',
        email: 'suzuki.saburo@example.com',
        suspended: false,
      },
    ])
    api.addMember.mockResolvedValue()
    renderDetail(api)
    await user.click(await screen.findByTestId('groupadmin-add-member'))
    const dialog = await screen.findByRole('dialog', { name: 'メンバーを足す' })
    await user.type(within(dialog).getByTestId('groupadmin-candidate-input'), 'a{Enter}')
    const taken = await within(dialog).findByTestId('groupadmin-candidate-row-21')
    expect(taken).toHaveTextContent('メンバー済み')
    await user.click(within(dialog).getByRole('button', { name: '鈴木 三郎を足す' }))
    expect(api.addMember).toHaveBeenCalledWith(3, 30)
    expect(await within(dialog).findByTestId('groupadmin-candidate-status')).toHaveTextContent(
      '鈴木 三郎さんをメンバーにしました。',
    )
    await user.click(within(dialog).getByTestId('groupadmin-candidate-close'))
    await waitFor(() => expect(api.getGroup).toHaveBeenCalledTimes(2))
    await waitFor(() => expect(screen.getByTestId('groupadmin-add-member')).toHaveFocus())
  })

  it('maps add failures: already a member, missing user and busy', async () => {
    const user = userEvent.setup()
    const api = detailApi()
    api.searchUsers.mockResolvedValue([
      {
        userId: 30,
        displayName: '鈴木 三郎',
        email: 'suzuki.saburo@example.com',
        suspended: false,
      },
      {
        userId: 31,
        displayName: '高橋 四郎',
        email: 'takahashi.shiro@example.com',
        suspended: false,
      },
      { userId: 32, displayName: '伊藤 五郎', email: 'ito.goro@example.com', suspended: false },
    ])
    api.addMember
      .mockRejectedValueOnce(apiError(409, 'GROUP_NO_CHANGE'))
      .mockRejectedValueOnce(apiError(404, 'USER_NOT_FOUND'))
      .mockRejectedValueOnce(apiError(409, 'GROUP_BUSY'))
    renderDetail(api)
    await user.click(await screen.findByTestId('groupadmin-add-member'))
    const dialog = await screen.findByRole('dialog')
    await user.type(within(dialog).getByTestId('groupadmin-candidate-input'), 'a{Enter}')
    const status = await within(dialog).findByTestId('groupadmin-candidate-status')
    for (const [name, text] of [
      ['鈴木 三郎', 'すでにメンバーです。'],
      ['高橋 四郎', 'この利用者はいません。'],
      ['伊藤 五郎', 'ほかの操作と重なりました。'],
    ] as const) {
      await user.click(await within(dialog).findByRole('button', { name: `${name}を足す` }))
      await waitFor(() => expect(status).toHaveTextContent(text))
    }
    expect(within(dialog).getByRole('button', { name: '鈴木 三郎を足す' })).toBeDisabled()
    expect(within(dialog).getByRole('button', { name: '高橋 四郎を足す' })).toBeDisabled()
    expect(within(dialog).getByRole('button', { name: '伊藤 五郎を足す' })).toBeEnabled()
  })

  it('renames the group from the detail and shows a duplicate name under the field', async () => {
    const user = userEvent.setup()
    const api = detailApi()
    api.renameGroup
      .mockRejectedValueOnce(apiError(409, 'GROUP_NAME_DUPLICATE'))
      .mockResolvedValueOnce()
    renderDetail(api)
    await user.click(await screen.findByTestId('groupadmin-rename'))
    const input = await screen.findByTestId('groupadmin-name-input')
    await user.clear(input)
    await user.type(input, '営業二部')
    await user.click(screen.getByTestId('groupadmin-name-submit'))
    expect(await screen.findByText('同じ名前のグループがあります。')).toBeInTheDocument()
    await user.click(screen.getByTestId('groupadmin-name-submit'))
    expect(await screen.findByTestId('groupadmin-detail-notice')).toHaveTextContent(
      'グループの名前を「営業二部」に変えました。',
    )
    expect(api.renameGroup).toHaveBeenLastCalledWith(3, '営業二部')
  })

  it('has no accessibility violations with a 64 character name and two-word member names', async () => {
    const api = detailApi()
    api.getGroup.mockResolvedValue(
      detailOf({
        name: LONG_GROUP_NAME,
        members: [memberOf(), memberOf({ userId: 22, displayName: '佐藤 花子' })],
      }),
    )
    const view = renderDetail(api)
    await screen.findByTestId('groupadmin-detail-page')
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
