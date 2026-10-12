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
// 誤りの一覧のテスト（Intent 261004-role-menu の U6 の D26、AC3.1.5・AC3.1.12・AC3.1.13）。件数の警告に出たときに
// フォーカス、無い値は「—」、path は点の形のまま、reason は画面の言語の文言で知らない値は汎用、サーバーの文言を出さない、
// 横に動く表の領域にフォーカスと名前があることを確かめる。
import { screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import type { TransferErrorItem } from './api/types'
import { renderRoleTransfer } from './testing/fixtures'
import { TransferErrorList } from './TransferErrorList'

const ERRORS: TransferErrorItem[] = [
  { reason: 'UNKNOWN_KEY', line: 3, column: 5, path: 'roles.0.permissions.SALES.x' },
  { reason: 'SOMETHING_NEW', line: null, column: null, path: null },
]

async function renderList(errors: TransferErrorItem[] = ERRORS) {
  const view = renderRoleTransfer(<TransferErrorList errors={errors} />)
  await screen.findByTestId('roletransfer-errors')
  return view
}

describe('TransferErrorList', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('focuses the count alert when shown', async () => {
    await renderList()
    const alert = screen.getByTestId('roletransfer-errors-alert')
    expect(alert).toHaveAttribute('role', 'alert')
    expect(alert).toHaveTextContent('ファイルに誤りがあります（2 件）。')
    await waitFor(() => expect(alert).toHaveFocus())
  })

  it('shows line, column, dotted path and the localized reason, with a dash for missing values', async () => {
    await renderList()
    const [first, second] = screen.getAllByTestId('roletransfer-errors-row')
    expect(first).toHaveTextContent('35roles.0.permissions.SALES.x知らない項目です')
    expect(second).toHaveTextContent('———ファイルに誤りがあります')
  })

  it('makes the scrolling table region focusable and named', async () => {
    await renderList()
    const region = screen.getByRole('region', { name: '誤りの一覧の表' })
    expect(region).toHaveAttribute('tabindex', '0')
  })

  it('shows only the alert when the list is empty', async () => {
    await renderList([])
    expect(screen.getByTestId('roletransfer-errors-alert')).toHaveTextContent('（0 件）')
    expect(screen.queryByTestId('roletransfer-errors-region')).toBeNull()
  })

  it('has no accessibility violations', async () => {
    const view = await renderList()
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
