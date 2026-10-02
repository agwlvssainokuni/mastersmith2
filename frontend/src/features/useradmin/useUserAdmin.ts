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
// 利用者の管理の画面の状態と操作（frontend-components.md の 3節、functional-spec.md の 4〜6節・D1〜D4・D8〜D14・D17・D20、
// performance-design.md の 2節・3節、security-design.md の 2.2・6.1）。
// - 画面の状態はこの画面の中だけで持ち、アプリ全体の置き場・ブラウザの保存・URL・履歴・コンソールに出さない（D2・D20）。
// - 一覧を読むのは、開いたとき・ページ送り・検索・検索を消す・操作と保存の応答の後・「もう一度読み込む」のときだけ。
//   決まった間隔の読み直しはしない（D3）。読み直しごとに番号を増やし、最後に始めた読み直しの答え（失敗を含む）だけを使う。
//   古い答えは useAdminForbidden にも渡さない（計画 8節の D-6）。画面を離れた後の答えは捨てる。
// - 今のページと今の検索の文字は、応答 200 を受けたときだけ読んだ値に置き換える（送る前と 400 では書き換えない。R-04）。
// - 読み直しで今のページが空になり全件数が 1 以上なら、correctedPage の最後のページを読む。これは1回の読み込みにつき
//   1回まで（D10、R-06）。
// - 二重の送信を参照で防ぐ（D13 の (3)）。loadingRef（読み直しの最中）・submittingRef（送信の最中）を立てた関数の finally で
//   必ず戻す（PD 3.2、R-03）。loadingRef は最後に始めた読み直しが終わったときにだけ戻す。
// - 失敗の扱いは handledCommonly の1か所に集め（一覧は handleListFailure から呼ぶ）、401 → 権限が無い（useAdminForbidden）→ 場面ごとの順に行う（D11・D12、SD 2.2）。
// - 送信から 5 秒を過ぎたら「時間がかかっています」を出す。時間で要求を打ち切らない（D14）。
// - 自分の行の保存が成功したら、反映（applyOwnProfile）→ Toast（送った言語で引く）→ 読み直しの順に行う（D17・W8、R-02）。
import { useToast } from 'make-you-chic-ui'
import { useCallback, useEffect, useRef, useState } from 'react'
import type { ApplyOwnProfileInput } from '../../app/display-settings/useApplyOwnProfile'
import { readFieldErrors } from '../../shared/api-client/fieldErrors'
import { trimDisplayName } from '../../shared/validation/codePoints'
import {
  correctedPage,
  pageRange,
  pagerButtonDisabledAfter,
  type PagerDirection,
} from '../../shared/paging/paging'
import type { AdminUser, AdminUserPage, UserLanguage } from './api/types'
import {
  USER_ADMIN_API_ROOT,
  userAdminOperationPath,
  type UserAdminApi,
  type UserAdminOperation,
} from './api/userAdminApi'
import { failureMessageKey, failureStatus, knownCode } from './failureMessage'
import type { FocusTarget } from './focusTarget'
import {
  checkProfileName,
  PROFILE_FIELDS,
  PROFILE_FORM_INVALID_KEY,
  profileMessageKey,
  toProfileReason,
  type ProfileField,
} from './profileInput'
import { rowActions, type ConfirmActionKind, type RowActionKind } from './rowActions'
import { checkSearchInput } from './searchInput'
import type { UserAdminText } from './useUserAdminText'

/** 一覧の読み込みの状態 */
export type UserAdminLoadState = 'loading' | 'reloading' | 'loaded' | 'failed'

/** 画面の状態（functional-spec.md の 4.1 の6つ。loadState・list・searchText から導く） */
export type UserAdminView =
  'loading' | 'reloading' | 'populated' | 'empty-search' | 'empty-page' | 'load-error'

/** 業務の失敗の知らせ（文言の鍵と差し込む氏名） */
export interface FailureNotice {
  key: string
  name?: string
}

/** 確かめの表示（S3）の状態 */
export interface ConfirmState {
  action: ConfirmActionKind
  user: AdminUser
  submitting: boolean
}

/** 氏名・言語の入力（S4）の状態の種類（invalid は fieldErrors があることで表す） */
export type EditStatus = 'open' | 'submitting' | 'notFound' | 'failed'

/** 氏名・言語の入力（S4）の状態 */
export interface EditState {
  user: AdminUser
  displayName: string
  language: UserLanguage
  /** 項目ごとの誤りの文言の鍵 */
  fieldErrors: Partial<Record<ProfileField, string>>
  /** 誤りを出した回数（同じ誤りでも描いた後に最初の誤りの欄へフォーカスを移すため） */
  errorSeq: number
  status: EditStatus
  /** not-found・failed の文言の鍵 */
  dialogMessage: string | null
}

/** フックが受ける口 */
export interface UseUserAdminOptions {
  /** API の関数の集まり（テストで差し替える） */
  api: UserAdminApi
  /** 文言を引く関数（Toast と読み上げに使う） */
  t: UserAdminText
  /** 管理の画面の 403 を骨組みに渡す口（U4 の useAdminForbidden、C4） */
  reportForbidden: (error: unknown, apiPath: string) => boolean
  /** 自分の氏名と言語を当てる口（U4 の useApplyOwnProfile、C4） */
  applyOwnProfile: (profile: ApplyOwnProfileInput) => void
}

/** 読み込みの目当て（読むページと検索の文字） */
interface LoadTarget {
  page: number
  searchText: string
}

/** 読み込みの添え */
interface LoadOptions {
  /** 読めた後に当てるフォーカス */
  focus?: FocusTarget
  /** 押したページ送りのボタン（押せなくなったら一覧の見出しへ） */
  pagerDirection?: PagerDirection
  /** 「もう一度読み込む」から（また失敗したら「もう一度読み込む」へフォーカス） */
  retry?: boolean
}

/** 未認証の状態コード（ApiClient とログインの状態に任せる） */
const UNAUTHORIZED = 401

/** 「時間がかかっています」を出すまでの時間（ミリ秒、D14） */
export const SLOW_NOTICE_MS = 5000

/** 確かめの操作と API の操作の名前 */
const OPERATIONS: Readonly<Record<ConfirmActionKind, UserAdminOperation>> = {
  grantAdmin: 'grantAdmin',
  revokeAdmin: 'revokeAdmin',
  suspend: 'suspend',
  resume: 'resume',
  resetFailures: 'resetFailures',
}

function callOperation(
  api: UserAdminApi,
  action: ConfirmActionKind,
  userId: number,
): Promise<void> {
  switch (action) {
    case 'grantAdmin':
      return api.grantAdmin(userId)
    case 'revokeAdmin':
      return api.revokeAdmin(userId)
    case 'suspend':
      return api.suspendUser(userId)
    case 'resume':
      return api.resumeUser(userId)
    case 'resetFailures':
      return api.resetLoginFailures(userId)
  }
}

/** 画面の状態を導く（4.1） */
export function viewOf(
  loadState: UserAdminLoadState,
  list: AdminUserPage | null,
  searchText: string,
): UserAdminView {
  if (loadState === 'failed') {
    return 'load-error'
  }
  if (list === null || loadState === 'loading') {
    return 'loading'
  }
  if (loadState === 'reloading') {
    return 'reloading'
  }
  if (list.items.length > 0) {
    return 'populated'
  }
  return list.total === 0 && searchText !== '' ? 'empty-search' : 'empty-page'
}

/** 画面の状態と操作 */
export function useUserAdmin({ api, t, reportForbidden, applyOwnProfile }: UseUserAdminOptions) {
  const toast = useToast()
  const [page, setPage] = useState(1)
  const [searchText, setSearchText] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const [searchError, setSearchError] = useState<string | null>(null)
  const [list, setList] = useState<AdminUserPage | null>(null)
  const [loadState, setLoadState] = useState<UserAdminLoadState>('loading')
  const [failure, setFailure] = useState<FailureNotice | null>(null)
  const [confirm, setConfirm] = useState<ConfirmState | null>(null)
  const [edit, setEdit] = useState<EditState | null>(null)
  const [busyUserId, setBusyUserId] = useState<number | null>(null)
  const [slow, setSlow] = useState(false)
  const [focusTarget, setFocusTarget] = useState<FocusTarget | null>(null)
  const [liveMessage, setLiveMessage] = useState('')

  const mounted = useRef(true)
  const loadSeq = useRef(0)
  const loadingRef = useRef(false)
  const submittingRef = useRef(false)
  const slowTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const current = useRef<LoadTarget>({ page: 1, searchText: '' })
  const listRef = useRef<AdminUserPage | null>(null)
  const text = useRef(t)
  const forbidden = useRef(reportForbidden)
  const applyOwn = useRef(applyOwnProfile)

  // 応答を受けた後（描画の外）で使う関数は、最新のものを参照に置く。
  useEffect(() => {
    text.current = t
    forbidden.current = reportForbidden
    applyOwn.current = applyOwnProfile
  }, [t, reportForbidden, applyOwnProfile])

  const stopSlowTimer = useCallback((): void => {
    if (slowTimer.current !== null) {
      clearTimeout(slowTimer.current)
      slowTimer.current = null
    }
  }, [])

  useEffect(() => {
    mounted.current = true
    return () => {
      mounted.current = false
      stopSlowTimer()
    }
  }, [stopSlowTimer])

  /** 401 の後に開いている表示を閉じる（D12）。 */
  const closeDialogsOnUnauthorized = useCallback((): void => {
    setConfirm(null)
    setEdit(null)
  }, [])

  /**
   * 失敗の扱いの入口（D11・D12）。401 なら開いている S3・S4 を閉じて終わり、権限が無い（403・ACCESS_DENIED）なら骨組みに
   * 渡して終わり、どちらでもなければ false を返して、呼び出し元が場面ごとに扱う。
   */
  const handledCommonly = useCallback(
    (error: unknown, apiPath: string): boolean => {
      if (failureStatus(error) === UNAUTHORIZED) {
        closeDialogsOnUnauthorized()
        return true
      }
      return forbidden.current(error, apiPath)
    },
    [closeDialogsOnUnauthorized],
  )

  /** 一覧の読み込みの失敗を扱う（6.1）。 */
  const handleListFailure = useCallback(
    (error: unknown, options: LoadOptions): void => {
      const hasList = listRef.current !== null
      if (handledCommonly(error, USER_ADMIN_API_ROOT)) {
        // 状態を変えない（401 はログインの画面へ、403 は S6 へ移る）。
        setLoadState(hasList ? 'loaded' : 'loading')
        return
      }
      if (hasList && failureMessageKey(error, 'list') !== 'useradmin.list.loadFailed') {
        // 400 VALIDATION_FAILED: 一覧と今のページ・今の検索の文字は前のまま、入力欄の下に出す（W3 の 5）。
        setLoadState('loaded')
        setSearchError(failureMessageKey(error, 'list'))
        return
      }
      listRef.current = null
      setList(null)
      setLoadState('failed')
      setLiveMessage('')
      if (options.retry === true) {
        setFocusTarget({ kind: 'retry' })
      }
    },
    [handledCommonly],
  )

  /** 一覧を読む（最後に始めた読み直しの答えだけを使う）。 */
  const load = useCallback(
    async (target: LoadTarget, options: LoadOptions = {}): Promise<void> => {
      loadSeq.current += 1
      const seq = loadSeq.current
      const isLatest = () => mounted.current && seq === loadSeq.current
      loadingRef.current = true
      setLoadState(listRef.current === null ? 'loading' : 'reloading')
      try {
        let readPage = target.page
        let result = await api.listUsers(readPage, target.searchText)
        if (!isLatest()) {
          return
        }
        const corrected = correctedPage(readPage, result.total, result.items.length)
        if (corrected !== undefined) {
          // 空のページの補正は1回の読み込みにつき1回まで（補正した先も空なら empty-page）。
          readPage = corrected
          result = await api.listUsers(readPage, target.searchText)
          if (!isLatest()) {
            return
          }
        }
        current.current = { page: readPage, searchText: target.searchText }
        listRef.current = result
        setPage(readPage)
        setSearchText(target.searchText)
        setList(result)
        setLoadState('loaded')
        setSearchError(null)
        const range = pageRange(readPage, result.total)
        setLiveMessage(
          result.items.length > 0
            ? text.current('useradmin.list.range', { ...range, total: result.total })
            : '',
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
        handleListFailure(error, options)
      } finally {
        if (seq === loadSeq.current) {
          loadingRef.current = false
        }
      }
    },
    [api, handleListFailure],
  )

  // 画面を開いたら1ページ目・検索なしを読む（今のページと検索の文字は画面の中だけに持つ、D2）。
  useEffect(() => {
    void load({ page: 1, searchText: '' })
  }, [load])

  function reload(options: LoadOptions = {}): void {
    void load({ ...current.current }, options)
  }

  function goToPage(next: number, direction: PagerDirection): void {
    if (loadingRef.current || submittingRef.current) {
      return
    }
    setFailure(null)
    void load({ page: next, searchText: current.current.searchText }, { pagerDirection: direction })
  }

  function changeSearchInput(value: string): void {
    setSearchInput(value)
  }

  function submitSearch(): void {
    if (loadingRef.current || submittingRef.current) {
      return
    }
    const checked = checkSearchInput(searchInput)
    if (!checked.ok) {
      setSearchError('useradmin.search.tooLong')
      return
    }
    setFailure(null)
    void load({ page: 1, searchText: checked.searchText })
  }

  function clearSearch(): void {
    if (loadingRef.current || submittingRef.current) {
      return
    }
    setFailure(null)
    setSearchInput('')
    setSearchError(null)
    void load({ page: 1, searchText: '' })
  }

  function retry(): void {
    if (loadingRef.current || submittingRef.current) {
      return
    }
    setFailure(null)
    reload({ focus: { kind: 'heading' }, retry: true })
  }

  function selectAction(user: AdminUser, action: RowActionKind): void {
    if (loadingRef.current || submittingRef.current) {
      return
    }
    const item = rowActions(user).find((candidate) => candidate.kind === action)
    if (item === undefined || item.disabledReason !== undefined) {
      return
    }
    setFailure(null)
    if (action === 'editProfile') {
      setEdit({
        user,
        displayName: user.displayName,
        language: user.language,
        fieldErrors: {},
        errorSeq: 0,
        status: 'open',
        dialogMessage: null,
      })
      return
    }
    setConfirm({ action, user, submitting: false })
  }

  function beginSubmit(userId: number): void {
    submittingRef.current = true
    setBusyUserId(userId)
    setSlow(false)
    stopSlowTimer()
    slowTimer.current = setTimeout(() => {
      slowTimer.current = null
      if (mounted.current) {
        setSlow(true)
      }
    }, SLOW_NOTICE_MS)
  }

  function endSubmit(): void {
    submittingRef.current = false
    stopSlowTimer()
    if (mounted.current) {
      setBusyUserId(null)
      setSlow(false)
    }
  }

  async function confirmAction(): Promise<void> {
    if (submittingRef.current || confirm === null || confirm.submitting) {
      return
    }
    const { action, user } = confirm
    const path = userAdminOperationPath(user.userId, OPERATIONS[action])
    beginSubmit(user.userId)
    setConfirm({ ...confirm, submitting: true })
    setFailure(null)
    try {
      await callOperation(api, action, user.userId)
      if (!mounted.current) {
        return
      }
      setConfirm(null)
      toast.show({
        message: text.current(`useradmin.toast.${action}`, { name: user.displayName }),
        variant: 'success',
      })
      reload({ focus: { kind: 'row', userId: user.userId } })
    } catch (error: unknown) {
      if (!mounted.current) {
        return
      }
      if (handledCommonly(error, path)) {
        setConfirm(null)
        return
      }
      setConfirm(null)
      setFailure({ key: failureMessageKey(error, 'operation'), name: user.displayName })
      reload({ focus: { kind: 'row', userId: user.userId } })
    } finally {
      endSubmit()
    }
  }

  function closeConfirm(): void {
    if (confirm === null || confirm.submitting) {
      return
    }
    setConfirm(null)
  }

  function changeEditName(value: string): void {
    setEdit((state) => (state ? { ...state, displayName: value } : state))
  }

  function changeEditLanguage(value: UserLanguage): void {
    setEdit((state) => (state ? { ...state, language: value } : state))
  }

  /** サーバーの 400 の項目ごとの誤りを、画面の文言の鍵にする（項目に結び付くものだけ）。 */
  function serverFieldErrors(error: unknown): Partial<Record<ProfileField, string>> {
    const problem =
      typeof error === 'object' && error !== null
        ? (error as { problem?: unknown }).problem
        : undefined
    const result: Partial<Record<ProfileField, string>> = {}
    for (const { field, reason } of readFieldErrors(problem, PROFILE_FIELDS)) {
      const key = profileMessageKey(field, toProfileReason(reason))
      if (key !== undefined) {
        result[field] = key
      }
    }
    return result
  }

  async function saveEdit(): Promise<void> {
    if (
      submittingRef.current ||
      edit === null ||
      edit.status === 'submitting' ||
      edit.status === 'notFound'
    ) {
      return
    }
    const nameProblem = checkProfileName(edit.displayName)
    if (nameProblem !== undefined) {
      setEdit({
        ...edit,
        fieldErrors: { displayName: profileMessageKey('displayName', nameProblem) },
        errorSeq: edit.errorSeq + 1,
        status: 'open',
        dialogMessage: null,
      })
      return
    }
    const { user } = edit
    // 氏名は前後の White_Space を除いた値を送る（サーバーと同じ集合で除く。W7 の 4）。
    const sent: ApplyOwnProfileInput = {
      displayName: trimDisplayName(edit.displayName),
      language: edit.language,
    }
    const path = userAdminOperationPath(user.userId, 'profile')
    beginSubmit(user.userId)
    setEdit({ ...edit, fieldErrors: {}, status: 'submitting', dialogMessage: null })
    try {
      await api.updateProfile(user.userId, sent)
      if (!mounted.current) {
        return
      }
      setEdit(null)
      if (user.self) {
        // 反映 → Toast（送った言語で引く）→ 読み直しの順（W8 の 3）。
        applyOwn.current(sent)
      }
      toast.show({
        message: text.current(
          'useradmin.toast.editProfile',
          { name: sent.displayName },
          user.self ? sent.language : undefined,
        ),
        variant: 'success',
      })
      reload({ focus: { kind: 'row', userId: user.userId } })
    } catch (error: unknown) {
      if (!mounted.current) {
        return
      }
      if (handledCommonly(error, path)) {
        setEdit(null)
        return
      }
      const code = knownCode(error)
      if (code === 'VALIDATION_FAILED' && failureStatus(error) === 400) {
        const fieldErrors = serverFieldErrors(error)
        const bound = Object.keys(fieldErrors).length > 0
        setEdit((state) =>
          state
            ? {
                ...state,
                fieldErrors,
                errorSeq: bound ? state.errorSeq + 1 : state.errorSeq,
                status: bound ? 'open' : 'failed',
                dialogMessage: bound ? null : PROFILE_FORM_INVALID_KEY,
              }
            : state,
        )
        return
      }
      const notFound = code === 'USER_NOT_FOUND'
      setEdit((state) =>
        state
          ? {
              ...state,
              status: notFound ? 'notFound' : 'failed',
              dialogMessage: failureMessageKey(error, 'save'),
            }
          : state,
      )
      // 後ろで一覧を読み直す（フォーカスは入力の表示に残す）。
      reload()
    } finally {
      endSubmit()
    }
  }

  function closeEdit(): void {
    if (edit === null || edit.status === 'submitting') {
      return
    }
    setEdit(null)
  }

  function dismissFailure(): void {
    setFailure(null)
  }

  const consumeFocus = useCallback(() => setFocusTarget(null), [])

  return {
    view: viewOf(loadState, list, searchText),
    list,
    page,
    searchText,
    searchInput,
    searchError,
    failure,
    liveMessage,
    confirm,
    edit,
    busyUserId,
    slow,
    focusTarget,
    loading: loadState === 'loading' || loadState === 'reloading',
    goToPage,
    changeSearchInput,
    submitSearch,
    clearSearch,
    retry,
    selectAction,
    confirmAction,
    closeConfirm,
    changeEditName,
    changeEditLanguage,
    saveEdit,
    closeEdit,
    dismissFailure,
    consumeFocus,
  }
}
