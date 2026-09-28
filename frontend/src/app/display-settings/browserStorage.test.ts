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
// ブラウザの保存のテスト（4.1・4.2・D5〜D7、NFR2.2）。jsdom の localStorage をそのまま使う。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  DESIGN_SYSTEM_FONT_SIZE_KEY,
  DESIGN_SYSTEM_THEME_KEY,
  DISPLAY_SETTINGS_KEY,
  readStoredDisplaySettings,
  rewriteDesignSystemCopies,
  writeStoredDisplaySettings,
  writeStoredLanguage,
} from './browserStorage'

beforeEach(() => {
  localStorage.clear()
})

afterEach(() => {
  localStorage.clear()
})

describe('browserStorage', () => {
  it('saves the three axes and reads them back', () => {
    writeStoredDisplaySettings({ language: 'en', theme: 'dark', fontSize: 'lg' })

    expect(readStoredDisplaySettings()).toEqual({ language: 'en', theme: 'dark', fontSize: 'lg' })
  })

  it('writes only the three item names of the display settings', () => {
    writeStoredDisplaySettings({
      language: 'ja',
      theme: 'system',
      fontSize: 'sm',
      token: 'test-access-token-for-display',
    } as never)

    const stored = JSON.parse(localStorage.getItem(DISPLAY_SETTINGS_KEY) ?? '{}') as object
    expect(Object.keys(stored).sort()).toEqual(['fontSize', 'language', 'theme'])
    expect(localStorage.getItem(DISPLAY_SETTINGS_KEY)).not.toContain('test-access-token')
  })

  it('saves only the language, keeping the other items and leaving missing items missing', () => {
    writeStoredDisplaySettings({ language: 'ja', theme: 'dark', fontSize: 'lg' })
    expect(writeStoredLanguage('en')).toEqual({ language: 'en', theme: 'dark', fontSize: 'lg' })

    localStorage.setItem(DISPLAY_SETTINGS_KEY, '{"theme":"light"}')
    writeStoredLanguage('ja')
    expect(JSON.parse(localStorage.getItem(DISPLAY_SETTINGS_KEY) ?? '{}')).toEqual({
      language: 'ja',
      theme: 'light',
    })
  })

  it('rewrites the design system copies from the U4 key before the first render', () => {
    writeStoredDisplaySettings({ language: 'ja', theme: 'dark', fontSize: 'lg' })
    localStorage.setItem(DESIGN_SYSTEM_THEME_KEY, 'light')
    localStorage.setItem(DESIGN_SYSTEM_FONT_SIZE_KEY, 'sm')

    rewriteDesignSystemCopies(false)

    expect(localStorage.getItem(DESIGN_SYSTEM_THEME_KEY)).toBe('dark')
    expect(localStorage.getItem(DESIGN_SYSTEM_FONT_SIZE_KEY)).toBe('lg')
  })

  it('rewrites the copies with the OS color scheme and md when the U4 key is missing or broken', () => {
    rewriteDesignSystemCopies(true)
    expect(localStorage.getItem(DESIGN_SYSTEM_THEME_KEY)).toBe('dark')
    expect(localStorage.getItem(DESIGN_SYSTEM_FONT_SIZE_KEY)).toBe('md')

    localStorage.setItem(DISPLAY_SETTINGS_KEY, 'not-json')
    rewriteDesignSystemCopies(false)
    expect(localStorage.getItem(DESIGN_SYSTEM_THEME_KEY)).toBe('light')

    localStorage.setItem(DISPLAY_SETTINGS_KEY, '{"theme":"system"}')
    rewriteDesignSystemCopies(true)
    expect(localStorage.getItem(DESIGN_SYSTEM_THEME_KEY)).toBe('dark')
  })

  it('never writes the brand and font family keys of the design system', () => {
    writeStoredDisplaySettings({ language: 'ja', theme: 'dark', fontSize: 'lg' })
    writeStoredLanguage('en')
    rewriteDesignSystemCopies(false)

    expect(localStorage.getItem('design-system-brand')).toBeNull()
    expect(localStorage.getItem('design-system-font-family')).toBeNull()
  })

  it('does not throw when the browser storage throws', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new DOMException('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new DOMException('QuotaExceededError')
    })

    expect(readStoredDisplaySettings()).toEqual({})
    expect(() =>
      writeStoredDisplaySettings({ language: 'ja', theme: 'dark', fontSize: 'lg' }),
    ).not.toThrow()
    expect(writeStoredLanguage('en')).toEqual({ language: 'en' })
    expect(() => rewriteDesignSystemCopies(true)).not.toThrow()
  })
})
