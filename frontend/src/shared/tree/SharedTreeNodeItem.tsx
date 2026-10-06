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
// 共有の木の1つの節（frontend-components.md 2.3・2.5、BR4.1〜BR4.3・BR4.6・BR4.8・BR4.10〜BR4.12）。
// 開閉のボタンと選ぶボタンを分け、表示名と印は文字の差し込みだけで描く（HTML として描かない）。
import { useRef } from 'react'
import type { SharedTreeChildState, SharedTreeLabels, SharedTreeNode } from './types'

/** 節の描画に要る、木の全体で共通の値 */
export interface SharedTreeContextValue {
  selectedId: string | null
  expandedIds: ReadonlySet<string>
  labels: SharedTreeLabels
  childStateOf: (id: string) => SharedTreeChildState | undefined
  onSelect: (id: string) => void
  onToggle: (id: string, expanded: boolean) => void
  onRetry: (id: string) => void
}

export interface SharedTreeNodeItemProps {
  node: SharedTreeNode
  tree: SharedTreeContextValue
}

/** 開いた節の子の区画（子の一覧・読み込み中・失敗と再試行・子が無い のどれか1つ） */
function ChildArea({ node, tree }: SharedTreeNodeItemProps) {
  const areaRef = useRef<HTMLDivElement>(null)
  const state = tree.childStateOf(node.id)
  const { labels } = tree

  if (state?.status === 'loaded') {
    const children = state.children ?? []
    if (children.length === 0) {
      return <p className="shared-tree-message">{labels.empty}</p>
    }
    return (
      <ul className="shared-tree-group">
        {children.map((child) => (
          <SharedTreeNodeItem key={child.id} node={child} tree={tree} />
        ))}
      </ul>
    )
  }

  // 再試行の後もフォーカスをこの場所（読み込み中の表示）に残すため、区画そのものにフォーカスを当てられるようにする。
  return (
    <div className="shared-tree-children" ref={areaRef} tabIndex={-1}>
      {state?.status === 'failed' ? (
        <p className="shared-tree-message shared-tree-message-failed">
          {labels.loadFailed}{' '}
          <button
            type="button"
            className="shared-tree-retry"
            data-testid="shared-tree-retry"
            onClick={() => {
              areaRef.current?.focus()
              tree.onRetry(node.id)
            }}
          >
            {labels.retry}
          </button>
        </p>
      ) : (
        <p className="shared-tree-message" role="status">
          {labels.loading}
        </p>
      )}
    </div>
  )
}

/** 木の1つの節 */
export function SharedTreeNodeItem({ node, tree }: SharedTreeNodeItemProps) {
  const expanded = node.hasChildren && tree.expandedIds.has(node.id)
  const selected = tree.selectedId === node.id
  return (
    <li className="shared-tree-item">
      <div className="shared-tree-row">
        {node.hasChildren ? (
          <button
            type="button"
            className="shared-tree-toggle"
            data-testid="shared-tree-toggle"
            aria-expanded={expanded}
            aria-label={
              expanded ? tree.labels.collapse(node.label) : tree.labels.expand(node.label)
            }
            onClick={() => tree.onToggle(node.id, !expanded)}
          >
            <span aria-hidden="true">{expanded ? '▾' : '▸'}</span>
          </button>
        ) : (
          <span className="shared-tree-toggle-spacer" aria-hidden="true" />
        )}
        <button
          type="button"
          className="shared-tree-select"
          data-testid="shared-tree-select"
          aria-current={selected ? 'true' : undefined}
          onClick={() => tree.onSelect(node.id)}
        >
          {node.label}
        </button>
        {(node.badges ?? []).map((badge) => (
          <span
            key={badge.id}
            className={`shared-tree-badge shared-tree-badge-${badge.tone ?? 'neutral'}`}
          >
            {badge.text}
          </span>
        ))}
      </div>
      {expanded ? <ChildArea node={node} tree={tree} /> : null}
    </li>
  )
}
