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
// タブのテスト（interaction-spec.md の DslTabs、NFR9.1・NFR10.1）。
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { DslTabs, type DslTab } from './DslTabs'
import { renderDsl } from './testing/renderDsl'

const panels = {
  preview: <p>プレビューの中身</p>,
  submit: <p>投入の中身</p>,
  history: <p>履歴の中身</p>,
}

function Harness({ onSelect }: { onSelect: (tab: DslTab) => void }) {
  const [selected, setSelected] = useState<DslTab>('preview')
  return (
    <DslTabs
      selected={selected}
      panels={panels}
      onSelect={(tab) => {
        onSelect(tab)
        setSelected(tab)
      }}
    />
  )
}

describe('DslTabs', () => {
  it('selects the preview first and shows only its panel', () => {
    renderDsl(<Harness onSelect={vi.fn()} />)

    expect(screen.getByRole('tablist', { name: 'DSL の操作' })).toBeInTheDocument()
    expect(screen.getByRole('tab', { name: 'プレビュー' })).toHaveAttribute('aria-selected', 'true')
    expect(screen.getByRole('tabpanel')).toHaveTextContent('プレビューの中身')
    expect(screen.queryByText('投入の中身')).not.toBeInTheDocument()
  })

  it('moves between the tabs with the arrow keys and clicks', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    renderDsl(<Harness onSelect={onSelect} />)

    screen.getByRole('tab', { name: 'プレビュー' }).focus()
    await user.keyboard('{ArrowRight}')
    expect(onSelect).toHaveBeenLastCalledWith('submit')
    expect(screen.getByRole('tab', { name: '投入' })).toHaveFocus()
    expect(screen.getByRole('tabpanel')).toHaveTextContent('投入の中身')

    await user.click(screen.getByRole('tab', { name: '履歴' }))
    expect(onSelect).toHaveBeenLastCalledWith('history')
    expect(screen.getByRole('tabpanel')).toHaveTextContent('履歴の中身')
  })

  it('shows English labels', () => {
    renderDsl(<Harness onSelect={vi.fn()} />, ['en-US'])

    expect(screen.getAllByRole('tab').map((tab) => tab.textContent)).toEqual([
      'Preview',
      'Submit',
      'History',
    ])
  })

  it('has no accessibility violations', async () => {
    const { container } = renderDsl(<Harness onSelect={vi.fn()} />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
