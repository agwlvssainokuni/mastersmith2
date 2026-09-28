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
// 招待の入力の前もっての確かめ（functional-spec.md の D8、CR6.1）。画面が確かめるのはメールアドレスが空（空白だけを含む）
// でないことだけで、形式・長さ・改行はサーバー（U3 の BR1.1）の判定に任せる。入れた値は正規化せず、そのまま送る。

/**
 * メールアドレスが空（空白だけを含む）か。空白は JavaScript の空白と改行（全角の空白・タブ・改行を含む）。
 *
 * @param value 入れた値
 */
export function isBlankEmail(value: string): boolean {
  return value.trim() === ''
}
