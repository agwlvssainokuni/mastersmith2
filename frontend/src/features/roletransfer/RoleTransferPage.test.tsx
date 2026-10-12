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
// S7 権限の受け渡しの画面のテスト（useRoleTransfer を通す。Intent 261004-role-menu の U6、functional-spec.md の 3.5・4.5、
// D22〜D26、AC3.1.1〜AC3.1.14）。API は差し替える。
import { screen, waitFor, within } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import type { TransferCheck } from './api/types'
import { RoleTransferPage } from './RoleTransferPage'
import {
  apiError,
  checkOf,
  DETAIL_MARKER,
  fakeRoleTransferApi,
  fileOf,
  renderRoleTransfer,
  type FakeRoleTransferApi,
} from './testing/fixtures'

async function renderPage(api: FakeRoleTransferApi) {
  const view = renderRoleTransfer(<RoleTransferPage api={api} />)
  await screen.findByTestId('roletransfer-page')
  return view
}

async function chooseAndCheck(user: UserEvent, text = 'roles: []\n') {
  await user.upload(screen.getByLabelText('ファイルを選ぶ'), fileOf(text))
  await user.click(screen.getByTestId('roletransfer-check'))
}

describe('RoleTransferPage', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
    vi.unstubAllGlobals()
  })

  it('disables export while exporting and shows the import limit notice', async () => {
    const user = userEvent.setup()
    vi.spyOn(HTMLAnchorElement.prototype, 'click').mockImplementation(() => {})
    vi.stubGlobal(
      'URL',
      Object.assign(Object.create(URL), {
        createObjectURL: () => 'blob:x',
        revokeObjectURL: () => {},
      }),
    )
    const api = fakeRoleTransferApi()
    let answer!: (value: { blob: Blob; fileName: string; exceedsImportLimit: boolean }) => void
    api.exportRoles.mockReturnValue(new Promise((resolve) => (answer = resolve)))
    await renderPage(api)
    await user.click(screen.getByTestId('roletransfer-export'))
    await user.click(screen.getByTestId('roletransfer-export'))
    expect(api.exportRoles).toHaveBeenCalledTimes(1)
    answer({ blob: new Blob(['x']), fileName: 'roles.yaml', exceedsImportLimit: true })
    expect(await screen.findByTestId('roletransfer-export-exceeds')).toHaveTextContent(
      '書き出したファイルは読み込みの上限（10 MiB）を超えています。',
    )
  })

  it('cannot check before a file is chosen, and checks the chosen file', async () => {
    const user = userEvent.setup()
    const api = fakeRoleTransferApi()
    api.checkTransfer.mockResolvedValue(checkOf())
    await renderPage(api)
    expect(screen.getByTestId('roletransfer-check')).toBeDisabled()
    await chooseAndCheck(user, 'version: 2\n')
    expect(api.checkTransfer).toHaveBeenCalledWith('version: 2\n')
    expect(await screen.findByTestId('roletransfer-check-table')).toBeInTheDocument()
    expect(screen.getByTestId('roletransfer-apply')).toBeEnabled()
  })

  it('shows "no change" and keeps apply disabled', async () => {
    const user = userEvent.setup()
    const api = fakeRoleTransferApi()
    api.checkTransfer.mockResolvedValue(checkOf({ roles: [] }))
    await renderPage(api)
    await chooseAndCheck(user)
    expect(await screen.findByTestId('roletransfer-no-change')).toHaveTextContent(
      '変わる点はありません。',
    )
    expect(screen.getByTestId('roletransfer-apply')).toBeDisabled()
  })

  it('sends nothing when the apply is cancelled, and applies after confirmation', async () => {
    const user = userEvent.setup()
    const api = fakeRoleTransferApi()
    api.checkTransfer.mockResolvedValue(checkOf())
    api.applyTransfer.mockResolvedValue({ created: 1, replaced: 1 })
    await renderPage(api)
    await chooseAndCheck(user, 'body')
    await user.click(await screen.findByTestId('roletransfer-apply'))
    await user.click(await screen.findByTestId('roletransfer-apply-cancel'))
    expect(api.applyTransfer).not.toHaveBeenCalled()
    await waitFor(() => expect(screen.getByTestId('roletransfer-apply')).toHaveFocus())
    await user.click(screen.getByTestId('roletransfer-apply'))
    await user.click(await screen.findByTestId('roletransfer-apply-submit'))
    expect(api.applyTransfer).toHaveBeenCalledWith('body', 'fp-1')
    expect(await screen.findByTestId('roletransfer-notice')).toHaveTextContent('作成 1・置き換え 1')
    expect(screen.queryByTestId('roletransfer-check-table')).toBeNull()
    expect(screen.getByTestId('roletransfer-file-status')).toHaveTextContent(
      'ファイルが選ばれていません。',
    )
  })

  it('keeps the file selection on STALE so it can be checked again', async () => {
    const user = userEvent.setup()
    const api = fakeRoleTransferApi()
    api.checkTransfer.mockResolvedValue(checkOf())
    api.applyTransfer.mockRejectedValue(apiError(409, 'ROLE_TRANSFER_STALE'))
    await renderPage(api)
    await chooseAndCheck(user)
    await user.click(await screen.findByTestId('roletransfer-apply'))
    await user.click(await screen.findByTestId('roletransfer-apply-submit'))
    expect(await screen.findByTestId('roletransfer-notice')).toHaveTextContent(
      '確かめた後にロールの設定が変わったため、適用できませんでした。',
    )
    expect(screen.queryByTestId('roletransfer-check-table')).toBeNull()
    expect(screen.getByTestId('roletransfer-file-status')).toHaveTextContent('roles.yaml')
    expect(screen.getByTestId('roletransfer-check')).toBeEnabled()
  })

  it('shows the error list with focus on ROLE_TRANSFER_INVALID', async () => {
    const user = userEvent.setup()
    const api = fakeRoleTransferApi()
    api.checkTransfer.mockRejectedValue(
      apiError(400, 'ROLE_TRANSFER_INVALID', {
        errors: [{ reason: 'SYNTAX', line: 1, column: 2, path: null }],
      }),
    )
    await renderPage(api)
    await chooseAndCheck(user)
    const alert = await screen.findByTestId('roletransfer-errors-alert')
    await waitFor(() => expect(alert).toHaveFocus())
    expect(document.body).not.toHaveTextContent(DETAIL_MARKER)
  })

  it.each([
    [apiError(409, 'DSL_NOT_APPLIED'), 'DSL が適用されていないため読み込めません。'],
    [apiError(415), '操作できませんでした。もう一度お試しください。'],
    [apiError(409, 'ROLE_BUSY'), 'ほかの読み込みか操作と重なりました。'],
  ])('shows the message for a rejected check (%#)', async (error, text) => {
    const user = userEvent.setup()
    const api = fakeRoleTransferApi()
    api.checkTransfer.mockRejectedValue(error)
    await renderPage(api)
    await chooseAndCheck(user)
    expect(await screen.findByTestId('roletransfer-notice')).toHaveTextContent(text)
    expect(document.body).not.toHaveTextContent(DETAIL_MARKER)
    expect(screen.getByTestId('roletransfer-check')).toBeEnabled()
  })

  it('refuses a file over the limit without sending it', async () => {
    const user = userEvent.setup()
    const api = fakeRoleTransferApi()
    await renderPage(api)
    await user.upload(screen.getByLabelText('ファイルを選ぶ'), fileOf('x', 'big.yaml', 10_485_761))
    expect(screen.getByTestId('roletransfer-file-too-large')).toBeInTheDocument()
    expect(screen.getByTestId('roletransfer-check')).toBeDisabled()
    expect(api.checkTransfer).not.toHaveBeenCalled()
  })

  it('shows the slow notice with the 15 second check bound after 5 seconds', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    const user = userEvent.setup({ advanceTimers: vi.advanceTimersByTime })
    const api = fakeRoleTransferApi()
    api.checkTransfer.mockReturnValue(new Promise(() => {}))
    await renderPage(api)
    await chooseAndCheck(user)
    vi.advanceTimersByTime(4_900)
    expect(screen.queryByTestId('roletransfer-check-slow')).toBeNull()
    vi.advanceTimersByTime(200)
    expect(await screen.findByTestId('roletransfer-check-slow')).toHaveTextContent(
      '確かめに最大 15 秒かかります',
    )
    vi.useRealTimers()
  })

  it('has no accessibility violations with the result table', async () => {
    const user = userEvent.setup()
    const api = fakeRoleTransferApi()
    api.checkTransfer.mockResolvedValue(checkOf() as TransferCheck)
    const view = await renderPage(api)
    await chooseAndCheck(user)
    await screen.findByTestId('roletransfer-check-table')
    expect(within(view.container).getByRole('heading', { level: 1 })).toHaveTextContent(
      '権限の受け渡し',
    )
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
