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
// 招待の入力のテスト（W4〜W7、D8・D9・D12、AC1.1.1・AC1.1.3・AC1.1.4・AC1.1.9、CR6.1〜CR6.3・CR6.6、NFR6.4・NFR7.1・
// NFR7.2・NFR8.2）。状態は画面が持つため、ここでは値を渡して描き、操作が上へ渡ることを見る。
import { fireEvent, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { InviteDialog, type InviteDialogProps } from './InviteDialog'
import { renderInvitation } from './testing/renderInvitation'
import type { InviteState } from './useInvitationAdmin'

function stateOf(overrides: Partial<InviteState> = {}): InviteState {
  return {
    email: '',
    language: 'ja',
    busy: false,
    fieldError: null,
    errorSeq: 0,
    pending: null,
    dialogAlert: null,
    reloadOnClose: false,
    ...overrides,
  }
}

function renderDialog(state: Partial<InviteState> = {}, languages: readonly string[] = ['ja-JP']) {
  const props: InviteDialogProps = {
    state: stateOf(state),
    onEmailChange: vi.fn(),
    onLanguageChange: vi.fn(),
    onSubmit: vi.fn(),
    onClose: vi.fn(),
    onShowPendingRow: vi.fn(),
  }
  return { props, ...renderInvitation(<InviteDialog {...props} />, languages) }
}

describe('InviteDialog', () => {
  it('opens with an empty address, the given language and focus on the address', () => {
    renderDialog()
    const dialog = screen.getByRole('dialog', { name: '利用者を招待する' })
    expect(dialog).toBeInTheDocument()
    const email = screen.getByLabelText('メールアドレス（必須）')
    expect(email).toHaveValue('')
    expect(email).toHaveAttribute('type', 'email')
    expect(email).toHaveAttribute('autocomplete', 'off')
    expect(email).toHaveFocus()
    expect(screen.getByRole('group', { name: '招待メールの言語' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: '日本語' })).toBeChecked()
    expect(screen.getByText('English')).toHaveAttribute('lang', 'en')
    expect(screen.getByText('日本語')).toHaveAttribute('lang', 'ja')
    expect(screen.getByText('初期値は自分の言語')).toBeInTheDocument()
  })

  it('checks the language chosen when it opened (English for an English administrator)', () => {
    renderDialog({ language: 'en' }, ['en-US'])
    expect(screen.getByRole('dialog', { name: 'Invite a user' })).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'English' })).toBeChecked()
    expect(screen.getByRole('button', { name: 'Close' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'Language of the invitation email' })).toBeVisible()
  })

  it('passes typing, the language and the submit up without validating the format itself', async () => {
    const user = userEvent.setup()
    const { props } = renderDialog()
    await user.type(screen.getByLabelText('メールアドレス（必須）'), 'x')
    expect(props.onEmailChange).toHaveBeenCalledWith('x')
    await user.click(screen.getByRole('radio', { name: 'English' }))
    expect(props.onLanguageChange).toHaveBeenCalledWith('en')
    await user.click(screen.getByRole('button', { name: '招待する' }))
    expect(props.onSubmit).toHaveBeenCalledTimes(1)
  })

  it('ties an error to the address, keeps the value and moves focus to the address', () => {
    renderDialog({
      email: 'not-an-email',
      fieldError: 'invitation.error.VALIDATION_FAILED',
      errorSeq: 1,
    })
    const email = screen.getByLabelText('メールアドレス（必須）')
    expect(email).toHaveValue('not-an-email')
    expect(email).toHaveAttribute('aria-invalid', 'true')
    const describedBy = email.getAttribute('aria-describedby') ?? ''
    expect(document.getElementById(describedBy)).toHaveTextContent(
      'メールアドレスの形式が正しくありません。',
    )
    expect(email).toHaveFocus()
  })

  it('keeps the dialog open and the focus while sending', async () => {
    const user = userEvent.setup()
    const { props } = renderDialog({ email: 'hanako@example.test', busy: true })
    const submit = screen.getByRole('button', { name: '送信しています' })
    expect(submit).toHaveAttribute('aria-disabled', 'true')
    expect(submit).toHaveAttribute('aria-busy', 'true')
    submit.focus()
    await user.click(submit)
    expect(props.onSubmit).not.toHaveBeenCalled()
    expect(submit).toHaveFocus()
    expect(screen.getByRole('button', { name: 'やめる' })).toBeDisabled()
    await user.keyboard('{Escape}')
    await user.click(screen.getByRole('button', { name: '閉じる' }))
    expect(props.onClose).not.toHaveBeenCalled()
  })

  it('closes with Escape, the close button and cancel, but not with the backdrop', async () => {
    const user = userEvent.setup()
    const { props } = renderDialog()
    fireEvent.mouseDown(screen.getByTestId('modal-overlay'))
    expect(props.onClose).not.toHaveBeenCalled()
    await user.keyboard('{Escape}')
    await user.click(screen.getByRole('button', { name: '閉じる' }))
    await user.click(screen.getByRole('button', { name: 'やめる' }))
    expect(props.onClose).toHaveBeenCalledTimes(3)
  })

  it('shows the pending notice with a way to the row, and the alerts inside the dialog', async () => {
    const user = userEvent.setup()
    const pending = renderDialog({
      email: 'hanako@example.test',
      fieldError: 'invitation.error.INVITATION_ALREADY_PENDING',
      errorSeq: 1,
      pending: { invitationId: 42, page: 3 },
    })
    expect(screen.getByText('すでに招待中です。一覧から送り直してください。')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '一覧でこの招待を見る' }))
    expect(pending.props.onShowPendingRow).toHaveBeenCalledTimes(1)
    pending.unmount()

    const unavailable = renderDialog({
      dialogAlert: { kind: 'unavailable', reasons: ['SMTP_NOT_CONFIGURED'] },
    })
    expect(screen.getByTestId('invitation-invite-dialog-alert')).toHaveTextContent(
      'メールの送り先が設定されていません',
    )
    expect(screen.queryByRole('button', { name: '一覧でこの招待を見る' })).toBeNull()
    unavailable.unmount()

    renderDialog({ dialogAlert: { kind: 'message', key: 'invitation.errorGeneral.network' } })
    expect(screen.getByTestId('invitation-invite-dialog-alert')).toHaveTextContent(
      'サーバーにつながりませんでした',
    )
  })

  it('has no accessibility violations', async () => {
    renderDialog({ fieldError: 'invitation.invite.required', errorSeq: 1 })
    expect(await axe(document.body)).toHaveNoViolations()
  })
})
