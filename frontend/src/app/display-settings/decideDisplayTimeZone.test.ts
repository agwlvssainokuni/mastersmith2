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
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { canInterpretTimeZone } from '../../shared/format/timeZone'
import { decideDisplayTimeZone } from './decideDisplayTimeZone'

describe('decideDisplayTimeZone', () => {
  it('uses the saved name when this browser can interpret it', () => {
    expect(decideDisplayTimeZone('Asia/Kolkata', 'Asia/Tokyo')).toEqual({
      timeZone: 'Asia/Kolkata',
      source: 'saved',
    })
  })

  it('uses the browser time zone when nothing is saved', () => {
    expect(decideDisplayTimeZone(null, 'America/New_York')).toEqual({
      timeZone: 'America/New_York',
      source: 'browser',
    })
  })

  it('falls back to the browser time zone and says so when the saved name cannot be interpreted', () => {
    expect(decideDisplayTimeZone('Mars/Olympus', 'Asia/Tokyo')).toEqual({
      timeZone: 'Asia/Tokyo',
      source: 'saved-unresolvable',
    })
    expect(decideDisplayTimeZone('+09:00', 'Asia/Tokyo').source).toBe('saved-unresolvable')
  })

  it('falls back to UTC when the browser name cannot be interpreted either', () => {
    expect(decideDisplayTimeZone(null, 'Mars/Olympus')).toEqual({
      timeZone: 'UTC',
      source: 'browser',
    })
    expect(decideDisplayTimeZone('Mars/Olympus', '')).toEqual({
      timeZone: 'UTC',
      source: 'saved-unresolvable',
    })
  })

  it('always decides a name this browser can interpret', () => {
    fc.assert(
      fc.property(fc.option(fc.string(), { nil: null }), fc.string(), (saved, browser) => {
        const decided = decideDisplayTimeZone(saved, browser)
        expect(canInterpretTimeZone(decided.timeZone)).toBe(true)
      }),
      { numRuns: 200 },
    )
  })
})
