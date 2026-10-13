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
// 置き場の問い合わせの API のテスト（Intent 261004-role-menu の U7 の L9、D3・D21、NFR1.4）。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { registerAuthHandlers, resetApiClient } from '../../shared/api-client/apiClient'
import { getTableAccess, parseTableAccess, tableAccessPath } from './tableAccessApi'

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

const ok = {
  schemaName: 'SALES',
  tableName: 'ORDER',
  displayName: { ja: '受注', en: 'Orders' },
  main: 'READ',
}

describe('tableAccessApi', () => {
  it('sends only schema and table in the query and copies only the known fields', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { ...ok, roleId: 3, explicit: 'x' }))
    expect(await getTableAccess({ schemaName: 'SALES', tableName: 'ORDER' })).toEqual(ok)
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe('/api/me/table-access?schema=SALES&table=ORDER')
    expect(init.method).toBe('GET')
  })

  it('encodes names with special characters', () => {
    expect(tableAccessPath({ schemaName: 'A/B?C#', tableName: 'x&item=1' })).toBe(
      '/api/me/table-access?schema=A%2FB%3FC%23&table=x%26item%3D1',
    )
  })

  it('passes 403 and 400 as API errors', async () => {
    fetchMock.mockResolvedValueOnce(json(403, { status: 403, code: 'ACCESS_DENIED' }))
    await expect(getTableAccess({ schemaName: 'S', tableName: 'T' })).rejects.toMatchObject({
      kind: 'response',
      status: 403,
      code: 'ACCESS_DENIED',
    })
    fetchMock.mockResolvedValueOnce(json(400, { status: 400, code: 'VALIDATION_FAILED' }))
    await expect(getTableAccess({ schemaName: 'S', tableName: 'T' })).rejects.toMatchObject({
      status: 400,
    })
  })

  it('accepts FULL and treats broken bodies and unknown main values as network failures', async () => {
    expect(parseTableAccess({ ...ok, main: 'FULL' })?.main).toBe('FULL')
    for (const body of [
      { ...ok, main: 'NONE' },
      { ...ok, displayName: { ja: '受注' } },
      { ...ok, schemaName: 1 },
      { ...ok, displayName: null },
      null,
    ]) {
      expect(parseTableAccess(body)).toBeUndefined()
      fetchMock.mockResolvedValueOnce(json(200, body))
      await expect(getTableAccess({ schemaName: 'S', tableName: 'T' })).rejects.toEqual({
        kind: 'network',
      })
    }
  })

  it('treats a body that is not JSON as a network failure', async () => {
    fetchMock.mockResolvedValueOnce(new Response('<html>', { status: 200 }))
    await expect(getTableAccess({ schemaName: 'S', tableName: 'T' })).rejects.toEqual({
      kind: 'network',
    })
  })
})
