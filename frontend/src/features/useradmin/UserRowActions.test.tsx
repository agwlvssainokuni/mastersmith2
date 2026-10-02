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
// 行の「操作」のテスト（functional-spec.md の 4.4・W4・D6・D13、frontend-components.md の 2.5・8節、AC2.1.9・AC3.1.8、
// NFR1.1・NFR7.2）。固定先 3d9521a の本物の Dropdown を通す（押せない項目は onClick を呼ばずメニューは開いたまま、
// aria-disabled、理由の文は aria-describedby）。
// 押せない項目の読み上げの名前は項目名だけ（固定先 3d9521a で aria-labelledby が項目名だけを結ぶ。generation-notes.md の
// N-3 の直り）のため、項目は名前の完全一致で探し、理由の文は説明（aria-describedby）として確かめる。
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import type { AdminUser } from './api/types'
import { selfRow, userOf } from './testing/fixtures'
import { renderUserAdmin } from './testing/renderUserAdmin'
import { UserRowActions } from './UserRowActions'

function renderActions(user: AdminUser, busy = false, onSelect = vi.fn()) {
  const view = renderUserAdmin(<UserRowActions user={user} busy={busy} onSelect={onSelect} />)
  return { ...view, onSelect }
}

function trigger(name: string | RegExp): HTMLElement {
  return screen.getByRole('button', { name })
}

describe('UserRowActions', () => {
  it('names the button with the name and the email, and adds processing while busy', () => {
    const user = userOf({ userId: 3, displayName: '山田 太郎', email: 'yamada.taro@example.com' })
    const { unmount } = renderActions(user)
    expect(trigger('山田 太郎（yamada.taro@example.com）の操作')).toHaveAttribute(
      'aria-haspopup',
      'true',
    )
    unmount()
    renderActions(user, true)
    const busyButton = trigger('山田 太郎（yamada.taro@example.com）の操作（処理中）')
    expect(busyButton).toHaveAttribute('aria-disabled', 'true')
    expect(busyButton).toHaveAttribute('aria-busy', 'true')
    expect(busyButton).not.toBeDisabled()
  })

  it('opens the menu with the items of the row and selects an enabled item', async () => {
    const user = userEvent.setup()
    const { onSelect } = renderActions(userOf({ userId: 3, resettable: true }))
    await user.click(trigger(/の操作$/))
    const items = screen.getAllByRole('menuitem').map((item) => item.textContent)
    expect(items).toEqual([
      '管理者の印を付ける',
      '利用を止める',
      'ロックを解除（失敗回数を戻す）',
      '氏名・言語を直す',
    ])
    await user.click(screen.getByRole('menuitem', { name: '利用を止める' }))
    expect(onSelect).toHaveBeenCalledWith('suspend')
    await waitFor(() => expect(screen.queryByRole('menu')).toBeNull())
  })

  it('marks the disabled items of the own row and links the reason', async () => {
    const user = userEvent.setup()
    renderActions(selfRow())
    await user.click(trigger(/の操作$/))
    const revoke = screen.getByRole('menuitem', { name: '管理者の印を外す' })
    const suspend = screen.getByRole('menuitem', { name: '利用を止める' })
    expect(revoke).toHaveAttribute('aria-disabled', 'true')
    expect(revoke).toHaveAccessibleDescription('自分自身の印は外せません')
    expect(suspend).toHaveAttribute('aria-disabled', 'true')
    expect(suspend).toHaveAccessibleDescription('自分自身は止められません')
    expect(screen.getByRole('menuitem', { name: '氏名・言語を直す' })).not.toHaveAttribute(
      'aria-disabled',
    )
  })

  it('does not select a disabled item by click, Enter or Space and keeps the menu open', async () => {
    const user = userEvent.setup()
    const { onSelect } = renderActions(selfRow())
    await user.click(trigger(/の操作$/))
    await user.click(screen.getByRole('menuitem', { name: '管理者の印を外す' }))
    expect(screen.getByRole('menu')).toBeInTheDocument()
    screen.getByRole('menuitem', { name: '利用を止める' }).focus()
    await user.keyboard('{Enter}')
    await user.keyboard(' ')
    expect(onSelect).not.toHaveBeenCalled()
    expect(screen.getByRole('menu')).toBeInTheDocument()
  })

  it('moves the focus to disabled items with the arrow keys and returns it with Escape', async () => {
    const user = userEvent.setup()
    renderActions(selfRow())
    trigger(/の操作$/).focus()
    await user.keyboard('{Enter}')
    await waitFor(() =>
      expect(screen.getByRole('menuitem', { name: '管理者の印を外す' })).toHaveFocus(),
    )
    await user.keyboard('{ArrowDown}')
    expect(screen.getByRole('menuitem', { name: '利用を止める' })).toHaveFocus()
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('menu')).toBeNull())
    expect(trigger(/の操作$/)).toHaveFocus()
  })

  it('does not open the menu while busy by click, Enter or Space and keeps the focus', async () => {
    const user = userEvent.setup()
    renderActions(userOf({ userId: 3 }), true)
    const button = trigger(/（処理中）$/)
    await user.click(button)
    button.focus()
    await user.keyboard('{Enter}')
    await user.keyboard(' ')
    expect(screen.queryByRole('menu')).toBeNull()
    expect(button).toHaveFocus()
  })

  it('shows English items and reasons on an English screen', async () => {
    const user = userEvent.setup()
    renderUserAdmin(<UserRowActions user={selfRow()} busy={false} onSelect={vi.fn()} />, {
      languages: ['en-US'],
    })
    await user.click(screen.getByRole('button', { name: /^Actions for / }))
    expect(screen.getByRole('menuitem', { name: 'Revoke admin' })).toHaveAccessibleDescription(
      'You cannot revoke your own admin role',
    )
    expect(screen.getByRole('menuitem', { name: 'Edit name and language' })).toBeInTheDocument()
  })

  it('has no accessibility violations with the own row menu open and while busy', async () => {
    const user = userEvent.setup()
    const { unmount } = renderActions(selfRow())
    await user.click(trigger(/の操作$/))
    await screen.findByRole('menu')
    // メニューは make-you-chic-ui が body の直下に描く（ランドマークの外）ため、best-practice の region の規則に当たる。
    // ShellLayout のユーザーメニューのテストと同じく、WCAG 2.0・2.1 の A・AA の規則で確かめる（文字のコントラストは
    // jsdom で計算できないため、実際のブラウザの 120 で確かめる）。
    expect(
      await axe(document.body, {
        runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
        rules: {
          'color-contrast': { enabled: false },
          'link-in-text-block': { enabled: false },
        },
      }),
    ).toHaveNoViolations()
    unmount()
    const busyView = renderActions(userOf({ userId: 3 }), true)
    expect(await axe(busyView.container)).toHaveNoViolations()
  })
})
