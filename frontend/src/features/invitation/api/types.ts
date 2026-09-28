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
// 招待の管理の API（契約 C5）の型（frontend-components.md の 2節・5節、NFR1.1）。
// 招待のトークン・招待の URL の項目は持たない（応答に無く、画面でも扱わない）。応答の知らない項目は型に書かず捨てる。
// 日時（invitedAt・expiresAt）は ISO 8601 の UTC の文字列のまま持ち、表示のときに書式にする。

/** 送信の結果の値 */
export const SEND_RESULTS = ['SENT', 'FAILED'] as const

/** 送信の結果（知らない値は画面の側で FAILED として扱う） */
export type SendResult = (typeof SEND_RESULTS)[number]

/** 招待を使えない理由の値 */
export const UNAVAILABLE_REASONS = ['SMTP_NOT_CONFIGURED', 'BASE_URL_NOT_CONFIGURED'] as const

/** 招待を使えない理由（知らない値は画面の側で捨てる） */
export type UnavailableReason = (typeof UNAVAILABLE_REASONS)[number]

/** 招待メールの言語の値 */
export const INVITATION_LANGUAGES = ['ja', 'en'] as const

/** 招待メールの言語 */
export type InvitationLanguage = (typeof INVITATION_LANGUAGES)[number]

/** 招待中の人（一覧の1行、招待・送り直しの応答） */
export interface Invitation {
  invitationId: number
  /** 招待のメールアドレス（受けた値のまま） */
  email: string
  language: InvitationLanguage
  /** 招待した管理者の氏名（利用者の行が無ければ空の文字列） */
  invitedBy: string
  /** 招待した日時（UTC、ISO 8601） */
  invitedAt: string
  /** 有効期限（UTC、ISO 8601） */
  expiresAt: string
  sendResult: SendResult
  /** 期限切れか（サーバーが判定する。画面の時計で比べない） */
  expired: boolean
}

/** 一覧の1ページ */
export interface InvitationPage {
  items: Invitation[]
  /** ページ（1 から） */
  page: number
  /** ページの大きさ */
  size: number
  /** 全件数 */
  total: number
  /** 招待を使えるか */
  invitationEnabled: boolean
  /** 使えない理由（使えるときは空） */
  unavailableReasons: UnavailableReason[]
}

/** 招待の要求の本文（2つの項目だけ） */
export interface InvitationRequest {
  email: string
  language: InvitationLanguage
}

/** 409 INVITATION_ALREADY_PENDING の追加の項目（招待中の行の位置） */
export interface PendingProblem {
  invitationId: number
  /** 行があるページ（1 以上） */
  page: number
}
