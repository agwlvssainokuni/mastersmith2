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
// 適用の確かめのテスト（Intent 261004-role-menu の U6 の D25・D8、AC3.1.3・AC3.1.10）。alertdialog・文言の数・はじめの
// フォーカスは「やめる」・やめると送らない・送信中は閉じず2回目を送らない・待ちの案内を確かめる。
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { ApplyConfirmDialog, type ApplyConfirmDialogProps } from './ApplyConfirmDialog'
import { renderRoleTransfer } from './testing/fixtures'

async function renderDialog(props: Partial<ApplyConfirmDialogProps> = {}) {
  const onConfirm = vi.fn()
  const onCancel = vi.fn()
  const view = renderRoleTransfer(
    <ApplyConfirmDialog
      roleCount={2}
      cleared={4}
      submitting={false}
      slow={false}
      onConfirm={onConfirm}
      onCancel={onCancel}
      {...props}
    />,
  )
  await screen.findByTestId('roletransfer-apply-dialog')
  return { ...view, onConfirm, onCancel }
}

describe('ApplyConfirmDialog', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('states the counts and puts the first focus on cancel', async () => {
    await renderDialog()
    expect(screen.getByRole('alertdialog', { name: '適用する' })).toHaveTextContent(
      '2 つのロールを置き換え・作成します。置き換えるロールでは、ファイルに無い対象の設定（4 件）は設定なしに戻ります。ファイルに無いロールは残ります。',
    )
    await waitFor(() => expect(screen.getByTestId('roletransfer-apply-cancel')).toHaveFocus())
  })

  it('sends nothing when cancelled, and confirms from the apply button', async () => {
    const user = userEvent.setup()
    const { onConfirm, onCancel } = await renderDialog()
    await user.click(screen.getByTestId('roletransfer-apply-cancel'))
    expect(onCancel).toHaveBeenCalledTimes(1)
    expect(onConfirm).not.toHaveBeenCalled()
    await user.click(screen.getByTestId('roletransfer-apply-submit'))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('stays open while submitting, ignores another press and shows the slow notice', async () => {
    const user = userEvent.setup()
    const { onConfirm, onCancel } = await renderDialog({ submitting: true, slow: true })
    await user.keyboard('{Escape}')
    await user.click(screen.getByTestId('roletransfer-apply-submit'))
    expect(onCancel).not.toHaveBeenCalled()
    expect(onConfirm).not.toHaveBeenCalled()
    expect(screen.getByTestId('roletransfer-apply-slow')).toHaveTextContent(
      '適用に最大 30 秒かかります',
    )
  })

  it('has no accessibility violations', async () => {
    await renderDialog()
    expect(await axe(document.body)).toHaveNoViolations()
  })
})
