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
// 表示のタイムゾーンの選択肢の組み立て（Intent 261102-timezone-ui-polish の U2、LC7、BR2.17・BR2.18、PD3・PD4）。純粋な関数。
// - 名前の列（ブラウザの Intl が答える一覧）を取り出した配列ごとに 1 回だけ、UTC を無いときだけ足して昇順（コード点の順）にそろえ、
//   ブラウザが解釈できない名前を除き、時差（±HH:mm）を求める。結果は WeakMap で配列の参照に結び付ける。
// - 先頭は「ブラウザに合わせる」（値は空の文字列）。保存済みの名前が一覧に無く解釈できるときは、その次に足す。
// - ラベルは `名前 (±HH:mm)`。時差は画面を開いた時点の時刻（now）で 1 回だけ求める。名前は React の文字のノードとしてだけ描く。

import { canInterpretTimeZone, formatInstant } from '../../shared/format/timeZone'

/** 選択肢 1 つ（value が空の文字列は「ブラウザに合わせる」） */
export interface TimeZoneOption {
  readonly value: string
  readonly label: string
}

/** 名前の配列ごとの、時差つきの名前の並び（now は配列ごとに最初に求めた時刻で固定） */
const cache = new WeakMap<readonly string[], { at: number; entries: readonly TimeZoneOption[] }>()

/** ブラウザが答える名前の一覧を取り出す。取り出せなければ空（UTC だけの一覧になる）。 */
export function readSupportedTimeZoneNames(): readonly string[] {
  try {
    return Intl.supportedValuesOf('timeZone')
  } catch {
    return []
  }
}

/** formatInstant の ok の文字から時差（±HH:mm[:ss]）を取り出す。 */
function offsetOf(text: string): string {
  const match = /([+-]\d{2}:\d{2}(?::\d{2})?)$/.exec(text)
  return match === null ? '' : (match[1] ?? '')
}

function offsetLabel(name: string, instant: string): string | null {
  if (!canInterpretTimeZone(name)) {
    return null
  }
  const result = formatInstant(instant, name)
  return result.kind === 'ok' ? `${name} (${offsetOf(result.text)})` : null
}

function entriesFor(names: readonly string[], now: Date): readonly TimeZoneOption[] {
  const cached = cache.get(names)
  if (cached !== undefined) {
    return cached.entries
  }
  const instant = now.toISOString()
  const unique = new Set<string>(names)
  unique.add('UTC')
  const entries: TimeZoneOption[] = []
  for (const name of [...unique].sort(compareCodePoints)) {
    const label = offsetLabel(name, instant)
    if (label !== null) {
      entries.push({ value: name, label })
    }
  }
  cache.set(names, { at: now.getTime(), entries })
  return entries
}

function compareCodePoints(a: string, b: string): number {
  return a < b ? -1 : a > b ? 1 : 0
}

/**
 * 選択肢を組み立てる。
 *
 * @param names ブラウザの Intl が答える名前の一覧（同じ配列の参照では 1 回だけ組み立てる）
 * @param saved 保存済みの名前（未設定は null）
 * @param now 画面を開いた時点の時刻
 * @param browserLabel 先頭の「ブラウザに合わせる」の文言
 */
export function buildTimeZoneOptions(
  names: readonly string[],
  saved: string | null,
  now: Date,
  browserLabel: string,
): readonly TimeZoneOption[] {
  const entries = entriesFor(names, now)
  const head: TimeZoneOption = { value: '', label: browserLabel }
  if (saved === null || saved === '' || entries.some((entry) => entry.value === saved)) {
    return [head, ...entries]
  }
  const extra = offsetLabel(saved, now.toISOString())
  return extra === null ? [head, ...entries] : [head, { value: saved, label: extra }, ...entries]
}
