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
// 使えないリンクの表示のテスト（W11、AC3.2.2、NFR3・NFR1.4・NFR7.2・NFR8.1）。
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { RegistrationUnavailable } from './RegistrationUnavailable'
import { TEST_TOKEN } from './testing/fixtures'
import { renderRegistration } from './testing/renderRegistration'

beforeEach(() => {
  resetDisplayTestState()
})

describe('RegistrationUnavailable', () => {
  it('shows the same warning for every reason as an alert', () => {
    renderRegistration(<RegistrationUnavailable />)
    expect(screen.getByRole('alert')).toHaveTextContent(
      'このリンクは使えません。いちばん新しい招待メールのリンクを使うか、招待した管理者に招待の送り直しを依頼してください。',
    )
    expect(screen.queryByRole('form')).not.toBeInTheDocument()
  })

  it('links to the login page with an in-app link, not a button', async () => {
    const user = userEvent.setup()
    renderRegistration(<RegistrationUnavailable />)
    const link = screen.getByRole('link', { name: 'ログインの画面へ' })
    expect(link).toHaveAttribute('href', '/login')
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
    await user.click(link)
    expect(screen.getByTestId('location')).toHaveTextContent('/login')
  })

  it('does not show the token, an email address or the length of validity', () => {
    renderRegistration(<RegistrationUnavailable />)
    const text = screen.getByTestId('registration-unavailable').textContent ?? ''
    expect(text).not.toContain(TEST_TOKEN)
    expect(text).not.toContain('@')
    expect(text).not.toMatch(/\d+ ?(時間|hours?)/)
  })

  it('shows the texts in English for an English screen', () => {
    renderRegistration(<RegistrationUnavailable />, { languages: ['en-US'] })
    expect(screen.getByRole('alert')).toHaveTextContent('This link cannot be used.')
    expect(screen.getByRole('link', { name: 'Go to the login page' })).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderRegistration(<RegistrationUnavailable />)
    expect(await axe(container)).toHaveNoViolations()
  })
})
