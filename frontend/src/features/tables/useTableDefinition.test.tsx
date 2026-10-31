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
// テーブルの定義の状態のテスト（Intent 261016-business-data-views の U3 の L7、D4・F1・F6・F8、計画 Step 10、
// U3-NFR1.9・8.7、R-04・P-9）。作業ロールと業務のメニューは偽の API で包む。
import { act, waitFor } from '@testing-library/react'
import { useEffect } from 'react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { TableRef } from '../../app/navigation/tablePlaceholderPath'
import { deferred, SALES, ACCOUNTING, type Deferred } from '../../app/testing/frameTestSupport'
import type { WorkRoleState } from '../../app/work-role/workRoleApi'
import {
  fakeProvider,
  renderWithProviders,
  resetDisplayTestState,
} from '../../app/testing/renderWithProviders'
import { clearBusinessDataStore, readTableEntry } from './businessDataStore'
import type { DefinitionId, DefinitionPurpose, TableDefinition } from './businessDataTypes'
import { sampleDefinition } from './testing/sampleDefinition'
import { SAMPLE_REF } from './testing/businessDataSamples'
import { ONE_ROLE, tableFrame } from './testing/tableProviders'
import { useTableDefinition, type TableDefinitionState } from './useTableDefinition'

beforeEach(() => clearBusinessDataStore())
afterEach(() => {
  clearBusinessDataStore()
  resetDisplayTestState()
})

function forbidden() {
  return Object.assign({ kind: 'response', status: 403, code: 'ACCESS_DENIED' })
}

function render(
  ref: TableRef | null,
  load: (ref: TableRef, id?: DefinitionId, purpose?: DefinitionPurpose) => Promise<TableDefinition>,
  roles: (WorkRoleState | Deferred<WorkRoleState>)[] = [ONE_ROLE],
  purpose?: DefinitionPurpose,
) {
  const latest: { state: TableDefinitionState | null } = { state: null }
  function Probe({ target }: { target: TableRef | null }) {
    const state = useTableDefinition(target, load, purpose)
    useEffect(() => {
      latest.state = state
    })
    return null
  }
  const frame = tableFrame(roles)
  const view = renderWithProviders(
    <frame.Frame>
      <Probe target={ref} />
    </frame.Frame>,
    { provider: fakeProvider({ loggedIn: true, admin: false }) },
  )
  return { latest, view, frame }
}

describe('useTableDefinition', () => {
  it('waits for the work role, loads once and then uses the cached definition', async () => {
    const role = deferred<typeof ONE_ROLE>()
    const load = vi.fn(() => Promise.resolve(sampleDefinition()))
    const first = render(SAMPLE_REF, load, [role])
    await waitFor(() => expect(first.latest.state?.status).toBe('loading'))
    await act(async () => Promise.resolve())
    expect(load).not.toHaveBeenCalled()
    act(() => role.resolve(ONE_ROLE))
    await waitFor(() => expect(first.latest.state?.status).toBe('ready'))
    expect(load).toHaveBeenCalledTimes(1)
    expect(load).toHaveBeenCalledWith(SAMPLE_REF, undefined)
    first.view.unmount()

    const second = render(SAMPLE_REF, load)
    await waitFor(() => expect(second.latest.state?.status).toBe('ready'))
    expect(load).toHaveBeenCalledTimes(1)
  })

  it('reports a missing table without sending a request', async () => {
    const load = vi.fn(() => Promise.resolve(sampleDefinition()))
    const { latest } = render(null, load)
    await waitFor(() => expect(latest.state?.status).toBe('problem'))
    expect(latest.state?.failure?.kind).toBe('table-not-found')
    await act(async () => Promise.resolve())
    expect(load).not.toHaveBeenCalled()
  })

  it('reloads the menu and the work role once after a 403 and does not send the request again', async () => {
    const load = vi.fn(() => Promise.reject(forbidden()))
    const { latest, frame } = render(SAMPLE_REF, load)
    await waitFor(() => expect(latest.state?.failure?.kind).toBe('forbidden'))
    await waitFor(() => expect(frame.navCalls.count).toBe(2))
    expect(frame.roleCalls.get).toBe(2)
    await act(async () => new Promise((resolve) => setTimeout(resolve, 20)))
    expect(load).toHaveBeenCalledTimes(1)
  })

  it('reloads with the previous definition id and replaces the definition (F6)', async () => {
    const changed = sampleDefinition({ displayName: { ja: '新しい商品', en: 'New items' } })
    const load = vi
      .fn<(ref: TableRef, id?: DefinitionId) => Promise<TableDefinition>>()
      .mockResolvedValueOnce(sampleDefinition())
      .mockResolvedValueOnce(changed)
    const { latest } = render(SAMPLE_REF, load)
    await waitFor(() => expect(latest.state?.status).toBe('ready'))
    act(() => latest.state?.reload())
    await waitFor(() => expect(latest.state?.definition?.displayName.ja).toBe('新しい商品'))
    expect(load).toHaveBeenLastCalledWith(SAMPLE_REF, sampleDefinition().definitionId)
  })

  it('does not write back an answer that arrives after the screen is left', async () => {
    const answer = deferred<TableDefinition>()
    const { view } = render(SAMPLE_REF, () => answer.promise)
    await act(async () => Promise.resolve())
    view.unmount()
    await act(async () => answer.resolve(sampleDefinition()))
    expect(readTableEntry(SAMPLE_REF, SALES.roleId).definition).toBeNull()
  })

  it('loads again for another work role and offers a retry after a failure', async () => {
    const load = vi
      .fn<(ref: TableRef, id?: DefinitionId) => Promise<TableDefinition>>()
      .mockRejectedValueOnce({ kind: 'network' })
      .mockResolvedValue(sampleDefinition({ definitionId: { dslHash: 'h', workRoleId: 2 } }))
    const { latest } = render(SAMPLE_REF, load, [
      { roles: [SALES, ACCOUNTING], current: ACCOUNTING },
    ])
    await waitFor(() => expect(latest.state?.failure?.kind).toBe('network'))
    act(() => latest.state?.retry())
    await waitFor(() => expect(latest.state?.status).toBe('ready'))
    expect(readTableEntry(SAMPLE_REF, ACCOUNTING.roleId).definition?.definitionId.workRoleId).toBe(
      2,
    )
  })

  // Intent 261029-reference-lookup-cleanup の U3（D1・D2・X-2）
  it('loads with the purpose every time and neither reads nor writes the stored definition', async () => {
    const viewLoad = vi.fn(() => Promise.resolve(sampleDefinition()))
    const view = render(SAMPLE_REF, viewLoad)
    await waitFor(() => expect(view.latest.state?.status).toBe('ready'))
    view.view.unmount()
    expect(readTableEntry(SAMPLE_REF, SALES.roleId).definition).not.toBeNull()

    const purposed = sampleDefinition({ displayName: { ja: '作成用', en: 'For create' } })
    const load = vi.fn(() => Promise.resolve(purposed))
    const first = render(SAMPLE_REF, load, [ONE_ROLE], 'CREATE')
    await waitFor(() => expect(first.latest.state?.definition?.displayName.ja).toBe('作成用'))
    expect(load).toHaveBeenCalledWith(SAMPLE_REF, undefined, 'CREATE')
    expect(readTableEntry(SAMPLE_REF, SALES.roleId).definition?.displayName.ja).toBe('商品')
    first.view.unmount()

    const second = render(SAMPLE_REF, load, [ONE_ROLE], 'CREATE')
    await waitFor(() => expect(second.latest.state?.status).toBe('ready'))
    expect(load).toHaveBeenCalledTimes(2)
  })

  it('reloads and retries with the same purpose and the last read definition id', async () => {
    const changedId = { dslHash: 'changed', workRoleId: 1 }
    const load = vi
      .fn<
        (ref: TableRef, id?: DefinitionId, purpose?: DefinitionPurpose) => Promise<TableDefinition>
      >()
      .mockResolvedValueOnce(sampleDefinition())
      .mockRejectedValueOnce({ kind: 'network' })
      .mockResolvedValueOnce(sampleDefinition({ definitionId: changedId }))
    const { latest } = render(SAMPLE_REF, load, [ONE_ROLE], 'UPDATE')
    await waitFor(() => expect(latest.state?.status).toBe('ready'))
    act(() => latest.state?.reload())
    await waitFor(() => expect(latest.state?.failure?.kind).toBe('network'))
    expect(load).toHaveBeenLastCalledWith(SAMPLE_REF, sampleDefinition().definitionId, 'UPDATE')
    act(() => latest.state?.retry())
    await waitFor(() => expect(latest.state?.definition?.definitionId).toEqual(changedId))
    expect(load).toHaveBeenLastCalledWith(SAMPLE_REF, undefined, 'UPDATE')
    expect(readTableEntry(SAMPLE_REF, SALES.roleId).definition).toBeNull()
  })
})
