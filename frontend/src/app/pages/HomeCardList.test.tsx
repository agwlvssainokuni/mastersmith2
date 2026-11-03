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
// ホームのカードの並びのテスト（Intent 261102-timezone-ui-polish の U5、D21）。
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import { renderWithProviders, resetDisplayTestState } from '../testing/renderWithProviders'
import { HomeCardList } from './HomeCardList'
import type { HomeCardModel } from './homeCards'

afterEach(() => {
  resetDisplayTestState()
})

const CARDS: HomeCardModel[] = [
  { id: 'biz:0', label: '受注', icon: 'table', path: '/tables?schema=S&table=ORDER' },
  { id: 'biz:1', label: '集計', icon: 'view', path: '/tables?schema=S&table=V' },
  { id: 'biz:2', label: 'まとめ', icon: 'folder', path: '/tables?schema=S&table=LATER' },
]

function renderList(cards: readonly HomeCardModel[] = CARDS) {
  return renderWithProviders(
    <section>
      <h2 id="heading">業務</h2>
      <HomeCardList headingId="heading" cards={cards} testIdPrefix="home-business" />
    </section>,
  )
}

describe('HomeCardList', () => {
  it('is a list named by its heading with one link per card in the given order', () => {
    renderList()

    const list = screen.getByRole('list', { name: '業務' })
    const links = within(list).getAllByRole('link')
    expect(links.map((link) => link.textContent)).toEqual(['受注', '集計', 'まとめ'])
    expect(within(list).getAllByRole('listitem')).toHaveLength(3)
    expect(links[1]).toHaveAttribute('data-testid', 'home-business-card-1')
  })

  it('reaches every card with Tab and opens one with Enter', async () => {
    const user = userEvent.setup()
    renderList()

    await user.tab()
    expect(screen.getByRole('link', { name: '受注' })).toHaveFocus()
    await user.tab()
    expect(screen.getByRole('link', { name: '集計' })).toHaveFocus()
    await user.keyboard('{Enter}')
    expect(screen.getByRole('link', { name: '集計' })).toHaveAttribute(
      'href',
      '/tables?schema=S&table=V',
    )
  })

  it('has no accessibility violations', async () => {
    const { container } = renderList()

    expect(await axe(container)).toHaveNoViolations()
  })
})
