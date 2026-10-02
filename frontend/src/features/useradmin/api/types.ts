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
// 利用者の管理の API（契約 C3）の型（frontend-components.md の 5.1、security-design.md の 6.3、NFR3.3）。
// 応答の知らない項目（passwordHash・failedAttempts・refreshToken など）は型に書かず捨てる。
// 日時（registeredAt・lockedUntil）は ISO 8601 の UTC の文字列のまま持ち、表示のときに書式にする。
// lockedUntil はロックしていないとき応答に null で来るが、画面の値では「持たない」に写す（計画 8節の D-4）。

/** 利用者の言語の値 */
export const USER_LANGUAGES = ['ja', 'en'] as const

/** 利用者の言語 */
export type UserLanguage = (typeof USER_LANGUAGES)[number]

/** 一覧の1行（AdminUser） */
export interface AdminUser {
  userId: number
  email: string
  displayName: string
  language: UserLanguage
  /** 管理者の印 */
  admin: boolean
  /** 利用停止中 */
  suspended: boolean
  /** ロック中（サーバーが判定する。画面の時計で比べない） */
  locked: boolean
  /** 解除の予定の時刻（UTC、ISO 8601。ロックしていなければ無い） */
  lockedUntil?: string
  /** 失敗回数を戻せるか（ロック中かどうかによらない） */
  resettable: boolean
  /** 登録した日時（UTC、ISO 8601） */
  registeredAt: string
  /** 操作している管理者自身の行か */
  self: boolean
}

/** 一覧の1ページ（AdminUserPage） */
export interface AdminUserPage {
  items: AdminUser[]
  /** ページ（1 から） */
  page: number
  /** ページの大きさ */
  size: number
  /** 検索の条件に当たる全件数 */
  total: number
}

/** 氏名と言語の変更の要求の本文（2つの項目だけ） */
export interface ProfileRequest {
  displayName: string
  language: UserLanguage
}
