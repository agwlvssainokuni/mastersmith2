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
// 行の書き込みの送信のテスト（Intent 261016-business-data-views の U3 の L7、計画 Step 33、D23）。
import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SLOW_NOTICE_MS } from '../../shared/slow-notice/useSlowNotice'
import { useRowWrite, type RowWriteApi } from './useRowWrite'
import type { WriteOutcome } from './writeOutcome'

const REF = { schemaName: 'shop', tableName: 'item' }
const ID = { dslHash: 'h', workRoleId: 1 }

function fakeApi(): {
  api: RowWriteApi
  resolve: (outcome: WriteOutcome) => void
  calls: string[]
} {
  let resolve: (outcome: WriteOutcome) => void = () => {}
  const calls: string[] = []
  const pending = () =>
    new Promise<WriteOutcome>((done) => {
      resolve = done
    })
  return {
    calls,
    resolve: (outcome) => resolve(outcome),
    api: {
      createRow: () => {
        calls.push('create')
        return pending()
      },
      updateRow: () => {
        calls.push('update')
        return pending()
      },
      deleteRow: () => {
        calls.push('delete')
        return pending()
      },
    },
  }
}

afterEach(() => {
  vi.useRealTimers()
})

describe('useRowWrite', () => {
  it('sends one write at a time and ignores a second press while saving', async () => {
    const { api, resolve, calls } = fakeApi()
    const { result } = renderHook(() => useRowWrite(REF, api))
    let first: Promise<WriteOutcome> | undefined
    act(() => {
      first = result.current.create(ID, { code: 'A' })
    })
    expect(result.current.saving).toBe(true)
    let second: Promise<WriteOutcome> | undefined
    act(() => {
      second = result.current.update(ID, { code: 'A' }, {})
    })
    expect(second).toBeUndefined()
    expect(calls).toEqual(['create'])
    await act(async () => {
      resolve({ kind: 'saved', key: null })
      await first
    })
    expect(result.current.saving).toBe(false)
  })

  it('tells slow after five seconds and stops after the answer', async () => {
    vi.useFakeTimers()
    const { api, resolve } = fakeApi()
    const { result } = renderHook(() => useRowWrite(REF, api))
    let pending: Promise<WriteOutcome> | undefined
    act(() => {
      pending = result.current.remove(ID, { code: 'A' })
    })
    act(() => {
      vi.advanceTimersByTime(SLOW_NOTICE_MS - 1)
    })
    expect(result.current.slow).toBe(false)
    act(() => {
      vi.advanceTimersByTime(1)
    })
    expect(result.current.slow).toBe(true)
    await act(async () => {
      resolve({ kind: 'unknown' })
      await pending
    })
    expect(result.current.slow).toBe(false)
  })

  it('passes the update and delete arguments', async () => {
    const seen: unknown[] = []
    const api: RowWriteApi = {
      createRow: (...args) => {
        seen.push(['create', ...args])
        return Promise.resolve({ kind: 'saved', key: null })
      },
      updateRow: (...args) => {
        seen.push(['update', ...args])
        return Promise.resolve({ kind: 'saved', key: null })
      },
      deleteRow: (...args) => {
        seen.push(['delete', ...args])
        return Promise.resolve({ kind: 'saved', key: null })
      },
    }
    const { result } = renderHook(() => useRowWrite(REF, api))
    await act(async () => {
      await result.current.update(ID, { code: 'K' }, { name: '' })
    })
    await act(async () => {
      await result.current.remove(ID, { code: 'K' })
    })
    expect(seen).toEqual([
      ['update', REF, ID, { code: 'K' }, { name: '' }],
      ['delete', REF, ID, { code: 'K' }],
    ])
  })
})
