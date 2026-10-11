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
// 業務のメニューの負荷の試験（Intent 261004-role-menu の U5 navigation、Bolt B7）の DSL を、ロールの準備の場面（rolePrepare）が使う
// 版 2 の DSL（perf/dsl-timing.sh が生成した 100 テーブル × 100 カラムのスキーマ large）から作る。最上位の menus: の塊だけを行の単位で
// 置き換え、スキーマとテーブルはそのまま残す（1万カラムのロールと同じ名前の上で絞り込みを働かせるため。計画の D-7）。
// Node の標準の部品だけを使い、結果の置き場（build/ の下）にだけ書く。
//   node perf/make-nav-dsl.mjs --worst    <版 2 の DSL> <出力のファイル>
//   node perf/make-nav-dsl.mjs --baseline <版 2 の DSL> <出力のファイル>
// --worst:    悪い側（NFR2.1）。menus 1,000 項目・深さ 5。最上位のまとまり 40 個のそれぞれが 25 項目（段 1〜4 の鎖の項目 4 つ、
//             鎖の各段の子の表の項目 5 つずつ 20、段 5 の鎖の先 1 つ）を持ち、鎖の項目もテーブルを指す（テーブルと子の両方を持つ項目）。
//             1,000 項目のすべてがテーブルを指し、100 テーブルを順に回すため、各テーブルはちょうど 10 回、別々のまとまりから指される。
// --baseline: 目安（NFR2.2 の比べる相手）。menus 100 項目・深さ 1。各項目がテーブルを1つずつ指す。
// menus: の塊が見つからない・版が 2 でない・スキーマかテーブルが見つからないときは、何も書かずに止まる。
import { readFileSync, writeFileSync } from 'node:fs'

const [mode, input, output] = process.argv.slice(2)
if (!['--worst', '--baseline'].includes(mode) || !input || !output) {
  console.error('使い方: node perf/make-nav-dsl.mjs --worst|--baseline <版 2 の DSL> <出力のファイル>')
  process.exit(1)
}

const source = readFileSync(input, 'utf8')
if (!/^version: 2$/m.test(source)) {
  console.error('書式の版 2 の DSL ではありません')
  process.exit(1)
}
const lines = source.split('\n')
const menusAt = lines.indexOf('menus:')
if (menusAt < 0 || lines.indexOf('menus:', menusAt + 1) >= 0) {
  console.error('最上位の menus: の塊がちょうど1つ見つかりません')
  process.exit(1)
}
// menus: の塊の終わり（次の最上位の鍵の行。字下げの無い、コメントでない行）
let menusEnd = menusAt + 1
while (menusEnd < lines.length && !/^[^\s#]/.test(lines[menusEnd])) menusEnd++

// スキーマの名前（schemas: の直下、字下げ 2）とテーブルの名前（tables: の直下、字下げ 6）
const schemasAt = lines.indexOf('schemas:')
const schemaNames = []
const tableNames = []
if (schemasAt >= 0) {
  for (let i = schemasAt + 1; i < lines.length && !/^[^\s#]/.test(lines[i]); i++) {
    const schema = /^ {2}([^\s:#][^:]*):$/.exec(lines[i])
    if (schema) schemaNames.push(schema[1])
    const table = /^ {6}([^\s:#][^:]*):$/.exec(lines[i])
    if (table) tableNames.push(table[1])
  }
}
if (schemaNames.length !== 1 || tableNames.length === 0) {
  console.error('スキーマがちょうど1つ、テーブルが1つ以上の DSL ではありません')
  process.exit(1)
}
const schema = schemaNames[0]

let next = 0
function nextTable() {
  return tableNames[next++ % tableNames.length]
}

// 項目1つの行を作る（字下げ indent。子は items: の下に置く）
function item(indent, ja, en, icon, table, children) {
  const pad = ' '.repeat(indent)
  const out = [
    `${pad}- label:`,
    `${pad}    ja: ${JSON.stringify(ja)}`,
    `${pad}    en: ${JSON.stringify(en)}`,
    `${pad}  icon: ${icon}`,
    `${pad}  table:`,
    `${pad}    schema: ${JSON.stringify(schema)}`,
    `${pad}    name: ${JSON.stringify(table)}`,
  ]
  if (children.length > 0) {
    out.push(`${pad}  items:`)
    for (const child of children) out.push(...child(indent + 4))
  }
  return out
}

function worstMenus() {
  const roots = []
  for (let g = 1; g <= 40; g++) {
    // 段 5 の鎖の先から上へ組み立てる（各段の項目は鎖の子と、表の項目 5 つを持つ）
    const tail = nextTable()
    let chain = (indent) => item(indent, `深い ${g}`, `deep ${g}`, 'list', tail, [])
    for (let level = 4; level >= 1; level--) {
      const own = nextTable()
      const leaves = []
      for (let k = 1; k <= 5; k++) {
        const table = nextTable()
        leaves.push((indent) => item(indent, `表 ${g}-${level}-${k}`, `table ${g}-${level}-${k}`, 'list', table, []))
      }
      const children = [chain, ...leaves]
      const label = `まとまり ${g}-${level}`
      chain = (indent) => item(indent, label, `group ${g}-${level}`, 'home', own, children)
    }
    roots.push(chain)
  }
  return roots
}

function baselineMenus() {
  return tableNames.slice(0, 100).map((table, i) => (indent) => item(indent, `目安 ${i + 1}`, `baseline ${i + 1}`, 'list', table, []))
}

const roots = mode === '--worst' ? worstMenus() : baselineMenus()
const menus = ['menus:']
for (const root of roots) menus.push(...root(2))
const result = [...lines.slice(0, menusAt), ...menus, ...lines.slice(menusEnd)].join('\n')
writeFileSync(output, result)
const count = menus.filter((line) => /^\s*- label:$/.test(line)).length
console.log(`${mode.slice(2)}: menus ${count} 項目、テーブル ${tableNames.length}、${Buffer.byteLength(result)} バイト`)
