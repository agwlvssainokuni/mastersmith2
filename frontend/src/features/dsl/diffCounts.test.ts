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
// 違いの数え方のテスト（U2 dsl-v2 の BR5.2、計画の 10節 Q5: A）。
import { describe, expect, it } from 'vitest'
import type { DslDiff, SchemaDiff, TableChange, TableDiff } from './api/types'
import { allTables, countColumnChanges, countSchemaChanges, countTableChanges } from './diffCounts'
import { sampleTables } from './testing/fixtures'

function schema(name: string, change: TableChange, tables: TableDiff[] = []): SchemaDiff {
  return { name, label: { ja: name, en: name }, change, tables }
}

describe('diffCounts', () => {
  it('counts the schemas by change and leaves the unchanged ones out', () => {
    const diff: DslDiff = {
      appliedExists: true,
      schemas: [
        schema('a', 'ADDED'),
        schema('b', 'REMOVED'),
        schema('c', 'CHANGED'),
        schema('d', 'UNCHANGED'),
        schema('e', 'ADDED'),
      ],
    }

    expect(countSchemaChanges(diff)).toEqual({ added: 2, removed: 1, changed: 1 })
  })

  it('counts every schema as added when nothing is applied yet', () => {
    const diff: DslDiff = {
      appliedExists: false,
      schemas: [schema('sales', 'ADDED', sampleTables())],
    }

    expect(countSchemaChanges(diff)).toEqual({ added: 1, removed: 0, changed: 0 })
  })

  it('counts no schema when there are none', () => {
    expect(countSchemaChanges({ appliedExists: true, schemas: [] })).toEqual({
      added: 0,
      removed: 0,
      changed: 0,
    })
  })

  it('sums the tables and the columns of all schemas', () => {
    const diff: DslDiff = {
      appliedExists: true,
      schemas: [schema('a', 'UNCHANGED', sampleTables()), schema('b', 'CHANGED', sampleTables())],
    }

    expect(allTables(diff)).toHaveLength(8)
    expect(countTableChanges(diff)).toEqual({ added: 2, removed: 2, changed: 2 })
    expect(countColumnChanges(diff)).toEqual({ added: 4, removed: 4, changed: 2 })
  })
})
