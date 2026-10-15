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
// S3 ロールの一覧の状態と操作（Intent 261004-role-menu の U6、functional-spec.md の 3.1 W3.1〜W3.6・4.1・4.2・5節、
// D3〜D9・D29・D30、frontend-components.md の 2.2、L4・L5、NFR2.10・NFR2.11）。
// - 一覧の読み込みは latestLoad の世代で見分け、最後に始めた読み込みの答えだけを使う。画面を離れたら世代を進める。
// - 読み直しの間のページ送り・操作の押下は捨てる（D5）。空のページは correctedPage で最後のページを1回だけ読む。
// - 送信は useSlowNotice の run で1つずつ行い、送信の最中の2回目の押下を捨てる。5 秒で待ちの案内を出す。
// - 失敗は code と状態だけで文言を選ぶ（failureMessage.ts）。403 は useAdminForbidden に要求の道を添えて渡し、画面は
//   独自の文言を出さない（D2）。401 は ApiClient とログインの状態に任せ、開いている Modal を閉じる。
// - Modal を閉じた後の読み直しは、Modal が閉じ終わる（背景の inert が外れ、開いた所へフォーカスが戻る）のを待ってから
//   始める（shared/modal/afterModalClosed。読み直しの後に当てるフォーカスを Modal の戻しが上書きしないため）。
// - 応答・要求の値をコンソールとブラウザの保存に出さない（D3）。
import { useCallback, useEffect, useRef, useState } from 'react'
import { runAfterModalClosed } from '../../shared/modal/afterModalClosed'
import {
  correctedPage,
  pagerButtonDisabledAfter,
  pageRange,
  type PagerDirection,
} from '../../shared/paging/paging'
import { roleListPath, rolePath, ROLE_ADMIN_API_ROOT, type RoleAdminApi } from './api/roleAdminApi'
import type { RolePage, RoleRow } from './api/types'
import { failureMessageKey, inUseCounts, knownCode } from './failureMessage'
import { createLatestLoad } from './latestLoad'
import type { RoleAdminText } from './useRoleAdminText'
import { useSlowNotice } from '../../shared/slow-notice/useSlowNotice'

/** 一覧の画面の状態（4.1） */
export type RoleListView = 'loading' | 'populated' | 'empty' | 'reloading' | 'load-error'

/** 開いている Modal（4.1 の dialog-open） */
export type RoleListDialog =
  | { kind: 'none' }
  | { kind: 'create' }
  | { kind: 'rename'; row: RoleRow }
  | { kind: 'delete'; row: RoleRow }

/** 画面の上の知らせ（D30。1つだけ持ち、次の操作で置き換える） */
export interface RoleListNotice {
  variant: 'success' | 'warning'
  key: string
  values?: Readonly<Record<string, string | number>>
  /** 割り当てのタブへのリンク（ROLE_IN_USE のとき、D9） */
  assignmentsOf?: number
}

/** 読み込みの後のフォーカスの行き先 */
export type RoleListFocus =
  { kind: 'heading' } | { kind: 'retry' } | { kind: 'row'; roleId: number }

/** フックが受ける口 */
export interface UseRoleListOptions {
  /** API の関数の集まり（テストで差し替える） */
  api: RoleAdminApi
  /** 文言を引く関数 */
  t: RoleAdminText
  /** 管理の画面の 403 を骨組みに渡す口（useAdminForbidden） */
  reportForbidden: (error: unknown, apiPath: string) => boolean
}

interface LoadOptions {
  focus?: RoleListFocus
  pagerDirection?: PagerDirection
  retry?: boolean
}

/** 未認証の状態コード（ApiClient とログインの状態に任せる） */
const UNAUTHORIZED = 401

function statusOf(error: unknown): number | undefined {
  const value = error as { kind?: unknown; status?: unknown } | null | undefined
  return typeof value === 'object' &&
    value !== null &&
    value.kind === 'response' &&
    typeof value.status === 'number'
    ? value.status
    : undefined
}

/** 一覧の画面の状態を導く（4.1）。 */
export function roleListViewOf(
  loadState: 'loading' | 'reloading' | 'loaded' | 'failed',
  list: RolePage | null,
): RoleListView {
  if (loadState === 'failed') {
    return 'load-error'
  }
  if (list === null || loadState === 'loading') {
    return 'loading'
  }
  if (loadState === 'reloading') {
    return 'reloading'
  }
  return list.items.length > 0 ? 'populated' : 'empty'
}

/** S3 の状態と操作 */
export function useRoleList({ api, t, reportForbidden }: UseRoleListOptions) {
  const slowNotice = useSlowNotice()
  const [list, setList] = useState<RolePage | null>(null)
  const [page, setPage] = useState(1)
  const [loadState, setLoadState] = useState<'loading' | 'reloading' | 'loaded' | 'failed'>(
    'loading',
  )
  const [dialog, setDialog] = useState<RoleListDialog>({ kind: 'none' })
  const [nameProblem, setNameProblem] = useState<string | null>(null)
  const [dialogMessage, setDialogMessage] = useState<string | null>(null)
  const [notice, setNotice] = useState<RoleListNotice | null>(null)
  const [liveMessage, setLiveMessage] = useState('')
  const [focusTarget, setFocusTarget] = useState<RoleListFocus | null>(null)

  const latest = useRef(createLatestLoad())
  const mounted = useRef(true)
  const loadingRef = useRef(false)
  const listRef = useRef<RolePage | null>(null)
  const pageRef = useRef(1)
  const text = useRef(t)
  const forbidden = useRef(reportForbidden)
  const cancelAfterClose = useRef<(() => void) | null>(null)

  // 応答を受けた後（描画の外）で使う関数は、最新のものを参照に置く。
  useEffect(() => {
    text.current = t
    forbidden.current = reportForbidden
  }, [t, reportForbidden])

  useEffect(() => {
    const load = latest.current
    mounted.current = true
    return () => {
      // 画面を離れたら世代を進め、後から届いた答えをすべて捨てる（NFR2.11）。
      mounted.current = false
      load.invalidate()
      cancelAfterClose.current?.()
      cancelAfterClose.current = null
    }
  }, [])

  /** 401・403 を扱ったら true（呼び出し元は何もしない）。 */
  const handledCommonly = useCallback((error: unknown, apiPath: string): boolean => {
    if (statusOf(error) === UNAUTHORIZED) {
      setDialog({ kind: 'none' })
      return true
    }
    return forbidden.current(error, apiPath)
  }, [])

  /** 一覧を読む（最後に始めた読み込みの答えだけを使う）。 */
  const load = useCallback(
    async (target: number, options: LoadOptions = {}): Promise<void> => {
      const generation = latest.current.begin()
      const isLatest = () => mounted.current && latest.current.isLatest(generation)
      loadingRef.current = true
      setLoadState(listRef.current === null ? 'loading' : 'reloading')
      let readPage = target
      try {
        let result = await api.listRoles(readPage)
        if (!isLatest()) {
          return
        }
        const corrected = correctedPage(readPage, result.total, result.items.length)
        if (corrected !== undefined) {
          // 空のページの補正は1回の読み込みにつき1回まで（W3.2 の 2）。
          readPage = corrected
          result = await api.listRoles(readPage)
          if (!isLatest()) {
            return
          }
        }
        listRef.current = result
        pageRef.current = readPage
        setList(result)
        setPage(readPage)
        setLoadState('loaded')
        setLiveMessage(
          result.items.length > 0
            ? text.current('roleadmin.list.range', {
                ...pageRange(readPage, result.total),
                total: result.total,
              })
            : text.current('roleadmin.list.empty'),
        )
        if (options.focus !== undefined) {
          setFocusTarget(options.focus)
        } else if (
          options.pagerDirection !== undefined &&
          pagerButtonDisabledAfter(options.pagerDirection, readPage, result.total)
        ) {
          setFocusTarget({ kind: 'heading' })
        }
      } catch (error: unknown) {
        if (!isLatest()) {
          return
        }
        if (handledCommonly(error, roleListPath(readPage))) {
          setLoadState(listRef.current === null ? 'loading' : 'loaded')
          return
        }
        listRef.current = null
        setList(null)
        setLoadState('failed')
        setLiveMessage('')
        if (options.retry === true) {
          setFocusTarget({ kind: 'retry' })
        }
      } finally {
        if (latest.current.isLatest(generation)) {
          loadingRef.current = false
        }
      }
    },
    [api, handledCommonly],
  )

  // 画面を開いたら1ページ目を読む（W3.1）。決まった間隔の自動の読み直しはしない。
  useEffect(() => {
    void load(1)
  }, [load])

  const busy = (): boolean => loadingRef.current || slowNotice.submitting

  /** Modal を閉じる状態の更新と同じ時点で呼び、閉じ終わった後に一覧を読み直す。 */
  function reloadAfterDialogClosed(options: LoadOptions): void {
    cancelAfterClose.current?.()
    cancelAfterClose.current = runAfterModalClosed(() => {
      cancelAfterClose.current = null
      if (mounted.current) {
        void load(pageRef.current, options)
      }
    })
  }

  function goToPage(next: number, direction: PagerDirection): void {
    if (busy()) {
      return
    }
    setNotice(null)
    void load(next, { pagerDirection: direction })
  }

  function retry(): void {
    if (busy()) {
      return
    }
    setNotice(null)
    void load(pageRef.current, { focus: { kind: 'heading' }, retry: true })
  }

  function openDialog(next: RoleListDialog): void {
    if (busy()) {
      return
    }
    setNotice(null)
    setNameProblem(null)
    setDialogMessage(null)
    setDialog(next)
  }

  function openCreate(): void {
    openDialog({ kind: 'create' })
  }

  function openRename(row: RoleRow): void {
    openDialog({ kind: 'rename', row })
  }

  function openDelete(row: RoleRow): void {
    openDialog({ kind: 'delete', row })
  }

  function closeDialog(): void {
    if (slowNotice.submitting) {
      return
    }
    setDialog({ kind: 'none' })
  }

  /** 名前の Modal の入力が変わった（サーバーの拒否の文言を消す）。 */
  function changeName(): void {
    setNameProblem(null)
    setDialogMessage(null)
  }

  /** 名前の送信の拒否を扱う（4.2 の rejected、W3.3 の 5・W3.4 の 2）。 */
  function handleNameFailure(error: unknown, apiPath: string): void {
    if (handledCommonly(error, apiPath)) {
      return
    }
    const code = knownCode(error)
    if (code === 'ROLE_NOT_FOUND') {
      setDialog({ kind: 'none' })
      setNotice({ variant: 'warning', key: 'roleadmin.role.missing' })
      reloadAfterDialogClosed({ focus: { kind: 'heading' } })
      return
    }
    const key = failureMessageKey(error, 'name')
    if (
      code === 'VALIDATION_FAILED' ||
      code === 'ROLE_NAME_DUPLICATE' ||
      code === 'ROLE_NO_CHANGE'
    ) {
      setNameProblem(key)
    } else {
      setDialogMessage(key)
    }
  }

  /** 名前を送る（W3.3・W3.4）。画面の検査を通った名前だけが届く。 */
  function submitName(name: string): void {
    const current = dialog
    if (current.kind !== 'create' && current.kind !== 'rename') {
      return
    }
    const apiPath = current.kind === 'create' ? ROLE_ADMIN_API_ROOT : rolePath(current.row.roleId)
    const sent = slowNotice.run(() =>
      current.kind === 'create'
        ? api.createRole(name).then((role) => role.name)
        : api.renameRole(current.row.roleId, name).then(() => name),
    )
    if (sent === undefined) {
      return
    }
    setNameProblem(null)
    setDialogMessage(null)
    sent.then(
      (savedName) => {
        if (!mounted.current) {
          return
        }
        setDialog({ kind: 'none' })
        setNotice({
          variant: 'success',
          key: current.kind === 'create' ? 'roleadmin.result.created' : 'roleadmin.result.renamed',
          values: { name: savedName },
        })
        reloadAfterDialogClosed(
          current.kind === 'rename' ? { focus: { kind: 'row', roleId: current.row.roleId } } : {},
        )
      },
      (error: unknown) => {
        if (mounted.current) {
          handleNameFailure(error, apiPath)
        }
      },
    )
  }

  /** 削除を送る（W3.5 の 3・4）。 */
  function confirmDelete(): void {
    const current = dialog
    if (current.kind !== 'delete') {
      return
    }
    const { row } = current
    const sent = slowNotice.run(() => api.deleteRole(row.roleId))
    if (sent === undefined) {
      return
    }
    setDialogMessage(null)
    sent.then(
      () => {
        if (!mounted.current) {
          return
        }
        setDialog({ kind: 'none' })
        setNotice({
          variant: 'success',
          key: 'roleadmin.result.deleted',
          values: { name: row.name },
        })
        reloadAfterDialogClosed({ focus: { kind: 'heading' } })
      },
      (error: unknown) => {
        if (!mounted.current || handledCommonly(error, rolePath(row.roleId))) {
          return
        }
        const code = knownCode(error)
        if (code === 'ROLE_IN_USE') {
          // 確かめを閉じ、数と割り当てのタブへのリンクを知らせに出す（D9）。数が読めないときは数を除いた文言。
          const counts = inUseCounts(error)
          setDialog({ kind: 'none' })
          setNotice({
            variant: 'warning',
            key: counts !== undefined ? 'roleadmin.inUse' : 'roleadmin.inUse.noCount',
            values: counts !== undefined ? { name: row.name, ...counts } : { name: row.name },
            assignmentsOf: row.roleId,
          })
          return
        }
        if (code === 'ROLE_NOT_FOUND') {
          setDialog({ kind: 'none' })
          setNotice({ variant: 'warning', key: 'roleadmin.role.missing' })
          reloadAfterDialogClosed({ focus: { kind: 'heading' } })
          return
        }
        // BUSY（D4）とほかの失敗（D3）は確かめを開いたまま、理由を Modal の中に出す。
        setDialogMessage(failureMessageKey(error, 'delete'))
      },
    )
  }

  function dismissNotice(): void {
    setNotice(null)
  }

  const consumeFocus = useCallback((): void => {
    setFocusTarget(null)
  }, [])

  return {
    view: roleListViewOf(loadState, list),
    list,
    page,
    dialog,
    nameProblem,
    dialogMessage,
    notice,
    liveMessage,
    focusTarget,
    submitting: slowNotice.submitting,
    slow: slowNotice.slow,
    loading: loadState === 'loading' || loadState === 'reloading',
    goToPage,
    retry,
    openCreate,
    openRename,
    openDelete,
    closeDialog,
    changeName,
    submitName,
    confirmDelete,
    dismissNotice,
    consumeFocus,
  }
}

/** S3 の状態と操作の型 */
export type RoleListState = ReturnType<typeof useRoleList>
