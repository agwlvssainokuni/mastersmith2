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
// 確かめの表示のテスト（functional-spec.md の 4.2・W5・D7・D13・D14・7.3、frontend-components.md の 8節、
// AC2.1.8・AC3.1.7・AC4.1.9、NFR7.2・NFR8.2）。
import { fireEvent, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useRef, useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { ConfirmActionDialog } from './ConfirmActionDialog'
import type { ConfirmActionKind } from './rowActions'
import { userOf } from './testing/fixtures'
import { renderUserAdmin } from './testing/renderUserAdmin'

const TARGET = userOf({ userId: 3, displayName: '山田 太郎', email: 'yamada.taro@example.com' })

function renderDialog(
  action: ConfirmActionKind,
  options: { submitting?: boolean; slow?: boolean; languages?: readonly string[] } = {},
) {
  const onConfirm = vi.fn()
  const onCancel = vi.fn()
  const view = renderUserAdmin(
    <ConfirmActionDialog
      state={{ action, user: TARGET, submitting: options.submitting ?? false }}
      slow={options.slow ?? false}
      onConfirm={onConfirm}
      onCancel={onCancel}
    />,
    { languages: options.languages },
  )
  return { ...view, onConfirm, onCancel }
}

/** 閉じると状態を null にし、開いた元の代わりのボタンを finalFocusRef で渡す試しの部品（FR1.2） */
function ClosableHarness() {
  const [open, setOpen] = useState(true)
  const returnRef = useRef<HTMLButtonElement>(null)
  return (
    <>
      <button type="button" ref={returnRef}>
        戻り先
      </button>
      <ConfirmActionDialog
        state={open ? { action: 'suspend', user: TARGET, submitting: false } : null}
        slow={false}
        onConfirm={() => {}}
        onCancel={() => setOpen(false)}
        finalFocusRef={returnRef}
      />
    </>
  )
}

const EXPECTED: Record<ConfirmActionKind, { title: string; effect: string; submit: string }> = {
  grantAdmin: {
    title: '管理者の印を付けますか？',
    effect: '山田 太郎さんは、次の操作から管理の画面を使えるようになります。',
    submit: '管理者の印を付ける',
  },
  revokeAdmin: {
    title: '管理者の印を外しますか？',
    effect: '山田 太郎さんは、次の操作から管理の画面を使えなくなります。ログインは続きます。',
    submit: '管理者の印を外す',
  },
  suspend: {
    title: '利用を止めますか？',
    effect:
      '山田 太郎さんはすぐにこのアプリを使えなくなります。停止を解いた後も、ログインし直す必要があります。',
    submit: '利用を止める',
  },
  resume: {
    title: '停止を解きますか？',
    effect: '山田 太郎さんは、新しくログインすればこのアプリを使えるようになります。',
    submit: '停止を解く',
  },
  resetFailures: {
    title: 'ロックを解除しますか？',
    effect:
      '山田 太郎さんのログインの失敗回数を 0 に戻します。すぐに正しいパスワードでログインできます。',
    submit: 'ロックを解除',
  },
}

const ACTIONS = Object.keys(EXPECTED) as ConfirmActionKind[]

describe('ConfirmActionDialog', () => {
  it('shows the title, the target, the effect and the button of each of the five actions', () => {
    for (const action of ACTIONS) {
      const { unmount } = renderDialog(action)
      const dialog = screen.getByRole('alertdialog', { name: EXPECTED[action].title })
      expect(within(dialog).getByTestId('useradmin-confirm-target')).toHaveTextContent(
        '対象: 山田 太郎（yamada.taro@example.com）',
      )
      expect(within(dialog).getByTestId('useradmin-confirm-effect')).toHaveTextContent(
        EXPECTED[action].effect,
      )
      expect(
        within(dialog).getByRole('button', { name: EXPECTED[action].submit }),
      ).toBeInTheDocument()
      unmount()
    }
  })

  it('starts the description with the target and the effect and uses the danger look for revoke and suspend', () => {
    const { unmount } = renderDialog('suspend')
    const description = screen.getByRole('alertdialog').getAttribute('aria-describedby') ?? ''
    expect(document.getElementById(description)?.textContent?.startsWith('対象: 山田 太郎')).toBe(
      true,
    )
    expect(screen.getByTestId('useradmin-confirm-submit')).toHaveClass('variant-danger')
    unmount()
    renderDialog('resume')
    expect(screen.getByTestId('useradmin-confirm-submit')).toHaveClass('variant-primary')
  })

  it('puts the first focus on cancel and does not close with a backdrop click', () => {
    const { onCancel, onConfirm } = renderDialog('grantAdmin')
    expect(screen.getByRole('button', { name: 'やめる' })).toHaveFocus()
    fireEvent.mouseDown(screen.getByTestId('modal-overlay'))
    expect(onCancel).not.toHaveBeenCalled()
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('cancels with the cancel button and Escape without confirming', async () => {
    const user = userEvent.setup()
    const { onCancel, onConfirm } = renderDialog('revokeAdmin')
    await user.click(screen.getByRole('button', { name: 'やめる' }))
    await user.keyboard('{Escape}')
    expect(onCancel).toHaveBeenCalledTimes(2)
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it.each([
    ['the cancel button', 'cancel'],
    ['Escape', 'escape'],
  ] as const)(
    'moves the focus to the element given as finalFocusRef after closing with %s (FR1.2)',
    async (_label, how) => {
      const user = userEvent.setup()
      renderUserAdmin(<ClosableHarness />)
      expect(screen.getByRole('button', { name: 'やめる' })).toHaveFocus()
      if (how === 'cancel') {
        await user.click(screen.getByRole('button', { name: 'やめる' }))
      } else {
        await user.keyboard('{Escape}')
      }
      await waitFor(() => expect(screen.queryByRole('alertdialog')).toBeNull())
      await waitFor(() => expect(screen.getByRole('button', { name: '戻り先' })).toHaveFocus())
    },
  )

  it('confirms with the action button', async () => {
    const user = userEvent.setup()
    const { onConfirm } = renderDialog('resetFailures')
    await user.click(screen.getByRole('button', { name: 'ロックを解除' }))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('does not close while submitting and shows processing and the slow notice', async () => {
    const user = userEvent.setup()
    const { onCancel, unmount } = renderDialog('suspend', { submitting: true })
    expect(screen.getByRole('button', { name: '処理中' })).toHaveAttribute('aria-busy', 'true')
    expect(screen.getByRole('button', { name: 'やめる' })).toBeDisabled()
    expect(screen.queryByTestId('useradmin-confirm-slow')).toBeNull()
    await user.keyboard('{Escape}')
    await user.click(screen.getByRole('button', { name: '閉じる' }))
    expect(onCancel).not.toHaveBeenCalled()
    unmount()
    renderDialog('suspend', { submitting: true, slow: true })
    expect(screen.getByTestId('useradmin-confirm-slow')).toHaveTextContent(
      '時間がかかっています。そのままお待ちください。',
    )
  })

  it('shows English text and the close label in the screen language', () => {
    renderDialog('suspend', { languages: ['en-US'] })
    expect(screen.getByRole('alertdialog', { name: 'Suspend this user?' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Close' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeInTheDocument()
    expect(screen.getByTestId('useradmin-confirm-target')).toHaveTextContent(
      'Target: 山田 太郎 (yamada.taro@example.com)',
    )
  })

  it('has no accessibility violations for the five actions', async () => {
    for (const action of ACTIONS) {
      const { unmount } = renderDialog(action)
      expect(await axe(document.body)).toHaveNoViolations()
      unmount()
    }
  })
})
