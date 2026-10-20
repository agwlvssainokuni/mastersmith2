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
import { Alert } from 'make-you-chic-ui'
import { useTranslation } from 'react-i18next'
import type { RestrictionReason } from './businessDataTypes'

export interface RestrictionNoticeProps {
  /** 行えない操作の理由（無ければ null） */
  reason: RestrictionReason | null
  /** 書き込みの接続の設定があるか */
  writeConfigured: boolean
}

/** 理由の文言の鍵 */
export function restrictionMessageKey(reason: RestrictionReason): string {
  return reason === 'VIEW' ? 'tables.restriction.view' : 'tables.restriction.noPrimaryKey'
}

/** 行えない操作の理由の表示 */
export function RestrictionNotice({ reason, writeConfigured }: RestrictionNoticeProps) {
  const { t } = useTranslation()
  if (reason === null && writeConfigured) {
    return null
  }
  return (
    <div className="tables-notices" data-testid="tables-restriction">
      {reason !== null && <Alert variant="info">{t(restrictionMessageKey(reason))}</Alert>}
      {!writeConfigured && <Alert variant="info">{t('tables.writeNotConfigured')}</Alert>}
    </div>
  )
}
