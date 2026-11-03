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
// ロックの解除の予定の時刻の書式（Intent 261102-timezone-ui-polish の U2、BR2.19、旧い lockedUntil.ts の置き換え）。応答の
// ISO 8601 の UTC を受け、表示のタイムゾーンで今日なら時刻と時差だけ（`10:42 +09:00`）、今日でなければ分までの書式
// （`2026-11-04 10:42 +09:00`）にする。今日かの比べは、表示のタイムゾーンでの「今日」の日付（1 ページにつき 1 回だけ求めて渡す）と、
// 時刻の日付の比べで行う。ロックしているかは応答の locked をそのまま使い、ここで時刻を比べて判定しない（D4）。
// 読めない値・解釈できないタイムゾーンは元の文字をそのまま返す。時差は必ず引数で受ける（ブラウザの既定を使わない）。
import { formatInstantToMinutes } from '../../shared/format/formatInstantToMinutes'

/** 分までの書式: 日付 時:分 時差 */
const MINUTES = /^(\d{4,}-\d{2}-\d{2}) (\d{2}:\d{2}) ([+-]\d{2}:\d{2}(?::\d{2})?)$/

/**
 * 表示のタイムゾーンでの「今日」の日付（YYYY-MM-DD）を求める。求められなければ null。
 *
 * @param now 今の時刻
 * @param timeZone 表示のタイムゾーン
 */
export function todayIn(now: Date, timeZone: string): string | null {
  const match = MINUTES.exec(formatInstantToMinutes(now.toISOString(), timeZone))
  return match === null ? null : (match[1] ?? null)
}

/**
 * 解除の予定の時刻を書式にする（「まで」は文言の側で添える）。
 *
 * @param iso 解除の予定の時刻（ISO 8601 の UTC）
 * @param timeZone 表示のタイムゾーン
 * @param today 表示のタイムゾーンでの今日の日付（{@link todayIn} の結果。求められなければ null）
 */
export function lockedUntilInZone(iso: string, timeZone: string, today: string | null): string {
  const text = formatInstantToMinutes(iso, timeZone)
  const match = MINUTES.exec(text)
  if (match === null || today === null || match[1] !== today) {
    return text
  }
  return `${match[2]} ${match[3]}`
}
