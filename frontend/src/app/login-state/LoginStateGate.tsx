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
// ログイン状態を得て、画面の部品へ渡す（ST2、BR7.3）。骨組みはログイン状態を自分で持たず、提供元（U2）の値に従う。
import { createContext, useContext, useEffect, useState, type ReactNode } from 'react'
import type { LoginState, LoginStateProvider } from '../registry/types'

/** 未ログインの状態 */
export const LOGGED_OUT: LoginState = { loggedIn: false, admin: false }

const LoginStateContext = createContext<LoginState>(LOGGED_OUT)

/**
 * 提供元の値を整える。loggedIn が false なら admin は常に false で、氏名と表示の設定も落とす。
 * ログイン中なら氏名と表示の設定（preferences。あれば）を通す。
 */
export function normalizeLoginState(state: LoginState): LoginState {
  if (!state.loggedIn) {
    return LOGGED_OUT
  }
  const normalized: LoginState = {
    loggedIn: true,
    admin: state.admin,
    displayName: state.displayName,
  }
  if (state.preferences !== undefined) {
    normalized.preferences = state.preferences
  }
  return normalized
}

export interface LoginStateGateProps {
  /** ログイン状態の提供元（無ければ未ログインとして扱う） */
  provider?: LoginStateProvider
  children: ReactNode
}

/**
 * 提供元にログイン状態を問い合わせ、子の部品へ渡す。提供元が無ければ未ログイン、問い合わせに失敗したら未ログインとして扱う。
 * 提供元の最初の答えが出るまでは何も表示しない。
 */
export function LoginStateGate({ provider, children }: LoginStateGateProps) {
  const [state, setState] = useState<LoginState | null>(provider ? null : LOGGED_OUT)

  useEffect(() => {
    if (!provider) {
      return undefined
    }
    let active = true
    const refresh = () => {
      Promise.resolve()
        .then(() => provider.getLoginState())
        .then(
          (next) => {
            if (active) {
              setState(normalizeLoginState(next))
            }
          },
          () => {
            if (active) {
              setState(LOGGED_OUT)
            }
          },
        )
    }
    refresh()
    const unsubscribe = provider.subscribe?.(refresh)
    return () => {
      active = false
      unsubscribe?.()
    }
  }, [provider])

  if (state === null) {
    return null
  }
  return <LoginStateContext.Provider value={state}>{children}</LoginStateContext.Provider>
}

/** 現在のログイン状態を返す。 */
export function useLoginState(): LoginState {
  return useContext(LoginStateContext)
}
