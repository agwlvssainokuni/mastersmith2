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
// 表示のタイムゾーンの提供元（Intent 261102-timezone-ui-polish の U2、LC6、BR2.12〜BR2.16、RD4・PD1）。
// - ログイン中になったとき GET /api/me/preferences を 1 回読み、保存済みの名前（か未設定）から使う名前を決める。
//   状態は loading・ready（timeZone と由来）・error。要求の世代で古い答えを捨て、未ログインで値と世代を捨てる。
// - applySaved はプリファレンスの画面が読み込み・保存の成功で呼び、読み取りなしで値を切り替える（error からも回復する）。
//   reload は error の再読み込み。ready の読み直しの失敗では前の値を残す。
// - 値は React の状態だけで持つ（ブラウザの保存先・URL・コンソールに置かない）。状態用と操作用の文脈を分ける。
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
import { readBrowserTimeZone, type BrowserTimeZoneReader } from './browserTimeZone'
import { decideDisplayTimeZone, type DisplayTimeZoneSource } from './decideDisplayTimeZone'
import { fetchDisplayTimeZone, type DisplayTimeZoneFetcher } from './displayTimeZoneApi'

/** 表示のタイムゾーンの状態 */
export type DisplayTimeZoneState =
  | { readonly status: 'loading' }
  | { readonly status: 'error' }
  | {
      readonly status: 'ready'
      readonly timeZone: string
      readonly source: DisplayTimeZoneSource
    }

/** 表示のタイムゾーンの操作 */
export interface DisplayTimeZoneActions {
  /** 保存済みの名前（未設定は null）を受けて、読み取りなしで値を切り替える */
  readonly applySaved: (saved: string | null) => void
  /** 読み直す（error の再読み込み） */
  readonly reload: () => void
}

const LOADING: DisplayTimeZoneState = { status: 'loading' }

const OUTSIDE_PROVIDER =
  'DisplayTimeZoneProvider の外で使われました（骨組みの並びに置き忘れがあります）'

/** 状態の文脈（提供元の外では null） */
export const DisplayTimeZoneContext = createContext<DisplayTimeZoneState | null>(null)

/** 操作の文脈（提供元の外では null） */
export const DisplayTimeZoneActionsContext = createContext<DisplayTimeZoneActions | null>(null)

export interface DisplayTimeZoneProviderProps {
  /** 保存済みの名前を読む関数（既定は GET /api/me/preferences） */
  fetcher?: DisplayTimeZoneFetcher
  /** ブラウザのタイムゾーンを読む関数（既定は browserTimeZone の唯一の場所） */
  browserTimeZone?: BrowserTimeZoneReader
  children: ReactNode
}

/** 表示のタイムゾーンを持ち、子に渡す。 */
export function DisplayTimeZoneProvider({
  fetcher = fetchDisplayTimeZone,
  browserTimeZone = readBrowserTimeZone,
  children,
}: DisplayTimeZoneProviderProps) {
  const { loggedIn } = useLoginState()
  const [state, setState] = useState<DisplayTimeZoneState>(LOADING)
  const generation = useRef(0)
  const stateRef = useRef<DisplayTimeZoneState>(LOADING)
  const loggedInRef = useRef(loggedIn)
  const fetcherRef = useRef(fetcher)
  const browserRef = useRef(browserTimeZone)

  useEffect(() => {
    fetcherRef.current = fetcher
    browserRef.current = browserTimeZone
    loggedInRef.current = loggedIn
  })

  const commit = useCallback((next: DisplayTimeZoneState): void => {
    stateRef.current = next
    setState(next)
  }, [])

  const load = useCallback((): void => {
    generation.current += 1
    const mine = generation.current
    if (stateRef.current.status !== 'ready') {
      commit(LOADING)
    }
    fetcherRef.current().then(
      (saved) => {
        if (generation.current !== mine) {
          return
        }
        const decided = decideDisplayTimeZone(saved, browserRef.current())
        commit({ status: 'ready', timeZone: decided.timeZone, source: decided.source })
      },
      () => {
        if (generation.current !== mine) {
          return
        }
        // 読み直しの失敗は前の値を残す。まだ値が無いときだけ error。
        if (stateRef.current.status !== 'ready') {
          commit({ status: 'error' })
        }
      },
    )
  }, [commit])

  // 未ログインになったら値を捨てる（描画の中で前の値と比べて直す。React の「値が変わったときに状態を直す」の形）。
  const [seenLoggedIn, setSeenLoggedIn] = useState(loggedIn)
  if (seenLoggedIn !== loggedIn) {
    setSeenLoggedIn(loggedIn)
    if (!loggedIn) {
      setState(LOADING)
    }
  }

  // ログイン中になったとき 1 回読む。未ログインになったら世代を進めて、後から届いた答えを捨てる（外の値だけを直す）。
  useEffect(() => {
    if (loggedIn) {
      load()
    } else {
      generation.current += 1
      stateRef.current = LOADING
    }
  }, [loggedIn, load])

  const applySaved = useCallback(
    (saved: string | null): void => {
      if (!loggedInRef.current) {
        return
      }
      generation.current += 1
      const decided = decideDisplayTimeZone(saved, browserRef.current())
      commit({ status: 'ready', timeZone: decided.timeZone, source: decided.source })
    },
    [commit],
  )

  const reload = useCallback((): void => {
    if (loggedInRef.current) {
      load()
    }
  }, [load])

  const actions = useMemo<DisplayTimeZoneActions>(
    () => ({ applySaved, reload }),
    [applySaved, reload],
  )

  return (
    <DisplayTimeZoneContext.Provider value={state}>
      <DisplayTimeZoneActionsContext.Provider value={actions}>
        {children}
      </DisplayTimeZoneActionsContext.Provider>
    </DisplayTimeZoneContext.Provider>
  )
}

/** 表示のタイムゾーンの状態を返す。提供元の外では例外。 */
export function useDisplayTimeZone(): DisplayTimeZoneState {
  const value = useContext(DisplayTimeZoneContext)
  if (value === null) {
    throw new Error(OUTSIDE_PROVIDER)
  }
  return value
}

/** 表示のタイムゾーンの操作を返す。提供元の外では例外。 */
export function useDisplayTimeZoneActions(): DisplayTimeZoneActions {
  const value = useContext(DisplayTimeZoneActionsContext)
  if (value === null) {
    throw new Error(OUTSIDE_PROVIDER)
  }
  return value
}
