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
// 骨組みの作業ロール・業務のメニューのテストの補助（テストからだけ使う。アプリの画面からは読み込まない）
// （Intent 261004-role-menu の U7、unit-test-instructions.md 6節）。
// - 状態を差し替えて知らせる偽のログイン状態の提供元、解決を手で決める約束、道を変えるボタン。
// - WorkRoleProvider・BusinessNavigationProvider に渡す偽の API（呼ばれた回数を数え、答えを順に返す）。
import { useNavigate } from 'react-router'
import type { WorkRoleState } from '../work-role/workRoleApi'
import type { NavigationResponse } from '../navigation/navigationApi'
import type { LoginState, LoginStateProvider } from '../registry/types'

/** 状態を差し替えて知らせる偽の提供元 */
export function switchableProvider(initial: LoginState): {
  provider: LoginStateProvider
  set: (next: LoginState) => void
  notify: () => void
} {
  let state = initial
  const listeners = new Set<() => void>()
  const notify = () => {
    for (const listener of listeners) {
      listener()
    }
  }
  return {
    provider: {
      getLoginState: () => state,
      subscribe: (listener) => {
        listeners.add(listener)
        return () => {
          listeners.delete(listener)
        }
      },
    },
    set: (next) => {
      state = next
      notify()
    },
    notify,
  }
}

/** 解決を手で決める約束 */
export interface Deferred<T> {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (reason: unknown) => void
}

export function deferred<T>(): Deferred<T> {
  let resolve!: (value: T) => void
  let reject!: (reason: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

/** 押すと道を変えるボタン */
export function NavigateButton({ to, label }: { to: string; label: string }) {
  const navigate = useNavigate()
  return (
    <button type="button" onClick={() => void navigate(to)}>
      {label}
    </button>
  )
}

/** 偽の作業ロールの API（答えは呼ばれるたびに先頭から使い、無くなったら最後の答えを繰り返す） */
export function fakeWorkRoleApi(
  answers: (WorkRoleState | Deferred<WorkRoleState> | Error)[],
  switchAnswer: () => Promise<void> = () => Promise.resolve(),
) {
  const calls = { get: 0, put: [] as number[] }
  let index = 0
  return {
    calls,
    api: {
      getWorkRole: (): Promise<WorkRoleState> => {
        calls.get += 1
        const answer = answers[Math.min(index, answers.length - 1)]
        index += 1
        if (answer instanceof Error) {
          return Promise.reject(answer)
        }
        return 'promise' in answer ? answer.promise : Promise.resolve(answer)
      },
      switchWorkRole: (roleId: number): Promise<void> => {
        calls.put.push(roleId)
        return switchAnswer()
      },
    },
  }
}

/** 偽の業務のメニューの読み込み（答えの使い方は fakeWorkRoleApi と同じ） */
export function fakeNavigationLoad(
  answers: (NavigationResponse | Deferred<NavigationResponse> | Error)[],
) {
  const calls = { count: 0 }
  let index = 0
  return {
    calls,
    load: (): Promise<NavigationResponse> => {
      calls.count += 1
      const answer = answers[Math.min(index, answers.length - 1)]
      index += 1
      if (answer instanceof Error) {
        return Promise.reject(answer)
      }
      return 'promise' in answer ? answer.promise : Promise.resolve(answer)
    },
  }
}

export const SALES = { roleId: 1, name: '営業' }
export const ACCOUNTING = { roleId: 2, name: '経理' }
