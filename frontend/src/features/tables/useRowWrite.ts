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
// 行の作成・更新・削除の送信（Intent 261016-business-data-views の U3 の L7、D23・D24・F4・F5、frontend-components.md の 4節）。
// - 結果は writeOutcome の3つ（保存した・保存されていない・分からない）で返す。送信の最中の2回目は送らずに undefined を返す
//   （useSlowNotice の run。保存・削除のボタンも押せなくする）。
// - 5 秒を超えたら slow を真にする（「時間がかかっています」。D23）。自動の再送はしない（U3-NFR1.9）。
// - 値・主キーを console・ブラウザの保存に出さない。
import { useCallback } from 'react'
import type { TableRef } from '../../app/navigation/tablePlaceholderPath'
import { useSlowNotice } from '../../shared/slow-notice/useSlowNotice'
import { createRow, deleteRow, updateRow } from './businessDataApi'
import type { DefinitionId, RowKey, RowValues } from './businessDataTypes'
import type { WriteOutcome } from './writeOutcome'

/** 書き込みの API の関数（テストで差し替える） */
export interface RowWriteApi {
  createRow: (ref: TableRef, definitionId: DefinitionId, values: RowValues) => Promise<WriteOutcome>
  updateRow: (
    ref: TableRef,
    definitionId: DefinitionId,
    key: RowKey,
    values: RowValues,
  ) => Promise<WriteOutcome>
  deleteRow: (ref: TableRef, definitionId: DefinitionId, key: RowKey) => Promise<WriteOutcome>
}

const DEFAULT_API: RowWriteApi = { createRow, updateRow, deleteRow }

/** useRowWrite が返す値 */
export interface RowWriteState {
  /** 作る（送信の最中なら undefined） */
  create: (definitionId: DefinitionId, values: RowValues) => Promise<WriteOutcome> | undefined
  /** 更新する（送信の最中なら undefined） */
  update: (
    definitionId: DefinitionId,
    key: RowKey,
    values: RowValues,
  ) => Promise<WriteOutcome> | undefined
  /** 削除する（送信の最中なら undefined） */
  remove: (definitionId: DefinitionId, key: RowKey) => Promise<WriteOutcome> | undefined
  /** 送信の最中か */
  saving: boolean
  /** 5 秒を超えても答えが無いか */
  slow: boolean
}

/**
 * 行の書き込みの送信を返す。
 *
 * @param ref スキーマ名とテーブル名
 * @param api 書き込みの API の関数（テストで差し替える）
 */
export function useRowWrite(ref: TableRef, api: RowWriteApi = DEFAULT_API): RowWriteState {
  const { submitting, slow, run } = useSlowNotice()
  const create = useCallback(
    (definitionId: DefinitionId, values: RowValues) =>
      run(() => api.createRow(ref, definitionId, values)),
    [run, api, ref],
  )
  const update = useCallback(
    (definitionId: DefinitionId, key: RowKey, values: RowValues) =>
      run(() => api.updateRow(ref, definitionId, key, values)),
    [run, api, ref],
  )
  const remove = useCallback(
    (definitionId: DefinitionId, key: RowKey) => run(() => api.deleteRow(ref, definitionId, key)),
    [run, api, ref],
  )
  return { create, update, remove, saving: submitting, slow }
}
