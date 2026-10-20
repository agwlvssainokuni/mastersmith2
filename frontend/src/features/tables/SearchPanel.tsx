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
// S1 の検索の領域（Intent 261016-business-data-views の U3 の L7、F2・Q3: A、frontend-components.md の 4節、
// interaction-spec.md の SearchPanel、AC1.2.1〜AC1.2.13、U3-NFR4.2・5.9・5.10、計画 4.3 の R-06）。
// - 項目は定義の検索の項目だけから作る。演算子ごとの入力: EQUALS・CONTAINS は1つの入力、RANGE は下限と上限、CHOICE は
//   選択肢のチェックボックス（真偽値の選択肢の表示名は値のまま来るため、カラムの listFormat の文言を当てる）。
// - 入力の途中の値はこの部品の中だけに持ち、送るまで記憶に置かない（表を描き直さない。performance-design.md の 1節）。
// - 検索（Enter か［検索］）の前に、型・範囲の向き・長さ（1,000 コードポイント）を画面で確かめ、誤りがあればその項目に出し、
//   最初の誤りの項目へフォーカスを移す。直前の一覧は消さない。サーバーの 400 の誤りも同じ項目に結ぶ。
// - 開閉のボタンに aria-expanded・aria-controls。範囲と選択肢は fieldset・legend。誤りは aria-invalid・aria-describedby。
// - 入力は autocomplete="off"（入力の履歴に検索の値を残さない。R-06）。DOM の id にカラム名を入れない（項目の番号で作る）。
import { Button, Checkbox, FormField, TextInput } from 'make-you-chic-ui'
import { useId, useRef, useState, type FormEvent } from 'react'
import { useTranslation } from 'react-i18next'
import type { DisplayLanguage } from '../../app/i18n/resolveLanguage'
import type {
  BusinessFieldError,
  ColumnDefinition,
  SearchField,
  SearchInput,
  SearchInputs,
  ValueType,
} from './businessDataTypes'
import { fieldErrorMessageKey } from './problemMessage'
import { countActiveConditions, inputOf, validateSearchInputs } from './searchConditions'
import { localizedName } from './tablesText'
import { formatCell, type ValueTexts } from './valueFormat'
import './SearchPanel.css'

export interface SearchPanelProps {
  fields: readonly SearchField[]
  /** カラムの定義（真偽値の選択肢の文言に listFormat を使う） */
  columns: readonly ColumnDefinition[]
  /** 送った検索の入力（この値で入力を始める） */
  values: SearchInputs
  /** サーバーの 400 の項目ごとの誤り */
  serverErrors: readonly BusinessFieldError[]
  expanded: boolean
  language: DisplayLanguage
  texts: ValueTexts
  onSearch: (values: SearchInputs) => void
  onClear: () => void
  onToggle: () => void
}

/** 型ごとの入力の種類 */
function inputAttributes(type: ValueType): {
  type: string
  inputMode?: 'numeric' | 'decimal'
  step?: number
} {
  switch (type) {
    case 'DATE':
      return { type: 'date' }
    case 'DATETIME':
    case 'DATETIME_TZ':
      return { type: 'datetime-local', step: 1 }
    case 'TIME':
    case 'TIME_TZ':
      return { type: 'time', step: 1 }
    case 'INTEGER':
    case 'BIG_INTEGER':
      return { type: 'text', inputMode: 'numeric' }
    case 'DECIMAL':
      return { type: 'text', inputMode: 'decimal' }
    default:
      return { type: 'text' }
  }
}

/** 項目の誤りの文 */
function errorText(
  t: (key: string) => string,
  language: DisplayLanguage,
  error: BusinessFieldError | undefined,
): string | undefined {
  if (error === undefined) {
    return undefined
  }
  if (error.reason === 'PATTERN_MISMATCH' && error.message !== undefined) {
    return localizedName(error.message, language, t(fieldErrorMessageKey(error.reason)))
  }
  return t(fieldErrorMessageKey(error.reason))
}

/** S1 の検索の領域 */
export function SearchPanel({
  fields,
  columns,
  values,
  serverErrors,
  expanded,
  language,
  texts,
  onSearch,
  onClear,
  onToggle,
}: SearchPanelProps) {
  const { t } = useTranslation()
  const baseId = useId()
  const panelId = `${baseId}-panel`
  const [draft, setDraft] = useState<SearchInputs>(values)
  const [clientErrors, setClientErrors] = useState<Readonly<Record<string, string>> | null>(null)
  const firstInputs = useRef<(HTMLInputElement | null)[]>([])

  const errorOf = (column: string): BusinessFieldError | undefined => {
    if (clientErrors !== null) {
      return Object.hasOwn(clientErrors, column)
        ? { field: column, reason: clientErrors[column] }
        : undefined
    }
    return serverErrors.find((error) => error.field === column)
  }

  function update(column: string, next: Partial<SearchInput>): void {
    setDraft((prev) => ({ ...prev, [column]: { ...inputOf(prev, column), ...next } }))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault()
    const errors = validateSearchInputs(fields, draft)
    const firstError = fields.findIndex((field) => Object.hasOwn(errors, field.column))
    if (firstError >= 0) {
      setClientErrors(errors)
      firstInputs.current[firstError]?.focus()
      return
    }
    setClientErrors(null)
    onSearch(draft)
  }

  function handleClear(): void {
    setDraft({})
    setClientErrors(null)
    onClear()
  }

  const choiceLabel = (field: SearchField, value: string, label: string): string => {
    if (field.valueType !== 'BOOLEAN') {
      return label
    }
    const column = columns.find((item) => item.column === field.column)
    return formatCell(
      { valueType: 'BOOLEAN', listFormat: column?.listFormat ?? null, masked: false },
      value,
      texts,
    ).text
  }

  const activeCount = countActiveConditions(fields, values)

  return (
    <section className="tables-search" aria-label={t('tables.search.heading')}>
      <Button
        variant="ghost"
        size="sm"
        aria-expanded={expanded}
        aria-controls={panelId}
        onClick={onToggle}
        data-testid="tables-search-toggle"
      >
        {t('tables.search.heading')}
        {!expanded &&
          activeCount > 0 &&
          ` ${t('tables.search.conditionCount', { count: activeCount })}`}
      </Button>
      <form
        id={panelId}
        className="tables-search-form"
        hidden={!expanded}
        onSubmit={handleSubmit}
        noValidate
        data-testid="tables-search-form"
      >
        <div className="tables-search-fields">
          {fields.map((field, index) => {
            const name = localizedName(field.displayName, language, field.column)
            const op = t(`tables.search.op.${field.operator}`)
            const label = t('tables.search.fieldLabel', { name, op })
            const input = inputOf(draft, field.column)
            const error = errorText(t, language, errorOf(field.column))
            const errorId = `${baseId}-f${index}-error`
            const attributes = inputAttributes(field.valueType)
            const keep = (element: HTMLInputElement | null) => {
              firstInputs.current[index] = element
            }
            if (field.operator === 'RANGE') {
              return (
                <fieldset key={field.column} className="tables-search-group">
                  <legend>{label}</legend>
                  <div className="tables-search-range">
                    <FormField label={t('tables.search.from', { name })}>
                      <TextInput
                        ref={keep}
                        {...attributes}
                        autoComplete="off"
                        value={input.from}
                        aria-invalid={error !== undefined || undefined}
                        aria-describedby={error !== undefined ? errorId : undefined}
                        onChange={(value) => update(field.column, { from: value })}
                      />
                    </FormField>
                    <span aria-hidden="true">〜</span>
                    <FormField label={t('tables.search.to', { name })}>
                      <TextInput
                        {...attributes}
                        autoComplete="off"
                        value={input.to}
                        aria-invalid={error !== undefined || undefined}
                        aria-describedby={error !== undefined ? errorId : undefined}
                        onChange={(value) => update(field.column, { to: value })}
                      />
                    </FormField>
                  </div>
                  {error !== undefined && (
                    <p id={errorId} className="tables-search-error" role="alert">
                      {error}
                    </p>
                  )}
                </fieldset>
              )
            }
            if (field.operator === 'CHOICE') {
              return (
                <fieldset key={field.column} className="tables-search-group">
                  <legend>{label}</legend>
                  <div className="tables-search-choices">
                    {field.options.map((option, optionIndex) => (
                      <Checkbox
                        key={option.value}
                        ref={optionIndex === 0 ? keep : undefined}
                        label={choiceLabel(
                          field,
                          option.value,
                          localizedName(option.label, language, option.value),
                        )}
                        checked={input.values.includes(option.value)}
                        aria-invalid={error !== undefined || undefined}
                        aria-describedby={error !== undefined ? errorId : undefined}
                        onChange={(checked) =>
                          update(field.column, {
                            values: checked
                              ? [...input.values, option.value]
                              : input.values.filter((value) => value !== option.value),
                          })
                        }
                      />
                    ))}
                  </div>
                  {error !== undefined && (
                    <p id={errorId} className="tables-search-error" role="alert">
                      {error}
                    </p>
                  )}
                </fieldset>
              )
            }
            return (
              <FormField key={field.column} label={label} error={error}>
                <TextInput
                  ref={keep}
                  {...attributes}
                  autoComplete="off"
                  value={input.value}
                  onChange={(value) => update(field.column, { value })}
                />
              </FormField>
            )
          })}
        </div>
        <div className="tables-search-buttons">
          <Button type="button" variant="secondary" onClick={handleClear}>
            {t('tables.search.clear')}
          </Button>
          <Button type="submit" variant="primary">
            {t('tables.search.submit')}
          </Button>
        </div>
      </form>
    </section>
  )
}
