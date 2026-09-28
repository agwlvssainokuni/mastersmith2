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
// フォームの値から、項目ごとの誤りの文言の鍵と最初の誤りの項目を決める純粋な関数（functional-spec.md の W7・D8）。
// 確かめは共用の shared/validation の関数で行い、誤りの種類を登録の完了の画面の文言の鍵に変える。
import type {
  DisplayLanguage,
  FontSize,
  ThemeChoice,
} from '../../app/display-settings/displaySettingsTypes'
import { validateDisplayName } from '../../shared/validation/validateDisplayName'
import {
  validateNewPassword,
  validatePasswordConfirmation,
} from '../../shared/validation/validatePassword'

/** 確かめる項目（上から順） */
export type RegistrationField = 'displayName' | 'password' | 'passwordConfirmation'

/** 確かめる項目の並び（最初の誤りの項目を決める順） */
export const REGISTRATION_FIELDS: readonly RegistrationField[] = [
  'displayName',
  'password',
  'passwordConfirmation',
]

/** 確かめる値 */
export interface RegistrationFormText {
  displayName: string
  password: string
  passwordConfirmation: string
}

/** フォームの値（選んだ言語・テーマ・文字の大きさを含む。frontend-components.md の 4.1節の values） */
export interface RegistrationValues extends RegistrationFormText {
  language: DisplayLanguage
  theme: ThemeChoice
  fontSize: FontSize
}

/** フォームの上の失敗の知らせ（400 VALIDATION_FAILED と、ほかの失敗） */
export type RegistrationFailure = 'validationFailed' | 'submitFailed'

/** 次の描画の確定の後にフォーカスを移す先（項目か、フォームの上の知らせ） */
export type RegistrationFocusTarget = RegistrationField | 'failure'

/** 項目ごとの誤りの文言の鍵 */
export type RegistrationProblems = Partial<Record<RegistrationField, string>>

/** 確かめの結果 */
export interface FormCheck {
  problems: RegistrationProblems
  /** 上から見て最初の誤りの項目（誤りが無ければ undefined） */
  firstInvalid: RegistrationField | undefined
}

/** フォームの値を確かめ、項目ごとの誤りの文言の鍵と最初の誤りの項目を返す。 */
export function checkRegistrationForm(text: RegistrationFormText): FormCheck {
  const found: Record<RegistrationField, string | undefined> = {
    displayName: validateDisplayName(text.displayName),
    password: validateNewPassword(text.password),
    passwordConfirmation: validatePasswordConfirmation(text.password, text.passwordConfirmation),
  }
  const problems: RegistrationProblems = {}
  let firstInvalid: RegistrationField | undefined
  for (const field of REGISTRATION_FIELDS) {
    const problem = found[field]
    if (problem !== undefined) {
      problems[field] = `registration.${field}.${problem}`
      firstInvalid ??= field
    }
  }
  return { problems, firstInvalid }
}
