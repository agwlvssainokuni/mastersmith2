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
// 重い正規表現を pattern に多数含む DSL と、同じ形で pattern の無い普通の DSL を作る（U2-PATTERN-COMPILE、perf/dsl-timing.sh --pattern が使う）。
//   node perf/make-pattern-dsl.mjs <出力のディレクトリ> [テーブルの数（既定 20）] [カラムの数（既定 100）]
// 作るもの:
//   pattern-heavy.yaml  すべてのカラムに、1,000 文字近くの重い正規表現（入れ子の繰り返し・深い入れ子・大きな繰り返しの回数・
//                       Unicode の文字の種類の積・遅延の繰り返しの選択）を1つずつ持たせたもの（テーブル × カラムの数だけ組み立てる）
//   pattern-plain.yaml  同じテーブルとカラムで、pattern の無いもの（直後の投入が遅れないかの比べる相手）
// どれも正しい正規表現で、組み立て（Pattern.compile）は通る。DSL の値には当てはめない（U2 の NFR3.6）。
import { writeFileSync } from 'node:fs'
import path from 'node:path'

const [outDir, tablesArg, columnsArg] = process.argv.slice(2)
if (!outDir) {
  console.error('使い方: node perf/make-pattern-dsl.mjs <出力のディレクトリ> [テーブルの数] [カラムの数]')
  process.exit(1)
}
const tables = Number(tablesArg ?? 20)
const columns = Number(columnsArg ?? 100)
const MAX = 1000

/** unit を max 文字以下でくり返す。 */
function fill(unit, max) {
  return unit.repeat(Math.floor(max / unit.length))
}
/** 深い入れ子の繰り返し（((((a)*)*)*)…）を max 文字以下で作る。 */
function nested(max) {
  const depth = Math.floor((max - 1) / 3)
  return '('.repeat(depth) + 'a' + ')*'.repeat(depth)
}
export const HEAVY_PATTERNS = [
  nested(MAX),
  fill('(a+)+', MAX),
  fill('(?:a{1,1000}){1,1000}', MAX),
  '(?iu)' + fill('[\\p{L}\\p{N}\\p{IsHan}&&[^\\p{Lu}]]{1,999}', MAX - 5),
  fill('(?:x|y|z|\\w+?|\\d{2,9})*', MAX),
  '(?iu)' + fill('\\p{InCJKUnifiedIdeographs}|', MAX - 5).replace(/\|$/, ''),
]
for (const p of HEAVY_PATTERNS) {
  if ([...p].length > MAX) throw new Error(`長さが上限を超えました: ${[...p].length}`)
  if (p.includes("'")) throw new Error('引用符を含みます')
}

function dsl(withPattern) {
  const out = ['version: 1', 'menus:']
  const names = Array.from({ length: tables }, (_, t) => `p_${String(t + 1).padStart(3, '0')}`)
  for (const name of names) out.push(`  - label: { ja: ${name}, en: ${name} }`, `    table: ${name}`)
  out.push('tables:')
  let n = 0
  for (const name of names) {
    out.push(
      `  ${name}:`,
      `    label: { ja: ${name}, en: ${name} }`,
      '    view: false',
      '    primaryKey: [c_001]',
      '    foreignKeys: []',
      '    columns:',
    )
    for (let c = 1; c <= columns; c++) {
      const col = `c_${String(c).padStart(3, '0')}`
      out.push(
        `      ${col}:`,
        `        label: { ja: ${col}, en: ${col} }`,
        '        dbType: { name: VARCHAR, length: 40, precision: null, scale: null, nullable: true }',
        '        formPart: text',
        '        search: { enabled: false, collapsed: false }',
        `        list: { visible: true, order: ${c}, sortable: true }`,
        '        detail: { visible: true }',
      )
      if (withPattern) {
        const pattern = HEAVY_PATTERNS[n++ % HEAVY_PATTERNS.length]
        out.push('        validations:', '          - type: pattern', `            value: '${pattern}'`, '            origin: MANUAL')
      } else {
        out.push('        validations: []')
      }
    }
  }
  return out.join('\n') + '\n'
}

const heavy = dsl(true)
const plain = dsl(false)
writeFileSync(path.join(outDir, 'pattern-heavy.yaml'), heavy)
writeFileSync(path.join(outDir, 'pattern-plain.yaml'), plain)
console.log(
  [
    `tables=${tables}`,
    `columns_per_table=${columns}`,
    `patterns=${tables * columns}`,
    ...HEAVY_PATTERNS.map((p, i) => `pattern_${i}_chars=${[...p].length} head=${p.slice(0, 40)}`),
    `pattern_heavy_bytes=${Buffer.byteLength(heavy)}`,
    `pattern_plain_bytes=${Buffer.byteLength(plain)}`,
  ].join('\n'),
)
