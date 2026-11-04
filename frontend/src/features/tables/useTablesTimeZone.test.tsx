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
// 業務データの画面の表示のタイムゾーンの読み口のテスト（Intent 261102-timezone-ui-polish の U4、計画 Step 12、D9〜D11）。
import { renderHook } from '@testing-library/react'
import type { ReactNode } from 'react'
import { describe, expect, it } from 'vitest'
import type { DisplayTimeZoneState } from '../../app/display-settings/DisplayTimeZoneProvider'
import { DisplayTimeZoneTestProvider } from '../../app/display-settings/testing/DisplayTimeZoneTestProvider'
import { PREFERENCES_PATH } from '../preferences/registration'
import type { ColumnDefinition, PickerInfo, ReferenceField } from './businessDataTypes'
import { lookupField } from './testing/referenceSamples'
import { sampleDefinition } from './testing/sampleDefinition'
import {
  columnsNeedTimeZone,
  PREFERENCES_LINK_PATH,
  searchFieldsNeedTimeZone,
  toCellTimeZone,
  toFieldTimeZone,
  useTablesTimeZone,
} from './useTablesTimeZone'

const READY: DisplayTimeZoneState = { status: 'ready', timeZone: 'Asia/Tokyo', source: 'saved' }

function wrapperFor(state: DisplayTimeZoneState) {
  return function Wrapper({ children }: { children: ReactNode }) {
    return <DisplayTimeZoneTestProvider state={state}>{children}</DisplayTimeZoneTestProvider>
  }
}

const BASE_COLUMN = sampleDefinition().detailColumns[0]

function column(overrides: Partial<ColumnDefinition>): ColumnDefinition {
  return { ...BASE_COLUMN, ...overrides }
}

function reference(overrides: Partial<ReferenceField>): ReferenceField {
  return lookupField({ picker: null, labelValueType: null, ...overrides })
}

const PICKER: PickerInfo = {
  searchFields: [
    { column: 'name', displayName: { ja: '', en: '' }, operator: 'CONTAINS', valueType: 'STRING' },
  ],
  listColumns: [{ column: 'name', displayName: { ja: '', en: '' }, valueType: 'STRING' }],
}

describe('useTablesTimeZone', () => {
  it('waits only while loading and only when the screen needs a time zone', () => {
    const loading = wrapperFor({ status: 'loading' })
    expect(
      renderHook(() => useTablesTimeZone(true), { wrapper: loading }).result.current.waiting,
    ).toBe(true)
    expect(
      renderHook(() => useTablesTimeZone(false), { wrapper: loading }).result.current.waiting,
    ).toBe(false)
    const ready = renderHook(() => useTablesTimeZone(true), { wrapper: wrapperFor(READY) })
    expect(ready.result.current).toMatchObject({
      waiting: false,
      timeZone: 'Asia/Tokyo',
      cell: { status: 'ready', timeZone: 'Asia/Tokyo' },
      field: { status: 'ready', timeZone: 'Asia/Tokyo' },
    })
  })

  it('returns the same object while the state is the same', () => {
    const { result, rerender } = renderHook(() => useTablesTimeZone(true), {
      wrapper: wrapperFor(READY),
    })
    const first = result.current
    rerender()
    expect(result.current).toBe(first)
  })

  it('maps a failed load to an error for cells and fields', () => {
    const { result } = renderHook(() => useTablesTimeZone(true), {
      wrapper: wrapperFor({ status: 'error' }),
    })
    expect(result.current).toMatchObject({
      waiting: false,
      timeZone: null,
      cell: { status: 'error' },
      field: { status: 'error' },
    })
    expect(toCellTimeZone({ status: 'loading' })).toEqual({ status: 'error' })
    expect(toFieldTimeZone({ status: 'loading' })).toEqual({ status: 'loading' })
  })
})

describe('columnsNeedTimeZone', () => {
  it('finds instants in columns, reference labels and picker columns and conditions', () => {
    expect(columnsNeedTimeZone([column({ valueType: 'STRING' })])).toBe(false)
    expect(columnsNeedTimeZone([column({ valueType: 'DATETIME' })])).toBe(false)
    expect(columnsNeedTimeZone([column({ valueType: 'DATETIME_TZ' })])).toBe(true)
    expect(
      columnsNeedTimeZone([column({ reference: reference({ labelValueType: 'DATETIME_TZ' }) })]),
    ).toBe(true)
    expect(columnsNeedTimeZone([column({ reference: reference({ picker: PICKER }) })])).toBe(false)
    expect(
      columnsNeedTimeZone([
        column({
          reference: reference({
            picker: {
              ...PICKER,
              listColumns: [
                { column: 'at', displayName: { ja: '', en: '' }, valueType: 'DATETIME_TZ' },
              ],
            },
          }),
        }),
      ]),
    ).toBe(true)
    expect(
      columnsNeedTimeZone([
        column({
          reference: reference({
            picker: {
              ...PICKER,
              searchFields: [
                {
                  column: 'at',
                  displayName: { ja: '', en: '' },
                  operator: 'EQUALS',
                  valueType: 'DATETIME_TZ',
                },
              ],
            },
          }),
        }),
      ]),
    ).toBe(true)
  })

  it('finds instants in search fields', () => {
    const field = {
      column: 'at',
      displayName: { ja: '', en: '' },
      operator: 'RANGE' as const,
      valueType: 'DATETIME_TZ' as const,
      defaultValue: null,
      collapsed: false,
      options: [],
    }
    expect(searchFieldsNeedTimeZone([field])).toBe(true)
    expect(searchFieldsNeedTimeZone([{ ...field, valueType: 'DATE' }])).toBe(false)
  })
})

describe('PREFERENCES_LINK_PATH', () => {
  it('matches the path registered by the preferences feature', () => {
    expect(PREFERENCES_LINK_PATH).toBe(PREFERENCES_PATH)
  })
})
