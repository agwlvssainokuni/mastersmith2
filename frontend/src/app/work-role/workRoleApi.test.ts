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
// 作業ロールの API の関数のテスト（Intent 261004-role-menu の U7 の L1、D3、NFR1.4）。fetch を差し替えて ApiClient を
// 通し、送る道・方法・本文と、応答の写し・形の誤りを確かめる。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { registerAuthHandlers, resetApiClient } from '../../shared/api-client/apiClient'
import { getWorkRole, parseWorkRole, switchWorkRole, WORK_ROLE_API } from './workRoleApi'

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
  resetApiClient()
})

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function call(index = 0): { path: string; init: RequestInit } {
  const [path, init] = fetchMock.mock.calls[index] as [string, RequestInit]
  return { path, init }
}

const NETWORK = { kind: 'network' }

describe('workRoleApi', () => {
  it('reads the roles and the current work role without parameters, dropping unknown fields', async () => {
    fetchMock.mockResolvedValueOnce(
      json(200, {
        roles: [
          { roleId: 1, name: '営業', builtIn: true },
          { roleId: 2, name: '経理' },
        ],
        current: { roleId: 2, name: '経理', userId: 9 },
        userId: 9,
      }),
    )
    expect(await getWorkRole()).toEqual({
      roles: [
        { roleId: 1, name: '営業' },
        { roleId: 2, name: '経理' },
      ],
      current: { roleId: 2, name: '経理' },
    })
    expect(call().path).toBe(WORK_ROLE_API)
    expect(call().path).toBe('/api/me/work-role')
    expect(call().init.method).toBe('GET')
  })

  it('accepts no roles and a null current role', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { roles: [], current: null }))
    expect(await getWorkRole()).toEqual({ roles: [], current: null })
  })

  it('sends only the role id when switching', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))
    await switchWorkRole(7)
    expect(call().path).toBe('/api/me/work-role')
    expect(call().init.method).toBe('PUT')
    expect(JSON.parse(call().init.body as string)).toEqual({ roleId: 7 })
    expect(Object.keys(JSON.parse(call().init.body as string) as object)).toEqual(['roleId'])
  })

  it('passes the rejection of a switch as the API error', async () => {
    fetchMock.mockResolvedValueOnce(
      json(409, { status: 409, code: 'ROLE_NOT_ASSIGNED', title: 'x', detail: 'secret' }),
    )
    await expect(switchWorkRole(3)).rejects.toEqual({
      kind: 'response',
      status: 409,
      code: 'ROLE_NOT_ASSIGNED',
    })
  })

  it('treats a broken body as a network failure', async () => {
    const broken = [
      { roles: 'x', current: null },
      { roles: [{ roleId: '1', name: 'a' }], current: null },
      { roles: [{ roleId: 1.5, name: 'a' }], current: null },
      { roles: [{ roleId: 1 }], current: null },
      { roles: [] },
      { roles: [], current: { roleId: 1 } },
      [],
      null,
    ]
    for (const body of broken) {
      expect(parseWorkRole(body)).toBeUndefined()
      fetchMock.mockResolvedValueOnce(json(200, body))
      await expect(getWorkRole()).rejects.toEqual(NETWORK)
    }
    fetchMock.mockResolvedValueOnce(new Response('not json', { status: 200 }))
    await expect(getWorkRole()).rejects.toEqual(NETWORK)
  })
})
