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
// 他テーブル参照のテストの見本（Intent 261029-reference-lookup-cleanup の U3、unit-test-instructions.md 6節）。テストからだけ使う。
// - 形は U1 の本物の応答（code-generation-plan.md 1.1節）と同じ JSON で作る。options・picker は null のとき本文に無い
//   （NON_NULL）、listColumns の operator も本文に無い、ラベルの1件読みは page: null・cells は空。
// - XSS の見本・100 文字以上の名前・200 件の選択肢はここの1か所に置き、選択肢・ダイアログ・ラベルのテストで共有する（D37）。
// - 値はすべて架空。
import type { WireObject } from './businessDataSamples'
import type {
  PickerInfo,
  ReferenceField,
  ReferenceOption,
  ReferenceRow,
  ReferenceSource,
} from '../businessDataTypes'

/** XSS の見本（文字として出ることを確かめる。team.md の他テーブル参照の (f)） */
export const XSS_SAMPLES = [
  '<script>alert(1)</script>',
  '<img src=x onerror=alert(1)>',
  '"二重引用符"',
  "'一重引用符'",
  // リンタの no-script-url に当たらないよう組み立てる（既存のテストと同じ書き方）
  ['javascript', 'alert(1)'].join(':'),
] as const

/** 100 文字以上の長い名前（`<` を含む） */
export const LONG_LABEL = `とても長い名前の見本 <` + 'あいうえおかきくけこ'.repeat(10)

/** 2語以上の現実に近い品名 */
export const TWO_WORD_LABEL = '青い 木製の椅子'

/** 参照元の見本 */
export const SAMPLE_SOURCE: ReferenceSource = {
  schemaName: 'shop',
  tableName: 'order',
  column: 'product',
}

/** 選択肢の見本（ラベルが null の行を含む） */
export const SAMPLE_OPTIONS: readonly ReferenceOption[] = [
  { value: 'C001', label: '東京 本店' },
  { value: 'C002', label: null },
  { value: 'C003', label: '大阪 支店' },
]

/** XSS の見本をラベルに持つ選択肢 */
export const XSS_OPTIONS: readonly ReferenceOption[] = XSS_SAMPLES.map((label, index) => ({
  value: `X${index}`,
  label,
}))

/** n 件の選択肢（radio の 200 件など） */
export function manyOptions(count: number): ReferenceOption[] {
  return Array.from({ length: count }, (_, index) => ({
    value: `V${String(index + 1).padStart(3, '0')}`,
    label: `候補 ${index + 1}`,
  }))
}

/** ピッカーの情報の見本（商品コードは一致・品名は部分一致） */
export const SAMPLE_PICKER: PickerInfo = {
  searchFields: [
    {
      column: 'code',
      displayName: { ja: '商品コード', en: 'Code' },
      operator: 'EQUALS',
      valueType: 'STRING',
    },
    {
      column: 'name',
      displayName: { ja: '品名', en: 'Name' },
      operator: 'CONTAINS',
      valueType: 'STRING',
    },
  ],
  listColumns: [
    { column: 'code', displayName: { ja: '商品コード', en: 'Code' }, valueType: 'STRING' },
    { column: 'name', displayName: { ja: '品名', en: 'Name' }, valueType: 'STRING' },
    { column: 'price', displayName: { ja: '価格', en: 'Price' }, valueType: 'DECIMAL' },
  ],
}

/** 写した後の参照の項目（REFERENCE・AVAILABLE） */
export function referenceField(overrides: Partial<ReferenceField> = {}): ReferenceField {
  return {
    kind: 'REFERENCE',
    status: 'AVAILABLE',
    options: SAMPLE_OPTIONS,
    picker: null,
    ...overrides,
  }
}

/** 写した後の参照の項目（LOOKUP・AVAILABLE） */
export function lookupField(overrides: Partial<ReferenceField> = {}): ReferenceField {
  return { kind: 'LOOKUP', status: 'AVAILABLE', options: [], picker: SAMPLE_PICKER, ...overrides }
}

/** ピッカーの1行（写した後） */
export function pickerRow(index: number, overrides: Partial<ReferenceRow> = {}): ReferenceRow {
  return {
    value: `P${String(index + 1).padStart(3, '0')}`,
    label: `商品 ${index + 1}`,
    cells: [`P${String(index + 1).padStart(3, '0')}`, `商品 ${index + 1}`, '1200'],
    ...overrides,
  }
}

// ---- 本物の応答と同じ JSON ----

/** 定義のカラムの reference（REFERENCE・AVAILABLE）。options は届く */
export function wireReference(overrides: WireObject = {}): WireObject {
  return {
    kind: 'REFERENCE',
    status: 'AVAILABLE',
    options: SAMPLE_OPTIONS.map((option) => ({ value: option.value, label: option.label })),
    ...overrides,
  }
}

/** 本物の応答の picker（listColumns の operator は本文に無い） */
export function wirePicker(): WireObject {
  return {
    searchFields: SAMPLE_PICKER.searchFields.map((field) => ({ ...field })),
    listColumns: SAMPLE_PICKER.listColumns.map((column) => ({ ...column })),
  }
}

/** 定義のカラムの reference（LOOKUP・AVAILABLE）。options は本文に無い */
export function wireLookup(overrides: WireObject = {}): WireObject {
  return { kind: 'LOOKUP', status: 'AVAILABLE', picker: wirePicker(), ...overrides }
}

/** 入力不可の reference（options・picker は本文に無い） */
export function wireBlocked(
  kind: 'REFERENCE' | 'LOOKUP',
  status: 'NO_PERMISSION' | 'TOO_MANY',
): WireObject {
  return { kind, status }
}

/** 検索の応答（page は数） */
export function wireRowsPage(rows: readonly ReferenceRow[], page = 1, hasNext = false): WireObject {
  return {
    rows: rows.map((row) => ({ value: row.value, label: row.label, cells: [...row.cells] })),
    page,
    hasNext,
  }
}

/** ラベルの1件読みの応答（U1 の 4.4: rows 1件・cells は空・page: null・hasNext: false） */
export function wireLabel(value: string, label: string | null): WireObject {
  return { rows: [{ value, label, cells: [] }], page: null, hasNext: false }
}
