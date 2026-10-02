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
// 氏名・言語の入力（S4）の確かめと、誤りの理由から文言の鍵を選ぶ純粋な関数（functional-spec.md の D16・W7・7.6、
// frontend-components.md の 7節）。画面の確かめ（validateDisplayName の理由）とサーバーの項目ごとの誤り（U2 の reason）を
// 同じ理由の種類に寄せてから、1つの表で文言の鍵にする（プリファレンスの errorMessages.ts と同じ考え方。機能どうしで
// 読み込まないため、表はこの機能の中に持つ）。サーバーの detail は使わない。判定はサーバーが正（FR6.2）。
import { validateDisplayName } from '../../shared/validation/validateDisplayName'

/** 入力の項目の値（サーバーの fieldErrors の field と同じ名前） */
export const PROFILE_FIELDS = ['displayName', 'language'] as const

/** 入力の項目 */
export type ProfileField = (typeof PROFILE_FIELDS)[number]

/** 寄せた誤りの理由（invalidValue はサーバーだけが返す理由、unknown は知らない理由） */
export type ProfileReason = 'required' | 'tooLong' | 'invalidCharacter' | 'invalidValue' | 'unknown'

/** サーバーの reason（U2 の FieldErrorReason）から寄せる理由 */
const SERVER_REASONS: Readonly<Record<string, ProfileReason>> = {
  REQUIRED: 'required',
  TOO_LONG: 'tooLong',
  INVALID_CHARACTER: 'invalidCharacter',
  INVALID_VALUE: 'invalidValue',
}

/** 項目と理由から文言の鍵への表（表に無い組は項目に結び付けない） */
const MESSAGE_KEYS: Readonly<Record<ProfileField, Partial<Record<ProfileReason, string>>>> = {
  displayName: {
    required: 'useradmin.edit.nameRequired',
    tooLong: 'useradmin.edit.nameTooLong',
    invalidCharacter: 'useradmin.edit.nameInvalidCharacter',
  },
  language: {
    required: 'useradmin.edit.languageInvalid',
    invalidValue: 'useradmin.edit.languageInvalid',
  },
}

/** 項目に結び付かない 400 VALIDATION_FAILED の文言の鍵（failed の表示） */
export const PROFILE_FORM_INVALID_KEY = 'useradmin.edit.formInvalid'

/** 氏名を画面の側で確かめる。誤りが無ければ undefined（required・tooLong・invalidCharacter の順）。 */
export function checkProfileName(value: string): ProfileReason | undefined {
  return validateDisplayName(value)
}

/** サーバーの reason を寄せる。知らない値は unknown。 */
export function toProfileReason(serverReason: string): ProfileReason {
  return Object.hasOwn(SERVER_REASONS, serverReason)
    ? (SERVER_REASONS[serverReason] ?? 'unknown')
    : 'unknown'
}

/** 項目と理由から文言の鍵を選ぶ。項目に結び付かない組（知らない理由など）は undefined。 */
export function profileMessageKey(field: ProfileField, reason: ProfileReason): string | undefined {
  return MESSAGE_KEYS[field][reason]
}
