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
// 利用者の管理の画面の登録と文言のテスト（frontend-components.md の 6節、NFR8.1・NFR5.6）。画面でメニューを隠すことは
// サーバー側の判定の代わりにしない（401・403・200 は U3 のサーバー側のテストが確かめる、NFR1.1）。
import { screen } from '@testing-library/react'
import { Suspense, type ComponentType } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { baseMessageKeys } from '../../app/i18n/i18n'
import { buildSidebarEntries, HOME_ENTRY } from '../../app/navigation/navigationItems'
import { validateRegistrations } from '../../app/registry/validateRegistrations'
import { registration as adminRegistration } from '../admin/registration'
import { registration as authRegistration } from '../auth/registration'
import { registration as dslRegistration } from '../dsl/registration'
import { registration as invitationRegistration } from '../invitation/registration'
import { registration, USER_ADMIN_PATH } from './registration'
import { renderUserAdmin } from './testing/renderUserAdmin'

describe('useradmin registration', () => {
  it('registers the screen as an ADMIN route inside the application shell', () => {
    expect(registration.featureId).toBe('useradmin')
    expect(registration.routes).toHaveLength(1)
    expect(registration.routes?.[0]).toMatchObject({
      path: '/admin/users',
      layout: 'SHELL',
      access: 'ADMIN',
    })
    expect(USER_ADMIN_PATH).toBe('/admin/users')
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

  it('shows the sidebar item only to an administrator, right after the invitation item', () => {
    expect(registration.sidebarItems?.[0]).toMatchObject({
      id: 'useradmin',
      labelKey: 'useradmin.nav.label',
      path: USER_ADMIN_PATH,
      order: 230,
      visibleWhen: 'ADMIN',
    })
    const entries = buildSidebarEntries(
      [registration, invitationRegistration, dslRegistration, adminRegistration],
      { loggedIn: true, admin: true },
    )
    expect(entries.map((entry) => entry.id)).toEqual([
      HOME_ENTRY.id,
      'admin-area',
      'dsl',
      'invitation',
      'useradmin',
    ])
    expect(buildSidebarEntries([registration], { loggedIn: true, admin: false })).toEqual([
      HOME_ENTRY,
    ])
    expect(buildSidebarEntries([registration], { loggedIn: false, admin: false })).toEqual([])
  })

  it('has every message in Japanese and in English, not empty, with keys starting with useradmin.', () => {
    const ja = registration.messages?.ja ?? {}
    const en = registration.messages?.en ?? {}
    expect(Object.keys(ja).sort()).toEqual(Object.keys(en).sort())
    expect(ja['useradmin.nav.label']).toBe('利用者の管理')
    expect(en['useradmin.nav.label']).toBe('Users')
    for (const key of Object.keys(ja)) {
      expect(key.startsWith('useradmin.')).toBe(true)
      expect(ja[key]?.trim()).toBeTruthy()
      expect(en[key]?.trim()).toBeTruthy()
      // 差し込みの名前が ja と en で同じ
      const names = (value: string | undefined) => (value?.match(/\{\{\w+\}\}/g) ?? []).sort()
      expect(names(en[key]), key).toEqual(names(ja[key]))
    }
    const values = [...Object.values(ja), ...Object.values(en)]
    for (const value of values) {
      expect(value).not.toMatch(/<[a-zA-Z/!]/)
    }
  })

  it('loads the screen lazily', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => new Promise(() => {})),
    )
    const Screen = registration.routes?.[0].screen as ComponentType
    renderUserAdmin(
      <Suspense fallback={<p>loading</p>}>
        <Screen />
      </Suspense>,
    )
    expect(await screen.findByTestId('useradmin-page')).toBeInTheDocument()
    vi.unstubAllGlobals()
  })
})
