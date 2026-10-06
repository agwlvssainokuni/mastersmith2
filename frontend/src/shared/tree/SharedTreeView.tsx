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
// 共有の木（契約 C2、frontend-components.md 2節、functional-spec.md W6・5.1、BR4.1〜BR4.12）。
// 開閉と選びは呼ぶ側が持ち、木は節ごとの子の読み込みの状態だけを持つ。nodes が新しい配列に替わったら、持っていた子を捨て、
// 開いている節を読み直す。読み込み中に替わったときや部品が外れた後に届いた古い結果は捨てる。
// 入れ子の ul と button で表し、tree の役割と矢印のキーの決まりは持たない（BR4.10）。
import { useCallback, useEffect, useMemo, useRef, useState } from 'react'
import { SharedTreeNodeItem, type SharedTreeContextValue } from './SharedTreeNodeItem'
import type { SharedTreeChildState, SharedTreeNode, SharedTreeViewProps } from './types'
import './SharedTreeView.css'

/** どの nodes に対する子の読み込みの状態か */
interface ChildStore {
  source: readonly SharedTreeNode[]
  states: ReadonlyMap<string, SharedTreeChildState>
}

const NO_STATES: ReadonlyMap<string, SharedTreeChildState> = new Map()

/** 開いていて描かれている節のうち、子をまだ読んでいないものを集める */
function nodesToLoad(
  nodes: readonly SharedTreeNode[],
  expandedIds: ReadonlySet<string>,
  states: ReadonlyMap<string, SharedTreeChildState>,
): string[] {
  const ids: string[] = []
  const visit = (list: readonly SharedTreeNode[]) => {
    for (const node of list) {
      if (!node.hasChildren || !expandedIds.has(node.id)) {
        continue
      }
      const state = states.get(node.id)
      if (state === undefined || state.status === 'notLoaded') {
        ids.push(node.id)
      } else if (state.status === 'loaded') {
        visit(state.children ?? [])
      }
    }
  }
  visit(nodes)
  return ids
}

/** 共有の木 */
export function SharedTreeView({
  nodes,
  loadChildren,
  selectedId,
  onSelect,
  expandedIds,
  onToggle,
  labels,
  ariaLabel,
}: SharedTreeViewProps) {
  const [store, setStore] = useState<ChildStore>(() => ({ source: nodes, states: NO_STATES }))
  const states = store.source === nodes ? store.states : NO_STATES

  /** 今の nodes（届いた結果が古いかを見分ける）と、部品が付いているかの印 */
  const latestNodes = useRef(nodes)
  const mounted = useRef(true)
  /** 読み込みを始めた節（同じ nodes の間に同じ節を二重に読まないため） */
  const requested = useRef<{ source: readonly SharedTreeNode[]; ids: Set<string> }>({
    source: nodes,
    ids: new Set(),
  })

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
    }
  }, [])

  const setChildState = useCallback(
    (source: readonly SharedTreeNode[], state: SharedTreeChildState) => {
      setStore((current) => {
        if (source !== latestNodes.current) {
          return current
        }
        const base = current.source === source ? current.states : NO_STATES
        const next = new Map(base)
        next.set(state.nodeId, state)
        return { source, states: next }
      })
    },
    [],
  )

  const load = useCallback(
    (source: readonly SharedTreeNode[], id: string) => {
      setChildState(source, { nodeId: id, status: 'loading' })
      Promise.resolve()
        .then(() => loadChildren(id))
        .then(
          (children) => {
            if (mounted.current && source === latestNodes.current) {
              setChildState(source, { nodeId: id, status: 'loaded', children })
            }
          },
          () => {
            // 拒否の理由（例外の文・応答の中身）は画面に出さない（BR4.6・NFR1.9）。
            if (mounted.current && source === latestNodes.current) {
              setChildState(source, { nodeId: id, status: 'failed' })
            }
          },
        )
    },
    [loadChildren, setChildState],
  )

  useEffect(() => {
    latestNodes.current = nodes
    if (requested.current.source !== nodes) {
      requested.current = { source: nodes, ids: new Set() }
    }
    for (const id of nodesToLoad(nodes, expandedIds, states)) {
      if (!requested.current.ids.has(id)) {
        requested.current.ids.add(id)
        load(nodes, id)
      }
    }
  }, [nodes, expandedIds, states, load])

  const tree: SharedTreeContextValue = useMemo(
    () => ({
      selectedId,
      expandedIds,
      labels,
      childStateOf: (id) => states.get(id),
      onSelect,
      onToggle,
      onRetry: (id) => load(nodes, id),
    }),
    [selectedId, expandedIds, labels, states, onSelect, onToggle, load, nodes],
  )

  return (
    <ul className="shared-tree" aria-label={ariaLabel} data-testid="shared-tree">
      {nodes.map((node) => (
        <SharedTreeNodeItem key={node.id} node={node} tree={tree} />
      ))}
    </ul>
  )
}
