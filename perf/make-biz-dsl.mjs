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
// 業務データの負荷の試験（Intent 261016-business-data-views の U1、Bolt B2）の DSL（書式の版 2）を作る。
// perf/biz/make-biz-seed.sh が作るスキーマ bizperf の3つの表（biz_item・biz_pair・biz_wide）と同じ形で書く（片方を変えるときは
// 両方を変える）。型の名前は、既定の DSL の生成が DB の種類ごとに写す名前（PG は int4・int8 など、MySQL・MariaDB は int・bigint など）。
//   biz_item: 100 カラムのすべてを一覧に出す（order 1〜100）。並べ替えは id（既定 ASC）と c_001（bizSearchLastSorted）だけ。
//             検索は id の完全一致（EQUALS）と c_010 の部分一致（CONTAINS。bizSearchContains）だけ。
//   biz_pair: 複合の主キー（region_code・item_code）。全カラムを一覧に出し、主キーの順で並べる。
//   biz_wide: 100 カラムのすべてを一覧に出す（bizSearchWide の 100 件 × 100 カラム × 100 字）。
//   biz_ref:  外部キーの参照先（U2 の書き込みの場面）。
//   biz_new:  100 カラムの書き込みの表（U2）。主キー id は自動の採番で必須にしない、u_code は unique、ref_id は biz_ref を指す外部キー、
//             n_001〜n_097 は文字の長い型（bizCreateLarge で 10,000 字ずつ入れる）。
// 他テーブル参照の読み取りの場面（Intent 261029-reference-lookup-cleanup の U1、Bolt B2。NFR2.4）:
//   biz_code200・biz_code201: REFERENCE の参照先（code・name。ちょうど 200 行と 201 行）。
//   biz_refsrc200・biz_refsrc201: code が biz_code200・biz_code201 を指す REFERENCE（値 code・ラベル name。部品は select）。
//   biz_refpick: item_id が biz_item を指す LOOKUP（値 id・ラベル c_007。検索は id の完全一致と c_010 の部分一致、一覧は c_007・c_001）。
// 他テーブル参照の保存前の確かめの場面（Intent 261029-reference-lookup-cleanup の U2、Bolt B3。NFR2.21〜NFR2.24）:
//   biz_refcreate: 主キー id は自動の採番で必須にしない、code は biz_code200 を指す NOT NULL の REFERENCE（値 code・ラベル name。
//                  部品は select）。biz_new の ref_id には参照の options を足さない（NFR2.23）。
// メニューは表を1つずつ指す。Node の標準の部品だけを使い、指定した出力のファイル（build/ の下など）にだけ書く。
//   node perf/make-biz-dsl.mjs <postgres|mysql|mariadb> <出力のファイル>
import { writeFileSync } from 'node:fs'

const [kind, output] = process.argv.slice(2)
if (!['postgres', 'mysql', 'mariadb'].includes(kind) || !output) {
  console.error('使い方: node perf/make-biz-dsl.mjs <postgres|mysql|mariadb> <出力のファイル>')
  process.exit(1)
}
const pg = kind === 'postgres'
const SCHEMA = 'bizperf'

// make-biz-seed.sh の kind_of と同じ（c_010・c_030・c_050・c_070・c_090 は文字の長い型、ほかは i % 7 で回す）
const CYCLE = ['varchar', 'int', 'bigint', 'decimal', 'date', 'datetime', 'bool']
function kindOf(i) {
  return [10, 30, 50, 70, 90].includes(i) ? 'text' : CYCLE[i % 7]
}

// 型ごとの dbType・フォーム部品・一覧の書式（DB の種類ごとの名前）
function typeOf(k, i) {
  switch (k) {
    case 'varchar':
      return { db: { name: 'varchar', length: 40 }, formPart: 'text', format: null }
    case 'int':
      return { db: pg ? { name: 'int4', precision: 32, scale: 0 } : { name: 'int', precision: 10, scale: 0 }, formPart: 'number', format: 'NUMBER_GROUPED' }
    case 'bigint':
      return { db: pg ? { name: 'int8', precision: 64, scale: 0 } : { name: 'bigint', precision: 19, scale: 0 }, formPart: 'number', format: 'NUMBER_GROUPED' }
    case 'decimal':
      return { db: { name: pg ? 'numeric' : 'decimal', precision: 12, scale: 2 }, formPart: 'number', format: 'NUMBER_GROUPED' }
    case 'date':
      return { db: { name: 'date' }, formPart: 'date', format: 'DATE' }
    case 'datetime':
      return { db: { name: pg ? 'timestamp' : 'datetime' }, formPart: 'datetime', format: 'DATETIME' }
    case 'bool':
      return { db: { name: pg ? 'bool' : 'tinyint', precision: pg ? null : 3, scale: pg ? null : 0 }, formPart: 'checkbox', format: 'BOOLEAN_YES_NO' }
    case 'text':
      return { db: { name: !pg && i === 10 ? 'longtext' : 'text' }, formPart: 'textarea', format: null }
    default:
      throw new Error(`知らない型です: ${k}`)
  }
}

const q = (value) => JSON.stringify(value)
const lines = []
function push(indent, text) {
  lines.push(' '.repeat(indent) + text)
}
function label(indent, ja, en) {
  push(indent, 'label:')
  push(indent + 2, `ja: ${q(ja)}`)
  push(indent + 2, `en: ${q(en)}`)
}

// カラム1つ（字下げ 10。columns: の直下）
function column(name, ja, type, opts) {
  push(10, `${name}:`)
  label(12, ja, name)
  push(12, 'dbType:')
  push(14, `name: ${type.db.name}`)
  push(14, `length: ${type.db.length ?? 'null'}`)
  push(14, `precision: ${type.db.precision ?? 'null'}`)
  push(14, `scale: ${type.db.scale ?? 'null'}`)
  push(14, `nullable: ${opts.nullable}`)
  push(12, `formPart: ${opts.formPart ?? type.formPart}`)
  push(12, 'search:')
  push(14, `enabled: ${opts.search !== null}`)
  push(14, `operator: ${opts.search ?? 'null'}`)
  push(14, 'default: null')
  push(14, 'collapsed: false')
  push(12, 'list:')
  push(14, 'visible: true')
  push(14, `order: ${opts.order}`)
  push(14, 'width: null')
  push(14, `sortable: ${opts.sortable}`)
  push(14, `defaultSort: ${opts.defaultSort ?? 'null'}`)
  push(14, `format: ${type.format ?? 'null'}`)
  push(12, 'detail:')
  push(14, 'visible: true')
  // 必須は既定で NULL を許さないカラム（自動の採番の主キーは required: false で外す）。unique は U2 の書き込みの表だけ。
  const required = opts.required ?? !opts.nullable
  if (!required && !opts.unique) {
    push(12, 'validations: []')
  } else {
    push(12, 'validations:')
    if (required) {
      push(14, '- type: required')
      push(14, '  origin: DB')
    }
    if (opts.unique) {
      push(14, '- type: unique')
      push(14, '  origin: DB')
    }
  }
  if (!opts.options) {
    push(12, 'options: null')
    return
  }
  // 他テーブル参照の options（U1）。REFERENCE は table・valueColumn・labelColumn、LOOKUP は加えて lookupSearch・lookupList
  const o = opts.options
  push(12, 'options:')
  push(14, `source: ${o.source}`)
  push(14, `table: ${o.table}`)
  push(14, `valueColumn: ${o.valueColumn}`)
  if (o.labelColumn) push(14, `labelColumn: ${o.labelColumn}`)
  if (o.source === 'LOOKUP') {
    push(14, 'lookupSearch:')
    for (const item of o.lookupSearch) push(16, `- { column: ${item.column}, operator: ${item.operator} }`)
    push(14, 'lookupList:')
    for (const item of o.lookupList) push(16, `- { column: ${item.column}, order: ${item.order} }`)
  }
}

function table(name, ja, primaryKey, columns, foreignKeys = []) {
  push(6, `${name}:`)
  label(8, ja, name)
  push(8, 'view: false')
  push(8, 'primaryKey:')
  for (const key of primaryKey) push(10, `- ${key}`)
  if (foreignKeys.length === 0) {
    push(8, 'foreignKeys: []')
  } else {
    push(8, 'foreignKeys:')
    for (const key of foreignKeys) {
      push(10, `- columns: [${key.columns.join(', ')}]`)
      push(10, `  referencedTable: ${key.referencedTable}`)
      push(10, `  referencedColumns: [${key.referencedColumns.join(', ')}]`)
    }
  }
  push(8, 'columns:')
  columns()
}

const TABLES = [
  ['biz_item', '業務データの試験の表'],
  ['biz_pair', '複合の主キーの表'],
  ['biz_wide', '幅の広い表'],
  ['biz_ref', '参照先の表'],
  ['biz_new', '書き込みの試験の表'],
  ['biz_code200', '参照先のコードの表（200 行）'],
  ['biz_code201', '参照先のコードの表（201 行）'],
  ['biz_refsrc200', '参照元の表（200 行の参照先）'],
  ['biz_refsrc201', '参照元の表（201 行の参照先）'],
  ['biz_refpick', '参照元の表（ピッカー）'],
  ['biz_refcreate', '参照元の表（保存前の確かめ）'],
]

push(0, '# 業務データの負荷の試験の DSL（perf/make-biz-dsl.mjs で作った。値はすべて架空）')
push(0, 'version: 2')
push(0, 'menus:')
for (const [name, ja] of TABLES) {
  push(2, '- label:')
  push(6, `ja: ${q(ja)}`)
  push(6, `en: ${q(name)}`)
  push(4, 'icon: list')
  push(4, 'table:')
  push(6, `schema: ${SCHEMA}`)
  push(6, `name: ${name}`)
}
push(0, 'schemas:')
push(2, `${SCHEMA}:`)
label(4, '業務データの試験', SCHEMA)
push(4, 'tables:')

table('biz_item', TABLES[0][1], ['id'], () => {
  const idType = { db: pg ? { name: 'int8', precision: 64, scale: 0 } : { name: 'bigint', precision: 19, scale: 0 }, formPart: 'number', format: 'NUMBER_GROUPED' }
  column('id', '番号', idType, { nullable: false, search: 'EQUALS', order: 1, sortable: true, defaultSort: 'ASC' })
  for (let i = 1; i <= 99; i++) {
    const name = `c_${String(i).padStart(3, '0')}`
    column(name, `カラム ${i}`, typeOf(kindOf(i), i), {
      nullable: true,
      search: i === 10 ? 'CONTAINS' : null,
      order: i + 1,
      sortable: i === 1,
      defaultSort: null,
    })
  }
})

table('biz_pair', TABLES[1][1], ['region_code', 'item_code'], () => {
  const int = typeOf('int', 0)
  column('region_code', '地域', int, { nullable: false, search: 'EQUALS', order: 1, sortable: true, defaultSort: 'ASC' })
  column('item_code', '品目', typeOf('varchar', 0), { nullable: false, search: 'EQUALS', order: 2, sortable: true, defaultSort: 'ASC' })
  column('title', '題', typeOf('varchar', 0), { nullable: true, search: 'CONTAINS', order: 3, sortable: false, defaultSort: null })
  column('amount', '金額', typeOf('decimal', 0), { nullable: true, search: null, order: 4, sortable: true, defaultSort: null })
  column('updated_on', '更新日', typeOf('date', 0), { nullable: true, search: null, order: 5, sortable: true, defaultSort: null })
})

table('biz_wide', TABLES[2][1], ['id'], () => {
  column('id', '番号', typeOf('int', 0), { nullable: false, search: 'EQUALS', order: 1, sortable: true, defaultSort: 'ASC' })
  for (let i = 1; i <= 99; i++) {
    const name = `w_${String(i).padStart(3, '0')}`
    column(name, `幅 ${i}`, typeOf('text', 0), { nullable: true, search: null, order: i + 1, sortable: false, defaultSort: null })
  }
})

table('biz_ref', TABLES[3][1], ['id'], () => {
  column('id', '番号', typeOf('int', 0), { nullable: false, search: 'EQUALS', order: 1, sortable: true, defaultSort: 'ASC' })
  column('name', '名前', typeOf('varchar', 0), { nullable: true, search: null, order: 2, sortable: false, defaultSort: null })
})

table(
  'biz_new',
  TABLES[4][1],
  ['id'],
  () => {
    column('id', '番号', typeOf('int', 0), {
      nullable: false,
      required: false,
      search: 'EQUALS',
      order: 1,
      sortable: true,
      defaultSort: 'ASC',
    })
    column('u_code', '一意の符号', typeOf('varchar', 0), {
      nullable: true,
      unique: true,
      search: null,
      order: 2,
      sortable: false,
      defaultSort: null,
    })
    column('ref_id', '参照', typeOf('int', 0), { nullable: true, search: null, order: 3, sortable: false, defaultSort: null })
    for (let i = 1; i <= 97; i++) {
      const name = `n_${String(i).padStart(3, '0')}`
      column(name, `書き込み ${i}`, typeOf('text', 0), { nullable: true, search: null, order: i + 3, sortable: false, defaultSort: null })
    }
  },
  [{ columns: ['ref_id'], referencedTable: 'biz_ref', referencedColumns: ['id'] }],
)

// 他テーブル参照の読み取りの場面の表（U1）
for (const [index, count] of [[5, 200], [6, 201]]) {
  table(`biz_code${count}`, TABLES[index][1], ['id'], () => {
    column('id', '番号', typeOf('int', 0), { nullable: false, search: 'EQUALS', order: 1, sortable: true, defaultSort: 'ASC' })
    column('code', 'コード', typeOf('varchar', 0), { nullable: true, search: null, order: 2, sortable: false, defaultSort: null })
    column('name', '名前', typeOf('varchar', 0), { nullable: true, search: null, order: 3, sortable: false, defaultSort: null })
  })
}
for (const [index, count] of [[7, 200], [8, 201]]) {
  table(`biz_refsrc${count}`, TABLES[index][1], ['id'], () => {
    column('id', '番号', typeOf('int', 0), { nullable: false, search: 'EQUALS', order: 1, sortable: true, defaultSort: 'ASC' })
    column('code', 'コード', typeOf('varchar', 0), {
      nullable: true,
      search: null,
      order: 2,
      sortable: false,
      defaultSort: null,
      formPart: 'select',
      options: { source: 'REFERENCE', table: `biz_code${count}`, valueColumn: 'code', labelColumn: 'name' },
    })
  })
}
table('biz_refpick', TABLES[9][1], ['id'], () => {
  column('id', '番号', typeOf('int', 0), { nullable: false, search: 'EQUALS', order: 1, sortable: true, defaultSort: 'ASC' })
  column('item_id', '品目', typeOf('bigint', 0), {
    nullable: true,
    search: null,
    order: 2,
    sortable: false,
    defaultSort: null,
    formPart: 'lookup',
    options: {
      source: 'LOOKUP',
      table: 'biz_item',
      valueColumn: 'id',
      labelColumn: 'c_007',
      lookupSearch: [
        { column: 'id', operator: 'EQUALS' },
        { column: 'c_010', operator: 'CONTAINS' },
      ],
      lookupList: [
        { column: 'c_007', order: 1 },
        { column: 'c_001', order: 2 },
      ],
    },
  })
})

// 他テーブル参照の保存前の確かめの場面の表（U2）
table('biz_refcreate', TABLES[10][1], ['id'], () => {
  column('id', '番号', typeOf('int', 0), {
    nullable: false,
    required: false,
    search: 'EQUALS',
    order: 1,
    sortable: true,
    defaultSort: 'ASC',
  })
  column('code', 'コード', typeOf('varchar', 0), {
    nullable: false,
    search: null,
    order: 2,
    sortable: false,
    defaultSort: null,
    formPart: 'select',
    options: { source: 'REFERENCE', table: 'biz_code200', valueColumn: 'code', labelColumn: 'name' },
  })
})

const result = lines.join('\n') + '\n'
writeFileSync(output, result)
console.log(`${kind}: テーブル ${TABLES.length}、${Buffer.byteLength(result)} バイト`)
