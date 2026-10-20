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
// 業務データの一覧のページの計算のテスト（Intent 261016-business-data-views の U3 の L6、D11・D12、計画 Step 7）。
import { describe, expect, it } from 'vitest'
import { MAX_PAGE } from './businessDataParse'
import {
  DEFAULT_PAGE_SIZE,
  clampPage,
  displayRange,
  isPageOverflow,
  isPageSize,
  previousPageFor,
  totalPages,
} from './listPaging'

describe('listPaging', () => {
  it('accepts only 20, 50 and 100 rows per page with 20 as the default', () => {
    expect(DEFAULT_PAGE_SIZE).toBe(20)
    expect([20, 50, 100].every(isPageSize)).toBe(true)
    expect([0, 10, 30, '20', null].some(isPageSize)).toBe(false)
  })

  it('counts pages for each page size and keeps at least one page', () => {
    expect(totalPages(0, 20)).toBe(1)
    expect(totalPages(45, 20)).toBe(3)
    expect(totalPages(45, 50)).toBe(1)
    expect(totalPages(101, 100)).toBe(2)
    expect(totalPages(Number.MAX_SAFE_INTEGER, 20)).toBe(MAX_PAGE)
  })

  it('shows the displayed range of the current page', () => {
    expect(displayRange(1, 20, 20)).toEqual({ from: 1, to: 20 })
    expect(displayRange(3, 20, 5)).toEqual({ from: 41, to: 45 })
    expect(displayRange(2, 50, 0)).toBeNull()
  })

  it('detects a page beyond the rows and goes back to the last page', () => {
    expect(isPageOverflow(0, 45)).toBe(true)
    expect(isPageOverflow(0, 0)).toBe(false)
    expect(isPageOverflow(5, 45)).toBe(false)
    expect(previousPageFor(5, 45, 20)).toBe(3)
    expect(previousPageFor(3, 45, 20)).toBe(2)
    expect(previousPageFor(1, 45, 20)).toBe(1)
  })

  it('clamps page numbers into 1 to the upper limit', () => {
    expect(clampPage(0)).toBe(1)
    expect(clampPage(2.7)).toBe(2)
    expect(clampPage(MAX_PAGE + 5)).toBe(MAX_PAGE)
    expect(clampPage(Number.NaN)).toBe(1)
  })
})
