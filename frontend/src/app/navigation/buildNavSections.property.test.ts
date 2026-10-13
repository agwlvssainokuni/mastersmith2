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
// サイドバーの区画を作る関数の性質ベースのテスト（Intent 261004-role-menu の U7 の functional-spec.md 6.2 の性質 1〜7、
// NFR6.2、team.md の N 階層のメニューの決まり）。
// fast-check は、失敗したときに seed と path を出力に示す。再現はその値を fc.assert の第2引数に一時的に書いて行う。
import fc from 'fast-check'
import type { SidebarNavItem, SidebarNavSection } from 'make-you-chic-ui'
import { describe, expect, it } from 'vitest'
import { ALLOWED_ICONS } from '../registry/allowedIcons'
import type { FeatureRegistration, LoginState, SidebarItemRegistration } from '../registry/types'
import { buildNavSections, type BuildNavInput } from './buildNavSections'
import { addAncestors } from './navExpansion'
import type { NavNode } from './navigationApi'
import { readTablePlaceholderParams } from './tablePlaceholderPath'

const SCRIPT_URL = ['javascript', 'alert(1)'].join(':')

/** id の無い項目の形（id は位置の道で付け直す） */
interface RawNode {
  label: { ja: string; en: string }
  icon: string
  table: { schemaName: string; tableName: string } | null
  navigable: boolean
  items: RawNode[]
}

const nameArb = fc.oneof(
  fc.string({ maxLength: 40 }),
  fc.constantFrom(
    '<script>alert(1)</script>',
    '<img src=x onerror=alert(1)>',
    '&"\'<>',
    '',
    SCRIPT_URL,
  ),
)
const iconArb = fc.oneof(
  fc.constantFrom(...ALLOWED_ICONS),
  fc.constantFrom('', 'skull', 'HOME'),
  fc.string(),
)
// 同じ組を指す項目の重なりを作るため、名前は少ない候補から選ぶ。
const tableArb = fc.option(
  fc.record({
    schemaName: fc.constantFrom('S', 'A/B', '..', SCRIPT_URL),
    tableName: fc.constantFrom('T1', 'T2', 'x?y#z', '100%', 'https://example.com/'),
  }),
  { nil: null },
)

function rawNodeArb(depth: number): fc.Arbitrary<RawNode> {
  return fc.record({
    label: fc.record({ ja: nameArb, en: nameArb }),
    icon: iconArb,
    table: tableArb,
    navigable: fc.boolean(),
    items:
      depth >= 5
        ? fc.constant([])
        : fc.array(rawNodeArb(depth + 1), { maxLength: depth < 2 ? 3 : 2 }),
  })
}

function withIds(nodes: readonly RawNode[], prefix: string): NavNode[] {
  return nodes.map((raw, index) => {
    const id = prefix === '' ? String(index) : `${prefix}.${index}`
    return { ...raw, id, items: withIds(raw.items, id) }
  })
}

const treeArb = fc.array(rawNodeArb(1), { maxLength: 3 }).map((nodes) => withIds(nodes, ''))

const adminItemArb: fc.Arbitrary<SidebarItemRegistration> = fc.record({
  id: fc.constantFrom('roleadmin', 'groupadmin', 'useradmin', 'dsl'),
  labelKey: fc.constant('x.nav'),
  path: fc.constantFrom('/admin/roles', '/admin/roles/transfer', '/admin/groups', '/admin'),
  order: fc.integer({ min: 0, max: 1000 }),
  visibleWhen: fc.constant('ADMIN' as const),
  section: fc.constant('ADMIN' as const),
  icon: fc.option(fc.constantFrom(...ALLOWED_ICONS), { nil: undefined }),
})
const registrationsArb: fc.Arbitrary<FeatureRegistration[]> = fc
  .uniqueArray(adminItemArb, { maxLength: 4, selector: (item) => item.id })
  .map((sidebarItems) => [{ featureId: 'adm', sidebarItems }])

const loginArb: fc.Arbitrary<LoginState> = fc.boolean().map((admin) => ({ loggedIn: true, admin }))

const locationArb = fc.oneof(
  fc.constant({ pathname: '/', search: '' }),
  fc
    .constantFrom(
      '/admin',
      '/admin/roles',
      '/admin/roles/3/assignments',
      '/admin/roles/transfer',
      '/x',
    )
    .map((pathname) => ({ pathname, search: '' })),
  fc
    .record({
      schema: fc.constantFrom('S', 'A/B', '..', SCRIPT_URL),
      table: fc.constantFrom('T1', 'T2', 'x?y#z', '100%', 'https://example.com/'),
      item: fc.option(
        fc.oneof(
          fc.constantFrom('0', '1', '0.0', '0.1', '1.0', '0.0.0'),
          fc.string({ maxLength: 8 }),
        ),
        { nil: null },
      ),
    })
    .map(({ schema, table, item }) => {
      const query = new URLSearchParams({ schema, table })
      if (item !== null) {
        query.set('item', item)
      }
      return { pathname: '/tables', search: `?${query.toString()}` }
    }),
  fc.string().map((pathname) => ({ pathname, search: '' })),
)

const inputArb = fc.record({
  tree: treeArb,
  registrations: registrationsArb,
  loginState: loginArb,
  location: locationArb,
  language: fc.constantFrom('ja' as const, 'en' as const),
})

function build(values: {
  tree: NavNode[]
  registrations: FeatureRegistration[]
  loginState: LoginState
  location: { pathname: string; search: string }
  language: 'ja' | 'en'
}) {
  const input: BuildNavInput = {
    loginState: values.loginState,
    registrations: values.registrations,
    business: { status: 'loaded', response: { items: values.tree, emptyReason: null } },
    location: values.location,
    language: values.language,
    t: (key) => key,
    navigate: () => {},
  }
  return buildNavSections(input)
}

function businessSection(sections: readonly SidebarNavSection[]): SidebarNavSection | undefined {
  return sections.find((section) => section.id === 'business')
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

/** 応答の木から、子も道も無い項目を落とした id の木（参照の実装） */
interface IdTree {
  id: string
  children: IdTree[]
}
function expectedTree(nodes: readonly NavNode[]): IdTree[] {
  return nodes.flatMap((node) => {
    const children = expectedTree(node.items)
    const linked = node.navigable && node.table !== null
    return children.length === 0 && !linked ? [] : [{ id: node.id, children }]
  })
}
function builtTree(items: readonly SidebarNavItem[]): IdTree[] {
  return items.map((item) => ({
    id: item.id.replace(/^biz:/, ''),
    children: builtTree(item.children ?? []),
  }))
}

/** 応答の木を行きがけの順に、移れる項目（道を持つ）とその祖先の id を集める（参照の実装） */
function navigableInPreOrder(
  nodes: readonly NavNode[],
  ancestors: string[] = [],
): { node: NavNode; ancestors: string[] }[] {
  return nodes.flatMap((node) => [
    ...(node.navigable && node.table !== null ? [{ node, ancestors }] : []),
    ...navigableInPreOrder(node.items, [...ancestors, `biz:${node.id}`]),
  ])
}

describe('buildNavSections properties', () => {
  it('1. keeps the parent-child structure and the order of the response with biz: ids', () => {
    fc.assert(
      fc.property(inputArb, (values) => {
        const section = businessSection(build(values).sections)
        const expected = expectedTree(values.tree)
        expect(section === undefined ? [] : builtTree(section.items)).toEqual(expected)
      }),
    )
  })

  it('2. builds only home, /tables or registered links, and /tables links read back the same names', () => {
    fc.assert(
      fc.property(inputArb, (values) => {
        const items = allItems(build(values).sections)
        const registered = new Set(
          values.registrations.flatMap((r) => (r.sidebarItems ?? []).map((item) => item.path)),
        )
        const sources = new Map<string, NavNode>()
        const collect = (nodes: readonly NavNode[]) =>
          nodes.forEach((node) => {
            sources.set(`biz:${node.id}`, node)
            collect(node.items)
          })
        collect(values.tree)
        for (const item of items) {
          if (item.href === undefined) {
            continue
          }
          const url = new URL(item.href, 'https://app.example.com')
          expect(url.origin).toBe('https://app.example.com')
          if (item.id.startsWith('biz:')) {
            expect(item.href.startsWith('/tables?')).toBe(true)
            const source = sources.get(item.id)
            expect(readTablePlaceholderParams(url.search)).toEqual({
              ref: source?.table,
              itemId: source?.id,
            })
          } else {
            expect(item.href === '/' || registered.has(item.href)).toBe(true)
          }
        }
      }),
    )
  })

  it('3. uses only icons of the allowed list', () => {
    const allowed = new Set<string>(ALLOWED_ICONS)
    fc.assert(
      fc.property(inputArb, (values) => {
        for (const item of allItems(build(values).sections)) {
          expect(item.icon === undefined || allowed.has(item.icon)).toBe(true)
        }
      }),
    )
  })

  it('4. marks at most one current item and opening its ancestors makes it reachable', () => {
    fc.assert(
      fc.property(inputArb, (values) => {
        const output = build(values)
        const current = allItems(output.sections).filter((item) => item.current)
        expect(current.length).toBeLessThanOrEqual(1)
        const expanded = addAncestors(new Set(), output.currentAncestorIds)
        if (current.length === 1) {
          // 開いた枝だけをたどって今の項目に届くこと
          const reachable = (items: readonly SidebarNavItem[]): boolean =>
            items.some(
              (item) =>
                item.current === true || (expanded.has(item.id) && reachable(item.children ?? [])),
            )
          expect(output.sections.some((section) => reachable(section.items))).toBe(true)
        } else {
          expect(output.currentAncestorIds).toEqual([])
        }
      }),
    )
  })

  it('5. leaves no item without children and a link, and no headed section without items', () => {
    fc.assert(
      fc.property(inputArb, (values) => {
        const { sections } = build(values)
        for (const section of sections) {
          expect(section.items.length).toBeGreaterThan(0)
        }
        for (const item of allItems(sections)) {
          expect(item.href !== undefined || (item.children?.length ?? 0) > 0).toBe(true)
        }
      }),
    )
  })

  it('6. shows the admin section only to admins, and the business section regardless of admin', () => {
    fc.assert(
      fc.property(inputArb, (values) => {
        const asAdmin = build({ ...values, loginState: { loggedIn: true, admin: true } })
        const asMember = build({ ...values, loginState: { loggedIn: true, admin: false } })
        const hasAdminItems = values.registrations.some((r) => (r.sidebarItems ?? []).length > 0)
        expect(asAdmin.sections.some((s) => s.id === 'admin')).toBe(hasAdminItems)
        expect(asMember.sections.some((s) => s.id === 'admin')).toBe(false)
        expect(builtTree(businessSection(asAdmin.sections)?.items ?? [])).toEqual(
          builtTree(businessSection(asMember.sections)?.items ?? []),
        )
      }),
    )
  })

  it('7. chooses the item named by item, otherwise the first in pre-order for the table', () => {
    fc.assert(
      fc.property(inputArb, (values) => {
        const output = build(values)
        const current = allItems(output.sections).find((item) => item.current)
        if (values.location.pathname !== '/tables') {
          expect(current === undefined || !current.id.startsWith('biz:')).toBe(true)
          return
        }
        const { ref, itemId } = readTablePlaceholderParams(values.location.search)
        const candidates = navigableInPreOrder(values.tree).filter(
          ({ node }) =>
            ref !== null &&
            node.table?.schemaName === ref.schemaName &&
            node.table.tableName === ref.tableName,
        )
        const expected = candidates.find(({ node }) => node.id === itemId) ?? candidates[0]
        expect(current?.id).toBe(expected === undefined ? undefined : `biz:${expected.node.id}`)
        expect(output.currentAncestorIds).toEqual(expected?.ancestors ?? [])
      }),
    )
  })
})
