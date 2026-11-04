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
import { screen, waitFor } from '@testing-library/react'
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
import type { FieldTimeZone } from './useTablesTimeZone'

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

/** 標準時の間の今の時刻（New York は -05:00） */
const WINTER_NOW = '2026-12-01T00:00:00Z'

function renderPanel(
  props: {
    values?: SearchInputs
    expanded?: boolean
    serverErrors?: BusinessFieldError[]
    fields?: SearchField[]
    timeZone?: FieldTimeZone
    now?: string
    languages?: string[]
  } = {},
) {
  const onSearch = vi.fn()
  const onClear = vi.fn()
  const onToggle = vi.fn()
  const view = renderWithProviders(
    <main>
      <SearchPanel
        fields={props.fields ?? FIELDS}
        columns={ACTIVE === undefined ? [] : [ACTIVE]}
        values={props.values ?? {}}
        serverErrors={props.serverErrors ?? []}
        expanded={props.expanded ?? true}
        language="ja"
        texts={JA_TEXTS}
        timeZone={props.timeZone ?? { status: 'ready', timeZone: 'Asia/Tokyo' }}
        now={props.now ?? WINTER_NOW}
        onSearch={onSearch}
        onClear={onClear}
        onToggle={onToggle}
      />
    </main>,
    { registrations: [registration], languages: props.languages ?? ['ja-JP'] },
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

// Intent 261102-timezone-ui-polish の U4（計画 Step 13、D23〜D28・Q4: A）: 日時の条件
describe('SearchPanel with date and time conditions', () => {
  const NEW_YORK: FieldTimeZone = { status: 'ready', timeZone: 'America/New_York' }
  const TIME_FIELDS: SearchField[] = [
    field('at', '受注の時刻', { operator: 'RANGE', valueType: 'DATETIME_TZ' }),
    field('exact', '出荷の時刻', { valueType: 'DATETIME_TZ' }),
    field('local', '締めの日時', { operator: 'RANGE', valueType: 'DATETIME' }),
  ]

  it('uses text inputs with one hint for a range and the exact match hint with the fraction note', async () => {
    renderPanel({ fields: TIME_FIELDS, timeZone: NEW_YORK })
    const rangeHint =
      'America/New_York (-05:00) で入力。始まりも終わりも含みます。端数を含めるときは 23:59:59.999999 のように入れます'
    const from = await screen.findByRole('textbox', { name: '受注の時刻の下限' })
    expect(from).toHaveAttribute('type', 'text')
    expect(from).toHaveAttribute('autocomplete', 'off')
    expect(from).toHaveAccessibleDescription(rangeHint)
    expect(screen.getByRole('textbox', { name: '受注の時刻の上限' })).toHaveAccessibleDescription(
      rangeHint,
    )
    // 範囲の案内は両端で1つだけ（時点の範囲と時差なしの範囲の2つ）
    expect(screen.getAllByText(/始まりも終わりも含みます/)).toHaveLength(2)
    expect(
      screen.getByRole('textbox', { name: '出荷の時刻（完全一致）' }),
    ).toHaveAccessibleDescription(
      'America/New_York (-05:00) で入力。例: 2026-11-02 09:30:15。秒の端数を持つ値は、端数まで入れてください（例: .123）',
    )
    expect(screen.getByRole('textbox', { name: '締めの日時の下限' })).toHaveAccessibleDescription(
      '始まりも終わりも含みます。端数を含めるときは 23:59:59.999999 のように入れます',
    )
  })

  it('shows the overlap choice after leaving an exact match field and searches with the choice', async () => {
    const user = userEvent.setup()
    const { onSearch } = renderPanel({ fields: TIME_FIELDS, timeZone: NEW_YORK })
    await user.type(
      await screen.findByRole('textbox', { name: '出荷の時刻（完全一致）' }),
      '2026-11-01 01:30:00',
    )
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
    await user.tab()
    expect(await screen.findByRole('radio', { name: '先（-04:00）' })).not.toBeChecked()
    await user.click(screen.getByRole('radio', { name: '後（-05:00）' }))
    await user.click(screen.getByRole('button', { name: '検索' }))
    expect(onSearch).toHaveBeenLastCalledWith({
      exact: { ...EMPTY_SEARCH_INPUT, value: '2026-11-01 01:30:00', overlapChoice: 'later' },
    })
  })

  it('does not search an unchosen overlap, focuses the first choice and clears the choice on edit', async () => {
    const user = userEvent.setup()
    const { onSearch } = renderPanel({ fields: TIME_FIELDS, timeZone: NEW_YORK })
    const exact = await screen.findByRole('textbox', { name: '出荷の時刻（完全一致）' })
    await user.type(exact, '2026-11-01 01:30:00{Enter}')
    const earlier = await screen.findByRole('radio', { name: '先（-04:00）' })
    await waitFor(() => expect(earlier).toHaveFocus())
    expect(screen.getByText('どちらの時刻かを選んでください')).toBeInTheDocument()
    expect(onSearch).not.toHaveBeenCalled()
    await user.click(earlier)
    expect(screen.queryByText('どちらの時刻かを選んでください')).not.toBeInTheDocument()
    await user.type(exact, '1')
    expect(screen.queryByRole('radio')).not.toBeInTheDocument()
  })

  it('rejects a skipped time and an unreadable value with the zone in the text', async () => {
    const user = userEvent.setup()
    const { onSearch } = renderPanel({ fields: TIME_FIELDS, timeZone: NEW_YORK })
    await user.type(
      await screen.findByRole('textbox', { name: '受注の時刻の下限' }),
      '2026-03-08 02:30:00',
    )
    await user.click(screen.getByRole('button', { name: '検索' }))
    expect(
      await screen.findByText(
        'America/New_York では、この時刻はありません（夏時間の切り替えで飛ばされる時刻です）',
      ),
    ).toBeInTheDocument()
    expect(screen.getByRole('textbox', { name: '受注の時刻の下限' })).toHaveFocus()
    await user.clear(screen.getByRole('textbox', { name: '受注の時刻の下限' }))
    await user.type(screen.getByRole('textbox', { name: '受注の時刻の下限' }), "2026-11-01'; --")
    await user.click(screen.getByRole('button', { name: '検索' }))
    expect(
      await screen.findByText('日時を YYYY-MM-DD HH:mm:ss の形で入れてください'),
    ).toBeInTheDocument()
    expect(onSearch).not.toHaveBeenCalled()
  })

  it('shows an error for a sent value that the current zone cannot read, keeping the value', async () => {
    renderPanel({
      fields: TIME_FIELDS,
      values: { exact: { ...EMPTY_SEARCH_INPUT, value: '2026-11-01T09:00:00' } },
    })
    const exact = await screen.findByRole('textbox', { name: '出荷の時刻（完全一致）' })
    expect(exact).toHaveValue('2026-11-01T09:00:00')
    expect(exact).toHaveAttribute('aria-invalid', 'true')
    expect(screen.getByRole('alert')).toHaveTextContent(
      '日時を YYYY-MM-DD HH:mm:ss の形で入れてください',
    )
  })

  it('disables instant fields while the zone failed but still searches without them', async () => {
    const user = userEvent.setup()
    const { onSearch } = renderPanel({ fields: TIME_FIELDS, timeZone: { status: 'error' } })
    expect(await screen.findByRole('textbox', { name: '出荷の時刻（完全一致）' })).toBeDisabled()
    const local = screen.getByRole('textbox', { name: '締めの日時の下限' })
    expect(local).toBeEnabled()
    await user.type(local, '2026-11-01 09:00:00.5')
    await user.click(screen.getByRole('button', { name: '検索' }))
    expect(onSearch).toHaveBeenLastCalledWith({
      local: { ...EMPTY_SEARCH_INPUT, from: '2026-11-01 09:00:00.5', overlapChoice: null },
    })
  })

  it('has no accessibility violations with a date time error and an overlap choice', async () => {
    const user = userEvent.setup()
    const { view } = renderPanel({ fields: TIME_FIELDS, timeZone: NEW_YORK })
    await user.type(
      await screen.findByRole('textbox', { name: '出荷の時刻（完全一致）' }),
      '2026-11-01 01:30:00',
    )
    await user.type(screen.getByRole('textbox', { name: '受注の時刻の下限' }), 'x')
    await user.click(screen.getByRole('button', { name: '検索' }))
    await screen.findByRole('radio', { name: '先（-04:00）' })
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
