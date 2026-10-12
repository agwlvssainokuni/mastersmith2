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
// 実効の値の文字のテスト（Intent 261004-role-menu の U6 の D11、計画 13節 Q4: A）。
import { describe, expect, it } from 'vitest'
import type { PermissionNode } from './api/types'
import { effectiveLabel, formatEffectiveLabel } from './permissionLabels'

const TEXTS: Readonly<Record<string, string>> = {
  'roleadmin.effective.explicit': '{{value}}（明示）',
  'roleadmin.effective.inheritedSchema': '{{value}}（スキーマ {{name}} から継承）',
  'roleadmin.effective.inheritedTable': '{{value}}（テーブル {{name}} から継承）',
  'roleadmin.effective.default': '{{value}}（既定）',
  'roleadmin.effective.aux': '{{value}}',
  'roleadmin.effective.auxExplicit': '{{value}}（明示）',
  'roleadmin.value.allowed': '可',
  'roleadmin.value.denied': '不可',
}

function t(key: string, values?: Readonly<Record<string, string | number>>): string {
  const text = TEXTS[key] ?? key
  return text.replace(/\{\{(\w+)\}\}/g, (_, name: string) => String(values?.[name] ?? ''))
}

function node(overrides: Partial<PermissionNode> = {}): PermissionNode {
  return {
    schemaName: 'SALES',
    tableName: 'ORDER_LINE',
    columnName: null,
    displayName: '受注明細',
    explicit: { main: null, create: null, delete: null },
    effective: { main: 'READ', create: false, delete: false },
    inheritedFrom: 'SCHEMA',
    inMenu: false,
    inCurrentDsl: true,
    hasChildren: false,
    ...overrides,
  }
}

const text = (target: PermissionNode, kind: 'main' | 'create' | 'delete', names = {}) =>
  formatEffectiveLabel(effectiveLabel(target, kind, names), t)

describe('permissionLabels', () => {
  it('labels an explicit main permission', () => {
    expect(
      text(
        node({ inheritedFrom: 'EXPLICIT', explicit: { main: 'READ', create: null, delete: null } }),
        'main',
      ),
    ).toBe('READ（明示）')
  })

  it('labels a main permission inherited from the schema with its display name', () => {
    expect(text(node(), 'main', { schema: '販売DB' })).toBe('READ（スキーマ 販売DB から継承）')
  })

  it('falls back to the physical name when the display name of the parent is unknown', () => {
    expect(text(node(), 'main')).toBe('READ（スキーマ SALES から継承）')
    expect(text(node({ columnName: 'NO', inheritedFrom: 'TABLE' }), 'main')).toBe(
      'READ（テーブル ORDER_LINE から継承）',
    )
  })

  it('labels a main permission inherited from the table with its display name', () => {
    expect(
      text(node({ columnName: 'NO', inheritedFrom: 'TABLE' }), 'main', { table: '受注明細' }),
    ).toBe('READ（テーブル 受注明細 から継承）')
  })

  it('labels the default main permission', () => {
    expect(
      text(
        node({
          inheritedFrom: 'DEFAULT',
          effective: { main: 'NONE', create: false, delete: false },
        }),
        'main',
      ),
    ).toBe('NONE（既定）')
  })

  it('labels an auxiliary permission as allowed or denied and adds explicit only when set explicitly', () => {
    const explicitCreate = node({
      explicit: { main: null, create: true, delete: null },
      effective: { main: 'READ', create: true, delete: false },
    })
    expect(text(explicitCreate, 'create')).toBe('可（明示）')
    expect(text(explicitCreate, 'delete')).toBe('不可')
  })

  it('does not reuse the inheritance of the main permission for the auxiliary permissions', () => {
    const inherited = node({ effective: { main: 'FULL', create: true, delete: true } })
    expect(text(inherited, 'create', { schema: '販売DB' })).toBe('可')
    expect(text(inherited, 'delete', { schema: '販売DB' })).toBe('可')
  })

  it('keeps names with markup as plain values', () => {
    const label = effectiveLabel(node(), 'main', { schema: '<b>&"' })
    expect(label.name).toBe('<b>&"')
  })
})
