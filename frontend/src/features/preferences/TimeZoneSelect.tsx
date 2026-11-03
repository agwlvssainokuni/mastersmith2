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
// 表示のタイムゾーンの選択（Intent 261102-timezone-ui-polish の U2、LC7、BR2.17・BR2.18・BR2.23・BR2.24、PD3・PD4、SD5）。
// FormField と Select（ブラウザ標準の select）。先頭は「ブラウザに合わせる」。保存済みの名前を今のブラウザが解釈できないときは、
// 警告の Alert を出し、選択は「ブラウザに合わせる」に見せる（保存済みの値は選び直すまで変えない）。拒否された誤りは項目の下に出す。
// 名前・保存済みの名前・ブラウザが返した名前は React の文字のノードとしてだけ描く。
import { Alert, FormField, Select } from 'make-you-chic-ui'
import { forwardRef, memo, useMemo, useState } from 'react'
import { useMessages } from '../../app/i18n/I18nProvider'
import { canInterpretTimeZone } from '../../shared/format/timeZone'
import { buildTimeZoneOptions, readSupportedTimeZoneNames } from './buildTimeZoneOptions'

export interface TimeZoneSelectProps {
  /** 選ばれている名前（null は「ブラウザに合わせる」） */
  value: string | null
  onChange: (value: string | null) => void
  /** 送信中は選べない */
  disabled: boolean
  /** 拒否された誤りの文（無ければ undefined） */
  error: string | undefined
  /** ブラウザの名前の一覧（テストで差し替える。既定は Intl の一覧） */
  names?: readonly string[]
  /** 画面を開いた時点の時刻を返す関数（テストで固定する） */
  now?: () => Date
}

function currentTime(): Date {
  return new Date()
}

/** 表示のタイムゾーンの選択 */
export const TimeZoneSelect = memo(
  forwardRef<HTMLSelectElement, TimeZoneSelectProps>(function TimeZoneSelect(
    { value, onChange, disabled, error, names, now = currentTime },
    ref,
  ) {
    const t = useMessages()
    // 名前の一覧と開いた時点の時刻は、画面を開いたときに 1 回だけ決める（再描画で作り直さない）。
    const [opened] = useState(() => ({ names: names ?? readSupportedTimeZoneNames(), at: now() }))
    const browserLabel = t('preferences.timeZone.browser')
    const options = useMemo(
      () => buildTimeZoneOptions(opened.names, value, opened.at, browserLabel),
      [opened, value, browserLabel],
    )
    const unresolvable = value !== null && value !== '' && !canInterpretTimeZone(value)
    const shown = value === null || unresolvable ? '' : value
    return (
      <>
        {unresolvable && (
          <div data-testid="preferences-time-zone-unresolvable">
            <Alert variant="warning">{t('preferences.timeZone.unresolvable')}</Alert>
          </div>
        )}
        <FormField
          label={t('preferences.timeZone')}
          helperText={t('preferences.timeZone.hint')}
          error={error}
        >
          <Select
            ref={ref}
            options={options.map((option) => ({ value: option.value, label: option.label }))}
            value={shown}
            onChange={(next) => onChange(next === '' ? null : next)}
            disabled={disabled}
            data-testid="preferences-time-zone-select"
          />
        </FormField>
      </>
    )
  }),
)
