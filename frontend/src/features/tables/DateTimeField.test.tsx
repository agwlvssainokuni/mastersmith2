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
// 日時の欄のテスト（Intent 261102-timezone-ui-polish の U4、計画 Step 12、D13〜D15・D18・D21・D39・D40、security-design.md 2.6）。
// タイムゾーンと今の時刻は引数で明示に渡す（機械の既定のタイムゾーンに預けない）。
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { renderWithProviders, resetDisplayTestState } from '../../app/testing/renderWithProviders'
import type { OverlapChoice } from '../../shared/format/timeZone'
import {
  DateTimeField,
  dateTimeHint,
  isDateTimeUsable,
  type DateTimeFieldProps,
} from './DateTimeField'
import { registration } from './registration'
import type { FieldTimeZone } from './useTablesTimeZone'

afterEach(() => resetDisplayTestState())

const TOKYO: FieldTimeZone = { status: 'ready', timeZone: 'Asia/Tokyo' }
const NEW_YORK: FieldTimeZone = { status: 'ready', timeZone: 'America/New_York' }
/** 夏時間の間の今の時刻（New York は -04:00） */
const SUMMER_NOW = '2026-07-01T00:00:00Z'
/** 標準時の間の今の時刻（New York は -05:00） */
const WINTER_NOW = '2026-12-01T00:00:00Z'
const OVERLAP = { earlier: '-04:00', later: '-05:00' }
const DANGEROUS = '<img src=x onerror="alert(1)">"\'javascript:'

type HarnessProps = Partial<DateTimeFieldProps> & { initialChoice?: OverlapChoice | null }

/** 値と2択の選択を状態に持つ入れ物 */
function Harness({ initialChoice = null, ...props }: HarnessProps) {
  const [value, setValue] = useState(props.value ?? '')
  const [choice, setChoice] = useState<OverlapChoice | null>(initialChoice)
  return (
    <DateTimeField
      kind="instant"
      purpose="form"
      label="受注の時刻"
      timeZone={TOKYO}
      now={SUMMER_NOW}
      overlap={null}
      name="f0"
      testId="field"
      {...props}
      value={value}
      onChange={(next) => {
        setValue(next)
        props.onChange?.(next)
      }}
      overlapChoice={choice}
      onOverlapChoice={(next) => {
        setChoice(next)
        props.onOverlapChoice?.(next)
      }}
    />
  )
}

function renderField(props: HarnessProps = {}, languages: string[] = ['ja-JP']) {
  return renderWithProviders(<Harness {...props} />, {
    registrations: [registration],
    languages,
  })
}

describe('DateTimeField', () => {
  it('has no axe violations with the overlap choice in a separate field and its error shown', async () => {
    const { container } = renderField({
      timeZone: NEW_YORK,
      value: '2026-11-01 01:30:00',
      overlap: OVERLAP,
      overlapError: 'どちらの時刻かを選んでください',
    })
    expect(
      await screen.findByRole('group', {
        name: 'America/New_York では、この時刻は2回あります。どちらですか',
      }),
    ).toBeInTheDocument()
    expect(await axe(container)).toHaveNoViolations()
  })

  it('has no axe violations with a field error and with a chosen overlap option', async () => {
    const { container } = renderField({
      timeZone: NEW_YORK,
      value: '2026-11-01 01:30:00',
      overlap: OVERLAP,
      initialChoice: 'later',
      error: '日時を YYYY-MM-DD HH:mm:ss の形で入れてください',
    })
    expect(await screen.findByRole('radio', { name: '後（-05:00）' })).toBeChecked()
    expect(await axe(container)).toHaveNoViolations()
  })

  it('shows the instant hint with the zone and the current offset, linked to the input', async () => {
    renderField({ timeZone: NEW_YORK, now: WINTER_NOW })
    const input = await screen.findByRole('textbox', { name: '受注の時刻' })
    const hint =
      'America/New_York (-05:00) で入力。例: 2026-11-02 09:30:15（秒の端数は .123 のように付けられます）'
    expect(screen.getByText(hint)).toBeInTheDocument()
    expect(input).toHaveAccessibleDescription(hint)
    expect(input).toHaveAttribute('type', 'text')
    expect(input).toHaveAttribute('autocomplete', 'off')
    expect(input).not.toHaveAttribute('aria-invalid')
  })

  it('shows the local hint without a time zone and stays usable while the zone is unavailable', async () => {
    renderField({ kind: 'local', timeZone: { status: 'error' } })
    const input = await screen.findByRole('textbox', { name: '受注の時刻' })
    expect(input).toBeEnabled()
    expect(input).toHaveAccessibleDescription(
      '例: 2026-11-02 09:30:15（秒の端数は .123 のように付けられます）',
    )
  })

  it('disables an instant field with a short text while the zone is loading or failed', async () => {
    const first = renderField({ timeZone: { status: 'error' } })
    const failed = await screen.findByRole('textbox', { name: '受注の時刻' })
    expect(failed).toBeDisabled()
    expect(failed).toHaveAccessibleDescription('時刻を表示できません')
    first.unmount()
    renderField({ timeZone: { status: 'loading' }, overlap: OVERLAP })
    const loading = await screen.findByRole('textbox', { name: '受注の時刻' })
    expect(loading).toBeDisabled()
    expect(loading).toHaveAccessibleDescription('読み込み中')
    // 使えない欄には2択も出さない
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
  })

  it('marks the input invalid and shows the error instead of the hint', async () => {
    renderField({ error: '日時を YYYY-MM-DD HH:mm:ss の形で入れてください' })
    const input = await screen.findByRole('textbox', { name: '受注の時刻' })
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('alert')).toHaveTextContent(
      '日時を YYYY-MM-DD HH:mm:ss の形で入れてください',
    )
    expect(screen.queryByText(/で入力。例/)).not.toBeInTheDocument()
  })

  it('shows the overlap choice only when offsets are given', async () => {
    const first = renderField({ timeZone: NEW_YORK })
    await screen.findByRole('textbox', { name: '受注の時刻' })
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
    first.unmount()
    renderField({ timeZone: NEW_YORK, overlap: OVERLAP })
    expect(await screen.findByRole('radio', { name: '先（-04:00）' })).not.toBeChecked()
    expect(screen.getByRole('radio', { name: '後（-05:00）' })).not.toBeChecked()
  })

  it('chooses an overlap option with the keyboard only', async () => {
    const onOverlapChoice = vi.fn()
    const user = userEvent.setup()
    renderField({ timeZone: NEW_YORK, overlap: OVERLAP, onOverlapChoice })
    const input = await screen.findByRole('textbox', { name: '受注の時刻' })
    input.focus()
    await user.tab()
    expect(screen.getByRole('radio', { name: '先（-04:00）' })).toHaveFocus()
    await user.keyboard(' ')
    await waitFor(() => expect(onOverlapChoice).toHaveBeenLastCalledWith('earlier'))
    await user.keyboard('{ArrowDown}')
    await waitFor(() => expect(onOverlapChoice).toHaveBeenLastCalledWith('later'))
    expect(screen.getByRole('radio', { name: '後（-05:00）' })).toBeChecked()
  })

  it('links the overlap group to its error when nothing is chosen', async () => {
    renderField({
      timeZone: NEW_YORK,
      overlap: OVERLAP,
      overlapError: 'どちらの時刻かを選んでください',
    })
    const group = await screen.findByRole('group', {
      name: 'America/New_York では、この時刻は2回あります。どちらですか',
    })
    expect(group).toHaveAttribute('aria-invalid', 'true')
    expect(group).toHaveAccessibleDescription('どちらの時刻かを選んでください')
  })

  it('renders dangerous texts in the label, value and zone name as plain text', async () => {
    const { container } = renderField({
      label: DANGEROUS,
      value: DANGEROUS,
      timeZone: { status: 'ready', timeZone: DANGEROUS },
      overlap: OVERLAP,
    })
    const input = await screen.findByRole('textbox', { name: DANGEROUS })
    expect(input).toHaveValue(DANGEROUS)
    expect(container.querySelector('img')).toBeNull()
    expect(container.querySelector('script')).toBeNull()
    expect(
      screen.getAllByText(`${DANGEROUS} では、この時刻は2回あります。どちらですか`).length,
    ).toBeGreaterThan(0)
  })

  it('uses an outside description instead of the hint when describedBy is given', async () => {
    renderWithProviders(
      <>
        <p id="outside">外の案内</p>
        <Harness showHint={false} describedBy="outside" invalid />
      </>,
      { registrations: [registration] },
    )
    const input = await screen.findByRole('textbox', { name: '受注の時刻' })
    expect(input).toHaveAccessibleDescription('外の案内')
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(screen.queryByText(/で入力。例/)).not.toBeInTheDocument()
  })

  it('shows the texts in English', async () => {
    renderField({ timeZone: NEW_YORK, now: SUMMER_NOW, overlap: OVERLAP }, ['en-US'])
    expect(
      await screen.findByText(
        'Enter in America/New_York (-04:00). Example: 2026-11-02 09:30:15 (you can add fractional seconds such as .123)',
      ),
    ).toBeInTheDocument()
    expect(screen.getByRole('radio', { name: 'Earlier (-04:00)' })).toBeInTheDocument()
    expect(
      screen.getByRole('group', { name: 'This time occurs twice in America/New_York. Which one?' }),
    ).toBeInTheDocument()
  })
})

describe('dateTimeHint', () => {
  const t = (key: string, options?: Record<string, string>) =>
    `${key}${options === undefined ? '' : JSON.stringify(options)}`

  it('chooses the hint by purpose and kind and uses the offset of the given now', () => {
    expect(dateTimeHint(t, 'instant', 'range-from', NEW_YORK, SUMMER_NOW)).toBe(
      'tables.dateTime.hint.instantRange{"zone":"America/New_York","offset":"-04:00"}',
    )
    expect(
      dateTimeHint(
        t,
        'instant',
        'equals',
        { status: 'ready', timeZone: 'Asia/Kathmandu' },
        SUMMER_NOW,
      ),
    ).toBe('tables.dateTime.hint.instantEquals{"zone":"Asia/Kathmandu","offset":"+05:45"}')
    expect(dateTimeHint(t, 'local', 'range-to', { status: 'error' }, SUMMER_NOW)).toBe(
      'tables.dateTime.hint.localRange',
    )
    expect(dateTimeHint(t, 'instant', 'form', { status: 'error' }, SUMMER_NOW)).toBe(
      'tables.error.TIME_ZONE_UNAVAILABLE',
    )
  })

  it('tells whether a field is usable', () => {
    expect(isDateTimeUsable('local', { status: 'loading' })).toBe(true)
    expect(isDateTimeUsable('instant', { status: 'loading' })).toBe(false)
    expect(isDateTimeUsable('instant', TOKYO)).toBe(true)
  })
})
