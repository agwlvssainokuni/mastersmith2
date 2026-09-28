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
// 自分の設定の API の関数のテスト（frontend-components.md の 5節、security-design.md の 3.1・4節、NFR6.4・NFR9.1〜NFR9.3）。
// fetch を差し替え、ApiClient は本物で動かす（テストごとに resetApiClient で戻す）。
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import {
  registerAuthHandlers,
  registerLanguageResolver,
  resetApiClient,
} from '../../shared/api-client/apiClient'
import type { ApiError } from '../../shared/api-client/apiError'
import {
  changePassword,
  getPreferences,
  ME_PASSWORD_PATH,
  ME_PREFERENCES_PATH,
  PreferencesShapeError,
  savePreferences,
  type Preferences,
} from './preferencesApi'

const PREFS: Preferences = {
  displayName: '山田 花子',
  language: 'ja',
  theme: 'system',
  fontSize: 'md',
}
const REFRESH_PATH = '/api/auth/session/refresh'

function json(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function problem(status: number, code: string, extra: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({ status, code, detail: 'server-detail-marker', ...extra }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

interface Sent {
  path: string
  init: RequestInit
}

function stubFetch(...answers: (Response | Error)[]): Sent[] {
  const sent: Sent[] = []
  const queue = [...answers]
  vi.stubGlobal(
    'fetch',
    vi.fn((input: RequestInfo | URL, init: RequestInit = {}) => {
      sent.push({ path: String(input), init })
      const next = queue.shift()
      if (next === undefined || next instanceof Error) {
        return Promise.reject(next ?? new Error('no answer'))
      }
      return Promise.resolve(next)
    }),
  )
  return sent
}

async function rejection(promise: Promise<unknown>): Promise<unknown> {
  try {
    await promise
  } catch (error) {
    return error
  }
  throw new Error('resolved')
}

const refresh = vi.fn<() => Promise<boolean>>()
const onUnauthenticated = vi.fn()

beforeEach(() => {
  resetApiClient()
  refresh.mockReset().mockResolvedValue(true)
  onUnauthenticated.mockReset()
  registerAuthHandlers({ getAccessToken: () => 'access-token-marker', refresh, onUnauthenticated })
  registerLanguageResolver(() => 'ja')
})

afterEach(() => {
  resetApiClient()
  vi.unstubAllGlobals()
})

describe('preferencesApi', () => {
  it('sends GET, PUT with only the four values and POST with only the three passwords', async () => {
    const sent = stubFetch(json(200, PREFS), json(200, PREFS), new Response(null, { status: 204 }))
    await getPreferences()
    await savePreferences({ ...PREFS, email: 'x@example.test' } as Preferences)
    await changePassword({
      currentPassword: 'old-secret',
      newPassword: 'new-secret-12',
      newPasswordConfirmation: 'new-secret-12',
      extra: 'x',
    } as never)
    expect(sent.map((s) => [s.init.method, s.path])).toEqual([
      ['GET', ME_PREFERENCES_PATH],
      ['PUT', ME_PREFERENCES_PATH],
      ['POST', ME_PASSWORD_PATH],
    ])
    expect(JSON.parse(String(sent[1]?.init.body))).toEqual(PREFS)
    expect(JSON.parse(String(sent[2]?.init.body))).toEqual({
      currentPassword: 'old-secret',
      newPassword: 'new-secret-12',
      newPasswordConfirmation: 'new-secret-12',
    })
    for (const request of sent) {
      const headers = new Headers(request.init.headers)
      // ApiClient が付ける値だけ（呼び出し側で別の値を付けていない）
      expect(headers.get('Authorization')).toBe('Bearer access-token-marker')
      expect(headers.get('Accept-Language')).toBe('ja')
    }
    expect(new Headers(sent[1]?.init.headers).get('Content-Type')).toBe('application/json')
  })

  it('returns only the four values of a successful answer', async () => {
    stubFetch(json(200, { ...PREFS, email: 'leak@example.test', admin: true }))
    await expect(getPreferences()).resolves.toEqual(PREFS)
    stubFetch(json(200, { ...PREFS, displayName: '佐藤 花子', theme: 'dark', extra: 1 }))
    await expect(savePreferences(PREFS)).resolves.toEqual({
      ...PREFS,
      displayName: '佐藤 花子',
      theme: 'dark',
    })
  })

  it('throws a shape error for values outside the allowed ones or a broken body', async () => {
    const broken = [
      { ...PREFS, language: 'fr' },
      { ...PREFS, theme: 'Dark' },
      { ...PREFS, fontSize: 'xl' },
      { ...PREFS, displayName: 42 },
      { language: 'ja', theme: 'light', fontSize: 'md' },
      [PREFS],
      null,
    ]
    for (const body of broken) {
      stubFetch(json(200, body))
      expect(await rejection(getPreferences())).toBeInstanceOf(PreferencesShapeError)
    }
    stubFetch(new Response('not json', { status: 200 }))
    expect(await rejection(savePreferences(PREFS))).toBeInstanceOf(PreferencesShapeError)
  })

  it('treats an empty 204 as a successful password change', async () => {
    stubFetch(new Response(null, { status: 204 }))
    await expect(
      changePassword({
        currentPassword: 'a',
        newPassword: 'b',
        newPasswordConfirmation: 'b',
      }),
    ).resolves.toBeUndefined()
  })

  it('passes 400 errors with the code and the problem as they are', async () => {
    const fieldErrors = [{ field: 'displayName', reason: 'TOO_LONG' }]
    stubFetch(problem(400, 'VALIDATION_FAILED', { fieldErrors }))
    const validation = (await rejection(savePreferences(PREFS))) as ApiError
    expect(validation).toMatchObject({ kind: 'response', status: 400, code: 'VALIDATION_FAILED' })
    expect(validation.kind === 'response' && validation.problem?.fieldErrors).toEqual(fieldErrors)

    stubFetch(problem(400, 'PASSWORD_CURRENT_MISMATCH'))
    const mismatch = await rejection(
      changePassword({ currentPassword: 'x', newPassword: 'y', newPasswordConfirmation: 'y' }),
    )
    expect(mismatch).toMatchObject({ status: 400, code: 'PASSWORD_CURRENT_MISMATCH' })
  })

  it('passes 500 and network failures as ApiError', async () => {
    stubFetch(problem(500, 'INTERNAL_ERROR'))
    expect(await rejection(getPreferences())).toMatchObject({ kind: 'response', status: 500 })
    stubFetch(new Error('offline'))
    expect(await rejection(getPreferences())).toEqual({ kind: 'network' })
  })

  it('refreshes once on 401 AUTHENTICATION_REQUIRED and sends the request again', async () => {
    const sent = stubFetch(problem(401, 'AUTHENTICATION_REQUIRED'), json(200, PREFS))
    await expect(getPreferences()).resolves.toEqual(PREFS)
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(sent.map((s) => s.path)).toEqual([ME_PREFERENCES_PATH, ME_PREFERENCES_PATH])
    expect(onUnauthenticated).not.toHaveBeenCalled()
  })

  it('does not refresh the tokens for 400 PASSWORD_CURRENT_MISMATCH (NFR9.3)', async () => {
    const sent = stubFetch(problem(400, 'PASSWORD_CURRENT_MISMATCH'))
    await rejection(
      changePassword({ currentPassword: 'x', newPassword: 'y', newPasswordConfirmation: 'y' }),
    )
    expect(refresh).not.toHaveBeenCalled()
    expect(onUnauthenticated).not.toHaveBeenCalled()
    expect(sent.map((s) => s.path)).toEqual([ME_PASSWORD_PATH])
    expect(sent.some((s) => s.path === REFRESH_PATH)).toBe(false)
  })
})
