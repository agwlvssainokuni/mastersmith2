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
// 手で流す道具: PNG の並びから `.ico` を作る（U6 の SD3。ビルドには組み込まない）。
//   node scripts/make-ico.mjs <出力の道> <PNG> <PNG> ...
// 出力の前に inspectIco で形と大きさを確かめ、通らなければ書かずに終了コード 1 で終わる。
// 手順（rsvg-convert で PNG を作る方法・版・コマンド）は docs/app-icon.md。
import { readFileSync, writeFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { inspectIco, packIco } from './icon-binary.mjs'

/**
 * PNG のファイルから ICO を作って書く。
 * @param {string} output 出力の道
 * @param {string[]} inputs PNG のファイルの道
 * @returns {{ ok: true, bytes: number, images: { width: number, height: number }[] } | { ok: false, message: string }} 結果
 */
export function makeIco(output, inputs) {
  let ico
  try {
    ico = packIco(inputs.map((file) => readFileSync(file)))
  } catch (error) {
    return { ok: false, message: error instanceof Error ? error.message : String(error) }
  }
  const checked = inspectIco(ico)
  if (!checked.ok) {
    return { ok: false, message: `作った ICO が確かめに通りません（${checked.code}）` }
  }
  writeFileSync(output, ico)
  return { ok: true, bytes: ico.length, images: checked.images }
}

function main() {
  const [output, ...inputs] = process.argv.slice(2)
  if (!output || inputs.length === 0) {
    console.error('使い方: node scripts/make-ico.mjs <出力の道> <PNG> <PNG> ...')
    process.exit(2)
  }
  const result = makeIco(output, inputs)
  if (!result.ok) {
    console.error(`ICO を作れませんでした: ${result.message}`)
    process.exit(1)
  }
  const sizes = result.images.map((i) => `${i.width}x${i.height}`).join(', ')
  console.log(`${output}: ${result.bytes} バイト、画像 ${result.images.length} 枚（${sizes}）`)
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main()
}
