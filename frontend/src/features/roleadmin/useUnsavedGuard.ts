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
// - 写しが空でない間、道が変わる移動（タブ・「← ロールの一覧」・サイドバー・ユーザーメニュー・ブラウザの戻る）を
//   data router の useBlocker で止める。止めたら呼ぶ側が LeaveConfirmDialog を出す。［留まる］は reset、
//   ［移る］は写しを捨てる関数を呼んでから proceed する。道が同じ移動（問い合わせだけの違いを含まない）は止めない。
// - 写しが空でない間は beforeunload を張り、読み込み直し・タブを閉じるときにブラウザの確かめを出す（文言はブラウザが
//   決める）。写しが空になったら外す。
// - 骨組みのログアウトは道を変える前にトークンを捨てるため、確かめが出ないことがある（7.2、README に書く）。
import { useCallback, useEffect } from 'react'
import { useBlocker, type BlockerFunction } from 'react-router'

/** 未保存の確かめの状態と口 */
export interface UnsavedGuard {
  /** 移動を止めていて、確かめを出すときか */
  blocked: boolean
  /** ［留まる］ */
  stay: () => void
  /** ［移る］（写しを捨ててから移る） */
  leave: () => void
}

/** 画面を閉じる・読み込み直すときのブラウザの確かめ */
function preventUnload(event: BeforeUnloadEvent): void {
  event.preventDefault()
  // 古いブラウザは returnValue に値があるときだけ確かめを出す。
  event.returnValue = ''
}

/**
 * 未保存の確かめを当てる。
 *
 * @param dirty 写しが空でないか
 * @param discard 写しを捨てる（［移る］のとき移る前に呼ぶ）
 */
export function useUnsavedGuard(dirty: boolean, discard: () => void): UnsavedGuard {
  const shouldBlock = useCallback<BlockerFunction>(
    ({ currentLocation, nextLocation }) =>
      dirty && currentLocation.pathname !== nextLocation.pathname,
    [dirty],
  )
  const blocker = useBlocker(shouldBlock)

  useEffect(() => {
    if (!dirty) {
      return
    }
    window.addEventListener('beforeunload', preventUnload)
    return () => {
      window.removeEventListener('beforeunload', preventUnload)
    }
  }, [dirty])

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

  return { blocked: blocker.state === 'blocked', stay, leave }
}
