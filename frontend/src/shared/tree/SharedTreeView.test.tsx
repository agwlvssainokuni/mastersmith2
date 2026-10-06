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
// 共有の木のテスト（frontend-components.md 2.6、unit-test-instructions.md 4節、NFR1.9・NFR4.1〜NFR4.4、BR4.1〜BR4.12）。
// 開閉と選びは呼ぶ側が持つため、テストの中の Harness が状態を持って木に渡す。loadChildren は手で進める Promise。
import { act, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { SharedTreeView } from './SharedTreeView'
import type { SharedTreeLabels, SharedTreeNode } from './types'

const labels: SharedTreeLabels = {
  expand: (label) => `${label} を開く`,
  collapse: (label) => `${label} を閉じる`,
  loading: '読み込み中',
  loadFailed: '読み込めませんでした',
  retry: '再試行',
  empty: '子がありません',
}

const englishLabels: SharedTreeLabels = {
  expand: (label) => `Expand ${label}`,
  collapse: (label) => `Collapse ${label}`,
  loading: 'Loading',
  loadFailed: 'Could not load',
  retry: 'Retry',
  empty: 'No children',
}

const SALES: SharedTreeNode = { id: 'db:sales', label: '販売DB', hasChildren: true }
const STOCK: SharedTreeNode = { id: 'db:stock', label: '在庫DB', hasChildren: true }
const LEAF: SharedTreeNode = { id: 'leaf', label: '設定', hasChildren: false }

interface Pending {
  id: string
  resolve: (children: readonly SharedTreeNode[]) => void
  reject: (reason: unknown) => void
}

/** 呼ばれた順に手で進められる loadChildren */
function manualLoader() {
  const pending: Pending[] = []
  const loadChildren = vi.fn(
    (id: string) =>
      new Promise<readonly SharedTreeNode[]>((resolve, reject) => {
        pending.push({ id, resolve, reject })
      }),
  )
  const take = (id: string): Pending => {
    const index = pending.findIndex((entry) => entry.id === id)
    if (index < 0) {
      throw new Error(`loadChildren was not called for ${id}`)
    }
    return pending.splice(index, 1)[0]
  }
  return { loadChildren, take }
}

/** 子を表で返す loadChildren */
function tableLoader(table: Record<string, readonly SharedTreeNode[]>) {
  return vi.fn((id: string) => Promise.resolve(table[id] ?? []))
}

interface HarnessProps {
  nodes: readonly SharedTreeNode[]
  loadChildren: (id: string) => Promise<readonly SharedTreeNode[]>
  initialExpanded?: readonly string[]
  initialSelected?: string | null
  treeLabels?: SharedTreeLabels
  onSelect?: (id: string) => void
  onToggle?: (id: string, expanded: boolean) => void
}

/** 呼ぶ側の役（開閉と選びを持つ） */
function Harness({
  nodes,
  loadChildren,
  initialExpanded = [],
  initialSelected = null,
  treeLabels = labels,
  onSelect,
  onToggle,
}: HarnessProps) {
  const [expandedIds, setExpandedIds] = useState<ReadonlySet<string>>(new Set(initialExpanded))
  const [selectedId, setSelectedId] = useState<string | null>(initialSelected)
  return (
    <main>
      <SharedTreeView
        nodes={nodes}
        loadChildren={loadChildren}
        selectedId={selectedId}
        onSelect={(id) => {
          onSelect?.(id)
          setSelectedId(id)
        }}
        expandedIds={expandedIds}
        onToggle={(id, expanded) => {
          onToggle?.(id, expanded)
          setExpandedIds((current) => {
            const next = new Set(current)
            if (expanded) {
              next.add(id)
            } else {
              next.delete(id)
            }
            return next
          })
        }}
        labels={treeLabels}
        ariaLabel="権限の設定の対象"
      />
    </main>
  )
}

describe('SharedTreeView', () => {
  it('reports the next state on toggle and shows aria-expanded from expandedIds', async () => {
    const user = userEvent.setup()
    const onToggle = vi.fn()
    render(<Harness nodes={[SALES, LEAF]} loadChildren={tableLoader({})} onToggle={onToggle} />)

    const toggle = screen.getByRole('button', { name: '販売DB を開く' })
    expect(toggle).toHaveAttribute('aria-expanded', 'false')
    await user.click(toggle)

    expect(onToggle).toHaveBeenLastCalledWith('db:sales', true)
    const collapse = await screen.findByRole('button', { name: '販売DB を閉じる' })
    expect(collapse).toHaveAttribute('aria-expanded', 'true')
    await user.click(collapse)
    expect(onToggle).toHaveBeenLastCalledWith('db:sales', false)
    // 子を持たない節には開閉のボタンを出さない
    expect(screen.queryByRole('button', { name: '設定 を開く' })).not.toBeInTheDocument()
  })

  it('reports the selection and marks only the selected node with aria-current', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    render(
      <Harness
        nodes={[SALES, LEAF]}
        loadChildren={tableLoader({})}
        initialSelected="missing"
        onSelect={onSelect}
      />,
    )
    // 読み込んだ節に無い selectedId では、どの節にも付けない
    expect(document.querySelectorAll('[aria-current]')).toHaveLength(0)

    await user.click(screen.getByRole('button', { name: '設定' }))

    expect(onSelect).toHaveBeenCalledWith('leaf')
    expect(screen.getByRole('button', { name: '設定' })).toHaveAttribute('aria-current', 'true')
    expect(screen.getByRole('button', { name: '販売DB' })).not.toHaveAttribute('aria-current')
    // 選びは開閉を変えない
    expect(screen.getByRole('button', { name: '販売DB を開く' })).toHaveAttribute(
      'aria-expanded',
      'false',
    )
  })

  it('loads the children only the first time a node is opened', async () => {
    const user = userEvent.setup()
    const loadChildren = tableLoader({
      'db:sales': [{ id: 't:orders', label: '受注', hasChildren: false }],
    })
    render(<Harness nodes={[SALES]} loadChildren={loadChildren} />)

    await user.click(screen.getByRole('button', { name: '販売DB を開く' }))
    expect(await screen.findByRole('button', { name: '受注' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: '販売DB を閉じる' }))
    await user.click(screen.getByRole('button', { name: '販売DB を開く' }))
    expect(await screen.findByRole('button', { name: '受注' })).toBeInTheDocument()

    expect(loadChildren).toHaveBeenCalledTimes(1)
    expect(loadChildren).toHaveBeenCalledWith('db:sales')
  })

  it('reloads the open nodes when the nodes are replaced and drops a result that arrives too late', async () => {
    const { loadChildren, take } = manualLoader()
    const { rerender } = render(
      <Harness nodes={[SALES]} loadChildren={loadChildren} initialExpanded={['db:sales']} />,
    )
    await waitFor(() => expect(loadChildren).toHaveBeenCalledTimes(1))
    const stale = take('db:sales')

    rerender(
      <Harness nodes={[{ ...SALES }]} loadChildren={loadChildren} initialExpanded={['db:sales']} />,
    )
    await waitFor(() => expect(loadChildren).toHaveBeenCalledTimes(2))
    const fresh = take('db:sales')

    await act(async () => {
      stale.resolve([{ id: 't:old', label: '古い子', hasChildren: false }])
    })
    expect(screen.queryByRole('button', { name: '古い子' })).not.toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('読み込み中')

    await act(async () => {
      fresh.resolve([{ id: 't:new', label: '新しい子', hasChildren: false }])
    })
    expect(await screen.findByRole('button', { name: '新しい子' })).toBeInTheDocument()
  })

  it('shows only the failure text under the node, keeps the rest usable and loads again on retry', async () => {
    const user = userEvent.setup()
    const { loadChildren, take } = manualLoader()
    render(
      <Harness nodes={[SALES, STOCK]} loadChildren={loadChildren} initialExpanded={['db:sales']} />,
    )
    await waitFor(() => expect(loadChildren).toHaveBeenCalledTimes(1))

    await act(async () => {
      take('db:sales').reject(new Error('SECRET-REASON 403 forbidden'))
    })
    expect(await screen.findByText('読み込めませんでした')).toBeInTheDocument()
    expect(document.body.textContent).not.toContain('SECRET-REASON')
    // ほかの節は使えるまま
    await user.click(screen.getByRole('button', { name: '在庫DB を開く' }))
    await waitFor(() => expect(loadChildren).toHaveBeenCalledWith('db:stock'))

    await user.click(screen.getByRole('button', { name: '再試行' }))
    expect(
      within(screen.getByRole('list', { name: '権限の設定の対象' })).getAllByRole('status'),
    ).toHaveLength(2)
    await act(async () => {
      take('db:sales').resolve([{ id: 't:orders', label: '受注', hasChildren: false }])
    })
    expect(await screen.findByRole('button', { name: '受注' })).toBeInTheDocument()
    expect(loadChildren.mock.calls.filter(([id]) => id === 'db:sales')).toHaveLength(2)
  })

  it('shows the empty text when a node has no children', async () => {
    render(
      <Harness nodes={[SALES]} loadChildren={tableLoader({})} initialExpanded={['db:sales']} />,
    )
    expect(await screen.findByText('子がありません')).toBeInTheDocument()
  })

  it('renders labels and badges containing markup characters as plain text', async () => {
    const hostile: SharedTreeNode = {
      id: 'x',
      label: '<script>alert(1)</script>',
      hasChildren: true,
      badges: [{ id: 'b', text: `A&B "q" 'q' <b>`, tone: 'warning' }],
    }
    render(<Harness nodes={[hostile]} loadChildren={tableLoader({})} />)

    expect(screen.getByRole('button', { name: '<script>alert(1)</script>' })).toBeInTheDocument()
    expect(screen.getByText(`A&B "q" 'q' <b>`)).toBeInTheDocument()
    expect(
      screen.getByRole('button', { name: '<script>alert(1)</script> を開く' }),
    ).toBeInTheDocument()
    expect(document.querySelectorAll('script, b')).toHaveLength(0)
  })

  it('opens and selects a deep node with Tab, Enter and Space only', async () => {
    const user = userEvent.setup()
    const onSelect = vi.fn()
    const loadChildren = tableLoader({
      'db:sales': [{ id: 's:public', label: 'public', hasChildren: true }],
      's:public': [{ id: 't:orders', label: '受注', hasChildren: true }],
      't:orders': [{ id: 'c:amount', label: '金額', hasChildren: false }],
    })
    render(<Harness nodes={[SALES]} loadChildren={loadChildren} onSelect={onSelect} />)

    await user.tab()
    expect(screen.getByRole('button', { name: '販売DB を開く' })).toHaveFocus()
    await user.keyboard('{Enter}')
    await screen.findByRole('button', { name: 'public' })
    await user.tab() // 販売DB の選ぶボタン
    await user.tab() // public の開閉のボタン
    expect(screen.getByRole('button', { name: 'public を開く' })).toHaveFocus()
    await user.keyboard(' ')
    await screen.findByRole('button', { name: '受注' })
    await user.tab() // public の選ぶボタン
    await user.tab() // 受注の開閉のボタン
    await user.keyboard('{Enter}')
    await screen.findByRole('button', { name: '金額' })
    await user.tab() // 受注の選ぶボタン
    await user.tab() // 金額の選ぶボタン
    expect(screen.getByRole('button', { name: '金額' })).toHaveFocus()
    await user.keyboard(' ')

    expect(onSelect).toHaveBeenCalledWith('c:amount')
    expect(screen.getByRole('button', { name: '金額' })).toHaveAttribute('aria-current', 'true')
  })

  it('shows only the texts handed over in labels', async () => {
    const { loadChildren, take } = manualLoader()
    render(
      <Harness
        nodes={[SALES, STOCK]}
        loadChildren={loadChildren}
        initialExpanded={['db:sales', 'db:stock']}
        treeLabels={englishLabels}
      />,
    )
    expect(await screen.findAllByText('Loading')).toHaveLength(2)
    await waitFor(() => expect(loadChildren).toHaveBeenCalledTimes(2))
    await act(async () => {
      take('db:sales').reject(new Error('x'))
      take('db:stock').resolve([])
    })

    expect(await screen.findByText('Could not load')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Retry' })).toBeInTheDocument()
    expect(screen.getByText('No children')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Collapse 販売DB' })).toBeInTheDocument()
    expect(document.body.textContent).not.toMatch(/読み込|再試行|子がありません/)
  })

  it('has no accessibility violations while open, loading and failed', async () => {
    const { loadChildren, take } = manualLoader()
    const { container } = render(
      <Harness
        nodes={[
          SALES,
          { ...STOCK, badges: [{ id: 'w', text: '⚠ 今の DSL に無い', tone: 'warning' }] },
          { id: 'db:hr', label: '人事DB', hasChildren: true },
          LEAF,
        ]}
        loadChildren={loadChildren}
        initialExpanded={['db:sales', 'db:stock', 'db:hr']}
        initialSelected="leaf"
      />,
    )
    await waitFor(() => expect(loadChildren).toHaveBeenCalledTimes(3))
    await act(async () => {
      take('db:sales').resolve([{ id: 't:orders', label: '受注', hasChildren: true }])
      take('db:stock').reject(new Error('x'))
    })
    await screen.findByText('読み込めませんでした')
    expect(screen.getByRole('status')).toHaveTextContent('読み込み中')

    expect(await axe(container)).toHaveNoViolations()
  })
})
