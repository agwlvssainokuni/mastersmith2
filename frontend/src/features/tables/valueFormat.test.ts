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
// 値の書式のテスト（Intent 261016-business-data-views の U3 の L6、D15、計画 Step 7、AC1.5.2〜AC1.5.8）。
import { describe, expect, it } from 'vitest'
import type { FormatColumn } from './valueFormat'
import { MASKED_TEXT, formatCell, groupDigits } from './valueFormat'
import { JA_TEXTS } from './testing/valueTexts'

const SCRIPT_URL = ['javascript', 'alert(1)'].join(':')

const STRING: FormatColumn = { valueType: 'STRING', listFormat: null, masked: false }

describe('formatCell', () => {
  it('shows NULL, an empty string, masked and binary values distinctly', () => {
    expect(formatCell(STRING, null, JA_TEXTS)).toEqual({ kind: 'null', text: '（値なし）' })
    expect(formatCell(STRING, undefined, JA_TEXTS)).toEqual({ kind: 'null', text: '（値なし）' })
    expect(formatCell(STRING, '', JA_TEXTS)).toEqual({ kind: 'text', text: '' })
    expect(formatCell({ ...STRING, masked: true }, undefined, JA_TEXTS)).toEqual({
      kind: 'masked',
      text: MASKED_TEXT,
    })
    expect(formatCell({ ...STRING, valueType: 'BINARY' }, null, JA_TEXTS)).toEqual({
      kind: 'binary',
      text: '（バイナリ）',
    })
  })

  it('groups digits as text without losing digits', () => {
    const column: FormatColumn = {
      valueType: 'DECIMAL',
      listFormat: 'NUMBER_GROUPED',
      masked: false,
    }
    expect(formatCell(column, '-1234.5', JA_TEXTS).text).toBe('-1,234.5')
    expect(formatCell(column, '12345678901234567890.1234567890', JA_TEXTS).text).toBe(
      '12,345,678,901,234,567,890.1234567890',
    )
    expect(formatCell(column, '123', JA_TEXTS).text).toBe('123')
    expect(groupDigits('abc')).toBe('abc')
  })

  it('shows booleans in the words of the list format', () => {
    const yesNo: FormatColumn = {
      valueType: 'BOOLEAN',
      listFormat: 'BOOLEAN_YES_NO',
      masked: false,
    }
    const onOff: FormatColumn = {
      valueType: 'BOOLEAN',
      listFormat: 'BOOLEAN_ON_OFF',
      masked: false,
    }
    expect(formatCell(yesNo, 'true', JA_TEXTS).text).toBe('はい')
    expect(formatCell(yesNo, 'false', JA_TEXTS).text).toBe('いいえ')
    expect(formatCell(onOff, 'true', JA_TEXTS).text).toBe('オン')
    expect(formatCell(onOff, 'false', JA_TEXTS).text).toBe('オフ')
    expect(formatCell({ ...yesNo, listFormat: null }, 'true', JA_TEXTS).text).toBe('はい')
    expect(formatCell(yesNo, 'maybe', JA_TEXTS).text).toBe('maybe')
  })

  it('shows date and time values with a space and an explicit UTC offset', () => {
    const datetime: FormatColumn = { valueType: 'DATETIME', listFormat: null, masked: false }
    const tz: FormatColumn = { valueType: 'DATETIME_TZ', listFormat: null, masked: false }
    const timeTz: FormatColumn = { valueType: 'TIME_TZ', listFormat: null, masked: false }
    expect(formatCell(datetime, '2026-10-18T09:30:00', JA_TEXTS).text).toBe('2026-10-18 09:30:00')
    expect(formatCell(tz, '2026-10-18T00:30:00+00:00', JA_TEXTS).text).toBe(
      '2026-10-18 00:30:00+00:00',
    )
    expect(formatCell(tz, '2026-10-18T00:30:00Z', JA_TEXTS).text).toBe('2026-10-18 00:30:00+00:00')
    expect(formatCell(timeTz, '09:00:00Z', JA_TEXTS).text).toBe('09:00:00+00:00')
    expect(formatCell({ ...datetime, valueType: 'DATE' }, '2026-10-18', JA_TEXTS).text).toBe(
      '2026-10-18',
    )
  })

  it('returns markup and script-like values as plain text', () => {
    for (const value of ['<script>alert(1)</script>', '<img src=x onerror=alert(1)>', SCRIPT_URL]) {
      expect(formatCell(STRING, value, JA_TEXTS)).toEqual({ kind: 'text', text: value })
    }
  })
})
