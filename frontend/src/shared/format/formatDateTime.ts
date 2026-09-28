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
// 日時の書式（DSL の管理画面の BR5.10・BR6.3、招待の管理の画面の D6）。features/dsl/format.ts から移した共通の関数で、
// DSL と招待の2つの機能が使う（U5 の機能設計の Q1 A・9節の (d)）。shared は app に依存しない（ApiClient と同じ）ため、
// 言語の型はこのファイルに置く（U4 の DisplayLanguage と同じ値）。ふるまいは移す前と同じ。

/** 書式の言語 */
export type FormatLanguage = 'ja' | 'en'

function part(parts: Intl.DateTimeFormatPart[], type: Intl.DateTimeFormatPartTypes): string {
  return parts.find((p) => p.type === type)?.value ?? ''
}

/**
 * UTC の日時を、表示言語に合わせた書式で、端末（または指定）の時差で示し、時差の略号を添える。
 * 日本語は `2026-09-24 10:15 JST`、英語は `Sep 24, 2026, 10:15 GMT+9` の形。読めない値はそのまま返す。
 *
 * @param iso ISO 8601 の日時
 * @param language 表示言語
 * @param timeZone 時差（省略すると端末の時差。テストで固定する）
 */
export function formatDateTime(iso: string, language: FormatLanguage, timeZone?: string): string {
  const date = new Date(iso)
  if (Number.isNaN(date.getTime())) {
    return iso
  }
  const locale = language === 'ja' ? 'ja-JP' : 'en-US'
  const parts = new Intl.DateTimeFormat(locale, {
    timeZone,
    year: 'numeric',
    month: language === 'ja' ? '2-digit' : 'short',
    day: language === 'ja' ? '2-digit' : 'numeric',
    hour: '2-digit',
    minute: '2-digit',
    hourCycle: 'h23',
    timeZoneName: 'short',
  }).formatToParts(date)
  const time = `${part(parts, 'hour')}:${part(parts, 'minute')}`
  const zone = part(parts, 'timeZoneName')
  if (language === 'ja') {
    return `${part(parts, 'year')}-${part(parts, 'month')}-${part(parts, 'day')} ${time} ${zone}`
  }
  return `${part(parts, 'month')} ${part(parts, 'day')}, ${part(parts, 'year')}, ${time} ${zone}`
}
