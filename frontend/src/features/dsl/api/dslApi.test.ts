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
// dslApi のテスト（BR2.1〜BR2.5・BR2.8、NFR3.10）。fetch を差し替えて、要求の形と応答の読み取りを確かめる。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { registerAuthHandlers, resetApiClient } from '../../../shared/api-client/apiClient'
import { toApiError } from '../../../shared/api-client/apiError'
import {
  applyPreview,
  DEFAULT_DOWNLOAD_FILE_NAME,
  discardPreview,
  downloadApplied,
  downloadPreview,
  fileNameFromContentDisposition,
  generatePreview,
  getHistory,
  getPreview,
  getStatus,
  readErrorReport,
  restoreRevision,
  submitPreview,
} from './dslApi'

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

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function problem(status: number, body: Record<string, unknown>): Response {
  return new Response(JSON.stringify({ status, ...body }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

function call(index = 0): { path: string; init: RequestInit; headers: Headers } {
  const [path, init] = fetchMock.mock.calls[index] as [string, RequestInit]
  return { path, init, headers: new Headers(init.headers) }
}

const previewBody = {
  previewId: 'p-1',
  dslHash: 'a'.repeat(64),
  source: 'UPLOAD',
  by: { userId: '1', email: 'admin@example.com' },
  at: '2026-09-24T02:02:00Z',
  summary: {
    tableCount: 1,
    viewCount: 0,
    columnCount: 2,
    menuTree: [],
    missingDisplayNames: [],
    missingDisplayNameTotal: 0,
  },
  diff: { appliedExists: false, tables: [] },
  warnings: [],
  unknownField: 'ignored',
}

describe('dslApi', () => {
  it('reads the status and the preview with the access token', async () => {
    fetchMock
      .mockResolvedValueOnce(json(200, { applied: null, preview: null }))
      .mockResolvedValueOnce(json(200, previewBody))

    expect(await getStatus()).toEqual({ applied: null, preview: null })
    const preview = await getPreview()

    expect(preview.previewId).toBe('p-1')
    expect(call(0)).toMatchObject({ path: '/api/admin/dsl/status', init: { method: 'GET' } })
    expect(call(1).path).toBe('/api/admin/dsl/preview')
    expect(call(1).headers.get('Authorization')).toBe('Bearer access-token')
  })

  it('submits the text as application/yaml with the source in the query', async () => {
    fetchMock.mockResolvedValueOnce(json(201, previewBody))

    await submitPreview('version: 1\n表示名: 部署\n', 'PASTE')

    const { path, init, headers } = call()
    expect(path).toBe('/api/admin/dsl/preview?source=PASTE')
    expect(init.method).toBe('POST')
    expect(headers.get('Content-Type')).toBe('application/yaml')
    expect(init.body).toBe('version: 1\n表示名: 部署\n')
  })

  it('sends UPLOAD as the source of a file', async () => {
    fetchMock.mockResolvedValueOnce(json(201, previewBody))

    await submitPreview('version: 1\n', 'UPLOAD')

    expect(call().path).toBe('/api/admin/dsl/preview?source=UPLOAD')
  })

  it('posts generate, restore and apply to their paths', async () => {
    fetchMock
      .mockResolvedValueOnce(json(201, previewBody))
      .mockResolvedValueOnce(json(201, previewBody))
      .mockResolvedValueOnce(json(200, { applied: null, preview: null }))

    await generatePreview()
    await restoreRevision('r/1 x')
    await applyPreview('p-1')

    expect(call(0)).toMatchObject({
      path: '/api/admin/dsl/preview/generate',
      init: { method: 'POST' },
    })
    expect(call(1)).toMatchObject({
      path: '/api/admin/dsl/history/r%2F1%20x/restore',
      init: { method: 'POST' },
    })
    expect(call(2).path).toBe('/api/admin/dsl/apply')
    expect(call(2).headers.get('Content-Type')).toBe('application/json')
    expect(JSON.parse(call(2).init.body as string)).toEqual({ previewId: 'p-1' })
  })

  it('discards the preview and reads the history', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response(null, { status: 204 }))
      .mockResolvedValueOnce(json(200, [{ revisionId: 'r-1', current: true }]))

    await expect(discardPreview()).resolves.toBeUndefined()
    const history = await getHistory()

    expect(call(0)).toMatchObject({ path: '/api/admin/dsl/preview', init: { method: 'DELETE' } })
    expect(call(1).path).toBe('/api/admin/dsl/history')
    expect(history[0].current).toBe(true)
  })

  it('rejects with the status and the code of a failure', async () => {
    fetchMock.mockResolvedValueOnce(problem(404, { code: 'DSL_PREVIEW_NOT_FOUND' }))

    await expect(getPreview()).rejects.toEqual({
      kind: 'response',
      status: 404,
      code: 'DSL_PREVIEW_NOT_FOUND',
    })
  })

  it('rejects as a network error when a success body is not JSON', async () => {
    fetchMock.mockResolvedValueOnce(new Response('<html></html>', { status: 200 }))

    await expect(getStatus()).rejects.toEqual({ kind: 'network' })
  })

  it('downloads the preview and the applied DSL with the file name of the server', async () => {
    fetchMock
      .mockResolvedValueOnce(
        new Response('version: 1\n', {
          status: 200,
          headers: {
            'Content-Disposition': 'attachment; filename="dsl-preview-8b02d4aaaaaa.yaml"',
          },
        }),
      )
      .mockResolvedValueOnce(new Response('version: 1\n', { status: 200 }))

    const preview = await downloadPreview()
    const applied = await downloadApplied()

    expect(preview.fileName).toBe('dsl-preview-8b02d4aaaaaa.yaml')
    expect(await preview.blob.text()).toBe('version: 1\n')
    expect(call(0).path).toBe('/api/admin/dsl/preview/download')
    expect(applied.fileName).toBe(DEFAULT_DOWNLOAD_FILE_NAME)
    expect(call(1).path).toBe('/api/admin/dsl/applied/download')
  })
})

describe('fileNameFromContentDisposition', () => {
  it('reads the quoted, the unquoted and the extended file names', () => {
    expect(fileNameFromContentDisposition('attachment; filename="a.yaml"')).toBe('a.yaml')
    expect(fileNameFromContentDisposition('attachment; filename=b.yaml')).toBe('b.yaml')
    expect(
      fileNameFromContentDisposition(
        'attachment; filename="x.yaml"; filename*=UTF-8\'\'%E5%AE%9A%E7%BE%A9.yaml',
      ),
    ).toBe('定義.yaml')
  })

  it('falls back to dsl.yaml when the name cannot be read', () => {
    expect(fileNameFromContentDisposition(null)).toBe('dsl.yaml')
    expect(fileNameFromContentDisposition('attachment')).toBe('dsl.yaml')
    expect(fileNameFromContentDisposition('attachment; filename=""')).toBe('dsl.yaml')
    expect(fileNameFromContentDisposition("attachment; filename*=UTF-8''%E5%ZZ")).toBe('dsl.yaml')
  })
})

describe('readErrorReport', () => {
  async function errorOf(status: number, body: Record<string, unknown>): Promise<unknown> {
    return toApiError(problem(status, body))
  }

  it('reads the total and the errors of DSL_INVALID', async () => {
    const error = await errorOf(422, {
      code: 'DSL_INVALID',
      total: 128,
      errors: [
        { kind: 'SEMANTIC', line: 12, column: 5, path: 'tables.dept_mst', message: '誤り' },
        { kind: 'SIZE_LIMIT', line: null, column: null, path: null, message: '大きすぎます' },
        { kind: 'SYNTAX', message: '行と列なし' },
      ],
    })

    expect(readErrorReport(error)).toEqual({
      total: 128,
      errors: [
        { kind: 'SEMANTIC', line: 12, column: 5, path: 'tables.dept_mst', message: '誤り' },
        { kind: 'SIZE_LIMIT', line: null, column: null, path: null, message: '大きすぎます' },
        { kind: 'SYNTAX', line: null, column: null, path: null, message: '行と列なし' },
      ],
    })
  })

  it('treats a body without a usable total or errors as an unknown error', async () => {
    expect(readErrorReport(await errorOf(422, { code: 'DSL_INVALID' }))).toBeUndefined()
    expect(
      readErrorReport(await errorOf(422, { code: 'DSL_INVALID', total: 0, errors: [] })),
    ).toBeUndefined()
    expect(
      readErrorReport(await errorOf(422, { code: 'DSL_INVALID', total: 1.5, errors: [] })),
    ).toBeUndefined()
    expect(
      readErrorReport(await errorOf(422, { code: 'DSL_INVALID', total: 1, errors: 'x' })),
    ).toBeUndefined()
    expect(
      readErrorReport(
        await errorOf(422, { code: 'DSL_INVALID', total: 1, errors: [{ kind: 'SYNTAX' }] }),
      ),
    ).toBeUndefined()
    expect(
      readErrorReport(
        await errorOf(422, {
          code: 'DSL_INVALID',
          total: 1,
          errors: [{ kind: 'SYNTAX', message: 'm', line: '1' }],
        }),
      ),
    ).toBeUndefined()
  })

  it('reads nothing from a network error or a value that is not an error', () => {
    expect(readErrorReport({ kind: 'network' })).toBeUndefined()
    expect(readErrorReport(undefined)).toBeUndefined()
    expect(readErrorReport({ kind: 'response', status: 422 })).toBeUndefined()
  })
})
