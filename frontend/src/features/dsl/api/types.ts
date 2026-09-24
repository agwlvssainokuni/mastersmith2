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
// DSL の管理の API（契約 C6）の応答の型（BR2.1）。サーバーの応答の知らない項目は無視する（型に書かない）。
// 日時は ISO 8601 の UTC の文字列、利用者の ID は文字列で、利用者が見つからないときはメールアドレスが null。

/** DSL の出どころ */
export type DslSource = 'GENERATED' | 'UPLOAD' | 'PASTE' | 'RESTORE'

/** 投入の出どころ（画面から送れるもの） */
export type SubmitSource = 'UPLOAD' | 'PASTE'

/** 操作した人 */
export interface DslUser {
  userId: string
  /** 利用者が見つからないときは null */
  email: string | null
}

/** DSL の参照（本文を持たない） */
export interface DslRef {
  /** 本文の識別（SHA-256 の16進） */
  dslHash: string
  source: DslSource
  by: DslUser
  /** 置いた・適用した日時（UTC、ISO 8601） */
  at: string
}

/** 適用中の DSL の参照 */
export interface AppliedRef extends DslRef {
  revisionId: string
}

/** プレビューの参照 */
export interface PreviewRef extends DslRef {
  /** プレビューを置くたびに新しくなる識別（適用の要求に付ける） */
  previewId: string
}

/** 今の状態 */
export interface DslStatus {
  applied: AppliedRef | null
  preview: PreviewRef | null
}

/** 表示言語ごとの表示名 */
export interface DisplayLabel {
  ja: string
  en: string
}

/** メニューの木の1つの節 */
export interface MenuNode {
  label: DisplayLabel
  /** テーブルの物理名（テーブルを指さない節は null） */
  table: string | null
  children: MenuNode[]
}

/** 表示名が埋まっていない場所 */
export interface MissingDisplayName {
  path: string
  language: 'ja' | 'en'
}

/** プレビューの要約 */
export interface PreviewSummary {
  tableCount: number
  viewCount: number
  columnCount: number
  menuTree: MenuNode[]
  /** 先頭の 100 件まで（サーバーが絞る） */
  missingDisplayNames: MissingDisplayName[]
  /** 表示名が埋まっていない場所の総数（無ければ一覧の件数を使う） */
  missingDisplayNameTotal?: number
}

/** テーブルの違いの区分 */
export type TableChange = 'ADDED' | 'REMOVED' | 'CHANGED' | 'UNCHANGED'

/** カラムの違いの区分 */
export type ColumnChange = 'ADDED' | 'REMOVED' | 'CHANGED'

/** カラムの違い */
export interface ColumnDiff {
  name: string
  change: ColumnChange
  /** 変わった項目の名前（項目の名前のまま示す） */
  changedItems: string[]
}

/** テーブルの違い */
export interface TableDiff {
  name: string
  change: TableChange
  columns: ColumnDiff[]
}

/** 適用中との違い */
export interface DslDiff {
  /** 適用中の DSL があるか（無ければすべてが増えた） */
  appliedExists: boolean
  tables: TableDiff[]
}

/** 照合の警告の種類 */
export type WarningKind =
  | 'TABLE_MISSING'
  | 'COLUMN_MISSING'
  | 'TYPE_MISMATCH'
  | 'TARGET_UNCONFIGURED'
  | 'TARGET_UNAVAILABLE'

/** 照合の警告 */
export interface DslWarning {
  kind: WarningKind
  path: string | null
  /** 要求の表示言語の文言（サーバーが返したまま示す） */
  message: string
}

/** プレビューの中身 */
export interface Preview extends PreviewRef {
  summary: PreviewSummary
  diff: DslDiff
  warnings: DslWarning[]
}

/** 適用の履歴の1件 */
export interface HistoryEntry extends AppliedRef {
  /** 今適用中の版なら true */
  current: boolean
}

/** 誤りの種類 */
export type DslErrorKind =
  | 'SIZE_LIMIT'
  | 'DEPTH_LIMIT'
  | 'ALIAS_LIMIT'
  | 'FORBIDDEN_TAG'
  | 'DUPLICATE_KEY'
  | 'UNSUPPORTED_VERSION'
  | 'SYNTAX'
  | 'SEMANTIC'

/** 誤りの1件 */
export interface DslErrorItem {
  kind: DslErrorKind
  line: number | null
  column: number | null
  path: string | null
  /** 要求の表示言語の文言（サーバーが返したまま示す） */
  message: string
}

/** 誤りの一覧（422 DSL_INVALID の本文） */
export interface DslErrorReport {
  /** 誤りの総数（1以上） */
  total: number
  /** 先頭の 100 件まで */
  errors: DslErrorItem[]
}

/** ダウンロードしたファイル */
export interface DslFile {
  blob: Blob
  fileName: string
}
