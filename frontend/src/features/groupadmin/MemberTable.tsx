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
// S6 のメンバーの表（Intent 261004-role-menu の U6 の D21・D20、functional-spec.md の W6.2・W6.4、frontend-components.md
// の 4節）。氏名・メールアドレス・「（利用停止）」・外す。素の table（make-you-chic-ui の Table の見た目に合わせる）で、
// 外枠の中で横に動き、長い氏名・メールアドレスは折り返す。名前は React の文字として描く。
import { Button } from 'make-you-chic-ui'
import type { Member } from './api/types'
import { useGroupAdminText } from './useGroupAdminText'
import './GroupDetailPage.css'

export interface MemberTableProps {
  /** メンバー（ページ送りなし） */
  members: readonly Member[]
  /** 外している利用者の ID */
  removingId: number | null
  /** 送信中（ほかの行の「外す」も押せない） */
  busy: boolean
  /** 外す */
  onRemove: (member: Member) => void
}

/** S6 のメンバーの表 */
export function MemberTable({ members, removingId, busy, onRemove }: MemberTableProps) {
  const t = useGroupAdminText()
  if (members.length === 0) {
    return (
      <p className="groupadmin-status" data-testid="groupadmin-members-empty">
        {t('groupadmin.member.empty')}
      </p>
    )
  }
  return (
    <div className="groupadmin-scroll">
      <table className="groupadmin-grid" data-testid="groupadmin-members">
        <thead>
          <tr>
            <th scope="col">{t('groupadmin.member.column.displayName')}</th>
            <th scope="col">{t('groupadmin.member.column.email')}</th>
            <th scope="col">{t('groupadmin.member.column.actions')}</th>
          </tr>
        </thead>
        <tbody>
          {members.map((member) => (
            <tr key={member.userId} data-testid={`groupadmin-member-${member.userId}`}>
              <th scope="row">
                <span className="groupadmin-target">
                  <span className="groupadmin-wrap">{member.displayName}</span>
                  {member.suspended && (
                    <span className="groupadmin-sub">{t('groupadmin.suspended')}</span>
                  )}
                </span>
              </th>
              <td>
                <span className="groupadmin-wrap">{member.email}</span>
              </td>
              <td>
                <Button
                  variant="secondary"
                  size="sm"
                  loading={removingId === member.userId}
                  disabled={busy && removingId !== member.userId}
                  aria-label={t('groupadmin.remove.name', { name: member.displayName })}
                  onClick={() => onRemove(member)}
                >
                  {t('groupadmin.remove')}
                </Button>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  )
}
