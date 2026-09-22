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
// フロントエンドのソースファイルの先頭に Apache License 2.0 のヘッダーがあるかを確かめる。
// ヘッダーのひな形はリポジトリのルートの config/license-header.txt（Spotless と共通）。
// - TS・TSX・JS・MJS・CJS・CSS: `/* ... */` の形（`/** ... */` は認めない）
// - HTML: `<!-- ... -->` の形
// ヘッダーが無い・形が違うファイルを示して、終了コード 1 で失敗させる。
import { readFileSync, readdirSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const templatePath = path.resolve(frontendRoot, '..', 'config', 'license-header.txt')

const SKIP_DIRS = new Set(['node_modules', 'dist', 'coverage', 'playwright-report', 'test-results'])
const BLOCK_EXTENSIONS = new Set(['.ts', '.tsx', '.js', '.mjs', '.cjs', '.css'])
const HTML_EXTENSIONS = new Set(['.html'])

/**
 * ひな形の行から、コメントの形ごとの期待するヘッダーを作る。
 * @param {string[]} lines ひな形の行
 * @returns {{ block: string, html: string }} 期待するヘッダー
 */
export function buildExpectedHeaders(lines) {
  const block = ['/*', ...lines.map((l) => (l === '' ? ' *' : ` * ${l}`)), ' */'].join('\n')
  const html = ['<!--', ...lines.map((l) => (l === '' ? '' : `  ${l}`)), '-->'].join('\n')
  return { block, html }
}

/**
 * 1つのファイルの中身がヘッダーの決まりに合うかを判定する。
 * @param {string} fileName ファイル名（拡張子の判定に使う）
 * @param {string} content ファイルの中身
 * @param {{ block: string, html: string }} expected 期待するヘッダー
 * @returns {string | null} 合わなければ理由、合えば null
 */
export function checkContent(fileName, content, expected) {
  const ext = path.extname(fileName)
  const normalized = content.replace(/\r\n/g, '\n')
  if (BLOCK_EXTENSIONS.has(ext)) {
    if (normalized.startsWith('/**')) {
      return '`/** ... */` ではなく `/* ... */` の形で書いてください'
    }
    return normalized.startsWith(expected.block + '\n') ? null : 'ライセンスヘッダーがありません'
  }
  if (HTML_EXTENSIONS.has(ext)) {
    return normalized.startsWith(expected.html + '\n') ? null : 'ライセンスヘッダーがありません'
  }
  return null
}

/**
 * 対象のファイルを集める。
 * @param {string} dir 探すディレクトリ
 * @returns {string[]} ファイルの絶対パス
 */
function collectFiles(dir) {
  const result = []
  for (const name of readdirSync(dir)) {
    if (SKIP_DIRS.has(name) || name.startsWith('.')) {
      continue
    }
    const full = path.join(dir, name)
    if (statSync(full).isDirectory()) {
      result.push(...collectFiles(full))
    } else if (
      BLOCK_EXTENSIONS.has(path.extname(name)) ||
      HTML_EXTENSIONS.has(path.extname(name))
    ) {
      result.push(full)
    }
  }
  return result
}

function main() {
  const lines = readFileSync(templatePath, 'utf8')
    .replace(/\r\n/g, '\n')
    .replace(/\n+$/, '')
    .split('\n')
  const expected = buildExpectedHeaders(lines)
  const problems = []
  for (const file of collectFiles(frontendRoot)) {
    const reason = checkContent(file, readFileSync(file, 'utf8'), expected)
    if (reason) {
      problems.push(`${path.relative(frontendRoot, file)}: ${reason}`)
    }
  }
  if (problems.length > 0) {
    console.error('ライセンスヘッダーの確認に失敗しました:')
    for (const p of problems) {
      console.error(`  ${p}`)
    }
    process.exit(1)
  }
  console.log('ライセンスヘッダーの確認: すべてのファイルにヘッダーがあります。')
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main()
}
