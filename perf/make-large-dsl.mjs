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
// 投入の上限ちょうど（10,485,760 バイト）の DSL を、アプリが生成した既定の DSL から作る（perf/dsl-timing.sh が使う）。
// リポジトリに大きなファイルを置かないため、結果の置き場（build/ の下）にだけ書く。
//   node perf/make-large-dsl.mjs <生成した DSL> <出力のディレクトリ>
// 作るもの（どれもちょうど 10,485,760 バイト）:
//   valid-10mb.yaml         生成した DSL の先頭のテーブルを別名（x_ で始まる名前）で写して足し、足りない分を末尾の YAML のコメントで埋めたもの
//   invalid-many-10mb.yaml  写したテーブルのすべてのカラムの formPart を書式に無い値（同じ長さの x の並び）にしたもの（JSON Schema の誤りが多数）
//   invalid-tail-10mb.yaml  最後に写したテーブルの主キーを無いカラム（同じ長さの z の並び）にしたもの（意味の誤りが末尾に1件。全部の段を通る）
// 誤りを入れても長さを変えないため、3つとも同じ埋め草の量になる。
// 写したテーブルは元のテーブルと同じ中身のため、外部キー・選択肢の参照の先は元からあるテーブルで、正しい DSL のままになる。
import { readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'

const LIMIT = 10 * 1024 * 1024
const [input, outDir] = process.argv.slice(2)
if (!input || !outDir) {
  console.error('使い方: node perf/make-large-dsl.mjs <生成した DSL> <出力のディレクトリ>')
  process.exit(1)
}

const source = readFileSync(input, 'utf8')
const tablesAt = source.indexOf('\ntables:\n')
if (tablesAt < 0) {
  console.error('tables: が見つかりません')
  process.exit(1)
}
const body = source.slice(tablesAt + '\ntables:\n'.length)
// テーブルの見出し（字下げ 2 の「名前:」の行）で区切る。
const heads = [...body.matchAll(/^ {2}([^\s:#][^:]*):\n/gm)]
const blocks = heads.map((m, i) => ({
  name: m[1],
  text: body.slice(m.index, i + 1 < heads.length ? heads[i + 1].index : body.length),
}))
const trailing = source.endsWith('\n') ? '' : '\n'
const baseBytes = Buffer.byteLength(source + trailing)
const paddingLine = `# padding${'-'.repeat(88)}\n`
const paddingHeader = '# 以下は大きさを上限ちょうどにするための埋め草（DSL の中身ではない）\n'

// 足せるだけテーブルを写す（埋め草の見出しの分を残す）。
const copies = []
let size = baseBytes
for (const block of blocks) {
  const copy = block.text.replace(/^ {2}[^\n]*:\n/, `  x_${copies.length + 1}_${block.name}:\n`)
  const bytes = Buffer.byteLength(copy)
  if (size + bytes + Buffer.byteLength(paddingHeader) > LIMIT) break
  copies.push(copy)
  size += bytes
}

function pad(text) {
  let rest = LIMIT - Buffer.byteLength(text) - Buffer.byteLength(paddingHeader)
  if (rest < 0) throw new Error('上限を超えました')
  let padding = paddingHeader
  while (rest >= paddingLine.length) {
    padding += paddingLine
    rest -= paddingLine.length
  }
  if (rest > 0) padding += rest === 1 ? '\n' : `#${'-'.repeat(rest - 2)}\n`
  const out = text + padding
  if (Buffer.byteLength(out) !== LIMIT) throw new Error(`大きさが合いません: ${Buffer.byteLength(out)}`)
  return out
}

const valid = pad(source + trailing + copies.join(''))
const invalidMany = pad(
  source + trailing + copies.map((c) => c.replace(/^( {8}formPart: )(\S+)$/gm, (_, p, v) => p + 'x'.repeat(v.length))).join(''),
)
const last = copies.length - 1
const invalidTail = pad(
  source +
    trailing +
    copies
      .map((c, i) => (i === last ? c.replace(/^( {4}primaryKey:\n {6}- )(\S+)$/m, (_, p, v) => p + 'z'.repeat(v.length)) : c))
      .join(''),
)
if (invalidTail === valid) throw new Error('末尾の誤りを入れられませんでした')

writeFileSync(path.join(outDir, 'valid-10mb.yaml'), valid)
writeFileSync(path.join(outDir, 'invalid-many-10mb.yaml'), invalidMany)
writeFileSync(path.join(outDir, 'invalid-tail-10mb.yaml'), invalidTail)
const paddingBytes = LIMIT - baseBytes - copies.reduce((n, c) => n + Buffer.byteLength(c), 0)
console.log(
  [
    `generated_bytes=${baseBytes}`,
    `generated_tables=${blocks.length}`,
    `copied_tables=${copies.length}`,
    `padding_comment_bytes=${paddingBytes}`,
    `output_bytes=${LIMIT}`,
    `invalid_many_formpart_replaced=${(invalidMany.match(/^ {8}formPart: x+$/gm) ?? []).length}`,
  ].join('\n'),
)
