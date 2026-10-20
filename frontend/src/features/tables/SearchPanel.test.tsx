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
// S1 の検索の領域のテスト（Intent 261016-business-data-views の U3 の L7、F2・Q3: A、AC1.2.1〜AC1.2.13、
// U3-NFR5.9・5.10、計画 4.3 の R-06）。
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { renderWithProviders, resetDisplayTestState } from '../../app/testing/renderWithProviders'
import type { BusinessFieldError, SearchField, SearchInputs } from './businessDataTypes'
import { registration } from './registration'
import { EMPTY_SEARCH_INPUT } from './searchConditions'
import { SearchPanel } from './SearchPanel'
import { wireColumn } from './testing/businessDataSamples'
import { parseColumnDefinition } from './businessDataParse'
import { JA_TEXTS } from './testing/valueTexts'

afterEach(() => resetDisplayTestState())

function field(column: string, ja: string, overrides: Partial<SearchField> = {}): SearchField {
  return {
    column,
    displayName: { ja, en: column },
    operator: 'EQUALS',
    valueType: 'STRING',
    defaultValue: null,
    collapsed: false,
    options: [],
    ...overrides,
  }
}

const FIELDS: SearchField[] = [
  field('code', '商品コード'),
  field('name', '商品名', { operator: 'CONTAINS' }),
  field('price', '価格', { operator: 'RANGE', valueType: 'DECIMAL' }),
  field('active', '有効', {
    operator: 'CHOICE',
    valueType: 'BOOLEAN',
    options: [
      { value: 'true', label: { ja: 'true', en: 'true' } },
      { value: 'false', label: { ja: 'false', en: 'false' } },
    ],
  }),
]

const ACTIVE = parseColumnDefinition(
  wireColumn('active', { valueType: 'BOOLEAN', listFormat: 'BOOLEAN_ON_OFF' }),
)

function renderPanel(
  props: { values?: SearchInputs; expanded?: boolean; serverErrors?: BusinessFieldError[] } = {},
) {
  const onSearch = vi.fn()
  const onClear = vi.fn()
  const onToggle = vi.fn()
  const view = renderWithProviders(
    <main>
      <SearchPanel
        fields={FIELDS}
        columns={ACTIVE === undefined ? [] : [ACTIVE]}
        values={props.values ?? {}}
        serverErrors={props.serverErrors ?? []}
        expanded={props.expanded ?? true}
        language="ja"
        texts={JA_TEXTS}
        onSearch={onSearch}
        onClear={onClear}
        onToggle={onToggle}
      />
    </main>,
    { registrations: [registration] },
  )
  return { onSearch, onClear, onToggle, view }
}

describe('SearchPanel', () => {
  it('names the fields with the operator and groups ranges and choices in fieldsets', async () => {
    renderPanel()
    expect(await screen.findByRole('textbox', { name: '商品コード（完全一致）' })).toHaveAttribute(
      'autocomplete',
      'off',
    )
    expect(screen.getByRole('textbox', { name: '商品名（部分一致）' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: '価格（範囲）' })).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '価格の下限' })).toHaveAttribute(
      'inputmode',
      'decimal',
    )
    expect(screen.getByRole('group', { name: '有効（選択肢）' })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'オン' })).toBeInTheDocument()
    expect(screen.getByRole('checkbox', { name: 'オフ' })).toBeInTheDocument()
  })

  it('opens and closes with aria-expanded and shows the condition count while closed', async () => {
    const user = userEvent.setup()
    const { onToggle } = renderPanel({
      expanded: false,
      values: {
        code: { ...EMPTY_SEARCH_INPUT, value: 'A' },
        name: { ...EMPTY_SEARCH_INPUT, value: 'B' },
      },
    })
    const toggle = await screen.findByRole('button', { name: '検索の条件 （2 件の条件）' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    expect(document.getElementById(toggle.getAttribute('aria-controls') ?? '')).not.toBeVisible()
    await user.click(toggle)
    expect(onToggle).toHaveBeenCalledTimes(1)
  })

  it('searches with Enter and the button, keeping % and _ as text', async () => {
    const user = userEvent.setup()
    const { onSearch } = renderPanel()
    await user.type(
      await screen.findByRole('textbox', { name: '商品名（部分一致）' }),
      '10%_off{Enter}',
    )
    expect(onSearch).toHaveBeenLastCalledWith({ name: { ...EMPTY_SEARCH_INPUT, value: '10%_off' } })
    await user.click(screen.getByRole('checkbox', { name: 'オン' }))
    await user.click(screen.getByRole('button', { name: '検索' }))
    expect(onSearch).toHaveBeenLastCalledWith({
      name: { ...EMPTY_SEARCH_INPUT, value: '10%_off' },
      active: { ...EMPTY_SEARCH_INPUT, values: ['true'] },
    })
  })

  it('ties a reversed range to its inputs, moves the focus there and does not search', async () => {
    const user = userEvent.setup()
    const { onSearch } = renderPanel()
    await user.type(await screen.findByRole('textbox', { name: '価格の下限' }), '10')
    await user.type(screen.getByRole('textbox', { name: '価格の上限' }), '9')
    await user.click(screen.getByRole('button', { name: '検索' }))
    const message = await screen.findByText('下限が上限より大きくなっています。')
    const from = screen.getByRole('textbox', { name: '価格の下限' })
    expect(from).toHaveFocus()
    expect(from).toHaveAttribute('aria-invalid', 'true')
    expect(from.getAttribute('aria-describedby')).toContain(message.id)
    expect(message.id).not.toContain('price')
    expect(onSearch).not.toHaveBeenCalled()
  })

  it('shows the field errors of the server on the field and clears the inputs', async () => {
    const user = userEvent.setup()
    const { onClear } = renderPanel({
      values: { code: { ...EMPTY_SEARCH_INPUT, value: 'X' } },
      serverErrors: [{ field: 'code', reason: 'INVALID_VALUE' }],
    })
    expect(
      await screen.findByText('値を読めませんでした。形を確かめてください。'),
    ).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '商品コード（完全一致）' })).toHaveAttribute(
      'aria-invalid',
      'true',
    )
    await user.click(screen.getByRole('button', { name: '条件をクリア' }))
    expect(onClear).toHaveBeenCalledTimes(1)
    expect(screen.getByRole('textbox', { name: '商品コード（完全一致）' })).toHaveValue('')
  })

  it('has no accessibility violations, also with an error', async () => {
    const { view } = renderPanel({ serverErrors: [{ field: 'price', reason: 'RANGE_REVERSED' }] })
    await screen.findByText('下限が上限より大きくなっています。')
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
