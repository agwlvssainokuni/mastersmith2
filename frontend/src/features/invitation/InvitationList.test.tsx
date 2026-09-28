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
// 招待中の人の一覧のテスト（frontend-components.md の 7節、functional-spec.md の W1・W2・W7〜W9、D5〜D7・D9・D13、
// NFR1.1・NFR6.4・NFR7.1・NFR7.2・NFR8.2・NFR9.1）。時差を固定して描き、make-you-chic-ui の Table は差し替えない。
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import type { Invitation, InvitationPage } from './api/types'
import { InvitationList, type InvitationListProps } from './InvitationList'
import { invitationOf, pageOf, rowsOf, sampleRows } from './testing/fixtures'
import { renderInvitation } from './testing/renderInvitation'

function renderList(
  overrides: Partial<InvitationListProps> = {},
  languages: readonly string[] = ['ja-JP'],
) {
  const props: InvitationListProps = {
    list: pageOf(),
    page: 1,
    loadState: 'loaded',
    loadFailureKey: null,
    invitationEnabled: true,
    resendingIds: new Set(),
    highlightedId: null,
    focusTarget: null,
    language: languages[0]?.startsWith('en') ? 'en' : 'ja',
    timeZone: 'Asia/Tokyo',
    onResend: vi.fn(),
    onRevoke: vi.fn(),
    onRetry: vi.fn(),
    onPageChange: vi.fn(),
    onFocusApplied: vi.fn(),
    ...overrides,
  }
  return { props, ...renderInvitation(<InvitationList {...props} />, languages) }
}

describe('InvitationList', () => {
  it('shows every column with language names, dates, the inviter and text badges', () => {
    renderList()
    const table = screen.getByRole('table', { name: '招待中の人' })
    expect(
      within(table)
        .getAllByRole('columnheader')
        .map((th) => th.textContent),
    ).toEqual([
      'メールアドレス',
      '言語',
      '招待した管理者',
      '招待した日時',
      '有効期限',
      '送信の結果',
      '状態',
      '操作',
    ])
    expect(screen.getByText('English')).toHaveAttribute('lang', 'en')
    expect(screen.getAllByText('日本語')[0]).toHaveAttribute('lang', 'ja')
    expect(screen.getAllByText('2026-09-25 21:00 JST').length).toBeGreaterThan(0)
    expect(screen.getAllByText('2026-09-26 21:00 JST').length).toBeGreaterThan(0)
    expect(screen.getByText('山田 花子')).toBeInTheDocument()
    expect(screen.getByText('（不明）')).toBeInTheDocument()
    expect(screen.getAllByText('送信済み')).toHaveLength(2)
    expect(screen.getByText('送信に失敗')).toBeInTheDocument()
    expect(screen.getAllByText('期限内')).toHaveLength(2)
    expect(screen.getByText('期限切れ')).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: 'hanako@example.test への招待を送り直す' }),
    ).toHaveTextContent('送り直す')
    expect(
      screen.getByRole('button', { name: 'taro@example.test への招待を取り消す' }),
    ).toHaveTextContent('取り消す')
  })

  it('never shows the token or the URL and draws symbols in an address as text', () => {
    const leaky = {
      ...invitationOf({ invitationId: 5, email: 'a<b>&c@example.test' }),
      token: 'leak-check-token-value',
      url: 'http://leak-check.example.test/register#token=x',
    } as Invitation
    const { container } = renderList({ list: pageOf({ items: [leaky] }) })
    expect(container.innerHTML).not.toContain('leak-check')
    expect(document.body.innerHTML).not.toContain('leak-check')
    expect(screen.getByText('a<b>&c@example.test')).toBeInTheDocument()
    expect(container.querySelector('b')).toBeNull()
  })

  it('shows the loading, empty and failed states without a table', async () => {
    const user = userEvent.setup()
    const loading = renderList({ list: null, loadState: 'loading' })
    expect(screen.getByTestId('invitation-list-loading')).toHaveTextContent(
      '招待中の人を読み込んでいます',
    )
    expect(screen.getByTestId('invitation-list-loading')).toHaveAttribute('role', 'status')
    expect(screen.queryByRole('table')).toBeNull()
    expect(screen.getByRole('heading', { level: 2, name: '招待中の人' })).toBeInTheDocument()
    loading.unmount()

    const empty = renderList({ list: pageOf({ items: [], total: 0 }) })
    expect(screen.getByTestId('invitation-list-empty')).toHaveTextContent(
      '招待中の人はいません。『招待する』から招待できます。',
    )
    expect(screen.queryByRole('table')).toBeNull()
    empty.unmount()

    const { props } = renderList({
      loadState: 'failed',
      loadFailureKey: 'invitation.errorGeneral.server',
    })
    expect(screen.getByRole('alert')).toHaveTextContent('招待中の人を読み込めませんでした。')
    expect(screen.getByRole('alert')).toHaveTextContent('サーバーで問題が起きました')
    expect(screen.queryByRole('table')).toBeNull()
    await user.click(screen.getByRole('button', { name: 'もう一度読み込む' }))
    expect(props.onRetry).toHaveBeenCalledTimes(1)
  })

  it('keeps focus on a row being resent, ignores its clicks, and leaves other rows pressable', async () => {
    const user = userEvent.setup()
    const { props } = renderList({ resendingIds: new Set([11]) })
    const busy = screen.getByRole('button', {
      name: 'hanako@example.test への招待を送信しています',
    })
    expect(busy).toHaveTextContent('送信しています')
    expect(busy).toHaveAttribute('aria-disabled', 'true')
    expect(busy).toHaveAttribute('aria-busy', 'true')
    busy.focus()
    await user.click(busy)
    expect(props.onResend).not.toHaveBeenCalled()
    expect(busy).toHaveFocus()
    expect(
      screen.getByRole('button', { name: 'hanako@example.test への招待を取り消す' }),
    ).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'taro@example.test への招待を送り直す' }))
    expect(props.onResend).toHaveBeenCalledWith(expect.objectContaining({ invitationId: 12 }))
  })

  it('disables only resend when invitations are unavailable, and lets expired rows be pressed', async () => {
    const user = userEvent.setup()
    const unavailable = renderList({ invitationEnabled: false })
    expect(
      screen.getByRole('button', { name: 'hanako@example.test への招待を送り直す' }),
    ).toBeDisabled()
    await user.click(screen.getByRole('button', { name: 'jiro@example.test への招待を取り消す' }))
    expect(unavailable.props.onRevoke).toHaveBeenCalledWith(
      expect.objectContaining({ invitationId: 13 }),
    )
    unavailable.unmount()

    const { props } = renderList()
    await user.click(screen.getByRole('button', { name: 'jiro@example.test への招待を送り直す' }))
    expect(props.onResend).toHaveBeenCalledWith(expect.objectContaining({ invitationId: 13 }))
  })

  it('marks the highlighted row and moves focus to the requested place after drawing', () => {
    const first = renderList({
      highlightedId: 12,
      focusTarget: { kind: 'resend', invitationId: 12 },
    })
    const marks = document.querySelectorAll('[data-invitation-highlighted]')
    expect(marks).toHaveLength(1)
    expect(screen.getByTestId('invitation-row-email-12')).toContainElement(marks[0] as HTMLElement)
    expect(
      screen.getByRole('button', { name: 'taro@example.test への招待を送り直す' }),
    ).toHaveFocus()
    expect(first.props.onFocusApplied).toHaveBeenCalledTimes(1)
    first.unmount()

    const disabled = renderList({
      invitationEnabled: false,
      focusTarget: { kind: 'resend', invitationId: 12 },
    })
    expect(screen.getByTestId('invitation-row-email-12')).toHaveFocus()
    disabled.unmount()

    const gone = renderList({ focusTarget: { kind: 'resend', invitationId: 99 } })
    expect(screen.getByRole('heading', { level: 2 })).toHaveFocus()
    gone.unmount()

    const loading = renderList({ loadState: 'loading', focusTarget: { kind: 'heading' } })
    expect(screen.getByRole('heading', { level: 2 })).not.toHaveFocus()
    expect(loading.props.onFocusApplied).not.toHaveBeenCalled()
    loading.unmount()

    renderList({ list: pageOf({ items: [], total: 0 }), focusTarget: { kind: 'heading' } })
    expect(screen.getByTestId('invitation-list-empty')).toHaveFocus()
  })

  it('names the table, reaches its buttons with Tab, and shows the pager status and its edges', async () => {
    const user = userEvent.setup()
    const single = renderList()
    expect(screen.getByRole('table', { name: '招待中の人' })).toBeInTheDocument()
    await user.tab()
    expect(
      screen.getByRole('button', { name: 'hanako@example.test への招待を送り直す' }),
    ).toHaveFocus()
    await user.tab()
    expect(
      screen.getByRole('button', { name: 'hanako@example.test への招待を取り消す' }),
    ).toHaveFocus()
    expect(screen.getByText('1〜3 件目 / 全 3 件（1 / 1 ページ）')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '前へ' })).toBeDisabled()
    expect(screen.getByRole('button', { name: '次へ' })).toBeDisabled()
    single.unmount()

    const list: InvitationPage = pageOf({ items: rowsOf(20, 21), page: 2, total: 43 })
    const { props } = renderList({ list, page: 2 })
    expect(screen.getByText('21〜40 件目 / 全 43 件（2 / 3 ページ）')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '次へ' }))
    expect(props.onPageChange).toHaveBeenLastCalledWith(3, 'next')
    await user.click(screen.getByRole('button', { name: '前へ' }))
    expect(props.onPageChange).toHaveBeenLastCalledWith(1, 'prev')
  })

  it('uses English Table labels on an English screen and keeps the table and pager while reloading', () => {
    const list = pageOf({ items: rowsOf(20), total: 43 })
    renderList({ list, loadState: 'loading', page: 2 }, ['en-US'])
    expect(screen.getByTestId('invitation-list-loading')).toHaveTextContent(
      'Loading pending invitations',
    )
    const table = screen.getByRole('table', { name: 'Pending invitations' })
    expect(within(table).queryAllByRole('row')).toHaveLength(1)
    expect(screen.getByRole('button', { name: 'Previous' })).toBeEnabled()
    expect(screen.getByRole('button', { name: 'Next' })).toBeEnabled()
    expect(screen.getByText('21–40 of 43 (page 2 of 3)')).toBeInTheDocument()
    const text = document.body.textContent ?? ''
    for (const japanese of ['前へ', '次へ', '件', 'ページ', '選択', '詳細']) {
      expect(text).not.toContain(japanese)
    }
  })

  it('scrolls a focused row button or address cell into view inside the table', () => {
    const scrollIntoView = vi.fn()
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', {
      value: scrollIntoView,
      configurable: true,
    })
    try {
      renderList()
      screen.getByRole('button', { name: 'taro@example.test への招待を取り消す' }).focus()
      expect(scrollIntoView).toHaveBeenCalledWith({ block: 'nearest', inline: 'nearest' })
      screen.getByTestId('invitation-row-email-12').focus()
      expect(scrollIntoView).toHaveBeenCalledTimes(2)
    } finally {
      Reflect.deleteProperty(HTMLElement.prototype, 'scrollIntoView')
    }
  })

  it('has no accessibility violations with rows', async () => {
    const { container } = renderList({ list: pageOf({ items: sampleRows() }), highlightedId: 11 })
    expect(await axe(container)).toHaveNoViolations()
  })
})
