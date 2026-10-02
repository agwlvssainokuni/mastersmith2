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
// 氏名・言語の入力のテスト（functional-spec.md の 4.3・W7・D13・D14・D16・7.6、frontend-components.md の 8節、AC5.1.7、
// NFR7.2）。入力の状態は外から渡し、操作の呼び出しを確かめる（保存の流れそのものは UserAdminPage.test.tsx）。
import { fireEvent, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { EditProfileDialog } from './EditProfileDialog'
import { userOf } from './testing/fixtures'
import { renderUserAdmin } from './testing/renderUserAdmin'
import type { EditState } from './useUserAdmin'

const TARGET = userOf({
  userId: 3,
  displayName: '山田 太郎',
  email: 'yamada.taro@example.com',
  language: 'en',
})

function stateOf(overrides: Partial<EditState> = {}): EditState {
  return {
    user: TARGET,
    displayName: TARGET.displayName,
    language: TARGET.language,
    fieldErrors: {},
    errorSeq: 0,
    status: 'open',
    dialogMessage: null,
    ...overrides,
  }
}

interface HarnessProps {
  initial?: EditState
  slow?: boolean
  onSave?: () => void
  onCancel?: () => void
  /** 保存のときに出す誤り（画面の確かめの代わり） */
  errorOnSave?: Partial<EditState>
}

function Harness({
  initial = stateOf(),
  slow = false,
  onSave,
  onCancel,
  errorOnSave,
}: HarnessProps) {
  const [state, setState] = useState<EditState>(initial)
  return (
    <EditProfileDialog
      state={state}
      slow={slow}
      onChangeName={(value) => setState((s) => ({ ...s, displayName: value }))}
      onChangeLanguage={(value) => setState((s) => ({ ...s, language: value }))}
      onSave={() => {
        onSave?.()
        if (errorOnSave !== undefined) {
          setState((s) => ({ ...s, ...errorOnSave, errorSeq: s.errorSeq + 1 }))
        }
      }}
      onCancel={onCancel ?? (() => {})}
    />
  )
}

function nameInput(): HTMLElement {
  return screen.getByRole('textbox', { name: '氏名（必須）' })
}

describe('EditProfileDialog', () => {
  it('opens with the current values, the target and the first focus on the name', () => {
    renderUserAdmin(<Harness />)
    expect(screen.getByRole('dialog', { name: '氏名と言語を直す' })).toBeInTheDocument()
    expect(screen.getByTestId('useradmin-edit-target')).toHaveTextContent(
      '対象: 山田 太郎（yamada.taro@example.com）',
    )
    expect(nameInput()).toHaveValue('山田 太郎')
    expect(nameInput()).toHaveAttribute('autocomplete', 'off')
    expect(nameInput()).toHaveFocus()
    expect(screen.getByRole('radio', { name: 'English' })).toBeChecked()
    expect(screen.queryByRole('textbox', { name: /メール|パスワード/ })).toBeNull()
  })

  it('shows a name error under the input, keeps the input and moves the focus to it', async () => {
    const user = userEvent.setup()
    renderUserAdmin(
      <Harness errorOnSave={{ fieldErrors: { displayName: 'useradmin.edit.nameRequired' } }} />,
    )
    await user.clear(nameInput())
    await user.type(nameInput(), '  ')
    await user.click(screen.getByRole('radio', { name: '日本語' }))
    await user.click(screen.getByRole('button', { name: '保存' }))
    await waitFor(() => expect(nameInput()).toHaveFocus())
    expect(nameInput()).toHaveAttribute('aria-invalid', 'true')
    expect(nameInput()).toHaveAccessibleDescription('氏名を入れてください。')
    expect(nameInput()).toHaveValue('  ')
  })

  it('shows a server language error under the language group and focuses it', async () => {
    const user = userEvent.setup()
    renderUserAdmin(
      <Harness errorOnSave={{ fieldErrors: { language: 'useradmin.edit.languageInvalid' } }} />,
    )
    await user.click(screen.getByRole('button', { name: '保存' }))
    expect(await screen.findByTestId('useradmin-edit-language-error')).toHaveTextContent(
      '言語を選んでください。',
    )
    await waitFor(() => expect(screen.getByRole('radio', { name: 'English' })).toHaveFocus())
  })

  it('shows not-found and does not allow saving', () => {
    const onSave = vi.fn()
    renderUserAdmin(
      <Harness
        initial={stateOf({ status: 'notFound', dialogMessage: 'useradmin.edit.notFound' })}
        onSave={onSave}
      />,
    )
    expect(screen.getByTestId('useradmin-edit-alert')).toHaveTextContent(
      '対象の利用者が見つかりません。一覧を読み直してください。',
    )
    expect(screen.getByRole('button', { name: '保存' })).toBeDisabled()
    fireEvent.submit(screen.getByTestId('useradmin-edit-dialog'))
    expect(onSave).not.toHaveBeenCalled()
  })

  it('keeps the input after a general failure and allows saving again', async () => {
    const user = userEvent.setup()
    const onSave = vi.fn()
    renderUserAdmin(
      <Harness
        initial={stateOf({
          displayName: '山田 花子',
          status: 'failed',
          dialogMessage: 'useradmin.edit.failed',
        })}
        onSave={onSave}
      />,
    )
    expect(screen.getByTestId('useradmin-edit-alert')).toHaveTextContent(
      '保存できませんでした。時間をおいて、もう一度保存してください。',
    )
    expect(nameInput()).toHaveValue('山田 花子')
    await user.click(screen.getByRole('button', { name: '保存' }))
    expect(onSave).toHaveBeenCalledTimes(1)
  })

  it('does not close or save while submitting and shows the slow notice', async () => {
    const user = userEvent.setup()
    const onSave = vi.fn()
    const onCancel = vi.fn()
    renderUserAdmin(
      <Harness
        initial={stateOf({ status: 'submitting' })}
        slow
        onSave={onSave}
        onCancel={onCancel}
      />,
    )
    expect(screen.getByRole('button', { name: '処理中' })).toHaveAttribute('aria-busy', 'true')
    expect(screen.getByRole('button', { name: 'やめる' })).toBeDisabled()
    expect(screen.getByTestId('useradmin-edit-slow')).toHaveTextContent('時間がかかっています')
    await user.keyboard('{Escape}')
    await user.click(screen.getByRole('button', { name: '処理中' }))
    fireEvent.mouseDown(screen.getByTestId('modal-overlay'))
    expect(onCancel).not.toHaveBeenCalled()
    expect(onSave).not.toHaveBeenCalled()
  })

  it('makes the name read-only while submitting so that the sent value and the input do not diverge', async () => {
    const user = userEvent.setup()
    renderUserAdmin(<Harness initial={stateOf({ status: 'submitting' })} />)
    expect(nameInput()).toHaveAttribute('readonly')
    await user.type(nameInput(), '子')
    expect(nameInput()).toHaveValue(TARGET.displayName)
  })

  it('keeps the name editable when not submitting', () => {
    renderUserAdmin(<Harness />)
    expect(nameInput()).not.toHaveAttribute('readonly')
  })

  it('cancels with the cancel button and Escape but not with a backdrop click', async () => {
    const user = userEvent.setup()
    const onCancel = vi.fn()
    renderUserAdmin(<Harness onCancel={onCancel} />)
    fireEvent.mouseDown(screen.getByTestId('modal-overlay'))
    expect(onCancel).not.toHaveBeenCalled()
    await user.click(screen.getByRole('button', { name: 'やめる' }))
    await user.keyboard('{Escape}')
    expect(onCancel).toHaveBeenCalledTimes(2)
  })

  it('has no accessibility violations with the current values and with errors', async () => {
    const { unmount } = renderUserAdmin(<Harness />)
    expect(await axe(document.body)).toHaveNoViolations()
    unmount()
    renderUserAdmin(
      <Harness
        initial={stateOf({
          fieldErrors: {
            displayName: 'useradmin.edit.nameTooLong',
            language: 'useradmin.edit.languageInvalid',
          },
          errorSeq: 1,
          status: 'failed',
          dialogMessage: 'useradmin.edit.formInvalid',
        })}
      />,
    )
    expect(await axe(document.body)).toHaveNoViolations()
  })
})
