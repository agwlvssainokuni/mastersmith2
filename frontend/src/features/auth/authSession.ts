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
// ログイン状態とアクセストークンの保持（WF6、WF7、WF9、ST3、BR8.3、BR8.4、BR8.6）。
// アクセストークンと利用者はモジュールの中の変数（メモリ）だけに持ち、localStorage・sessionStorage・Cookie には置かない。
import { registerAuthHandlers } from '../../shared/api-client/apiClient'
import {
  requestLogin,
  requestLogout,
  requestRefresh,
  type CurrentUser,
  type TokenResult,
} from './authApi'

/** ログイン状態（ST3） */
export type AuthStatus = 'Restoring' | 'LoggedIn' | 'LoggedOut'

/** 画面が見るログイン状態 */
export interface AuthSnapshot {
  status: AuthStatus
  user: CurrentUser | null
}

interface SessionState {
  status: AuthStatus
  accessToken: string | null
  user: CurrentUser | null
}

const listeners = new Set<() => void>()

let state: SessionState = { status: 'Restoring', accessToken: null, user: null }

let restoring: Promise<void> | null = null

function setState(next: SessionState): void {
  state = next
  listeners.forEach((listener) => listener())
}

function applyTokens(result: TokenResult): void {
  setState({ status: 'LoggedIn', accessToken: result.accessToken, user: result.user })
}

function clear(): void {
  setState({ status: 'LoggedOut', accessToken: null, user: null })
}

/** 今のログイン状態を返す。 */
export function getAuthSnapshot(): AuthSnapshot {
  return { status: state.status, user: state.user }
}

/** 今のアクセストークンを返す（ApiClient が使う）。 */
export function getAccessToken(): string | null {
  return state.accessToken
}

/** ログイン状態の変化を受け取る。戻り値は受け取りをやめる関数。 */
export function subscribe(listener: () => void): () => void {
  listeners.add(listener)
  return () => {
    listeners.delete(listener)
  }
}

/** 画面を開いたときに、トークンの更新を1回だけ試みる（BR8.4）。 */
export function restore(): Promise<void> {
  restoring ??= refresh().then(() => undefined)
  return restoring
}

/** 復元が終わるのを待つ（まだ始めていなければ始める）。 */
export async function whenRestored(): Promise<AuthSnapshot> {
  await restore()
  return getAuthSnapshot()
}

/** トークンの更新を1回行う。成功したら true（ApiClient が使う）。 */
export async function refresh(): Promise<boolean> {
  try {
    applyTokens(await requestRefresh())
    return true
  } catch {
    clear()
    return false
  }
}

/** メールアドレスとパスワードでログインする。失敗は ApiError を投げる。 */
export async function login(email: string, password: string): Promise<void> {
  const result = await requestLogin(email, password)
  restoring ??= Promise.resolve()
  applyTokens(result)
}

/** ログアウトする。API の結果によらず、画面側のトークンとログイン状態を消す（BR8.6）。 */
export async function logout(): Promise<void> {
  try {
    await requestLogout()
  } catch {
    // API の失敗（通信エラーを含む）でも、画面側の破棄は必ず行う。
  } finally {
    restoring ??= Promise.resolve()
    clear()
  }
}

/** 画面の起動時に、ApiClient へトークンの取得と更新の手段を登録する。 */
export function initializeAuthSession(): void {
  registerAuthHandlers({
    getAccessToken,
    refresh,
    onUnauthenticated: clear,
  })
}

/** 状態を初めに戻す（テストで使う）。 */
export function resetAuthSession(): void {
  listeners.clear()
  restoring = null
  state = { status: 'Restoring', accessToken: null, user: null }
}
