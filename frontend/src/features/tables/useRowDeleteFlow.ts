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
// 行の削除の流れ（Intent 261016-business-data-views の U3 の L7、F5・D26・D3・D4）。詳細の画面と一覧の行の操作が使う。
// - open で確かめを開き、confirm で削除の API を1回送る（送信の最中の2回目は送らない）。
// - 保存した: 閉じて onDeleted を呼ぶ（呼ぶ側が一覧へ戻る・読み直す、Toast を出す）。
// - 保存されていない: 403 は閉じて onForbidden（D3。S6 の 403 の表示）、定義が変わった（409 BUSINESS_DEFINITION_CHANGED）は
//   onDefinitionStale（F6）と文、ほかは Modal の中に code ごとの文（外部キー・行が無い・つながらない など）。
// - 分からない（D4: A）: Modal の中に文と確かめる入口を出し、もう一度押すと「削除済みかもしれません」の確かめを経て送る。
// - 送った後に画面を離れていたら、届いた結果では何もしない（useScreenPresence。コードのレビューの R-01）。
import { useCallback, useState } from 'react'
import { useTranslation } from 'react-i18next'
import type { TableRef } from '../../app/navigation/tablePlaceholderPath'
import type { RowKey, TableDefinition } from './businessDataTypes'
import type { DeletePhase } from './DeleteConfirmModal'
import { writeProblemKind, writeProblemMessageKey } from './problemMessage'
import { useRowWrite, type RowWriteApi } from './useRowWrite'
import { useScreenPresence } from './useScreenPresence'

/** 削除する行 */
export interface DeleteTarget {
  key: RowKey
  /** 行のラベル（見出しに使う） */
  label: string
  /** 一覧のページの中の番号（一覧から開いたとき） */
  index?: number
}

/** 削除の流れの状態と口 */
export interface RowDeleteFlow {
  target: DeleteTarget | null
  phase: DeletePhase
  busy: boolean
  slow: boolean
  message: string | undefined
  unknown: boolean
  open: (target: DeleteTarget) => void
  cancel: () => void
  confirm: () => void
}

export interface RowDeleteFlowOptions {
  ref: TableRef
  definition: TableDefinition | null
  onDeleted: (target: DeleteTarget) => void
  onForbidden: () => void
  onDefinitionStale: () => void
  api?: RowWriteApi
}

/** 行の削除の流れを返す */
export function useRowDeleteFlow({
  ref,
  definition,
  onDeleted,
  onForbidden,
  onDefinitionStale,
  api,
}: RowDeleteFlowOptions): RowDeleteFlow {
  const { t } = useTranslation()
  const write = useRowWrite(ref, api)
  const beginPresence = useScreenPresence()
  const [target, setTarget] = useState<DeleteTarget | null>(null)
  const [phase, setPhase] = useState<DeletePhase>('confirm')
  const [message, setMessage] = useState<string | undefined>(undefined)
  const [unknown, setUnknown] = useState(false)

  const open = useCallback((next: DeleteTarget) => {
    setTarget(next)
    setPhase('confirm')
    setMessage(undefined)
    setUnknown(false)
  }, [])

  const cancel = useCallback(() => {
    setTarget(null)
    setPhase('confirm')
    setMessage(undefined)
    setUnknown(false)
  }, [])

  const confirm = useCallback(() => {
    if (target === null || definition === null) {
      return
    }
    if (unknown && phase === 'confirm') {
      setPhase('maybe')
      return
    }
    const pending = write.remove(definition.definitionId, target.key)
    if (pending === undefined) {
      return
    }
    const stillHere = beginPresence()
    void pending.then((outcome) => {
      // 送った後に画面を離れていたら、onDeleted（一覧への移動・読み直し・Toast）などを呼ばない（コードのレビューの R-01）。
      if (!stillHere()) {
        return
      }
      if (outcome.kind === 'saved') {
        setTarget(null)
        setPhase('confirm')
        setMessage(undefined)
        setUnknown(false)
        onDeleted(target)
        return
      }
      if (outcome.kind === 'unknown') {
        setUnknown(true)
        setPhase('confirm')
        setMessage(undefined)
        return
      }
      const kind = writeProblemKind(outcome.problem)
      setUnknown(false)
      setPhase('confirm')
      if (kind === 'forbidden') {
        setTarget(null)
        onForbidden()
        return
      }
      if (kind === 'definition-changed') {
        onDefinitionStale()
      }
      setMessage(t(writeProblemMessageKey(kind, 'delete')))
    })
  }, [
    target,
    definition,
    unknown,
    phase,
    write,
    beginPresence,
    onDeleted,
    onForbidden,
    onDefinitionStale,
    t,
  ])

  return {
    target,
    phase,
    busy: write.saving,
    slow: write.slow,
    message,
    unknown,
    open,
    cancel,
    confirm,
  }
}
