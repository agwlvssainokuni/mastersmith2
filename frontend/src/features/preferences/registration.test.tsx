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
// プリファレンスとパスワードの変更の機能の登録と文言のテスト（frontend-components.md の 6節、functional-spec.md の W1・7節、
// AC5.1.9、NFR8.1・NFR9.4）。
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { baseMessageKeys } from '../../app/i18n/i18n'
import { buildSidebarEntries, buildUserMenuItems } from '../../app/navigation/navigationItems'
import { validateRegistrations } from '../../app/registry/validateRegistrations'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { registration as adminRegistration } from '../admin/registration'
import { resetAuthSession } from '../auth/authSession'
import { registration as authRegistration } from '../auth/registration'
import { registration as dslRegistration } from '../dsl/registration'
import { registration as invitationRegistration } from '../invitation/registration'
import { registration as registrationRegistration } from '../registration/registration'
import { ME_PREFERENCES_PATH } from './preferencesApi'
import { PASSWORD_CHANGE_PATH, PREFERENCES_PATH, registration } from './registration'
import {
  installFakeServer,
  jsonResponse,
  NAMED_PREFERENCES,
  problemResponse,
  REFRESH_PATH,
  sessionResponse,
} from './testing/fixtures'
import { renderPreferencesApp } from './testing/renderPreferences'

const ENGLISH_USER = { ...NAMED_PREFERENCES, language: 'en' as const }

beforeEach(() => {
  resetDisplayTestState()
})

afterEach(() => {
  vi.unstubAllGlobals()
  resetAuthSession()
  resetDisplayTestState()
})

describe('preferences feature registration', () => {
  it('registers two lazy SHELL screens for logged-in users', () => {
    expect(registration.featureId).toBe('preferences')
    expect(PREFERENCES_PATH).toBe('/me/preferences')
    expect(PASSWORD_CHANGE_PATH).toBe('/me/password')
    expect(
      registration.routes?.map(({ path, layout, access, role }) => ({
        path,
        layout,
        access,
        role,
      })),
    ).toEqual([
      { path: '/me/preferences', layout: 'SHELL', access: 'LOGGED_IN', role: undefined },
      { path: '/me/password', layout: 'SHELL', access: 'LOGGED_IN', role: undefined },
    ])
    for (const route of registration.routes ?? []) {
      // React.lazy の部品（遅延読み込み）
      expect((route.screen as unknown as { $$typeof: symbol }).$$typeof).toBe(
        Symbol.for('react.lazy'),
      )
    }
  })

  it('adds two path items before the logout and no sidebar item', () => {
    expect(registration.sidebarItems).toBeUndefined()
    expect(registration.loginStateProvider).toBeUndefined()
    const items = buildUserMenuItems([authRegistration, registration], {
      loggedIn: true,
      admin: true,
    })
    expect(items.map(({ id, path, order }) => ({ id, path, order }))).toEqual([
      { id: 'preferences-open', path: '/me/preferences', order: 80 },
      { id: 'preferences-password', path: '/me/password', order: 90 },
      { id: 'auth-logout', path: undefined, order: 100 },
    ])
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
          registrationRegistration,
          registration,
        ],
        baseMessageKeys,
      ),
    ).not.toThrow()
  })

  it('has every message in Japanese and in English, not empty, with keys starting with preferences.', () => {
    const ja = registration.messages?.ja ?? {}
    const en = registration.messages?.en ?? {}
    expect(Object.keys(ja).sort()).toEqual(Object.keys(en).sort())
    expect(Object.keys(ja)).toHaveLength(40)
    for (const key of Object.keys(ja)) {
      expect(key.startsWith('preferences.')).toBe(true)
      expect(ja[key]?.trim()).toBeTruthy()
      expect(en[key]?.trim()).toBeTruthy()
    }
    for (const value of [...Object.values(ja), ...Object.values(en)]) {
      expect(value).not.toMatch(/<[a-zA-Z/!]/)
    }
  })

  it('sends logged-out users to the login screen for both screens', async () => {
    for (const route of [PREFERENCES_PATH, PASSWORD_CHANGE_PATH]) {
      const server = installFakeServer({
        [REFRESH_PATH]: [problemResponse(401, 'AUTHENTICATION_REQUIRED')],
      })
      const view = renderPreferencesApp({ route })
      expect(await screen.findByTestId('login-layout')).toBeInTheDocument()
      expect(screen.queryByTestId('preferences-page')).not.toBeInTheDocument()
      expect(server.requestsTo(ME_PREFERENCES_PATH)).toHaveLength(0)
      view.unmount()
      vi.unstubAllGlobals()
      resetDisplayTestState()
    }
  })

  it('opens both screens from the user menu without reloading, in the order before the logout (AC5.1.9)', async () => {
    const user = userEvent.setup()
    installFakeServer({
      [REFRESH_PATH]: [sessionResponse(ENGLISH_USER)],
      [`GET ${ME_PREFERENCES_PATH}`]: [jsonResponse(200, ENGLISH_USER)],
    })
    renderPreferencesApp({ route: '/' })
    await screen.findByTestId('home-page')
    await user.click(await screen.findByRole('button', { name: /検査 太郎/ }))
    const menu = within(await screen.findByRole('menu'))
    expect(menu.getAllByRole('menuitem').map((item) => item.textContent)).toEqual([
      'Preferences',
      'Change password',
      'Log out',
    ])
    await user.click(menu.getByRole('menuitem', { name: 'Change password' }))
    expect(await screen.findByTestId('preferences-password-page')).toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/me/password')
    await user.click(await screen.findByRole('button', { name: /検査 太郎/ }))
    await user.click(await screen.findByRole('menuitem', { name: 'Preferences' }))
    expect(await screen.findByTestId('preferences-form')).toBeInTheDocument()
    // 画面の言語は利用者の言語（en）。言語の選択肢は訳さない
    expect(screen.getByRole('radio', { name: '日本語' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'English' })).toBeChecked()
  })
})
