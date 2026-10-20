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
// 業務データの記憶のテスト（Intent 261016-business-data-views の U3 の L3、計画 Step 5、U3-NFR4.2・4.22、R-04）。
import { afterEach, beforeEach, describe, expect, it } from 'vitest'
import { notifyLoginScopeChanged } from '../../app/login-state/loginScope'
import { parseTableDefinition } from './businessDataParse'
import {
  clearBusinessDataStore,
  currentStoreGeneration,
  peekRowKey,
  putRowKey,
  readTableEntry,
  saveDefinition,
  saveListState,
} from './businessDataStore'
import type { ListState, TableDefinition } from './businessDataTypes'
import { SAMPLE_REF, wireDefinition } from './testing/businessDataSamples'

const ROLE = 1

function definition(workRoleId: number | null = ROLE): TableDefinition {
  const parsed = parseTableDefinition(
    wireDefinition({ definitionId: { dslHash: 'h', workRoleId } }),
    SAMPLE_REF,
  )
  if (parsed === undefined) {
    throw new Error('sample definition is broken')
  }
  return parsed
}

const LIST: ListState = { search: {}, sort: null, page: 3, size: 50, searchExpanded: true }

function table(n: number) {
  return { schemaName: 's', tableName: `t${n}` }
}

beforeEach(() => clearBusinessDataStore())
afterEach(() => clearBusinessDataStore())

describe('businessDataStore', () => {
  it('returns a random 32 hex token unrelated to the key and keeps the key after reading', () => {
    const token = putRowKey({ code: 'A01' }, ROLE)
    expect(token).toMatch(/^[0-9a-f]{32}$/)
    expect(token).not.toContain('A01')
    expect(peekRowKey(token, ROLE)).toEqual({ code: 'A01' })
    expect(peekRowKey(token, ROLE)).toEqual({ code: 'A01' })
    expect(putRowKey({ code: 'A01' }, ROLE)).not.toBe(token)
    expect(peekRowKey(42, ROLE)).toBeUndefined()
    expect(peekRowKey('unknown', ROLE)).toBeUndefined()
  })

  it('drops the oldest token when the 101st one is put', () => {
    const tokens = Array.from({ length: 101 }, (_, i) => putRowKey({ code: `K${i}` }, ROLE))
    expect(peekRowKey(tokens[0], ROLE)).toBeUndefined()
    expect(peekRowKey(tokens[1], ROLE)).toEqual({ code: 'K1' })
    expect(peekRowKey(tokens[100], ROLE)).toEqual({ code: 'K100' })
  })

  it('keeps definitions and list states per table and drops the least recently used 101st table', () => {
    for (let i = 0; i < 100; i += 1) {
      saveListState(table(i), ROLE, LIST)
    }
    // t0 を読んで使った順の最後へ移すと、101 テーブル目で捨てられるのは t1 になる。
    expect(readTableEntry(table(0), ROLE).listState).toEqual(LIST)
    saveListState(table(100), ROLE, LIST)
    expect(readTableEntry(table(1), ROLE).listState).toBeNull()
    expect(readTableEntry(table(0), ROLE).listState).toEqual(LIST)
    expect(readTableEntry(table(100), ROLE).listState).toEqual(LIST)
  })

  it('clears everything on a login scope notification', () => {
    const token = putRowKey({ code: 'A01' }, ROLE)
    saveDefinition(SAMPLE_REF, ROLE, definition(), currentStoreGeneration())
    saveListState(SAMPLE_REF, ROLE, LIST)
    notifyLoginScopeChanged()
    expect(peekRowKey(token, ROLE)).toBeUndefined()
    expect(readTableEntry(SAMPLE_REF, ROLE)).toEqual({ definition: null, listState: null })
  })

  it.each([
    ['a number to another number', 1, 2],
    ['a number to null', 1, null],
    ['null to a number', null, 1],
  ])('clears everything when the work role changes from %s', (_, before, after) => {
    const token = putRowKey({ code: 'A01' }, before)
    saveListState(SAMPLE_REF, before, LIST)
    expect(peekRowKey(token, after)).toBeUndefined()
    expect(readTableEntry(SAMPLE_REF, before).listState).toBeNull()
  })

  it('does not write back an answer requested before the store was cleared', () => {
    const generation = currentStoreGeneration()
    clearBusinessDataStore()
    expect(saveDefinition(SAMPLE_REF, ROLE, definition(), generation)).toBe(false)
    expect(saveListState(SAMPLE_REF, ROLE, LIST, generation)).toBe(false)
    expect(readTableEntry(SAMPLE_REF, ROLE)).toEqual({ definition: null, listState: null })
    expect(saveDefinition(SAMPLE_REF, ROLE, definition(), currentStoreGeneration())).toBe(true)
    expect(readTableEntry(SAMPLE_REF, ROLE).definition?.displayName.ja).toBe('商品')
  })

  it('does not use a definition made for another work role', () => {
    saveDefinition(SAMPLE_REF, ROLE, definition(99), currentStoreGeneration())
    expect(readTableEntry(SAMPLE_REF, ROLE).definition).toBeNull()
  })

  it('never touches the browser storage', () => {
    localStorage.clear()
    sessionStorage.clear()
    putRowKey({ code: 'A01' }, ROLE)
    saveListState(SAMPLE_REF, ROLE, LIST)
    expect(localStorage.length).toBe(0)
    expect(sessionStorage.length).toBe(0)
  })
})
