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
// 送る前の画面の確かめ（functional-spec.md の W11、D7〜D9、security-design.md の 3.3）。確かめは共用の関数
// （frontend/src/shared/validation/、U6 が作る）だけで行い、決まりをここに別に書かない。今のパスワードは空だけを見る
// （規則を当てない。照合はサーバー）。項目ごとの理由（1項目に1つ）と、画面の並びで最初の誤りの項目を返す。
// React・window・API・ブラウザの保存に触れない純粋な関数。
import { validateDisplayName } from '../../shared/validation/validateDisplayName'
import {
  validateNewPassword,
  validatePasswordConfirmation,
} from '../../shared/validation/validatePassword'
import type { FieldReason } from './errorMessages'
import type { PasswordChangeInput, PasswordField, PreferencesField } from './preferencesApi'

/** 確かめの結果 */
export interface FormCheck<F extends string> {
  /** 項目ごとの理由（誤りのある項目だけ） */
  reasons: Partial<Record<F, FieldReason>>
  /** 画面の並びで最初の誤りの項目（誤りが無ければ undefined） */
  firstInvalid: F | undefined
}

/** パスワードの変更の画面の項目の並び */
export const PASSWORD_FIELDS: readonly PasswordField[] = [
  'currentPassword',
  'newPassword',
  'newPasswordConfirmation',
]

/** プリファレンスの画面の項目の並び */
export const PREFERENCES_FIELDS: readonly PreferencesField[] = [
  'displayName',
  'language',
  'theme',
  'fontSize',
]

/** 並びの順に理由を集める。 */
function collect<F extends string>(
  order: readonly F[],
  found: Partial<Record<F, FieldReason | undefined>>,
): FormCheck<F> {
  const reasons: Partial<Record<F, FieldReason>> = {}
  let firstInvalid: F | undefined
  for (const field of order) {
    const reason = found[field]
    if (reason !== undefined) {
      reasons[field] = reason
      firstInvalid ??= field
    }
  }
  return { reasons, firstInvalid }
}

/**
 * プリファレンスのフォームを確かめる。氏名だけを共用の関数で確かめる（言語・テーマ・文字の大きさは選択肢から選ぶため、
 * 画面の値はいつも許される値。W11 の2）。
 */
export function checkPreferencesForm(form: { displayName: string }): FormCheck<PreferencesField> {
  return collect(PREFERENCES_FIELDS, { displayName: validateDisplayName(form.displayName) })
}

/** パスワードの変更のフォームを確かめる（今 → 新しい → 確かめの順）。 */
export function checkPasswordChangeForm(form: PasswordChangeInput): FormCheck<PasswordField> {
  return collect(PASSWORD_FIELDS, {
    currentPassword: form.currentPassword.length === 0 ? 'required' : undefined,
    newPassword: validateNewPassword(form.newPassword),
    newPasswordConfirmation: validatePasswordConfirmation(
      form.newPassword,
      form.newPasswordConfirmation,
    ),
  })
}
