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
// S6 グループの詳細（Intent 261004-role-menu の U6、functional-spec.md の 3.4 W6.2〜W6.4、D20・D21・D32、
// frontend-components.md の 4節・8節）。「← グループの一覧」、見出し「グループ: 営業部」、「名前を変える」、
// 「メンバーを足す」、メンバーの表、割り当てたロール（各ロールはその割り当てのタブへのリンク、無ければ「ありません」）を
// 置く。道の ID が正の整数でない・GROUP_NOT_FOUND のときは「このグループはありません。」と一覧へのリンク（D32 と同じ形）。
// 状態と操作は useGroupDetail が持つ。ロールの割り当ては S5 で変える（ここは読み取り）。
import { Alert, Button } from 'make-you-chic-ui'
import { useRef } from 'react'
import { Link, useParams } from 'react-router'
import { useAdminForbidden } from '../../app/admin-forbidden/AdminForbiddenProvider'
import { groupAdminApi, type GroupAdminApi } from './api/groupAdminApi'
import { CandidateDialog } from './CandidateDialog'
import { GroupNameDialog } from './GroupNameDialog'
import { MemberTable } from './MemberTable'
import { GROUP_LIST_PATH, roleAssignmentsPath } from './paths'
import { parseGroupId, useGroupDetail } from './useGroupDetail'
import { useGroupAdminText } from './useGroupAdminText'
import './GroupListPage.css'
import './GroupDetailPage.css'

export interface GroupDetailPageProps {
  /** API の関数の集まり（テストで差し替える） */
  api?: GroupAdminApi
}

/** S6 グループの詳細 */
export function GroupDetailPage({ api = groupAdminApi }: GroupDetailPageProps) {
  const t = useGroupAdminText()
  const params = useParams()
  const reportForbidden = useAdminForbidden()
  const groupId = parseGroupId(params.groupId)
  const state = useGroupDetail({ api, groupId, t, reportForbidden })
  const renameRef = useRef<HTMLButtonElement>(null)
  const addRef = useRef<HTMLButtonElement>(null)
  const { detail, notice } = state

  if (state.phase === 'missing') {
    return (
      <div className="groupadmin-page" data-testid="groupadmin-detail-missing">
        <h1 className="groupadmin-heading">{t('groupadmin.title')}</h1>
        <p className="groupadmin-dialog-text">{t('groupadmin.group.missing')}</p>
        <Link to={GROUP_LIST_PATH} className="groupadmin-link">
          {t('groupadmin.detail.toList')}
        </Link>
      </div>
    )
  }
  if (state.phase === 'load-error') {
    return (
      <div className="groupadmin-page groupadmin-load-error" data-testid="groupadmin-detail-error">
        <h1 className="groupadmin-heading">{t('groupadmin.title')}</h1>
        <Alert variant="danger">{t('groupadmin.detail.loadFailed')}</Alert>
        <div>
          <Button variant="secondary" onClick={state.retry}>
            {t('groupadmin.action.retry')}
          </Button>
        </div>
      </div>
    )
  }
  if (state.phase === 'loading' || detail === null) {
    return (
      <div className="groupadmin-page" data-testid="groupadmin-detail-loading">
        <p role="status" className="groupadmin-status">
          {t('groupadmin.detail.loading')}
        </p>
      </div>
    )
  }

  const memberIds = new Set(detail.members.map((member) => member.userId))

  return (
    <div className="groupadmin-page" data-testid="groupadmin-detail-page">
      <Link to={GROUP_LIST_PATH} className="groupadmin-link" data-testid="groupadmin-back">
        {t('groupadmin.detail.back')}
      </Link>
      <div className="groupadmin-page-header">
        <h1 className="groupadmin-heading">
          {t('groupadmin.detail.heading', { name: detail.name })}
        </h1>
        <Button
          ref={renameRef}
          variant="secondary"
          onClick={state.openRename}
          data-testid="groupadmin-rename"
        >
          {t('groupadmin.menu.rename')}
        </Button>
      </div>
      <div className="groupadmin-notice" data-testid="groupadmin-detail-notice">
        {notice !== null && (
          <Alert
            variant={notice.variant}
            onDismiss={state.dismissNotice}
            dismissLabel={t('groupadmin.action.dismiss')}
          >
            <span className="groupadmin-wrap">{t(notice.key, notice.values)}</span>
          </Alert>
        )}
      </div>
      <section className="groupadmin-section" aria-labelledby="groupadmin-members-heading">
        <div className="groupadmin-page-header">
          <h2 id="groupadmin-members-heading" className="groupadmin-subheading">
            {t('groupadmin.member.heading')}
          </h2>
          <Button
            ref={addRef}
            variant="secondary"
            onClick={state.openPicker}
            data-testid="groupadmin-add-member"
          >
            {t('groupadmin.member.add')}
          </Button>
        </div>
        <MemberTable
          members={detail.members}
          removingId={state.removingId}
          busy={state.submitting}
          onRemove={state.removeMember}
        />
        {state.submitting && state.slow && state.removingId !== null && (
          <p role="status" className="groupadmin-dialog-slow">
            {t('groupadmin.slow')}
          </p>
        )}
      </section>
      <section className="groupadmin-section" aria-labelledby="groupadmin-roles-heading">
        <h2 id="groupadmin-roles-heading" className="groupadmin-subheading">
          {t('groupadmin.roles.heading')}
        </h2>
        {state.roles.phase === 'failed' ? (
          <p className="groupadmin-status" data-testid="groupadmin-roles-failed">
            {t('groupadmin.roles.loadFailed')}
          </p>
        ) : state.roles.items.length === 0 ? (
          <p className="groupadmin-status" data-testid="groupadmin-roles-empty">
            {t('groupadmin.roles.empty')}
          </p>
        ) : (
          <ul className="groupadmin-roles" data-testid="groupadmin-roles">
            {state.roles.items.map((role) => (
              <li key={role.roleId}>
                <Link to={roleAssignmentsPath(role.roleId)} className="groupadmin-link">
                  {role.name}
                </Link>
              </li>
            ))}
          </ul>
        )}
      </section>
      {state.renameOpen && (
        <GroupNameDialog
          mode="rename"
          initialName={detail.name}
          submitting={state.submitting}
          slow={state.slow}
          serverProblem={state.nameProblem ?? undefined}
          dialogMessage={state.dialogMessage ?? undefined}
          onSubmit={state.submitName}
          onChange={state.changeName}
          onCancel={state.closeRename}
          finalFocusRef={renameRef}
        />
      )}
      {state.pickerOpen && (
        <CandidateDialog
          isTaken={(id) => memberIds.has(id)}
          search={state.searchUsers}
          onAdd={state.add}
          onClose={state.closePicker}
          finalFocusRef={addRef}
        />
      )}
    </div>
  )
}
