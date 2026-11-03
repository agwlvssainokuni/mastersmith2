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
// ホームのカードを作る関数の性質ベースのテスト（Intent 261102-timezone-ui-polish の U5、D16〜D18、fast-check）。
// 失敗したときの seed と path は fast-check が出力に示す。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { ALLOWED_ICONS } from '../registry/allowedIcons'
import type { NavNode } from '../navigation/navigationApi'
import type { FeatureRegistration, LoginState } from '../registry/types'
import { buildHomeCards } from './homeCards'

interface RawNode {
  navigable: boolean
  tableName: string
  kind: 'TABLE' | 'VIEW' | null
  items: RawNode[]
}

function rawArb(depth: number): fc.Arbitrary<RawNode> {
  return fc.record({
    navigable: fc.boolean(),
    tableName: fc.constantFrom('T1', 'x?y#z', '100%', 'https://example.com/'),
    kind: fc.constantFrom<'TABLE' | 'VIEW' | null>('TABLE', 'VIEW', null),
    items: depth >= 3 ? fc.constant([]) : fc.array(rawArb(depth + 1), { maxLength: 2 }),
  })
}

function toNodes(raws: readonly RawNode[], prefix: string): NavNode[] {
  return raws.map((raw, index) => {
    const id = prefix === '' ? String(index) : `${prefix}.${index}`
    return {
      id,
      label: { ja: `<b>${id}</b>`, en: id },
      icon: 'list',
      table: raw.navigable ? { schemaName: 'S', tableName: raw.tableName } : null,
      navigable: raw.navigable,
      items: toNodes(raw.items, id),
      tableKind: raw.navigable ? raw.kind : null,
    }
  })
}

const treeArb = fc.array(rawArb(1), { maxLength: 4 }).map((raws) => toNodes(raws, ''))

const adminArb: fc.Arbitrary<FeatureRegistration[]> = fc
  .uniqueArray(
    fc.record({
      id: fc.constantFrom('a', 'b', 'c', 'd'),
      labelKey: fc.constant('x.nav'),
      path: fc.constantFrom('/admin/a', '/admin/b', '//evil/', 'https://example.com/', '/x'),
      order: fc.integer({ min: 0, max: 100 }),
      visibleWhen: fc.constant('ADMIN' as const),
      section: fc.constant('ADMIN' as const),
      icon: fc.option(fc.constantFrom(...ALLOWED_ICONS), { nil: undefined }),
    }),
    { maxLength: 4, selector: (item) => item.id },
  )
  .map((sidebarItems) => [{ featureId: 'adm', sidebarItems }])

const loginArb: fc.Arbitrary<LoginState> = fc.boolean().map((admin) => ({ loggedIn: true, admin }))

function allIds(nodes: readonly NavNode[]): string[] {
  return nodes.flatMap((node) => [node.id, ...allIds(node.items)])
}

describe('buildHomeCards properties', () => {
  it('never makes a card for an item that is not in the response, and keeps the response order', () => {
    fc.assert(
      fc.property(treeArb, loginArb, adminArb, (tree, loginState, registrations) => {
        const cards = buildHomeCards({
          business: { status: 'loaded', response: { items: tree, emptyReason: null } },
          loginState,
          registrations,
          language: 'ja',
          t: (key) => key,
        })
        const topIds = tree.map((node) => `biz:${node.id}`)
        const cardIds = cards.business.map((card) => card.id)
        expect(cardIds.every((id) => topIds.includes(id))).toBe(true)
        expect(cardIds).toEqual(topIds.filter((id) => cardIds.includes(id)))
        expect(allIds(tree).length).toBeGreaterThanOrEqual(cardIds.length)
      }),
    )
  })

  it('makes no admin card unless the user is an administrator, and orders them by order', () => {
    fc.assert(
      fc.property(treeArb, loginArb, adminArb, (tree, loginState, registrations) => {
        const cards = buildHomeCards({
          business: { status: 'loaded', response: { items: tree, emptyReason: null } },
          loginState,
          registrations,
          language: 'en',
          t: (key) => key,
        })
        if (!loginState.admin) {
          expect(cards.admin).toEqual([])
          return
        }
        const orderOf = new Map(
          registrations
            .flatMap((r) => r.sidebarItems ?? [])
            .map((item) => [`adm:${item.id}`, item.order]),
        )
        const orders = cards.admin.map((card) => orderOf.get(card.id) ?? -1)
        expect(orders).toEqual([...orders].sort((a, b) => a - b))
      }),
    )
  })

  it('only makes cards whose path stays inside the application and whose icon is allowed', () => {
    const allowed = new Set<string>(ALLOWED_ICONS)
    fc.assert(
      fc.property(treeArb, adminArb, (tree, registrations) => {
        const cards = buildHomeCards({
          business: { status: 'loaded', response: { items: tree, emptyReason: null } },
          loginState: { loggedIn: true, admin: true },
          registrations,
          language: 'ja',
          t: (key) => key,
        })
        for (const card of [...cards.business, ...cards.admin]) {
          expect(card.path.startsWith('/') && !card.path.startsWith('//')).toBe(true)
          expect(allowed.has(card.icon)).toBe(true)
        }
      }),
    )
  })
})
