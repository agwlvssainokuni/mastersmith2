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
// ログインの画面の言語の切り替えのテスト（U4 の W7・D12、CR1.5・CR6.6、NFR7.2・NFR7.4・NFR8.2）。
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { DISPLAY_SETTINGS_KEY } from '../../app/display-settings/browserStorage'
import { renderWithProviders, resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { resetAuthSession } from './authSession'
import { LoginPage } from './LoginPage'
import { registration } from './registration'

function render(languages: readonly string[] = ['ja-JP']) {
  return renderWithProviders(<LoginPage />, {
    registrations: [registration],
    route: '/login',
    languages,
  })
}

function stored(): unknown {
  return JSON.parse(localStorage.getItem(DISPLAY_SETTINGS_KEY) ?? 'null')
}

beforeEach(() => {
  resetDisplayTestState()
  resetAuthSession()
})

afterEach(() => {
  vi.unstubAllGlobals()
  resetDisplayTestState()
  resetAuthSession()
})

describe('LoginLanguageSwitch', () => {
  it('names the choices in their own languages with lang attributes, even on an English screen', () => {
    render(['en-US'])

    const group = screen.getByRole('group', { name: 'Display language' })
    expect(group).toBeInTheDocument()
    expect(screen.getByTestId('login-language-switch-ja')).toHaveTextContent('日本語')
    expect(screen.getByTestId('login-language-switch-ja')).toHaveAttribute('lang', 'ja')
    expect(screen.getByTestId('login-language-switch-en')).toHaveTextContent('English')
    expect(screen.getByTestId('login-language-switch-en')).toHaveAttribute('lang', 'en')
  })

  it('marks the current language as pressed', () => {
    render()

    expect(screen.getByTestId('login-language-switch-ja')).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByTestId('login-language-switch-en')).toHaveAttribute('aria-pressed', 'false')
    expect(screen.getByRole('group', { name: '表示の言語' })).toBeInTheDocument()
  })

  it('switches the text and <html lang> and saves only the language', async () => {
    localStorage.setItem(DISPLAY_SETTINGS_KEY, '{"theme":"dark","fontSize":"lg"}')
    render()

    await userEvent.click(screen.getByTestId('login-language-switch-en'))

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Sign in')
    expect(document.documentElement.lang).toBe('en')
    expect(stored()).toEqual({ language: 'en', theme: 'dark', fontSize: 'lg' })
    expect(screen.getByTestId('login-language-switch-en')).toHaveAttribute('aria-pressed', 'true')
  })

  it('sends a failed login after switching with the switched request language', async () => {
    const fetchMock = vi.fn().mockResolvedValue(
      new Response(JSON.stringify({ code: 'AUTHENTICATION_FAILED' }), {
        status: 401,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    )
    vi.stubGlobal('fetch', fetchMock)
    render()

    await userEvent.click(screen.getByTestId('login-language-switch-en'))
    await userEvent.type(screen.getByTestId('login-form-email-input'), 'hanako@example.com')
    await userEvent.type(screen.getByTestId('login-form-password-input'), 'wrong-password')
    await userEvent.click(screen.getByTestId('login-form-submit-button'))

    await waitFor(() => expect(fetchMock).toHaveBeenCalled())
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe('/api/auth/login')
    expect(new Headers(init.headers).get('Accept-Language')).toBe('en')
    expect(await screen.findByTestId('login-form-error-text')).toHaveTextContent(
      'The email address or the password is not correct',
    )
  })

  it('switches with the keyboard and keeps the focus on the chosen button', async () => {
    render()
    const keyboard = userEvent.setup()

    await keyboard.tab()
    expect(screen.getByTestId('login-language-switch-ja')).toHaveFocus()
    await keyboard.tab()
    expect(screen.getByTestId('login-language-switch-en')).toHaveFocus()
    await keyboard.keyboard('{Enter}')
    expect(document.documentElement.lang).toBe('en')
    expect(screen.getByTestId('login-language-switch-en')).toHaveFocus()

    await keyboard.tab({ shift: true })
    await keyboard.keyboard(' ')
    expect(document.documentElement.lang).toBe('ja')
    expect(screen.getByTestId('login-language-switch-ja')).toHaveFocus()
  })

  it('has no accessibility violations', async () => {
    const { container } = render()

    expect(await axe(container)).toHaveNoViolations()
  })
})
