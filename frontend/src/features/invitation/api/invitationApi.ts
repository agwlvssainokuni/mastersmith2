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
// 招待の管理の API（契約 C5 の4本）を呼ぶ関数（frontend-components.md の 5節、security-design.md の 1節・3.3・3.4）。
// すべて既存の ApiClient を通し、アクセストークン・401 での更新・Accept-Language は ApiClient に任せる。
// 失敗は ApiClient の ApiError（kind・status・code、本文が Problem Details なら problem）としてそのまま投げる。
// 成功の本文は一覧・招待・送り直しだけ JSON として読み、決まった項目だけの値を作る（知らない項目は捨てる。
// sendResult の知らない値は FAILED、unavailableReasons の知らない値は捨てる）。読めない・形が違う本文は通信の失敗とする。
// 取り消しの 204 は本文を読まない。応答の値をコンソール・ブラウザの保存に出さない。
import { apiRequest } from '../../../shared/api-client/apiClient'
import { networkError, type ApiError } from '../../../shared/api-client/apiError'
import { knownReasons } from '../unavailable'
import {
  INVITATION_LANGUAGES,
  SEND_RESULTS,
  type Invitation,
  type InvitationLanguage,
  type InvitationPage,
  type InvitationRequest,
  type PendingProblem,
  type SendResult,
  type UnavailableReason,
} from './types'

/** 招待の管理の API の根 */
export const INVITATION_API_ROOT = '/api/admin/invitations'

/** 送り直しの API のパス */
export function resendPath(invitationId: number): string {
  return `${INVITATION_API_ROOT}/${encodeURIComponent(String(invitationId))}/resend`
}

/** 取り消しの API のパス */
export function cancelPath(invitationId: number): string {
  return `${INVITATION_API_ROOT}/${encodeURIComponent(String(invitationId))}/cancel`
}

type Json = Readonly<Record<string, unknown>>

function isObject(value: unknown): value is Json {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function isInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value)
}

function malformed(): ApiError {
  return networkError()
}

/** 応答の1行を、決まった項目だけの値にする。形が違えば undefined。 */
function toInvitation(value: unknown): Invitation | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { invitationId, email, language, invitedBy, invitedAt, expiresAt, sendResult, expired } =
    value
  if (
    !isInteger(invitationId) ||
    typeof email !== 'string' ||
    typeof language !== 'string' ||
    !(INVITATION_LANGUAGES as readonly string[]).includes(language) ||
    typeof invitedBy !== 'string' ||
    typeof invitedAt !== 'string' ||
    typeof expiresAt !== 'string' ||
    typeof expired !== 'boolean'
  ) {
    return undefined
  }
  const result: SendResult =
    typeof sendResult === 'string' && (SEND_RESULTS as readonly string[]).includes(sendResult)
      ? (sendResult as SendResult)
      : 'FAILED'
  return {
    invitationId,
    email,
    language: language as InvitationLanguage,
    invitedBy,
    invitedAt,
    expiresAt,
    sendResult: result,
    expired,
  }
}

/** 一覧の応答を、決まった項目だけの値にする。形が違えば undefined。 */
function toInvitationPage(value: unknown): InvitationPage | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const { items, page, size, total, invitationEnabled, unavailableReasons } = value
  if (
    !Array.isArray(items) ||
    !isInteger(page) ||
    !isInteger(size) ||
    !isInteger(total) ||
    typeof invitationEnabled !== 'boolean' ||
    !Array.isArray(unavailableReasons)
  ) {
    return undefined
  }
  const rows: Invitation[] = []
  for (const item of items) {
    const row = toInvitation(item)
    if (row === undefined) {
      return undefined
    }
    rows.push(row)
  }
  return {
    items: rows,
    page,
    size,
    total,
    invitationEnabled,
    unavailableReasons: knownReasons(unavailableReasons),
  }
}

/** 本文を JSON として読み、形を確かめる。読めない・形が違えば通信の失敗として投げる。 */
async function readBody<T>(response: Response, convert: (value: unknown) => T | undefined) {
  let body: unknown
  try {
    body = await response.json()
  } catch {
    throw malformed()
  }
  const converted = convert(body)
  if (converted === undefined) {
    throw malformed()
  }
  return converted
}

/** 招待中の一覧の1ページを読む（GET /api/admin/invitations?page=n）。 */
export async function listInvitations(page: number): Promise<InvitationPage> {
  const response = await apiRequest(
    `${INVITATION_API_ROOT}?page=${encodeURIComponent(String(page))}`,
    { method: 'GET' },
  )
  return readBody(response, toInvitationPage)
}

/** 招待する（POST /api/admin/invitations、201）。本文は email・language の2つだけ。 */
export async function createInvitation(request: InvitationRequest): Promise<Invitation> {
  const body: InvitationRequest = { email: request.email, language: request.language }
  const response = await apiRequest(INVITATION_API_ROOT, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  return readBody(response, toInvitation)
}

/** 招待を送り直す（POST /api/admin/invitations/{invitationId}/resend、200）。本文なし。 */
export async function resendInvitation(invitationId: number): Promise<Invitation> {
  const response = await apiRequest(resendPath(invitationId), { method: 'POST' })
  return readBody(response, toInvitation)
}

/** 招待を取り消す（POST /api/admin/invitations/{invitationId}/cancel、204）。本文を読まない。 */
export async function cancelInvitation(invitationId: number): Promise<void> {
  await apiRequest(cancelPath(invitationId), { method: 'POST' })
}

function problemOf(error: unknown, code: string): Json | undefined {
  const apiError = error as ApiError | undefined
  if (apiError?.kind !== 'response' || apiError.code !== code || !isObject(apiError.problem)) {
    return undefined
  }
  return apiError.problem
}

/**
 * 409 INVITATION_ALREADY_PENDING の招待中の行の位置を読む。invitationId が整数、page が 1 以上の整数のときだけ返し、
 * 合わなければ undefined（画面は「一覧でこの招待を見る」を出さない）。
 */
export function readPendingProblem(error: unknown): PendingProblem | undefined {
  const problem = problemOf(error, 'INVITATION_ALREADY_PENDING')
  if (problem === undefined) {
    return undefined
  }
  const { invitationId, page } = problem
  if (!isInteger(invitationId) || !isInteger(page) || page < 1) {
    return undefined
  }
  return { invitationId, page }
}

/** 503 INVITATION_NOT_CONFIGURED の使えない理由のうち、知っている値だけを読む（無ければ空）。 */
export function readUnavailableReasons(error: unknown): UnavailableReason[] {
  const problem = problemOf(error, 'INVITATION_NOT_CONFIGURED')
  const reasons = problem?.unavailableReasons
  return Array.isArray(reasons) ? knownReasons(reasons) : []
}

/** 画面が使う API の関数の集まり（テストで差し替える） */
export const invitationApi = {
  listInvitations,
  createInvitation,
  resendInvitation,
  cancelInvitation,
}

/** 画面が使う API の関数の集まりの型 */
export type InvitationApi = typeof invitationApi
