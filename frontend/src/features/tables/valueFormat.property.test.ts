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
// 時点を表す型の性質（Intent 261102-timezone-ui-polish の U4、計画 Step 7、D1・D3）を足した。
// fast-check は、失敗したときに seed と path を出力に示す。再現はその値を fc.assert の第2引数に一時的に書いて行い、
// seed を記録に残す（team.md の Testing Posture）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { compareInstants } from '../../shared/format/timeZone'
import { formatCell, type CellTimeZone } from './valueFormat'
import { JA_TEXTS } from './testing/valueTexts'

const digits = fc.stringMatching(/^[1-9][0-9]{0,40}$/)

const TOKYO: CellTimeZone = { status: 'ready', timeZone: 'Asia/Tokyo' }

/** 表示のタイムゾーンの候補（夏時間の地域と端数の時差を含む） */
const ZONES = ['Asia/Tokyo', 'America/New_York', 'Asia/Kathmandu', 'America/St_Johns', 'UTC']

/** 1990〜2100 年の UTC の時点の文字（端数 0・3・6 桁） */
const instantText = fc
  .tuple(
    fc.date({
      min: new Date('1990-01-01T00:00:00Z'),
      max: new Date('2100-01-01T00:00:00Z'),
      noInvalidDate: true,
    }),
    fc.constantFrom('', '123', '123456'),
  )
  .map(([date, fraction]) => {
    const base = date.toISOString().slice(0, 19)
    return `${base}${fraction === '' ? '' : `.${fraction}`}+00:00`
  })

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
            TOKYO,
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
          formatCell(
            { valueType: 'STRING', listFormat: null, masked: false },
            value,
            JA_TEXTS,
            TOKYO,
          ),
        ).toEqual({ kind: 'text', text: value })
      }),
    )
  })

  it('shows the same instant with the offset of the display time zone and the fraction as returned', () => {
    fc.assert(
      fc.property(instantText, fc.constantFrom(...ZONES), (value, name) => {
        const text = formatCell(
          { valueType: 'DATETIME_TZ', listFormat: null, masked: false },
          value,
          JA_TEXTS,
          { status: 'ready', timeZone: name },
        ).text
        const match = /^(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2})(\.\d+)? ([+-]\d{2}:\d{2})$/.exec(
          text,
        )
        expect(match).not.toBeNull()
        const [, date, time, fraction = '', offset] = match as RegExpExecArray
        // 書式の文字を時点に読み戻すと、元の時点と同じ（端数の桁も同じ）
        expect(fraction).toBe(/(\.\d+)\+00:00$/.exec(value)?.[1] ?? '')
        expect(compareInstants(`${date}T${time}${fraction}${offset}`, value)).toEqual({
          kind: 'ok',
          order: 0,
        })
      }),
    )
  })

  it('shows a value of a type without a zone the same in every display time zone', () => {
    fc.assert(
      fc.property(
        instantText.map((value) => value.slice(0, -'+00:00'.length)),
        fc.constantFrom(...ZONES),
        (value, name) => {
          const column = { valueType: 'DATETIME', listFormat: null, masked: false } as const
          expect(
            formatCell(column, value, JA_TEXTS, { status: 'ready', timeZone: name }).text,
          ).toBe(formatCell(column, value, JA_TEXTS, TOKYO).text)
        },
      ),
    )
  })
})
