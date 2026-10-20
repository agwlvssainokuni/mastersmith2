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
// サイドバーの区画を作る関数の例のテスト（Intent 261004-role-menu の U7 の D10〜D15・D18・D20、L5、NFR1.1・NFR1.7）。
import type { SidebarNavItem, SidebarNavSection } from 'make-you-chic-ui'
import { describe, expect, it, vi } from 'vitest'
import type { FeatureRegistration, LoginState } from '../registry/types'
import { buildNavSections, type BuildNavInput, type BusinessMenuView } from './buildNavSections'
import type { NavNode, NavigationResponse } from './navigationApi'

const MESSAGES: Record<string, string> = {
  'nav.home': 'ホーム',
  'nav.section.business': '業務',
  'nav.section.admin': '管理',
  'nav.businessLoadError': '業務のメニューを読み込めませんでした',
  'nav.untitled': '（名前なし）',
  'roleadmin.nav.label': 'ロール',
  'groupadmin.nav.label': 'グループ',
  'useradmin.nav.label': '利用者',
}
const t = (key: string) => MESSAGES[key] ?? key

const ADMIN: LoginState = { loggedIn: true, admin: true }
const MEMBER: LoginState = { loggedIn: true, admin: false }

const adminFeature: FeatureRegistration = {
  featureId: 'adm',
  sidebarItems: [
    {
      id: 'groupadmin',
      labelKey: 'groupadmin.nav.label',
      path: '/admin/groups',
      order: 250,
      visibleWhen: 'ADMIN',
      section: 'ADMIN',
      icon: 'user',
    },
    {
      id: 'roleadmin',
      labelKey: 'roleadmin.nav.label',
      path: '/admin/roles',
      order: 240,
      visibleWhen: 'ADMIN',
      section: 'ADMIN',
    },
    {
      id: 'roletransfer',
      labelKey: 'roleadmin.nav.label',
      path: '/admin/roles/transfer',
      order: 260,
      visibleWhen: 'ADMIN',
      section: 'ADMIN',
    },
  ],
}

function node(id: string, extra: Partial<NavNode> = {}): NavNode {
  return {
    id,
    label: { ja: `項目${id}`, en: `Item ${id}` },
    icon: 'list',
    table: null,
    navigable: false,
    items: [],
    ...extra,
  }
}

function leaf(id: string, tableName: string, extra: Partial<NavNode> = {}): NavNode {
  return node(id, { table: { schemaName: 'S', tableName }, navigable: true, ...extra })
}

function loaded(items: NavNode[]): BusinessMenuView {
  const response: NavigationResponse = {
    items,
    emptyReason: items.length ? null : 'NOTHING_VISIBLE',
  }
  return { status: 'loaded', response }
}

function build(overrides: Partial<BuildNavInput> = {}) {
  return buildNavSections({
    loginState: ADMIN,
    registrations: [adminFeature],
    business: { status: 'loading', response: null },
    location: { pathname: '/', search: '' },
    language: 'ja',
    t,
    navigate: () => {},
    ...overrides,
  })
}

function allItems(sections: readonly SidebarNavSection[]): SidebarNavItem[] {
  const result: SidebarNavItem[] = []
  const walk = (items: readonly SidebarNavItem[]) => {
    for (const item of items) {
      result.push(item)
      walk(item.children ?? [])
    }
  }
  sections.forEach((section) => walk(section.items))
  return result
}

const tree = [
  node('0', {
    items: [
      node('0.0', { items: [leaf('0.0.0', 'ORDER'), leaf('0.0.1', 'ORDER_LINE')] }),
      node('0.1'),
    ],
  }),
  leaf('1', 'ORDER'),
]

describe('buildNavSections', () => {
  it('returns no sections when logged out', () => {
    expect(
      build({ loginState: { loggedIn: false, admin: false }, business: loaded(tree) }),
    ).toEqual({ sections: [], currentAncestorIds: [], businessIds: [] })
  })

  it('orders home, business and admin sections with headings only on the last two', () => {
    const { sections } = build({ business: loaded(tree) })
    expect(sections.map((s) => [s.id, s.heading])).toEqual([
      ['home', undefined],
      ['business', '業務'],
      ['admin', '管理'],
    ])
    expect(sections[0].items).toMatchObject([
      { id: 'home', label: 'ホーム', icon: 'home', href: '/' },
    ])
    expect(sections[2].items.map((i) => [i.id, i.label, i.icon, i.href])).toEqual([
      ['adm:roleadmin', 'ロール', 'list', '/admin/roles'],
      ['adm:groupadmin', 'グループ', 'user', '/admin/groups'],
      ['adm:roletransfer', 'ロール', 'list', '/admin/roles/transfer'],
    ])
  })

  it('has no admin section for non-admins and no business section while loading or empty', () => {
    expect(build({ loginState: MEMBER }).sections.map((s) => s.id)).toEqual(['home'])
    expect(build({ business: loaded([]) }).sections.map((s) => s.id)).toEqual(['home', 'admin'])
    expect(
      build({ business: { status: 'idle', response: null } }).sections.map((s) => s.id),
    ).toEqual(['home', 'admin'])
  })

  it('copies the business tree with the biz: prefix and drops items without children or a link', () => {
    const { sections, businessIds } = build({ business: loaded(tree) })
    const business = sections[1].items
    expect(business.map((i) => i.id)).toEqual(['biz:0', 'biz:1'])
    expect(business[0].href).toBeUndefined()
    expect(business[0].children?.map((i) => i.id)).toEqual(['biz:0.0'])
    expect(business[0].children?.[0].children?.map((i) => i.href)).toEqual([
      '/tables?schema=S&table=ORDER&item=0.0.0',
      '/tables?schema=S&table=ORDER_LINE&item=0.0.1',
    ])
    expect(businessIds).toEqual(['biz:0', 'biz:0.0', 'biz:0.0.0', 'biz:0.0.1', 'biz:1'])
  })

  it('shows one item without a link when the first load failed', () => {
    const { sections } = build({ business: { status: 'load-error', response: null } })
    expect(sections[1]).toEqual({
      id: 'business',
      heading: '業務',
      items: [{ id: 'biz-load-error', label: '業務のメニューを読み込めませんでした' }],
    })
  })

  it('keeps the previous tree while reloading', () => {
    const { sections } = build({ business: { ...loaded(tree), status: 'reloading' } })
    expect(sections[1].items.map((i) => i.id)).toEqual(['biz:0', 'biz:1'])
  })

  it('chooses the label of the display language, then the other language, the table name and untitled', () => {
    const items = [
      leaf('a', 'TA', { label: { ja: '日本語', en: 'English' } }),
      leaf('b', 'TB', { label: { ja: '', en: 'Only English' } }),
      leaf('c', 'TC', { label: { ja: ' ', en: '' } }),
      node('d', { label: { ja: '', en: '' }, items: [leaf('d.0', 'TD')] }),
    ]
    const labels = (language: 'ja' | 'en') =>
      build({ business: loaded(items), language }).sections[1].items.map((i) => i.label)
    expect(labels('ja')).toEqual(['日本語', 'Only English', 'TC', '（名前なし）'])
    expect(labels('en')).toEqual(['English', 'Only English', 'TC', '（名前なし）'])
  })

  it('replaces unknown, empty and wrongly cased icons with list', () => {
    const items = ['user', 'skull', '', 'HOME'].map((icon, index) =>
      leaf(String(index), `T${index}`, { icon }),
    )
    expect(build({ business: loaded(items) }).sections[1].items.map((i) => i.icon)).toEqual([
      'user',
      'list',
      'list',
      'list',
    ])
  })

  it('marks home as current only at /', () => {
    const at = (pathname: string) =>
      allItems(build({ business: loaded(tree), location: { pathname, search: '' } }).sections)
        .filter((i) => i.current)
        .map((i) => i.id)
    expect(at('/')).toEqual(['home'])
    expect(at('/no/such')).toEqual([])
  })

  it('marks the admin item with the longest path matching at a path boundary', () => {
    const at = (pathname: string) =>
      allItems(build({ location: { pathname, search: '' } }).sections)
        .filter((i) => i.current)
        .map((i) => i.id)
    expect(at('/admin/roles')).toEqual(['adm:roleadmin'])
    expect(at('/admin/roles/12/assignments')).toEqual(['adm:roleadmin'])
    expect(at('/admin/roles/transfer')).toEqual(['adm:roletransfer'])
    expect(at('/admin/rolesx')).toEqual([])
  })

  it('marks the business item named by item, or the first in pre-order, and returns its ancestors', () => {
    const run = (search: string) => {
      const output = build({ business: loaded(tree), location: { pathname: '/tables', search } })
      return {
        current: allItems(output.sections)
          .filter((i) => i.current)
          .map((i) => i.id),
        ancestors: output.currentAncestorIds,
      }
    }
    expect(run('?schema=S&table=ORDER&item=1')).toEqual({ current: ['biz:1'], ancestors: [] })
    expect(run('?schema=S&table=ORDER&item=9')).toEqual({
      current: ['biz:0.0.0'],
      ancestors: ['biz:0', 'biz:0.0'],
    })
    expect(run('?schema=S&table=ORDER')).toEqual({
      current: ['biz:0.0.0'],
      ancestors: ['biz:0', 'biz:0.0'],
    })
    expect(run('?schema=S&table=ORDER_LINE&item=0.0.0')).toEqual({
      current: ['biz:0.0.1'],
      ancestors: ['biz:0', 'biz:0.0'],
    })
    expect(run('?schema=S&table=NONE')).toEqual({ current: [], ancestors: [] })
    expect(run('')).toEqual({ current: [], ancestors: [] })
  })

  it('keeps the business item current on the detail, create and update paths (U3 D1, AC1.1.6)', () => {
    for (const pathname of ['/tables/row', '/tables/new', '/tables/edit']) {
      const output = build({
        business: loaded(tree),
        location: { pathname, search: '?schema=S&table=ORDER_LINE&item=0.0.1' },
      })
      expect(
        allItems(output.sections)
          .filter((i) => i.current)
          .map((i) => i.id),
      ).toEqual(['biz:0.0.1'])
      expect(output.currentAncestorIds).toEqual(['biz:0', 'biz:0.0'])
    }
    const other = build({
      business: loaded(tree),
      location: { pathname: '/tables/other', search: '?schema=S&table=ORDER' },
    })
    expect(allItems(other.sections).filter((i) => i.current)).toEqual([])
  })

  it('navigates without reloading when a link is clicked', () => {
    const navigate = vi.fn()
    const { sections } = build({ business: loaded(tree), navigate })
    const event = { preventDefault: vi.fn() }
    sections[1].items[1].onClick?.(event as never)
    expect(event.preventDefault).toHaveBeenCalled()
    expect(navigate).toHaveBeenCalledWith('/tables?schema=S&table=ORDER&item=1')
  })

  it.each([
    ['Ctrl', { ctrlKey: true }],
    ['Cmd', { metaKey: true }],
    ['Shift', { shiftKey: true }],
    ['Alt', { altKey: true }],
    ['the middle button', { button: 1 }],
  ])(
    'leaves a click with %s to the browser without preventDefault or navigate',
    (_name, modifier) => {
      const navigate = vi.fn()
      const { sections } = build({ business: loaded(tree), navigate })
      const links = [sections[0].items[0], sections[1].items[1], sections[2].items[0]]
      for (const link of links) {
        const event = { button: 0, preventDefault: vi.fn(), ...modifier }
        link.onClick?.(event as never)
        expect(event.preventDefault).not.toHaveBeenCalled()
      }
      expect(navigate).not.toHaveBeenCalled()
    },
  )

  it('still navigates for a plain primary-button click with every modifier off', () => {
    const navigate = vi.fn()
    const { sections } = build({ business: loaded(tree), navigate })
    const event = {
      button: 0,
      ctrlKey: false,
      metaKey: false,
      shiftKey: false,
      altKey: false,
      preventDefault: vi.fn(),
    }
    sections[0].items[0].onClick?.(event as never)
    expect(event.preventDefault).toHaveBeenCalled()
    expect(navigate).toHaveBeenCalledWith('/')
  })
})
