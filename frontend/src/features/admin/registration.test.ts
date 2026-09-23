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
// 管理者向け領域の登録のテスト（BR5.1、BR5.4、NFR7.1）。
import { describe, expect, it } from 'vitest'
import { baseMessageKeys } from '../../app/i18n/i18n'
import { buildSidebarEntries, HOME_ENTRY } from '../../app/navigation/navigationItems'
import { registration as authRegistration } from '../auth/registration'
import { validateRegistrations } from '../../app/registry/validateRegistrations'
import { ADMIN_AREA_PATH, registration } from './registration'

describe('admin registration', () => {
  it('passes the checks of the application shell together with the other features', () => {
    expect(() => validateRegistrations([registration], baseMessageKeys)).not.toThrow()
    expect(() =>
      validateRegistrations([authRegistration, registration], baseMessageKeys),
    ).not.toThrow()
  })

  it('registers the admin area as an ADMIN route inside the application shell', () => {
    expect(registration.routes).toHaveLength(1)
    expect(registration.routes?.[0]).toMatchObject({
      path: ADMIN_AREA_PATH,
      layout: 'SHELL',
      access: 'ADMIN',
    })
    expect(registration.routes?.[0].role).toBeUndefined()
  })

  it('shows the sidebar item to an administrator after home', () => {
    const entries = buildSidebarEntries([registration], {
      loggedIn: true,
      admin: true,
    })

    expect(entries).toHaveLength(2)
    expect(entries[0]).toEqual(HOME_ENTRY)
    expect(entries[1]).toEqual({
      id: 'admin-area',
      labelKey: 'admin.nav.label',
      path: ADMIN_AREA_PATH,
    })
  })

  it('hides the sidebar item from a logged-in user without the administrator flag', () => {
    const entries = buildSidebarEntries([registration], { loggedIn: true, admin: false })

    expect(entries).toEqual([HOME_ENTRY])
  })

  it('hides the sidebar item when nobody is logged in', () => {
    expect(buildSidebarEntries([registration], { loggedIn: false, admin: false })).toEqual([])
  })

  it('has the same message keys in Japanese and in English', () => {
    const ja = Object.keys(registration.messages?.ja ?? {}).sort()
    const en = Object.keys(registration.messages?.en ?? {}).sort()

    expect(ja).toEqual(en)
    expect(ja.length).toBeGreaterThan(0)
    expect(ja.every((key) => key.startsWith('admin.'))).toBe(true)
  })
})
