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
// 入力不可の参照の項目（M2・M3・M7。Intent 261029-reference-lookup-cleanup の U3、D11〜D14、security-design.md 2.1）。
// - 読み取り専用の TextInput（readOnly・aria-readonly）を FormField で包む。disabled にしない（Tab で止まり値を読める）。
// - 値は保存されている値そのもの（参照先のラベルは出さない）。作成では空。値を変える口を持たない。
// - 説明文は helperText: 作成の必須（blockedRequired）なら D13 の理由ごとの文、そうでなければ D12 の理由ごとの文。
//   開いた直後は helperText（role="alert"・aria-invalid を付けない。NFR 設計の Q1: A）。保存を押した後の誤りだけを
//   FormField の error（role="alert"）に渡す。
import { FormField, TextInput } from 'make-you-chic-ui'
import { useTranslation } from 'react-i18next'
import { requiredBlockedMessageKey } from '../problemMessage'
import type { ReadonlyReason } from './referenceField'

export interface ReadonlyReferenceFieldProps {
  index: number
  /** 項目名（日英の表示名） */
  name: string
  /** 保存されている値そのもの（作成では空） */
  value: string
  reason: ReadonlyReason
  /** 作成の画面で必須のとき true（D13 の文を説明文に出す） */
  blockedRequired: boolean
  /** 保存を押した後・保存の後の誤り */
  error: string | undefined
}

/** 入力不可の参照の項目 */
export function ReadonlyReferenceField({
  index,
  name,
  value,
  reason,
  blockedRequired,
  error,
}: ReadonlyReferenceFieldProps) {
  const { t } = useTranslation()
  const helperText = blockedRequired
    ? t(requiredBlockedMessageKey(reason))
    : t(reason === 'too-many' ? 'tables.reference.tooMany' : 'tables.reference.noPermission')
  return (
    <FormField label={name} error={error} helperText={helperText}>
      <TextInput
        value={value}
        readOnly
        aria-readonly="true"
        autoComplete="off"
        data-testid={`tables-form-readonly-reference-c${index}`}
      />
    </FormField>
  )
}
