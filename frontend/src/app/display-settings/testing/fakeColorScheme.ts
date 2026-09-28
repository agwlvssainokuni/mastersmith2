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
// OS の配色の差し替え（テストからだけ使う）。jsdom には matchMedia が無いため、
// `prefers-color-scheme: dark` の今の値を持ち、変化の知らせを送れる偽の matchMedia を置く。
import { vi } from 'vitest'

/** 偽の OS の配色の操作口 */
export interface FakeColorScheme {
  /** OS の配色を変え、購読している相手に知らせる */
  setDark: (dark: boolean) => void
  /** 今、変化の知らせを受けている相手の数 */
  listenerCount: () => number
}

/** window.matchMedia を偽物に差し替える（vi.unstubAllGlobals で戻る）。 */
export function installFakeColorScheme(initialDark: boolean): FakeColorScheme {
  let dark = initialDark
  const listeners = new Set<(event: MediaQueryListEvent) => void>()
  const matchMedia = (query: string): MediaQueryList => {
    const isDarkQuery = query.includes('prefers-color-scheme: dark')
    const list = {
      media: query,
      get matches() {
        return isDarkQuery && dark
      },
      onchange: null,
      addEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => {
        if (isDarkQuery) {
          listeners.add(listener)
        }
      },
      removeEventListener: (_type: string, listener: (event: MediaQueryListEvent) => void) => {
        listeners.delete(listener)
      },
      addListener: () => undefined,
      removeListener: () => undefined,
      dispatchEvent: () => true,
    }
    return list as unknown as MediaQueryList
  }
  vi.stubGlobal('matchMedia', matchMedia)
  return {
    setDark: (next) => {
      dark = next
      for (const listener of listeners) {
        listener({ matches: next, media: '(prefers-color-scheme: dark)' } as MediaQueryListEvent)
      }
    },
    listenerCount: () => listeners.size,
  }
}
