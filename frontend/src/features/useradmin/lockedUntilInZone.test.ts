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
import { lockedUntilInZone, todayIn } from './lockedUntilInZone'

const NOW = new Date('2026-10-03T01:00:00Z')

describe('todayIn', () => {
  it('gives the date of now in the zone', () => {
    expect(todayIn(NOW, 'Asia/Tokyo')).toBe('2026-10-03')
    expect(todayIn(NOW, 'America/Los_Angeles')).toBe('2026-10-02')
  })

  it('gives null for a zone that cannot be interpreted', () => {
    expect(todayIn(NOW, 'Mars/Olympus')).toBeNull()
  })
})

describe('lockedUntilInZone', () => {
  const today = todayIn(NOW, 'Asia/Tokyo')

  it('shows only the time and the offset when it is today in the zone', () => {
    expect(lockedUntilInZone('2026-10-03T01:30:00Z', 'Asia/Tokyo', today)).toBe('10:30 +09:00')
  })

  it('shows the date as well when it is not today in the zone', () => {
    expect(lockedUntilInZone('2026-10-04T01:30:00Z', 'Asia/Tokyo', today)).toBe(
      '2026-10-04 10:30 +09:00',
    )
  })

  it('judges today in the display zone, across the day boundary', () => {
    // UTC では 10-02 の 16:00 でも、日本時間では 10-03 の 01:00（今日）。
    expect(lockedUntilInZone('2026-10-02T16:00:00Z', 'Asia/Tokyo', today)).toBe('01:00 +09:00')
    // 日本時間の 10-04 0:00（UTC では 10-03 15:00）は今日でない。
    expect(lockedUntilInZone('2026-10-03T15:00:00Z', 'Asia/Tokyo', today)).toBe(
      '2026-10-04 00:00 +09:00',
    )
  })

  it('returns the original text for an unreadable value', () => {
    expect(lockedUntilInZone('broken', 'Asia/Tokyo', today)).toBe('broken')
  })

  it('shows the date when today could not be determined', () => {
    expect(lockedUntilInZone('2026-10-03T01:30:00Z', 'Asia/Tokyo', null)).toBe(
      '2026-10-03 10:30 +09:00',
    )
  })
})
