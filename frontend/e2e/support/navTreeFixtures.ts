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
// U7 の骨組みの API の見本（Intent 261004-role-menu の U7 の L13、performance-design.md 4.1、logical-components.md 5節）。
// - 型は画面の側の型（NavigationResponse・WorkRoleState・TableAccess・MyPermissionNodes）で縛る（project.md の学び。
//   本物の応答との項目の名前と型の一致は 170・150 で確かめる）。
// - 項目の id はサーバーと同じ「絞る前の木での位置の道」（各段の 0 から数えた番号を . でつなぐ）。
// - 名前は架空のもの（テーブル・ロールの名前と表示名だけ）。氏名・メールアドレスを持たない。
import type { NavigationResponse, NavNode } from '../../src/app/navigation/navigationApi'
import type { WorkRoleRef, WorkRoleState } from '../../src/app/work-role/workRoleApi'
import type { TableAccess } from '../../src/features/tables/tableAccessApi'
import type {
  MyPermissionNode,
  MyPermissionNodes,
} from '../../src/features/mypermissions/myPermissionsApi'

/** 見本のスキーマ */
export const FRAME_SCHEMA = 'U7_SALES'

/** 末端の項目が指すテーブル */
export const LEAF_TABLE = 'ORDER_LINE'

/** 末端のテーブルの表示名（日本語・英語、どちらも 40 文字） */
export const LEAF_LABEL = {
  ja: `受注明細の長い表示名${'あ'.repeat(30)}`,
  en: `Order lines with a long label ${'x'.repeat(10)}`,
} as const

/** 段の数（深さの上限ちょうど） */
export const MAX_DEPTH = 5

/** 40 文字の名前（段の番号と順の番号を含めて見分ける） */
export function longLabel(prefix: string): { ja: string; en: string } {
  const ja = `${prefix}の長い名前`
  const en = `${prefix} long name`
  return { ja: ja.padEnd(40, 'い'), en: en.padEnd(40, 'y') }
}

/** 移れる末端の項目 */
function leaf(id: string, label: { ja: string; en: string }, table: string): NavNode {
  return {
    id,
    label,
    icon: 'list',
    table: { schemaName: FRAME_SCHEMA, tableName: table },
    navigable: true,
    items: [],
  }
}

/** 子を持つまとまり */
function group(id: string, label: { ja: string; en: string }, items: NavNode[]): NavNode {
  return { id, label, icon: 'menu', table: null, navigable: false, items }
}

/**
 * 末端までの1本の枝（1段目〜4段目のまとまりと5段目の末端）。各段のまとまりに、filler 件の移れる項目を足す。
 *
 * @param base 1段目の位置の番号
 * @param filler 各段のまとまりに足す移れる項目の数
 * @returns 1段目のまとまりと、作った項目の数
 */
function chain(base: number, filler: number): { node: NavNode; count: number } {
  let count = 0
  const build = (path: string, depth: number): NavNode => {
    count += 1
    if (depth === MAX_DEPTH) {
      return leaf(path, LEAF_LABEL, LEAF_TABLE)
    }
    const children: NavNode[] = [build(`${path}.0`, depth + 1)]
    for (let index = 1; index <= filler; index += 1) {
      count += 1
      children.push(
        leaf(`${path}.${index}`, longLabel(`D${depth + 1}-${index}`), `T_${depth}_${index}`),
      )
    }
    return group(path, longLabel(`段${depth}`), children)
  }
  const node = build(String(base), 1)
  return { node, count }
}

/** 末端までの枝の id（1段目〜5段目） */
export function chainIds(base = 0): string[] {
  return Array.from({ length: MAX_DEPTH }, (_, index) =>
    [String(base), ...Array.from({ length: index }, () => '0')].join('.'),
  )
}

/** 検査の木（5段の枝と、1段目の移れる項目をいくつか。今の項目は末端） */
export function inspectionTree(): NavigationResponse {
  const { node } = chain(0, 2)
  const items: NavNode[] = [node]
  for (let index = 1; index <= 3; index += 1) {
    items.push(leaf(String(index), longLabel(`見本${index}`), `TOP_${index}`))
  }
  // 記号を含む名前と、許す一覧に無いアイコン（画面は list に替える）
  items.push({
    ...leaf(
      '4',
      { ja: '記号 <見積> & "受注" \'明細\'', en: 'Symbols <quote> & "order"' },
      'SYMBOLS',
    ),
    icon: 'table',
  })
  return { items, emptyReason: null }
}

/**
 * 目安の木（100 項目・深さ 1）。marker があれば最後に印の項目を足す（切り替えの後の木の見分け）。
 *
 * @param marker 印の項目を足すか
 */
export function typicalTree(marker: boolean): NavigationResponse {
  const items: NavNode[] = []
  for (let index = 0; index < 100; index += 1) {
    items.push(leaf(String(index), longLabel(`目安${index}`), `TYP_${index}`))
  }
  if (marker) {
    items.push(leaf(String(items.length), longLabel('切り替えの後'), 'AFTER_SWITCH'))
  }
  return { items, emptyReason: null }
}

/** 目安の木の印の項目の id */
export const TYPICAL_MARKER_ID = '100'

/**
 * 悪い側の木（1,000 項目・深さ 5・40 文字の名前・末端までの1本の枝）。marker があれば最後に印の項目を足す。
 *
 * @param marker 印の項目を足すか
 */
export function worstTree(marker: boolean): NavigationResponse {
  const { node, count } = chain(0, 20)
  const items: NavNode[] = [node]
  for (let index = 1; count + items.length - 1 < 1000; index += 1) {
    items.push(leaf(String(index), longLabel(`悪い側${index}`), `WST_${index}`))
  }
  if (marker) {
    items.push(leaf(String(items.length), longLabel('切り替えの後'), 'AFTER_SWITCH'))
  }
  return { items, emptyReason: null }
}

/** 悪い側の木の印の項目の id（worstTree(true) の最後の項目） */
export function worstMarkerId(): string {
  const tree = worstTree(true)
  return tree.items[tree.items.length - 1]?.id ?? ''
}

/** 木の項目の数を数える */
export function countNodes(items: readonly NavNode[]): number {
  return items.reduce((sum, node) => sum + 1 + countNodes(node.items), 0)
}

/** 空の業務のメニュー（理由つき） */
export function emptyNavigation(reason: 'NOT_CONFIGURED' | 'NOTHING_VISIBLE'): NavigationResponse {
  return { items: [], emptyReason: reason }
}

/** 40 文字のロール名 */
export const LONG_WORK_ROLE = `検査用の長い作業ロールの名前${'う'.repeat(26)}`

/** ロールが3つ（1つは 40 文字） */
export const THREE_ROLES: readonly WorkRoleRef[] = [
  { roleId: 11, name: '営業' },
  { roleId: 12, name: '経理' },
  { roleId: 13, name: LONG_WORK_ROLE },
]

/** 作業ロールの見本 */
export function workRoleWire(
  roles: readonly WorkRoleRef[],
  current: WorkRoleRef | null,
): WorkRoleState {
  return { roles: [...roles], current }
}

/** 置き場の見本（見てよいテーブル） */
export function tableAccessWire(): TableAccess {
  return {
    schemaName: FRAME_SCHEMA,
    tableName: LEAF_TABLE,
    displayName: { ...LEAF_LABEL },
    main: 'READ',
  }
}

/** 置き場の権限なしの応答（403 ACCESS_DENIED の Problem Details の形） */
export function accessDeniedProblem(): Readonly<Record<string, unknown>> {
  return {
    type: 'about:blank',
    title: 'Forbidden',
    status: 403,
    code: 'ACCESS_DENIED',
  }
}

/** 自分の権限の節 */
function permissionNode(
  tableName: string | null,
  columnName: string | null,
  displayName: string,
  main: 'NONE' | 'READ' | 'FULL',
  hasChildren: boolean,
): MyPermissionNode {
  return {
    schemaName: FRAME_SCHEMA,
    tableName,
    columnName,
    displayName,
    effective: { main, create: main === 'FULL', delete: false },
    inMenu: tableName !== null && columnName === null,
    hasChildren,
  }
}

/** 自分の権限のスキーマの段 */
export function mySchemasWire(
  workRole: WorkRoleRef | null = THREE_ROLES[0] ?? null,
): MyPermissionNodes {
  return { workRole, items: [permissionNode(null, null, '販売の長い表示名', 'READ', true)] }
}

/** 自分の権限のテーブルの段 */
export function myTablesWire(
  workRole: WorkRoleRef | null = THREE_ROLES[0] ?? null,
): MyPermissionNodes {
  return {
    workRole,
    items: [
      permissionNode(LEAF_TABLE, null, LEAF_LABEL.ja, 'FULL', true),
      permissionNode('ORDER_HEAD', null, '受注', 'READ', true),
    ],
  }
}

/** 自分の権限のカラムの段 */
export function myColumnsWire(
  workRole: WorkRoleRef | null = THREE_ROLES[0] ?? null,
): MyPermissionNodes {
  return {
    workRole,
    items: [
      permissionNode(LEAF_TABLE, 'ORDER_NO', '受注番号', 'FULL', false),
      permissionNode(LEAF_TABLE, 'AMOUNT', '金額', 'READ', false),
      permissionNode(LEAF_TABLE, 'NOTE', '', 'NONE', false),
    ],
  }
}
