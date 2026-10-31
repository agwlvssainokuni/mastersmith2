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
// 入力不可の参照の項目のテスト（Intent 261029-reference-lookup-cleanup の U3、D11〜D14、security-design.md 2.1・2.4、NFR 設計の
// Q1: A）。開いた直後は説明文（role="alert"・aria-invalid なし）、保存を押した後は誤り（aria-invalid と誤りの id）。
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../../app/testing/renderWithProviders'
import { renderReference } from '../testing/renderReference'
import { ReadonlyReferenceField, type ReadonlyReferenceFieldProps } from './ReadonlyReferenceField'

afterEach(() => resetDisplayTestState())

const PERMISSION = '参照先を読む権限がないため変更できません'
const TOO_MANY =
  '候補が多いため選べません。管理者に、検索（ピッカー）への切り替えを依頼してください'
const BLOCKED =
  '必須の参照項目に値を入れられません。参照先を読む権限がないためです。管理者に権限を依頼してください'

function renderField(overrides: Partial<ReadonlyReferenceFieldProps> = {}) {
  const props: ReadonlyReferenceFieldProps = {
    index: 2,
    name: '顧客',
    value: 'C001',
    reason: 'permission',
    blockedRequired: false,
    error: undefined,
    ...overrides,
  }
  return renderReference(<ReadonlyReferenceField {...props} />)
}

describe('ReadonlyReferenceField', () => {
  it('is a read-only input that stays in the tab order and shows the stored value', async () => {
    renderField()
    const input = await screen.findByRole('textbox', { name: '顧客' })
    expect(input).toHaveValue('C001')
    expect(input).toHaveAttribute('readonly')
    expect(input).toHaveAttribute('aria-readonly', 'true')
    expect(input).not.toBeDisabled()
    await userEvent.setup().tab()
    expect(input).toHaveFocus()
  })

  it.each([
    ['permission', PERMISSION],
    ['too-many', TOO_MANY],
  ] as const)('describes the reason %s with the helper text', async (reason, text) => {
    renderField({ reason })
    const input = await screen.findByRole('textbox')
    expect(input).toHaveAccessibleDescription(text)
    expect(input).not.toHaveAttribute('aria-invalid')
  })

  it('shows the blocked required text as a helper without an alert right after opening', async () => {
    renderField({ value: '', blockedRequired: true })
    const input = await screen.findByRole('textbox')
    expect(input).toHaveAccessibleDescription(BLOCKED)
    expect(input).not.toHaveAttribute('aria-invalid')
    expect(screen.queryByRole('alert')).toBeNull()
  })

  it('binds the error after saving and marks the input invalid', async () => {
    renderField({ value: '', blockedRequired: true, error: BLOCKED })
    const input = await screen.findByRole('textbox')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent(BLOCKED)
    expect(input.getAttribute('aria-describedby')).toContain(alert.id)
  })

  it.each([
    ['permission', { reason: 'permission' as const }],
    ['too many', { reason: 'too-many' as const }],
    ['right after opening', { blockedRequired: true, value: '' }],
    ['after saving', { blockedRequired: true, value: '', error: BLOCKED }],
  ])('has no accessibility violations (%s)', async (_name, overrides) => {
    const view = renderField(overrides)
    await screen.findByRole('textbox')
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
