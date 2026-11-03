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
import { describe, expect, it } from 'vitest'
import {
  civilFromDays,
  daysFromCivil,
  daysInMonth,
  instantCandidates,
  isYearInRange,
  offsetSecondsAt,
  secondsToWall,
  wallAt,
  wallToSeconds,
  type WallTime,
} from './wallClock'

/** UTC の日時から秒を作る（テストの入力用） */
function utc(year: number, month: number, day: number, hour = 0, minute = 0, second = 0): number {
  return wallToSeconds({ year, month, day, hour, minute, second })
}

function wall(
  year: number,
  month: number,
  day: number,
  hour: number,
  minute: number,
  second = 0,
): WallTime {
  return { year, month, day, hour, minute, second }
}

describe('wallClock', () => {
  it('converts an instant to the wall clock of Asia/Tokyo across the date line of UTC', () => {
    const w = wallAt('Asia/Tokyo', utc(2026, 11, 1, 15))
    expect(w).toEqual(wall(2026, 11, 2, 0, 0))
    expect(offsetSecondsAt('Asia/Tokyo', utc(2026, 11, 1, 15))).toBe(32400)
  })

  it('returns the historical offset with seconds for Asia/Tokyo in 1800', () => {
    expect(offsetSecondsAt('Asia/Tokyo', utc(1800, 1, 1, 12))).toBe(9 * 3600 + 18 * 60 + 59)
  })

  it('switches the offset of America/New_York exactly at the transition', () => {
    expect(offsetSecondsAt('America/New_York', utc(2026, 3, 8, 6, 59, 59))).toBe(-18000)
    expect(offsetSecondsAt('America/New_York', utc(2026, 3, 8, 7, 0, 0))).toBe(-14400)
  })

  it.each([
    ['Asia/Kathmandu', utc(2026, 6, 1), 5 * 3600 + 45 * 60],
    ['Australia/Adelaide', utc(2026, 7, 1), 9 * 3600 + 30 * 60],
    ['America/St_Johns', utc(2026, 1, 15), -(3 * 3600 + 30 * 60)],
  ])('reads the offset with minutes of %s', (zone, instant, expected) => {
    expect(offsetSecondsAt(zone, instant)).toBe(expected)
  })

  it('finds no instant for a time skipped by daylight saving time', () => {
    expect(instantCandidates('America/New_York', wall(2026, 3, 8, 2, 30))).toEqual([])
  })

  it('finds two instants, earlier first, for a repeated time', () => {
    expect(instantCandidates('America/New_York', wall(2026, 11, 1, 1, 30))).toEqual([
      utc(2026, 11, 1, 5, 30),
      utc(2026, 11, 1, 6, 30),
    ])
  })

  it('finds one instant for an ordinary time', () => {
    expect(instantCandidates('Asia/Tokyo', wall(2026, 11, 2, 0, 0))).toEqual([utc(2026, 11, 1, 15)])
  })

  it('handles the 30 minute daylight saving time of Australia/Lord_Howe', () => {
    expect(instantCandidates('Australia/Lord_Howe', wall(2026, 10, 4, 2, 15))).toEqual([])
    expect(instantCandidates('Australia/Lord_Howe', wall(2026, 4, 5, 1, 45))).toHaveLength(2)
  })

  it('finds no instant for a day skipped entirely (Pacific/Apia 2011-12-30)', () => {
    expect(instantCandidates('Pacific/Apia', wall(2011, 12, 30, 12, 0))).toEqual([])
  })

  it('round trips the earliest and the 1500s years in UTC', () => {
    for (const w of [
      wall(1, 1, 1, 0, 0),
      wall(1500, 6, 15, 12, 30, 45),
      wall(1582, 10, 10, 0, 0),
    ]) {
      const candidates = instantCandidates('UTC', w)
      expect(candidates).toEqual([wallToSeconds(w)])
      expect(wallAt('UTC', wallToSeconds(w))).toEqual(w)
    }
  })

  it('reports years outside 0001 to 9999 without hiding them', () => {
    const afterMax = wallAt('Asia/Kolkata', utc(9999, 12, 31, 23, 59, 59))
    expect(afterMax?.year).toBe(10000)
    expect(isYearInRange(afterMax?.year ?? 0)).toBe(false)
    const beforeMin = wallAt('UTC', utc(1, 1, 1) - 1)
    expect(beforeMin?.year).toBe(0)
    expect(isYearInRange(0)).toBe(false)
    expect(isYearInRange(1)).toBe(true)
    expect(isYearInRange(9999)).toBe(true)
  })

  it('returns null instead of throwing for an unknown time zone or a date outside the Date range', () => {
    expect(wallAt('Mars/Olympus', 0)).toBeNull()
    expect(offsetSecondsAt('Mars/Olympus', 0)).toBeNull()
    expect(instantCandidates('Mars/Olympus', wall(2026, 1, 1, 0, 0))).toBeNull()
    expect(wallAt('UTC', 9e15)).toBeNull()
  })

  it('computes the calendar without Date, including leap years and negative days', () => {
    expect(daysFromCivil(1970, 1, 1)).toBe(0)
    expect(civilFromDays(daysFromCivil(2024, 2, 29))).toEqual({ year: 2024, month: 2, day: 29 })
    expect(civilFromDays(-1)).toEqual({ year: 1969, month: 12, day: 31 })
    expect(secondsToWall(-1)).toEqual(wall(1969, 12, 31, 23, 59, 59))
    expect([
      daysInMonth(2024, 2),
      daysInMonth(2025, 2),
      daysInMonth(1900, 2),
      daysInMonth(2000, 2),
    ]).toEqual([29, 28, 28, 29])
    expect([daysInMonth(2026, 4), daysInMonth(2026, 12)]).toEqual([30, 31])
  })
})
