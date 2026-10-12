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
// roleAdminApi のテスト（Intent 261004-role-menu の U6、frontend-components.md の 3節、L2、NFR1.4・NFR1.9、計画 2.3）。
// fetch を差し替えて ApiClient を通し、送る道・引数・方法・本文の項目と、応答の写し・形の誤りを確かめる。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { registerAuthHandlers, resetApiClient } from '../../../shared/api-client/apiClient'
import {
  assignGroup,
  assignUser,
  clearPermissions,
  createRole,
  deleteRole,
  getAssignments,
  getRole,
  getUserRoles,
  listColumns,
  listGroups,
  listRoles,
  listSchemas,
  listTables,
  renameRole,
  savePermissions,
  searchUsers,
  unassignGroup,
  unassignUser,
} from './roleAdminApi'

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  resetApiClient()
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  registerAuthHandlers({
    getAccessToken: () => 'access-token',
    refresh: () => Promise.resolve(false),
    onUnauthenticated: () => {},
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function noContent(): Response {
  return new Response(null, { status: 204 })
}

function call(index = 0): { path: string; init: RequestInit; body: unknown } {
  const [path, init] = fetchMock.mock.calls[index] as [string, RequestInit]
  return {
    path,
    init,
    body: typeof init.body === 'string' ? (JSON.parse(init.body) as unknown) : undefined,
  }
}

const node = {
  schemaName: 'SALES',
  tableName: 'ORDER_LINE',
  columnName: null,
  displayName: '受注明細',
  explicit: { main: 'READ', create: null, delete: true },
  effective: { main: 'READ', create: false, delete: true },
  inheritedFrom: 'EXPLICIT',
  inMenu: true,
  inCurrentDsl: true,
  hasChildren: false,
}

const NETWORK = { kind: 'network' }

describe('roleAdminApi requests', () => {
  it('reads a role page and maps only the known fields', async () => {
    fetchMock.mockResolvedValueOnce(
      json(200, {
        items: [{ roleId: 1, name: '営業', userCount: 2, groupCount: 1, secret: 'x' }],
        page: 2,
        size: 20,
        total: 21,
        passwordHash: 'h',
      }),
    )
    const page = await listRoles(2)
    expect(call().path).toBe('/api/admin/roles?page=2')
    expect(call().init.method).toBe('GET')
    expect(page).toEqual({
      items: [{ roleId: 1, name: '営業', userCount: 2, groupCount: 1 }],
      page: 2,
      size: 20,
      total: 21,
    })
  })

  it('reads, creates, renames and deletes a role with only the name in the body', async () => {
    const role = {
      roleId: 5,
      name: '経理',
      createdAt: '2026-10-01T00:00:00Z',
      updatedAt: '2026-10-01T00:00:00Z',
    }
    fetchMock
      .mockResolvedValueOnce(json(200, { ...role, builtIn: true }))
      .mockResolvedValueOnce(json(201, role))
      .mockResolvedValueOnce(noContent())
      .mockResolvedValueOnce(noContent())
    expect(await getRole(5)).toEqual(role)
    expect(await createRole('経理')).toEqual(role)
    await renameRole(5, '経理部')
    await deleteRole(5)
    expect(call(0).path).toBe('/api/admin/roles/5')
    expect(call(1)).toMatchObject({ path: '/api/admin/roles', body: { name: '経理' } })
    expect(call(1).init.method).toBe('POST')
    expect(Object.keys(call(1).body as object)).toEqual(['name'])
    expect(call(2)).toMatchObject({ path: '/api/admin/roles/5', body: { name: '経理部' } })
    expect(call(2).init.method).toBe('PUT')
    expect(call(3).path).toBe('/api/admin/roles/5')
    expect(call(3).init.method).toBe('DELETE')
  })

  it('puts schema and table names into the query of the permission tree paths', async () => {
    fetchMock
      .mockResolvedValueOnce(json(200, { items: [node] }))
      .mockResolvedValueOnce(json(200, { items: [] }))
      .mockResolvedValueOnce(json(200, { items: [] }))
    await listSchemas(3)
    await listTables(3, 'A/B?C#D%E')
    await listColumns(3, 'S&T', 'T=U')
    expect(call(0).path).toBe('/api/admin/roles/3/permissions/schemas')
    expect(call(1).path).toBe('/api/admin/roles/3/permissions/tables?schema=A%2FB%3FC%23D%25E')
    expect(call(2).path).toBe('/api/admin/roles/3/permissions/columns?schema=S%26T&table=T%3DU')
  })

  it('maps permission nodes and drops unknown fields', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { items: [{ ...node, internal: 1 }], extra: true }))
    expect(await listSchemas(1)).toEqual([node])
  })

  it('saves only the scope names and the entry fields, without columnName in the scope', async () => {
    fetchMock.mockResolvedValueOnce(noContent())
    await savePermissions(4, { schemaName: 'S', tableName: 'T', columnName: 'X' } as never, [
      {
        schemaName: 'S',
        tableName: 'T',
        columnName: 'C',
        main: 'READ',
        create: null,
        delete: null,
        extra: 1,
      } as never,
    ])
    expect(call().path).toBe('/api/admin/roles/4/permissions')
    expect(call().init.method).toBe('PUT')
    expect(call().body).toEqual({
      scope: { schemaName: 'S', tableName: 'T' },
      entries: [
        {
          schemaName: 'S',
          tableName: 'T',
          columnName: 'C',
          main: 'READ',
          create: null,
          delete: null,
        },
      ],
    })
  })

  it('clears targets with only the target names', async () => {
    fetchMock.mockResolvedValueOnce(noContent())
    await clearPermissions(4, [{ schemaName: 'S', tableName: null, columnName: null }])
    expect(call().path).toBe('/api/admin/roles/4/permissions/clear')
    expect(call().init.method).toBe('POST')
    expect(call().body).toEqual({
      targets: [{ schemaName: 'S', tableName: null, columnName: null }],
    })
  })

  it('reads assignments and maps the source type to the kind', async () => {
    fetchMock.mockResolvedValueOnce(
      json(200, {
        users: [
          {
            userId: 7,
            displayName: '計測 花子',
            email: 'hanako@example.com',
            suspended: false,
            passwordHash: 'h',
            sources: [
              { type: 'DIRECT', groupId: null, name: null },
              { type: 'GROUP', groupId: 2, name: '営業部' },
            ],
          },
        ],
        groups: [{ groupId: 2, name: '営業部', memberCount: 3 }],
      }),
    )
    expect(await getAssignments(9)).toEqual({
      users: [
        {
          userId: 7,
          displayName: '計測 花子',
          email: 'hanako@example.com',
          suspended: false,
          sources: [{ kind: 'DIRECT' }, { kind: 'GROUP', groupId: 2, name: '営業部' }],
        },
      ],
      groups: [{ groupId: 2, name: '営業部' }],
    })
    expect(call().path).toBe('/api/admin/roles/9/assignments')
  })

  it('assigns and unassigns with only one of userId and groupId', async () => {
    fetchMock
      .mockResolvedValueOnce(noContent())
      .mockResolvedValueOnce(noContent())
      .mockResolvedValueOnce(noContent())
      .mockResolvedValueOnce(noContent())
    await assignUser(9, 7)
    await assignGroup(9, 2)
    await unassignUser(9, 7)
    await unassignGroup(9, 2)
    expect(call(0)).toMatchObject({ path: '/api/admin/roles/9/assignments', body: { userId: 7 } })
    expect(Object.keys(call(0).body as object)).toEqual(['userId'])
    expect(Object.keys(call(1).body as object)).toEqual(['groupId'])
    expect(call(2).path).toBe('/api/admin/roles/9/assignments/users/7')
    expect(call(2).init.method).toBe('DELETE')
    expect(call(3).path).toBe('/api/admin/roles/9/assignments/groups/2')
  })

  it('reads candidates and the roles of a user', async () => {
    fetchMock
      .mockResolvedValueOnce(
        json(200, {
          items: [{ groupId: 2, name: '営業部', memberCount: 1, assignedRoleCount: 0 }],
          page: 1,
          size: 20,
          total: 1,
        }),
      )
      .mockResolvedValueOnce(
        json(200, {
          items: [
            {
              userId: 7,
              displayName: '計測 花子',
              email: 'h@example.com',
              suspended: true,
              admin: false,
              locked: false,
            },
          ],
          page: 1,
          size: 20,
          total: 1,
        }),
      )
      .mockResolvedValueOnce(
        json(200, [
          { roleId: 1, name: '営業', sources: [{ type: 'DIRECT', groupId: null, name: null }] },
        ]),
      )
    expect(await listGroups(1)).toEqual({
      items: [{ groupId: 2, name: '営業部' }],
      page: 1,
      size: 20,
      total: 1,
    })
    expect(await searchUsers('花子 &?')).toEqual([
      { userId: 7, displayName: '計測 花子', email: 'h@example.com', suspended: true },
    ])
    expect(await getUserRoles(7)).toEqual([
      { roleId: 1, name: '営業', sources: [{ kind: 'DIRECT' }] },
    ])
    expect(call(0).path).toBe('/api/admin/groups?page=1')
    expect(call(1).path).toBe('/api/admin/users?page=1&q=%E8%8A%B1%E5%AD%90%20%26%3F')
    expect(call(2).path).toBe('/api/admin/users/7/roles')
  })

  it('searches without q when the search text is empty', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { items: [], page: 1, size: 20, total: 0 }))
    await searchUsers('')
    expect(call().path).toBe('/api/admin/users?page=1')
  })
})

describe('roleAdminApi response shapes', () => {
  it('rejects a node with an unknown inheritedFrom or a missing field as a network error', async () => {
    fetchMock
      .mockResolvedValueOnce(json(200, { items: [{ ...node, inheritedFrom: 'PARENT' }] }))
      .mockResolvedValueOnce(json(200, { items: [{ ...node, displayName: undefined }] }))
      .mockResolvedValueOnce(json(200, [node]))
    await expect(listSchemas(1)).rejects.toEqual(NETWORK)
    await expect(listSchemas(1)).rejects.toEqual(NETWORK)
    await expect(listSchemas(1)).rejects.toEqual(NETWORK)
  })

  it('rejects a source type other than DIRECT and GROUP', async () => {
    fetchMock.mockResolvedValueOnce(
      json(200, [
        { roleId: 1, name: '営業', sources: [{ type: 'USER', groupId: null, name: null }] },
      ]),
    )
    await expect(getUserRoles(7)).rejects.toEqual(NETWORK)
  })

  it('rejects roles of a user that are not an array and a page with a wrong type', async () => {
    fetchMock
      .mockResolvedValueOnce(json(200, { items: [] }))
      .mockResolvedValueOnce(
        json(200, {
          items: [{ roleId: '1', name: 'x', userCount: 0, groupCount: 0 }],
          page: 1,
          size: 20,
          total: 1,
        }),
      )
      .mockResolvedValueOnce(new Response('not json', { status: 200 }))
    await expect(getUserRoles(7)).rejects.toEqual(NETWORK)
    await expect(listRoles(1)).rejects.toEqual(NETWORK)
    await expect(getRole(1)).rejects.toEqual(NETWORK)
  })

  it('passes the error of the server through with its code', async () => {
    fetchMock.mockResolvedValueOnce(
      json(409, { status: 409, code: 'ROLE_IN_USE', assignedUsers: 2, assignedGroups: 1 }),
    )
    await expect(deleteRole(1)).rejects.toMatchObject({
      kind: 'response',
      status: 409,
      code: 'ROLE_IN_USE',
    })
  })
})
