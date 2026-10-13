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
// 業務のメニューの API の関数のテスト（Intent 261004-role-menu の U7 の L2、D3、NFR1.4）。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { registerAuthHandlers, resetApiClient } from '../../shared/api-client/apiClient'
import { getNavigation, NAVIGATION_API, parseNavigation } from './navigationApi'

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

const leafNode = {
  id: '0.1',
  label: { ja: '受注', en: 'Orders' },
  icon: 'list',
  table: { schemaName: 'SALES', tableName: 'ORDER' },
  navigable: true,
  items: [],
}

describe('navigationApi', () => {
  it('reads the menu without parameters and copies only the known fields', async () => {
    fetchMock.mockResolvedValueOnce(
      json(200, {
        items: [
          {
            id: '0',
            label: { ja: '販売', en: 'Sales', fr: 'x' },
            icon: 'home',
            table: null,
            navigable: false,
            roleId: 3,
            items: [{ ...leafNode, table: { ...leafNode.table, url: 'jdbc:x' } }],
          },
        ],
        emptyReason: null,
        dslId: 4,
      }),
    )
    expect(await getNavigation()).toEqual({
      items: [
        {
          id: '0',
          label: { ja: '販売', en: 'Sales' },
          icon: 'home',
          table: null,
          navigable: false,
          items: [leafNode],
        },
      ],
      emptyReason: null,
    })
    expect(call().path).toBe(NAVIGATION_API)
    expect(call().path).toBe('/api/me/navigation')
    expect(call().init.method).toBe('GET')
  })

  it('reads an empty menu with each empty reason', async () => {
    for (const emptyReason of ['NOT_CONFIGURED', 'NOTHING_VISIBLE']) {
      fetchMock.mockResolvedValueOnce(json(200, { items: [], emptyReason }))
      expect(await getNavigation()).toEqual({ items: [], emptyReason })
    }
  })

  it('reads a null icon as an empty name for the screen to replace', () => {
    expect(
      parseNavigation({ items: [{ ...leafNode, icon: null }], emptyReason: null })?.items[0].icon,
    ).toBe('')
  })

  it('treats an unknown empty reason and broken nodes as a network failure', async () => {
    const broken = [
      { items: [], emptyReason: 'SOMETHING_ELSE' },
      { items: [], emptyReason: 1 },
      { items: 'x', emptyReason: null },
      { items: [{ ...leafNode, id: 1 }], emptyReason: null },
      { items: [{ ...leafNode, label: { ja: 'x' } }], emptyReason: null },
      { items: [{ ...leafNode, table: { schemaName: 'S' } }], emptyReason: null },
      { items: [{ ...leafNode, navigable: 'yes' }], emptyReason: null },
      { items: [{ ...leafNode, icon: 3 }], emptyReason: null },
      { items: [{ ...leafNode, items: [{ id: 'x' }] }], emptyReason: null },
      { items: [] },
      null,
    ]
    for (const body of broken) {
      expect(parseNavigation(body)).toBeUndefined()
      fetchMock.mockResolvedValueOnce(json(200, body))
      await expect(getNavigation()).rejects.toEqual(NETWORK)
    }
  })

  it('passes a failed response as the API error', async () => {
    fetchMock.mockResolvedValueOnce(json(500, { status: 500, code: 'INTERNAL_ERROR' }))
    await expect(getNavigation()).rejects.toMatchObject({ kind: 'response', status: 500 })
    fetchMock.mockRejectedValueOnce(new TypeError('offline'))
    await expect(getNavigation()).rejects.toEqual(NETWORK)
  })
})
