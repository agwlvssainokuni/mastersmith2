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
// 表示の整形（LC4、BR1.1〜BR1.5）。時点の文字を、表示のタイムゾーンの時刻と時差の文字
// YYYY-MM-DD HH:mm:ss[.fraction] ±HH:mm[:ss] に直す。名前の検査 → 時点の読み取り → 壁時計と時差 → 文字の組み立ての順。
// 例外は投げない。結果の文字は HTML ではない、ただの文字（呼ぶ側は文字として出す。SD2）。

import { parseInstantText } from './instantText'
import { getFormatter } from './timeZoneGate'
import type { FormatResult } from './types'
import { isYearInRange, offsetSecondsAt, wallAt } from './wallClock'

function pad(value: number, length: number): string {
  return String(value).padStart(length, '0')
}

/** 時差の秒を ±HH:mm（秒が 0 でないときだけ ±HH:mm:ss）にする */
function formatOffset(offsetSeconds: number): string {
  const sign = offsetSeconds < 0 ? '-' : '+'
  const abs = Math.abs(offsetSeconds)
  const hours = Math.floor(abs / 3600)
  const minutes = Math.floor((abs % 3600) / 60)
  const seconds = abs % 60
  const base = `${sign}${pad(hours, 2)}:${pad(minutes, 2)}`
  return seconds === 0 ? base : `${base}:${pad(seconds, 2)}`
}

/**
 * 時点を表示のタイムゾーンの文字に直す。
 *
 * @param instant 時点の文字（YYYY-MM-DDTHH:mm:ss[.fraction](Z|z|±hh:mm)）
 * @param timeZone IANA の名前（必ず明示に渡す。解釈できなければ invalid-time-zone）
 * @param fractionDigits 端数の桁（省略すると文字の桁のまま。元の桁が短いときだけ 0 を足す）
 */
export function formatInstant(
  instant: string,
  timeZone: string,
  fractionDigits?: number,
): FormatResult {
  if (getFormatter(timeZone) === null) {
    return { kind: 'invalid-time-zone' }
  }
  const raw = typeof instant === 'string' ? instant : ''
  const parsed = parseInstantText(instant)
  if (parsed === null) {
    return { kind: 'unreadable', raw }
  }
  const wall = wallAt(timeZone, parsed.epochSeconds)
  const offset = offsetSecondsAt(timeZone, parsed.epochSeconds)
  // 表示のタイムゾーンへ直した年が 0001〜9999 を外れると YYYY に収まらず読み戻せないため、読めない値として返す（Q2: A）
  if (wall === null || offset === null || !isYearInRange(wall.year)) {
    return { kind: 'unreadable', raw }
  }
  const wanted =
    typeof fractionDigits === 'number' && Number.isInteger(fractionDigits) && fractionDigits > 0
      ? Math.min(fractionDigits, 9)
      : 0
  const fraction =
    parsed.fraction.length < wanted ? parsed.fraction.padEnd(wanted, '0') : parsed.fraction
  const date = `${pad(wall.year, 4)}-${pad(wall.month, 2)}-${pad(wall.day, 2)}`
  const time = `${pad(wall.hour, 2)}:${pad(wall.minute, 2)}:${pad(wall.second, 2)}${fraction === '' ? '' : `.${fraction}`}`
  return { kind: 'ok', text: `${date} ${time} ${formatOffset(offset)}` }
}
