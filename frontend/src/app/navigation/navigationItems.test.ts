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
import { describe, expect, it } from 'vitest'
import type { FeatureRegistration } from '../registry/types'
import { buildUserMenuItems, selectAdminSidebarItems } from './navigationItems'

const noop = () => {}

const registrations: FeatureRegistration[] = [
  {
    featureId: 'a',
    sidebarItems: [
      {
        id: 'admin',
        labelKey: 'a.admin',
        path: '/admin',
        order: 20,
        visibleWhen: 'ADMIN',
        section: 'ADMIN',
      },
      {
        id: 'items',
        labelKey: 'a.items',
        path: '/items',
        order: 10,
        visibleWhen: 'LOGGED_IN',
        section: 'ADMIN',
      },
    ],
    userMenuItems: [{ id: 'logout', labelKey: 'a.logout', action: noop, order: 99 }],
  },
  {
    featureId: 'b',
    sidebarItems: [
      {
        id: 'reports',
        labelKey: 'b.reports',
        path: '/reports',
        order: 5,
        visibleWhen: 'LOGGED_IN',
        section: 'ADMIN',
      },
    ],
    userMenuItems: [{ id: 'profile', labelKey: 'b.profile', action: noop, order: 1 }],
  },
]

describe('navigationItems', () => {
  it('selects the ADMIN section items in ascending order for administrators', () => {
    expect(
      selectAdminSidebarItems(registrations, { loggedIn: true, admin: true }).map((e) => e.id),
    ).toEqual(['reports', 'items', 'admin'])
  })

  it('selects no items for logged-in non-administrators', () => {
    expect(selectAdminSidebarItems(registrations, { loggedIn: true, admin: false })).toEqual([])
  })

  it('selects nothing when nothing is registered', () => {
    expect(selectAdminSidebarItems([], { loggedIn: true, admin: true })).toEqual([])
  })

  it('sorts user menu items by order', () => {
    expect(
      buildUserMenuItems(registrations, { loggedIn: true, admin: false }).map((i) => i.id),
    ).toEqual(['profile', 'logout'])
  })

  it('sorts path items and action items together by order', () => {
    const mixed: FeatureRegistration[] = [
      {
        featureId: 'auth',
        userMenuItems: [{ id: 'logout', labelKey: 'auth.logout', action: noop, order: 100 }],
      },
      {
        featureId: 'me',
        userMenuItems: [
          { id: 'password', labelKey: 'me.password', path: '/me/password', order: 90 },
          { id: 'prefs', labelKey: 'me.prefs', path: '/me/preferences', order: 80 },
        ],
      },
    ]
    const items = buildUserMenuItems(mixed, { loggedIn: true, admin: false })
    expect(items.map((i) => i.id)).toEqual(['prefs', 'password', 'logout'])
    expect(items.map((i) => i.path)).toEqual(['/me/preferences', '/me/password', undefined])
  })

  it('shows no items when logged out', () => {
    const loggedOut = { loggedIn: false, admin: false }
    expect(selectAdminSidebarItems(registrations, loggedOut)).toEqual([])
    expect(buildUserMenuItems(registrations, loggedOut)).toEqual([])
  })
})
