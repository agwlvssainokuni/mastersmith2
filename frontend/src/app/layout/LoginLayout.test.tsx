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
import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import { renderWithProviders } from '../testing/renderWithProviders'
import { LoginLayout } from './LoginLayout'
import { StandaloneLayout } from './StandaloneLayout'

describe('LoginLayout', () => {
  it('shows the application name and the Japanese heading', () => {
    renderWithProviders(<LoginLayout />)
    expect(screen.getByTestId('login-layout-app-name')).toHaveTextContent('MasterSmith')
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('ログイン')
  })

  it('shows the English heading for an English browser', () => {
    renderWithProviders(<LoginLayout />, { languages: ['en-US'] })
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Sign in')
  })

  it('places the inputs given by U2 in its body', () => {
    renderWithProviders(
      <LoginLayout>
        <label htmlFor="email">メール</label>
        <input id="email" data-testid="login-email" />
      </LoginLayout>,
    )
    expect(screen.getByTestId('login-layout-body')).toContainElement(
      screen.getByTestId('login-email'),
    )
  })

  it('has no inputs or buttons of its own and renders without children', () => {
    renderWithProviders(<LoginLayout />)
    expect(screen.getByTestId('login-layout-body')).toBeEmptyDOMElement()
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderWithProviders(
      <StandaloneLayout>
        <LoginLayout />
      </StandaloneLayout>,
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
