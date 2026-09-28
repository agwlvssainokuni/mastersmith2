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
// 招待中の人の一覧（frontend-components.md の 1節・4節、functional-spec.md の W1・W2・W7〜W9、D5〜D7・D13、
// logical-components.md の 6.2）。一覧の見出し（h2、tabIndex=-1）をいつも描き、読み込み中・空・読めなかった・表を出す。
// 表は make-you-chic-ui の Table（ページ送りを含む）。Table は caption・行の class・包む要素の名前を持たないため、
// 表の名前は aria-label と見える見出しで、目立たせた行は列の render で描く印と機能の CSS の :has() で示す。
// 行の値は決まった項目を1つずつ読んで描き、行を丸ごと広げて渡さない（応答に項目が加わっても出さない、NFR1.1）。
// 矢印のキーの処理は作らない（Tab で行のボタンとページ送りに届く。機能設計の承認の場の U5 R-01）。
// フォーカスは、読み込み中でない描画の確定の後に、求められた行き先（focusTarget.ts で決める）へ当てる。
// Table の中の class・data-testid を探さず、render で描く要素への参照で当てる。
import { Badge, Button, Table, type TableColumn, type TableLabels } from 'make-you-chic-ui'
import { useEffect, useId, useRef, type FocusEvent } from 'react'
import { LANGUAGE_NAMES } from '../../app/display-settings/displaySettingsTypes'
import { formatDateTime, type FormatLanguage } from '../../shared/format/formatDateTime'
import type { Invitation, InvitationPage } from './api/types'
import { resolveFocusTarget, type FocusTarget } from './focusTarget'
import { PAGE_SIZE, pageRange, type PagerDirection } from './paging'
import type { InvitationLoadState } from './useInvitationAdmin'
import { useInvitationText } from './useInvitationText'
import './InvitationList.css'

export interface InvitationListProps {
  /** 最後に使った一覧の応答（まだ無ければ null） */
  list: InvitationPage | null
  /** 今のページ（読んでいるページ） */
  page: number
  loadState: InvitationLoadState
  /** 読めなかったときの一般の文言の鍵 */
  loadFailureKey: string | null
  /** 招待を使えるか（使えないと「送り直す」を押せない） */
  invitationEnabled: boolean
  /** 行の処理中の invitationId */
  resendingIds: ReadonlySet<number>
  /** 目立たせた行の invitationId */
  highlightedId: number | null
  /** 次に一覧を描いた後に当てるフォーカス */
  focusTarget: FocusTarget | null
  /** 画面の言語（日時の書式に使う） */
  language: FormatLanguage
  /** 日時の時差（省略すると端末の時差。テストで固定する） */
  timeZone?: string
  onResend: (invitation: Invitation) => void
  onRevoke: (invitation: Invitation) => void
  onRetry: () => void
  onPageChange: (page: number, direction: PagerDirection) => void
  onFocusApplied: () => void
}

/**
 * フォーカスを受けた表の中の要素を、表の包む要素（横に動く領域）の中で見える位置へ動かす。
 * ブラウザは一部が見えている要素へのフォーカスでは動かさないため（375px の幅で、Tab で届いた「取り消す」が表示の幅の外に
 * 残った。U5 の計画の Step 16）、ここで近い側へ動かす。矢印のキーの処理は作らない。
 */
function revealFocused(event: FocusEvent<HTMLElement>): void {
  const target = event.target
  if (typeof target.scrollIntoView === 'function') {
    target.scrollIntoView({ block: 'nearest', inline: 'nearest' })
  }
}

/** 招待中の人の一覧 */
export function InvitationList({
  list,
  page,
  loadState,
  loadFailureKey,
  invitationEnabled,
  resendingIds,
  highlightedId,
  focusTarget,
  language,
  timeZone,
  onResend,
  onRevoke,
  onRetry,
  onPageChange,
  onFocusApplied,
}: InvitationListProps) {
  const t = useInvitationText()
  const headingId = useId()
  const headingRef = useRef<HTMLHeadingElement>(null)
  const emptyRef = useRef<HTMLParagraphElement>(null)
  const resendRefs = useRef(new Map<number, HTMLButtonElement>())
  const emailRefs = useRef(new Map<number, HTMLSpanElement>())

  useEffect(() => {
    if (focusTarget === null || loadState === 'loading') {
      return
    }
    if (loadState === 'failed' || list === null) {
      headingRef.current?.focus()
      onFocusApplied()
      return
    }
    const place = resolveFocusTarget(focusTarget, {
      rowIds: list.items.map((item) => item.invitationId),
      total: list.total,
      invitationEnabled,
      resendingIds,
    })
    const element =
      place.kind === 'resend'
        ? resendRefs.current.get(place.invitationId)
        : place.kind === 'email'
          ? emailRefs.current.get(place.invitationId)
          : place.kind === 'empty'
            ? emptyRef.current
            : headingRef.current
    ;(element ?? headingRef.current)?.focus()
    onFocusApplied()
  }, [focusTarget, loadState, list, invitationEnabled, resendingIds, onFocusApplied])

  const labels: TableLabels = {
    previousPage: t('invitation.action.prev'),
    nextPage: t('invitation.action.next'),
    selectAllRows: t('invitation.table.selectAllRows'),
    selectRow: t('invitation.table.selectRow'),
    emptyStatus: t('invitation.table.emptyStatus'),
    toggleRowDetail: t('invitation.table.toggleRowDetail'),
    pageStatus: (current, totalPages, totalCount) => {
      const { from, to } = pageRange(current, totalCount)
      return t('invitation.pager.status', {
        from,
        to,
        total: totalCount,
        page: current,
        pages: totalPages,
      })
    },
  }

  function registerRef<E extends HTMLElement>(map: Map<number, E>, id: number) {
    return (element: E | null) => {
      if (element) {
        map.set(id, element)
      } else {
        map.delete(id)
      }
    }
  }

  const columns: TableColumn<Invitation>[] = [
    {
      key: 'email',
      header: t('invitation.column.email'),
      render: (row) => (
        <span
          className="invitation-email"
          tabIndex={-1}
          onFocus={revealFocused}
          ref={registerRef(emailRefs.current, row.invitationId)}
          data-testid={`invitation-row-email-${row.invitationId}`}
        >
          {row.invitationId === highlightedId && (
            <span className="invitation-highlight-mark" data-invitation-highlighted="true" />
          )}
          {row.email}
        </span>
      ),
    },
    {
      key: 'language',
      header: t('invitation.column.language'),
      render: (row) => <span lang={row.language}>{LANGUAGE_NAMES[row.language]}</span>,
    },
    {
      key: 'invitedBy',
      header: t('invitation.column.invitedBy'),
      render: (row) => row.invitedBy || t('invitation.invitedBy.unknown'),
    },
    {
      key: 'invitedAt',
      header: t('invitation.column.invitedAt'),
      render: (row) => (
        <span className="invitation-nowrap">
          {formatDateTime(row.invitedAt, language, timeZone)}
        </span>
      ),
    },
    {
      key: 'expiresAt',
      header: t('invitation.column.expiresAt'),
      render: (row) => (
        <span className="invitation-nowrap">
          {formatDateTime(row.expiresAt, language, timeZone)}
        </span>
      ),
    },
    {
      key: 'sendResult',
      header: t('invitation.column.sendResult'),
      render: (row) => (
        <Badge variant={row.sendResult === 'SENT' ? 'secondary' : 'danger'}>
          {t(`invitation.sendResult.${row.sendResult}`)}
        </Badge>
      ),
    },
    {
      key: 'status',
      header: t('invitation.column.status'),
      render: (row) => (
        <Badge variant={row.expired ? 'danger' : 'secondary'}>
          {t(row.expired ? 'invitation.status.expired' : 'invitation.status.valid')}
        </Badge>
      ),
    },
    {
      key: 'actions',
      header: t('invitation.column.actions'),
      render: (row) => {
        const resending = resendingIds.has(row.invitationId)
        const values = { email: row.email }
        return (
          <span className="invitation-row-actions" onFocus={revealFocused}>
            <Button
              variant="secondary"
              size="sm"
              ref={registerRef(resendRefs.current, row.invitationId)}
              loading={resending}
              disabled={!invitationEnabled}
              aria-label={t(
                resending ? 'invitation.row.resendingName' : 'invitation.row.resendName',
                values,
              )}
              onClick={() => onResend(row)}
              data-testid={`invitation-row-resend-${row.invitationId}`}
            >
              {t(resending ? 'invitation.action.sending' : 'invitation.action.resend')}
            </Button>
            <Button
              variant="danger"
              size="sm"
              disabled={resending}
              aria-label={t('invitation.row.revokeName', values)}
              onClick={() => onRevoke(row)}
              data-testid={`invitation-row-revoke-${row.invitationId}`}
            >
              {t('invitation.action.revoke')}
            </Button>
          </span>
        )
      },
    },
  ]

  const loading = loadState === 'loading'
  // 一度表示した表（1件以上）は、読み直しの間も描いたまま行を空にする（ページ送りのボタンのフォーカスを保つ）。
  const showTable =
    loadState !== 'failed' && list !== null && list.total > 0 && (loading || list.items.length > 0)

  return (
    <section className="invitation-list" aria-labelledby={headingId} data-testid="invitation-list">
      <h2
        id={headingId}
        ref={headingRef}
        tabIndex={-1}
        className="invitation-list-heading"
        data-testid="invitation-list-heading"
      >
        {t('invitation.list.caption')}
      </h2>
      {loading && (
        <p role="status" className="invitation-muted" data-testid="invitation-list-loading">
          {t('invitation.list.loading')}
        </p>
      )}
      {loadState === 'failed' && (
        <div className="invitation-list-failed">
          <div role="alert" data-testid="invitation-list-failed">
            <p className="invitation-list-failed-title">{t('invitation.list.failed')}</p>
            {loadFailureKey !== null && <p className="invitation-muted">{t(loadFailureKey)}</p>}
          </div>
          <div>
            <Button variant="secondary" onClick={onRetry} data-testid="invitation-list-retry">
              {t('invitation.action.retry')}
            </Button>
          </div>
        </div>
      )}
      {loadState === 'loaded' && list !== null && list.total <= 0 && (
        <p
          ref={emptyRef}
          tabIndex={-1}
          className="invitation-list-empty"
          data-testid="invitation-list-empty"
        >
          {t('invitation.list.empty')}
        </p>
      )}
      {showTable && (
        <Table<Invitation>
          columns={columns}
          data={loading ? [] : list.items}
          totalCount={list.total}
          page={page}
          pageSize={PAGE_SIZE}
          onPageChange={(next) => onPageChange(next, next < page ? 'prev' : 'next')}
          getRowId={(row) => String(row.invitationId)}
          aria-label={t('invitation.list.caption')}
          labels={labels}
        />
      )}
    </section>
  )
}
