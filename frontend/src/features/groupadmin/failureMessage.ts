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
// グループの管理の画面の失敗の文言の鍵を選ぶ純粋な関数（Intent 261004-role-menu の U6 の D3・D4・D7・D9、
// functional-spec.md 5節、L3、NFR1.8）。応答の code と状態コードだけを見て、サーバーの detail・title は読まない。
// 知らない code・code の無い応答・通信の失敗は汎用の文言にする。403 ACCESS_DENIED は画面の側が先に扱う。
// GROUP_IN_USE の数は Problem Details の追加の項目 members・assignedRoles を整数のときだけ読む（D9）。
import type { ApiError } from '../../shared/api-client/apiError'

/** 画面が扱う code */
export const GROUP_ADMIN_ERROR_CODES = [
  'VALIDATION_FAILED',
  'GROUP_NOT_FOUND',
  'USER_NOT_FOUND',
  'GROUP_NAME_DUPLICATE',
  'GROUP_NO_CHANGE',
  'GROUP_IN_USE',
  'GROUP_BUSY',
] as const

/** 画面が扱う code */
export type GroupAdminErrorCode = (typeof GROUP_ADMIN_ERROR_CODES)[number]

/** 失敗の場面（GROUP_NO_CHANGE・VALIDATION_FAILED の文言が場面で変わる） */
export type GroupAdminScene = 'name' | 'delete' | 'addMember' | 'removeMember'

const KNOWN_CODES: ReadonlySet<string> = new Set(GROUP_ADMIN_ERROR_CODES)

/** 汎用の失敗の文言の鍵（D3） */
export const GENERAL_FAILURE_KEY = 'groupadmin.error.general'

/** 失敗の code（画面が扱うものだけ。ほかは undefined） */
export function knownCode(error: unknown): GroupAdminErrorCode | undefined {
  const apiError = error as ApiError | undefined
  if (
    typeof apiError === 'object' &&
    apiError !== null &&
    apiError.kind === 'response' &&
    typeof apiError.code === 'string' &&
    KNOWN_CODES.has(apiError.code)
  ) {
    return apiError.code as GroupAdminErrorCode
  }
  return undefined
}

const NO_CHANGE_KEYS: Readonly<Record<GroupAdminScene, string>> = {
  name: 'groupadmin.name.noChange',
  delete: GENERAL_FAILURE_KEY,
  addMember: 'groupadmin.member.already',
  removeMember: 'groupadmin.member.alreadyRemoved',
}

/** 失敗の文言の鍵を選ぶ（5節の表）。 */
export function failureMessageKey(error: unknown, scene: GroupAdminScene): string {
  switch (knownCode(error)) {
    case 'VALIDATION_FAILED':
      return scene === 'name' ? 'groupadmin.name.invalid' : GENERAL_FAILURE_KEY
    case 'GROUP_NAME_DUPLICATE':
      return 'groupadmin.name.duplicate'
    case 'GROUP_NO_CHANGE':
      return NO_CHANGE_KEYS[scene]
    case 'GROUP_NOT_FOUND':
      return 'groupadmin.group.missing'
    case 'USER_NOT_FOUND':
      return 'groupadmin.user.missing'
    case 'GROUP_IN_USE':
      return 'groupadmin.inUse.noCount'
    case 'GROUP_BUSY':
      return 'groupadmin.busy'
    case undefined:
      return GENERAL_FAILURE_KEY
  }
}

/** GROUP_IN_USE の数（members・assignedRoles の両方が 0 以上の整数のときだけ）。 */
export function inUseCounts(error: unknown): { members: number; roles: number } | undefined {
  if (knownCode(error) !== 'GROUP_IN_USE') {
    return undefined
  }
  const problem = (error as { problem?: Readonly<Record<string, unknown>> }).problem
  const members = problem?.members
  const roles = problem?.assignedRoles
  if (
    typeof members === 'number' &&
    Number.isInteger(members) &&
    members >= 0 &&
    typeof roles === 'number' &&
    Number.isInteger(roles) &&
    roles >= 0
  ) {
    return { members, roles }
  }
  return undefined
}
