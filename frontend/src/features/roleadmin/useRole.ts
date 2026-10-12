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
// S4・S5 の枠のロール1件の読み込み（Intent 261004-role-menu の U6 の D32、functional-spec.md の W4.1・W5.1、
// frontend-components.md の 2.2）。道の roleId が正の整数でなければ読まずに missing にする。404 ROLE_NOT_FOUND も
// missing。403 は骨組みに渡し、401 は ApiClient とログインの状態に任せる。答えは latestLoad の世代で見分け、画面を
// 離れた後の答えは捨てる。パネルが ROLE_NOT_FOUND を受けたときも markMissing で missing にする（D14）。
import { useCallback, useEffect, useRef, useState } from 'react'
import { rolePath, type RoleAdminApi } from './api/roleAdminApi'
import type { Role } from './api/types'
import { knownCode } from './failureMessage'
import { createLatestLoad } from './latestLoad'

/** ロールの読み込みの状態 */
export type RolePhase = 'loading' | 'ready' | 'missing' | 'load-error'

/** 道の roleId を正の整数に読む（読めなければ undefined）。 */
export function parseRoleId(value: string | undefined): number | undefined {
  if (value === undefined || !/^[1-9][0-9]*$/.test(value)) {
    return undefined
  }
  const id = Number(value)
  return Number.isSafeInteger(id) ? id : undefined
}

/** S4・S5 の枠のロール1件 */
export function useRole(
  api: RoleAdminApi,
  roleId: number | undefined,
  reportForbidden: (error: unknown, apiPath: string) => boolean,
) {
  const [phase, setPhase] = useState<RolePhase>(roleId === undefined ? 'missing' : 'loading')
  const [role, setRole] = useState<Role | null>(null)
  const latest = useRef(createLatestLoad())
  const forbidden = useRef(reportForbidden)

  useEffect(() => {
    forbidden.current = reportForbidden
  }, [reportForbidden])

  const load = useCallback(async (): Promise<void> => {
    if (roleId === undefined) {
      setPhase('missing')
      return
    }
    const generation = latest.current.begin()
    setPhase('loading')
    try {
      const result = await api.getRole(roleId)
      if (latest.current.isLatest(generation)) {
        setRole(result)
        setPhase('ready')
      }
    } catch (error: unknown) {
      if (!latest.current.isLatest(generation)) {
        return
      }
      if (knownCode(error) === 'ROLE_NOT_FOUND') {
        setPhase('missing')
        return
      }
      if (forbidden.current(error, rolePath(roleId))) {
        return
      }
      setPhase('load-error')
    }
  }, [api, roleId])

  // 開いたらロールを読む。読み込みの状態の更新を効果の中で同期に呼ばないよう、マイクロタスクで始める（効果の後始末で
  // 取りやめる）。
  useEffect(() => {
    const current = latest.current
    let active = true
    queueMicrotask(() => {
      if (active) {
        void load()
      }
    })
    return () => {
      active = false
      current.invalidate()
    }
  }, [load])

  const markMissing = useCallback((): void => {
    latest.current.invalidate()
    setPhase('missing')
  }, [])

  return { phase, role, retry: load, markMissing }
}
