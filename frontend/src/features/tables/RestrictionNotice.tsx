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
// 行えない操作の理由と、書き込みの設定が無い旨の表示（Intent 261016-business-data-views の U3 の L7、D13、
// AC3.2.1〜AC3.2.3・AC3.2.5・AC3.3.9）。Alert（info）で一覧の上に出す。どのカラムが NONE かは出さない。
// - 書き込みの設定が無い旨は、書き込みの操作が許される人にだけ出す（B5 の承認の場の依頼者の決定 N-5。機能設計 D13 の
//   「writeConfigured が false なら出す」との差）。サーバーは書き込みの設定が無いと operations に CREATE・UPDATE・DELETE を
//   入れず、補助権限（CREATE・DELETE）も返さないため、画面は「ビューでなく、詳細のカラムに作成か更新で入力できるもの
//   （主権限 FULL）がある」ことで近く判定する（canWriteWhenConfigured。generation-notes の B6 の差）。
import { Alert } from 'make-you-chic-ui'
import { useTranslation } from 'react-i18next'
import type { RestrictionReason, TableDefinition } from './businessDataTypes'

/**
 * 書き込みの設定があれば書き込みの操作が許されるはずの人か（N-5 の近い判定）。
 *
 * @param definition テーブルの定義
 */
export function canWriteWhenConfigured(
  definition: Pick<TableDefinition, 'view' | 'detailColumns'>,
): boolean {
  return (
    !definition.view &&
    definition.detailColumns.some((column) => column.editableOnCreate || column.editableOnUpdate)
  )
}

export interface RestrictionNoticeProps {
  /** 行えない操作の理由（無ければ null） */
  reason: RestrictionReason | null
  /** 書き込みの接続の設定があるか */
  writeConfigured: boolean
  /** 書き込みの設定があれば書き込みの操作が許されるはずか（無ければ書き込みの設定が無い旨を出さない。N-5） */
  writeAllowed: boolean
}

/** 理由の文言の鍵 */
export function restrictionMessageKey(reason: RestrictionReason): string {
  return reason === 'VIEW' ? 'tables.restriction.view' : 'tables.restriction.noPrimaryKey'
}

/** 行えない操作の理由の表示 */
export function RestrictionNotice({
  reason,
  writeConfigured,
  writeAllowed,
}: RestrictionNoticeProps) {
  const { t } = useTranslation()
  const showWrite = !writeConfigured && writeAllowed
  if (reason === null && !showWrite) {
    return null
  }
  return (
    <div className="tables-notices" data-testid="tables-restriction">
      {reason !== null && <Alert variant="info">{t(restrictionMessageKey(reason))}</Alert>}
      {showWrite && <Alert variant="info">{t('tables.writeNotConfigured')}</Alert>}
    </div>
  )
}
