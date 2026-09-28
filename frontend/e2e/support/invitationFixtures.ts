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
// 招待の一覧の応答の見本（契約 C5 の InvitationPage。Intent 260925-user-management の B5 の共用の手伝い）。
// E2E で一覧の API の答えを差し替えるときは、この見本だけを使い、画面の側の型（src/features/invitation/api/types.ts）を
// 付ける。本物の応答と項目の名前・型が一致することを、本物の一覧を読む測定のテストで毎回確かめる（project.md の Corrections）。
// メールアドレスは予約されたドメイン（example.com）の下の固定の値、招待した管理者の氏名は架空の値。
import type { Invitation, InvitationPage } from '../../src/features/invitation/api/types'

/** 行ありの見本の全件数（20 行を超え、ページ送りが押せる） */
export const SAMPLE_TOTAL = 23

/** 見本の1行 */
function sampleRow(index: number): Invitation {
  const number = String(index).padStart(2, '0')
  return {
    invitationId: 1000 + index,
    email: `e2e-invitee-${number}@example.com`,
    language: index % 2 === 0 ? 'en' : 'ja',
    // index 3 の行は招待した管理者が空。契約 C5 では必須で、U2 で氏名は必須のため今の API では起きない。
    // D7 の表示（「（不明）」）を確かめる防御のための行（NFR 設計の承認の場の U5 R-01）。
    invitedBy: index === 3 ? '' : '見本 管理者',
    invitedAt: `2026-09-${String(10 + (index % 18)).padStart(2, '0')}T01:00:00Z`,
    expiresAt: `2026-09-${String(11 + (index % 18)).padStart(2, '0')}T01:00:00Z`,
    sendResult: index % 5 === 0 ? 'FAILED' : 'SENT',
    expired: index % 4 === 0,
  }
}

/** 行ありの一覧（20 行・全件数 23・招待を使える。送信の結果と期限と言語が混ざる） */
export function sampleInvitationPage(): InvitationPage {
  return {
    items: Array.from({ length: 20 }, (_, index) => sampleRow(index + 1)),
    page: 1,
    size: 20,
    total: SAMPLE_TOTAL,
    invitationEnabled: true,
    unavailableReasons: [],
  }
}

/** 招待を使えない一覧（理由2つ・行あり） */
export function unavailableInvitationPage(): InvitationPage {
  return {
    ...sampleInvitationPage(),
    invitationEnabled: false,
    unavailableReasons: ['BASE_URL_NOT_CONFIGURED', 'SMTP_NOT_CONFIGURED'],
  }
}

function sameShape(value: unknown, sample: object): boolean {
  if (typeof value !== 'object' || value === null || Array.isArray(value)) {
    return false
  }
  const record = value as Record<string, unknown>
  const sampleRecord = sample as Record<string, unknown>
  const keys = Object.keys(record).sort()
  const sampleKeys = Object.keys(sampleRecord).sort()
  return (
    JSON.stringify(keys) === JSON.stringify(sampleKeys) &&
    sampleKeys.every(
      (key) =>
        typeof record[key] === typeof sampleRecord[key] &&
        Array.isArray(record[key]) === Array.isArray(sampleRecord[key]),
    )
  )
}

/**
 * 本物の一覧の応答の項目の名前と値の型が、見本と一致するか（行があれば1行目も比べる）。
 * 一致しなければ、見本での差し替えが本物の形から外れている。
 */
export function hasInvitationPageShape(body: unknown): boolean {
  const sample = sampleInvitationPage()
  if (!sameShape(body, sample)) {
    return false
  }
  const items = (body as { items: unknown[] }).items
  return items.length === 0 || sameShape(items[0], sample.items[0] as Invitation)
}
