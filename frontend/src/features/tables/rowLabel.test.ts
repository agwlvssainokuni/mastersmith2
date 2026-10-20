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
// 行のラベルのテスト（Intent 261016-business-data-views の U3 の L6、D9・R-02、計画 Step 7、U3-NFR5.9）。
import { describe, expect, it } from 'vitest'
import { rowLabelParts, rowLabelText, rowLinkColumn, rowLinkText } from './rowLabel'
import { wireColumn } from './testing/businessDataSamples'
import { sampleDefinition } from './testing/sampleDefinition'
import { JA_TEXTS } from './testing/valueTexts'

describe('rowLabel', () => {
  it('builds the label from formatted visible text in the rowLabelColumns order', () => {
    const definition = sampleDefinition({ rowLabelColumns: ['code', 'price'] })
    const parts = rowLabelParts(definition, { code: 'A01', price: '12345' }, JA_TEXTS)
    expect(parts).toEqual(['A01', '12,345'])
    expect(rowLabelText(parts, JA_TEXTS)).toBe('A01 12,345')
    expect(rowLinkText(parts, JA_TEXTS)).toBe('A01')
  })

  it('uses the NULL, masked and binary words and turns blank parts into the empty word', () => {
    const definition = sampleDefinition({
      rowLabelColumns: ['code', 'secret', 'blob', 'name'],
      listColumns: [
        wireColumn('code'),
        wireColumn('secret', { masked: true }),
        wireColumn('blob', { valueType: 'BINARY' }),
        wireColumn('name'),
      ],
    })
    const parts = rowLabelParts(definition, { code: '   ', name: null }, JA_TEXTS)
    expect(parts).toEqual(['（空）', '••••••', '（バイナリ）', '（値なし）'])
    expect(rowLinkText(parts, JA_TEXTS)).toBe('（空）')
  })

  it('collapses white space like an accessible name', () => {
    const definition = sampleDefinition({ rowLabelColumns: ['name'] })
    const parts = rowLabelParts(definition, { name: '  青い\n ペン  ' }, JA_TEXTS)
    expect(parts).toEqual(['青い ペン'])
  })

  it('returns the empty word when there are no label columns', () => {
    expect(rowLabelText([], JA_TEXTS)).toBe('（空）')
    expect(rowLinkText([], JA_TEXTS)).toBe('（空）')
  })

  it('puts the row link on the first label column only when it is listed and detail is allowed', () => {
    expect(rowLinkColumn(sampleDefinition())).toBe('code')
    expect(rowLinkColumn(sampleDefinition({ rowLabelColumns: [] }))).toBeNull()
    expect(rowLinkColumn(sampleDefinition({ operations: ['LIST'] }))).toBeNull()
    expect(rowLinkColumn(sampleDefinition({ listColumns: [wireColumn('name')] }))).toBeNull()
  })

  it('keeps markup in values as text', () => {
    const definition = sampleDefinition({ rowLabelColumns: ['name'] })
    expect(rowLabelParts(definition, { name: '<script>x</script>' }, JA_TEXTS)).toEqual([
      '<script>x</script>',
    ])
  })
})
