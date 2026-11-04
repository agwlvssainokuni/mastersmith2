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
// 参照ピッカーのダイアログのテスト（Intent 261029-reference-lookup-cleanup の U3、D21〜D30、frontend-components.md 7節、
// AC2.2.1〜AC2.2.11・AC2.4.1〜AC2.4.3）。検索は手で解く Promise で返し、時計・sleep に頼らない。
import { act, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useRef, useState } from 'react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { deferred, type Deferred } from '../../../app/testing/frameTestSupport'
import { resetDisplayTestState } from '../../../app/testing/renderWithProviders'
import type {
  PickerInfo,
  ReferenceCondition,
  ReferenceRow,
  ReferenceRowPage,
} from '../businessDataTypes'
import { renderReference } from '../testing/renderReference'
import {
  LONG_LABEL,
  SAMPLE_PICKER,
  SAMPLE_SOURCE,
  XSS_SAMPLES,
  pickerRow,
} from '../testing/referenceSamples'
import { LookupPickerDialog, type LookupPickerDialogProps } from './LookupPickerDialog'

afterEach(() => resetDisplayTestState())

interface SearchCall {
  conditions: readonly ReferenceCondition[]
  page: number
  answer: Deferred<ReferenceRowPage>
}

function fakeSearch() {
  const calls: SearchCall[] = []
  const search = vi.fn(
    (_source: unknown, conditions: readonly ReferenceCondition[], page: number) => {
      const answer = deferred<ReferenceRowPage>()
      calls.push({ conditions, page, answer })
      return answer.promise
    },
  )
  return { search, calls }
}

function rows(count: number, start = 0): ReferenceRow[] {
  return Array.from({ length: count }, (_, index) => pickerRow(start + index))
}

function problem(status: number, code: string, fieldErrors: unknown[] = []) {
  return { kind: 'response', status, code, problem: { status, code, fieldErrors } }
}

/** 表示のタイムゾーン（固定の Asia/Tokyo）と今の時刻（Intent 261102-timezone-ui-polish の U4） */
const TOKYO_CELL = { status: 'ready', timeZone: 'Asia/Tokyo' } as const
const TEST_NOW = '2026-07-01T00:00:00Z'

/** 「選ぶ…」のボタンとダイアログを描く（閉じたらダイアログを外す） */
function Harness({
  picker,
  search,
  onSelect,
  extra = {},
}: {
  picker: PickerInfo
  search: ReturnType<typeof fakeSearch>['search']
  onSelect: (row: ReferenceRow) => void
  /** 日時の書式の受け渡し（Intent 261102-timezone-ui-polish の U4） */
  extra?: Partial<
    Pick<LookupPickerDialogProps, 'labelValueType' | 'valueType' | 'timeZone' | 'now'>
  >
}) {
  const [open, setOpen] = useState(true)
  const opener = useRef<HTMLButtonElement | null>(null)
  return (
    <>
      <button type="button" ref={opener} onClick={() => setOpen(true)}>
        開く
      </button>
      {open && (
        <LookupPickerDialog
          name="商品"
          picker={picker}
          source={SAMPLE_SOURCE}
          finalFocusRef={opener}
          onSelect={(row) => {
            setOpen(false)
            onSelect(row)
          }}
          onClose={() => setOpen(false)}
          search={search}
          timeZone={TOKYO_CELL}
          now={TEST_NOW}
          {...extra}
        />
      )}
    </>
  )
}

function renderDialog(
  picker: PickerInfo = SAMPLE_PICKER,
  languages?: string[],
  extra: Partial<
    Pick<LookupPickerDialogProps, 'labelValueType' | 'valueType' | 'timeZone' | 'now'>
  > = {},
) {
  const fake = fakeSearch()
  const onSelect = vi.fn()
  const view = renderReference(
    <Harness picker={picker} search={fake.search} onSelect={onSelect} extra={extra} />,
    languages === undefined ? {} : { languages },
  )
  return { ...fake, onSelect, view, user: userEvent.setup() }
}

async function searchFor(user: ReturnType<typeof userEvent.setup>, code: string) {
  await user.type(await screen.findByRole('textbox', { name: '商品コード（一致）' }), code)
  await user.click(screen.getByTestId('tables-picker-search'))
}

describe('LookupPickerDialog', () => {
  it('focuses the first condition, keeps open on backdrop click and returns the focus on Escape', async () => {
    const { user, search } = renderDialog()
    const dialog = await screen.findByRole('dialog', { name: '商品を選ぶ' })
    await waitFor(() =>
      expect(screen.getByRole('textbox', { name: '商品コード（一致）' })).toHaveFocus(),
    )
    expect(within(dialog).getByRole('status')).toHaveTextContent(
      '条件を入れて「検索」を押してください',
    )
    const overlay = dialog.parentElement
    if (overlay !== null) {
      await user.click(overlay)
    }
    expect(screen.getByRole('dialog')).toBeInTheDocument()
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    await waitFor(() => expect(screen.getByRole('button', { name: '開く' })).toHaveFocus())
    expect(search).not.toHaveBeenCalled()
  })

  it('does not send for empty conditions, 1,001 characters or an unreadable EQUALS value', async () => {
    const picker: PickerInfo = {
      ...SAMPLE_PICKER,
      searchFields: [
        { ...SAMPLE_PICKER.searchFields[0], valueType: 'INTEGER' },
        SAMPLE_PICKER.searchFields[1],
      ],
    }
    const { user, search } = renderDialog(picker)
    await user.click(await screen.findByTestId('tables-picker-search'))
    expect(screen.getByRole('status')).toHaveTextContent('条件を入れてください')
    const name = screen.getByRole('textbox', { name: '品名（部分一致）' })
    await user.click(name)
    await user.paste('あ'.repeat(1001))
    await user.click(screen.getByTestId('tables-picker-search'))
    expect(name).toHaveAccessibleDescription('1,000 文字以内で入れてください')
    await user.clear(name)
    const code = screen.getByRole('spinbutton', { name: '商品コード（一致）' })
    await user.type(code, '1e')
    await user.click(screen.getByTestId('tables-picker-search'))
    expect(search).not.toHaveBeenCalled()
  })

  it.each([
    ['INTEGER', 'EQUALS', 'spinbutton', 'number'],
    ['DATE', 'EQUALS', null, 'date'],
    ['DECIMAL', 'EQUALS', 'textbox', 'text'],
    ['INTEGER', 'CONTAINS', 'textbox', 'text'],
  ] as const)('uses an input that suits %s %s', async (valueType, operator, role, type) => {
    renderDialog({
      ...SAMPLE_PICKER,
      searchFields: [{ column: 'v', displayName: { ja: '値', en: 'Value' }, operator, valueType }],
    })
    const dialog = await screen.findByRole('dialog')
    const input = dialog.querySelector<HTMLInputElement>(
      '[data-testid="tables-picker-field-c0"] input',
    )
    expect(input?.type).toBe(type)
    if (role !== null) {
      expect(within(dialog).getByRole(role)).toBe(input)
    }
  })

  it('uses a select of unspecified, yes and no for BOOLEAN', async () => {
    renderDialog({
      ...SAMPLE_PICKER,
      searchFields: [
        {
          column: 'b',
          displayName: { ja: '有効', en: 'Active' },
          operator: 'EQUALS',
          valueType: 'BOOLEAN',
        },
      ],
    })
    const select = await screen.findByRole('combobox', { name: '有効（一致）' })
    expect(
      within(select)
        .getAllByRole('option')
        .map((o) => o.getAttribute('value')),
    ).toEqual(['', 'true', 'false'])
  })

  it('searches with Enter, ignores a second press while searching and shows results', async () => {
    const { user, calls } = renderDialog()
    await user.type(await screen.findByRole('textbox', { name: '商品コード（一致）' }), 'P{Enter}')
    expect(calls).toHaveLength(1)
    expect(calls[0].conditions).toEqual([{ column: 'code', value: 'P' }])
    expect(screen.getByRole('status')).toHaveTextContent('検索中')
    await user.keyboard('{Enter}')
    await user.click(screen.getByTestId('tables-picker-search'))
    expect(calls).toHaveLength(1)
    await act(async () => calls[0].answer.resolve({ rows: rows(20), page: 1, hasNext: true }))
    const table = await screen.findByRole('table', { name: '商品の検索結果' })
    expect(
      within(table)
        .getAllByRole('columnheader')
        .map((th) => th.textContent),
    ).toEqual(['選ぶ', '商品コード', '品名', '価格'])
    expect(screen.getByRole('region', { name: '商品の検索結果' })).toBeInTheDocument()
    expect(screen.getByTestId('tables-picker-live')).toHaveTextContent(
      '1〜20 件目を表示しています。続きがあります',
    )
    expect(screen.getByText('1 ページ目（続きがあります）')).toBeInTheDocument()
  })

  it('moves to the next page with the same conditions and drops page presses while searching', async () => {
    const { user, calls } = renderDialog()
    await searchFor(user, 'P')
    await act(async () => calls[0].answer.resolve({ rows: rows(20), page: 1, hasNext: true }))
    await user.click(await screen.findByRole('button', { name: '次へ' }))
    expect(calls).toHaveLength(2)
    expect(calls[1]).toMatchObject({ conditions: [{ column: 'code', value: 'P' }], page: 2 })
    await act(async () => calls[1].answer.resolve({ rows: rows(3, 20), page: 2, hasNext: false }))
    expect(await screen.findByText('2 ページ目（ここまで）')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: '次へ' })).toBeDisabled()
    expect(screen.getByTestId('tables-picker-live')).toHaveTextContent(
      '21〜23 件目を表示しています。これで最後です',
    )
  })

  it('shows no results on page 1 and only "go to the previous page" on an empty later page', async () => {
    const { user, calls } = renderDialog()
    await searchFor(user, 'Z')
    await act(async () => calls[0].answer.resolve({ rows: [], page: 1, hasNext: false }))
    expect(await screen.findByRole('status')).toHaveTextContent('該当する行がありません')
    expect(screen.queryByRole('table')).toBeNull()

    await user.click(screen.getByTestId('tables-picker-search'))
    await act(async () => calls[1].answer.resolve({ rows: rows(20), page: 1, hasNext: true }))
    await user.click(await screen.findByRole('button', { name: '次へ' }))
    await act(async () => calls[2].answer.resolve({ rows: [], page: 2, hasNext: false }))
    expect(await screen.findByRole('status')).toHaveTextContent('このページには行がありません')
    expect(screen.queryByRole('table')).toBeNull()
    await user.click(screen.getByRole('button', { name: '前のページへ' }))
    expect(calls[3]).toMatchObject({ page: 1, conditions: [{ column: 'code', value: 'Z' }] })
  })

  it.each([
    [
      '503',
      problem(503, 'BUSINESS_TARGET_DB_TIMEOUT'),
      '検索できませんでした。もう一度検索してください',
      true,
    ],
    ['network', { kind: 'network' }, '検索できませんでした。もう一度検索してください', true],
    [
      '400 without field errors',
      problem(400, 'VALIDATION_FAILED'),
      '検索できませんでした。もう一度検索してください',
      true,
    ],
    [
      '403',
      problem(403, 'ACCESS_DENIED'),
      'この項目の候補を検索できません。画面を開き直してください',
      false,
    ],
  ])(
    'shows the failure of %s as an alert and keeps the conditions',
    async (_name, error, text, retry) => {
      const { user, calls } = renderDialog()
      await searchFor(user, 'P')
      await act(async () => calls[0].answer.reject(error))
      expect(await screen.findByRole('alert')).toHaveTextContent(text)
      expect(screen.getByRole('textbox', { name: '商品コード（一致）' })).toHaveValue('P')
      expect(screen.queryByRole('button', { name: '再度検索' }) !== null).toBe(retry)
      if (retry) {
        await user.click(screen.getByRole('button', { name: '再度検索' }))
        expect(calls).toHaveLength(2)
      }
    },
  )

  it('shows the mismatch text for 409 and a condition error for 400 INVALID_VALUE', async () => {
    const { user, calls } = renderDialog()
    await searchFor(user, 'P')
    await act(async () => calls[0].answer.reject(problem(409, 'BUSINESS_TARGET_DB_MISMATCH')))
    expect(await screen.findByRole('alert')).not.toHaveTextContent('検索できませんでした')
    await user.click(screen.getByRole('button', { name: '再度検索' }))
    await act(async () =>
      calls[1].answer.reject(
        problem(400, 'VALIDATION_FAILED', [{ field: 'code', reason: 'INVALID_VALUE' }]),
      ),
    )
    await waitFor(() =>
      expect(
        screen.getByRole('textbox', { name: '商品コード（一致）' }),
      ).toHaveAccessibleDescription('この条件には、この形の値を入れられません'),
    )
  })

  it('drops an old answer that arrives after the dialog is closed', async () => {
    const { user, calls, onSelect } = renderDialog()
    await searchFor(user, 'P')
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    await act(async () => calls[0].answer.resolve({ rows: rows(1), page: 1, hasNext: false }))
    expect(screen.queryByRole('table')).toBeNull()
    expect(onSelect).not.toHaveBeenCalled()
  })

  it('names the select button with the label or the value and keeps row values out of ids and test ids', async () => {
    const { user, calls, onSelect, view } = renderDialog()
    await searchFor(user, 'P')
    await act(async () =>
      calls[0].answer.resolve({
        rows: [
          pickerRow(0),
          pickerRow(1, { label: null }),
          ...XSS_SAMPLES.map((label, i) =>
            pickerRow(10 + i, { label, cells: [label, label, null] }),
          ),
        ],
        page: 1,
        hasNext: false,
      }),
    )
    const table = await screen.findByRole('table')
    expect(within(table).getByRole('button', { name: 'P002を選ぶ' })).toHaveTextContent('選ぶ')
    expect(within(table).getAllByRole('cell', { name: /^値なし/ }).length).toBeGreaterThan(0)
    expect(view.container.ownerDocument.querySelector('script, img')).toBeNull()
    for (const element of view.container.ownerDocument.querySelectorAll('[data-testid], [id]')) {
      const marks = `${element.getAttribute('data-testid') ?? ''} ${element.id}`
      expect(marks).not.toMatch(/P0\d\d|商品 \d|script|onerror/)
    }
    expect(document.title).not.toMatch(/P0|商品 1/)
    await user.click(within(table).getByRole('button', { name: '商品 1を選ぶ' }))
    expect(onSelect).toHaveBeenCalledWith(pickerRow(0))
  })

  // Intent 261102-timezone-ui-polish の U4、計画 Step 11、D32〜D34
  it('shows date time cells and labels in the display time zone and selects the original row', async () => {
    const picker: PickerInfo = {
      searchFields: [
        {
          column: 'at',
          displayName: { ja: '日時', en: 'Time' },
          operator: 'EQUALS',
          valueType: 'DATETIME_TZ',
        },
      ],
      listColumns: [
        { column: 'at', displayName: { ja: '日時', en: 'Time' }, valueType: 'DATETIME_TZ' },
        { column: 'name', displayName: { ja: '品名', en: 'Name' }, valueType: 'STRING' },
      ],
    }
    const { user, calls, onSelect } = renderDialog(picker, undefined, {
      labelValueType: 'DATETIME_TZ',
      valueType: 'STRING',
      timeZone: { status: 'ready', timeZone: 'Asia/Tokyo' },
    })
    await user.type(
      await screen.findByRole('textbox', { name: '日時（一致）' }),
      '2026-11-02 00:00:00',
    )
    await user.click(screen.getByTestId('tables-picker-search'))
    // 送る条件は、表示のタイムゾーンの壁時計を直した時点（UTC の時差つき）
    expect(calls[0].conditions).toEqual([{ column: 'at', value: '2026-11-01T15:00:00+00:00' }])
    const row: ReferenceRow = {
      value: 'E1',
      label: '2026-11-01T15:00:00.123+00:00',
      cells: ['2026-11-01T15:00:00.123+00:00', '<script>x</script>'],
    }
    await act(async () => calls[0].answer.resolve({ rows: [row], page: 1, hasNext: false }))
    const table = await screen.findByRole('table')
    expect(within(table).getByText('2026-11-02 00:00:00.123 +09:00')).toBeInTheDocument()
    expect(within(table).getByText('<script>x</script>')).toBeInTheDocument()
    await user.click(
      within(table).getByRole('button', { name: '2026-11-02 00:00:00.123 +09:00を選ぶ' }),
    )
    expect(onSelect).toHaveBeenCalledWith(row)
  })

  // Intent 261102-timezone-ui-polish の U4、計画 Step 13、D26・D27・D34
  it('uses a date time field and asks which time an overlapping exact match means', async () => {
    const picker: PickerInfo = {
      searchFields: [
        {
          column: 'at',
          displayName: { ja: '日時', en: 'Time' },
          operator: 'EQUALS',
          valueType: 'DATETIME_TZ',
        },
      ],
      listColumns: [
        { column: 'at', displayName: { ja: '日時', en: 'Time' }, valueType: 'DATETIME_TZ' },
      ],
    }
    const { user, calls, view } = renderDialog(picker, undefined, {
      timeZone: { status: 'ready', timeZone: 'America/New_York' },
      now: '2026-12-01T00:00:00Z',
    })
    const input = await screen.findByRole('textbox', { name: '日時（一致）' })
    expect(input).toHaveAttribute('type', 'text')
    expect(input).toHaveAccessibleDescription(
      'America/New_York (-05:00) で入力。例: 2026-11-02 09:30:15。秒の端数を持つ値は、端数まで入れてください（例: .123）',
    )
    await user.type(input, '2026-11-01 01:30:00{Enter}')
    const earlier = await screen.findByRole('radio', { name: '先（-04:00）' })
    await waitFor(() => expect(earlier).toHaveFocus())
    expect(screen.getByText('どちらの時刻かを選んでください')).toBeInTheDocument()
    expect(calls).toHaveLength(0)
    expect(await axe(view.container)).toHaveNoViolations()
    await user.click(earlier)
    await user.click(screen.getByTestId('tables-picker-search'))
    expect(calls[0].conditions).toEqual([{ column: 'at', value: '2026-11-01T05:30:00+00:00' }])
  })

  it('shows a skipped time error with the zone name in the picker', async () => {
    const picker: PickerInfo = {
      searchFields: [
        {
          column: 'at',
          displayName: { ja: '日時', en: 'Time' },
          operator: 'EQUALS',
          valueType: 'DATETIME_TZ',
        },
      ],
      listColumns: [
        { column: 'at', displayName: { ja: '日時', en: 'Time' }, valueType: 'DATETIME_TZ' },
      ],
    }
    const { user, calls } = renderDialog(picker, undefined, {
      timeZone: { status: 'ready', timeZone: 'America/New_York' },
    })
    const input = await screen.findByRole('textbox', { name: '日時（一致）' })
    await user.type(input, '2026-03-08 02:30:00')
    await user.click(screen.getByTestId('tables-picker-search'))
    expect(
      await screen.findByText(
        'America/New_York では、この時刻はありません（夏時間の切り替えで飛ばされる時刻です）',
      ),
    ).toBeInTheDocument()
    expect(input).toHaveAttribute('aria-invalid', 'true')
    expect(calls).toHaveLength(0)
  })

  it('starts empty again when reopened', async () => {
    const { user, calls } = renderDialog()
    await searchFor(user, 'P')
    await act(async () => calls[0].answer.resolve({ rows: rows(1), page: 1, hasNext: false }))
    await screen.findByRole('table')
    await user.keyboard('{Escape}')
    await waitFor(() => expect(screen.queryByRole('dialog')).toBeNull())
    await user.click(screen.getByRole('button', { name: '開く' }))
    await screen.findByRole('dialog')
    expect(screen.queryByRole('table')).toBeNull()
    expect(screen.getByRole('textbox', { name: '商品コード（一致）' })).toHaveValue('')
  })

  it('shows English texts', async () => {
    const { user, calls } = renderDialog(SAMPLE_PICKER, ['en-US'])
    expect(await screen.findByRole('dialog', { name: 'Select 商品' })).toBeInTheDocument()
    await user.type(screen.getByRole('textbox', { name: 'Code (equals)' }), 'P{Enter}')
    await act(async () => calls[0].answer.resolve({ rows: rows(1), page: 1, hasNext: false }))
    expect(await screen.findByRole('button', { name: 'Select 商品 1' })).toHaveTextContent('Select')
    expect(screen.getByText('Page 1 (last page)')).toBeInTheDocument()
  })

  it('keeps a long name as full text', async () => {
    const { user, calls } = renderDialog()
    await searchFor(user, 'P')
    await act(async () =>
      calls[0].answer.resolve({
        rows: [pickerRow(0, { label: LONG_LABEL, cells: ['P001', LONG_LABEL, null] })],
        page: 1,
        hasNext: false,
      }),
    )
    expect(await screen.findByText(LONG_LABEL)).toBeInTheDocument()
  })

  it('has no accessibility violations in the initial, results, no results and failed states', async () => {
    const { user, calls } = renderDialog()
    await screen.findByRole('dialog')
    expect(await axe(document.body)).toHaveNoViolations()
    await searchFor(user, 'P')
    await act(async () => calls[0].answer.resolve({ rows: rows(20), page: 1, hasNext: true }))
    await screen.findByRole('table')
    expect(await axe(document.body)).toHaveNoViolations()
    await user.click(screen.getByTestId('tables-picker-search'))
    await act(async () => calls[1].answer.resolve({ rows: [], page: 1, hasNext: false }))
    await screen.findByText('該当する行がありません')
    expect(await axe(document.body)).toHaveNoViolations()
    await user.click(screen.getByTestId('tables-picker-search'))
    await act(async () => calls[2].answer.reject({ kind: 'network' }))
    await screen.findByRole('alert')
    expect(await axe(document.body)).toHaveNoViolations()
  })
})
