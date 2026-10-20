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
// 行の「操作」のテスト（Intent 261016-business-data-views の U3 の L7、D9、AC4.1.1、U3-NFR4.21・5.7）。
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { renderWithProviders, resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { registration } from './registration'
import type { Operation } from './businessDataTypes'
import { RowActionsMenu, rowActionItems } from './RowActionsMenu'

afterEach(() => resetDisplayTestState())

function renderMenu(props: { busy?: boolean; operations?: Operation[] } = {}) {
  const onSelect = vi.fn()
  const view = renderWithProviders(
    <main>
      <RowActionsMenu
        index={3}
        rowLabel="A01 見本の品"
        operations={props.operations ?? ['LIST', 'DETAIL']}
        busy={props.busy ?? false}
        onSelect={onSelect}
      />
    </main>,
    { registrations: [registration] },
  )
  return { onSelect, view }
}

describe('RowActionsMenu', () => {
  it('names the trigger with the visible text and the row label', async () => {
    renderMenu()
    const trigger = await screen.findByRole('button', { name: '操作: A01 見本の品' })
    expect(trigger).toHaveTextContent('操作')
    expect(screen.getByTestId('tables-row-actions-r3')).toContainElement(trigger)
  })

  it('opens the menu with only the allowed items and tells the choice', async () => {
    const user = userEvent.setup()
    const { onSelect } = renderMenu()
    await user.click(await screen.findByRole('button', { name: '操作: A01 見本の品' }))
    expect(screen.getAllByRole('menuitem').map((item) => item.textContent)).toEqual(['詳細'])
    await user.click(screen.getByRole('menuitem', { name: '詳細' }))
    expect(onSelect).toHaveBeenCalledWith('detail')
  })

  it('adds edit and delete only when they are allowed (B6)', async () => {
    const user = userEvent.setup()
    const { onSelect } = renderMenu({ operations: ['LIST', 'DETAIL', 'UPDATE', 'DELETE'] })
    await user.click(await screen.findByRole('button', { name: '操作: A01 見本の品' }))
    expect(screen.getAllByRole('menuitem').map((item) => item.textContent)).toEqual([
      '詳細',
      '更新',
      '削除',
    ])
    await user.click(screen.getByRole('menuitem', { name: '削除' }))
    expect(onSelect).toHaveBeenCalledWith('delete')
  })

  it('adds the processing suffix while busy and does not open', async () => {
    const user = userEvent.setup()
    const { onSelect } = renderMenu({ busy: true })
    const trigger = await screen.findByRole('button', { name: '操作: A01 見本の品（処理中）' })
    await user.click(trigger)
    expect(screen.queryByRole('menuitem')).toBeNull()
    expect(onSelect).not.toHaveBeenCalled()
  })

  it('shows nothing without allowed operations', async () => {
    renderMenu({ operations: ['LIST'] })
    await screen.findByRole('main')
    expect(screen.queryByRole('button')).toBeNull()
    expect(rowActionItems(['LIST'])).toEqual([])
    expect(rowActionItems(['LIST', 'DETAIL'])).toEqual(['detail'])
    expect(rowActionItems(['LIST', 'UPDATE', 'DELETE'])).toEqual([])
    expect(rowActionItems(['LIST', 'DETAIL', 'UPDATE', 'DELETE'])).toEqual([
      'detail',
      'update',
      'delete',
    ])
  })

  it('has no accessibility violations', async () => {
    const { view } = renderMenu()
    await screen.findByRole('button')
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
