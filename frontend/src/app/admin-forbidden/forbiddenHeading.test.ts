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
// S6 の見出しの鍵を決める関数のテスト（U4 の D11、NFR9.9）。性質ベースのテストを含む。
// 性質ベースのテスト（fast-check、既定の 100 回）が失敗すると、報告に seed と path が出る。
// 再現するときは、その fc.assert(property) を fc.assert(property, { seed: <seed>, path: '<path>' }) に
// 一時的に替えて、このファイルを名指しして流す。原因を直した後に元に戻す（指定を残してコミットしない）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import type { FeatureRegistration } from '../registry/types'
import { ADMIN_FORBIDDEN_HEADING_KEY, forbiddenHeadingKey } from './forbiddenHeading'

const registrations: FeatureRegistration[] = [
  {
    featureId: 'admin',
    sidebarItems: [
      {
        id: 'admin',
        labelKey: 'admin.nav.label',
        path: '/admin',
        order: 90,
        visibleWhen: 'ADMIN',
        section: 'ADMIN',
      },
    ],
  },
  {
    featureId: 'reports',
    sidebarItems: [
      {
        id: 'reports',
        labelKey: 'reports.nav.label',
        path: '/reports',
        order: 10,
        visibleWhen: 'LOGGED_IN',
        section: 'ADMIN',
      },
      {
        id: 'user',
        labelKey: 'reports.user.label',
        path: '/admin/users/:id',
        order: 20,
        visibleWhen: 'ADMIN',
        section: 'ADMIN',
      },
    ],
  },
  { featureId: 'empty' },
]

describe('forbiddenHeadingKey', () => {
  it('returns the label key of the sidebar item registered for the URL', () => {
    expect(forbiddenHeadingKey('/reports', registrations)).toBe('reports.nav.label')
  })

  it('finds ADMIN items whatever the login state is', () => {
    expect(forbiddenHeadingKey('/admin', registrations)).toBe('admin.nav.label')
  })

  it('matches a path with URL parameters', () => {
    expect(forbiddenHeadingKey('/admin/users/42', registrations)).toBe('reports.user.label')
  })

  it('falls back to the common heading for unregistered or partially matching URLs', () => {
    expect(forbiddenHeadingKey('/admin/dsl', registrations)).toBe(ADMIN_FORBIDDEN_HEADING_KEY)
    expect(forbiddenHeadingKey('/admin/users', registrations)).toBe(ADMIN_FORBIDDEN_HEADING_KEY)
    expect(forbiddenHeadingKey('/admin', [])).toBe(ADMIN_FORBIDDEN_HEADING_KEY)
    expect(ADMIN_FORBIDDEN_HEADING_KEY).toBe('adminForbidden.heading')
  })

  it('always returns the common heading for URLs that are not registered (property)', () => {
    const registered = new Set(['/admin', '/reports'])
    fc.assert(
      fc.property(
        fc
          .array(fc.stringMatching(/^[a-z0-9-]{1,8}$/), { minLength: 1, maxLength: 4 })
          .map((segments) => `/${segments.join('/')}`)
          .filter((path) => !registered.has(path) && !/^\/admin\/users\/[^/]+$/.test(path)),
        (path) => {
          expect(forbiddenHeadingKey(path, registrations)).toBe(ADMIN_FORBIDDEN_HEADING_KEY)
        },
      ),
    )
  })
})
