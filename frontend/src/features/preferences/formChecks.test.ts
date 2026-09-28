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
// 送る前の画面の確かめのテスト（functional-spec.md の W11、AC4.1.7・AC5.1.3、NFR9.7）。共用の関数は差し替えずに通す。
import { describe, expect, it } from 'vitest'
import { checkPasswordChangeForm, checkPreferencesForm } from './formChecks'

const VALID_PASSWORD = 'correct-horse-12'

function password(newPassword: string, confirmation = newPassword, current = 'old-secret') {
  return checkPasswordChangeForm({
    currentPassword: current,
    newPassword,
    newPasswordConfirmation: confirmation,
  })
}

describe('checkPreferencesForm', () => {
  it('has no problem for a valid name', () => {
    expect(checkPreferencesForm({ displayName: '山田 花子' })).toEqual({
      reasons: {},
      firstInvalid: undefined,
    })
    expect(checkPreferencesForm({ displayName: 'hanako@example.test' }).firstInvalid).toBe(
      undefined,
    )
  })

  it('rejects a name of only white space as required', () => {
    for (const name of ['', '   ', '　　', '\t']) {
      expect(checkPreferencesForm({ displayName: name })).toEqual({
        reasons: { displayName: 'required' },
        firstInvalid: 'displayName',
      })
    }
  })

  it('accepts 254 code points and rejects 255 as tooLong', () => {
    expect(checkPreferencesForm({ displayName: 'あ'.repeat(254) }).firstInvalid).toBeUndefined()
    expect(checkPreferencesForm({ displayName: '😀'.repeat(254) }).firstInvalid).toBeUndefined()
    expect(checkPreferencesForm({ displayName: 'あ'.repeat(255) }).reasons.displayName).toBe(
      'tooLong',
    )
    // 前後の空白は数えない
    expect(
      checkPreferencesForm({ displayName: ` ${'a'.repeat(254)} ` }).firstInvalid,
    ).toBeUndefined()
  })

  it('rejects inner line breaks and zero width spaces as invalidCharacter', () => {
    for (const name of ['山田\n花子', '山田\t花子', '山田​花子']) {
      expect(checkPreferencesForm({ displayName: name }).reasons.displayName).toBe(
        'invalidCharacter',
      )
    }
  })
})

describe('checkPasswordChangeForm', () => {
  it('has no problem for valid values', () => {
    expect(password(VALID_PASSWORD)).toEqual({ reasons: {}, firstInvalid: undefined })
  })

  it('checks only emptiness for the current password', () => {
    expect(password(VALID_PASSWORD, VALID_PASSWORD, '')).toEqual({
      reasons: { currentPassword: 'required' },
      firstInvalid: 'currentPassword',
    })
    // 規則を当てない（11 文字・長すぎる値でも誤りにしない）
    expect(password(VALID_PASSWORD, VALID_PASSWORD, 'a'.repeat(11)).firstInvalid).toBeUndefined()
    expect(password(VALID_PASSWORD, VALID_PASSWORD, 'a'.repeat(100)).firstInvalid).toBeUndefined()
  })

  it('checks the boundaries of the new password (AC5.1.3)', () => {
    expect(password('').reasons.newPassword).toBe('required')
    expect(password('a'.repeat(11)).reasons.newPassword).toBe('tooShort')
    expect(password('a'.repeat(12)).firstInvalid).toBeUndefined()
    expect(password('あ'.repeat(24)).firstInvalid).toBeUndefined()
    expect(password(`${'あ'.repeat(24)}a`).reasons.newPassword).toBe('tooLong')
    expect(password('😀'.repeat(11)).reasons.newPassword).toBe('tooShort')
    expect(password('😀'.repeat(12)).firstInvalid).toBeUndefined()
  })

  it('checks the confirmation', () => {
    expect(password(VALID_PASSWORD, '').reasons.newPasswordConfirmation).toBe('required')
    expect(password(VALID_PASSWORD, `${VALID_PASSWORD} `).reasons.newPasswordConfirmation).toBe(
      'mismatch',
    )
  })

  it('points to the first invalid field in the order of the screen', () => {
    expect(
      checkPasswordChangeForm({
        currentPassword: '',
        newPassword: '',
        newPasswordConfirmation: '',
      }),
    ).toEqual({
      reasons: {
        currentPassword: 'required',
        newPassword: 'required',
        newPasswordConfirmation: 'required',
      },
      firstInvalid: 'currentPassword',
    })
    expect(password('short', 'other').firstInvalid).toBe('newPassword')
    expect(password(VALID_PASSWORD, 'other').firstInvalid).toBe('newPasswordConfirmation')
  })
})
