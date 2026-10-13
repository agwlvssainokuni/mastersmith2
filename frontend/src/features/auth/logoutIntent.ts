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
// ログアウトの印（Intent 261004-role-menu の U7 の L11、security-design.md 5.1、NFR1.8）。
// - メモリ上の真偽1つ。履歴の state・ブラウザの保存・URL には置かない。
// - ユーザーメニューの「ログアウト」が移る直前に立て（beforeNavigate）、ログアウトの画面が使って消す。`/logout` 以外の
//   道が描かれたときは骨組みが clearLogoutIntent（= consumeLogoutIntent）で消す。S4 の未保存の確かめで［留まる］を
//   選んだ後は、次に道が変わったときに消える（依頼者の決定 G1: A。security-design.md 5.1 との差）。

let intended = false

/** 印を立てる。 */
export function markLogoutIntent(): void {
  intended = true
}

/** 印を消さずに読む。 */
export function peekLogoutIntent(): boolean {
  return intended
}

/** 印を読んで消す。 */
export function consumeLogoutIntent(): boolean {
  const value = intended
  intended = false
  return value
}
