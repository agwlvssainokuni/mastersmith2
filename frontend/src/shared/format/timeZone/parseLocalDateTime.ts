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
// ローカル時刻の変換（LC5、BR1.6〜BR1.9・BR1.12）。表示のタイムゾーンのローカル時刻 YYYY-MM-DD HH:mm:ss[.1〜6桁] を
// UTC の時点（+00:00 の文字）に直す。名前の検査 → 書式 → 範囲 → 候補の時点（0・1・2 通り）の順。
// 夏時間で存在しない時刻は gap、重なる時刻は overlap（overlapChoice があれば選んだ側の ok）。
// UTC に直した年が 0001〜9999 を外れるときは invalid の out-of-range にする（5桁の年の文字を作らない）。例外は投げない。

import { formatUtcInstant } from './instantText'
import { getFormatter } from './timeZoneGate'
import type { OverlapChoice, ParseResult } from './types'
import {
  MAX_YEAR,
  MIN_YEAR,
  daysInMonth,
  instantCandidates,
  isYearInRange,
  secondsToWall,
} from './wallClock'

/** ローカル時刻の文字の長さの上限（書式の最長 YYYY-MM-DD HH:mm:ss.123456 は 26 文字。SD1.2） */
export const MAX_LOCAL_DATE_TIME_LENGTH = 26

/** ローカル時刻の書式（先頭と末尾に固定。区切りは半角の空白1つ。数字は [0-9] だけ。SD1.3） */
const LOCAL_PATTERN =
  /^([0-9]{4})-([0-9]{2})-([0-9]{2}) ([0-9]{2}):([0-9]{2}):([0-9]{2})(?:\.([0-9]{1,6}))?$/

/**
 * ローカル時刻を時点に直す。
 *
 * @param text ローカル時刻の文字（YYYY-MM-DD HH:mm:ss[.1〜6桁]）
 * @param timeZone IANA の名前（必ず明示に渡す。解釈できなければ invalid-time-zone）
 * @param overlapChoice 重なる時刻のときだけ使う選択（重ならない時刻では無視する）
 */
export function parseLocalDateTime(
  text: string,
  timeZone: string,
  overlapChoice?: OverlapChoice,
): ParseResult {
  if (getFormatter(timeZone) === null) {
    return { kind: 'invalid-time-zone' }
  }
  if (typeof text !== 'string' || text.length > MAX_LOCAL_DATE_TIME_LENGTH) {
    return { kind: 'invalid', reason: 'format' }
  }
  const match = LOCAL_PATTERN.exec(text)
  if (match === null) {
    return { kind: 'invalid', reason: 'format' }
  }
  const [, y, mo, d, h, mi, s, fraction = ''] = match
  const wall = {
    year: Number(y),
    month: Number(mo),
    day: Number(d),
    hour: Number(h),
    minute: Number(mi),
    second: Number(s),
  }
  if (
    wall.year < MIN_YEAR ||
    wall.year > MAX_YEAR ||
    wall.month < 1 ||
    wall.month > 12 ||
    wall.day < 1 ||
    wall.day > daysInMonth(wall.year, wall.month) ||
    wall.hour > 23 ||
    wall.minute > 59 ||
    wall.second > 59
  ) {
    return { kind: 'invalid', reason: 'out-of-range' }
  }
  const candidates = instantCandidates(timeZone, wall)
  if (candidates === null) {
    // 名前の検査を通ったのに Intl が壁時計を求められなかった（極端な値）。範囲外として返す（SD1.5）
    return { kind: 'invalid', reason: 'out-of-range' }
  }
  if (candidates.length === 0) {
    return { kind: 'gap' }
  }
  const selected =
    candidates.length >= 2 && (overlapChoice === 'earlier' || overlapChoice === 'later')
      ? [overlapChoice === 'later' ? candidates[candidates.length - 1] : candidates[0]]
      : candidates
  if (
    selected.some((seconds) => seconds === undefined || !isYearInRange(secondsToWall(seconds).year))
  ) {
    return { kind: 'invalid', reason: 'out-of-range' }
  }
  const [first, second] = selected.map((seconds) => formatUtcInstant(seconds as number, fraction))
  if (second !== undefined && first !== undefined) {
    return { kind: 'overlap', earlier: first, later: second }
  }
  return { kind: 'ok', instant: first as string }
}
