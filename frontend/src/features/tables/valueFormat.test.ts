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
// 時点を表す型の表示（Intent 261102-timezone-ui-polish の U4、計画 Step 7、D1〜D7・D11・D41）。タイムゾーンは引数で明示に渡す。
import { beforeEach, describe, expect, it, vi } from 'vitest'
import * as timeZone from '../../shared/format/timeZone'
import type { CellTimeZone, FormatColumn } from './valueFormat'
import { MASKED_TEXT, formatCell, groupDigits } from './valueFormat'
import { JA_TEXTS } from './testing/valueTexts'

// 整形（formatInstant）の呼び出しの回数を数える。中身は本物のまま（NFR2.1.3）
vi.mock('../../shared/format/timeZone', async (importOriginal) => {
  const actual = await importOriginal<typeof import('../../shared/format/timeZone')>()
  return { ...actual, formatInstant: vi.fn(actual.formatInstant) }
})

const SCRIPT_URL = ['javascript', 'alert(1)'].join(':')

const STRING: FormatColumn = { valueType: 'STRING', listFormat: null, masked: false }
const INSTANT: FormatColumn = { valueType: 'DATETIME_TZ', listFormat: null, masked: false }

const TOKYO: CellTimeZone = { status: 'ready', timeZone: 'Asia/Tokyo' }
const NEW_YORK: CellTimeZone = { status: 'ready', timeZone: 'America/New_York' }
const ERROR: CellTimeZone = { status: 'error' }

function ready(name: string): CellTimeZone {
  return { status: 'ready', timeZone: name }
}

beforeEach(() => {
  vi.mocked(timeZone.formatInstant).mockClear()
})

describe('formatCell', () => {
  it('shows NULL, an empty string, masked and binary values distinctly', () => {
    expect(formatCell(STRING, null, JA_TEXTS, TOKYO)).toEqual({ kind: 'null', text: '（値なし）' })
    expect(formatCell(STRING, undefined, JA_TEXTS, TOKYO)).toEqual({
      kind: 'null',
      text: '（値なし）',
    })
    expect(formatCell(STRING, '', JA_TEXTS, TOKYO)).toEqual({ kind: 'text', text: '' })
    expect(formatCell({ ...STRING, masked: true }, undefined, JA_TEXTS, TOKYO)).toEqual({
      kind: 'masked',
      text: MASKED_TEXT,
    })
    expect(formatCell({ ...STRING, valueType: 'BINARY' }, null, JA_TEXTS, TOKYO)).toEqual({
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
    expect(formatCell(column, '-1234.5', JA_TEXTS, TOKYO).text).toBe('-1,234.5')
    expect(formatCell(column, '12345678901234567890.1234567890', JA_TEXTS, TOKYO).text).toBe(
      '12,345,678,901,234,567,890.1234567890',
    )
    expect(formatCell(column, '123', JA_TEXTS, TOKYO).text).toBe('123')
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
    expect(formatCell(yesNo, 'true', JA_TEXTS, TOKYO).text).toBe('はい')
    expect(formatCell(yesNo, 'false', JA_TEXTS, TOKYO).text).toBe('いいえ')
    expect(formatCell(onOff, 'true', JA_TEXTS, TOKYO).text).toBe('オン')
    expect(formatCell(onOff, 'false', JA_TEXTS, TOKYO).text).toBe('オフ')
    expect(formatCell({ ...yesNo, listFormat: null }, 'true', JA_TEXTS, TOKYO).text).toBe('はい')
    expect(formatCell(yesNo, 'maybe', JA_TEXTS, TOKYO).text).toBe('maybe')
  })

  it('shows the types without a zone as stored, whatever the display time zone (D3, D4)', () => {
    const datetime: FormatColumn = { valueType: 'DATETIME', listFormat: null, masked: false }
    const timeTz: FormatColumn = { valueType: 'TIME_TZ', listFormat: null, masked: false }
    for (const zone of [TOKYO, NEW_YORK, ERROR]) {
      expect(formatCell(datetime, '2026-10-18T09:30:00.123456', JA_TEXTS, zone).text).toBe(
        '2026-10-18 09:30:00.123456',
      )
      expect(formatCell(timeTz, '09:00:00Z', JA_TEXTS, zone).text).toBe('09:00:00+00:00')
      expect(formatCell(timeTz, '09:00:00+05:45', JA_TEXTS, zone).text).toBe('09:00:00+05:45')
      expect(formatCell({ ...datetime, valueType: 'TIME' }, '09:00:00', JA_TEXTS, zone).text).toBe(
        '09:00:00',
      )
      expect(
        formatCell({ ...datetime, valueType: 'DATE' }, '2026-10-18', JA_TEXTS, zone).text,
      ).toBe('2026-10-18')
    }
    expect(timeZone.formatInstant).not.toHaveBeenCalled()
  })

  it('returns markup and script-like values as plain text', () => {
    for (const value of ['<script>alert(1)</script>', '<img src=x onerror=alert(1)>', SCRIPT_URL]) {
      expect(formatCell(STRING, value, JA_TEXTS, TOKYO)).toEqual({ kind: 'text', text: value })
    }
  })
})

describe('formatCell for an instant (DATETIME_TZ, D1, D2, D5, D11, D41)', () => {
  it('moves the date at the day boundary in the display time zone', () => {
    expect(formatCell(INSTANT, '2026-11-01T15:00:00+00:00', JA_TEXTS, TOKYO)).toEqual({
      kind: 'text',
      text: '2026-11-02 00:00:00 +09:00',
    })
    expect(formatCell(INSTANT, '2026-12-31T15:00:00Z', JA_TEXTS, TOKYO).text).toBe(
      '2027-01-01 00:00:00 +09:00',
    )
  })

  it.each([
    ['2026-11-02T00:30:15+00:00', '2026-11-02 09:30:15 +09:00'],
    ['2026-11-02T00:30:15.123+00:00', '2026-11-02 09:30:15.123 +09:00'],
    ['2026-11-02T00:30:15.123456+00:00', '2026-11-02 09:30:15.123456 +09:00'],
  ])('keeps the fraction digits of %s without rounding or padding', (value, expected) => {
    expect(formatCell(INSTANT, value, JA_TEXTS, TOKYO).text).toBe(expected)
  })

  it('changes the offset around the daylight saving time changes of America/New_York', () => {
    // 2026-03-08 02:00 EST に夏時間へ、2026-11-01 02:00 EDT に標準時へ切り替わる
    expect(formatCell(INSTANT, '2026-03-08T06:59:59+00:00', JA_TEXTS, NEW_YORK).text).toBe(
      '2026-03-08 01:59:59 -05:00',
    )
    expect(formatCell(INSTANT, '2026-03-08T07:00:00+00:00', JA_TEXTS, NEW_YORK).text).toBe(
      '2026-03-08 03:00:00 -04:00',
    )
    expect(formatCell(INSTANT, '2026-11-01T05:30:00+00:00', JA_TEXTS, NEW_YORK).text).toBe(
      '2026-11-01 01:30:00 -04:00',
    )
    expect(formatCell(INSTANT, '2026-11-01T06:30:00+00:00', JA_TEXTS, NEW_YORK).text).toBe(
      '2026-11-01 01:30:00 -05:00',
    )
  })

  it.each([
    ['Asia/Kathmandu', '2026-01-15 05:45:00 +05:45'],
    ['Australia/Darwin', '2026-01-15 09:30:00 +09:30'],
    ['America/St_Johns', '2026-01-14 20:30:00 -03:30'],
  ])('keeps the minute fraction of the offset of %s', (name, expected) => {
    expect(formatCell(INSTANT, '2026-01-15T00:00:00+00:00', JA_TEXTS, ready(name)).text).toBe(
      expected,
    )
  })

  it('shows an unreadable value as the original text without an offset (D5)', () => {
    for (const value of [
      '0000-00-00 00:00:00',
      '2026-02-30T00:00:00+00:00',
      '<script>x</script>',
    ]) {
      expect(formatCell(INSTANT, value, JA_TEXTS, TOKYO)).toEqual({ kind: 'text', text: value })
    }
  })

  it('shows the short text and does not format while the display time zone fails (D11)', () => {
    expect(formatCell(INSTANT, '2026-11-01T15:00:00+00:00', JA_TEXTS, ERROR)).toEqual({
      kind: 'unavailable',
      text: '時刻を表示できません',
    })
    expect(timeZone.formatInstant).not.toHaveBeenCalled()
  })

  it('keeps NULL and an empty string as they are even while the display time zone fails (D7)', () => {
    expect(formatCell(INSTANT, null, JA_TEXTS, ERROR).kind).toBe('null')
    expect(formatCell(INSTANT, '', JA_TEXTS, ERROR)).toEqual({ kind: 'text', text: '' })
    expect(timeZone.formatInstant).not.toHaveBeenCalled()
  })

  it('formats each instant value once and never for the other types (NFR2.1.3)', () => {
    const values = ['2026-11-01T15:00:00+00:00', null, '2026-11-02T00:00:00.5+00:00', 'bad']
    for (const value of values) {
      formatCell(INSTANT, value, JA_TEXTS, TOKYO)
      formatCell(STRING, value, JA_TEXTS, TOKYO)
    }
    expect(timeZone.formatInstant).toHaveBeenCalledTimes(3)
    expect(timeZone.formatInstant).toHaveBeenCalledWith('bad', 'Asia/Tokyo')
  })

  it('treats a display time zone name that cannot be interpreted like a failure', () => {
    expect(
      formatCell(INSTANT, '2026-11-01T15:00:00+00:00', JA_TEXTS, ready('Mars/Olympus')),
    ).toEqual({ kind: 'unavailable', text: '時刻を表示できません' })
  })
})
