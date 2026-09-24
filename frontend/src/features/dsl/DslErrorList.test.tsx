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
// 誤りの一覧のテスト（BR5.1〜BR5.4、NFR1.21・NFR3.9・NFR9.1・NFR10.1、AC2.2.9）。
import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import type { DslErrorReport } from './api/types'
import { DslErrorList } from './DslErrorList'
import { errorReport } from './testing/fixtures'
import { renderDsl } from './testing/renderDsl'

describe('DslErrorList', () => {
  it('shows the count first and every error when there are 100 or fewer', () => {
    renderDsl(<DslErrorList report={errorReport(100)} />)

    expect(screen.getByTestId('dsl-error-list-count')).toHaveTextContent(
      'DSL に誤りが 100件あります。受け付けていません。',
    )
    expect(screen.queryByTestId('dsl-error-list-rest')).not.toBeInTheDocument()
    expect(screen.getAllByTestId('dsl-error-list-row')).toHaveLength(100)
  })

  it('draws only the first 100 of 128 errors and shows the rest', () => {
    renderDsl(<DslErrorList report={{ total: 128, errors: errorReport(128, 128).errors }} />)

    expect(screen.getByTestId('dsl-error-list-count')).toHaveTextContent('128件')
    expect(screen.getByTestId('dsl-error-list-rest')).toHaveTextContent(
      '先頭の 100件を表示しています（残り 28件）。',
    )
    expect(screen.getAllByTestId('dsl-error-list-row')).toHaveLength(100)
    expect(screen.queryByText('誤り 101')).not.toBeInTheDocument()
  })

  it('shows the line, column, place, kind and message, with a dash for missing values', () => {
    const report: DslErrorReport = {
      total: 2,
      errors: [
        {
          kind: 'SEMANTIC',
          line: 12,
          column: 5,
          path: 'tables.dept_mst',
          message: 'ラベルがありません',
        },
        { kind: 'SYNTAX', line: null, column: null, path: null, message: '<script>x</script>' },
      ],
    }
    const { container } = renderDsl(<DslErrorList report={report} />)

    const table = screen.getByRole('table', { name: 'DSL の誤りの一覧' })
    const rows = within(table).getAllByRole('row')
    expect(rows[1]).toHaveTextContent('125tables.dept_mst意味ラベルがありません')
    expect(
      within(rows[2])
        .getAllByRole('cell')
        .map((c) => c.textContent),
    ).toEqual(['—', '—', '—', '構文', '<script>x</script>'])
    expect(container.querySelector('script')).toBeNull()
  })

  it('shows only the reason for a single dangerous-shape error', () => {
    renderDsl(
      <DslErrorList
        report={{
          total: 1,
          errors: [
            { kind: 'SIZE_LIMIT', line: null, column: null, path: null, message: '大きすぎます' },
          ],
        }}
      />,
    )

    expect(screen.getByTestId('dsl-error-list-single')).toHaveTextContent('大きさ：大きすぎます')
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
    expect(screen.queryByTestId('dsl-error-list-count')).not.toBeInTheDocument()
  })

  it('shows a table for a single syntax error', () => {
    renderDsl(
      <DslErrorList
        report={{
          total: 1,
          errors: [{ kind: 'SYNTAX', line: 1, column: 1, path: null, message: '構文の誤り' }],
        }}
      />,
    )

    expect(screen.getByRole('table')).toBeInTheDocument()
    expect(screen.queryByTestId('dsl-error-list-single')).not.toBeInTheDocument()
  })

  it('shows a general message when the shape of the body is unknown', () => {
    renderDsl(<DslErrorList report={null} />)

    expect(screen.getByTestId('dsl-error-list-alert')).toHaveTextContent(
      'DSL を受け付けられませんでした',
    )
    expect(screen.queryByRole('table')).not.toBeInTheDocument()
  })

  it('moves the focus to the count alert when shown', () => {
    renderDsl(<DslErrorList report={errorReport(3)} />)

    const alert = screen.getByRole('alert')
    expect(alert).toHaveFocus()
    expect(alert).toHaveAttribute('tabindex', '-1')
  })

  it('shows English text', () => {
    renderDsl(<DslErrorList report={{ total: 150, errors: errorReport(150, 100).errors }} />, [
      'en-US',
    ])

    expect(screen.getByTestId('dsl-error-list-count')).toHaveTextContent(
      'The DSL has 150 errors. It was not accepted.',
    )
    expect(screen.getByTestId('dsl-error-list-rest')).toHaveTextContent(
      'Showing the first 100 (50 more).',
    )
  })

  it('has no accessibility violations', async () => {
    const { container } = renderDsl(<DslErrorList report={errorReport(5)} />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
