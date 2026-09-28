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
// 登録の完了の画面のテスト（useRegistration を通す。frontend-components.md の 8節、unit-test-instructions.md の 3節）。
// API は fetch の差し替え、U4 の口（表示の設定・受け渡し）とログイン状態（AuthUi の authSession）は本物で動かす。
// どの流れでも console が呼ばれないこと（NFR9.3）を、各テストの後に確かめる。
import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import type { ReactElement } from 'react'
import { Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi, type MockInstance } from 'vitest'
import { axe } from 'vitest-axe'
import { DISPLAY_SETTINGS_KEY } from '../../app/display-settings/browserStorage'
import { getDisplaySettingsSnapshot } from '../../app/display-settings/displaySettingsStore'
import { takeLoginHandoff } from '../../app/login-handoff/loginHandoff'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { resetAuthSession } from '../auth/authSession'
import { LoginForm } from '../auth/LoginForm'
import { loginStateProvider } from '../auth/loginStateProvider'
import { registration as authRegistration } from '../auth/registration'
import { RegistrationPage } from './RegistrationPage'
import {
  DETAIL_MARKER,
  deferred,
  installFakeServer,
  jsonResponse,
  noContent,
  problemResponse,
  TEST_TOKEN,
  VERIFIED_EN,
  VERIFIED_JA,
  type FakeServer,
} from './testing/fixtures'
import { renderRegistration } from './testing/renderRegistration'

const VERIFY = '/api/registration/verify'
const COMPLETE = '/api/registration/complete'
const LOGOUT = '/api/auth/session/logout'
const REFRESH = '/api/auth/session/refresh'
const LOGIN = '/api/auth/login'
const EMAIL = VERIFIED_JA.email
const PASSWORD = 'secret-pass-1234'

const CONSOLE_METHODS = ['log', 'info', 'warn', 'error', 'debug'] as const
let consoleSpies: MockInstance[] = []

/** 受け渡しの値の項目の名前を示す（ログインの画面の代わりに置く） */
function HandoffProbe() {
  const handoff = takeLoginHandoff()
  return <p data-testid="handoff-keys">{handoff === null ? '' : Object.keys(handoff).join(',')}</p>
}

/** 登録の完了・ログイン・ホームの3つの画面を持つ描画 */
function Screens({ loginElement }: { loginElement: ReactElement }) {
  return (
    <Routes>
      <Route path="/register" element={<RegistrationPage />} />
      <Route path="/login" element={loginElement} />
      <Route path="/" element={<p data-testid="home-probe">home</p>} />
      <Route path="/elsewhere" element={<p data-testid="elsewhere-probe">elsewhere</p>} />
    </Routes>
  )
}

interface PageOptions {
  route?: string
  languages?: readonly string[]
  loggedIn?: boolean
  loginElement?: ReactElement
}

function renderPage(options: PageOptions = {}) {
  return renderRegistration(<Screens loginElement={options.loginElement ?? <LoginForm />} />, {
    route: options.route,
    languages: options.languages,
    provider: options.loggedIn ? loginStateProvider : undefined,
    registrations: [authRegistration],
  })
}

function storedDisplaySettings(): Record<string, unknown> {
  return JSON.parse(localStorage.getItem(DISPLAY_SETTINGS_KEY) ?? '{}') as Record<string, unknown>
}

function storeDisplaySettings(value: Record<string, string>): void {
  localStorage.setItem(DISPLAY_SETTINGS_KEY, JSON.stringify(value))
}

/** ブラウザの保存のすべての鍵と値 */
function allStoredValues(): string {
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

/** ブラウザの保存と画面の文字に、トークン・パスワード・メールアドレスが無いこと */
function expectNoSecretsStored(): void {
  const stored = allStoredValues()
  expect(stored).not.toContain(TEST_TOKEN)
  expect(stored).not.toContain(PASSWORD)
  expect(stored).not.toContain(EMAIL)
  const page = screen.queryByTestId('registration-page')?.textContent ?? ''
  expect(page).not.toContain(TEST_TOKEN)
  expect(page).not.toContain(DETAIL_MARKER)
}

function html(): { lang: string; theme: string | null; fontSize: string | null } {
  const element = document.documentElement
  return {
    lang: element.lang,
    theme: element.getAttribute('data-theme'),
    fontSize: element.getAttribute('data-font-size'),
  }
}

async function findForm(): Promise<HTMLElement> {
  return screen.findByTestId('registration-form')
}

/** 欄の値を入れ直す（貼り付けで入れる）。 */
async function fill(
  user: ReturnType<typeof userEvent.setup>,
  testId: string,
  value: string,
): Promise<void> {
  const field = screen.getByTestId(testId)
  await user.clear(field)
  if (value.length > 0) {
    await user.click(field)
    await user.paste(value)
  }
}

async function fillPasswords(user: ReturnType<typeof userEvent.setup>, password = PASSWORD) {
  await fill(user, 'registration-password-input', password)
  await fill(user, 'registration-password-confirmation-input', password)
}

let server: FakeServer

beforeEach(() => {
  resetDisplayTestState()
  resetAuthSession()
  server = installFakeServer({ [VERIFY]: [jsonResponse(200, VERIFIED_JA)] })
  consoleSpies = CONSOLE_METHODS.map((method) =>
    vi.spyOn(console, method).mockImplementation(() => undefined),
  )
})

afterEach(() => {
  // jsdom が axe の色の検査で出す「Not implemented」は、この画面のコードの出力ではないため除く。
  const calls = consoleSpies
    .flatMap((spy) => spy.mock.calls)
    .filter((args) => !String(args[0]).startsWith('Not implemented'))
  const text = calls.map((args) => args.map(String).join(' ')).join('\n')
  const secrets = [TEST_TOKEN, PASSWORD, EMAIL].filter((secret) => text.includes(secret))
  expect(calls, `console was called (secrets in the arguments: ${secrets.length})`).toHaveLength(0)
  vi.unstubAllGlobals()
  resetAuthSession()
  resetDisplayTestState()
})

describe('RegistrationPage opening', () => {
  it('shows the form in the invitation language with the initial values and the stored look (AC3.2.1)', async () => {
    storeDisplaySettings({ theme: 'dark', fontSize: 'lg' })
    renderPage({ languages: ['en-US'] })
    await findForm()

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('登録を完了する')
    expect(html()).toEqual({ lang: 'ja', theme: 'dark', fontSize: 'lg' })
    expect(screen.getByTestId('registration-display-name-input')).toHaveValue(EMAIL)
    expect(screen.getByTestId('registration-email-input')).toHaveValue(EMAIL)
    expect(screen.getByRole('radio', { name: '日本語' })).toBeChecked()
    expect(screen.getByRole('radio', { name: 'OS に合わせる' })).toBeChecked()
    expect(screen.getByRole('radio', { name: '標準' })).toBeChecked()
    expect(screen.getByText('MasterSmith')).toBeInTheDocument()
    // アプリシェル（サイドバー・ユーザーメニュー）の外に出る
    expect(screen.queryByRole('navigation')).not.toBeInTheDocument()
    expect(storedDisplaySettings()).toEqual({ theme: 'dark', fontSize: 'lg' })
  })

  it('shows the checking status in the screen language before the invitation is known', async () => {
    const pending = deferred()
    installFakeServer({ [VERIFY]: [pending.promise] })
    renderPage({ languages: ['en-US'] })

    expect(await screen.findByRole('status')).toHaveTextContent('Checking your link')
    expect(html().lang).toBe('en')
    await act(async () => {
      pending.resolve(jsonResponse(200, VERIFIED_EN))
      await pending.promise
    })
    expect(await findForm()).toBeInTheDocument()
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(
      'Complete your registration',
    )
  })
})

describe('RegistrationPage token', () => {
  it('sends the token only in the body and removes the fragment by replacing the entry (NFR1.1)', async () => {
    renderPage({ route: `/register?from=mail#token=${TEST_TOKEN}` })
    await findForm()

    const [request] = server.requestsTo(VERIFY)
    expect(request.method).toBe('POST')
    expect(request.body).toEqual({ token: TEST_TOKEN })
    expect(request.path).not.toContain(TEST_TOKEN)
    expect(screen.getByTestId('registration-location-hash').textContent).toBe('')
    expect(screen.getByTestId('registration-location-search')).toHaveTextContent('?from=mail')
    expect(screen.getByTestId('registration-location-type')).toHaveTextContent('REPLACE')
    expect(screen.getByTestId('location')).toHaveTextContent('/register')
    expectNoSecretsStored()
  })

  it.each(['/register', '/register#token=', '/register#other=1'])(
    'shows the unavailable link without any request for %s (D1)',
    async (route) => {
      renderPage({ route })
      expect(await screen.findByTestId('registration-unavailable')).toBeInTheDocument()
      expect(server.requests).toHaveLength(0)
      expect(screen.queryByTestId('registration-form')).not.toBeInTheDocument()
    },
  )

  it('shows the unavailable link after a reload, because only the address without the fragment remains', async () => {
    const first = renderPage()
    await findForm()
    first.unmount()
    // 読み込み直すと、置き換えた後のアドレス（フラグメントなし）で開き直す
    renderPage({ route: '/register' })
    expect(await screen.findByTestId('registration-unavailable')).toBeInTheDocument()
    expect(server.requestsTo(VERIFY)).toHaveLength(1)
  })
})

describe('RegistrationPage verification failures', () => {
  it('shows the same unavailable display for 404 whatever the detail says (NFR3.1)', async () => {
    const expired = installFakeServer({
      [VERIFY]: [problemResponse(404, 'REGISTRATION_LINK_INVALID', 'expired-marker')],
    })
    const first = renderPage()
    const firstText = (await screen.findByTestId('registration-unavailable')).textContent
    first.unmount()
    installFakeServer({
      [VERIFY]: [problemResponse(404, 'REGISTRATION_LINK_INVALID', 'used-marker')],
    })
    renderPage()
    const secondText = (await screen.findByTestId('registration-unavailable')).textContent
    expect(secondText).toBe(firstText)
    expect(secondText).not.toContain('marker')
    expect(screen.getByRole('link', { name: 'ログインの画面へ' })).toHaveAttribute('href', '/login')
    expect(screen.queryByTestId('registration-form')).not.toBeInTheDocument()
    expect(expired.requests).toHaveLength(1)
  })

  it.each<[string, Response | Error]>([
    ['500', problemResponse(500, 'INTERNAL_ERROR')],
    ['a network failure', new TypeError('Failed to fetch')],
    ['404 with another code', problemResponse(404, 'NOT_FOUND')],
  ])(
    'shows the load failure for %s and verifies again with the same token (D5)',
    async (_label, failure) => {
      const user = userEvent.setup()
      server = installFakeServer({ [VERIFY]: [failure, jsonResponse(200, VERIFIED_JA)] })
      renderPage()

      expect(await screen.findByRole('alert')).toHaveTextContent('読み込めませんでした。')
      expect(screen.getByTestId('registration-page').textContent).not.toContain(DETAIL_MARKER)
      await user.click(screen.getByRole('button', { name: 'もう一度読み込む' }))
      expect(await findForm()).toBeInTheDocument()
      const requests = server.requestsTo(VERIFY)
      expect(requests).toHaveLength(2)
      expect(requests[1].body).toEqual({ token: TEST_TOKEN })
    },
  )
})

describe('RegistrationPage while logged in', () => {
  function answerLoggedIn(): void {
    server = installFakeServer({
      [VERIFY]: [jsonResponse(200, VERIFIED_JA)],
      [REFRESH]: [
        jsonResponse(200, {
          accessToken: 'access-token-value',
          expiresAt: '2026-09-28T00:00:00Z',
          user: {
            email: 'admin@example.test',
            admin: true,
            displayName: '管理 太郎',
            language: 'ja',
            theme: 'light',
            fontSize: 'md',
          },
        }),
      ],
      [LOGOUT]: [noContent()],
    })
  }

  it('shows the notice without verifying, and verifies once after logging out (Q1 A)', async () => {
    const user = userEvent.setup()
    answerLoggedIn()
    renderPage({ loggedIn: true })

    expect(await screen.findByTestId('registration-logged-in-notice')).toBeInTheDocument()
    // ログイン中でも、描画の確定の後にフラグメントを消す（W2 の2）
    await waitFor(() =>
      expect(screen.getByTestId('registration-location-hash').textContent).toBe(''),
    )
    expect(server.requestsTo(VERIFY)).toHaveLength(0)

    const button = screen.getByRole('button', { name: 'ログアウトして続ける' })
    await user.click(button)
    await user.click(button)
    expect(await findForm()).toBeInTheDocument()
    expect(server.requestsTo(LOGOUT)).toHaveLength(1)
    const verifies = server.requestsTo(VERIFY)
    expect(verifies).toHaveLength(1)
    expect(verifies[0].headers.has('Authorization')).toBe(false)
    expect(verifies[0].body).toEqual({ token: TEST_TOKEN })
  })

  it('goes back to home without verifying', async () => {
    const user = userEvent.setup()
    answerLoggedIn()
    renderPage({ loggedIn: true })

    await user.click(await screen.findByRole('button', { name: 'ホームへ戻る' }))
    expect(await screen.findByTestId('home-probe')).toBeInTheDocument()
    expect(server.requestsTo(VERIFY)).toHaveLength(0)
    expect(server.requestsTo(LOGOUT)).toHaveLength(0)
  })
})

describe('RegistrationPage choices', () => {
  it('applies only the chosen axis at the time of choosing and keeps the stored values (D7)', async () => {
    const user = userEvent.setup()
    storeDisplaySettings({ theme: 'light', fontSize: 'sm' })
    renderPage()
    await findForm()
    expect(html()).toMatchObject({ theme: null, fontSize: 'sm' })

    const dark = screen.getByRole('radio', { name: 'ダーク' })
    await user.click(dark)
    expect(html()).toMatchObject({ theme: 'dark', fontSize: 'sm' })
    expect(dark).toHaveFocus()
    const large = screen.getByRole('radio', { name: '大' })
    await user.click(large)
    expect(html()).toMatchObject({ theme: 'dark', fontSize: 'lg' })
    expect(large).toHaveFocus()
    expect(storedDisplaySettings()).toEqual({ theme: 'light', fontSize: 'sm' })
  })

  it('switches the texts, <html lang>, shown errors and Accept-Language to English (CR1, NFR8.2)', async () => {
    const user = userEvent.setup()
    renderPage()
    await findForm()
    await fillPasswords(user, 'a'.repeat(11))
    await user.click(screen.getByRole('button', { name: '登録を完了する' }))
    expect(screen.getByText('パスワードは 12 文字以上で入力してください')).toBeInTheDocument()

    const english = screen.getByRole('radio', { name: 'English' })
    await user.click(english)
    expect(html().lang).toBe('en')
    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent(
      'Complete your registration',
    )
    expect(screen.getByText('Enter a password of at least 12 characters')).toBeInTheDocument()
    expect(english).toHaveFocus()
    expect(screen.getByText('日本語')).toHaveAttribute('lang', 'ja')

    server.answer(COMPLETE, noContent())
    await fillPasswords(user)
    await user.click(screen.getByRole('button', { name: 'Complete registration' }))
    await screen.findByTestId('login-form')
    const [complete] = server.requestsTo(COMPLETE)
    expect(complete.headers.get('Accept-Language')).toBe('en')
    expect(complete.body).toMatchObject({ language: 'en' })
  })
})

describe('RegistrationPage validation on the screen', () => {
  it.each<[string, string, string, string, string]>([
    [
      '11 code points',
      EMAIL,
      'a'.repeat(11),
      'a'.repeat(11),
      'パスワードは 12 文字以上で入力してください',
    ],
    [
      '73 bytes',
      EMAIL,
      `${'あ'.repeat(24)}a`,
      `${'あ'.repeat(24)}a`,
      '長すぎます（半角で 72 文字、全角でおよそ 24 文字まで）',
    ],
    [
      '11 emoji',
      EMAIL,
      '😀'.repeat(11),
      '😀'.repeat(11),
      'パスワードは 12 文字以上で入力してください',
    ],
    ['an empty password', EMAIL, '', '', 'パスワードを入力してください'],
    ['a different confirmation', EMAIL, PASSWORD, `${PASSWORD}x`, 'パスワードが一致しません'],
    [
      'a name of 255 code points',
      'あ'.repeat(255),
      PASSWORD,
      PASSWORD,
      '氏名は 254 文字以内で入力してください',
    ],
    ['a name of spaces only', ' 　\t', PASSWORD, PASSWORD, '氏名を入力してください'],
  ])(
    'does not send for %s, shows the error and keeps the values',
    async (_label, name, password, confirmation, message) => {
      const user = userEvent.setup()
      renderPage()
      await findForm()
      await fill(user, 'registration-display-name-input', name)
      await fill(user, 'registration-password-input', password)
      await fill(user, 'registration-password-confirmation-input', confirmation)
      await user.click(screen.getByRole('button', { name: '登録を完了する' }))

      const error = screen.getByText(message)
      expect(error).toBeInTheDocument()
      expect(server.requestsTo(COMPLETE)).toHaveLength(0)
      expect(screen.queryByTestId('registration-failure-alert')).not.toBeInTheDocument()
      const focused = document.activeElement as HTMLElement
      expect(focused).toHaveAttribute('aria-invalid', 'true')
      expect(screen.getByTestId('registration-password-input')).toHaveValue(password)
      expect(screen.getByTestId('registration-display-name-input')).toHaveValue(name)
    },
  )

  it('accepts the boundary values of 254 code points, 12 code points, 72 bytes and 12 emoji', async () => {
    const user = userEvent.setup()
    server.answer(COMPLETE, problemResponse(500), problemResponse(500), problemResponse(500))
    renderPage()
    await findForm()
    await fill(user, 'registration-display-name-input', 'あ'.repeat(254))
    for (const password of ['a'.repeat(12), 'あ'.repeat(24), '😀'.repeat(12)]) {
      await fillPasswords(user, password)
      await user.click(screen.getByRole('button', { name: '登録を完了する' }))
      await screen.findByTestId('registration-failure-alert')
    }
    expect(
      server.requestsTo(COMPLETE).map((request) => (request.body as { password: string }).password),
    ).toEqual(['a'.repeat(12), 'あ'.repeat(24), '😀'.repeat(12)])
  })
})

describe('RegistrationPage submitting', () => {
  it('sends the completion once while busy, keeping focus and the body (NFR9.4)', async () => {
    const user = userEvent.setup()
    const pending = deferred()
    server.answer(COMPLETE, pending.promise)
    renderPage()
    await findForm()
    await fillPasswords(user)
    const submit = screen.getByRole('button', { name: '登録を完了する' })
    await user.click(submit)

    const busy = screen.getByRole('button', { name: '登録しています' })
    expect(busy).toHaveAttribute('aria-disabled', 'true')
    expect(busy).toHaveAttribute('aria-busy', 'true')
    expect(busy).toHaveFocus()
    await user.click(busy)
    await user.keyboard('{Enter}')
    await user.click(screen.getByRole('radio', { name: 'ダーク' }))
    await user.type(screen.getByTestId('registration-display-name-input'), 'x')
    expect(screen.getByTestId('registration-display-name-input')).toHaveAttribute('readonly')
    expect(screen.getByRole('radio', { name: 'OS に合わせる' })).toBeChecked()
    expect(html().theme).toBeNull()

    await act(async () => {
      pending.resolve(noContent())
      await pending.promise
    })
    await screen.findByTestId('login-form')
    const requests = server.requestsTo(COMPLETE)
    expect(requests).toHaveLength(1)
    expect(requests[0].body).toEqual({
      token: TEST_TOKEN,
      displayName: EMAIL,
      password: PASSWORD,
      passwordConfirmation: PASSWORD,
      language: 'ja',
      theme: 'system',
      fontSize: 'md',
    })
  })
})

describe('RegistrationPage completion', () => {
  it('saves the choices, hands the email to the login page and does not log in (AC3.2.5, AC3.2.17, AC3.2.18)', async () => {
    const user = userEvent.setup()
    server.answer(COMPLETE, noContent())
    renderPage()
    await findForm()
    await fillPasswords(user)
    await user.click(screen.getByRole('radio', { name: 'English' }))
    await user.click(screen.getByRole('radio', { name: 'Dark' }))
    await user.click(screen.getByRole('radio', { name: 'Large' }))
    await user.click(screen.getByRole('button', { name: 'Complete registration' }))

    const alert = await screen.findByTestId('login-form-registered-alert')
    expect(alert).toHaveTextContent('Registration is complete.')
    expect(screen.getByTestId('login-form-email-input')).toHaveValue(EMAIL)
    expect(screen.getByTestId('location')).toHaveTextContent('/login')
    expect(screen.getByTestId('registration-location-type')).toHaveTextContent('REPLACE')
    expect(screen.getByTestId('registration-location-search').textContent).toBe('')
    expect(screen.getByTestId('registration-location-hash').textContent).toBe('')
    expect(storedDisplaySettings()).toEqual({ language: 'en', theme: 'dark', fontSize: 'lg' })
    expect(html()).toEqual({ lang: 'en', theme: 'dark', fontSize: 'lg' })
    expect(server.requestsTo(LOGIN)).toHaveLength(0)
    expect(server.requestsTo(REFRESH)).toHaveLength(0)
    // 成功の Toast は出さない（ログインの画面の案内で知らせる。CR6.4 との差）
    expect(screen.queryAllByText(/Registration is complete/)).toHaveLength(1)
    expectNoSecretsStored()
  })

  it('hands over only the email address', async () => {
    const user = userEvent.setup()
    server.answer(COMPLETE, noContent())
    renderPage({ loginElement: <HandoffProbe /> })
    await findForm()
    await fillPasswords(user)
    await user.click(screen.getByRole('button', { name: '登録を完了する' }))
    expect(await screen.findByTestId('handoff-keys')).toHaveTextContent(/^email$/)
    expect(takeLoginHandoff()).toEqual({ email: EMAIL })
  })

  it('keeps the values after 400, moves focus to the notice and completes after sending again (AC3.2.10)', async () => {
    const user = userEvent.setup()
    server.answer(COMPLETE, problemResponse(400, 'VALIDATION_FAILED'), noContent())
    renderPage()
    await findForm()
    await fillPasswords(user)
    await user.click(screen.getByRole('button', { name: '登録を完了する' }))

    const notice = await screen.findByTestId('registration-failure-alert')
    expect(notice).toHaveTextContent('入力を確かめてください。')
    await waitFor(() => expect(notice).toHaveFocus())
    expect(screen.getByTestId('registration-password-input')).toHaveValue(PASSWORD)
    expect(notice.textContent).not.toContain(DETAIL_MARKER)

    await user.click(screen.getByRole('button', { name: '登録を完了する' }))
    expect(await screen.findByTestId('login-form-registered-alert')).toBeInTheDocument()
    expect(server.requestsTo(COMPLETE)).toHaveLength(2)
  })

  it('moves to the unavailable display and drops the form after 404 (AC3.2.11)', async () => {
    const user = userEvent.setup()
    server.answer(COMPLETE, problemResponse(404, 'REGISTRATION_LINK_INVALID'))
    renderPage()
    await findForm()
    await fillPasswords(user)
    await user.click(screen.getByRole('button', { name: '登録を完了する' }))

    expect(await screen.findByTestId('registration-unavailable')).toBeInTheDocument()
    expect(screen.queryByTestId('registration-form')).not.toBeInTheDocument()
    expect(screen.queryByDisplayValue(PASSWORD)).not.toBeInTheDocument()
    expectNoSecretsStored()
  })

  it.each<[string, Response | Error]>([
    ['500', problemResponse(500, 'INTERNAL_ERROR')],
    ['a network failure', new TypeError('Failed to fetch')],
    ['403', problemResponse(403, 'ACCESS_DENIED')],
  ])(
    'shows the submit failure for %s and keeps the values (CR6.4, D10)',
    async (_label, failure) => {
      const user = userEvent.setup()
      server.answer(COMPLETE, failure)
      renderPage()
      await findForm()
      await fillPasswords(user)
      await user.click(screen.getByRole('button', { name: '登録を完了する' }))

      const notice = await screen.findByTestId('registration-failure-alert')
      expect(notice).toHaveTextContent(
        '登録できませんでした。しばらくしてから、もう一度お試しください。',
      )
      expect(screen.getByTestId('registration-page').textContent).not.toContain(DETAIL_MARKER)
      expect(screen.getByTestId('registration-password-input')).toHaveValue(PASSWORD)
      expect(screen.getByTestId('registration-password-confirmation-input')).toHaveValue(PASSWORD)
      expectNoSecretsStored()
    },
  )
})

describe('RegistrationPage leaving', () => {
  it('returns the language, theme and text size to the stored values when leaving without completing (D12)', async () => {
    const user = userEvent.setup()
    storeDisplaySettings({ language: 'ja', theme: 'light', fontSize: 'md' })
    server = installFakeServer({ [VERIFY]: [jsonResponse(200, VERIFIED_EN)] })
    renderPage()
    await findForm()
    await user.click(screen.getByRole('radio', { name: 'Dark' }))
    await user.click(screen.getByRole('radio', { name: 'Large' }))
    expect(html()).toEqual({ lang: 'en', theme: 'dark', fontSize: 'lg' })

    await user.click(screen.getByRole('radio', { name: '日本語' }))
    await user.click(screen.getByRole('radio', { name: 'English' }))
    // 完了せずに離れる（ほかの画面への移動で部品が外れる）
    server.answer(COMPLETE, problemResponse(404, 'REGISTRATION_LINK_INVALID'))
    await fillPasswords(user)
    await user.click(screen.getByRole('button', { name: 'Complete registration' }))
    await user.click(await screen.findByRole('link', { name: 'Go to the login page' }))
    await screen.findByTestId('login-form')
    expect(html()).toEqual({ lang: 'ja', theme: null, fontSize: 'md' })
    expect(storedDisplaySettings()).toEqual({ language: 'ja', theme: 'light', fontSize: 'md' })
  })

  it('ignores an answer that arrives after leaving', async () => {
    const pending = deferred()
    server = installFakeServer({ [VERIFY]: [pending.promise] })
    const { unmount } = renderPage()
    await screen.findByRole('status')
    unmount()
    await act(async () => {
      pending.resolve(jsonResponse(200, VERIFIED_EN))
      await pending.promise
    })
    // 外れた後の答えでは、言語の見せ方を置かない
    expect(getDisplaySettingsSnapshot().preview).toBeNull()
  })
})

describe('RegistrationPage accessibility', () => {
  it('has no accessibility violations on the ready screen', async () => {
    const { container } = renderPage()
    await findForm()
    expect(await axe(container)).toHaveNoViolations()
  })
})
