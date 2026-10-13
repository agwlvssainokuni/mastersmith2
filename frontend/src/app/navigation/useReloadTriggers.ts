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
// 作業ロールと業務のメニューの読み直しのきっかけ（Intent 261004-role-menu の U7 の D4、L4、NFR2.2・NFR2.9）。
// - (a) ログイン状態の知らせ（ログイン・トークンの更新・ログアウト。LoginStateGate が知らせのたびに新しい値を渡す）、
//   (d) 画面の道（pathname と問い合わせの引数）の変化、と呼ぶ側が渡すほかのきっかけ（extra。作業ロールの切り替えの回数
//   など）を1つの効果にまとめる。同じ描画の中で重なったきっかけは効果の1回の実行になる（読み直しは1回）。
// - 未ログインのときと、ログアウトの画面（`/logout`）の道では呼ばない（security-design.md 5.2）。
import { useEffect, useEffectEvent } from 'react'
import { useLocation } from 'react-router'
import { useLoginState } from '../login-state/LoginStateGate'

/** ログアウトの画面の道（読み直さない） */
export const LOGOUT_PATH = '/logout'

/**
 * 読み直しのきっかけごとに onTrigger を1回呼ぶ。
 *
 * @param onTrigger 読み直す関数（最新のものを呼ぶ）
 * @param extra ほかのきっかけ（値が変わったら呼ぶ）
 */
export function useReloadTriggers(onTrigger: () => void, extra?: unknown): void {
  const loginState = useLoginState()
  const { pathname, search } = useLocation()
  const fire = useEffectEvent(onTrigger)

  useEffect(() => {
    if (!loginState.loggedIn || pathname === LOGOUT_PATH) {
      return
    }
    fire()
  }, [loginState, pathname, search, extra])
}
