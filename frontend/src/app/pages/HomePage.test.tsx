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
import { StandaloneLayout } from '../layout/StandaloneLayout'
import { renderWithProviders } from '../testing/renderWithProviders'
import { HomePage } from './HomePage'

describe('HomePage', () => {
  it('shows the heading and a short description in Japanese', () => {
    renderWithProviders(<HomePage />)
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('ホーム')
    expect(screen.getByText(/MasterSmith へようこそ/)).toBeInTheDocument()
  })

  it('shows English text for an English browser', () => {
    renderWithProviders(<HomePage />, { languages: ['en'] })
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Home')
    expect(screen.getByText(/Welcome to MasterSmith/)).toBeInTheDocument()
  })

  it('has exactly one top-level heading', () => {
    renderWithProviders(<HomePage />)
    expect(screen.getAllByRole('heading')).toHaveLength(1)
  })

  it('has no accessibility violations', async () => {
    const { container } = renderWithProviders(
      <StandaloneLayout>
        <HomePage />
      </StandaloneLayout>,
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
