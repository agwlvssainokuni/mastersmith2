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
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { resolveLanguage } from './resolveLanguage'

describe('resolveLanguage', () => {
  it('returns English for en-US', () => {
    expect(resolveLanguage(['en-US'])).toBe('en')
  })

  it('returns Japanese for ja-JP', () => {
    expect(resolveLanguage(['ja-JP'])).toBe('ja')
  })

  it('uses the first supported language in preference order', () => {
    expect(resolveLanguage(['fr', 'en', 'ja'])).toBe('en')
    expect(resolveLanguage(['de-DE', 'ja-JP', 'en-US'])).toBe('ja')
  })

  it('falls back to Japanese when nothing matches or the list is empty', () => {
    expect(resolveLanguage(['fr'])).toBe('ja')
    expect(resolveLanguage([])).toBe('ja')
    expect(resolveLanguage(['', '-'])).toBe('ja')
  })

  it('ignores letter case', () => {
    expect(resolveLanguage(['EN-gb'])).toBe('en')
    expect(resolveLanguage(['Ja'])).toBe('ja')
  })

  it('does not treat languages that only start with the same letters as a match', () => {
    expect(resolveLanguage(['jam', 'eng'])).toBe('ja')
  })

  it('always returns ja or en and picks the first matching entry for any list', () => {
    const tag = fc.oneof(
      fc.constantFrom('ja', 'en', 'ja-JP', 'en-US', 'fr', 'de-DE', 'zh-Hans'),
      fc.string(),
    )
    fc.assert(
      fc.property(fc.array(tag), (languages) => {
        const result = resolveLanguage(languages)
        expect(['ja', 'en']).toContain(result)
        const first = languages
          .map((l) => l.split('-')[0]?.trim().toLowerCase())
          .find((p) => p === 'ja' || p === 'en')
        expect(result).toBe(first ?? 'ja')
      }),
    )
  })
})
