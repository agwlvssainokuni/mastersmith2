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
// 実効の値の文字を作る純粋な関数（Intent 261004-role-menu の U6 の D11、NFR4.1）。色に頼らず文字で区別する。
// - 主権限: 「READ（明示）」「READ（スキーマ 販売DB から継承）」「READ（テーブル 受注明細 から継承）」「NONE（既定）」。
//   継承の元の名前は表示名（無ければ物理名）。
// - 補助権限（CREATE・DELETE）: 「可」「不可」と、明示の値があるときだけ「（明示）」を添える。明示が無いときは出どころを
//   添えない。応答の inheritedFrom は主権限だけの値のため流用しない（計画 13節 Q4: A、11節 D-13）。
import type { PermissionNode } from './api/types'

/** 実効の値の種類 */
export type EffectiveKind = 'main' | 'create' | 'delete'

/** 継承の元の表示名（呼び出し側が親の節の一覧から引く。無ければ物理名にする） */
export interface InheritedNames {
  schema?: string
  table?: string
}

/** 実効の値の文字の部品（文言の鍵と差し込む値） */
export interface EffectiveLabel {
  /** 文言の鍵 */
  key: string
  /** 主権限の値（訳さない）か、補助権限の値の文言の鍵 */
  value: { literal: string } | { key: string }
  /** 継承の元の名前（継承のときだけ） */
  name?: string
}

/** 実効の値の文字の部品を作る。 */
export function effectiveLabel(
  node: PermissionNode,
  kind: EffectiveKind,
  names: InheritedNames = {},
): EffectiveLabel {
  if (kind !== 'main') {
    const allowed = node.effective[kind]
    const value = { key: allowed ? 'roleadmin.value.allowed' : 'roleadmin.value.denied' }
    return node.explicit[kind] !== null
      ? { key: 'roleadmin.effective.auxExplicit', value }
      : { key: 'roleadmin.effective.aux', value }
  }
  const value = { literal: node.effective.main }
  switch (node.inheritedFrom) {
    case 'EXPLICIT':
      return { key: 'roleadmin.effective.explicit', value }
    case 'SCHEMA':
      return {
        key: 'roleadmin.effective.inheritedSchema',
        value,
        name: names.schema ?? node.schemaName,
      }
    case 'TABLE':
      return {
        key: 'roleadmin.effective.inheritedTable',
        value,
        name: names.table ?? node.tableName ?? node.schemaName,
      }
    case 'DEFAULT':
      return { key: 'roleadmin.effective.default', value }
  }
}

/** 部品を文言にする（t は鍵と差し込む値から文言を返す関数）。 */
export function formatEffectiveLabel(
  label: EffectiveLabel,
  t: (key: string, values?: Readonly<Record<string, string | number>>) => string,
): string {
  const value = 'literal' in label.value ? label.value.literal : t(label.value.key)
  return label.name === undefined
    ? t(label.key, { value })
    : t(label.key, { value, name: label.name })
}
