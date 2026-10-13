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
// 自分の作業ロールの API（契約 C8 の GET・PUT /api/me/work-role）を呼ぶ関数と応答の写し（Intent 261004-role-menu の
// U7 の L1、functional-spec.md の 1.2、D3、NFR1.4・NFR2.2）。
// - ApiClient（apiRequest）を通し、トークン・401 での更新・Accept-Language は ApiClient に任せる。失敗は ApiError のまま。
// - 応答は型の項目だけを写した新しい値にし、必要な項目が無い・型が違う本文は通信の失敗（networkError）にする。
// - 切り替えの本文は {roleId} だけ。利用者を指す値を送らない（主体はサーバーが要求の文脈から読む）。
// - 応答・要求の値をコンソール・ブラウザの保存に出さない。
import { apiRequest } from '../../shared/api-client/apiClient'
import { networkError } from '../../shared/api-client/apiError'

/** 自分の作業ロールの API の道 */
export const WORK_ROLE_API = '/api/me/work-role'

/** ロールの組（ID と名前） */
export interface WorkRoleRef {
  roleId: number
  name: string
}

/** 自分のロールの一覧（ロールの ID の順）と、読み替えの後の有効な作業ロール */
export interface WorkRoleState {
  roles: readonly WorkRoleRef[]
  current: WorkRoleRef | null
}

function isObject(value: unknown): value is Readonly<Record<string, unknown>> {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function parseRef(value: unknown): WorkRoleRef | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { roleId, name } = value
  if (typeof roleId !== 'number' || !Number.isInteger(roleId) || typeof name !== 'string') {
    return undefined
  }
  return { roleId, name }
}

/**
 * 作業ロールの応答を写す。形が違えば undefined。
 *
 * @param body 応答の本文
 */
export function parseWorkRole(body: unknown): WorkRoleState | undefined {
  if (!isObject(body) || !Array.isArray(body.roles)) {
    return undefined
  }
  const roles: WorkRoleRef[] = []
  for (const item of body.roles as unknown[]) {
    const role = parseRef(item)
    if (role === undefined) {
      return undefined
    }
    roles.push(role)
  }
  if (body.current === null) {
    return { roles, current: null }
  }
  const current = parseRef(body.current)
  return current === undefined ? undefined : { roles, current }
}

/** 自分のロールの一覧と今の作業ロールを読む。 */
export async function getWorkRole(): Promise<WorkRoleState> {
  const response = await apiRequest(WORK_ROLE_API, { method: 'GET' })
  let body: unknown
  try {
    body = await response.json()
  } catch {
    throw networkError()
  }
  const state = parseWorkRole(body)
  if (state === undefined) {
    throw networkError()
  }
  return state
}

/**
 * 作業ロールを切り替える（204）。拒否は ApiError（ROLE_NOT_ASSIGNED・ROLE_BUSY・VALIDATION_FAILED）。
 *
 * @param roleId 切り替える先のロールの ID
 */
export async function switchWorkRole(roleId: number): Promise<void> {
  const body: { roleId: number } = { roleId }
  await apiRequest(WORK_ROLE_API, {
    method: 'PUT',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
}
