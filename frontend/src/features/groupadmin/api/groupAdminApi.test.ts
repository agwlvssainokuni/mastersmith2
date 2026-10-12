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
// groupAdminApi のテスト（Intent 261004-role-menu の U6、frontend-components.md の 4節、L2、NFR1.4・NFR1.9）。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { registerAuthHandlers, resetApiClient } from '../../../shared/api-client/apiClient'
import {
  addMember,
  createGroup,
  deleteGroup,
  getGroup,
  getGroupRoles,
  getUserRoles,
  listGroups,
  removeMember,
  renameGroup,
  searchUsers,
} from './groupAdminApi'

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

function call(index = 0): { path: string; init: RequestInit; headers: Headers; body: unknown } {
  const [path, init] = fetchMock.mock.calls[index] as [string, RequestInit]
  let body: unknown
  if (typeof init.body === 'string') {
    try {
      body = JSON.parse(init.body) as unknown
    } catch {
      body = init.body
    }
  }
  return { path, init, headers: new Headers(init.headers), body }
}

const NETWORK = { kind: 'network' }

const member = { userId: 7, displayName: '計測 花子', email: 'hanako@example.com', suspended: true }

describe('groupAdminApi requests', () => {
  it('reads a group page and maps only the known fields', async () => {
    fetchMock.mockResolvedValueOnce(
      json(200, {
        items: [{ groupId: 2, name: '営業部', memberCount: 3, assignedRoleCount: 1, secret: 1 }],
        page: 1,
        size: 20,
        total: 1,
      }),
    )
    expect(await listGroups(1)).toEqual({
      items: [{ groupId: 2, name: '営業部', memberCount: 3, assignedRoleCount: 1 }],
      page: 1,
      size: 20,
      total: 1,
    })
    expect(call().path).toBe('/api/admin/groups?page=1')
  })

  it('creates, reads, renames and deletes a group with only the name in the body', async () => {
    const group = {
      groupId: 2,
      name: '営業部',
      createdAt: '2026-10-01T00:00:00Z',
      updatedAt: '2026-10-01T00:00:00Z',
    }
    fetchMock
      .mockResolvedValueOnce(json(201, { ...group, createdBy: 1 }))
      .mockResolvedValueOnce(
        json(200, { groupId: 2, name: '営業部', members: [{ ...member, passwordHash: 'h' }] }),
      )
      .mockResolvedValueOnce(noContent())
      .mockResolvedValueOnce(noContent())
    expect(await createGroup('営業部')).toEqual(group)
    expect(await getGroup(2)).toEqual({ groupId: 2, name: '営業部', members: [member] })
    await renameGroup(2, '営業一部')
    await deleteGroup(2)
    expect(call(0)).toMatchObject({ path: '/api/admin/groups', body: { name: '営業部' } })
    expect(Object.keys(call(0).body as object)).toEqual(['name'])
    expect(call(1).path).toBe('/api/admin/groups/2')
    expect(call(2)).toMatchObject({ path: '/api/admin/groups/2', body: { name: '営業一部' } })
    expect(call(2).init.method).toBe('PUT')
    expect(call(3).init.method).toBe('DELETE')
  })

  it('adds and removes a member with only the userId', async () => {
    fetchMock.mockResolvedValueOnce(noContent()).mockResolvedValueOnce(noContent())
    await addMember(2, 7)
    await removeMember(2, 7)
    expect(call(0)).toMatchObject({ path: '/api/admin/groups/2/members', body: { userId: 7 } })
    expect(Object.keys(call(0).body as object)).toEqual(['userId'])
    expect(call(1).path).toBe('/api/admin/groups/2/members/7')
    expect(call(1).init.method).toBe('DELETE')
  })

  it('reads the roles of a group and of a user as arrays', async () => {
    fetchMock
      .mockResolvedValueOnce(json(200, [{ roleId: 1, name: '営業', extra: true }]))
      .mockResolvedValueOnce(
        json(200, [
          { roleId: 1, name: '営業', sources: [{ type: 'GROUP', groupId: 2, name: '営業部' }] },
        ]),
      )
    expect(await getGroupRoles(2)).toEqual([{ roleId: 1, name: '営業' }])
    expect(await getUserRoles(7)).toEqual([
      { roleId: 1, name: '営業', sources: [{ kind: 'GROUP', groupId: 2, name: '営業部' }] },
    ])
    expect(call(0).path).toBe('/api/admin/groups/2/roles')
    expect(call(1).path).toBe('/api/admin/users/7/roles')
  })

  it('searches candidates with the encoded text', async () => {
    fetchMock.mockResolvedValueOnce(
      json(200, { items: [{ ...member, admin: true }], page: 1, size: 20, total: 1 }),
    )
    expect(await searchUsers('a/b#c')).toEqual([member])
    expect(call().path).toBe('/api/admin/users?page=1&q=a%2Fb%23c')
  })
})

describe('groupAdminApi response shapes', () => {
  it('rejects wrong shapes as a network error', async () => {
    fetchMock
      .mockResolvedValueOnce(
        json(200, { groupId: 2, name: '営業部', members: [{ ...member, suspended: 'no' }] }),
      )
      .mockResolvedValueOnce(json(200, { items: [] }))
      .mockResolvedValueOnce(json(200, [{ roleId: 1, name: '営業', sources: [{ type: 'OTHER' }] }]))
    await expect(getGroup(2)).rejects.toEqual(NETWORK)
    await expect(getGroupRoles(2)).rejects.toEqual(NETWORK)
    await expect(getUserRoles(7)).rejects.toEqual(NETWORK)
  })

  it('passes the error of the server through with its code', async () => {
    fetchMock.mockResolvedValueOnce(
      json(409, { status: 409, code: 'GROUP_IN_USE', members: 3, assignedRoles: 1 }),
    )
    await expect(deleteGroup(2)).rejects.toMatchObject({ status: 409, code: 'GROUP_IN_USE' })
  })
})
