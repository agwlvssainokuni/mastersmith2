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
// ロールの削除の確かめのテスト（Intent 261004-role-menu の U6 の D8・D4、AC1.1.7）。はじめのフォーカスは「やめる」、
// やめると送らない、送信中は閉じない、BUSY の文言を Modal の中に出す、名前は文字のまま出す。
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { DeleteConfirmDialog, type DeleteConfirmDialogProps } from './DeleteConfirmDialog'
import { LONG_ROLE_NAME, SCRIPT_NAME } from './testing/fixtures'
import { renderRoleAdmin } from './testing/renderRoleAdmin'

async function renderDialog(props: Partial<DeleteConfirmDialogProps> = {}) {
  const onConfirm = vi.fn()
  const onCancel = vi.fn()
  renderRoleAdmin(
    <DeleteConfirmDialog
      name="営業"
      submitting={false}
      slow={false}
      onConfirm={onConfirm}
      onCancel={onCancel}
      {...props}
    />,
  )
  await screen.findByTestId('roleadmin-delete-dialog')
  return { onConfirm, onCancel }
}

describe('DeleteConfirmDialog', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('is an alertdialog with the confirmation text and the first focus on cancel', async () => {
    await renderDialog()
    expect(screen.getByRole('alertdialog')).toHaveAccessibleName('ロールを削除する')
    expect(screen.getByTestId('roleadmin-delete-text')).toHaveTextContent(
      'ロール「営業」を削除します。権限の設定も消え、元に戻せません。',
    )
    await waitFor(() => expect(screen.getByTestId('roleadmin-delete-cancel')).toHaveFocus())
  })

  it('does not confirm when cancelled', async () => {
    const user = userEvent.setup()
    const { onConfirm, onCancel } = await renderDialog()
    await user.click(screen.getByTestId('roleadmin-delete-cancel'))
    expect(onCancel).toHaveBeenCalledTimes(1)
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('confirms from the delete button', async () => {
    const user = userEvent.setup()
    const { onConfirm } = await renderDialog()
    await user.click(screen.getByTestId('roleadmin-delete-submit'))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('stays open while submitting, ignores a second press and shows the busy message', async () => {
    const user = userEvent.setup()
    const { onConfirm, onCancel } = await renderDialog({
      submitting: true,
      slow: true,
      dialogMessage: 'roleadmin.busy',
    })
    expect(screen.getByTestId('roleadmin-delete-cancel')).toBeDisabled()
    await user.keyboard('{Escape}')
    await user.click(screen.getByTestId('roleadmin-delete-submit'))
    expect(onCancel).not.toHaveBeenCalled()
    expect(onConfirm).not.toHaveBeenCalled()
    expect(screen.getByTestId('roleadmin-delete-alert')).toHaveTextContent(
      'ほかの操作と重なりました。少し待ってからもう一度お試しください。',
    )
    expect(screen.getByTestId('roleadmin-delete-slow')).toBeInTheDocument()
  })

  it('shows a name that looks like markup as plain text', async () => {
    await renderDialog({ name: SCRIPT_NAME })
    expect(screen.getByTestId('roleadmin-delete-text')).toHaveTextContent(SCRIPT_NAME)
    expect(document.querySelector('script')).toBeNull()
  })

  it('has no accessibility violations with a 64 character name and a message', async () => {
    await renderDialog({ name: LONG_ROLE_NAME, dialogMessage: 'roleadmin.error.general' })
    expect(await axe(document.body)).toHaveNoViolations()
  })
})
