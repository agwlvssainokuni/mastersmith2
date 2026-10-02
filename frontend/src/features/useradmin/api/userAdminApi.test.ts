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
// userAdminApi のテスト（frontend-components.md の 5節、security-design.md の 6.3、NFR3.3・NFR5.3、計画 8節の D-4）。
// fetch を差し替えて ApiClient を通し、要求の形と応答の読み取りを確かめる。ApiClient の中（トークン・401 の更新・
// Accept-Language）は既存と U4 のテストが確かめるため、ここでは見ない。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { registerAuthHandlers, resetApiClient } from '../../../shared/api-client/apiClient'
import type { ApiError } from '../../../shared/api-client/apiError'
import {
  grantAdmin,
  listUsers,
  resetLoginFailures,
  resumeUser,
  revokeAdmin,
  suspendUser,
  updateProfile,
  USER_ADMIN_API_ROOT,
  userAdminOperationPath,
} from './userAdminApi'

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

function call(index = 0): { path: string; init: RequestInit; headers: Headers } {
  const [path, init] = fetchMock.mock.calls[index] as [string, RequestInit]
  return { path, init, headers: new Headers(init.headers) }
}

const row = {
  userId: 7,
  email: 'yamada.taro@example.com',
  displayName: '山田 太郎',
  language: 'ja',
  admin: false,
  suspended: false,
  locked: false,
  lockedUntil: null,
  resettable: false,
  registeredAt: '2026-09-25T12:00:00Z',
  self: false,
}

const pageBody = { items: [row], page: 1, size: 20, total: 1 }

describe('userAdminApi requests', () => {
  it('reads a page and adds q only when there is search text', async () => {
    fetchMock.mockResolvedValueOnce(json(200, pageBody)).mockResolvedValueOnce(json(200, pageBody))
    await listUsers(3, '')
    await listUsers(1, '山田')
    expect(call(0).path).toBe('/api/admin/users?page=3')
    expect(call(0).init.method).toBe('GET')
    expect(call(1).path).toBe(`/api/admin/users?page=1&q=${encodeURIComponent('山田')}`)
  })

  it('encodes special characters of the search text', async () => {
    fetchMock.mockResolvedValueOnce(json(200, pageBody))
    await listUsers(2, 'a&b#c%d e＋')
    expect(call().path).toBe('/api/admin/users?page=2&q=a%26b%23c%25d%20e%EF%BC%8B')
  })

  it('sends the five operations to their paths without a body', async () => {
    for (let i = 0; i < 5; i += 1) fetchMock.mockResolvedValueOnce(noContent())
    await grantAdmin(7)
    await revokeAdmin(7)
    await suspendUser(7)
    await resumeUser(7)
    await resetLoginFailures(7)
    const paths = fetchMock.mock.calls.map(([path]) => path as string)
    expect(paths).toEqual([
      '/api/admin/users/7/grant-admin',
      '/api/admin/users/7/revoke-admin',
      '/api/admin/users/7/suspend',
      '/api/admin/users/7/resume',
      '/api/admin/users/7/reset-login-failures',
    ])
    for (let i = 0; i < 5; i += 1) {
      expect(call(i).init.method).toBe('POST')
      expect(call(i).init.body).toBeUndefined()
    }
  })

  it('builds the operation paths from the root and an encoded user id', () => {
    expect(USER_ADMIN_API_ROOT).toBe('/api/admin/users')
    expect(userAdminOperationPath(12, 'profile')).toBe('/api/admin/users/12/profile')
    expect(userAdminOperationPath(Number.NaN, 'suspend')).toBe('/api/admin/users/NaN/suspend')
  })

  it('sends only the name and the language when updating the profile', async () => {
    fetchMock.mockResolvedValueOnce(noContent())
    const request = {
      displayName: '山田 花子',
      language: 'en',
      admin: true,
      passwordHash: 'x',
    } as const
    await updateProfile(7, request)
    expect(call().path).toBe('/api/admin/users/7/profile')
    expect(call().init.method).toBe('PUT')
    expect(call().headers.get('Content-Type')).toBe('application/json')
    expect(JSON.parse(call().init.body as string)).toEqual({
      displayName: '山田 花子',
      language: 'en',
    })
  })
})

describe('userAdminApi success bodies', () => {
  it('keeps only the C3 fields and drops extra ones such as the password hash', async () => {
    const leaky = {
      ...row,
      passwordHash: 'leak-check-hash',
      failedAttempts: 4,
      refreshToken: 'leak-check-token',
    }
    fetchMock.mockResolvedValueOnce(json(200, { ...pageBody, items: [leaky], extra: 'leak-check' }))
    const page = await listUsers(1, '')
    expect(JSON.stringify(page)).not.toContain('leak-check')
    expect(Object.keys(page.items[0] ?? {})).not.toContain('failedAttempts')
    expect(Object.keys(page)).toEqual(['items', 'page', 'size', 'total'])
  })

  it('maps a null or missing lockedUntil to no value and keeps a string', async () => {
    const withoutLockedUntil: Record<string, unknown> = { ...row }
    delete withoutLockedUntil.lockedUntil
    fetchMock.mockResolvedValueOnce(
      json(200, {
        ...pageBody,
        items: [
          row,
          { ...withoutLockedUntil, userId: 8 },
          { ...row, userId: 9, locked: true, lockedUntil: '2026-10-03T01:30:00Z' },
        ],
        total: 3,
      }),
    )
    const page = await listUsers(1, '')
    expect(page.items[0]).not.toHaveProperty('lockedUntil')
    expect(page.items[1]).not.toHaveProperty('lockedUntil')
    expect(page.items[2]?.lockedUntil).toBe('2026-10-03T01:30:00Z')
    expect(page.items[0]).toEqual({ ...withoutLockedUntil })
  })

  it('treats an unreadable or malformed body as a network failure', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response('not json', { status: 200 }))
      .mockResolvedValueOnce(json(200, { ...pageBody, total: 'many' }))
      .mockResolvedValueOnce(json(200, { ...pageBody, items: [{ ...row, userId: '7' }] }))
      .mockResolvedValueOnce(json(200, { ...pageBody, items: [{ ...row, language: 'fr' }] }))
      .mockResolvedValueOnce(json(200, { ...pageBody, items: [{ ...row, lockedUntil: 5 }] }))
      .mockResolvedValueOnce(json(200, { ...pageBody, items: [{ ...row, self: undefined }] }))
      .mockResolvedValueOnce(json(200, [row]))
    for (let i = 0; i < 7; i += 1) {
      await expect(listUsers(1, '')).rejects.toEqual({ kind: 'network' })
    }
  })

  it('treats the empty 204 body of an operation as a success', async () => {
    fetchMock.mockResolvedValueOnce(noContent()).mockResolvedValueOnce(noContent())
    await expect(suspendUser(7)).resolves.toBeUndefined()
    await expect(updateProfile(7, { displayName: 'a', language: 'ja' })).resolves.toBeUndefined()
  })
})

describe('userAdminApi failures', () => {
  it('passes the kind, status and code of a failure through as they are', async () => {
    fetchMock
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({ status: 409, code: 'USER_ADMIN_LAST_ADMIN', detail: 'marker' }),
          { status: 409, headers: { 'Content-Type': 'application/problem+json' } },
        ),
      )
      .mockResolvedValueOnce(new Response('<html>Bad Request</html>', { status: 400 }))
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))
    const conflict = await revokeAdmin(7).catch((error: unknown) => error as ApiError)
    expect(conflict).toEqual({ kind: 'response', status: 409, code: 'USER_ADMIN_LAST_ADMIN' })
    const html = await listUsers(1, 'x').catch((error: unknown) => error as ApiError)
    expect(html).toEqual({ kind: 'response', status: 400 })
    const network = await updateProfile(7, { displayName: 'a', language: 'ja' }).catch(
      (error: unknown) => error as ApiError,
    )
    expect(network).toEqual({ kind: 'network' })
  })
})
