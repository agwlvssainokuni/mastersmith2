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
// 行の詳細の状態のテスト（Intent 261016-business-data-views の U3 の L7、D2・F3・F6・F8、計画 Step 10）。
import { act, waitFor } from '@testing-library/react'
import { useEffect } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { SALES } from '../../app/testing/frameTestSupport'
import {
  fakeProvider,
  renderWithProviders,
  resetDisplayTestState,
} from '../../app/testing/renderWithProviders'
import { parseRowDetail } from './businessDataParse'
import { clearBusinessDataStore, putRowKey } from './businessDataStore'
import type { RowDetail } from './businessDataTypes'
import { SAMPLE_REF, wireDetail } from './testing/businessDataSamples'
import { sampleDefinition } from './testing/sampleDefinition'
import { tableFrame } from './testing/tableProviders'
import { useRowDetail, type GetRowFn, type RowDetailState } from './useRowDetail'

beforeEach(() => clearBusinessDataStore())
afterEach(() => {
  clearBusinessDataStore()
  resetDisplayTestState()
})

function detail(code: string, overrides: Record<string, unknown> = {}): RowDetail {
  const parsed = parseRowDetail(wireDetail(code, overrides))
  if (parsed === undefined) {
    throw new Error('the sample detail does not pass the parser')
  }
  return parsed
}

function render(token: unknown, load: GetRowFn) {
  const latest: { state: RowDetailState | null } = { state: null }
  const onStale = vi.fn()
  const definition = sampleDefinition()
  function Probe() {
    const state = useRowDetail(SAMPLE_REF, token, definition, onStale, load)
    useEffect(() => {
      latest.state = state
    })
    return null
  }
  const frame = tableFrame()
  renderWithProviders(
    <frame.Frame>
      <Probe />
    </frame.Frame>,
    { provider: fakeProvider({ loggedIn: true, admin: false }) },
  )
  return { latest, onStale }
}

describe('useRowDetail', () => {
  it('reports missing without a request when the token is not in the store', async () => {
    const load = vi.fn<GetRowFn>()
    const { latest } = render('0123456789abcdef0123456789abcdef', load)
    await waitFor(() => expect(latest.state?.status).toBe('missing'))
    const other = render(undefined, load)
    await waitFor(() => expect(other.latest.state?.status).toBe('missing'))
    await act(async () => Promise.resolve())
    expect(load).not.toHaveBeenCalled()
  })

  it('reads the row with the key kept in the store', async () => {
    const token = putRowKey({ code: 'A/1?#' }, SALES.roleId)
    const load = vi.fn<GetRowFn>(() => Promise.resolve(detail('A/1?#')))
    const { latest } = render(token, load)
    await waitFor(() => expect(latest.state?.status).toBe('ready'))
    expect(load).toHaveBeenCalledWith(SAMPLE_REF, sampleDefinition().definitionId, {
      code: 'A/1?#',
    })
    expect(latest.state?.row?.values.code).toBe('A/1?#')
  })

  it('asks for a definition reload when the answer says the definition changed', async () => {
    const token = putRowKey({ code: 'A01' }, SALES.roleId)
    const { latest, onStale } = render(token, () =>
      Promise.resolve(detail('A01', { definitionChanged: true })),
    )
    await waitFor(() => expect(latest.state?.status).toBe('ready'))
    expect(onStale).toHaveBeenCalledTimes(1)
  })

  it('shows a missing row and retries once more on request', async () => {
    const token = putRowKey({ code: 'A01' }, SALES.roleId)
    const missingRow = { kind: 'response', status: 404, code: 'BUSINESS_ROW_NOT_FOUND' }
    const load = vi
      .fn<GetRowFn>()
      .mockRejectedValueOnce(missingRow)
      .mockResolvedValueOnce(detail('A01'))
    const { latest } = render(token, load)
    await waitFor(() => expect(latest.state?.failure?.kind).toBe('row-not-found'))
    act(() => latest.state?.retry())
    await waitFor(() => expect(latest.state?.status).toBe('ready'))
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('treats the key as missing after the store was cleared for another work role', async () => {
    const token = putRowKey({ code: 'A01' }, 99)
    const load = vi.fn<GetRowFn>()
    const { latest } = render(token, load)
    await waitFor(() => expect(latest.state?.status).toBe('missing'))
    expect(load).not.toHaveBeenCalled()
  })
})
