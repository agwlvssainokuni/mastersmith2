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
// テーブルの定義の状態（Intent 261016-business-data-views の U3 の L7、D4・F1・F6・F8、frontend-components.md の 4節、
// U3-NFR1.9・8.7、計画 4.3 の R-04・4.8 の P-9・P-10）。
// - 作業ロールの読み込みが済んでから始める（P-9）。記憶に今の作業ロールの定義があれば読まない（D4）。
// - 無ければ定義の API を1回読み、記憶に書く（要求を送った時の世代と違えば書かない。R-04）。
// - reload（F6）: 記憶の定義を外し、今の定義の識別を添えて読み直す。読み直しの間は読み込み中にする。
// - 道の引数が無ければ要求を送らずに「テーブルが見つかりません」（F1 の 1）。
// - 403 の後は業務のメニューと作業ロールを読み直す（F8）。業務データの API は送り直さない（U3-NFR8.7）。
// - 決まった間隔の読み直し・自動の再送はしない。世代で古い答えと画面を離れた後の答えを捨てる。
import { useCallback, useEffect, useEffectEvent, useRef, useState } from 'react'
import type { TableRef } from '../../app/navigation/tablePlaceholderPath'
import { getDefinition } from './businessDataApi'
import {
  currentStoreGeneration,
  forgetDefinition,
  readTableEntry,
  saveDefinition,
} from './businessDataStore'
import type { DefinitionId, TableDefinition } from './businessDataTypes'
import {
  toReadFailure,
  useForbiddenFollowUp,
  useWorkRoleKey,
  type ReadFailure,
} from './tableHookSupport'

/** 定義を読む関数（テストで差し替える） */
export type LoadDefinition = (
  ref: TableRef,
  definitionId?: DefinitionId,
) => Promise<TableDefinition>

/** 定義の状態 */
export type DefinitionStatus = 'loading' | 'ready' | 'problem'

/** useTableDefinition が返す値 */
export interface TableDefinitionState {
  status: DefinitionStatus
  definition: TableDefinition | null
  /** 失敗（status が problem のときだけ） */
  failure: ReadFailure | null
  /** 定義が変わったので読み直す（F6） */
  reload: () => void
  /** 失敗の後にもう一度読む */
  retry: () => void
}

interface Result {
  key: string
  definition: TableDefinition | null
  failure: ReadFailure | null
}

/**
 * テーブルの定義の状態を返す。
 *
 * @param ref 道の引数のスキーマ名とテーブル名（無ければ null）
 * @param load 定義を読む関数（テストで差し替える）
 */
export function useTableDefinition(
  ref: TableRef | null,
  load: LoadDefinition = getDefinition,
): TableDefinitionState {
  const { settled, roleId } = useWorkRoleKey()
  const followUp = useForbiddenFollowUp()
  const [result, setResult] = useState<Result | null>(null)
  const [attempt, setAttempt] = useState(0)
  const generation = useRef(0)
  const staleId = useRef<DefinitionId | undefined>(undefined)

  const schemaName = ref?.schemaName
  const tableName = ref?.tableName
  const key = JSON.stringify([schemaName, tableName, settled, roleId, attempt])
  const cached =
    settled && schemaName !== undefined && tableName !== undefined
      ? readTableEntry({ schemaName, tableName }, roleId).definition
      : null

  const query = useEffectEvent((target: TableRef, requestKey: string): void => {
    generation.current += 1
    const mine = generation.current
    const storeGeneration = currentStoreGeneration()
    const previousId = staleId.current
    staleId.current = undefined
    load(target, previousId).then(
      (definition) => {
        if (generation.current !== mine) {
          return
        }
        saveDefinition(target, roleId, definition, storeGeneration)
        setResult({ key: requestKey, definition, failure: null })
      },
      (error: unknown) => {
        if (generation.current !== mine) {
          return
        }
        const failure = toReadFailure(error)
        setResult({ key: requestKey, definition: null, failure })
        if (failure.kind === 'forbidden') {
          followUp()
        }
      },
    )
  })

  const hasCache = cached !== null
  useEffect(() => {
    if (!settled || schemaName === undefined || tableName === undefined || hasCache) {
      return undefined
    }
    query({ schemaName, tableName }, key)
    return () => {
      // 画面を離れた後・鍵が変わった後の答えを捨てる。
      generation.current += 1
    }
  }, [settled, schemaName, tableName, hasCache, key])

  const reload = useCallback(() => {
    if (schemaName === undefined || tableName === undefined) {
      return
    }
    const current = readTableEntry({ schemaName, tableName }, roleId).definition
    staleId.current = current?.definitionId
    forgetDefinition({ schemaName, tableName })
    setAttempt((value) => value + 1)
  }, [schemaName, tableName, roleId])

  const retry = useCallback(() => setAttempt((value) => value + 1), [])

  if (ref === null) {
    return {
      status: 'problem',
      definition: null,
      failure: { kind: 'table-not-found', problem: undefined },
      reload,
      retry,
    }
  }
  if (cached !== null) {
    return { status: 'ready', definition: cached, failure: null, reload, retry }
  }
  if (result !== null && result.key === key) {
    if (result.failure !== null) {
      return { status: 'problem', definition: null, failure: result.failure, reload, retry }
    }
    // 記憶への書き戻しが世代の違いで捨てられたときも、この鍵の答えとして描く。
    return { status: 'ready', definition: result.definition, failure: null, reload, retry }
  }
  return { status: 'loading', definition: null, failure: null, reload, retry }
}
