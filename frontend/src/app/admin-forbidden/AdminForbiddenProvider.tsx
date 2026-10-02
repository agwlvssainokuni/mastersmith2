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
// 管理の画面の「権限が無い」（403・ACCESS_DENIED）を骨組みの1か所で扱う（U4 の D2〜D8、FC 3.3、R-02）。
// - 管理の画面は、API の失敗を useAdminForbidden が返す関数に、その画面の API の根のパスを添えて渡す（D2）。
//   判定は isAdminForbidden の1か所だけで行い、ここでは状態コードと code を見ない（D1、NFR1.2）。
// - 権限が無いとき、その画面の URL（描画の時点のパス）を「権限が無い URL」として覚え、ShellLayout が
//   useIsAdminForbiddenHere で読んでコンテンツの領域を S6 に置き換える（D3）。
// - 権限が無い URL は1つだけ持ち、パスが変わったらその描画の中で捨てる（useEffect を待たない。D5）。
//   S6 を出すのは、覚えた URL と今のパスが同じときだけ（捨て方と二重に守る）。
// - 今の URL は ref（currentPathRef）で持ち、report の中で読む。画面が外れた後に届いた 403 は、
//   渡した画面の URL と今の URL が違うため S6 にしない（D4、W5）。
// - 権限が無い URL を新しく覚えたとき、ログインの状態を refreshSessionOnce で1回読み直す（D7）。
//   骨組みが呼んだ読み直しが終わるまでは新しく呼ばない。結果は待たない（読み直しの結果は
//   ログイン状態の知らせで届く、D8）。決まった間隔・時間切れ・時刻の判定は置かない（NFR9.1・NFR9.2）。
// - 状態用と関数用の context を分け、report は Provider が生きている間は同じ関数のままにする。
// - 失敗の値は覚えず、画面にも console にも出さない（NFR3.1）。
import {
  createContext,
  useCallback,
  useContext,
  useLayoutEffect,
  useRef,
  useState,
  type ReactNode,
} from 'react'
import { useLocation } from 'react-router'
import { isAdminForbidden } from '../../shared/api-client/adminForbidden'
import { refreshSessionOnce } from '../../shared/api-client/apiClient'

/** 画面の URL を「権限が無い URL」として知らせる関数（Provider の中だけで作る） */
type Report = (screenPath: string) => void

/** 今の URL が権限が無い URL か（Provider の外では null） */
const ForbiddenHereContext = createContext<boolean | null>(null)

/** 権限が無い URL を知らせる関数（Provider の外では null） */
const ReportContext = createContext<Report | null>(null)

const OUTSIDE_PROVIDER =
  'AdminForbiddenProvider の外で使われました（骨組みの並びに置き忘れがあります）'

export interface AdminForbiddenProviderProps {
  children: ReactNode
}

/** 権限が無い URL を持ち、管理の画面の 403 を受けて S6 への切り替えとログインの状態の読み直しを行う。 */
export function AdminForbiddenProvider({ children }: AdminForbiddenProviderProps) {
  const { pathname } = useLocation()
  const [forbidden, setForbidden] = useState<string | null>(null)
  const [seenPath, setSeenPath] = useState(pathname)
  // パスが変わったら、その描画の中で権限が無い URL を捨てる（前の描画の値と比べて状態を直す形、D5）。
  if (seenPath !== pathname) {
    setSeenPath(pathname)
    setForbidden(null)
  }

  const currentPathRef = useRef(pathname)
  const forbiddenRef = useRef<string | null>(null)
  const inFlightRef = useRef<Promise<boolean> | null>(null)

  useLayoutEffect(() => {
    currentPathRef.current = pathname
    if (forbiddenRef.current !== pathname) {
      forbiddenRef.current = null
    }
  }, [pathname])

  const report = useCallback<Report>((screenPath) => {
    const here = screenPath === currentPathRef.current
    if (here && forbiddenRef.current === screenPath) {
      // 同じ URL で重ねて届いた 403（D7）。読み直しを重ねない。
      return
    }
    if (here) {
      forbiddenRef.current = screenPath
      setForbidden(screenPath)
    }
    // 結び付かない 403（W5）も読み直しのきっかけにするが、骨組みが呼んだ読み直しが終わるまでは重ねない。
    if (inFlightRef.current === null) {
      inFlightRef.current = refreshSessionOnce().finally(() => {
        inFlightRef.current = null
      })
    }
  }, [])

  // 覚えた URL と今のパスが同じときだけ S6 にする（描画の中で求める、D5）。
  const forbiddenHere = forbidden !== null && forbidden === pathname

  return (
    <ReportContext.Provider value={report}>
      <ForbiddenHereContext.Provider value={forbiddenHere}>
        {children}
      </ForbiddenHereContext.Provider>
    </ReportContext.Provider>
  )
}

/**
 * 管理の画面が API の失敗を渡す口（D2、契約 C4）。返す関数は、権限が無い（isAdminForbidden が true）なら
 * その画面の URL を骨組みに知らせて true を返し、それ以外は何もせず false を返す。
 * 返す関数は、描画の時点の画面の URL が変わらない限り同じもの。Provider の外で呼ぶと例外。
 */
export function useAdminForbidden(): (error: unknown, apiPath: string) => boolean {
  const report = useContext(ReportContext)
  const { pathname } = useLocation()
  const handle = useCallback(
    (error: unknown, apiPath: string): boolean => {
      if (!isAdminForbidden(apiPath, error)) {
        return false
      }
      report?.(pathname)
      return true
    },
    [report, pathname],
  )
  if (report === null) {
    throw new Error(OUTSIDE_PROVIDER)
  }
  return handle
}

/** 今の URL が権限が無い URL か（ShellLayout が読む）。Provider の外で呼ぶと例外（計画の D-5）。 */
export function useIsAdminForbiddenHere(): boolean {
  const forbiddenHere = useContext(ForbiddenHereContext)
  if (forbiddenHere === null) {
    throw new Error(OUTSIDE_PROVIDER)
  }
  return forbiddenHere
}
