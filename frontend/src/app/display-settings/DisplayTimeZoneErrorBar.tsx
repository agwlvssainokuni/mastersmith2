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
// 表示のタイムゾーンの読み込み失敗の知らせ（Intent 261102-timezone-ui-polish の U2、LC8、BR2.20）。管理の画面の上の 1 か所に置き、
// 提供元が error のときだけ短い文と「もう一度読み込む」を出す。error 以外では何も描かない。
import { Alert, Button } from 'make-you-chic-ui'
import { useMessages } from '../i18n/I18nProvider'
import { useDisplayTimeZone, useDisplayTimeZoneActions } from './DisplayTimeZoneProvider'
import './DisplayTime.css'

/** 読み込み失敗のときだけ知らせと再読み込みを出す。 */
export function DisplayTimeZoneErrorBar() {
  const state = useDisplayTimeZone()
  const { reload } = useDisplayTimeZoneActions()
  const t = useMessages()
  if (state.status !== 'error') {
    return null
  }
  return (
    <div className="display-time-error-bar" data-testid="display-time-zone-error">
      <Alert variant="warning">{t('displayTime.errorBar')}</Alert>
      <div>
        <Button variant="secondary" onClick={reload} data-testid="display-time-zone-reload">
          {t('displayTime.reload')}
        </Button>
      </div>
    </div>
  )
}
