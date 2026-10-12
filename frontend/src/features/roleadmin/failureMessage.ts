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
// ロールの管理の画面の失敗の文言の鍵を選ぶ純粋な関数（Intent 261004-role-menu の U6 の D3・D4・D7・D9・D14、
// functional-spec.md 5節、L3、NFR1.8）。応答の code と状態コードだけを見て、サーバーの detail・title は読まない。
// 知らない code・code の無い応答・通信の失敗は汎用の文言にする。403 ACCESS_DENIED は画面の側（useAdminForbidden）が先に
// 扱う。ROLE_IN_USE の数は Problem Details の追加の項目 assignedUsers・assignedGroups を整数のときだけ読む（D9）。
import type { ApiError } from '../../shared/api-client/apiError'

/** 画面が扱う code */
export const ROLE_ADMIN_ERROR_CODES = [
  'VALIDATION_FAILED',
  'ROLE_NOT_FOUND',
  'USER_NOT_FOUND',
  'GROUP_NOT_FOUND',
  'ROLE_NAME_DUPLICATE',
  'ROLE_NO_CHANGE',
  'ROLE_IN_USE',
  'ROLE_BUSY',
  'GROUP_BUSY',
  'PERMISSION_TARGET_NOT_IN_DSL',
  'DSL_NOT_APPLIED',
] as const

/** 画面が扱う code */
export type RoleAdminErrorCode = (typeof ROLE_ADMIN_ERROR_CODES)[number]

/** 失敗の場面（ROLE_NO_CHANGE・VALIDATION_FAILED の文言が場面で変わる） */
export type RoleAdminScene = 'name' | 'delete' | 'save' | 'clear' | 'assign' | 'unassign'

const KNOWN_CODES: ReadonlySet<string> = new Set(ROLE_ADMIN_ERROR_CODES)

/** 汎用の失敗の文言の鍵（D3） */
export const GENERAL_FAILURE_KEY = 'roleadmin.error.general'

/** 失敗の code（画面が扱うものだけ。ほかは undefined） */
export function knownCode(error: unknown): RoleAdminErrorCode | undefined {
  const apiError = error as ApiError | undefined
  if (
    typeof apiError === 'object' &&
    apiError !== null &&
    apiError.kind === 'response' &&
    typeof apiError.code === 'string' &&
    KNOWN_CODES.has(apiError.code)
  ) {
    return apiError.code as RoleAdminErrorCode
  }
  return undefined
}

const NO_CHANGE_KEYS: Readonly<Record<RoleAdminScene, string>> = {
  name: 'roleadmin.name.noChange',
  delete: GENERAL_FAILURE_KEY,
  save: 'roleadmin.save.noChange',
  clear: 'roleadmin.clear.noChange',
  assign: 'roleadmin.assign.already',
  unassign: 'roleadmin.unassign.already',
}

/** 失敗の文言の鍵を選ぶ（5節の表）。 */
export function failureMessageKey(error: unknown, scene: RoleAdminScene): string {
  switch (knownCode(error)) {
    case 'VALIDATION_FAILED':
      return scene === 'name' ? 'roleadmin.name.invalid' : GENERAL_FAILURE_KEY
    case 'ROLE_NAME_DUPLICATE':
      return 'roleadmin.name.duplicate'
    case 'ROLE_NO_CHANGE':
      return NO_CHANGE_KEYS[scene]
    case 'ROLE_NOT_FOUND':
      return 'roleadmin.role.missing'
    case 'USER_NOT_FOUND':
      return 'roleadmin.user.missing'
    case 'GROUP_NOT_FOUND':
      return 'roleadmin.group.missing'
    case 'ROLE_IN_USE':
      return 'roleadmin.inUse.noCount'
    case 'ROLE_BUSY':
    case 'GROUP_BUSY':
      return 'roleadmin.busy'
    case 'PERMISSION_TARGET_NOT_IN_DSL':
      return 'roleadmin.save.notInDsl'
    case 'DSL_NOT_APPLIED':
      return 'roleadmin.noDsl'
    case undefined:
      return GENERAL_FAILURE_KEY
  }
}

/** ROLE_IN_USE の数（assignedUsers・assignedGroups の両方が 0 以上の整数のときだけ）。 */
export function inUseCounts(error: unknown): { users: number; groups: number } | undefined {
  if (knownCode(error) !== 'ROLE_IN_USE') {
    return undefined
  }
  const problem = (error as { problem?: Readonly<Record<string, unknown>> }).problem
  const users = problem?.assignedUsers
  const groups = problem?.assignedGroups
  if (
    typeof users === 'number' &&
    Number.isInteger(users) &&
    users >= 0 &&
    typeof groups === 'number' &&
    Number.isInteger(groups) &&
    groups >= 0
  ) {
    return { users, groups }
  }
  return undefined
}
