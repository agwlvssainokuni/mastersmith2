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
// カードの移り先の道を決める関数のテスト（Intent 261102-timezone-ui-polish の U5、D17）。
import { describe, expect, it } from 'vitest'
import { firstNavigablePath } from './navDestination'
import type { NavNode } from './navigationApi'
import { toTablePlaceholderPath } from './tablePlaceholderPath'

const SCRIPT_URL = ['javascript', 'alert(1)'].join(':')

function node(id: string, extra: Partial<NavNode> = {}): NavNode {
  return {
    id,
    label: { ja: id, en: id },
    icon: 'list',
    table: null,
    navigable: false,
    items: [],
    tableKind: null,
    ...extra,
  }
}

function leaf(id: string, tableName: string, items: NavNode[] = []): NavNode {
  return node(id, {
    table: { schemaName: 'S', tableName },
    navigable: true,
    tableKind: 'TABLE',
    items,
  })
}

describe('firstNavigablePath', () => {
  it('returns the own path of an openable item', () => {
    expect(firstNavigablePath(leaf('3', 'T1'))).toBe(
      toTablePlaceholderPath({ schemaName: 'S', tableName: 'T1' }, '3'),
    )
  })

  it('returns the own path of an openable item that has children', () => {
    expect(firstNavigablePath(leaf('1', 'PARENT', [leaf('1.0', 'CHILD')]))).toBe(
      toTablePlaceholderPath({ schemaName: 'S', tableName: 'PARENT' }, '1'),
    )
  })

  it('returns the first openable descendant of a group in depth-first order', () => {
    const group = node('0', {
      items: [
        node('0.0', { items: [leaf('0.0.0', 'DEEP'), leaf('0.0.1', 'DEEP2')] }),
        leaf('0.1', 'SIBLING'),
      ],
    })

    expect(firstNavigablePath(group)).toBe(
      toTablePlaceholderPath({ schemaName: 'S', tableName: 'DEEP' }, '0.0.0'),
    )
  })

  it('returns null when nothing can be opened (an unexpected shape)', () => {
    expect(firstNavigablePath(node('0', { items: [node('0.0')] }))).toBeNull()
    expect(firstNavigablePath(node('0'))).toBeNull()
  })

  it('builds a path from the encoded fixed form only, whatever the names are', () => {
    const path = firstNavigablePath(
      node('0', {
        items: [
          node('0.0', {
            table: { schemaName: SCRIPT_URL, tableName: '//evil/x?y#z' },
            navigable: true,
          }),
        ],
      }),
    )

    expect(path).not.toBeNull()
    expect(path?.startsWith('/tables?')).toBe(true)
    expect(path).not.toContain('//evil')
  })
})
