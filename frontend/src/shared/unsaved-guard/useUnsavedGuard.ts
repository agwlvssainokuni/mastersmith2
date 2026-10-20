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
// 未保存の変更の確かめ（Intent 261004-role-menu の U6 の D15、functional-spec.md の 7.2、L6、AC1.2.15）。
// Intent 261016-business-data-views の U3（D25・計画 4.3 の R-08）で features/roleadmin/ から src/shared/ へ移し、業務データの
// 作成・更新の画面でも使う。roleadmin は markClean と版の数を使わないため、振る舞いは移す前と同じ。
// - 変更がある間、道が変わる移動（タブ・一覧へ戻る・サイドバー・ユーザーメニュー・ブラウザの戻る）を data router の
//   useBlocker で止める。止めたら呼ぶ側が LeaveConfirmDialog を出す。［留まる］は reset、［移る］は変更を捨てる関数を
//   呼んでから proceed する。道が同じ移動（問い合わせだけの違い）は止めない。
// - 変更がある間は beforeunload を張り、読み込み直し・タブを閉じるときにブラウザの確かめを出す（文言はブラウザが決める）。
// - 移動を止めるかの判定の関数は、描き直しを待たずに ref から最新の値を読み、副作用を持たない（判定が1回の移動で何回
//   呼ばれても、旗を使い切らない）。
// - markClean(): 保存の成功の直後に呼ぶ。「変更なし」にし、beforeunload を外し、次の移動1回だけ確かめを飛ばす。
//   飛ばす旗を下ろすのは、(1) 止めなかった移動が成り立った後（useLocation の key の変化で見る。道が同じ移動を含む）と、
//   (2) markClean の後に入力の版の数が変わったとき（変更が生まれたら、また止める）、(3) markClean と同じ手順の中で移動が
//   始まらなかったとき（次のタスクで下ろす。data router の navigate は呼んだ手順の中で止めるかを判定するため、保存の成功の
//   直後の移動は (3) より先に判定される。移動が拒まれた・呼ばれなかったときに旗が残り、後の離れる操作が確かめ無しで通るのを
//   防ぐ。Intent 261016-business-data-views の U3 のコードのレビューの R-05）。版の数が変わったら「変更なし」も解き、
//   beforeunload を付け直す。
// - 骨組みのログアウトは道を変える前にトークンを捨てるため、確かめが出ないことがある（7.2、README に書く）。
import { useCallback, useEffect, useLayoutEffect, useRef, useState } from 'react'
import { useBlocker, useLocation, type BlockerFunction } from 'react-router'

/** 未保存の確かめの状態と口 */
export interface UnsavedGuard {
  /** 移動を止めていて、確かめを出すときか */
  blocked: boolean
  /** ［留まる］ */
  stay: () => void
  /** ［移る］（変更を捨ててから移る） */
  leave: () => void
  /** 「変更なし」にし、beforeunload を外し、次の移動1回だけ確かめを飛ばす */
  markClean: () => void
  /** 次の移動の確かめを飛ばす旗が立っているか（読むだけ。旗の寿命の確かめに使う） */
  skipsNextNavigation: () => boolean
}

/** 未保存の確かめの任意の設定 */
export interface UnsavedGuardOptions {
  /**
   * 入力の版の数（入力を変えるたびに増やす）。markClean の後に版が変わったら、また変更ありとして止める。
   * 渡さなければ 0 のまま（roleadmin）。
   */
  version?: number
}

/** 画面を閉じる・読み込み直すときのブラウザの確かめ */
function preventUnload(event: BeforeUnloadEvent): void {
  event.preventDefault()
  // 古いブラウザは returnValue に値があるときだけ確かめを出す。
  event.returnValue = ''
}

/** 移動を止めるかの判定（副作用なし。旗は読むだけ） */
export function shouldBlockNavigation(
  dirty: boolean,
  skipOnce: boolean,
  currentPathname: string,
  nextPathname: string,
): boolean {
  return !skipOnce && dirty && currentPathname !== nextPathname
}

/**
 * 未保存の確かめを当てる。
 *
 * @param dirty 変更があるか
 * @param discard 変更を捨てる（［移る］のとき移る前に呼ぶ）
 * @param options 入力の版の数
 */
export function useUnsavedGuard(
  dirty: boolean,
  discard: () => void,
  options: UnsavedGuardOptions = {},
): UnsavedGuard {
  const version = options.version ?? 0
  const location = useLocation()
  // markClean を呼んだときの版の数（null は呼んでいない）。同じ版の間は「変更なし」とみなす。
  const [cleanVersion, setCleanVersion] = useState<number | null>(null)
  const effectiveDirty = dirty && !(cleanVersion !== null && cleanVersion === version)
  const dirtyRef = useRef(effectiveDirty)
  const skipOnceRef = useRef(false)
  const skipLocationKeyRef = useRef<string | null>(null)
  const versionRef = useRef(version)
  const expiryTimerRef = useRef<number | null>(null)

  useLayoutEffect(() => {
    dirtyRef.current = effectiveDirty
    versionRef.current = version
  })

  const shouldBlock = useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) =>
      shouldBlockNavigation(
        dirtyRef.current,
        skipOnceRef.current,
        currentLocation.pathname,
        nextLocation.pathname,
      ),
    [],
  )
  const blocker = useBlocker(shouldBlock)

  // (1) 止めなかった移動が成り立ったら、飛ばす旗を下ろす。
  useEffect(() => {
    if (skipOnceRef.current && skipLocationKeyRef.current !== location.key) {
      skipOnceRef.current = false
      skipLocationKeyRef.current = null
    }
  }, [location.key])

  // (2) markClean の後に版の数が変わったら、旗を下ろす（「変更なし」は版の数の比べで描くたびに解ける）。
  useEffect(() => {
    if (skipOnceRef.current && cleanVersion !== null && cleanVersion !== version) {
      skipOnceRef.current = false
      skipLocationKeyRef.current = null
    }
  }, [cleanVersion, version])

  // (3) の時計を、画面が外れたら止める。
  useEffect(
    () => () => {
      if (expiryTimerRef.current !== null) {
        window.clearTimeout(expiryTimerRef.current)
        expiryTimerRef.current = null
      }
    },
    [],
  )

  useEffect(() => {
    if (!effectiveDirty) {
      return
    }
    window.addEventListener('beforeunload', preventUnload)
    return () => {
      window.removeEventListener('beforeunload', preventUnload)
    }
  }, [effectiveDirty])

  const markClean = useCallback((): void => {
    // 描き直しを待たずに判定へ効かせるため、ref を先に書き換える。
    dirtyRef.current = false
    skipOnceRef.current = true
    skipLocationKeyRef.current = location.key
    window.removeEventListener('beforeunload', preventUnload)
    setCleanVersion(versionRef.current)
    // (3) 同じ手順の中で移動が始まらなければ、次のタスクで旗を下ろす。
    if (expiryTimerRef.current !== null) {
      window.clearTimeout(expiryTimerRef.current)
    }
    expiryTimerRef.current = window.setTimeout(() => {
      expiryTimerRef.current = null
      skipOnceRef.current = false
      skipLocationKeyRef.current = null
    }, 0)
  }, [location.key])

  const skipsNextNavigation = useCallback((): boolean => skipOnceRef.current, [])

  const stay = useCallback((): void => {
    if (blocker.state === 'blocked') {
      blocker.reset()
    }
  }, [blocker])

  const leave = useCallback((): void => {
    if (blocker.state === 'blocked') {
      discard()
      blocker.proceed()
    }
  }, [blocker, discard])

  return { blocked: blocker.state === 'blocked', stay, leave, markClean, skipsNextNavigation }
}
