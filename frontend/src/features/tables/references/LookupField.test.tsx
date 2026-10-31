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
// LOOKUP の項目のテスト（Intent 261029-reference-lookup-cleanup の U3、D16〜D20、AC2.2.3・AC2.2.7・AC2.3.1〜AC2.3.3、R-02）。
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../../app/testing/renderWithProviders'
import { renderReference } from '../testing/renderReference'
import { SAMPLE_PICKER, SAMPLE_SOURCE, XSS_SAMPLES, pickerRow } from '../testing/referenceSamples'
import { LookupField, type LookupFieldProps } from './LookupField'

afterEach(() => resetDisplayTestState())

function renderField(overrides: Partial<LookupFieldProps> = {}) {
  const props: LookupFieldProps = {
    index: 4,
    name: '商品',
    picker: SAMPLE_PICKER,
    source: SAMPLE_SOURCE,
    value: '',
    label: null,
    labelLoading: false,
    clearable: true,
    error: undefined,
    disabled: false,
    onChange: vi.fn(),
    search: vi.fn(() => Promise.resolve({ rows: [pickerRow(0)], page: 1, hasNext: false })),
    ...overrides,
  }
  const view = renderReference(<LookupField {...props} />)
  return { props, view }
}

describe('LookupField', () => {
  it('shows an empty field with the hint, "label (value)" and the value alone', async () => {
    const first = renderField()
    const empty = await screen.findByRole('textbox', { name: '商品' })
    expect(empty).toHaveValue('')
    expect(empty).toHaveAccessibleDescription('検索して選びます')
    first.view.unmount()
    const second = renderField({ value: 'P001', label: '青い 木製の椅子' })
    expect(await screen.findByRole('textbox')).toHaveValue('青い 木製の椅子 (P001)')
    second.view.unmount()
    renderField({ value: 'P001', label: null, labelLoading: true })
    const busy = await screen.findByRole('textbox')
    expect(busy).toHaveValue('P001')
    expect(busy).toHaveAttribute('aria-busy', 'true')
  })

  it('names the buttons starting with the visible text (D17)', async () => {
    renderField({ value: 'P001' })
    expect(await screen.findByRole('button', { name: '選ぶ…（商品）' })).toHaveTextContent('選ぶ…')
    expect(screen.getByRole('button', { name: 'クリア（商品）' })).toHaveTextContent('クリア')
  })

  it('offers clear only for nullable fields and moves the focus to the field after clearing', async () => {
    const { props, view } = renderField({ value: 'P001', label: 'x' })
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: 'クリア（商品）' }))
    expect(props.onChange).toHaveBeenCalledWith('', null)
    expect(screen.getByRole('textbox')).toHaveFocus()
    view.unmount()
    renderField({ value: 'P001', clearable: false })
    await screen.findByRole('textbox')
    expect(screen.queryByRole('button', { name: /クリア/ })).toBeNull()
  })

  it('cannot open or clear while saving', async () => {
    renderField({ value: 'P001', disabled: true })
    expect(await screen.findByRole('button', { name: '選ぶ…（商品）' })).toBeDisabled()
    expect(screen.getByRole('button', { name: 'クリア（商品）' })).toBeDisabled()
  })

  it('binds the error only to the field, not to the buttons (R-02)', async () => {
    const { view } = renderField({ value: 'P001', error: '選べない値です。選び直してください' })
    const input = await screen.findByRole('textbox')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription('選べない値です。選び直してください')
    for (const button of screen.getAllByRole('button')) {
      expect(button).not.toHaveAttribute('aria-describedby')
      expect(button).not.toHaveAttribute('aria-invalid')
    }
    const ids = [...view.container.querySelectorAll('[id]')].map((element) => element.id)
    expect(new Set(ids).size).toBe(ids.length)
  })

  it('opens the picker and puts the chosen value and label', async () => {
    const { props } = renderField()
    const user = userEvent.setup()
    await user.click(await screen.findByRole('button', { name: '選ぶ…（商品）' }))
    const dialog = await screen.findByRole('dialog', { name: '商品を選ぶ' })
    await user.type(
      await screen.findByRole('textbox', { name: '商品コード（一致）' }),
      'P001{Enter}',
    )
    await user.click(await screen.findByRole('button', { name: '商品 1を選ぶ' }))
    await waitFor(() => expect(dialog).not.toBeInTheDocument())
    expect(props.onChange).toHaveBeenCalledWith('P001', '商品 1')
    await waitFor(() => expect(screen.getByRole('button', { name: '選ぶ…（商品）' })).toHaveFocus())
  })

  it.each(XSS_SAMPLES)('shows the label %s as text', async (label) => {
    const { view } = renderField({ value: 'X', label })
    expect(await screen.findByRole('textbox')).toHaveValue(`${label} (X)`)
    expect(view.container.querySelector('script, img')).toBeNull()
  })

  it('has no accessibility violations with and without an error', async () => {
    const first = renderField({ value: 'P001', label: 'x' })
    await screen.findByRole('textbox')
    expect(await axe(first.view.container)).toHaveNoViolations()
    first.view.unmount()
    const second = renderField({ error: '選べない値です。選び直してください' })
    await screen.findByRole('textbox')
    expect(await axe(second.view.container)).toHaveNoViolations()
  })
})

// 直しの Bolt B7（U3 R-01・R-02）: 必須の印と、FormField の文脈による欄への結び付け
describe('LookupField required mark and field binding', () => {
  it('marks a required field (not clearable) and sets aria-required on the field (R-01)', async () => {
    renderField({ clearable: false })
    const input = await screen.findByRole('textbox', { name: '商品 *必須' })
    expect(input).toHaveAttribute('aria-required', 'true')
    expect(input).toHaveAttribute('data-testid', 'tables-lookup-value-c4')
  })

  it('has no mark and no aria-required for a clearable field', async () => {
    renderField({ clearable: true })
    const input = await screen.findByRole('textbox', { name: '商品' })
    expect(input).not.toHaveAttribute('aria-required')
  })

  it('returns the field for the label text through the label for attribute (R-02)', async () => {
    renderField({ clearable: false })
    const input = await screen.findByLabelText('商品 *必須')
    expect(input).toHaveAttribute('data-testid', 'tables-lookup-value-c4')
    const label = document.querySelector(`label[for="${input.id}"]`)
    expect(label).not.toBeNull()
    expect(label).toHaveTextContent('商品 *必須')
  })

  it('points aria-describedby at the error text and sets aria-invalid on the field only (R-02)', async () => {
    renderField({ clearable: false, error: '値を選んでください' })
    const input = await screen.findByTestId('tables-lookup-value-c4')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    const describedBy = input.getAttribute('aria-describedby') ?? ''
    const described = describedBy
      .split(' ')
      .filter((id) => id !== '')
      .map((id) => document.getElementById(id))
    expect(described.length).toBeGreaterThan(0)
    expect(described.some((element) => element?.textContent === '値を選んでください')).toBe(true)
    for (const button of screen.getAllByRole('button')) {
      expect(button).not.toHaveAttribute('aria-describedby')
      expect(button).not.toHaveAttribute('aria-invalid')
    }
  })

  it('points aria-describedby at the hint while empty and drops it once a value is set (R-02)', async () => {
    const first = renderField({ value: '' })
    const empty = await screen.findByTestId('tables-lookup-value-c4')
    expect(empty).not.toHaveAttribute('aria-invalid')
    const hintId = empty.getAttribute('aria-describedby') ?? ''
    expect(hintId).not.toBe('')
    expect(document.getElementById(hintId)).toHaveTextContent('検索して選びます')
    first.view.unmount()
    renderField({ value: 'P001', label: 'x' })
    const filled = await screen.findByTestId('tables-lookup-value-c4')
    expect(filled).not.toHaveAttribute('aria-describedby')
    expect(filled).not.toHaveAttribute('aria-invalid')
    for (const button of screen.getAllByRole('button')) {
      expect(button).not.toHaveAttribute('aria-describedby')
      expect(button).not.toHaveAttribute('aria-invalid')
    }
  })

  it('has no accessibility violations for a required field with an error', async () => {
    const { view } = renderField({ clearable: false, error: '値を選んでください' })
    await screen.findByRole('textbox')
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
