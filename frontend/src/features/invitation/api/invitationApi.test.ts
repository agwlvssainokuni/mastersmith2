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
// invitationApi のテスト（frontend-components.md の 5節、security-design.md の 1節・3.3〜3.5、NFR1.1・NFR9.1）。
// fetch を差し替えて、要求の形と応答の読み取りを確かめる。ApiClient の中（トークン・401 の更新・Accept-Language）は
// 既存と U4 のテストが確かめるため、ここでは見ない。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { registerAuthHandlers, resetApiClient } from '../../../shared/api-client/apiClient'
import type { ApiError } from '../../../shared/api-client/apiError'
import {
  cancelInvitation,
  createInvitation,
  listInvitations,
  readPendingProblem,
  readUnavailableReasons,
  resendInvitation,
} from './invitationApi'

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
  return new Response(JSON.stringify({ status, detail: 'server-detail-marker', ...body }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

function call(index = 0): { path: string; init: RequestInit; headers: Headers } {
  const [path, init] = fetchMock.mock.calls[index] as [string, RequestInit]
  return { path, init, headers: new Headers(init.headers) }
}

const row = {
  invitationId: 7,
  email: 'hanako@example.test',
  language: 'ja',
  invitedBy: '山田 花子',
  invitedAt: '2026-09-25T12:00:00Z',
  expiresAt: '2026-09-26T12:00:00Z',
  sendResult: 'SENT',
  expired: false,
}

const pageBody = {
  items: [row],
  page: 1,
  size: 20,
  total: 1,
  invitationEnabled: true,
  unavailableReasons: [],
}

async function failure(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise
  } catch (error) {
    return error as ApiError
  }
  throw new Error('expected a failure')
}

describe('invitationApi requests', () => {
  it('sends the four requests with the right method, path and body', async () => {
    fetchMock
      .mockResolvedValueOnce(json(200, pageBody))
      .mockResolvedValueOnce(json(201, row))
      .mockResolvedValueOnce(json(200, row))
      .mockResolvedValueOnce(new Response(null, { status: 204 }))

    await listInvitations(3)
    await createInvitation({ email: ' hanako@example.test ', language: 'en' })
    await resendInvitation(7)
    await cancelInvitation(7)

    expect(call(0).path).toBe('/api/admin/invitations?page=3')
    expect(call(0).init.method).toBe('GET')
    expect(call(1).path).toBe('/api/admin/invitations')
    expect(call(1).init.method).toBe('POST')
    expect(call(1).headers.get('Content-Type')).toBe('application/json')
    expect(JSON.parse(call(1).init.body as string)).toEqual({
      email: ' hanako@example.test ',
      language: 'en',
    })
    expect(call(2).path).toBe('/api/admin/invitations/7/resend')
    expect(call(2).init.method).toBe('POST')
    expect(call(2).init.body).toBeUndefined()
    expect(call(3).path).toBe('/api/admin/invitations/7/cancel')
    expect(call(3).init.method).toBe('POST')
    expect(call(3).init.body).toBeUndefined()
  })

  it('sends only the email and the language even when more fields are given', async () => {
    fetchMock.mockResolvedValueOnce(json(201, row))
    const request = { email: 'a@example.test', language: 'ja', token: 'x' } as const
    await createInvitation(request)
    expect(Object.keys(JSON.parse(call().init.body as string))).toEqual(['email', 'language'])
  })
})

describe('invitationApi success bodies', () => {
  it('keeps only the fixed fields and never the token or the URL (NFR1.1)', async () => {
    const leaky = {
      ...row,
      token: 'leak-check-token-value',
      url: 'http://leak-check.example.test/register#token=x',
    }
    fetchMock
      .mockResolvedValueOnce(json(200, { ...pageBody, items: [leaky], extra: 'x' }))
      .mockResolvedValueOnce(json(201, leaky))
      .mockResolvedValueOnce(json(200, leaky))

    const page = await listInvitations(1)
    const created = await createInvitation({ email: row.email, language: 'ja' })
    const resent = await resendInvitation(7)

    expect(page).toEqual(pageBody)
    for (const value of [page, created, resent]) {
      expect(JSON.stringify(value)).not.toContain('leak-check')
    }
    expect(created).toEqual(row)
    expect(resent).toEqual(row)
  })

  it('treats an unknown send result as FAILED and drops unknown reasons', async () => {
    fetchMock.mockResolvedValueOnce(
      json(200, {
        ...pageBody,
        items: [{ ...row, sendResult: 'QUEUED' }],
        invitationEnabled: false,
        unavailableReasons: ['SMTP_NOT_CONFIGURED', 'DATABASE_DOWN'],
      }),
    )
    const page = await listInvitations(1)
    expect(page.items[0]?.sendResult).toBe('FAILED')
    expect(page.unavailableReasons).toEqual(['SMTP_NOT_CONFIGURED'])
  })

  it('treats an unreadable or malformed 200 or 201 body as a network failure', async () => {
    fetchMock
      .mockResolvedValueOnce(new Response('not json', { status: 200 }))
      .mockResolvedValueOnce(json(200, { ...pageBody, total: 'many' }))
      .mockResolvedValueOnce(json(200, { ...pageBody, items: [{ ...row, invitationId: '7' }] }))
      .mockResolvedValueOnce(json(201, { ...row, language: 'fr' }))
      .mockResolvedValueOnce(json(200, [row]))
      .mockResolvedValueOnce(new Response(null, { status: 200 }))

    await expect(listInvitations(1)).rejects.toEqual({ kind: 'network' })
    await expect(listInvitations(1)).rejects.toEqual({ kind: 'network' })
    await expect(listInvitations(1)).rejects.toEqual({ kind: 'network' })
    await expect(createInvitation({ email: row.email, language: 'ja' })).rejects.toEqual({
      kind: 'network',
    })
    await expect(resendInvitation(7)).rejects.toEqual({ kind: 'network' })
    await expect(resendInvitation(7)).rejects.toEqual({ kind: 'network' })
  })

  it('treats the empty 204 body of a revoke as a success', async () => {
    fetchMock.mockResolvedValueOnce(new Response(null, { status: 204 }))
    await expect(cancelInvitation(7)).resolves.toBeUndefined()
  })
})

describe('invitationApi failures', () => {
  it('passes the kind, status and code of a failure through as they are', async () => {
    fetchMock
      .mockResolvedValueOnce(problem(400, { code: 'VALIDATION_FAILED' }))
      .mockResolvedValueOnce(problem(404, { code: 'INVITATION_NOT_FOUND' }))
      .mockResolvedValueOnce(problem(409, { code: 'INVITATION_EMAIL_REGISTERED' }))
      .mockResolvedValueOnce(problem(503, { code: 'INVITATION_NOT_CONFIGURED' }))
      .mockResolvedValueOnce(problem(500, { code: 'INTERNAL_ERROR' }))
      .mockRejectedValueOnce(new TypeError('Failed to fetch'))

    expect(await failure(listInvitations(0))).toEqual({
      kind: 'response',
      status: 400,
      code: 'VALIDATION_FAILED',
    })
    expect(await failure(resendInvitation(1))).toEqual({
      kind: 'response',
      status: 404,
      code: 'INVITATION_NOT_FOUND',
    })
    expect(await failure(createInvitation({ email: 'a@example.test', language: 'ja' }))).toEqual({
      kind: 'response',
      status: 409,
      code: 'INVITATION_EMAIL_REGISTERED',
    })
    expect(await failure(resendInvitation(1))).toEqual({
      kind: 'response',
      status: 503,
      code: 'INVITATION_NOT_CONFIGURED',
    })
    expect(await failure(cancelInvitation(1))).toEqual({
      kind: 'response',
      status: 500,
      code: 'INTERNAL_ERROR',
    })
    expect(await failure(listInvitations(1))).toEqual({ kind: 'network' })
  })

  it('reads the pending invitation position only when its types are right', async () => {
    const bodies = [
      { code: 'INVITATION_ALREADY_PENDING', invitationId: 42, page: 3 },
      { code: 'INVITATION_ALREADY_PENDING', invitationId: '42', page: 3 },
      { code: 'INVITATION_ALREADY_PENDING', invitationId: 42, page: 0 },
      { code: 'INVITATION_ALREADY_PENDING', invitationId: 42, page: 1.5 },
      { code: 'INVITATION_ALREADY_PENDING', invitationId: 42 },
      { code: 'INVITATION_EMAIL_REGISTERED', invitationId: 42, page: 3 },
    ]
    const results = []
    for (const body of bodies) {
      fetchMock.mockResolvedValueOnce(problem(409, body))
      results.push(
        readPendingProblem(
          await failure(createInvitation({ email: 'a@example.test', language: 'ja' })),
        ),
      )
    }
    expect(results).toEqual([
      { invitationId: 42, page: 3 },
      undefined,
      undefined,
      undefined,
      undefined,
      undefined,
    ])
    expect(readPendingProblem({ kind: 'network' })).toBeUndefined()
  })

  it('reads only the known unavailable reasons of a 503', async () => {
    const bodies = [
      {
        code: 'INVITATION_NOT_CONFIGURED',
        unavailableReasons: ['BASE_URL_NOT_CONFIGURED', 'SMTP_NOT_CONFIGURED'],
      },
      { code: 'INVITATION_NOT_CONFIGURED', unavailableReasons: ['SMTP_NOT_CONFIGURED'] },
      { code: 'INVITATION_NOT_CONFIGURED', unavailableReasons: ['X', 'SMTP_NOT_CONFIGURED'] },
      { code: 'INVITATION_NOT_CONFIGURED', unavailableReasons: 'SMTP_NOT_CONFIGURED' },
      { code: 'SERVICE_UNAVAILABLE', unavailableReasons: ['SMTP_NOT_CONFIGURED'] },
    ]
    const results = []
    for (const body of bodies) {
      fetchMock.mockResolvedValueOnce(problem(503, body))
      results.push(readUnavailableReasons(await failure(resendInvitation(1))))
    }
    expect(results).toEqual([
      ['BASE_URL_NOT_CONFIGURED', 'SMTP_NOT_CONFIGURED'],
      ['SMTP_NOT_CONFIGURED'],
      ['SMTP_NOT_CONFIGURED'],
      [],
      [],
    ])
  })
})
