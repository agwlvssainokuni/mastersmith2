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
// 他テーブル参照の E2E の見本の単体のテスト（Intent 261029-reference-lookup-cleanup の U3、計画 Step 15）。見本が画面の写しの関数を
// 通ること（型と形が画面の側と合うこと）と、値が目印を含む架空の値であることを確かめる（businessDataFixtures.test.ts と同じ形）。
import { describe, expect, it } from 'vitest'
import {
  parseReferenceRowPage,
  parseTableDefinition,
} from '../../src/features/tables/businessDataParse'
import { REFERENCE_TABLES } from './businessDataDsl'
import {
  CUSTOMER_OPTIONS,
  manyCustomerOptions,
  orderDefinition,
  productPage,
  productRow,
  type CustomerVariant,
} from './referenceFixtures'
import { BUSINESS_VALUE_MARK } from './secretValues'

const wire = (value: unknown): unknown => JSON.parse(JSON.stringify(value))
const REF = { schemaName: 'e2e_biz_x', tableName: REFERENCE_TABLES.order }

describe('referenceFixtures', () => {
  it.each<CustomerVariant>([
    'available',
    'no-permission',
    'too-many',
    'blocked-required',
    'radio-200',
  ])('passes the definition parser of the screen for the %s customer', (variant) => {
    const parsed = parseTableDefinition(wire(orderDefinition('e2e_biz_x', 3, variant)), REF)
    expect(parsed).toBeDefined()
    const customer = parsed?.detailColumns.find((column) => column.column === 'customer_code')
    expect(customer?.reference?.status).toBe(
      variant === 'too-many'
        ? 'TOO_MANY'
        : variant === 'no-permission' || variant === 'blocked-required'
          ? 'NO_PERMISSION'
          : 'AVAILABLE',
    )
    const product = parsed?.detailColumns.find((column) => column.column === 'product_code')
    expect(product?.formPart).toBe('lookup')
    expect(product?.reference?.picker?.searchFields).toHaveLength(2)
  })

  it('omits the options and picker that the server leaves out of the body', () => {
    const body = wire(orderDefinition('e2e_biz_x', 3, 'too-many')) as {
      detailColumns: { column: string; reference: Record<string, unknown> | null }[]
    }
    const customer = body.detailColumns.find((column) => column.column === 'customer_code')
    expect(customer?.reference).toEqual({ kind: 'REFERENCE', status: 'TOO_MANY' })
    const product = body.detailColumns.find((column) => column.column === 'product_code')
    expect(Object.keys(product?.reference ?? {})).not.toContain('options')
  })

  it('makes pages that pass the search parser and values with the mark', () => {
    const rows = Array.from({ length: 20 }, (_, index) => productRow(index + 1))
    expect(parseReferenceRowPage(wire(productPage(rows, 1, true)))?.rows).toHaveLength(20)
    for (const option of [...CUSTOMER_OPTIONS, ...manyCustomerOptions()]) {
      expect(option.label).toContain(BUSINESS_VALUE_MARK)
    }
    expect(manyCustomerOptions()).toHaveLength(200)
    expect(rows.every((row) => row.label?.includes(BUSINESS_VALUE_MARK))).toBe(true)
  })
})
