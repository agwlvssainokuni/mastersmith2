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
// ロックの解除の予定の時刻の書式（functional-spec.md の D18、機能設計 10節の (b)）。応答の ISO 8601 の UTC を受け、
// 表示の時間帯で今日なら時刻と時差の略号だけ（ja `10:42 JST`、en `10:42 GMT+9`、24 時間）、今日でなければ既存の
// formatDateTime の書式で日付つきにする。今の時刻と時間帯は引数で受ける（テストで固定する。実時刻に頼らない）。
// ロックしているかは応答の locked をそのまま使い、ここで時刻を比べて判定しない（D4）。
import { formatDateTime, type FormatLanguage } from '../../shared/format/formatDateTime'

function part(parts: Intl.DateTimeFormatPart[], type: Intl.DateTimeFormatPartTypes): string {
  return parts.find((p) => p.type === type)?.value ?? ''
}

/** 時間帯での日付（年・月・日）の文字列 */
function dayOf(date: Date, timeZone?: string): string {
  const parts = new Intl.DateTimeFormat('en-US', {
    timeZone,
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).formatToParts(date)
  return `${part(parts, 'year')}-${part(parts, 'month')}-${part(parts, 'day')}`
}

/**
 * 解除の予定の時刻を書式にする（「まで」は文言の側で添える）。読めない値はそのまま返す。
 *
 * @param iso 解除の予定の時刻（ISO 8601 の UTC）
 * @param language 表示の言語
 * @param now 今の時刻（今日の判定に使う）
 * @param timeZone 時間帯（省略すると端末の時間帯。テストで固定する）
 */
export function formatLockedUntil(
  iso: string,
  language: FormatLanguage,
  now: Date,
  timeZone?: string,
): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return iso
  }
  if (dayOf(date, timeZone) !== dayOf(now, timeZone)) {
    return formatDateTime(iso, language, timeZone)
  }
  const parts = new Intl.DateTimeFormat(language === 'ja' ? 'ja-JP' : 'en-US', {
    timeZone,
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
    timeZoneName: 'short',
  }).formatToParts(date)
  return `${part(parts, 'hour')}:${part(parts, 'minute')} ${part(parts, 'timeZoneName')}`
}
