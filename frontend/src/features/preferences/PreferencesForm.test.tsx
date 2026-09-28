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
// プリファレンスのフォームのテスト（functional-spec.md の W3〜W5・W8・W12・W13、D9〜D11・D14、CR6.1・CR6.3・CR6.6、
// NFR7.2・NFR7.5・NFR8.1）。値と操作は props で渡す（状態の移り変わりは PreferencesPage のテストで確かめる）。
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import type { Preferences } from './preferencesApi'
import { PreferencesForm, type PreferencesFormProps } from './PreferencesForm'
import { NAMED_PREFERENCES } from './testing/fixtures'
import { renderPreferencesPart } from './testing/renderPreferences'

beforeEach(() => {
  resetDisplayTestState()
})

function props(overrides: Partial<PreferencesFormProps> = {}): PreferencesFormProps {
  return {
    headingId: 'heading',
    form: NAMED_PREFERENCES,
    fieldErrors: {},
    alert: undefined,
    dirty: false,
    saving: false,
    focusRequest: undefined,
    setDisplayName: vi.fn(),
    setLanguage: vi.fn(),
    setTheme: vi.fn(),
    setFontSize: vi.fn(),
    reset: vi.fn(),
    save: vi.fn(),
    dismissAlert: vi.fn(),
    ...overrides,
  }
}

function renderForm(overrides: Partial<PreferencesFormProps> = {}, languages = ['ja-JP']) {
  const all = props(overrides)
  const result = renderPreferencesPart(
    <>
      <h1 id="heading">プリファレンス</h1>
      <PreferencesForm {...all} />
    </>,
    languages,
  )
  return { ...result, props: all }
}

describe('PreferencesForm', () => {
  it('draws three groups as fieldsets named by their legends, the language with its hint', async () => {
    renderForm()
    expect(await screen.findByRole('form', { name: 'プリファレンス' })).toBeInTheDocument()
    const groups = screen.getAllByRole('group')
    expect(groups.map((group) => group.tagName)).toEqual(['FIELDSET', 'FIELDSET', 'FIELDSET'])
    expect(
      screen.getByRole('group', { name: /言語.*保存すると切り替わります/ }),
    ).toBeInTheDocument()
    expect(screen.getByRole('group', { name: 'テーマ' })).toBeInTheDocument()
    expect(screen.getByRole('group', { name: '文字の大きさ' })).toBeInTheDocument()
    expect(screen.getByTestId('preferences-appearance-hint')).toHaveTextContent(
      'テーマと文字の大きさは選ぶと画面に反映されます',
    )
  })

  it('marks the language names with their lang and orders the themes as in the mockup', async () => {
    renderForm({}, ['en-US'])
    const language = within(await screen.findByTestId('preferences-language'))
    expect(language.getByText('日本語')).toHaveAttribute('lang', 'ja')
    expect(language.getByText('English')).toHaveAttribute('lang', 'en')
    const theme = within(screen.getByTestId('preferences-theme'))
    expect(theme.getAllByRole('radio').map((radio) => radio.getAttribute('value'))).toEqual([
      'system',
      'light',
      'dark',
    ])
    expect(theme.getByText('Match OS')).not.toHaveAttribute('lang')
    expect(
      within(screen.getByTestId('preferences-font-size')).getByText('Large'),
    ).not.toHaveAttribute('lang')
    expect(screen.getByRole('radio', { name: 'Light' })).toBeChecked()
  })

  it('chooses with the arrow keys and passes the chosen values', async () => {
    const user = userEvent.setup()
    const { props: all } = renderForm()
    await user.click(await screen.findByRole('radio', { name: 'ライト' }))
    await user.keyboard('{ArrowRight}')
    expect(all.setTheme).toHaveBeenLastCalledWith('dark')
    await user.click(screen.getByRole('radio', { name: '大' }))
    expect(all.setFontSize).toHaveBeenLastCalledWith('lg')
    await user.click(screen.getByRole('radio', { name: 'English' }))
    expect(all.setLanguage).toHaveBeenLastCalledWith('en')
    await user.type(screen.getByTestId('preferences-display-name-input'), 'X')
    expect(all.setDisplayName).toHaveBeenLastCalledWith('検査 太郎X')
  })

  it('links the name error to the field and puts choice errors in the legend', async () => {
    renderForm({
      fieldErrors: {
        displayName: 'preferences.displayName.tooLong',
        language: 'preferences.choice.invalid',
      },
    })
    const input = await screen.findByTestId('preferences-display-name-input')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(input).toHaveAccessibleDescription('氏名は 254 文字以内で入力してください')
    expect(
      screen.getByRole('group', { name: /言語.*保存すると切り替わります.*選択を確かめてください/ }),
    ).toBeInTheDocument()
  })

  it('enables Revert only when changed and not saving', async () => {
    const user = userEvent.setup()
    const { unmount } = renderForm()
    expect(await screen.findByRole('button', { name: '元に戻す' })).toBeDisabled()
    unmount()
    const { props: all } = renderForm({ dirty: true })
    const revert = await screen.findByRole('button', { name: '元に戻す' })
    expect(revert).toBeEnabled()
    await user.click(revert)
    expect(all.reset).toHaveBeenCalledTimes(1)
  })

  it('shows the saving state without disabling the save button or the radios', async () => {
    const user = userEvent.setup()
    const { props: all } = renderForm({ dirty: true, saving: true })
    const save = await screen.findByTestId('preferences-save-button')
    expect(save).toHaveTextContent('保存しています')
    expect(save).toHaveAttribute('aria-disabled', 'true')
    expect(save).toHaveAttribute('aria-busy', 'true')
    expect(save).not.toBeDisabled()
    expect(screen.getByTestId('preferences-display-name-input')).toHaveAttribute('readonly')
    expect(screen.getByRole('button', { name: '元に戻す' })).toBeDisabled()
    for (const radio of screen.getAllByRole('radio')) {
      expect(radio).not.toBeDisabled()
    }
    await user.click(save)
    expect(all.save).not.toHaveBeenCalled()
  })

  it('submits with the save button and with Enter in the name field', async () => {
    const user = userEvent.setup()
    const { props: all } = renderForm()
    await user.click(await screen.findByRole('button', { name: '保存する' }))
    await user.type(screen.getByTestId('preferences-display-name-input'), '{Enter}')
    expect(all.save).toHaveBeenCalledTimes(2)
  })

  it('shows one alert with a localized close button', async () => {
    const user = userEvent.setup()
    const { props: all } = renderForm({ alert: 'preferences.save.failed' }, ['en-US'])
    const alert = within(await screen.findByTestId('preferences-alert')).getByRole('alert')
    expect(alert).toHaveTextContent('The preferences could not be saved. Please try again later.')
    await user.click(screen.getByRole('button', { name: 'Close' }))
    expect(all.dismissAlert).toHaveBeenCalledTimes(1)
  })

  it('moves the focus to the chosen radio of a group when asked', async () => {
    const form: Preferences = { ...NAMED_PREFERENCES, fontSize: 'lg' }
    renderForm({ form, focusRequest: { target: 'fontSize', seq: 1 } })
    expect(await screen.findByRole('radio', { name: '大' })).toHaveFocus()
  })

  it('has no accessibility violations with errors and an alert', async () => {
    const { container } = renderForm({
      dirty: true,
      alert: 'preferences.form.invalid',
      fieldErrors: {
        displayName: 'preferences.displayName.required',
        theme: 'preferences.choice.invalid',
      },
    })
    await screen.findByTestId('preferences-form')
    expect(await axe(container)).toHaveNoViolations()
  })
})
