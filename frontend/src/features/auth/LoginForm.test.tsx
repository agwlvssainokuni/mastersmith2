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
// ログインのフォームのテスト（BR8.2、BR8.8、NFR4.3、NFR8.1）。
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { handOffToLogin, takeLoginHandoff } from '../../app/login-handoff/loginHandoff'
import { renderWithProviders, resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { resetApiClient } from '../../shared/api-client/apiClient'
import * as authApi from './authApi'
import { resetAuthSession } from './authSession'
import { LoginForm } from './LoginForm'
import { registration } from './registration'

const tokens = {
  accessToken: 'access-token',
  expiresAt: '2026-09-22T00:05:00Z',
  user: {
    email: 'user@example.com',
    admin: false,
    displayName: '利用者 一郎',
    language: 'ja' as const,
    theme: 'system' as const,
    fontSize: 'md' as const,
  },
}

function render(languages: readonly string[] = ['ja-JP']) {
  return renderWithProviders(<LoginForm />, { registrations: [registration], languages })
}

beforeEach(() => {
  resetAuthSession()
  resetApiClient()
})

afterEach(() => {
  vi.restoreAllMocks()
})

describe('LoginForm', () => {
  it('does not submit an empty form and shows a message next to each field', async () => {
    const request = vi.spyOn(authApi, 'requestLogin')
    render()

    await userEvent.click(screen.getByTestId('login-form-submit-button'))

    expect(await screen.findByText('メールアドレスを入力してください')).toBeInTheDocument()
    expect(screen.getByText('パスワードを入力してください')).toBeInTheDocument()
    expect(request).not.toHaveBeenCalled()
  })

  it('does not submit when only the password is missing', async () => {
    const request = vi.spyOn(authApi, 'requestLogin')
    render()

    await userEvent.type(screen.getByTestId('login-form-email-input'), 'user@example.com')
    await userEvent.click(screen.getByTestId('login-form-submit-button'))

    expect(await screen.findByText('パスワードを入力してください')).toBeInTheDocument()
    expect(request).not.toHaveBeenCalled()
  })

  it('moves to the home screen after a successful login', async () => {
    vi.spyOn(authApi, 'requestLogin').mockResolvedValue(tokens)
    render()

    await userEvent.type(screen.getByTestId('login-form-email-input'), 'user@example.com')
    await userEvent.type(screen.getByTestId('login-form-password-input'), 'パスワード')
    await userEvent.click(screen.getByTestId('login-form-submit-button'))

    await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/'))
  })

  it('shows one message for every authentication failure and empties the password field', async () => {
    vi.spyOn(authApi, 'requestLogin').mockRejectedValue({
      kind: 'response',
      status: 401,
      code: 'AUTHENTICATION_FAILED',
    })
    render()

    await userEvent.type(screen.getByTestId('login-form-email-input'), 'user@example.com')
    await userEvent.type(screen.getByTestId('login-form-password-input'), 'まちがい')
    await userEvent.click(screen.getByTestId('login-form-submit-button'))

    const alert = await screen.findByRole('alert')
    expect(alert).toHaveTextContent('メールアドレスまたはパスワードが正しくありません')
    expect(screen.getByTestId('login-form-error-alert')).toBeInTheDocument()
    expect(screen.getByTestId('login-form-password-input')).toHaveValue('')
  })

  it('shows the same message when the account is locked', async () => {
    vi.spyOn(authApi, 'requestLogin').mockRejectedValue({
      kind: 'response',
      status: 401,
      code: 'AUTHENTICATION_FAILED',
    })
    render()

    await userEvent.type(screen.getByTestId('login-form-email-input'), 'locked@example.com')
    await userEvent.type(screen.getByTestId('login-form-password-input'), '正しいパスワード')
    await userEvent.click(screen.getByTestId('login-form-submit-button'))

    expect(await screen.findByTestId('login-form-error-text')).toHaveTextContent(
      'メールアドレスまたはパスワードが正しくありません',
    )
  })

  it('shows a general message when the connection fails', async () => {
    vi.spyOn(authApi, 'requestLogin').mockRejectedValue({ kind: 'network' })
    render()

    await userEvent.type(screen.getByTestId('login-form-email-input'), 'user@example.com')
    await userEvent.type(screen.getByTestId('login-form-password-input'), 'パスワード')
    await userEvent.click(screen.getByTestId('login-form-submit-button'))

    expect(await screen.findByTestId('login-form-error-text')).toHaveTextContent(
      'ログインできませんでした。しばらくしてから、もう一度お試しください',
    )
  })

  it('disables the button while the request is running', async () => {
    let resolveLogin: (value: typeof tokens) => void = () => {}
    vi.spyOn(authApi, 'requestLogin').mockReturnValue(
      new Promise<typeof tokens>((resolve) => {
        resolveLogin = resolve
      }),
    )
    render()

    await userEvent.type(screen.getByTestId('login-form-email-input'), 'user@example.com')
    await userEvent.type(screen.getByTestId('login-form-password-input'), 'パスワード')
    await userEvent.click(screen.getByTestId('login-form-submit-button'))

    await waitFor(() => expect(screen.getByTestId('login-form-submit-button')).toBeDisabled())
    resolveLogin(tokens)
    await waitFor(() => expect(screen.getByTestId('login-form-submit-button')).toBeEnabled())
  })

  it('shows the English texts when the browser prefers English', async () => {
    render(['en-US'])

    await userEvent.click(screen.getByTestId('login-form-submit-button'))

    expect(await screen.findByText('Enter your email address')).toBeInTheDocument()
    expect(screen.getByTestId('login-form-submit-button')).toHaveTextContent('Log in')
  })

  it('has no accessibility violations', async () => {
    const { container } = render()

    expect(await axe(container)).toHaveNoViolations()
  })

  describe('after the registration is completed', () => {
    const EMAIL = 'display-leak-check@example.com'

    beforeEach(() => {
      resetDisplayTestState()
    })

    afterEach(() => {
      resetDisplayTestState()
    })

    function storedValues(): string {
      const values: string[] = []
      for (const storage of [localStorage, sessionStorage]) {
        for (let i = 0; i < storage.length; i += 1) {
          const key = storage.key(i)
          if (key !== null) values.push(`${key}=${storage.getItem(key) ?? ''}`)
        }
      }
      return values.join('\n')
    }

    it('shows the notice and fills in the handed-off email address', () => {
      handOffToLogin(EMAIL)
      render()

      expect(screen.getByTestId('login-form-registered-alert')).toHaveTextContent(
        '登録が完了しました。設定したパスワードでログインしてください。',
      )
      expect(screen.getByTestId('login-form-email-input')).toHaveValue(EMAIL)
      expect(takeLoginHandoff()).toBeNull()
    })

    it('does not show the notice on the second display or without a hand-off', () => {
      handOffToLogin(EMAIL)
      const first = render()
      first.unmount()

      render()
      expect(screen.queryByTestId('login-form-registered-alert')).not.toBeInTheDocument()
      expect(screen.getByTestId('login-form-email-input')).toHaveValue('')
    })

    it('shows the notice in English for the English screen', () => {
      handOffToLogin(EMAIL)
      render(['en-US'])

      expect(screen.getByTestId('login-form-registered-alert')).toHaveTextContent(
        'Registration is complete. Log in with the password you set.',
      )
    })

    it('puts the email address neither in the URL nor in the browser storage', () => {
      handOffToLogin(EMAIL)
      render()

      expect(window.location.href).not.toContain(EMAIL)
      expect(window.location.href).not.toContain(encodeURIComponent(EMAIL))
      expect(storedValues()).not.toContain(EMAIL)
    })

    it('has no accessibility violations with the notice', async () => {
      handOffToLogin(EMAIL)
      const { container } = render()

      expect(await axe(container)).toHaveNoViolations()
    })
  })
})
