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
// NULL の表示のテスト（Intent 261016-business-data-views の U3 の L8、D15、AC1.5.5）。
import { render, screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import { NullValue } from './NullValue'

describe('NullValue', () => {
  it('shows the visible text and hides it from the reading', () => {
    render(<NullValue text="（値なし）" spokenText="値なし" />)
    const visible = screen.getByText('（値なし）')
    expect(visible).toHaveAttribute('aria-hidden', 'true')
    expect(screen.getByText('値なし')).toHaveClass('shared-null-value-spoken')
  })

  it('reads as the spoken text inside a cell', () => {
    render(
      <table>
        <tbody>
          <tr>
            <td data-testid="cell">
              <NullValue text="(none)" spokenText="no value" />
            </td>
          </tr>
        </tbody>
      </table>,
    )
    expect(screen.getByTestId('cell')).toHaveTextContent('(none)no value')
    expect(screen.getByRole('cell', { name: 'no value' })).toBeInTheDocument()
  })

  it('marks the value with words, not only with a color', () => {
    render(<NullValue text="（値なし）" spokenText="値なし" />)
    expect(screen.getByTestId('null-value')).toHaveClass('shared-null-value')
    expect(screen.getByTestId('null-value')).toHaveTextContent('（値なし）')
  })

  it('shows markup in the text as text', () => {
    render(<NullValue text="<i>x</i>" spokenText="<b>y</b>" />)
    expect(screen.getByText('<i>x</i>')).toBeInTheDocument()
    expect(screen.getByText('<b>y</b>')).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <main>
        <NullValue text="（値なし）" spokenText="値なし" />
      </main>,
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
