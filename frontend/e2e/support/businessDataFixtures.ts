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
// 業務データの API の型を付けた1つの見本（Intent 261016-business-data-views の U3 の L10、NFR 要件 Q2: A、計画 4.8 の P-3、
// unit-test-instructions.md 6節）。
// - 型は画面の側の型（src/features/tables/businessDataTypes.ts）を import type で読み、見本はその型で縛る。項目の名前と型が
//   本物の応答と合うことは、180（B6）の中で shapeCompare で毎回確かめる（project.md の学び）。
// - 値は架空の固定の文字と番号だけ。見本の行の値（主キー・セルの値）は、すべて目印 BUSINESS_VALUE_MARK を含む形で作り、
//   報告に漏れたら secretValues の確かめで見つかるようにする（security-design.md の 8節）。
// - 列の名前も架空の固定の名前（code・name・note・price と c001〜c100）。行の数・列の数・長い値は引数で決める。
import type { NavigationResponse } from '../../src/app/navigation/navigationApi'
import type {
  ColumnDefinition,
  RowDetail,
  RowItem,
  RowPage,
  SearchField,
  TableDefinition,
} from '../../src/features/tables/businessDataTypes'
import { FRAME_SCHEMA, LEAF_LABEL, LEAF_TABLE } from './navTreeFixtures'
import { BUSINESS_VALUE_MARK } from './secretValues'

/** 見本の DSL の識別 */
export const FIXTURE_DSL_HASH = 'e2e-fixture-dsl-hash'

/** 広い表の見本のテーブル */
export const WIDE_SCHEMA = 'E2E_BIZ'
export const WIDE_TABLE = 'WIDE_ITEM'

/** 広い表の表示名（長い名前） */
export const WIDE_LABEL = {
  ja: `測りの広い表の長い表示名${'い'.repeat(30)}`,
  en: `Wide table with a long display name ${'w'.repeat(10)}`,
} as const

/** 詳細で読む長い値の字数（U1 の詳細の上限） */
export const LONG_VALUE_LENGTH = 10_000

/** カラムの見本（検証の材料を含む、サーバーと同じ形） */
export function fixtureColumn(
  column: string,
  displayName: { ja: string; en: string },
  overrides: Partial<ColumnDefinition> = {},
): ColumnDefinition {
  return {
    column,
    displayName,
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

function searchField(
  column: string,
  ja: string,
  en: string,
  overrides: Partial<SearchField> = {},
): SearchField {
  return {
    column,
    displayName: { ja, en },
    operator: 'EQUALS',
    valueType: 'STRING',
    defaultValue: null,
    collapsed: false,
    options: [],
    ...overrides,
  }
}

/** 見本の主キーの値（目印を含む） */
export function fixtureKey(number: number): string {
  return `${BUSINESS_VALUE_MARK}-K${String(number).padStart(4, '0')}`
}

/** 見本の定義を作る */
function definitionOf(
  schema: string,
  table: string,
  displayName: { ja: string; en: string },
  columns: ColumnDefinition[],
  workRoleId: number | null,
): TableDefinition {
  return {
    definitionId: { dslHash: FIXTURE_DSL_HASH, workRoleId },
    definitionChanged: false,
    schema,
    table,
    displayName,
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
      searchField('code', '商品コード', 'Code'),
      searchField('name', '商品名', 'Name', { operator: 'CONTAINS' }),
    ],
    sortableColumns: ['code', 'name'],
    defaultSort: [{ column: 'code', direction: 'ASC' }],
  }
}

/** 4 つの列（商品コード・長い表示名の商品名・備考・価格）の定義を、テーブルの名前と表示名を決めて作る */
export function tableDefinitionFor(options: {
  schema: string
  table: string
  displayName: { ja: string; en: string }
  workRoleId: number | null
}): TableDefinition {
  return definitionOf(
    options.schema,
    options.table,
    { ...options.displayName },
    [
      fixtureColumn('code', { ja: '商品コード', en: 'Code' }, { width: 160 }),
      fixtureColumn('name', { ja: `商品名の長い表示名${'え'.repeat(20)}`, en: 'Name' }),
      fixtureColumn('note', { ja: '備考', en: 'Note' }),
      fixtureColumn(
        'price',
        { ja: '価格', en: 'Price' },
        { valueType: 'DECIMAL', listFormat: 'NUMBER_GROUPED' },
      ),
    ],
    options.workRoleId,
  )
}

/** 末端のテーブル（160 のメニューの末端）の定義。列は 4 つ（NULL を含む行を作る） */
export function leafDefinition(workRoleId: number | null): TableDefinition {
  return tableDefinitionFor({
    schema: FRAME_SCHEMA,
    table: LEAF_TABLE,
    displayName: LEAF_LABEL,
    workRoleId,
  })
}

/** 広い表の定義（列の数 columns。c001〜） */
export function wideDefinition(columns: number, workRoleId: number | null): TableDefinition {
  const list = Array.from({ length: columns }, (_, index) => {
    const number = String(index + 1).padStart(3, '0')
    if (index === 0) {
      return fixtureColumn('code', { ja: '商品コード', en: 'Code' }, { width: 200 })
    }
    if (index === 1) {
      return fixtureColumn('name', { ja: `商品名の長い表示名${'お'.repeat(20)}`, en: 'Name' })
    }
    return fixtureColumn(
      `c${number}`,
      { ja: `列${number}`, en: `Column ${number}` },
      { width: 160 },
    )
  })
  return definitionOf(WIDE_SCHEMA, WIDE_TABLE, { ...WIDE_LABEL }, list, workRoleId)
}

/** 1行の値（7 行ごとに3列目を NULL にする。値は目印を含む） */
function rowValues(definition: TableDefinition, number: number): Record<string, string | null> {
  return Object.fromEntries(
    definition.listColumns.map((column, index): [string, string | null] => {
      if (column.column === 'code') {
        return [column.column, fixtureKey(number)]
      }
      if (column.column === 'price') {
        return [column.column, String(1000 + number)]
      }
      if (index === 2 && number % 7 === 0) {
        return [column.column, null]
      }
      return [column.column, `${BUSINESS_VALUE_MARK} 見本の値 ${number}-${index}`]
    }),
  )
}

/** 一覧の1行 */
function rowItem(definition: TableDefinition, number: number): RowItem {
  return {
    key: { code: fixtureKey(number) },
    values: rowValues(definition, number),
    truncatedColumns: [],
  }
}

/**
 * 一覧の見本（page ページ目の size 件。全体は total 件）。
 *
 * @param definition 定義
 * @param page ページの番号
 * @param size 1ページの件数
 * @param total 全体の件数
 */
export function fixturePage(
  definition: TableDefinition,
  page: number,
  size: 20 | 50 | 100,
  total: number,
): RowPage {
  const start = (page - 1) * size
  const count = Math.max(0, Math.min(size, total - start))
  return {
    definitionId: definition.definitionId,
    definitionChanged: false,
    items: Array.from({ length: count }, (_, index) => rowItem(definition, start + index + 1)),
    page,
    size,
    total,
  }
}

/** 詳細の見本（2列目に 10,000 字の値を入れる） */
export function longDetail(definition: TableDefinition, number: number): RowDetail {
  const values = rowValues(definition, number)
  const second = definition.detailColumns[1]?.column
  if (second !== undefined) {
    const head = `${BUSINESS_VALUE_MARK} 長い値 `
    values[second] = head + 'あ'.repeat(LONG_VALUE_LENGTH - head.length)
  }
  return {
    definitionId: definition.definitionId,
    definitionChanged: false,
    key: { code: fixtureKey(number) },
    values,
    truncatedColumns: [],
  }
}

/** 広い表だけを末端に持つ業務のメニュー */
export function wideNavigation(): NavigationResponse {
  return {
    items: [
      {
        id: '0',
        label: { ...WIDE_LABEL },
        icon: 'list',
        table: { schemaName: WIDE_SCHEMA, tableName: WIDE_TABLE },
        navigable: true,
        items: [],
      },
    ],
    emptyReason: null,
  }
}
