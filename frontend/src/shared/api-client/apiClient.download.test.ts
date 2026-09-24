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
// ApiClient の拡張のテスト（DSL の管理画面の BR2.4・BR2.6、NFR3.10）。
// fetch を差し替えて、エラーに Problem Details の本文が付くことと、ダウンロードの受け取りを確かめる。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { apiDownload, apiRequest, registerAuthHandlers, resetApiClient } from './apiClient'
import type { ApiResponseError } from './apiError'

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  resetApiClient()
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  registerAuthHandlers({
    getAccessToken: () => 'access-token',
    refresh: () => Promise.resolve(false),
    onUnauthenticated: () => {},
  })
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('apiClient problem details and download', () => {
  it('rejects with the Problem Details body attached to the error', async () => {
    const body = { status: 422, code: 'DSL_INVALID', total: 1, errors: [] }
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify(body), {
        status: 422,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    )

    const error = (await apiRequest('/api/admin/dsl/preview', { method: 'POST' }).catch(
      (e: unknown) => e,
    )) as ApiResponseError

    expect(error).toEqual({ kind: 'response', status: 422, code: 'DSL_INVALID' })
    expect(error.problem).toEqual(body)
  })

  it('receives the bytes and the Content-Disposition of a download with the access token', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response('version: 1\n', {
        status: 200,
        headers: {
          'Content-Type': 'application/yaml',
          'Content-Disposition': 'attachment; filename="dsl-preview-3f9a1c000000.yaml"',
        },
      }),
    )

    const download = await apiDownload('/api/admin/dsl/preview/download')

    expect(await download.blob.text()).toBe('version: 1\n')
    expect(download.contentDisposition).toBe('attachment; filename="dsl-preview-3f9a1c000000.yaml"')
    const [path, init] = fetchMock.mock.calls[0] as [string, RequestInit]
    expect(path).toBe('/api/admin/dsl/preview/download')
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer access-token')
  })

  it('gives a null Content-Disposition when the header is missing', async () => {
    fetchMock.mockResolvedValueOnce(new Response('a: 1\n', { status: 200 }))

    const download = await apiDownload('/api/admin/dsl/applied/download')

    expect(download.contentDisposition).toBeNull()
  })

  it('rejects a failed download with the code of the server', async () => {
    fetchMock.mockResolvedValueOnce(
      new Response(JSON.stringify({ status: 404, code: 'DSL_APPLIED_NOT_FOUND' }), {
        status: 404,
        headers: { 'Content-Type': 'application/problem+json' },
      }),
    )

    await expect(apiDownload('/api/admin/dsl/applied/download')).rejects.toEqual({
      kind: 'response',
      status: 404,
      code: 'DSL_APPLIED_NOT_FOUND',
    })
  })

  it('rejects as a network error when the body of a download cannot be read', async () => {
    const broken = new Response('x', { status: 200 })
    vi.spyOn(broken, 'blob').mockRejectedValueOnce(new TypeError('aborted'))
    fetchMock.mockResolvedValueOnce(broken)

    await expect(apiDownload('/api/admin/dsl/preview/download')).rejects.toEqual({
      kind: 'network',
    })
  })
})
