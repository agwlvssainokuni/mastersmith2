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
// 横のスクロールの枠のテスト（Intent 261016-business-data-views の U3 の L8、D27、AC1.3.15）。
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import { ScrollRegion } from './ScrollRegion'

describe('ScrollRegion', () => {
  it('is a named region that wraps its content', () => {
    render(
      <ScrollRegion label="商品の一覧の表">
        <table aria-label="表">
          <tbody>
            <tr>
              <td>値</td>
            </tr>
          </tbody>
        </table>
      </ScrollRegion>,
    )
    const region = screen.getByRole('region', { name: '商品の一覧の表' })
    expect(region).toContainElement(screen.getByRole('table'))
    expect(region).toHaveClass('shared-scroll-region')
  })

  it('takes the keyboard focus so it can be scrolled with the arrow keys', async () => {
    const user = userEvent.setup()
    render(
      <>
        <button type="button">前</button>
        <ScrollRegion label="枠">
          <p>中身</p>
        </ScrollRegion>
      </>,
    )
    await user.click(screen.getByRole('button', { name: '前' }))
    await user.tab()
    expect(screen.getByRole('region', { name: '枠' })).toHaveFocus()
    expect(screen.getByRole('region', { name: '枠' })).toHaveAttribute('tabindex', '0')
  })

  it('shows the label as given without interpreting markup', () => {
    render(
      <ScrollRegion label="<b>名前</b>">
        <p>x</p>
      </ScrollRegion>,
    )
    expect(screen.getByRole('region', { name: '<b>名前</b>' })).toBeInTheDocument()
  })

  it('puts the test id only when given', () => {
    const { rerender } = render(
      <ScrollRegion label="枠">
        <p>x</p>
      </ScrollRegion>,
    )
    expect(screen.getByRole('region')).not.toHaveAttribute('data-testid')
    rerender(
      <ScrollRegion label="枠" testId="tables-scroll">
        <p>x</p>
      </ScrollRegion>,
    )
    expect(screen.getByTestId('tables-scroll')).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <main>
        <ScrollRegion label="商品の一覧の表">
          <p>中身</p>
        </ScrollRegion>
      </main>,
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
