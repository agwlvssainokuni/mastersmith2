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
// S4 権限の設定の状態と操作（Intent 261004-role-menu の U6、functional-spec.md の 3.2 W4.1〜W4.7・4.3・5節、
// D10〜D15、frontend-components.md の 2.2、L4・L5・L7、NFR1.4・NFR2.10・NFR2.11）。
// - 木の節の id は対象の名前の JSON の配列（permissionDraft の targetKey と同じ）。スキーマの子（テーブル）は共有の木の
//   loadChildren で読み、表の値にも使う。カラムは選んだテーブルについて読む。
// - 写しは permissionDraft で持つ。保存の entries は写しから作り、範囲（選んでいる節）の外を含めない。
// - 保存の後は、スキーマの一覧を新しい配列にして共有の木に開いている節を読み直させる（共有の木の決まり、計画 D-9）。
//   読み直しで先に読んだテーブルの一覧は prefetch に置き、木の loadChildren はそれを使って二重に読まない。
// - 拒否は D14: BUSY は写しを残す、NO_CHANGE は写しを捨てて読み直す、TARGET_NOT_IN_DSL は読み直して今の DSL に残る対象の
//   写しだけを残す、DSL_NOT_APPLIED は noDsl、ROLE_NOT_FOUND は onMissing。403 は骨組みに渡す（D2）。
// - 応答・要求の値をコンソールとブラウザの保存に出さない（D3）。
import { useCallback, useEffect, useRef, useState } from 'react'
import type { SharedTreeNode } from '../../shared/tree/types'
import { permissionTreePath, rolePath, type RoleAdminApi } from './api/roleAdminApi'
import type { Explicit, PermissionNode, PermissionScope, PermissionTarget } from './api/types'
import { failureMessageKey, knownCode } from './failureMessage'
import { createLatestLoad } from './latestLoad'
import {
  applyChange,
  draftExplicit,
  EMPTY_DRAFT,
  retainDraft,
  targetKey,
  toEntries,
  type PermissionDraft,
} from './permissionDraft'
import type { RoleAdminText } from './useRoleAdminText'
import { useSlowNotice } from './useSlowNotice'

/** S4 の外側の状態（4.3） */
export type PermissionPhase = 'loading' | 'noDsl' | 'ready' | 'load-error'

/** カラムの読み込み */
export interface ColumnsState {
  phase: 'idle' | 'loading' | 'loaded' | 'failed'
  items: PermissionNode[]
}

/** 画面の知らせ */
export interface PermissionNotice {
  variant: 'success' | 'warning'
  key: string
}

/** 明示の値の項目 */
export type ExplicitField = keyof Explicit

/** フックが受ける口 */
export interface UseRolePermissionsOptions {
  api: RoleAdminApi
  roleId: number
  t: RoleAdminText
  reportForbidden: (error: unknown, apiPath: string) => boolean
  /** ROLE_NOT_FOUND を受けた（D32 へ） */
  onMissing: () => void
}

const NO_COLUMNS: ColumnsState = { phase: 'idle', items: [] }

/** 節の id から対象（スキーマ名・テーブル名）を読む。 */
export function parseNodeId(id: string): PermissionScope | undefined {
  try {
    const names: unknown = JSON.parse(id)
    if (
      Array.isArray(names) &&
      (names.length === 1 || names.length === 2) &&
      names.every((name) => typeof name === 'string')
    ) {
      const [schemaName, tableName] = names as string[]
      return { schemaName: schemaName ?? '', tableName: tableName ?? null }
    }
  } catch {
    return undefined
  }
  return undefined
}

function scopeTarget(scope: PermissionScope): PermissionTarget {
  return { schemaName: scope.schemaName, tableName: scope.tableName, columnName: null }
}

function statusOf(error: unknown): number | undefined {
  const value = error as { kind?: unknown; status?: unknown } | null | undefined
  return typeof value === 'object' && value !== null && value.kind === 'response'
    ? (value.status as number | undefined)
    : undefined
}

/** 幅が狭い（768px 未満、木と表を縦に積む）か */
function narrowScreen(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia('(max-width: 767px)').matches
}

/** S4 の状態と操作 */
export function useRolePermissions({
  api,
  roleId,
  t,
  reportForbidden,
  onMissing,
}: UseRolePermissionsOptions) {
  const slowNotice = useSlowNotice()
  const [phase, setPhase] = useState<PermissionPhase>('loading')
  const [schemas, setSchemas] = useState<PermissionNode[]>([])
  const [tables, setTables] = useState<ReadonlyMap<string, PermissionNode[]>>(new Map())
  const [columns, setColumns] = useState<ColumnsState>(NO_COLUMNS)
  const [selected, setSelected] = useState<PermissionScope | null>(null)
  const [expandedIds, setExpandedIds] = useState<ReadonlySet<string>>(new Set())
  const [draft, setDraft] = useState<PermissionDraft>(EMPTY_DRAFT)
  const [notice, setNotice] = useState<PermissionNotice | null>(null)
  const [pendingSelect, setPendingSelect] = useState<string | null>(null)
  const [focusTableSeq, setFocusTableSeq] = useState(0)

  const mounted = useRef(true)
  const treeLoad = useRef(createLatestLoad())
  const columnsLoad = useRef(createLatestLoad())
  const prefetch = useRef(new Map<string, Promise<PermissionNode[]>>())
  const selectedRef = useRef<PermissionScope | null>(null)
  const forbidden = useRef(reportForbidden)
  const missing = useRef(onMissing)

  useEffect(() => {
    forbidden.current = reportForbidden
    missing.current = onMissing
  }, [reportForbidden, onMissing])

  useEffect(() => {
    const tree = treeLoad.current
    const cols = columnsLoad.current
    mounted.current = true
    return () => {
      mounted.current = false
      tree.invalidate()
      cols.invalidate()
    }
  }, [])

  /**
   * 読み込みの失敗の共通の扱い。401・403・ROLE_NOT_FOUND・DSL_NOT_APPLIED を扱ったら true。
   */
  const handledCommonly = useCallback((error: unknown, apiPath: string): boolean => {
    if (statusOf(error) === 401) {
      return true
    }
    if (forbidden.current(error, apiPath)) {
      return true
    }
    const code = knownCode(error)
    if (code === 'ROLE_NOT_FOUND') {
      missing.current()
      return true
    }
    if (code === 'DSL_NOT_APPLIED') {
      setPhase('noDsl')
      setDraft(EMPTY_DRAFT)
      return true
    }
    return false
  }, [])

  /** カラムを読む（選んだテーブル）。 */
  const loadColumns = useCallback(
    async (scope: PermissionScope): Promise<PermissionNode[] | undefined> => {
      if (scope.tableName === null) {
        setColumns(NO_COLUMNS)
        return undefined
      }
      const generation = columnsLoad.current.begin()
      setColumns((current) => ({ phase: 'loading', items: current.items }))
      const path = permissionTreePath(roleId, 'columns', {
        schema: scope.schemaName,
        table: scope.tableName,
      })
      try {
        const items = await api.listColumns(roleId, scope.schemaName, scope.tableName)
        if (mounted.current && columnsLoad.current.isLatest(generation)) {
          setColumns({ phase: 'loaded', items })
          return items
        }
      } catch (error: unknown) {
        if (mounted.current && columnsLoad.current.isLatest(generation)) {
          if (!handledCommonly(error, path)) {
            setColumns({ phase: 'failed', items: [] })
          }
        }
      }
      return undefined
    },
    [api, roleId, handledCommonly],
  )

  /** スキーマの一覧を読む（開いたとき・保存の後）。読めたスキーマの一覧を返す。 */
  const loadSchemas = useCallback(
    async (first: boolean): Promise<PermissionNode[] | undefined> => {
      const generation = treeLoad.current.begin()
      try {
        const result = await api.listSchemas(roleId)
        if (!mounted.current || !treeLoad.current.isLatest(generation)) {
          return undefined
        }
        setSchemas(result)
        setPhase('ready')
        if (first && result.length === 1) {
          // スキーマが1つなら、そのスキーマを開いて選んだ状態で始める（W4.1 の 3）。
          const only = result[0]
          if (only !== undefined) {
            const id = targetKey(only)
            const scope = { schemaName: only.schemaName, tableName: null }
            setExpandedIds(new Set([id]))
            setSelected(scope)
            selectedRef.current = scope
          }
        }
        return result
      } catch (error: unknown) {
        if (!mounted.current || !treeLoad.current.isLatest(generation)) {
          return undefined
        }
        if (!handledCommonly(error, permissionTreePath(roleId, 'schemas'))) {
          setPhase(first ? 'load-error' : 'ready')
        }
        return undefined
      }
    },
    [api, roleId, handledCommonly],
  )

  // 開いたらスキーマの一覧を読む。読み込みの状態の更新を効果の中で同期に呼ばないよう、マイクロタスクで始める
  // （効果の後始末で取りやめる）。
  useEffect(() => {
    let active = true
    queueMicrotask(() => {
      if (active) {
        void loadSchemas(true)
      }
    })
    return () => {
      active = false
    }
  }, [loadSchemas])

  /** 共有の木の子の読み込み（スキーマの子のテーブル）。 */
  const loadChildren = useCallback(
    async (id: string): Promise<readonly SharedTreeNode[]> => {
      const scope = parseNodeId(id)
      if (scope === undefined || scope.tableName !== null) {
        return []
      }
      const prefetched = prefetch.current.get(scope.schemaName)
      prefetch.current.delete(scope.schemaName)
      let items: PermissionNode[]
      try {
        items = await (prefetched ?? api.listTables(roleId, scope.schemaName))
      } catch (error: unknown) {
        if (mounted.current) {
          handledCommonly(error, permissionTreePath(roleId, 'tables', { schema: scope.schemaName }))
        }
        throw error
      }
      if (mounted.current) {
        setTables((current) => new Map(current).set(scope.schemaName, items))
      }
      return items.map((node) => treeNodeOf(node, t))
    },
    [api, roleId, handledCommonly, t],
  )

  /** 選んでいる節の設定の節 */
  const selectedNode: PermissionNode | undefined =
    selected === null
      ? undefined
      : selected.tableName === null
        ? schemas.find((node) => node.schemaName === selected.schemaName)
        : tables.get(selected.schemaName)?.find((node) => node.tableName === selected.tableName)

  // テーブルを選んだらカラムを読む。
  const selectedKey = selected === null ? null : targetKey(scopeTarget(selected))
  useEffect(() => {
    const scope = selectedRef.current
    if (scope === null || scope.tableName === null) {
      columnsLoad.current.invalidate()
      return
    }
    void loadColumns(scope)
  }, [selectedKey, loadColumns])

  const dirty = draft.size > 0

  function doSelect(id: string): void {
    const scope = parseNodeId(id)
    if (scope === undefined) {
      return
    }
    setNotice(null)
    selectedRef.current = scope
    if (scope.tableName === null) {
      setColumns(NO_COLUMNS)
    }
    setSelected(scope)
    if (narrowScreen()) {
      setFocusTableSeq((value) => value + 1)
    }
  }

  /** 木の節を選ぶ（写しがあれば確かめる、W4.2 の 2）。 */
  function select(id: string): void {
    if (slowNotice.submitting) {
      return
    }
    if (selectedKey === id) {
      return
    }
    if (dirty) {
      setPendingSelect(id)
      return
    }
    doSelect(id)
  }

  /** 木の節の確かめで［留まる］ */
  function staySelect(): void {
    setPendingSelect(null)
  }

  /** 木の節の確かめで［移る］（写しを捨てて選ぶ） */
  function leaveSelect(): void {
    const id = pendingSelect
    setPendingSelect(null)
    setDraft(EMPTY_DRAFT)
    if (id !== null) {
      doSelect(id)
    }
  }

  function toggle(id: string, expanded: boolean): void {
    setExpandedIds((current) => {
      const next = new Set(current)
      if (expanded) {
        next.add(id)
      } else {
        next.delete(id)
      }
      return next
    })
  }

  /** 値を変える（W4.3）。 */
  function change(
    node: PermissionNode,
    field: ExplicitField,
    value: Explicit[ExplicitField],
  ): void {
    if (slowNotice.submitting) {
      return
    }
    setNotice(null)
    const current = draftExplicit(draft, node, node.explicit)
    setDraft(applyChange(draft, node.explicit, node, { ...current, [field]: value }))
  }

  /** 変更を取り消す（W4.5） */
  const discard = useCallback((): void => {
    setDraft(EMPTY_DRAFT)
  }, [])

  /**
   * 選んでいる段と親の段を読み直す（保存の後）。スキーマの一覧を新しい配列にし、選んでいるスキーマのテーブルは
   * 先に読んで prefetch に置く（共有の木が開いている節を読み直すときに使う）。読み直した節の一覧を返す。
   */
  async function refresh(): Promise<PermissionNode[]> {
    const scope = selectedRef.current
    const tablesPromise = scope !== null ? api.listTables(roleId, scope.schemaName) : undefined
    if (scope !== null && tablesPromise !== undefined) {
      // 読み直しが失敗しても未処理の約束にしない（木の読み込みの失敗として扱われる）。
      tablesPromise.catch(() => undefined)
      prefetch.current.set(scope.schemaName, tablesPromise)
    }
    const [schemaNodes, tableNodes, columnNodes] = await Promise.all([
      loadSchemas(false),
      tablesPromise?.catch(() => undefined),
      scope !== null && scope.tableName !== null ? loadColumns(scope) : Promise.resolve(undefined),
    ])
    if (scope !== null && tableNodes !== undefined && mounted.current) {
      setTables((current) => new Map(current).set(scope.schemaName, tableNodes))
    }
    return [...(schemaNodes ?? []), ...(tableNodes ?? []), ...(columnNodes ?? [])]
  }

  /** 保存（W4.4） */
  function save(): void {
    const scope = selectedRef.current
    if (scope === null || !dirty) {
      return
    }
    const entries = toEntries(draft, scope)
    if (entries.length === 0) {
      return
    }
    const sent = slowNotice.run(() => api.savePermissions(roleId, scope, entries))
    if (sent === undefined) {
      return
    }
    setNotice(null)
    const apiPath = rolePath(roleId, '/permissions')
    sent.then(
      async () => {
        if (!mounted.current) {
          return
        }
        setDraft(EMPTY_DRAFT)
        setNotice({ variant: 'success', key: 'roleadmin.save.done' })
        await refresh()
      },
      async (error: unknown) => {
        if (!mounted.current || handledCommonly(error, apiPath)) {
          return
        }
        const code = knownCode(error)
        setNotice({ variant: 'warning', key: failureMessageKey(error, 'save') })
        if (code === 'ROLE_NO_CHANGE') {
          setDraft(EMPTY_DRAFT)
          await refresh()
        } else if (code === 'PERMISSION_TARGET_NOT_IN_DSL') {
          const fresh = await refresh()
          const inDsl = new Set(
            fresh.filter((node) => node.inCurrentDsl).map((node) => targetKey(node)),
          )
          if (mounted.current) {
            setDraft((current) => retainDraft(current, (target) => inDsl.has(targetKey(target))))
          }
        }
      },
    )
  }

  /** 今の DSL に無い設定を消す（W4.6、Should）。テーブルを消すときは設定のあるカラムも含める。 */
  function clearOrphan(node: PermissionNode): void {
    const targets: PermissionTarget[] = [
      { schemaName: node.schemaName, tableName: node.tableName, columnName: node.columnName },
    ]
    if (node.columnName === null && node.tableName !== null) {
      for (const column of columns.items) {
        const e = column.explicit
        if (e.main !== null || e.create !== null || e.delete !== null) {
          targets.push({
            schemaName: column.schemaName,
            tableName: column.tableName,
            columnName: column.columnName,
          })
        }
      }
    }
    const sent = slowNotice.run(() => api.clearPermissions(roleId, targets))
    if (sent === undefined) {
      return
    }
    setNotice(null)
    const apiPath = rolePath(roleId, '/permissions/clear')
    sent.then(
      async () => {
        if (mounted.current) {
          setNotice({ variant: 'success', key: 'roleadmin.clear.done' })
          await refresh()
        }
      },
      async (error: unknown) => {
        if (!mounted.current || handledCommonly(error, apiPath)) {
          return
        }
        if (knownCode(error) === 'ROLE_NO_CHANGE') {
          await refresh()
          return
        }
        setNotice({ variant: 'warning', key: failureMessageKey(error, 'clear') })
      },
    )
  }

  function retryColumns(): void {
    const scope = selectedRef.current
    if (scope !== null) {
      void loadColumns(scope)
    }
  }

  function retry(): void {
    setPhase('loading')
    void loadSchemas(true)
  }

  function dismissNotice(): void {
    setNotice(null)
  }

  return {
    phase,
    schemas,
    tables,
    columns,
    selected,
    selectedId: selectedKey,
    selectedNode,
    expandedIds,
    draft,
    dirty,
    notice,
    pendingSelect: pendingSelect !== null,
    focusTableSeq,
    saving: slowNotice.submitting,
    slow: slowNotice.slow,
    loadChildren,
    select,
    staySelect,
    leaveSelect,
    toggle,
    change,
    discard,
    save,
    clearOrphan,
    retryColumns,
    retry,
    dismissNotice,
  }
}

/** 権限の節を共有の木の節にする（今の DSL に無い節は印を付ける、D11）。 */
export function treeNodeOf(node: PermissionNode, t: RoleAdminText): SharedTreeNode {
  return {
    id: targetKey(node),
    label: node.displayName,
    hasChildren: node.hasChildren && node.tableName === null,
    badges: node.inCurrentDsl
      ? []
      : [{ id: 'orphan', text: t('roleadmin.orphan'), tone: 'warning' }],
  }
}

/** S4 の状態と操作の型 */
export type RolePermissionsState = ReturnType<typeof useRolePermissions>
