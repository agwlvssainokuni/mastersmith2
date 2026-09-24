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
// DSL の管理画面の登録のテスト（BR1.1・BR1.2・BR6.1、NFR9.1、AC6.2.4）。
import { screen } from '@testing-library/react'
import { Suspense, type ComponentType } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { baseMessageKeys } from '../../app/i18n/i18n'
import { buildSidebarEntries, HOME_ENTRY } from '../../app/navigation/navigationItems'
import { validateRegistrations } from '../../app/registry/validateRegistrations'
import { registration as adminRegistration } from '../admin/registration'
import { registration as authRegistration } from '../auth/registration'
import { DSL_ERROR_CODES } from './failureMessage'
import { DSL_ADMIN_PATH, registration } from './registration'
import { renderDsl } from './testing/renderDsl'

describe('dsl registration', () => {
  it('passes the checks of the application shell together with the other features', () => {
    expect(() => validateRegistrations([registration], baseMessageKeys)).not.toThrow()
    expect(() =>
      validateRegistrations([authRegistration, adminRegistration, registration], baseMessageKeys),
    ).not.toThrow()
  })

  it('registers the DSL screen as an ADMIN route inside the application shell', () => {
    expect(registration.featureId).toBe('dsl')
    expect(registration.routes).toHaveLength(1)
    expect(registration.routes?.[0]).toMatchObject({
      path: '/admin/dsl',
      layout: 'SHELL',
      access: 'ADMIN',
    })
    expect(DSL_ADMIN_PATH).toBe('/admin/dsl')
  })

  it('shows the sidebar item to an administrator right after the administration item', () => {
    expect(registration.sidebarItems?.[0]).toMatchObject({ order: 210, visibleWhen: 'ADMIN' })
    const entries = buildSidebarEntries([registration, adminRegistration], {
      loggedIn: true,
      admin: true,
    })

    expect(entries.map((entry) => entry.id)).toEqual([HOME_ENTRY.id, 'admin-area', 'dsl'])
    expect(entries[2]).toEqual({ id: 'dsl', labelKey: 'dsl.nav.label', path: DSL_ADMIN_PATH })
  })

  it('hides the sidebar item from users without the administrator flag', () => {
    expect(buildSidebarEntries([registration], { loggedIn: true, admin: false })).toEqual([
      HOME_ENTRY,
    ])
    expect(buildSidebarEntries([registration], { loggedIn: false, admin: false })).toEqual([])
  })

  it('has every message in Japanese and in English with keys starting with dsl.', () => {
    const ja = Object.keys(registration.messages?.ja ?? {}).sort()
    const en = Object.keys(registration.messages?.en ?? {}).sort()

    expect(ja).toEqual(en)
    expect(ja.every((key) => key.startsWith('dsl.'))).toBe(true)
    expect(
      ja.every((key) => registration.messages?.ja[key] && registration.messages?.en[key]),
    ).toBe(true)
  })

  it('has a message for each of the nine codes including DSL_BUSY and the 10MB limit', () => {
    const ja = registration.messages?.ja ?? {}
    expect(DSL_ERROR_CODES).toHaveLength(9)
    for (const code of DSL_ERROR_CODES) {
      expect(ja[`dsl.error.${code}`]).toBeTruthy()
    }
    expect(ja['dsl.error.DSL_BUSY']).toBe('ほかの処理中です。少し待ってからやり直してください')
    expect(ja['dsl.error.DSL_TOO_LARGE']).toContain('10MB')
    for (const kind of ['SIZE_LIMIT', 'DEPTH_LIMIT', 'ALIAS_LIMIT', 'FORBIDDEN_TAG']) {
      expect(ja[`dsl.errorKind.${kind}`]).toBeTruthy()
    }
    for (const source of ['GENERATED', 'UPLOAD', 'PASTE', 'RESTORE']) {
      expect(ja[`dsl.source.${source}`]).toBeTruthy()
    }
    for (const change of ['ADDED', 'REMOVED', 'CHANGED', 'UNCHANGED']) {
      expect(ja[`dsl.change.${change}`]).toBeTruthy()
    }
  })

  it('loads the screen lazily', async () => {
    const pending = new Promise(() => {})
    vi.stubGlobal(
      'fetch',
      vi.fn(() => pending),
    )
    const Screen = registration.routes?.[0].screen as ComponentType
    renderDsl(
      <Suspense fallback={<p>loading</p>}>
        <Screen />
      </Suspense>,
    )

    expect(await screen.findByTestId('dsl-admin-page')).toBeInTheDocument()
    vi.unstubAllGlobals()
  })
})
