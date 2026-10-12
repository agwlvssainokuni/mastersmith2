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
// S7 の状態と操作のテスト（Intent 261004-role-menu の U6、functional-spec.md の 3.5・4.5、D22〜D26、NFR 設計 Q1 A、
// NFR1.5・NFR2.4・NFR2.11）。上限ちょうどは送る・1 バイト超えは送らない、確かめた本文と指紋がそのまま適用で送られる、
// 選び直しで本文と指紋が捨てられる、画面を離れた後の答えを捨てる、STALE・INVALID・413・403 の扱い、書き出しの保存と
// 上限超えの案内を確かめる。
import { act, renderHook } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { APPLY_PATH, CHECK_PATH, EXPORT_PATH } from './api/roleTransferApi'
import { MAX_TRANSFER_BYTES } from './transferLimits'
import {
  apiError,
  checkOf,
  fakeRoleTransferApi,
  fileOf,
  planOf,
  type FakeRoleTransferApi,
} from './testing/fixtures'
import { hasNoChange, useRoleTransfer } from './useRoleTransfer'

function setup(api: FakeRoleTransferApi = fakeRoleTransferApi()) {
  const reportForbidden = vi.fn(() => false)
  const hook = renderHook(() => useRoleTransfer({ api, reportForbidden }))
  return { ...hook, api, reportForbidden }
}

async function settle(): Promise<void> {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 0))
  })
}

describe('useRoleTransfer', () => {
  afterEach(() => {
    vi.restoreAllMocks()
  })

  it('accepts a file of exactly 10,485,760 bytes and refuses 10,485,761 bytes without sending', async () => {
    const { result, api } = setup()
    act(() => result.current.selectFile(fileOf('a', 'roles.yaml', MAX_TRANSFER_BYTES)))
    expect(result.current.phase).toBe('fileSelected')
    act(() => result.current.selectFile(fileOf('a', 'roles.yaml', MAX_TRANSFER_BYTES + 1)))
    expect(result.current.phase).toBe('fileTooLarge')
    expect(result.current.fileInfo).toEqual({ name: 'roles.yaml', size: MAX_TRANSFER_BYTES + 1 })
    act(() => result.current.runCheck())
    await settle()
    expect(api.checkTransfer).not.toHaveBeenCalled()
  })

  it('sends the checked text with the fingerprint when applying, then forgets them', async () => {
    const api = fakeRoleTransferApi()
    api.checkTransfer.mockResolvedValue(checkOf({ fingerprint: 'fp-xyz' }))
    api.applyTransfer.mockResolvedValue({ created: 1, replaced: 1 })
    const { result } = setup(api)
    act(() => result.current.selectFile(fileOf('roles:\n  - name: 営業\n')))
    act(() => result.current.runCheck())
    await settle()
    expect(api.checkTransfer).toHaveBeenCalledWith('roles:\n  - name: 営業\n')
    expect(result.current.phase).toBe('checked')
    act(() => result.current.openApply())
    expect(result.current.confirmOpen).toBe(true)
    act(() => result.current.apply())
    await settle()
    expect(api.applyTransfer).toHaveBeenCalledWith('roles:\n  - name: 営業\n', 'fp-xyz')
    expect(result.current.phase).toBe('idle')
    expect(result.current.check).toBeNull()
    expect(result.current.fileInfo).toBeNull()
    expect(result.current.notice).toEqual({
      variant: 'success',
      key: 'roletransfer.apply.done',
      values: { created: 1, replaced: 1 },
    })
    act(() => result.current.apply())
    expect(api.applyTransfer).toHaveBeenCalledTimes(1)
  })

  it('forgets the text and fingerprint when another file is chosen, including a check in flight', async () => {
    const api = fakeRoleTransferApi()
    let answer!: (value: ReturnType<typeof checkOf>) => void
    api.checkTransfer.mockReturnValueOnce(new Promise((resolve) => (answer = resolve)))
    const { result } = setup(api)
    act(() => result.current.selectFile(fileOf('first')))
    act(() => result.current.runCheck())
    await settle()
    act(() => result.current.selectFile(fileOf('second')))
    await act(async () => {
      answer(checkOf())
      await Promise.resolve()
    })
    await settle()
    expect(result.current.check).toBeNull()
    expect(result.current.phase).toBe('fileSelected')
    act(() => result.current.openApply())
    expect(result.current.confirmOpen).toBe(false)
  })

  it('detects no change and keeps apply closed', async () => {
    expect(hasNoChange(checkOf({ roles: [] }))).toBe(true)
    expect(hasNoChange(checkOf({ roles: [planOf({ added: 0, changed: 0, cleared: 0 })] }))).toBe(
      true,
    )
    expect(
      hasNoChange(checkOf({ roles: [planOf({ action: 'CREATE', added: 0, cleared: 0 })] })),
    ).toBe(false)
    const api = fakeRoleTransferApi()
    api.checkTransfer.mockResolvedValue(checkOf({ roles: [] }))
    const { result } = setup(api)
    act(() => result.current.selectFile(fileOf()))
    act(() => result.current.runCheck())
    await settle()
    expect(result.current.phase).toBe('noChange')
    act(() => result.current.openApply())
    expect(result.current.confirmOpen).toBe(false)
  })

  it('keeps the file selection on STALE and clears the result', async () => {
    const api = fakeRoleTransferApi()
    api.checkTransfer.mockResolvedValue(checkOf())
    api.applyTransfer.mockRejectedValue(apiError(409, 'ROLE_TRANSFER_STALE'))
    const { result } = setup(api)
    act(() => result.current.selectFile(fileOf()))
    act(() => result.current.runCheck())
    await settle()
    act(() => result.current.openApply())
    act(() => result.current.apply())
    await settle()
    expect(result.current.phase).toBe('stale')
    expect(result.current.check).toBeNull()
    expect(result.current.fileInfo).not.toBeNull()
    expect(result.current.notice?.key).toBe('roletransfer.stale')
    expect(result.current.confirmOpen).toBe(false)
  })

  it('keeps the result on ROLE_BUSY while applying, so it can be applied again', async () => {
    const api = fakeRoleTransferApi()
    api.checkTransfer.mockResolvedValue(checkOf())
    api.applyTransfer
      .mockRejectedValueOnce(apiError(409, 'ROLE_BUSY'))
      .mockResolvedValueOnce({ created: 1, replaced: 1 })
    const { result } = setup(api)
    act(() => result.current.selectFile(fileOf('text')))
    act(() => result.current.runCheck())
    await settle()
    act(() => result.current.apply())
    await settle()
    expect(result.current.phase).toBe('checked')
    expect(result.current.notice?.key).toBe('roletransfer.busy')
    act(() => result.current.apply())
    await settle()
    expect(api.applyTransfer).toHaveBeenLastCalledWith('text', 'fp-1')
  })

  it('shows the error list on ROLE_TRANSFER_INVALID and the size message on 413 without a code', async () => {
    const api = fakeRoleTransferApi()
    api.checkTransfer
      .mockRejectedValueOnce(
        apiError(400, 'ROLE_TRANSFER_INVALID', {
          errors: [{ reason: 'UNKNOWN_KEY', line: 3, column: 5, path: 'roles.0.x' }],
        }),
      )
      .mockRejectedValueOnce(apiError(413))
    const { result } = setup(api)
    act(() => result.current.selectFile(fileOf()))
    act(() => result.current.runCheck())
    await settle()
    expect(result.current.phase).toBe('invalid')
    expect(result.current.errors).toEqual([
      { reason: 'UNKNOWN_KEY', line: 3, column: 5, path: 'roles.0.x' },
    ])
    act(() => result.current.runCheck())
    await settle()
    expect(result.current.phase).toBe('rejected')
    expect(result.current.notice).toEqual({
      variant: 'warning',
      key: 'roletransfer.file.tooLarge',
      values: { limit: 10 },
    })
  })

  it('passes a 403 with the request path and shows no message of its own', async () => {
    const api = fakeRoleTransferApi()
    api.checkTransfer.mockRejectedValue(apiError(403, 'ACCESS_DENIED'))
    api.applyTransfer.mockRejectedValue(apiError(403, 'ACCESS_DENIED'))
    api.exportRoles.mockRejectedValue(apiError(403, 'ACCESS_DENIED'))
    const reportForbidden = vi.fn((_error: unknown, _path: string) => true)
    const { result } = renderHook(() => useRoleTransfer({ api, reportForbidden }))
    act(() => result.current.selectFile(fileOf()))
    act(() => result.current.runCheck())
    await settle()
    await act(async () => {
      await result.current.exportFile()
    })
    expect(reportForbidden.mock.calls.map(([, path]) => path)).toEqual([CHECK_PATH, EXPORT_PATH])
    expect(result.current.notice).toBeNull()
    expect(APPLY_PATH).toBe('/api/admin/role-transfer/apply')
  })

  it('saves the exported file under its name, revokes the URL at once and reports the import limit', async () => {
    const created = vi.fn(() => 'blob:mock')
    const revoked = vi.fn()
    vi.stubGlobal(
      'URL',
      Object.assign(Object.create(URL), { createObjectURL: created, revokeObjectURL: revoked }),
    )
    const click = vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    const api = fakeRoleTransferApi()
    api.exportRoles
      .mockResolvedValueOnce({
        blob: new Blob(['x']),
        fileName: 'roles-2026.yaml',
        exceedsImportLimit: true,
      })
      .mockResolvedValueOnce({
        blob: new Blob(['x']),
        fileName: 'roles.yaml',
        exceedsImportLimit: false,
      })
    const { result } = setup(api)
    await act(async () => {
      await result.current.exportFile()
    })
    expect(click).toHaveBeenCalledTimes(1)
    expect((click.mock.contexts[0] as HTMLAnchorElement).download).toBe('roles-2026.yaml')
    expect(revoked).toHaveBeenCalledWith('blob:mock')
    expect(result.current.exceeds).toBe(true)
    await act(async () => {
      await result.current.exportFile()
    })
    expect(result.current.exceeds).toBe(false)
    expect(document.querySelector('a[download]')).toBeNull()
    vi.unstubAllGlobals()
  })

  it('ignores answers after leaving the screen', async () => {
    const api = fakeRoleTransferApi()
    let answer!: (value: ReturnType<typeof checkOf>) => void
    api.checkTransfer.mockReturnValueOnce(new Promise((resolve) => (answer = resolve)))
    const errors = vi.spyOn(console, 'error')
    const { result, unmount } = setup(api)
    act(() => result.current.selectFile(fileOf()))
    act(() => result.current.runCheck())
    await settle()
    unmount()
    await act(async () => {
      answer(checkOf())
      await Promise.resolve()
    })
    expect(errors).not.toHaveBeenCalled()
  })
})
