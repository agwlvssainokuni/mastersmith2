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
// 画面の初回の読み込みに入る JavaScript（入口のファイルと、そこから静的に読み込まれるファイル）の
// 圧縮後（gzip）の合計を測る。目安の 500KB を超えたら警告を出す（統合は止めない。NFR1.5）。
// ビルドの結果（dist/.vite/manifest.json）が無ければ失敗させる。
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { gzipSync } from 'node:zlib'
import { fileURLToPath } from 'node:url'

const LIMIT_BYTES = 500 * 1024

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const distDir = path.join(frontendRoot, 'dist')
const manifestPath = path.join(distDir, '.vite', 'manifest.json')

if (!existsSync(manifestPath)) {
  console.error(
    `ビルドの結果が見つかりません: ${path.relative(frontendRoot, manifestPath)}（先に npm run build を実行してください）`,
  )
  process.exit(1)
}

const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'))
const entries = Object.keys(manifest).filter((key) => manifest[key].isEntry)
const visited = new Set()

/** 入口から静的な import をたどって、初回に読み込まれるファイルを集める。 */
function visit(key) {
  if (visited.has(key)) {
    return
  }
  visited.add(key)
  for (const imported of manifest[key].imports ?? []) {
    visit(imported)
  }
}
entries.forEach(visit)

let total = 0
for (const key of visited) {
  const file = manifest[key].file
  if (!file.endsWith('.js')) {
    continue
  }
  const size = gzipSync(readFileSync(path.join(distDir, file))).length
  total += size
  console.log(`  ${file}: ${(size / 1024).toFixed(1)} KB (gzip)`)
}

console.log(`初回の読み込みの JavaScript の合計: ${(total / 1024).toFixed(1)} KB (gzip)`)
if (total > LIMIT_BYTES) {
  console.warn(
    `警告: 目安の ${LIMIT_BYTES / 1024} KB を超えています。Performance Validation で見直してください。`,
  )
}
