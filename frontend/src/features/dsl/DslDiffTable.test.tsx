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
// 違いの表のテスト（BR5.7、NFR1.19・NFR3.9・NFR9.1・NFR10.1、AC3.1.3〜AC3.1.5）。
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import type { DslDiff, TableDiff } from './api/types'
import { countColumnChanges, countTableChanges } from './diffCounts'
import { DslDiffTable } from './DslDiffTable'
import { sampleTables } from './testing/fixtures'
import { renderDsl } from './testing/renderDsl'

function diffOf(tables: TableDiff[] = sampleTables(), appliedExists = true): DslDiff {
  return { appliedExists, tables }
}

/** 100 カラムが変わったテーブルを 100 個 */
function largeDiff(): DslDiff {
  const tables: TableDiff[] = Array.from({ length: 100 }, (_, t) => ({
    name: `table_${t}`,
    change: 'CHANGED',
    columns: Array.from({ length: 100 }, (__, c) => ({
      name: `column_${t}_${c}`,
      change: 'CHANGED',
      changedItems: ['label.ja'],
    })),
  }))
  return diffOf(tables)
}

describe('DslDiffTable', () => {
  it('shows only the changed tables with text badges and the counts by default', () => {
    renderDsl(<DslDiffTable diff={diffOf()} />)

    expect(screen.getByTestId('dsl-diff-counts')).toHaveTextContent(
      '増えた 1・減った 1・変わった 1',
    )
    expect(screen.getByTestId('dsl-diff-row-dept_mst')).toHaveTextContent('変わった')
    expect(screen.getByTestId('dsl-diff-row-dept_mst')).toHaveTextContent('+1 −1 ~1')
    expect(screen.getByTestId('dsl-diff-row-item_mst')).toHaveTextContent('増えた')
    expect(screen.getByTestId('dsl-diff-row-old_code')).toHaveTextContent('減った')
    expect(screen.queryByTestId('dsl-diff-row-same_mst')).not.toBeInTheDocument()
    expect(screen.queryByTestId('dsl-diff-no-applied')).not.toBeInTheDocument()
  })

  it('shows the unchanged tables too when show all is on', async () => {
    const user = userEvent.setup()
    renderDsl(<DslDiffTable diff={diffOf()} />)

    await user.click(screen.getByRole('switch', { name: 'すべて表示' }))

    const row = screen.getByTestId('dsl-diff-row-same_mst')
    expect(row).toHaveTextContent('変わらない')
    expect(row).toHaveTextContent('—')
    expect(within(row).queryByRole('button')).not.toBeInTheDocument()
  })

  it('draws the columns of an opened row only and toggles aria-expanded', async () => {
    const user = userEvent.setup()
    renderDsl(<DslDiffTable diff={diffOf()} />)

    expect(screen.queryByTestId('dsl-diff-columns-dept_mst')).not.toBeInTheDocument()
    const toggle = screen.getByRole('button', { name: 'dept_mst のカラムの違いを開く' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')

    await user.click(toggle)

    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(toggle).toHaveAccessibleName('dept_mst のカラムの違いを閉じる')
    expect(toggle).toHaveFocus()
    const columns = screen.getByRole('table', { name: 'dept_mst のカラムの違い' })
    expect(within(columns).getByText('dept_kana')).toBeInTheDocument()
    expect(within(columns).getByText('label.ja・dbType.length')).toBeInTheDocument()
    expect(screen.queryByTestId('dsl-diff-columns-item_mst')).not.toBeInTheDocument()

    await user.keyboard('{Enter}')
    expect(screen.queryByTestId('dsl-diff-columns-dept_mst')).not.toBeInTheDocument()
  })

  it('does not draw the columns of 100 tables with 100 columns until a row is opened', async () => {
    const user = userEvent.setup()
    const { container } = renderDsl(<DslDiffTable diff={largeDiff()} />)

    expect(screen.getAllByTestId(/^dsl-diff-row-/)).toHaveLength(100)
    expect(container.querySelectorAll('[data-testid^="dsl-diff-columns-"]')).toHaveLength(0)
    expect(screen.queryByText('column_0_0')).not.toBeInTheDocument()

    await user.click(screen.getByTestId('dsl-diff-toggle-table_5'))

    expect(container.querySelectorAll('[data-testid^="dsl-diff-columns-"]')).toHaveLength(1)
    expect(within(screen.getByTestId('dsl-diff-columns-table_5')).getAllByRole('row')).toHaveLength(
      101,
    )
  })

  it('tells that everything is added when nothing is applied yet', () => {
    const tables: TableDiff[] = [
      {
        name: 'dept_mst',
        change: 'ADDED',
        columns: [{ name: 'id', change: 'ADDED', changedItems: [] }],
      },
    ]
    renderDsl(<DslDiffTable diff={diffOf(tables, false)} />)

    expect(screen.getByTestId('dsl-diff-no-applied')).toHaveTextContent(
      'すべてのテーブルとカラムを',
    )
    expect(screen.getByTestId('dsl-diff-counts')).toHaveTextContent('増えた 1')
  })

  it('tells that there is no difference', () => {
    renderDsl(<DslDiffTable diff={diffOf([{ name: 'a', change: 'UNCHANGED', columns: [] }])} />)

    expect(screen.getByTestId('dsl-diff-none')).toHaveTextContent('違いはありません')
  })

  it('keeps table and column names that look like HTML as text and does not translate them', async () => {
    const user = userEvent.setup()
    const tables: TableDiff[] = [
      {
        name: '<script>x</script>',
        change: 'ADDED',
        columns: [{ name: '<img src=x>', change: 'ADDED', changedItems: [] }],
      },
    ]
    const { container } = renderDsl(<DslDiffTable diff={diffOf(tables)} />, ['en-US'])

    await user.click(screen.getByRole('button', { name: /<script>x<\/script>/ }))

    expect(
      screen.getByRole('button', { name: 'Hide the column differences of <script>x</script>' }),
    ).toBeInTheDocument()
    expect(screen.getByText('<img src=x>')).toBeInTheDocument()
    expect(container.querySelector('script, img')).toBeNull()
    expect(screen.getByTestId('dsl-diff-counts')).toHaveTextContent('Added 1')
  })

  it('counts the changes of the tables and of the columns', () => {
    expect(countTableChanges(diffOf())).toEqual({ added: 1, removed: 1, changed: 1 })
    expect(countColumnChanges(diffOf())).toEqual({ added: 2, removed: 2, changed: 1 })
  })

  it('has no accessibility violations with an opened row', async () => {
    const user = userEvent.setup()
    const { container } = renderDsl(<DslDiffTable diff={diffOf()} />)
    await user.click(screen.getByTestId('dsl-diff-toggle-dept_mst'))

    expect(await axe(container)).toHaveNoViolations()
  })
})
