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
// 行の詳細の状態（Intent 261016-business-data-views の U3 の L7、D2・D18・F3・F8、frontend-components.md の 4節、
// U3-NFR1.9・4.22・8.7）。
// - 主キーは履歴の状態の印（rowToken）で記憶から引く。印が記憶に無ければ（再読み込みの後・作業ロールの変化の後など）
//   要求を送らずに missing を返し、画面が一覧へ置き換えて移る（D2・D2 の決定 A）。
// - 詳細の API を1回読む。応答の definitionChanged は onDefinitionStale を呼ぶ（F6。定義が変われば読み直す）。
// - 403 の後は業務のメニューと作業ロールを読み直し、業務データの API は送り直さない（F8）。
// - 世代で古い答えと画面を離れた後の答えを捨てる。決まった間隔の読み直し・自動の再送はしない。
import { useCallback, useEffect, useEffectEvent, useRef, useState } from 'react'
import type { TableRef } from '../../app/navigation/tablePlaceholderPath'
import { getRow } from './businessDataApi'
import { peekRowKey } from './businessDataStore'
import type { DefinitionId, RowDetail, RowKey, TableDefinition } from './businessDataTypes'
import {
  toReadFailure,
  useForbiddenFollowUp,
  useWorkRoleKey,
  type ReadFailure,
} from './tableHookSupport'

/** 詳細を読む関数（テストで差し替える） */
export type GetRowFn = (
  ref: TableRef,
  definitionId: DefinitionId,
  key: RowKey,
) => Promise<RowDetail>

/** 詳細の状態 */
export type RowDetailStatus = 'idle' | 'missing' | 'loading' | 'ready' | 'problem'

/** useRowDetail が返す値 */
export interface RowDetailState {
  status: RowDetailStatus
  row: RowDetail | null
  failure: ReadFailure | null
  /** 失敗の後にもう一度読む */
  retry: () => void
}

interface Result {
  key: string
  row: RowDetail | null
  failure: ReadFailure | null
}

/**
 * 行の詳細の状態を返す。
 *
 * @param ref スキーマ名とテーブル名（無ければ null）
 * @param token 履歴の状態から読んだ印（無ければ undefined）
 * @param definition テーブルの定義（読み終えるまで null。null の間は要求を送らない）
 * @param onDefinitionStale 定義が変わったと分かったとき（F6）
 * @param load 詳細を読む関数（テストで差し替える）
 */
export function useRowDetail(
  ref: TableRef | null,
  token: unknown,
  definition: TableDefinition | null,
  onDefinitionStale: () => void,
  load: GetRowFn = getRow,
): RowDetailState {
  const { settled, roleId } = useWorkRoleKey()
  const followUp = useForbiddenFollowUp()
  const [result, setResult] = useState<Result | null>(null)
  const [attempt, setAttempt] = useState(0)
  const generation = useRef(0)

  const rowKey = settled ? peekRowKey(token, roleId) : undefined
  const requestKey = JSON.stringify([
    ref?.schemaName,
    ref?.tableName,
    roleId,
    typeof token === 'string' ? token : null,
    definition?.definitionId ?? null,
    attempt,
  ])
  const ready = settled && ref !== null && definition !== null && rowKey !== undefined

  const query = useEffectEvent((key: string): void => {
    if (ref === null || definition === null || rowKey === undefined) {
      return
    }
    generation.current += 1
    const mine = generation.current
    load(ref, definition.definitionId, rowKey).then(
      (row) => {
        if (generation.current !== mine) {
          return
        }
        setResult({ key, row, failure: null })
        if (row.definitionChanged) {
          onDefinitionStale()
        }
      },
      (error: unknown) => {
        if (generation.current !== mine) {
          return
        }
        const failure = toReadFailure(error)
        setResult({ key, row: null, failure })
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
    query(requestKey)
    return () => {
      generation.current += 1
    }
  }, [ready, requestKey])

  const retry = useCallback(() => setAttempt((value) => value + 1), [])

  if (!settled || ref === null) {
    return { status: 'idle', row: null, failure: null, retry }
  }
  if (rowKey === undefined) {
    return { status: 'missing', row: null, failure: null, retry }
  }
  if (definition === null) {
    return { status: 'idle', row: null, failure: null, retry }
  }
  if (result !== null && result.key === requestKey) {
    return result.failure !== null
      ? { status: 'problem', row: null, failure: result.failure, retry }
      : { status: 'ready', row: result.row, failure: null, retry }
  }
  return { status: 'loading', row: null, failure: null, retry }
}
