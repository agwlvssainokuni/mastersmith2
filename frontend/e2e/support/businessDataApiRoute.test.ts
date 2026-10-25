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
// 業務データの API の差し替えの口の純粋な部分の単体のテスト（Intent 261016-business-data-views の U3 の L10、計画 4.8 の P-8）。
import { describe, expect, it } from 'vitest'
import { fixturePage, leafDefinition } from './businessDataFixtures'
import {
  BUSINESS_DATA_API,
  classifyBusinessDataRequest,
  isBusinessDataApiPath,
  requestedPage,
  requestedTable,
  tableResponder,
} from './businessDataApiRoute'
import { FRAME_SCHEMA, LEAF_TABLE } from './navTreeFixtures'

describe('businessDataApiRoute', () => {
  it('classifies the six business data requests by method and path only', () => {
    expect(classifyBusinessDataRequest('POST', BUSINESS_DATA_API.definition)).toBe('definition')
    expect(classifyBusinessDataRequest('POST', BUSINESS_DATA_API.search)).toBe('search')
    expect(classifyBusinessDataRequest('POST', BUSINESS_DATA_API.get)).toBe('get')
    expect(classifyBusinessDataRequest('POST', BUSINESS_DATA_API.rows)).toBe('create')
    expect(classifyBusinessDataRequest('PATCH', BUSINESS_DATA_API.rows)).toBe('update')
    expect(classifyBusinessDataRequest('POST', BUSINESS_DATA_API.delete)).toBe('delete')
    expect(classifyBusinessDataRequest('GET', BUSINESS_DATA_API.definition)).toBeNull()
    expect(classifyBusinessDataRequest('POST', '/api/tables/other')).toBeNull()
    expect(isBusinessDataApiPath('/api/tables/definition')).toBe(true)
    expect(isBusinessDataApiPath('/api/me/navigation')).toBe(false)
  })

  it('reads the table and the page from a body and falls back for broken bodies', () => {
    expect(requestedTable({ schema: 'S', table: 'T' })).toEqual({ schema: 'S', table: 'T' })
    expect(requestedTable(null)).toEqual({ schema: '', table: '' })
    expect(requestedPage({ page: 3, size: 100 })).toEqual({ page: 3, size: 100 })
    expect(requestedPage({ page: 0, size: 30 })).toEqual({ page: 1, size: 20 })
  })

  it('answers the fixture table and 403 for any other table', () => {
    const definition = leafDefinition(11)
    const respond = tableResponder(definition, (page, size) =>
      fixturePage(definition, page, size, 45),
    )
    const target = { schema: FRAME_SCHEMA, table: LEAF_TABLE }
    expect(respond('definition', target)).toEqual({ status: 200, body: JSON.stringify(definition) })
    const page = respond('search', { ...target, page: 2, size: 20 })
    expect(page !== undefined && 'body' in page ? JSON.parse(page.body ?? '').page : null).toBe(2)
    expect(respond('definition', { schema: FRAME_SCHEMA, table: 'SECRET_T' })).toMatchObject({
      status: 403,
    })
    expect(respond('get', target)).toBeUndefined()
    expect(respond('create', target)).toBeUndefined()
  })
})
