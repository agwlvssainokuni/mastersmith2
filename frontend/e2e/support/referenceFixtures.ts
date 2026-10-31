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
// 他テーブル参照の axe の E2E（195）の差し替えの見本（Intent 261029-reference-lookup-cleanup の U3、D38、security-design.md 3.4、
// X-3）。
// - 型は画面の側の型（src/features/tables/businessDataTypes.ts）を import type で読み、本物の応答の形（null の options・picker と
//   listColumns の operator は本文に無い。U1 の NON_NULL）に合わせた「送られてくる形」の型で縛る。
// - 項目の名前と型が本物の応答と合うことは、195 の中で取った本物の応答と shapeCompare で毎回比べる（X-3）。
// - 値は架空の固定の文字と番号。選択肢・行の値は目印 BUSINESS_VALUE_MARK を含める。
import type {
  ColumnDefinition,
  PickerListColumn,
  PickerSearchField,
  ReferenceKind,
  ReferenceOption,
  ReferenceRow,
  ReferenceStatus,
  TableDefinition,
} from '../../src/features/tables/businessDataTypes'
import { fixtureColumn, tableDefinitionFor } from './businessDataFixtures'
import { REFERENCE_TABLES } from './businessDataDsl'
import { BUSINESS_VALUE_MARK } from './secretValues'

/** 本物の応答の参照の項目（null の options・picker は本文に無い） */
export interface ReferenceFieldWire {
  kind: ReferenceKind
  status: ReferenceStatus
  options?: ReferenceOption[]
  picker?: { searchFields: PickerSearchField[]; listColumns: PickerListColumn[] }
}

/** 本物の応答のカラム */
export type ColumnWire = Omit<ColumnDefinition, 'reference'> & {
  reference: ReferenceFieldWire | null
}

/** 本物の応答の定義 */
export type DefinitionWire = Omit<TableDefinition, 'listColumns' | 'detailColumns'> & {
  listColumns: ColumnWire[]
  detailColumns: ColumnWire[]
}

/** 本物の応答の検索の結果 */
export interface ReferenceRowsWire {
  rows: ReferenceRow[]
  page: number | null
  hasNext: boolean
}

/** 顧客の項目の差し替えの形（195 の状態） */
export type CustomerVariant =
  'available' | 'no-permission' | 'too-many' | 'blocked-required' | 'radio-200'

/** 選択肢の見本（3 件） */
export const CUSTOMER_OPTIONS: ReferenceOption[] = [1, 2, 3].map((number) => ({
  value: `CUST-00${number}`,
  label: `${BUSINESS_VALUE_MARK} 顧客 0${number}`,
}))

/** radio の 200 件の選択肢 */
export function manyCustomerOptions(): ReferenceOption[] {
  return Array.from({ length: 200 }, (_, index) => {
    const number = String(index + 1).padStart(3, '0')
    return { value: `CUST-R${number}`, label: `${BUSINESS_VALUE_MARK} 候補 ${number}` }
  })
}

/** ピッカーの情報（businessDataDsl.ts の lookupSearch・lookupList と同じ） */
export const PRODUCT_PICKER: Required<ReferenceFieldWire>['picker'] = {
  searchFields: [
    {
      column: 'product_code',
      displayName: { ja: '商品コード', en: 'Product code' },
      operator: 'EQUALS',
      valueType: 'STRING',
    },
    {
      column: 'product_name',
      displayName: { ja: '品名', en: 'Product name' },
      operator: 'CONTAINS',
      valueType: 'STRING',
    },
  ],
  listColumns: [
    {
      column: 'product_code',
      displayName: { ja: '商品コード', en: 'Product code' },
      valueType: 'STRING',
    },
    {
      column: 'product_name',
      displayName: { ja: '品名', en: 'Product name' },
      valueType: 'STRING',
    },
    { column: 'price', displayName: { ja: '価格', en: 'Price' }, valueType: 'INTEGER' },
  ],
}

function customerReference(variant: CustomerVariant): ReferenceFieldWire {
  switch (variant) {
    case 'no-permission':
    case 'blocked-required':
      return { kind: 'REFERENCE', status: 'NO_PERMISSION' }
    case 'too-many':
      return { kind: 'REFERENCE', status: 'TOO_MANY' }
    case 'radio-200':
      return { kind: 'REFERENCE', status: 'AVAILABLE', options: manyCustomerOptions() }
    default:
      return { kind: 'REFERENCE', status: 'AVAILABLE', options: CUSTOMER_OPTIONS }
  }
}

/** 注文の表のカラム（businessDataDsl.ts の e2e_order と同じ並び） */
export function orderColumns(variant: CustomerVariant = 'available'): ColumnWire[] {
  const orderNo: ColumnWire = {
    ...fixtureColumn(
      'order_no',
      { ja: '注文番号', en: 'Order number' },
      {
        editableOnCreate: true,
        formPart: 'text',
        required: true,
        nullable: false,
        maxLength: 20,
      },
    ),
    reference: null,
  }
  const customer: ColumnWire = {
    ...fixtureColumn(
      'customer_code',
      { ja: '顧客', en: 'Customer' },
      {
        editableOnCreate: true,
        editableOnUpdate: true,
        formPart: variant === 'radio-200' ? 'radio' : 'select',
        required: variant === 'blocked-required',
        nullable: variant !== 'blocked-required',
      },
    ),
    reference: customerReference(variant),
  }
  const product: ColumnWire = {
    ...fixtureColumn(
      'product_code',
      { ja: '商品', en: 'Product' },
      {
        editableOnCreate: true,
        editableOnUpdate: true,
        formPart: 'lookup',
      },
    ),
    reference: { kind: 'LOOKUP', status: 'AVAILABLE', picker: PRODUCT_PICKER },
  }
  return [orderNo, customer, product]
}

/**
 * 注文の表の定義（作成の用途。D1）。
 *
 * @param schema 実行のスキーマ
 * @param workRoleId 作業ロールの ID
 * @param variant 顧客の項目の差し替えの形
 */
export function orderDefinition(
  schema: string,
  workRoleId: number | null,
  variant: CustomerVariant = 'available',
): DefinitionWire {
  const base = tableDefinitionFor({
    schema,
    table: REFERENCE_TABLES.order,
    displayName: { ja: '注文', en: 'Orders' },
    workRoleId,
  })
  const columns = orderColumns(variant)
  return {
    ...base,
    hasPrimaryKey: true,
    primaryKeyColumns: ['order_no'],
    operations: ['LIST', 'DETAIL', 'CREATE', 'UPDATE'],
    writeConfigured: true,
    rowLabelColumns: ['order_no'],
    listColumns: columns,
    detailColumns: columns,
    searchFields: [],
    sortableColumns: ['order_no'],
    defaultSort: [],
  }
}

/** ピッカーの1行（目印を含む） */
export function productRow(number: number): ReferenceRow {
  const code = `PROD-${String(number).padStart(3, '0')}`
  const name = `${BUSINESS_VALUE_MARK} 商品 ${String(number).padStart(2, '0')}`
  return { value: code, label: name, cells: [code, name, String(number * 100)] }
}

/** 検索の結果の見本 */
export function productPage(rows: ReferenceRow[], page = 1, hasNext = false): ReferenceRowsWire {
  return { rows, page, hasNext }
}

/** 誤りの応答の見本（Problem Details と code・fieldErrors） */
export function problemBody(
  status: number,
  code: string,
  fieldErrors: { field: string; reason: string }[] = [],
): Readonly<Record<string, unknown>> {
  return { type: 'about:blank', title: 'Problem', status, code, fieldErrors }
}
