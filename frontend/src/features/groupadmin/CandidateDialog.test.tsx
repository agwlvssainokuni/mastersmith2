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
// メンバーの候補の Modal のテスト（Intent 261004-role-menu の U6 の D17、AC2.1.8、計画 13節 Q3: A）。検索は Enter でも
// 行い打つたびには読まない、上限の 254 コードポイントを超えると送らない、件数を role="status" で伝える、済みの候補は押せず
// 理由を文字で示す、足した後も開いたまま、読み込みの失敗を伝える。
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { CandidateDialog, type Candidate, type CandidateDialogProps } from './CandidateDialog'
import { renderGroupAdmin } from './testing/renderGroupAdmin'

const PEOPLE: Candidate[] = [
  { id: 1, name: '田中 一郎', email: 'tanaka.ichiro@example.com', suspended: false },
  { id: 2, name: '鈴木 次郎', email: 'suzuki.jiro@example.com', suspended: true },
]

async function renderDialog(props: Partial<CandidateDialogProps> = {}) {
  const onAdd = vi.fn(() =>
    Promise.resolve({
      status: 'added' as const,
      messageKey: 'groupadmin.member.added',
      values: { name: '鈴木 次郎' },
    }),
  )
  const onClose = vi.fn()
  const search = vi.fn(() => Promise.resolve(PEOPLE))
  const view = renderGroupAdmin(
    <CandidateDialog
      isTaken={(id) => id === 1}
      search={search}
      onAdd={onAdd}
      onClose={onClose}
      {...props}
    />,
  )
  await screen.findByRole('dialog', { name: 'メンバーを足す' })
  return { ...view, onAdd, onClose, search }
}

describe('groupadmin CandidateDialog', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('searches only on Enter, trimmed, and announces the count', async () => {
    const user = userEvent.setup()
    const { search } = await renderDialog()
    const input = screen.getByTestId('groupadmin-candidate-input')
    await waitFor(() => expect(input).toHaveFocus())
    await user.type(input, ' 田 ')
    expect(search).not.toHaveBeenCalled()
    await user.keyboard('{Enter}')
    expect(search).toHaveBeenCalledWith('田')
    expect(await screen.findByTestId('groupadmin-candidate-status')).toHaveTextContent(
      '2 件見つかりました。',
    )
  })

  it('does not send search text longer than 254 code points', async () => {
    const user = userEvent.setup()
    const { search } = await renderDialog()
    await user.click(screen.getByTestId('groupadmin-candidate-input'))
    await user.paste('あ'.repeat(255))
    await user.click(screen.getByRole('button', { name: '検索' }))
    expect(search).not.toHaveBeenCalled()
    expect(screen.getByText('検索の文字は 254 文字以内で入れてください。')).toBeInTheDocument()
  })

  it('marks a member as taken with a reason and keeps the dialog open after adding', async () => {
    const user = userEvent.setup()
    const { onAdd, onClose } = await renderDialog()
    await user.type(screen.getByTestId('groupadmin-candidate-input'), 'x{Enter}')
    const taken = within(await screen.findByTestId('groupadmin-candidate-row-1')).getByRole(
      'button',
      {
        name: '田中 一郎を足す',
      },
    )
    expect(taken).toBeDisabled()
    expect(taken).toHaveAccessibleDescription('メンバー済み')
    const row = screen.getByTestId('groupadmin-candidate-row-2')
    expect(row).toHaveTextContent('（利用停止）')
    await user.click(within(row).getByRole('button', { name: '鈴木 次郎を足す' }))
    expect(onAdd).toHaveBeenCalledWith(PEOPLE[1])
    expect(await screen.findByTestId('groupadmin-candidate-status')).toHaveTextContent(
      '鈴木 次郎さんをメンバーにしました。',
    )
    expect(within(row).getByRole('button', { name: '鈴木 次郎を足す' })).toBeDisabled()
    expect(onClose).not.toHaveBeenCalled()
  })

  it('shows nothing found and a failure of the search', async () => {
    const user = userEvent.setup()
    const search = vi.fn().mockResolvedValueOnce([]).mockRejectedValueOnce(new Error('x'))
    await renderDialog({ search })
    await user.type(screen.getByTestId('groupadmin-candidate-input'), 'x{Enter}')
    expect(await screen.findByTestId('groupadmin-candidate-status')).toHaveTextContent(
      '見つかりませんでした。',
    )
    await user.keyboard('{Enter}')
    await waitFor(() =>
      expect(screen.getByTestId('groupadmin-candidate-status')).toHaveTextContent(
        '読み込めませんでした。',
      ),
    )
  })

  it('closes from the close button', async () => {
    const user = userEvent.setup()
    const { onClose } = await renderDialog()
    await user.click(screen.getByTestId('groupadmin-candidate-close'))
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('has no accessibility violations with results', async () => {
    const user = userEvent.setup()
    await renderDialog()
    await user.type(screen.getByTestId('groupadmin-candidate-input'), 'x{Enter}')
    await screen.findByTestId('groupadmin-candidate-row-1')
    expect(await axe(document.body)).toHaveNoViolations()
  })
})
