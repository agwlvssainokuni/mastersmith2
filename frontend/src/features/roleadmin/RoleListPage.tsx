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
// S3 ロールの一覧（Intent 261004-role-menu の U6、functional-spec.md の 3.1・4.1、D5〜D9・D28〜D31、frontend-components.md
// の 2.2・8節）。見出し（h1）と「ロールを作る」、画面の上の知らせ（Alert。成功は role="status"、拒否は role="alert"
// で読み上げる。1つだけ）、読み込みの失敗（「もう一度」）、
// 一覧の見出し（h2、tabIndex=-1、フォーカスの行き先）、読み込みの状態（role="status"）、表を並べる。
// 状態と操作は useRoleList が持ち、この部品は値と操作を渡すだけ。403 は骨組みが受け持つため forbidden の状態を持たない。
// 名前・数は React の文字として描く（HTML として解釈しない）。表は make-you-chic-ui の Table に画面の言語の labels を渡す。
import { Alert, Button, Table, type TableColumn, type TableLabels } from 'make-you-chic-ui'
import { useEffect, useId, useRef } from 'react'
import { Link } from 'react-router'
import { useAdminForbidden } from '../../app/admin-forbidden/AdminForbiddenProvider'
import { PAGE_SIZE } from '../../shared/paging/paging'
import { roleAdminApi, type RoleAdminApi } from './api/roleAdminApi'
import type { RoleRow } from './api/types'
import { DeleteConfirmDialog } from './DeleteConfirmDialog'
import { roleAssignmentsPath } from './paths'
import { RoleNameDialog } from './RoleNameDialog'
import { RoleRowActions } from './RoleRowActions'
import { useRoleAdminText } from './useRoleAdminText'
import { useRoleList } from './useRoleList'
import './RoleListPage.css'

export interface RoleListPageProps {
  /** API の関数の集まり（テストで差し替える） */
  api?: RoleAdminApi
}

/** S3 ロールの一覧 */
export function RoleListPage({ api = roleAdminApi }: RoleListPageProps) {
  const t = useRoleAdminText()
  const reportForbidden = useAdminForbidden()
  const state = useRoleList({ api, t, reportForbidden })
  const headingId = useId()
  const headingRef = useRef<HTMLHeadingElement>(null)
  const retryRef = useRef<HTMLButtonElement>(null)
  const createRef = useRef<HTMLButtonElement>(null)
  const actionRefs = useRef(new Map<number, HTMLSpanElement>())
  // Modal を閉じた後にフォーカスを戻す先（「ロールを作る」か、開いた元の行の「操作」）。
  const dialogReturnRef = useRef<HTMLElement | null>(null)
  const { view, list, focusTarget, consumeFocus, dialog } = state

  useEffect(() => {
    if (focusTarget === null || view === 'loading' || view === 'reloading') {
      return
    }
    if (focusTarget.kind === 'row') {
      const button = actionRefs.current.get(focusTarget.roleId)?.querySelector('button')
      ;(button ?? headingRef.current)?.focus()
    } else if (focusTarget.kind === 'retry') {
      ;(retryRef.current ?? headingRef.current)?.focus()
    } else {
      headingRef.current?.focus()
    }
    consumeFocus()
  }, [focusTarget, view, consumeFocus])

  function registerActions(roleId: number) {
    return (element: HTMLSpanElement | null) => {
      if (element) {
        actionRefs.current.set(roleId, element)
      } else {
        actionRefs.current.delete(roleId)
      }
    }
  }

  const busy = state.loading || state.submitting
  const labels: TableLabels = {
    previousPage: t('roleadmin.pager.prev'),
    nextPage: t('roleadmin.pager.next'),
    selectAllRows: t('roleadmin.table.selectAllRows'),
    selectRow: t('roleadmin.table.selectRow'),
    emptyStatus: t('roleadmin.table.emptyStatus'),
    toggleRowDetail: t('roleadmin.table.toggleRowDetail'),
    pageStatus: (current, totalPages, totalCount) =>
      t('roleadmin.pager.status', { page: current, pages: totalPages, total: totalCount }),
  }
  const columns: TableColumn<RoleRow>[] = [
    {
      key: 'name',
      header: t('roleadmin.column.name'),
      render: (row) => <span className="roleadmin-wrap">{row.name}</span>,
    },
    {
      key: 'users',
      header: t('roleadmin.column.users'),
      render: (row) => (
        <span className="roleadmin-nowrap">
          {t('roleadmin.count.users', { count: row.userCount })}
        </span>
      ),
    },
    {
      key: 'groups',
      header: t('roleadmin.column.groups'),
      render: (row) => (
        <span className="roleadmin-nowrap">
          {t('roleadmin.count.groups', { count: row.groupCount })}
        </span>
      ),
    },
    {
      key: 'actions',
      header: t('roleadmin.column.actions'),
      render: (row) => (
        <RoleRowActions
          role={row}
          busy={busy}
          containerRef={registerActions(row.roleId)}
          onSelect={(action) => {
            dialogReturnRef.current =
              actionRefs.current.get(row.roleId)?.querySelector('button') ?? null
            if (action === 'rename') {
              state.openRename(row)
            } else {
              state.openDelete(row)
            }
          }}
        />
      ),
    },
  ]

  function handlePageChange(next: number): void {
    if (busy) {
      // Table にページ送りを押せなくする口が無いため、読み直しの間の押下はここで捨てる（D5）。
      return
    }
    state.goToPage(next, next < state.page ? 'prev' : 'next')
  }

  const showTable = list !== null && (view === 'populated' || view === 'reloading')
  const statusText =
    view === 'loading'
      ? t('roleadmin.list.loading')
      : view === 'reloading'
        ? t('roleadmin.list.reloading')
        : state.liveMessage
  const statusVisible = view === 'loading' || view === 'reloading'
  const notice = state.notice

  return (
    <div className="roleadmin-page" data-testid="roleadmin-list-page">
      <div className="roleadmin-page-header">
        <h1 className="roleadmin-heading">{t('roleadmin.title')}</h1>
        <Button
          ref={createRef}
          variant="primary"
          onClick={() => {
            dialogReturnRef.current = createRef.current
            state.openCreate()
          }}
          data-testid="roleadmin-create"
        >
          {t('roleadmin.action.create')}
        </Button>
      </div>
      <div className="roleadmin-notice" data-testid="roleadmin-notice">
        {notice !== null && (
          <Alert
            variant={notice.variant}
            onDismiss={state.dismissNotice}
            dismissLabel={t('roleadmin.action.dismiss')}
          >
            <span className="roleadmin-wrap">{t(notice.key, notice.values)}</span>
            {notice.assignmentsOf !== undefined && (
              <>
                {' '}
                <Link
                  to={roleAssignmentsPath(notice.assignmentsOf)}
                  className="roleadmin-link"
                  data-testid="roleadmin-inuse-link"
                >
                  {t('roleadmin.inUse.link')}
                </Link>
              </>
            )}
          </Alert>
        )}
      </div>
      {view === 'load-error' && (
        <div className="roleadmin-load-error" data-testid="roleadmin-load-error">
          <Alert variant="danger">{t('roleadmin.list.loadFailed')}</Alert>
          <div>
            <Button
              ref={retryRef}
              variant="secondary"
              onClick={state.retry}
              data-testid="roleadmin-retry"
            >
              {t('roleadmin.action.retry')}
            </Button>
          </div>
        </div>
      )}
      <section className="roleadmin-list" aria-labelledby={headingId}>
        <h2
          id={headingId}
          ref={headingRef}
          tabIndex={-1}
          className="roleadmin-list-heading"
          data-testid="roleadmin-list-heading"
        >
          {t('roleadmin.list.heading')}
        </h2>
        <p
          role="status"
          className={statusVisible ? 'roleadmin-status' : 'roleadmin-visually-hidden'}
          data-testid="roleadmin-status"
        >
          {statusText}
        </p>
        {view === 'empty' && (
          <div className="roleadmin-empty" data-testid="roleadmin-empty">
            <p className="roleadmin-empty-text">{t('roleadmin.list.empty')}</p>
            <div>
              <Button
                variant="secondary"
                onClick={(event) => {
                  dialogReturnRef.current = event.currentTarget
                  state.openCreate()
                }}
                data-testid="roleadmin-empty-create"
              >
                {t('roleadmin.action.create')}
              </Button>
            </div>
          </div>
        )}
        {showTable && (
          <div
            className="roleadmin-table"
            aria-busy={busy || undefined}
            data-testid="roleadmin-table"
          >
            <Table<RoleRow>
              columns={columns}
              data={list.items}
              totalCount={list.total}
              page={state.page}
              pageSize={PAGE_SIZE}
              onPageChange={handlePageChange}
              getRowId={(row) => String(row.roleId)}
              aria-label={t('roleadmin.list.heading')}
              labels={labels}
            />
          </div>
        )}
      </section>
      {(dialog.kind === 'create' || dialog.kind === 'rename') && (
        <RoleNameDialog
          // 開くたびに入力を初めから作る（名前の変更は今の名前から）。
          key={dialog.kind === 'rename' ? `rename-${dialog.row.roleId}` : 'create'}
          mode={dialog.kind}
          initialName={dialog.kind === 'rename' ? dialog.row.name : ''}
          submitting={state.submitting}
          slow={state.slow}
          serverProblem={state.nameProblem ?? undefined}
          dialogMessage={state.dialogMessage ?? undefined}
          onSubmit={state.submitName}
          onChange={state.changeName}
          onCancel={state.closeDialog}
          finalFocusRef={dialogReturnRef}
        />
      )}
      {dialog.kind === 'delete' && (
        <DeleteConfirmDialog
          name={dialog.row.name}
          submitting={state.submitting}
          slow={state.slow}
          dialogMessage={state.dialogMessage ?? undefined}
          onConfirm={state.confirmDelete}
          onCancel={state.closeDialog}
          finalFocusRef={dialogReturnRef}
        />
      )}
    </div>
  )
}
