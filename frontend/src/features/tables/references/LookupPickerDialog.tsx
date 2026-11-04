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
// 参照ピッカーのダイアログ（M5。Intent 261029-reference-lookup-cleanup の U3、D21〜D30、frontend-components.md の 4.4）。
// - make-you-chic-ui の Modal（size="lg"・背景のクリックで閉じない・closeLabel）。開いたら最初の条件へ、閉じたら「選ぶ…」へ
//   フォーカスを移す。Escape と「閉じる」で閉じ、値は変えない（D29）。
// - 条件と「検索」は1つの <form>（Enter で検索）。入力の部品は searchInputFor（valueType と演算子。D22）。
// - 結果は ScrollRegion で包んだ Table。1列目は「選ぶ」、列の鍵は select・c0〜、getRowId は r0〜（行の値を data-testid・id に
//   載せない。D34）。0 件では Table を描かず、2ページ目以降は「前のページへ」だけ（D26）。
// - 状態の文: 初期・空の条件・検索中・0 件は role="status"、失敗・権限は role="alert"、件数の変化は aria-live="polite"。
// - 行の値（ラベル・セル）は文字として描く（HTML にしない・href にしない）。
// - 行のラベルはラベルの列の型、結果の列は列の valueType で、日時を表示のタイムゾーンの書式にする（Intent
//   261102-timezone-ui-polish の U4、計画 Step 11、D32）。選んだ行は、返された値とラベルの文字のまま渡す（D33）。
// - 日時の一致の条件（計画 Step 13、D34・D26・D27）は DateTimeField（文字の欄と案内）。夏時間で2回ある時刻は、欄を離れたときか
//   「検索」の押下で2択を出し、選ぶまで検索させない。押下で未選択なら2択の最初の選択肢へフォーカスを移す。
import { Button, FormField, Modal, Select, Table, TextInput } from 'make-you-chic-ui'
import type { TableColumn, TableLabels } from 'make-you-chic-ui'
import { useEffect, useMemo, useRef, useState, type RefObject } from 'react'
import { useTranslation } from 'react-i18next'
import { useDisplayLanguage } from '../../../app/i18n/I18nProvider'
import { NullValue } from '../../../shared/null-value/NullValue'
import { ScrollRegion } from '../../../shared/scroll-region/ScrollRegion'
import { PICKER_PAGE_SIZE } from '../businessDataParse'
import type { PickerInfo, ReferenceRow, ReferenceSource, ValueType } from '../businessDataTypes'
import { DateTimeField } from '../DateTimeField'
import { localizedName, useValueTexts } from '../tablesText'
import type { FieldTimeZone } from '../useTablesTimeZone'
import type { CellTimeZone } from '../valueFormat'
import { pickerPaging, searchInputFor, type PickerInputError } from './pickerSearch'
import { formatReferenceText } from './referenceField'
import { useLookupSearch, type SearchReference } from './useLookupSearch'
import './reference.css'

export interface LookupPickerDialogProps {
  /** 見出しと表の名前に使う項目名 */
  name: string
  picker: PickerInfo
  source: ReferenceSource
  finalFocusRef: RefObject<HTMLElement | null>
  onSelect: (row: ReferenceRow) => void
  onClose: () => void
  /** 検索の関数（テストで差し替える） */
  search?: SearchReference
  /** ラベルの列の型（型が分からなければ null。D31・D32） */
  labelValueType?: ValueType | null
  /** 参照元のカラムの型（値の書式に使う） */
  valueType?: ValueType
  /** 表示のタイムゾーンの状態（作成・更新の画面がフォームを開いたときに固定した値） */
  timeZone: CellTimeZone
  /** 今の時刻（ISO 8601。条件の案内の今の時差に使う。画面を開いたときに1回だけ取った値） */
  now: string
}

interface PickerTableRow {
  index: number
  row: ReferenceRow
}

/** 条件の誤りの文の鍵（日時の誤りは業務データの項目の誤りと同じ文） */
function pickerErrorKey(error: PickerInputError): string {
  switch (error) {
    case 'too-long':
      return 'tables.picker.tooLong'
    case 'invalid-value':
      return 'tables.picker.invalidValue'
    default:
      return `tables.error.${error}`
  }
}

/** 参照ピッカーのダイアログ */
export function LookupPickerDialog({
  name,
  picker,
  source,
  finalFocusRef,
  onSelect,
  onClose,
  search,
  labelValueType = null,
  valueType = 'STRING',
  timeZone,
  now,
}: LookupPickerDialogProps) {
  const { t } = useTranslation()
  const language = useDisplayLanguage()
  const texts = useValueTexts()
  const timeZoneName = timeZone.status === 'ready' ? timeZone.timeZone : null
  const fieldTimeZone: FieldTimeZone = timeZone
  const lookup = useLookupSearch(source, picker, search, timeZoneName)
  const { state, inputs, errors, overlaps, overlapChoices, goToPage, retry } = lookup
  const { setInput, setOverlapChoice, checkOverlap, submit } = lookup
  const firstRef = useRef<HTMLElement | null>(null)
  const formRef = useRef<HTMLFormElement | null>(null)
  /** 未選択の2択へフォーカスを移す先（条件の番号。2択を描いた後に移す） */
  const [overlapFocus, setOverlapFocus] = useState<{ index: number } | null>(null)
  useEffect(() => {
    if (overlapFocus !== null) {
      formRef.current
        ?.querySelector<HTMLInputElement>(
          `[data-testid="tables-picker-field-c${overlapFocus.index}-overlap"] input`,
        )
        ?.focus()
    }
  }, [overlapFocus])
  const resultsName = t('tables.picker.results', { field: name })

  const columns = useMemo<TableColumn<PickerTableRow>[]>(
    () => [
      {
        key: 'select',
        header: t('tables.picker.selectColumn'),
        render: (item) => (
          <Button
            variant="secondary"
            size="sm"
            aria-label={t('tables.picker.selectRow', {
              label:
                item.row.label === null
                  ? formatReferenceText(item.row.value, valueType, { texts, timeZone })
                  : formatReferenceText(item.row.label, labelValueType, { texts, timeZone }),
            })}
            onClick={() => onSelect(item.row)}
            data-testid={`tables-picker-select-r${item.index}`}
          >
            {t('tables.picker.selectColumn')}
          </Button>
        ),
      },
      ...picker.listColumns.map((column, columnIndex): TableColumn<PickerTableRow> => ({
        key: `c${columnIndex}`,
        header: localizedName(column.displayName, language, column.column),
        render: (item) => {
          const cell = item.row.cells[columnIndex]
          return cell === null || cell === undefined ? (
            <NullValue text={texts.null} spokenText={t('tables.value.nullSpoken')} />
          ) : (
            <span className="tables-picker-cell">
              {formatReferenceText(cell, column.valueType, { texts, timeZone })}
            </span>
          )
        },
      })),
    ],
    [picker, language, texts, t, onSelect, labelValueType, valueType, timeZone],
  )

  const searching = state.kind === 'searching'
  let result: React.JSX.Element | null = null
  let live = ''
  if (state.kind === 'initial') {
    result = (
      <p role="status" className="tables-text" data-testid="tables-picker-status">
        {t('tables.picker.initial')}
      </p>
    )
  } else if (state.kind === 'empty-condition') {
    result = (
      <p role="status" className="tables-text" data-testid="tables-picker-status">
        {t('tables.picker.emptyCondition')}
      </p>
    )
  } else if (state.kind === 'searching') {
    result = (
      <p role="status" className="tables-text" data-testid="tables-picker-status">
        {t('tables.picker.searching')}
      </p>
    )
  } else if (state.kind === 'failed') {
    result = (
      <div className="tables-picker-problem">
        <p role="alert" className="tables-text" data-testid="tables-picker-status">
          {state.message === 'mismatch' ? t('tables.problem.mismatch') : t('tables.picker.failed')}
        </p>
        <Button variant="secondary" onClick={retry} data-testid="tables-picker-retry">
          {t('tables.picker.retry')}
        </Button>
      </div>
    )
  } else if (state.kind === 'denied') {
    result = (
      <p role="alert" className="tables-text" data-testid="tables-picker-status">
        {t('tables.picker.denied')}
      </p>
    )
  } else {
    const rows = state.kind === 'results' ? state.rows : []
    const paging = pickerPaging(
      state.page,
      rows.length,
      state.kind === 'results' ? state.hasNext : false,
    )
    if (paging.kind === 'empty') {
      result = (
        <div className="tables-picker-problem">
          <p role="status" className="tables-text" data-testid="tables-picker-status">
            {paging.backTo === null ? t('tables.picker.noResults') : t('tables.picker.pageEmpty')}
          </p>
          {paging.backTo !== null && (
            <Button
              variant="secondary"
              onClick={() => goToPage(paging.backTo as number)}
              data-testid="tables-picker-back"
            >
              {t('tables.picker.backPage')}
            </Button>
          )}
        </div>
      )
    } else {
      live = t(paging.rangeKey, { from: paging.from, to: paging.to })
      const labels: Partial<TableLabels> = {
        previousPage: t('tables.picker.previousPage'),
        nextPage: t('tables.picker.nextPage'),
        emptyStatus: t('tables.picker.noResults'),
        pageStatus: () => t(paging.statusKey, { page: state.page }),
      }
      result = (
        <ScrollRegion label={resultsName} testId="tables-picker-results">
          <Table<PickerTableRow>
            columns={columns}
            data={rows.map((row, index) => ({ index, row }))}
            totalCount={paging.totalCount}
            page={state.page}
            pageSize={PICKER_PAGE_SIZE}
            onPageChange={goToPage}
            getRowId={(item) => `r${item.index}`}
            aria-label={resultsName}
            labels={labels}
          />
        </ScrollRegion>
      )
    }
  }

  return (
    <Modal
      open
      size="lg"
      title={t('tables.picker.title', { field: name })}
      onClose={onClose}
      initialFocusRef={firstRef}
      finalFocusRef={finalFocusRef}
      closeLabel={t('tables.picker.close')}
      closeOnBackdropClick={false}
    >
      <div className="tables-picker" data-testid="tables-picker">
        <form
          ref={formRef}
          className="tables-picker-form"
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            const found = submit()
            const first = picker.searchFields.findIndex(
              (field) => found !== null && Object.hasOwn(found, field.column),
            )
            if (
              found !== null &&
              first >= 0 &&
              found[picker.searchFields[first].column] === 'DATE_TIME_OVERLAP'
            ) {
              setOverlapFocus({ index: first })
            }
          }}
          data-testid="tables-picker-form"
        >
          <div className="tables-picker-fields">
            {picker.searchFields.map((field, index) => {
              const fieldName = localizedName(field.displayName, language, field.column)
              const spec = searchInputFor(field)
              const error = Object.hasOwn(errors, field.column) ? errors[field.column] : undefined
              const keep =
                index === 0
                  ? (element: HTMLElement | null) => {
                      firstRef.current = element
                    }
                  : undefined
              const value = Object.hasOwn(inputs, field.column) ? inputs[field.column] : ''
              const fieldLabel = t(`tables.picker.operator.${field.operator}`, { name: fieldName })
              const errorMessage =
                error === undefined
                  ? undefined
                  : t(pickerErrorKey(error), { zone: timeZoneName ?? '' })
              if (spec.kind === 'text' && spec.dateTime !== undefined) {
                const overlapPending = error === 'DATE_TIME_OVERLAP'
                return (
                  <DateTimeField
                    key={field.column}
                    kind={spec.dateTime}
                    purpose="equals"
                    label={fieldLabel}
                    name={`tables-picker-c${index}`}
                    value={value}
                    onChange={(next) => setInput(field.column, next)}
                    onBlur={() => checkOverlap(field.column)}
                    timeZone={fieldTimeZone}
                    now={now}
                    error={overlapPending ? undefined : errorMessage}
                    overlap={Object.hasOwn(overlaps, field.column) ? overlaps[field.column] : null}
                    overlapChoice={
                      Object.hasOwn(overlapChoices, field.column)
                        ? overlapChoices[field.column]
                        : null
                    }
                    onOverlapChoice={(choice) => setOverlapChoice(field.column, choice)}
                    overlapError={overlapPending ? errorMessage : undefined}
                    inputRef={keep}
                    testId={`tables-picker-field-c${index}`}
                  />
                )
              }
              return (
                <div key={field.column} data-testid={`tables-picker-field-c${index}`}>
                  <FormField
                    label={fieldLabel}
                    error={errorMessage}
                    helperText={
                      spec.kind === 'text' && spec.hintKeys.length > 0
                        ? spec.hintKeys.map((key) => t(key)).join(' ')
                        : undefined
                    }
                  >
                    {spec.kind === 'boolean' ? (
                      <Select
                        ref={keep}
                        options={[
                          { value: '', label: t('tables.picker.unspecified') },
                          { value: 'true', label: t('tables.value.yes') },
                          { value: 'false', label: t('tables.value.no') },
                        ]}
                        value={value}
                        onChange={(next) => setInput(field.column, next)}
                      />
                    ) : (
                      <TextInput
                        ref={keep}
                        type={spec.type}
                        inputMode={spec.inputMode}
                        step={spec.step}
                        autoComplete="off"
                        value={value}
                        onChange={(next) => setInput(field.column, next)}
                      />
                    )}
                  </FormField>
                </div>
              )
            })}
          </div>
          <div className="tables-picker-actions">
            <Button
              type="submit"
              variant="primary"
              loading={searching}
              data-testid="tables-picker-search"
            >
              {t('tables.picker.search')}
            </Button>
          </div>
        </form>
        {result}
        <p className="tables-visually-hidden" aria-live="polite" data-testid="tables-picker-live">
          {live}
        </p>
      </div>
    </Modal>
  )
}
