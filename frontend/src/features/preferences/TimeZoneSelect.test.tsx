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
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { TimeZoneSelect, type TimeZoneSelectProps } from './TimeZoneSelect'
import { renderPreferencesPart } from './testing/renderPreferences'

const NAMES = ['Asia/Tokyo', 'America/New_York', 'Asia/Kolkata', 'UTC']
const NOW = () => new Date('2026-01-15T00:00:00Z')

beforeEach(() => {
  resetDisplayTestState()
})

function renderSelect(overrides: Partial<TimeZoneSelectProps> = {}, languages = ['ja-JP']) {
  const props: TimeZoneSelectProps = {
    value: null,
    onChange: vi.fn(),
    disabled: false,
    error: undefined,
    names: NAMES,
    now: NOW,
    ...overrides,
  }
  return { props, ...renderPreferencesPart(<TimeZoneSelect {...props} />, languages) }
}

const select = () => screen.getByTestId('preferences-time-zone-select')

describe('TimeZoneSelect', () => {
  it('shows the browser choice first and labels every name with its offset', async () => {
    renderSelect()

    const options = within(await screen.findByTestId('preferences-time-zone-select'))
      .getAllByRole('option')
      .map((option) => option.textContent)
    expect(options).toEqual([
      'ブラウザに合わせる',
      'America/New_York (-05:00)',
      'Asia/Kolkata (+05:30)',
      'Asia/Tokyo (+09:00)',
      'UTC (+00:00)',
    ])
    expect(screen.getByLabelText('表示のタイムゾーン')).toBe(select())
  })

  it('shows English texts for an English browser', async () => {
    renderSelect({}, ['en-US'])

    expect(await screen.findByLabelText('Display time zone')).toBeInTheDocument()
    expect(screen.getByRole('option', { name: 'Match the browser' })).toBeInTheDocument()
  })

  it('selects with the keyboard and passes the name, or null for the browser choice', async () => {
    const user = userEvent.setup()
    const { props } = renderSelect({ value: 'Asia/Tokyo' })
    await user.selectOptions(
      await screen.findByTestId('preferences-time-zone-select'),
      'Asia/Kolkata',
    )
    expect(props.onChange).toHaveBeenLastCalledWith('Asia/Kolkata')

    await user.selectOptions(select(), '')
    expect(props.onChange).toHaveBeenLastCalledWith(null)
    expect(select()).toHaveValue('Asia/Tokyo')
  })

  it('shows a saved name that is not in the browser list, right after the browser choice', async () => {
    renderSelect({ value: 'Europe/Paris' })

    const options = within(await screen.findByTestId('preferences-time-zone-select')).getAllByRole(
      'option',
    )
    expect(options[1]).toHaveTextContent('Europe/Paris (+01:00)')
    expect(select()).toHaveValue('Europe/Paris')
  })

  it('shows a warning and the browser choice when the saved name cannot be interpreted', async () => {
    renderSelect({ value: 'Mars/Olympus' })

    const warning = await screen.findByTestId('preferences-time-zone-unresolvable')
    expect(within(warning).getByRole('alert')).toHaveTextContent('保存されているタイムゾーン')
    expect(select()).toHaveValue('')
    expect(screen.queryByRole('option', { name: /Mars/ })).not.toBeInTheDocument()
  })

  it('shows no warning for a saved name or for unset', async () => {
    renderSelect({ value: 'Asia/Tokyo' })

    await screen.findByTestId('preferences-time-zone-select')
    expect(screen.queryByTestId('preferences-time-zone-unresolvable')).not.toBeInTheDocument()
  })

  it('shows a server rejection under the field and marks it invalid', async () => {
    renderSelect({ error: 'タイムゾーンを選び直してください' })

    expect(await screen.findByText('タイムゾーンを選び直してください')).toBeInTheDocument()
    expect(select()).toHaveAttribute('aria-invalid', 'true')
  })

  it('is disabled while saving', async () => {
    renderSelect({ disabled: true })

    expect(await screen.findByTestId('preferences-time-zone-select')).toBeDisabled()
  })

  it('draws a hostile name as text only: no element is added and no link is made', async () => {
    const scriptUrl = ['javascript', ':alert(1)'].join('')
    const hostile = ['<img src=x onerror=alert(1)>', '"quoted"', "it's", scriptUrl]
    // 一覧に無く解釈できない名前は並べない。名前の一覧そのものに敵意のある名前が入っても、文字としてだけ描く。
    renderSelect({ names: [...NAMES, ...hostile], value: scriptUrl })

    await screen.findByTestId('preferences-time-zone-select')
    expect(document.querySelectorAll('img')).toHaveLength(0)
    expect(document.querySelectorAll('a')).toHaveLength(0)
    expect(document.body.innerHTML).not.toContain('<img')
  })

  it('draws a very long saved name without breaking the field', async () => {
    renderSelect({ value: 'a'.repeat(64) })

    expect(await screen.findByTestId('preferences-time-zone-select')).toBeInTheDocument()
  })

  it('has no accessibility violations, including with the warning and an error', async () => {
    const { container } = renderSelect({
      value: 'Mars/Olympus',
      error: 'タイムゾーンを選び直してください',
    })
    await screen.findByTestId('preferences-time-zone-select')

    expect(await axe(container)).toHaveNoViolations()
  })
})
