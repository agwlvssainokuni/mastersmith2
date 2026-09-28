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
// フラグメントからトークンを取り出す関数のテスト（D1、NFR1.1、NFR9.9）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { readRegistrationToken } from './registrationToken'

describe('readRegistrationToken', () => {
  it('reads the token with or without the leading hash', () => {
    expect(readRegistrationToken('#token=abc')).toBe('abc')
    expect(readRegistrationToken('token=abc')).toBe('abc')
  })

  it('reads the token among other keys', () => {
    expect(readRegistrationToken('#a=1&token=abc&b=2')).toBe('abc')
    expect(readRegistrationToken('#tokens=x&token=abc')).toBe('abc')
  })

  it('decodes the URL encoding of the value', () => {
    expect(readRegistrationToken('#token=a%2Bb%3D%26c')).toBe('a+b=&c')
  })

  it('returns undefined when the token is missing or empty', () => {
    expect(readRegistrationToken('')).toBeUndefined()
    expect(readRegistrationToken('#')).toBeUndefined()
    expect(readRegistrationToken('#token=')).toBeUndefined()
    expect(readRegistrationToken('#token')).toBeUndefined()
    expect(readRegistrationToken('#other=abc')).toBeUndefined()
  })

  it('returns undefined for a broken percent encoding', () => {
    expect(readRegistrationToken('#token=%E3%81')).toBeUndefined()
    expect(readRegistrationToken('#token=%zz')).toBeUndefined()
  })

  it('restores any non-empty value encoded with encodeURIComponent', () => {
    const wellFormed = fc.string({ minLength: 1 }).filter((value) => {
      try {
        encodeURIComponent(value)
        return true
      } catch {
        return false
      }
    })
    fc.assert(
      fc.property(
        wellFormed,
        (value) => readRegistrationToken(`#token=${encodeURIComponent(value)}`) === value,
      ),
    )
  })
})
