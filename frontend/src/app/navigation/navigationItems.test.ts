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
import { buildSidebarEntries, buildUserMenuItems, HOME_ENTRY } from './navigationItems'

const noop = () => {}

const registrations: FeatureRegistration[] = [
  {
    featureId: 'a',
    sidebarItems: [
      { id: 'admin', labelKey: 'a.admin', path: '/admin', order: 20, visibleWhen: 'ADMIN' },
      { id: 'items', labelKey: 'a.items', path: '/items', order: 10, visibleWhen: 'LOGGED_IN' },
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
      },
    ],
    userMenuItems: [{ id: 'profile', labelKey: 'b.profile', action: noop, order: 1 }],
  },
]

describe('navigationItems', () => {
  it('puts home first followed by items in ascending order', () => {
    expect(
      buildSidebarEntries(registrations, { loggedIn: true, admin: true }).map((e) => e.id),
    ).toEqual(['home', 'reports', 'items', 'admin'])
  })

  it('shows ADMIN items only to administrators', () => {
    expect(
      buildSidebarEntries(registrations, { loggedIn: true, admin: false }).map((e) => e.id),
    ).toEqual(['home', 'reports', 'items'])
  })

  it('shows only home when nothing is registered', () => {
    expect(buildSidebarEntries([], { loggedIn: true, admin: false })).toEqual([HOME_ENTRY])
  })

  it('sorts user menu items by order', () => {
    expect(
      buildUserMenuItems(registrations, { loggedIn: true, admin: false }).map((i) => i.id),
    ).toEqual(['profile', 'logout'])
  })

  it('shows no items when logged out', () => {
    const loggedOut = { loggedIn: false, admin: false }
    expect(buildSidebarEntries(registrations, loggedOut)).toEqual([])
    expect(buildUserMenuItems(registrations, loggedOut)).toEqual([])
  })
})
