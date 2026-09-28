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
// 氏名の確かめのテスト（AC3.2.9、U2 の BR1.1〜BR1.4、NFR9.9）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { isWhiteSpaceCodePoint } from './codePoints'
import { DISPLAY_NAME_MAX_CODE_POINTS } from './limits'
import { validateDisplayName } from './validateDisplayName'

describe('validateDisplayName', () => {
  it('returns required for an empty value and for white space only', () => {
    expect(validateDisplayName('')).toBe('required')
    expect(validateDisplayName('   ')).toBe('required')
    expect(validateDisplayName('　　')).toBe('required')
    expect(validateDisplayName('\t \t')).toBe('required')
  })

  it('accepts 253 and 254 code points and rejects 255 as tooLong', () => {
    expect(DISPLAY_NAME_MAX_CODE_POINTS).toBe(254)
    expect(validateDisplayName('あ'.repeat(253))).toBeUndefined()
    expect(validateDisplayName('あ'.repeat(254))).toBeUndefined()
    expect(validateDisplayName('あ'.repeat(255))).toBe('tooLong')
    // 絵文字もコードポイントで数える
    expect(validateDisplayName('😀'.repeat(254))).toBeUndefined()
    expect(validateDisplayName('😀'.repeat(255))).toBe('tooLong')
  })

  it('judges the length after removing White_Space at both ends', () => {
    expect(validateDisplayName(` ${'あ'.repeat(254)}　`)).toBeUndefined()
  })

  it('returns invalidCharacter for line breaks, tabs and invisible characters inside', () => {
    expect(validateDisplayName('山田\n花子')).toBe('invalidCharacter')
    expect(validateDisplayName('山田\t花子')).toBe('invalidCharacter')
    expect(validateDisplayName('山田​花子')).toBe('invalidCharacter')
    expect(validateDisplayName('﻿山田')).toBe('invalidCharacter')
  })

  it('accepts a usual name, including inner spaces', () => {
    expect(validateDisplayName('山田 花子')).toBeUndefined()
    expect(validateDisplayName('山田　花子')).toBeUndefined()
    expect(validateDisplayName('hanako@example.test')).toBeUndefined()
  })

  it('checks in the order of required, tooLong and invalidCharacter', () => {
    expect(validateDisplayName(`${'あ'.repeat(255)}\n`)).toBe('tooLong')
    expect(validateDisplayName(`\n${'あ'.repeat(255)}`)).toBe('tooLong')
    expect(validateDisplayName('\n')).toBe('required')
  })

  it('accepts every name of 1 to 254 code points without Cc, Cf or White_Space', () => {
    const usable = fc
      .integer({ min: 0x21, max: 0x10ffff })
      .filter(
        (codePoint) =>
          (codePoint < 0xd800 || codePoint > 0xdfff) &&
          !isWhiteSpaceCodePoint(codePoint) &&
          !/[\p{Cc}\p{Cf}]/u.test(String.fromCodePoint(codePoint)),
      )
      .map((codePoint) => String.fromCodePoint(codePoint))
    fc.assert(
      fc.property(
        fc.array(usable, { minLength: 1, maxLength: DISPLAY_NAME_MAX_CODE_POINTS }),
        (chars) => validateDisplayName(chars.join('')) === undefined,
      ),
    )
  })
})
