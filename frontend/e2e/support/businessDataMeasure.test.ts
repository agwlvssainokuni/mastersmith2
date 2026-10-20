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
// 業務データの画面の測りの集計の単体のテスト（Intent 261016-business-data-views の U3 の L10、R-11、計画 4.8 の P-8）。
import { describe, expect, it } from 'vitest'
import { BUSINESS_DATA_TARGETS, summarizePaintRuns } from './businessDataMeasure'

describe('summarizePaintRuns', () => {
  it('judges by the median of the judged values and lists the paint times as a reference', () => {
    const record = summarizePaintRuns(
      'listShown100',
      [
        { value: 50, toPaint: 260 },
        { value: 40, toPaint: 2000 },
        { value: 60, toPaint: 250 },
        { value: 45, toPaint: 240 },
        { value: 55, toPaint: 270 },
      ],
      BUSINESS_DATA_TARGETS.listShown100,
      5,
    )
    expect(record).toMatchObject({
      id: 'listShown100',
      median: 50,
      max: 60,
      first: 50,
      verdict: 'Met',
    })
    expect(record.paint).toEqual({
      runs: [260, 2000, 250, 240, 270],
      median: 260,
      max: 2000,
      first: 260,
    })
  })

  it('is Not Met when the median exceeds the target and Unverified with missing runs', () => {
    const slow = Array.from({ length: 5 }, () => ({ value: 600, toPaint: 700 }))
    expect(
      summarizePaintRuns('listShown20', slow, BUSINESS_DATA_TARGETS.listShown20, 5).verdict,
    ).toBe('Not Met')
    expect(summarizePaintRuns('detailFocused', slow.slice(0, 3), 500, 5, 'x').verdict).toBe(
      'Unverified',
    )
  })

  it('keeps the targets of the screen time requirement', () => {
    expect(BUSINESS_DATA_TARGETS).toEqual({
      listShown100: 1000,
      listShown20: 500,
      detailFocused: 500,
      formShown100: 500,
      listShown100Back: 1000,
    })
  })
})
