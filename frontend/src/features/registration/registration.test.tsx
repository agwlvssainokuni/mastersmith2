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
// 登録の完了の画面の登録と文言のテスト（functional-spec.md の W1・7節、NFR6.3・NFR8.1）。
import { screen } from '@testing-library/react'
import { Suspense, type ComponentType } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { baseMessageKeys } from '../../app/i18n/i18n'
import { buildSidebarEntries } from '../../app/navigation/navigationItems'
import { validateRegistrations } from '../../app/registry/validateRegistrations'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { registration as adminRegistration } from '../admin/registration'
import { registration as authRegistration } from '../auth/registration'
import { registration as dslRegistration } from '../dsl/registration'
import { registration as invitationRegistration } from '../invitation/registration'
import { REGISTRATION_PATH, registration } from './registration'
import { renderRegistration } from './testing/renderRegistration'

beforeEach(() => {
  resetDisplayTestState()
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('registration feature', () => {
  it('registers /register as a public standalone screen without a role', () => {
    expect(registration.featureId).toBe('registration')
    expect(REGISTRATION_PATH).toBe('/register')
    expect(registration.routes).toHaveLength(1)
    const route = registration.routes?.[0]
    expect(route).toMatchObject({ path: '/register', layout: 'STANDALONE', access: 'PUBLIC' })
    expect(route?.role).toBeUndefined()
  })

  it('has no sidebar or user menu items', () => {
    expect(registration.sidebarItems).toBeUndefined()
    expect(registration.userMenuItems).toBeUndefined()
    expect(registration.loginStateProvider).toBeUndefined()
    expect(buildSidebarEntries([registration], { loggedIn: true, admin: true })).toHaveLength(1)
  })

  it('passes the checks of the application shell together with the other features', () => {
    expect(() => validateRegistrations([registration], baseMessageKeys)).not.toThrow()
    expect(() =>
      validateRegistrations(
        [
          authRegistration,
          adminRegistration,
          dslRegistration,
          invitationRegistration,
          registration,
        ],
        baseMessageKeys,
      ),
    ).not.toThrow()
  })

  it('has every message in Japanese and in English, not empty, with keys starting with registration.', () => {
    const ja = registration.messages?.ja ?? {}
    const en = registration.messages?.en ?? {}
    expect(Object.keys(ja).sort()).toEqual(Object.keys(en).sort())
    expect(Object.keys(ja)).toHaveLength(34)
    for (const key of Object.keys(ja)) {
      expect(key.startsWith('registration.')).toBe(true)
      expect(ja[key]?.trim()).toBeTruthy()
      expect(en[key]?.trim()).toBeTruthy()
    }
    // 有効期限の長さ（例: 24 時間）を書かない。パスワードの上限の「24 文字」の案内だけは許す。
    for (const value of [...Object.values(ja), ...Object.values(en)]) {
      expect(value).not.toMatch(/\d+ ?(時間|hours?)/)
      expect(value).not.toMatch(/<[a-zA-Z/!]/)
    }
  })

  it('loads the screen lazily and names the languages in their own language on an English screen', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() =>
        Promise.resolve(
          new Response(JSON.stringify({ email: 'hanako@example.test', language: 'en' }), {
            status: 200,
            headers: { 'Content-Type': 'application/json' },
          }),
        ),
      ),
    )
    const Screen = registration.routes?.[0].screen as ComponentType
    renderRegistration(
      <Suspense fallback={<p>loading</p>}>
        <Screen />
      </Suspense>,
      { languages: ['en-US'] },
    )
    expect(await screen.findByTestId('registration-form')).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: '日本語' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'English' })).toBeChecked()
  })
})
