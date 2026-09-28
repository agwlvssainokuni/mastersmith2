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
// 受け渡しのテスト（D13・W9、NFR2.1）。
import { beforeEach, describe, expect, it } from 'vitest'
import {
  clearLoginHandoff,
  handOffToLogin,
  resetLoginHandoff,
  takeLoginHandoff,
} from './loginHandoff'

const EMAIL = 'display-leak-check@example.com'

function storedValues(): string[] {
  const values: string[] = []
  for (const storage of [localStorage, sessionStorage]) {
    for (let i = 0; i < storage.length; i += 1) {
      const key = storage.key(i)
      if (key !== null) {
        values.push(`${key}=${storage.getItem(key) ?? ''}`)
      }
    }
  }
  return values
}

beforeEach(() => {
  resetLoginHandoff()
  localStorage.clear()
  sessionStorage.clear()
})

describe('loginHandoff', () => {
  it('has nothing before a hand-off', () => {
    expect(takeLoginHandoff()).toBeNull()
  })

  it('reads the handed-off email address', () => {
    handOffToLogin(EMAIL)

    expect(takeLoginHandoff()).toEqual({ email: EMAIL })
  })

  it('keeps the value when it is only read', () => {
    handOffToLogin(EMAIL)
    takeLoginHandoff()

    expect(takeLoginHandoff()).toEqual({ email: EMAIL })
  })

  it('has nothing after it is cleared or reset', () => {
    handOffToLogin(EMAIL)
    clearLoginHandoff()
    expect(takeLoginHandoff()).toBeNull()

    handOffToLogin(EMAIL)
    resetLoginHandoff()
    expect(takeLoginHandoff()).toBeNull()
  })

  it('puts the email address neither in the URL nor in the browser storage', () => {
    handOffToLogin(EMAIL)

    expect(window.location.href).not.toContain(EMAIL)
    expect(window.location.href).not.toContain(encodeURIComponent(EMAIL))
    expect(storedValues().some((value) => value.includes(EMAIL))).toBe(false)
  })
})
