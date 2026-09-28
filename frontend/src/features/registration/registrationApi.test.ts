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
// registrationApi のテスト（frontend-components.md の 3節、security-design.md の 3.1、NFR1.1・NFR9.1）。
// fetch を差し替えて、要求の形と応答の読み取りを確かめる。ApiClient は本物で動かす。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  registerAuthHandlers,
  registerLanguageResolver,
  resetApiClient,
  TOKENLESS_API_PATHS,
} from '../../shared/api-client/apiClient'
import type { ApiError } from '../../shared/api-client/apiError'
import {
  completeRegistration,
  REGISTRATION_COMPLETE_PATH,
  REGISTRATION_VERIFY_PATH,
  verifyRegistration,
  type CompleteRegistrationRequest,
} from './registrationApi'

const TOKEN = 'registration-test-token-value'

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  resetApiClient()
  fetchMock = vi.fn()
  vi.stubGlobal('fetch', fetchMock)
  // ログイン中の状態（アクセストークンがある）でも、公開のパスには付かないことを見る。
  registerAuthHandlers({
    getAccessToken: () => 'access-token',
    refresh: () => Promise.resolve(false),
    onUnauthenticated: () => {},
  })
  registerLanguageResolver(() => 'en')
})

afterEach(() => {
  vi.unstubAllGlobals()
  resetApiClient()
})

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function problem(status: number, code: string | undefined): Response {
  return new Response(
    JSON.stringify({ status, code, detail: 'server-detail-marker', title: 'marker' }),
    { status, headers: { 'Content-Type': 'application/problem+json' } },
  )
}

function call(index = 0): { path: string; init: RequestInit; headers: Headers } {
  const [path, init] = fetchMock.mock.calls[index] as [string, RequestInit]
  return { path, init, headers: new Headers(init.headers) }
}

async function failure(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise
  } catch (error) {
    return error as ApiError
  }
  throw new Error('expected a failure')
}

const request: CompleteRegistrationRequest = {
  token: TOKEN,
  displayName: '招待 花子',
  password: 'password-1234',
  passwordConfirmation: 'password-1234',
  language: 'ja',
  theme: 'dark',
  fontSize: 'lg',
}

describe('registrationApi', () => {
  it('uses the same paths as the tokenless API paths of ApiClient', () => {
    expect(TOKENLESS_API_PATHS).toContain(REGISTRATION_VERIFY_PATH)
    expect(TOKENLESS_API_PATHS).toContain(REGISTRATION_COMPLETE_PATH)
  })

  it('sends the token only in the body of the verify request, without an access token', async () => {
    fetchMock.mockResolvedValue(json(200, { email: 'hanako@example.test', language: 'ja' }))

    await verifyRegistration(TOKEN)

    const { path, init, headers } = call()
    expect(path).toBe('/api/registration/verify')
    expect(init.method).toBe('POST')
    expect(headers.get('Content-Type')).toBe('application/json')
    expect(JSON.parse(String(init.body))).toEqual({ token: TOKEN })
    expect(path).not.toContain(TOKEN)
    expect(headers.has('Authorization')).toBe(false)
    // Accept-Language は呼び出し側ではなく ApiClient が画面の言語で付ける。
    expect(headers.get('Accept-Language')).toBe('en')
  })

  it('returns only the email and language of the invitation', async () => {
    fetchMock.mockResolvedValue(
      json(200, { email: 'hanako@example.test', language: 'en', extra: 'dropped' }),
    )

    await expect(verifyRegistration(TOKEN)).resolves.toEqual({
      email: 'hanako@example.test',
      language: 'en',
    })
  })

  it('treats a malformed verify body as a network failure', async () => {
    fetchMock.mockResolvedValueOnce(json(200, { email: 'hanako@example.test', language: 'fr' }))
    expect(await failure(verifyRegistration(TOKEN))).toEqual({ kind: 'network' })
    fetchMock.mockResolvedValueOnce(json(200, { email: 7, language: 'ja' }))
    expect(await failure(verifyRegistration(TOKEN))).toEqual({ kind: 'network' })
    fetchMock.mockResolvedValueOnce(json(200, ['hanako@example.test', 'ja']))
    expect(await failure(verifyRegistration(TOKEN))).toEqual({ kind: 'network' })
    fetchMock.mockResolvedValueOnce(new Response('not json', { status: 200 }))
    expect(await failure(verifyRegistration(TOKEN))).toEqual({ kind: 'network' })
  })

  it('sends only the seven items in the complete request and accepts an empty 204', async () => {
    fetchMock.mockResolvedValue(new Response(null, { status: 204 }))

    await expect(
      completeRegistration({ ...request, extra: 'dropped' } as CompleteRegistrationRequest),
    ).resolves.toBeUndefined()

    const { path, init, headers } = call()
    expect(path).toBe('/api/registration/complete')
    expect(init.method).toBe('POST')
    expect(JSON.parse(String(init.body))).toEqual(request)
    expect(path).not.toContain(TOKEN)
    expect(headers.has('Authorization')).toBe(false)
  })

  it('passes the status and code of a failure as they are', async () => {
    fetchMock.mockResolvedValueOnce(problem(404, 'REGISTRATION_LINK_INVALID'))
    expect(await failure(verifyRegistration(TOKEN))).toEqual({
      kind: 'response',
      status: 404,
      code: 'REGISTRATION_LINK_INVALID',
    })
    fetchMock.mockResolvedValueOnce(problem(400, 'VALIDATION_FAILED'))
    expect(await failure(completeRegistration(request))).toEqual({
      kind: 'response',
      status: 400,
      code: 'VALIDATION_FAILED',
    })
    fetchMock.mockResolvedValueOnce(problem(500, undefined))
    expect(await failure(completeRegistration(request))).toEqual({ kind: 'response', status: 500 })
  })

  it('does not refresh the token on 401 and reports a network failure as it is', async () => {
    const refresh = vi.fn(() => Promise.resolve(true))
    registerAuthHandlers({
      getAccessToken: () => 'access-token',
      refresh,
      onUnauthenticated: () => {},
    })
    fetchMock.mockResolvedValueOnce(problem(401, 'AUTHENTICATION_REQUIRED'))
    expect(await failure(verifyRegistration(TOKEN))).toMatchObject({ status: 401 })
    expect(refresh).not.toHaveBeenCalled()
    expect(fetchMock).toHaveBeenCalledTimes(1)

    fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
    expect(await failure(completeRegistration(request))).toEqual({ kind: 'network' })
  })
})
