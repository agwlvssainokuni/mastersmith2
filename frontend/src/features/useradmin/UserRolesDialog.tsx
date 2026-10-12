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
// 利用者のロールの読み取りの Modal（Intent 261004-role-menu の U6 の D27、functional-spec.md の W9.1、
// frontend-components.md の 6節・8節、AC2.2.7）。題は「田中 一郎さんのロール」。開いたときに GET …/users/{userId}/roles を
// 1回読み、読み込み中・一覧（ul、「営業（直接・グループ 営業部）」）・空・失敗・USER_NOT_FOUND を出す。403 は骨組みに渡し、
// 独自の文言を出さない（D2）。下にロールの一覧（/admin/roles、道の文字列。roleadmin は import しない）へのリンク。
// 閉じたら行の「操作」へフォーカスを戻す（呼ぶ側の finalFocusRef）。名前は React の文字として描く。
import { Button, Modal } from 'make-you-chic-ui'
import { useEffect, useState, type RefObject } from 'react'
import { Link } from 'react-router'
import { userRolesPath, type UserAdminApi } from './api/userAdminApi'
import type { AdminUser, UserRole, UserRoleSource } from './api/types'
import { knownCode } from './failureMessage'
import { useUserAdminText, type UserAdminText } from './useUserAdminText'
import './UserRolesDialog.css'

/** ロールの一覧の画面の道（roleadmin を import しない） */
const ROLE_LIST_PATH = '/admin/roles'

export interface UserRolesDialogProps {
  /** 対象の利用者 */
  user: AdminUser
  /** API の関数の集まり */
  api: UserAdminApi
  /** 管理の画面の 403 を骨組みに渡す口 */
  reportForbidden: (error: unknown, apiPath: string) => boolean
  onClose: () => void
  /** 閉じた後にフォーカスを戻す先（行の「操作」） */
  finalFocusRef?: RefObject<HTMLElement | null>
}

type RolesState =
  | { phase: 'loading' }
  | { phase: 'ready'; roles: UserRole[] }
  | { phase: 'failed'; key: string }
  | { phase: 'handled' }

/** 出どころの文字（「直接・グループ 営業部」） */
export function userRoleSourcesText(sources: readonly UserRoleSource[], t: UserAdminText): string {
  return sources
    .map((source) =>
      source.kind === 'DIRECT'
        ? t('useradmin.roles.direct')
        : t('useradmin.roles.group', { name: source.name }),
    )
    .join(t('useradmin.roles.separator'))
}

/** 利用者のロールの読み取りの Modal */
export function UserRolesDialog({
  user,
  api,
  reportForbidden,
  onClose,
  finalFocusRef,
}: UserRolesDialogProps) {
  const t = useUserAdminText()
  const [state, setState] = useState<RolesState>({ phase: 'loading' })

  // 開いたときに1回読む。状態の更新を効果の中で同期に呼ばないよう、答えを受けてから更新する。
  useEffect(() => {
    let active = true
    api.getUserRoles(user.userId).then(
      (roles) => {
        if (active) {
          setState({ phase: 'ready', roles })
        }
      },
      (error: unknown) => {
        if (!active) {
          return
        }
        if (reportForbidden(error, userRolesPath(user.userId))) {
          setState({ phase: 'handled' })
          return
        }
        setState({
          phase: 'failed',
          key:
            knownCode(error) === 'USER_NOT_FOUND'
              ? 'useradmin.roles.userMissing'
              : 'useradmin.roles.failed',
        })
      },
    )
    return () => {
      active = false
    }
  }, [api, user.userId, reportForbidden])

  return (
    <Modal
      open
      title={t('useradmin.roles.title', { name: user.displayName })}
      onClose={onClose}
      finalFocusRef={finalFocusRef}
      closeLabel={t('useradmin.action.close')}
      closeOnBackdropClick={false}
    >
      <div className="useradmin-roles" data-testid="useradmin-roles-dialog">
        {state.phase === 'loading' && (
          <p role="status" className="useradmin-roles-text">
            {t('useradmin.roles.loading')}
          </p>
        )}
        {state.phase === 'failed' && (
          <p role="alert" className="useradmin-roles-text" data-testid="useradmin-roles-failed">
            {t(state.key)}
          </p>
        )}
        {state.phase === 'ready' && state.roles.length === 0 && (
          <p className="useradmin-roles-text" data-testid="useradmin-roles-empty">
            {t('useradmin.roles.empty')}
          </p>
        )}
        {state.phase === 'ready' && state.roles.length > 0 && (
          <ul className="useradmin-roles-list" aria-label={t('useradmin.roles.list')}>
            {state.roles.map((role) => (
              <li key={role.roleId} className="useradmin-roles-text">
                {t('useradmin.roles.item', {
                  name: role.name,
                  sources: userRoleSourcesText(role.sources, t),
                })}
              </li>
            ))}
          </ul>
        )}
        <div className="useradmin-roles-footer">
          <Link to={ROLE_LIST_PATH} className="useradmin-roles-link">
            {t('useradmin.roles.toList')}
          </Link>
          <Button variant="secondary" onClick={onClose} data-testid="useradmin-roles-close">
            {t('useradmin.action.close')}
          </Button>
        </div>
      </div>
    </Modal>
  )
}
