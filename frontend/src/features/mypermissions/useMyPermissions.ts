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
// S8 自分の権限の状態（Intent 261004-role-menu の U7 の L10、functional-spec.md の D24・D25・W8.1〜W8.3・4.4、
// frontend-components.md の 7節、NFR2.9、計画 12節 D-5）。
// - 最上位（スキーマの段）を読み、作業ロール（current の roleId・切り替えの回数）か表示の言語が変わったら、選びと開閉を
//   捨てて最上位から読み直す（表示名をサーバーが要求の言語で選ぶため、D-5）。
// - 節の id は名前を JSON の配列にした文字列（`["SALES"]`・`["SALES","ORDER_LINE"]`）。テーブルの段は共有の木が開いたとき
//   に読む（loadChildren）。テーブルを選んだらカラムの段を読む。古い答えは世代で捨てる。
// - 効果の中で状態を同期で変えない（読み直しの鍵と答えの鍵を比べて、読み込み中・選び・開閉を導く）。
import { useCallback, useEffect, useEffectEvent, useMemo, useRef, useState } from 'react'
import { useDisplayLanguage } from '../../app/i18n/I18nProvider'
import { useWorkRole } from '../../app/work-role/WorkRoleProvider'
import type { SharedTreeNode } from '../../shared/tree/types'
import {
  listMyColumns,
  listMySchemas,
  listMyTables,
  type MyPermissionNode,
  type MyPermissionNodes,
} from './myPermissionsApi'

/** 画面の状態（4.4） */
export type MyPermissionsView = 'loading' | 'ready' | 'no-role' | 'empty' | 'load-error'

/** 自分の権限の API（テストで差し替える） */
export interface MyPermissionsApi {
  listMySchemas: () => Promise<MyPermissionNodes>
  listMyTables: (schema: string) => Promise<MyPermissionNodes>
  listMyColumns: (schema: string, table: string) => Promise<MyPermissionNodes>
}

const DEFAULT_API: MyPermissionsApi = { listMySchemas, listMyTables, listMyColumns }

/** カラムの段の読み込みの状態 */
export type ColumnsState =
  | { status: 'none' }
  | { status: 'loading' }
  | { status: 'loaded'; items: readonly MyPermissionNode[] }
  | { status: 'failed' }

interface TopState {
  key: string | null
  view: MyPermissionsView
  data: MyPermissionNodes | null
}

interface Keyed<T> {
  key: string
  value: T
}

/** 節の id（名前の JSON の配列） */
export function nodeIdOf(node: MyPermissionNode): string {
  return JSON.stringify(
    node.tableName === null ? [node.schemaName] : [node.schemaName, node.tableName],
  )
}

/** 節の表示名（空ならテーブル名・スキーマ名） */
export function labelOf(node: MyPermissionNode): string {
  if (node.displayName.trim() !== '') {
    return node.displayName
  }
  return node.columnName ?? node.tableName ?? node.schemaName
}

function toTreeNode(node: MyPermissionNode): SharedTreeNode {
  return { id: nodeIdOf(node), label: labelOf(node), hasChildren: node.hasChildren }
}

/**
 * S8 の状態と操作を返す。
 *
 * @param api API の関数（既定は myPermissionsApi）
 */
export function useMyPermissions(api: MyPermissionsApi = DEFAULT_API) {
  const workRole = useWorkRole()
  const language = useDisplayLanguage()
  const [attempt, setAttempt] = useState(0)
  const [top, setTop] = useState<TopState>({ key: null, view: 'loading', data: null })
  const [selection, setSelection] = useState<
    Keyed<{ id: string; node: MyPermissionNode | null } | null>
  >({ key: '', value: null })
  const [expansion, setExpansion] = useState<Keyed<ReadonlySet<string>>>({
    key: '',
    value: new Set(),
  })
  const [columns, setColumns] = useState<Keyed<ColumnsState>>({
    key: '',
    value: { status: 'none' },
  })
  const generation = useRef(0)
  const columnsGeneration = useRef(0)
  const tables = useRef(new Map<string, MyPermissionNode>())

  const roleSettled = workRole.status !== 'loading'
  const key = JSON.stringify([
    roleSettled,
    workRole.current?.roleId,
    workRole.revision,
    language,
    attempt,
  ])

  const readTop = useEffectEvent((requestKey: string): void => {
    generation.current += 1
    const mine = generation.current
    tables.current = new Map()
    api.listMySchemas().then(
      (data) => {
        if (generation.current !== mine) {
          return
        }
        let view: MyPermissionsView = 'ready'
        if (data.workRole === null) {
          view = 'no-role'
        } else if (data.items.length === 0) {
          view = 'empty'
        }
        setTop({ key: requestKey, view, data })
      },
      () => {
        if (generation.current === mine) {
          setTop({ key: requestKey, view: 'load-error', data: null })
        }
      },
    )
  })

  useEffect(() => {
    if (roleSettled) {
      readTop(key)
    }
  }, [roleSettled, key])

  const nodes = useMemo(() => (top.data?.items ?? []).map(toTreeNode), [top.data])

  const loadChildren = useCallback(
    async (id: string): Promise<readonly SharedTreeNode[]> => {
      const [schema] = JSON.parse(id) as [string]
      const mine = generation.current
      const result = await api.listMyTables(schema)
      if (generation.current === mine) {
        for (const item of result.items) {
          tables.current.set(nodeIdOf(item), item)
        }
      }
      return result.items.map(toTreeNode)
    },
    [api],
  )

  const pending = top.key !== key
  const current = selection.key === key ? selection.value : null
  const selectedId = current?.id ?? null
  const selectedNode = current?.node ?? null
  const columnsState: ColumnsState =
    columns.key === `${key}|${selectedId}` ? columns.value : { status: 'none' }
  const expandedIds = expansion.key === key ? expansion.value : new Set<string>()

  const select = useCallback(
    (id: string): void => {
      const node =
        top.data?.items.find((item) => nodeIdOf(item) === id) ?? tables.current.get(id) ?? null
      setSelection({ key, value: { id, node } })
      const names = JSON.parse(id) as string[]
      if (names.length !== 2) {
        return
      }
      const [schema, table] = names
      columnsGeneration.current += 1
      const mine = columnsGeneration.current
      const columnsKey = `${key}|${id}`
      setColumns({ key: columnsKey, value: { status: 'loading' } })
      api.listMyColumns(schema, table).then(
        (result) => {
          if (columnsGeneration.current === mine) {
            setColumns({ key: columnsKey, value: { status: 'loaded', items: result.items } })
          }
        },
        () => {
          if (columnsGeneration.current === mine) {
            setColumns({ key: columnsKey, value: { status: 'failed' } })
          }
        },
      )
    },
    [api, key, top.data],
  )

  const toggle = useCallback(
    (id: string, expanded: boolean): void => {
      setExpansion((prev) => {
        const next = new Set(prev.key === key ? prev.value : [])
        if (expanded) {
          next.add(id)
        } else {
          next.delete(id)
        }
        return { key, value: next }
      })
    },
    [key],
  )

  const retry = useCallback(() => setAttempt((value) => value + 1), [])

  return {
    view: pending ? ('loading' as const) : top.view,
    workRole: top.data?.workRole ?? null,
    nodes,
    loadChildren,
    selectedId,
    selectedNode,
    select,
    expandedIds,
    toggle,
    columns: columnsState,
    retry,
  }
}
