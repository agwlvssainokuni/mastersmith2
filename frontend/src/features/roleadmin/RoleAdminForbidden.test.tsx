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
// 画面と API の組の 403 の表のテスト（Intent 261004-role-menu の U6 の D2、NFR1.1、機能設計の読み直し R-02、
// security-design.md 1節）。読み込みと各操作の API が 403 ACCESS_DENIED を返すと、useAdminForbidden に要求の道を添えて
// 1回渡し、画面は独自の文言を出さない。骨組みの ShellLayout の中では権限なしの表示に置き換わる。判定の正はサーバー
// （401・403・200 は role・group のサーバー側のテストが確かめる）。
import { screen, waitFor, within } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { AssignmentPanel } from './AssignmentPanel'
import { PermissionPanel } from './PermissionPanel'
import { registration } from './registration'
import { RoleDetailPage } from './RoleDetailPage'
import { RoleListPage } from './RoleListPage'
import {
  apiError,
  assignmentsOf,
  DETAIL_MARKER,
  fakeRoleAdminApi,
  nodeOf,
  roleOf,
  rolePageOf,
  roleRowOf,
  type FakeRoleAdminApi,
} from './testing/fixtures'
import { renderRoleAdmin, renderRoleAdminDataRouter } from './testing/renderRoleAdmin'

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

function readyApi(): FakeRoleAdminApi {
  const api = fakeRoleAdminApi()
  api.listRoles.mockResolvedValue(rolePageOf([roleRowOf()]))
  api.getRole.mockResolvedValue(roleOf())
  api.listSchemas.mockResolvedValue([nodeOf()])
  api.listTables.mockResolvedValue([])
  api.getAssignments.mockResolvedValue(assignmentsOf({ groups: [{ groupId: 3, name: '営業部' }] }))
  return api
}

async function openRowMenu(user: UserEvent, item: string) {
  await user.click(await screen.findByRole('button', { name: '営業の操作' }))
  await user.click(within(await screen.findByRole('menu')).getByRole('menuitem', { name: item }))
}

interface Row {
  name: string
  path: string
  arrange: (api: FakeRoleAdminApi) => void
  act: (user: UserEvent, api: FakeRoleAdminApi) => Promise<void>
}

const LIST_ROWS: Row[] = [
  {
    name: 'S3 list',
    path: '/api/admin/roles?page=1',
    arrange: (api) => api.listRoles.mockRejectedValue(FORBIDDEN),
    act: async () => {},
  },
  {
    name: 'S3 create',
    path: '/api/admin/roles',
    arrange: (api) => api.createRole.mockRejectedValue(FORBIDDEN),
    act: async (user) => {
      await user.click(await screen.findByTestId('roleadmin-create'))
      await user.type(await screen.findByTestId('roleadmin-name-input'), '総務')
      await user.click(screen.getByTestId('roleadmin-name-submit'))
    },
  },
  {
    name: 'S3 rename',
    path: '/api/admin/roles/1',
    arrange: (api) => api.renameRole.mockRejectedValue(FORBIDDEN),
    act: async (user) => {
      await openRowMenu(user, '名前を変える')
      await user.type(await screen.findByTestId('roleadmin-name-input'), '部')
      await user.click(screen.getByTestId('roleadmin-name-submit'))
    },
  },
  {
    name: 'S3 delete',
    path: '/api/admin/roles/1',
    arrange: (api) => api.deleteRole.mockRejectedValue(FORBIDDEN),
    act: async (user) => {
      await openRowMenu(user, '削除')
      await user.click(await screen.findByTestId('roleadmin-delete-submit'))
    },
  },
]

const DETAIL_ROWS: Row[] = [
  {
    name: 'S4 role',
    path: '/api/admin/roles/7',
    arrange: (api) => api.getRole.mockRejectedValue(FORBIDDEN),
    act: async () => {},
  },
  {
    name: 'S4 schemas',
    path: '/api/admin/roles/7/permissions/schemas',
    arrange: (api) => api.listSchemas.mockRejectedValue(FORBIDDEN),
    act: async () => {},
  },
  {
    name: 'S4 tables',
    path: '/api/admin/roles/7/permissions/tables?schema=SALES',
    arrange: (api) => api.listTables.mockRejectedValue(FORBIDDEN),
    act: async () => {},
  },
  {
    name: 'S4 save',
    path: '/api/admin/roles/7/permissions',
    arrange: (api) => api.savePermissions.mockRejectedValue(FORBIDDEN),
    act: async (user) => {
      await user.selectOptions(
        await screen.findByRole('combobox', { name: '販売DBの主権限' }),
        'READ',
      )
      await user.click(screen.getByTestId('roleadmin-save'))
    },
  },
]

const ASSIGNMENT_ROWS: Row[] = [
  {
    name: 'S5 add user',
    path: '/api/admin/roles/7/assignments',
    arrange: (api) => {
      api.searchUsers.mockResolvedValue([
        {
          userId: 30,
          displayName: '鈴木 三郎',
          email: 'suzuki.saburo@example.com',
          suspended: false,
        },
      ])
      api.assignUser.mockRejectedValue(FORBIDDEN)
    },
    act: async (user, api) => {
      await user.click(await screen.findByTestId('roleadmin-add-user'))
      await user.type(await screen.findByTestId('roleadmin-candidate-input'), 's{Enter}')
      await user.click(await screen.findByRole('button', { name: '鈴木 三郎を足す' }))
      // 割り当ての読み込みの道と同じ道のため、読み込みの1回と足すの1回が並ぶ前に足すの答えを待つ。
      await waitFor(() => expect(api.assignUser).toHaveBeenCalledTimes(1))
    },
  },
  {
    name: 'S5 assignments',
    path: '/api/admin/roles/7/assignments',
    arrange: (api) => api.getAssignments.mockRejectedValue(FORBIDDEN),
    act: async () => {},
  },
  {
    name: 'S5 remove group',
    path: '/api/admin/roles/7/assignments/groups/3',
    arrange: (api) => api.unassignGroup.mockRejectedValue(FORBIDDEN),
    act: async (user) => {
      await user.click(await screen.findByRole('button', { name: '営業部を外す' }))
    },
  },
]

describe('roleadmin 403 table', () => {
  beforeEach(() => {
    recorded.paths = []
  })

  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it.each(LIST_ROWS)('$name passes the 403 with $path once', async (row) => {
    const user = userEvent.setup()
    const api = readyApi()
    row.arrange(api)
    renderRoleAdmin(<RoleListPage api={api} />)
    await screen.findByTestId('roleadmin-list-page')
    await row.act(user, api)
    await waitFor(() => expect(recorded.paths).toContain(row.path))
    expect(recorded.paths.filter((path) => path === row.path)).toHaveLength(1)
    expect(screen.queryByTestId('roleadmin-notice')?.textContent ?? '').toBe('')
    expect(document.body).not.toHaveTextContent('操作できませんでした')
    expect(document.body).not.toHaveTextContent(DETAIL_MARKER)
  })

  it.each(DETAIL_ROWS)('$name passes the 403 with $path once', async (row) => {
    const user = userEvent.setup()
    const api = readyApi()
    row.arrange(api)
    const element = <RoleDetailPage api={api} />
    renderRoleAdminDataRouter([{ path: '/admin/roles/:roleId', element }], {
      route: '/admin/roles/7',
    })
    await row.act(user, api)
    await waitFor(() => expect(recorded.paths).toContain(row.path))
    expect(recorded.paths.filter((path) => path === row.path)).toHaveLength(1)
    expect(document.body).not.toHaveTextContent('操作できませんでした')
    if (row.name !== 'S4 tables') {
      // テーブルの一覧は共有の木の子の読み込みのため、木は自分の「読み込めませんでした」を出す。骨組みの中では画面全体が
      // 権限なしの表示に置き換わるため、利用者には見えない。
      expect(document.body).not.toHaveTextContent('読み込めませんでした')
    }
  })

  it.each(ASSIGNMENT_ROWS)('$name passes the 403 with $path once', async (row) => {
    const user = userEvent.setup()
    const api = readyApi()
    row.arrange(api)
    renderRoleAdmin(<AssignmentPanel roleId={7} roleName="営業" api={api} onMissing={vi.fn()} />)
    await row.act(user, api)
    await waitFor(() => expect(recorded.paths).toContain(row.path))
    expect(recorded.paths.filter((path) => path === row.path)).toHaveLength(1)
    expect(document.body).not.toHaveTextContent('操作できませんでした')
    expect(document.body).not.toHaveTextContent('読み込めませんでした')
  })

  it('replaces the list screen with the forbidden view inside the shell', async () => {
    const api = readyApi()
    api.listRoles.mockRejectedValue(FORBIDDEN)
    renderRoleAdmin(<RoleListPage api={api} />, { withShell: true, registrations: [registration] })
    expect(await screen.findByTestId('admin-forbidden-view')).toBeInTheDocument()
    expect(screen.getByTestId('admin-forbidden-heading')).toHaveTextContent('ロール')
    expect(screen.queryByTestId('roleadmin-list-page')).toBeNull()
  })

  it('treats a 403 without ACCESS_DENIED as a general failure of the panel', async () => {
    const api = readyApi()
    api.listSchemas.mockRejectedValue(apiError(403))
    renderRoleAdminDataRouter(
      [
        {
          path: '/admin/roles/:roleId',
          element: <PermissionPanel roleId={7} api={api} onMissing={vi.fn()} />,
        },
      ],
      { route: '/admin/roles/7' },
    )
    expect(await screen.findByTestId('roleadmin-perm-error')).toBeInTheDocument()
  })
})
