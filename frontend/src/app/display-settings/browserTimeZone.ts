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
// ブラウザのタイムゾーンを読む唯一の場所（Intent 261102-timezone-ui-polish の U2、BR2.13・SD6）。
// 暗黙のタイムゾーン（ブラウザの既定）に頼る書き方は ESLint の規則で止めており、この場所だけがその例外。
// 解釈できない名前が返ったときは UTC にする。

import { canInterpretTimeZone } from '../../shared/format/timeZone'

/** ブラウザのタイムゾーンを返す関数（テストと提供元で差し替えられる） */
export type BrowserTimeZoneReader = () => string

/** ブラウザの Intl が答えるタイムゾーンを読む。解釈できなければ UTC。 */
export function readBrowserTimeZone(): string {
  let name: unknown
  try {
    name = new Intl.DateTimeFormat().resolvedOptions().timeZone
  } catch {
    return 'UTC'
  }
  return typeof name === 'string' && canInterpretTimeZone(name) ? name : 'UTC'
}
