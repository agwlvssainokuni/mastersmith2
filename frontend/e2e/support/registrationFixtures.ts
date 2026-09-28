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
// 登録の完了の画面のリンクの確かめの応答の見本（契約 C6 の InvitationViewResponse。Intent 260925-user-management の B5 の
// 共用の手伝い、U6 の NFR 設計 security-design.md 5節）。E2E で確かめの API の答えを差し替えるときは、この見本だけを使い、
// 画面の側の型（src/features/registration/registrationApi.ts の VerifiedInvitation）を付ける。
// 本物の応答と項目の名前・型が一致することを、E2E-1（090）で毎回確かめる（shapeOf。値は比べない）。
// メールアドレスは予約されたドメイン（example.com）の下の固定の値。トークンはどの招待にも当たらない低いエントロピーの値。
import type { VerifiedInvitation } from '../../src/features/registration/registrationApi'

/** 確かめの応答の見本（言語 ja の招待） */
export const VERIFY_SAMPLE: VerifiedInvitation = {
  email: 'e2e-a11y-invitee@example.com',
  language: 'ja',
}

/** 検査で開くリンクの見本のトークン（どの招待にも当たらない。サーバーへは送らず、差し替えた答えだけを受ける） */
export const A11Y_SAMPLE_TOKEN = 'a11y-sample-token'

/**
 * 本文の項目の名前ごとに値の型の名前を返す（値は返さない）。`language` は `ja`・`en` のどちらかなら `language`、
 * そうでなければ値の型の名前にする。オブジェクトでなければ `(body)` に型の名前を入れる。
 */
export function shapeOf(body: unknown): Record<string, string> {
  if (typeof body !== 'object' || body === null || Array.isArray(body)) {
    return { '(body)': Array.isArray(body) ? 'array' : body === null ? 'null' : typeof body }
  }
  const shape: Record<string, string> = {}
  for (const [key, value] of Object.entries(body as Record<string, unknown>).sort(([a], [b]) =>
    a.localeCompare(b),
  )) {
    if (key === 'language' && (value === 'ja' || value === 'en')) {
      shape[key] = 'language'
    } else {
      shape[key] = value === null ? 'null' : Array.isArray(value) ? 'array' : typeof value
    }
  }
  return shape
}
