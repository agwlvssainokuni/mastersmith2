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
// 利用者の管理の画面のテスト（useUserAdmin を通す。frontend-components.md の 8節、functional-spec.md の 4〜6節・
// D1〜D4・D9〜D14・D17・D20、機能設計の承認の場の R-01〜R-06、performance-design.md の 2.4・3.3、security-design.md の
// 6節、計画 8節の D-5〜D-7、U4 のレビューの R-04、NFR1.2・NFR3.1・NFR3.2・NFR5.4・NFR5.5・NFR7.2・NFR8.1・NFR8.3）。
// API の関数を差し替え、答えを返さない約束で止めて途中の表示を見る。時間帯と今の時刻を固定する。
// 骨組みの useAdminForbidden に渡したパスを確かめるため、本物の口を包んで渡したパスを記録する（判定と S6 は本物のまま）。
// 権限が無い（403・ACCESS_DENIED）の S6 は、画面を骨組みの ShellLayout の中に本物の登録と管理者の提供元で描いて確かめる。
import { act, fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent, { type UserEvent } from '@testing-library/user-event'
import { afterEach, beforeEach, describe, expect, it, vi, type Mock } from 'vitest'
import { axe } from 'vitest-axe'
import { FONT_SIZES } from '../../app/display-settings/displaySettingsTypes'
import type { ApiError } from '../../shared/api-client/apiError'
import type { AdminUser, AdminUserPage, ProfileRequest } from './api/types'
import type { UserAdminApi } from './api/userAdminApi'
import { registration } from './registration'
import { pageOf, rowsOf, sampleRows, selfRow, userOf } from './testing/fixtures'
import { adminProvider, renderUserAdmin } from './testing/renderUserAdmin'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { UserAdminPage } from './UserAdminPage'

const recorded = vi.hoisted(() => ({ paths: [] as string[] }))

vi.mock('../../app/admin-forbidden/AdminForbiddenProvider', async (importOriginal) => {
  const actual =
    await importOriginal<typeof import('../../app/admin-forbidden/AdminForbiddenProvider')>()
  return {
    ...actual,
    useAdminForbidden: () => {
      const handle = actual.useAdminForbidden()
      return (error: unknown, apiPath: string) => {
        recorded.paths.push(apiPath)
        return handle(error, apiPath)
      }
    },
  }
})

/** 2026-10-03 10:00 JST */
const NOW = new Date('2026-10-03T01:00:00Z')

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
      detail: 'user-admin-detail-marker',
      title: 'user-admin-detail-marker',
      ...extra,
    },
    enumerable: false,
  })
  return error
}

const NETWORK: ApiError = { kind: 'network' }

type ListFn = (page: number, searchText: string) => Promise<AdminUserPage>
type OperationFn = (userId: number) => Promise<void>

interface FakeApi {
  api: UserAdminApi
  list: Mock<ListFn>
  grant: Mock<OperationFn>
  revoke: Mock<OperationFn>
  suspend: Mock<OperationFn>
  resume: Mock<OperationFn>
  reset: Mock<OperationFn>
  profile: Mock<(userId: number, request: ProfileRequest) => Promise<void>>
}

function fakeApi(firstPage: AdminUserPage = pageOf()): FakeApi {
  const list = vi.fn<ListFn>(() => Promise.resolve(firstPage))
  const op = () => vi.fn<OperationFn>(() => Promise.resolve())
  const grant = op()
  const revoke = op()
  const suspend = op()
  const resume = op()
  const reset = op()
  const profile = vi.fn<(userId: number, request: ProfileRequest) => Promise<void>>(() =>
    Promise.resolve(),
  )
  return {
    api: {
      listUsers: list,
      grantAdmin: grant,
      revokeAdmin: revoke,
      suspendUser: suspend,
      resumeUser: resume,
      resetLoginFailures: reset,
      updateProfile: profile,
    },
    list,
    grant,
    revoke,
    suspend,
    resume,
    reset,
    profile,
  }
}

interface RenderPageOptions {
  languages?: readonly string[]
  withShell?: boolean
  admin?: boolean
}

function renderPage(fake: FakeApi, options: RenderPageOptions = {}) {
  const { languages, withShell = false, admin = withShell } = options
  return renderUserAdmin(<UserAdminPage api={fake.api} timeZone="Asia/Tokyo" now={() => NOW} />, {
    languages,
    withShell,
    provider: admin ? adminProvider() : undefined,
    registrations: withShell ? [registration] : undefined,
  })
}

async function rowsShown(): Promise<void> {
  await screen.findByRole('table', { name: '利用者の一覧' })
  await waitFor(() =>
    expect(screen.getByTestId('useradmin-status')).not.toHaveTextContent('読み込'),
  )
}

function heading(): HTMLElement {
  return screen.getByRole('heading', { level: 2, name: '利用者の一覧' })
}

function actionsButton(row: AdminUser): HTMLElement {
  return within(screen.getByTestId(`useradmin-row-actions-${row.userId}`)).getByRole('button')
}

async function choose(user: UserEvent, row: AdminUser, item: string | RegExp): Promise<void> {
  await user.click(actionsButton(row))
  await user.click(await screen.findByRole('menuitem', { name: item }))
}

function failureAlert(): HTMLElement {
  return screen.getByTestId('useradmin-failure-alert')
}

const TARGET = userOf({ userId: 11, email: 'sato.hanako@example.com', displayName: '佐藤 花子' })

beforeEach(() => {
  recorded.paths.length = 0
})

afterEach(() => {
  vi.useRealTimers()
  vi.restoreAllMocks()
  resetDisplayTestState()
})

describe('UserAdminPage states', () => {
  it('reads the first page without search on open and announces the range', async () => {
    const fake = fakeApi()
    renderPage(fake)
    expect(screen.getByRole('heading', { level: 1, name: '利用者の管理' })).toBeInTheDocument()
    expect(screen.getByTestId('useradmin-status')).toHaveTextContent('利用者を読み込んでいます…')
    expect(screen.getByTestId('useradmin-skeleton')).toHaveAttribute('aria-hidden', 'true')
    await rowsShown()
    expect(fake.list.mock.calls).toEqual([[1, '']])
    expect(screen.getByTestId('useradmin-status')).toHaveTextContent('1〜5 件目 / 全 5 件')
    expect(screen.getByText('1 / 1ページ（全5件）')).toBeInTheDocument()
  })

  it('keeps the rows while reloading and marks the row actions as processing', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: rowsOf(20, 1), total: 43 }))
    renderPage(fake)
    await rowsShown()
    const next = deferred<AdminUserPage>()
    fake.list.mockReturnValueOnce(next.promise)
    await user.click(screen.getByRole('button', { name: '次へ' }))
    expect(screen.getByTestId('useradmin-status')).toHaveTextContent('読み込んでいます…')
    expect(screen.getByText('user-01@example.com')).toBeInTheDocument()
    expect(screen.getByTestId('useradmin-table')).toHaveAttribute('aria-busy', 'true')
    expect(
      screen.getByRole('button', { name: '利用 01（user-01@example.com）の操作（処理中）' }),
    ).toHaveAttribute('aria-disabled', 'true')
    await act(async () => next.resolve(pageOf({ items: rowsOf(20, 21), page: 2, total: 43 })))
    expect(await screen.findByText('user-21@example.com')).toBeInTheDocument()
  })

  it('shows the empty search message with the search text', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    renderPage(fake)
    await rowsShown()
    fake.list.mockResolvedValueOnce(pageOf({ items: [], total: 0 }))
    await user.type(screen.getByRole('searchbox', { name: '検索' }), 'いない人')
    await user.click(screen.getByRole('button', { name: '検索' }))
    expect(await screen.findByTestId('useradmin-empty-search')).toHaveTextContent(
      '「いない人」に当たる利用者はいません。',
    )
    expect(screen.queryByRole('table')).toBeNull()
  })

  it('reads the last page once and stops with the empty page when it is empty again', async () => {
    const fake = fakeApi(pageOf({ items: [], total: 25 }))
    renderPage(fake)
    expect(await screen.findByTestId('useradmin-empty-page')).toHaveTextContent(
      'このページに利用者はいません。',
    )
    expect(fake.list.mock.calls).toEqual([
      [1, ''],
      [2, ''],
    ])
    expect(screen.getByText('2 / 2ページ（全25件）')).toBeInTheDocument()
  })

  it('moves to the last page given by correctedPage', async () => {
    const fake = fakeApi()
    fake.list
      .mockResolvedValueOnce(pageOf({ items: [], total: 25 }))
      .mockResolvedValueOnce(pageOf({ items: rowsOf(5, 21), page: 2, total: 25 }))
    renderPage(fake)
    await screen.findByText('user-25@example.com')
    expect(screen.getByText('2 / 2ページ（全25件）')).toBeInTheDocument()
  })

  it('shows the load error, loads again and focuses the heading or the retry button', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    fake.list.mockRejectedValueOnce(apiError(500)).mockRejectedValueOnce(NETWORK)
    renderPage(fake)
    expect(await screen.findByTestId('useradmin-load-error')).toHaveTextContent(
      '利用者の一覧を読み込めませんでした。',
    )
    expect(screen.queryByRole('table')).toBeNull()
    await user.click(screen.getByRole('button', { name: 'もう一度読み込む' }))
    await waitFor(() =>
      expect(screen.getByRole('button', { name: 'もう一度読み込む' })).toHaveFocus(),
    )
    await user.click(screen.getByRole('button', { name: 'もう一度読み込む' }))
    await rowsShown()
    await waitFor(() => expect(heading()).toHaveFocus())
    expect(document.body.textContent).not.toContain('user-admin-detail-marker')
  })

  it('treats a 400 of the first load and a 400 without a code as a load error', async () => {
    const fake = fakeApi()
    fake.list.mockRejectedValueOnce(apiError(400, 'VALIDATION_FAILED'))
    const { unmount } = renderPage(fake)
    expect(await screen.findByTestId('useradmin-load-error')).toBeInTheDocument()
    unmount()
    const other = fakeApi()
    other.list.mockRejectedValueOnce(apiError(400))
    renderPage(other)
    expect(await screen.findByTestId('useradmin-load-error')).toBeInTheDocument()
  })
})

describe('UserAdminPage reading', () => {
  it('uses only the answer of the last reload', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    renderPage(fake)
    await rowsShown()
    const stale = deferred<AdminUserPage>()
    const latest = deferred<AdminUserPage>()
    fake.list.mockReturnValueOnce(stale.promise).mockReturnValueOnce(latest.promise)
    fake.profile.mockRejectedValue(apiError(500))
    await choose(user, TARGET, '氏名・言語を直す')
    await user.click(screen.getByRole('button', { name: '保存' }))
    await screen.findByText('保存できませんでした。時間をおいて、もう一度保存してください。')
    await user.click(screen.getByRole('button', { name: '保存' }))
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(3))
    await act(async () =>
      latest.resolve(pageOf({ items: [userOf({ userId: 21, email: 'latest@example.com' })] })),
    )
    await act(async () =>
      stale.resolve(pageOf({ items: [userOf({ userId: 22, email: 'stale@example.com' })] })),
    )
    expect(screen.getByText('latest@example.com')).toBeInTheDocument()
    expect(screen.queryByText('stale@example.com')).toBeNull()
  })

  it('ignores an answer that arrives after leaving the screen', async () => {
    const late = deferred<AdminUserPage>()
    const fake = fakeApi()
    fake.list.mockReturnValueOnce(late.promise)
    const error = vi.spyOn(console, 'error')
    const { unmount } = renderPage(fake)
    unmount()
    await act(async () => late.resolve(pageOf()))
    expect(error).not.toHaveBeenCalled()
  })

  it('never reloads on its own as time passes', async () => {
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
})

describe('UserAdminPage pages and search', () => {
  it('keeps the search text when paging and reads page 1 when searching', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: rowsOf(20, 1), total: 43 }))
    renderPage(fake)
    await rowsShown()
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(2))
    await rowsShown()
    await user.type(screen.getByRole('searchbox', { name: '検索' }), '  利用  ')
    await user.keyboard('{Enter}')
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(3))
    await rowsShown()
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(4))
    expect(fake.list.mock.calls).toEqual([
      [1, ''],
      [2, ''],
      [1, '利用'],
      [2, '利用'],
    ])
    expect(screen.getByRole('searchbox', { name: '検索' })).toHaveValue('  利用  ')
  })

  it('keeps the page and the search text after a search 400 so that next reads page 4', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    fake.list.mockImplementation((page) =>
      Promise.resolve(pageOf({ items: rowsOf(20, (page - 1) * 20 + 1), page, total: 100 })),
    )
    renderPage(fake)
    await rowsShown()
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await screen.findByText('user-21@example.com')
    await rowsShown()
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await screen.findByText('user-41@example.com')
    await rowsShown()
    fake.list.mockRejectedValueOnce(apiError(400, 'VALIDATION_FAILED'))
    await user.type(screen.getByRole('searchbox', { name: '検索' }), 'x')
    await user.click(screen.getByRole('button', { name: '検索' }))
    expect(await screen.findByTestId('useradmin-search-error')).toHaveTextContent(
      '検索の文字が正しくありません。',
    )
    expect(screen.getByText('user-41@example.com')).toBeInTheDocument()
    expect(screen.getByRole('searchbox', { name: '検索' })).toHaveValue('x')
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await screen.findByText('user-61@example.com')
    expect(fake.list.mock.calls.at(-1)).toEqual([4, ''])
  })

  it('does not send a search over the limit and shows the limit under the input', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    renderPage(fake)
    await rowsShown()
    fireEvent.change(screen.getByRole('searchbox', { name: '検索' }), {
      target: { value: 'a'.repeat(255) },
    })
    await user.click(screen.getByRole('button', { name: '検索' }))
    expect(screen.getByTestId('useradmin-search-error')).toHaveTextContent(
      '検索の文字は 254 文字までにしてください。',
    )
    expect(fake.list).toHaveBeenCalledTimes(1)
  })

  it('clears the search, the input and its error and reads page 1 without search', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    renderPage(fake)
    await rowsShown()
    await user.type(screen.getByRole('searchbox', { name: '検索' }), '佐藤')
    await user.keyboard('{Enter}')
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(2))
    await rowsShown()
    await user.click(screen.getByRole('button', { name: '検索を消す' }))
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(3))
    expect(fake.list.mock.calls.at(-1)).toEqual([1, ''])
    expect(screen.getByRole('searchbox', { name: '検索' })).toHaveValue('')
  })

  it('moves the focus to the heading only when the pressed pager button becomes disabled', async () => {
    const user = userEvent.setup()
    const fake = fakeApi()
    fake.list.mockImplementation((page) =>
      Promise.resolve(
        pageOf({ items: rowsOf(page === 3 ? 3 : 20, (page - 1) * 20 + 1), page, total: 43 }),
      ),
    )
    renderPage(fake)
    await rowsShown()
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await screen.findByText('user-21@example.com')
    await rowsShown()
    expect(screen.getByRole('button', { name: '次へ' })).toHaveFocus()
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await screen.findByText('user-43@example.com')
    await waitFor(() => expect(heading()).toHaveFocus())
  })
})

describe('UserAdminPage double sending', () => {
  it('does not add requests for paging, searching or opening a row while reloading', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET, ...rowsOf(19, 30)], total: 43 }))
    renderPage(fake)
    await rowsShown()
    const pending = deferred<AdminUserPage>()
    fake.list.mockReturnValueOnce(pending.promise)
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await user.click(screen.getByRole('button', { name: '検索' }))
    await user.click(screen.getByRole('button', { name: '検索を消す' }))
    await user.click(actionsButton(TARGET))
    expect(screen.queryByRole('menu')).toBeNull()
    expect(fake.list).toHaveBeenCalledTimes(2)
    await act(async () => pending.resolve(pageOf({ items: rowsOf(20, 21), page: 2, total: 43 })))
  })

  it('sends the operation and the save only once when pressed twice', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    const operation = deferred<void>()
    fake.suspend.mockReturnValueOnce(operation.promise)
    renderPage(fake)
    await rowsShown()
    await choose(user, TARGET, '利用を止める')
    const submit = screen.getByRole('button', { name: '利用を止める' })
    fireEvent.click(submit)
    fireEvent.click(submit)
    expect(fake.suspend).toHaveBeenCalledTimes(1)
    await act(async () => operation.resolve())
    await rowsShown()
    const save = deferred<void>()
    fake.profile.mockReturnValueOnce(save.promise)
    await choose(user, TARGET, '氏名・言語を直す')
    const saveButton = screen.getByRole('button', { name: '保存' })
    fireEvent.click(saveButton)
    fireEvent.click(saveButton)
    expect(fake.profile).toHaveBeenCalledTimes(1)
    await act(async () => save.resolve())
  })

  it.each([
    ['a 409 conflict', () => Promise.reject(apiError(409, 'USER_ADMIN_BUSY'))],
    ['a network failure', () => Promise.reject(NETWORK)],
    [
      'an unexpected exception',
      () => {
        throw new Error('unexpected')
      },
    ],
  ])('lets the next operation through after %s while sending', async (_name, failure) => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    fake.suspend.mockImplementationOnce(failure)
    renderPage(fake)
    await rowsShown()
    await choose(user, TARGET, '利用を止める')
    await user.click(screen.getByRole('button', { name: '利用を止める' }))
    await waitFor(() => expect(failureAlert()).toBeInTheDocument())
    await rowsShown()
    await choose(user, TARGET, '利用を止める')
    await user.click(screen.getByRole('button', { name: '利用を止める' }))
    await waitFor(() => expect(fake.suspend).toHaveBeenCalledTimes(2))
  })

  it.each([
    ['a 400 of the search', () => Promise.reject(apiError(400, 'VALIDATION_FAILED')), '検索'],
    ['a network failure', () => Promise.reject(NETWORK), 'もう一度読み込む'],
    [
      'an unexpected exception',
      () => {
        throw new Error('unexpected')
      },
      'もう一度読み込む',
    ],
  ])('lets the next reading through after %s while reading', async (_name, failure, next) => {
    const user = userEvent.setup()
    const fake = fakeApi()
    renderPage(fake)
    await rowsShown()
    fake.list.mockImplementationOnce(failure)
    await user.type(screen.getByRole('searchbox', { name: '検索' }), '佐藤')
    await user.click(screen.getByRole('button', { name: '検索' }))
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(2))
    await user.click(await screen.findByRole('button', { name: next }))
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(3))
  })

  it('lets the first reading and an operation through after leaving while sending', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    fake.suspend.mockReturnValueOnce(new Promise(() => {}))
    const { unmount } = renderPage(fake)
    await rowsShown()
    await choose(user, TARGET, '利用を止める')
    await user.click(screen.getByRole('button', { name: '利用を止める' }))
    unmount()
    const again = fakeApi(pageOf({ items: [TARGET] }))
    renderPage(again)
    await rowsShown()
    await choose(user, TARGET, '利用を止める')
    await user.click(screen.getByRole('button', { name: '利用を止める' }))
    await waitFor(() => expect(again.suspend).toHaveBeenCalledTimes(1))
    expect(again.list).toHaveBeenCalled()
  })
})

describe('UserAdminPage operation results', () => {
  it.each([
    [
      'grantAdmin',
      userOf({ userId: 31, displayName: '甲 一' }),
      '管理者の印を付ける',
      'grant',
      '甲 一さんに管理者の印を付けました',
    ],
    [
      'revokeAdmin',
      userOf({ userId: 32, displayName: '乙 二', admin: true }),
      '管理者の印を外す',
      'revoke',
      '乙 二さんの管理者の印を外しました',
    ],
    [
      'suspend',
      userOf({ userId: 33, displayName: '丙 三' }),
      '利用を止める',
      'suspend',
      '丙 三さんの利用を止めました',
    ],
    [
      'resume',
      userOf({ userId: 34, displayName: '丁 四', suspended: true }),
      '停止を解く',
      'resume',
      '丁 四さんの停止を解きました',
    ],
    [
      'resetFailures',
      userOf({ userId: 35, displayName: '戊 五', resettable: true, locked: true }),
      'ロックを解除（失敗回数を戻す）',
      'reset',
      '戊 五さんのロックを解除しました',
    ],
  ] as const)(
    'runs %s, shows the toast, reloads and focuses the same row',
    async (_action, row, item, method, toastText) => {
      const user = userEvent.setup()
      const fake = fakeApi(pageOf({ items: [row] }))
      renderPage(fake)
      await rowsShown()
      await choose(user, row, item)
      const dialog = screen.getByRole('alertdialog')
      await user.click(within(dialog).getAllByRole('button').at(-1) as HTMLElement)
      expect(await screen.findByText(toastText)).toBeInTheDocument()
      expect(fake[method]).toHaveBeenCalledWith(row.userId)
      await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(2))
      await waitFor(() => expect(actionsButton(row)).toHaveFocus())
      expect(screen.queryByRole('alertdialog')).toBeNull()
    },
  )

  it('cancels the confirm and the edit without requests and returns the focus to the row', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    renderPage(fake)
    await rowsShown()
    await choose(user, TARGET, '利用を止める')
    expect(screen.getByRole('button', { name: 'やめる' })).toHaveFocus()
    await user.click(screen.getByRole('button', { name: 'やめる' }))
    expect(screen.queryByRole('alertdialog')).toBeNull()
    await waitFor(() => expect(actionsButton(TARGET)).toHaveFocus())
    await choose(user, TARGET, '氏名・言語を直す')
    await user.type(screen.getByRole('textbox', { name: '氏名（必須）' }), '子')
    await user.keyboard('{Escape}')
    expect(screen.queryByRole('dialog')).toBeNull()
    await waitFor(() => expect(actionsButton(TARGET)).toHaveFocus())
    expect(fake.suspend).not.toHaveBeenCalled()
    expect(fake.profile).not.toHaveBeenCalled()
    expect(fake.list).toHaveBeenCalledTimes(1)
  })

  it('focuses the heading when the row is no longer on the page after the reload', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET, userOf({ userId: 12 })] }))
    renderPage(fake)
    await rowsShown()
    fake.list.mockResolvedValueOnce(pageOf({ items: [userOf({ userId: 12 })] }))
    await choose(user, TARGET, '利用を止める')
    await user.click(screen.getByRole('button', { name: '利用を止める' }))
    await waitFor(() => expect(heading()).toHaveFocus())
  })

  it.each([
    ['USER_NOT_FOUND', 404, '対象の利用者が見つかりません。一覧を読み直しました。'],
    ['USER_ADMIN_SELF_OPERATION', 409, '自分自身にはこの操作をできません。'],
    ['USER_ADMIN_TARGET_SUSPENDED', 409, '佐藤 花子さんは利用停止中です。'],
    ['USER_ADMIN_NO_CHANGE', 409, '佐藤 花子さんはすでにこの状態です。'],
    ['USER_ADMIN_LAST_ADMIN', 409, '管理者が1人もいなくなるため受け付けられません。'],
    ['USER_ADMIN_BUSY', 409, 'ほかの処理と重なったため、操作できませんでした。'],
  ] as const)('shows the reason of %s and reloads', async (code, status, text) => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    fake.suspend.mockRejectedValueOnce(apiError(status, code))
    renderPage(fake)
    await rowsShown()
    await choose(user, TARGET, '利用を止める')
    await user.click(screen.getByRole('button', { name: '利用を止める' }))
    await waitFor(() => expect(failureAlert()).toHaveTextContent(text))
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(2))
    expect(document.body.textContent).not.toContain('user-admin-detail-marker')
  })

  it('shows the general message for an unknown code, a 400 without a code, a 5xx and a network failure', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    const failures = [apiError(409, 'SOMETHING_NEW'), apiError(400), apiError(503), NETWORK]
    renderPage(fake)
    await rowsShown()
    for (const [index, failure] of failures.entries()) {
      fake.suspend.mockRejectedValueOnce(failure)
      await choose(user, TARGET, '利用を止める')
      await user.click(screen.getByRole('button', { name: '利用を止める' }))
      await waitFor(() =>
        expect(failureAlert()).toHaveTextContent(
          '操作を完了できませんでした。一覧を読み直して状態を確かめてください。',
        ),
      )
      await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(index + 2))
      await rowsShown()
    }
    expect(document.body.textContent).not.toContain('user-admin-detail-marker')
  })

  it('removes the notice with the close button but not with a reload', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    fake.suspend.mockRejectedValueOnce(apiError(409, 'USER_ADMIN_BUSY'))
    renderPage(fake)
    await rowsShown()
    await choose(user, TARGET, '利用を止める')
    await user.click(screen.getByRole('button', { name: '利用を止める' }))
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(2))
    await rowsShown()
    expect(failureAlert()).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '知らせを閉じる' }))
    expect(screen.queryByTestId('useradmin-failure-alert')).toBeNull()
  })

  it('does not start anything for a disabled item of the own row', async () => {
    const user = userEvent.setup()
    const self = selfRow()
    const fake = fakeApi(pageOf({ items: [self] }))
    renderPage(fake)
    await rowsShown()
    await user.click(actionsButton(self))
    await user.click(screen.getByRole('menuitem', { name: '管理者の印を外す' }))
    expect(screen.queryByRole('alertdialog')).toBeNull()
    expect(fake.revoke).not.toHaveBeenCalled()
  })
})

describe('UserAdminPage profile', () => {
  it('checks the name on the screen and does not send an invalid name', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    renderPage(fake)
    await rowsShown()
    await choose(user, TARGET, '氏名・言語を直す')
    const name = screen.getByRole('textbox', { name: '氏名（必須）' })
    await user.clear(name)
    await user.click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByText('氏名を入れてください。')).toBeInTheDocument()
    await waitFor(() => expect(name).toHaveFocus())
    expect(fake.profile).not.toHaveBeenCalled()
  })

  it('sends the trimmed name and the language, closes, shows the toast and reloads', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    renderPage(fake)
    await rowsShown()
    await choose(user, TARGET, '氏名・言語を直す')
    const name = screen.getByRole('textbox', { name: '氏名（必須）' })
    await user.clear(name)
    await user.type(name, '　佐藤 春子 ')
    await user.click(screen.getByRole('radio', { name: 'English' }))
    await user.click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByText('佐藤 春子さんの氏名と言語を直しました')).toBeInTheDocument()
    expect(fake.profile).toHaveBeenCalledWith(11, { displayName: '佐藤 春子', language: 'en' })
    expect(screen.queryByRole('dialog')).toBeNull()
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(2))
    expect(document.documentElement).toHaveAttribute('lang', 'ja')
  })

  it('shows the server field errors under the fields and the form message otherwise', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    fake.profile
      .mockRejectedValueOnce(
        apiError(400, 'VALIDATION_FAILED', {
          fieldErrors: [
            { field: 'displayName', reason: 'INVALID_CHARACTER' },
            { field: 'language', reason: 'INVALID_VALUE' },
          ],
        }),
      )
      .mockRejectedValueOnce(
        apiError(400, 'VALIDATION_FAILED', { fieldErrors: [{ field: 'email', reason: 'X' }] }),
      )
    renderPage(fake)
    await rowsShown()
    await choose(user, TARGET, '氏名・言語を直す')
    await user.click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByText('氏名に使えない文字が含まれています。')).toBeInTheDocument()
    expect(screen.getByTestId('useradmin-edit-language-error')).toHaveTextContent(
      '言語を選んでください。',
    )
    await waitFor(() => expect(screen.getByRole('textbox', { name: '氏名（必須）' })).toHaveFocus())
    await user.click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByTestId('useradmin-edit-alert')).toHaveTextContent(
      '入力の内容を確かめてください。',
    )
    expect(fake.list).toHaveBeenCalledTimes(1)
  })

  it('shows not-found for a 404 and reloads in the background', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    fake.profile.mockRejectedValueOnce(apiError(404, 'USER_NOT_FOUND'))
    renderPage(fake)
    await rowsShown()
    await choose(user, TARGET, '氏名・言語を直す')
    await user.click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByTestId('useradmin-edit-alert')).toHaveTextContent(
      '対象の利用者が見つかりません。一覧を読み直してください。',
    )
    expect(screen.getByRole('button', { name: '保存' })).toBeDisabled()
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(2))
    expect(screen.getByRole('dialog')).toBeInTheDocument()
  })

  it('keeps the input after a general failure and reloads in the background', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    fake.profile.mockRejectedValueOnce(apiError(500))
    renderPage(fake)
    await rowsShown()
    await choose(user, TARGET, '氏名・言語を直す')
    const name = screen.getByRole('textbox', { name: '氏名（必須）' })
    await user.type(name, '子')
    await user.click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByTestId('useradmin-edit-alert')).toHaveTextContent(
      '保存できませんでした。',
    )
    expect(name).toHaveValue('佐藤 花子子')
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(2))
  })

  // 計画 9節の Q-C（8KB を超える要求に Tomcat が返す HTML の 400 は code を持たない）。一覧・操作と同じく一般の失敗にする。
  it('treats a save 400 without a code (an HTML 400) as a general failure', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    fake.profile.mockRejectedValueOnce(apiError(400))
    renderPage(fake)
    await rowsShown()
    await choose(user, TARGET, '氏名・言語を直す')
    const name = screen.getByRole('textbox', { name: '氏名（必須）' })
    await user.type(name, '子')
    await user.click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByTestId('useradmin-edit-alert')).toHaveTextContent(
      '保存できませんでした。',
    )
    expect(name).toHaveValue('佐藤 花子子')
    expect(name).not.toHaveAttribute('aria-invalid')
    expect(screen.getByRole('button', { name: '保存' })).toBeEnabled()
    expect(document.body.textContent).not.toContain('user-admin-detail-marker')
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(2))
  })
})

describe('UserAdminPage own profile', () => {
  it('applies the own name and language before the toast, in English, and before the reload', async () => {
    const user = userEvent.setup()
    const self = selfRow()
    const fake = fakeApi(pageOf({ items: [self, TARGET] }))
    renderPage(fake, { withShell: true })
    await rowsShown()
    expect(screen.getByRole('img', { name: '管理 一郎' })).toBeInTheDocument()
    await choose(user, self, '氏名・言語を直す')
    const name = screen.getByRole('textbox', { name: '氏名（必須）' })
    await user.clear(name)
    await user.type(name, '管理 二郎')
    await user.click(screen.getByRole('radio', { name: 'English' }))
    await user.click(screen.getByRole('button', { name: '保存' }))
    await waitFor(() =>
      expect(screen.getByText('Updated the name and language of 管理 二郎')).toBeInTheDocument(),
    )
    await waitFor(() => expect(document.documentElement).toHaveAttribute('lang', 'en'))
    expect(screen.getByRole('img', { name: '管理 二郎' })).toBeInTheDocument()
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(2))
    expect(screen.getByRole('heading', { level: 1, name: 'Users' })).toBeInTheDocument()
  })

  it('does not apply the language of another user to the own screen', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [selfRow(), TARGET] }))
    renderPage(fake, { withShell: true })
    await rowsShown()
    await choose(user, TARGET, '氏名・言語を直す')
    await user.click(screen.getByRole('radio', { name: 'English' }))
    await user.click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByText('佐藤 花子さんの氏名と言語を直しました')).toBeInTheDocument()
    expect(document.documentElement).toHaveAttribute('lang', 'ja')
    expect(screen.getByRole('img', { name: '管理 一郎' })).toBeInTheDocument()
  })
})

describe('UserAdminPage 403 and 401', () => {
  it('passes the list, operation and save 403 with the request path and shows nothing', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    fake.list.mockRejectedValueOnce(apiError(403, 'ACCESS_DENIED'))
    const { unmount } = renderPage(fake)
    await waitFor(() => expect(recorded.paths).toEqual(['/api/admin/users']))
    expect(screen.queryByTestId('useradmin-load-error')).toBeNull()
    unmount()

    const op = fakeApi(pageOf({ items: [TARGET] }))
    op.suspend.mockRejectedValueOnce(apiError(403, 'ACCESS_DENIED'))
    op.profile.mockRejectedValueOnce(apiError(403, 'ACCESS_DENIED'))
    renderPage(op)
    await rowsShown()
    await choose(user, TARGET, '利用を止める')
    await user.click(screen.getByRole('button', { name: '利用を止める' }))
    await waitFor(() => expect(recorded.paths).toContain('/api/admin/users/11/suspend'))
    expect(screen.queryByTestId('useradmin-failure-alert')).toBeNull()
    expect(screen.queryByRole('alertdialog')).toBeNull()
    await choose(user, TARGET, '氏名・言語を直す')
    await user.click(screen.getByRole('button', { name: '保存' }))
    await waitFor(() => expect(recorded.paths).toContain('/api/admin/users/11/profile'))
    expect(screen.queryByTestId('useradmin-edit-alert')).toBeNull()
    expect(op.list).toHaveBeenCalledTimes(1)
  })

  it('replaces the screen with the forbidden view in the shell', async () => {
    const fake = fakeApi()
    fake.list.mockRejectedValueOnce(apiError(403, 'ACCESS_DENIED'))
    renderPage(fake, { withShell: true })
    expect(await screen.findByTestId('admin-forbidden-view')).toBeInTheDocument()
    expect(screen.getByTestId('admin-forbidden-heading')).toHaveTextContent('利用者の管理')
    expect(screen.queryByTestId('useradmin-page')).toBeNull()
  })

  it('treats a 403 without ACCESS_DENIED as a general failure', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    fake.suspend.mockRejectedValueOnce(apiError(403))
    renderPage(fake)
    await rowsShown()
    await choose(user, TARGET, '利用を止める')
    await user.click(screen.getByRole('button', { name: '利用を止める' }))
    await waitFor(() => expect(failureAlert()).toHaveTextContent('操作を完了できませんでした。'))
  })

  it('shows nothing and closes the confirm and the edit dialogs on a 401', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    fake.suspend.mockRejectedValueOnce(apiError(401, 'AUTHENTICATION_REQUIRED'))
    fake.profile.mockRejectedValueOnce(apiError(401, 'AUTHENTICATION_REQUIRED'))
    renderPage(fake)
    await rowsShown()
    await choose(user, TARGET, '利用を止める')
    await user.click(screen.getByRole('button', { name: '利用を止める' }))
    await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
    await choose(user, TARGET, '氏名・言語を直す')
    await user.click(screen.getByRole('button', { name: '保存' }))
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    expect(screen.queryByTestId('useradmin-failure-alert')).toBeNull()
    expect(fake.list).toHaveBeenCalledTimes(1)
  })

  it('ignores the 403 of a stale reload and shows the forbidden view for the latest one (U4 R-04)', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    renderPage(fake, { withShell: true })
    await rowsShown()
    const stale = deferred<AdminUserPage>()
    const latest = deferred<AdminUserPage>()
    fake.list.mockReturnValueOnce(stale.promise).mockReturnValueOnce(latest.promise)
    fake.profile.mockRejectedValue(apiError(500))
    await choose(user, TARGET, '氏名・言語を直す')
    await user.click(screen.getByRole('button', { name: '保存' }))
    await screen.findByTestId('useradmin-edit-alert')
    await user.click(screen.getByRole('button', { name: '保存' }))
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(3))
    await act(async () => stale.reject(apiError(403, 'ACCESS_DENIED')))
    expect(screen.queryByTestId('admin-forbidden-view')).toBeNull()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    await act(async () => latest.reject(apiError(403, 'ACCESS_DENIED')))
    expect(await screen.findByTestId('admin-forbidden-view')).toBeInTheDocument()
    expect(screen.queryByRole('dialog')).toBeNull()
  })
})

describe('UserAdminPage waiting', () => {
  it('shows the slow notice at 5000 ms, not at 4999 ms, and removes it on the answer', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    const operation = deferred<void>()
    fake.suspend.mockReturnValueOnce(operation.promise)
    renderPage(fake)
    await rowsShown()
    await choose(user, TARGET, '利用を止める')
    vi.useFakeTimers()
    fireEvent.click(screen.getByRole('button', { name: '利用を止める' }))
    await act(async () => {
      vi.advanceTimersByTime(4999)
    })
    expect(screen.queryByTestId('useradmin-confirm-slow')).toBeNull()
    await act(async () => {
      vi.advanceTimersByTime(1)
    })
    expect(screen.getByTestId('useradmin-confirm-slow')).toHaveTextContent('時間がかかっています')
    await act(async () => operation.resolve())
    expect(screen.queryByTestId('useradmin-confirm-slow')).toBeNull()
    vi.useRealTimers()
  })
})

describe('UserAdminPage privacy, language and accessibility', () => {
  it('keeps addresses, names and search text out of storage, the URL, the history and the console', async () => {
    const user = userEvent.setup()
    const methods = ['log', 'info', 'warn', 'error', 'debug'] as const
    const spies = methods.map((method) => vi.spyOn(console, method))
    const before = { href: window.location.href, length: window.history.length }
    const fake = fakeApi(pageOf({ items: [TARGET, ...rowsOf(19, 40)], total: 43 }))
    fake.suspend.mockRejectedValueOnce(apiError(409, 'USER_ADMIN_BUSY'))
    fake.profile.mockRejectedValueOnce(NETWORK)
    renderPage(fake)
    await rowsShown()
    await user.type(screen.getByRole('searchbox', { name: '検索' }), '佐藤 花子')
    await user.keyboard('{Enter}')
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(2))
    await rowsShown()
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(3))
    await rowsShown()
    await choose(user, TARGET, '利用を止める')
    await user.click(screen.getByRole('button', { name: '利用を止める' }))
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(4))
    await rowsShown()
    await choose(user, TARGET, '利用を止める')
    await user.click(screen.getByRole('button', { name: '利用を止める' }))
    await waitFor(() => expect(fake.list).toHaveBeenCalledTimes(5))
    await rowsShown()
    await choose(user, TARGET, '氏名・言語を直す')
    await user.click(screen.getByRole('button', { name: '保存' }))
    await screen.findByTestId('useradmin-edit-alert')
    await user.click(screen.getByRole('button', { name: '保存' }))
    await screen.findByText('佐藤 花子さんの氏名と言語を直しました')

    const stored = [localStorage, sessionStorage].flatMap((storage) =>
      Array.from({ length: storage.length }, (_, index) => {
        const key = storage.key(index) ?? ''
        return `${key}=${storage.getItem(key) ?? ''}`
      }),
    )
    for (const value of stored) {
      expect(value).not.toContain('sato.hanako@example.com')
      expect(value).not.toContain('佐藤')
    }
    expect(window.location.href).toBe(before.href)
    expect(window.history.length).toBe(before.length)
    expect(screen.getByTestId('location')).toHaveTextContent('/admin/users')
    for (const spy of spies) {
      expect(spy).not.toHaveBeenCalled()
    }
  })

  it('shows every text in English on an English screen', async () => {
    const user = userEvent.setup()
    const fake = fakeApi(pageOf({ items: [TARGET] }))
    fake.suspend.mockRejectedValueOnce(apiError(409, 'USER_ADMIN_LAST_ADMIN'))
    renderPage(fake, { languages: ['en-US'] })
    await screen.findByRole('table', { name: 'User list' })
    expect(screen.getByRole('heading', { level: 1, name: 'Users' })).toBeInTheDocument()
    expect(screen.getByText('Page 1 of 1 (1 total)')).toBeInTheDocument()
    expect(screen.getByText('Sep 25, 2026, 12:00 GMT+9')).toBeInTheDocument()
    await user.click(
      screen.getByRole('button', { name: 'Actions for 佐藤 花子 (sato.hanako@example.com)' }),
    )
    await user.click(await screen.findByRole('menuitem', { name: 'Suspend' }))
    await user.click(screen.getByRole('button', { name: 'Suspend' }))
    await waitFor(() =>
      expect(failureAlert()).toHaveTextContent('This action would leave no administrators'),
    )
    expect(screen.getByRole('button', { name: 'Dismiss' })).toBeInTheDocument()
  })

  it('has no accessibility violations when shown, loading, failed, empty and with a notice', async () => {
    const pending = fakeApi()
    pending.list.mockReturnValueOnce(new Promise(() => {}))
    const loading = renderPage(pending)
    expect(await axe(loading.container)).toHaveNoViolations()
    loading.unmount()

    const failed = fakeApi()
    failed.list.mockRejectedValueOnce(apiError(500))
    const failedView = renderPage(failed)
    await screen.findByTestId('useradmin-load-error')
    expect(await axe(failedView.container)).toHaveNoViolations()
    failedView.unmount()

    const empty = fakeApi(pageOf({ items: [], total: 0 }))
    const emptyView = renderPage(empty)
    await screen.findByTestId('useradmin-empty-page')
    expect(await axe(emptyView.container)).toHaveNoViolations()
    emptyView.unmount()

    const user = userEvent.setup()
    const shown = fakeApi(pageOf({ items: sampleRows() }))
    shown.suspend.mockRejectedValueOnce(apiError(409, 'USER_ADMIN_BUSY'))
    const shownView = renderPage(shown)
    await rowsShown()
    expect(await axe(shownView.container)).toHaveNoViolations()
    await choose(user, TARGET, '利用を止める')
    await user.click(screen.getByRole('button', { name: '利用を止める' }))
    await waitFor(() => expect(shown.list).toHaveBeenCalledTimes(2))
    await rowsShown()
    expect(failureAlert()).toBeInTheDocument()
    expect(await axe(shownView.container)).toHaveNoViolations()
  })

  it('has no accessibility violations for the six theme and font size combinations', async () => {
    for (const theme of ['light', 'dark'] as const) {
      for (const fontSize of FONT_SIZES) {
        const fake = fakeApi()
        const view = renderUserAdmin(
          <UserAdminPage api={fake.api} timeZone="Asia/Tokyo" now={() => NOW} />,
          { preferences: { language: 'ja', theme, fontSize } },
        )
        await rowsShown()
        // light は属性なし（DisplaySettingsProvider の applyHtmlAttributes）
        await waitFor(() =>
          expect(document.documentElement).toHaveAttribute('data-font-size', fontSize),
        )
        expect(document.documentElement.getAttribute('data-theme')).toBe(
          theme === 'light' ? null : theme,
        )
        expect(await axe(view.container), `${theme}-${fontSize}`).toHaveNoViolations()
        view.unmount()
      }
    }
  })
})
