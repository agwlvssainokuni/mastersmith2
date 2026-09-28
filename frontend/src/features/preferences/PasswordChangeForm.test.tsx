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
// パスワードの変更のフォームのテスト（functional-spec.md の W9・W10・W12、D9〜D11、CR6.2・CR6.3・CR6.9、NFR7.2・NFR9.1）。
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { PasswordChangeForm, type PasswordChangeFormProps } from './PasswordChangeForm'
import { renderPreferencesPart } from './testing/renderPreferences'

beforeEach(() => {
  resetDisplayTestState()
})

function renderForm(overrides: Partial<PasswordChangeFormProps> = {}, languages = ['ja-JP']) {
  const all: PasswordChangeFormProps = {
    headingId: 'heading',
    form: { currentPassword: '', newPassword: '', newPasswordConfirmation: '' },
    fieldErrors: {},
    alert: undefined,
    sending: false,
    focusRequest: undefined,
    setField: vi.fn(),
    submit: vi.fn(),
    dismissAlert: vi.fn(),
    ...overrides,
  }
  const result = renderPreferencesPart(
    <>
      <h1 id="heading">パスワードの変更</h1>
      <PasswordChangeForm {...all} />
    </>,
    languages,
  )
  return { ...result, props: all }
}

describe('PasswordChangeForm', () => {
  it('has three password fields with their autocomplete values (CR6.9)', async () => {
    renderForm()
    const current = await screen.findByLabelText(/今のパスワード/)
    const next = screen.getByLabelText(/^新しいパスワード\*?$/)
    const confirm = screen.getByLabelText(/新しいパスワード（確かめ）/)
    for (const input of [current, next, confirm]) {
      expect(input).toHaveAttribute('type', 'password')
    }
    expect(current).toHaveAttribute('autocomplete', 'current-password')
    expect(next).toHaveAttribute('autocomplete', 'new-password')
    expect(confirm).toHaveAttribute('autocomplete', 'new-password')
  })

  it('shows the hint of 12 characters before the input and passes the typed values', async () => {
    const user = userEvent.setup()
    const { props: all } = renderForm()
    const next = await screen.findByTestId('preferences-password-new-input')
    expect(next).toHaveAccessibleDescription('12 文字以上')
    await user.type(next, 'a')
    expect(all.setField).toHaveBeenLastCalledWith('newPassword', 'a')
  })

  it('links errors to their fields and focuses the asked field', async () => {
    renderForm({
      fieldErrors: { currentPassword: 'preferences.password.currentMismatch' },
      focusRequest: { target: 'currentPassword', seq: 1 },
    })
    const current = await screen.findByTestId('preferences-password-current-input')
    expect(current).toHaveAttribute('aria-invalid', 'true')
    expect(current).toHaveAccessibleDescription('今のパスワードが正しくありません')
    expect(current).toHaveFocus()
  })

  it('shows the sending state and keeps the fields focusable but read only', async () => {
    const user = userEvent.setup()
    const { props: all } = renderForm({ sending: true })
    const submit = await screen.findByTestId('preferences-password-submit-button')
    expect(submit).toHaveTextContent('変更しています')
    expect(submit).toHaveAttribute('aria-disabled', 'true')
    expect(submit).toHaveAttribute('aria-busy', 'true')
    for (const id of ['current', 'new', 'confirm']) {
      expect(screen.getByTestId(`preferences-password-${id}-input`)).toHaveAttribute('readonly')
    }
    await user.click(submit)
    expect(all.submit).not.toHaveBeenCalled()
  })

  it('has no method or action and submits through the handler', async () => {
    const user = userEvent.setup()
    const { props: all } = renderForm({}, ['en-US'])
    const form = await screen.findByTestId('preferences-password-form')
    expect(form).not.toHaveAttribute('method')
    expect(form).not.toHaveAttribute('action')
    await user.click(screen.getByRole('button', { name: 'Change' }))
    expect(all.submit).toHaveBeenCalledTimes(1)
  })

  it('has no accessibility violations with errors and an alert', async () => {
    const { container } = renderForm({
      alert: 'preferences.password.failed',
      fieldErrors: {
        newPassword: 'preferences.password.tooShort',
        newPasswordConfirmation: 'preferences.password.mismatch',
      },
    })
    await screen.findByTestId('preferences-password-form')
    expect(await axe(container)).toHaveNoViolations()
  })
})
