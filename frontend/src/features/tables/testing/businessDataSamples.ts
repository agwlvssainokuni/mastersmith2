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
// 業務データの画面のテストの見本の応答（テストからだけ使う。アプリの画面からは読み込まない。unit-test-instructions.md 6節）。
// 形は U1・U2 のサーバーの応答（計画 1.5）と同じ JSON で作り、unknown のまま写しの関数に渡す。値は架空のものだけ。

/** JSON の物 */
export type WireObject = Record<string, unknown>

/** 見本のスキーマ名とテーブル名 */
export const SAMPLE_REF = { schemaName: 'shop', tableName: 'item' } as const

/** カラムの定義の見本（検証の材料を含む、サーバーと同じ形） */
export function wireColumn(column: string, overrides: WireObject = {}): WireObject {
  return {
    column,
    displayName: { ja: `${column}の名前`, en: `${column} name` },
    valueType: 'STRING',
    listFormat: null,
    width: null,
    editableOnCreate: false,
    editableOnUpdate: false,
    masked: false,
    formPart: null,
    required: false,
    nullable: true,
    minLength: null,
    maxLength: null,
    precision: null,
    scale: null,
    min: null,
    max: null,
    choices: [],
    hasPattern: false,
    ...overrides,
  }
}

/** 検索の項目の見本 */
export function wireSearchField(column: string, overrides: WireObject = {}): WireObject {
  return {
    column,
    displayName: { ja: `${column}の名前`, en: `${column} name` },
    operator: 'EQUALS',
    valueType: 'STRING',
    defaultValue: null,
    collapsed: false,
    options: [],
    ...overrides,
  }
}

/** 見本の定義の識別 */
export const SAMPLE_DEFINITION_ID = { dslHash: 'hash-1', workRoleId: 1 }

/** 定義の応答の見本（商品のテーブル。主キー code、表示名 name） */
export function wireDefinition(overrides: WireObject = {}): WireObject {
  const columns = [
    wireColumn('code', { displayName: { ja: '商品コード', en: 'Code' } }),
    wireColumn('name', { displayName: { ja: '商品名', en: 'Name' } }),
    wireColumn('price', {
      displayName: { ja: '価格', en: 'Price' },
      valueType: 'DECIMAL',
      listFormat: 'NUMBER_GROUPED',
    }),
  ]
  return {
    definitionId: SAMPLE_DEFINITION_ID,
    definitionChanged: false,
    schema: SAMPLE_REF.schemaName,
    table: SAMPLE_REF.tableName,
    displayName: { ja: '商品', en: 'Items' },
    view: false,
    hasPrimaryKey: true,
    primaryKeyColumns: ['code'],
    operations: ['LIST', 'DETAIL'],
    restrictionReason: null,
    writeConfigured: false,
    rowLabelColumns: ['code'],
    listColumns: columns,
    detailColumns: columns,
    searchFields: [
      wireSearchField('code', { displayName: { ja: '商品コード', en: 'Code' } }),
      wireSearchField('name', { displayName: { ja: '商品名', en: 'Name' }, operator: 'CONTAINS' }),
    ],
    sortableColumns: ['code', 'name'],
    defaultSort: [{ column: 'code', direction: 'ASC' }],
    ...overrides,
  }
}

/** 一覧の1行の見本 */
export function wireRow(code: string, overrides: WireObject = {}): WireObject {
  return {
    key: { code },
    values: { code, name: `見本の品 ${code}`, price: '1200' },
    truncatedColumns: [],
    ...overrides,
  }
}

/** 一覧の応答の見本 */
export function wirePage(items: readonly WireObject[], overrides: WireObject = {}): WireObject {
  return {
    definitionId: SAMPLE_DEFINITION_ID,
    definitionChanged: false,
    items,
    page: 1,
    size: 20,
    total: items.length,
    ...overrides,
  }
}

/** 詳細の応答の見本 */
export function wireDetail(code: string, overrides: WireObject = {}): WireObject {
  return {
    definitionId: SAMPLE_DEFINITION_ID,
    definitionChanged: false,
    key: { code },
    values: { code, name: `見本の品 ${code}`, price: '1200' },
    truncatedColumns: [],
    ...overrides,
  }
}

/** JSON の応答を作る */
export function jsonResponse(status: number, body: unknown): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json' },
  })
}
