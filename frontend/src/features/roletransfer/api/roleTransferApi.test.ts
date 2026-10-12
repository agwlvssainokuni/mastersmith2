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
// roleTransferApi のテスト（Intent 261004-role-menu の U6、frontend-components.md の 5節、L2・L8・L10、NFR1.5・NFR1.9・
// NFR1.10・NFR6.6、計画 13節 Q2: A）。書き出しのファイル名と上限超えのヘッダー、確かめと適用の本文の型と指紋、
// 応答の写しと誤りの一覧を確かめる。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { registerAuthHandlers, resetApiClient } from '../../../shared/api-client/apiClient'
import type { ApiResponseError } from '../../../shared/api-client/apiError'
import { applyTransfer, checkTransfer, exportRoles, transferErrors } from './roleTransferApi'

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

const change = {
  target: { schemaName: 'SALES', tableName: 'ORDER_LINE', columnName: null },
  before: { main: 'READ', create: null, delete: null },
  after: { main: null, create: null, delete: null },
  reason: 'CLEARED',
  inCurrentDsl: false,
}
const check = {
  fingerprint: 'fp-1',
  roles: [
    {
      name: '営業',
      roleId: 1,
      action: 'REPLACE',
      added: 0,
      changed: 0,
      cleared: 1,
      notInDsl: 1,
      changes: [change],
    },
    {
      name: '経理',
      roleId: null,
      action: 'CREATE',
      added: 0,
      changed: 0,
      cleared: 0,
      notInDsl: 0,
      changes: [],
    },
  ],
  untouchedRoles: ['総務'],
}

function download(headers: Record<string, string>): Response {
  return new Response('version: 1\n', {
    status: 200,
    headers: { 'Content-Type': 'application/yaml', ...headers },
  })
}

describe('roleTransferApi export', () => {
  it('uses the safe file name and reports the limit header only when it is true', async () => {
    fetchMock
      .mockResolvedValueOnce(
        download({
          'Content-Disposition': 'attachment; filename="roles.yaml"',
          'X-Role-Transfer-Exceeds-Import-Limit': 'true',
        }),
      )
      .mockResolvedValueOnce(
        download({ 'Content-Disposition': 'attachment; filename="../evil.yaml"' }),
      )
      .mockResolvedValueOnce(download({ 'X-Role-Transfer-Exceeds-Import-Limit': 'false' }))
      .mockResolvedValueOnce(download({ 'X-Role-Transfer-Exceeds-Import-Limit': 'TRUE' }))
    const first = await exportRoles()
    expect(first.fileName).toBe('roles.yaml')
    expect(first.exceedsImportLimit).toBe(true)
    expect(await first.blob.text()).toBe('version: 1\n')
    expect(call(0).path).toBe('/api/admin/role-transfer/export')
    expect(call(0).init.method).toBe('GET')
    const second = await exportRoles()
    expect(second.fileName).toBe('roles.yaml')
    expect(second.exceedsImportLimit).toBe(false)
    expect((await exportRoles()).exceedsImportLimit).toBe(false)
    expect((await exportRoles()).exceedsImportLimit).toBe(false)
  })
})

describe('roleTransferApi check and apply', () => {
  it('sends the YAML text as application/yaml and maps the check result', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { ...check, internal: 'x' }))
    const text = 'version: 1\nroles: []\n'
    expect(await checkTransfer(text)).toEqual(check)
    expect(call().path).toBe('/api/admin/role-transfer/check')
    expect(call().init.method).toBe('POST')
    expect(call().init.body).toBe(text)
    expect(call().headers.get('Content-Type')).toBe('application/yaml')
    expect(call().headers.get('X-Role-Transfer-Fingerprint')).toBeNull()
  })

  it('sends the same text and the fingerprint header when applying', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { created: 1, replaced: 2, extra: 0 }))
    expect(await applyTransfer('roles: []\n', 'fp-1')).toEqual({ created: 1, replaced: 2 })
    expect(call().path).toBe('/api/admin/role-transfer/apply')
    expect(call().init.body).toBe('roles: []\n')
    expect(call().headers.get('Content-Type')).toBe('application/yaml')
    expect(call().headers.get('X-Role-Transfer-Fingerprint')).toBe('fp-1')
  })

  it('rejects wrong shapes of the check and the result as a network error', async () => {
    fetchMock
      .mockResolvedValueOnce(
        json(200, { ...check, roles: [{ ...check.roles[0], action: 'DELETE' }] }),
      )
      .mockResolvedValueOnce(
        json(200, {
          ...check,
          roles: [{ ...check.roles[0], changes: [{ ...change, reason: 'MOVED' }] }],
        }),
      )
      .mockResolvedValueOnce(json(200, { ...check, fingerprint: 1 }))
      .mockResolvedValueOnce(json(200, { created: '1', replaced: 0 }))
    await expect(checkTransfer('a')).rejects.toEqual(NETWORK)
    await expect(checkTransfer('a')).rejects.toEqual(NETWORK)
    await expect(checkTransfer('a')).rejects.toEqual(NETWORK)
    await expect(applyTransfer('a', 'fp')).rejects.toEqual(NETWORK)
  })
})

describe('roleTransferApi error list', () => {
  function invalid(problem: Readonly<Record<string, unknown>>): ApiResponseError {
    const error: ApiResponseError = { kind: 'response', status: 400, code: 'ROLE_TRANSFER_INVALID' }
    Object.defineProperty(error, 'problem', { value: problem, enumerable: false })
    return error
  }

  it('reads the errors with nullable line, column and path and drops other fields', () => {
    expect(
      transferErrors(
        invalid({
          errors: [
            { reason: 'UNKNOWN_KEY', line: 3, column: 5, path: 'roles[0].extra', message: 'x' },
            { reason: 'SYNTAX', line: null, column: null, path: null },
          ],
        }),
      ),
    ).toEqual([
      { reason: 'UNKNOWN_KEY', line: 3, column: 5, path: 'roles[0].extra' },
      { reason: 'SYNTAX', line: null, column: null, path: null },
    ])
  })

  it('gives an empty list for a missing or broken list', () => {
    expect(transferErrors(invalid({}))).toEqual([])
    expect(transferErrors(invalid({ errors: [{ reason: 1 }] }))).toEqual([])
    expect(transferErrors({ kind: 'network' })).toEqual([])
    expect(transferErrors(undefined)).toEqual([])
  })
})
