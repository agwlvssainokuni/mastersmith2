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
// 氏名の画面の側の確かめ（U6 の functional-spec.md の 6節、U2 の BR1.1〜BR1.4）。誤りの種類だけを返し、文言は機能ごとに持つ。
// 判定はサーバーが正で、画面の確かめはその代わりにしない（D8）。U6 の登録の完了と U7 のプリファレンスが使う。
import { countCodePoints, trimDisplayName } from './codePoints'
import { DISPLAY_NAME_MAX_CODE_POINTS } from './limits'

/** 氏名の誤りの種類 */
export type DisplayNameProblem = 'required' | 'tooLong' | 'invalidCharacter'

/** 制御文字（Cc）と書式文字（Cf） */
const CONTROL_OR_FORMAT = /[\p{Cc}\p{Cf}]/u

/**
 * 氏名を確かめる。前後の White_Space を除いた後に、空は required、254 コードポイントを超えれば tooLong、
 * 内側に Cc・Cf の文字があれば invalidCharacter（この順に判定する）。誤りが無ければ undefined。
 */
export function validateDisplayName(value: string): DisplayNameProblem | undefined {
  const trimmed = trimDisplayName(value)
  if (trimmed.length === 0) {
    return 'required'
  }
  if (countCodePoints(trimmed) > DISPLAY_NAME_MAX_CODE_POINTS) {
    return 'tooLong'
  }
  if (CONTROL_OR_FORMAT.test(trimmed)) {
    return 'invalidCharacter'
  }
  return undefined
}
