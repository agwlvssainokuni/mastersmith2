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
// 時点の文字（LC3）。時点の文字 YYYY-MM-DDTHH:mm:ss[.fraction](Z|z|±hh:mm) の厳密な読み取り（SD1 の順序: 型 → 長さ →
// 固定の正規表現 → 範囲）と、UTC の時点の文字の組み立て、元の時点の保持（preserveInstant）、時点の比べ（compareInstants）。
// 端数は元の文字のまま（桁を変えない）。例外は投げない。暦の計算は wallClock の日数の計算を使う（Date の繰り上げに頼らない）。

import type { CompareResult } from './types'
import {
  MAX_YEAR,
  MIN_YEAR,
  daysInMonth,
  isYearInRange,
  secondsToWall,
  wallToSeconds,
} from './wallClock'

/** 時点の文字の長さの上限（書式の最長は 35 文字。SD1.2） */
export const MAX_INSTANT_TEXT_LENGTH = 40

/** 時点の文字の書式（先頭と末尾に固定。量指定子を入れ子にしない。数字は [0-9] だけ。SD1.3） */
const INSTANT_PATTERN =
  /^([0-9]{4})-([0-9]{2})-([0-9]{2})T([0-9]{2}):([0-9]{2}):([0-9]{2})(?:\.([0-9]{1,9}))?(Z|z|[+-][0-9]{2}:[0-9]{2})$/

/** 読み取った時点（epochSeconds は UTC の秒、fraction は元の端数の数字。端数なしは空の文字） */
export interface ParsedInstant {
  readonly epochSeconds: number
  readonly fraction: string
}

/**
 * 時点の文字を読む。読めない（型・長さ・書式・範囲の外れ。UTC に直した年が 0001〜9999 の外も含む）ときは null。
 */
export function parseInstantText(raw: unknown): ParsedInstant | null {
  if (typeof raw !== 'string' || raw.length > MAX_INSTANT_TEXT_LENGTH) {
    return null
  }
  const match = INSTANT_PATTERN.exec(raw)
  if (match === null) {
    return null
  }
  const [, y, mo, d, h, mi, s, fraction = '', zone = ''] = match
  const year = Number(y)
  const month = Number(mo)
  const day = Number(d)
  const hour = Number(h)
  const minute = Number(mi)
  const second = Number(s)
  if (
    year < MIN_YEAR ||
    year > MAX_YEAR ||
    month < 1 ||
    month > 12 ||
    day < 1 ||
    day > daysInMonth(year, month) ||
    hour > 23 ||
    minute > 59 ||
    second > 59
  ) {
    return null
  }
  let offset = 0
  if (zone !== 'Z' && zone !== 'z') {
    const offsetHour = Number(zone.slice(1, 3))
    const offsetMinute = Number(zone.slice(4, 6))
    if (offsetHour > 23 || offsetMinute > 59) {
      return null
    }
    offset = (zone.startsWith('-') ? -1 : 1) * (offsetHour * 3600 + offsetMinute * 60)
  }
  const epochSeconds = wallToSeconds({ year, month, day, hour, minute, second }) - offset
  if (!isYearInRange(secondsToWall(epochSeconds).year)) {
    return null
  }
  return { epochSeconds, fraction }
}

function pad(value: number, length: number): string {
  return String(value).padStart(length, '0')
}

/** UTC の秒と端数から、UTC の時点の文字 YYYY-MM-DDTHH:mm:ss[.fraction]+00:00 を組み立てる（年は 0001〜9999 の前提） */
export function formatUtcInstant(epochSeconds: number, fraction: string): string {
  const w = secondsToWall(epochSeconds)
  const base = `${pad(w.year, 4)}-${pad(w.month, 2)}-${pad(w.day, 2)}T${pad(w.hour, 2)}:${pad(w.minute, 2)}:${pad(w.second, 2)}`
  return `${base}${fraction === '' ? '' : `.${fraction}`}+00:00`
}

/** 時点の元の文字を、変換せずにそのまま返す（BR1.11。恒等） */
export function preserveInstant(rawInstant: string): string {
  return rawInstant
}

/** 端数を 9 桁にそろえた文字（大小の比べ用） */
function fractionKey(fraction: string): string {
  return fraction.padEnd(9, '0')
}

/**
 * 2つの時点を、時点として比べる（BR1.15）。a が前なら負、同じなら 0、後なら正。端は含む。
 * 読めない側があれば unreadable と、どちら側かを返す。
 */
export function compareInstants(a: unknown, b: unknown): CompareResult {
  const left = parseInstantText(a)
  const right = parseInstantText(b)
  if (left === null || right === null) {
    return {
      kind: 'unreadable',
      side: left === null && right === null ? 'both' : left === null ? 'a' : 'b',
    }
  }
  if (left.epochSeconds !== right.epochSeconds) {
    return { kind: 'ok', order: left.epochSeconds < right.epochSeconds ? -1 : 1 }
  }
  const leftKey = fractionKey(left.fraction)
  const rightKey = fractionKey(right.fraction)
  return { kind: 'ok', order: leftKey === rightKey ? 0 : leftKey < rightKey ? -1 : 1 }
}
