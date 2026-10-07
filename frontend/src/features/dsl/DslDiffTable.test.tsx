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
import type { DslDiff, SchemaDiff, TableDiff } from './api/types'
import { countColumnChanges, countTableChanges } from './diffCounts'
import { DslDiffTable } from './DslDiffTable'
import { sampleSchemas, sampleTables } from './testing/fixtures'
import { renderDsl } from './testing/renderDsl'

function diffOf(tables: TableDiff[] = sampleTables(), appliedExists = true): DslDiff {
  return { appliedExists, schemas: sampleSchemas(tables) }
}

function schemaOf(name: string, change: SchemaDiff['change'], tables: TableDiff[]): SchemaDiff {
  return { name, label: { ja: `${name} の表示名`, en: `${name} label` }, change, tables }
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
    expect(screen.getByTestId('dsl-diff-row-sales/dept_mst')).toHaveTextContent('変わった')
    expect(screen.getByTestId('dsl-diff-row-sales/dept_mst')).toHaveTextContent('+1 −1 ~1')
    expect(screen.getByTestId('dsl-diff-row-sales/item_mst')).toHaveTextContent('増えた')
    expect(screen.getByTestId('dsl-diff-row-sales/old_code')).toHaveTextContent('減った')
    expect(screen.queryByTestId('dsl-diff-row-sales/same_mst')).not.toBeInTheDocument()
    expect(screen.queryByTestId('dsl-diff-no-applied')).not.toBeInTheDocument()
  })

  it('shows the unchanged tables too when show all is on', async () => {
    const user = userEvent.setup()
    renderDsl(<DslDiffTable diff={diffOf()} />)

    await user.click(screen.getByRole('switch', { name: 'すべて表示' }))

    const row = screen.getByTestId('dsl-diff-row-sales/same_mst')
    expect(row).toHaveTextContent('変わらない')
    expect(row).toHaveTextContent('—')
    expect(within(row).queryByRole('button')).not.toBeInTheDocument()
  })

  it('draws the columns of an opened row only and toggles aria-expanded', async () => {
    const user = userEvent.setup()
    renderDsl(<DslDiffTable diff={diffOf()} />)

    expect(screen.queryByTestId('dsl-diff-columns-sales/dept_mst')).not.toBeInTheDocument()
    const toggle = screen.getByRole('button', { name: 'dept_mst のカラムの違いを開く' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')

    await user.click(toggle)

    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(toggle).toHaveAccessibleName('dept_mst のカラムの違いを閉じる')
    expect(toggle).toHaveFocus()
    const columns = screen.getByRole('table', { name: 'dept_mst のカラムの違い' })
    expect(within(columns).getByText('dept_kana')).toBeInTheDocument()
    expect(within(columns).getByText('label.ja・dbType.length')).toBeInTheDocument()
    expect(screen.queryByTestId('dsl-diff-columns-sales/item_mst')).not.toBeInTheDocument()

    await user.keyboard('{Enter}')
    expect(screen.queryByTestId('dsl-diff-columns-sales/dept_mst')).not.toBeInTheDocument()
  })

  it('does not draw the columns of 100 tables with 100 columns until a row is opened', async () => {
    const user = userEvent.setup()
    const { container } = renderDsl(<DslDiffTable diff={largeDiff()} />)

    expect(screen.getAllByTestId(/^dsl-diff-row-/)).toHaveLength(100)
    expect(container.querySelectorAll('[data-testid^="dsl-diff-columns-"]')).toHaveLength(0)
    expect(screen.queryByText('column_0_0')).not.toBeInTheDocument()

    await user.click(screen.getByTestId('dsl-diff-toggle-sales/table_5'))

    expect(container.querySelectorAll('[data-testid^="dsl-diff-columns-"]')).toHaveLength(1)
    expect(
      within(screen.getByTestId('dsl-diff-columns-sales/table_5')).getAllByRole('row'),
    ).toHaveLength(101)
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

  it('puts a rowgroup heading with the schema name, label and change before the tables of each schema', () => {
    renderDsl(
      <DslDiffTable
        diff={{
          appliedExists: true,
          schemas: [schemaOf('sales', 'CHANGED', sampleTables()), schemaOf('hr', 'ADDED', [])],
        }}
      />,
    )

    const heading = screen.getByTestId('dsl-diff-schema-heading-sales')
    expect(heading.tagName).toBe('TH')
    expect(heading).toHaveAttribute('scope', 'rowgroup')
    expect(heading).toHaveAttribute('colspan', '3')
    expect(heading).toHaveTextContent('スキーマ sales（sales の表示名）')
    expect(heading).toHaveTextContent('変わった')
    expect(screen.getByTestId('dsl-diff-schema-heading-hr')).toHaveTextContent('増えた')
    expect(
      within(screen.getByTestId('dsl-diff-schema-sales')).getByTestId(
        'dsl-diff-row-sales/dept_mst',
      ),
    ).toBeInTheDocument()
  })

  it('keeps the open state apart for tables of the same name in two schemas', async () => {
    const user = userEvent.setup()
    const table = (): TableDiff => ({
      name: 'dept_mst',
      change: 'CHANGED',
      columns: [{ name: 'code', change: 'ADDED', changedItems: [] }],
    })
    renderDsl(
      <DslDiffTable
        diff={{
          appliedExists: true,
          schemas: [schemaOf('sales', 'UNCHANGED', [table()]), schemaOf('hr', 'ADDED', [table()])],
        }}
      />,
    )

    await user.click(screen.getByTestId('dsl-diff-toggle-hr/dept_mst'))

    expect(screen.getByTestId('dsl-diff-columns-hr/dept_mst')).toBeInTheDocument()
    expect(screen.queryByTestId('dsl-diff-columns-sales/dept_mst')).not.toBeInTheDocument()
    expect(screen.getByTestId('dsl-diff-toggle-sales/dept_mst')).toHaveAttribute(
      'aria-expanded',
      'false',
    )
  })

  it('hides an unchanged schema without changed tables unless show all is on, but keeps a relabeled one', async () => {
    const user = userEvent.setup()
    const same: TableDiff = { name: 'same', change: 'UNCHANGED', columns: [] }
    renderDsl(
      <DslDiffTable
        diff={{
          appliedExists: true,
          schemas: [schemaOf('quiet', 'UNCHANGED', [same]), schemaOf('renamed', 'CHANGED', [same])],
        }}
      />,
    )

    expect(screen.queryByTestId('dsl-diff-schema-heading-quiet')).not.toBeInTheDocument()
    expect(screen.getByTestId('dsl-diff-schema-heading-renamed')).toBeInTheDocument()
    expect(screen.queryByTestId('dsl-diff-row-renamed/same')).not.toBeInTheDocument()

    await user.click(screen.getByRole('switch', { name: 'すべて表示' }))

    expect(screen.getByTestId('dsl-diff-schema-heading-quiet')).toBeInTheDocument()
    expect(screen.getByTestId('dsl-diff-row-quiet/same')).toBeInTheDocument()
  })

  it('counts the tables of all schemas together', () => {
    const diff: DslDiff = {
      appliedExists: true,
      schemas: [schemaOf('a', 'UNCHANGED', sampleTables()), schemaOf('b', 'ADDED', sampleTables())],
    }
    renderDsl(<DslDiffTable diff={diff} />)

    expect(screen.getByTestId('dsl-diff-counts')).toHaveTextContent(
      '増えた 2・減った 2・変わった 2',
    )
  })

  it('has no accessibility violations with schema headings in English', async () => {
    const { container } = renderDsl(
      <DslDiffTable
        diff={{
          appliedExists: true,
          schemas: [schemaOf('sales', 'CHANGED', sampleTables()), schemaOf('hr', 'REMOVED', [])],
        }}
      />,
      ['en'],
    )

    expect(
      screen.getByRole('table', { name: 'Differences by schema and table' }),
    ).toBeInTheDocument()
    expect(screen.getByTestId('dsl-diff-schema-heading-sales')).toHaveTextContent(
      'Schema sales (sales label)',
    )
    expect(await axe(container)).toHaveNoViolations()
  })

  it('has no accessibility violations with an opened row', async () => {
    const user = userEvent.setup()
    const { container } = renderDsl(<DslDiffTable diff={diffOf()} />)
    await user.click(screen.getByTestId('dsl-diff-toggle-sales/dept_mst'))

    expect(await axe(container)).toHaveNoViolations()
  })
})
