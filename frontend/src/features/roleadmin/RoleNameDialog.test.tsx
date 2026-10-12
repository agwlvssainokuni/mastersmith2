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
// ロールの作成・名前の変更の Modal のテスト（Intent 261004-role-menu の U6 の D7・D4、functional-spec.md の 4.2・8.1、
// NFR1.4）。画面の検査の境界（64 と 65 コードポイント、サロゲートペア、空白だけ、全角の空白、制御文字、書式文字）と、
// サーバーの拒否の文言の置き場、送信中の扱いを確かめる。
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { RoleNameDialog, type RoleNameDialogProps } from './RoleNameDialog'
import { LONG_ROLE_NAME, ZERO_WIDTH_NAME } from './testing/fixtures'
import { renderRoleAdmin } from './testing/renderRoleAdmin'

async function renderDialog(props: Partial<RoleNameDialogProps> = {}) {
  const onSubmit = vi.fn()
  const onCancel = vi.fn()
  renderRoleAdmin(
    <RoleNameDialog
      mode="create"
      submitting={false}
      slow={false}
      onSubmit={onSubmit}
      onCancel={onCancel}
      {...props}
    />,
  )
  await screen.findByTestId('roleadmin-name-input')
  return { onSubmit, onCancel }
}

function nameInput(): HTMLInputElement {
  return screen.getByTestId('roleadmin-name-input')
}

describe('RoleNameDialog', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('sends a name of exactly 64 code points, counting a surrogate pair as one', async () => {
    const user = userEvent.setup()
    const { onSubmit } = await renderDialog()
    const name = `${'あ'.repeat(62)}𠮷𠮷`
    await user.type(nameInput(), name)
    expect(screen.getByText('残り 0 文字')).toBeInTheDocument()
    await user.click(screen.getByTestId('roleadmin-name-submit'))
    expect(onSubmit).toHaveBeenCalledWith(name)
  })

  it('rejects 65 code points with the message under the field and does not send', async () => {
    const user = userEvent.setup()
    const { onSubmit } = await renderDialog()
    await user.type(nameInput(), `${LONG_ROLE_NAME}い`)
    await user.click(screen.getByTestId('roleadmin-name-submit'))
    const message = screen.getByText('名前は 64 文字以内で入れてください。')
    expect(nameInput()).toHaveAttribute('aria-invalid', 'true')
    expect(nameInput().getAttribute('aria-describedby')).toContain(message.id)
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('rejects an empty name, spaces only and full-width spaces only', async () => {
    const user = userEvent.setup()
    const { onSubmit } = await renderDialog()
    await user.click(screen.getByTestId('roleadmin-name-submit'))
    expect(screen.getByText('名前を入れてください。')).toBeInTheDocument()
    await user.type(nameInput(), '  　　 ')
    await user.click(screen.getByTestId('roleadmin-name-submit'))
    expect(screen.getByText('名前を入れてください。')).toBeInTheDocument()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('counts the length after removing surrounding white space', async () => {
    const user = userEvent.setup()
    const { onSubmit } = await renderDialog()
    const name = `　 ${LONG_ROLE_NAME} 　`
    await user.type(nameInput(), name)
    expect(screen.getByText('残り 0 文字')).toBeInTheDocument()
    await user.click(screen.getByTestId('roleadmin-name-submit'))
    expect(onSubmit).toHaveBeenCalledWith(name)
  })

  it('rejects a control character and accepts a zero width space (Cf)', async () => {
    const user = userEvent.setup()
    const { onSubmit } = await renderDialog({ mode: 'rename', initialName: '営業\u0007部' })
    await user.click(screen.getByTestId('roleadmin-name-submit'))
    expect(screen.getByText('名前に改行などの制御文字は使えません。')).toBeInTheDocument()
    await user.clear(nameInput())
    await user.type(nameInput(), ZERO_WIDTH_NAME)
    await user.click(screen.getByTestId('roleadmin-name-submit'))
    expect(onSubmit).toHaveBeenCalledWith(ZERO_WIDTH_NAME)
  })

  it('shows a server rejection under the field and a busy message inside the dialog, keeping the input', async () => {
    await renderDialog({
      mode: 'rename',
      initialName: '営業',
      serverProblem: 'roleadmin.name.duplicate',
      dialogMessage: 'roleadmin.busy',
    })
    const message = screen.getByText('同じ名前のロールがあります。')
    expect(nameInput().getAttribute('aria-describedby')).toContain(message.id)
    expect(screen.getByTestId('roleadmin-name-alert')).toHaveTextContent(
      'ほかの操作と重なりました。少し待ってからもう一度お試しください。',
    )
    expect(nameInput()).toHaveValue('営業')
    expect(screen.getByRole('dialog')).toHaveAccessibleName('名前を変える')
  })

  it('keeps the dialog open while submitting and shows the slow notice', async () => {
    const user = userEvent.setup()
    const { onCancel, onSubmit } = await renderDialog({
      submitting: true,
      slow: true,
      initialName: '営業',
    })
    expect(screen.getByTestId('roleadmin-name-cancel')).toBeDisabled()
    expect(screen.getByTestId('roleadmin-name-slow')).toHaveTextContent(
      '時間がかかっています。そのままお待ちください。',
    )
    await user.keyboard('{Escape}')
    await user.click(screen.getByTestId('roleadmin-name-submit'))
    expect(onCancel).not.toHaveBeenCalled()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('calls onCancel from the cancel button and puts the first focus on the input', async () => {
    const user = userEvent.setup()
    const { onCancel } = await renderDialog()
    await waitFor(() => expect(nameInput()).toHaveFocus())
    await user.click(screen.getByTestId('roleadmin-name-cancel'))
    expect(onCancel).toHaveBeenCalledTimes(1)
  })

  it('has no accessibility violations with an error and a 64 character name', async () => {
    await renderDialog({
      mode: 'rename',
      initialName: LONG_ROLE_NAME,
      serverProblem: 'roleadmin.name.noChange',
    })
    expect(screen.getByText('今と同じ名前です。')).toBeInTheDocument()
    expect(await axe(document.body)).toHaveNoViolations()
  })
})
