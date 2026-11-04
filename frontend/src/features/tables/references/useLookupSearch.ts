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
// 参照ピッカーの検索の状態（Intent 261029-reference-lookup-cleanup の U3、D21・D23・D24・D26・D27・D29、
// frontend-components.md の 4.4）。ダイアログを開くたびに部品ごと作り直す（前の結果を持ち越さない）。
// - 要求ごとに世代を進め、古い答え・閉じた後の答えを捨てる。検索中の「検索」・ページ送りの押下は捨てる（D24）。
// - 失敗の分け方（D27）: 400 VALIDATION_FAILED の fieldErrors のうち searchFields にあるカラムの INVALID_VALUE だけを条件の誤り、
//   ほかの 400 は failed（retry）、403 は denied、409 BUSINESS_TARGET_DB_MISMATCH は failed（mismatch）、ほかは failed（retry）。
// - 遅いときの知らせ（useSlowNotice）は使わない（NFR 設計の Q4: A）。値は React の状態にだけ持つ。
import { useCallback, useEffect, useRef, useState } from 'react'
import { searchReferenceRows } from '../businessDataApi'
import { parseBusinessProblem } from '../businessDataParse'
import type {
  PickerInfo,
  ReferenceCondition,
  ReferenceRow,
  ReferenceRowPage,
  ReferenceSource,
} from '../businessDataTypes'
import type { OverlapChoice } from '../../../shared/format/timeZone'
import { dateTimeKindOf, resolveDateTimeInput, type OverlapOffsets } from '../resolveDateTimeInput'
import { validatePickerInputs, type PickerInputError, type PickerInputs } from './pickerSearch'

/** ピッカーの状態（frontend-components.md の 4.4） */
export type PickerState =
  | { kind: 'initial' }
  | { kind: 'empty-condition' }
  | { kind: 'searching'; page: number }
  | { kind: 'results'; page: number; rows: readonly ReferenceRow[]; hasNext: boolean }
  | { kind: 'no-results'; page: number }
  | { kind: 'failed'; message: 'retry' | 'mismatch' }
  | { kind: 'denied' }

/** 検索の関数（テストで差し替える） */
export type SearchReference = (
  source: ReferenceSource,
  conditions: readonly ReferenceCondition[],
  page: number,
) => Promise<ReferenceRowPage>

/** useLookupSearch が返す値 */
export interface LookupSearch {
  state: PickerState
  inputs: PickerInputs
  errors: Readonly<Record<string, PickerInputError>>
  /** 一致の時点の条件で、夏時間で2回ある時刻の2択の2つの時差（条件ごと。出さない条件は項目なし） */
  overlaps: Readonly<Record<string, OverlapOffsets>>
  /** 条件ごとの2択の選択（未選択は項目なし） */
  overlapChoices: Readonly<Record<string, OverlapChoice>>
  /** 条件の文字を変える（その条件の2択の選択と2択を解く。D21） */
  setInput: (column: string, value: string) => void
  /** 2択を選ぶ（その条件の誤りを消す） */
  setOverlapChoice: (column: string, choice: OverlapChoice) => void
  /** 条件の欄を離れたとき、2回ある時刻なら2択を出す */
  checkOverlap: (column: string) => void
  /** 「検索」（1ページ目から）。画面の確かめの誤り（無ければ空、検索中で捨てたときは null）を返す */
  submit: () => Readonly<Record<string, PickerInputError>> | null
  /** ページ送り・「前のページへ」（同じ条件で読む） */
  goToPage: (page: number) => void
  /** 「再度検索」（最後に送った条件とページで読む） */
  retry: () => void
}

/**
 * 参照ピッカーの検索の状態を返す。
 *
 * @param source 参照元のスキーマ・テーブル・カラム
 * @param picker ピッカーの情報
 * @param search 検索の関数（テストで差し替える）
 * @param timeZone 表示のタイムゾーンの名前（時点を表す型の一致の条件に使う。読み込めないときは null で、その条件は誤り）
 */
export function useLookupSearch(
  source: ReferenceSource,
  picker: PickerInfo,
  search: SearchReference = searchReferenceRows,
  timeZone: string | null = null,
): LookupSearch {
  const [state, setState] = useState<PickerState>({ kind: 'initial' })
  const [inputs, setInputs] = useState<PickerInputs>({})
  const [errors, setErrors] = useState<Readonly<Record<string, PickerInputError>>>({})
  const [overlaps, setOverlaps] = useState<Readonly<Record<string, OverlapOffsets>>>({})
  const [overlapChoices, setOverlapChoices] = useState<Readonly<Record<string, OverlapChoice>>>({})
  const generation = useRef(0)
  const searching = useRef(false)
  const last = useRef<{ conditions: readonly ReferenceCondition[]; page: number } | null>(null)

  // 閉じた後の答えを捨てる
  useEffect(
    () => () => {
      generation.current += 1
    },
    [],
  )

  const run = useCallback(
    (conditions: readonly ReferenceCondition[], page: number, before: PickerState) => {
      if (searching.current) {
        return
      }
      searching.current = true
      generation.current += 1
      const mine = generation.current
      last.current = { conditions, page }
      setState({ kind: 'searching', page })
      search(source, conditions, page).then(
        (result) => {
          if (generation.current !== mine) {
            return
          }
          searching.current = false
          setState(
            result.rows.length > 0
              ? { kind: 'results', page: result.page, rows: result.rows, hasNext: result.hasNext }
              : { kind: 'no-results', page: result.page },
          )
        },
        (error: unknown) => {
          if (generation.current !== mine) {
            return
          }
          searching.current = false
          const problem = parseBusinessProblem(error)
          if (problem?.status === 400) {
            const known = new Set(picker.searchFields.map((field) => field.column))
            const fieldErrors = problem.fieldErrors.filter(
              (item) => item.reason === 'INVALID_VALUE' && known.has(item.field),
            )
            if (fieldErrors.length > 0) {
              setErrors(
                Object.fromEntries(
                  fieldErrors.map((item): [string, PickerInputError] => [
                    item.field,
                    'invalid-value',
                  ]),
                ),
              )
              setState(before)
              return
            }
          }
          if (problem?.status === 403) {
            setState({ kind: 'denied' })
          } else if (problem?.code === 'BUSINESS_TARGET_DB_MISMATCH') {
            setState({ kind: 'failed', message: 'mismatch' })
          } else {
            setState({ kind: 'failed', message: 'retry' })
          }
        },
      )
    },
    [search, source, picker],
  )

  const setInput = useCallback((column: string, value: string) => {
    setInputs((previous) => ({ ...previous, [column]: value }))
    setOverlaps((previous) => withoutKey(previous, column))
    setOverlapChoices((previous) => withoutKey(previous, column))
  }, [])

  const setOverlapChoice = useCallback((column: string, choice: OverlapChoice) => {
    setOverlapChoices((previous) => ({ ...previous, [column]: choice }))
    setErrors((previous) => withoutKey(previous, column))
  }, [])

  const checkOverlap = useCallback(
    (column: string) => {
      const field = picker.searchFields.find((item) => item.column === column)
      const text = Object.hasOwn(inputs, column) ? inputs[column] : ''
      if (
        field === undefined ||
        field.operator !== 'EQUALS' ||
        dateTimeKindOf(field.valueType) !== 'instant' ||
        text === '' ||
        timeZone === null
      ) {
        return
      }
      const resolved = resolveDateTimeInput({
        text,
        kind: 'instant',
        timeZone,
        purpose: 'equals',
        overlapChoice: Object.hasOwn(overlapChoices, column) ? overlapChoices[column] : null,
      })
      const offsets =
        resolved.kind === 'overlap-pending' || resolved.kind === 'ok' ? resolved.overlap : null
      setOverlaps((previous) =>
        offsets === null ? withoutKey(previous, column) : { ...previous, [column]: offsets },
      )
    },
    [picker, inputs, timeZone, overlapChoices],
  )

  const submit = useCallback((): Readonly<Record<string, PickerInputError>> | null => {
    if (searching.current) {
      return null
    }
    // 日時の一致の条件は、表示のタイムゾーンで時点に直す（Intent 261102-timezone-ui-polish の U4、D34）
    const checked = validatePickerInputs(picker.searchFields, inputs, { timeZone, overlapChoices })
    if (checked.kind === 'empty') {
      setErrors({})
      setState({ kind: 'empty-condition' })
      return {}
    }
    if (checked.kind === 'errors') {
      setErrors(checked.errors)
      if (checked.overlaps !== undefined) {
        const found = checked.overlaps
        setOverlaps((previous) => ({ ...previous, ...found }))
      }
      return checked.errors
    }
    setErrors({})
    run(checked.conditions, 1, state.kind === 'empty-condition' ? { kind: 'initial' } : state)
    return {}
  }, [picker, inputs, run, state, timeZone, overlapChoices])

  const goToPage = useCallback(
    (page: number) => {
      if (searching.current || last.current === null) {
        return
      }
      run(last.current.conditions, page, state)
    },
    [run, state],
  )

  const retry = useCallback(() => {
    if (searching.current || last.current === null) {
      return
    }
    run(last.current.conditions, last.current.page, state)
  }, [run, state])

  return {
    state,
    inputs,
    errors,
    overlaps,
    overlapChoices,
    setInput,
    setOverlapChoice,
    checkOverlap,
    submit,
    goToPage,
    retry,
  }
}

/** 組から1つの鍵を除く（無ければ同じ物を返す） */
function withoutKey<T>(
  record: Readonly<Record<string, T>>,
  key: string,
): Readonly<Record<string, T>> {
  if (!Object.hasOwn(record, key)) {
    return record
  }
  const rest = { ...record }
  delete rest[key]
  return rest
}
