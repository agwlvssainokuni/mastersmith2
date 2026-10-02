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
// 招待の管理の画面のテスト（useInvitationAdmin を通す。frontend-components.md の 7節、functional-spec.md の 4〜6節、
// performance-design.md の 2.2、security-design.md の 2.2・3.5・4節、NFR2.1・NFR6.3・NFR6.4・NFR7.2・NFR8.1・NFR9.1・NFR9.2）。
// API の関数を差し替え、答えを返さない約束で止めて途中の表示を見る。時差を固定する。
// 権限が無い（403・ACCESS_DENIED）の確かめだけは、画面を骨組みの ShellLayout の中に本物の登録と管理者の提供元で描き、
// S6 に置き換わることを確かめる（U4 の FC 6.2・R-03、AC2.2.1・AC2.2.4）。code の無い 403 は今までどおり一般の文言。
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, onTestFailed, vi, type Mock } from 'vitest'
import { axe } from 'vitest-axe'
import { fakeProvider } from '../../app/testing/renderWithProviders'
import type { ApiError } from '../../shared/api-client/apiError'
import type { InvitationApi } from './api/invitationApi'
import type { Invitation, InvitationPage } from './api/types'
import { InvitationAdminPage } from './InvitationAdminPage'
import { registration } from './registration'
import { invitationOf, pageOf, rowsOf, sampleRows, unavailablePageOf } from './testing/fixtures'
import { renderInvitation } from './testing/renderInvitation'

/** 漏えいの確かめの1件（すべての流れを操作する）だけの上限。設定全体の既定は 5 秒のまま（FR3.2）。 */
const LEAK_CHECK_TIMEOUT_MS = 15_000

interface Deferred<T> {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (error: unknown) => void
}

function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

/** 失敗の値（detail と title に目印を入れる。画面に出てはいけない） */
function apiError(status: number, code?: string, extra: Record<string, unknown> = {}): ApiError {
  const error: ApiError = code ? { kind: 'response', status, code } : { kind: 'response', status }
  Object.defineProperty(error, 'problem', {
    value: {
      status,
      code,
      detail: 'server-detail-marker',
      title: 'server-detail-marker',
      ...extra,
    },
    enumerable: false,
  })
  return error
}

const NETWORK: ApiError = { kind: 'network' }

interface FakeApi {
  api: InvitationApi
  list: Mock<(page: number) => Promise<InvitationPage>>
  create: Mock<InvitationApi['createInvitation']>
  resend: Mock<(invitationId: number) => Promise<Invitation>>
  cancel: Mock<(invitationId: number) => Promise<void>>
}

function fakeApi(firstPage: InvitationPage = pageOf()): FakeApi {
  const list = vi.fn<(page: number) => Promise<InvitationPage>>(() => Promise.resolve(firstPage))
  const create = vi.fn<InvitationApi['createInvitation']>(() =>
    Promise.resolve(invitationOf({ invitationId: 99 })),
  )
  const resend = vi.fn<(invitationId: number) => Promise<Invitation>>((invitationId) =>
    Promise.resolve(invitationOf({ invitationId })),
  )
  const cancel = vi.fn<(invitationId: number) => Promise<void>>(() => Promise.resolve())
  return {
    api: {
      listInvitations: list,
      createInvitation: create,
      resendInvitation: resend,
      cancelInvitation: cancel,
    },
    list,
    create,
    resend,
    cancel,
  }
}

function renderPage(fake: FakeApi, languages: readonly string[] = ['ja-JP']) {
  return renderInvitation(<InvitationAdminPage api={fake.api} timeZone="Asia/Tokyo" />, languages)
}

/** 画面を ShellLayout の中に、本物の登録と管理者のログイン状態で描く（S6 の確かめ用、U4 の R-03）。 */
function renderInShell(fake: FakeApi) {
  return renderInvitation(<InvitationAdminPage api={fake.api} timeZone="Asia/Tokyo" />, undefined, {
    withShell: true,
    provider: fakeProvider({ loggedIn: true, admin: true, displayName: '管理者' }),
    registrations: [registration],
  })
}

/** S6 に置き換わり、招待の画面の中身・誤りの表示・確かめと入力の表示が残っていないこと */
async function expectForbiddenView(): Promise<void> {
  expect(await screen.findByTestId('admin-forbidden-view')).toBeInTheDocument()
  expect(screen.getByTestId('admin-forbidden-heading')).toHaveTextContent('利用者の招待')
  expect(screen.queryByRole('table', { name: '招待中の人' })).toBeNull()
  expect(screen.queryByTestId('invitation-list-failed')).toBeNull()
  expect(screen.queryByTestId('invitation-failure-alert')).toBeNull()
  expect(screen.queryByTestId('invitation-invite-dialog-alert')).toBeNull()
  expect(screen.queryByRole('dialog')).toBeNull()
  expect(screen.queryByRole('alertdialog')).toBeNull()
  expect(document.body.textContent).not.toContain('server-detail-marker')
}

async function rowsShown(): Promise<void> {
  await screen.findByRole('table', { name: '招待中の人' })
  await waitFor(() => expect(screen.queryByTestId('invitation-list-loading')).toBeNull())
}

function heading(): HTMLElement {
  return screen.getByRole('heading', { level: 2, name: '招待中の人' })
}

function failureAlert(): HTMLElement {
  return screen.getByTestId('invitation-failure-alert')
}

afterEach(() => {
  vi.useRealTimers()
})

describe('InvitationAdminPage loading', () => {
  it('reads the first page on open and announces the range', async () => {
    const fake = fakeApi()
    renderPage(fake)
    expect(screen.getByRole('heading', { level: 1, name: '利用者の招待' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '招待する' })).toBeDisabled()
    await rowsShown()
    expect(fake.list).toHaveBeenCalledWith(1)
    expect(screen.getByTestId('invitation-live-region')).toHaveTextContent('1〜3 件目 / 全 3 件')
    expect(screen.getByRole('button', { name: '招待する' })).toBeEnabled()
  })

  it('moves between pages and focuses the heading when the pressed button becomes disabled', async () => {
    const user = userEvent.setup()
    const pages: Record<number, InvitationPage> = {
      1: pageOf({ items: rowsOf(20, 1), total: 43 }),
      2: pageOf({ items: rowsOf(20, 21), page: 2, total: 43 }),
      3: pageOf({ items: rowsOf(3, 41), page: 3, total: 43 }),
    }
    const fake = fakeApi()
    fake.list.mockImplementation((page) => Promise.resolve(pages[page] as InvitationPage))
    renderPage(fake)
    await rowsShown()
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await screen.findByText('invitee-21@example.test')
    expect(screen.getByTestId('invitation-live-region')).toHaveTextContent('21〜40 件目 / 全 43 件')
    expect(screen.getByRole('button', { name: '次へ' })).toHaveFocus()
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await screen.findByText('invitee-43@example.test')
    await waitFor(() => expect(heading()).toHaveFocus())
    expect(fake.list.mock.calls.map(([page]) => page)).toEqual([1, 2, 3])
  })

  it('reads the last page when the page read is past the end', async () => {
    const fake = fakeApi()
    fake.list
      .mockResolvedValueOnce(pageOf({ items: [], page: 3, total: 25 }))
      .mockResolvedValueOnce(pageOf({ items: rowsOf(5, 21), page: 2, total: 25 }))
    renderPage(fake)
    await screen.findByText('invitee-25@example.test')
    expect(fake.list.mock.calls.map(([page]) => page)).toEqual([1, 2])
    expect(screen.getByText('21〜25 件目 / 全 25 件（2 / 2 ページ）')).toBeInTheDocument()
  })

  it('uses only the answer of the last reload and ignores answers after leaving', async () => {
    const user = userEvent.setup()
    const first = pageOf({ items: rowsOf(20, 1), total: 43 })
    const slow = deferred<InvitationPage>()
    const fast = deferred<InvitationPage>()
    const fake = fakeApi(first)
    renderPage(fake)
    await rowsShown()
    fake.list.mockReturnValueOnce(slow.promise).mockReturnValueOnce(fast.promise)
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await user.click(screen.getByRole('button', { name: '前へ' }))
    await act(async () =>
      fast.resolve(
        pageOf({
          items: [invitationOf({ invitationId: 1, email: 'latest@example.test' })],
          total: 43,
        }),
      ),
    )
    await act(async () =>
      slow.resolve(
        pageOf({
          items: [invitationOf({ invitationId: 30, email: 'stale@example.test' })],
          page: 2,
          total: 43,
        }),
      ),
    )
    expect(screen.getByText('latest@example.test')).toBeInTheDocument()
    expect(screen.queryByText('stale@example.test')).toBeNull()

    const late = deferred<InvitationPage>()
    const other = fakeApi()
    other.list.mockReturnValueOnce(late.promise)
    const error = vi.spyOn(console, 'error')
    const { unmount } = renderPage(other)
    unmount()
    await act(async () => late.resolve(unavailablePageOf()))
    expect(error).not.toHaveBeenCalled()
  })

  it('never reloads on its own as time passes (NFR6.3)', async () => {
    const fake = fakeApi()
    renderPage(fake)
    await rowsShown()
    vi.useFakeTimers()
    await act(async () => {
      vi.advanceTimersByTime(10 * 60 * 1000)
    })
    vi.useRealTimers()
    expect(fake.list).toHaveBeenCalledTimes(1)
  })

  it('shows the failed list with the general message, cannot invite, and loads again', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    fake.list.mockRejectedValueOnce(apiError(403))
    renderPage(fake)
    const failed = await screen.findByTestId('invitation-list-failed')
    expect(failed).toHaveTextContent('招待中の人を読み込めませんでした。')
    expect(failed).toHaveTextContent('操作を受け付けられませんでした')
    expect(document.body.textContent).not.toContain('server-detail-marker')
    expect(screen.getByRole('button', { name: '招待する' })).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'もう一度読み込む' }))
    await rowsShown()
    expect(screen.getByRole('button', { name: '招待する' })).toBeEnabled()
  })

  it('shows the warning and blocks inviting and resending when invitations are unavailable', async () => {
    const fake = fakeApi(unavailablePageOf())
    renderPage(fake)
    await rowsShown()
    expect(screen.getByTestId('invitation-unavailable-alert')).toHaveTextContent('招待を使えません')
    expect(screen.getByRole('button', { name: '招待する' })).toBeDisabled()
    expect(
      screen.getByRole('button', { name: 'hanako@example.test への招待を送り直す' }),
    ).toBeDisabled()
    expect(
      screen.getByRole('button', { name: 'hanako@example.test への招待を取り消す' }),
    ).toBeEnabled()
  })
})

describe('InvitationAdminPage inviting', () => {
  async function openInvite(user: ReturnType<typeof userEvent.setup>) {
    await rowsShown()
    await user.click(screen.getByRole('button', { name: '招待する' }))
    return screen.getByRole('dialog')
  }

  it('invites, keeps the dialog while sending, then closes it with a toast and reads the first page', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    const sending = deferred<Invitation>()
    fake.create.mockReturnValueOnce(sending.promise)
    renderPage(fake)
    await openInvite(user)
    expect(screen.getByRole('radio', { name: '日本語' })).toBeChecked()
    await user.type(screen.getByLabelText('メールアドレス（必須）'), 'new@example.test')
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: '招待する' }))
    const busy = within(screen.getByRole('dialog')).getByRole('button', { name: '送信しています' })
    expect(busy).toHaveAttribute('aria-busy', 'true')
    expect(busy).toHaveFocus()
    await user.keyboard('{Escape}')
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    await act(async () => sending.resolve(invitationOf({ invitationId: 99, sendResult: 'SENT' })))
    expect(await screen.findByText('招待を送りました')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).toBeNull()
    expect(fake.create).toHaveBeenCalledWith({ email: 'new@example.test', language: 'ja' })
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(2))
    expect(fake.list).toHaveBeenLastCalledWith(1)
    await waitFor(() => expect(screen.getByRole('button', { name: '招待する' })).toHaveFocus())
    expect(screen.queryByTestId('invitation-failure-alert')).toBeNull()
  })

  it('starts with the language of an English administrator and sends it', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    renderPage(fake, ['en-US'])
    await screen.findByRole('table', { name: 'Pending invitations' })
    await user.click(screen.getByRole('button', { name: 'Invite' }))
    expect(screen.getByRole('radio', { name: 'English' })).toBeChecked()
    await user.type(screen.getByLabelText('Email address (required)'), 'en@example.test')
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Invite' }))
    expect(await screen.findByText('Invitation sent')).toBeInTheDocument()
    expect(fake.create).toHaveBeenCalledWith({ email: 'en@example.test', language: 'en' })
  })

  it('shows the send failure after a 201 with FAILED and reads the first page', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    fake.create.mockResolvedValueOnce(invitationOf({ invitationId: 99, sendResult: 'FAILED' }))
    renderPage(fake)
    await openInvite(user)
    await user.type(screen.getByLabelText('メールアドレス（必須）'), 'new@example.test')
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: '招待する' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(failureAlert()).toHaveTextContent(
      '招待は作りましたが、メールを送れませんでした。一覧から送り直せます。',
    )
    await waitFor(() => expect(fake.list).toHaveBeenLastCalledWith(1))
  })

  it('shows input errors under the address, keeps the value and the dialog open', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    fake.create
      .mockRejectedValueOnce(apiError(400, 'VALIDATION_FAILED', { fieldErrors: [] }))
      .mockRejectedValueOnce(apiError(409, 'INVITATION_EMAIL_REGISTERED'))
    renderPage(fake)
    const dialog = await openInvite(user)
    const email = screen.getByLabelText('メールアドレス（必須）')
    await user.type(email, '　 ')
    await user.click(within(dialog).getByRole('button', { name: '招待する' }))
    expect(within(dialog).getByText('メールアドレスを入れてください。')).toBeInTheDocument()
    expect(email).toHaveFocus()
    expect(fake.create).not.toHaveBeenCalled()

    await user.clear(email)
    await user.type(email, 'bad')
    await user.click(within(dialog).getByRole('button', { name: '招待する' }))
    expect(
      await within(dialog).findByText('メールアドレスの形式が正しくありません。'),
    ).toBeVisible()
    expect(email).toHaveValue('bad')
    expect(email).toHaveFocus()

    await user.click(within(dialog).getByRole('button', { name: '招待する' }))
    expect(
      await within(dialog).findByText('このメールアドレスの利用者はすでに登録されています。'),
    ).toBeVisible()
    expect(email).toHaveFocus()
    expect(document.body.textContent).not.toContain('server-detail-marker')
  })

  it('moves from the pending notice to the row on its page and highlights it', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    const pendingRow = invitationOf({ invitationId: 42, email: 'pending@example.test' })
    fake.list
      .mockResolvedValueOnce(pageOf({ items: rowsOf(20, 1), total: 25 }))
      .mockResolvedValueOnce(pageOf({ items: [...rowsOf(4, 21), pendingRow], page: 2, total: 25 }))
    fake.create.mockRejectedValueOnce(
      apiError(409, 'INVITATION_ALREADY_PENDING', { invitationId: 42, page: 2 }),
    )
    renderPage(fake)
    const dialog = await openInvite(user)
    await user.type(screen.getByLabelText('メールアドレス（必須）'), 'pending@example.test')
    await user.click(within(dialog).getByRole('button', { name: '招待する' }))
    await user.click(await screen.findByRole('button', { name: '一覧でこの招待を見る' }))
    await screen.findByText('pending@example.test')
    expect(fake.list).toHaveBeenLastCalledWith(2)
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'pending@example.test への招待を送り直す' }),
      ).toHaveFocus(),
    )
    expect(screen.getByTestId('invitation-row-email-42')).toContainElement(
      document.querySelector('[data-invitation-highlighted]') as HTMLElement,
    )
  })

  it('focuses the heading when the pending row is gone, and corrects a page past the end', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    fake.list
      .mockResolvedValueOnce(pageOf())
      .mockResolvedValueOnce(pageOf({ items: [], page: 5, total: 3 }))
      .mockResolvedValueOnce(pageOf())
    fake.create.mockRejectedValueOnce(
      apiError(409, 'INVITATION_ALREADY_PENDING', { invitationId: 77, page: 5 }),
    )
    renderPage(fake)
    const dialog = await openInvite(user)
    await user.type(screen.getByLabelText('メールアドレス（必須）'), 'gone@example.test')
    await user.click(within(dialog).getByRole('button', { name: '招待する' }))
    await user.click(await screen.findByRole('button', { name: '一覧でこの招待を見る' }))
    await waitFor(() => expect(heading()).toHaveFocus())
    expect(fake.list.mock.calls.map(([page]) => page)).toEqual([1, 5, 1])
    expect(document.querySelector('[data-invitation-highlighted]')).toBeNull()
  })

  it('shows the reasons of a 503 inside the dialog and reloads after closing', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    fake.create.mockRejectedValueOnce(
      apiError(503, 'INVITATION_NOT_CONFIGURED', { unavailableReasons: ['SMTP_NOT_CONFIGURED'] }),
    )
    renderPage(fake)
    const dialog = await openInvite(user)
    await user.type(screen.getByLabelText('メールアドレス（必須）'), 'new@example.test')
    await user.click(within(dialog).getByRole('button', { name: '招待する' }))
    expect(await screen.findByTestId('invitation-invite-dialog-alert')).toHaveTextContent(
      'メールの送り先が設定されていません',
    )
    expect(screen.getByLabelText('メールアドレス（必須）')).toHaveValue('new@example.test')
    fake.list.mockResolvedValueOnce(unavailablePageOf())
    await user.click(within(dialog).getByRole('button', { name: 'やめる' }))
    expect(await screen.findByTestId('invitation-unavailable-alert')).toBeInTheDocument()
    expect(fake.list).toHaveBeenCalledTimes(2)
  })

  it('shows the general message inside the dialog for 403, unknown codes, 5xx and network failures', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    fake.create
      .mockRejectedValueOnce(apiError(403))
      .mockRejectedValueOnce(apiError(422))
      .mockRejectedValueOnce(apiError(500, 'INTERNAL_ERROR'))
      .mockRejectedValueOnce(NETWORK)
    renderPage(fake)
    const dialog = await openInvite(user)
    await user.type(screen.getByLabelText('メールアドレス（必須）'), 'new@example.test')
    const expected = [
      '操作を受け付けられませんでした',
      '操作を受け付けられませんでした',
      'サーバーで問題が起きました',
      'サーバーにつながりませんでした',
    ]
    for (const text of expected) {
      await user.click(within(dialog).getByRole('button', { name: '招待する' }))
      await waitFor(() =>
        expect(screen.getByTestId('invitation-invite-dialog-alert')).toHaveTextContent(text),
      )
    }
    expect(screen.getByLabelText('メールアドレス（必須）')).toHaveValue('new@example.test')
    expect(document.body.textContent).not.toContain('server-detail-marker')
    expect(fake.list).toHaveBeenCalledTimes(1)
  })
})

describe('InvitationAdminPage resending and revoking', () => {
  const RESEND_HANAKO = 'hanako@example.test への招待を送り直す'

  it('resends with a toast or a send failure, reloads and keeps focus on the same resend button', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    const sending = deferred<Invitation>()
    fake.resend
      .mockReturnValueOnce(sending.promise)
      .mockResolvedValueOnce(invitationOf({ invitationId: 11, sendResult: 'FAILED' }))
    renderPage(fake)
    await rowsShown()
    await user.click(screen.getByRole('button', { name: RESEND_HANAKO }))
    const busy = screen.getByRole('button', {
      name: 'hanako@example.test への招待を送信しています',
    })
    expect(busy).toHaveAttribute('aria-busy', 'true')
    expect(busy).toHaveFocus()
    expect(
      screen.getByRole('button', { name: 'hanako@example.test への招待を取り消す' }),
    ).toBeDisabled()
    expect(
      screen.getByRole('button', { name: 'taro@example.test への招待を送り直す' }),
    ).toBeEnabled()
    await act(async () => sending.resolve(invitationOf({ invitationId: 11, sendResult: 'SENT' })))
    expect(await screen.findByText('招待を送り直しました')).toBeInTheDocument()
    await waitFor(() => expect(screen.getByRole('button', { name: RESEND_HANAKO })).toHaveFocus())
    expect(fake.resend).toHaveBeenCalledWith(11)
    expect(fake.list).toHaveBeenCalledTimes(2)

    await user.click(screen.getByRole('button', { name: RESEND_HANAKO }))
    await waitFor(() =>
      expect(failureAlert()).toHaveTextContent(
        '招待を送り直しましたが、メールを送れませんでした。もう一度送り直せます。',
      ),
    )
    await waitFor(() => expect(screen.getByRole('button', { name: RESEND_HANAKO })).toHaveFocus())
    expect(fake.list).toHaveBeenCalledTimes(3)
  })

  it('reloads after a 404 and focuses the heading when the row is gone', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    fake.resend.mockRejectedValueOnce(apiError(404, 'INVITATION_NOT_FOUND'))
    renderPage(fake)
    await rowsShown()
    fake.list.mockResolvedValueOnce(pageOf({ items: sampleRows().slice(1), total: 2 }))
    await user.click(screen.getByRole('button', { name: RESEND_HANAKO }))
    await waitFor(() => expect(failureAlert()).toHaveTextContent('この招待は見つかりません。'))
    await waitFor(() => expect(heading()).toHaveFocus())
    expect(fake.list).toHaveBeenCalledTimes(2)
    expect(document.body.textContent).not.toContain('server-detail-marker')
  })

  it('reloads after a 503, shows the reasons and focuses the address cell of the row', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    fake.resend.mockRejectedValueOnce(
      apiError(503, 'INVITATION_NOT_CONFIGURED', {
        unavailableReasons: ['BASE_URL_NOT_CONFIGURED', 'SMTP_NOT_CONFIGURED'],
      }),
    )
    renderPage(fake)
    await rowsShown()
    fake.list.mockResolvedValueOnce(unavailablePageOf())
    await user.click(screen.getByRole('button', { name: RESEND_HANAKO }))
    await waitFor(() => expect(failureAlert()).toHaveTextContent('アプリの URL'))
    await waitFor(() => expect(screen.getByTestId('invitation-row-email-11')).toHaveFocus())
    expect(screen.getByTestId('invitation-unavailable-alert')).toBeInTheDocument()
  })

  it('does not reload after 403, 5xx or network failures and keeps focus on the resend button', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    fake.resend
      .mockRejectedValueOnce(apiError(403))
      .mockRejectedValueOnce(apiError(502))
      .mockRejectedValueOnce(NETWORK)
    renderPage(fake)
    await rowsShown()
    const expected = [
      '操作を受け付けられませんでした',
      'サーバーで問題が起きました',
      'サーバーにつながりませんでした',
    ]
    for (const text of expected) {
      await user.click(screen.getByRole('button', { name: RESEND_HANAKO }))
      await waitFor(() => expect(failureAlert()).toHaveTextContent(text))
      expect(screen.getByRole('button', { name: RESEND_HANAKO })).toHaveFocus()
      expect(screen.getAllByTestId('invitation-failure-alert')).toHaveLength(1)
    }
    expect(fake.list).toHaveBeenCalledTimes(1)
    expect(document.body.textContent).not.toContain('server-detail-marker')
  })

  it('revokes after confirming, reloads, and focuses the heading or the empty message', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [invitationOf({ invitationId: 11 })], total: 1 }))
    const revoking = deferred<void>()
    fake.cancel.mockReturnValueOnce(revoking.promise)
    renderPage(fake)
    await rowsShown()
    await user.click(screen.getByRole('button', { name: 'hanako@example.test への招待を取り消す' }))
    const dialog = screen.getByRole('alertdialog', { name: '招待を取り消しますか' })
    await user.click(within(dialog).getByRole('button', { name: '取り消す' }))
    expect(within(dialog).getByRole('button', { name: '取り消しています' })).toHaveAttribute(
      'aria-busy',
      'true',
    )
    expect(within(dialog).getByRole('button', { name: 'やめる' })).toBeDisabled()
    fake.list.mockResolvedValueOnce(pageOf({ items: [], total: 0 }))
    await act(async () => revoking.resolve())
    expect(await screen.findByText('招待を取り消しました')).toBeInTheDocument()
    expect(screen.queryByRole('alertdialog')).toBeNull()
    await waitFor(() => expect(screen.getByTestId('invitation-list-empty')).toHaveFocus())
    expect(fake.cancel).toHaveBeenCalledWith(11)
  })

  it('reads the previous page after revoking the last row of a page', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    fake.list
      .mockResolvedValueOnce(pageOf({ items: rowsOf(20, 1), total: 21 }))
      .mockResolvedValueOnce(pageOf({ items: rowsOf(1, 21), page: 2, total: 21 }))
      .mockResolvedValueOnce(pageOf({ items: [], page: 2, total: 20 }))
      .mockResolvedValueOnce(pageOf({ items: rowsOf(20, 1), total: 20 }))
    renderPage(fake)
    await rowsShown()
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await user.click(
      await screen.findByRole('button', { name: 'invitee-21@example.test への招待を取り消す' }),
    )
    await user.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: '取り消す' }),
    )
    await screen.findByText('1〜20 件目 / 全 20 件（1 / 1 ページ）')
    expect(fake.list.mock.calls.map(([page]) => page)).toEqual([1, 2, 2, 1])
    await waitFor(() => expect(heading()).toHaveFocus())
  })

  it('reloads after a 404 on revoke, and returns focus to the row after other failures', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    fake.cancel
      .mockRejectedValueOnce(apiError(404, 'INVITATION_NOT_FOUND'))
      .mockRejectedValueOnce(apiError(500, 'INTERNAL_ERROR'))
    renderPage(fake)
    await rowsShown()
    const revokeTaro = 'taro@example.test への招待を取り消す'
    await user.click(screen.getByRole('button', { name: revokeTaro }))
    await user.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: '取り消す' }),
    )
    await waitFor(() => expect(failureAlert()).toHaveTextContent('この招待は見つかりません。'))
    await waitFor(() => expect(heading()).toHaveFocus())
    expect(fake.list).toHaveBeenCalledTimes(2)

    await user.click(screen.getByRole('button', { name: revokeTaro }))
    expect(screen.queryByTestId('invitation-failure-alert')).toBeNull()
    await user.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: '取り消す' }),
    )
    await waitFor(() => expect(failureAlert()).toHaveTextContent('サーバーで問題が起きました'))
    expect(screen.queryByRole('alertdialog')).toBeNull()
    expect(screen.getByRole('button', { name: revokeTaro })).toHaveFocus()
    expect(fake.list).toHaveBeenCalledTimes(2)
  })

  it('closes the confirmation without revoking and returns focus to the row', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    renderPage(fake)
    await rowsShown()
    const revokeTaro = screen.getByRole('button', { name: 'taro@example.test への招待を取り消す' })
    await user.click(revokeTaro)
    expect(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: 'やめる' }),
    ).toHaveFocus()
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('alertdialog')).toBeNull()
    expect(revokeTaro).toHaveFocus()
    expect(fake.cancel).not.toHaveBeenCalled()
  })

  it('keeps a single failure notice, replaces it on the next result and closes it', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    fake.resend.mockRejectedValueOnce(NETWORK).mockRejectedValueOnce(apiError(500))
    renderPage(fake)
    await rowsShown()
    await user.click(screen.getByRole('button', { name: RESEND_HANAKO }))
    await waitFor(() => expect(failureAlert()).toHaveTextContent('サーバーにつながりませんでした'))
    await user.click(screen.getByRole('button', { name: 'taro@example.test への招待を送り直す' }))
    await waitFor(() => expect(failureAlert()).toHaveTextContent('サーバーで問題が起きました'))
    expect(screen.getAllByTestId('invitation-failure-alert')).toHaveLength(1)
    await user.click(screen.getByRole('button', { name: '知らせを閉じる' }))
    expect(screen.queryByTestId('invitation-failure-alert')).toBeNull()
  })
})

describe('InvitationAdminPage unauthenticated and leaving', () => {
  it('leaves a 401 to the login flow and shows no message', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    fake.create.mockRejectedValueOnce(apiError(401, 'AUTHENTICATION_REQUIRED'))
    fake.resend.mockRejectedValueOnce(apiError(401, 'AUTHENTICATION_REQUIRED'))
    fake.cancel.mockRejectedValueOnce(apiError(401, 'AUTHENTICATION_REQUIRED'))
    renderPage(fake)
    await rowsShown()

    await user.click(screen.getByRole('button', { name: '招待する' }))
    await user.type(screen.getByLabelText('メールアドレス（必須）'), 'new@example.test')
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: '招待する' }))
    await waitFor(() =>
      expect(
        within(screen.getByRole('dialog')).getByRole('button', { name: '招待する' }),
      ).not.toHaveAttribute('aria-busy'),
    )
    expect(screen.queryByTestId('invitation-invite-dialog-alert')).toBeNull()
    await user.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'やめる' }))

    await user.click(screen.getByRole('button', { name: 'hanako@example.test への招待を送り直す' }))
    await waitFor(() => expect(fake.resend).toHaveBeenCalledTimes(1))
    await waitFor(() =>
      expect(
        screen.getByRole('button', { name: 'hanako@example.test への招待を送り直す' }),
      ).not.toHaveAttribute('aria-busy'),
    )
    await user.click(screen.getByRole('button', { name: 'taro@example.test への招待を取り消す' }))
    await user.click(
      within(screen.getByRole('alertdialog')).getByRole('button', { name: '取り消す' }),
    )
    await waitFor(() =>
      expect(
        within(screen.getByRole('alertdialog')).getByRole('button', { name: '取り消す' }),
      ).not.toHaveAttribute('aria-busy'),
    )
    expect(screen.queryByTestId('invitation-failure-alert')).toBeNull()
    expect(fake.list).toHaveBeenCalledTimes(1)

    const listed = fakeApi()
    listed.list.mockRejectedValueOnce(apiError(401, 'AUTHENTICATION_REQUIRED'))
    const other = renderPage(listed)
    await waitFor(() => expect(listed.list).toHaveBeenCalledTimes(1))
    expect(within(other.container).getByTestId('invitation-list-loading')).toBeInTheDocument()
    expect(within(other.container).queryByTestId('invitation-list-failed')).toBeNull()
  })

  it('ignores the answers of operations that arrive after leaving the screen', async () => {
    const user = userEvent.setup()
    const creating = deferred<Invitation>()
    const resending = deferred<Invitation>()
    const revoking = deferred<void>()
    const error = vi.spyOn(console, 'error')
    for (const [start, settle] of [
      [
        async (fake: FakeApi) => {
          fake.create.mockReturnValueOnce(creating.promise)
          await user.click(screen.getByRole('button', { name: '招待する' }))
          await user.type(screen.getByLabelText('メールアドレス（必須）'), 'a@example.test')
          await user.click(
            within(screen.getByRole('dialog')).getByRole('button', { name: '招待する' }),
          )
        },
        () => creating.reject(NETWORK),
      ],
      [
        async (fake: FakeApi) => {
          fake.resend.mockReturnValueOnce(resending.promise)
          await user.click(
            screen.getByRole('button', { name: 'hanako@example.test への招待を送り直す' }),
          )
        },
        () => resending.resolve(invitationOf({ invitationId: 11 })),
      ],
      [
        async (fake: FakeApi) => {
          fake.cancel.mockReturnValueOnce(revoking.promise)
          await user.click(
            screen.getByRole('button', { name: 'taro@example.test への招待を取り消す' }),
          )
          await user.click(
            within(screen.getByRole('alertdialog')).getByRole('button', { name: '取り消す' }),
          )
        },
        () => revoking.reject(NETWORK),
      ],
    ] as const) {
      const fake = fakeApi()
      const { unmount } = renderPage(fake)
      await rowsShown()
      await start(fake)
      unmount()
      await act(async () => settle())
      expect(fake.list).toHaveBeenCalledTimes(1)
    }
    expect(error).not.toHaveBeenCalled()
  })
})

describe('InvitationAdminPage admin forbidden (U4)', () => {
  it('shows the forbidden view when the list answers 403 ACCESS_DENIED', async () => {
    const fake = fakeApi()
    fake.list.mockRejectedValueOnce(apiError(403, 'ACCESS_DENIED'))
    renderInShell(fake)

    await expectForbiddenView()
    expect(fake.list).toHaveBeenCalledTimes(1)
  })

  it('closes the invite dialog and shows the forbidden view when inviting answers 403', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    fake.create.mockRejectedValueOnce(apiError(403, 'ACCESS_DENIED'))
    renderInShell(fake)
    await rowsShown()
    await user.click(screen.getByRole('button', { name: '招待する' }))
    const dialog = screen.getByRole('dialog')
    await user.type(screen.getByLabelText('メールアドレス（必須）'), 'new@example.test')
    await user.click(within(dialog).getByRole('button', { name: '招待する' }))

    await expectForbiddenView()
    expect(fake.create).toHaveBeenCalledTimes(1)
    expect(fake.list).toHaveBeenCalledTimes(1)
  })

  it('shows the forbidden view without reloading when resending answers 403', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    fake.resend.mockRejectedValueOnce(apiError(403, 'ACCESS_DENIED'))
    renderInShell(fake)
    await rowsShown()
    await user.click(screen.getByRole('button', { name: 'hanako@example.test への招待を送り直す' }))

    await expectForbiddenView()
    expect(fake.resend).toHaveBeenCalledTimes(1)
    expect(fake.list).toHaveBeenCalledTimes(1)
  })
})

describe('InvitationAdminPage language, privacy and accessibility', () => {
  it('shows every text in English on an English screen', async () => {
    const fake = fakeApi()
    fake.resend.mockRejectedValueOnce(apiError(404, 'INVITATION_NOT_FOUND'))
    renderPage(fake, ['en-US'])
    await screen.findByRole('table', { name: 'Pending invitations' })
    expect(screen.getByRole('heading', { level: 1, name: 'User invitations' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Invite' })).toBeEnabled()
    expect(screen.getAllByText('Sep 26, 2026, 21:00 GMT+9').length).toBeGreaterThan(0)
    const user = userEvent.setup()
    await user.click(
      screen.getByRole('button', { name: 'Resend the invitation to hanako@example.test' }),
    )
    await waitFor(() => expect(failureAlert()).toHaveTextContent('This invitation was not found.'))
    expect(screen.getByRole('button', { name: 'Dismiss the message' })).toBeInTheDocument()
  })

  // この1件だけ上限を 15 秒にする（設定全体の既定の 5 秒は変えない）。すべての流れ（ページ送り・招待3回・送り直し2回・
  // 取り消し2回）を1件で操作するため時間がかかり、CI で既定の 5 秒を超えて失敗した（Intent 260928-quality-followup の
  // FR3.2）。原因は確かめておらず（要件の F1: B）、失敗したときにかかった時間を出す（FR3.3）。
  it(
    'keeps addresses and names out of storage, the URL and the console in every flow',
    async () => {
      const startedAt = performance.now()
      onTestFailed(() => {
        // このテストは console の各メソッドが呼ばれないことを確かめるため、console ではなく標準エラーへ直接書く。
        // 出すのはテストの名前と時間だけで、メールアドレス・氏名は出さない（NFR5）。
        const elapsedMs = Math.round(performance.now() - startedAt)
        process.stderr.write(
          `[diagnostic] "keeps addresses and names out of storage, the URL and the console in every flow" failed after ${elapsedMs} ms (timeout ${LEAK_CHECK_TIMEOUT_MS} ms)\n`,
        )
      })
      const user = userEvent.setup()
      const leakEmail = 'invitation-leak-check@example.test'
      const leakName = '招待確認用の氏名'
      const methods = ['log', 'info', 'warn', 'error', 'debug'] as const
      const spies = methods.map((method) => vi.spyOn(console, method))
      localStorage.clear()
      sessionStorage.clear()
      const leakRow = invitationOf({ invitationId: 11, email: leakEmail, invitedBy: leakName })
      const fake = fakeApi(pageOf({ items: [leakRow, ...rowsOf(19, 100)], total: 21 }))
      fake.create
        .mockResolvedValueOnce(invitationOf({ invitationId: 50, email: leakEmail }))
        .mockRejectedValueOnce(
          apiError(409, 'INVITATION_ALREADY_PENDING', { invitationId: 11, page: 1 }),
        )
        .mockRejectedValueOnce(NETWORK)
      fake.resend
        .mockResolvedValueOnce(
          invitationOf({ invitationId: 11, email: leakEmail, sendResult: 'FAILED' }),
        )
        .mockRejectedValueOnce(NETWORK)
      fake.cancel.mockRejectedValueOnce(NETWORK).mockResolvedValueOnce(undefined)
      const href = window.location.href
      renderPage(fake)
      await rowsShown()
      const location = screen.getByTestId('location').textContent

      await user.click(screen.getByRole('button', { name: '次へ' }))
      await user.click(screen.getByRole('button', { name: '前へ' }))
      await screen.findByText(leakEmail)
      for (let i = 0; i < 3; i++) {
        await user.click(screen.getByRole('button', { name: '招待する' }))
        await user.type(screen.getByLabelText('メールアドレス（必須）'), leakEmail)
        await user.click(
          within(screen.getByRole('dialog')).getByRole('button', { name: '招待する' }),
        )
        await waitFor(() =>
          expect(
            within(document.body).queryByRole('button', { name: '送信しています' }),
          ).toBeNull(),
        )
        if (screen.queryByRole('dialog')) {
          await user.click(
            within(screen.getByRole('dialog')).getByRole('button', { name: 'やめる' }),
          )
        }
        await rowsShown()
      }
      for (let i = 0; i < 2; i++) {
        await user.click(
          await screen.findByRole('button', { name: `${leakEmail} への招待を送り直す` }),
        )
        await waitFor(() => expect(screen.getByTestId('invitation-failure-alert')).toBeVisible())
      }
      for (let i = 0; i < 2; i++) {
        await rowsShown()
        await user.click(screen.getByRole('button', { name: `${leakEmail} への招待を取り消す` }))
        await user.click(
          within(screen.getByRole('alertdialog')).getByRole('button', { name: '取り消す' }),
        )
        await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
      }

      const stored = [localStorage, sessionStorage].flatMap((storage) =>
        Array.from({ length: storage.length }, (_, index) => {
          const key = storage.key(index) ?? ''
          return `${key}=${storage.getItem(key) ?? ''}`
        }),
      )
      for (const entry of stored) {
        expect(entry).not.toContain(leakEmail)
        expect(entry).not.toContain(leakName)
      }
      expect(window.location.href).toBe(href)
      expect(screen.getByTestId('location').textContent).toBe(location)
      for (const [index, spy] of spies.entries()) {
        const args = JSON.stringify(spy.mock.calls)
        expect(args, `console.${methods[index]} got an address or a name`).not.toContain(leakEmail)
        expect(args, `console.${methods[index]} got an address or a name`).not.toContain(leakName)
        expect(spy, `console.${methods[index]} was called`).not.toHaveBeenCalled()
      }
    },
    LEAK_CHECK_TIMEOUT_MS,
  )

  it('has no accessibility violations with rows', async () => {
    const fake = fakeApi()
    const { container } = renderPage(fake)
    await rowsShown()
    expect(await axe(container)).toHaveNoViolations()
  })
})
