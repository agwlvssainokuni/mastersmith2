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
// OS の配色の購読（D4・W11）。matchMedia を扱えないブラウザ（と jsdom）は light として扱う
// （make-you-chic-ui の今の扱いと同じ。W11 の5）。

const DARK_QUERY = '(prefers-color-scheme: dark)'

function darkQuery(): MediaQueryList | null {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return null
  }
  return window.matchMedia(DARK_QUERY)
}

/** OS の配色が dark か。 */
export function getPrefersDark(): boolean {
  return darkQuery()?.matches ?? false
}

/** OS の配色の変化を受け取る。戻り値は受け取りをやめる関数。 */
export function subscribePrefersDark(listener: () => void): () => void {
  const query = darkQuery()
  if (query === null) {
    return () => undefined
  }
  query.addEventListener('change', listener)
  return () => query.removeEventListener('change', listener)
}
