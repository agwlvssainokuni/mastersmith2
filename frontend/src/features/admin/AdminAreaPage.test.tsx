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
// 管理者向け領域のテスト（BR5.2、NFR9.1、NFR7.1、NFR8.1）。
import { screen, waitFor } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { renderWithProviders } from '../../app/testing/renderWithProviders'
import { registerAuthHandlers, resetApiClient } from '../../shared/api-client/apiClient'
import { AdminAreaPage } from './AdminAreaPage'
import { ADMIN_CHECK_PATH } from './adminApi'
import { registration } from './registration'

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  resetApiClient()
  registerAuthHandlers({
    getAccessToken: () => 'access-token',
    refresh: () => Promise.resolve(false),
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
  return renderWithProviders(<AdminAreaPage />, {
    route: '/admin',
    registrations: [registration],
    languages,
  })
}

describe('AdminAreaPage', () => {
  it('hides the content and tells assistive technology while the check is running', () => {
    fetchMock.mockReturnValueOnce(new Promise(() => {}))

    render()

    expect(screen.getByTestId('admin-area-page')).toHaveAttribute('aria-busy', 'true')
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

  it('shows the not found screen when the check answers 403', async () => {
    fetchMock.mockResolvedValueOnce(problem(403, 'ACCESS_DENIED'))

    render()

    expect(await screen.findByTestId('not-found-page')).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('ページが見つかりません')
    expect(screen.queryByTestId('admin-placeholder')).not.toBeInTheDocument()
  })

  it('shows a generic message when the server fails', async () => {
    fetchMock.mockResolvedValueOnce(problem(500, 'INTERNAL_ERROR'))

    render()

    expect(await screen.findByTestId('admin-area-error')).toHaveTextContent('表示できませんでした')
    expect(screen.queryByTestId('not-found-page')).not.toBeInTheDocument()
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

    expect(await screen.findByTestId('not-found-page')).toBeInTheDocument()
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
