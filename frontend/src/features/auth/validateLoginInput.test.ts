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
// 空の入力の検査のテスト（BR8.2）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { isValidLoginInput, validateLoginInput } from './validateLoginInput'

describe('validateLoginInput', () => {
  it('rejects an empty email address and an empty password', () => {
    const problems = validateLoginInput({ email: '', password: '' })

    expect(problems.email).toBe('auth.login.emailRequired')
    expect(problems.password).toBe('auth.login.passwordRequired')
    expect(isValidLoginInput(problems)).toBe(false)
  })

  it('rejects an email address made of spaces only', () => {
    expect(validateLoginInput({ email: '   ', password: 'パスワード' }).email).toBe(
      'auth.login.emailRequired',
    )
  })

  it('accepts a filled form', () => {
    const problems = validateLoginInput({ email: 'user@example.com', password: 'p' })

    expect(isValidLoginInput(problems)).toBe(true)
  })

  it('never checks the length of the password', () => {
    expect(
      isValidLoginInput(validateLoginInput({ email: 'user@example.com', password: 'a' })),
    ).toBe(true)
  })

  it('accepts every non-blank pair and rejects every blank one', () => {
    fc.assert(
      fc.property(fc.string(), fc.string(), (email, password) => {
        const problems = validateLoginInput({ email, password })
        expect(problems.email === undefined).toBe(email.trim() !== '')
        expect(problems.password === undefined).toBe(password !== '')
      }),
      { numRuns: 100 },
    )
  })
})
