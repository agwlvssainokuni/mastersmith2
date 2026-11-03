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
// ホームのカードを作る関数の例のテスト（Intent 261102-timezone-ui-polish の U5、D16〜D18・D29）。
import { describe, expect, it } from 'vitest'
import type { BusinessMenuView } from '../navigation/buildNavSections'
import type { NavNode } from '../navigation/navigationApi'
import { toTablePlaceholderPath } from '../navigation/tablePlaceholderPath'
import type { FeatureRegistration, LoginState } from '../registry/types'
import { buildHomeCards, isInAppPath, type BuildHomeCardsInput } from './homeCards'

const MESSAGES: Record<string, string> = {
  'nav.untitled': '（名前なし）',
  'useradmin.nav.label': '利用者の管理',
  'dsl.nav.label': 'DSL',
}
const t = (key: string) => MESSAGES[key] ?? key

const SCRIPT_URL = ['javascript', 'alert(1)'].join(':')

const ADMIN: LoginState = { loggedIn: true, admin: true }
const MEMBER: LoginState = { loggedIn: true, admin: false }

const registrations: FeatureRegistration[] = [
  {
    featureId: 'adm',
    sidebarItems: [
      {
        id: 'useradmin',
        labelKey: 'useradmin.nav.label',
        path: '/admin/users',
        order: 230,
        visibleWhen: 'ADMIN',
        section: 'ADMIN',
        icon: 'user',
      },
      {
        id: 'dsl',
        labelKey: 'dsl.nav.label',
        path: '/admin/dsl',
        order: 210,
        visibleWhen: 'ADMIN',
        section: 'ADMIN',
        icon: 'edit',
      },
    ],
  },
]

function node(id: string, extra: Partial<NavNode> = {}): NavNode {
  return {
    id,
    label: { ja: `項目${id}`, en: `Item ${id}` },
    icon: 'list',
    table: null,
    navigable: false,
    items: [],
    tableKind: null,
    ...extra,
  }
}

function leaf(id: string, tableName: string, extra: Partial<NavNode> = {}): NavNode {
  return node(id, {
    table: { schemaName: 'S', tableName },
    navigable: true,
    tableKind: 'TABLE',
    ...extra,
  })
}

function loaded(items: NavNode[], status: BusinessMenuView['status'] = 'loaded'): BusinessMenuView {
  return { status, response: { items, emptyReason: null } }
}

function build(overrides: Partial<BuildHomeCardsInput> = {}) {
  return buildHomeCards({
    business: loaded([]),
    loginState: ADMIN,
    registrations,
    language: 'ja',
    t,
    ...overrides,
  })
}

describe('buildHomeCards business cards', () => {
  it('makes one card per top-level item in the order of the response, with kind icons and paths', () => {
    const cards = build({
      business: loaded([
        leaf('0', 'ORDER'),
        leaf('1', 'V_ORDER', { tableKind: 'VIEW' }),
        node('2', {
          items: [node('2.0', { items: [leaf('2.0.0', 'DEEP')] }), leaf('2.1', 'LATER')],
        }),
        leaf('3', 'X', { icon: 'user' }),
      ]),
    }).business

    expect(cards.map((c) => [c.id, c.label, c.icon])).toEqual([
      ['biz:0', '項目0', 'table'],
      ['biz:1', '項目1', 'view'],
      ['biz:2', '項目2', 'folder'],
      ['biz:3', '項目3', 'user'],
    ])
    expect(cards[0].path).toBe(toTablePlaceholderPath({ schemaName: 'S', tableName: 'ORDER' }, '0'))
    // フォルダは最初の移れる子孫（深さ優先）の道
    expect(cards[2].path).toBe(
      toTablePlaceholderPath({ schemaName: 'S', tableName: 'DEEP' }, '2.0.0'),
    )
  })

  it('shows cards only after the menu is loaded or while it is reloading', () => {
    const items = [leaf('0', 'A')]
    for (const status of ['idle', 'loading', 'load-error'] as const) {
      expect(build({ business: loaded(items, status) }).business).toEqual([])
    }
    expect(build({ business: loaded(items, 'loaded') }).business).toHaveLength(1)
    expect(build({ business: loaded(items, 'reloading') }).business).toHaveLength(1)
    expect(build({ business: { status: 'loaded', response: null } }).business).toEqual([])
  })

  it('names a card in the display language and falls back like the sidebar', () => {
    const items = [
      leaf('0', 'A', { label: { ja: '日本語', en: 'English' } }),
      leaf('1', 'B', { label: { ja: '', en: 'Only English' } }),
      leaf('2', 'TC', { label: { ja: '', en: '' } }),
    ]
    expect(build({ business: loaded(items), language: 'en' }).business.map((c) => c.label)).toEqual(
      ['English', 'Only English', 'TC'],
    )
    expect(build({ business: loaded(items), language: 'ja' }).business.map((c) => c.label)).toEqual(
      ['日本語', 'Only English', 'TC'],
    )
  })

  it('drops an item that has no openable item in it (an unexpected shape)', () => {
    const cards = build({ business: loaded([node('0', { items: [node('0.0')] }), leaf('1', 'A')]) })

    expect(cards.business.map((c) => c.id)).toEqual(['biz:1'])
  })

  it('keeps untrusted labels as plain strings', () => {
    const cards = build({
      business: loaded([
        leaf('0', 'A', { label: { ja: '<script>alert(1)</script>&"\'', en: '' } }),
      ]),
    })

    expect(cards.business[0].label).toBe('<script>alert(1)</script>&"\'')
  })
})

describe('buildHomeCards admin cards', () => {
  it('makes the admin cards in the order of the registration for an administrator only', () => {
    const cards = build().admin

    expect(cards.map((c) => [c.id, c.label, c.icon, c.path])).toEqual([
      ['adm:dsl', 'DSL', 'edit', '/admin/dsl'],
      ['adm:useradmin', '利用者の管理', 'user', '/admin/users'],
    ])
  })

  it('makes no admin card for a non-administrator or a logged-out user', () => {
    expect(build({ loginState: MEMBER }).admin).toEqual([])
    expect(build({ loginState: { loggedIn: false, admin: false } }).admin).toEqual([])
  })

  it('shows the admin cards whatever the business menu state is', () => {
    for (const status of ['idle', 'loading', 'load-error'] as const) {
      expect(build({ business: { status, response: null } }).admin).toHaveLength(2)
    }
  })

  it('does not make a card for a path outside the application', () => {
    const outside: FeatureRegistration[] = [
      {
        featureId: 'x',
        sidebarItems: ['//evil.example/', 'https://example.com/', SCRIPT_URL, 'rel'].map(
          (path, index) => ({
            id: `i${index}`,
            labelKey: 'x',
            path,
            order: index,
            visibleWhen: 'ADMIN' as const,
            section: 'ADMIN' as const,
          }),
        ),
      },
    ]

    expect(build({ registrations: outside }).admin).toEqual([])
  })
})

describe('isInAppPath', () => {
  it('accepts a path inside the application only', () => {
    expect(isInAppPath('/')).toBe(true)
    expect(isInAppPath('/tables?schema=S')).toBe(true)
    expect(isInAppPath('//evil.example')).toBe(false)
    expect(isInAppPath('https://example.com')).toBe(false)
    expect(isInAppPath(SCRIPT_URL)).toBe(false)
    expect(isInAppPath('')).toBe(false)
  })
})
