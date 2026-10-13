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
// テーブルの置き場の画面の登録のテスト（Intent 261004-role-menu の U7 の D1・D20、計画 11節）。
import { describe, expect, it } from 'vitest'
import { baseMessageKeys } from '../../app/i18n/i18n'
import { validateRegistrations } from '../../app/registry/validateRegistrations'
import { decideRoute } from '../../app/routing/decideRoute'
import { registration as authRegistration } from '../auth/registration'
import { registration } from './registration'

describe('tables registration', () => {
  it('registers /tables as a lazy screen for logged-in users inside the shell, without sidebar items', () => {
    expect(registration.routes).toHaveLength(1)
    expect(registration.routes?.[0]).toMatchObject({
      path: '/tables',
      access: 'LOGGED_IN',
      layout: 'SHELL',
    })
    const screen = registration.routes?.[0]?.screen as { $$typeof?: symbol } | undefined
    expect(screen?.$$typeof).toBe(Symbol.for('react.lazy'))
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

  it('sends logged-out users to the login screen', () => {
    const decision = decideRoute('/tables', [registration, authRegistration], {
      loggedIn: false,
      admin: false,
    })
    expect(decision.kind).toBe('REDIRECT_TO_LOGIN')
  })
})
