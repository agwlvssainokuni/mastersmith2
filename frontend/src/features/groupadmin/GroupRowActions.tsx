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
// グループの一覧の行の操作（Intent 261004-role-menu の U6 の D6、functional-spec.md の W6.1、frontend-components.md の 4節。
// roleadmin の RoleRowActions と同じ形。
// の 8節）。「開く」（S4 へのリンク、読み上げの名前に対象の名前）と、Dropdown（placement="bottom-end"）の「名前を変える」
// 「削除」。削除は割り当ての数によらず押せ、判定はサーバー（D6）。trigger の名前は見える文字で始める「操作: 〔名前〕」
// （Intent 261016-business-data-views の U4 の D1〜D4、src/shared/row-actions の rowActionsName。WCAG 2.5.3）、busy なら
// 「（処理中）」を添え、Button の loading でメニューを開かない（既存の UserRowActions と同じ形）。
// Dropdown は trigger の ref を置き換えるため、フォーカスを戻す先は包む要素の中のボタンから探す。
import { Button, Dropdown, type MenuItem } from 'make-you-chic-ui'
import { Link } from 'react-router'
import { rowActionsName } from '../../shared/row-actions/rowActionsName'
import type { GroupRow } from './api/types'
import { groupDetailPath } from './paths'
import { useGroupAdminText } from './useGroupAdminText'
import './GroupListPage.css'

/** 行の Dropdown の項目 */
export type GroupRowAction = 'rename' | 'delete'

export interface GroupRowActionsProps {
  /** 対象の行 */
  group: GroupRow
  /** 一覧の読み直しの間、または送信中 */
  busy: boolean
  /** 項目を選んだ */
  onSelect: (action: GroupRowAction) => void
  /** Dropdown の trigger を包む要素（フォーカスを戻すときに中のボタンを探す） */
  containerRef?: (element: HTMLSpanElement | null) => void
}

/** グループの一覧の行の操作 */
export function GroupRowActions({ group, busy, onSelect, containerRef }: GroupRowActionsProps) {
  const t = useGroupAdminText()
  const baseName = rowActionsName(t('groupadmin.actions.button'), group.name)
  const name = busy ? `${baseName}${t('groupadmin.actions.busySuffix')}` : baseName
  const items: MenuItem[] = (['rename', 'delete'] as const).map((action) => ({
    label: t(`groupadmin.menu.${action}`),
    onClick: () => {
      if (!busy) {
        onSelect(action)
      }
    },
  }))

  return (
    <span
      className="groupadmin-row-actions"
      data-testid={`groupadmin-row-actions-${group.groupId}`}
    >
      <Link
        to={groupDetailPath(group.groupId)}
        className="groupadmin-link"
        aria-label={t('groupadmin.action.openName', { name: group.name })}
        data-testid={`groupadmin-open-${group.groupId}`}
      >
        {t('groupadmin.action.open')}
      </Link>
      <span ref={containerRef} className="groupadmin-row-menu">
        <Dropdown
          trigger={
            <Button variant="secondary" size="sm" loading={busy} aria-label={name}>
              {t('groupadmin.actions.button')}
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
