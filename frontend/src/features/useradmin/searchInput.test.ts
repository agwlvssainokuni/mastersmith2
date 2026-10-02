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
// 検索の文字の確かめのテスト（functional-spec.md の D15、AC1.1.4・AC1.1.11、NFR9.6）。境目の値と性質ベースのテスト。
// 失敗したときの再現の仕方: fast-check は失敗の報告に seed と path を出すため、その fc.assert(property) を
// fc.assert(property, { seed: <seed>, path: '<path>' }) に一時的に替えてこのファイルだけを流す（原因を直した後に元に戻し、
// git diff で指定が残っていないことを確かめる）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { countCodePoints } from '../../shared/validation/codePoints'
import { checkSearchInput, USER_SEARCH_MAX_CODE_POINTS } from './searchInput'

/** 前後に付ける空白（半角・全角・タブ・改行） */
const spaceArbitrary = fc.string({
  unit: fc.constantFrom(' ', '　', '\t', '\n'),
  maxLength: 5,
})

describe('checkSearchInput', () => {
  it('accepts exactly 254 code points and rejects 255', () => {
    expect(USER_SEARCH_MAX_CODE_POINTS).toBe(254)
    expect(checkSearchInput('a'.repeat(254))).toEqual({ ok: true, searchText: 'a'.repeat(254) })
    expect(checkSearchInput('a'.repeat(255))).toEqual({ ok: false, reason: 'tooLong' })
  })

  it('removes leading and trailing half-width and full-width spaces', () => {
    expect(checkSearchInput(' 　山田 太郎　 ')).toEqual({ ok: true, searchText: '山田 太郎' })
  })

  it('treats spaces only as no search', () => {
    expect(checkSearchInput('')).toEqual({ ok: true, searchText: '' })
    expect(checkSearchInput(' 　\t')).toEqual({ ok: true, searchText: '' })
  })

  it('counts a surrogate pair as one code point', () => {
    const emoji = '\u{1F600}'
    expect(checkSearchInput(emoji.repeat(254))).toEqual({ ok: true, searchText: emoji.repeat(254) })
    expect(checkSearchInput(emoji.repeat(255))).toEqual({ ok: false, reason: 'tooLong' })
  })

  it('counts after removing the spaces, so 254 characters with spaces around pass', () => {
    const value = `  ${'b'.repeat(254)}　`
    expect(checkSearchInput(value)).toEqual({ ok: true, searchText: 'b'.repeat(254) })
  })

  it('returns the trimmed value without changing inner characters', () => {
    expect(checkSearchInput(' a&b<c>"d\' ')).toEqual({ ok: true, searchText: 'a&b<c>"d\'' })
  })

  it('gives the same result when spaces are added around', () => {
    fc.assert(
      fc.property(
        fc.string({ maxLength: 300 }),
        spaceArbitrary,
        spaceArbitrary,
        (value, before, after) => {
          expect(checkSearchInput(`${before}${value}${after}`)).toEqual(checkSearchInput(value))
        },
      ),
    )
  })

  it('never returns a value longer than 254 code points', () => {
    fc.assert(
      fc.property(fc.string({ unit: 'grapheme', maxLength: 300 }), (value) => {
        const result = checkSearchInput(value)
        if (result.ok) {
          expect(countCodePoints(result.searchText)).toBeLessThanOrEqual(
            USER_SEARCH_MAX_CODE_POINTS,
          )
        }
      }),
    )
  })
})
