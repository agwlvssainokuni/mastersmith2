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
import { Button, FormField, Modal, Select, Table, TextInput } from 'make-you-chic-ui'
import type { TableColumn, TableLabels } from 'make-you-chic-ui'
import { useMemo, useRef, type RefObject } from 'react'
import { useTranslation } from 'react-i18next'
import { useDisplayLanguage } from '../../../app/i18n/I18nProvider'
import { NullValue } from '../../../shared/null-value/NullValue'
import { ScrollRegion } from '../../../shared/scroll-region/ScrollRegion'
import { PICKER_PAGE_SIZE } from '../businessDataParse'
import type { PickerInfo, ReferenceRow, ReferenceSource } from '../businessDataTypes'
import { localizedName, useValueTexts } from '../tablesText'
import { pickerPaging, searchInputFor } from './pickerSearch'
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
}

interface PickerTableRow {
  index: number
  row: ReferenceRow
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
}: LookupPickerDialogProps) {
  const { t } = useTranslation()
  const language = useDisplayLanguage()
  const texts = useValueTexts()
  const lookup = useLookupSearch(source, picker, search)
  const { state, inputs, errors, setInput, submit, goToPage, retry } = lookup
  const firstRef = useRef<HTMLElement | null>(null)
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
            aria-label={t('tables.picker.selectRow', { label: item.row.label ?? item.row.value })}
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
            <span className="tables-picker-cell">{cell}</span>
          )
        },
      })),
    ],
    [picker, language, texts, t, onSelect],
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
          className="tables-picker-form"
          noValidate
          onSubmit={(event) => {
            event.preventDefault()
            submit()
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
              return (
                <div key={field.column} data-testid={`tables-picker-field-c${index}`}>
                  <FormField
                    label={t(`tables.picker.operator.${field.operator}`, { name: fieldName })}
                    error={
                      error === undefined
                        ? undefined
                        : t(
                            error === 'too-long'
                              ? 'tables.picker.tooLong'
                              : 'tables.picker.invalidValue',
                          )
                    }
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
