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
// 権限の受け渡しの API（契約 C7 の書き出し・確かめ・適用）の画面の側の型（Intent 261004-role-menu の U6、
// frontend-components.md の 5節、L2・L8、NFR1.9）。応答の知らない項目は型に書かず捨てる。誤りの行・列は null がありうる
// （role の TransferErrorItem。計画 2.3 の A15）。

/** 主権限の値 */
export const TRANSFER_MAIN_VALUES = ['NONE', 'READ', 'FULL'] as const

/** 主権限の値 */
export type TransferMainValue = (typeof TRANSFER_MAIN_VALUES)[number]

/** ロールの変わり方 */
export const TRANSFER_ACTIONS = ['CREATE', 'REPLACE'] as const

/** ロールの変わり方（作る・置き換える） */
export type TransferAction = (typeof TRANSFER_ACTIONS)[number]

/** 変わる点の理由 */
export const TRANSFER_CHANGE_REASONS = ['ADDED', 'CHANGED', 'CLEARED'] as const

/** 変わる点の理由（足す・変わる・消える） */
export type TransferChangeReason = (typeof TRANSFER_CHANGE_REASONS)[number]

/** 変わる対象 */
export interface TransferTarget {
  schemaName: string
  tableName: string | null
  columnName: string | null
}

/** 前・後の値（null は設定なし） */
export interface TransferValues {
  main: TransferMainValue | null
  create: boolean | null
  delete: boolean | null
}

/** 変わる点の1件 */
export interface TransferChange {
  target: TransferTarget
  before: TransferValues
  after: TransferValues
  reason: TransferChangeReason
  inCurrentDsl: boolean
}

/** ロールごとの計画 */
export interface TransferRolePlan {
  name: string
  /** 作るロールは null */
  roleId: number | null
  action: TransferAction
  added: number
  changed: number
  cleared: number
  notInDsl: number
  changes: TransferChange[]
}

/** 確かめの結果 */
export interface TransferCheck {
  /** 適用のヘッダーに入れる指紋（画面の状態にだけ持つ） */
  fingerprint: string
  roles: TransferRolePlan[]
  /** ファイルに無いため残るロールの名前 */
  untouchedRoles: string[]
}

/** 適用の結果 */
export interface TransferResult {
  created: number
  replaced: number
}

/** 誤りの1件（ROLE_TRANSFER_INVALID の errors） */
export interface TransferErrorItem {
  /** 理由（知らない値は汎用の文言にする） */
  reason: string
  line: number | null
  column: number | null
  path: string | null
}

/** 書き出しの結果 */
export interface TransferExport {
  blob: Blob
  /** 保存するファイルの名前（安全でなければ既定の roles.yaml） */
  fileName: string
  /** 書き出した中身が読み込みの上限を超えるか（応答のヘッダーが true のときだけ） */
  exceedsImportLimit: boolean
}
