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
// 行の「操作」（Intent 261016-business-data-views の U3 の L7、D9、AC4.1.1、U3-NFR4.21・5.7・5.8）。
// - make-you-chic-ui の Dropdown を placement="bottom-end" で置く（表の右端の列で画面の右へはみ出さない）。
// - トリガーの読み上げの名前は U4 の rowActionsName で「操作: <行のラベル>」にする（見える文字で始める。WCAG 2.5.3）。
//   busy のときは末尾に「（処理中）」を足し、Button を loading にする（メニューは開かない。フォーカスは残る）。
// - data-testid は Dropdown を包む span に tables-row-actions-r<n>（ページの中の番号）で置く。Dropdown はトリガーの
//   data-testid を上書きするため、トリガーには置けない。行の値・主キーを data-testid・id に入れない（U4 の R-02）。
// - 項目は許す操作だけ。B5 は「詳細」だけで、B6 で「更新」「削除」を足す。画面で出し分けるのは表示だけで、判定はサーバー。
import { Button, Dropdown, type MenuItem } from 'make-you-chic-ui'
import { useTranslation } from 'react-i18next'
import { rowActionsName } from '../../shared/row-actions/rowActionsName'
import type { Operation } from './businessDataTypes'

/** 行の操作の項目 */
export type RowAction = 'detail'

export interface RowActionsMenuProps {
  /** ページの中の行の番号（data-testid の末尾） */
  index: number
  /** 行のラベル（rowLabel の関数の結果） */
  rowLabel: string
  /** 許す操作 */
  operations: readonly Operation[]
  /** 読み直しの間 */
  busy: boolean
  /** 項目を選んだ */
  onSelect: (action: RowAction) => void
}

/** 許す操作から出す項目 */
export function rowActionItems(operations: readonly Operation[]): RowAction[] {
  return operations.includes('DETAIL') ? ['detail'] : []
}

/** 行の「操作」 */
export function RowActionsMenu({
  index,
  rowLabel,
  operations,
  busy,
  onSelect,
}: RowActionsMenuProps) {
  const { t } = useTranslation()
  const actions = rowActionItems(operations)
  if (actions.length === 0) {
    return null
  }
  const baseName = rowActionsName(t('tables.actions.button'), rowLabel)
  const name = busy ? `${baseName}${t('tables.actions.busySuffix')}` : baseName
  const items: MenuItem[] = actions.map((action) => ({
    label: t(`tables.actions.${action}`),
    onClick: () => {
      if (!busy) {
        onSelect(action)
      }
    },
  }))
  return (
    <span className="tables-row-actions" data-testid={`tables-row-actions-r${index}`}>
      <Dropdown
        trigger={
          <Button variant="secondary" size="sm" loading={busy} aria-label={name}>
            {t('tables.actions.button')}
          </Button>
        }
        items={items}
        placement="bottom-end"
      />
    </span>
  )
}
