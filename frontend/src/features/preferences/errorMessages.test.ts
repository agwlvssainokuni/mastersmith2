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
// 理由の寄せ方と文言の鍵の選び方のテスト（security-design.md の 3.3、NFR8.2、D13）。
import { describe, expect, it } from 'vitest'
import {
  CURRENT_MISMATCH_KEY,
  fieldMessageKey,
  FORM_INVALID_KEY,
  LOAD_FAILED_KEY,
  PASSWORD_FAILED_KEY,
  SAVE_FAILED_KEY,
  toFieldReason,
  type FieldReason,
  type PreferencesFormField,
} from './errorMessages'
import { preferencesMessages } from './messages'

const ALL_FIELDS: readonly PreferencesFormField[] = [
  'displayName',
  'language',
  'theme',
  'fontSize',
  'currentPassword',
  'newPassword',
  'newPasswordConfirmation',
]

const ALL_REASONS: readonly FieldReason[] = [
  'required',
  'tooShort',
  'tooLong',
  'invalidCharacter',
  'mismatch',
  'invalidValue',
  'unknown',
]

describe('toFieldReason', () => {
  it('maps the six reasons of the server', () => {
    expect(toFieldReason('REQUIRED')).toBe('required')
    expect(toFieldReason('TOO_SHORT')).toBe('tooShort')
    expect(toFieldReason('TOO_LONG')).toBe('tooLong')
    expect(toFieldReason('INVALID_CHARACTER')).toBe('invalidCharacter')
    expect(toFieldReason('MISMATCH')).toBe('mismatch')
    expect(toFieldReason('INVALID_VALUE')).toBe('invalidValue')
  })

  it('maps unknown values to unknown, including lower case and prototype names', () => {
    for (const value of ['', 'required', 'SOMETHING_NEW', 'toString', '__proto__', 'constructor']) {
      expect(toFieldReason(value)).toBe('unknown')
    }
  })
})

describe('fieldMessageKey', () => {
  it('chooses the key of the table for each field and reason', () => {
    expect(fieldMessageKey('displayName', 'required')).toBe('preferences.displayName.required')
    expect(fieldMessageKey('displayName', 'tooLong')).toBe('preferences.displayName.tooLong')
    expect(fieldMessageKey('displayName', 'invalidCharacter')).toBe(
      'preferences.displayName.invalidCharacter',
    )
    expect(fieldMessageKey('currentPassword', 'required')).toBe(
      'preferences.password.currentRequired',
    )
    expect(fieldMessageKey('newPassword', 'required')).toBe('preferences.password.newRequired')
    expect(fieldMessageKey('newPassword', 'tooShort')).toBe('preferences.password.tooShort')
    expect(fieldMessageKey('newPassword', 'tooLong')).toBe('preferences.password.tooLong')
    expect(fieldMessageKey('newPasswordConfirmation', 'required')).toBe(
      'preferences.password.confirmRequired',
    )
    expect(fieldMessageKey('newPasswordConfirmation', 'mismatch')).toBe(
      'preferences.password.mismatch',
    )
  })

  it('uses the general messages for pairs outside the table and for unknown', () => {
    expect(fieldMessageKey('displayName', 'unknown')).toBe('preferences.field.invalid')
    expect(fieldMessageKey('displayName', 'mismatch')).toBe('preferences.field.invalid')
    expect(fieldMessageKey('currentPassword', 'tooShort')).toBe('preferences.field.invalid')
    expect(fieldMessageKey('newPassword', 'invalidValue')).toBe('preferences.field.invalid')
    for (const field of ['language', 'theme', 'fontSize'] as const) {
      for (const reason of ALL_REASONS) {
        expect(fieldMessageKey(field, reason)).toBe('preferences.choice.invalid')
      }
    }
  })

  it('has every key of the table and the notices in Japanese and in English, not empty', () => {
    const keys = new Set<string>([
      LOAD_FAILED_KEY,
      SAVE_FAILED_KEY,
      PASSWORD_FAILED_KEY,
      FORM_INVALID_KEY,
      CURRENT_MISMATCH_KEY,
    ])
    for (const field of ALL_FIELDS) {
      for (const reason of ALL_REASONS) {
        keys.add(fieldMessageKey(field, reason))
      }
    }
    expect(keys.size).toBeGreaterThanOrEqual(16)
    for (const key of keys) {
      expect(preferencesMessages.ja[key]?.trim(), `ja ${key}`).toBeTruthy()
      expect(preferencesMessages.en[key]?.trim(), `en ${key}`).toBeTruthy()
    }
  })
})
