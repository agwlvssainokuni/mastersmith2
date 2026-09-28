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
// 登録の完了からログインの画面への1回だけの受け渡し（D13・W9、NFR2.1）。
// 値はこのモジュールの中の変数1つだけに持ち、URL・ブラウザの保存・history.state・console に載せない。
// 渡せるのはメールアドレスだけで、パスワード・招待のトークンを受ける口は作らない。

/** 受け渡しの値 */
export interface LoginHandoff {
  email: string
}

let handoff: LoginHandoff | null = null

/** 登録の完了の画面が、ログインの画面へメールアドレスを渡す。 */
export function handOffToLogin(email: string): void {
  handoff = { email }
}

/** 受け渡しの値を読む（読むだけでは消えない）。無ければ null。 */
export function takeLoginHandoff(): LoginHandoff | null {
  return handoff
}

/** 受け渡しの値を消す（ログインの画面が描画の確定の後に呼ぶ）。 */
export function clearLoginHandoff(): void {
  handoff = null
}

/** 状態を初めに戻す（テストで使う）。 */
export function resetLoginHandoff(): void {
  handoff = null
}
