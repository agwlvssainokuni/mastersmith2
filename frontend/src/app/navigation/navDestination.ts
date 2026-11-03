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
// 業務の項目のカードの移り先の道を決める純粋な関数（Intent 261102-timezone-ui-polish の U5、functional-spec.md の D17・D29）。
// - 移れる項目（navigable かつ table あり）は自分の道。移れる項目が子を持つときも自分の道（子へは移らない）。
// - 移れない項目（まとまり）は、子を応答の順に深さ優先でたどり、最初に見つかる移れる項目の道。見つからなければ null。
// - 道を作るのは toTablePlaceholderPath の 1 か所だけ（文字列の連結で作らない）。
// - 最初の移れる項目で打ち切るため、訪問する項目の数は木の項目数を超えない（NFR2.2.2）。
import type { NavNode } from './navigationApi'
import { toTablePlaceholderPath } from './tablePlaceholderPath'

/**
 * カードの移り先の道を返す。
 *
 * @param node 応答の項目
 * @returns 道。移れる項目が自分にも子孫にも無ければ null
 */
export function firstNavigablePath(node: NavNode): string | null {
  if (node.navigable && node.table !== null) {
    return toTablePlaceholderPath(node.table, node.id)
  }
  for (const child of node.items) {
    const path = firstNavigablePath(child)
    if (path !== null) {
      return path
    }
  }
  return null
}
