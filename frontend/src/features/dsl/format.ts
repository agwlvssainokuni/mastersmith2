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
// 表示の書式（BR5.10・BR6.3）。識別は先頭 12 文字、日時は端末の時差で時差の略号を添える、ファイルの大きさ。
import type { DisplayLanguage } from '../../app/i18n/resolveLanguage'

/** 画面に示す識別の長さ */
export const SHORT_HASH_LENGTH = 12

/** 識別を先頭 12 文字に縮める。 */
export function shortHash(hash: string): string {
  return hash.slice(0, SHORT_HASH_LENGTH)
}

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
export function formatDateTime(iso: string, language: DisplayLanguage, timeZone?: string): string {
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

/** ファイルの大きさを人が読める形にする（1024 を単位とする）。 */
export function formatBytes(bytes: number): string {
  if (bytes < 1024) {
    return `${bytes}B`
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)}KB`
  }
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`
}
