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
// 新しいパスワードとその確かめのテスト（AC3.2.4、U2 の BR4.1、U3 の BR7.2、NFR9.9）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { PASSWORD_MAX_UTF8_BYTES, PASSWORD_MIN_CODE_POINTS } from './limits'
import { validateNewPassword, validatePasswordConfirmation } from './validatePassword'

describe('validateNewPassword', () => {
  it('returns required for an empty password', () => {
    expect(validateNewPassword('')).toBe('required')
  })

  it('rejects 11 code points as tooShort and accepts 12', () => {
    expect(PASSWORD_MIN_CODE_POINTS).toBe(12)
    expect(validateNewPassword('a'.repeat(11))).toBe('tooShort')
    expect(validateNewPassword('a'.repeat(12))).toBeUndefined()
    // 空白だけでも長さで判定する（前後の空白を除かない）
    expect(validateNewPassword(' '.repeat(12))).toBeUndefined()
  })

  it('accepts 72 bytes and rejects 73 bytes as tooLong', () => {
    expect(PASSWORD_MAX_UTF8_BYTES).toBe(72)
    expect(validateNewPassword('あ'.repeat(24))).toBeUndefined()
    expect(validateNewPassword(`${'あ'.repeat(24)}a`)).toBe('tooLong')
    expect(validateNewPassword('a'.repeat(72))).toBeUndefined()
    expect(validateNewPassword('a'.repeat(73))).toBe('tooLong')
  })

  it('counts emoji as one character each (AC3.2.4)', () => {
    expect(validateNewPassword('😀'.repeat(11))).toBe('tooShort')
    expect(validateNewPassword('😀'.repeat(12))).toBeUndefined()
    expect(validateNewPassword('😀'.repeat(19))).toBe('tooLong')
  })

  it('changes the result exactly at the boundaries of 12 code points and 72 bytes', () => {
    fc.assert(
      fc.property(fc.constantFrom('a', 'é', 'あ', '😀'), (char) => {
        const bytes = new TextEncoder().encode(char).length
        const below = validateNewPassword(char.repeat(PASSWORD_MIN_CODE_POINTS - 1))
        const at = validateNewPassword(char.repeat(PASSWORD_MIN_CODE_POINTS))
        const fits = Math.floor(PASSWORD_MAX_UTF8_BYTES / bytes)
        const upper =
          fits >= PASSWORD_MIN_CODE_POINTS ? validateNewPassword(char.repeat(fits)) : undefined
        const over = validateNewPassword(char.repeat(fits) + 'a'.repeat(bytes))
        return (
          below === 'tooShort' &&
          (at === undefined || at === 'tooLong') &&
          upper === undefined &&
          (fits + bytes < PASSWORD_MIN_CODE_POINTS || over === 'tooLong')
        )
      }),
    )
  })
})

describe('validatePasswordConfirmation', () => {
  it('returns required for an empty confirmation', () => {
    expect(validatePasswordConfirmation('password-1234', '')).toBe('required')
    expect(validatePasswordConfirmation('', '')).toBe('required')
  })

  it('returns mismatch for one different character and for different spaces', () => {
    expect(validatePasswordConfirmation('password-1234', 'password-1235')).toBe('mismatch')
    expect(validatePasswordConfirmation('password-1234', ' password-1234')).toBe('mismatch')
    expect(validatePasswordConfirmation('password-1234', 'password-1234 ')).toBe('mismatch')
  })

  it('does not normalize the strings (NFC and NFD differ)', () => {
    const nfc = 'パスワードがぎ'.normalize('NFC') + '12345'
    const nfd = nfc.normalize('NFD')
    expect(nfc).not.toBe(nfd)
    expect(validatePasswordConfirmation(nfc, nfd)).toBe('mismatch')
  })

  it('accepts the same string', () => {
    expect(validatePasswordConfirmation('password-1234', 'password-1234')).toBeUndefined()
  })
})
