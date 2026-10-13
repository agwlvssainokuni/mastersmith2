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
// ロール・グループの名前の確かめのテスト（Intent 261004-role-menu の U6 の D7、L9、NFR1.4、計画 2.3 の A19）。
// 失敗したときの再現の仕方: fast-check は失敗の報告に seed と path を出すため、その fc.assert(property) を
// fc.assert(property, { seed: <seed>, path: '<path>' }) に一時的に替えてこのファイルだけを流す。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { ADMIN_NAME_MAX_CODE_POINTS } from './limits'
import { remainingAdminNameLength, validateAdminName } from './validateAdminName'

/** 前後に付ける Unicode の White_Space（半角・全角・タブ・改行・U+2028・U+2029 を含む） */
const whiteSpaceArbitrary = fc.string({
  unit: fc.constantFrom(' ', '\u3000', '\t', '\n', '\u00a0', '\u2028', '\u2029', '\u0085'),
  maxLength: 4,
})

describe('validateAdminName', () => {
  it('returns required for an empty value and for white space only', () => {
    expect(validateAdminName('')).toBe('required')
    expect(validateAdminName('   ')).toBe('required')
    expect(validateAdminName('\u3000\u3000')).toBe('required')
    expect(validateAdminName('\t\n ')).toBe('required')
  })

  it('accepts 64 code points and rejects 65 as tooLong', () => {
    expect(ADMIN_NAME_MAX_CODE_POINTS).toBe(64)
    expect(validateAdminName('あ'.repeat(64))).toBeUndefined()
    expect(validateAdminName('あ'.repeat(65))).toBe('tooLong')
  })

  it('counts a surrogate pair as one code point', () => {
    expect(validateAdminName('😀'.repeat(64))).toBeUndefined()
    expect(validateAdminName('😀'.repeat(65))).toBe('tooLong')
  })

  it('judges the length after removing white space at both ends, including the full-width space', () => {
    expect(validateAdminName(`\u3000 ${'a'.repeat(64)} \u3000`)).toBeUndefined()
    expect(validateAdminName(` ${'a'.repeat(65)} `)).toBe('tooLong')
  })

  it('rejects Cc, U+2028 and U+2029 inside as invalidCharacter', () => {
    expect(validateAdminName('営業\n部')).toBe('invalidCharacter')
    expect(validateAdminName('営業\t部')).toBe('invalidCharacter')
    expect(validateAdminName('営業\u0000部')).toBe('invalidCharacter')
    expect(validateAdminName('営業\u007f部')).toBe('invalidCharacter')
    expect(validateAdminName('営業\u2028部')).toBe('invalidCharacter')
    expect(validateAdminName('営業\u2029部')).toBe('invalidCharacter')
  })

  it('accepts format characters (Cf) such as U+200B, unlike the display name rule', () => {
    expect(validateAdminName('営業\u200b部')).toBeUndefined()
    expect(validateAdminName('\ufeff営業')).toBeUndefined()
  })

  it('returns required for a name made only of format characters (Cf) and white space', () => {
    expect(validateAdminName('\u200b')).toBe('required')
    expect(validateAdminName('\u200b\u200b')).toBe('required')
    expect(validateAdminName('\ufeff')).toBe('required')
    expect(validateAdminName('\u2060')).toBe('required')
    expect(validateAdminName('\u200b\u3000\u200b')).toBe('required')
    expect(validateAdminName(' \u200b ')).toBe('required')
    expect(validateAdminName('\u200d\u00a0\u200c')).toBe('required')
  })

  it('checks the invisible-only rule after tooLong and invalidCharacter, as the server does', () => {
    expect(validateAdminName('\u200b'.repeat(65))).toBe('tooLong')
    expect(validateAdminName('\u200b\u0007\u200b')).toBe('invalidCharacter')
    expect(validateAdminName('\u200b'.repeat(64))).toBe('required')
  })

  it('returns required for any mix of format characters and white space, and accepts it with one visible character (property)', () => {
    const invisible = fc.string({
      unit: fc.constantFrom(
        '\u200b',
        '\u200c',
        '\u200d',
        '\u2060',
        '\ufeff',
        '\u00ad',
        ' ',
        '\u3000',
        '\u00a0',
        '\u2003',
      ),
      minLength: 1,
      maxLength: 63,
    })
    fc.assert(
      fc.property(
        invisible,
        fc.constantFrom('a', '営', '😀', '-', 'Ｓ'),
        fc.nat({ max: 63 }),
        (name, visible, at) => {
          expect(validateAdminName(name)).toBe('required')
          const index = Math.min(at, name.length)
          expect(
            validateAdminName(`${name.slice(0, index)}${visible}${name.slice(index)}`),
          ).toBeUndefined()
        },
      ),
    )
  })

  it('accepts inner spaces and symbols that the server accepts', () => {
    expect(validateAdminName('営業 部')).toBeUndefined()
    expect(validateAdminName('<script>&"\'')).toBeUndefined()
  })

  it('checks in the order of required, tooLong and invalidCharacter', () => {
    expect(validateAdminName(`${'あ'.repeat(65)}\n`)).toBe('tooLong')
  })

  it('gives the remaining length after removing white space at both ends', () => {
    expect(remainingAdminNameLength('')).toBe(64)
    expect(remainingAdminNameLength(` ${'a'.repeat(52)} `)).toBe(12)
    expect(remainingAdminNameLength('a'.repeat(66))).toBe(-2)
  })

  it('does not change the result when white space is added at both ends (property)', () => {
    fc.assert(
      fc.property(
        fc.string({ maxLength: 80 }),
        whiteSpaceArbitrary,
        whiteSpaceArbitrary,
        (name, before, after) => {
          expect(validateAdminName(`${before}${name}${after}`)).toBe(validateAdminName(name))
        },
      ),
    )
  })
})
