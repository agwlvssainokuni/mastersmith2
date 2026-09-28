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
// 解き方の純粋な関数のテスト（D1・D2・D4・D7、NFR9.2・NFR9.3・NFR9.9）。
// 性質ベースのテスト（fast-check）は、失敗したときに seed と path を出力に示す。再現はその値を
// fc.assert の第2引数に一時的に書いて行う。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import {
  BRAND_COLORS,
  DISPLAY_LANGUAGES,
  FONT_FAMILIES,
  FONT_SIZES,
  THEME_CHOICES,
  type PartialDisplaySettings,
} from './displaySettingsTypes'
import {
  decideScreenSettings,
  isDisplayLanguage,
  parseAppearance,
  parseStoredDisplaySettings,
  resolveTheme,
  validateUserDisplaySettings,
  type ScreenSettingsInput,
} from './resolveDisplaySettings'

const loginA = { loggedIn: true }
const loginB = { loggedIn: true }
const guest = { loggedIn: false }

function input(overrides: Partial<ScreenSettingsInput>): ScreenSettingsInput {
  return {
    loggedIn: false,
    binding: guest,
    stored: {},
    browserLanguages: ['ja-JP'],
    prefersDark: false,
    ...overrides,
  }
}

const anyValue = fc.oneof(
  fc.string(),
  fc.constantFrom(...DISPLAY_LANGUAGES, ...THEME_CHOICES, ...FONT_SIZES),
  fc.constant(null),
  fc.integer(),
)
const partialSettings: fc.Arbitrary<PartialDisplaySettings> = fc.record(
  { language: anyValue, theme: anyValue, fontSize: anyValue },
  { requiredKeys: [] },
) as fc.Arbitrary<PartialDisplaySettings>

describe('parseStoredDisplaySettings', () => {
  it('treats a missing, broken or non-object value as nothing stored', () => {
    for (const raw of [null, '{', 'not-json', '[]', '1', 'null', '"dark"']) {
      expect(parseStoredDisplaySettings(raw)).toEqual({})
    }
  })

  it('keeps valid items, drops unknown values and ignores unknown items', () => {
    expect(parseStoredDisplaySettings('{"language":"en","theme":"dark","fontSize":"lg"}')).toEqual({
      language: 'en',
      theme: 'dark',
      fontSize: 'lg',
    })
    expect(parseStoredDisplaySettings('{"theme":"blue","fontSize":"xl","language":"fr"}')).toEqual(
      {},
    )
    expect(parseStoredDisplaySettings('{"theme":"Dark","fontSize":"sm"}')).toEqual({
      fontSize: 'sm',
    })
    expect(parseStoredDisplaySettings('{"theme":"dark","token":"x"}')).toEqual({ theme: 'dark' })
  })

  it('never throws and only accepts allowed values for any string (property)', () => {
    fc.assert(
      fc.property(fc.oneof(fc.string(), fc.json()), (raw) => {
        const parsed = parseStoredDisplaySettings(raw)
        expect(
          Object.keys(parsed).every((key) => ['language', 'theme', 'fontSize'].includes(key)),
        ).toBe(true)
        if (parsed.language !== undefined) expect(DISPLAY_LANGUAGES).toContain(parsed.language)
        if (parsed.theme !== undefined) expect(THEME_CHOICES).toContain(parsed.theme)
        if (parsed.fontSize !== undefined) expect(FONT_SIZES).toContain(parsed.fontSize)
      }),
    )
  })
})

describe('resolveTheme', () => {
  it('resolves system with the OS color scheme and keeps light and dark as they are', () => {
    expect(resolveTheme('system', true)).toBe('dark')
    expect(resolveTheme('system', false)).toBe('light')
    expect(resolveTheme('light', true)).toBe('light')
    expect(resolveTheme('dark', false)).toBe('dark')
  })

  it('always gives light or dark (property)', () => {
    fc.assert(
      fc.property(fc.constantFrom(...THEME_CHOICES), fc.boolean(), (choice, dark) => {
        const resolved = resolveTheme(choice, dark)
        expect(['light', 'dark']).toContain(resolved)
        if (choice !== 'system') expect(resolved).toBe(choice)
      }),
    )
  })
})

describe('validateUserDisplaySettings', () => {
  it('returns undefined when the login state has no display settings', () => {
    expect(validateUserDisplaySettings(undefined)).toBeUndefined()
    expect(validateUserDisplaySettings(null)).toBeUndefined()
    expect(validateUserDisplaySettings([])).toBeUndefined()
  })

  it('drops only the items out of the contract', () => {
    expect(validateUserDisplaySettings({ language: 'en', theme: 'blue', fontSize: 'lg' })).toEqual({
      language: 'en',
      fontSize: 'lg',
    })
  })
})

describe('decideScreenSettings', () => {
  it('uses the stored values before login and the defaults for missing axes', () => {
    expect(decideScreenSettings(input({ stored: { theme: 'dark' } }))).toEqual({
      language: 'ja',
      theme: 'dark',
      fontSize: 'md',
      resolvedTheme: 'dark',
    })
    expect(decideScreenSettings(input({ browserLanguages: ['en-US'], prefersDark: true }))).toEqual(
      { language: 'en', theme: 'system', fontSize: 'md', resolvedTheme: 'dark' },
    )
  })

  it('uses the user settings after login and never the previous stored values', () => {
    const settings = decideScreenSettings(
      input({
        loggedIn: true,
        binding: loginA,
        stored: { language: 'en', theme: 'dark', fontSize: 'lg' },
        userPreferences: { theme: 'light' },
      }),
    )
    expect(settings).toEqual({
      language: 'ja',
      theme: 'light',
      fontSize: 'md',
      resolvedTheme: 'light',
    })
  })

  it('keeps the stored values after login when the login state has no display settings', () => {
    expect(
      decideScreenSettings(input({ loggedIn: true, binding: loginA, stored: { fontSize: 'lg' } }))
        .fontSize,
    ).toBe('lg')
  })

  it('prefers the saved user settings bound to the current login state', () => {
    const base = {
      loggedIn: true,
      userPreferences: { language: 'ja', theme: 'light', fontSize: 'sm' } as const,
      savedUser: {
        binding: loginA,
        value: { language: 'en', theme: 'dark', fontSize: 'lg' } as const,
      },
    }
    expect(decideScreenSettings(input({ ...base, binding: loginA })).language).toBe('en')
    expect(decideScreenSettings(input({ ...base, binding: loginB })).language).toBe('ja')
  })

  it('replaces only the axes of a preview bound to the current login state', () => {
    const preview = { binding: guest, value: { theme: 'dark' } as const }
    expect(
      decideScreenSettings(input({ stored: { theme: 'light', fontSize: 'lg' }, preview })),
    ).toEqual({ language: 'ja', theme: 'dark', fontSize: 'lg', resolvedTheme: 'dark' })
    expect(
      decideScreenSettings(input({ loggedIn: true, binding: loginA, userPreferences: {}, preview }))
        .theme,
    ).toBe('system')
  })

  it('always gives allowed values and keeps the applied value on axes without preview (property)', () => {
    fc.assert(
      fc.property(
        fc.boolean(),
        partialSettings,
        fc.option(partialSettings, { nil: undefined }),
        fc.option(partialSettings, { nil: undefined }),
        fc.array(fc.string(), { maxLength: 3 }),
        fc.boolean(),
        (loggedIn, stored, prefs, previewValue, languages, dark) => {
          const settings = decideScreenSettings(
            input({
              loggedIn,
              binding: loginA,
              stored,
              userPreferences: prefs,
              preview: previewValue ? { binding: loginA, value: previewValue } : null,
              browserLanguages: languages,
              prefersDark: dark,
            }),
          )
          expect(DISPLAY_LANGUAGES).toContain(settings.language)
          expect(THEME_CHOICES).toContain(settings.theme)
          expect(FONT_SIZES).toContain(settings.fontSize)
          expect(['light', 'dark']).toContain(settings.resolvedTheme)
          const withoutPreview = decideScreenSettings(
            input({
              loggedIn,
              binding: loginA,
              stored,
              userPreferences: prefs,
              browserLanguages: languages,
              prefersDark: dark,
            }),
          )
          if (!THEME_CHOICES.includes(previewValue?.theme as never)) {
            expect(settings.theme).toBe(withoutPreview.theme)
          }
          if (!FONT_SIZES.includes(previewValue?.fontSize as never)) {
            expect(settings.fontSize).toBe(withoutPreview.fontSize)
          }
        },
      ),
    )
  })

  it('always decides the screen language as ja or en (property)', () => {
    fc.assert(
      fc.property(partialSettings, fc.array(fc.string(), { maxLength: 4 }), (stored, languages) => {
        const { language } = decideScreenSettings(input({ stored, browserLanguages: languages }))
        expect(isDisplayLanguage(language)).toBe(true)
      }),
    )
  })
})

describe('parseAppearance', () => {
  it('keeps the four brand colors and the two font families', () => {
    for (const brandColor of BRAND_COLORS) {
      for (const fontFamily of FONT_FAMILIES) {
        expect(parseAppearance({ brandColor, fontFamily })).toEqual({ brandColor, fontFamily })
      }
    }
  })

  it('drops disallowed values item by item and ignores extra items', () => {
    expect(parseAppearance({ brandColor: 'red', fontFamily: 'serif' })).toEqual({
      fontFamily: 'serif',
    })
    expect(parseAppearance({ brandColor: 'BLUE', fontFamily: 'mono' })).toEqual({})
    expect(parseAppearance({ brandColor: ' blue' })).toEqual({})
    expect(parseAppearance({ brandColor: 'green', extra: 'x' })).toEqual({ brandColor: 'green' })
  })

  it('applies nothing for a body of the wrong shape', () => {
    for (const body of [null, [], 1, 'blue', undefined, [{ brandColor: 'blue' }]]) {
      expect(parseAppearance(body)).toEqual({})
    }
  })
})
