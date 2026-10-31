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
// REFERENCE の選択のテスト（Intent 261029-reference-lookup-cleanup の U3、D6〜D9、AC1.1.1〜AC1.1.10、R-02）。
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../../app/testing/renderWithProviders'
import type { ColumnDefinition, ReferenceOption } from '../businessDataTypes'
import { renderReference } from '../testing/renderReference'
import {
  SAMPLE_OPTIONS,
  XSS_OPTIONS,
  manyOptions,
  referenceField,
} from '../testing/referenceSamples'
import { ReferenceSelectField, type ReferenceSelectFieldProps } from './ReferenceSelectField'

afterEach(() => resetDisplayTestState())

function column(
  options: readonly ReferenceOption[] = SAMPLE_OPTIONS,
  overrides: Partial<ColumnDefinition> = {},
): ColumnDefinition {
  return {
    column: 'customer',
    displayName: { ja: '顧客', en: 'Customer' },
    valueType: 'STRING',
    listFormat: null,
    width: null,
    editableOnCreate: true,
    editableOnUpdate: true,
    masked: false,
    formPart: 'select',
    required: false,
    nullable: true,
    reference: referenceField({ options }),
    ...overrides,
  }
}

function renderField(overrides: Partial<ReferenceSelectFieldProps> = {}) {
  const props: ReferenceSelectFieldProps = {
    column: column(),
    index: 3,
    name: '顧客',
    mode: 'create',
    value: '',
    initialValue: '',
    error: undefined,
    disabled: false,
    onChange: vi.fn(),
    onBlur: vi.fn(),
    ...overrides,
  }
  const view = renderReference(<ReferenceSelectField {...props} />)
  return { props, view }
}

describe('ReferenceSelectField', () => {
  it('shows "label (value)" and the value alone for a null label, in the server order', async () => {
    const { props } = renderField()
    const select = await screen.findByRole('combobox', { name: '顧客' })
    const texts = within(select)
      .getAllByRole('option')
      .map((option) => option.textContent)
    expect(texts).toEqual(['（選ばない）', '東京 本店 (C001)', 'C002', '大阪 支店 (C003)'])
    await userEvent.setup().selectOptions(select, 'C003')
    expect(props.onChange).toHaveBeenCalledWith('C003')
  })

  it('adds the saved value alone at the top when it is not in the options (D8)', async () => {
    renderField({ mode: 'update', value: 'GONE', initialValue: 'GONE' })
    const select = await screen.findByRole('combobox', { name: '顧客' })
    expect(select).toHaveValue('GONE')
    expect(within(select).getAllByRole('option')[1]).toHaveTextContent(/^GONE$/)
  })

  it('omits the unselected option for a required field with a value', async () => {
    renderField({ column: column(SAMPLE_OPTIONS, { required: true }), value: 'C001' })
    const select = await screen.findByRole('combobox', { name: /^顧客 / })
    expect(within(select).queryByRole('option', { name: '（選ばない）' })).toBeNull()
    expect(select).toHaveAttribute('aria-required', 'true')
  })

  it('shows the no-options text and the required text for zero options (D9)', async () => {
    renderField({ column: column([], { nullable: false, required: true }) })
    const select = await screen.findByRole('combobox')
    expect(within(select).getAllByRole('option')).toHaveLength(1)
    expect(select).toHaveAccessibleDescription(
      '選べる候補がありません この項目は必須のため、候補が入るまで保存できません',
    )
  })

  it('shows XSS labels as text (AC1.1.10)', async () => {
    const { view } = renderField({ column: column(XSS_OPTIONS) })
    const select = await screen.findByRole('combobox')
    expect(
      within(select).getByRole('option', { name: '<script>alert(1)</script> (X0)' }),
    ).toBeTruthy()
    expect(view.container.querySelector('script, img')).toBeNull()
    for (const element of view.container.querySelectorAll('[data-testid], [id]')) {
      expect(element.getAttribute('data-testid') ?? '').not.toMatch(/C00|X\d|script/)
      expect(element.id).not.toMatch(/C00|script/)
    }
  })

  it('binds the error to the select (R-02)', async () => {
    renderField({ error: '選べない値です。選び直してください' })
    const select = await screen.findByRole('combobox')
    expect(select).toHaveAttribute('aria-invalid', 'true')
    expect(select).toHaveAccessibleDescription('選べない値です。選び直してください')
  })

  it('renders 200 radio options in one group reachable by the arrow keys and binds the error to the fieldset', async () => {
    const options = manyOptions(200)
    const { props } = renderField({
      column: column(options, { formPart: 'radio' }),
      value: 'V001',
      error: '必須です',
    })
    const radios = await screen.findAllByRole('radio')
    expect(radios).toHaveLength(200)
    expect(new Set(radios.map((radio) => radio.getAttribute('name')))).toEqual(
      new Set(['tables-form-r3']),
    )
    expect(screen.getByRole('radio', { name: '候補 200 (V200)' })).toBeInTheDocument()
    const group = screen.getByRole('group')
    expect(group).toHaveAttribute('aria-invalid', 'true')
    expect(group).toHaveAccessibleDescription('必須です')
    const user = userEvent.setup()
    await user.tab()
    expect(radios[0]).toHaveFocus()
    await user.keyboard('{ArrowDown}')
    expect(props.onChange).toHaveBeenLastCalledWith('V002')
  })

  it('has no accessibility violations for select and 200 radios', async () => {
    const first = renderField()
    await screen.findByRole('combobox')
    expect(await axe(first.view.container)).toHaveNoViolations()
    first.view.unmount()
    const second = renderField({
      column: column(manyOptions(200), { formPart: 'radio' }),
      value: 'V010',
    })
    await screen.findAllByRole('radio')
    expect(await axe(second.view.container)).toHaveNoViolations()
  })
})
