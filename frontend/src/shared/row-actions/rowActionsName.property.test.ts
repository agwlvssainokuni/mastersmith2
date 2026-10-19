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
// 行の「操作」のトリガーの名前の関数の性質ベースのテスト（U4 の D1・D8、frontend-components.md の 2節、U4-NFR4.6・7.4）。
// fast-check は、失敗したときに seed と path を出力に示す。再現はその値を fc.assert の第2引数に一時的に書いて行い、
// seed を記録に残す（team.md の Testing Posture）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { rowActionsName } from './rowActionsName'

const SEPARATOR = ': '

/** 空白・タブ・改行・全角の空白だけの文字列（空文字を含む） */
const whitespaceOnly = fc
  .array(fc.constantFrom(' ', '\t', '\n', '\r', '　'), { maxLength: 8 })
  .map((chars) => chars.join(''))

/** 区切りを含まない、空でない見える文字 */
const visibleLabel = fc.string({ minLength: 1 }).filter((s) => !s.includes(SEPARATOR))

/** 空白だけでない行の値 */
const nonBlankRow = fc.string({ minLength: 1 }).filter((s) => s.trim() !== '')

/** 区切りを含まない、任意の行の値 */
const rowWithoutSeparator = fc.string().filter((s) => !s.trim().includes(SEPARATOR))

/** 文字列に部分の文字列が何回現れるか（重ならない数え方） */
function countOccurrences(text: string, part: string): number {
  return text.split(part).length - 1
}

describe('rowActionsName properties', () => {
  it('always starts with the visible label', () => {
    fc.assert(
      fc.property(visibleLabel, fc.string(), (label, row) => {
        expect(rowActionsName(label, row).startsWith(label)).toBe(true)
      }),
    )
  })

  it('contains the trimmed row label when the row label is not blank', () => {
    fc.assert(
      fc.property(visibleLabel, nonBlankRow, (label, row) => {
        expect(rowActionsName(label, row)).toContain(row.trim())
      }),
    )
  })

  it('equals the visible label when the row label is blank', () => {
    fc.assert(
      fc.property(visibleLabel, whitespaceOnly, (label, row) => {
        expect(rowActionsName(label, row)).toBe(label)
      }),
    )
  })

  it('contains the separator exactly once when the row label is not blank', () => {
    fc.assert(
      fc.property(
        visibleLabel,
        rowWithoutSeparator.filter((s) => s.trim() !== ''),
        (label, row) => {
          expect(countOccurrences(rowActionsName(label, row), SEPARATOR)).toBe(1)
        },
      ),
    )
  })
})
