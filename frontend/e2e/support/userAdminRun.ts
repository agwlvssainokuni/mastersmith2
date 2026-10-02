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
// 110 の実行ごとの印 runTag を作る（Intent 260930-user-admin の U5、security-design.md 3.4、計画 8節の D-11）。
// 時刻の16進と randomBytes(8) の16進（16 文字の乱数）をつなぐ（例 11 文字＋16 文字）。乱数の部分を 16 文字以上にして、
// 短い値が報告の中の無関係な文字と偶然一致することを防ぐ。報告の部品は 24 文字以上のときだけ runTag を単独の値として探す。
// 110 は U の宛先（u7-perf-<runTag>@example.com）を作るためと、検索の欄に入れるために使う。既存の invitationSeed.ts の
// newRunTag（乱数 6 文字）は使わない（既存の手伝いは変えない）。
import { randomBytes } from 'node:crypto'

/** 110 の実行ごとに重ならない印を作る。 */
export function newUserAdminRunTag(): string {
  return `${Date.now().toString(16)}${randomBytes(8).toString('hex')}`
}
