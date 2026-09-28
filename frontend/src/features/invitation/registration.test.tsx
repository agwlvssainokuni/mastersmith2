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
// 招待の管理の画面の登録と文言のテスト（frontend-components.md の 6節、NFR8.1・NFR9.2、機能設計の承認の場の R-02）。
// 画面でメニューを隠すことはサーバー側の判定の代わりにしない（401・403 は U3 のサーバー側のテストが確かめる）。
import { screen } from '@testing-library/react'
import { Suspense, type ComponentType } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { baseMessageKeys } from '../../app/i18n/i18n'
import { buildSidebarEntries, HOME_ENTRY } from '../../app/navigation/navigationItems'
import { validateRegistrations } from '../../app/registry/validateRegistrations'
import { registration as adminRegistration } from '../admin/registration'
import { registration as authRegistration } from '../auth/registration'
import { registration as dslRegistration } from '../dsl/registration'
import { INVITATION_ADMIN_PATH, registration } from './registration'
import { renderInvitation } from './testing/renderInvitation'

describe('invitation registration', () => {
  it('registers the screen as an ADMIN route inside the application shell', () => {
    expect(registration.featureId).toBe('invitation')
    expect(registration.routes).toHaveLength(1)
    expect(registration.routes?.[0]).toMatchObject({
      path: '/admin/invitations',
      layout: 'SHELL',
      access: 'ADMIN',
    })
    expect(INVITATION_ADMIN_PATH).toBe('/admin/invitations')
  })

  it('passes the checks of the application shell together with the other features', () => {
    expect(() => validateRegistrations([registration], baseMessageKeys)).not.toThrow()
    expect(() =>
      validateRegistrations(
        [authRegistration, adminRegistration, dslRegistration, registration],
        baseMessageKeys,
      ),
    ).not.toThrow()
  })

  it('shows the sidebar item only to an administrator, right after the DSL item', () => {
    expect(registration.sidebarItems?.[0]).toMatchObject({
      id: 'invitation',
      labelKey: 'invitation.nav.label',
      path: INVITATION_ADMIN_PATH,
      order: 220,
      visibleWhen: 'ADMIN',
    })
    const entries = buildSidebarEntries([registration, dslRegistration, adminRegistration], {
      loggedIn: true,
      admin: true,
    })
    expect(entries.map((entry) => entry.id)).toEqual([
      HOME_ENTRY.id,
      'admin-area',
      'dsl',
      'invitation',
    ])
    expect(buildSidebarEntries([registration], { loggedIn: true, admin: false })).toEqual([
      HOME_ENTRY,
    ])
    expect(buildSidebarEntries([registration], { loggedIn: false, admin: false })).toEqual([])
  })

  it('has every message in Japanese and in English, not empty, with keys starting with invitation.', () => {
    const ja = registration.messages?.ja ?? {}
    const en = registration.messages?.en ?? {}
    expect(Object.keys(ja).sort()).toEqual(Object.keys(en).sort())
    for (const key of Object.keys(ja)) {
      expect(key.startsWith('invitation.')).toBe(true)
      expect(ja[key]?.trim()).toBeTruthy()
      expect(en[key]?.trim()).toBeTruthy()
    }
  })

  it('does not put the validity period or anything shaped like an HTML tag into the messages', () => {
    const values = [
      ...Object.values(registration.messages?.ja ?? {}),
      ...Object.values(registration.messages?.en ?? {}),
    ]
    for (const value of values) {
      expect(value).not.toContain('24')
      expect(value).not.toMatch(/<[a-zA-Z/!]/)
    }
  })

  it('loads the screen lazily', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => new Promise(() => {})),
    )
    const Screen = registration.routes?.[0].screen as ComponentType
    renderInvitation(
      <Suspense fallback={<p>loading</p>}>
        <Screen />
      </Suspense>,
    )
    expect(await screen.findByTestId('invitation-admin-page')).toBeInTheDocument()
    vi.unstubAllGlobals()
  })
})
