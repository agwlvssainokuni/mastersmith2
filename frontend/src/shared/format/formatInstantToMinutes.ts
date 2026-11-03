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
// 時点の文字を、表示のタイムゾーンの「分まで」の文字 YYYY-MM-DD HH:mm ±HH:mm に直す（Intent 261102-timezone-ui-polish の U2、
// BR2.19）。U1 の formatInstant の ok の文字から、秒・端数を除いて取り出す薄い関数。読めない時点は元の文字、解釈できない
// タイムゾーンの名前も元の文字をそのまま返す（例外は投げない）。結果は HTML ではない、ただの文字。
import { formatInstant } from './timeZone'

/** formatInstant の ok の文字: 日付 時:分[:秒[.端数]] 時差 */
const FORMATTED =
  /^(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2})(?::\d{2}(?:\.\d+)?)? ([+-]\d{2}:\d{2}(?::\d{2})?)$/

/**
 * 時点を分までの文字にする。
 *
 * @param instant 時点の文字
 * @param timeZone IANA の名前（必ず明示に渡す）
 */
export function formatInstantToMinutes(instant: string, timeZone: string): string {
  const result = formatInstant(instant, timeZone)
  if (result.kind === 'unreadable') {
    return result.raw
  }
  if (result.kind === 'invalid-time-zone') {
    return instant
  }
  const match = FORMATTED.exec(result.text)
  return match === null ? instant : `${match[1]} ${match[2]} ${match[3]}`
}
