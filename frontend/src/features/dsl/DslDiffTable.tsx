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
// 適用中との違い（interaction-spec.md の DslDiffTable、BR5.7、NFR1.19、AC3.1.3〜AC3.1.5）。
// 既定では違いのあるテーブルだけを示し、「すべて表示」で変わらないテーブルも示す。区分は文字のバッジで示す。
// カラムの違いは、行を開いたときだけ描く（開いていない行のカラムは描かない）。
// make-you-chic-ui の Table には行の開閉が無く、ページ送りの文言が日本語に固定されているため、同じ見た目の
// 素の表（mycui-table の見た目）で作る（design-system-mapping.md の 2節）。
// 書式の版 2（U2 dsl-v2 の BR7.5・BR5.2、frontend-components.md の F5）では、スキーマごとに tbody を分け、先頭に見出しの行
// （th scope="rowgroup"、全列、スキーマ名・表示名・区分）を置く。開閉はテーブルの行だけで、鍵は「スキーマ名/テーブル名」。
import { Badge, Icon, Switch, type BadgeProps } from 'make-you-chic-ui'
import { useState } from 'react'
import { useDisplayLanguage } from '../../app/i18n/I18nProvider'
import type { ColumnChange, DslDiff, SchemaDiff, TableChange, TableDiff } from './api/types'
import { countTableChanges } from './diffCounts'
import { useDslText } from './useDslText'
import './DslCommon.css'
import './DslDiffTable.css'

export interface DslDiffTableProps {
  diff: DslDiff
}

const BADGE_VARIANT: Record<TableChange, NonNullable<BadgeProps['variant']>> = {
  ADDED: 'success',
  REMOVED: 'danger',
  CHANGED: 'primary',
  UNCHANGED: 'secondary',
}

function ChangeBadge({ change }: { change: TableChange | ColumnChange }) {
  const t = useDslText()
  return <Badge variant={BADGE_VARIANT[change]}>{t(`dsl.change.${change}`)}</Badge>
}

/** カラムの違いの数を `+1 −1 ~1` の形で示す（無ければ —）。 */
function columnSummary(table: TableDiff): string {
  const added = table.columns.filter((c) => c.change === 'ADDED').length
  const removed = table.columns.filter((c) => c.change === 'REMOVED').length
  const changed = table.columns.filter((c) => c.change === 'CHANGED').length
  const parts = [
    added > 0 ? `+${added}` : null,
    removed > 0 ? `−${removed}` : null,
    changed > 0 ? `~${changed}` : null,
  ].filter((p): p is string => p !== null)
  return parts.length > 0 ? parts.join(' ') : '—'
}

function ColumnTable({ table, rowKey }: { table: TableDiff; rowKey: string }) {
  const t = useDslText()
  return (
    <table
      className="mycui-table dsl-diff-columns"
      aria-label={t('dsl.diff.columnTableLabel', { table: table.name })}
      data-testid={`dsl-diff-columns-${rowKey}`}
    >
      <thead>
        <tr>
          <th scope="col">{t('dsl.diff.column')}</th>
          <th scope="col">{t('dsl.diff.change')}</th>
          <th scope="col">{t('dsl.diff.changedItems')}</th>
        </tr>
      </thead>
      <tbody>
        {table.columns.map((column) => (
          <tr key={column.name}>
            <td>{column.name}</td>
            <td>
              <ChangeBadge change={column.change} />
            </td>
            <td>{column.changedItems.length > 0 ? column.changedItems.join('・') : '—'}</td>
          </tr>
        ))}
      </tbody>
    </table>
  )
}

/** 適用中との違い */
export function DslDiffTable({ diff }: DslDiffTableProps) {
  const t = useDslText()
  const [showAll, setShowAll] = useState(false)
  const [expanded, setExpanded] = useState<ReadonlySet<string>>(() => new Set())
  const counts = countTableChanges(diff)
  const groups = diff.schemas
    .map((schema) => ({
      schema,
      tables: showAll
        ? schema.tables
        : schema.tables.filter((table) => table.change !== 'UNCHANGED'),
    }))
    .filter((group) => showAll || group.schema.change !== 'UNCHANGED' || group.tables.length > 0)

  function toggle(name: string): void {
    setExpanded((current) => {
      const next = new Set(current)
      if (next.has(name)) {
        next.delete(name)
      } else {
        next.add(name)
      }
      return next
    })
  }

  return (
    <div className="dsl-diff" data-testid="dsl-diff-table">
      {!diff.appliedExists && (
        <p className="dsl-muted" data-testid="dsl-diff-no-applied">
          {t('dsl.diff.noApplied')}
        </p>
      )}
      <div className="dsl-diff-toolbar">
        <p className="dsl-diff-counts" data-testid="dsl-diff-counts">
          {t('dsl.diff.counts', { ...counts })}
        </p>
        <Switch
          label={t('dsl.diff.showAll')}
          checked={showAll}
          onChange={setShowAll}
          data-testid="dsl-diff-show-all"
        />
      </div>
      {groups.length === 0 ? (
        <p className="dsl-muted" data-testid="dsl-diff-none">
          {t('dsl.diff.none')}
        </p>
      ) : (
        <div className="mycui-table-wrapper">
          <table className="mycui-table" aria-label={t('dsl.diff.tableLabel')}>
            <thead>
              <tr>
                <th scope="col">{t('dsl.diff.table')}</th>
                <th scope="col">{t('dsl.diff.change')}</th>
                <th scope="col">{t('dsl.diff.columnChanges')}</th>
              </tr>
            </thead>
            {groups.map(({ schema, tables }) => (
              <tbody key={schema.name} data-testid={`dsl-diff-schema-${schema.name}`}>
                <SchemaHeading schema={schema} />
                {tables.map((table) => {
                  const rowKey = `${schema.name}/${table.name}`
                  const isOpen = expanded.has(rowKey)
                  const canOpen = table.columns.length > 0
                  return (
                    <DiffRow
                      key={rowKey}
                      rowKey={rowKey}
                      table={table}
                      isOpen={isOpen && canOpen}
                      canOpen={canOpen}
                      onToggle={() => toggle(rowKey)}
                    />
                  )
                })}
              </tbody>
            ))}
          </table>
        </div>
      )}
    </div>
  )
}

/** スキーマの見出しの行（全列にまたがる rowgroup の見出し） */
function SchemaHeading({ schema }: { schema: SchemaDiff }) {
  const t = useDslText()
  const language = useDisplayLanguage()
  return (
    <tr className="dsl-diff-schema-row">
      <th
        scope="rowgroup"
        colSpan={3}
        className="dsl-diff-schema-heading"
        data-testid={`dsl-diff-schema-heading-${schema.name}`}
      >
        <span className="dsl-diff-schema-heading-inner">
          <span className="dsl-diff-schema-name">
            {t('dsl.diff.schemaHeading', {
              name: schema.name,
              label: schema.label[language] || '—',
            })}
          </span>
          <ChangeBadge change={schema.change} />
        </span>
      </th>
    </tr>
  )
}

interface DiffRowProps {
  rowKey: string
  table: TableDiff
  isOpen: boolean
  canOpen: boolean
  onToggle: () => void
}

function DiffRow({ rowKey, table, isOpen, canOpen, onToggle }: DiffRowProps) {
  const t = useDslText()
  return (
    <>
      <tr data-testid={`dsl-diff-row-${rowKey}`}>
        <th scope="row" className="dsl-diff-name">
          {canOpen ? (
            <button
              type="button"
              className="dsl-disclosure"
              aria-expanded={isOpen}
              aria-label={t(isOpen ? 'dsl.diff.collapse' : 'dsl.diff.expand', {
                table: table.name,
              })}
              onClick={onToggle}
              data-testid={`dsl-diff-toggle-${rowKey}`}
            >
              <Icon name={isOpen ? 'chevron-up' : 'chevron-down'} size={14} />
              <span>{table.name}</span>
            </button>
          ) : (
            <span className="dsl-diff-plain-name">{table.name}</span>
          )}
        </th>
        <td>
          <ChangeBadge change={table.change} />
        </td>
        <td>{columnSummary(table)}</td>
      </tr>
      {isOpen && (
        <tr className="dsl-diff-detail-row">
          <td colSpan={3}>
            <ColumnTable table={table} rowKey={rowKey} />
          </td>
        </tr>
      )}
    </>
  )
}
