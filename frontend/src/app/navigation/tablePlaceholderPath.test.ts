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
// 置き場の道の純粋な関数のテスト（Intent 261004-role-menu の U7 の D20、L6、NFR1.6・NFR1.7）。
// Intent 261016-business-data-views の U3 の D1・L9 で、業務データの4つの道と isTablePath のテストを足した。
// 性質ベースのテスト（fast-check）は、失敗したときに seed と path を出力に示す。再現はその値を fc.assert の第2引数に
// 一時的に書いて行う。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import {
  TABLE_PATHS,
  isTablePath,
  readTablePlaceholderParams,
  toTablePath,
  toTablePlaceholderPath,
  type TableScreen,
} from './tablePlaceholderPath'

const SCRIPT_URL = ['javascript', 'alert(1)'].join(':')

const tricky = [
  'A/B',
  '../..',
  'a?b',
  'a#b',
  '100%',
  '%2F',
  'a b',
  'a&b=c',
  'x=y',
  SCRIPT_URL,
  'https://example.com/',
  '//example.com',
  '受注明細',
]

describe('toTablePlaceholderPath', () => {
  it('builds the path with schema, table and item in the query', () => {
    expect(toTablePlaceholderPath({ schemaName: 'SALES', tableName: 'ORDER' }, '0.1')).toBe(
      '/tables?schema=SALES&table=ORDER&item=0.1',
    )
  })

  it('omits the item when it is missing or empty', () => {
    const ref = { schemaName: 'S', tableName: 'T' }
    expect(toTablePlaceholderPath(ref)).toBe('/tables?schema=S&table=T')
    expect(toTablePlaceholderPath(ref, null)).toBe('/tables?schema=S&table=T')
    expect(toTablePlaceholderPath(ref, '')).toBe('/tables?schema=S&table=T')
  })

  it('always starts with /tables? and reads back the same names for tricky names', () => {
    for (const name of tricky) {
      const path = toTablePlaceholderPath({ schemaName: name, tableName: name }, name)
      expect(path.startsWith('/tables?')).toBe(true)
      expect(path).not.toContain('#')
      const params = readTablePlaceholderParams(path.slice('/tables'.length))
      expect(params).toEqual({ ref: { schemaName: name, tableName: name }, itemId: name })
    }
  })

  it('round-trips any non-empty names (property)', () => {
    fc.assert(
      fc.property(
        fc.string({ minLength: 1 }),
        fc.string({ minLength: 1 }),
        fc.option(fc.string({ minLength: 1 }), { nil: null }),
        (schemaName, tableName, itemId) => {
          const path = toTablePlaceholderPath({ schemaName, tableName }, itemId)
          const url = new URL(path, 'https://app.example.com')
          expect(url.origin).toBe('https://app.example.com')
          expect(url.pathname).toBe('/tables')
          expect(readTablePlaceholderParams(url.search)).toEqual({
            ref: { schemaName, tableName },
            itemId,
          })
        },
      ),
    )
  })
})

describe('readTablePlaceholderParams', () => {
  it('reads the names as they are, with or without the leading question mark', () => {
    expect(readTablePlaceholderParams('?schema=S&table=T&item=1.2')).toEqual({
      ref: { schemaName: 'S', tableName: 'T' },
      itemId: '1.2',
    })
    expect(readTablePlaceholderParams('schema=%20S&table=T')).toEqual({
      ref: { schemaName: ' S', tableName: 'T' },
      itemId: null,
    })
  })

  it('returns no table when schema or table is missing or empty', () => {
    for (const search of ['', '?schema=S', '?table=T', '?schema=&table=T', '?schema=S&table=']) {
      expect(readTablePlaceholderParams(search).ref).toBeNull()
    }
    expect(readTablePlaceholderParams('?schema=S&table=T&item=').itemId).toBeNull()
  })
})

describe('toTablePath', () => {
  const screens: TableScreen[] = ['list', 'row', 'new', 'edit']

  it('builds the four business data paths with the same query', () => {
    const ref = { schemaName: 'SALES', tableName: 'ORDER' }
    expect(screens.map((screen) => toTablePath(screen, ref, '0.1'))).toEqual([
      '/tables?schema=SALES&table=ORDER&item=0.1',
      '/tables/row?schema=SALES&table=ORDER&item=0.1',
      '/tables/new?schema=SALES&table=ORDER&item=0.1',
      '/tables/edit?schema=SALES&table=ORDER&item=0.1',
    ])
    expect(toTablePath('list', ref)).toBe(toTablePlaceholderPath(ref))
  })

  it('keeps every path inside the app and reads back the names (property)', () => {
    fc.assert(
      fc.property(
        fc.constantFrom(...screens),
        fc.string({ minLength: 1 }),
        fc.string({ minLength: 1 }),
        fc.option(fc.string({ minLength: 1 }), { nil: null }),
        (screen, schemaName, tableName, itemId) => {
          const path = toTablePath(screen, { schemaName, tableName }, itemId)
          const url = new URL(path, 'https://app.example.com')
          expect(url.origin).toBe('https://app.example.com')
          expect(url.pathname).toBe(TABLE_PATHS[screen])
          expect(isTablePath(url.pathname)).toBe(true)
          expect(readTablePlaceholderParams(url.search)).toEqual({
            ref: { schemaName, tableName },
            itemId,
          })
        },
      ),
    )
  })
})

describe('isTablePath', () => {
  it('accepts only the four business data paths exactly', () => {
    for (const path of ['/tables', '/tables/row', '/tables/new', '/tables/edit']) {
      expect(isTablePath(path)).toBe(true)
    }
    for (const path of ['/tables/', '/tables/rows', '/tablesx', '/TABLES', '/', '/tables/row/1']) {
      expect(isTablePath(path)).toBe(false)
    }
  })
})
