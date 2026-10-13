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
// 業務のメニューの状態（Intent 261004-role-menu の U7 の L4、functional-spec.md の D2・D4・D16・D18・4.2、
// frontend-components.md の 4.1、NFR2.2・NFR2.9、security-design.md 3節、計画 12節 D-7・D-8）。
// - ログインしている間だけ GET /api/me/navigation を読む。きっかけは useReloadTriggers（D4 の (a)・(d)）と作業ロールの
//   切り替えの回数（(b)・(c)。同じ効果にまとめ、重なりを1回にする）と reload()（置き場の 403・ホームの［もう一度］）。
// - 要求の世代で古い答えを捨てる。読み直しの間と読み直しの失敗では前に読めた木を残す（D18）。ただし、前に読めた木が
//   今と違う作業ロールの切り替えの回数（revision）のときに読んだもので、作業ロールの切り替え（拒否を含む）の後の読み直しが
//   失敗したときは、前のロールの木を印もなく出し続けないよう木を捨て、初めての読み込みの失敗と同じ load-error にする
//   （Build and Test の Q3 A、app-frame-ui の R-01。D18 との差は build-and-test/fix-notes.md に記録）。
// - 未ログインになったら木を捨て、開閉の保存の鍵を消す（clearExpanded。security-design.md 3節、D-7）。
// - 提供元が無いときは既定の値（idle、要求を送らない）を返す（D-8）。
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
import { useWorkRole } from '../work-role/WorkRoleProvider'
import type { BusinessMenuView, BusinessNavStatus } from './buildNavSections'
import { clearExpanded } from './navExpansion'
import { getNavigation, type NavigationResponse } from './navigationApi'
import { useReloadTriggers } from './useReloadTriggers'

/** useBusinessNavigation が返す値 */
export interface BusinessNavigationValue extends BusinessMenuView {
  status: BusinessNavStatus
  /** 最後に読めた応答（無ければ null） */
  response: NavigationResponse | null
  /** 読み直す */
  reload: () => void
}

const IDLE: BusinessNavigationValue = { status: 'idle', response: null, reload: () => {} }

const BusinessNavigationContext = createContext<BusinessNavigationValue>(IDLE)

interface LoadState {
  phase: 'idle' | 'loading' | 'loaded' | 'load-error'
  response: NavigationResponse | null
  reloading: boolean
  /** response を読んだときの作業ロールの切り替えの回数（response が無ければ null） */
  revision: number | null
}

const INITIAL: LoadState = { phase: 'idle', response: null, reloading: false, revision: null }
const LOAD_ERROR: LoadState = {
  phase: 'load-error',
  response: null,
  reloading: false,
  revision: null,
}

export interface BusinessNavigationProviderProps {
  /** 業務のメニューを読む関数（既定は navigationApi の getNavigation） */
  load?: () => Promise<NavigationResponse>
  children: ReactNode
}

/** 業務のメニューの状態を持ち、子に渡す。 */
export function BusinessNavigationProvider({
  load = getNavigation,
  children,
}: BusinessNavigationProviderProps) {
  const loginState = useLoginState()
  const { revision } = useWorkRole()
  const [state, setState] = useState<LoadState>(INITIAL)
  const generation = useRef(0)
  const loggedInRef = useRef(loginState.loggedIn)
  const revisionRef = useRef(revision)

  // 読み直しのきっかけ（useReloadTriggers の効果）より先に、最新の値を写す（同じ描画の効果は書いた順に動く）。
  useEffect(() => {
    loggedInRef.current = loginState.loggedIn
    revisionRef.current = revision
  })

  const reload = useCallback((): void => {
    if (!loggedInRef.current) {
      return
    }
    generation.current += 1
    const mine = generation.current
    const requestedRevision = revisionRef.current
    setState((prev) =>
      prev.response === null
        ? { phase: 'loading', response: null, reloading: false, revision: null }
        : { ...prev, reloading: true },
    )
    load().then(
      (response) => {
        if (generation.current === mine) {
          setState({ phase: 'loaded', response, reloading: false, revision: requestedRevision })
        }
      },
      () => {
        if (generation.current === mine) {
          // 前の木が無いとき、または前の木が切り替えの前に読んだものなら捨てる（R-01）。同じ回数の間の失敗は木を残す（D18）。
          setState((prev) =>
            prev.response === null || prev.revision !== requestedRevision
              ? LOAD_ERROR
              : { ...prev, reloading: false },
          )
        }
      },
    )
  }, [load])

  useReloadTriggers(reload, revision)

  // 未ログインになったら値を捨てる（描画の中で前の値と比べて直す。React の「値が変わったときに状態を直す」の形）。
  const [seenLoggedIn, setSeenLoggedIn] = useState(loginState.loggedIn)
  if (seenLoggedIn !== loginState.loggedIn) {
    setSeenLoggedIn(loginState.loggedIn)
    if (!loginState.loggedIn) {
      setState(INITIAL)
    }
  }

  // 未ログインになったら世代を進めて、後から届いた答えを捨てる（外の値だけを直し、状態は変えない）。
  useEffect(() => {
    if (!loginState.loggedIn) {
      generation.current += 1
      clearExpanded()
    }
  }, [loginState.loggedIn])

  const value = useMemo<BusinessNavigationValue>(() => {
    if (!loginState.loggedIn || state.phase === 'idle') {
      return IDLE
    }
    const status: BusinessNavStatus =
      state.phase === 'loaded' && state.reloading ? 'reloading' : state.phase
    return { status, response: state.response, reload }
  }, [loginState.loggedIn, state, reload])

  return (
    <BusinessNavigationContext.Provider value={value}>
      {children}
    </BusinessNavigationContext.Provider>
  )
}

/** 業務のメニューの状態を返す。提供元が無ければ既定の値（idle、要求を送らない）。 */
export function useBusinessNavigation(): BusinessNavigationValue {
  return useContext(BusinessNavigationContext)
}
