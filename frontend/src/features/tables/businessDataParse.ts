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
// 業務データの API の応答を unknown から型へ写す純粋な関数（Intent 261016-business-data-views の U3 の L2、D5、
// U3-NFR4.24、計画 1.5）。
// - 例外を投げず、形が違えば undefined を返す（画面は通信の失敗の表示にする）。行の値を含む Error を作らない（NFR4.2）。
// - 列挙はすべて既知の一覧と照らす。知らない値は形の崩れ（参照の status・kind だけは入力不可に倒す。Intent
//   261029-reference-lookup-cleanup の U3 の D3）。
// - 値は文字か null のまま写し、数に直さない（BIGINT・DECIMAL の桁を失わない）。
// - 写すのは型の項目だけ。知らない項目は無視する。カラムの名前は利用者の入力（DSL）のため、写すときは
//   Object.fromEntries で作り（`__proto__` などの名前でも物の形を変えない）、読むときは Object.hasOwn で確かめる。
// - 値が null になりうる項目（listFormat・width・restrictionReason など）は、項目が無いときも null として受ける
//   （サーバーの JSON の null の出し方に頼らない）。
import type { ApiError } from '../../shared/api-client/apiError'
import {
  FORM_PARTS,
  LIST_FORMATS,
  OPERATIONS,
  PAGE_SIZES,
  REFERENCE_KINDS,
  REFERENCE_STATUSES,
  RESTRICTION_REASONS,
  SEARCH_OPERATORS,
  SORT_DIRECTIONS,
  VALUE_TYPES,
  type PickerInfo,
  type PickerListColumn,
  type PickerSearchField,
  type ReferenceField,
  type ReferenceLabel,
  type ReferenceOption,
  type ReferenceRow,
  type ReferenceRowPage,
  type ReferenceStatus,
  type BusinessFieldError,
  type BusinessProblem,
  type CellValue,
  type Choice,
  type ColumnDefinition,
  type DefinitionId,
  type LocalizedText,
  type PageSize,
  type RowDetail,
  type RowItem,
  type RowKey,
  type RowPage,
  type RowValues,
  type SearchField,
  type SortKey,
  type TableDefinition,
} from './businessDataTypes'

/** ページの番号の上限（U1 の PageRequest.MAX_PAGE） */
export const MAX_PAGE = 1_000_000

type Json = Readonly<Record<string, unknown>>

/** 写せなかった印（undefined と区別するため。null を許す項目に使う） */
const INVALID: unique symbol = Symbol('invalid')
type Invalid = typeof INVALID

function isObject(value: unknown): value is Json {
  return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function oneOf<T extends string>(list: readonly T[], value: unknown): value is T {
  return typeof value === 'string' && (list as readonly string[]).includes(value)
}

function isSafeNonNegativeInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value) && value >= 0
}

function parseText(value: unknown): LocalizedText | undefined {
  if (!isObject(value) || typeof value.ja !== 'string' || typeof value.en !== 'string') {
    return undefined
  }
  return { ja: value.ja, en: value.en }
}

function parseStrings(value: unknown): readonly string[] | undefined {
  if (!Array.isArray(value) || !value.every((item) => typeof item === 'string')) {
    return undefined
  }
  return [...(value as string[])]
}

/** 配列の各要素を写す。1つでも写せなければ undefined。 */
function parseList<T>(value: unknown, parse: (item: unknown) => T | undefined): T[] | undefined {
  if (!Array.isArray(value)) {
    return undefined
  }
  const result: T[] = []
  for (const item of value as unknown[]) {
    const parsed = parse(item)
    if (parsed === undefined) {
      return undefined
    }
    result.push(parsed)
  }
  return result
}

/** null を許す項目（無い・null は null、型が違えば INVALID） */
function nullable<T>(value: unknown, check: (item: unknown) => item is T): T | null | Invalid {
  if (value === undefined || value === null) {
    return null
  }
  return check(value) ? value : INVALID
}

function isString(value: unknown): value is string {
  return typeof value === 'string'
}

function isInteger(value: unknown): value is number {
  return typeof value === 'number' && Number.isSafeInteger(value)
}

function parseDefinitionId(value: unknown): DefinitionId | undefined {
  if (!isObject(value) || typeof value.dslHash !== 'string') {
    return undefined
  }
  const workRoleId = nullable(value.workRoleId, isInteger)
  if (workRoleId === INVALID) {
    return undefined
  }
  return { dslHash: value.dslHash, workRoleId }
}

function parseChoice(value: unknown): Choice | undefined {
  if (!isObject(value) || typeof value.value !== 'string') {
    return undefined
  }
  const label = parseText(value.label)
  return label === undefined ? undefined : { value: value.value, label }
}

/** 検証の材料（任意の項目。無ければ写さない。あるのに型が違えば INVALID） */
function parseValidation(value: Json): Partial<ColumnDefinition> | Invalid {
  const result: Partial<ColumnDefinition> = {}
  if (value.formPart !== undefined) {
    const formPart = nullable(value.formPart, (item): item is ColumnDefinition['formPart'] =>
      oneOf(FORM_PARTS, item),
    )
    if (formPart === INVALID) {
      return INVALID
    }
    result.formPart = formPart
  }
  for (const name of ['required', 'nullable', 'hasPattern'] as const) {
    const item = value[name]
    if (item !== undefined) {
      if (typeof item !== 'boolean') {
        return INVALID
      }
      result[name] = item
    }
  }
  for (const name of ['minLength', 'maxLength', 'precision', 'scale'] as const) {
    if (value[name] !== undefined) {
      const item = nullable(value[name], isInteger)
      if (item === INVALID) {
        return INVALID
      }
      result[name] = item
    }
  }
  for (const name of ['min', 'max'] as const) {
    if (value[name] !== undefined) {
      const item = nullable(value[name], isString)
      if (item === INVALID) {
        return INVALID
      }
      result[name] = item
    }
  }
  if (value.choices !== undefined) {
    const choices = parseList(value.choices, parseChoice)
    if (choices === undefined) {
      return INVALID
    }
    result.choices = choices
  }
  if (value.reference !== undefined) {
    const reference = value.reference === null ? null : parseReference(value.reference)
    if (reference === undefined) {
      return INVALID
    }
    result.reference = reference
  }
  // `lookup` は LOOKUP の参照の項目にだけ届く。reference が無い・REFERENCE の組は届かない形（D5・D3）。
  if (
    result.formPart === 'lookup' &&
    (result.reference === undefined ||
      result.reference === null ||
      (isObject(value.reference) && value.reference.kind === 'REFERENCE'))
  ) {
    return INVALID
  }
  return result
}

// ---- 他テーブル参照（Intent 261029-reference-lookup-cleanup の U3、D3・D18・X-1） ----

/** ピッカーの1ページの行の数（U1 の D1） */
export const PICKER_PAGE_SIZE = 20

function parseReferenceOption(value: unknown): ReferenceOption | undefined {
  if (!isObject(value) || typeof value.value !== 'string') {
    return undefined
  }
  const label = nullable(value.label, isString)
  return label === INVALID ? undefined : { value: value.value, label }
}

function parsePickerSearchField(value: unknown): PickerSearchField | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const displayName = parseText(value.displayName)
  if (
    typeof value.column !== 'string' ||
    displayName === undefined ||
    (value.operator !== 'EQUALS' && value.operator !== 'CONTAINS') ||
    !oneOf(VALUE_TYPES, value.valueType)
  ) {
    return undefined
  }
  return {
    column: value.column,
    displayName,
    operator: value.operator,
    valueType: value.valueType,
  }
}

/** 結果の列（operator は本文に無い。届いても読まない。X-1） */
function parsePickerListColumn(value: unknown): PickerListColumn | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const displayName = parseText(value.displayName)
  if (
    typeof value.column !== 'string' ||
    displayName === undefined ||
    !oneOf(VALUE_TYPES, value.valueType)
  ) {
    return undefined
  }
  return { column: value.column, displayName, valueType: value.valueType }
}

function parsePickerInfo(value: unknown): PickerInfo | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const searchFields = parseList(value.searchFields, parsePickerSearchField)
  const listColumns = parseList(value.listColumns, parsePickerListColumn)
  if (searchFields === undefined || listColumns === undefined) {
    return undefined
  }
  return { searchFields, listColumns }
}

/**
 * カラムの参照の項目を写す。形が違えば undefined（通信の失敗）。
 * - `options`・`picker` は null のとき本文に無い（NON_NULL）。欠けは空の並び・null と読み、REFERENCE・AVAILABLE の
 *   `options` の欠けと LOOKUP・AVAILABLE の `picker` の欠けだけを形の崩れにする（X-1）。
 * - 知らない `status` は NO_PERMISSION に倒す。知らない `kind`（`FIXED` を含む）も同じく入力不可にする（D3）。
 *
 * @param value 参照の項目の本文
 */
export function parseReference(value: unknown): ReferenceField | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const knownKind = oneOf(REFERENCE_KINDS, value.kind)
  const knownStatus = oneOf(REFERENCE_STATUSES, value.status)
  const options =
    value.options === undefined || value.options === null
      ? null
      : parseList(value.options, parseReferenceOption)
  const picker =
    value.picker === undefined || value.picker === null ? null : parsePickerInfo(value.picker)
  if (options === undefined || picker === undefined) {
    return undefined
  }
  const available = knownStatus && value.status === 'AVAILABLE'
  if (knownKind && available && value.kind === 'REFERENCE' && options === null) {
    return undefined
  }
  if (knownKind && available && value.kind === 'LOOKUP' && picker === null) {
    return undefined
  }
  if (!knownKind) {
    // 知らない種類は参照の入力として扱わず、権限不足と同じ入力不可にする（D3）。
    return { kind: 'REFERENCE', status: 'NO_PERMISSION', options: [], picker: null }
  }
  return {
    kind: value.kind as ReferenceField['kind'],
    status: knownStatus ? (value.status as ReferenceStatus) : 'NO_PERMISSION',
    options: options ?? [],
    picker,
  }
}

function parseReferenceRow(value: unknown): ReferenceRow | undefined {
  if (!isObject(value) || typeof value.value !== 'string') {
    return undefined
  }
  const label = nullable(value.label, isString)
  const cells = parseList(value.cells, (item) =>
    typeof item === 'string' || item === null ? { item } : undefined,
  )
  if (label === INVALID || cells === undefined) {
    return undefined
  }
  return { value: value.value, label, cells: cells.map(({ item }) => item) }
}

/**
 * 参照先の検索の応答を写す。形が違えば undefined。page は 1〜MAX_PAGE の整数（null は形の崩れ）、行は 20 件まで、
 * hasNext が真なら行はちょうど 20 件（ページ送りの総ページを今のページより小さくしない。D25）。
 *
 * @param body 応答の本文
 */
export function parseReferenceRowPage(body: unknown): ReferenceRowPage | undefined {
  if (!isObject(body)) {
    return undefined
  }
  const rows = parseList(body.rows, parseReferenceRow)
  if (
    rows === undefined ||
    !isSafeNonNegativeInteger(body.page) ||
    body.page < 1 ||
    body.page > MAX_PAGE ||
    typeof body.hasNext !== 'boolean' ||
    rows.length > PICKER_PAGE_SIZE ||
    (body.hasNext && rows.length !== PICKER_PAGE_SIZE)
  ) {
    return undefined
  }
  return { rows, page: body.page, hasNext: body.hasNext }
}

/**
 * ラベルの1件読みの応答を写す（検索の写しと別。D18・R-01）。page・hasNext・cells は見ず、rows がちょうど1件で
 * value（文字）・label（文字か null）の形が正しいことだけを確かめる。
 *
 * @param body 応答の本文
 */
export function parseReferenceLabel(body: unknown): ReferenceLabel | undefined {
  if (!isObject(body) || !Array.isArray(body.rows) || body.rows.length !== 1) {
    return undefined
  }
  const row: unknown = body.rows[0]
  if (!isObject(row) || typeof row.value !== 'string') {
    return undefined
  }
  const label = nullable(row.label, isString)
  return label === INVALID ? undefined : { value: row.value, label }
}

/**
 * カラムの定義を写す。形が違えば undefined。
 *
 * @param value カラムの定義の本文
 */
export function parseColumnDefinition(value: unknown): ColumnDefinition | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const displayName = parseText(value.displayName)
  const listFormat = nullable(value.listFormat, (item): item is ColumnDefinition['listFormat'] =>
    oneOf(LIST_FORMATS, item),
  )
  const width = nullable(
    value.width,
    (item): item is number => isInteger(item) && (item as number) > 0,
  )
  const validation = parseValidation(value)
  if (
    typeof value.column !== 'string' ||
    displayName === undefined ||
    !oneOf(VALUE_TYPES, value.valueType) ||
    listFormat === INVALID ||
    width === INVALID ||
    typeof value.editableOnCreate !== 'boolean' ||
    typeof value.editableOnUpdate !== 'boolean' ||
    typeof value.masked !== 'boolean' ||
    validation === INVALID
  ) {
    return undefined
  }
  return {
    column: value.column,
    displayName,
    valueType: value.valueType,
    listFormat,
    width,
    editableOnCreate: value.editableOnCreate,
    editableOnUpdate: value.editableOnUpdate,
    masked: value.masked,
    ...validation,
  }
}

function parseSearchField(value: unknown): SearchField | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const displayName = parseText(value.displayName)
  const defaultValue = nullable(value.defaultValue, isString)
  const options = parseList(value.options, parseChoice)
  if (
    typeof value.column !== 'string' ||
    displayName === undefined ||
    !oneOf(SEARCH_OPERATORS, value.operator) ||
    !oneOf(VALUE_TYPES, value.valueType) ||
    defaultValue === INVALID ||
    typeof value.collapsed !== 'boolean' ||
    options === undefined
  ) {
    return undefined
  }
  return {
    column: value.column,
    displayName,
    operator: value.operator,
    valueType: value.valueType,
    defaultValue,
    collapsed: value.collapsed,
    options,
  }
}

function parseSortKey(value: unknown): SortKey | undefined {
  if (
    !isObject(value) ||
    typeof value.column !== 'string' ||
    !oneOf(SORT_DIRECTIONS, value.direction)
  ) {
    return undefined
  }
  return { column: value.column, direction: value.direction }
}

/**
 * 入力にするカラム（作成か更新で入力できる詳細のカラム）が、フォームを作る材料を持つか（機能設計の承認の場の U2 R-07・U3 R-09、
 * D19、U3-NFR4.24）。formPart・required・nullable のどれかが欠ければ形の崩れ（黙って文字の入力にしない）。
 * 入力にしないカラムの `formPart: null` は正しい形。知らない formPart と、参照の無い `lookup` は parseValidation が先に弾く。
 *
 * @param column 写したカラムの定義
 */
export function hasWriteMaterial(column: ColumnDefinition): boolean {
  if (!column.editableOnCreate && !column.editableOnUpdate) {
    return true
  }
  return (
    column.formPart !== undefined &&
    column.formPart !== null &&
    typeof column.required === 'boolean' &&
    typeof column.nullable === 'boolean'
  )
}

/** 要求したスキーマ名とテーブル名 */
export interface RequestedTable {
  schemaName: string
  tableName: string
}

/**
 * 定義の応答を写す。形が違うか、要求したテーブルと違えば undefined。
 *
 * @param body 応答の本文
 * @param requested 要求したスキーマ名とテーブル名
 */
export function parseTableDefinition(
  body: unknown,
  requested: RequestedTable,
): TableDefinition | undefined {
  if (!isObject(body)) {
    return undefined
  }
  const definitionId = parseDefinitionId(body.definitionId)
  const displayName = parseText(body.displayName)
  const primaryKeyColumns = parseStrings(body.primaryKeyColumns)
  const operations = parseList(body.operations, (item) =>
    oneOf(OPERATIONS, item) ? item : undefined,
  )
  const restrictionReason = nullable(
    body.restrictionReason,
    (item): item is TableDefinition['restrictionReason'] => oneOf(RESTRICTION_REASONS, item),
  )
  const rowLabelColumns = parseStrings(body.rowLabelColumns)
  const listColumns = parseList(body.listColumns, parseColumnDefinition)
  const detailColumns = parseList(body.detailColumns, parseColumnDefinition)
  const searchFields = parseList(body.searchFields, parseSearchField)
  const sortableColumns = parseStrings(body.sortableColumns)
  const defaultSort = parseList(body.defaultSort, parseSortKey)
  if (
    definitionId === undefined ||
    typeof body.definitionChanged !== 'boolean' ||
    body.schema !== requested.schemaName ||
    body.table !== requested.tableName ||
    displayName === undefined ||
    typeof body.view !== 'boolean' ||
    typeof body.hasPrimaryKey !== 'boolean' ||
    primaryKeyColumns === undefined ||
    operations === undefined ||
    restrictionReason === INVALID ||
    typeof body.writeConfigured !== 'boolean' ||
    rowLabelColumns === undefined ||
    listColumns === undefined ||
    detailColumns === undefined ||
    searchFields === undefined ||
    sortableColumns === undefined ||
    defaultSort === undefined ||
    !detailColumns.every(hasWriteMaterial)
  ) {
    return undefined
  }
  return {
    definitionId,
    definitionChanged: body.definitionChanged,
    schema: requested.schemaName,
    table: requested.tableName,
    displayName,
    view: body.view,
    hasPrimaryKey: body.hasPrimaryKey,
    primaryKeyColumns,
    operations,
    restrictionReason,
    writeConfigured: body.writeConfigured,
    rowLabelColumns,
    listColumns,
    detailColumns,
    searchFields,
    sortableColumns,
    defaultSort,
  }
}

/** 名前と文字の組（主キー）を写す。 */
function parseKey(value: unknown): RowKey | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const entries = Object.entries(value)
  if (!entries.every(([, item]) => typeof item === 'string')) {
    return undefined
  }
  return Object.fromEntries(entries) as RowKey
}

/** 名前と値（文字か null）の組を写す。 */
function parseValues(value: unknown): RowValues | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const entries = Object.entries(value)
  if (!entries.every(([, item]) => typeof item === 'string' || item === null)) {
    return undefined
  }
  return Object.fromEntries(entries) as Readonly<Record<string, CellValue>>
}

function parseRowItem(value: unknown): RowItem | undefined {
  if (!isObject(value)) {
    return undefined
  }
  const key = value.key === undefined || value.key === null ? null : parseKey(value.key)
  const values = parseValues(value.values)
  const truncatedColumns = parseStrings(value.truncatedColumns)
  if (key === undefined || values === undefined || truncatedColumns === undefined) {
    return undefined
  }
  return { key, values, truncatedColumns }
}

/**
 * 一覧と検索の応答を写す。形が違えば undefined。page・total が Number の安全な範囲を外れたら形の崩れ。
 *
 * @param body 応答の本文
 */
export function parseRowPage(body: unknown): RowPage | undefined {
  if (!isObject(body)) {
    return undefined
  }
  const definitionId = parseDefinitionId(body.definitionId)
  const items = parseList(body.items, parseRowItem)
  if (
    definitionId === undefined ||
    typeof body.definitionChanged !== 'boolean' ||
    items === undefined ||
    !isSafeNonNegativeInteger(body.page) ||
    body.page < 1 ||
    body.page > MAX_PAGE ||
    !(PAGE_SIZES as readonly unknown[]).includes(body.size) ||
    !isSafeNonNegativeInteger(body.total) ||
    items.length > (body.size as number)
  ) {
    return undefined
  }
  return {
    definitionId,
    definitionChanged: body.definitionChanged,
    items,
    page: body.page,
    size: body.size as PageSize,
    total: body.total,
  }
}

/**
 * 詳細の応答を写す。形が違えば undefined。
 *
 * @param body 応答の本文
 */
export function parseRowDetail(body: unknown): RowDetail | undefined {
  if (!isObject(body)) {
    return undefined
  }
  const definitionId = parseDefinitionId(body.definitionId)
  const key = parseKey(body.key)
  const values = parseValues(body.values)
  const truncatedColumns = parseStrings(body.truncatedColumns)
  if (
    definitionId === undefined ||
    typeof body.definitionChanged !== 'boolean' ||
    key === undefined ||
    values === undefined ||
    truncatedColumns === undefined
  ) {
    return undefined
  }
  return {
    definitionId,
    definitionChanged: body.definitionChanged,
    key,
    values,
    truncatedColumns,
  }
}

function parseFieldError(value: unknown): BusinessFieldError | undefined {
  if (!isObject(value) || typeof value.field !== 'string' || typeof value.reason !== 'string') {
    return undefined
  }
  const message = parseText(value.message)
  return message === undefined
    ? { field: value.field, reason: value.reason }
    : { field: value.field, reason: value.reason, message }
}

/**
 * API の失敗を、業務データの誤りの形に写す。サーバーの応答でなければ（通信の失敗など）undefined。
 * fieldErrors の形の違う要素は捨てる。definitionChanged は true のときだけ true。
 *
 * @param error API の呼び出しの失敗
 */
export function parseBusinessProblem(error: unknown): BusinessProblem | undefined {
  if (!isObject(error) || error.kind !== 'response' || typeof error.status !== 'number') {
    return undefined
  }
  const apiError = error as unknown as Extract<ApiError, { kind: 'response' }>
  const problem: unknown = apiError.problem
  const fieldErrors: BusinessFieldError[] = []
  let definitionChanged = false
  if (isObject(problem)) {
    if (Array.isArray(problem.fieldErrors)) {
      for (const item of problem.fieldErrors as unknown[]) {
        const parsed = parseFieldError(item)
        if (parsed !== undefined) {
          fieldErrors.push(parsed)
        }
      }
    }
    definitionChanged = problem.definitionChanged === true
  }
  const code = typeof apiError.code === 'string' ? apiError.code : undefined
  return code === undefined
    ? { status: apiError.status, fieldErrors, definitionChanged }
    : { status: apiError.status, code, fieldErrors, definitionChanged }
}

/**
 * 書き込みの成功の応答を写す（作成の 201 の `RowCreatedResponse`・更新と削除の 200 の `RowWrittenResponse`）。
 * 作成の `key` は、DETAIL が許され主キーを作れたときだけ来る（U2 の G8・R-03）。`key` が無い・形が違うときは null
 * （一覧へ戻る。D24）。成功の状態コードを受けた後の写しのため、本文の形が違っても undefined にはしない
 * （保存したとみなす。計画 4.3 の R-03）。
 *
 * @param body 応答の本文（読めなかったときは undefined）
 */
export function parseCreatedKey(body: unknown): RowKey | null {
  if (!isObject(body) || body.key === undefined || body.key === null) {
    return null
  }
  const key = parseKey(body.key)
  return key === undefined || Object.keys(key).length === 0 ? null : key
}
