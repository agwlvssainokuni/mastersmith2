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
// ログイン画面のテスト（BR8.1、NFR8.1）。
import { screen } from '@testing-library/react'
import { beforeEach, describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import { renderWithProviders } from '../../app/testing/renderWithProviders'
import { resetApiClient } from '../../shared/api-client/apiClient'
import { resetAuthSession } from './authSession'
import { LoginPage } from './LoginPage'
import { registration } from './registration'

function render() {
  return renderWithProviders(<LoginPage />, { registrations: [registration], route: '/login' })
}

beforeEach(() => {
  resetAuthSession()
  resetApiClient()
})

describe('LoginPage', () => {
  it('puts the login form inside the login layout', () => {
    render()

    expect(screen.getByTestId('login-layout')).toBeInTheDocument()
    expect(screen.getByTestId('login-layout-body')).toContainElement(
      screen.getByTestId('login-form'),
    )
  })

  it('shows the application name and the login heading', () => {
    render()

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('ログイン')
    expect(screen.getByTestId('login-layout-app-name')).toHaveTextContent('MasterSmith')
  })

  it('has no accessibility violations', async () => {
    const { container } = render()

    expect(await axe(container)).toHaveNoViolations()
  })
})
