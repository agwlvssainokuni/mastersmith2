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
// 写しの関数の性質ベースのテスト（Intent 261016-business-data-views の U3 の L2、D29、U3-NFR4.24・7.10）。
// fast-check は、失敗したときに seed と path を出力に示す。再現はその値を fc.assert の第2引数に一時的に書いて行い、
// seed を記録に残す（team.md の Testing Posture）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { parseRowDetail, parseRowPage, parseTableDefinition } from './businessDataParse'
import { SAMPLE_REF, wireDetail, wirePage, wireRow } from './testing/businessDataSamples'

/** 任意の JSON の値 */
const anyJson = fc.jsonValue()

/** 桁の多い整数・小数の文字（BIGINT・DECIMAL） */
const bigDecimalText = fc
  .tuple(
    fc.boolean(),
    fc.stringMatching(/^[1-9][0-9]{0,37}$/),
    fc.option(fc.stringMatching(/^[0-9]{1,10}$/), { nil: undefined }),
  )
  .map(
    ([negative, digits, fraction]) =>
      `${negative ? '-' : ''}${digits}${fraction === undefined ? '' : `.${fraction}`}`,
  )

describe('businessDataParse properties', () => {
  it('never throws on any JSON and returns undefined or an object', () => {
    fc.assert(
      fc.property(anyJson, (body) => {
        for (const result of [
          parseTableDefinition(body, SAMPLE_REF),
          parseRowPage(body),
          parseRowDetail(body),
        ]) {
          expect(result === undefined || typeof result === 'object').toBe(true)
        }
      }),
    )
  })

  it('never throws when any field of a valid page is replaced with any JSON', () => {
    const fields = ['definitionId', 'definitionChanged', 'items', 'page', 'size', 'total']
    fc.assert(
      fc.property(fc.constantFrom(...fields), anyJson, (field, value) => {
        const body = wirePage([wireRow('A01')], { [field]: value })
        const result = parseRowPage(body)
        expect(result === undefined || Array.isArray(result.items)).toBe(true)
      }),
    )
  })

  it('keeps the digits of BIGINT and DECIMAL values exactly', () => {
    fc.assert(
      fc.property(bigDecimalText, bigDecimalText, (price, code) => {
        const page = parseRowPage(wirePage([wireRow(code, { values: { code, price } })]))
        expect(page?.items[0]?.values.price).toBe(price)
        expect(page?.items[0]?.key?.code).toBe(code)
        const detail = parseRowDetail(wireDetail(code, { values: { price } }))
        expect(detail?.values.price).toBe(price)
      }),
    )
  })

  it('keeps any string value and any column name as is', () => {
    fc.assert(
      fc.property(fc.string(), fc.string(), (column, value) => {
        const detail = parseRowDetail(wireDetail('A01', { values: { [column]: value } }))
        expect(detail).toBeDefined()
        expect(Object.hasOwn(detail?.values ?? {}, column)).toBe(true)
        expect(detail?.values[column]).toBe(value)
      }),
    )
  })
})
