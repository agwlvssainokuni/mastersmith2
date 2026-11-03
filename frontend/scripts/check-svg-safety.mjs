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
// 自作の SVG の中身を許可リストで確かめる（U6 の SD1）。許可リストに載るものだけを通し、想定外の形はその場で拒否する（fail-closed）。
// - 字句の走査は `<` を起点にした 1 回の前から後ろへの走査（状態機械）で、全体に正規表現をかけない。XML のパーサーは足さない。
// - 名前は大文字小文字を区別して完全一致で照らす。`href`・`xlink:href`・`style`・`class` は属性の許可リストに載せない。
// - 最初の違反で止まり、違反の種別のコードと位置（先頭からの文字数）だけを返す。文書の中身は返さない。
// - 検査の対象は frontend/ の下のすべての `.svg`（ライセンスヘッダーの検査と同じ範囲。集める関数は共有する）。
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { collectFiles } from './check-license-header.mjs'

const frontendRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const repoRoot = path.resolve(frontendRoot, '..')

/** 1 ファイルの文字数の上限（NFR2.1.1。ASCII だけに絞るため文字数 = バイト数） */
export const MAX_SVG_LENGTH = 4096
/** 要素の入れ子の深さの上限（根を 1 とする） */
export const MAX_DEPTH = 8

/** 要素の許可リスト（SD1.4） */
export const ALLOWED_ELEMENTS = new Set([
  'svg',
  'g',
  'path',
  'circle',
  'ellipse',
  'rect',
  'line',
  'polyline',
  'polygon',
  'title',
  'desc',
  'defs',
  'clipPath',
])

const NUMERIC = /^[0-9A-Za-z\s,.+-]*$/
const TRANSFORM = /^[A-Za-z0-9\s,.+()-]*$/
const PAINT = /^(?:none|currentColor|#[0-9A-Fa-f]{3,8}|url\(#[A-Za-z][A-Za-z0-9_-]*\))$/
const URL_REF = /^url\(#[A-Za-z][A-Za-z0-9_-]*\)$/
const ID = /^[A-Za-z][A-Za-z0-9_-]*$/
const SVG_NAMESPACE = 'http://www.w3.org/2000/svg'
const TITLE_TEXT = /^[A-Za-z0-9\s.,-]*$/

/** 値の文法の関数 */
const oneOf = (words) => (value) => words.includes(value)
const matches = (re) => (value) => re.test(value)

/** 属性の許可リストと値の文法（SD1.5）。xmlns は根の svg だけ（走査の側で見る） */
export const ATTRIBUTE_GRAMMARS = new Map([
  ['xmlns', (value) => value === SVG_NAMESPACE],
  ...[
    'viewBox',
    'width',
    'height',
    'cx',
    'cy',
    'r',
    'rx',
    'ry',
    'x',
    'y',
    'x1',
    'y1',
    'x2',
    'y2',
    'stroke-width',
    'opacity',
    'd',
    'points',
  ].map((name) => [name, matches(NUMERIC)]),
  ['transform', matches(TRANSFORM)],
  ['fill', matches(PAINT)],
  ['stroke', matches(PAINT)],
  ['clip-path', matches(URL_REF)],
  ['id', matches(ID)],
  ['stroke-linecap', oneOf(['butt', 'round', 'square'])],
  ['stroke-linejoin', oneOf(['miter', 'round', 'bevel'])],
  ['fill-rule', oneOf(['nonzero', 'evenodd'])],
  ['clip-rule', oneOf(['nonzero', 'evenodd'])],
  ['role', oneOf(['img', 'presentation', 'none'])],
  ['aria-hidden', oneOf(['true', 'false'])],
  ['focusable', oneOf(['true', 'false'])],
])

/** @typedef {{ ok: true } | { ok: false, code: string, position: number }} SvgCheckResult */

const fail = (code, position) => ({ ok: false, code, position })
const isSpace = (ch) => ch === ' ' || ch === '\t' || ch === '\n' || ch === '\r'

/**
 * 名前を読む（終わりの文字の手前まで）。
 * @param {string} s 文字列
 * @param {number} from 読み始め
 * @param {string} stops 名前の終わりになる文字（空白は常に終わり）
 * @returns {number} 名前の終わりの位置
 */
function readName(s, from, stops) {
  let j = from
  while (j < s.length && !isSpace(s[j]) && !stops.includes(s[j])) {
    j++
  }
  return j
}

/**
 * 入口の確かめ（SD1.2）。長さと、使ってよい文字（ASCII の印字可能な文字・タブ・改行）。
 * @param {unknown} content 検査する文字列
 * @returns {SvgCheckResult | null} 違反があれば結果、無ければ null
 */
function checkEntrance(content) {
  if (typeof content !== 'string' || content.length > MAX_SVG_LENGTH) {
    return fail('too-large', 0)
  }
  for (let i = 0; i < content.length; i++) {
    const code = content.charCodeAt(i)
    const printable = code >= 0x20 && code <= 0x7e
    if (!printable && code !== 0x09 && code !== 0x0a && code !== 0x0d) {
      return fail('non-ascii-or-control', i)
    }
  }
  return null
}

/**
 * 開始タグを読み、属性を確かめる。
 * @returns {{ result: SvgCheckResult, end: number, selfClose: boolean, name: string }}
 */
function readOpenTag(content, pos, isRoot) {
  const nameEnd = readName(content, pos + 1, '/>')
  const name = content.slice(pos + 1, nameEnd)
  const bad = (code, at) => ({ result: fail(code, at), end: pos, selfClose: false, name })
  if (!ALLOWED_ELEMENTS.has(name)) {
    return bad('unknown-element', pos)
  }
  const seen = new Set()
  let j = nameEnd
  for (;;) {
    const before = j
    while (j < content.length && isSpace(content[j])) {
      j++
    }
    const spaced = j > before
    const c = content[j]
    if (c === undefined) {
      return bad('unterminated-tag', j)
    }
    if (c === '>') {
      return { result: { ok: true }, end: j + 1, selfClose: false, name, xmlns: seen.has('xmlns') }
    }
    if (c === '/') {
      if (content[j + 1] !== '>') {
        return bad('bad-slash', j)
      }
      return { result: { ok: true }, end: j + 2, selfClose: true, name, xmlns: seen.has('xmlns') }
    }
    if (!spaced) {
      return bad('attr-spacing', j)
    }
    const attrEnd = readName(content, j, '=/>')
    const attr = content.slice(j, attrEnd)
    const grammar = ATTRIBUTE_GRAMMARS.get(attr)
    if (grammar === undefined || (attr === 'xmlns' && !isRoot)) {
      return bad('unknown-attribute', j)
    }
    if (seen.has(attr)) {
      return bad('duplicate-attribute', j)
    }
    seen.add(attr)
    if (content[attrEnd] !== '=') {
      return bad('no-equals', attrEnd)
    }
    const quote = content[attrEnd + 1]
    if (quote !== '"' && quote !== "'") {
      return bad('unquoted', attrEnd + 1)
    }
    const close = content.indexOf(quote, attrEnd + 2)
    if (close < 0) {
      return bad('unterminated-value', attrEnd + 1)
    }
    if (!grammar(content.slice(attrEnd + 2, close))) {
      return bad(`bad-value:${attr}`, attrEnd + 2)
    }
    j = close + 1
  }
}

/**
 * SVG の中身を許可リストで確かめる。
 * @param {string} content 検査する文字列
 * @param {{ standalone?: boolean }} [options] standalone=true（既定）は `.svg` のファイル（先頭のヘッダーのコメントと根の xmlns が必須）。
 *   false は画面の部品を描いた結果（ヘッダーも xmlns も要求しない）
 * @returns {SvgCheckResult} 最初の違反（種別のコードと位置）か `{ ok: true }`
 */
export function checkSvgContent(content, options = {}) {
  const standalone = options.standalone !== false
  const entrance = checkEntrance(content)
  if (entrance) {
    return entrance
  }
  let pos = 0
  if (standalone) {
    if (!content.startsWith('<!--')) {
      return fail('header-missing', 0)
    }
    const end = content.indexOf('-->', 4)
    if (end < 0) {
      return fail('header-missing', 0)
    }
    const body = content.slice(4, end)
    if (body.includes('--') || body.includes('<')) {
      return fail('comment-body', 4)
    }
    pos = end + 3
  }
  const stack = []
  let rootSeen = false
  let rootClosed = false
  while (pos < content.length) {
    if (content[pos] !== '<') {
      const next = content.indexOf('<', pos)
      const stop = next < 0 ? content.length : next
      const text = content.slice(pos, stop)
      if (text.includes('>')) {
        return fail('stray-gt', pos + text.indexOf('>'))
      }
      const inTitle = stack.length > 0 && ['title', 'desc'].includes(stack[stack.length - 1])
      if (!(inTitle ? TITLE_TEXT.test(text) : text.trim() === '')) {
        return fail('text', pos)
      }
      pos = stop
      continue
    }
    const kind = content[pos + 1]
    if (kind === '?') {
      return fail('processing-instruction', pos)
    }
    if (kind === '!') {
      if (content.startsWith('<![CDATA[', pos)) {
        return fail('cdata', pos)
      }
      const head = content.slice(pos, pos + 9).toUpperCase()
      if (head.startsWith('<!DOCTYPE')) {
        return fail('doctype', pos)
      }
      if (head.startsWith('<!ENTITY')) {
        return fail('entity', pos)
      }
      return fail('comment-or-decl', pos)
    }
    if (kind === '/') {
      const nameEnd = readName(content, pos + 2, '/>')
      const name = content.slice(pos + 2, nameEnd)
      let j = nameEnd
      while (j < content.length && isSpace(content[j])) {
        j++
      }
      if (content[j] !== '>') {
        return fail('bad-close', pos)
      }
      if (stack.length === 0 || stack[stack.length - 1] !== name) {
        return fail('nesting', pos)
      }
      stack.pop()
      if (stack.length === 0) {
        rootClosed = true
      }
      pos = j + 1
      continue
    }
    if (rootClosed) {
      return fail('after-root', pos)
    }
    const tag = readOpenTag(content, pos, !rootSeen)
    if (!tag.result.ok) {
      return tag.result
    }
    if (!rootSeen) {
      if (tag.name !== 'svg') {
        return fail('root-not-svg', pos)
      }
      if (standalone && !tag.xmlns) {
        return fail('xmlns-missing', pos)
      }
      rootSeen = true
    }
    if (stack.length >= MAX_DEPTH) {
      return fail('too-deep', pos)
    }
    if (tag.selfClose) {
      if (stack.length === 0) {
        rootClosed = true
      }
    } else {
      stack.push(tag.name)
    }
    pos = tag.end
  }
  if (stack.length > 0) {
    return fail('unclosed', content.length)
  }
  return rootSeen ? { ok: true } : fail('no-root', content.length)
}

/**
 * 検査の対象の `.svg` を集める（ライセンスヘッダーの検査と同じ集める関数・同じ除外）。
 * @param {string} root 探すディレクトリ（既定は frontend/）
 * @returns {string[]} ファイルの絶対パス
 */
export function collectSvgFiles(root = frontendRoot) {
  return collectFiles(root, (ext) => ext === '.svg')
}

function main() {
  const problems = []
  const files = collectSvgFiles()
  for (const file of files) {
    const result = checkSvgContent(readFileSync(file, 'utf8'), { standalone: true })
    if (!result.ok) {
      problems.push(`${path.relative(repoRoot, file)}: ${result.code} @${result.position}`)
    }
  }
  if (problems.length > 0) {
    console.error('SVG の中身の確認に失敗しました:')
    for (const p of problems) {
      console.error(`  ${p}`)
    }
    process.exit(1)
  }
  console.log(`SVG の中身の確認: ${files.length} 件のファイルが許可リストの内側です。`)
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main()
}
