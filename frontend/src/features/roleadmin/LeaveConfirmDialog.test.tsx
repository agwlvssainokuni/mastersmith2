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
// 未保存の変更の確かめの Modal のテスト（Intent 261004-role-menu の U6 の D15、frontend-components.md の 8節）。
// alertdialog・はじめのフォーカスは「留まる」・Escape は「留まる」と同じ・押した方の口が呼ばれる。
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { LeaveConfirmDialog } from './LeaveConfirmDialog'
import { renderRoleAdmin } from './testing/renderRoleAdmin'

async function renderDialog() {
  const onStay = vi.fn()
  const onLeave = vi.fn()
  const view = renderRoleAdmin(<LeaveConfirmDialog onStay={onStay} onLeave={onLeave} />)
  await screen.findByTestId('roleadmin-leave-dialog')
  return { ...view, onStay, onLeave }
}

describe('LeaveConfirmDialog', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('is an alertdialog with the message and the first focus on stay', async () => {
    await renderDialog()
    expect(screen.getByRole('alertdialog')).toHaveAccessibleName('保存していない変更')
    expect(screen.getByRole('alertdialog')).toHaveTextContent(
      '保存していない変更があります。移ると変更は保存されません。',
    )
    await waitFor(() => expect(screen.getByTestId('roleadmin-leave-stay')).toHaveFocus())
  })

  it('stays on the stay button and on Escape', async () => {
    const user = userEvent.setup()
    const { onStay, onLeave } = await renderDialog()
    await user.click(screen.getByTestId('roleadmin-leave-stay'))
    await user.keyboard('{Escape}')
    expect(onStay).toHaveBeenCalledTimes(2)
    expect(onLeave).not.toHaveBeenCalled()
  })

  it('leaves on the leave button', async () => {
    const user = userEvent.setup()
    const { onStay, onLeave } = await renderDialog()
    await user.click(screen.getByTestId('roleadmin-leave-go'))
    expect(onLeave).toHaveBeenCalledTimes(1)
    expect(onStay).not.toHaveBeenCalled()
  })

  it('has no accessibility violations', async () => {
    await renderDialog()
    expect(await axe(document.body)).toHaveNoViolations()
  })
})
