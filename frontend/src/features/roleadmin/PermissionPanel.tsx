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
// S4 権限の設定（Intent 261004-role-menu の U6、functional-spec.md の 3.2・4.3・7節、D10〜D15・D29〜D31、
// frontend-components.md の 2.2・8節、AC1.2.1〜AC1.2.17）。左に共有の木（スキーマ → テーブル）、右に選んだ節の表と
// 保存の操作を置く。状態と操作は useRolePermissions が持つ。未保存の確かめは、道の移動を useUnsavedGuard（useBlocker と
// beforeunload）で、木の節の選び（道は変わらない）を画面の中で、同じ LeaveConfirmDialog で出す。
// 保存は写しが空なら押せず、理由「変更はありません」を隣に文字で出して aria-describedby で結ぶ。保存の結果は Alert
// （成功は role="status"）で伝え、フォーカスは保存のボタンに残す（D13）。
import { Alert, Button } from 'make-you-chic-ui'
import { useEffect, useId, useMemo, useRef } from 'react'
import { Link } from 'react-router'
import { useAdminForbidden } from '../../app/admin-forbidden/AdminForbiddenProvider'
import { SharedTreeView } from '../../shared/tree/SharedTreeView'
import type { SharedTreeLabels } from '../../shared/tree/types'
import { roleAdminApi, type RoleAdminApi } from './api/roleAdminApi'
import { LeaveConfirmDialog } from './LeaveConfirmDialog'
import { DSL_ADMIN_PATH } from './paths'
import { PermissionTable } from './PermissionTable'
import { treeNodeOf, useRolePermissions } from './useRolePermissions'
import { useRoleAdminText } from './useRoleAdminText'
import { useUnsavedGuard } from './useUnsavedGuard'
import './PermissionPanel.css'

export interface PermissionPanelProps {
  /** ロールの ID */
  roleId: number
  /** API の関数の集まり（テストで差し替える） */
  api?: RoleAdminApi
  /** ROLE_NOT_FOUND を受けた（D32 へ） */
  onMissing: () => void
}

/** S4 権限の設定 */
export function PermissionPanel({ roleId, api = roleAdminApi, onMissing }: PermissionPanelProps) {
  const t = useRoleAdminText()
  const reportForbidden = useAdminForbidden()
  const state = useRolePermissions({ api, roleId, t, reportForbidden, onMissing })
  const guard = useUnsavedGuard(state.dirty, state.discard)
  const reasonId = useId()
  const tableHeadingRef = useRef<HTMLHeadingElement>(null)
  const { focusTableSeq, schemas } = state
  // 共有の木は nodes が新しい配列になると開いている節を読み直すため、スキーマの一覧が変わったときだけ作り直す。
  const treeNodes = useMemo(() => schemas.map((schema) => treeNodeOf(schema, t)), [schemas, t])

  // 狭い画面で木から選んだら、表の見出しへフォーカスを移す（D31）。
  useEffect(() => {
    if (focusTableSeq > 0) {
      tableHeadingRef.current?.focus()
    }
  }, [focusTableSeq])

  const labels: SharedTreeLabels = {
    expand: (name) => t('roleadmin.tree.expand', { name }),
    collapse: (name) => t('roleadmin.tree.collapse', { name }),
    loading: t('roleadmin.tree.loading'),
    loadFailed: t('roleadmin.tree.loadFailed'),
    childArea: (name) => t('roleadmin.tree.childArea', { name }),
    retry: t('roleadmin.tree.retry'),
    empty: t('roleadmin.tree.empty'),
  }

  if (state.phase === 'loading') {
    return (
      <p role="status" className="roleadmin-status" data-testid="roleadmin-perm-loading">
        {t('roleadmin.perm.loading')}
      </p>
    )
  }
  if (state.phase === 'load-error') {
    return (
      <div className="roleadmin-load-error" data-testid="roleadmin-perm-error">
        <Alert variant="danger">{t('roleadmin.perm.loadFailed')}</Alert>
        <div>
          <Button variant="secondary" onClick={state.retry}>
            {t('roleadmin.action.retry')}
          </Button>
        </div>
      </div>
    )
  }
  if (state.phase === 'noDsl') {
    return (
      <div className="roleadmin-nodsl" data-testid="roleadmin-nodsl">
        <p className="roleadmin-dialog-text">{t('roleadmin.noDsl')}</p>
        <Link to={DSL_ADMIN_PATH} className="roleadmin-link">
          {t('roleadmin.noDsl.link')}
        </Link>
      </div>
    )
  }

  const node = state.selectedNode
  const schemaName =
    state.selected === null
      ? undefined
      : state.schemas.find((schema) => schema.schemaName === state.selected?.schemaName)
          ?.displayName
  const notice = state.notice
  const busy = state.saving

  return (
    <div className="roleadmin-perm" data-testid="roleadmin-perm">
      <div className="roleadmin-notice" data-testid="roleadmin-perm-notice">
        {notice !== null && (
          <Alert
            variant={notice.variant}
            onDismiss={state.dismissNotice}
            dismissLabel={t('roleadmin.action.dismiss')}
          >
            {t(notice.key)}
          </Alert>
        )}
      </div>
      <div className="roleadmin-perm-body">
        <div className="roleadmin-perm-tree">
          <SharedTreeView
            nodes={treeNodes}
            loadChildren={state.loadChildren}
            selectedId={state.selectedId}
            onSelect={state.select}
            expandedIds={state.expandedIds}
            onToggle={state.toggle}
            labels={labels}
            ariaLabel={t('roleadmin.perm.treeLabel')}
          />
        </div>
        <div className="roleadmin-perm-main">
          {node === undefined ? (
            <p className="roleadmin-status">{t('roleadmin.perm.selectPrompt')}</p>
          ) : (
            <>
              <PermissionTable
                node={node}
                columns={state.columns}
                draft={state.draft}
                busy={busy}
                names={{ schema: schemaName }}
                onChange={state.change}
                onClearOrphan={state.clearOrphan}
                onRetryColumns={state.retryColumns}
                headingRef={tableHeadingRef}
              />
              <div className="roleadmin-save" data-testid="roleadmin-save-area">
                <p className="roleadmin-status" data-testid="roleadmin-unsaved-count">
                  {t('roleadmin.unsaved.count', { count: state.draft.size })}
                </p>
                <div className="roleadmin-dialog-actions">
                  <Button
                    variant="secondary"
                    disabled={!state.dirty || busy}
                    onClick={state.discard}
                    data-testid="roleadmin-discard"
                  >
                    {t('roleadmin.unsaved.discard')}
                  </Button>
                  <Button
                    variant="primary"
                    loading={busy}
                    disabled={!state.dirty}
                    aria-describedby={!state.dirty ? reasonId : undefined}
                    onClick={state.save}
                    data-testid="roleadmin-save"
                  >
                    {t(busy ? 'roleadmin.action.processing' : 'roleadmin.save')}
                  </Button>
                  {!state.dirty && (
                    <span id={reasonId} className="roleadmin-save-reason">
                      {t('roleadmin.save.none')}
                    </span>
                  )}
                </div>
                {busy && state.slow && (
                  <p
                    role="status"
                    className="roleadmin-dialog-slow"
                    data-testid="roleadmin-save-slow"
                  >
                    {t('roleadmin.slow')}
                  </p>
                )}
              </div>
            </>
          )}
        </div>
      </div>
      {(guard.blocked || state.pendingSelect) && (
        <LeaveConfirmDialog
          onStay={guard.blocked ? guard.stay : state.staySelect}
          onLeave={guard.blocked ? guard.leave : state.leaveSelect}
        />
      )}
    </div>
  )
}
