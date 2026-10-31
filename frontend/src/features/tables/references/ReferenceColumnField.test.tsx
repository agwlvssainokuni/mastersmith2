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
// カラムごとの部品の選び分けのテスト（Intent 261029-reference-lookup-cleanup の U3、D5、X-4）。選び分けの表を1件ずつ描く。
import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../../app/testing/renderWithProviders'
import type { ColumnDefinition } from '../businessDataTypes'
import { renderReference } from '../testing/renderReference'
import { lookupField, referenceField } from '../testing/referenceSamples'
import { ReferenceColumnField, type ReferenceColumnFieldProps } from './ReferenceColumnField'

afterEach(() => resetDisplayTestState())

function column(overrides: Partial<ColumnDefinition> = {}): ColumnDefinition {
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
    reference: referenceField(),
    ...overrides,
  }
}

function renderColumn(overrides: Partial<ReferenceColumnFieldProps> = {}) {
  const props: ReferenceColumnFieldProps = {
    column: column(),
    index: 5,
    name: '顧客',
    mode: 'create',
    truncated: [],
    table: { schemaName: 'shop', tableName: 'order' },
    value: '',
    initialValue: 'C001',
    error: undefined,
    disabled: false,
    lookupLabel: null,
    lookupLabelLoading: false,
    onChange: vi.fn(),
    onBlur: vi.fn(),
    renderPlain: () => <p>既存の入力</p>,
    renderLocked: () => <p>既存の読み取り</p>,
    ...overrides,
  }
  return renderReference(<ReferenceColumnField {...props} />)
}

describe('ReferenceColumnField', () => {
  it('renders nothing for a column that cannot be entered on the create screen', () => {
    const view = renderColumn({ column: column({ editableOnCreate: false }) })
    expect(view.container.querySelector('[data-testid="tables-form-field-c5"]')).toBeNull()
    expect(screen.queryByText('既存の読み取り')).toBeNull()
  })

  it('renders the existing readonly display on the update screen, whatever the status (R-05)', () => {
    renderColumn({
      mode: 'update',
      column: column({ reference: referenceField({ status: 'NO_PERMISSION', options: [] }) }),
      truncated: ['customer'],
    })
    expect(screen.getByText('既存の読み取り')).toBeInTheDocument()
    expect(screen.queryByText('参照先を読む権限がないため変更できません')).toBeNull()
  })

  it('renders the existing input for a column without a reference', () => {
    renderColumn({ column: column({ reference: null, formPart: 'text' }) })
    expect(screen.getByText('既存の入力')).toBeInTheDocument()
  })

  it('renders the readonly reference with the stored value', async () => {
    renderColumn({
      mode: 'update',
      column: column({ reference: referenceField({ status: 'TOO_MANY', options: [] }) }),
    })
    expect(await screen.findByRole('textbox', { name: '顧客' })).toHaveValue('C001')
  })

  it('renders the REFERENCE select and the LOOKUP field', async () => {
    const first = renderColumn()
    expect(await screen.findByRole('combobox', { name: '顧客' })).toBeInTheDocument()
    first.unmount()
    renderColumn({
      column: column({ formPart: 'lookup', reference: lookupField() }),
      value: 'P001',
      lookupLabel: '青い 木製の椅子',
    })
    expect(await screen.findByRole('textbox', { name: '顧客' })).toHaveValue(
      '青い 木製の椅子 (P001)',
    )
    expect(screen.getByRole('button', { name: '選ぶ…（顧客）' })).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const view = renderColumn()
    await screen.findByRole('combobox')
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
