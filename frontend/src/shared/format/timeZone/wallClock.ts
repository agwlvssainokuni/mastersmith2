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
// 壁時計（LC2）。時点（UTC の秒）→ タイムゾーンの壁時計と時差の秒、壁時計 → 候補の時点（0・1・2 通り）。
// Intl.DateTimeFormat の formatToParts の数字だけを読み、時差は「壁時計の数字を UTC とみなした秒」と元の時点の差から
// 求める（timeZoneName: 'longOffset' は使わない。NFR Design の Q1: A）。暦の計算は Date に頼らず、先発グレゴリオ暦の
// 日数の計算（年が負でも同じ）で行う。例外は投げない（Intl の失敗は null で返す）。

import { getFormatter } from './timeZoneGate'

/** 壁時計（年は紀元前を 1 - 年 の負の側で表す。0 は紀元前 1 年） */
export interface WallTime {
  readonly year: number
  readonly month: number
  readonly day: number
  readonly hour: number
  readonly minute: number
  readonly second: number
}

/** 表示できる年の下限・上限（YYYY の4桁に収まる範囲） */
export const MIN_YEAR = 1
export const MAX_YEAR = 9999

/** 年が 0001〜9999 に収まるか */
export function isYearInRange(year: number): boolean {
  return year >= MIN_YEAR && year <= MAX_YEAR
}

/** 1970-01-01 からの日数（先発グレゴリオ暦。月は 1〜12） */
export function daysFromCivil(year: number, month: number, day: number): number {
  const y = month <= 2 ? year - 1 : year
  const era = Math.floor(y / 400)
  const yearOfEra = y - era * 400
  const monthIndex = month > 2 ? month - 3 : month + 9
  const dayOfYear = Math.floor((153 * monthIndex + 2) / 5) + day - 1
  const dayOfEra =
    yearOfEra * 365 + Math.floor(yearOfEra / 4) - Math.floor(yearOfEra / 100) + dayOfYear
  return era * 146097 + dayOfEra - 719468
}

/** 1970-01-01 からの日数 → 年月日 */
export function civilFromDays(days: number): { year: number; month: number; day: number } {
  const z = days + 719468
  const era = Math.floor(z / 146097)
  const dayOfEra = z - era * 146097
  const yearOfEra = Math.floor(
    (dayOfEra -
      Math.floor(dayOfEra / 1460) +
      Math.floor(dayOfEra / 36524) -
      Math.floor(dayOfEra / 146096)) /
      365,
  )
  const dayOfYear =
    dayOfEra - (365 * yearOfEra + Math.floor(yearOfEra / 4) - Math.floor(yearOfEra / 100))
  const monthIndex = Math.floor((5 * dayOfYear + 2) / 153)
  const day = dayOfYear - Math.floor((153 * monthIndex + 2) / 5) + 1
  const month = monthIndex < 10 ? monthIndex + 3 : monthIndex - 9
  const year = yearOfEra + era * 400 + (month <= 2 ? 1 : 0)
  return { year, month, day }
}

/** 壁時計の数字を UTC とみなした 1970-01-01T00:00:00 からの秒 */
export function wallToSeconds(wall: WallTime): number {
  return (
    daysFromCivil(wall.year, wall.month, wall.day) * 86400 +
    wall.hour * 3600 +
    wall.minute * 60 +
    wall.second
  )
}

/** UTC の秒 → 壁時計の数字（UTC のまま読む） */
export function secondsToWall(seconds: number): WallTime {
  const days = Math.floor(seconds / 86400)
  const rest = seconds - days * 86400
  const { year, month, day } = civilFromDays(days)
  return {
    year,
    month,
    day,
    hour: Math.floor(rest / 3600),
    minute: Math.floor((rest % 3600) / 60),
    second: rest % 60,
  }
}

/** 月の日数（うるう年を含む） */
export function daysInMonth(year: number, month: number): number {
  if (month === 2) {
    const leap = (year % 4 === 0 && year % 100 !== 0) || year % 400 === 0
    return leap ? 29 : 28
  }
  return month === 4 || month === 6 || month === 9 || month === 11 ? 30 : 31
}

function partNumber(parts: Intl.DateTimeFormatPart[], type: Intl.DateTimeFormatPartTypes): number {
  const value = parts.find((p) => p.type === type)?.value
  return value === undefined ? Number.NaN : Number(value)
}

/**
 * 時点（UTC の秒）を、名前のタイムゾーンの壁時計に直す。名前が解釈できない・Intl が整形できないときは null。
 * 年の範囲（0001〜9999）は確かめない（呼ぶ側が isYearInRange で確かめる）。
 */
export function wallAt(timeZone: string, epochSeconds: number): WallTime | null {
  const formatter = getFormatter(timeZone)
  if (formatter === null) {
    return null
  }
  let parts: Intl.DateTimeFormatPart[]
  try {
    parts = formatter.formatToParts(new Date(epochSeconds * 1000))
  } catch {
    // 日付の範囲外などで Intl が RangeError を出した。壁時計が求まらないことを null で返す（SD1.5）
    return null
  }
  const era = parts.find((p) => p.type === 'era')?.value ?? 'AD'
  const rawYear = partNumber(parts, 'year')
  const wall: WallTime = {
    year: era.toUpperCase().startsWith('B') ? 1 - rawYear : rawYear,
    month: partNumber(parts, 'month'),
    day: partNumber(parts, 'day'),
    hour: partNumber(parts, 'hour'),
    minute: partNumber(parts, 'minute'),
    second: partNumber(parts, 'second'),
  }
  return Object.values(wall).some((v) => Number.isNaN(v)) ? null : wall
}

/** その時点の時差の秒（壁時計 - UTC）。求まらないときは null */
export function offsetSecondsAt(timeZone: string, epochSeconds: number): number | null {
  const wall = wallAt(timeZone, epochSeconds)
  return wall === null ? null : wallToSeconds(wall) - epochSeconds
}

/**
 * 壁時計を持つ時点（UTC の秒）を、小さい順に返す。0 通り（夏時間で飛ばされる）・1 通り・2 通り（切り戻しで重なる）。
 * 壁時計の前後 24 時間の時差から候補を作り、その時点の壁時計が合うものだけを残す。Intl が使えないときは null。
 */
export function instantCandidates(timeZone: string, wall: WallTime): number[] | null {
  const guess = wallToSeconds(wall)
  const before = offsetSecondsAt(timeZone, guess - 86400)
  const after = offsetSecondsAt(timeZone, guess + 86400)
  if (before === null || after === null) {
    return null
  }
  const candidates = new Set<number>([guess - before, guess - after])
  const matched: number[] = []
  for (const candidate of candidates) {
    const offset = offsetSecondsAt(timeZone, candidate)
    if (offset === null) {
      return null
    }
    if (candidate + offset === guess) {
      matched.push(candidate)
    }
  }
  return matched.sort((a, b) => a - b)
}
