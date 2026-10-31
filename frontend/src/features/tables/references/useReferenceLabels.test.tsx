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
// ラベルの1件読みの同時の数と世代のテスト（Intent 261029-reference-lookup-cleanup の U3、D18、performance-design.md 2.1 の
// 確かめ (1)〜(6)、R-01・R-05）。答えは手で解く Promise で返し、時計・sleep に頼らない。
import { act, renderHook, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { deferred, type Deferred } from '../../../app/testing/frameTestSupport'
import { getReferenceLabel } from '../businessDataApi'
import type { ReferenceLabel, ReferenceSource } from '../businessDataTypes'
import { jsonResponse } from '../testing/businessDataSamples'
import { SAMPLE_SOURCE, wireLabel } from '../testing/referenceSamples'
import { LABEL_READ_CONCURRENCY, useReferenceLabels, type LabelItem } from './useReferenceLabels'

afterEach(() => vi.unstubAllGlobals())

interface Call {
  value: string
  signal: AbortSignal | undefined
  answer: Deferred<ReferenceLabel>
}

function fakeLoad() {
  const calls: Call[] = []
  const load = vi.fn((_source: ReferenceSource, value: string, signal?: AbortSignal) => {
    const answer = deferred<ReferenceLabel>()
    calls.push({ value, signal, answer })
    return answer.promise
  })
  return { load, calls }
}

function items(count: number): LabelItem[] {
  return Array.from({ length: count }, (_, index) => ({
    column: `c${index}`,
    source: { ...SAMPLE_SOURCE, column: `c${index}` },
    value: `V${index}`,
  }))
}

function settle(): Promise<void> {
  return act(async () => {
    await Promise.resolve()
  })
}

describe('useReferenceLabels', () => {
  it('sends three requests at once for three items (1)', async () => {
    const { load, calls } = fakeLoad()
    const { result } = renderHook(() => useReferenceLabels(items(3), 'k1', load))
    await settle()
    expect(LABEL_READ_CONCURRENCY).toBe(3)
    expect(calls.map((call) => call.value)).toEqual(['V0', 'V1', 'V2'])
    expect([...result.current.loading]).toEqual(['c0', 'c1', 'c2'])
  })

  it('sends the fourth only after one answer (2) and fills labels in any order (3)', async () => {
    const { load, calls } = fakeLoad()
    const { result } = renderHook(() => useReferenceLabels(items(4), 'k1', load))
    await settle()
    expect(calls).toHaveLength(3)
    await act(async () => calls[2].answer.resolve({ value: 'V2', label: '三' }))
    await waitFor(() => expect(calls).toHaveLength(4))
    await act(async () => calls[3].answer.resolve({ value: 'V3', label: '四' }))
    await act(async () => calls[0].answer.resolve({ value: 'V0', label: '一' }))
    await act(async () => calls[1].answer.resolve({ value: 'V1', label: null }))
    await waitFor(() =>
      expect(result.current.labels).toEqual({ c0: '一', c1: null, c2: '三', c3: '四' }),
    )
    expect(result.current.loading.size).toBe(0)
  })

  it('records a failure as null and still sends the next (4)', async () => {
    const { load, calls } = fakeLoad()
    const { result } = renderHook(() => useReferenceLabels(items(4), 'k1', load))
    await settle()
    await act(async () => calls[0].answer.reject({ kind: 'response', status: 403 }))
    await waitFor(() => expect(calls).toHaveLength(4))
    expect(result.current.labels).toEqual({ c0: null })
    expect(result.current.loading.has('c0')).toBe(false)
  })

  it('stops sending and drops answers after the screen is left (5)', async () => {
    const { load, calls } = fakeLoad()
    const { result, unmount } = renderHook(() => useReferenceLabels(items(5), 'k1', load))
    await settle()
    const last = result.current
    unmount()
    expect(calls.every((call) => call.signal?.aborted === true)).toBe(true)
    await act(async () => {
      calls.forEach((call, index) => call.answer.resolve({ value: `V${index}`, label: 'x' }))
    })
    expect(calls).toHaveLength(3)
    expect(last.labels).toEqual({})
  })

  it('aborts the old generation on reload and never sends its rest (6, R-05)', async () => {
    const { load, calls } = fakeLoad()
    const { result, rerender } = renderHook(
      ({ key }: { key: string }) => useReferenceLabels(items(5), key, load),
      { initialProps: { key: 'old' } },
    )
    await settle()
    const old = calls.slice(0, 3)
    rerender({ key: 'new' })
    await settle()
    expect(old.every((call) => call.signal?.aborted === true)).toBe(true)
    const fresh = calls.slice(3)
    expect(fresh.map((call) => call.value)).toEqual(['V0', 'V1', 'V2'])
    expect(fresh.every((call) => call.signal?.aborted === false)).toBe(true)

    // 古い世代の要求をすべて後から解いても、新しい世代の数とラベルは変わらず、古い世代の4・5項目目は送られない
    await act(async () => {
      old[0].answer.resolve({ value: 'V0', label: '古い' })
      old[1].answer.reject(new Error('aborted'))
      old[2].answer.resolve({ value: 'V2', label: '古い' })
    })
    expect(calls).toHaveLength(6)
    expect(result.current.labels).toEqual({})

    await act(async () => fresh[0].answer.resolve({ value: 'V0', label: '新しい' }))
    await waitFor(() => expect(calls).toHaveLength(7))
    expect(calls[6].value).toBe('V3')
    expect(result.current.labels).toEqual({ c0: '新しい' })
    expect(calls.filter((call) => call.value === 'V4')).toHaveLength(0)
  })

  it('does not read anything for an empty key or no items', async () => {
    const { load, calls } = fakeLoad()
    const empty = renderHook(() => useReferenceLabels(items(2), '', load))
    const none = renderHook(() => useReferenceLabels([], 'k', load))
    await settle()
    expect(calls).toHaveLength(0)
    expect(empty.result.current.loading.size).toBe(0)
    expect(none.result.current.loading.size).toBe(0)
  })

  it('reads a label from the real call with a page null body and null on 403, 503 and a broken body', async () => {
    const fetchMock = vi.fn((_path: string, init?: RequestInit) => {
      const body = JSON.parse(String(init?.body)) as { value: string }
      switch (body.value) {
        case 'OK':
          return Promise.resolve(jsonResponse(200, wireLabel('OK', '青い 木製の椅子')))
        case 'DENIED':
          return Promise.resolve(jsonResponse(403, { status: 403, code: 'ACCESS_DENIED' }))
        case 'DOWN':
          return Promise.resolve(
            jsonResponse(503, { status: 503, code: 'BUSINESS_TARGET_DB_UNAVAILABLE' }),
          )
        default:
          return Promise.resolve(jsonResponse(200, { rows: [], page: null, hasNext: false }))
      }
    })
    vi.stubGlobal('fetch', fetchMock)
    const list: LabelItem[] = ['OK', 'DENIED', 'DOWN', 'BROKEN'].map((value) => ({
      column: value,
      source: SAMPLE_SOURCE,
      value,
    }))
    const { result } = renderHook(() => useReferenceLabels(list, 'k', getReferenceLabel))
    await waitFor(() => expect(result.current.loading.size).toBe(0))
    expect(result.current.labels).toEqual({
      OK: '青い 木製の椅子',
      DENIED: null,
      DOWN: null,
      BROKEN: null,
    })
  })
})
