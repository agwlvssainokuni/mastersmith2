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
// 業務データの読み取りと書き込みの API のテスト（Intent 261016-business-data-views の U3 の L1、計画 Step 5・27、U3-NFR1.9・4.23・4.24）。
// fetch を差し替えて ApiClient（apiRequest）を通す。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { registerAuthHandlers, resetApiClient } from '../../shared/api-client/apiClient'
import { utf8ByteLength } from '../../shared/validation/codePoints'
import {
  BUSINESS_DATA_PATHS,
  createRow,
  deleteRow,
  getDefinition,
  getRow,
  postWithinLimit,
  searchRows,
  updateRow,
} from './businessDataApi'
import { MAX_READ_BODY_BYTES, MAX_WRITE_BODY_BYTES } from './businessDataLimits'
import {
  SAMPLE_DEFINITION_ID,
  SAMPLE_REF,
  jsonResponse,
  wireDefinition,
  wireDetail,
  wirePage,
  wireRow,
} from './testing/businessDataSamples'

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

function lastCall(): { path: string; init: RequestInit } {
  const [path, init] = fetchMock.mock.calls.at(-1) as [string, RequestInit]
  return { path, init }
}

/** 本文の JSON の長さがちょうど bytes になる値を作る（payload の外の部分の長さを引く） */
function valueForBodyBytes(bytes: number, unit: string): string {
  const shell = utf8ByteLength(JSON.stringify({ v: '' }))
  const unitBytes = utf8ByteLength(JSON.stringify(unit)) - 2
  const count = Math.floor((bytes - shell) / unitBytes)
  const rest = bytes - shell - count * unitBytes
  return unit.repeat(count) + 'a'.repeat(rest)
}

describe('businessDataApi', () => {
  it('sends the table names only in the body of the definition request', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, wireDefinition()))
    const definition = await getDefinition(SAMPLE_REF)
    expect(definition.displayName.ja).toBe('商品')
    const { path, init } = lastCall()
    expect(path).toBe(BUSINESS_DATA_PATHS.definition)
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual({ schema: 'shop', table: 'item' })
    expect(new Headers(init.headers).get('Content-Type')).toBe('application/json')
  })

  it('sends the definition id, conditions, sort, page and size in the search body', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, wirePage([wireRow('A01')])))
    const page = await searchRows({
      ref: SAMPLE_REF,
      definitionId: SAMPLE_DEFINITION_ID,
      conditions: [{ column: 'name', value: '%_' }],
      sort: { column: 'code', direction: 'DESC' },
      page: 2,
      size: 50,
    })
    expect(page.items).toHaveLength(1)
    const { path, init } = lastCall()
    expect(path).toBe(BUSINESS_DATA_PATHS.search)
    expect(JSON.parse(String(init.body))).toEqual({
      schema: 'shop',
      table: 'item',
      definitionId: SAMPLE_DEFINITION_ID,
      conditions: [{ column: 'name', value: '%_' }],
      sort: { column: 'code', direction: 'DESC' },
      page: 2,
      size: 50,
    })
  })

  it('omits the sort when the default order is used and sends the key for a row', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, wirePage([])))
    await searchRows({
      ref: SAMPLE_REF,
      definitionId: SAMPLE_DEFINITION_ID,
      conditions: [],
      sort: null,
      page: 1,
      size: 20,
    })
    expect(JSON.parse(String(lastCall().init.body))).not.toHaveProperty('sort')

    fetchMock.mockResolvedValueOnce(jsonResponse(200, wireDetail('A/1?#')))
    await getRow(SAMPLE_REF, SAMPLE_DEFINITION_ID, { code: 'A/1?#' })
    const { path, init } = lastCall()
    expect(path).toBe(BUSINESS_DATA_PATHS.get)
    expect(JSON.parse(String(init.body)).key).toEqual({ code: 'A/1?#' })
  })

  it.each([
    ['ASCII', 'a'],
    ['a surrogate pair', '𠮷'],
    ['an unpaired surrogate', '\ud800'],
    ['a control character', '\u0001'],
  ])('sends a body of exactly the limit and refuses one byte more (%s)', async (_, unit) => {
    fetchMock.mockImplementation(() => Promise.resolve(jsonResponse(200, { ok: true })))
    const exact = valueForBodyBytes(MAX_READ_BODY_BYTES, unit)
    expect(utf8ByteLength(JSON.stringify({ v: exact }))).toBe(MAX_READ_BODY_BYTES)
    await expect(postWithinLimit('/api/x', { v: exact }, MAX_READ_BODY_BYTES)).resolves.toEqual({
      ok: true,
    })
    const calls = fetchMock.mock.calls.length
    await expect(
      postWithinLimit('/api/x', { v: `${exact}a` }, MAX_READ_BODY_BYTES),
    ).rejects.toEqual({ kind: 'response', status: 413, code: 'PAYLOAD_TOO_LARGE' })
    expect(fetchMock.mock.calls.length).toBe(calls)
  })

  it('passes server errors as API errors and does not retry', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(403, { status: 403, code: 'ACCESS_DENIED' }))
    await expect(getDefinition(SAMPLE_REF)).rejects.toMatchObject({
      kind: 'response',
      status: 403,
      code: 'ACCESS_DENIED',
    })
    fetchMock.mockResolvedValueOnce(
      jsonResponse(503, { status: 503, code: 'BUSINESS_TARGET_DB_UNAVAILABLE' }),
    )
    await expect(getDefinition(SAMPLE_REF)).rejects.toMatchObject({ status: 503 })
    expect(fetchMock).toHaveBeenCalledTimes(2)
  })

  it('treats broken bodies, other tables and non JSON as network failures', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, wireDefinition({ table: 'other' })))
    await expect(getDefinition(SAMPLE_REF)).rejects.toEqual({ kind: 'network' })
    fetchMock.mockResolvedValueOnce(jsonResponse(200, wirePage([], { size: 7 })))
    await expect(
      searchRows({
        ref: SAMPLE_REF,
        definitionId: SAMPLE_DEFINITION_ID,
        conditions: [],
        sort: null,
        page: 1,
        size: 20,
      }),
    ).rejects.toEqual({ kind: 'network' })
    fetchMock.mockResolvedValueOnce(new Response('<html>', { status: 200 }))
    await expect(getRow(SAMPLE_REF, SAMPLE_DEFINITION_ID, { code: 'A' })).rejects.toEqual({
      kind: 'network',
    })
    fetchMock.mockRejectedValueOnce(new TypeError('offline'))
    await expect(getDefinition(SAMPLE_REF)).rejects.toEqual({ kind: 'network' })
  })
})

describe('businessDataApi writes', () => {
  it('creates a row with the definition id and the changed values in the body', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(201, { definitionId: SAMPLE_DEFINITION_ID, key: { code: 'N/1' } }),
    )
    const outcome = await createRow(SAMPLE_REF, SAMPLE_DEFINITION_ID, { code: 'N/1', note: null })
    expect(outcome).toEqual({ kind: 'saved', key: { code: 'N/1' } })
    const { path, init } = lastCall()
    expect(path).toBe(BUSINESS_DATA_PATHS.rows)
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual({
      schema: 'shop',
      table: 'item',
      definitionId: SAMPLE_DEFINITION_ID,
      values: { code: 'N/1', note: null },
    })
  })

  it('updates with PATCH and deletes with POST to the delete path', async () => {
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { definitionId: SAMPLE_DEFINITION_ID }))
    await expect(
      updateRow(SAMPLE_REF, SAMPLE_DEFINITION_ID, { code: 'A' }, { name: '' }),
    ).resolves.toEqual({ kind: 'saved', key: null })
    expect(lastCall().init.method).toBe('PATCH')
    expect(lastCall().path).toBe(BUSINESS_DATA_PATHS.rows)
    expect(JSON.parse(String(lastCall().init.body))).toMatchObject({
      key: { code: 'A' },
      values: { name: '' },
    })
    fetchMock.mockResolvedValueOnce(jsonResponse(200, { definitionId: SAMPLE_DEFINITION_ID }))
    await expect(deleteRow(SAMPLE_REF, SAMPLE_DEFINITION_ID, { code: 'A' })).resolves.toEqual({
      kind: 'saved',
      key: null,
    })
    expect(lastCall().path).toBe(BUSINESS_DATA_PATHS.delete)
    expect(lastCall().init.method).toBe('POST')
  })

  it('splits failures into not saved and unknown without throwing', async () => {
    fetchMock.mockResolvedValueOnce(
      jsonResponse(409, { status: 409, code: 'BUSINESS_SAVE_REJECTED' }),
    )
    await expect(createRow(SAMPLE_REF, SAMPLE_DEFINITION_ID, {})).resolves.toMatchObject({
      kind: 'not-saved',
      problem: { status: 409, code: 'BUSINESS_SAVE_REJECTED' },
    })
    fetchMock.mockResolvedValueOnce(
      jsonResponse(503, { status: 503, code: 'BUSINESS_TARGET_DB_TIMEOUT' }),
    )
    await expect(createRow(SAMPLE_REF, SAMPLE_DEFINITION_ID, {})).resolves.toEqual({
      kind: 'unknown',
    })
    fetchMock.mockResolvedValueOnce(new Response('<html>', { status: 502 }))
    await expect(updateRow(SAMPLE_REF, SAMPLE_DEFINITION_ID, { code: 'A' }, {})).resolves.toEqual({
      kind: 'unknown',
    })
    fetchMock.mockRejectedValueOnce(new TypeError('offline'))
    await expect(deleteRow(SAMPLE_REF, SAMPLE_DEFINITION_ID, { code: 'A' })).resolves.toEqual({
      kind: 'unknown',
    })
    fetchMock.mockResolvedValueOnce(
      new Response('<html>login</html>', { status: 201, headers: { 'Content-Type': 'text/html' } }),
    )
    await expect(createRow(SAMPLE_REF, SAMPLE_DEFINITION_ID, {})).resolves.toEqual({
      kind: 'unknown',
    })
  })

  it('does not send a write body over its limit and reports it as not saved', async () => {
    fetchMock.mockImplementation(() =>
      Promise.resolve(jsonResponse(201, { definitionId: SAMPLE_DEFINITION_ID })),
    )
    const shell = utf8ByteLength(
      JSON.stringify({
        schema: 'shop',
        table: 'item',
        definitionId: SAMPLE_DEFINITION_ID,
        values: { v: '' },
      }),
    )
    const exact = 'a'.repeat(MAX_WRITE_BODY_BYTES - shell)
    await expect(createRow(SAMPLE_REF, SAMPLE_DEFINITION_ID, { v: exact })).resolves.toEqual({
      kind: 'saved',
      key: null,
    })
    const calls = fetchMock.mock.calls.length
    await expect(
      createRow(SAMPLE_REF, SAMPLE_DEFINITION_ID, { v: `${exact}𠮷` }),
    ).resolves.toMatchObject({
      kind: 'not-saved',
      problem: { status: 413, code: 'PAYLOAD_TOO_LARGE' },
    })
    expect(fetchMock.mock.calls.length).toBe(calls)
  })
})
