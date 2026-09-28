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
// フォームの誤りを集める関数のテスト（W7、D8）。
import { describe, expect, it } from 'vitest'
import { registrationMessages } from './messages'
import { checkRegistrationForm } from './formProblems'

const valid = {
  displayName: '招待 花子',
  password: 'password-1234',
  passwordConfirmation: 'password-1234',
}

describe('checkRegistrationForm', () => {
  it('returns no problems for valid values', () => {
    expect(checkRegistrationForm(valid)).toEqual({ problems: {}, firstInvalid: undefined })
  })

  it('returns the message key of a single problem', () => {
    expect(checkRegistrationForm({ ...valid, displayName: ' ' })).toEqual({
      problems: { displayName: 'registration.displayName.required' },
      firstInvalid: 'displayName',
    })
    expect(
      checkRegistrationForm({
        ...valid,
        password: 'a'.repeat(11),
        passwordConfirmation: 'a'.repeat(11),
      }),
    ).toEqual({
      problems: { password: 'registration.password.tooShort' },
      firstInvalid: 'password',
    })
  })

  it('returns the mismatch key for a different confirmation', () => {
    expect(checkRegistrationForm({ ...valid, passwordConfirmation: 'password-1235' })).toEqual({
      problems: { passwordConfirmation: 'registration.passwordConfirmation.mismatch' },
      firstInvalid: 'passwordConfirmation',
    })
  })

  it('chooses the first field in the order of name, password and confirmation', () => {
    const all = checkRegistrationForm({
      displayName: 'あ'.repeat(255),
      password: '',
      passwordConfirmation: '',
    })
    expect(all.problems).toEqual({
      displayName: 'registration.displayName.tooLong',
      password: 'registration.password.required',
      passwordConfirmation: 'registration.passwordConfirmation.required',
    })
    expect(all.firstInvalid).toBe('displayName')
    const later = checkRegistrationForm({
      ...valid,
      password: `${'あ'.repeat(24)}a`,
      passwordConfirmation: '',
    })
    expect(later.firstInvalid).toBe('password')
  })

  it('uses only keys that have ja and en messages', () => {
    const keys = [
      checkRegistrationForm({ displayName: '', password: '', passwordConfirmation: '' }),
      checkRegistrationForm({ displayName: 'a\nb', password: 'x', passwordConfirmation: 'y' }),
      checkRegistrationForm({
        displayName: 'あ'.repeat(255),
        password: 'a'.repeat(73),
        passwordConfirmation: 'a',
      }),
    ].flatMap((check) => Object.values(check.problems))
    expect(keys.length).toBeGreaterThanOrEqual(8)
    for (const key of keys) {
      expect(registrationMessages.ja[key]).toBeTruthy()
      expect(registrationMessages.en[key]).toBeTruthy()
    }
  })
})
