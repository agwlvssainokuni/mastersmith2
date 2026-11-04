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
// - 日時の条件（Intent 261102-timezone-ui-polish の U4、計画 Step 13、D23〜D28、frontend-components.md の 3節・4節）:
//   EQUALS・RANGE の時点を表す型（DATETIME_TZ）と時差を持たない日時（DATETIME）は DateTimeField（文字の欄と案内）にする。
//   範囲は両端の案内を入れ物に1つだけ出す。一致の時点を表す型で夏時間に2回ある時刻は、欄を離れたときか検索の押下で
//   2択を出し、選ぶまで検索させない（Q4: A）。未選択の誤りでは2択の最初の選択肢へフォーカスを移す（NFR 設計の Q2 の 1）。
//   表示のタイムゾーンを使えない間は時点の欄を使えない状態にする（検索の押下は、時点の条件が空なら通す）。
//   送った入力（values）が今は直せない日時（前の版が保存した T 区切りの値など）なら、開いたときから欄に値を残したまま誤りを
//   出す（その条件は一覧の要求で送られない。黙って捨てない・自動で直さない。依頼者の決定）。
import { Button, Checkbox, FormField, TextInput } from 'make-you-chic-ui'
import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
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
import { DateTimeField, dateTimeHint } from './DateTimeField'
import { fieldErrorMessageKey } from './problemMessage'
import { dateTimeKindOf, resolveDateTimeInput, type OverlapOffsets } from './resolveDateTimeInput'
import {
  countActiveConditions,
  inputOf,
  validateSearchInputs,
  type SearchInputError,
} from './searchConditions'
import { localizedName } from './tablesText'
import type { FieldTimeZone } from './useTablesTimeZone'
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
  /** 表示のタイムゾーンの状態（時点を表す型の条件に使う） */
  timeZone: FieldTimeZone
  /** 今の時刻（ISO 8601。案内の今の時差に使う。画面を開いたときに1回だけ取る） */
  now: string
  onSearch: (values: SearchInputs) => void
  onClear: () => void
  onToggle: () => void
}

/** 送った入力を開いたときに見せる日時の誤り（表示のタイムゾーンを使えないときの理由は欄の案内が伝える） */
const STORED_DATE_TIME_ERRORS: readonly SearchInputError[] = [
  'DATE_TIME_FORMAT',
  'DATE_TIME_GAP',
  'DATE_TIME_OVERLAP',
]

/** 型ごとの入力の種類（日時の条件は DateTimeField で描く） */
function inputAttributes(type: ValueType): {
  type: string
  inputMode?: 'numeric' | 'decimal'
  step?: number
} {
  switch (type) {
    case 'DATE':
      return { type: 'date' }
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

/** 項目の誤りの文（日時の誤りはタイムゾーンの名前を差し込む） */
function errorText(
  t: (key: string, options?: Record<string, string>) => string,
  language: DisplayLanguage,
  error: BusinessFieldError | undefined,
  zone: string,
): string | undefined {
  if (error === undefined) {
    return undefined
  }
  if (error.reason === 'PATTERN_MISMATCH' && error.message !== undefined) {
    return localizedName(error.message, language, t(fieldErrorMessageKey(error.reason)))
  }
  return t(fieldErrorMessageKey(error.reason), { zone })
}

/**
 * 一致の時点を表す型の条件の2択の時差を求める（2回ある時刻でなければ null）。欄を離れたとき・検索の押下・開いたときだけ呼ぶ。
 */
function equalsOverlap(
  field: SearchField,
  input: SearchInput,
  timeZone: string | null,
): OverlapOffsets | null {
  if (
    field.operator !== 'EQUALS' ||
    dateTimeKindOf(field.valueType) !== 'instant' ||
    input.value === '' ||
    timeZone === null
  ) {
    return null
  }
  const resolved = resolveDateTimeInput({
    text: input.value,
    kind: 'instant',
    timeZone,
    purpose: 'equals',
    overlapChoice: input.overlapChoice ?? null,
  })
  if (resolved.kind === 'overlap-pending') {
    return resolved.overlap
  }
  return resolved.kind === 'ok' ? resolved.overlap : null
}

/** 送った入力の、開いたときに見せる日時の誤り（今の表示のタイムゾーンで直せない値） */
function storedDateTimeErrors(
  fields: readonly SearchField[],
  values: SearchInputs,
  timeZone: string | null,
): Readonly<Record<string, string>> {
  const errors = validateSearchInputs(
    fields.filter((field) => dateTimeKindOf(field.valueType) !== null),
    values,
    timeZone,
  )
  return Object.fromEntries(
    Object.entries(errors).filter(([, reason]) => STORED_DATE_TIME_ERRORS.includes(reason)),
  )
}

/** フォーカスを移す先（項目の番号と、2択の最初の選択肢か） */
interface FocusRequest {
  index: number
  overlap: boolean
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
  timeZone,
  now,
  onSearch,
  onClear,
  onToggle,
}: SearchPanelProps) {
  const { t } = useTranslation()
  const baseId = useId()
  const panelId = `${baseId}-panel`
  const zoneName = timeZone.status === 'ready' ? timeZone.timeZone : null
  const [draft, setDraft] = useState<SearchInputs>(values)
  // 開いたときの誤りと2択（送った入力の日時を今の表示のタイムゾーンで確かめる。開いたときに1回だけ）
  const [storedErrors, setStoredErrors] = useState<Readonly<Record<string, string>>>(() =>
    storedDateTimeErrors(fields, values, zoneName),
  )
  const [overlaps, setOverlaps] = useState<Readonly<Record<string, OverlapOffsets | null>>>(() =>
    Object.fromEntries(
      fields.map((field) => [
        field.column,
        equalsOverlap(field, inputOf(values, field.column), zoneName),
      ]),
    ),
  )
  const [clientErrors, setClientErrors] = useState<Readonly<Record<string, string>> | null>(null)
  const [focusRequest, setFocusRequest] = useState<FocusRequest | null>(null)
  const firstInputs = useRef<(HTMLInputElement | null)[]>([])
  const formRef = useRef<HTMLFormElement | null>(null)

  // 誤りを出した後に、最初の誤りの欄（2択の未選択なら最初の選択肢）へフォーカスを移す（2択は描いた後に移す）
  useEffect(() => {
    if (focusRequest === null) {
      return
    }
    if (focusRequest.overlap) {
      formRef.current
        ?.querySelector<HTMLInputElement>(
          `[data-testid="tables-search-field-f${focusRequest.index}-overlap"] input`,
        )
        ?.focus()
    } else {
      firstInputs.current[focusRequest.index]?.focus()
    }
  }, [focusRequest])

  const errorOf = (column: string): BusinessFieldError | undefined => {
    if (clientErrors !== null) {
      return Object.hasOwn(clientErrors, column)
        ? { field: column, reason: clientErrors[column] }
        : undefined
    }
    if (Object.hasOwn(storedErrors, column)) {
      return { field: column, reason: storedErrors[column] }
    }
    return serverErrors.find((error) => error.field === column)
  }

  function update(column: string, next: Partial<SearchInput>): void {
    setDraft((prev) => ({ ...prev, [column]: { ...inputOf(prev, column), ...next } }))
  }

  /** 日時の欄の文字を変える（2択の選択を解き、2択と開いたときの誤りを消す。D21） */
  function updateDateTime(column: string, next: Partial<SearchInput>): void {
    update(column, { ...next, overlapChoice: null })
    setOverlaps((prev) => ({ ...prev, [column]: null }))
    setStoredErrors((prev) => {
      if (!Object.hasOwn(prev, column)) {
        return prev
      }
      const rest = { ...prev }
      delete rest[column]
      return rest
    })
  }

  /** 一致の時点の欄を離れたとき、2回ある時刻なら2択を出す */
  function checkOverlap(field: SearchField): void {
    const offsets = equalsOverlap(field, inputOf(draft, field.column), zoneName)
    setOverlaps((prev) => ({ ...prev, [field.column]: offsets }))
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault()
    const errors = validateSearchInputs(fields, draft, zoneName)
    const firstError = fields.findIndex((field) => Object.hasOwn(errors, field.column))
    if (firstError >= 0) {
      setClientErrors(errors)
      const nextOverlaps: Record<string, OverlapOffsets | null> = { ...overlaps }
      for (const field of fields) {
        if (errors[field.column] === 'DATE_TIME_OVERLAP') {
          nextOverlaps[field.column] = equalsOverlap(field, inputOf(draft, field.column), zoneName)
        }
      }
      setOverlaps(nextOverlaps)
      setFocusRequest({
        index: firstError,
        overlap: errors[fields[firstError].column] === 'DATE_TIME_OVERLAP',
      })
      return
    }
    setClientErrors(null)
    onSearch(draft)
  }

  function handleClear(): void {
    setDraft({})
    setClientErrors(null)
    setStoredErrors({})
    setOverlaps({})
    onClear()
  }

  const choiceLabel = (field: SearchField, value: string, label: string): string => {
    if (field.valueType !== 'BOOLEAN') {
      return label
    }
    const column = columns.find((item) => item.column === field.column)
    // 真偽値の文言だけを当てる（時点の書式は使わないため、タイムゾーンの状態は問わない）
    return formatCell(
      { valueType: 'BOOLEAN', listFormat: column?.listFormat ?? null, masked: false },
      value,
      texts,
      { status: 'error' },
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
        ref={formRef}
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
            const fieldError = errorOf(field.column)
            const error = errorText(t, language, fieldError, zoneName ?? '')
            const errorId = `${baseId}-f${index}-error`
            const hintId = `${baseId}-f${index}-hint`
            const attributes = inputAttributes(field.valueType)
            const dateTime =
              field.operator === 'EQUALS' || field.operator === 'RANGE'
                ? dateTimeKindOf(field.valueType)
                : null
            const keep = (element: HTMLInputElement | null) => {
              firstInputs.current[index] = element
            }
            if (field.operator === 'RANGE' && dateTime !== null) {
              const describedBy = error !== undefined ? `${hintId} ${errorId}` : hintId
              const common = {
                kind: dateTime,
                timeZone,
                now,
                overlap: null,
                overlapChoice: null,
                onOverlapChoice: () => {},
                showHint: false,
                describedBy,
                invalid: error !== undefined,
              }
              return (
                <fieldset
                  key={field.column}
                  className="tables-search-group"
                  data-testid={`tables-search-field-f${index}`}
                >
                  <legend>{label}</legend>
                  <div className="tables-search-range">
                    <DateTimeField
                      {...common}
                      purpose="range-from"
                      label={t('tables.search.from', { name })}
                      name={`${baseId}-f${index}-from`}
                      value={input.from}
                      onChange={(value) => updateDateTime(field.column, { from: value })}
                      inputRef={keep}
                    />
                    <span aria-hidden="true">〜</span>
                    <DateTimeField
                      {...common}
                      purpose="range-to"
                      label={t('tables.search.to', { name })}
                      name={`${baseId}-f${index}-to`}
                      value={input.to}
                      onChange={(value) => updateDateTime(field.column, { to: value })}
                    />
                  </div>
                  <p id={hintId} className="tables-search-hint">
                    {dateTimeHint(t, dateTime, 'range-from', timeZone, now)}
                  </p>
                  {error !== undefined && (
                    <p id={errorId} className="tables-search-error" role="alert">
                      {error}
                    </p>
                  )}
                </fieldset>
              )
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
            if (dateTime !== null) {
              const overlapPending = fieldError?.reason === 'DATE_TIME_OVERLAP'
              const overlap = Object.hasOwn(overlaps, field.column) ? overlaps[field.column] : null
              return (
                <DateTimeField
                  key={field.column}
                  kind={dateTime}
                  purpose="equals"
                  label={label}
                  name={`${baseId}-f${index}`}
                  value={input.value}
                  onChange={(value) => updateDateTime(field.column, { value })}
                  onBlur={() => checkOverlap(field)}
                  timeZone={timeZone}
                  now={now}
                  error={overlapPending ? undefined : error}
                  overlap={overlap}
                  overlapChoice={input.overlapChoice ?? null}
                  onOverlapChoice={(choice) => {
                    update(field.column, { overlapChoice: choice })
                    setClientErrors((prev) => {
                      if (prev === null || !Object.hasOwn(prev, field.column)) {
                        return prev
                      }
                      const rest = { ...prev }
                      delete rest[field.column]
                      return rest
                    })
                  }}
                  overlapError={overlapPending ? error : undefined}
                  inputRef={keep}
                  testId={`tables-search-field-f${index}`}
                />
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
