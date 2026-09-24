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
// メニューの木（mockups.md の 2、BR5.9、NFR1.19）。表示言語の表示名を示し、テーブルの物理名は訳さない。
// 既定では1段目だけを開き（1段目の節の子を示す）、それより下は開いた節だけを描く。
// ARIA の tree の役割は使わず、入れ子のリストと開閉のボタン（aria-expanded）で表す（design-system-mapping.md の 2節）。
import { Icon } from 'make-you-chic-ui'
import { useState } from 'react'
import { useDisplayLanguage } from '../../app/i18n/I18nProvider'
import type { MenuNode } from './api/types'
import { useDslText } from './useDslText'
import './DslCommon.css'
import './DslMenuTree.css'

export interface DslMenuTreeProps {
  nodes: MenuNode[]
}

/** 節の位置を表す鍵（例: `0.2.1`） */
function keyOf(parent: string, index: number): string {
  return parent === '' ? String(index) : `${parent}.${index}`
}

/** 既定で開く節（1段目で子を持つもの） */
function initialExpanded(nodes: MenuNode[]): ReadonlySet<string> {
  return new Set(nodes.flatMap((node, index) => (node.children.length > 0 ? [String(index)] : [])))
}

/** メニューの木 */
export function DslMenuTree({ nodes }: DslMenuTreeProps) {
  const t = useDslText()
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(() => initialExpanded(nodes))

  function toggle(key: string): void {
    setExpanded((current) => {
      const next = new Set(current)
      if (next.has(key)) {
        next.delete(key)
      } else {
        next.add(key)
      }
      return next
    })
  }

  if (nodes.length === 0) {
    return (
      <p className="dsl-muted" data-testid="dsl-menu-empty">
        {t('dsl.menu.empty')}
      </p>
    )
  }
  return (
    <div data-testid="dsl-menu-tree">
      <MenuList nodes={nodes} parentKey="" expanded={expanded} onToggle={toggle} />
    </div>
  )
}

interface MenuListProps {
  nodes: MenuNode[]
  parentKey: string
  expanded: ReadonlySet<string>
  onToggle: (key: string) => void
}

function MenuList({ nodes, parentKey, expanded, onToggle }: MenuListProps) {
  return (
    <ul className="dsl-menu-list">
      {nodes.map((node, index) => {
        const key = keyOf(parentKey, index)
        return (
          <MenuItem key={key} node={node} nodeKey={key} expanded={expanded} onToggle={onToggle} />
        )
      })}
    </ul>
  )
}

interface MenuItemProps {
  node: MenuNode
  nodeKey: string
  expanded: ReadonlySet<string>
  onToggle: (key: string) => void
}

function MenuItem({ node, nodeKey, expanded, onToggle }: MenuItemProps) {
  const t = useDslText()
  const language = useDisplayLanguage()
  const label = node.label[language] || '—'
  const isOpen = expanded.has(nodeKey)
  const text = (
    <>
      <span>{label}</span>
      {node.table !== null && <span className="dsl-menu-table">（{node.table}）</span>}
    </>
  )
  return (
    <li data-testid={`dsl-menu-node-${nodeKey}`}>
      {node.children.length > 0 ? (
        <button
          type="button"
          className="dsl-disclosure"
          aria-expanded={isOpen}
          aria-label={t(isOpen ? 'dsl.menu.collapse' : 'dsl.menu.expand', { label })}
          onClick={() => onToggle(nodeKey)}
          data-testid={`dsl-menu-toggle-${nodeKey}`}
        >
          <Icon name={isOpen ? 'chevron-up' : 'chevron-down'} size={14} />
          {text}
        </button>
      ) : (
        <span className="dsl-menu-leaf">{text}</span>
      )}
      {isOpen && node.children.length > 0 && (
        <MenuList
          nodes={node.children}
          parentKey={nodeKey}
          expanded={expanded}
          onToggle={onToggle}
        />
      )}
    </li>
  )
}
