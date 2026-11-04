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
    reference: null,
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

/**
 * 書き込みのできる定義にする（Intent 261016-business-data-views の U3 の B6。185 の作成・更新・削除の状態と formShown100）。
 * 許す操作をすべてにし、書き込みの設定ありにする。主キー（code）は作成だけで入力し必須、ほかのカラムは作成と更新で入力する。
 * 部品はカラムの formPart を保ち、無ければ text にする（時刻の表の datetime を保つ。Intent 261102-timezone-ui-polish の U4）。
 */
export function writableDefinition(definition: TableDefinition): TableDefinition {
  const editable = (column: ColumnDefinition): ColumnDefinition =>
    column.column === 'code'
      ? {
          ...column,
          editableOnCreate: true,
          formPart: column.formPart ?? 'text',
          required: true,
          nullable: false,
        }
      : {
          ...column,
          editableOnCreate: true,
          editableOnUpdate: true,
          formPart: column.formPart ?? 'text',
        }
  return {
    ...definition,
    operations: ['LIST', 'DETAIL', 'CREATE', 'UPDATE', 'DELETE'],
    writeConfigured: true,
    listColumns: definition.listColumns.map(editable),
    detailColumns: definition.detailColumns.map(editable),
  }
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

/** 1つの表だけを末端に持つ業務のメニュー */
function singleTableNavigation(
  label: { ja: string; en: string },
  table: string,
): NavigationResponse {
  return {
    items: [
      {
        id: '0',
        label: { ...label },
        icon: 'list',
        table: { schemaName: WIDE_SCHEMA, tableName: table },
        navigable: true,
        items: [],
        tableKind: 'TABLE',
      },
    ],
    emptyReason: null,
  }
}

/** 広い表だけを末端に持つ業務のメニュー */
export function wideNavigation(): NavigationResponse {
  return singleTableNavigation(WIDE_LABEL, WIDE_TABLE)
}

// --- 時刻の表（Intent 261102-timezone-ui-polish の U4、計画 Step 14、security-design.md 2.6 の 185 の時刻の状態） ---
// - 本物の応答と同じ形にする: 時点を表す型（DATETIME_TZ）は UTC の時差 +00:00 つきの ISO 8601 で、秒の端数は列の桁のまま
//   （0・3・6 桁）。時差を持たない日時（DATETIME）は区切りの T のまま。読めない値は元の文字のまま届く（K-99）。
// - 時刻の値は架空の固定の値で、行を見分ける値ではないため目印を含めない（目印を入れると時刻として読めなくなる）。主キーと
//   文字の値は今の見本と同じく目印を含める。
// - 本物の応答と項目の名前・型が一致することは、流れ 200 の中で shapeCompare で毎回確かめる（project.md の学び）。

/** 時刻の表の見本のテーブル */
export const TIME_TABLE = 'TIME_EVENT'

/** 時刻の表の表示名（長い名前） */
export const TIME_LABEL = {
  ja: `時刻の表の長い表示名${'か'.repeat(20)}`,
  en: `Time table with a long display name ${'t'.repeat(10)}`,
} as const

/** 時刻の表の時点の値（UTC の +00:00。端数 6 桁・0 桁・3 桁。+05:45 で日付が変わる値と、2回ある時刻を含む） */
export const TIME_INSTANTS = [
  '2026-10-31T18:15:00.123456+00:00',
  '2026-11-01T06:30:00+00:00',
  '2026-02-28T23:59:59.999+00:00',
] as const

/** 時刻の表の時差を持たない日時の値 */
export const TIME_LOCALS = ['2026-11-01T09:00:00.5', '2026-03-08T02:30:00'] as const

/** 読めない時点の値（範囲の外の値が元の文字のまま届く形） */
export const UNREADABLE_INSTANT = '0000-00-00 00:00:00'

/** 時刻の表の定義（主キー・名前・長い表示名の時点・時差を持たない日時。検索は主キーの一致と時点の範囲） */
export function timeDefinition(workRoleId: number | null): TableDefinition {
  const definition = definitionOf(
    WIDE_SCHEMA,
    TIME_TABLE,
    { ...TIME_LABEL },
    [
      fixtureColumn('code', { ja: '出来事の番号', en: 'Event code' }, { width: 200 }),
      fixtureColumn('name', { ja: '名前', en: 'Name' }),
      fixtureColumn(
        'at',
        { ja: `発生の時刻の長い表示名${'き'.repeat(20)}`, en: 'Occurred at' },
        { valueType: 'DATETIME_TZ', formPart: 'datetime', precision: 6 },
      ),
      fixtureColumn(
        'closes',
        { ja: '締めの日時', en: 'Closes at' },
        { valueType: 'DATETIME', formPart: 'datetime' },
      ),
    ],
    workRoleId,
  )
  return {
    ...definition,
    searchFields: [
      searchField('code', '出来事の番号', 'Event code'),
      searchField('at', '発生の時刻', 'Occurred at', {
        operator: 'RANGE',
        valueType: 'DATETIME_TZ',
      }),
    ],
  }
}

/** 時刻の表の1行の値（5 行ごとに時点を読めない値、7 行ごとに時差を持たない日時を NULL にする） */
function timeRowValues(number: number): Record<string, string | null> {
  return {
    code: fixtureKey(number),
    name: `${BUSINESS_VALUE_MARK} 出来事 ${number}`,
    at:
      number % 5 === 0
        ? UNREADABLE_INSTANT
        : (TIME_INSTANTS[(number - 1) % TIME_INSTANTS.length] ?? null),
    closes: number % 7 === 0 ? null : (TIME_LOCALS[(number - 1) % TIME_LOCALS.length] ?? null),
  }
}

/**
 * 時刻の表の一覧の見本（page ページ目の size 件。全体は total 件）。
 *
 * @param definition 時刻の表の定義
 * @param page ページの番号
 * @param size 1ページの件数
 * @param total 全体の件数
 */
export function timePage(
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
    items: Array.from({ length: count }, (_, index) => {
      const number = start + index + 1
      return {
        key: { code: fixtureKey(number) },
        values: timeRowValues(number),
        truncatedColumns: [],
      }
    }),
    page,
    size,
    total,
  }
}

/** 時刻の表の詳細の見本 */
export function timeDetail(definition: TableDefinition, number: number): RowDetail {
  return {
    definitionId: definition.definitionId,
    definitionChanged: false,
    key: { code: fixtureKey(number) },
    values: timeRowValues(number),
    truncatedColumns: [],
  }
}

/** 時刻の表だけを末端に持つ業務のメニュー */
export function timeNavigation(): NavigationResponse {
  return singleTableNavigation(TIME_LABEL, TIME_TABLE)
}
