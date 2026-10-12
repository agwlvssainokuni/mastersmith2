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
// 権限の設定の未保存の写しと、保存の entries を作る純粋な関数（Intent 261004-role-menu の U6 の D10・D12、L7、
// NFR1.4・NFR6.2）。React・API に触れない。写しは「対象の鍵 → 対象と明示の値」の表で、サーバーの値に戻した対象は
// 写しから消す。対象の鍵は名前の JSON の配列（`["SALES"]`・`["SALES","ORDER_LINE"]`・`["SALES","ORDER_LINE","NO"]`）で、
// 名前に何が入っても重ならない（木の節の id と同じ考え方）。
// 保存の範囲は role の実装（B4-7）に合わせる: スキーマの範囲はそのスキーマの段の対象だけ、テーブルの範囲はそのテーブルと
// そのカラムだけ（計画 2.3 の A7、11節 D-3）。範囲の外の対象は entries に入れない。
import type { Explicit, PermissionEntry, PermissionScope, PermissionTarget } from './api/types'

/** 写しの1件 */
export interface DraftItem {
  target: PermissionTarget
  explicit: Explicit
}

/** 未保存の写し（対象の鍵 → 写しの1件） */
export type PermissionDraft = ReadonlyMap<string, DraftItem>

/** 空の写し */
export const EMPTY_DRAFT: PermissionDraft = new Map()

/** 対象の鍵（名前の JSON の配列。下の段が無ければその段から後を含めない）。 */
export function targetKey(target: PermissionTarget): string {
  const names: string[] = [target.schemaName]
  if (target.tableName !== null) {
    names.push(target.tableName)
    if (target.columnName !== null) {
      names.push(target.columnName)
    }
  }
  return JSON.stringify(names)
}

/** 保存の範囲の鍵（木の節の id と同じ形）。 */
export function scopeKey(scope: PermissionScope): string {
  return targetKey({ schemaName: scope.schemaName, tableName: scope.tableName, columnName: null })
}

/** 2つの明示の値が同じか。 */
export function sameExplicit(a: Explicit, b: Explicit): boolean {
  return a.main === b.main && a.create === b.create && a.delete === b.delete
}

/** 対象を写しから引く（無ければサーバーの値）。 */
export function draftExplicit(
  draft: PermissionDraft,
  target: PermissionTarget,
  server: Explicit,
): Explicit {
  return draft.get(targetKey(target))?.explicit ?? server
}

/**
 * 写しに1つの対象の値を当てる。サーバーの値と同じなら写しから消す。カラムの対象は補助権限を持たないため、
 * create・delete を null にそろえる（D11）。元の写しは変えず、新しい写しを返す。
 */
export function applyChange(
  draft: PermissionDraft,
  server: Explicit,
  target: PermissionTarget,
  next: Explicit,
): PermissionDraft {
  const key = targetKey(target)
  const value: Explicit =
    target.columnName !== null ? { main: next.main, create: null, delete: null } : { ...next }
  const result = new Map(draft)
  if (sameExplicit(value, server)) {
    result.delete(key)
  } else {
    result.set(key, { target: { ...target }, explicit: value })
  }
  return result
}

/** 対象が保存の範囲の中か（スキーマの範囲はスキーマの段だけ、テーブルの範囲はそのテーブルとそのカラム）。 */
export function inScope(target: PermissionTarget, scope: PermissionScope): boolean {
  if (target.schemaName !== scope.schemaName) {
    return false
  }
  if (scope.tableName === null) {
    return target.tableName === null && target.columnName === null
  }
  return target.tableName === scope.tableName
}

/**
 * 写しから保存の entries を作る。範囲の中の対象だけを、対象ごとに1つ、鍵の順に並べる。写しが空なら空の配列。
 */
export function toEntries(draft: PermissionDraft, scope: PermissionScope): PermissionEntry[] {
  return [...draft.entries()]
    .filter(([, item]) => inScope(item.target, scope))
    .sort(([a], [b]) => (a < b ? -1 : a > b ? 1 : 0))
    .map(([, item]) => ({
      schemaName: item.target.schemaName,
      tableName: item.target.tableName,
      columnName: item.target.columnName,
      main: item.explicit.main,
      create: item.explicit.create,
      delete: item.explicit.delete,
    }))
}

/** 写しのうち、条件に合う対象だけを残す（PERMISSION_TARGET_NOT_IN_DSL の後に、今の DSL に残る対象だけを残す。D14）。 */
export function retainDraft(
  draft: PermissionDraft,
  keep: (target: PermissionTarget) => boolean,
): PermissionDraft {
  return new Map([...draft.entries()].filter(([, item]) => keep(item.target)))
}
