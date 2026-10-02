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
// 一覧の表のテスト（functional-spec.md の W1・W2・D4・D13・D18・D19、frontend-components.md の 8節、AC1.1.1・AC1.1.12、
// NFR3.3・NFR7.2・NFR8.2）。時間帯と今の時刻を固定する。
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import type { AdminUser } from './api/types'
import { LONG_EMAIL, rowsOf, sampleRows, SYMBOL_NAME, userOf } from './testing/fixtures'
import { renderUserAdmin } from './testing/renderUserAdmin'
import { UserTable, type UserTableProps } from './UserTable'

/** 2026-10-03 10:00 JST */
const NOW = new Date('2026-10-03T01:00:00Z')

function renderTable(
  props: Partial<UserTableProps> = {},
  languages: readonly string[] = ['ja-JP'],
) {
  return renderUserAdmin(
    <UserTable
      rows={props.rows ?? sampleRows()}
      total={props.total ?? 5}
      page={props.page ?? 1}
      onPageChange={props.onPageChange ?? (() => {})}
      busy={props.busy ?? false}
      label={props.label ?? '利用者の一覧'}
      language={props.language ?? 'ja'}
      timeZone="Asia/Tokyo"
      now={NOW}
      renderActions={
        props.renderActions ?? ((row: AdminUser) => <span>{`操作-${row.userId}`}</span>)
      }
    />,
    { languages },
  )
}

function row(userId: number): HTMLElement {
  return screen.getByTestId(`table-row-${userId}`)
}

describe('UserTable', () => {
  it('shows the columns and the marks as text', () => {
    renderTable()
    const table = screen.getByRole('table', { name: '利用者の一覧' })
    const headers = within(table)
      .getAllByRole('columnheader')
      .map((header) => header.textContent)
    expect(headers).toEqual([
      'メールアドレス',
      '氏名',
      '管理者',
      '状態',
      'ロック',
      '登録した日時',
      '操作',
    ])
    expect(within(row(10)).getByText('あなた')).toBeInTheDocument()
    expect(within(row(10)).getByText('管理者')).toBeInTheDocument()
    expect(within(row(13)).getByText('利用停止')).toBeInTheDocument()
    expect(within(row(11)).getByText('有効')).toBeInTheDocument()
    expect(within(row(12)).getByText('ロック中')).toBeInTheDocument()
    expect(within(row(12)).getByText('10:30 JST まで')).toBeInTheDocument()
    expect(within(row(12)).getByText('操作-12')).toBeInTheDocument()
  })

  it('reads a missing mark as none and hides the dash', () => {
    renderTable({ rows: [userOf({ userId: 1 })], total: 1 })
    const adminCell = screen.getByTestId('table-cell-1-admin')
    expect(within(adminCell).getByText('—')).toHaveAttribute('aria-hidden', 'true')
    expect(within(adminCell).getByText('なし')).toBeInTheDocument()
    expect(within(screen.getByTestId('table-cell-1-lock')).getByText('なし')).toBeInTheDocument()
  })

  it('draws long emails, two-word names and symbols as text', () => {
    renderTable()
    expect(within(row(14)).getByText(LONG_EMAIL)).toBeInTheDocument()
    expect(within(row(14)).getByText(SYMBOL_NAME)).toBeInTheDocument()
    expect(within(row(11)).getByText('佐藤 花子')).toBeInTheDocument()
    expect(document.querySelector('b')).toBeNull()
  })

  it('formats the dates in the screen language and the time zone', () => {
    renderTable({
      rows: [userOf({ userId: 1, locked: true, lockedUntil: '2026-10-04T01:30:00Z' })],
    })
    expect(screen.getByText('2026-09-25 12:00 JST')).toBeInTheDocument()
    expect(screen.getByText('2026-10-04 10:30 JST まで')).toBeInTheDocument()
  })

  it('shows the page status and the pager in the screen language', () => {
    renderTable({ rows: rowsOf(20, 21), total: 43, page: 2 })
    expect(screen.getByText('2 / 3ページ（全43件）')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '前へ' })).toBeEnabled()
  })

  it('shows English text without the Japanese defaults of the table', () => {
    renderTable({ rows: rowsOf(20, 21), total: 43, page: 2, language: 'en' }, ['en-US'])
    expect(screen.getByText('Page 2 of 3 (43 total)')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Previous' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Next' })).toBeInTheDocument()
    expect(screen.queryByText(/ページ|前へ|次へ/)).toBeNull()
  })

  it('drops pager presses while busy and passes the direction otherwise', async () => {
    const user = userEvent.setup()
    const onPageChange = vi.fn()
    const view = renderTable({ rows: rowsOf(20, 21), total: 43, page: 2, busy: true, onPageChange })
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await user.click(screen.getByRole('button', { name: '前へ' }))
    expect(onPageChange).not.toHaveBeenCalled()
    expect(screen.getByTestId('useradmin-table')).toHaveAttribute('aria-busy', 'true')
    view.unmount()
    renderTable({ rows: rowsOf(20, 21), total: 43, page: 2, onPageChange })
    await user.click(screen.getByRole('button', { name: '次へ' }))
    await user.click(screen.getByRole('button', { name: '前へ' }))
    expect(onPageChange.mock.calls).toEqual([
      [3, 'next'],
      [1, 'prev'],
    ])
  })

  it('does not show values of extra fields of a row', () => {
    const leaky = { ...userOf({ userId: 1 }), passwordHash: 'leak-check-hash' } as AdminUser
    renderTable({ rows: [leaky], total: 1 })
    expect(document.body.textContent).not.toContain('leak-check')
  })

  it('has no accessibility violations', async () => {
    const { container } = renderTable()
    expect(await axe(container)).toHaveNoViolations()
  })
})
