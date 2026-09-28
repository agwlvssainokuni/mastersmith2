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
// 誤りの理由と項目の名前から、この機能の文言の鍵を選ぶ純粋な関数（functional-spec.md の W11・W12、D13、
// security-design.md の 3.3、NFR8.2）。画面の確かめ（shared/validation の理由）とサーバーの項目ごとの誤り（U2 の reason）を
// 同じ理由の union に寄せてから、1つの表で文言の鍵にする。サーバーの detail は使わない。
import type { PasswordField, PreferencesField } from './preferencesApi'

/** 寄せた誤りの理由（invalidValue はサーバーだけが返す理由、unknown は知らない理由） */
export type FieldReason =
  'required' | 'tooShort' | 'tooLong' | 'invalidCharacter' | 'mismatch' | 'invalidValue' | 'unknown'

/** この機能の項目の名前 */
export type PreferencesFormField = PreferencesField | PasswordField

/** 読み込みの失敗の知らせの鍵 */
export const LOAD_FAILED_KEY = 'preferences.load.failed'

/** 保存の失敗の知らせの鍵 */
export const SAVE_FAILED_KEY = 'preferences.save.failed'

/** パスワードの変更の失敗の知らせの鍵 */
export const PASSWORD_FAILED_KEY = 'preferences.password.failed'

/** 対応づけられる項目の誤りが無い 400 VALIDATION_FAILED の知らせの鍵 */
export const FORM_INVALID_KEY = 'preferences.form.invalid'

/** 今のパスワードの誤り（400 PASSWORD_CURRENT_MISMATCH）の鍵 */
export const CURRENT_MISMATCH_KEY = 'preferences.password.currentMismatch'

/** 文字の項目の一般の文言の鍵 */
const FIELD_INVALID_KEY = 'preferences.field.invalid'

/** 選択のまとまりの一般の文言の鍵 */
const CHOICE_INVALID_KEY = 'preferences.choice.invalid'

/** サーバーの reason（U2 の FieldErrorReason）から寄せる理由 */
const SERVER_REASONS: Readonly<Record<string, FieldReason>> = {
  REQUIRED: 'required',
  TOO_SHORT: 'tooShort',
  TOO_LONG: 'tooLong',
  INVALID_CHARACTER: 'invalidCharacter',
  MISMATCH: 'mismatch',
  INVALID_VALUE: 'invalidValue',
}

/** 選択のまとまりの項目 */
const CHOICE_FIELDS: readonly PreferencesFormField[] = ['language', 'theme', 'fontSize']

/** 項目と理由から文言の鍵への表（security-design.md の 3.3。表に無い組は一般の文言） */
const MESSAGE_KEYS: Readonly<
  Partial<Record<PreferencesFormField, Partial<Record<FieldReason, string>>>>
> = {
  displayName: {
    required: 'preferences.displayName.required',
    tooLong: 'preferences.displayName.tooLong',
    invalidCharacter: 'preferences.displayName.invalidCharacter',
  },
  currentPassword: {
    required: 'preferences.password.currentRequired',
  },
  newPassword: {
    required: 'preferences.password.newRequired',
    tooShort: 'preferences.password.tooShort',
    tooLong: 'preferences.password.tooLong',
  },
  newPasswordConfirmation: {
    required: 'preferences.password.confirmRequired',
    mismatch: 'preferences.password.mismatch',
  },
}

/** サーバーの reason を寄せる。知らない値は unknown。 */
export function toFieldReason(serverReason: string): FieldReason {
  return Object.hasOwn(SERVER_REASONS, serverReason)
    ? (SERVER_REASONS[serverReason] ?? 'unknown')
    : 'unknown'
}

/** 項目と理由から文言の鍵を選ぶ。表に無い組と unknown は、文字の項目・選択のまとまりの一般の文言。 */
export function fieldMessageKey(field: PreferencesFormField, reason: FieldReason): string {
  if (CHOICE_FIELDS.includes(field)) {
    return CHOICE_INVALID_KEY
  }
  return MESSAGE_KEYS[field]?.[reason] ?? FIELD_INVALID_KEY
}
