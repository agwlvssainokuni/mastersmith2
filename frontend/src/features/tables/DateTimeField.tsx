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
// 日時の欄（Intent 261102-timezone-ui-polish の U4、計画 Step 12、D13〜D15・D18・D21・D24・D39・D40、frontend-components.md の
// 3節、security-design.md 2.6、NFR 設計の Q2 の 1・2）。作成・更新・検索の画面・ピッカーで使い回す。
// - 入力は文字の欄（TextInput、type="text"・autocomplete="off"）。ブラウザの datetime-local は使わない。
// - 案内（FormField の helperText。aria-describedby で結ぶ）: 時点を表す型は「{名前} ({今の時差}) で入力。…」、時差を持たない
//   日時は書式の例だけ（タイムゾーンを出さない）。今の時差は、呼ぶ側が開いたときに1回取った今の時刻（now）で決める。
//   FormField は誤りの間は案内を出さないため、誤りの文は書式やタイムゾーンの名前を自分で持つ（Q2 の 2）。
// - 表示のタイムゾーンを使えない間（loading・error）は、時点を表す型の欄を使えない状態にし、欄の下に短い文を出す（D11）。
// - 夏時間で2回ある時刻の2択は、時刻の欄の FormField とは別の FormField（ラベルは legend と同じ文、誤りは未選択の文）に、
//   見えない legend の RadioGroup を1つだけ置く（FormField に入力を2つ入れると同じ id が2つの部品に付くため。Q2 の 1）。
//   出すかどうか・2つの時差は、呼ぶ側が判定の結果（resolveDateTimeInput）から渡す（変換はこの部品で行わない）。
// - 文字はすべて React の文字のノードとして描く（HTML として描かない）。DOM の id・name・data-testid に値を入れない。
import { FormField, RadioGroup, TextInput } from 'make-you-chic-ui'
import type { RefObject } from 'react'
import { useTranslation } from 'react-i18next'
import type { OverlapChoice } from '../../shared/format/timeZone'
import {
  offsetOf,
  type DateTimeKind,
  type DateTimePurpose,
  type OverlapOffsets,
} from './resolveDateTimeInput'
import type { FieldTimeZone } from './useTablesTimeZone'
import './tablesCommon.css'
import './DateTimeField.css'

/** 文言を引く関数 */
type Translate = (key: string, options?: Record<string, string>) => string

/** 用途ごとの案内の鍵（時点を表す型・時差を持たない日時） */
const HINT_KEYS: Readonly<Record<DateTimePurpose, { instant: string; local: string }>> = {
  form: { instant: 'tables.dateTime.hint.instant', local: 'tables.dateTime.hint.local' },
  'range-from': {
    instant: 'tables.dateTime.hint.instantRange',
    local: 'tables.dateTime.hint.localRange',
  },
  'range-to': {
    instant: 'tables.dateTime.hint.instantRange',
    local: 'tables.dateTime.hint.localRange',
  },
  equals: {
    instant: 'tables.dateTime.hint.instantEquals',
    local: 'tables.dateTime.hint.localEquals',
  },
}

/**
 * 日時の欄の案内の文を作る（D15・D24）。時点を表す型で表示のタイムゾーンを使えない間は短い文（D11）。
 *
 * @param t 文言を引く関数
 * @param kind 欄の種類
 * @param purpose 欄の用途
 * @param timeZone 表示のタイムゾーンの状態
 * @param now 今の時刻（ISO 8601。今の時差に使う）
 */
export function dateTimeHint(
  t: Translate,
  kind: DateTimeKind,
  purpose: DateTimePurpose,
  timeZone: FieldTimeZone,
  now: string,
): string {
  const keys = HINT_KEYS[purpose]
  if (kind === 'local') {
    return t(keys.local)
  }
  switch (timeZone.status) {
    case 'loading':
      return t('tables.dateTime.loading')
    case 'error':
      return t('tables.error.TIME_ZONE_UNAVAILABLE')
    case 'ready':
      return t(keys.instant, {
        zone: timeZone.timeZone,
        offset: offsetOf(now, timeZone.timeZone),
      })
  }
}

/**
 * 日時の欄を使えるか（時点を表す型は表示のタイムゾーンが ready のときだけ。時差を持たない日時はいつでも）。
 *
 * @param kind 欄の種類
 * @param timeZone 表示のタイムゾーンの状態
 */
export function isDateTimeUsable(kind: DateTimeKind, timeZone: FieldTimeZone): boolean {
  return kind === 'local' || timeZone.status === 'ready'
}

/** 2択の値を読む（知らない値は null） */
function toOverlapChoice(value: string): OverlapChoice | null {
  return value === 'earlier' || value === 'later' ? value : null
}

export interface DateTimeFieldProps {
  kind: DateTimeKind
  purpose: DateTimePurpose
  /** 欄のラベル（必須の印は呼ぶ側の文に入れる） */
  label: string
  value: string
  onChange: (value: string) => void
  onBlur?: () => void
  /** 表示のタイムゾーンの状態（作成・更新はフォームを開いたときに固定した値、検索は今の値） */
  timeZone: FieldTimeZone
  /** 今の時刻（ISO 8601。案内の今の時差に使う。呼ぶ側が開いたときに1回だけ取る） */
  now: string
  /** 欄の誤りの文 */
  error?: string
  /** 2択を出すときの2つの時差（出さないときは null） */
  overlap: OverlapOffsets | null
  /** 2択の選択（未選択は null） */
  overlapChoice: OverlapChoice | null
  onOverlapChoice: (choice: OverlapChoice) => void
  /** 2択の誤りの文（未選択で保存・検索を押したとき） */
  overlapError?: string
  /** 2択の入れ物（未選択の誤りのとき、最初の選択肢へフォーカスを移すのに使う） */
  overlapRef?: RefObject<HTMLDivElement | null>
  /** 欄の印の番号から作る名前（2択の name。値を入れない） */
  name: string
  /** 必須か（aria-required。印の文はラベルに入れる） */
  required?: boolean
  /** 保存中などで使えない */
  disabled?: boolean
  /** 欄の下の案内を出すか（範囲の検索は両端の案内を入れ物に1つだけ出す） */
  showHint?: boolean
  /** 欄を外の文に結ぶ（範囲の検索の案内と誤り。渡したときは FormField の結びの代わりに使う） */
  describedBy?: string
  /** 欄を誤りの状態にする（範囲の検索の入れ物の誤り） */
  invalid?: boolean
  /** 時刻の欄の要素を受け取る（誤りの欄へフォーカスを移すのに使う） */
  inputRef?: (element: HTMLInputElement | null) => void
  testId?: string
}

/** 日時の欄（時刻の文字の欄と、夏時間の2択） */
export function DateTimeField({
  kind,
  purpose,
  label,
  value,
  onChange,
  onBlur,
  timeZone,
  now,
  error,
  overlap,
  overlapChoice,
  onOverlapChoice,
  overlapError,
  overlapRef,
  name,
  required = false,
  disabled = false,
  showHint = true,
  describedBy,
  invalid,
  inputRef,
  testId,
}: DateTimeFieldProps) {
  const { t } = useTranslation()
  const usable = isDateTimeUsable(kind, timeZone)
  const hint = showHint ? dateTimeHint(t, kind, purpose, timeZone, now) : undefined
  const zone = timeZone.status === 'ready' ? timeZone.timeZone : ''
  const legend = t('tables.dateTime.overlapLegend', { zone })
  const linked =
    describedBy === undefined
      ? {}
      : { 'aria-describedby': describedBy, 'aria-invalid': invalid === true ? true : undefined }
  return (
    <div className="tables-date-time" data-testid={testId}>
      <FormField label={label} error={error} helperText={hint}>
        <TextInput
          ref={inputRef}
          type="text"
          autoComplete="off"
          value={value}
          onChange={onChange}
          onBlur={onBlur}
          disabled={disabled || !usable}
          aria-required={required ? true : undefined}
          {...linked}
        />
      </FormField>
      {overlap !== null && usable && (
        <div
          ref={overlapRef}
          className="tables-date-time-overlap"
          data-testid={testId === undefined ? undefined : `${testId}-overlap`}
        >
          <FormField label={legend} error={overlapError}>
            <RadioGroup
              name={`${name}-overlap`}
              options={[
                {
                  value: 'earlier',
                  label: t('tables.dateTime.earlier', { offset: overlap.earlier }),
                },
                { value: 'later', label: t('tables.dateTime.later', { offset: overlap.later }) },
              ]}
              value={overlapChoice ?? ''}
              onChange={(next) => {
                const choice = toOverlapChoice(next)
                if (choice !== null) {
                  onOverlapChoice(choice)
                }
              }}
              disabled={disabled}
              legend={<span className="tables-visually-hidden">{legend}</span>}
            />
          </FormField>
        </div>
      )}
    </div>
  )
}
