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
// 確かめの結果の表のテスト（Intent 261004-role-menu の U6 の D24、AC3.1.2・AC3.1.6・AC3.1.9・AC3.1.14）。列、行の開閉の
// 名前（aria-expanded・aria-controls）、100 件ずつのページ送り、理由と ⚠ の文字、表の下の文、名前が文字のまま出ることを
// 確かめる。
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import type { TransferCheck } from './api/types'
import { changeOf, checkOf, planOf, renderRoleTransfer, SCRIPT_NAME } from './testing/fixtures'
import { TransferCheckTable } from './TransferCheckTable'

async function renderTable(check: TransferCheck = checkOf()) {
  const view = renderRoleTransfer(<TransferCheckTable check={check} />)
  await screen.findByTestId('roletransfer-check-table')
  return view
}

describe('TransferCheckTable', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('lists each role with its action and counts', async () => {
    await renderTable()
    const table = screen.getByRole('table', { name: 'ロールごとの変わる点' })
    expect(
      within(table)
        .getAllByRole('columnheader')
        .map((cell) => cell.textContent),
    ).toEqual(['ロール', '変わり方', '足す', '変わる', '消える', '今の DSL に無い'])
    expect(screen.getByTestId('roletransfer-plan-0')).toHaveTextContent('置き換える')
    expect(screen.getByTestId('roletransfer-plan-1')).toHaveTextContent('作る')
  })

  it('opens and closes a row with a named toggle bound to the change table', async () => {
    const user = userEvent.setup()
    await renderTable()
    const open = screen.getByRole('button', { name: '営業の変わる点を開く' })
    expect(open).toHaveAttribute('aria-expanded', 'false')
    await user.click(open)
    const close = screen.getByRole('button', { name: '営業の変わる点を閉じる' })
    expect(close).toHaveAttribute('aria-expanded', 'true')
    const region = document.getElementById(close.getAttribute('aria-controls') ?? '')
    expect(region).toHaveAttribute('data-testid', 'roletransfer-changes-営業')
    const rows = within(region as HTMLElement).getAllByTestId('roletransfer-change-row')
    expect(rows[0]).toHaveTextContent('SALES.ORDER_LINE')
    expect(rows[0]).toHaveTextContent('主権限 FULL・CREATE 可・DELETE 設定なし')
    expect(rows[1]).toHaveTextContent('主権限 READ・CREATE 設定なし・DELETE 設定なし')
    expect(rows[1]).toHaveTextContent('ファイルに無い')
    await user.click(close)
    expect(screen.queryByTestId('roletransfer-changes-営業')).toBeNull()
  })

  it('pages the changes of an opened row by 100 and marks targets not in the DSL', async () => {
    const user = userEvent.setup()
    const changes = Array.from({ length: 150 }, (_, index) =>
      changeOf({
        target: { schemaName: 'SALES', tableName: `T${index}`, columnName: null },
        inCurrentDsl: index !== 0,
      }),
    )
    await renderTable(checkOf({ roles: [planOf({ changes, cleared: 150 })] }))
    await user.click(screen.getByRole('button', { name: '営業の変わる点を開く' }))
    expect(screen.getAllByTestId('roletransfer-change-row')).toHaveLength(100)
    expect(screen.getAllByTestId('roletransfer-change-row')[0]).toHaveTextContent(
      '⚠ 今の DSL に無い',
    )
    expect(screen.getByText('1〜100 件目 / 全 150 件')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '次へ' }))
    expect(screen.getAllByTestId('roletransfer-change-row')).toHaveLength(50)
    expect(screen.getByRole('button', { name: '次へ' })).toBeDisabled()
  })

  it('explains cleared settings and the roles that remain, and shows names as text', async () => {
    await renderTable(
      checkOf({
        roles: [planOf({ name: SCRIPT_NAME, cleared: 4 })],
        untouchedRoles: ['経理', '総務'],
      }),
    )
    expect(screen.getByTestId('roletransfer-summary-cleared')).toHaveTextContent('消える: 4')
    expect(screen.getByTestId('roletransfer-summary-untouched')).toHaveTextContent(
      'ファイルに無いロール（経理、総務）は残ります。',
    )
    expect(screen.getByText(SCRIPT_NAME)).toBeInTheDocument()
    expect(document.querySelector('script')).toBeNull()
  })

  it('has no accessibility violations with an opened row', async () => {
    const user = userEvent.setup()
    const view = await renderTable()
    await user.click(screen.getByRole('button', { name: '営業の変わる点を開く' }))
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
