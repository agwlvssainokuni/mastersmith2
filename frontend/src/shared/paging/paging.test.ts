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
// 管理の一覧のページ送りの計算のテスト（BR2.1〜BR2.4、NFR9.6・NFR9.7）。境目の値と、fast-check の性質ベースのテスト。
// 失敗したときの再現の仕方: fast-check は失敗の報告に seed と path を出すため、その fc.assert(property) を
// fc.assert(property, { seed: <seed>, path: '<path>' }) に一時的に替えて流す（原因を直した後に元に戻す）。
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

  it('joins the ranges of neighboring pages without gaps and sums them up to the total', () => {
    fc.assert(
      fc.property(fc.integer({ min: 1, max: 100_000 }), (total) => {
        let sum = 0
        let previousTo = 0
        for (let page = 1; page <= pageCount(total); page += 1) {
          const { from, to } = pageRange(page, total)
          expect(from).toBe(previousTo + 1)
          sum += to - from + 1
          previousTo = to
        }
        expect(sum).toBe(total)
      }),
    )
  })

  it('counts just enough pages to hold the total', () => {
    fc.assert(
      fc.property(fc.integer({ min: 1, max: 100_000 }), (total) => {
        const count = pageCount(total)
        expect((count - 1) * PAGE_SIZE).toBeLessThan(total)
        expect(total).toBeLessThanOrEqual(count * PAGE_SIZE)
      }),
    )
  })

  // 期待の値は pageCount の式を使わず、最後のページ（last）と最後のページの件数（1〜20）から全件数を組み立てて決める。
  const totalWithLastPage = fc
    .record({
      last: fc.integer({ min: 1, max: 5_000 }),
      rowsOnLast: fc.integer({ min: 1, max: PAGE_SIZE }),
    })
    .map(({ last, rowsOnLast }) => ({ last, total: (last - 1) * PAGE_SIZE + rowsOnLast }))

  it('keeps the next button enabled before the last page and disables it on and after the last page', () => {
    fc.assert(
      fc.property(
        totalWithLastPage,
        fc.integer({ min: 1, max: 1_000 }),
        ({ last, total }, beyond) => {
          expect(pagerButtonDisabledAfter('next', last, total)).toBe(true)
          expect(pagerButtonDisabledAfter('next', last + beyond, total)).toBe(true)
          if (last >= 2) {
            expect(pagerButtonDisabledAfter('next', last - 1, total)).toBe(false)
            expect(pagerButtonDisabledAfter('next', 1, total)).toBe(false)
          }
        },
      ),
    )
  })

  it('corrects an empty page past the end to the last page, but not when the page has a row', () => {
    fc.assert(
      fc.property(
        totalWithLastPage,
        fc.integer({ min: 1, max: 1_000 }),
        fc.integer({ min: 1, max: PAGE_SIZE }),
        ({ last, total }, beyond, itemCount) => {
          expect(correctedPage(last + beyond, total, 0)).toBe(last)
          expect(correctedPage(last + beyond, total, itemCount)).toBeUndefined()
          expect(correctedPage(last, total, 0)).toBeUndefined()
          expect(correctedPage(last, total, itemCount)).toBeUndefined()
        },
      ),
    )
  })
})
