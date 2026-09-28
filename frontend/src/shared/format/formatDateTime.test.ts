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
// 日時の書式のテスト（DSL の管理画面の BR5.10・BR6.3、招待の管理の画面の D6、NFR9.8）。
// features/dsl/format.test.ts から、確かめの中身を変えずに移した。時差と表示言語を固定して確かめる。
import { describe, expect, it } from 'vitest'
import { formatDateTime } from './formatDateTime'

describe('formatDateTime', () => {
  it('shows a UTC date in the given time zone with its abbreviation in Japanese', () => {
    expect(formatDateTime('2026-09-24T01:15:00Z', 'ja', 'Asia/Tokyo')).toBe('2026-09-24 10:15 JST')
    expect(formatDateTime('2026-09-24T01:15:00Z', 'ja', 'UTC')).toBe('2026-09-24 01:15 UTC')
  })

  it('shows a UTC date in the English format', () => {
    expect(formatDateTime('2026-09-20T07:40:00Z', 'en', 'UTC')).toBe('Sep 20, 2026, 07:40 UTC')
  })

  it('returns a value that is not a date as it is', () => {
    expect(formatDateTime('not a date', 'ja', 'UTC')).toBe('not a date')
  })
})
