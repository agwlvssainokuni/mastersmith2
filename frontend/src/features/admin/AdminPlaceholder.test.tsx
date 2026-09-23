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
// 管理者向け領域のプレースホルダのテスト（BR5.3、NFR7.1、NFR8.1）。
import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import { renderWithProviders } from '../../app/testing/renderWithProviders'
import { AdminPlaceholder } from './AdminPlaceholder'
import { registration } from './registration'

describe('AdminPlaceholder', () => {
  it('shows the heading and the explanation in Japanese', () => {
    renderWithProviders(<AdminPlaceholder />, { registrations: [registration] })

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('管理')
    expect(screen.getByTestId('admin-placeholder')).toHaveTextContent('今後の管理機能')
  })

  it('shows the heading and the explanation in English for an English browser', () => {
    renderWithProviders(<AdminPlaceholder />, {
      registrations: [registration],
      languages: ['en-US'],
    })

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Administration')
    expect(screen.getByTestId('admin-placeholder')).toHaveTextContent(
      'Administration features will be added',
    )
  })

  it('has no accessibility violations', async () => {
    const { container } = renderWithProviders(<AdminPlaceholder />, {
      registrations: [registration],
    })

    expect(await axe(container)).toHaveNoViolations()
  })
})
