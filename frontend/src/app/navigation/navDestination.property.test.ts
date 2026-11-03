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
// カードの移り先の道を決める関数の性質ベースのテスト（Intent 261102-timezone-ui-polish の U5、D17・NFR2.2.2、fast-check）。
// 失敗したときの seed と path は fast-check が出力に示す。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import { firstNavigablePath } from './navDestination'
import type { NavNode } from './navigationApi'
import { toTablePlaceholderPath } from './tablePlaceholderPath'

interface RawNode {
  navigable: boolean
  tableName: string
  items: RawNode[]
}

function rawArb(depth: number): fc.Arbitrary<RawNode> {
  return fc.record({
    navigable: fc.boolean(),
    tableName: fc.constantFrom('T1', 'T2', 'x?y#z', '100%', 'https://example.com/'),
    items: depth >= 4 ? fc.constant([]) : fc.array(rawArb(depth + 1), { maxLength: 3 }),
  })
}

function toNodes(raws: readonly RawNode[], prefix: string): NavNode[] {
  return raws.map((raw, index) => {
    const id = prefix === '' ? String(index) : `${prefix}.${index}`
    return {
      id,
      label: { ja: id, en: id },
      icon: 'list',
      table: raw.navigable ? { schemaName: 'S', tableName: raw.tableName } : null,
      navigable: raw.navigable,
      items: toNodes(raw.items, id),
      tableKind: raw.navigable ? 'TABLE' : null,
    }
  })
}

/** 行きがけの順の、移れる項目の道の一覧（参照の実装） */
function openablePaths(node: NavNode): string[] {
  const own =
    node.navigable && node.table !== null ? [toTablePlaceholderPath(node.table, node.id)] : []
  return [...own, ...node.items.flatMap(openablePaths)]
}

function countNodes(node: NavNode): number {
  return 1 + node.items.reduce((sum, child) => sum + countNodes(child), 0)
}

const rootArb = fc
  .array(rawArb(1), { minLength: 1, maxLength: 3 })
  .map((raws) => toNodes(raws, '')[0])

describe('firstNavigablePath properties', () => {
  it('returns the first openable path in pre-order, or null when there is none', () => {
    fc.assert(
      fc.property(rootArb, (root) => {
        const expected = openablePaths(root)
        expect(firstNavigablePath(root)).toBe(expected.length > 0 ? expected[0] : null)
      }),
    )
  })

  it('returns the own path for an openable item, even with children', () => {
    fc.assert(
      fc.property(rootArb, (root) => {
        if (root.navigable && root.table !== null) {
          expect(firstNavigablePath(root)).toBe(toTablePlaceholderPath(root.table, root.id))
        }
      }),
    )
  })

  it('never visits more items than the tree has', () => {
    fc.assert(
      fc.property(rootArb, (root) => {
        let visits = 0
        const counting = (node: NavNode): NavNode => {
          const proxy = { ...node }
          Object.defineProperty(proxy, 'items', {
            get() {
              visits += 1
              return node.items.map(counting)
            },
          })
          return proxy
        }
        firstNavigablePath(counting(root))
        // items を読む回数は、たどった移れない項目の数以下（項目数を超えない）
        expect(visits).toBeLessThanOrEqual(countNodes(root))
      }),
    )
  })
})
