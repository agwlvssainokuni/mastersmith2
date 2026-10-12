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
// 権限の受け渡しの画面の登録と文言のテスト（Intent 261004-role-menu の U6 の D1・D2、NFR1.1・NFR4.3、計画 10節の 2）。
// 道で、管理者でないとき decideRoute が ADMIN_FORBIDDEN、未ログインはログインへ、管理者は画面を描く。画面の部品は
// 遅延読み込み（lazy）。サイドバーの「権限の受け渡し」は管理者にだけ示す（隠すのは見せ方だけで、判定はサーバー）。
import { describe, expect, it } from 'vitest'
import { baseMessageKeys } from '../../app/i18n/i18n'
import { buildSidebarEntries, HOME_ENTRY } from '../../app/navigation/navigationItems'
import type { LoginState } from '../../app/registry/types'
import { validateRegistrations } from '../../app/registry/validateRegistrations'
import { decideRoute } from '../../app/routing/decideRoute'
import { registration as authRegistration } from '../auth/registration'
import { registration as groupadminRegistration } from '../groupadmin/registration'
import { registration } from './registration'

const ADMIN: LoginState = { loggedIn: true, admin: true, displayName: '管理 一郎' }
const MEMBER: LoginState = { loggedIn: true, admin: false, displayName: '利用 花子' }
const GUEST: LoginState = { loggedIn: false, admin: false }
const PATHS = ['/admin/role-transfer'] as const

describe('roletransfer registration', () => {
  it('registers one lazy ADMIN route in the shell', () => {
    expect(registration.featureId).toBe('roletransfer')
    expect(registration.routes?.map((route) => route.path)).toEqual(['/admin/role-transfer'])
    for (const route of registration.routes ?? []) {
      expect(route).toMatchObject({ layout: 'SHELL', access: 'ADMIN' })
      expect((route.screen as unknown as { $$typeof: symbol }).$$typeof).toBe(
        Symbol.for('react.lazy'),
      )
    }
    expect(() =>
      validateRegistrations(
        [authRegistration, groupadminRegistration, registration],
        baseMessageKeys,
      ),
    ).not.toThrow()
  })

  it.each(PATHS)('decides %s by the login state', (path) => {
    const registrations = [authRegistration, registration]
    expect(decideRoute(path, registrations, ADMIN)).toMatchObject({ kind: 'SCREEN' })
    expect(decideRoute(path, registrations, MEMBER)).toMatchObject({ kind: 'ADMIN_FORBIDDEN' })
    expect(decideRoute(path, registrations, GUEST)).toMatchObject({ kind: 'REDIRECT_TO_LOGIN' })
  })

  it('shows the sidebar item "権限の受け渡し" (260) only to an administrator, after the groups', () => {
    expect(registration.sidebarItems?.[0]).toMatchObject({
      id: 'roletransfer',
      labelKey: 'roletransfer.nav.label',
      path: '/admin/role-transfer',
      order: 260,
      visibleWhen: 'ADMIN',
      section: 'ADMIN',
    })
    const entries = buildSidebarEntries([registration, groupadminRegistration], ADMIN)
    expect(entries.map((entry) => entry.id)).toEqual([HOME_ENTRY.id, 'groupadmin', 'roletransfer'])
    expect(buildSidebarEntries([registration], MEMBER)).toEqual([HOME_ENTRY])
    expect(buildSidebarEntries([registration], GUEST)).toEqual([])
  })

  it('has every message in Japanese and English with the same placeholders and no markup', () => {
    const ja = registration.messages?.ja ?? {}
    const en = registration.messages?.en ?? {}
    expect(Object.keys(ja).sort()).toEqual(Object.keys(en).sort())
    expect(ja['roletransfer.nav.label']).toBe('権限の受け渡し')
    expect(en['roletransfer.nav.label']).toBe('Permission transfer')
    const names = (value: string | undefined) =>
      [...new Set(value?.match(/\{\{\w+\}\}/g) ?? [])].sort()
    for (const key of Object.keys(ja)) {
      expect(key.startsWith('roletransfer.')).toBe(true)
      expect(ja[key]?.trim(), key).toBeTruthy()
      expect(en[key]?.trim(), key).toBeTruthy()
      expect(names(en[key]), key).toEqual(names(ja[key]))
    }
    for (const value of [...Object.values(ja), ...Object.values(en)]) {
      expect(value).not.toMatch(/<[a-zA-Z/!]/)
    }
  })
})
