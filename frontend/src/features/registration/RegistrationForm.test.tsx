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
// 登録の完了のフォームのテスト（W5〜W10、AC3.2.16、CR6.1〜CR6.3・CR6.5・CR6.6・CR6.9、NFR6.2・NFR7.2・NFR7.4・NFR8.1）。
// 状態は画面（useRegistration）が持つため、ここでは値を渡して描き、操作が上へ渡ることを見る。
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import type { RegistrationFocusTarget, RegistrationValues } from './formProblems'
import { RegistrationForm, type RegistrationFormProps } from './RegistrationForm'
import { renderRegistration } from './testing/renderRegistration'

const EMAIL = 'hanako@example.test'

function valuesOf(overrides: Partial<RegistrationValues> = {}): RegistrationValues {
  return {
    displayName: EMAIL,
    password: '',
    passwordConfirmation: '',
    language: 'ja',
    theme: 'system',
    fontSize: 'md',
    ...overrides,
  }
}

function renderForm(
  overrides: Partial<RegistrationFormProps> = {},
  languages: readonly string[] = ['ja-JP'],
) {
  const props: RegistrationFormProps = {
    headingId: 'registration-heading',
    email: EMAIL,
    values: valuesOf(),
    problems: {},
    failure: undefined,
    submitting: false,
    focusTarget: undefined,
    onFocusHandled: vi.fn(),
    onDisplayNameChange: vi.fn(),
    onPasswordChange: vi.fn(),
    onPasswordConfirmationChange: vi.fn(),
    onSelectLanguage: vi.fn(),
    onSelectTheme: vi.fn(),
    onSelectFontSize: vi.fn(),
    onSubmit: vi.fn(),
    ...overrides,
  }
  const result = renderRegistration(
    <>
      <h1 id="registration-heading">登録を完了する</h1>
      <RegistrationForm {...props} />
    </>,
    { languages },
  )
  return { props, ...result }
}

beforeEach(() => {
  resetDisplayTestState()
})

describe('RegistrationForm', () => {
  it('shows the email as a read-only username field that is not disabled (AC3.2.16)', () => {
    renderForm()
    expect(screen.getByRole('form', { name: '登録を完了する' })).toHaveAttribute('novalidate')
    const email = screen.getByLabelText('メールアドレス（ログインに使います）')
    expect(email).toHaveValue(EMAIL)
    expect(email).toHaveAttribute('readonly')
    expect(email).toHaveAttribute('type', 'email')
    expect(email).toHaveAttribute('autocomplete', 'username')
    expect(email).not.toBeDisabled()
  })

  it('uses new-password for both password fields and shows the length hint before typing', () => {
    renderForm()
    const password = screen.getByLabelText(/^パスワード\*?$/)
    const confirmation = screen.getByLabelText(/^パスワード（確かめ）/)
    expect(password).toHaveAttribute('type', 'password')
    expect(password).toHaveAttribute('autocomplete', 'new-password')
    expect(confirmation).toHaveAttribute('type', 'password')
    expect(confirmation).toHaveAttribute('autocomplete', 'new-password')
    expect(password).toHaveAccessibleDescription('12 文字以上')
    expect(screen.getByLabelText(/^氏名/)).toHaveAccessibleDescription('そのままでも登録できます')
  })

  it('passes typing and the submit up', async () => {
    const user = userEvent.setup()
    const { props } = renderForm()
    await user.type(screen.getByLabelText(/^パスワード\*?$/), 'x')
    expect(props.onPasswordChange).toHaveBeenCalledWith('x')
    await user.type(screen.getByLabelText(/^パスワード（確かめ）/), 'y')
    expect(props.onPasswordConfirmationChange).toHaveBeenCalledWith('y')
    await user.type(screen.getByLabelText(/^氏名/), 'z')
    expect(props.onDisplayNameChange).toHaveBeenCalledWith(`${EMAIL}z`)
    await user.click(screen.getByRole('button', { name: '登録を完了する' }))
    expect(props.onSubmit).toHaveBeenCalledTimes(1)
  })

  it('ties the errors to the fields as text and keeps the values', () => {
    renderForm({
      values: valuesOf({ password: 'short', passwordConfirmation: 'other' }),
      problems: {
        password: 'registration.password.tooShort',
        passwordConfirmation: 'registration.passwordConfirmation.mismatch',
      },
    })
    const password = screen.getByLabelText(/^パスワード\*?$/)
    expect(password).toHaveAttribute('aria-invalid', 'true')
    expect(password).toHaveAccessibleDescription('パスワードは 12 文字以上で入力してください')
    expect(password).toHaveValue('short')
    const confirmation = screen.getByLabelText(/^パスワード（確かめ）/)
    expect(confirmation).toHaveAccessibleDescription('パスワードが一致しません')
    expect(screen.getByLabelText(/^氏名/)).not.toHaveAttribute('aria-invalid')
  })

  it('draws the three choices as named fieldsets with lang only on the language options', async () => {
    const user = userEvent.setup()
    const { props } = renderForm()
    expect(screen.getByRole('heading', { level: 2, name: '表示の設定' })).toBeInTheDocument()
    const language = screen.getByRole('group', {
      name: /言語.*選ぶとこの画面の言語が切り替わります/,
    })
    expect(language.tagName).toBe('FIELDSET')
    expect(screen.getByRole('group', { name: 'テーマ' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: '文字の大きさ' })).toBeInTheDocument()
    expect(screen.getByText('日本語')).toHaveAttribute('lang', 'ja')
    expect(screen.getByText('English')).toHaveAttribute('lang', 'en')
    expect(screen.getByText('OS に合わせる')).not.toHaveAttribute('lang')
    expect(screen.getByText('標準')).not.toHaveAttribute('lang')
    expect(screen.getByRole('radio', { name: '日本語' })).toBeChecked()
    expect(screen.getByRole('radio', { name: 'OS に合わせる' })).toBeChecked()
    expect(screen.getByRole('radio', { name: '標準' })).toBeChecked()
    expect(screen.getByText('テーマと文字の大きさは選ぶと画面に反映されます')).toBeInTheDocument()

    await user.click(screen.getByRole('radio', { name: 'ダーク' }))
    expect(props.onSelectTheme).toHaveBeenCalledWith('dark')
    await user.click(screen.getByRole('radio', { name: '大' }))
    expect(props.onSelectFontSize).toHaveBeenCalledWith('lg')
    screen.getByRole('radio', { name: '日本語' }).focus()
    await user.keyboard('{ArrowRight}')
    expect(props.onSelectLanguage).toHaveBeenCalledWith('en')
  })

  it('shows the busy submit button and read-only fields while submitting', async () => {
    const user = userEvent.setup()
    const { props } = renderForm({ submitting: true })
    const submit = screen.getByRole('button', { name: '登録しています' })
    expect(submit).toHaveAttribute('aria-disabled', 'true')
    expect(submit).toHaveAttribute('aria-busy', 'true')
    expect(submit).not.toBeDisabled()
    expect(screen.getByLabelText(/^氏名/)).toHaveAttribute('readonly')
    expect(screen.getByLabelText(/^パスワード\*?$/)).toHaveAttribute('readonly')
    expect(screen.getByLabelText(/^パスワード（確かめ）/)).toHaveAttribute('readonly')
    expect(screen.getByRole('radio', { name: 'ダーク' })).not.toBeDisabled()
    submit.focus()
    await user.click(submit)
    expect(props.onSubmit).not.toHaveBeenCalled()
    expect(submit).toHaveFocus()
  })

  it('shows a failure notice as an alert above the form', () => {
    const { unmount } = renderForm({ failure: 'validationFailed' })
    expect(screen.getByRole('alert')).toHaveTextContent(
      '入力を確かめてください。直しても登録できないときは、招待した管理者に連絡してください。',
    )
    unmount()
    renderForm({ failure: 'submitFailed' }, ['en-US'])
    expect(screen.getByRole('alert')).toHaveTextContent('The registration failed.')
  })

  it.each<[RegistrationFocusTarget, string]>([
    ['displayName', 'registration-display-name-input'],
    ['password', 'registration-password-input'],
    ['passwordConfirmation', 'registration-password-confirmation-input'],
    ['failure', 'registration-failure-alert'],
  ])('moves focus to %s after rendering and reports it', (target, testId) => {
    const { props } = renderForm({ focusTarget: target, failure: 'submitFailed' })
    expect(screen.getByTestId(testId)).toHaveFocus()
    expect(props.onFocusHandled).toHaveBeenCalledTimes(1)
  })

  it('has no accessibility violations with errors and a failure notice', async () => {
    const { container } = renderForm({
      failure: 'validationFailed',
      problems: { displayName: 'registration.displayName.required' },
    })
    expect(await axe(container)).toHaveNoViolations()
  })
})
