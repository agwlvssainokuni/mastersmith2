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
// 候補の Modal のテスト（Intent 261004-role-menu の U6 の D17・D18、AC2.2.15・AC2.2.16、計画 13節 Q3: A）。
// 検索は Enter でも行い打つたびには読まない、上限の 254 コードポイントを超えると送らない、件数を role="status" で伝える、
// 済み・相手がいない候補は押せず理由を文字で示す、足した後も開いたまま、グループはページ送り。
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { CandidateDialog, type Candidate, type CandidateDialogProps } from './CandidateDialog'
import { renderRoleAdmin } from './testing/renderRoleAdmin'

const PEOPLE: Candidate[] = [
  { id: 1, name: '田中 一郎', email: 'tanaka.ichiro@example.com', suspended: false },
  { id: 2, name: '鈴木 次郎', email: 'suzuki.jiro@example.com', suspended: true },
]

async function renderDialog(props: Partial<CandidateDialogProps> = {}) {
  const onAdd = vi.fn(() =>
    Promise.resolve({
      status: 'added' as const,
      messageKey: 'roleadmin.assign.userAdded',
      values: { name: '鈴木 次郎' },
    }),
  )
  const onClose = vi.fn()
  const search = vi.fn(() => Promise.resolve(PEOPLE))
  const view = renderRoleAdmin(
    <CandidateDialog
      kind="user"
      title="利用者を足す"
      isTaken={(id) => id === 1}
      takenLabel="割り当て済み"
      search={search}
      onAdd={onAdd}
      onClose={onClose}
      {...props}
    />,
  )
  await screen.findByRole('dialog')
  return { ...view, onAdd, onClose, search }
}

describe('CandidateDialog', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('searches only on submit (Enter), announcing the result count', async () => {
    const user = userEvent.setup()
    const { search } = await renderDialog()
    const input = screen.getByTestId('roleadmin-candidate-input')
    await waitFor(() => expect(input).toHaveFocus())
    await user.type(input, ' 田中 ')
    expect(search).not.toHaveBeenCalled()
    await user.keyboard('{Enter}')
    expect(search).toHaveBeenCalledWith('田中')
    expect(await screen.findByTestId('roleadmin-candidate-status')).toHaveTextContent(
      '2 件見つかりました。',
    )
  })

  it('does not send search text longer than 254 code points', async () => {
    const user = userEvent.setup()
    const { search } = await renderDialog()
    const input = screen.getByTestId('roleadmin-candidate-input')
    await user.click(input)
    await user.paste('あ'.repeat(255))
    await user.click(screen.getByRole('button', { name: '検索' }))
    expect(search).not.toHaveBeenCalled()
    expect(screen.getByText('検索の文字は 254 文字以内で入れてください。')).toBeInTheDocument()
    await user.clear(input)
    await user.paste('あ'.repeat(254))
    await user.click(screen.getByRole('button', { name: '検索' }))
    expect(search).toHaveBeenCalledTimes(1)
  })

  it('marks taken and suspended candidates in text and disables adding a taken one with a reason', async () => {
    const user = userEvent.setup()
    await renderDialog()
    await user.type(screen.getByTestId('roleadmin-candidate-input'), 'x{Enter}')
    const taken = await screen.findByTestId('roleadmin-candidate-row-1')
    const button = within(taken).getByRole('button', { name: '田中 一郎を足す' })
    expect(button).toBeDisabled()
    expect(button).toHaveAccessibleDescription('割り当て済み')
    expect(screen.getByTestId('roleadmin-candidate-row-2')).toHaveTextContent('（利用停止）')
  })

  it('keeps the dialog open after adding, and makes an unavailable candidate unpressable', async () => {
    const user = userEvent.setup()
    const onAdd = vi
      .fn()
      .mockResolvedValueOnce({ status: 'unavailable', messageKey: 'roleadmin.user.missing' })
    const { onClose } = await renderDialog({ onAdd })
    await user.type(screen.getByTestId('roleadmin-candidate-input'), 'x{Enter}')
    const row = await screen.findByTestId('roleadmin-candidate-row-2')
    await user.click(within(row).getByRole('button', { name: '鈴木 次郎を足す' }))
    expect(await screen.findByTestId('roleadmin-candidate-status')).toHaveTextContent(
      'この利用者はいません。',
    )
    expect(within(row).getByRole('button', { name: '鈴木 次郎を足す' })).toBeDisabled()
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('shows the failure and lets the candidate be added again after a failed add', async () => {
    const user = userEvent.setup()
    const onAdd = vi
      .fn()
      .mockResolvedValueOnce({ status: 'failed', messageKey: 'roleadmin.busy' })
      .mockResolvedValueOnce({
        status: 'added',
        messageKey: 'roleadmin.assign.userAdded',
        values: { name: '鈴木 次郎' },
      })
    await renderDialog({ onAdd })
    await user.type(screen.getByTestId('roleadmin-candidate-input'), 'x{Enter}')
    const row = await screen.findByTestId('roleadmin-candidate-row-2')
    await user.click(within(row).getByRole('button', { name: '鈴木 次郎を足す' }))
    expect(await screen.findByTestId('roleadmin-candidate-status')).toHaveTextContent(
      'ほかの操作と重なりました。',
    )
    await user.click(within(row).getByRole('button', { name: '鈴木 次郎を足す' }))
    expect(await screen.findByTestId('roleadmin-candidate-status')).toHaveTextContent(
      '鈴木 次郎さんに割り当てました。',
    )
  })

  it('pages through groups', async () => {
    const user = userEvent.setup()
    const loadPage = vi.fn((page: number) =>
      Promise.resolve({
        items: [{ id: page * 100, name: `グループ${page}` }],
        total: 25,
      }),
    )
    await renderDialog({ kind: 'group', title: 'グループを足す', loadPage, search: undefined })
    expect(await screen.findByText('グループ1')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '次へ' }))
    expect(await screen.findByText('グループ2')).toBeInTheDocument()
    expect(loadPage.mock.calls.map(([page]) => page)).toEqual([1, 2])
    expect(screen.getByRole('button', { name: '次へ' })).toBeDisabled()
    expect(screen.getByTestId('roleadmin-candidate-status')).toHaveTextContent(
      '2 / 2ページ（全25件）',
    )
  })

  it('closes from the close button', async () => {
    const user = userEvent.setup()
    const { onClose } = await renderDialog()
    await user.click(screen.getByTestId('roleadmin-candidate-close'))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('has no accessibility violations with results and an error', async () => {
    const user = userEvent.setup()
    await renderDialog()
    await user.type(screen.getByTestId('roleadmin-candidate-input'), 'x{Enter}')
    await screen.findByTestId('roleadmin-candidate-row-1')
    expect(await axe(document.body)).toHaveNoViolations()
  })
})
