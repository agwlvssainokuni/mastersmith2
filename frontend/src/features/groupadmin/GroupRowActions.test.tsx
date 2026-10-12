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
// グループの一覧の行の操作のテスト（Intent 261004-role-menu の U6 の D6）。「開く」のリンクの先と読み上げの名前、
// Dropdown の項目と bottom-end、busy の間はメニューを開かず選んでも呼ばないことを確かめる。
import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { GroupRowActions } from './GroupRowActions'
import { LONG_GROUP_NAME, groupRowOf } from './testing/fixtures'
import { renderGroupAdmin } from './testing/renderGroupAdmin'

async function renderActions(busy = false, name = '営業部') {
  const onSelect = vi.fn()
  renderGroupAdmin(
    <GroupRowActions group={groupRowOf({ groupId: 7, name })} busy={busy} onSelect={onSelect} />,
  )
  await screen.findByTestId('groupadmin-row-actions-7')
  return { onSelect }
}

describe('GroupRowActions', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('links "open" to the group detail with the group name in its accessible name', async () => {
    await renderActions()
    const link = screen.getByRole('link', { name: '営業部を開く' })
    expect(link).toHaveAttribute('href', '/admin/groups/7')
    expect(link).toHaveTextContent('開く')
  })

  it('opens the menu with rename and delete and reports the choice', async () => {
    const user = userEvent.setup()
    const { onSelect } = await renderActions()
    await user.click(screen.getByRole('button', { name: '営業部の操作' }))
    const menu = screen.getByRole('menu')
    expect(
      within(menu)
        .getAllByRole('menuitem')
        .map((item) => item.textContent),
    ).toEqual(['名前を変える', '削除'])
    await user.click(within(menu).getByRole('menuitem', { name: '削除' }))
    expect(onSelect).toHaveBeenCalledWith('delete')
  })

  it('places the menu at bottom-end (its right edge on the trigger right edge)', async () => {
    const user = userEvent.setup()
    // jsdom は配置を計算しないため、trigger と menu の矩形を与えて、Dropdown が求めた位置で bottom-end を確かめる。
    vi.spyOn(HTMLElement.prototype, 'getBoundingClientRect').mockImplementation(function (
      this: HTMLElement,
    ) {
      const menu = this.getAttribute('role') === 'menu'
      return menu
        ? DOMRect.fromRect({ x: 0, y: 0, width: 200, height: 80 })
        : DOMRect.fromRect({ x: 300, y: 100, width: 80, height: 30 })
    })
    await renderActions()
    await user.click(screen.getByRole('button', { name: '営業部の操作' }))
    const menu = await screen.findByRole('menu')
    await waitFor(() => expect(menu.style.left).toBe('180px'))
  })

  it('marks the trigger busy and does not open the menu while busy', async () => {
    const user = userEvent.setup()
    const { onSelect } = await renderActions(true)
    const trigger = screen.getByRole('button', { name: '営業部の操作（処理中）' })
    await user.click(trigger)
    expect(screen.queryByRole('menu')).toBeNull()
    expect(onSelect).not.toHaveBeenCalled()
  })

  it('has no accessibility violations with a 64 character name and the menu open', async () => {
    const user = userEvent.setup()
    await renderActions(false, LONG_GROUP_NAME)
    await user.click(screen.getByRole('button', { name: `${LONG_GROUP_NAME}の操作` }))
    await screen.findByRole('menu')
    // メニューは make-you-chic-ui が body の直下に描く（ランドマークの外）ため、best-practice の region の規則に当たる。
    // useradmin の UserRowActions のテストと同じく、WCAG 2.0・2.1 の A・AA の規則で確かめる（文字のコントラストは
    // jsdom で計算できないため、実際のブラウザの 140 で確かめる）。
    expect(
      await axe(document.body, {
        runOnly: { type: 'tag', values: ['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'] },
        rules: {
          'color-contrast': { enabled: false },
          'link-in-text-block': { enabled: false },
        },
      }),
    ).toHaveNoViolations()
  })
})
