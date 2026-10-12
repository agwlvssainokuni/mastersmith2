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
// 画面と API の組の 403 の表のテスト（Intent 261004-role-menu の U6 の D2、NFR1.1、security-design.md 1節）。読み込みと
// 各操作の API が 403 ACCESS_DENIED を返すと、useAdminForbidden に要求の道を添えて1回渡し、画面は独自の文言を出さない。
// 骨組みの ShellLayout の中では権限なしの表示に置き換わる。判定の正はサーバー（group のサーバー側のテストが確かめる）。
import { screen, waitFor, within } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { GroupDetailPage } from './GroupDetailPage'
import { GroupListPage } from './GroupListPage'
import { registration } from './registration'
import {
  apiError,
  DETAIL_MARKER,
  detailOf,
  fakeGroupAdminApi,
  groupPageOf,
  groupRowOf,
  type FakeGroupAdminApi,
} from './testing/fixtures'
import { renderGroupAdmin } from './testing/renderGroupAdmin'

const recorded = vi.hoisted(() => ({ paths: [] as string[] }))

vi.mock('../../app/admin-forbidden/AdminForbiddenProvider', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('../../app/admin-forbidden/AdminForbiddenProvider')>()
  return {
    ...actual,
    useAdminForbidden: () => {
      const handle = actual.useAdminForbidden()
      return (error: unknown, apiPath: string) => {
        recorded.paths.push(apiPath)
        return handle(error, apiPath)
      }
    },
  }
})

const FORBIDDEN = apiError(403, 'ACCESS_DENIED')

function readyApi(): FakeGroupAdminApi {
  const api = fakeGroupAdminApi()
  api.listGroups.mockResolvedValue(groupPageOf([groupRowOf()]))
  api.getGroup.mockResolvedValue(detailOf())
  api.getGroupRoles.mockResolvedValue([])
  api.searchUsers.mockResolvedValue([
    { userId: 30, displayName: '鈴木 三郎', email: 'suzuki.saburo@example.com', suspended: false },
  ])
  return api
}

interface Row {
  name: string
  screen: 'list' | 'detail'
  path: string
  arrange: (api: FakeGroupAdminApi) => void
  act: (user: UserEvent) => Promise<void>
}

async function openRowMenu(user: UserEvent, item: string) {
  await user.click(await screen.findByRole('button', { name: '営業部の操作' }))
  await user.click(within(await screen.findByRole('menu')).getByRole('menuitem', { name: item }))
}

const ROWS: Row[] = [
  {
    name: 'S6 list',
    screen: 'list',
    path: '/api/admin/groups?page=1',
    arrange: (api) => api.listGroups.mockRejectedValue(FORBIDDEN),
    act: async () => {},
  },
  {
    name: 'S6 create',
    screen: 'list',
    path: '/api/admin/groups',
    arrange: (api) => api.createGroup.mockRejectedValue(FORBIDDEN),
    act: async (user) => {
      await user.click(await screen.findByTestId('groupadmin-create'))
      await user.type(await screen.findByTestId('groupadmin-name-input'), '総務部')
      await user.click(screen.getByTestId('groupadmin-name-submit'))
    },
  },
  {
    name: 'S6 delete',
    screen: 'list',
    path: '/api/admin/groups/1',
    arrange: (api) => api.deleteGroup.mockRejectedValue(FORBIDDEN),
    act: async (user) => {
      await openRowMenu(user, '削除')
      await user.click(await screen.findByTestId('groupadmin-delete-submit'))
    },
  },
  {
    name: 'S6 detail',
    screen: 'detail',
    path: '/api/admin/groups/3',
    arrange: (api) => api.getGroup.mockRejectedValue(FORBIDDEN),
    act: async () => {},
  },
  {
    name: 'S6 rename',
    screen: 'detail',
    path: '/api/admin/groups/3',
    arrange: (api) => api.renameGroup.mockRejectedValue(FORBIDDEN),
    act: async (user) => {
      await user.click(await screen.findByTestId('groupadmin-rename'))
      await user.type(await screen.findByTestId('groupadmin-name-input'), '二')
      await user.click(screen.getByTestId('groupadmin-name-submit'))
    },
  },
  {
    name: 'S6 add member',
    screen: 'detail',
    path: '/api/admin/groups/3/members',
    arrange: (api) => api.addMember.mockRejectedValue(FORBIDDEN),
    act: async (user) => {
      await user.click(await screen.findByTestId('groupadmin-add-member'))
      await user.type(await screen.findByTestId('groupadmin-candidate-input'), 's{Enter}')
      await user.click(await screen.findByRole('button', { name: '鈴木 三郎を足す' }))
    },
  },
  {
    name: 'S6 remove member',
    screen: 'detail',
    path: '/api/admin/groups/3/members/21',
    arrange: (api) => api.removeMember.mockRejectedValue(FORBIDDEN),
    act: async (user) => {
      await user.click(await screen.findByRole('button', { name: '田中 一郎を外す' }))
    },
  },
]

describe('groupadmin 403 table', () => {
  beforeEach(() => {
    recorded.paths = []
  })

  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it.each(ROWS)('$name passes the 403 with $path once', async (row) => {
    const user = userEvent.setup()
    const api = readyApi()
    row.arrange(api)
    if (row.screen === 'list') {
      renderGroupAdmin(<GroupListPage api={api} />)
    } else {
      renderGroupAdmin(
        <Routes>
          <Route path="/admin/groups/:groupId" element={<GroupDetailPage api={api} />} />
        </Routes>,
        { route: '/admin/groups/3' },
      )
    }
    await row.act(user)
    await waitFor(() => expect(recorded.paths).toContain(row.path))
    expect(recorded.paths.filter((path) => path === row.path)).toHaveLength(1)
    expect(document.body).not.toHaveTextContent('操作できませんでした')
    expect(document.body).not.toHaveTextContent('読み込めませんでした')
    expect(document.body).not.toHaveTextContent(DETAIL_MARKER)
  })

  it('replaces the list screen with the forbidden view inside the shell', async () => {
    const api = readyApi()
    api.listGroups.mockRejectedValue(FORBIDDEN)
    renderGroupAdmin(<GroupListPage api={api} />, {
      withShell: true,
      registrations: [registration],
      route: '/admin/groups',
    })
    expect(await screen.findByTestId('admin-forbidden-view')).toBeInTheDocument()
    expect(screen.getByTestId('admin-forbidden-heading')).toHaveTextContent('グループ')
  })
})
