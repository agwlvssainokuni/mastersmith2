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
// 自分の権限の API のテスト（Intent 261004-role-menu の U7 の L10、D3、NFR1.4・NFR6.8、計画 12節 D-3）。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { registerAuthHandlers, resetApiClient } from '../../shared/api-client/apiClient'
import {
  listMyColumns,
  listMySchemas,
  listMyTables,
  myPermissionsPath,
  parseMyPermissions,
} from './myPermissionsApi'

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

const node = {
  schemaName: 'SALES',
  tableName: 'ORDER',
  columnName: null,
  displayName: '受注',
  effective: { main: 'READ', create: false, delete: true },
  inMenu: true,
  hasChildren: false,
}

const pathOf = (index: number) => String(fetchMock.mock.calls[index][0])

describe('myPermissionsApi', () => {
  it('reads the three levels with the names in the query', async () => {
    fetchMock.mockImplementation(() =>
      Promise.resolve(json(200, { workRole: { roleId: 1, name: '営業' }, items: [] })),
    )
    await listMySchemas()
    await listMyTables('A/B?C')
    await listMyColumns('S&T', 'x=y')
    expect(pathOf(0)).toBe('/api/me/permissions/schemas')
    expect(pathOf(1)).toBe('/api/me/permissions/tables?schema=A%2FB%3FC')
    expect(pathOf(2)).toBe('/api/me/permissions/columns?schema=S%26T&table=x%3Dy')
    expect(myPermissionsPath('schemas')).toBe('/api/me/permissions/schemas')
  })

  it('copies a string display name and only the known fields', async () => {
    fetchMock.mockResolvedValueOnce(
      json(200, {
        workRole: { roleId: 1, name: '営業', builtIn: true },
        items: [{ ...node, explicit: { main: 'READ' }, inheritedFrom: 'EXPLICIT' }],
        dslId: 3,
      }),
    )
    expect(await listMySchemas()).toEqual({ workRole: { roleId: 1, name: '営業' }, items: [node] })
  })

  it('accepts a null work role and a null display name', () => {
    expect(parseMyPermissions({ workRole: null, items: [{ ...node, displayName: null }] })).toEqual(
      { workRole: null, items: [{ ...node, displayName: '' }] },
    )
  })

  it('treats broken bodies as network failures', async () => {
    for (const body of [
      { workRole: null, items: [{ ...node, displayName: { ja: '受注', en: 'Orders' } }] },
      {
        workRole: null,
        items: [{ ...node, effective: { main: 'ALL', create: true, delete: true } }],
      },
      { workRole: null, items: [{ ...node, inMenu: 'yes' }] },
      { workRole: { roleId: '1', name: 'x' }, items: [] },
      { workRole: null },
      null,
    ]) {
      expect(parseMyPermissions(body)).toBeUndefined()
      fetchMock.mockResolvedValueOnce(json(200, body))
      await expect(listMySchemas()).rejects.toEqual({ kind: 'network' })
    }
  })

  it('passes a failed response as the API error', async () => {
    fetchMock.mockResolvedValueOnce(json(400, { status: 400, code: 'VALIDATION_FAILED' }))
    await expect(listMyTables('')).rejects.toMatchObject({ kind: 'response', status: 400 })
  })
})
