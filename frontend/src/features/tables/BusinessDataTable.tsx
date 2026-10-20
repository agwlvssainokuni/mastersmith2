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
// 業務データの一覧の表（Intent 261016-business-data-views の U3 の L7、D7〜D10・D14〜D17、frontend-components.md の 4節、
// performance-design.md の 1節、U3-NFR1.7・2.10・2.11・4.20・4.21・5.6・5.9）。
// - make-you-chic-ui の Table に、列の定義（定義ごとに useMemo で1回）と行（ページの中の番号つき）を渡す。React.memo にする。
// - Table の列の鍵・getRowId には、ページの中の番号（c<n>・r<n>）だけを渡す。Table は鍵を data-testid に使うため、
//   カラム名・主キー・行の値を DOM の id・data-testid に入れない（D8・U4 の R-02）。
// - セルは書式の後の文字（valueFormat）を素の span に入れ、Tooltip で包む。省略は CSS の1行の省略だけで行い、DOM の文字は
//   切らない（D17。読み上げは DOM の文字で値の全体を読むため、セルに aria-label を付けない）。切ったカラムは印を付ける。
// - 行のリンク（rowLabelColumns の最初のカラムのセル）: 見える文字は rowLinkText、名前は「詳細: <表示名> <行のラベル>」。
//   先（href）は道を作る1か所の関数の結果だけ（D16）。押したら onOpenRow（印を作って詳細へ移る）。data-testid は
//   tables-row-link-r<n>。
// - 並べ替えは sortable のカラムの見出しだけをボタンにし、昇順 → 降順 → 既定の並びの順に変わる（Table の nextSortState。D10）。
// - Table にページ送りを押せなくする口が無いため、読み直しの間（busy）の押下は捨てる（D14）。
// - 値と表示名は React の文字として描き、HTML として描かない。
import {
  Table,
  Tooltip,
  type SortState,
  type TableColumn,
  type TableLabels,
} from 'make-you-chic-ui'
import { memo, useMemo, type MouseEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { DisplayLanguage } from '../../app/i18n/resolveLanguage'
import { NullValue } from '../../shared/null-value/NullValue'
import type { PageSize, RowItem, RowPage, SortKey, TableDefinition } from './businessDataTypes'
import { MAX_PAGE } from './businessDataParse'
import { RowActionsMenu, rowActionItems } from './RowActionsMenu'
import { rowLabelParts, rowLabelText, rowLinkColumn, rowLinkText } from './rowLabel'
import { localizedName } from './tablesText'
import { formatCell, type ValueTexts } from './valueFormat'
import './tablesCommon.css'
import './BusinessDataTable.css'

/** 表の1行（ページの中の番号と、行のラベルを先に求めたもの） */
interface TableRow {
  index: number
  item: RowItem
  label: string
  linkText: string
}

export interface BusinessDataTableProps {
  definition: TableDefinition
  page: RowPage
  /** 今の並べ替え（null はサーバーの既定の並び） */
  sort: SortKey | null
  size: PageSize
  /** 読み直しの間（ページ送り・並べ替えの押下を捨てる） */
  busy: boolean
  language: DisplayLanguage
  /** テーブルの表示名 */
  tableName: string
  texts: ValueTexts
  /** 行の詳細の道（行のリンクの href） */
  detailHref: string
  onSortChange: (sort: SortKey | null) => void
  onPageChange: (page: number) => void
  /** 行を開く（index はページの中の番号） */
  onOpenRow: (index: number, via: 'link' | 'actions') => void
  /** 行の操作の「更新」「削除」を選んだ（B6。無ければ「詳細」と同じく onOpenRow） */
  onRowAction?: (index: number, action: 'update' | 'delete', label: string) => void
}

/** リンクの押下を横取りしてよいか（新しいタブで開く操作は横取りしない） */
function isPlainClick(event: MouseEvent<HTMLAnchorElement>): boolean {
  return event.button === 0 && !event.metaKey && !event.ctrlKey && !event.shiftKey && !event.altKey
}

/** 業務データの一覧の表 */
export const BusinessDataTable = memo(function BusinessDataTable({
  definition,
  page,
  sort,
  size,
  busy,
  language,
  tableName,
  texts,
  detailHref,
  onSortChange,
  onPageChange,
  onOpenRow,
  onRowAction,
}: BusinessDataTableProps) {
  const { t } = useTranslation()

  const rows = useMemo<TableRow[]>(
    () =>
      page.items.map((item, index) => {
        const parts = rowLabelParts(definition, item.values, texts)
        return {
          index,
          item,
          label: rowLabelText(parts, texts),
          linkText: rowLinkText(parts, texts),
        }
      }),
    [page, definition, texts],
  )

  const columns = useMemo<TableColumn<TableRow>[]>(() => {
    const linkColumn = rowLinkColumn(definition)
    const sortable = new Set(definition.sortableColumns)
    const listed: TableColumn<TableRow>[] = definition.listColumns.map((column, columnIndex) => ({
      key: `c${columnIndex}`,
      header: localizedName(column.displayName, language, column.column),
      sortable: sortable.has(column.column),
      width: column.width ?? undefined,
      render: (row) => {
        if (column.column === linkColumn && row.item.key !== null) {
          return (
            <Tooltip content={row.linkText}>
              <a
                className="tables-row-link tables-cell-text"
                href={detailHref}
                data-testid={`tables-row-link-r${row.index}`}
                aria-label={t('tables.detailLink', { table: tableName, label: row.label })}
                onClick={(event) => {
                  if (isPlainClick(event)) {
                    event.preventDefault()
                    onOpenRow(row.index, 'link')
                  }
                }}
              >
                {row.linkText}
              </a>
            </Tooltip>
          )
        }
        const value = Object.hasOwn(row.item.values, column.column)
          ? row.item.values[column.column]
          : undefined
        const formatted = formatCell(column, value, texts)
        const truncated = row.item.truncatedColumns.includes(column.column)
        let content
        if (formatted.kind === 'null') {
          content = <NullValue text={texts.null} spokenText={t('tables.value.nullSpoken')} />
        } else if (formatted.kind === 'masked') {
          content = (
            <span className="tables-masked">
              <span aria-hidden="true">{formatted.text}</span>
              <span className="tables-visually-hidden">{t('tables.value.maskedSpoken')}</span>
            </span>
          )
        } else if (formatted.text === '') {
          content = <span className="tables-cell-text" />
        } else {
          content = (
            <Tooltip content={formatted.text}>
              <span className="tables-cell-text">{formatted.text}</span>
            </Tooltip>
          )
        }
        return (
          <span className="tables-cell">
            {content}
            {truncated && (
              <span className="tables-cell-mark">{t('tables.value.truncatedMark')}</span>
            )}
          </span>
        )
      },
    }))
    if (rowActionItems(definition.operations).length > 0) {
      listed.push({
        key: 'actions',
        header: t('tables.table.actionsColumn'),
        render: (row) =>
          row.item.key === null ? null : (
            <RowActionsMenu
              index={row.index}
              rowLabel={row.label}
              operations={definition.operations}
              busy={busy}
              onSelect={(action) => {
                if (action === 'detail' || onRowAction === undefined) {
                  onOpenRow(row.index, 'actions')
                } else {
                  onRowAction(row.index, action, row.label)
                }
              }}
            />
          ),
      })
    }
    return listed
  }, [definition, language, t, texts, tableName, detailHref, busy, onOpenRow, onRowAction])

  const labels: TableLabels = useMemo(
    () => ({
      previousPage: t('tables.table.prev'),
      nextPage: t('tables.table.next'),
      selectAllRows: t('tables.table.selectAllRows'),
      selectRow: t('tables.table.selectRow'),
      emptyStatus: t('tables.table.emptyStatus'),
      toggleRowDetail: t('tables.table.toggleRowDetail'),
      pageStatus: (current, pages, total) =>
        t('tables.table.pageStatus', { page: current, pages, total }),
    }),
    [t],
  )

  const sortIndex =
    sort === null ? -1 : definition.listColumns.findIndex((c) => c.column === sort.column)
  const sortState: SortState | null =
    sort === null || sortIndex < 0
      ? null
      : { key: `c${sortIndex}`, direction: sort.direction === 'ASC' ? 'asc' : 'desc' }

  function handleSortChange(next: SortState | null): void {
    if (busy) {
      return
    }
    if (next === null || next.direction === null) {
      onSortChange(null)
      return
    }
    const column = definition.listColumns[Number(next.key.slice(1))]
    if (column !== undefined) {
      onSortChange({ column: column.column, direction: next.direction === 'asc' ? 'ASC' : 'DESC' })
    }
  }

  function handlePageChange(next: number): void {
    if (busy || next < 1 || next > MAX_PAGE) {
      return
    }
    onPageChange(next)
  }

  return (
    <Table<TableRow>
      columns={columns}
      data={rows}
      totalCount={page.total}
      page={page.page}
      pageSize={size}
      onPageChange={handlePageChange}
      sortState={sortState}
      onSortChange={handleSortChange}
      getRowId={(row) => `r${row.index}`}
      aria-label={t('tables.list.regionLabel', { name: tableName })}
      labels={labels}
    />
  )
})
