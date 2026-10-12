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
// S6 のメンバーの表のテスト（Intent 261004-role-menu の U6 の D21、AC2.1.9）。列・利用停止の印・外すの名前・送信中の
// 押せない形・空のとき・記号の氏名が文字のまま出ることを確かめる。
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import type { Member } from './api/types'
import { MemberTable } from './MemberTable'
import { memberOf, SCRIPT_NAME } from './testing/fixtures'
import { renderGroupAdmin } from './testing/renderGroupAdmin'

async function renderTable(members: Member[], removingId: number | null = null, busy = false) {
  const onRemove = vi.fn()
  const view = renderGroupAdmin(
    <MemberTable members={members} removingId={removingId} busy={busy} onRemove={onRemove} />,
  )
  await screen.findByText(members.length === 0 ? 'メンバーはいません。' : '氏名')
  return { ...view, onRemove }
}

describe('MemberTable', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('lists name, email and the suspended mark, and removes the chosen member', async () => {
    const user = userEvent.setup()
    const suspended = memberOf({ userId: 22, displayName: '佐藤 花子', suspended: true })
    const { onRemove } = await renderTable([memberOf(), suspended])
    const table = screen.getByTestId('groupadmin-members')
    expect(
      within(table)
        .getAllByRole('columnheader')
        .map((cell) => cell.textContent),
    ).toEqual(['氏名', 'メールアドレス', '操作'])
    expect(screen.getByTestId('groupadmin-member-22')).toHaveTextContent('（利用停止）')
    expect(screen.getByTestId('groupadmin-member-21')).not.toHaveTextContent('（利用停止）')
    await user.click(screen.getByRole('button', { name: '佐藤 花子を外す' }))
    expect(onRemove).toHaveBeenCalledWith(suspended)
  })

  it('shows the empty message when there are no members', async () => {
    await renderTable([])
    expect(screen.getByTestId('groupadmin-members-empty')).toBeInTheDocument()
  })

  it('disables the other rows while one member is being removed', async () => {
    await renderTable([memberOf(), memberOf({ userId: 22, displayName: '佐藤 花子' })], 21, true)
    expect(screen.getByRole('button', { name: '佐藤 花子を外す' })).toBeDisabled()
  })

  it('draws a name that looks like markup as text', async () => {
    await renderTable([memberOf({ displayName: SCRIPT_NAME })])
    expect(screen.getByText(SCRIPT_NAME)).toBeInTheDocument()
    expect(document.querySelector('script')).toBeNull()
  })

  it('has no accessibility violations', async () => {
    const view = await renderTable([
      memberOf(),
      memberOf({ userId: 22, displayName: '佐藤 花子', suspended: true }),
    ])
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
