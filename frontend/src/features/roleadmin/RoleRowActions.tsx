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
// ロールの一覧の行の操作（Intent 261004-role-menu の U6 の D6、functional-spec.md の W3.4〜W3.6、frontend-components.md
// の 8節）。「開く」（S4 へのリンク、読み上げの名前に対象の名前）と、Dropdown（placement="bottom-end"）の「名前を変える」
// 「削除」。削除は割り当ての数によらず押せ、判定はサーバー（D6）。trigger の名前は見える文字で始める「操作: 〔名前〕」
// （Intent 261016-business-data-views の U4 の D1〜D4、src/shared/row-actions の rowActionsName。WCAG 2.5.3）、busy なら
// 「（処理中）」を添え、Button の loading でメニューを開かない（既存の UserRowActions と同じ形）。
// Dropdown は trigger の ref を置き換えるため、フォーカスを戻す先は包む要素の中のボタンから探す。
import { Button, Dropdown, type MenuItem } from 'make-you-chic-ui'
import { Link } from 'react-router'
import { rowActionsName } from '../../shared/row-actions/rowActionsName'
import type { RoleRow } from './api/types'
import { roleDetailPath } from './paths'
import { useRoleAdminText } from './useRoleAdminText'
import './RoleListPage.css'

/** 行の Dropdown の項目 */
export type RoleRowAction = 'rename' | 'delete'

export interface RoleRowActionsProps {
  /** 対象の行 */
  role: RoleRow
  /** 一覧の読み直しの間、または送信中 */
  busy: boolean
  /** 項目を選んだ */
  onSelect: (action: RoleRowAction) => void
  /** Dropdown の trigger を包む要素（フォーカスを戻すときに中のボタンを探す） */
  containerRef?: (element: HTMLSpanElement | null) => void
}

/** ロールの一覧の行の操作 */
export function RoleRowActions({ role, busy, onSelect, containerRef }: RoleRowActionsProps) {
  const t = useRoleAdminText()
  const baseName = rowActionsName(t('roleadmin.actions.button'), role.name)
  const name = busy ? `${baseName}${t('roleadmin.actions.busySuffix')}` : baseName
  const items: MenuItem[] = (['rename', 'delete'] as const).map((action) => ({
    label: t(`roleadmin.menu.${action}`),
    onClick: () => {
      if (!busy) {
        onSelect(action)
      }
    },
  }))

  return (
    <span className="roleadmin-row-actions" data-testid={`roleadmin-row-actions-${role.roleId}`}>
      <Link
        to={roleDetailPath(role.roleId)}
        className="roleadmin-link"
        aria-label={t('roleadmin.action.openName', { name: role.name })}
        data-testid={`roleadmin-open-${role.roleId}`}
      >
        {t('roleadmin.action.open')}
      </Link>
      <span ref={containerRef} className="roleadmin-row-menu">
        <Dropdown
          trigger={
            <Button variant="secondary" size="sm" loading={busy} aria-label={name}>
              {t('roleadmin.actions.button')}
            </Button>
          }
          items={items}
          // 「操作」は表の右端の列に置くため、メニューの右端を trigger の右端にそろえる（project.md の学び）。
          placement="bottom-end"
        />
      </span>
    </span>
  )
}
