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
// 作業ロールの状態・切り替え・読み直し（Intent 261004-role-menu の U7 の L3、functional-spec.md の D2・D4・D7・D8・
// 4.1、frontend-components.md の 3.1、NFR2.2・NFR2.9・NFR2.11、計画 12節 D-4・D-8）。
// - ログインしている間だけ GET /api/me/work-role を読む。読み直しのきっかけは useReloadTriggers（D4 の (a)・(d)）と、
//   切り替えの後・拒否の後（(b)・(c)）。要求の世代で比べ、古い答えは捨てる。読み直しの間は今の値を残す。
// - 切り替え（switchTo）は、今のロールと同じなら要求を送らずに 'unchanged'。送信の最中の2回目は送らずに 'ignored'
//   （NFR2.11）。成功は 'switched'、ROLE_NOT_ASSIGNED・ROLE_BUSY・VALIDATION_FAILED は 'rejected'（D8・D-4）、
//   ほかの失敗は 'failed'。どの結果でも作業ロールを読み直す。成功と拒否は切り替えの回数（revision）を1つ進め、業務の
//   メニューと S2・S8 の読み直しのきっかけにする。Toast と読み上げは部品（WorkRoleSwitcher）が出す。
// - 未ログインになったら値と世代を捨てる（D2）。
// - 提供元が無いときは既定の値（idle、要求を送らない）を返す（D-8。既存の画面のテストへの影響を避ける）。
// - 応答・要求の値をコンソール・ブラウザの保存に出さない。
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { useLoginState } from '../login-state/LoginStateGate'
import { useReloadTriggers } from '../navigation/useReloadTriggers'
import { getWorkRole, switchWorkRole, type WorkRoleRef, type WorkRoleState } from './workRoleApi'

/** 作業ロールの状態（functional-spec 4.1。idle は未ログイン） */
export type WorkRoleStatus = 'idle' | 'loading' | 'ready' | 'reloading' | 'switching' | 'load-error'

/** 切り替えの結果（'ignored' は送信の最中の2回目を捨てたとき） */
export type SwitchResult = 'unchanged' | 'switched' | 'rejected' | 'failed' | 'ignored'

/** 作業ロールの API（テストで差し替える） */
export interface WorkRoleApi {
  getWorkRole: () => Promise<WorkRoleState>
  switchWorkRole: (roleId: number) => Promise<void>
}

const DEFAULT_API: WorkRoleApi = { getWorkRole, switchWorkRole }

/** 切り替えの拒否として扱う code（D8・計画 12節 D-4） */
const REJECTION_CODES: readonly string[] = ['ROLE_NOT_ASSIGNED', 'ROLE_BUSY', 'VALIDATION_FAILED']

/** useWorkRole が返す値 */
export interface WorkRoleContextValue {
  status: WorkRoleStatus
  /** 自分のロール（応答の順、ロールの ID の順） */
  roles: readonly WorkRoleRef[]
  /** 有効な作業ロール（無ければ null） */
  current: WorkRoleRef | null
  /** 切り替えの成功・拒否のたびに 1 進む */
  revision: number
  /** 切り替えの送信の最中か */
  switching: boolean
  switchTo: (roleId: number) => Promise<SwitchResult>
  /** 作業ロールを読み直す */
  reload: () => void
  /** トップバーの切り替えのトリガーへフォーカスを移す（D23） */
  focusWorkRoleSwitcher: () => void
  /** トリガーへフォーカスを移す関数を置く（WorkRoleSwitcher が使う）。戻り値は外す関数 */
  registerSwitcherFocus: (focus: () => void) => () => void
}

const IDLE: WorkRoleContextValue = {
  status: 'idle',
  roles: [],
  current: null,
  revision: 0,
  switching: false,
  switchTo: () => Promise.resolve('ignored'),
  reload: () => {},
  focusWorkRoleSwitcher: () => {},
  registerSwitcherFocus: () => () => {},
}

const WorkRoleContext = createContext<WorkRoleContextValue>(IDLE)

interface LoadState {
  phase: 'idle' | 'loading' | 'loaded' | 'load-error'
  data: WorkRoleState | null
  /** 読み直しの最中か（前の値を残す） */
  reloading: boolean
}

const INITIAL: LoadState = { phase: 'idle', data: null, reloading: false }

function codeOf(error: unknown): string | undefined {
  const value = error as { kind?: unknown; code?: unknown } | null | undefined
  return typeof value === 'object' &&
    value !== null &&
    value.kind === 'response' &&
    typeof value.code === 'string'
    ? value.code
    : undefined
}

export interface WorkRoleProviderProps {
  /** API の関数（既定は workRoleApi） */
  api?: WorkRoleApi
  children: ReactNode
}

/** 作業ロールの状態を持ち、子に渡す。 */
export function WorkRoleProvider({ api = DEFAULT_API, children }: WorkRoleProviderProps) {
  const loginState = useLoginState()
  const [load, setLoad] = useState<LoadState>(INITIAL)
  const [revision, setRevision] = useState(0)
  const [switching, setSwitching] = useState(false)
  const generation = useRef(0)
  const switchingRef = useRef(false)
  const loggedInRef = useRef(loginState.loggedIn)
  const currentRef = useRef<WorkRoleRef | null>(null)
  const focusRef = useRef<(() => void) | null>(null)

  useEffect(() => {
    loggedInRef.current = loginState.loggedIn
    currentRef.current = load.data?.current ?? null
  })

  const reload = useCallback((): void => {
    if (!loggedInRef.current) {
      return
    }
    generation.current += 1
    const mine = generation.current
    setLoad((prev) =>
      prev.data === null
        ? { phase: 'loading', data: null, reloading: false }
        : { ...prev, reloading: true },
    )
    api.getWorkRole().then(
      (data) => {
        if (generation.current === mine) {
          currentRef.current = data.current
          setLoad({ phase: 'loaded', data, reloading: false })
        }
      },
      () => {
        if (generation.current === mine) {
          // 読み直しの失敗は前の値を残す。初めての読み込みの失敗だけ load-error。
          setLoad((prev) =>
            prev.data === null
              ? { phase: 'load-error', data: null, reloading: false }
              : { ...prev, reloading: false },
          )
        }
      },
    )
  }, [api])

  useReloadTriggers(reload)

  // 未ログインになったら値を捨てる（描画の中で前の値と比べて直す。React の「値が変わったときに状態を直す」の形）。
  const [seenLoggedIn, setSeenLoggedIn] = useState(loginState.loggedIn)
  if (seenLoggedIn !== loginState.loggedIn) {
    setSeenLoggedIn(loginState.loggedIn)
    if (!loginState.loggedIn) {
      setLoad(INITIAL)
    }
  }

  // 未ログインになったら世代を進めて、後から届いた答えを捨てる（外の値だけを直し、状態は変えない）。
  useEffect(() => {
    if (!loginState.loggedIn) {
      generation.current += 1
      currentRef.current = null
    }
  }, [loginState.loggedIn])

  const switchTo = useCallback(
    async (roleId: number): Promise<SwitchResult> => {
      if (switchingRef.current) {
        return 'ignored'
      }
      if (currentRef.current?.roleId === roleId) {
        return 'unchanged'
      }
      switchingRef.current = true
      setSwitching(true)
      let result: SwitchResult
      try {
        await api.switchWorkRole(roleId)
        result = 'switched'
      } catch (error: unknown) {
        const code = codeOf(error)
        result = code !== undefined && REJECTION_CODES.includes(code) ? 'rejected' : 'failed'
      } finally {
        switchingRef.current = false
        setSwitching(false)
      }
      if (result !== 'failed') {
        setRevision((value) => value + 1)
      }
      reload()
      return result
    },
    [api, reload],
  )

  const registerSwitcherFocus = useCallback((focus: () => void): (() => void) => {
    focusRef.current = focus
    return () => {
      if (focusRef.current === focus) {
        focusRef.current = null
      }
    }
  }, [])

  const focusWorkRoleSwitcher = useCallback((): void => {
    focusRef.current?.()
  }, [])

  const value = useMemo<WorkRoleContextValue>(() => {
    if (!loginState.loggedIn) {
      return { ...IDLE, registerSwitcherFocus, focusWorkRoleSwitcher }
    }
    let status: WorkRoleStatus
    if (load.phase === 'idle') {
      // ログインした直後で、初めての読み込みをこれから始める（読み込み中として見せる）。
      status = 'loading'
    } else if (switching) {
      status = 'switching'
    } else if (load.phase === 'loaded') {
      status = load.reloading ? 'reloading' : 'ready'
    } else {
      status = load.phase as 'loading' | 'load-error'
    }
    return {
      status,
      roles: load.data?.roles ?? [],
      current: load.data?.current ?? null,
      revision,
      switching,
      switchTo,
      reload,
      focusWorkRoleSwitcher,
      registerSwitcherFocus,
    }
  }, [
    loginState.loggedIn,
    load,
    switching,
    revision,
    switchTo,
    reload,
    focusWorkRoleSwitcher,
    registerSwitcherFocus,
  ])

  return <WorkRoleContext.Provider value={value}>{children}</WorkRoleContext.Provider>
}

/** 作業ロールの状態と操作を返す。提供元が無ければ既定の値（idle、要求を送らない）。 */
export function useWorkRole(): WorkRoleContextValue {
  return useContext(WorkRoleContext)
}
