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
// S5 割り当ての状態と操作（Intent 261004-role-menu の U6、functional-spec.md の 3.3 W5.1〜W5.5・4.4・5節、D16〜D19、
// frontend-components.md の 2.2、L4・L5、AC2.2.2・AC2.2.7〜AC2.2.9・AC2.2.15・AC2.2.16）。
// - 割り当ての読み込みは latestLoad の世代で見分け、画面を離れた後の答えは捨てる。
// - 候補の Modal を閉じたら、閉じ終わった後に割り当てを読み直す（開いたボタンへのフォーカスの戻しの後）。
// - 利用者の直接の割り当てを外すとき（Should の D19）、その利用者のロールを読み、出どころが直接だけなら確かめを出す。
//   読めなければ条件だけの文言で確かめる。グループ経由で残るなら確かめずに外す。
// - 拒否は code と状態だけで文言を選ぶ。403 は骨組みに渡し、ROLE_NOT_FOUND は onMissing（D32）。
import { useCallback, useEffect, useRef, useState } from 'react'
import { runAfterModalClosed } from '../../shared/modal/afterModalClosed'
import { rolePath, userRolesPath, type RoleAdminApi } from './api/roleAdminApi'
import type { AssignedGroup, AssignedUser, RoleAssignments } from './api/types'
import type { AddResult, Candidate, CandidatePage } from './CandidateDialog'
import { failureMessageKey, knownCode } from './failureMessage'
import { createLatestLoad } from './latestLoad'
import { removalImpact } from './removalImpact'
import { useSlowNotice } from '../../shared/slow-notice/useSlowNotice'

/** 割り当ての読み込みの状態 */
export type AssignmentsPhase = 'loading' | 'ready' | 'load-error'

/** 画面の知らせ */
export interface AssignmentNotice {
  variant: 'success' | 'warning'
  key: string
  values?: Readonly<Record<string, string | number>>
}

/** 外す確かめ（D19） */
export interface RemovalConfirm {
  user: AssignedUser
  key: string
  values: Readonly<Record<string, string | number>>
}

/** フックが受ける口 */
export interface UseRoleAssignmentsOptions {
  api: RoleAdminApi
  roleId: number
  roleName: string
  reportForbidden: (error: unknown, apiPath: string) => boolean
  onMissing: () => void
}

function statusOf(error: unknown): number | undefined {
  const value = error as { kind?: unknown; status?: unknown } | null | undefined
  return typeof value === 'object' && value !== null && value.kind === 'response'
    ? (value.status as number | undefined)
    : undefined
}

/** S5 の状態と操作 */
export function useRoleAssignments({
  api,
  roleId,
  roleName,
  reportForbidden,
  onMissing,
}: UseRoleAssignmentsOptions) {
  const slowNotice = useSlowNotice()
  const [phase, setPhase] = useState<AssignmentsPhase>('loading')
  const [assignments, setAssignments] = useState<RoleAssignments>({ users: [], groups: [] })
  const [picker, setPicker] = useState<'none' | 'user' | 'group'>('none')
  const [confirm, setConfirm] = useState<RemovalConfirm | null>(null)
  const [notice, setNotice] = useState<AssignmentNotice | null>(null)
  const [removingKey, setRemovingKey] = useState<string | null>(null)

  const mounted = useRef(true)
  const latest = useRef(createLatestLoad())
  const forbidden = useRef(reportForbidden)
  const missing = useRef(onMissing)
  const cancelAfterClose = useRef<(() => void) | null>(null)

  useEffect(() => {
    forbidden.current = reportForbidden
    missing.current = onMissing
  }, [reportForbidden, onMissing])

  useEffect(() => {
    const load = latest.current
    mounted.current = true
    return () => {
      mounted.current = false
      load.invalidate()
      cancelAfterClose.current?.()
      cancelAfterClose.current = null
    }
  }, [])

  /** 401・403・ROLE_NOT_FOUND を扱ったら true。 */
  const handledCommonly = useCallback((error: unknown, apiPath: string): boolean => {
    if (statusOf(error) === 401) {
      setPicker('none')
      setConfirm(null)
      return true
    }
    if (forbidden.current(error, apiPath)) {
      return true
    }
    if (knownCode(error) === 'ROLE_NOT_FOUND') {
      setPicker('none')
      setConfirm(null)
      missing.current()
      return true
    }
    return false
  }, [])

  const load = useCallback(async (): Promise<void> => {
    const generation = latest.current.begin()
    try {
      const result = await api.getAssignments(roleId)
      if (mounted.current && latest.current.isLatest(generation)) {
        setAssignments(result)
        setPhase('ready')
      }
    } catch (error: unknown) {
      if (!mounted.current || !latest.current.isLatest(generation)) {
        return
      }
      if (!handledCommonly(error, rolePath(roleId, '/assignments'))) {
        setPhase((current) => (current === 'ready' ? 'ready' : 'load-error'))
      }
    }
  }, [api, roleId, handledCommonly])

  // 開いたら割り当てを読む。読み込みの状態の更新を効果の中で同期に呼ばないよう、マイクロタスクで始める（効果の後始末で
  // 取りやめる）。
  useEffect(() => {
    let active = true
    queueMicrotask(() => {
      if (active) {
        void load()
      }
    })
    return () => {
      active = false
    }
  }, [load])

  function reloadAfterDialogClosed(): void {
    cancelAfterClose.current?.()
    cancelAfterClose.current = runAfterModalClosed(() => {
      cancelAfterClose.current = null
      if (mounted.current) {
        void load()
      }
    })
  }

  function retry(): void {
    setPhase('loading')
    void load()
  }

  function openPicker(kind: 'user' | 'group'): void {
    if (slowNotice.submitting) {
      return
    }
    setNotice(null)
    setPicker(kind)
  }

  /** 候補の Modal を閉じた（閉じ終わった後に読み直す、W5.2 の 4）。 */
  function closePicker(): void {
    setPicker('none')
    reloadAfterDialogClosed()
  }

  /** 1件を足した結果（W5.2・W5.3）。 */
  async function add(kind: 'user' | 'group', candidate: Candidate): Promise<AddResult> {
    const apiPath = rolePath(roleId, '/assignments')
    try {
      if (kind === 'user') {
        await api.assignUser(roleId, candidate.id)
      } else {
        await api.assignGroup(roleId, candidate.id)
      }
      return {
        status: 'added',
        messageKey: kind === 'user' ? 'roleadmin.assign.userAdded' : 'roleadmin.assign.groupAdded',
        values: { name: candidate.name },
      }
    } catch (error: unknown) {
      if (mounted.current && handledCommonly(error, apiPath)) {
        return { status: 'handled', messageKey: '' }
      }
      const code = knownCode(error)
      if (code === 'ROLE_NO_CHANGE') {
        return { status: 'taken', messageKey: 'roleadmin.assign.already' }
      }
      if (code === 'USER_NOT_FOUND' || code === 'GROUP_NOT_FOUND') {
        return { status: 'unavailable', messageKey: failureMessageKey(error, 'assign') }
      }
      return { status: 'failed', messageKey: failureMessageKey(error, 'assign') }
    }
  }

  const searchUsers = useCallback(
    async (text: string): Promise<Candidate[]> => {
      const users = await api.searchUsers(text)
      return users.map((user) => ({
        id: user.userId,
        name: user.displayName,
        email: user.email,
        suspended: user.suspended,
      }))
    },
    [api],
  )

  const loadGroups = useCallback(
    async (page: number): Promise<CandidatePage> => {
      const result = await api.listGroups(page)
      return {
        items: result.items.map((group) => ({ id: group.groupId, name: group.name })),
        total: result.total,
      }
    },
    [api],
  )

  /** 外す（W5.4・W5.5）。 */
  function unassign(kind: 'user' | 'group', id: number, name: string): void {
    const key = `${kind}-${id}`
    const sent = slowNotice.run(() =>
      kind === 'user' ? api.unassignUser(roleId, id) : api.unassignGroup(roleId, id),
    )
    if (sent === undefined) {
      return
    }
    setRemovingKey(key)
    setNotice(null)
    const apiPath = rolePath(roleId, `/assignments/${kind === 'user' ? 'users' : 'groups'}/${id}`)
    sent.then(
      () => {
        if (!mounted.current) {
          return
        }
        setRemovingKey(null)
        setNotice({
          variant: 'success',
          key: kind === 'user' ? 'roleadmin.unassign.userDone' : 'roleadmin.unassign.groupDone',
          values:
            kind === 'user' ? { user: name, role: roleName } : { group: name, role: roleName },
        })
        void load()
      },
      (error: unknown) => {
        if (!mounted.current) {
          return
        }
        setRemovingKey(null)
        if (handledCommonly(error, apiPath)) {
          return
        }
        const code = knownCode(error)
        setNotice({ variant: 'warning', key: failureMessageKey(error, 'unassign') })
        if (code === 'ROLE_NO_CHANGE' || code === 'GROUP_NOT_FOUND') {
          void load()
        }
      },
    )
  }

  /** 利用者の「外す」（D19 の判定をして必要なら確かめる）。 */
  async function requestRemoveUser(user: AssignedUser): Promise<void> {
    if (slowNotice.submitting || removingKey !== null) {
      return
    }
    setNotice(null)
    let roles
    try {
      roles = await api.getUserRoles(user.userId)
    } catch (error: unknown) {
      if (!mounted.current || handledCommonly(error, userRolesPath(user.userId))) {
        return
      }
      setConfirm({ user, key: 'roleadmin.removal.unknown', values: { user: user.displayName } })
      return
    }
    if (!mounted.current) {
      return
    }
    const impact = removalImpact(roles, roleId)
    if (!impact.losesRole) {
      unassign('user', user.userId, user.displayName)
      return
    }
    setConfirm(
      impact.nextWorkRole !== null
        ? {
            user,
            key: 'roleadmin.removal.next',
            values: { user: user.displayName, next: impact.nextWorkRole.name },
          }
        : { user, key: 'roleadmin.removal.none', values: { user: user.displayName } },
    )
  }

  function confirmRemoval(): void {
    const current = confirm
    if (current === null) {
      return
    }
    setConfirm(null)
    unassign('user', current.user.userId, current.user.displayName)
  }

  function cancelRemoval(): void {
    setConfirm(null)
  }

  function removeGroup(group: AssignedGroup): void {
    if (removingKey !== null) {
      return
    }
    unassign('group', group.groupId, group.name)
  }

  function dismissNotice(): void {
    setNotice(null)
  }

  return {
    phase,
    assignments,
    picker,
    confirm,
    notice,
    removingKey,
    busy: slowNotice.submitting,
    slow: slowNotice.slow,
    retry,
    openPicker,
    closePicker,
    add,
    searchUsers,
    loadGroups,
    requestRemoveUser,
    confirmRemoval,
    cancelRemoval,
    removeGroup,
    dismissNotice,
  }
}

/** S5 の状態と操作の型 */
export type RoleAssignmentsState = ReturnType<typeof useRoleAssignments>
