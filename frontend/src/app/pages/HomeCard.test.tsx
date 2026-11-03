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
// ホームのカード 1 枚のテスト（Intent 261102-timezone-ui-polish の U5、D17・D21・D29）。
import { screen, within } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import { renderWithProviders, resetDisplayTestState } from '../testing/renderWithProviders'
import { HomeCard } from './HomeCard'

afterEach(() => {
  resetDisplayTestState()
})

describe('HomeCard', () => {
  it('is one link whose name is the label, with a decorative icon', () => {
    renderWithProviders(
      <HomeCard
        card={{ id: 'biz:0', label: '受注', icon: 'table', path: '/tables?schema=S&table=T' }}
        testId="card-0"
      />,
    )

    const link = screen.getByRole('link', { name: '受注' })
    expect(link).toHaveAttribute('href', '/tables?schema=S&table=T')
    expect(link).toHaveAttribute('data-testid', 'card-0')
    const icon = within(link).getByTestId('icon-table')
    expect(icon).toHaveAttribute('aria-hidden', 'true')
  })

  it('renders a hostile label as text and does not turn it into markup or a link target', () => {
    const label = '<script>alert(1)</script><img src=x onerror=alert(2)>&"\'javascript:alert(3)'
    const { container } = renderWithProviders(
      <HomeCard card={{ id: 'biz:1', label, icon: 'view', path: '/tables?x=1' }} testId="card-1" />,
    )

    expect(screen.getByRole('link')).toHaveTextContent(label)
    expect(container.querySelector('script, img')).toBeNull()
    expect(screen.getByRole('link')).toHaveAttribute('href', '/tables?x=1')
  })

  it('has no accessibility violations', async () => {
    const { container } = renderWithProviders(
      <HomeCard
        card={{ id: 'adm:dsl', label: 'DSL の管理', icon: 'edit', path: '/admin/dsl' }}
        testId="card-2"
      />,
    )

    expect(await axe(container)).toHaveNoViolations()
  })
})
