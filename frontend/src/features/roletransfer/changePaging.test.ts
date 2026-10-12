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
// 開いた行の 100 件ずつのページ送りのテスト（Intent 261004-role-menu の U6 の D24、NFR6.2）。
// 失敗したときの再現の仕方: fast-check の報告の seed と path を fc.assert の2つ目の引数に一時的に渡して流す。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { CHANGE_PAGE_SIZE, changePageCount, changePageRange } from './changePaging'

describe('changePaging', () => {
  it('divides the changes by 100', () => {
    expect(CHANGE_PAGE_SIZE).toBe(100)
    expect(changePageCount(0)).toBe(0)
    expect(changePageCount(100)).toBe(1)
    expect(changePageCount(101)).toBe(2)
    expect(changePageRange(2, 250)).toEqual({ page: 2, pageCount: 3, start: 100, end: 200 })
    expect(changePageRange(3, 250)).toEqual({ page: 3, pageCount: 3, start: 200, end: 250 })
  })

  it('gives an empty range for no changes and moves a page outside the range into it', () => {
    expect(changePageRange(1, 0)).toEqual({ page: 0, pageCount: 0, start: 0, end: 0 })
    expect(changePageRange(0, 5)).toEqual({ page: 1, pageCount: 1, start: 0, end: 5 })
    expect(changePageRange(9, 150)).toEqual({ page: 2, pageCount: 2, start: 100, end: 150 })
  })

  it('covers all the changes without overlap (property)', () => {
    fc.assert(
      fc.property(fc.integer({ min: 0, max: 5000 }), (total) => {
        const count = changePageCount(total)
        let next = 0
        for (let page = 1; page <= count; page += 1) {
          const range = changePageRange(page, total)
          expect(range.start).toBe(next)
          expect(range.end - range.start).toBeGreaterThan(0)
          expect(range.end - range.start).toBeLessThanOrEqual(CHANGE_PAGE_SIZE)
          next = range.end
        }
        expect(next).toBe(total)
      }),
    )
  })
})
