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
// メニューの木のテスト（BR5.9、NFR1.19・NFR3.9・NFR9.1・NFR10.1、AC6.2.5）。
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import type { MenuNode } from './api/types'
import { DslMenuTree } from './DslMenuTree'
import { renderDsl } from './testing/renderDsl'

const tree: MenuNode[] = [
  {
    label: { ja: '基本マスタ', en: 'Basic masters' },
    table: null,
    children: [
      { label: { ja: '部署', en: 'Departments' }, table: 'dept_mst', children: [] },
      {
        label: { ja: '品目', en: 'Items' },
        table: null,
        children: [{ label: { ja: '価格', en: 'Prices' }, table: 'price_mst', children: [] }],
      },
    ],
  },
  { label: { ja: '', en: '' }, table: 'no_label', children: [] },
]

describe('DslMenuTree', () => {
  it('opens only the first level and shows Japanese labels with physical names', () => {
    renderDsl(<DslMenuTree nodes={tree} />)

    expect(screen.getByRole('button', { name: '基本マスタ を閉じる' })).toHaveAttribute(
      'aria-expanded',
      'true',
    )
    expect(screen.getByTestId('dsl-menu-node-0.0')).toHaveTextContent('部署（dept_mst）')
    expect(screen.getByRole('button', { name: '品目 を開く' })).toHaveAttribute(
      'aria-expanded',
      'false',
    )
    expect(screen.queryByText('価格')).not.toBeInTheDocument()
    expect(screen.getByTestId('dsl-menu-node-1')).toHaveTextContent('—（no_label）')
  })

  it('opens and closes a deeper level with the keyboard', async () => {
    const user = userEvent.setup()
    renderDsl(<DslMenuTree nodes={tree} />)

    screen.getByRole('button', { name: '品目 を開く' }).focus()
    await user.keyboard('{Enter}')
    expect(screen.getByTestId('dsl-menu-node-0.1.0')).toHaveTextContent('価格（price_mst）')

    await user.click(screen.getByRole('button', { name: '基本マスタ を閉じる' }))
    expect(screen.queryByTestId('dsl-menu-node-0.0')).not.toBeInTheDocument()
  })

  it('shows English labels without translating the physical names', () => {
    renderDsl(<DslMenuTree nodes={tree} />, ['en'])

    expect(screen.getByRole('button', { name: 'Collapse Basic masters' })).toBeInTheDocument()
    expect(screen.getByTestId('dsl-menu-node-0.0')).toHaveTextContent('Departments（dept_mst）')
  })

  it('keeps a label that looks like HTML as text', () => {
    const nodes: MenuNode[] = [
      { label: { ja: '<script>alert(1)</script>', en: 'x' }, table: 't', children: [] },
    ]
    const { container } = renderDsl(<DslMenuTree nodes={nodes} />)

    expect(screen.getByText('<script>alert(1)</script>')).toBeInTheDocument()
    expect(container.querySelector('script')).toBeNull()
  })

  it('tells that there is no menu', () => {
    renderDsl(<DslMenuTree nodes={[]} />)

    expect(screen.getByTestId('dsl-menu-empty')).toHaveTextContent('メニューはありません')
  })

  it('has no accessibility violations', async () => {
    const { container } = renderDsl(<DslMenuTree nodes={tree} />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
