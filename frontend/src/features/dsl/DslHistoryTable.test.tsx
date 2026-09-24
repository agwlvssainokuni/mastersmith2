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
// 適用の履歴のテスト（AC5.1.1・AC5.1.2・AC5.2.1、BR6.3・BR7.3、NFR9.1・NFR10.1）。
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { DslHistoryTable, type DslHistoryTableProps } from './DslHistoryTable'
import { historyOf } from './testing/fixtures'
import { renderDsl } from './testing/renderDsl'

function renderTable(
  overrides: Partial<DslHistoryTableProps> = {},
  languages: readonly string[] = ['ja-JP'],
) {
  const props: DslHistoryTableProps = {
    entries: historyOf(),
    loadState: 'loaded',
    busy: false,
    restoringId: null,
    onRestore: vi.fn(),
    onDownloadApplied: vi.fn(),
    onRetry: vi.fn(),
    timeZone: 'Asia/Tokyo',
    ...overrides,
  }
  return { props, ...renderDsl(<DslHistoryTable {...props} />, languages) }
}

describe('DslHistoryTable', () => {
  it('lists the history in the order of the server with the applied mark in text', () => {
    renderTable()

    const current = screen.getByTestId('dsl-history-row-revision-3')
    expect(current).toHaveTextContent('2026-09-24 10:15 JST')
    expect(current).toHaveTextContent('taro@example.com')
    expect(current).toHaveTextContent('3f9a1c000000…')
    expect(current).toHaveTextContent('適用中')
    const older = screen.getByTestId('dsl-history-row-revision-2')
    expect(older).toHaveTextContent('2026-09-20 16:40 JST')
    expect(older).toHaveTextContent('不明')
    expect(older).not.toHaveTextContent('適用中')
    const rows = screen.getAllByTestId(/^dsl-history-row-/)
    expect(rows.map((r) => r.dataset.testid)).toEqual([
      'dsl-history-row-revision-3',
      'dsl-history-row-revision-2',
    ])
  })

  it('restores an older version and downloads the applied one', async () => {
    const user = userEvent.setup()
    const { props } = renderTable()

    await user.click(
      screen.getByRole('button', { name: '2026-09-20 16:40 JST の版をプレビューに戻す' }),
    )
    await user.click(screen.getByRole('button', { name: '適用中の DSL をダウンロード' }))

    expect(props.onRestore).toHaveBeenCalledWith(props.entries[1])
    expect(props.onDownloadApplied).toHaveBeenCalledTimes(1)
  })

  it('disables the restore while busy but keeps the download', () => {
    renderTable({ busy: true, restoringId: 'revision-2' })

    expect(screen.getByTestId('dsl-history-restore-revision-2')).toBeDisabled()
    expect(screen.getByTestId('dsl-history-restore-revision-2')).toHaveAttribute(
      'aria-busy',
      'true',
    )
    expect(screen.getByTestId('dsl-history-download')).toBeEnabled()
  })

  it('shows the empty, loading and failed states', async () => {
    const user = userEvent.setup()
    const empty = renderTable({ entries: [] })
    expect(screen.getByTestId('dsl-history-empty')).toHaveTextContent('まだ一度も適用していません')
    empty.unmount()

    const loading = renderTable({ loadState: 'loading' })
    expect(screen.getByTestId('dsl-history-loading')).toBeInTheDocument()
    loading.unmount()

    const { props } = renderTable({ loadState: 'failed' })
    await user.click(screen.getByRole('button', { name: 'もう一度読み込む' }))
    expect(props.onRetry).toHaveBeenCalledTimes(1)
  })

  it('shows English text', () => {
    renderTable({}, ['en-US'])

    expect(screen.getByRole('columnheader', { name: 'Applied at' })).toBeInTheDocument()
    expect(screen.getByTestId('dsl-history-row-revision-2')).toHaveTextContent('Unknown')
    expect(
      screen.getByRole('button', {
        name: 'Restore to preview the version of Sep 20, 2026, 16:40 GMT+9',
      }),
    ).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderTable()

    expect(await axe(container)).toHaveNoViolations()
  })
})
