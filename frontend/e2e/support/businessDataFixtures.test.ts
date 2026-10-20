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
// 業務データの見本の単体のテスト（Intent 261016-business-data-views の U3 の L10、計画 4.8 の P-8）。見本が画面の写しの関数を
// 通ること（型と形が画面の側と合うこと）と、値が目印を含む架空の値であることを確かめる。
import { describe, expect, it } from 'vitest'
import {
  parseRowDetail,
  parseRowPage,
  parseTableDefinition,
} from '../../src/features/tables/businessDataParse'
import { parseNavigation } from '../../src/app/navigation/navigationApi'
import {
  fixturePage,
  leafDefinition,
  LONG_VALUE_LENGTH,
  longDetail,
  WIDE_SCHEMA,
  WIDE_TABLE,
  wideDefinition,
  wideNavigation,
} from './businessDataFixtures'
import { FRAME_SCHEMA, LEAF_TABLE } from './navTreeFixtures'
import { BUSINESS_VALUE_MARK } from './secretValues'

const wire = (value: unknown): unknown => JSON.parse(JSON.stringify(value))

describe('businessDataFixtures', () => {
  it('passes the definition parser of the screen unchanged', () => {
    const leaf = leafDefinition(11)
    expect(
      parseTableDefinition(wire(leaf), { schemaName: FRAME_SCHEMA, tableName: LEAF_TABLE }),
    ).toEqual(leaf)
    const wide = wideDefinition(100, 11)
    expect(wide.listColumns).toHaveLength(100)
    expect(
      parseTableDefinition(wire(wide), { schemaName: WIDE_SCHEMA, tableName: WIDE_TABLE }),
    ).toEqual(wide)
  })

  it('passes the page and detail parsers and keeps NULL rows', () => {
    const wide = wideDefinition(100, 11)
    const page = fixturePage(wide, 1, 100, 250)
    expect(page.items).toHaveLength(100)
    expect(parseRowPage(wire(page))).toEqual(page)
    expect(page.items.some((item) => Object.values(item.values).includes(null))).toBe(true)
    expect(fixturePage(wide, 3, 100, 250).items).toHaveLength(50)
    const detail = longDetail(wide, 7)
    expect(parseRowDetail(wire(detail))).toEqual(detail)
    expect(Object.values(detail.values).some((value) => value?.length === LONG_VALUE_LENGTH)).toBe(
      true,
    )
  })

  it('makes every key and text value carry the business value mark', () => {
    const page = fixturePage(leafDefinition(11), 2, 20, 45)
    for (const item of page.items) {
      expect(item.key?.code).toContain(BUSINESS_VALUE_MARK)
      for (const [column, value] of Object.entries(item.values)) {
        if (value !== null && column !== 'price') {
          expect(value).toContain(BUSINESS_VALUE_MARK)
        }
      }
    }
  })

  it('builds a navigation that points to the wide table', () => {
    const navigation = parseNavigation(wire(wideNavigation()))
    expect(navigation?.items[0]?.table).toEqual({ schemaName: WIDE_SCHEMA, tableName: WIDE_TABLE })
  })
})
