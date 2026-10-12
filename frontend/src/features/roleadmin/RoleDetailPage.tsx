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
// S4・S5 ロールの詳細の枠（Intent 261004-role-menu の U6 の D32・D15、functional-spec.md の W4.1・W4.7・W5.1、
// frontend-components.md の 2.2、NFR4.7）。道の roleId を読み、正の整数でなければ読まずに「このロールはありません。」と
// ロールの一覧へのリンクを出す。ロールを読めたら見出し「ロール: 営業」、「← ロールの一覧」、タブ［権限の設定］
// ［割り当て］を出す。タブは道で決め（末尾が /assignments なら割り当て）、切り替えは道を変える。未保存の写しがあれば
// 権限の設定のパネルの useBlocker が止める。S4 と S5 は同じ部品を2つの道に登録する。
import { Alert, Button, Tabs } from 'make-you-chic-ui'
import { useCallback } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { useAdminForbidden } from '../../app/admin-forbidden/AdminForbiddenProvider'
import { roleAdminApi, type RoleAdminApi } from './api/roleAdminApi'
import { AssignmentPanel } from './AssignmentPanel'
import { roleAssignmentsPath, roleDetailPath, ROLE_LIST_PATH } from './paths'
import { PermissionPanel } from './PermissionPanel'
import { parseRoleId, useRole } from './useRole'
import { useRoleAdminText } from './useRoleAdminText'
import './RoleListPage.css'

export interface RoleDetailPageProps {
  /** API の関数の集まり（テストで差し替える） */
  api?: RoleAdminApi
}

/** S4・S5 ロールの詳細 */
export function RoleDetailPage({ api = roleAdminApi }: RoleDetailPageProps) {
  const t = useRoleAdminText()
  const params = useParams()
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const reportForbidden = useAdminForbidden()
  const roleId = parseRoleId(params.roleId)
  const { phase, role, retry, markMissing } = useRole(api, roleId, reportForbidden)
  const assignmentsTab = pathname.endsWith('/assignments')
  const onMissing = useCallback(() => markMissing(), [markMissing])

  if (phase === 'missing' || roleId === undefined) {
    return (
      <div className="roleadmin-page" data-testid="roleadmin-detail-missing">
        <h1 className="roleadmin-heading">{t('roleadmin.title')}</h1>
        <p className="roleadmin-dialog-text">{t('roleadmin.role.missing')}</p>
        <Link to={ROLE_LIST_PATH} className="roleadmin-link">
          {t('roleadmin.detail.toList')}
        </Link>
      </div>
    )
  }
  if (phase === 'loading' || role === null) {
    if (phase === 'load-error') {
      return (
        <div className="roleadmin-page roleadmin-load-error" data-testid="roleadmin-detail-error">
          <h1 className="roleadmin-heading">{t('roleadmin.title')}</h1>
          <Alert variant="danger">{t('roleadmin.detail.loadFailed')}</Alert>
          <div>
            <Button variant="secondary" onClick={() => void retry()}>
              {t('roleadmin.action.retry')}
            </Button>
          </div>
        </div>
      )
    }
    return (
      <div className="roleadmin-page" data-testid="roleadmin-detail-loading">
        <p role="status" className="roleadmin-status">
          {t('roleadmin.detail.loading')}
        </p>
      </div>
    )
  }

  return (
    <div className="roleadmin-page" data-testid="roleadmin-detail-page">
      <Link to={ROLE_LIST_PATH} className="roleadmin-link" data-testid="roleadmin-back">
        {t('roleadmin.detail.back')}
      </Link>
      <h1 className="roleadmin-heading">{t('roleadmin.detail.heading', { name: role.name })}</h1>
      <div className="roleadmin-tabs" data-testid="roleadmin-tabs">
        <Tabs
          aria-label={t('roleadmin.tab.label')}
          activeIndex={assignmentsTab ? 1 : 0}
          onChange={(index) =>
            void navigate(index === 1 ? roleAssignmentsPath(roleId) : roleDetailPath(roleId))
          }
          items={[
            {
              label: t('roleadmin.tab.permissions'),
              content: assignmentsTab ? null : (
                <PermissionPanel roleId={roleId} api={api} onMissing={onMissing} />
              ),
            },
            {
              label: t('roleadmin.tab.assignments'),
              content: assignmentsTab ? (
                <AssignmentPanel
                  roleId={roleId}
                  roleName={role.name}
                  api={api}
                  onMissing={onMissing}
                />
              ) : null,
            },
          ]}
        />
      </div>
    </div>
  )
}
