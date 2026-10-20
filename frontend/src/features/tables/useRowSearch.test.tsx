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
// 一覧と検索の状態のテスト（Intent 261016-business-data-views の U3 の L7、D3・D14・F2・F6・F8、計画 Step 10、
// U3-NFR1.9・8.7、R-04）。作業ロールと業務のメニューは偽の API で包む。
import { act, waitFor } from '@testing-library/react'
import { useEffect } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { deferred, SALES } from '../../app/testing/frameTestSupport'
import {
  fakeProvider,
  renderWithProviders,
  resetDisplayTestState,
} from '../../app/testing/renderWithProviders'
import type { SearchRowsRequest } from './businessDataApi'
import { parseRowPage } from './businessDataParse'
import { clearBusinessDataStore, readTableEntry } from './businessDataStore'
import type { RowPage, TableDefinition } from './businessDataTypes'
import { SAMPLE_REF, wirePage, wireRow } from './testing/businessDataSamples'
import { sampleDefinition } from './testing/sampleDefinition'
import { tableFrame } from './testing/tableProviders'
import { useRowSearch, type RowSearchState } from './useRowSearch'

beforeEach(() => clearBusinessDataStore())
afterEach(() => {
  clearBusinessDataStore()
  resetDisplayTestState()
})

function page(codes: string[], overrides: Record<string, unknown> = {}): RowPage {
  const parsed = parseRowPage(
    wirePage(
      codes.map((code) => wireRow(code)),
      overrides,
    ),
  )
  if (parsed === undefined) {
    throw new Error('the sample page does not pass the parser')
  }
  return parsed
}

function problem(status: number, code: string, body: Record<string, unknown> = {}) {
  const error = { kind: 'response', status, code }
  Object.defineProperty(error, 'problem', { value: { status, code, ...body }, enumerable: false })
  return error
}

function render(
  search: (request: SearchRowsRequest) => Promise<RowPage>,
  definition: TableDefinition = sampleDefinition(),
) {
  const latest: { state: RowSearchState | null } = { state: null }
  const onStale = vi.fn()
  function Probe() {
    const state = useRowSearch(SAMPLE_REF, definition, onStale, search)
    useEffect(() => {
      latest.state = state
    })
    return null
  }
  const frame = tableFrame()
  const view = renderWithProviders(
    <frame.Frame>
      <Probe />
    </frame.Frame>,
    { provider: fakeProvider({ loggedIn: true, admin: false }) },
  )
  return { latest, onStale, view, frame }
}

describe('useRowSearch', () => {
  it('sends one search with the default state and one more per state change', async () => {
    const search = vi.fn((request: SearchRowsRequest) =>
      Promise.resolve(page(['A01'], { page: request.page })),
    )
    const { latest } = render(search)
    await waitFor(() => expect(latest.state?.status).toBe('ready'))
    expect(search).toHaveBeenCalledTimes(1)
    expect(search.mock.calls[0][0]).toMatchObject({ conditions: [], sort: null, page: 1, size: 20 })
    const state = latest.state?.listState
    if (!state) throw new Error('no list state')
    act(() => latest.state?.setListState({ ...state, page: 2 }))
    await waitFor(() => expect(latest.state?.page?.page).toBe(2))
    expect(search).toHaveBeenCalledTimes(2)
    expect(readTableEntry(SAMPLE_REF, SALES.roleId).listState?.page).toBe(2)
  })

  it('keeps the previous table busy while reloading and drops a late older answer', async () => {
    const first = deferred<RowPage>()
    const second = deferred<RowPage>()
    const search = vi
      .fn<(request: SearchRowsRequest) => Promise<RowPage>>()
      .mockResolvedValueOnce(page(['A01']))
      .mockReturnValueOnce(first.promise)
      .mockReturnValueOnce(second.promise)
    const { latest } = render(search)
    await waitFor(() => expect(latest.state?.page?.items[0]?.values.code).toBe('A01'))
    const state = latest.state?.listState
    if (!state) throw new Error('no list state')
    act(() => latest.state?.setListState({ ...state, page: 2 }))
    expect(latest.state?.busy).toBe(true)
    expect(latest.state?.page?.items[0]?.values.code).toBe('A01')
    act(() => latest.state?.setListState({ ...state, page: 3 }))
    await act(async () => second.resolve(page(['C01'], { page: 3 })))
    await act(async () => first.resolve(page(['B01'], { page: 2 })))
    expect(latest.state?.page?.items[0]?.values.code).toBe('C01')
    expect(latest.state?.busy).toBe(false)
  })

  it('asks for a definition reload when the answer or a 400 says the definition changed', async () => {
    const search = vi
      .fn<(request: SearchRowsRequest) => Promise<RowPage>>()
      .mockResolvedValueOnce(page(['A01'], { definitionChanged: true }))
      .mockRejectedValueOnce(
        problem(400, 'VALIDATION_FAILED', {
          fieldErrors: [{ field: 'gone', reason: 'NOT_ALLOWED' }],
        }),
      )
    const { latest, onStale } = render(search)
    await waitFor(() => expect(onStale).toHaveBeenCalledTimes(1))
    const state = latest.state?.listState
    if (!state) throw new Error('no list state')
    act(() => latest.state?.setListState({ ...state, page: 2 }))
    await waitFor(() => expect(onStale).toHaveBeenCalledTimes(2))
    expect(latest.state?.page?.items).toHaveLength(1)
  })

  it('ties field errors of a 400 to the search fields and keeps the previous table', async () => {
    const search = vi
      .fn<(request: SearchRowsRequest) => Promise<RowPage>>()
      .mockResolvedValueOnce(page(['A01']))
      .mockRejectedValueOnce(
        problem(400, 'VALIDATION_FAILED', {
          fieldErrors: [{ field: 'code', reason: 'INVALID_VALUE' }],
        }),
      )
      .mockRejectedValueOnce(problem(400, 'VALIDATION_FAILED'))
    const { latest } = render(search)
    await waitFor(() => expect(latest.state?.status).toBe('ready'))
    const state = latest.state?.listState
    if (!state) throw new Error('no list state')
    act(() => latest.state?.setListState({ ...state, page: 2 }))
    await waitFor(() =>
      expect(latest.state?.fieldErrors).toEqual([{ field: 'code', reason: 'INVALID_VALUE' }]),
    )
    expect(latest.state?.page?.items).toHaveLength(1)
    act(() => latest.state?.setListState({ ...state, page: 3 }))
    await waitFor(() => expect(latest.state?.notice?.kind).toBe('bad-request'))
    expect(latest.state?.status).toBe('ready')
  })

  it('shows a 403 as a problem, reloads the menu and work role, and sends no more searches', async () => {
    const search = vi.fn(() => Promise.reject(problem(403, 'ACCESS_DENIED')))
    const { latest, frame } = render(search)
    await waitFor(() => expect(latest.state?.failure?.kind).toBe('forbidden'))
    expect(latest.state?.page).toBeNull()
    await waitFor(() => expect(frame.navCalls.count).toBe(2))
    await act(async () => new Promise((resolve) => setTimeout(resolve, 30)))
    expect(search).toHaveBeenCalledTimes(1)
  })

  it('restores the list state kept in the store for the table and retries after a failure', async () => {
    const search = vi
      .fn<(request: SearchRowsRequest) => Promise<RowPage>>()
      .mockRejectedValueOnce({ kind: 'network' })
      .mockImplementation((request) => Promise.resolve(page(['A01'], { size: request.size })))
    const first = render(search)
    await waitFor(() => expect(first.latest.state?.failure?.kind).toBe('network'))
    act(() => first.latest.state?.retry())
    await waitFor(() => expect(first.latest.state?.status).toBe('ready'))
    const state = first.latest.state?.listState
    if (!state) throw new Error('no list state')
    act(() => first.latest.state?.setListState({ ...state, size: 50 }))
    await waitFor(() => expect(first.latest.state?.page?.size).toBe(50))
    first.view.unmount()
    const second = render(search)
    await waitFor(() => expect(second.latest.state?.status).toBe('ready'))
    expect(second.latest.state?.listState?.size).toBe(50)
  })
})
