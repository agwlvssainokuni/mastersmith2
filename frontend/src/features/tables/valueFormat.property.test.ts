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
// 値の書式の性質ベースのテスト（Intent 261016-business-data-views の U3 の L6、D29、U3-NFR7.10）。
// fast-check は、失敗したときに seed と path を出力に示す。再現はその値を fc.assert の第2引数に一時的に書いて行い、
// seed を記録に残す（team.md の Testing Posture）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { formatCell } from './valueFormat'
import { JA_TEXTS } from './testing/valueTexts'

const digits = fc.stringMatching(/^[1-9][0-9]{0,40}$/)

describe('formatCell properties', () => {
  it('keeps every digit of a grouped number in order', () => {
    fc.assert(
      fc.property(
        fc.boolean(),
        digits,
        fc.option(fc.stringMatching(/^[0-9]{1,12}$/)),
        (neg, int, frac) => {
          const value = `${neg ? '-' : ''}${int}${frac === null ? '' : `.${frac}`}`
          const text = formatCell(
            { valueType: 'DECIMAL', listFormat: 'NUMBER_GROUPED', masked: false },
            value,
            JA_TEXTS,
          ).text
          expect(text.replaceAll(',', '')).toBe(value)
        },
      ),
    )
  })

  it('returns any string of a text column unchanged', () => {
    fc.assert(
      fc.property(fc.string({ unit: 'grapheme' }), (value) => {
        fc.pre(value !== '')
        expect(
          formatCell({ valueType: 'STRING', listFormat: null, masked: false }, value, JA_TEXTS),
        ).toEqual({ kind: 'text', text: value })
      }),
    )
  })
})
