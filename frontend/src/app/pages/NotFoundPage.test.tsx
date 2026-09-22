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
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import { StandaloneLayout } from '../layout/StandaloneLayout'
import { renderWithProviders } from '../testing/renderWithProviders'
import { NotFoundPage } from './NotFoundPage'

describe('NotFoundPage', () => {
  it('shows the not found message', () => {
    renderWithProviders(<NotFoundPage />, { route: '/missing' })
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('ページが見つかりません')
  })

  it('moves to home when the home link is chosen', async () => {
    const user = userEvent.setup()
    renderWithProviders(<NotFoundPage />, { route: '/missing' })
    expect(screen.getByTestId('location')).toHaveTextContent('/missing')
    await user.click(screen.getByTestId('not-found-home-link'))
    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/)
  })

  it('shows English text for an English browser', () => {
    renderWithProviders(<NotFoundPage />, { languages: ['en-US'] })
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Page not found')
    expect(screen.getByTestId('not-found-home-link')).toHaveTextContent('Go to home')
  })

  it('has no accessibility violations', async () => {
    const { container } = renderWithProviders(
      <StandaloneLayout>
        <NotFoundPage />
      </StandaloneLayout>,
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
