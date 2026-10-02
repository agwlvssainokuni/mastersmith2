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
// 氏名・言語の入力の確かめと文言の鍵のテスト（functional-spec.md の D16・7.6、frontend-components.md の 7節）。
import { describe, expect, it } from 'vitest'
import {
  checkProfileName,
  PROFILE_FIELDS,
  profileMessageKey,
  toProfileReason,
} from './profileInput'

describe('profileInput', () => {
  it('checks the name with the three screen reasons in order', () => {
    expect(checkProfileName('山田 太郎')).toBeUndefined()
    expect(checkProfileName(' 　 ')).toBe('required')
    expect(checkProfileName('a'.repeat(255))).toBe('tooLong')
    expect(checkProfileName('山田\u0007太郎')).toBe('invalidCharacter')
    expect(checkProfileName(`  ${'a'.repeat(254)}  `)).toBeUndefined()
  })

  it('maps each screen reason of the name to its message key', () => {
    expect(profileMessageKey('displayName', 'required')).toBe('useradmin.edit.nameRequired')
    expect(profileMessageKey('displayName', 'tooLong')).toBe('useradmin.edit.nameTooLong')
    expect(profileMessageKey('displayName', 'invalidCharacter')).toBe(
      'useradmin.edit.nameInvalidCharacter',
    )
  })

  it('maps the server reasons to the same kinds as the screen reasons', () => {
    expect(toProfileReason('REQUIRED')).toBe('required')
    expect(toProfileReason('TOO_LONG')).toBe('tooLong')
    expect(toProfileReason('INVALID_CHARACTER')).toBe('invalidCharacter')
    expect(toProfileReason('INVALID_VALUE')).toBe('invalidValue')
  })

  it('maps a language error from the server to the language message', () => {
    expect(profileMessageKey('language', toProfileReason('INVALID_VALUE'))).toBe(
      'useradmin.edit.languageInvalid',
    )
    expect(profileMessageKey('language', toProfileReason('REQUIRED'))).toBe(
      'useradmin.edit.languageInvalid',
    )
  })

  it('does not tie an unknown reason to a field', () => {
    expect(toProfileReason('SOMETHING_NEW')).toBe('unknown')
    expect(toProfileReason('constructor')).toBe('unknown')
    expect(profileMessageKey('displayName', 'unknown')).toBeUndefined()
    expect(profileMessageKey('language', 'unknown')).toBeUndefined()
    expect(profileMessageKey('displayName', 'invalidValue')).toBeUndefined()
  })

  it('knows only the two fields of the request', () => {
    expect(PROFILE_FIELDS).toEqual(['displayName', 'language'])
  })
})
