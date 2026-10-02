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
// 管理者向け領域のテスト（BR5.2、NFR9.1、NFR7.1、NFR8.1）。画面は骨組みの ShellLayout の中に、管理者のログイン状態の
// 提供元で描く（U4 の R-03）。403 は偽物の fetch で ApiClient を通して作り、ACCESS_DENIED の 403 は骨組みが S6 に置き換え、
// ApiClient に登録した偽物の更新（ログインの状態の読み直し）が1回呼ばれる（U4 の FC 6.2、AC2.2.1・AC2.2.4）。
import { screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { ShellLayout } from '../../app/layout/ShellLayout'
import { fakeProvider, renderWithProviders } from '../../app/testing/renderWithProviders'
import { registerAuthHandlers, resetApiClient } from '../../shared/api-client/apiClient'
import { AdminAreaPage } from './AdminAreaPage'
import { ADMIN_CHECK_PATH } from './adminApi'
import { registration } from './registration'

let fetchMock: ReturnType<typeof vi.fn>
let refreshMock: ReturnType<typeof vi.fn<() => Promise<boolean>>>

beforeEach(() => {
  resetApiClient()
  refreshMock = vi.fn(() => Promise.resolve(false))
  registerAuthHandlers({
    getAccessToken: () => 'access-token',
    refresh: refreshMock,
    onUnauthenticated: () => {},
  })
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

function problem(status: number, code: string): Response {
  return new Response(JSON.stringify({ code, status }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

function noContent(): Response {
  return new Response(null, { status: 204 })
}

function render(languages: readonly string[] = ['ja-JP']) {
  return renderWithProviders(
    <ShellLayout>
      <AdminAreaPage />
    </ShellLayout>,
    {
      route: '/admin',
      registrations: [registration],
      provider: fakeProvider({ loggedIn: true, admin: true, displayName: '管理者' }),
      languages,
    },
  )
}

describe('AdminAreaPage', () => {
  it('hides the content and tells assistive technology while the check is running', async () => {
    fetchMock.mockReturnValueOnce(new Promise(() => {}))

    render()

    // ログイン状態の提供元を渡すと、骨組みはその状態を読んでから描くため、描かれるのを待つ（R-03 の描き方）。
    expect(await screen.findByTestId('admin-area-page')).toHaveAttribute('aria-busy', 'true')
    expect(screen.getByTestId('admin-area-checking')).toHaveTextContent('確認しています')
    expect(screen.queryByTestId('admin-placeholder')).not.toBeInTheDocument()
  })

  it('shows the heading and the explanation when the check answers 204', async () => {
    fetchMock.mockResolvedValueOnce(noContent())

    render()

    expect(await screen.findByTestId('admin-placeholder')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('管理')
    expect(screen.getByTestId('admin-area-page')).toHaveAttribute('aria-busy', 'false')
  })

  it('shows the forbidden view and reads the login state again when the check answers 403', async () => {
    fetchMock.mockResolvedValueOnce(problem(403, 'ACCESS_DENIED'))

    render()

    expect(await screen.findByTestId('admin-forbidden-view')).toBeInTheDocument()
    expect(screen.getByTestId('admin-forbidden-heading')).toHaveTextContent('管理')
    expect(screen.getByTestId('admin-forbidden-view')).toHaveTextContent(
      'この画面を使う権限がありません',
    )
    expect(screen.queryByTestId('admin-area-page')).not.toBeInTheDocument()
    expect(screen.queryByTestId('admin-placeholder')).not.toBeInTheDocument()
    expect(screen.queryByTestId('not-found-page')).not.toBeInTheDocument()
    expect(screen.queryByTestId('admin-area-error')).not.toBeInTheDocument()
    await waitFor(() => expect(refreshMock).toHaveBeenCalledTimes(1))
  })

  it('shows a generic message when the server fails', async () => {
    fetchMock.mockResolvedValueOnce(problem(500, 'INTERNAL_ERROR'))

    render()

    expect(await screen.findByTestId('admin-area-error')).toHaveTextContent('表示できませんでした')
    expect(screen.queryByTestId('not-found-page')).not.toBeInTheDocument()
    expect(screen.queryByTestId('admin-forbidden-view')).not.toBeInTheDocument()
  })

  it('shows a generic message when the request never reaches the server', async () => {
    fetchMock.mockRejectedValueOnce(new TypeError('failed to fetch'))

    render()

    expect(await screen.findByTestId('admin-area-error')).toBeInTheDocument()
  })

  it('calls the check again on every display and keeps no result in memory', async () => {
    fetchMock.mockResolvedValueOnce(noContent())
    const first = render()
    expect(await screen.findByTestId('admin-placeholder')).toBeInTheDocument()
    first.unmount()

    fetchMock.mockResolvedValueOnce(problem(403, 'ACCESS_DENIED'))
    render()

    expect(await screen.findByTestId('admin-forbidden-view')).toBeInTheDocument()
    expect(screen.queryByTestId('admin-placeholder')).not.toBeInTheDocument()
    await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2))
    expect(fetchMock.mock.calls[1][0]).toBe(ADMIN_CHECK_PATH)
  })

  it('shows English text for an English browser', async () => {
    fetchMock.mockResolvedValueOnce(noContent())

    render(['en-US'])

    expect(await screen.findByTestId('admin-placeholder')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Administration')
  })

  it('has no accessibility violations while checking', async () => {
    fetchMock.mockReturnValueOnce(new Promise(() => {}))

    const { container } = render()

    expect(await axe(container)).toHaveNoViolations()
  })

  it('has no accessibility violations once the content is shown', async () => {
    fetchMock.mockResolvedValueOnce(noContent())

    const { container } = render()
    await screen.findByTestId('admin-placeholder')

    expect(await axe(container)).toHaveNoViolations()
  })
})
