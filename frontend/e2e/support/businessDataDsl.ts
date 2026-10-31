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
// 業務データの E2E（180・190・195）が共有する DSL（Intent 261029-reference-lookup-cleanup の U3、D39、基盤の設計 2節・R-02・R-05）。
// 180 の flowDsl をここへ移し、参照の表（参照先の顧客・商品と参照元の注文）とそのメニューを足した。カラムは
// businessDataSchema.sql と同じ。
// - 投入・適用は各ファイルの流れの前の API の準備で毎回行い、適用済みかを判定しない（R-02）。
// - E2E は1つのアプリを全ファイルで共有し workers: 1 で順に流れるため、最後に流れたファイルの DSL が適用中のまま残ってよい。
//   次の実行のどのファイルも、前の DSL に頼らず自分で投入・適用する（R-05）。初期管理者・見本の DSL・見本の行には触れない。
// - 表とメニューの名前は、ファイルごとの印（tag）を入れた名前を呼ぶ側が渡す。参照の表の名前は「E2ER …」で始め、180 の
//   表の名前・メニューの名前（「E2EB 品目 …」）を含まない・重ならない（180 の getByRole('link', { name }) の部分一致に当たらない）。

/** 180 の表の名前（businessDataSchema.sql と同じ） */
export const ITEM_TABLE = 'e2e_item'

/** 参照の表の名前（businessDataSchema.sql と同じ） */
export const REFERENCE_TABLES = {
  customer: 'e2e_customer',
  product: 'e2e_product',
  order: 'e2e_order',
} as const

/** 表とメニューの表示名（呼ぶ側が印を入れて渡す） */
export interface BusinessDataDslLabels {
  /** 180 の表 */
  itemTable: string
  itemMenu: string
  /** 参照の表（無ければ印から作る） */
  orderTable?: string
  orderMenu?: string
  customerTable?: string
  productTable?: string
}

/**
 * 参照の表の既定の表示名（「E2ER …」で始める）。
 *
 * @param tag ファイルごとの印
 */
export function referenceLabels(
  tag: string,
): Required<
  Pick<BusinessDataDslLabels, 'orderTable' | 'orderMenu' | 'customerTable' | 'productTable'>
> {
  return {
    orderTable: `E2ER 注文 ${tag}`,
    orderMenu: `E2ER 注文のメニュー ${tag}`,
    customerTable: `E2ER 顧客 ${tag}`,
    productTable: `E2ER 商品 ${tag}`,
  }
}

/**
 * 版 2 の DSL（実行のスキーマの4つの表とメニュー）。
 *
 * @param schema 実行のスキーマ（./gradlew e2eTest が渡す E2E_BIZ_SCHEMA）
 * @param labels 表とメニューの表示名
 * @param tag 参照の表の既定の表示名に入れる印
 */
export function businessDataDsl(
  schema: string,
  labels: BusinessDataDslLabels,
  tag: string,
): string {
  const quoted = (value: string) => JSON.stringify(value)
  const reference = { ...referenceLabels(tag), ...stripUndefined(labels) }
  return `version: 2
menus:
  - label: { ja: ${quoted(labels.itemMenu)}, en: ${quoted(labels.itemMenu)} }
    table: { schema: ${schema}, name: ${ITEM_TABLE} }
  - label: { ja: ${quoted(reference.orderMenu)}, en: ${quoted(reference.orderMenu)} }
    table: { schema: ${schema}, name: ${REFERENCE_TABLES.order} }
schemas:
  ${schema}:
    label: { ja: ${quoted(schema)}, en: ${quoted(schema)} }
    tables:
      ${ITEM_TABLE}:
        label: { ja: ${quoted(labels.itemTable)}, en: ${quoted(labels.itemTable)} }
        view: false
        primaryKey: [item_code]
        foreignKeys: []
        columns:
          item_code:
            label: { ja: 品目コード, en: Item code }
            dbType: { name: varchar, length: 20, precision: null, scale: null, nullable: false }
            formPart: text
            search: { enabled: true, operator: EQUALS, collapsed: false }
            list: { visible: true, order: 1, sortable: true }
            detail: { visible: true }
            validations:
              - { type: required, origin: DB }
              - { type: maxLength, value: 20, origin: DB }
          item_name:
            label: { ja: 品目名, en: Item name }
            dbType: { name: varchar, length: 50, precision: null, scale: null, nullable: false }
            formPart: text
            search: { enabled: true, operator: CONTAINS, collapsed: false }
            list: { visible: true, order: 2, sortable: true }
            detail: { visible: true }
            validations:
              - { type: required, origin: DB }
              - { type: maxLength, value: 50, origin: DB }
          quantity:
            label: { ja: 数量, en: Quantity }
            dbType: { name: int4, length: null, precision: null, scale: null, nullable: true }
            formPart: number
            search: { enabled: false, collapsed: false }
            list: { visible: true, order: 3, sortable: true }
            detail: { visible: true }
            validations: []
          note:
            label: { ja: 備考, en: Note }
            dbType: { name: text, length: null, precision: null, scale: null, nullable: true }
            formPart: textarea
            search: { enabled: false, collapsed: false }
            list: { visible: true, order: 4, sortable: false }
            detail: { visible: true }
            validations: []
      ${REFERENCE_TABLES.customer}:
        label: { ja: ${quoted(reference.customerTable)}, en: ${quoted(reference.customerTable)} }
        view: false
        primaryKey: [customer_code]
        foreignKeys: []
        columns:
          customer_code:
            label: { ja: 顧客コード, en: Customer code }
            dbType: { name: varchar, length: 20, precision: null, scale: null, nullable: false }
            formPart: text
            search: { enabled: false, collapsed: false }
            list: { visible: true, order: 1, sortable: true }
            detail: { visible: true }
            validations:
              - { type: required, origin: DB }
              - { type: maxLength, value: 20, origin: DB }
          customer_name:
            label: { ja: 顧客名, en: Customer name }
            dbType: { name: varchar, length: 50, precision: null, scale: null, nullable: false }
            formPart: text
            search: { enabled: false, collapsed: false }
            list: { visible: true, order: 2, sortable: true }
            detail: { visible: true }
            validations:
              - { type: required, origin: DB }
              - { type: maxLength, value: 50, origin: DB }
      ${REFERENCE_TABLES.product}:
        label: { ja: ${quoted(reference.productTable)}, en: ${quoted(reference.productTable)} }
        view: false
        primaryKey: [product_code]
        foreignKeys: []
        columns:
          product_code:
            label: { ja: 商品コード, en: Product code }
            dbType: { name: varchar, length: 20, precision: null, scale: null, nullable: false }
            formPart: text
            search: { enabled: false, collapsed: false }
            list: { visible: true, order: 1, sortable: true }
            detail: { visible: true }
            validations:
              - { type: required, origin: DB }
              - { type: maxLength, value: 20, origin: DB }
          product_name:
            label: { ja: 品名, en: Product name }
            dbType: { name: varchar, length: 200, precision: null, scale: null, nullable: false }
            formPart: text
            search: { enabled: false, collapsed: false }
            list: { visible: true, order: 2, sortable: true }
            detail: { visible: true }
            validations:
              - { type: required, origin: DB }
              - { type: maxLength, value: 200, origin: DB }
          price:
            label: { ja: 価格, en: Price }
            dbType: { name: int4, length: null, precision: null, scale: null, nullable: true }
            formPart: number
            search: { enabled: false, collapsed: false }
            list: { visible: true, order: 3, sortable: true }
            detail: { visible: true }
            validations: []
      ${REFERENCE_TABLES.order}:
        label: { ja: ${quoted(reference.orderTable)}, en: ${quoted(reference.orderTable)} }
        view: false
        primaryKey: [order_no]
        foreignKeys: []
        columns:
          order_no:
            label: { ja: 注文番号, en: Order number }
            dbType: { name: varchar, length: 20, precision: null, scale: null, nullable: false }
            formPart: text
            search: { enabled: false, collapsed: false }
            list: { visible: true, order: 1, sortable: true }
            detail: { visible: true }
            validations:
              - { type: required, origin: DB }
              - { type: maxLength, value: 20, origin: DB }
          customer_code:
            label: { ja: 顧客, en: Customer }
            dbType: { name: varchar, length: 20, precision: null, scale: null, nullable: true }
            formPart: select
            search: { enabled: false, collapsed: false }
            list: { visible: true, order: 2, sortable: false }
            detail: { visible: true }
            validations: []
            options: { source: REFERENCE, table: ${REFERENCE_TABLES.customer}, valueColumn: customer_code, labelColumn: customer_name }
          product_code:
            label: { ja: 商品, en: Product }
            dbType: { name: varchar, length: 20, precision: null, scale: null, nullable: true }
            formPart: lookup
            search: { enabled: false, collapsed: false }
            list: { visible: true, order: 3, sortable: false }
            detail: { visible: true }
            validations: []
            options:
              source: LOOKUP
              table: ${REFERENCE_TABLES.product}
              valueColumn: product_code
              labelColumn: product_name
              lookupSearch:
                - { column: product_code, operator: EQUALS }
                - { column: product_name, operator: CONTAINS }
              lookupList:
                - { column: product_code, order: 1 }
                - { column: product_name, order: 2 }
                - { column: price, order: 3 }
`
}

function stripUndefined(labels: BusinessDataDslLabels): Partial<BusinessDataDslLabels> {
  return Object.fromEntries(Object.entries(labels).filter(([, value]) => value !== undefined))
}
