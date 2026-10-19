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
// 行の「操作」のトリガーの読み上げの名前（Intent 261016-business-data-views の U4 の D1・D8、frontend-components.md の 2節、
// U4-NFR4.2・4.6・7.1・7.4）。
// - 名前は見える文字（「操作」・Actions）で始め、半角のコロンと空白の後に行を見分ける値を続ける（WCAG 2.5.3）。
// - 行を見分ける値が空か空白だけなら、見える文字だけを返す。
// - 文字列をつなぐだけで、HTML として解釈しない。値は画面に出す値で、ログ・console・トレースには書かない。
// - 処理中の文字（「（処理中）」・" (processing)"）は、呼ぶ側が戻り値の後ろに足す（D3）。
// 利用者・グループ・ロールの管理の一覧（U4）と、業務データの一覧（U3）の行の操作が使う。

/**
 * 行の「操作」のトリガーの読み上げの名前を作る。
 *
 * @param visibleLabel ボタンに描く文言（ボタンと同じ文言の鍵の値）
 * @param rowLabel 行を見分ける値（前後の空白は切り落とす）
 * @returns 「<見える文字>: <行を見分ける値>」。値が空なら見える文字だけ
 */
export function rowActionsName(visibleLabel: string, rowLabel: string): string {
  const row = rowLabel.trim()
  return row === '' ? visibleLabel : `${visibleLabel}: ${row}`
}
