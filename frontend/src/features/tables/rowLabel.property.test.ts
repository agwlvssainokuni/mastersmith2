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
// 行のラベルの性質ベースのテスト（Intent 261016-business-data-views の U3 の L6、D9・D29、U3-NFR7.10、WCAG 2.5.3）。
// 任意の値（NULL・空の文字列・password・BINARY・真偽値・数、先頭のカラムが空の文字列を含む）で、行のリンクの見える文字は
// 「詳細: <表示名> <行のラベル>」と rowActionsName(見える文字, 行のラベル) に必ず含まれる。
// fast-check は、失敗したときに seed と path を出力に示す。再現はその値を fc.assert の第2引数に一時的に書いて行い、
// seed を記録に残す（team.md の Testing Posture）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { rowActionsName } from '../../shared/row-actions/rowActionsName'
import { rowLabelParts, rowLabelText, rowLinkText } from './rowLabel'
import { wireColumn } from './testing/businessDataSamples'
import { sampleDefinition } from './testing/sampleDefinition'
import { JA_TEXTS } from './testing/valueTexts'

const columnKind = fc.constantFrom(
  { valueType: 'STRING', listFormat: null, masked: false },
  { valueType: 'STRING', listFormat: null, masked: true },
  { valueType: 'BINARY', listFormat: null, masked: false },
  { valueType: 'BOOLEAN', listFormat: 'BOOLEAN_ON_OFF', masked: false },
  { valueType: 'DECIMAL', listFormat: 'NUMBER_GROUPED', masked: false },
  { valueType: 'DATETIME_TZ', listFormat: null, masked: false },
)

const cellValue = fc.oneof(
  fc.constant(null),
  fc.constant(''),
  fc.constantFrom(' ', '\t\n', '　'),
  fc.string(),
  fc.stringMatching(/^-?[0-9]{1,30}(\.[0-9]{1,5})?$/),
  fc.constantFrom('true', 'false'),
)

describe('rowLabel properties', () => {
  it('always contains the visible link text in the link name and the row actions name', () => {
    fc.assert(
      fc.property(
        fc.array(fc.tuple(columnKind, cellValue), { minLength: 1, maxLength: 4 }),
        fc.string({ minLength: 1 }),
        (columns, tableName) => {
          const names = columns.map((_, index) => `c${index}`)
          const definition = sampleDefinition({
            rowLabelColumns: names,
            listColumns: columns.map(([kind], index) => wireColumn(names[index], kind)),
          })
          const values = Object.fromEntries(
            columns.map(([, value], index) => [names[index], value]),
          )
          const parts = rowLabelParts(definition, values, JA_TEXTS)
          const label = rowLabelText(parts, JA_TEXTS)
          const link = rowLinkText(parts, JA_TEXTS)
          expect(link.trim()).not.toBe('')
          expect(`詳細: ${tableName} ${label}`).toContain(link)
          expect(rowActionsName('操作', label)).toContain(link)
        },
      ),
    )
  })
})
