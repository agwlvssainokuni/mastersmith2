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
// 開閉の状態の保存と照らし合わせのテスト（Intent 261004-role-menu の U7 の D16、L7、NFR1.6、計画 12節 D-7、機能設計の
// 承認の場の直し R-02）。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  addAncestors,
  clearExpanded,
  loadExpanded,
  NAV_EXPANDED_STORAGE_KEY,
  pruneExpanded,
  saveExpanded,
} from './navExpansion'

beforeEach(() => {
  sessionStorage.clear()
})

afterEach(() => {
  vi.restoreAllMocks()
  sessionStorage.clear()
})

describe('saving and loading the expanded ids', () => {
  it('saves only the ids as a JSON array and loads them back', () => {
    saveExpanded(new Set(['biz:0', 'biz:0.1', 'adm:roleadmin']))
    expect(JSON.parse(sessionStorage.getItem(NAV_EXPANDED_STORAGE_KEY) ?? '')).toEqual([
      'biz:0',
      'biz:0.1',
      'adm:roleadmin',
    ])
    expect(loadExpanded()).toEqual(new Set(['biz:0', 'biz:0.1', 'adm:roleadmin']))
  })

  it('removes the key instead of writing an empty set', () => {
    saveExpanded(new Set(['biz:0']))
    saveExpanded(new Set())
    expect(sessionStorage.getItem(NAV_EXPANDED_STORAGE_KEY)).toBeNull()
  })

  it('reads a broken or missing value as empty', () => {
    expect(loadExpanded()).toEqual(new Set())
    for (const raw of ['{', '"biz:0"', '{"a":1}', '[1,2]', '["biz:0",null]', 'null']) {
      sessionStorage.setItem(NAV_EXPANDED_STORAGE_KEY, raw)
      expect(loadExpanded()).toEqual(new Set())
    }
  })

  it('ignores storage errors when reading, writing and clearing', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw new Error('blocked')
    })
    expect(loadExpanded()).toEqual(new Set())
    expect(() => saveExpanded(new Set(['biz:0']))).not.toThrow()
    expect(() => saveExpanded(new Set())).not.toThrow()
    expect(() => clearExpanded()).not.toThrow()
  })

  it('removes the key on clear', () => {
    saveExpanded(new Set(['biz:0']))
    clearExpanded()
    expect(sessionStorage.getItem(NAV_EXPANDED_STORAGE_KEY)).toBeNull()
    expect(loadExpanded()).toEqual(new Set())
  })
})

describe('pruneExpanded', () => {
  it('drops business ids that are not in the current tree and keeps the others', () => {
    const ids = new Set(['biz:0', 'biz:9', 'adm:roleadmin', 'home'])
    expect(pruneExpanded(ids, ['biz:0', 'biz:0.1'])).toEqual(
      new Set(['biz:0', 'adm:roleadmin', 'home']),
    )
  })

  it('returns the same set when nothing is dropped', () => {
    const ids = new Set(['biz:0', 'adm:x'])
    expect(pruneExpanded(ids, ['biz:0'])).toBe(ids)
  })

  it('drops every business id for an empty tree', () => {
    expect(pruneExpanded(new Set(['biz:0', 'biz:1']), [])).toEqual(new Set())
  })
})

describe('addAncestors', () => {
  it('adds the ancestors and keeps the other ids', () => {
    expect(addAncestors(new Set(['biz:5']), ['biz:0', 'biz:0.1'])).toEqual(
      new Set(['biz:5', 'biz:0', 'biz:0.1']),
    )
  })

  it('returns the same set when every ancestor is already open, so closed branches stay closed', () => {
    const ids = new Set(['biz:0', 'biz:0.1'])
    expect(addAncestors(ids, ['biz:0', 'biz:0.1'])).toBe(ids)
    expect(addAncestors(ids, [])).toBe(ids)
  })
})
