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
// 一覧と検索の状態（Intent 261016-business-data-views の U3 の L7、D3・D10〜D14・F1〜F2・F6・F8、frontend-components.md の
// 4節、U3-NFR1.9・2.10・8.7、計画 4.3 の R-04）。
// - 一覧の状態（検索の条件・並べ替え・ページ・件数）はテーブルごとに記憶に持つ（D3）。記憶に無ければ既定の状態で始める。
//   定義が変わったら、使えなくなった検索の条件と並べ替えを外して1ページ目にする（F6 の 2）。
// - 状態が変わるたびに一覧の API を1本だけ送る（操作1回で要求1つ）。要求に世代を付け、古い答えと画面を離れた後の答えを捨てる。
//   読み直しの間は前の表を残し、busy を立てる（D14）。
// - 応答の definitionChanged、400 の needsDefinitionReload は onDefinitionStale を呼ぶ（F6。定義の読み直しは呼ぶ側）。
//   ほかの 400 の fieldErrors は検索の項目に結ぶ（F2 の 4）。
// - 403 の後は業務のメニューと作業ロールを読み直し、業務データの API は送り直さない（F8・U3-NFR8.7）。
// - 決まった間隔の読み直し・自動の再送はしない。
import { useCallback, useEffect, useEffectEvent, useRef, useState } from 'react'
import type { TableRef } from '../../app/navigation/tablePlaceholderPath'
import { searchRows, type SearchRowsRequest } from './businessDataApi'
import { readTableEntry, saveListState } from './businessDataStore'
import type { BusinessFieldError, ListState, RowPage, TableDefinition } from './businessDataTypes'
import { defaultListState, sanitizeListState } from './listState'
import { needsDefinitionReload } from './problemMessage'
import { buildConditions } from './searchConditions'
import {
  toReadFailure,
  useForbiddenFollowUp,
  useWorkRoleKey,
  type ReadFailure,
} from './tableHookSupport'

/** 一覧を読む関数（テストで差し替える） */
export type SearchRowsFn = (request: SearchRowsRequest) => Promise<RowPage>

/** 一覧の状態 */
export type RowSearchStatus = 'idle' | 'loading' | 'ready' | 'problem'

/** useRowSearch が返す値 */
export interface RowSearchState {
  status: RowSearchStatus
  /** 最後に読めたページ（読み直しの間も残す。テーブルや作業ロールが変われば null） */
  page: RowPage | null
  /** 表に替えて出す失敗（S6。status が problem のときだけ） */
  failure: ReadFailure | null
  /** 前の表を残したまま一覧の上に出す失敗（画面に結べない 400・413） */
  notice: ReadFailure | null
  /** 検索の項目に結ぶサーバーの誤り（400） */
  fieldErrors: readonly BusinessFieldError[]
  /** 読み直しの最中か（前の表を残している） */
  busy: boolean
  /** 今の一覧の状態 */
  listState: ListState | null
  /** 一覧の状態を変える（記憶に置き、一覧を読み直す） */
  setListState: (next: ListState) => void
  /** 失敗の後にもう一度読む */
  retry: () => void
}

interface Result {
  key: string
  /** どのテーブル・作業ロールの答えか（ページを残してよいかの判定） */
  scope: string
  page: RowPage | null
  failure: ReadFailure | null
  notice: ReadFailure | null
  fieldErrors: readonly BusinessFieldError[]
}

/**
 * 一覧と検索の状態を返す。
 *
 * @param ref スキーマ名とテーブル名（無ければ null）
 * @param definition テーブルの定義（読み終えるまで null。null の間は要求を送らない）
 * @param onDefinitionStale 定義が変わったと分かったとき（F6）
 * @param search 一覧を読む関数（テストで差し替える）
 */
export function useRowSearch(
  ref: TableRef | null,
  definition: TableDefinition | null,
  onDefinitionStale: () => void,
  search: SearchRowsFn = searchRows,
): RowSearchState {
  const { settled, roleId } = useWorkRoleKey()
  const followUp = useForbiddenFollowUp()
  const [result, setResult] = useState<Result | null>(null)
  const [, setVersion] = useState(0)
  const [attempt, setAttempt] = useState(0)
  const generation = useRef(0)

  const schemaName = ref?.schemaName
  const tableName = ref?.tableName
  const scope = JSON.stringify([schemaName, tableName, roleId])
  // 一覧の状態は記憶を正とする（記憶を書いた後は setVersion で描き直す）。
  const stored =
    settled && schemaName !== undefined && tableName !== undefined && definition !== null
      ? readTableEntry({ schemaName, tableName }, roleId).listState
      : null
  const listState =
    definition === null
      ? null
      : sanitizeListState(stored ?? defaultListState(definition), definition)
  const conditions =
    definition === null || listState === null
      ? []
      : buildConditions(definition.searchFields, listState.search)
  const key = JSON.stringify([
    scope,
    definition?.definitionId ?? null,
    conditions,
    listState?.sort ?? null,
    listState?.page ?? null,
    listState?.size ?? null,
    attempt,
  ])
  const ready = settled && definition !== null && ref !== null && listState !== null

  /** 前の表を残すときのページ（同じテーブル・作業ロールの答えだけ） */
  function keptPage(prev: Result | null): RowPage | null {
    return prev !== null && prev.scope === scope ? prev.page : null
  }

  const query = useEffectEvent((requestKey: string): void => {
    if (definition === null || listState === null || ref === null) {
      return
    }
    generation.current += 1
    const mine = generation.current
    const currentDefinition = definition
    search({
      ref: { schemaName: ref.schemaName, tableName: ref.tableName },
      definitionId: definition.definitionId,
      conditions,
      sort: listState.sort,
      page: listState.page,
      size: listState.size,
    }).then(
      (page) => {
        if (generation.current !== mine) {
          return
        }
        setResult({ key: requestKey, scope, page, failure: null, notice: null, fieldErrors: [] })
        if (page.definitionChanged) {
          onDefinitionStale()
        }
      },
      (error: unknown) => {
        if (generation.current !== mine) {
          return
        }
        const failure = toReadFailure(error)
        if (failure.kind === 'bad-request' || failure.kind === 'payload-too-large') {
          if (needsDefinitionReload(failure.problem, currentDefinition)) {
            setResult((prev) => ({
              key: requestKey,
              scope,
              page: keptPage(prev),
              failure: null,
              notice: null,
              fieldErrors: [],
            }))
            onDefinitionStale()
            return
          }
          const fieldErrors = (failure.problem?.fieldErrors ?? []).filter(
            (item) => item.field !== '',
          )
          // 画面に結べる誤りは検索の項目に出し、結べない誤りは一覧の上に出す。どちらも前の表を残す（F2 の 4）。
          setResult((prev) => ({
            key: requestKey,
            scope,
            page: keptPage(prev),
            failure: null,
            notice: fieldErrors.length > 0 ? null : failure,
            fieldErrors,
          }))
          return
        }
        setResult({ key: requestKey, scope, page: null, failure, notice: null, fieldErrors: [] })
        if (failure.kind === 'forbidden') {
          followUp()
        }
      },
    )
  })

  useEffect(() => {
    if (!ready) {
      return undefined
    }
    query(key)
    return () => {
      // 画面を離れた後・鍵が変わった後の答えを捨てる。
      generation.current += 1
    }
  }, [ready, key])

  const setListState = useCallback(
    (next: ListState) => {
      if (schemaName === undefined || tableName === undefined) {
        return
      }
      saveListState({ schemaName, tableName }, roleId, next)
      setVersion((value) => value + 1)
    },
    [schemaName, tableName, roleId],
  )

  const retry = useCallback(() => setAttempt((value) => value + 1), [])

  const base = { listState, setListState, retry }
  if (!ready) {
    return {
      ...base,
      status: 'idle',
      page: null,
      failure: null,
      notice: null,
      fieldErrors: [],
      busy: false,
    }
  }
  const current = result !== null && result.key === key ? result : null
  if (current !== null) {
    if (current.failure !== null) {
      return {
        ...base,
        status: 'problem',
        page: null,
        failure: current.failure,
        notice: null,
        fieldErrors: [],
        busy: false,
      }
    }
    return {
      ...base,
      status: current.page === null ? 'loading' : 'ready',
      page: current.page,
      failure: null,
      notice: current.notice,
      fieldErrors: current.fieldErrors,
      busy: current.page === null,
    }
  }
  const page = keptPage(result)
  return {
    ...base,
    status: page === null ? 'loading' : 'ready',
    page,
    failure: null,
    notice: null,
    fieldErrors: [],
    busy: true,
  }
}
