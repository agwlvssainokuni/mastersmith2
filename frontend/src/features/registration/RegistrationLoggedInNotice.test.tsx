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
// ログイン中の案内のテスト（W3、Q1 A、CR6.3、NFR7.2）。
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { RegistrationLoggedInNotice } from './RegistrationLoggedInNotice'
import { renderRegistration } from './testing/renderRegistration'

beforeEach(() => {
  resetDisplayTestState()
})

function renderNotice(loggingOut = false, languages: readonly string[] = ['ja-JP']) {
  const onLogout = vi.fn()
  const onHome = vi.fn()
  const result = renderRegistration(
    <RegistrationLoggedInNotice loggingOut={loggingOut} onLogout={onLogout} onHome={onHome} />,
    { languages },
  )
  return { onLogout, onHome, ...result }
}

describe('RegistrationLoggedInNotice', () => {
  it('shows the notice as an information status, not as an alert', () => {
    renderNotice()
    expect(screen.getByRole('status')).toHaveTextContent(
      'ログインしたままです。登録を続けるには、ログアウトしてください。',
    )
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('passes the two buttons up', async () => {
    const user = userEvent.setup()
    const { onLogout, onHome } = renderNotice()
    await user.click(screen.getByRole('button', { name: 'ログアウトして続ける' }))
    expect(onLogout).toHaveBeenCalledTimes(1)
    await user.click(screen.getByRole('button', { name: 'ホームへ戻る' }))
    expect(onHome).toHaveBeenCalledTimes(1)
  })

  it('makes the logout button busy while logging out, keeps focus and ignores presses', async () => {
    const user = userEvent.setup()
    const { onLogout } = renderNotice(true)
    const button = screen.getByRole('button', { name: 'ログアウトしています' })
    expect(button).toHaveAttribute('aria-disabled', 'true')
    expect(button).toHaveAttribute('aria-busy', 'true')
    expect(button).not.toBeDisabled()
    button.focus()
    await user.click(button)
    await user.keyboard('{Enter}')
    expect(onLogout).not.toHaveBeenCalled()
    expect(button).toHaveFocus()
  })

  it('shows the texts in English for an English screen', () => {
    renderNotice(false, ['en-US'])
    expect(screen.getByRole('status')).toHaveTextContent('You are logged in.')
    expect(screen.getByRole('button', { name: 'Log out and continue' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Back to home' })).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderNotice()
    expect(await axe(container)).toHaveNoViolations()
  })
})
