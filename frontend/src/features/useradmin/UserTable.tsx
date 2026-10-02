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
// 利用者の一覧の表（functional-spec.md の W1・W2・D1・D4・D13・D18・D19、frontend-components.md の 4節、
// AC1.1.1・AC1.1.12、NFR3.3・NFR7.1・NFR8.2）。make-you-chic-ui の Table（ページ送りを含む）に、列の描き方だけを渡す。
// - 行の値は決まった項目を1つずつ読んで描き、行を丸ごと広げて渡さない（応答に項目が加わっても出さない）。
// - 管理者の印・状態・ロック中・「あなた」は色に加えて Badge の文字で示し、無い印は「—」（aria-hidden）と見えない「なし」。
// - ロックの表示は応答の locked・lockedUntil のまま（画面の時計で判定しない）。日時は画面の言語と時間帯で書式にする。
// - Table の文言（labels）は画面の言語で渡し、既定の日本語に固定しない。getRowId は userId の文字列。
// - Table にページ送りを押せなくする口が無いため、busy（読み直しの間）の押下は onPageChange の側で捨てる（D13 の (2)）。
// - 長いメールアドレス・氏名は列の中で折り返す（切り詰めない）。表は包む要素の中で横に動く（RQ7 C）。
import { Badge, Table, type TableColumn, type TableLabels } from 'make-you-chic-ui'
import type { ReactNode } from 'react'
import { formatDateTime, type FormatLanguage } from '../../shared/format/formatDateTime'
import { PAGE_SIZE, type PagerDirection } from '../../shared/paging/paging'
import type { AdminUser } from './api/types'
import { formatLockedUntil } from './lockedUntil'
import { useUserAdminText } from './useUserAdminText'
import './UserTable.css'

export interface UserTableProps {
  /** 今のページの行 */
  rows: AdminUser[]
  /** 全件数 */
  total: number
  /** 今のページ */
  page: number
  /** ページ送り（押した向きを添える） */
  onPageChange: (page: number, direction: PagerDirection) => void
  /** 一覧の読み直しの間（ページ送りの押下を捨てる） */
  busy: boolean
  /** 表の名前（aria-label） */
  label: string
  /** 画面の言語（日時の書式） */
  language: FormatLanguage
  /** 日時の時間帯（省略すると端末の時間帯。テストで固定する） */
  timeZone?: string
  /** 今の時刻（解除の予定の時刻が今日かの判定。テストで固定する） */
  now: Date
  /** 行の「操作」を描く */
  renderActions: (row: AdminUser) => ReactNode
}

/** 無い印（「—」と、読み上げの「なし」） */
function NoMark({ label }: { label: string }) {
  return (
    <span className="useradmin-no-mark">
      <span aria-hidden="true">—</span>
      <span className="useradmin-visually-hidden">{label}</span>
    </span>
  )
}

/** 利用者の一覧の表 */
export function UserTable({
  rows,
  total,
  page,
  onPageChange,
  busy,
  label,
  language,
  timeZone,
  now,
  renderActions,
}: UserTableProps) {
  const t = useUserAdminText()
  const none = t('useradmin.none')

  const labels: TableLabels = {
    previousPage: t('useradmin.pager.prev'),
    nextPage: t('useradmin.pager.next'),
    selectAllRows: t('useradmin.table.selectAllRows'),
    selectRow: t('useradmin.table.selectRow'),
    emptyStatus: t('useradmin.table.emptyStatus'),
    toggleRowDetail: t('useradmin.table.toggleRowDetail'),
    pageStatus: (current, totalPages, totalCount) =>
      t('useradmin.pager.status', { page: current, pages: totalPages, total: totalCount }),
  }

  const columns: TableColumn<AdminUser>[] = [
    {
      key: 'email',
      header: t('useradmin.column.email'),
      render: (row) => <span className="useradmin-wrap">{row.email}</span>,
    },
    {
      key: 'name',
      header: t('useradmin.column.name'),
      render: (row) => (
        <span className="useradmin-name">
          <span className="useradmin-wrap">{row.displayName}</span>
          {row.self && <Badge variant="secondary">{t('useradmin.badge.you')}</Badge>}
        </span>
      ),
    },
    {
      key: 'admin',
      header: t('useradmin.column.admin'),
      render: (row) =>
        row.admin ? (
          <Badge variant="primary">{t('useradmin.badge.admin')}</Badge>
        ) : (
          <NoMark label={none} />
        ),
    },
    {
      key: 'status',
      header: t('useradmin.column.status'),
      render: (row) =>
        row.suspended ? (
          <Badge variant="danger">{t('useradmin.badge.suspended')}</Badge>
        ) : (
          <Badge variant="success">{t('useradmin.badge.active')}</Badge>
        ),
    },
    {
      key: 'lock',
      header: t('useradmin.column.lock'),
      render: (row) =>
        row.locked ? (
          <span className="useradmin-lock">
            <Badge variant="danger">{t('useradmin.badge.locked')}</Badge>
            {row.lockedUntil !== undefined && (
              <span className="useradmin-nowrap">
                {t('useradmin.lock.until', {
                  time: formatLockedUntil(row.lockedUntil, language, now, timeZone),
                })}
              </span>
            )}
          </span>
        ) : (
          <NoMark label={none} />
        ),
    },
    {
      key: 'registered',
      header: t('useradmin.column.registered'),
      render: (row) => (
        <span className="useradmin-nowrap">
          {formatDateTime(row.registeredAt, language, timeZone)}
        </span>
      ),
    },
    {
      key: 'actions',
      header: t('useradmin.column.actions'),
      render: (row) => renderActions(row),
    },
  ]

  function handlePageChange(next: number): void {
    if (busy) {
      return
    }
    onPageChange(next, next < page ? 'prev' : 'next')
  }

  return (
    <div className="useradmin-table" aria-busy={busy || undefined} data-testid="useradmin-table">
      <Table<AdminUser>
        columns={columns}
        data={rows}
        totalCount={total}
        page={page}
        pageSize={PAGE_SIZE}
        onPageChange={handlePageChange}
        getRowId={(row) => String(row.userId)}
        aria-label={label}
        labels={labels}
      />
    </div>
  )
}
