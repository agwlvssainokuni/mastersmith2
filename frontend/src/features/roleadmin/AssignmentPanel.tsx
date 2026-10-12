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
// S5 割り当て（Intent 261004-role-menu の U6、functional-spec.md の 3.3・4.4、D16〜D19・D29〜D31、frontend-components.md
// の 2.2・8節）。利用者の表（氏名・メールアドレス・出どころ・操作）とグループの表（名前・操作）、「利用者を足す」
// 「グループを足す」の候補の Modal、外すときの確かめ（D19）を置く。出どころは「直接」「グループ 営業部」を文字で「・」で
// つなぎ、「外す」は出どころに直接があるときだけ出す。状態と操作は useRoleAssignments が持つ。名前は React の文字として描く。
import { Alert, Button, Modal } from 'make-you-chic-ui'
import { useRef } from 'react'
import { useAdminForbidden } from '../../app/admin-forbidden/AdminForbiddenProvider'
import { roleAdminApi, type RoleAdminApi } from './api/roleAdminApi'
import type { AssignedUser, AssignmentSource } from './api/types'
import { CandidateDialog } from './CandidateDialog'
import { useRoleAdminText, type RoleAdminText } from './useRoleAdminText'
import { useRoleAssignments, type RemovalConfirm } from './useRoleAssignments'
import './AssignmentPanel.css'

export interface AssignmentPanelProps {
  /** ロールの ID */
  roleId: number
  /** ロールの名前（結果の文言に使う） */
  roleName: string
  /** API の関数の集まり（テストで差し替える） */
  api?: RoleAdminApi
  /** ROLE_NOT_FOUND を受けた（D32 へ） */
  onMissing: () => void
}

/** 出どころの文字（「直接・グループ 営業部」） */
export function sourcesText(sources: readonly AssignmentSource[], t: RoleAdminText): string {
  return sources
    .map((source) =>
      source.kind === 'DIRECT'
        ? t('roleadmin.source.direct')
        : t('roleadmin.source.group', { name: source.name }),
    )
    .join(t('roleadmin.source.separator'))
}

function hasDirect(user: AssignedUser): boolean {
  return user.sources.some((source) => source.kind === 'DIRECT')
}

/** 外すときの確かめ（D19、D8 と同じ形の Modal） */
function RemovalConfirmDialog({
  confirm,
  onConfirm,
  onCancel,
}: {
  confirm: RemovalConfirm
  onConfirm: () => void
  onCancel: () => void
}) {
  const t = useRoleAdminText()
  const cancelRef = useRef<HTMLButtonElement>(null)
  return (
    <Modal
      open
      role="alertdialog"
      title={t('roleadmin.removal.title')}
      onClose={onCancel}
      initialFocusRef={cancelRef}
      closeLabel={t('roleadmin.action.close')}
      closeOnBackdropClick={false}
    >
      <div className="roleadmin-dialog" data-testid="roleadmin-removal-dialog">
        <p className="roleadmin-dialog-text">{t(confirm.key, confirm.values)}</p>
        <div className="roleadmin-dialog-actions">
          <Button
            ref={cancelRef}
            variant="secondary"
            onClick={onCancel}
            data-testid="roleadmin-removal-cancel"
          >
            {t('roleadmin.action.cancel')}
          </Button>
          <Button variant="danger" onClick={onConfirm} data-testid="roleadmin-removal-submit">
            {t('roleadmin.removal.submit')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}

/** S5 割り当て */
export function AssignmentPanel({
  roleId,
  roleName,
  api = roleAdminApi,
  onMissing,
}: AssignmentPanelProps) {
  const t = useRoleAdminText()
  const reportForbidden = useAdminForbidden()
  const state = useRoleAssignments({ api, roleId, roleName, reportForbidden, onMissing })
  const addUserRef = useRef<HTMLButtonElement>(null)
  const addGroupRef = useRef<HTMLButtonElement>(null)
  const { assignments, notice } = state
  const directUserIds = new Set(assignments.users.filter(hasDirect).map((user) => user.userId))
  const groupIds = new Set(assignments.groups.map((group) => group.groupId))

  if (state.phase === 'loading') {
    return (
      <p role="status" className="roleadmin-status" data-testid="roleadmin-assign-loading">
        {t('roleadmin.assign.loading')}
      </p>
    )
  }
  if (state.phase === 'load-error') {
    return (
      <div className="roleadmin-load-error" data-testid="roleadmin-assign-error">
        <Alert variant="danger">{t('roleadmin.assign.loadFailed')}</Alert>
        <div>
          <Button variant="secondary" onClick={state.retry}>
            {t('roleadmin.action.retry')}
          </Button>
        </div>
      </div>
    )
  }

  const empty = assignments.users.length === 0 && assignments.groups.length === 0

  return (
    <div className="roleadmin-assign" data-testid="roleadmin-assign">
      <div className="roleadmin-notice" data-testid="roleadmin-assign-notice">
        {notice !== null && (
          <Alert
            variant={notice.variant}
            onDismiss={state.dismissNotice}
            dismissLabel={t('roleadmin.action.dismiss')}
          >
            <span className="roleadmin-wrap">{t(notice.key, notice.values)}</span>
          </Alert>
        )}
      </div>
      <div className="roleadmin-dialog-actions roleadmin-assign-actions">
        <Button
          ref={addUserRef}
          variant="secondary"
          onClick={() => state.openPicker('user')}
          data-testid="roleadmin-add-user"
        >
          {t('roleadmin.assign.addUser')}
        </Button>
        <Button
          ref={addGroupRef}
          variant="secondary"
          onClick={() => state.openPicker('group')}
          data-testid="roleadmin-add-group"
        >
          {t('roleadmin.assign.addGroup')}
        </Button>
      </div>
      {empty && (
        <p className="roleadmin-status" data-testid="roleadmin-assign-empty">
          {t('roleadmin.assign.empty')}
        </p>
      )}
      <section className="roleadmin-assign-section" aria-label={t('roleadmin.assign.users')}>
        <h2 className="roleadmin-perm-subheading">{t('roleadmin.assign.users')}</h2>
        {assignments.users.length === 0 ? (
          <p className="roleadmin-status">{t('roleadmin.assign.usersEmpty')}</p>
        ) : (
          <div className="roleadmin-scroll">
            <table className="roleadmin-grid" data-testid="roleadmin-assigned-users">
              <thead>
                <tr>
                  <th scope="col">{t('roleadmin.assign.column.displayName')}</th>
                  <th scope="col">{t('roleadmin.assign.column.email')}</th>
                  <th scope="col">{t('roleadmin.assign.column.source')}</th>
                  <th scope="col">{t('roleadmin.assign.column.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {assignments.users.map((user) => (
                  <tr key={user.userId} data-testid={`roleadmin-assigned-user-${user.userId}`}>
                    <th scope="row">
                      <span className="roleadmin-target">
                        <span className="roleadmin-wrap">{user.displayName}</span>
                        {user.suspended && (
                          <span className="roleadmin-physical">{t('roleadmin.suspended')}</span>
                        )}
                      </span>
                    </th>
                    <td>
                      <span className="roleadmin-wrap">{user.email}</span>
                    </td>
                    <td>
                      <span className="roleadmin-wrap">{sourcesText(user.sources, t)}</span>
                    </td>
                    <td>
                      {hasDirect(user) && (
                        <Button
                          variant="secondary"
                          size="sm"
                          loading={state.removingKey === `user-${user.userId}`}
                          aria-label={t('roleadmin.remove.name', { name: user.displayName })}
                          onClick={() => void state.requestRemoveUser(user)}
                        >
                          {t('roleadmin.remove')}
                        </Button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      <section className="roleadmin-assign-section" aria-label={t('roleadmin.assign.groups')}>
        <h2 className="roleadmin-perm-subheading">{t('roleadmin.assign.groups')}</h2>
        {assignments.groups.length === 0 ? (
          <p className="roleadmin-status">{t('roleadmin.assign.groupsEmpty')}</p>
        ) : (
          <div className="roleadmin-scroll">
            <table className="roleadmin-grid" data-testid="roleadmin-assigned-groups">
              <thead>
                <tr>
                  <th scope="col">{t('roleadmin.assign.column.name')}</th>
                  <th scope="col">{t('roleadmin.assign.column.actions')}</th>
                </tr>
              </thead>
              <tbody>
                {assignments.groups.map((group) => (
                  <tr key={group.groupId} data-testid={`roleadmin-assigned-group-${group.groupId}`}>
                    <th scope="row">
                      <span className="roleadmin-wrap">{group.name}</span>
                    </th>
                    <td>
                      <Button
                        variant="secondary"
                        size="sm"
                        loading={state.removingKey === `group-${group.groupId}`}
                        aria-label={t('roleadmin.remove.name', { name: group.name })}
                        onClick={() => state.removeGroup(group)}
                      >
                        {t('roleadmin.remove')}
                      </Button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>
      {state.busy && state.slow && (
        <p role="status" className="roleadmin-dialog-slow" data-testid="roleadmin-assign-slow">
          {t('roleadmin.slow')}
        </p>
      )}
      {state.picker === 'user' && (
        <CandidateDialog
          kind="user"
          title={t('roleadmin.candidate.userTitle')}
          isTaken={(id) => directUserIds.has(id)}
          takenLabel={t('roleadmin.candidate.taken')}
          search={state.searchUsers}
          onAdd={(candidate) => state.add('user', candidate)}
          onClose={state.closePicker}
          finalFocusRef={addUserRef}
        />
      )}
      {state.picker === 'group' && (
        <CandidateDialog
          kind="group"
          title={t('roleadmin.candidate.groupTitle')}
          isTaken={(id) => groupIds.has(id)}
          takenLabel={t('roleadmin.candidate.taken')}
          loadPage={state.loadGroups}
          onAdd={(candidate) => state.add('group', candidate)}
          onClose={state.closePicker}
          finalFocusRef={addGroupRef}
        />
      )}
      {state.confirm !== null && (
        <RemovalConfirmDialog
          confirm={state.confirm}
          onConfirm={state.confirmRemoval}
          onCancel={state.cancelRemoval}
        />
      )}
    </div>
  )
}
