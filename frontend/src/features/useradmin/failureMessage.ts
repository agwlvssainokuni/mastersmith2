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
// 失敗と場面（一覧・操作・保存）から文言の鍵を選ぶ純粋な関数（functional-spec.md の D8・W6・W7・6節・7.5・7.6、
// frontend-components.md の 5.2、security-design.md の 6.2、NFR3.2）。応答の code と状態コードだけを見て、サーバーの
// detail・title は読まない。知らない code・code の無い応答（Tomcat の HTML の 400 を含む、計画 9節の Q-C）・通信の失敗は、
// 場面ごとの一般の文言にする。401 と 403 の扱い（D11・D12）は画面の側（useUserAdmin の handledCommonly）が先に行う。
import type { ApiError } from '../../shared/api-client/apiError'

/** 画面が扱う code（招待の INVITATION_ERROR_CODES と同じ形） */
export const USER_ADMIN_ERROR_CODES = [
  'VALIDATION_FAILED',
  'USER_NOT_FOUND',
  'USER_ADMIN_SELF_OPERATION',
  'USER_ADMIN_TARGET_SUSPENDED',
  'USER_ADMIN_NO_CHANGE',
  'USER_ADMIN_LAST_ADMIN',
  'USER_ADMIN_BUSY',
] as const

/** 画面が扱う code */
export type UserAdminErrorCode = (typeof USER_ADMIN_ERROR_CODES)[number]

/** 失敗の場面 */
export type FailureScene = 'list' | 'operation' | 'save'

const KNOWN_CODES: ReadonlySet<string> = new Set(USER_ADMIN_ERROR_CODES)

/** 業務の失敗の知らせ（S5）に理由ごとの文を出す code（7.5） */
const OPERATION_CODES: ReadonlySet<UserAdminErrorCode> = new Set<UserAdminErrorCode>([
  'USER_NOT_FOUND',
  'USER_ADMIN_SELF_OPERATION',
  'USER_ADMIN_TARGET_SUSPENDED',
  'USER_ADMIN_NO_CHANGE',
  'USER_ADMIN_LAST_ADMIN',
  'USER_ADMIN_BUSY',
])

/** 一覧が読めなかったときの文言の鍵 */
export const LIST_LOAD_FAILED_KEY = 'useradmin.list.loadFailed'

/** 一覧の 400（検索の文字・ページの誤り）の文言の鍵（検索の入力欄の下に出す） */
export const SEARCH_INVALID_KEY = 'useradmin.search.invalid'

/** 操作の一般の失敗の文言の鍵 */
export const OPERATION_FAILED_KEY = 'useradmin.error.general'

/** 保存の 404 の文言の鍵（not-found の表示） */
export const SAVE_NOT_FOUND_KEY = 'useradmin.edit.notFound'

/** 保存の一般の失敗の文言の鍵（failed の表示） */
export const SAVE_FAILED_KEY = 'useradmin.edit.failed'

/** 失敗の code（画面が扱うものだけ。ほかは undefined） */
export function knownCode(error: unknown): UserAdminErrorCode | undefined {
  const apiError = error as ApiError | undefined
  if (
    typeof apiError === 'object' &&
    apiError !== null &&
    apiError.kind === 'response' &&
    typeof apiError.code === 'string' &&
    KNOWN_CODES.has(apiError.code)
  ) {
    return apiError.code as UserAdminErrorCode
  }
  return undefined
}

/** 失敗の状態コード（通信の失敗・応答でない値は undefined） */
export function failureStatus(error: unknown): number | undefined {
  const apiError = error as ApiError | undefined
  return typeof apiError === 'object' && apiError !== null && apiError.kind === 'response'
    ? apiError.status
    : undefined
}

/**
 * 失敗の文言の鍵を選ぶ。
 * - 一覧: 400 VALIDATION_FAILED は検索の入力欄の下の文言、ほかは読み込みの失敗の文言。
 * - 操作: 6つの code は理由ごとの文（7.5）、ほかは一般の文。
 * - 保存: 404 USER_NOT_FOUND は not-found の文、400 VALIDATION_FAILED は項目に結び付かないときの文、ほかは一般の文。
 */
export function failureMessageKey(error: unknown, scene: FailureScene): string {
  const code = knownCode(error)
  switch (scene) {
    case 'list':
      return code === 'VALIDATION_FAILED' && failureStatus(error) === 400
        ? SEARCH_INVALID_KEY
        : LIST_LOAD_FAILED_KEY
    case 'operation':
      return code !== undefined && OPERATION_CODES.has(code)
        ? `useradmin.error.${code}`
        : OPERATION_FAILED_KEY
    case 'save':
      if (code === 'USER_NOT_FOUND') {
        return SAVE_NOT_FOUND_KEY
      }
      return code === 'VALIDATION_FAILED' ? 'useradmin.edit.formInvalid' : SAVE_FAILED_KEY
  }
}
