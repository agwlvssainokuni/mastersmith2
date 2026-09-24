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
// 違いの数え方（BR5.7、AC4.1.2）。表示と確かめる表示で同じ数を使う。
import type { DslDiff, TableChange } from './api/types'

/** 区分ごとの数 */
export interface ChangeCounts {
  added: number
  removed: number
  changed: number
}

/** テーブルの区分ごとの数（変わらないテーブルは数えない）。 */
export function countTableChanges(diff: DslDiff): ChangeCounts {
  const count = (change: TableChange) => diff.tables.filter((t) => t.change === change).length
  return { added: count('ADDED'), removed: count('REMOVED'), changed: count('CHANGED') }
}

/** カラムの区分ごとの数（すべてのテーブルの合計）。 */
export function countColumnChanges(diff: DslDiff): ChangeCounts {
  const counts: ChangeCounts = { added: 0, removed: 0, changed: 0 }
  for (const table of diff.tables) {
    for (const column of table.columns) {
      if (column.change === 'ADDED') {
        counts.added += 1
      } else if (column.change === 'REMOVED') {
        counts.removed += 1
      } else {
        counts.changed += 1
      }
    }
  }
  return counts
}
