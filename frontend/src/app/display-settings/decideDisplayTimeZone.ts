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
// 表示のタイムゾーンの決め方（Intent 261102-timezone-ui-polish の U2、BR2.12・BR2.14、純粋な関数）。
// 保存済みの名前が今のブラウザで解釈できればそれを使う（saved）。保存が無ければブラウザのタイムゾーン（browser）。保存済みの名前を
// 解釈できなければブラウザのタイムゾーンで出し、その印を付ける（saved-unresolvable）。ブラウザの名前も解釈できなければ UTC。

import { canInterpretTimeZone } from '../../shared/format/timeZone'

/** 決めた表示のタイムゾーンの由来 */
export type DisplayTimeZoneSource = 'saved' | 'browser' | 'saved-unresolvable'

/** 決めた表示のタイムゾーン（timeZone は必ず解釈できる名前） */
export interface DecidedTimeZone {
  readonly timeZone: string
  readonly source: DisplayTimeZoneSource
}

/**
 * 保存済みの名前とブラウザの名前から、使う名前を決める。
 *
 * @param saved 保存済みの名前（未設定は null）
 * @param browser ブラウザのタイムゾーンの名前
 */
export function decideDisplayTimeZone(saved: string | null, browser: string): DecidedTimeZone {
  const fallback = canInterpretTimeZone(browser) ? browser : 'UTC'
  if (saved === null) {
    return { timeZone: fallback, source: 'browser' }
  }
  if (canInterpretTimeZone(saved)) {
    return { timeZone: saved, source: 'saved' }
  }
  return { timeZone: fallback, source: 'saved-unresolvable' }
}
