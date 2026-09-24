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
// 照合の警告の一覧のテスト（BR5.3・BR5.8・BR7.3、NFR3.9・NFR9.1・NFR10.1、AC3.2.1〜AC3.2.4）。
import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import type { DslWarning } from './api/types'
import { DslWarningList } from './DslWarningList'
import { renderDsl } from './testing/renderDsl'

const warnings: DslWarning[] = [
  { kind: 'TYPE_MISMATCH', path: 'item_mst.price', message: '型が違います' },
  { kind: 'COLUMN_MISSING', path: 'dept_mst.remarks', message: '対象DB にありません' },
  { kind: 'TARGET_UNAVAILABLE', path: null, message: '照合できませんでした' },
  { kind: 'COLUMN_MISSING', path: 'dept_mst.fax', message: '<script>alert(1)</script>' },
]

describe('DslWarningList', () => {
  it('groups the warnings by kind with the not-compared warning first', () => {
    renderDsl(<DslWarningList warnings={warnings} />)

    const headings = screen.getAllByRole('heading', { level: 4 }).map((h) => h.textContent)
    expect(headings).toEqual([
      '照合できませんでした（対象DB に接続できないか、応答がありません）',
      '対象DB に無いカラム',
      '型の違い',
    ])
    const columns = screen.getByTestId('dsl-warning-group-COLUMN_MISSING')
    expect(within(columns).getAllByRole('listitem')).toHaveLength(2)
    expect(columns).toHaveTextContent('dept_mst.remarks … 対象DB にありません')
    expect(screen.getByTestId('dsl-warning-group-TARGET_UNAVAILABLE')).not.toHaveTextContent('…')
  })

  it('keeps the message of the server as text', () => {
    const { container } = renderDsl(<DslWarningList warnings={warnings} />)

    expect(screen.getByText('<script>alert(1)</script>')).toBeInTheDocument()
    expect(container.querySelector('script')).toBeNull()
  })

  it('tells that there is no warning', () => {
    renderDsl(<DslWarningList warnings={[]} />)

    expect(screen.getByTestId('dsl-warning-none')).toHaveTextContent('食い違いはありません')
  })

  it('shows English headings without translating the places', () => {
    renderDsl(<DslWarningList warnings={warnings} />, ['en-GB'])

    expect(
      screen.getByRole('heading', { name: 'Columns missing from the target database' }),
    ).toBeInTheDocument()
    expect(screen.getByTestId('dsl-warning-group-TYPE_MISMATCH')).toHaveTextContent(
      'item_mst.price',
    )
  })

  it('has no accessibility violations', async () => {
    const { container } = renderDsl(<DslWarningList warnings={warnings} />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
