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
// 時差のラベル（Intent 261104-tz-options-perf の FR1・FR2・FR3）。名前と時点（UTC の秒）から、その時点の時差の文字
// ±HH:mm（秒の端数があれば ±HH:mm:ss。formatInstant の末尾と同じ形）を求める。表示のタイムゾーンの選択肢の組み立てのための関数。
// - Temporal の道: 実行環境に Temporal があれば、Temporal.Instant の toZonedDateTimeISO の offsetNanoseconds から時差を求める。
//   Intl.DateTimeFormat は作らない（生成 0 回・formatToParts 0 回）。
// - Intl の道: Temporal が無い環境（Safari など）では、名前ごとに整形器を 1 つ作って使い捨て、formatToParts を 1 回だけ呼び、
//   「壁時計の数字を UTC とみなした秒 − 元の時点の秒」で時差を求める（前の Intent の U1 の NFR Design の Q1: A と同じ求め方。
//   timeZoneName: 'longOffset' は使わない）。
// - どちらの道も、名前の検査（timeZoneGate の passesNameCheck）を先に 1 回行い、共有の整形器のキャッシュ（getFormatter）は
//   使わず、入れもしない。解釈できない名前・時差が求まらない・壁時計の年が 0001〜9999 を外れる（formatInstant が読めない値に
//   する範囲）ときは null を返す。例外は投げない。
// - 型は tsconfig の lib を変えずに、ここで使う分だけの最小の形を置き、globalThis から有無を確かめて読む。

import { formatOffset } from './formatInstant'
import { createUncachedFormatter, passesNameCheck } from './timeZoneGate'
import { isYearInRange, secondsToWall, wallToSeconds, wallWith } from './wallClock'

/** この関数が使う Temporal の最小の形（Temporal.Instant.fromEpochMilliseconds → toZonedDateTimeISO → offsetNanoseconds） */
export interface TemporalLike {
  readonly Instant: {
    fromEpochMilliseconds(epochMilliseconds: number): {
      toZonedDateTimeISO(timeZone: string): { readonly offsetNanoseconds: number }
    }
  }
}

const NANOSECONDS_PER_SECOND = 1_000_000_000

/**
 * 値が、この関数の使う Temporal の形を持つか確かめて返す。持たなければ null（Intl の道に戻る）。
 *
 * @param candidate 実行環境の Temporal（globalThis.Temporal。無い環境では undefined）
 */
export function temporalFrom(candidate: unknown): TemporalLike | null {
  if (typeof candidate !== 'object' || candidate === null) {
    return null
  }
  const instant: unknown = (candidate as { Instant?: unknown }).Instant
  if ((typeof instant !== 'function' && typeof instant !== 'object') || instant === null) {
    return null
  }
  return typeof (instant as { fromEpochMilliseconds?: unknown }).fromEpochMilliseconds ===
    'function'
    ? (candidate as TemporalLike)
    : null
}

/** 実行環境の Temporal（無ければ null） */
function runtimeTemporal(): TemporalLike | null {
  return temporalFrom((globalThis as { Temporal?: unknown }).Temporal)
}

/** Temporal の道。整形器を作らない。解釈できない名前（RangeError）などは null */
function offsetSecondsByTemporal(
  timeZone: string,
  epochSeconds: number,
  temporal: TemporalLike,
): number | null {
  let nanoseconds: number
  try {
    nanoseconds = temporal.Instant.fromEpochMilliseconds(epochSeconds * 1000).toZonedDateTimeISO(
      timeZone,
    ).offsetNanoseconds
  } catch {
    // Temporal が知らない名前・範囲の外（RangeError）。解釈できない名前として null を返す
    return null
  }
  const seconds = nanoseconds / NANOSECONDS_PER_SECOND
  // ±HH:mm:ss の形に収まらない端数（秒未満）は、Intl の道（秒までの壁時計）と結果が分かれるため、求まらない扱いにする
  return Number.isInteger(seconds) ? seconds : null
}

/** Intl の道。名前ごとに整形器を 1 つ作って使い捨て、formatToParts を 1 回だけ呼ぶ。キャッシュには入れない */
function offsetSecondsByIntl(timeZone: string, epochSeconds: number): number | null {
  const formatter = createUncachedFormatter(timeZone)
  if (formatter === null) {
    return null
  }
  const wall = wallWith(formatter, epochSeconds)
  return wall === null ? null : wallToSeconds(wall) - epochSeconds
}

/**
 * 時差のラベルを、道（Temporal を渡すか null か）を選んで求める。テストで 2 つの道を流すための口。
 *
 * @param timeZone IANA の名前
 * @param epochSeconds 時点（UTC の 1970-01-01T00:00:00 からの秒。整数）。呼ぶ側で 1 回だけ求めて渡す
 * @param temporal Temporal（null なら Intl の道）
 */
export function timeZoneOffsetTextWith(
  timeZone: unknown,
  epochSeconds: number,
  temporal: TemporalLike | null,
): string | null {
  if (!passesNameCheck(timeZone)) {
    return null
  }
  if (!Number.isInteger(epochSeconds) || !isYearInRange(secondsToWall(epochSeconds).year)) {
    return null
  }
  const offset =
    temporal === null
      ? offsetSecondsByIntl(timeZone, epochSeconds)
      : offsetSecondsByTemporal(timeZone, epochSeconds, temporal)
  // formatInstant と同じく、表示のタイムゾーンへ直した年が 0001〜9999 を外れるときは求まらない扱いにする
  if (offset === null || !isYearInRange(secondsToWall(epochSeconds + offset).year)) {
    return null
  }
  return formatOffset(offset)
}

/**
 * 名前の、その時点の時差のラベル（±HH:mm[:ss]）を返す。解釈できない名前などは null。実行環境に Temporal があれば
 * Temporal の道（整形器を作らない）、無ければ Intl の道（名前ごとに整形器の生成 1 回・formatToParts 1 回）で求める。
 * 共有の整形器のキャッシュには触れない。
 *
 * @param timeZone IANA の名前
 * @param epochSeconds 時点（UTC の秒。整数）
 */
export function timeZoneOffsetText(timeZone: unknown, epochSeconds: number): string | null {
  return timeZoneOffsetTextWith(timeZone, epochSeconds, runtimeTemporal())
}
