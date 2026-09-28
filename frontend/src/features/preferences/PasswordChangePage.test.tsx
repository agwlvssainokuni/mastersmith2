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
// パスワードの変更の画面と usePasswordChangeForm のテスト（frontend-components.md の 7節、functional-spec.md の W9〜W12、
// AC5.1.3・AC5.1.6〜AC5.1.8、NFR6.4・NFR6.5・NFR7.2・NFR9.1・NFR9.3・NFR9.7）。fetch を偽のサーバーに差し替え、
// ApiClient とログイン状態（auth の本物の状態）は本物で動かす。共用の確かめの関数も差し替えずに通す。
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { getAuthSnapshot, resetAuthSession } from '../auth/authSession'
import { ME_PASSWORD_PATH, ME_PREFERENCES_PATH } from './preferencesApi'
import {
  deferred,
  DETAIL_MARKER,
  installFakeServer,
  NAMED_PREFERENCES,
  noContent,
  problemResponse,
  REFRESH_PATH,
  sessionResponse,
  validationFailed,
  type FakeAnswer,
  type FakeServer,
} from './testing/fixtures'
import { renderPreferencesApp } from './testing/renderPreferences'

const CURRENT = 'current-secret-value'
const NEW = 'new-secret-value-12'
const CONSOLE_METHODS = ['log', 'info', 'warn', 'error', 'debug'] as const

let consoleSpies: ReturnType<typeof vi.spyOn>[] = []

beforeEach(() => {
  resetDisplayTestState()
  consoleSpies = CONSOLE_METHODS.map((method) =>
    vi.spyOn(console, method).mockImplementation(() => undefined),
  )
})

afterEach(() => {
  vi.unstubAllGlobals()
  resetAuthSession()
  resetDisplayTestState()
})

function consoleCalls(): unknown[][] {
  return consoleSpies
    .flatMap((spy) => spy.mock.calls)
    .filter((args) => !String(args[0]).startsWith('Not implemented: HTMLCanvasElement'))
}

function storedValues(): string {
  const values: string[] = []
  for (const storage of [localStorage, sessionStorage]) {
    for (let i = 0; i < storage.length; i += 1) {
      const key = storage.key(i)
      if (key !== null) {
        values.push(`${key}=${storage.getItem(key) ?? ''}`)
      }
    }
  }
  return values.join('\n')
}

/** ログインした利用者でパスワードの変更の画面を開く。 */
async function start(post: FakeAnswer[] = [noContent()]): Promise<FakeServer> {
  const server = installFakeServer({
    [REFRESH_PATH]: [sessionResponse(NAMED_PREFERENCES)],
    [`POST ${ME_PASSWORD_PATH}`]: post,
  })
  renderPreferencesApp({ route: '/me/password' })
  await screen.findByTestId('preferences-password-form')
  return server
}

const current = () => screen.getByTestId('preferences-password-current-input')
const next = () => screen.getByTestId('preferences-password-new-input')
const confirm = () => screen.getByTestId('preferences-password-confirm-input')
const submit = () => screen.getByTestId('preferences-password-submit-button')

async function fill(
  user: ReturnType<typeof userEvent.setup>,
  values: [string, string, string],
): Promise<void> {
  for (const [input, value] of [
    [current(), values[0]],
    [next(), values[1]],
    [confirm(), values[2]],
  ] as const) {
    await user.clear(input)
    if (value !== '') {
      await user.click(input)
      await user.paste(value)
    }
  }
}

const refreshes = (server: FakeServer) => server.requestsTo(REFRESH_PATH, 'POST').length
const posts = (server: FakeServer) => server.requestsTo(ME_PASSWORD_PATH, 'POST')

describe('PasswordChangePage', () => {
  it('opens without calling the API and shows three empty fields (NFR6.4)', async () => {
    const server = await start()
    expect(screen.getByRole('heading', { level: 1, name: 'パスワードの変更' })).toBeInTheDocument()
    expect(current()).toHaveValue('')
    expect(next()).toHaveValue('')
    expect(confirm()).toHaveValue('')
    expect(server.requests.map((r) => r.path)).toEqual([REFRESH_PATH])
    expect(server.requestsTo(ME_PREFERENCES_PATH)).toHaveLength(0)
  })

  it.each([
    [['', NEW, NEW], 'current', '今のパスワードを入力してください'],
    [[CURRENT, '', ''], 'new', '新しいパスワードを入力してください'],
    [[CURRENT, 'a'.repeat(11), 'a'.repeat(11)], 'new', '12 文字以上で入力してください'],
    [
      [CURRENT, `${'あ'.repeat(24)}a`, `${'あ'.repeat(24)}a`],
      'new',
      '長すぎます（半角で 72 文字、全角でおよそ 24 文字まで）',
    ],
    [[CURRENT, '😀'.repeat(11), '😀'.repeat(11)], 'new', '12 文字以上で入力してください'],
    [[CURRENT, NEW, `${NEW}x`], 'confirm', '新しいパスワードと同じ値を入力してください'],
  ] as [[string, string, string], string, string][])(
    'stops %j on the screen and focuses the %s field (AC5.1.3)',
    async (values, field, message) => {
      const user = userEvent.setup()
      const server = await start()
      await fill(user, values)
      await user.click(submit())
      expect(await screen.findByText(message)).toBeInTheDocument()
      const input = screen.getByTestId(`preferences-password-${field}-input`)
      expect(input).toHaveAttribute('aria-invalid', 'true')
      await waitFor(() => expect(input).toHaveFocus())
      expect(posts(server)).toHaveLength(0)
      expect(next()).toHaveValue(values[1])
    },
  )

  it('sends 12 code points, 72 bytes and 12 emoji, and does not check the current password rules', async () => {
    const user = userEvent.setup()
    const server = await start()
    for (const value of ['a'.repeat(12), 'あ'.repeat(24), '😀'.repeat(12)]) {
      await fill(user, ['short', value, value])
      await user.click(submit())
      await screen.findAllByText('パスワードを変更しました')
    }
    expect(posts(server).map((r) => r.body)).toEqual([
      {
        currentPassword: 'short',
        newPassword: 'a'.repeat(12),
        newPasswordConfirmation: 'a'.repeat(12),
      },
      {
        currentPassword: 'short',
        newPassword: 'あ'.repeat(24),
        newPasswordConfirmation: 'あ'.repeat(24),
      },
      {
        currentPassword: 'short',
        newPassword: '😀'.repeat(12),
        newPasswordConfirmation: '😀'.repeat(12),
      },
    ])
  })

  it('empties the fields, shows a toast and stays logged in on success (AC5.1.8)', async () => {
    const user = userEvent.setup()
    const server = await start()
    await fill(user, [CURRENT, NEW, NEW])
    await user.click(submit())
    const toast = within(screen.getByTestId('toast-container'))
    expect(await toast.findByText('パスワードを変更しました')).toBeInTheDocument()
    expect(current()).toHaveValue('')
    expect(next()).toHaveValue('')
    expect(confirm()).toHaveValue('')
    expect(submit()).toHaveFocus()
    expect(getAuthSnapshot().status).toBe('LoggedIn')
    expect(screen.queryByTestId('login-layout')).not.toBeInTheDocument()
    expect(screen.getByTestId('location')).toHaveTextContent('/me/password')
    expect(refreshes(server)).toBe(1)
  })

  it('links the current password mismatch to its field and stays logged in (AC5.1.6・AC5.1.7・NFR9.3)', async () => {
    const user = userEvent.setup()
    const server = await start([problemResponse(400, 'PASSWORD_CURRENT_MISMATCH')])
    await fill(user, [CURRENT, NEW, NEW])
    await user.click(submit())
    expect(await screen.findByText('今のパスワードが正しくありません')).toBeInTheDocument()
    expect(current()).toHaveAttribute('aria-invalid', 'true')
    expect(current()).toHaveAccessibleDescription('今のパスワードが正しくありません')
    await waitFor(() => expect(current()).toHaveFocus())
    expect(current()).toHaveValue(CURRENT)
    expect(next()).toHaveValue(NEW)
    expect(confirm()).toHaveValue(NEW)
    expect(refreshes(server)).toBe(1)
    expect(getAuthSnapshot().status).toBe('LoggedIn')
    expect(screen.queryByTestId('login-layout')).not.toBeInTheDocument()
    expect(document.body).not.toHaveTextContent(DETAIL_MARKER)
  })

  it('shows server field errors, or the form notice when none can be matched', async () => {
    const user = userEvent.setup()
    await start([
      validationFailed([
        { field: 'newPassword', reason: 'TOO_SHORT' },
        { field: 'newPasswordConfirmation', reason: 'MISMATCH' },
      ]),
      validationFailed([{ field: 'displayName', reason: 'REQUIRED' }]),
    ])
    await fill(user, [CURRENT, NEW, NEW])
    await user.click(submit())
    expect(await screen.findByText('12 文字以上で入力してください')).toBeInTheDocument()
    expect(screen.getByText('新しいパスワードと同じ値を入力してください')).toBeInTheDocument()
    await waitFor(() => expect(next()).toHaveFocus())
    await user.click(submit())
    const alert = within(await screen.findByTestId('preferences-password-alert')).getByRole('alert')
    expect(alert).toHaveTextContent('入力を確かめてください。')
  })

  it.each([
    ['500', problemResponse(500, 'INTERNAL_ERROR')],
    ['a network failure', new Error('offline')],
  ] as [string, FakeAnswer][])(
    'shows the notice and keeps the values for %s',
    async (_label, answer) => {
      const user = userEvent.setup()
      await start([answer])
      await fill(user, [CURRENT, NEW, NEW])
      await user.click(submit())
      const alert = within(await screen.findByTestId('preferences-password-alert')).getByRole(
        'alert',
      )
      expect(alert).toHaveTextContent(
        'パスワードを変更できませんでした。しばらくしてから、もう一度お試しください。',
      )
      expect(current()).toHaveValue(CURRENT)
      expect(next()).toHaveValue(NEW)
      expect(submit()).toHaveFocus()
      expect(document.body).not.toHaveTextContent(DETAIL_MARKER)
    },
  )

  it('sends POST once while sending and shows the sending state (NFR6.5)', async () => {
    const user = userEvent.setup()
    const answer = deferred()
    const server = await start([answer.promise])
    await fill(user, [CURRENT, NEW, NEW])
    await user.click(submit())
    await user.click(submit())
    await user.type(confirm(), '{Enter}')
    expect(posts(server)).toHaveLength(1)
    expect(submit()).toHaveTextContent('変更しています')
    expect(submit()).toHaveAttribute('aria-disabled', 'true')
    for (const input of [current(), next(), confirm()]) {
      expect(input).toHaveAttribute('readonly')
    }
    expect(confirm()).toHaveFocus()
    await user.type(next(), 'x')
    expect(next()).toHaveValue(NEW)
    answer.resolve(noContent())
    await screen.findAllByText('パスワードを変更しました')
  })

  it('drops the answer when the screen is left while sending', async () => {
    const user = userEvent.setup()
    const answer = deferred()
    await start([answer.promise])
    await fill(user, [CURRENT, NEW, NEW])
    await user.click(submit())
    await user.click(await screen.findByRole('button', { name: /検査 太郎/ }))
    await user.click(await screen.findByRole('menuitem', { name: 'プリファレンス' }))
    await screen.findByTestId('preferences-page')
    await act(async () => {
      answer.resolve(noContent())
      await answer.promise
    })
    expect(screen.getByTestId('toast-container')).toBeEmptyDOMElement()
    expect(consoleCalls()).toEqual([])
  })

  it('keeps the passwords out of the browser storage, the URL and the console (NFR9.1)', async () => {
    const user = userEvent.setup()
    await start([problemResponse(500), noContent()])
    await fill(user, [CURRENT, NEW, NEW])
    const check = () => {
      const stored = storedValues()
      expect(stored).not.toContain(CURRENT)
      expect(stored).not.toContain(NEW)
      expect(window.location.search).toBe('')
      expect(window.location.hash).toBe('')
    }
    check()
    await user.click(submit())
    await screen.findByTestId('preferences-password-alert')
    check()
    await user.click(submit())
    await screen.findAllByText('パスワードを変更しました')
    check()
    expect(consoleCalls()).toEqual([])
  })

  it('has no accessibility violations', async () => {
    await start()
    expect(await axe(document.body)).toHaveNoViolations()
  })
})
