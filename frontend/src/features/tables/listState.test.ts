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
// 一覧の状態の既定と直しのテスト（Intent 261016-business-data-views の U3 の L6、D3・F1 の 3・F6 の 2、AC1.2.7）。
import { describe, expect, it } from 'vitest'
import { defaultListState, sanitizeListState } from './listState'
import { EMPTY_SEARCH_INPUT } from './searchConditions'
import { wireSearchField } from './testing/businessDataSamples'
import { sampleDefinition } from './testing/sampleDefinition'

describe('listState', () => {
  it('starts with the default search, the server order, page 1 and 20 rows', () => {
    const state = defaultListState(
      sampleDefinition({ searchFields: [wireSearchField('code', { defaultValue: 'A' })] }),
    )
    expect(state).toEqual({
      search: { code: { ...EMPTY_SEARCH_INPUT, value: 'A' } },
      sort: null,
      page: 1,
      size: 20,
      searchExpanded: true,
    })
  })

  it('starts with the search closed only when every field is collapsed', () => {
    expect(
      defaultListState(
        sampleDefinition({ searchFields: [wireSearchField('code', { collapsed: true })] }),
      ).searchExpanded,
    ).toBe(false)
    expect(
      defaultListState(
        sampleDefinition({
          searchFields: [
            wireSearchField('code', { collapsed: true }),
            wireSearchField('name', { collapsed: false }),
          ],
        }),
      ).searchExpanded,
    ).toBe(true)
    expect(defaultListState(sampleDefinition({ searchFields: [] })).searchExpanded).toBe(true)
  })

  it('drops removed search inputs and an unusable sort and goes back to page 1', () => {
    const definition = sampleDefinition({
      searchFields: [wireSearchField('code')],
      sortableColumns: ['code'],
    })
    const state = {
      search: {
        code: { ...EMPTY_SEARCH_INPUT, value: 'A' },
        gone: { ...EMPTY_SEARCH_INPUT, value: 'x' },
      },
      sort: { column: 'name', direction: 'ASC' as const },
      page: 4,
      size: 50 as const,
      searchExpanded: false,
    }
    expect(sanitizeListState(state, definition)).toEqual({
      search: { code: { ...EMPTY_SEARCH_INPUT, value: 'A' } },
      sort: null,
      page: 1,
      size: 50,
      searchExpanded: false,
    })
  })

  it('returns the same state when nothing has to change', () => {
    const definition = sampleDefinition()
    const state = {
      ...defaultListState(definition),
      page: 3,
      sort: { column: 'code', direction: 'DESC' as const },
    }
    expect(sanitizeListState(state, definition)).toBe(state)
  })
})
