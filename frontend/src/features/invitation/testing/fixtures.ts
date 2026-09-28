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
// テストの値（契約 C5 の形）を組み立てる（テストからだけ使う）。メールアドレスは予約されたドメイン（example.test）の下の
// 値だけ、氏名は架空の値を使う。日時は ISO 8601 の UTC の固定の値で、期限内・期限切れは応答の expired で決める。
import type { Invitation, InvitationPage } from '../api/types'

/** 招待中の人の1行 */
export function invitationOf(overrides: Partial<Invitation> = {}): Invitation {
  return {
    invitationId: 1,
    email: 'hanako@example.test',
    language: 'ja',
    invitedBy: '山田 花子',
    invitedAt: '2026-09-25T12:00:00Z',
    expiresAt: '2026-09-26T12:00:00Z',
    sendResult: 'SENT',
    expired: false,
    ...overrides,
  }
}

/**
 * 招待した管理者が空の行。契約 C5 では必須で、U2 で氏名は必須のため今の API では起きない。
 * D7 の表示（「（不明）」）を確かめる防御のための行（NFR 設計の承認の場の U5 R-01）。
 */
export function unknownInviterRow(overrides: Partial<Invitation> = {}): Invitation {
  return invitationOf({ invitedBy: '', ...overrides })
}

/** 見本の3行（送信済み・送信に失敗・期限切れ、言語 ja・en、招待した管理者が空の行を含む） */
export function sampleRows(): Invitation[] {
  return [
    invitationOf({ invitationId: 11, email: 'hanako@example.test' }),
    invitationOf({
      invitationId: 12,
      email: 'taro@example.test',
      language: 'en',
      invitedBy: '佐藤 太郎',
      sendResult: 'FAILED',
    }),
    unknownInviterRow({
      invitationId: 13,
      email: 'jiro@example.test',
      expired: true,
      expiresAt: '2026-09-24T12:00:00Z',
    }),
  ]
}

/** n 件の行（invitationId は start から） */
export function rowsOf(count: number, start = 1): Invitation[] {
  return Array.from({ length: count }, (_, index) =>
    invitationOf({
      invitationId: start + index,
      email: `invitee-${String(start + index).padStart(2, '0')}@example.test`,
    }),
  )
}

/** 一覧の1ページ */
export function pageOf(overrides: Partial<InvitationPage> = {}): InvitationPage {
  const items = overrides.items ?? sampleRows()
  return {
    items,
    page: 1,
    size: 20,
    total: items.length,
    invitationEnabled: true,
    unavailableReasons: [],
    ...overrides,
  }
}

/** 招待を使えない一覧（理由2つ） */
export function unavailablePageOf(overrides: Partial<InvitationPage> = {}): InvitationPage {
  return pageOf({
    invitationEnabled: false,
    unavailableReasons: ['BASE_URL_NOT_CONFIGURED', 'SMTP_NOT_CONFIGURED'],
    ...overrides,
  })
}
