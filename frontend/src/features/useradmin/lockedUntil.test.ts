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
// 解除の予定の時刻の書式のテスト（functional-spec.md の D18、NFR8.3）。今の時刻と時間帯は引数で固定する（実時刻に頼らない）。
import { describe, expect, it } from 'vitest'
import { formatLockedUntil } from './lockedUntil'

/** 2026-10-03 10:00 JST（01:00 UTC） */
const NOW = new Date('2026-10-03T01:00:00Z')

describe('formatLockedUntil', () => {
  it('shows only the time and the zone in Japanese when the time is today', () => {
    expect(formatLockedUntil('2026-10-03T01:30:00Z', 'ja', NOW, 'Asia/Tokyo')).toBe('10:30 JST')
  })

  it('shows only the time and the zone in English when the time is today', () => {
    expect(formatLockedUntil('2026-10-03T01:30:00Z', 'en', NOW, 'Asia/Tokyo')).toBe('10:30 GMT+9')
  })

  it('shows the date when the time is tomorrow', () => {
    // 2026-10-04 00:10 JST（翌日にまたがる）
    expect(formatLockedUntil('2026-10-03T15:10:00Z', 'ja', NOW, 'Asia/Tokyo')).toBe(
      '2026-10-04 00:10 JST',
    )
    expect(formatLockedUntil('2026-10-03T15:10:00Z', 'en', NOW, 'Asia/Tokyo')).toBe(
      'Oct 4, 2026, 00:10 GMT+9',
    )
  })

  it('shows the date when the time is the day before', () => {
    expect(formatLockedUntil('2026-10-02T01:30:00Z', 'ja', NOW, 'Asia/Tokyo')).toBe(
      '2026-10-02 10:30 JST',
    )
  })

  it('decides today by the time zone', () => {
    // UTC では今は 10-03 01:00 で、時刻 10-02 23:30 UTC は前日（日付つき）。東京では両方 10-03（時刻だけ）。
    const iso = '2026-10-02T23:30:00Z'
    expect(formatLockedUntil(iso, 'ja', NOW, 'UTC')).toBe('2026-10-02 23:30 UTC')
    expect(formatLockedUntil(iso, 'ja', NOW, 'Asia/Tokyo')).toBe('08:30 JST')
  })

  it('returns a value that cannot be read as it is', () => {
    expect(formatLockedUntil('not-a-date', 'ja', NOW, 'Asia/Tokyo')).toBe('not-a-date')
  })
})
