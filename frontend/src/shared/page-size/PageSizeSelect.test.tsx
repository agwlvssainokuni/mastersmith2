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
// 1ページの件数の選択のテスト（Intent 261016-business-data-views の U3 の L8、D11、AC1.3.2）。
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { ThemeProvider } from 'make-you-chic-ui'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { PageSizeSelect } from './PageSizeSelect'

const SIZES = [20, 50, 100] as const

function renderSelect(props: { value?: 20 | 50 | 100; disabled?: boolean } = {}) {
  const onChange = vi.fn()
  const view = render(
    <ThemeProvider>
      <main>
        <PageSizeSelect
          value={props.value ?? 20}
          options={SIZES}
          onChange={onChange}
          label="1ページの件数"
          disabled={props.disabled}
        />
      </main>
    </ThemeProvider>,
  )
  return { onChange, view }
}

describe('PageSizeSelect', () => {
  it('is a combobox named by the label with the given options in order', () => {
    renderSelect()
    const select = screen.getByRole('combobox', { name: '1ページの件数' })
    expect(select).toHaveValue('20')
    expect(screen.getAllByRole('option').map((option) => option.textContent)).toEqual([
      '20',
      '50',
      '100',
    ])
  })

  it('tells the chosen size as a number', async () => {
    const user = userEvent.setup()
    const { onChange } = renderSelect()
    await user.selectOptions(screen.getByRole('combobox'), '100')
    expect(onChange).toHaveBeenCalledWith(100)
  })

  it('does not tell when the same size is chosen again', async () => {
    const user = userEvent.setup()
    const { onChange } = renderSelect({ value: 50 })
    await user.selectOptions(screen.getByRole('combobox'), '50')
    expect(onChange).not.toHaveBeenCalled()
  })

  it('cannot be changed while disabled', () => {
    renderSelect({ disabled: true })
    expect(screen.getByRole('combobox')).toBeDisabled()
  })

  it('shows the current value given by the caller', () => {
    renderSelect({ value: 100 })
    expect(screen.getByRole('combobox')).toHaveValue('100')
  })

  it('has no accessibility violations', async () => {
    const { view } = renderSelect()
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
