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
// ロール・グループの名前の画面の側の確かめ（Intent 261004-role-menu の U6 の D7、L9、NFR1.4）。誤りの種類だけを返し、
// 文言は機能（roleadmin・groupadmin）ごとに持つ。判定の正はサーバーで、画面の確かめはその代わりにしない。
// サーバーの RoleName・GroupName と同じ規則にそろえる: 前後の Unicode の White_Space（全角の空白を含む）を除き、
// 1〜64 コードポイント、制御文字（Cc と U+2028・U+2029）を含まない。目に見える文字と一緒の書式文字（Cf、U+200B など）は
// 拒まない（氏名の validateDisplayName は Cc・Cf を拒むため、規則が違う。計画 2.3 の A19）。ただし、書式文字と空白だけで
// 目に見える文字が無い名前は、サーバーと同じく空（required）とする（Build and Test の Q3 B、group の R-03）。
import { countCodePoints, trimDisplayName } from './codePoints'
import { ADMIN_NAME_MAX_CODE_POINTS } from './limits'

/** 名前の誤りの種類 */
export type AdminNameProblem = 'required' | 'tooLong' | 'invalidCharacter'

/** 制御文字（Cc）と行の区切り（U+2028）・段落の区切り（U+2029） */
const CONTROL_CHARACTER = /[\p{Cc}\u2028\u2029]/u
// 目に見えない文字（Unicode の White_Space と書式文字 Cf）だけの値。サーバーの RoleName・GroupName の isInvisible と同じ範囲。
const INVISIBLE_ONLY = /^[\p{White_Space}\p{Cf}]*$/u

/**
 * 名前を確かめる。前後の White_Space を除いた後に、空は required、64 コードポイントを超えれば tooLong、
 * 内側に Cc・U+2028・U+2029 があれば invalidCharacter（この順に判定する）。誤りが無ければ undefined。
 */
export function validateAdminName(value: string): AdminNameProblem | undefined {
  const trimmed = trimDisplayName(value)
  if (trimmed.length === 0) {
    return 'required'
  }
  if (countCodePoints(trimmed) > ADMIN_NAME_MAX_CODE_POINTS) {
    return 'tooLong'
  }
  if (CONTROL_CHARACTER.test(trimmed)) {
    return 'invalidCharacter'
  }
  if (INVISIBLE_ONLY.test(trimmed)) {
    return 'required'
  }
  return undefined
}

/** 前後の White_Space を除いた後の残りの文字数（上限を超えたら 0 未満）。 */
export function remainingAdminNameLength(value: string): number {
  return ADMIN_NAME_MAX_CODE_POINTS - countCodePoints(trimDisplayName(value))
}
