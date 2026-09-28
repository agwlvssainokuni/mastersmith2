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
// OS の配色の購読のテスト（D4・W11）。
import { afterEach, describe, expect, it, vi } from 'vitest'
import { getPrefersDark, subscribePrefersDark } from './colorScheme'
import { installFakeColorScheme } from './testing/fakeColorScheme'

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('colorScheme', () => {
  it('reads the current OS color scheme', () => {
    const os = installFakeColorScheme(true)
    expect(getPrefersDark()).toBe(true)
    os.setDark(false)
    expect(getPrefersDark()).toBe(false)
  })

  it('announces changes of the OS color scheme', () => {
    const os = installFakeColorScheme(false)
    const listener = vi.fn()
    subscribePrefersDark(listener)

    os.setDark(true)

    expect(listener).toHaveBeenCalledTimes(1)
  })

  it('stops announcing after unsubscribing', () => {
    const os = installFakeColorScheme(false)
    const listener = vi.fn()
    const unsubscribe = subscribePrefersDark(listener)
    unsubscribe()

    os.setDark(true)

    expect(listener).not.toHaveBeenCalled()
    expect(os.listenerCount()).toBe(0)
  })

  it('treats a browser without matchMedia as light', () => {
    vi.stubGlobal('matchMedia', undefined)
    expect(getPrefersDark()).toBe(false)
    const unsubscribe = subscribePrefersDark(() => undefined)
    expect(() => unsubscribe()).not.toThrow()
  })
})
