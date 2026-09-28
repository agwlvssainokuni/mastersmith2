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
// 取り消しの確かめのテスト（W9、D9・D12、AC2.2.11、CR6.7、NFR6.4・NFR7.1・NFR7.2・NFR8.2）。
import { fireEvent, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { CancelConfirmDialog, type CancelConfirmDialogProps } from './CancelConfirmDialog'
import { invitationOf } from './testing/fixtures'
import { renderInvitation } from './testing/renderInvitation'

function renderDialog(busy = false, languages: readonly string[] = ['ja-JP']) {
  const props: CancelConfirmDialogProps = {
    target: invitationOf({ email: 'hanako@example.test' }),
    busy,
    onConfirm: vi.fn(),
    onClose: vi.fn(),
  }
  return { props, ...renderInvitation(<CancelConfirmDialog {...props} />, languages) }
}

describe('CancelConfirmDialog', () => {
  it('is an alert dialog describing the address, with focus on cancel', () => {
    renderDialog()
    const dialog = screen.getByRole('alertdialog', { name: '招待を取り消しますか' })
    expect(dialog).toHaveAccessibleDescription(
      expect.stringContaining(
        'hanako@example.test への招待を取り消します。取り消すと、招待メールのリンクは使えなくなります。',
      ),
    )
    expect(screen.getByRole('button', { name: 'やめる' })).toHaveFocus()
  })

  it('closes with Escape and cancel without revoking, and not with the backdrop', async () => {
    const user = userEvent.setup()
    const { props } = renderDialog()
    fireEvent.mouseDown(screen.getByTestId('modal-overlay'))
    expect(props.onClose).not.toHaveBeenCalled()
    await user.keyboard('{Escape}')
    await user.click(screen.getByRole('button', { name: 'やめる' }))
    expect(props.onClose).toHaveBeenCalledTimes(2)
    expect(props.onConfirm).not.toHaveBeenCalled()
  })

  it('revokes with the danger button', async () => {
    const user = userEvent.setup()
    const { props } = renderDialog()
    const revoke = screen.getByRole('button', { name: '取り消す' })
    expect(revoke).toHaveClass('variant-danger')
    await user.click(revoke)
    expect(props.onConfirm).toHaveBeenCalledTimes(1)
  })

  it('keeps the dialog open while revoking, with a loading button and a disabled cancel', async () => {
    const user = userEvent.setup()
    const { props } = renderDialog(true)
    const revoke = screen.getByRole('button', { name: '取り消しています' })
    expect(revoke).toHaveAttribute('aria-disabled', 'true')
    expect(revoke).toHaveAttribute('aria-busy', 'true')
    revoke.focus()
    await user.click(revoke)
    expect(props.onConfirm).not.toHaveBeenCalled()
    expect(revoke).toHaveFocus()
    expect(screen.getByRole('button', { name: 'やめる' })).toBeDisabled()
    await user.keyboard('{Escape}')
    await user.click(screen.getByRole('button', { name: '閉じる' }))
    expect(props.onClose).not.toHaveBeenCalled()
  })

  it('names the close button in the screen language', () => {
    renderDialog(false, ['en-US'])
    expect(screen.getByRole('alertdialog', { name: 'Revoke this invitation?' })).toBeVisible()
    expect(screen.getByRole('button', { name: 'Close' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Revoke' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancel' })).toHaveFocus()
  })

  it('has no accessibility violations', async () => {
    renderDialog()
    expect(await axe(document.body)).toHaveNoViolations()
  })
})
