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
// プリファレンスの画面と usePreferencesForm のテスト（frontend-components.md の 7節、functional-spec.md の W1〜W8・W11〜W13、
// D1〜D13、AC4.1.1・AC4.1.4・AC4.1.7・AC4.1.8・AC4.1.10〜AC4.1.12、NFR2.1・NFR6.4・NFR6.5・NFR7.2）。
// fetch を偽のサーバーに差し替え、preferencesApi・ApiClient・U4 の口・ログイン状態（auth の本物の状態）は本物で動かす
// （計画の9節の決定 2）。U4 の口の呼ばれ方を見るため、useDisplaySettings を包んで applyUserPreferences の呼び出しを記録する
// （呼び出しは本物へそのまま渡す）。
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { installFakeColorScheme } from '../../app/display-settings/testing/fakeColorScheme'
import type { UserDisplaySettings } from '../../app/display-settings/displaySettingsTypes'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { refresh, resetAuthSession } from '../auth/authSession'
import { ME_PREFERENCES_PATH, type Preferences } from './preferencesApi'
import {
  deferred,
  DETAIL_MARKER,
  INITIAL_PREFERENCES,
  installFakeServer,
  jsonResponse,
  NAMED_PREFERENCES,
  problemResponse,
  REFRESH_PATH,
  sessionResponse,
  validationFailed,
  type FakeAnswer,
  type FakeServer,
} from './testing/fixtures'
import { renderPreferencesApp } from './testing/renderPreferences'

const applied = vi.hoisted(() => ({ calls: [] as unknown[] }))

vi.mock('../../app/display-settings/DisplaySettingsProvider', async (importOriginal) => {
  const original =
    await importOriginal<typeof import('../../app/display-settings/DisplaySettingsProvider')>()
  return {
    ...original,
    useDisplaySettings: () => {
      const value = original.useDisplaySettings()
      return {
        ...value,
        applyUserPreferences: (prefs: UserDisplaySettings) => {
          applied.calls.push(prefs)
          value.applyUserPreferences(prefs)
        },
      }
    },
  }
})

const GET = `GET ${ME_PREFERENCES_PATH}`
const PUT = `PUT ${ME_PREFERENCES_PATH}`
const CONSOLE_METHODS = ['log', 'info', 'warn', 'error', 'debug'] as const

let consoleSpies: ReturnType<typeof vi.spyOn>[] = []

beforeEach(() => {
  resetDisplayTestState()
  applied.calls.length = 0
  consoleSpies = CONSOLE_METHODS.map((method) =>
    vi.spyOn(console, method).mockImplementation(() => undefined),
  )
})

afterEach(() => {
  vi.unstubAllGlobals()
  resetAuthSession()
  resetDisplayTestState()
})

/** jsdom が axe の色の検査で出す知らせを除いた console の呼び出し */
function consoleCalls(): unknown[][] {
  return consoleSpies
    .flatMap((spy) => spy.mock.calls)
    .filter((args) => !String(args[0]).startsWith('Not implemented: HTMLCanvasElement'))
}

/** ブラウザの保存のすべての鍵と値 */
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

interface StartOptions {
  session?: Preferences
  get?: FakeAnswer[]
  put?: FakeAnswer[]
  languages?: readonly string[]
}

/** ログインした利用者でプリファレンスの画面を開く。 */
function start(options: StartOptions = {}): FakeServer {
  const server = installFakeServer({
    [REFRESH_PATH]: [sessionResponse(options.session ?? NAMED_PREFERENCES)],
    [GET]: options.get ?? [jsonResponse(200, options.session ?? NAMED_PREFERENCES)],
    ...(options.put ? { [PUT]: options.put } : {}),
  })
  renderPreferencesApp({ languages: options.languages })
  return server
}

async function formReady() {
  return screen.findByTestId('preferences-form')
}

const html = () => document.documentElement
const radio = (name: string) => screen.getByRole('radio', { name })
const nameInput = () => screen.getByTestId('preferences-display-name-input')
const saveButton = () => screen.getByTestId('preferences-save-button')

/** ユーザーメニューの名前のボタン */
async function menuButton(name: string | RegExp) {
  return screen.findByRole('button', { name })
}

describe('PreferencesPage loading (W2・W3)', () => {
  it('shows loading with aria-busy until the values arrive, and sends GET once (NFR6.4)', async () => {
    const answer = deferred()
    const server = start({ get: [answer.promise] })
    const loading = await screen.findByTestId('preferences-loading')
    expect(loading).toHaveAttribute('aria-busy', 'true')
    expect(within(loading).getByRole('status')).toHaveTextContent('読み込んでいます')
    expect(screen.queryByTestId('preferences-form')).not.toBeInTheDocument()
    answer.resolve(jsonResponse(200, NAMED_PREFERENCES))
    await formReady()
    expect(nameInput()).toHaveValue('検査 太郎')
    expect(radio('日本語')).toBeChecked()
    expect(radio('ライト')).toBeChecked()
    expect(radio('標準')).toBeChecked()
    expect(server.requestsTo(ME_PREFERENCES_PATH, 'GET')).toHaveLength(1)
    expect(screen.getByRole('heading', { level: 1, name: 'プリファレンス' })).toBeInTheDocument()
  })

  it('shows the initial values of an existing user and Match OS for system on a dark OS (AC4.1.1・AC4.1.10)', async () => {
    installFakeColorScheme(true)
    start({ session: INITIAL_PREFERENCES })
    await formReady()
    expect(nameInput()).toHaveValue(INITIAL_PREFERENCES.displayName)
    expect(radio('日本語')).toBeChecked()
    expect(radio('OS に合わせる')).toBeChecked()
    expect(radio('ダーク')).not.toBeChecked()
    expect(radio('標準')).toBeChecked()
    expect(html()).toHaveAttribute('data-theme', 'dark')
  })

  it.each([
    ['500', problemResponse(500, 'INTERNAL_ERROR')],
    ['a network failure', new Error('offline')],
    ['a broken body', jsonResponse(200, { ...NAMED_PREFERENCES, theme: 'blue' })],
  ] as [string, FakeAnswer][])(
    'shows a failure with Load again and no form for %s',
    async (_label, answer) => {
      const server = start({ get: [answer] })
      const failed = await screen.findByTestId('preferences-load-failed')
      expect(within(failed).getByRole('alert')).toHaveTextContent(
        'プリファレンスを読み込めませんでした。',
      )
      expect(screen.getByRole('button', { name: 'もう一度読み込む' })).toBeInTheDocument()
      expect(screen.queryByTestId('preferences-form')).not.toBeInTheDocument()
      expect(document.body).not.toHaveTextContent(DETAIL_MARKER)
      expect(server.requestsTo(ME_PREFERENCES_PATH, 'GET')).toHaveLength(1)
    },
  )

  it('loads again and focuses the name on success, or the button on another failure', async () => {
    const user = userEvent.setup()
    const server = start({
      get: [problemResponse(500), problemResponse(503), jsonResponse(200, NAMED_PREFERENCES)],
    })
    await user.click(await screen.findByRole('button', { name: 'もう一度読み込む' }))
    const retry = await screen.findByRole('button', { name: 'もう一度読み込む' })
    await waitFor(() => expect(retry).toHaveFocus())
    await user.click(retry)
    await formReady()
    await waitFor(() => expect(nameInput()).toHaveFocus())
    expect(server.requestsTo(ME_PREFERENCES_PATH, 'GET')).toHaveLength(3)
  })
})

describe('PreferencesPage form state (W4・W13)', () => {
  it('enables Revert only after a change and keeps an error while the value changes', async () => {
    const user = userEvent.setup()
    start()
    await formReady()
    const revert = screen.getByRole('button', { name: '元に戻す' })
    expect(revert).toBeDisabled()
    await user.clear(nameInput())
    expect(revert).toBeEnabled()
    await user.click(saveButton())
    expect(await screen.findByText('氏名を入力してください')).toBeInTheDocument()
    await user.type(nameInput(), '検査 太郎')
    expect(screen.getByText('氏名を入力してください')).toBeInTheDocument()
    expect(revert).toBeDisabled()
  })

  it('shows the screen in English for an English user, with the language names untranslated', async () => {
    start({ session: { ...NAMED_PREFERENCES, language: 'en' }, languages: ['ja-JP'] })
    await formReady()
    expect(screen.getByRole('heading', { level: 1, name: 'Preferences' })).toBeInTheDocument()
    expect(
      screen.getByRole('group', { name: /Language.*Changes when you save/ }),
    ).toBeInTheDocument()
    expect(radio('日本語')).toBeInTheDocument()
    expect(radio('English')).toBeChecked()
    expect(radio('Match OS')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Save' })).toBeInTheDocument()
  })
})

describe('PreferencesPage alignment with the stored values (D2)', () => {
  it('applies the loaded values when they differ from the applied ones, without a notice', async () => {
    start({
      session: NAMED_PREFERENCES,
      get: [
        jsonResponse(200, {
          displayName: '佐藤 花子',
          language: 'en',
          theme: 'dark',
          fontSize: 'lg',
        }),
      ],
    })
    expect(await menuButton(/佐藤 花子/)).toBeInTheDocument()
    await screen.findByRole('button', { name: 'Save' })
    expect(html().lang).toBe('en')
    expect(html()).toHaveAttribute('data-theme', 'dark')
    expect(html()).toHaveAttribute('data-font-size', 'lg')
    expect(applied.calls).toEqual([
      { displayName: '佐藤 花子', language: 'en', theme: 'dark', fontSize: 'lg' },
    ])
    expect(screen.getByTestId('toast-container')).toBeEmptyDOMElement()
  })

  it('does not call applyUserPreferences when the loaded values are already applied', async () => {
    start({ session: NAMED_PREFERENCES })
    await formReady()
    expect(applied.calls).toEqual([])
  })
})

describe('PreferencesPage preview (W4〜W6・AC4.1.11)', () => {
  it('shows the theme and size at once, keeps the language until saving', async () => {
    const user = userEvent.setup()
    start()
    await formReady()
    await user.click(radio('ダーク'))
    await user.click(radio('大'))
    expect(html()).toHaveAttribute('data-theme', 'dark')
    expect(html()).toHaveAttribute('data-font-size', 'lg')
    await user.click(radio('English'))
    expect(radio('English')).toBeChecked()
    expect(html().lang).toBe('ja')
    expect(saveButton()).toHaveTextContent('保存する')
    expect(screen.getByRole('group', { name: /保存すると切り替わります/ })).toBeInTheDocument()
    expect(applied.calls).toEqual([])
  })

  it('reverts the form and the preview, clears errors and focuses Save', async () => {
    const user = userEvent.setup()
    start()
    await formReady()
    await user.clear(nameInput())
    await user.click(radio('ダーク'))
    await user.click(saveButton())
    expect(await screen.findByText('氏名を入力してください')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '元に戻す' }))
    expect(html()).not.toHaveAttribute('data-theme')
    expect(html()).toHaveAttribute('data-font-size', 'md')
    expect(nameInput()).toHaveValue('検査 太郎')
    expect(radio('ライト')).toBeChecked()
    expect(screen.queryByText('氏名を入力してください')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '元に戻す' })).toBeDisabled()
    await waitFor(() => expect(saveButton()).toHaveFocus())
  })

  it('drops the preview when leaving the screen without saving', async () => {
    const user = userEvent.setup()
    start()
    await formReady()
    await user.click(radio('ダーク'))
    await user.click(radio('小'))
    expect(html()).toHaveAttribute('data-theme', 'dark')
    await user.click(await menuButton(/検査 太郎/))
    await user.click(await screen.findByRole('menuitem', { name: 'パスワードの変更' }))
    await screen.findByTestId('preferences-password-page')
    // 見せ方の取りやめは、画面が外れるときの描画の後の効果（useEffect の片付け）で行い、その後の描き直しで <html> に
    // 当たる。パスワードの変更の画面が見えた直後は、負荷が高いとその効果がまだ流れていないため、当たるのを待って確かめる
    // （CI で1回落ちた）。
    await waitFor(() => {
      expect(html()).not.toHaveAttribute('data-theme')
      expect(html()).toHaveAttribute('data-font-size', 'md')
    })
  })
})

describe('PreferencesPage saving (W7・W8)', () => {
  it('saves, switches to English at once and shows the new name and an English toast (AC4.1.4・AC4.1.8・AC4.1.12)', async () => {
    const user = userEvent.setup()
    const savedValues: Preferences = {
      displayName: '山田 花子',
      language: 'en',
      theme: 'light',
      fontSize: 'md',
    }
    const server = start({ put: [jsonResponse(200, savedValues)] })
    await formReady()
    const save = saveButton()
    const groups = screen.getAllByRole('group')
    await user.clear(nameInput())
    await user.type(nameInput(), ' 山田 花子 ')
    await user.click(radio('English'))
    await user.click(save)
    expect(await screen.findByText('Saved')).toBeInTheDocument()
    expect(within(screen.getByTestId('toast-container')).getByText('Saved')).toBeInTheDocument()
    expect(html().lang).toBe('en')
    expect(await menuButton(/山田 花子/)).toBeInTheDocument()
    expect(nameInput()).toHaveValue('山田 花子')
    expect(saveButton()).toBe(save)
    expect(saveButton()).toHaveTextContent('Save')
    expect(saveButton()).toHaveFocus()
    expect(screen.getAllByRole('group')).toEqual(groups)
    expect(screen.getByRole('button', { name: 'Revert' })).toBeDisabled()
    expect(server.requestsTo(ME_PREFERENCES_PATH, 'PUT')[0]?.body).toEqual({
      displayName: ' 山田 花子 ',
      language: 'en',
      theme: 'light',
      fontSize: 'md',
    })
    expect(applied.calls).toEqual([savedValues])
  })

  it('stops invalid names on the screen and focuses the name, keeping the preview (AC4.1.7)', async () => {
    const user = userEvent.setup()
    const server = start({ put: [jsonResponse(200, NAMED_PREFERENCES)] })
    await formReady()
    await user.click(radio('ダーク'))
    const cases: [string, string][] = [
      ['   ', '氏名を入力してください'],
      ['あ'.repeat(255), '氏名は 254 文字以内で入力してください'],
      ['山田\t花子', '氏名に改行・タブや見えない文字は使えません'],
      ['山田​花子', '氏名に改行・タブや見えない文字は使えません'],
    ]
    for (const [value, message] of cases) {
      await user.clear(nameInput())
      await user.click(nameInput())
      await user.paste(value)
      await user.click(saveButton())
      expect(await screen.findByText(message)).toBeInTheDocument()
      expect(nameInput()).toHaveAttribute('aria-invalid', 'true')
      await waitFor(() => expect(nameInput()).toHaveFocus())
    }
    expect(server.requestsTo(ME_PREFERENCES_PATH, 'PUT')).toHaveLength(0)
    expect(html()).toHaveAttribute('data-theme', 'dark')
    await user.clear(nameInput())
    await user.click(nameInput())
    await user.paste('あ'.repeat(254))
    await user.click(saveButton())
    await waitFor(() => expect(server.requestsTo(ME_PREFERENCES_PATH, 'PUT')).toHaveLength(1))
  })

  it('shows server field errors under the fields and focuses the first one', async () => {
    const user = userEvent.setup()
    start({
      put: [
        validationFailed([
          { field: 'displayName', reason: 'TOO_LONG' },
          { field: 'language', reason: 'INVALID_VALUE' },
        ]),
        validationFailed([{ field: 'language', reason: 'INVALID_VALUE' }]),
      ],
    })
    await formReady()
    await user.click(saveButton())
    expect(await screen.findByText('氏名は 254 文字以内で入力してください')).toBeInTheDocument()
    await waitFor(() => expect(nameInput()).toHaveFocus())
    expect(screen.getByRole('group', { name: /言語.*選択を確かめてください/ })).toBeInTheDocument()
    await user.click(saveButton())
    await waitFor(() => expect(radio('日本語')).toHaveFocus())
    expect(screen.queryByText('氏名は 254 文字以内で入力してください')).not.toBeInTheDocument()
    expect(document.body).not.toHaveTextContent(DETAIL_MARKER)
  })

  it('shows the form notice when no field error can be matched', async () => {
    const user = userEvent.setup()
    start({ put: [validationFailed([{ field: 'email', reason: 'REQUIRED' }]), validationFailed()] })
    await formReady()
    for (let i = 0; i < 2; i += 1) {
      await user.click(saveButton())
      const alert = within(await screen.findByTestId('preferences-alert')).getByRole('alert')
      expect(alert).toHaveTextContent('入力を確かめてください。')
    }
  })

  it('keeps the values and preview and does not move the focus on other failures', async () => {
    const user = userEvent.setup()
    const failures: FakeAnswer[] = [
      problemResponse(400, 'MALFORMED_REQUEST'),
      problemResponse(500, 'INTERNAL_ERROR'),
      new Error('offline'),
      jsonResponse(200, { ...NAMED_PREFERENCES, fontSize: 'xl' }),
    ]
    start({ put: failures })
    await formReady()
    await user.clear(nameInput())
    await user.type(nameInput(), '新しい 名前')
    await user.click(radio('ダーク'))
    for (let i = 0; i < failures.length; i += 1) {
      await user.click(saveButton())
      const alert = within(await screen.findByTestId('preferences-alert')).getByRole('alert')
      expect(alert).toHaveTextContent(
        '保存できませんでした。しばらくしてから、もう一度お試しください。',
      )
      expect(saveButton()).toHaveFocus()
      expect(nameInput()).toHaveValue('新しい 名前')
      expect(html()).toHaveAttribute('data-theme', 'dark')
      expect(document.body).not.toHaveTextContent(DETAIL_MARKER)
    }
    expect(applied.calls).toEqual([])
    expect(await menuButton(/検査 太郎/)).toBeInTheDocument()
  })
})

describe('PreferencesPage while saving (D10・NFR6.5)', () => {
  it('sends PUT once, shows the saving state and ignores changes', async () => {
    const user = userEvent.setup()
    const answer = deferred()
    const server = start({ put: [answer.promise] })
    await formReady()
    await user.click(radio('ダーク'))
    // 氏名で Enter を押して送ると、送信の間もフォーカスは氏名に残る
    await user.type(nameInput(), '{Enter}')
    expect(nameInput()).toHaveFocus()
    await user.click(saveButton())
    await user.type(nameInput(), '{Enter}')
    expect(server.requestsTo(ME_PREFERENCES_PATH, 'PUT')).toHaveLength(1)
    expect(saveButton()).toHaveTextContent('保存しています')
    expect(saveButton()).toHaveAttribute('aria-disabled', 'true')
    expect(saveButton()).toHaveAttribute('aria-busy', 'true')
    expect(nameInput()).toHaveAttribute('readonly')
    expect(screen.getByRole('button', { name: '元に戻す' })).toBeDisabled()
    await user.click(radio('大'))
    expect(radio('標準')).toBeChecked()
    expect(html()).toHaveAttribute('data-font-size', 'md')
    answer.resolve(jsonResponse(200, { ...NAMED_PREFERENCES, theme: 'dark' }))
    await screen.findByText('保存しました')
    expect(html()).toHaveAttribute('data-theme', 'dark')
  })

  it('applies a 200 that arrives after leaving, without a toast, and drops a failure', async () => {
    const user = userEvent.setup()
    const success = deferred()
    const failure = deferred()
    const saved = { ...NAMED_PREFERENCES, theme: 'dark' as const }
    start({
      get: [jsonResponse(200, NAMED_PREFERENCES), jsonResponse(200, saved)],
      put: [success.promise, failure.promise],
    })
    await formReady()
    await user.click(radio('ダーク'))
    await user.click(saveButton())
    await user.click(await menuButton(/検査 太郎/))
    await user.click(await screen.findByRole('menuitem', { name: 'パスワードの変更' }))
    await screen.findByTestId('preferences-password-page')
    await act(async () => {
      success.resolve(jsonResponse(200, saved))
      await success.promise
    })
    await waitFor(() => expect(applied.calls).toEqual([saved]))
    expect(html()).toHaveAttribute('data-theme', 'dark')
    expect(screen.getByTestId('toast-container')).toBeEmptyDOMElement()

    await user.click(await menuButton(/検査 太郎/))
    await user.click(await screen.findByRole('menuitem', { name: 'プリファレンス' }))
    await formReady()
    await user.click(saveButton())
    await user.click(await menuButton(/検査 太郎/))
    await user.click(await screen.findByRole('menuitem', { name: 'パスワードの変更' }))
    await act(async () => {
      failure.resolve(problemResponse(500))
      await failure.promise
    })
    expect(applied.calls).toHaveLength(1)
    expect(consoleCalls()).toEqual([])
  })
})

describe('PreferencesPage when the login state is renewed during a preview (10節の (f)、決定 4)', () => {
  it('records what the screen and the form show after a token refresh', async () => {
    const user = userEvent.setup()
    const server = start()
    await formReady()
    await user.click(radio('ダーク'))
    expect(html()).toHaveAttribute('data-theme', 'dark')
    server.answer(REFRESH_PATH, sessionResponse(NAMED_PREFERENCES))
    await act(async () => {
      await refresh()
    })
    // 結果（記録）: U4 はログイン状態が新しくなると見せ方を捨てるため、画面は当たっている値（ライト）に戻り、
    // フォームのテーマは選んだダークのまま残る（食い違い。保存・「元に戻す」・次の選択で解ける）。
    await waitFor(() => expect(html()).not.toHaveAttribute('data-theme'))
    expect(radio('ダーク')).toBeChecked()
    // 次の選択で解ける
    await user.click(radio('大'))
    expect(html()).toHaveAttribute('data-theme', 'dark')
    expect(html()).toHaveAttribute('data-font-size', 'lg')
  })
})

describe('PreferencesPage secrets and personal values (NFR2.1)', () => {
  it('keeps the name out of the browser storage, the console and the URL', async () => {
    const user = userEvent.setup()
    start({
      session: INITIAL_PREFERENCES,
      put: [
        jsonResponse(200, { ...INITIAL_PREFERENCES, displayName: '保存 花子' }),
        problemResponse(500),
      ],
    })
    await formReady()
    expect(storedValues()).not.toContain(INITIAL_PREFERENCES.displayName)
    await user.clear(nameInput())
    await user.type(nameInput(), '保存 花子')
    await user.click(saveButton())
    await screen.findByText('保存しました')
    await user.click(saveButton())
    await screen.findByTestId('preferences-alert')
    const stored = storedValues()
    expect(stored).not.toContain(INITIAL_PREFERENCES.displayName)
    expect(stored).not.toContain('保存 花子')
    expect(stored).not.toContain('access-token-marker')
    expect(window.location.search).toBe('')
    expect(window.location.hash).toBe('')
    expect(screen.getByTestId('location')).toHaveTextContent('/me/preferences')
    expect(consoleCalls()).toEqual([])
  })

  it('has no accessibility violations when ready', async () => {
    const { container } = (() => {
      start()
      return { container: document.body }
    })()
    await formReady()
    expect(await axe(container)).toHaveNoViolations()
  })
})
