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
// 業務データの API の受け渡しの型（Intent 261016-business-data-views の U3 の L2、frontend-components.md の 3節、計画 1.5）。
// - 形は U1・U2 のサーバーの record（backend の businessdata/web/ の XxxResponse）に合わせる。
// - 値は文字か null で持ち、数に直さない（桁を保つ。D15）。値の型に any を使わない。
// - 列挙は文字列リテラルの union で表し、写しの関数が照らす既知の一覧を同じ定数から作る（enum を使わない）。
// - 検証の材料（formPart など。U2 が B4 で足した項目）は、この Bolt（B5）では任意の項目として読む。

/** 値は文字か null（桁を保つ。D15） */
export type CellValue = string | null

/** 受け渡しの型（backend の ValueType） */
export const VALUE_TYPES = [
  'STRING',
  'INTEGER',
  'BIG_INTEGER',
  'DECIMAL',
  'BOOLEAN',
  'DATE',
  'DATETIME',
  'DATETIME_TZ',
  'TIME',
  'TIME_TZ',
  'BINARY',
  'OTHER',
] as const
export type ValueType = (typeof VALUE_TYPES)[number]

/** 許す操作（backend の AllowedOperation） */
export const OPERATIONS = ['LIST', 'DETAIL', 'CREATE', 'UPDATE', 'DELETE'] as const
export type Operation = (typeof OPERATIONS)[number]

/** 一覧の書式（backend の dsl の ListFormat） */
export const LIST_FORMATS = [
  'DATE',
  'DATETIME',
  'TIME',
  'NUMBER_GROUPED',
  'BOOLEAN_YES_NO',
  'BOOLEAN_ON_OFF',
] as const
export type ListFormat = (typeof LIST_FORMATS)[number]

/** フォーム部品（`lookup` はサーバーが `text` で返すため含めない。機能設計の承認の場の U3 R-09） */
export const FORM_PARTS = [
  'text',
  'textarea',
  'checkbox',
  'switch',
  'radio',
  'select',
  'date',
  'datetime',
  'number',
  'password',
] as const
export type FormPart = (typeof FORM_PARTS)[number]

/** 行えない操作の理由（backend の RestrictionReason） */
export const RESTRICTION_REASONS = ['NO_PRIMARY_KEY', 'PRIMARY_KEY_HIDDEN', 'VIEW'] as const
export type RestrictionReason = (typeof RESTRICTION_REASONS)[number]

/** 検索の演算子（backend の SearchMode） */
export const SEARCH_OPERATORS = ['EQUALS', 'CONTAINS', 'RANGE', 'CHOICE'] as const
export type SearchOperator = (typeof SEARCH_OPERATORS)[number]

/** 並びの向き（backend の dsl の SortDirection） */
export const SORT_DIRECTIONS = ['ASC', 'DESC'] as const
export type SortDirection = (typeof SORT_DIRECTIONS)[number]

/** 1ページの件数の選択肢（D11。U1 の PageRequest の上限 100） */
export const PAGE_SIZES = [20, 50, 100] as const
export type PageSize = (typeof PAGE_SIZES)[number]

/** 定義の識別（dslHash と作業ロールの ID） */
export interface DefinitionId {
  dslHash: string
  workRoleId: number | null
}

/** 日本語と英語の表示名 */
export interface LocalizedText {
  ja: string
  en: string
}

/** 選択肢（値と表示名） */
export interface Choice {
  value: string
  label: LocalizedText
}

/** カラムの定義（契約 C2 の ColumnDefinition。検証の材料は任意の項目） */
export interface ColumnDefinition {
  column: string
  displayName: LocalizedText
  valueType: ValueType
  listFormat: ListFormat | null
  width: number | null
  editableOnCreate: boolean
  editableOnUpdate: boolean
  masked: boolean
  formPart?: FormPart | null
  required?: boolean
  nullable?: boolean
  minLength?: number | null
  maxLength?: number | null
  precision?: number | null
  scale?: number | null
  min?: string | null
  max?: string | null
  choices?: readonly Choice[]
  hasPattern?: boolean
  /** 他テーブル参照の項目（契約 C2。参照でないカラムと用途 VIEW では null。中身の型は参照の画面を作るときに決める） */
  reference?: unknown
}

/** 検索の項目（契約 C2 の SearchField） */
export interface SearchField {
  column: string
  displayName: LocalizedText
  operator: SearchOperator
  valueType: ValueType
  defaultValue: string | null
  collapsed: boolean
  options: readonly Choice[]
}

/** 並べ替えの鍵 */
export interface SortKey {
  column: string
  direction: SortDirection
}

/** テーブルの定義（契約 C2 の TableDefinition に U1 が足した項目を含む。計画 1.5） */
export interface TableDefinition {
  definitionId: DefinitionId
  definitionChanged: boolean
  schema: string
  table: string
  displayName: LocalizedText
  view: boolean
  hasPrimaryKey: boolean
  primaryKeyColumns: readonly string[]
  operations: readonly Operation[]
  restrictionReason: RestrictionReason | null
  writeConfigured: boolean
  rowLabelColumns: readonly string[]
  listColumns: readonly ColumnDefinition[]
  detailColumns: readonly ColumnDefinition[]
  searchFields: readonly SearchField[]
  sortableColumns: readonly string[]
  defaultSort: readonly SortKey[]
}

/** 行を指す主キー（カラムの名前と値） */
export type RowKey = Readonly<Record<string, string>>

/** 行の値（カラムの名前と値。password と BINARY のカラムは含まれない） */
export type RowValues = Readonly<Record<string, CellValue>>

/** 一覧の1行 */
export interface RowItem {
  /** 行を指す主キー（DETAIL が許されないときは null） */
  key: RowKey | null
  values: RowValues
  truncatedColumns: readonly string[]
}

/** 一覧と検索の応答（契約 C2 の RowPage） */
export interface RowPage {
  definitionId: DefinitionId
  definitionChanged: boolean
  items: readonly RowItem[]
  page: number
  size: PageSize
  total: number
}

/** 詳細の応答（契約 C2 の RowDetail） */
export interface RowDetail {
  definitionId: DefinitionId
  definitionChanged: boolean
  key: RowKey
  values: RowValues
  truncatedColumns: readonly string[]
}

/** 検索の条件（送る形。値・範囲・選択肢のどれか） */
export interface SearchCondition {
  column: string
  value?: string
  from?: string
  to?: string
  values?: readonly string[]
}

/** 項目ごとの誤り（Problem Details の fieldErrors） */
export interface BusinessFieldError {
  /** カラムか検索の項目の名前（画面に結べないときは空の文字） */
  field: string
  reason: string
  /** DSL の検証の文（日英の組。PATTERN_MISMATCH のときだけ読む） */
  message?: LocalizedText
}

/** 業務データの API の誤りの応答（Problem Details から読んだもの） */
export interface BusinessProblem {
  status: number
  /** 安定した code（無ければ undefined） */
  code?: string
  fieldErrors: readonly BusinessFieldError[]
  /** 400 にも付く。無ければ false */
  definitionChanged: boolean
}

/** 検索の項目の入力（演算子ごとに使う項目だけを読む。EQUALS・CONTAINS は value、RANGE は from・to、CHOICE は values） */
export interface SearchInput {
  value: string
  from: string
  to: string
  values: readonly string[]
}

/** 検索の項目ごとの入力（カラムの名前ごと） */
export type SearchInputs = Readonly<Record<string, SearchInput>>

/** 一覧の状態（D3。テーブルごとに記憶に持つ。検索の値は道に載せない） */
export interface ListState {
  /** 送った検索の入力（入力の途中の値は持たない） */
  search: SearchInputs
  /** 並べ替え（null はサーバーの既定の並び） */
  sort: SortKey | null
  page: number
  size: PageSize
  /** 検索の領域を開いているか */
  searchExpanded: boolean
  /** 詳細から戻ったときにフォーカスを戻す行と、押した要素（ページの中の番号。行の値を持たない） */
  returnFocus?: { index: number; via: 'link' | 'actions' }
}
