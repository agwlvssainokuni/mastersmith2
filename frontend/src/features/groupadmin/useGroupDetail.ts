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
// S6 グループの詳細の状態と操作（Intent 261004-role-menu の U6、functional-spec.md の 3.4 W6.2〜W6.4・4.2・4.4・5節、
// D7・D17・D20・D21・D32、frontend-components.md の 4節、L4・L5、AC2.1.8〜AC2.1.10）。
// - グループと割り当てたロールを読む。GROUP_NOT_FOUND・道の ID が正の整数でないときは missing（D32 と同じ形）。
//   割り当てたロールが読めないときは、メンバーの表は出し、ロールの欄に読めなかったことを出す。
// - 名前を変える（GroupNameDialog）。メンバーを足す（候補の Modal、閉じ終わった後に読み直す）。メンバーを外す（確かめなし、
//   成功したらその利用者のロールを読み、このグループのロールのうち無くなったものを知らせに出す。D20）。
// - 失敗は code と状態だけで文言を選ぶ。403 は骨組みに渡す（D2）。答えは latestLoad の世代で見分ける。
import { useCallback, useEffect, useRef, useState } from 'react'
import { runAfterModalClosed } from '../../shared/modal/afterModalClosed'
import { groupPath, userRolesPath, type GroupAdminApi } from './api/groupAdminApi'
import type { GroupDetail, GroupRoleRef, Member } from './api/types'
import type { AddResult, Candidate } from './CandidateDialog'
import { failureMessageKey, knownCode } from './failureMessage'
import { createLatestLoad } from './latestLoad'
import { removedRoles } from './removedRoles'
import type { GroupAdminText } from './useGroupAdminText'
import { useSlowNotice } from '../../shared/slow-notice/useSlowNotice'

/** 詳細の読み込みの状態 */
export type GroupDetailPhase = 'loading' | 'ready' | 'missing' | 'load-error'

/** 割り当てたロールの読み込み */
export type GroupRolesState = { phase: 'loaded'; items: GroupRoleRef[] } | { phase: 'failed' }

/** 画面の知らせ */
export interface GroupDetailNotice {
  variant: 'success' | 'warning'
  key: string
  values?: Readonly<Record<string, string | number>>
}

/** 道の groupId を正の整数に読む（読めなければ undefined）。 */
export function parseGroupId(value: string | undefined): number | undefined {
  if (value === undefined || !/^[1-9][0-9]*$/.test(value)) {
    return undefined
  }
  const id = Number(value)
  return Number.isSafeInteger(id) ? id : undefined
}

function statusOf(error: unknown): number | undefined {
  const value = error as { kind?: unknown; status?: unknown } | null | undefined
  return typeof value === 'object' && value !== null && value.kind === 'response'
    ? (value.status as number | undefined)
    : undefined
}

/** フックが受ける口 */
export interface UseGroupDetailOptions {
  api: GroupAdminApi
  groupId: number | undefined
  t: GroupAdminText
  reportForbidden: (error: unknown, apiPath: string) => boolean
}

/** S6 の詳細の状態と操作 */
export function useGroupDetail({ api, groupId, t, reportForbidden }: UseGroupDetailOptions) {
  const slowNotice = useSlowNotice()
  const [phase, setPhase] = useState<GroupDetailPhase>(
    groupId === undefined ? 'missing' : 'loading',
  )
  const [detail, setDetail] = useState<GroupDetail | null>(null)
  const [roles, setRoles] = useState<GroupRolesState>({ phase: 'loaded', items: [] })
  const [notice, setNotice] = useState<GroupDetailNotice | null>(null)
  const [renameOpen, setRenameOpen] = useState(false)
  const [nameProblem, setNameProblem] = useState<string | null>(null)
  const [dialogMessage, setDialogMessage] = useState<string | null>(null)
  const [pickerOpen, setPickerOpen] = useState(false)
  const [removingId, setRemovingId] = useState<number | null>(null)

  const mounted = useRef(true)
  const latest = useRef(createLatestLoad())
  const forbidden = useRef(reportForbidden)
  const text = useRef(t)
  const cancelAfterClose = useRef<(() => void) | null>(null)
  const rolesRef = useRef<GroupRoleRef[]>([])

  useEffect(() => {
    forbidden.current = reportForbidden
    text.current = t
  }, [reportForbidden, t])

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

  /** 401・403・GROUP_NOT_FOUND を扱ったら true。 */
  const handledCommonly = useCallback((error: unknown, apiPath: string): boolean => {
    if (statusOf(error) === 401) {
      setRenameOpen(false)
      setPickerOpen(false)
      return true
    }
    if (forbidden.current(error, apiPath)) {
      return true
    }
    if (knownCode(error) === 'GROUP_NOT_FOUND') {
      latest.current.invalidate()
      setRenameOpen(false)
      setPickerOpen(false)
      setPhase('missing')
      return true
    }
    return false
  }, [])

  const load = useCallback(async (): Promise<void> => {
    if (groupId === undefined) {
      setPhase('missing')
      return
    }
    const generation = latest.current.begin()
    const isLatest = () => mounted.current && latest.current.isLatest(generation)
    const [detailResult, rolesResult] = await Promise.allSettled([
      api.getGroup(groupId),
      api.getGroupRoles(groupId),
    ])
    if (!isLatest()) {
      return
    }
    if (detailResult.status === 'rejected') {
      if (!handledCommonly(detailResult.reason, groupPath(groupId))) {
        setPhase((current) => (current === 'ready' ? 'ready' : 'load-error'))
      }
      return
    }
    setDetail(detailResult.value)
    if (rolesResult.status === 'fulfilled') {
      rolesRef.current = rolesResult.value
      setRoles({ phase: 'loaded', items: rolesResult.value })
    } else if (!handledCommonly(rolesResult.reason, groupPath(groupId, '/roles'))) {
      setRoles({ phase: 'failed' })
    }
    setPhase('ready')
  }, [api, groupId, handledCommonly])

  // 開いたら詳細を読む。状態の更新を効果の中で同期に呼ばないよう、マイクロタスクで始める（後始末で取りやめる）。
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

  function openRename(): void {
    if (slowNotice.submitting) {
      return
    }
    setNotice(null)
    setNameProblem(null)
    setDialogMessage(null)
    setRenameOpen(true)
  }

  function closeRename(): void {
    if (!slowNotice.submitting) {
      setRenameOpen(false)
    }
  }

  function changeName(): void {
    setNameProblem(null)
    setDialogMessage(null)
  }

  /** 名前を変える（W6.1 の W3.4 と同じ形）。 */
  function submitName(name: string): void {
    if (groupId === undefined) {
      return
    }
    const sent = slowNotice.run(() => api.renameGroup(groupId, name))
    if (sent === undefined) {
      return
    }
    setNameProblem(null)
    setDialogMessage(null)
    sent.then(
      () => {
        if (!mounted.current) {
          return
        }
        setRenameOpen(false)
        setNotice({ variant: 'success', key: 'groupadmin.result.renamed', values: { name } })
        reloadAfterDialogClosed()
      },
      (error: unknown) => {
        if (!mounted.current || handledCommonly(error, groupPath(groupId))) {
          return
        }
        const code = knownCode(error)
        const key = failureMessageKey(error, 'name')
        if (
          code === 'VALIDATION_FAILED' ||
          code === 'GROUP_NAME_DUPLICATE' ||
          code === 'GROUP_NO_CHANGE'
        ) {
          setNameProblem(key)
        } else {
          setDialogMessage(key)
        }
      },
    )
  }

  function openPicker(): void {
    if (slowNotice.submitting) {
      return
    }
    setNotice(null)
    setPickerOpen(true)
  }

  /** 候補の Modal を閉じた（閉じ終わった後に読み直す、W6.3 の 3）。 */
  function closePicker(): void {
    setPickerOpen(false)
    reloadAfterDialogClosed()
  }

  /** メンバーを1人足した結果（W6.3 の 2）。 */
  async function add(candidate: Candidate): Promise<AddResult> {
    if (groupId === undefined) {
      return { status: 'failed', messageKey: 'groupadmin.error.general' }
    }
    try {
      await api.addMember(groupId, candidate.id)
      return {
        status: 'added',
        messageKey: 'groupadmin.member.added',
        values: { name: candidate.name },
      }
    } catch (error: unknown) {
      if (mounted.current && handledCommonly(error, groupPath(groupId, '/members'))) {
        return { status: 'handled', messageKey: '' }
      }
      const code = knownCode(error)
      if (code === 'GROUP_NO_CHANGE') {
        return { status: 'taken', messageKey: 'groupadmin.member.already' }
      }
      if (code === 'USER_NOT_FOUND') {
        return { status: 'unavailable', messageKey: 'groupadmin.user.missing' }
      }
      return { status: 'failed', messageKey: failureMessageKey(error, 'addMember') }
    }
  }

  const searchUsers = useCallback(
    async (searchText: string): Promise<Candidate[]> => {
      const users = await api.searchUsers(searchText)
      return users.map((user) => ({
        id: user.userId,
        name: user.displayName,
        email: user.email,
        suspended: user.suspended,
      }))
    },
    [api],
  )

  /** 外した後の知らせ（D20）。その利用者のロールが読めなければ前の文だけ。 */
  async function removalNotice(member: Member, groupName: string): Promise<GroupDetailNotice> {
    const values = { user: member.displayName, group: groupName }
    try {
      const after = await api.getUserRoles(member.userId)
      const lost = removedRoles(rolesRef.current, after)
      if (lost.length > 0) {
        return {
          variant: 'success',
          key: 'groupadmin.member.removedRoles',
          values: {
            ...values,
            roles: lost.map((role) => role.name).join(text.current('groupadmin.roles.separator')),
          },
        }
      }
    } catch (error: unknown) {
      // 読めなくても外した結果は変わらないため、前の文だけを出す（403 は骨組みに渡す）。
      forbidden.current(error, userRolesPath(member.userId))
    }
    return { variant: 'success', key: 'groupadmin.member.removed', values }
  }

  /** メンバーを外す（W6.4、確かめなし）。 */
  function removeMember(member: Member): void {
    if (groupId === undefined || detail === null) {
      return
    }
    const groupName = detail.name
    const sent = slowNotice.run(() => api.removeMember(groupId, member.userId))
    if (sent === undefined) {
      return
    }
    setRemovingId(member.userId)
    setNotice(null)
    sent.then(
      async () => {
        const next = await removalNotice(member, groupName)
        if (!mounted.current) {
          return
        }
        setRemovingId(null)
        setNotice(next)
        void load()
      },
      (error: unknown) => {
        if (!mounted.current) {
          return
        }
        setRemovingId(null)
        if (handledCommonly(error, groupPath(groupId, `/members/${member.userId}`))) {
          return
        }
        setNotice({ variant: 'warning', key: failureMessageKey(error, 'removeMember') })
        if (knownCode(error) === 'GROUP_NO_CHANGE') {
          void load()
        }
      },
    )
  }

  function dismissNotice(): void {
    setNotice(null)
  }

  return {
    phase,
    detail,
    roles,
    notice,
    renameOpen,
    nameProblem,
    dialogMessage,
    pickerOpen,
    removingId,
    submitting: slowNotice.submitting,
    slow: slowNotice.slow,
    retry,
    openRename,
    closeRename,
    changeName,
    submitName,
    openPicker,
    closePicker,
    add,
    searchUsers,
    removeMember,
    dismissNotice,
  }
}
