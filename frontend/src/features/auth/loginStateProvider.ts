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
// U1 の差し込み口「ログイン状態の提供元」（BR8.7）。最初の復元の結果を待って返し、状態の変化を知らせる。
// ログイン中は、契約 C3 の氏名と表示の設定をログイン状態に通す（表示の設定の土台が使う。U4 の部品 5節）。
import type { LoginState, LoginStateProvider } from '../../app/registry/types'
import { getAuthSnapshot, subscribe, whenRestored } from './authSession'

/** 今の状態を U1 の形にする。 */
export function toLoginState(): LoginState {
  const snapshot = getAuthSnapshot()
  if (snapshot.status !== 'LoggedIn' || snapshot.user === null) {
    return { loggedIn: false, admin: false }
  }
  const { admin, displayName, language, theme, fontSize } = snapshot.user
  // ユーザーメニューには氏名を出す（メールアドレスではない。ストーリーの M7）。
  return { loggedIn: true, admin, displayName, preferences: { language, theme, fontSize } }
}

/** U1 に登録するログイン状態の提供元。 */
export const loginStateProvider: LoginStateProvider = {
  getLoginState: async () => {
    await whenRestored()
    return toLoginState()
  },
  subscribe,
}
