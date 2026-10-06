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
// 権限が無いときの表示 S6 のテスト（U4 の D11・D12、R-05、NFR3.1・NFR7.1・NFR7.2・NFR8.1）。
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import type { FeatureRegistration } from '../registry/types'
import {
  fakeProvider,
  renderWithProviders,
  resetDisplayTestState,
} from '../testing/renderWithProviders'
import { AdminForbiddenView } from './AdminForbiddenView'

const registrations: FeatureRegistration[] = [
  {
    featureId: 'dsl',
    sidebarItems: [
      {
        id: 'dsl',
        labelKey: 'dsl.nav.label',
        path: '/admin/dsl',
        order: 20,
        visibleWhen: 'ADMIN',
        section: 'ADMIN',
      },
    ],
    messages: { ja: { 'dsl.nav.label': 'DSL の管理' }, en: { 'dsl.nav.label': 'DSL admin' } },
  },
]

function renderView(route = '/admin/dsl', languages: readonly string[] = ['ja-JP']) {
  return renderWithProviders(<AdminForbiddenView />, {
    route,
    registrations,
    languages,
    provider: fakeProvider({ loggedIn: true, admin: false }),
  })
}

beforeEach(() => {
  resetDisplayTestState()
})

afterEach(() => {
  vi.restoreAllMocks()
  resetDisplayTestState()
})

describe('AdminForbiddenView', () => {
  it('shows the Japanese message, the home link and the heading of the sidebar item', async () => {
    renderView()
    const view = await screen.findByTestId('admin-forbidden-view')

    expect(within(view).getByRole('heading', { level: 1 })).toHaveTextContent('DSL の管理')
    expect(view).toHaveTextContent('この画面を使う権限がありません')
    expect(screen.getByTestId('admin-forbidden-home-link')).toHaveTextContent('ホームへ戻る')
    expect(screen.getByTestId('admin-forbidden-home-link')).toHaveAttribute('href', '/')
    expect(screen.queryByText('ページが見つかりません')).not.toBeInTheDocument()
  })

  it('shows the English texts for an English browser', async () => {
    renderView('/admin/dsl', ['en-US'])
    const view = await screen.findByTestId('admin-forbidden-view')

    expect(within(view).getByRole('heading', { level: 1 })).toHaveTextContent('DSL admin')
    expect(view).toHaveTextContent('You do not have permission to use this page.')
    expect(screen.getByTestId('admin-forbidden-home-link')).toHaveTextContent('Back to home')
  })

  it('uses the common heading for a URL without a sidebar item', async () => {
    renderView('/admin/unknown')
    expect(await screen.findByTestId('admin-forbidden-heading')).toHaveTextContent('管理')
  })

  it('announces the message politely with role status', async () => {
    renderView()
    const view = await screen.findByTestId('admin-forbidden-view')

    const alert = within(view).getByTestId('alert')
    expect(alert).toHaveAttribute('role', 'status')
    expect(alert).toHaveTextContent('この画面を使う権限がありません')
    expect(within(view).getByRole('heading', { level: 1 })).toHaveAccessibleName('DSL の管理')
    expect(view).toHaveAccessibleName('DSL の管理')
  })

  it('moves the focus to the heading and reaches home with the keyboard', async () => {
    const user = userEvent.setup()
    renderView()
    const heading = await screen.findByTestId('admin-forbidden-heading')
    await waitFor(() => expect(heading).toHaveFocus())

    await user.tab()
    expect(screen.getByTestId('admin-forbidden-home-link')).toHaveFocus()
    await user.keyboard('{Enter}')

    expect(screen.getByTestId('location')).toHaveTextContent(/^\/$/)
  })

  it('writes nothing to the console', async () => {
    const spies = (['log', 'info', 'warn', 'error', 'debug'] as const).map((name) =>
      vi.spyOn(console, name),
    )
    renderView()
    await screen.findByTestId('admin-forbidden-view')

    for (const spy of spies) {
      expect(spy).not.toHaveBeenCalled()
    }
  })

  it('has no accessibility violations', async () => {
    const { container } = renderView()
    await screen.findByTestId('admin-forbidden-view')
    expect(await axe(container)).toHaveNoViolations()
  })
})
