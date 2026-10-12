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
// 権限の設定の写しと entries の純粋な関数のテスト（Intent 261004-role-menu の U6 の D10・D12、NFR1.4・NFR6.2、
// 計画 2.3 の A7）。失敗したときの再現の仕方: fast-check は失敗の報告に seed と path を出すため、その fc.assert(property) を
// fc.assert(property, { seed: <seed>, path: '<path>' }) に一時的に替えてこのファイルだけを流す。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import type { Explicit, MainValue, PermissionScope, PermissionTarget } from './api/types'
import {
  applyChange,
  draftExplicit,
  EMPTY_DRAFT,
  inScope,
  retainDraft,
  scopeKey,
  targetKey,
  toEntries,
  type PermissionDraft,
} from './permissionDraft'

const NONE: Explicit = { main: null, create: null, delete: null }
const schema = (schemaName: string): PermissionTarget => ({
  schemaName,
  tableName: null,
  columnName: null,
})
const table = (schemaName: string, tableName: string): PermissionTarget => ({
  schemaName,
  tableName,
  columnName: null,
})
const column = (schemaName: string, tableName: string, columnName: string): PermissionTarget => ({
  schemaName,
  tableName,
  columnName,
})

/** 名前（記号・空白・引用符・配列の区切りを含む） */
const nameArbitrary = fc.string({ minLength: 1, maxLength: 6, unit: 'binary' })
const mainArbitrary = fc.constantFrom<MainValue | null>(null, 'NONE', 'READ', 'FULL')
const auxArbitrary = fc.constantFrom<boolean | null>(null, true, false)
const explicitArbitrary = fc.record({
  main: mainArbitrary,
  create: auxArbitrary,
  delete: auxArbitrary,
})
const targetArbitrary: fc.Arbitrary<PermissionTarget> = fc.oneof(
  nameArbitrary.map(schema),
  fc.tuple(nameArbitrary, nameArbitrary).map(([s, t]) => table(s, t)),
  fc.tuple(nameArbitrary, nameArbitrary, nameArbitrary).map(([s, t, c]) => column(s, t, c)),
)
const scopeArbitrary: fc.Arbitrary<PermissionScope> = fc.oneof(
  nameArbitrary.map((schemaName) => ({ schemaName, tableName: null })),
  fc
    .tuple(nameArbitrary, nameArbitrary)
    .map(([schemaName, tableName]) => ({ schemaName, tableName })),
)
/** カラムのサーバーの値は補助権限を持たない（D11） */
function serverFor(target: PermissionTarget, explicit: Explicit): Explicit {
  return target.columnName !== null ? { main: explicit.main, create: null, delete: null } : explicit
}
/** 対象ごとに1つのサーバーの値と、変えた値（同じ対象を何度変えてもよい） */
const changesArbitrary = fc
  .uniqueArray(fc.tuple(targetArbitrary, explicitArbitrary), {
    maxLength: 8,
    selector: ([target]) => targetKey(target),
  })
  .chain((targets) =>
    fc.array(
      fc.tuple(
        fc.constantFrom(...(targets.length > 0 ? targets : [[schema('S'), NONE] as const])),
        explicitArbitrary,
      ),
      { maxLength: 12 },
    ),
  )
  .map((changes) =>
    changes.map(
      ([[target, server], next]) =>
        [target, serverFor(target, server), next] as [PermissionTarget, Explicit, Explicit],
    ),
  )

function buildDraft(changes: readonly [PermissionTarget, Explicit, Explicit][]): PermissionDraft {
  return changes.reduce<PermissionDraft>(
    (draft, [target, server, next]) => applyChange(draft, server, target, next),
    EMPTY_DRAFT,
  )
}

describe('permissionDraft', () => {
  it('keys a target by the JSON array of its names', () => {
    expect(targetKey(schema('SALES'))).toBe('["SALES"]')
    expect(targetKey(table('SALES', 'ORDER_LINE'))).toBe('["SALES","ORDER_LINE"]')
    expect(targetKey(column('SALES', 'ORDER_LINE', 'NO'))).toBe('["SALES","ORDER_LINE","NO"]')
    expect(scopeKey({ schemaName: 'SALES', tableName: 'ORDER_LINE' })).toBe(
      '["SALES","ORDER_LINE"]',
    )
  })

  it('records a change and removes it when the value returns to the server value', () => {
    const server: Explicit = { main: 'READ', create: null, delete: null }
    const changed = applyChange(EMPTY_DRAFT, server, table('S', 'T'), { ...server, main: 'FULL' })
    expect(draftExplicit(changed, table('S', 'T'), server).main).toBe('FULL')
    const back = applyChange(changed, server, table('S', 'T'), server)
    expect(back.size).toBe(0)
    expect(draftExplicit(back, table('S', 'T'), server)).toEqual(server)
  })

  it('drops the auxiliary values of a column target', () => {
    const draft = applyChange(EMPTY_DRAFT, NONE, column('S', 'T', 'C'), {
      main: 'NONE',
      create: true,
      delete: false,
    })
    expect(toEntries(draft, { schemaName: 'S', tableName: 'T' })).toEqual([
      {
        schemaName: 'S',
        tableName: 'T',
        columnName: 'C',
        main: 'NONE',
        create: null,
        delete: null,
      },
    ])
  })

  it('limits a schema scope to the schema level and a table scope to the table and its columns', () => {
    expect(inScope(schema('S'), { schemaName: 'S', tableName: null })).toBe(true)
    expect(inScope(table('S', 'T'), { schemaName: 'S', tableName: null })).toBe(false)
    expect(inScope(table('S', 'T'), { schemaName: 'S', tableName: 'T' })).toBe(true)
    expect(inScope(column('S', 'T', 'C'), { schemaName: 'S', tableName: 'T' })).toBe(true)
    expect(inScope(column('S', 'U', 'C'), { schemaName: 'S', tableName: 'T' })).toBe(false)
    expect(inScope(schema('S'), { schemaName: 'S', tableName: 'T' })).toBe(false)
    expect(inScope(table('R', 'T'), { schemaName: 'S', tableName: 'T' })).toBe(false)
  })

  it('keeps only the targets that the predicate accepts', () => {
    let draft = applyChange(EMPTY_DRAFT, NONE, column('S', 'T', 'A'), { ...NONE, main: 'READ' })
    draft = applyChange(draft, NONE, column('S', 'T', 'B'), { ...NONE, main: 'FULL' })
    const kept = retainDraft(draft, (target) => target.columnName === 'B')
    expect([...kept.keys()]).toEqual(['["S","T","B"]'])
  })

  it('gives empty entries for an empty draft (property)', () => {
    fc.assert(
      fc.property(scopeArbitrary, (scope) => {
        expect(toEntries(EMPTY_DRAFT, scope)).toEqual([])
      }),
    )
  })

  it('removes every target from the draft once all values return to the server values (property)', () => {
    fc.assert(
      fc.property(changesArbitrary, (changes) => {
        const draft = buildDraft(changes)
        const restored = changes.reduce<PermissionDraft>(
          (current, [target, server]) => applyChange(current, server, target, server),
          draft,
        )
        expect(restored.size).toBe(0)
      }),
    )
  })

  it('never puts a target outside the scope into the entries and keeps one entry per target (property)', () => {
    fc.assert(
      fc.property(changesArbitrary, scopeArbitrary, (changes, scope) => {
        const entries = toEntries(buildDraft(changes), scope)
        for (const entry of entries) {
          expect(inScope(entry, scope)).toBe(true)
          if (scope.tableName === null) {
            expect(entry.tableName).toBeNull()
            expect(entry.columnName).toBeNull()
          } else {
            expect(entry.tableName).toBe(scope.tableName)
          }
        }
        const keys = entries.map((entry) => targetKey(entry))
        expect(new Set(keys).size).toBe(keys.length)
      }),
    )
  })

  it('does not let two different targets share a key whatever their names contain (property)', () => {
    fc.assert(
      fc.property(targetArbitrary, targetArbitrary, (a, b) => {
        const same =
          a.schemaName === b.schemaName &&
          a.tableName === b.tableName &&
          a.columnName === b.columnName
        expect(targetKey(a) === targetKey(b)).toBe(same)
      }),
    )
  })
})
