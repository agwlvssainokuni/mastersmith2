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
// 業務データの画面の登録のテスト（Intent 261016-business-data-views の U3 の D1・D28、U3-NFR1.10）。
import { describe, expect, it } from 'vitest'
import { baseMessageKeys } from '../../app/i18n/i18n'
import { validateRegistrations } from '../../app/registry/validateRegistrations'
import { decideRoute } from '../../app/routing/decideRoute'
import { registration as authRegistration } from '../auth/registration'
import { registration } from './registration'

describe('tables registration', () => {
  it('registers the list and the detail as separate lazy screens for logged-in users in the shell', () => {
    expect(registration.routes?.map((route) => route.path)).toEqual(['/tables', '/tables/row'])
    for (const route of registration.routes ?? []) {
      expect(route).toMatchObject({ access: 'LOGGED_IN', layout: 'SHELL' })
      const screen = route.screen as { $$typeof?: symbol }
      expect(screen.$$typeof).toBe(Symbol.for('react.lazy'))
    }
    const [list, detail] = registration.routes ?? []
    expect(list?.screen).not.toBe(detail?.screen)
    expect(registration.sidebarItems).toBeUndefined()
    expect(registration.userMenuItems).toBeUndefined()
  })

  it('passes the checks of the shell with every message in both languages', () => {
    expect(() =>
      validateRegistrations([registration, authRegistration], baseMessageKeys),
    ).not.toThrow()
    const ja = registration.messages?.ja ?? {}
    const en = registration.messages?.en ?? {}
    expect(Object.keys(ja).sort()).toEqual(Object.keys(en).sort())
    expect(Object.keys(ja).every((key) => key.startsWith('tables.'))).toBe(true)
  })

  it('sends logged-out users to the login screen on both paths', () => {
    for (const path of ['/tables', '/tables/row']) {
      const decision = decideRoute(path, [registration, authRegistration], {
        loggedIn: false,
        admin: false,
      })
      expect(decision.kind).toBe('REDIRECT_TO_LOGIN')
    }
  })
})
