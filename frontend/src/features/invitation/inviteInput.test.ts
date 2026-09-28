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
// 空の判定のテスト（D8、NFR9.7）。空白だけの文字列は空、前後に空白があっても中身があれば空でない。値は正規化しない。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { isBlankEmail } from './inviteInput'

const WHITESPACE = [' ', '　', '\t', '\n', '\r', ' ']

describe('isBlankEmail', () => {
  it('treats an empty value as blank', () => {
    expect(isBlankEmail('')).toBe(true)
  })

  it('treats half-width and full-width spaces, tabs and line breaks as blank', () => {
    expect(isBlankEmail('   ')).toBe(true)
    expect(isBlankEmail('　　')).toBe(true)
    expect(isBlankEmail('\t\n\r ')).toBe(true)
  })

  it('does not treat a value with spaces around it as blank', () => {
    expect(isBlankEmail(' hanako@example.test ')).toBe(false)
    expect(isBlankEmail('　a')).toBe(false)
  })

  it('does not treat a value that is not an email address as blank (the server judges the format)', () => {
    expect(isBlankEmail('not-an-email')).toBe(false)
    expect(isBlankEmail('a\nb')).toBe(false)
  })

  it('treats every string made only of whitespace as blank', () => {
    fc.assert(
      fc.property(fc.array(fc.constantFrom(...WHITESPACE), { maxLength: 50 }), (chars) => {
        expect(isBlankEmail(chars.join(''))).toBe(true)
      }),
    )
  })
})
