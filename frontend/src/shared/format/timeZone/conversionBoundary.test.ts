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
import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  canInterpretTimeZone,
  compareInstants,
  formatInstant,
  parseLocalDateTime,
  preserveInstant,
} from './index'
import { MAX_TIME_ZONE_NAME_LENGTH } from './timeZoneGate'

/** 正常・異常の入力を混ぜて、公開の全関数を呼び、結果を返す */
function callEverything(): unknown[] {
  const instants = ['2026-11-01T15:00:00.123Z', 'broken', '<img src=x onerror=1>', '9'.repeat(1000)]
  const locals = [
    '2026-11-02 00:00:00',
    '2026-03-08 02:30:00',
    '2026-11-01 01:30:00',
    'bad',
    '2026-02-30 00:00:00',
  ]
  const zones = ['Asia/Tokyo', 'America/New_York', '+09:00', 'Mars/Olympus', '']
  const results: unknown[] = []
  for (const zone of zones) {
    results.push(canInterpretTimeZone(zone))
    for (const instant of instants) {
      results.push(formatInstant(instant, zone), formatInstant(instant, zone, 6))
    }
    for (const local of locals) {
      results.push(parseLocalDateTime(local, zone), parseLocalDateTime(local, zone, 'later'))
    }
  }
  for (const a of instants) {
    results.push(preserveInstant(a))
    for (const b of instants) {
      results.push(compareInstants(a, b))
    }
  }
  results.push(
    formatInstant(null as unknown as string, 'UTC'),
    parseLocalDateTime(7 as unknown as string, 'UTC'),
  )
  return results
}

describe('conversion boundary', () => {
  const originalTz = process.env.TZ

  afterEach(() => {
    vi.restoreAllMocks()
    if (originalTz === undefined) {
      delete process.env.TZ
    } else {
      process.env.TZ = originalTz
    }
  })

  it('never writes to the console, for normal and abnormal input', () => {
    const spies = (['log', 'info', 'warn', 'error', 'debug', 'trace'] as const).map((method) =>
      vi.spyOn(console, method).mockImplementation(() => undefined),
    )
    callEverything()
    for (const spy of spies) {
      expect(spy).not.toHaveBeenCalled()
    }
  })

  it.each(['UTC', 'Asia/Tokyo', 'America/Los_Angeles', 'Pacific/Kiritimati'])(
    'gives the same results whatever the machine TZ (%s) and browser language',
    (machineZone) => {
      const baseline = JSON.stringify(callEverything())
      process.env.TZ = machineZone
      for (const language of ['ja-JP', 'ar-EG', 'en-US']) {
        vi.spyOn(navigator, 'language', 'get').mockReturnValue(language)
        expect(JSON.stringify(callEverything())).toBe(baseline)
      }
    },
  )

  it('writes ASCII digits even when the browser language uses other digits', () => {
    vi.spyOn(navigator, 'language', 'get').mockReturnValue('ar-EG')
    const result = formatInstant('2026-11-01T15:00:00.123456Z', 'Asia/Tokyo')
    expect(result).toEqual({ kind: 'ok', text: '2026-11-02 00:00:00.123456 +09:00' })
    expect(JSON.stringify(result)).toMatch(/^[\x20-\x7e]*$/)
  })

  it('accepts every time zone name the browser supports, within the 64 character limit', () => {
    const names = Intl.supportedValuesOf('timeZone')
    expect(names.length).toBeGreaterThan(300)
    const longest = Math.max(...names.map((name) => name.length))
    expect(longest).toBeLessThanOrEqual(MAX_TIME_ZONE_NAME_LENGTH)
    const refused = names.filter((name) => !canInterpretTimeZone(name))
    expect(refused).toEqual([])
    expect(canInterpretTimeZone('UTC')).toBe(true)
  })
})
