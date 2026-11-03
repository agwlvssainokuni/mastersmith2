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
// ビルドの後に、dist の素材（アイコン・favicon）が public/ と同じで、index.html の favicon の指定が正しいことを確かめる
// （U6 の SD4.2 の L2）。素材が dist に入らないと、サーバーは /favicon.ico に 404 でなく index.html（HTML）を 200 で返し、
// 気づけないため、サーバーの外でここで拾う。
// - public/ の各ファイル（再帰）が dist のルートの同じ相対の道にあり、バイト列が同じ。public/ が空なら失敗。
// - .svg は SVG の中身の検査（standalone）を通る。.ico・.png は形と大きさの確かめを通る（HTML の本文はここで落ちる）。
// - dist/index.html の <link rel="icon"> 系の href が、外部の URL を指さず、dist の外へ出ず、dist の中のファイルを指す。
//   SVG と .ico（か .png）の <link> が 1 本以上ある。
import { existsSync, readFileSync, readdirSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { checkSvgContent } from './check-svg-safety.mjs'
import { inspectIco, inspectPng } from './icon-binary.mjs'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

/**
 * ディレクトリの下のすべてのファイル（再帰）の、ディレクトリからの相対の道（/ 区切り）を返す。
 * @param {string} dir ディレクトリ
 * @param {string} [prefix] 内部の再帰用
 * @returns {string[]} 相対の道
 */
function listFiles(dir, prefix = '') {
  const result = []
  for (const name of readdirSync(dir)) {
    const full = path.join(dir, name)
    const relative = prefix === '' ? name : `${prefix}/${name}`
    if (statSync(full).isDirectory()) {
      result.push(...listFiles(full, relative))
    } else {
      result.push(relative)
    }
  }
  return result.sort()
}

/** href が外部（http:・https:・//・data:・javascript:）か */
const EXTERNAL_HREF = /^(?:[A-Za-z][A-Za-z0-9+.-]*:|\/\/)/

/**
 * dist/index.html の favicon の <link> を読み、決まりを確かめる。
 * @param {string} html index.html の中身
 * @param {string} distDir dist のディレクトリ
 * @returns {string[]} 問題の一覧
 */
function checkIconLinks(html, distDir) {
  const problems = []
  const hrefs = []
  for (const tag of html.matchAll(/<link\b[^>]*>/gi)) {
    const rel = /\brel\s*=\s*(["'])(.*?)\1/i.exec(tag[0])
    if (
      !rel ||
      !rel[2]
        .toLowerCase()
        .split(/\s+/)
        .some((t) => t === 'icon' || t.endsWith('-icon'))
    ) {
      continue
    }
    const href = /\bhref\s*=\s*(["'])(.*?)\1/i.exec(tag[0])
    if (!href) {
      problems.push('index.html の favicon の <link> に href がありません')
      continue
    }
    hrefs.push(href[2])
  }
  for (const href of hrefs) {
    if (EXTERNAL_HREF.test(href)) {
      problems.push(`index.html の favicon の href が外部を指しています: ${href}`)
      continue
    }
    const target = href.replace(/[?#].*$/, '').replace(/^\//, '')
    const resolved = path.resolve(distDir, target)
    if (target.split('/').includes('..') || !resolved.startsWith(distDir + path.sep)) {
      problems.push(`index.html の favicon の href が dist の外へ出ます: ${href}`)
    } else if (!existsSync(resolved) || !statSync(resolved).isFile()) {
      problems.push(`index.html の favicon の href が dist に無いファイルを指しています: ${href}`)
    }
  }
  if (!hrefs.some((h) => /\.svg(?:[?#].*)?$/i.test(h))) {
    problems.push('index.html に SVG の favicon の <link> がありません')
  }
  if (!hrefs.some((h) => /\.(?:ico|png)(?:[?#].*)?$/i.test(h))) {
    problems.push('index.html に .ico（か .png）の favicon の <link> がありません')
  }
  return problems
}

/**
 * dist の素材と index.html の favicon の指定を確かめる。
 * @param {{ publicDir: string, distDir: string }} dirs public/ と dist のディレクトリ
 * @returns {string[]} 問題の一覧（空なら合格）
 */
export function checkDistAssets({ publicDir, distDir }) {
  const problems = []
  const sources = existsSync(publicDir) ? listFiles(publicDir) : []
  if (sources.length === 0) {
    problems.push('public/ が空です（素材がありません）')
  }
  if (!existsSync(distDir)) {
    problems.push('dist がありません（先にビルドしてください）')
    return problems
  }
  for (const relative of sources) {
    const built = path.join(distDir, relative)
    if (!existsSync(built) || !statSync(built).isFile()) {
      problems.push(`dist に ${relative} がありません`)
      continue
    }
    const expected = readFileSync(path.join(publicDir, relative))
    const actual = readFileSync(built)
    if (!actual.equals(expected)) {
      problems.push(`dist の ${relative} が public/ と違います`)
      continue
    }
    const ext = path.extname(relative).toLowerCase()
    if (ext === '.svg') {
      const result = checkSvgContent(actual.toString('utf8'), { standalone: true })
      if (!result.ok) {
        problems.push(
          `dist の ${relative} が SVG の検査に通りません: ${result.code} @${result.position}`,
        )
      }
    } else if (ext === '.ico') {
      const result = inspectIco(actual)
      if (!result.ok) {
        problems.push(`dist の ${relative} が ICO の形に合いません: ${result.code}`)
      }
    } else if (ext === '.png') {
      const result = inspectPng(actual)
      if (!result.ok) {
        problems.push(`dist の ${relative} が PNG の形に合いません: ${result.code}`)
      }
    }
  }
  const indexPath = path.join(distDir, 'index.html')
  if (existsSync(indexPath)) {
    problems.push(...checkIconLinks(readFileSync(indexPath, 'utf8'), path.resolve(distDir)))
  } else {
    problems.push('dist/index.html がありません')
  }
  return problems
}

function main() {
  const problems = checkDistAssets({
    publicDir: path.join(frontendRoot, 'public'),
    distDir: path.join(frontendRoot, 'dist'),
  })
  if (problems.length > 0) {
    console.error('dist の素材の確認に失敗しました:')
    for (const p of problems) {
      console.error(`  ${p}`)
    }
    process.exit(1)
  }
  console.log(
    'dist の素材の確認: public/ の素材がすべて dist にあり、index.html の favicon の指定も正しいです。',
  )
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main()
}
