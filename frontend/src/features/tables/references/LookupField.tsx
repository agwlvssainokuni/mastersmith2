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
// LOOKUP の項目（M4。Intent 261029-reference-lookup-cleanup の U3、D16〜D20、frontend-components.md の 4.3）。
// - 読み取り専用の欄に「ラベル (値)」（ラベルが無ければ値、値が空なら空）。ラベルの読み込みの間は欄に aria-busy。
// - 「選ぶ…」と、NULL を許し必須でない項目だけ「クリア」。読み上げの名前は見える文字で始める（D17、WCAG 2.5.3）。
// - FormField の結び付け（id・aria-describedby・aria-invalid）は、FormField の文脈（FormFieldContext）で欄の TextInput だけが
//   受け、ボタンは受けない（Button は文脈を読まない。R-02）。
// - 必須（NULL を許さないか required が真。clearable の逆）なら、ラベルに必須の印を足し、欄に aria-required を付ける
//   （ReferenceSelectField と同じ書き方。直しの Bolt B7、U3 R-01）。
// - 選んだら値とラベルを入れ、「クリア」は値とラベルを消して欄へフォーカスを移す（D19）。保存中は押せない（D20）。
// - ダイアログは開いている間だけ描き、開くたびに作り直す（D21）。
import { Button, FormField, TextInput } from 'make-you-chic-ui'
import { useRef, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { PickerInfo, ReferenceRow, ReferenceSource } from '../businessDataTypes'
import { LookupPickerDialog, type LookupPickerDialogProps } from './LookupPickerDialog'
import { optionText } from './referenceField'
import type { SearchReference } from './useLookupSearch'
import './reference.css'

export interface LookupFieldProps {
  index: number
  /** 項目名（日英の表示名） */
  name: string
  picker: PickerInfo
  source: ReferenceSource
  value: string
  /** 選んだ行・読めたラベル。無ければ null（値だけを出す） */
  label: string | null
  labelLoading: boolean
  /** NULL を許し必須でない項目だけ「クリア」を出す（isRequiredColumn の否定。偽なら必須の印を付ける） */
  clearable: boolean
  error: string | undefined
  /** 保存中 */
  disabled: boolean
  onChange: (value: string, label: string | null) => void
  /** 検索の関数（テストで差し替える） */
  search?: SearchReference
}

/** LOOKUP の項目 */
export function LookupField({
  index,
  name,
  picker,
  source,
  value,
  label,
  labelLoading,
  clearable,
  error,
  disabled,
  onChange,
  search,
}: LookupFieldProps) {
  const { t } = useTranslation()
  const [open, setOpen] = useState(false)
  const openRef = useRef<HTMLButtonElement | null>(null)
  const inputRef = useRef<HTMLInputElement | null>(null)
  const required = !clearable
  const fieldLabel = required ? `${name} ${t('tables.form.required')}` : name
  const text = value === '' ? '' : optionText({ value, label })
  const dialogProps: LookupPickerDialogProps = {
    name,
    picker,
    source,
    finalFocusRef: openRef,
    onSelect: (row: ReferenceRow) => {
      setOpen(false)
      onChange(row.value, row.label)
    },
    onClose: () => setOpen(false),
    ...(search === undefined ? {} : { search }),
  }
  return (
    <>
      <FormField
        label={fieldLabel}
        error={error}
        helperText={value === '' ? t('tables.reference.lookupHint') : undefined}
      >
        <div className="tables-lookup">
          <TextInput
            ref={inputRef}
            className="tables-lookup-input"
            value={text}
            readOnly
            aria-readonly="true"
            aria-busy={labelLoading ? true : undefined}
            aria-required={required ? true : undefined}
            autoComplete="off"
            data-testid={`tables-lookup-value-c${index}`}
          />
          <div className="tables-lookup-buttons">
            <Button
              ref={openRef}
              variant="secondary"
              size="sm"
              disabled={disabled}
              aria-label={t('tables.reference.openName', { field: name })}
              onClick={() => setOpen(true)}
              data-testid={`tables-lookup-open-c${index}`}
            >
              {t('tables.reference.open')}
            </Button>
            {clearable && (
              <Button
                variant="ghost"
                size="sm"
                disabled={disabled || value === ''}
                aria-label={t('tables.reference.clearName', { field: name })}
                onClick={() => {
                  onChange('', null)
                  inputRef.current?.focus()
                }}
                data-testid={`tables-lookup-clear-c${index}`}
              >
                {t('tables.reference.clear')}
              </Button>
            )}
          </div>
        </div>
      </FormField>
      {open && <LookupPickerDialog {...dialogProps} />}
    </>
  )
}
