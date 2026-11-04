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
// 日時の入力の初めの値と送る形の薄い関数（Intent 261102-timezone-ui-polish の U4、D14・D16、計画 Step 5）。
// - 時点を表す型（DATETIME_TZ）: U1 の formatInstant の ok の文字から時差を除き、表示のタイムゾーンの壁時計
//   YYYY-MM-DD HH:mm:ss[.端数] を返す（端数はサーバーが返した桁のまま）。読めない時点は元の文字のまま。
// - 時差を持たない日時（DATETIME）: 区切りの T を空白にした文字を初めの値に、空白を T にした文字を送る形にする。
//   書式と範囲の確かめは U1 と同じ規則（parseLocalDateTime に UTC を渡し、書式と範囲の結果だけを使う）。別の規則を作らない。
// - 例外は投げない。結果は HTML ではない、ただの文字。タイムゾーンは必ず引数で明示に渡す（機械の既定を読まない）。
import { formatInstant, parseLocalDateTime } from './timeZone'

/** formatInstant の ok の文字（日付 時刻 時差）。最後の空白より前が壁時計 */
const FORMATTED = /^(\d{4}-\d{2}-\d{2} \d{2}:\d{2}:\d{2}(?:\.\d+)?) [+-]\d{2}:\d{2}(?::\d{2})?$/

/** 時差を持たない日時の受け渡しの文字（区切りが T）。秒の端数は 1〜9 桁まで受ける */
const LOCAL_WIRE = /^(\d{4}-\d{2}-\d{2})T(\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?)$/

/** 時差を持たない日時の入力の文字（区切りが半角の空白1つ） */
const LOCAL_INPUT = /^(\d{4}-\d{2}-\d{2}) (\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?)$/

/** 書式と範囲だけを確かめるときに渡すタイムゾーン（夏時間が無く、gap・overlap にならない） */
const CHECK_ONLY_ZONE = 'UTC'

/**
 * 時点を、入力の初めの値（表示のタイムゾーンの壁時計。時差なし）にする（D16）。
 *
 * @param instant 時点の文字（サーバーが返した UTC の時差つきの文字）
 * @param timeZone 表示のタイムゾーンの IANA の名前（必ず明示に渡す）
 * @returns 壁時計の文字。読めない時点・解釈できない名前のときは元の文字
 */
export function formatInstantForInput(instant: string, timeZone: string): string {
  const result = formatInstant(instant, timeZone)
  switch (result.kind) {
    case 'ok': {
      const match = FORMATTED.exec(result.text)
      return match === null ? instant : match[1]
    }
    case 'unreadable':
      return result.raw
    case 'invalid-time-zone':
      return instant
  }
}

/**
 * 時差を持たない日時の値（DB の値。区切りが T）を、入力の初めの値（区切りが空白）にする（D16）。端数は落とさない。
 *
 * @param value サーバーが返した値
 * @returns 区切りを空白にした文字。形が違えば元の文字
 */
export function formatLocalDateTimeForInput(value: string): string {
  const match = LOCAL_WIRE.exec(value)
  return match === null ? value : `${match[1]} ${match[2]}`
}

/**
 * 時差を持たない日時の入力の文字（区切りが空白）を、送る形（区切りが T）にする（D14・D17）。
 *
 * @param text 入力の文字（isLocalDateTimeText を通ったもの）
 * @returns 区切りを T にした文字。形が違えば元の文字
 */
export function toLocalDateTimeWire(text: string): string {
  const match = LOCAL_INPUT.exec(text)
  return match === null ? text : `${match[1]}T${match[2]}`
}

/**
 * 時差を持たない日時の入力の文字が、U1 と同じ書式（YYYY-MM-DD HH:mm:ss[.1〜6桁]）と範囲（暦にある日付・0001〜9999 年）に
 * 合うか（D14）。U1 の parseLocalDateTime に UTC を渡し、書式と範囲の結果だけを使う。
 *
 * @param text 入力の文字
 */
export function isLocalDateTimeText(text: string): boolean {
  return parseLocalDateTime(text, CHECK_ONLY_ZONE).kind === 'ok'
}
