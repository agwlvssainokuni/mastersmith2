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
// REFERENCE の選択（M1。Intent 261029-reference-lookup-cleanup の U3、D6〜D10、frontend-components.md の 4.1）。
// - 選択肢は reference.options から作り、文は「ラベル (値)」（ラベルが null なら値だけ）。並べ直さない。文字として描く。
// - select の「（選ばない）」の扱いは既存の select と同じ（必須で値が入っていれば置かない）。radio は置かない。
// - 必須は isRequiredColumn（NULL を許さないか required が真）で決め、印・aria-required・「（選ばない）」の出し分けをそろえる
//   （直しの Bolt B7、U3 R-01）。
// - 更新で保存済みの値が options に無いときは、値だけの選択肢を先頭に足す（D8）。
// - 0 件のときは「（選ばない）」だけの select と説明文、必須なら続けて必須の文（D9）。
// - 状態は持たない（値は作成・更新の画面のフォームの状態）。
import { FormField, RadioGroup, Select } from 'make-you-chic-ui'
import { useTranslation } from 'react-i18next'
import type { ColumnDefinition } from '../businessDataTypes'
import type { FormMode } from '../formDiff'
import { isRequiredColumn, optionText } from './referenceField'

export interface ReferenceSelectFieldProps {
  /** formPart が select か radio、reference が REFERENCE・AVAILABLE のカラム */
  column: ColumnDefinition
  /** テストの印と radio の name に使う位置の番号 */
  index: number
  /** 項目名（日英の表示名） */
  name: string
  mode: FormMode
  value: string
  /** 開いたときの値（更新で options に無ければ、値だけの選択肢を足す。D8） */
  initialValue: string
  error: string | undefined
  disabled: boolean
  onChange: (value: string) => void
  onBlur: () => void
}

/** REFERENCE の選択 */
export function ReferenceSelectField({
  column,
  index,
  name,
  mode,
  value,
  initialValue,
  error,
  disabled,
  onChange,
  onBlur,
}: ReferenceSelectFieldProps) {
  const { t } = useTranslation()
  const required = isRequiredColumn(column)
  const label = required ? `${name} ${t('tables.form.required')}` : name
  const options = column.reference?.options ?? []
  const choices = options.map((option) => ({ value: option.value, label: optionText(option) }))
  if (
    mode === 'update' &&
    initialValue !== '' &&
    !options.some((option) => option.value === initialValue)
  ) {
    choices.unshift({ value: initialValue, label: initialValue })
  }
  const hints: string[] = []
  if (options.length === 0) {
    hints.push(t('tables.reference.noOptions'))
    if (required) {
      hints.push(t('tables.reference.noOptionsRequired'))
    }
  }
  const helperText = hints.length === 0 ? undefined : hints.join(' ')
  const radio = column.formPart === 'radio' && options.length > 0
  return (
    <FormField label={label} error={error} helperText={helperText}>
      {radio ? (
        <RadioGroup
          name={`tables-form-r${index}`}
          options={choices}
          value={value}
          onChange={onChange}
          disabled={disabled}
          legend={<span className="tables-visually-hidden">{label}</span>}
        />
      ) : (
        <Select
          options={
            required && value !== ''
              ? choices
              : [{ value: '', label: t('tables.form.unselected') }, ...choices]
          }
          value={value}
          onChange={onChange}
          onBlur={onBlur}
          disabled={disabled}
          aria-required={required ? true : undefined}
          autoComplete="off"
        />
      )}
    </FormField>
  )
}
