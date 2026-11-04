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
// 表示のタイムゾーンの案内の1行（Intent 261102-timezone-ui-polish の U4、計画 Step 12、D9、frontend-components.md の 2節、
// mockups.md の S2）。一覧・詳細の見出しの下に置き、呼ぶ側が「時点を表す型のカラムがあり ready」のときだけ描く。
// - 文は U2 の source で3つに分ける（saved・browser・saved-unresolvable）。「プリファレンスで変更」はプリファレンスの画面への
//   リンクで、行き先は決まった道の定数だけ（値・名前から道を作らない。security-design.md 2.3）。
// - タイムゾーンの名前は文字として差し込む（HTML として描かない）。
import { useTranslation } from 'react-i18next'
import { Link } from 'react-router'
import type { DisplayTimeZoneSource } from '../../app/display-settings/decideDisplayTimeZone'
import { PREFERENCES_LINK_PATH } from './useTablesTimeZone'
import './TimeZoneNotice.css'

export interface TimeZoneNoticeProps {
  /** 表示のタイムゾーンの名前 */
  timeZone: string
  /** 名前の由来（U2） */
  source: DisplayTimeZoneSource
}

/** 由来ごとの文の鍵 */
const NOTICE_KEYS: Readonly<Record<DisplayTimeZoneSource, string>> = {
  saved: 'tables.timeZone.notice.saved',
  browser: 'tables.timeZone.notice.browser',
  'saved-unresolvable': 'tables.timeZone.notice.savedUnresolvable',
}

/** 表示のタイムゾーンの案内の1行 */
export function TimeZoneNotice({ timeZone, source }: TimeZoneNoticeProps) {
  const { t } = useTranslation()
  return (
    <p className="tables-time-zone-notice" data-testid="tables-time-zone-notice">
      {t(NOTICE_KEYS[source], { zone: timeZone })}
      {t('tables.timeZone.notice.linkBefore')}
      <Link
        className="tables-link"
        to={PREFERENCES_LINK_PATH}
        data-testid="tables-time-zone-preferences"
      >
        {t('tables.timeZone.notice.link')}
      </Link>
      {t('tables.timeZone.notice.linkAfter')}
    </p>
  )
}
