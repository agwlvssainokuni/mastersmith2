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
// ページの計算のテスト（D1・W2、NFR9.7）。境目の値と、fast-check の性質ベースのテスト（失敗時は seed と path が出力に出る）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { correctedPage, PAGE_SIZE, pageCount, pageRange, pagerButtonDisabledAfter } from './paging'

describe('paging', () => {
  it('counts the pages for 0, 20, 21 and 43 invitations', () => {
    expect(PAGE_SIZE).toBe(20)
    expect(pageCount(0)).toBe(0)
    expect(pageCount(20)).toBe(1)
    expect(pageCount(21)).toBe(2)
    expect(pageCount(43)).toBe(3)
  })

  it('gives the range of the first, a middle and the last page', () => {
    expect(pageRange(1, 43)).toEqual({ from: 1, to: 20 })
    expect(pageRange(2, 43)).toEqual({ from: 21, to: 40 })
    expect(pageRange(3, 43)).toEqual({ from: 41, to: 43 })
    expect(pageRange(1, 5)).toEqual({ from: 1, to: 5 })
  })

  it('gives an empty range when there are no invitations', () => {
    expect(pageRange(1, 0)).toEqual({ from: 0, to: 0 })
  })

  it('corrects to the last page when the page is past the end', () => {
    expect(correctedPage(3, 40, 0)).toBe(2)
    expect(correctedPage(5, 21, 0)).toBe(2)
  })

  it('does not correct when there are rows, nothing at all, or the last page was already read', () => {
    expect(correctedPage(2, 40, 20)).toBeUndefined()
    expect(correctedPage(1, 0, 0)).toBeUndefined()
    expect(correctedPage(2, 40, 0)).toBeUndefined()
  })

  it('tells whether the pressed pager button becomes disabled on the page that was read', () => {
    expect(pagerButtonDisabledAfter('prev', 1, 43)).toBe(true)
    expect(pagerButtonDisabledAfter('prev', 2, 43)).toBe(false)
    expect(pagerButtonDisabledAfter('next', 3, 43)).toBe(true)
    expect(pagerButtonDisabledAfter('next', 2, 43)).toBe(false)
  })

  it('keeps every range and correction within bounds for any total and valid page', () => {
    fc.assert(
      fc.property(
        fc.integer({ min: 1, max: 100_000 }).chain((total) =>
          fc.record({
            total: fc.constant(total),
            page: fc.integer({ min: 1, max: pageCount(total) }),
            beyond: fc.integer({ min: 1, max: 1_000 }),
          }),
        ),
        ({ total, page, beyond }) => {
          const { from, to } = pageRange(page, total)
          expect(from).toBeGreaterThanOrEqual(1)
          expect(from).toBeLessThanOrEqual(to)
          expect(to).toBeLessThanOrEqual(total)
          expect(to - from + 1).toBeLessThanOrEqual(PAGE_SIZE)
          const corrected = correctedPage(pageCount(total) + beyond, total, 0)
          expect(corrected).toBeGreaterThanOrEqual(1)
          expect(corrected).toBeLessThanOrEqual(pageCount(total))
        },
      ),
    )
  })
})
