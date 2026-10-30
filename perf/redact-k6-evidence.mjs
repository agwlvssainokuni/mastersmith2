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
// k6 の結果に入る認証の値（JWT の形の文字列）を伏せてから記録（evidence/）に置く道具。依存は Node の組み込みだけ。
//
// 口は 3 つ:
//   node perf/redact-k6-evidence.mjs <入力の summary json> <evidence/ の中の出力>          … JSON の口
//   node perf/redact-k6-evidence.mjs --text <入力のテキスト> <evidence/ の中の出力>        … テキストの口
//   node perf/redact-k6-evidence.mjs --scan <根のディレクトリ>                               … evidence の検査の口
//
// 終わりのコード（3 つの口で同じ意味）:
//   0 成功 / 1 呼び出しの誤り / 2 入力の誤り / 3 キーの衝突 / 4 残存の検出 / 5 書き出しの失敗
//
// 表示（標準出力・標準エラー）には、件数・パス・位置・道だけを出し、見つけた値とその前後の文字を出さない。
import { randomBytes } from 'node:crypto'
import { promises as fsp } from 'node:fs'
import path from 'node:path'
import process from 'node:process'
import { fileURLToPath } from 'node:url'

/** JWT の形の置き換えの印 */
export const JWT_MARK = '[REDACTED-JWT]'
/** setup() の返す値（setup_data）の下の文字列の置き換えの印 */
export const SETUP_MARK = '[REDACTED-SETUP]'
/** 入力（と evidence の検査の 1 ファイル）の大きさの上限（バイト） */
export const MAX_INPUT_BYTES = 64 * 1024 * 1024
/** NUL を含んでもよい（UTF-8 として読まない）バイナリの拡張子。足すときは依頼者の承認が要る */
export const BINARY_EXTENSIONS = Object.freeze([
  '.png',
  '.jpg',
  '.jpeg',
  '.gif',
  '.webp',
  '.svgz',
  '.gz',
  '.zip',
  '.pdf',
])

// ---- 層 1: 形の定義・置換・残存の検査・木の置換 ----

// JWT の形の定義（BR1.1）は、次の正規表現を左から順に重ならずに当てた結果（matchAll）と同じとする:
//
//   /(?<![A-Za-z0-9])eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g
//
// 正規表現のままだと、`-eyJ-eyJ…` のように始まりの候補が続き `.` の無い文字列で試しが入力の長さの 2 乗になる
// （64 KiB で約 1.3 秒、1 MiB では約 5 分の見込み）。そのため、同じ定義を左から 1 度だけ読む関数 scanJwtShapes で
// 実装する。置換（redactJwt）・残存の検査（findResidual）・evidence の検査はこの関数だけを使う。
// 区間の切れ目は、1 文字の集合の繰り返しだけの粘着の正規表現（RUN・GAP。戻りが無く線形）で読む。1 文字ずつの JS の
// ループより速く、カバレッジの計測の下でも 64 MiB を既定の時間の上限の中で読める。
// 定義と同じ結果になることは、テストの中に置いた上の正規表現との突き合わせ（性質ベースのテスト）で確かめる。

const CODE_DOT = 0x2e
/** base64url の文字（英大小・数字・- ・_）の続き */
const RUN = /[A-Za-z0-9_-]*/y
/** base64url でない文字の続き */
const GAP = /[^A-Za-z0-9_-]*/y

/** 英数字か（JWT の形の直前に来てはいけない文字） */
function isAlnumCode(c) {
  return (c >= 0x30 && c <= 0x39) || (c >= 0x41 && c <= 0x5a) || (c >= 0x61 && c <= 0x7a)
}

/** 位置 from から始まる base64url の文字の続きの終わり（続きが無ければ from） */
function runEnd(text, from) {
  RUN.lastIndex = from
  RUN.test(text)
  return RUN.lastIndex
}

/** 位置 from から始まる base64url でない文字の続きの終わり */
function gapEnd(text, from) {
  GAP.lastIndex = from
  GAP.test(text)
  return GAP.lastIndex
}

/**
 * JWT の形の区間（始まりと終わりの位置）を左から順にすべて返す。値そのものは返さない。
 * 文字列を base64url の文字の続き（区間）に分けて左から見る。1 件は「区間 1 の中の、直前が英数字でない eyJ
 * （その後ろに区間 1 の文字が 1 つ以上残るもの）」から始まり、区間 1 の直後が `.`、続く区間 2 が 1 文字以上で直後が `.`、
 * 続く区間 3 が 1 文字以上のときに、区間 3 の終わりまでとなる。eyJ を探す位置は前へ戻らないため、全体で線形の時間。
 * @param {string} text 調べる文字列
 * @returns {{ start: number, end: number }[]} 区間の一覧
 */
function scanJwtShapes(text) {
  const spans = []
  const n = text.length
  // nextEyj は、最後に探し始めた位置以降の最初の eyJ の位置（無ければ n）
  let nextEyj = -1
  const eyjAtOrAfter = (from) => {
    if (nextEyj < from) {
      const found = text.indexOf('eyJ', from)
      nextEyj = found === -1 ? n : found
    }
    return nextEyj
  }
  let p = gapEnd(text, 0)
  while (p < n) {
    const s1 = p
    const e1 = runEnd(text, s1)
    p = gapEnd(text, e1)
    if (e1 - s1 < 4 || text.charCodeAt(e1) !== CODE_DOT) {
      continue
    }
    const s2 = e1 + 1
    const e2 = runEnd(text, s2)
    if (e2 === s2 || text.charCodeAt(e2) !== CODE_DOT) {
      continue
    }
    const s3 = e2 + 1
    const e3 = runEnd(text, s3)
    if (e3 === s3) {
      continue
    }
    let start = -1
    for (let i = eyjAtOrAfter(s1); i <= e1 - 4; i = eyjAtOrAfter(i + 1)) {
      if (i === 0 || !isAlnumCode(text.charCodeAt(i - 1))) {
        start = i
        break
      }
    }
    if (start >= 0) {
      spans.push({ start, end: e3 })
      p = gapEnd(text, e3)
    }
  }
  return spans
}

/**
 * 文字列の中の JWT の形をすべて JWT_MARK に置き換える（BR1.2）。
 * @param {string} text 置き換える文字列
 * @returns {{ text: string, count: number }} 置き換えた文字列と件数
 */
export function redactJwt(text) {
  const spans = scanJwtShapes(text)
  if (spans.length === 0) {
    return { text, count: 0 }
  }
  const parts = []
  let last = 0
  for (const { start, end } of spans) {
    parts.push(text.slice(last, start), JWT_MARK)
    last = end
  }
  parts.push(text.slice(last))
  return { text: parts.join(''), count: spans.length }
}

/**
 * 置換を通さずに、文字列に JWT の形が残っていないかを調べる（BR2.1）。件数と位置だけを返す。
 * 行は LF で数え、桁は行の始まりからの文字の位置（1 始まり）、offset は文字の通し番号（0 始まり）。
 * @param {string} text 調べる文字列
 * @returns {{ count: number, positions: { line: number, column: number, offset: number }[] }} 件数と位置
 */
export function findResidual(text) {
  const spans = scanJwtShapes(text)
  const positions = []
  let line = 1
  let lineStart = 0
  let nextNewline = spans.length > 0 ? text.indexOf('\n') : -1
  for (const { start } of spans) {
    while (nextNewline !== -1 && nextNewline < start) {
      line++
      lineStart = nextNewline + 1
      nextNewline = text.indexOf('\n', lineStart)
    }
    positions.push({ line, column: start - lineStart + 1, offset: start })
  }
  return { count: spans.length, positions }
}

/** 値が配列でないオブジェクトか */
function isPlainObject(value) {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

/** 上の階層が setup_data を自分自身のキーとして持つか */
function hasTopLevelSetup(value) {
  return isPlainObject(value) && Object.hasOwn(value, 'setup_data')
}

/**
 * 道（親へのつながり）から表示の文字列を作る。キー名は JSON の文字列の形で書く。
 * @param {{ up: object | null, key: string | number } | null} node 道の末端
 * @returns {string} 例: $["setup_data"][0]
 */
function pathText(node) {
  const parts = []
  for (let n = node; n !== null; n = n.up) {
    parts.push(typeof n.key === 'number' ? `[${n.key}]` : `[${JSON.stringify(n.key)}]`)
  }
  return '$' + parts.reverse().join('')
}

/** 新しいオブジェクトに、元の並びのまま値の入れ物を作る（__proto__ のキーも自分自身の値として持つ） */
function defineSlot(target, key) {
  Object.defineProperty(target, key, {
    value: undefined,
    writable: true,
    enumerable: true,
    configurable: true,
  })
}

/**
 * JSON の木のすべての文字列（値・配列の要素・キー名）に redactJwt を当て、上の階層の setup_data の下の文字列を
 * SETUP_MARK に替える（BR1.3〜BR1.7）。再帰を使わず、明示の積み上げでたどる。
 * @param {unknown} value JSON.parse した木
 * @returns {{ ok: true, tree: unknown, replacedCount: number, setupRedactedCount: number }
 *   | { ok: false, collisionPath: string }} 置き換えた木、またはキーの衝突の道
 */
export function redactTree(value) {
  let replacedCount = 0
  let setupRedactedCount = 0
  const topLevelSetup = hasTopLevelSetup(value)
  const holder = [undefined]
  const work = [{ src: value, target: holder, slot: 0, inSetup: false, node: null }]
  while (work.length > 0) {
    const { src, target, slot, inSetup, node } = work.pop()
    if (typeof src === 'string') {
      if (inSetup) {
        setupRedactedCount++
        target[slot] = SETUP_MARK
      } else {
        const r = redactJwt(src)
        replacedCount += r.count
        target[slot] = r.text
      }
      continue
    }
    if (src === null || typeof src !== 'object') {
      target[slot] = src
      continue
    }
    if (Array.isArray(src)) {
      const array = Array.from({ length: src.length })
      target[slot] = array
      for (let i = src.length - 1; i >= 0; i--) {
        work.push({ src: src[i], target: array, slot: i, inSetup, node: { up: node, key: i } })
      }
      continue
    }
    const object = {}
    target[slot] = object
    const children = []
    for (const key of Object.keys(src)) {
      const r = redactJwt(key)
      replacedCount += r.count
      if (Object.hasOwn(object, r.text)) {
        return { ok: false, collisionPath: pathText(node) }
      }
      defineSlot(object, r.text)
      const childInSetup = inSetup || (node === null && topLevelSetup && key === 'setup_data')
      children.push({
        src: src[key],
        target: object,
        slot: r.text,
        inSetup: childInSetup,
        node: { up: node, key: r.text },
      })
    }
    for (let i = children.length - 1; i >= 0; i--) {
      work.push(children[i])
    }
  }
  return { ok: true, tree: holder[0], replacedCount, setupRedactedCount }
}

/**
 * 置換を通さずに、上の階層の setup_data の下に SETUP_MARK でない文字列が残っていないかを調べる（BR2.4）。
 * 件数と道（キー名は redactJwt を通したもの）だけを返す。
 * @param {unknown} tree JSON.parse した木
 * @returns {{ count: number, paths: string[] }} 件数と道
 */
export function findUnredactedSetup(tree) {
  const paths = []
  if (!hasTopLevelSetup(tree)) {
    return { count: 0, paths }
  }
  const work = [{ value: tree.setup_data, node: { up: null, key: 'setup_data' } }]
  while (work.length > 0) {
    const { value, node } = work.pop()
    if (typeof value === 'string') {
      if (value !== SETUP_MARK) {
        paths.push(pathText(node))
      }
    } else if (Array.isArray(value)) {
      for (let i = value.length - 1; i >= 0; i--) {
        work.push({ value: value[i], node: { up: node, key: i } })
      }
    } else if (isPlainObject(value)) {
      const keys = Object.keys(value)
      for (let i = keys.length - 1; i >= 0; i--) {
        work.push({ value: value[keys[i]], node: { up: node, key: redactJwt(keys[i]).text } })
      }
    }
  }
  return { count: paths.length, paths }
}

// ---- 層 2: 読み書きの守り・報告・JSON の口とテキストの口 ----

/** 本物の表示と読み書き。テストは main・checkAndWrite の io で一部だけを差し替える */
const REAL_IO = {
  stdout: (line) => process.stdout.write(line + '\n'),
  stderr: (line) => process.stderr.write(line + '\n'),
  stat: (target) => fsp.stat(target),
  readFile: (target) => fsp.readFile(target),
  realpath: (target) => fsp.realpath(target),
  readdir: (target) => fsp.readdir(target, { withFileTypes: true }),
  open: (target, flags) => fsp.open(target, flags),
  write: (handle, data) => handle.writeFile(data),
  close: (handle) => handle.close(),
  rename: (from, to) => fsp.rename(from, to),
  unlink: (target) => fsp.unlink(target),
  /**
   * 書いている間だけ SIGINT・SIGTERM の片付けを登録する。受けたら片付けてから 128＋番号で終える。
   * @param {() => Promise<void>} cleanup 片付け
   * @returns {() => void} 登録を外す関数
   */
  onSignal: (cleanup) => {
    const listener = (signal) => {
      cleanup().finally(() => process.exit(signal === 'SIGINT' ? 130 : 143))
    }
    process.once('SIGINT', listener)
    process.once('SIGTERM', listener)
    return () => {
      process.removeListener('SIGINT', listener)
      process.removeListener('SIGTERM', listener)
    }
  },
}

/** 差し替えの無い口を本物で埋める */
function withDefaults(io) {
  return { ...REAL_IO, ...io }
}

/** 表示する前のパス・道（JWT の形を伏せる） */
function shown(text) {
  return redactJwt(text).text
}

/** ファイルの誤りの code だけを取り出す（文言は入力の名前を含みうるため出さない） */
function errorCode(error) {
  const code = error !== null && typeof error === 'object' ? error.code : undefined
  return typeof code === 'string' && /^[A-Z0-9_]+$/.test(code) ? code : 'UNKNOWN'
}

/** 誤りの種類の名前だけを取り出す */
function errorName(error) {
  return error instanceof Error && /^[A-Za-z]+$/.test(error.name) ? error.name : 'Unknown'
}

/** JSON.parse の誤りの文言から位置の数だけを抜く（文言そのものは入力の断片を含むため出さない） */
function jsonErrorPosition(error) {
  const found = error instanceof Error ? /position (\d+)/.exec(error.message) : null
  return found ? Number(found[1]) : null
}

/** 表示する位置の件数の上限（残りは件数だけ） */
const MAX_SHOWN_POSITIONS = 10

/**
 * 決めた文言だけを表示する報告の部品。受けるのは件数・位置・パス・道・code だけで、パスと道は伏せてから出す。
 * 任意の文字列をそのまま出す口は持たない。
 */
const REPORT = {
  usage: (io) =>
    io.stderr(
      '使い方: node perf/redact-k6-evidence.mjs <入力の summary json> <evidence/ の中の出力>' +
        ' | --text <入力> <evidence/ の中の出力> | --scan <根のディレクトリ>',
    ),
  textJsonOutput: (io, file) =>
    io.stderr(
      `テキストの口の出力に .json は使えません（summary json は JSON の口で置きます）: ${shown(file)}`,
    ),
  outputParentMissing: (io, file) =>
    io.stderr(`出力の置き場のディレクトリがありません: ${shown(file)}`),
  outputNotEvidence: (io, file) =>
    io.stderr(`出力は evidence という名前のディレクトリの下に置いてください: ${shown(file)}`),
  sameFile: (io, file) => io.stderr(`入力と出力が同じファイルです: ${shown(file)}`),
  inputUnreadable: (io, file, code) => io.stderr(`入力を読めません（${code}）: ${shown(file)}`),
  inputNotFile: (io, file) => io.stderr(`入力が通常のファイルではありません: ${shown(file)}`),
  inputTooLarge: (io, file, size) =>
    io.stderr(
      `入力が上限（${MAX_INPUT_BYTES} バイト）を超えています（${size} バイト）: ${shown(file)}`,
    ),
  inputEmpty: (io, file) => io.stderr(`入力が空か、空白だけです: ${shown(file)}`),
  inputNotUtf8: (io, file) => io.stderr(`入力が UTF-8 として読めません: ${shown(file)}`),
  inputHasNul: (io, file) =>
    io.stderr(`入力に NUL の文字が含まれています（テキストとして置けません）: ${shown(file)}`),
  inputNotJson: (io, file, position) =>
    io.stderr(
      position === null
        ? `入力が JSON として読めません: ${shown(file)}`
        : `入力が JSON として読めません（位置 ${position}）: ${shown(file)}`,
    ),
  inputTooDeep: (io, file) => io.stderr(`入力の入れ子が深すぎて扱えません: ${shown(file)}`),
  textGotSummary: (io, file) =>
    io.stderr(
      'テキストの口に summary json（上の階層に setup_data を持つ JSON）は渡せません。' +
        `JSON の口で置いてください: ${shown(file)}`,
    ),
  collision: (io, objectPath) =>
    io.stderr(
      `キーの名前を置き換えると、同じオブジェクトの中で名前が重なります（道: ${shown(objectPath)}）。` +
        '出力は書いていません。',
    ),
  outputNotJson: (io) =>
    io.stderr('書き出す前の読み直しで JSON として読めませんでした。出力は書いていません。'),
  residual: (io, count, positions) => {
    io.stderr(`書き出す前の検査で JWT の形が ${count} 件残っています。出力は書いていません。`)
    for (const { line, column } of positions.slice(0, MAX_SHOWN_POSITIONS)) {
      io.stderr(`  行 ${line} 桁 ${column}`)
    }
    if (positions.length > MAX_SHOWN_POSITIONS) {
      io.stderr(`  ほか ${positions.length - MAX_SHOWN_POSITIONS} 件`)
    }
  },
  setupResidual: (io, count, paths) => {
    io.stderr(
      `書き出す前の検査で setup_data の下に伏せていない文字列が ${count} 件あります。出力は書いていません。`,
    )
    for (const p of paths.slice(0, MAX_SHOWN_POSITIONS)) {
      io.stderr(`  道: ${shown(p)}`)
    }
    if (paths.length > MAX_SHOWN_POSITIONS) {
      io.stderr(`  ほか ${paths.length - MAX_SHOWN_POSITIONS} 件`)
    }
  },
  writeFailed: (io, file, code) =>
    io.stderr(`書き出しに失敗しました（${code}）。出力は変えていません: ${shown(file)}`),
  tempCleanupFailed: (io, file, code) =>
    io.stderr(`一時ファイルを消せませんでした（${code}）。手で消してください: ${shown(file)}`),
  success: (io, file, replaced, setupRedacted) =>
    io.stdout(
      `置き換えた JWT の形: ${replaced} 件、[REDACTED-SETUP] にした値: ${setupRedacted} 件、出力: ${shown(file)}`,
    ),
  unexpected: (io, name) => io.stderr(`想定外の誤りで止まりました（種類: ${name}）。`),
}

/**
 * 書き出す直前の文字列に、置換とは独立の残存の検査を当て（BR2.1・BR2.4）、通れば同じディレクトリの一時ファイルに
 * 書いてから名前を替えて置く（BR3.3）。JSON の口とテキストの口の両方がこの関数を通る。
 * @param {string} text 書き出す文字列
 * @param {{ json: boolean }} options json のときは読み直した木の setup_data も確かめる
 * @param {string} outputPath 出力のパス
 * @param {object} [io] 表示と読み書きの差し替え
 * @returns {Promise<number>} 0 成功 / 4 残存の検出 / 5 書き出しの失敗
 */
export async function checkAndWrite(text, options, outputPath, io = {}) {
  const env = withDefaults(io)
  const residual = findResidual(text)
  let setup = { count: 0, paths: [] }
  if (options.json) {
    let reread
    try {
      reread = JSON.parse(text)
    } catch {
      REPORT.outputNotJson(env)
      return 4
    }
    setup = findUnredactedSetup(reread)
  }
  if (residual.count > 0 || setup.count > 0) {
    if (residual.count > 0) {
      REPORT.residual(env, residual.count, residual.positions)
    }
    if (setup.count > 0) {
      REPORT.setupResidual(env, setup.count, setup.paths)
    }
    return 4
  }

  const directory = path.dirname(outputPath)
  const temp = path.join(
    directory,
    `.${path.basename(outputPath)}.${process.pid}.${randomBytes(4).toString('hex')}.tmp`,
  )
  let created = false
  const cleanup = async () => {
    if (!created) {
      return
    }
    created = false
    try {
      await env.unlink(temp)
    } catch (error) {
      REPORT.tempCleanupFailed(env, temp, errorCode(error))
    }
  }
  const unregister = env.onSignal(cleanup)
  try {
    const handle = await env.open(temp, 'wx')
    created = true
    try {
      await env.write(handle, Buffer.from(text, 'utf8'))
    } finally {
      await env.close(handle)
    }
    await env.rename(temp, outputPath)
    created = false
    return 0
  } catch (error) {
    REPORT.writeFailed(env, outputPath, errorCode(error))
    await cleanup()
    return 5
  } finally {
    unregister()
  }
}

/**
 * 出力のパスを確かめ、置く先の絶対パスを返す（誤りなら null。表示は済ませる）。
 * @returns {Promise<string | null>}
 */
async function resolveOutput(env, input, output) {
  let parent
  try {
    parent = await env.realpath(path.dirname(path.resolve(output)))
  } catch {
    REPORT.outputParentMissing(env, output)
    return null
  }
  const parentStat = await env.stat(parent)
  if (!parentStat.isDirectory()) {
    REPORT.outputParentMissing(env, output)
    return null
  }
  if (!parent.split(path.sep).includes('evidence')) {
    REPORT.outputNotEvidence(env, output)
    return null
  }
  const target = path.join(parent, path.basename(output))
  let realInput = null
  try {
    realInput = await env.realpath(input)
  } catch {
    // 入力が無いときは、後の読み込みで入力の誤り（コード 2）として扱う
    realInput = null
  }
  if (realInput === target) {
    REPORT.sameFile(env, output)
    return null
  }
  return target
}

/**
 * 入力のバイト列を上限の中で読む（誤りなら null。表示は済ませる）。
 * @returns {Promise<Buffer | null>}
 */
async function readInput(env, input) {
  let info
  try {
    info = await env.stat(input)
  } catch (error) {
    REPORT.inputUnreadable(env, input, errorCode(error))
    return null
  }
  if (!info.isFile()) {
    REPORT.inputNotFile(env, input)
    return null
  }
  if (info.size > MAX_INPUT_BYTES) {
    REPORT.inputTooLarge(env, input, info.size)
    return null
  }
  let bytes
  try {
    bytes = await env.readFile(input)
  } catch (error) {
    REPORT.inputUnreadable(env, input, errorCode(error))
    return null
  }
  if (bytes.length > MAX_INPUT_BYTES) {
    REPORT.inputTooLarge(env, input, bytes.length)
    return null
  }
  return bytes
}

/** UTF-8 として厳しく読む（不正なバイトがあれば null） */
function decodeUtf8(bytes, keepBom) {
  try {
    return new TextDecoder('utf-8', { fatal: true, ignoreBOM: keepBom }).decode(bytes)
  } catch {
    return null
  }
}

/** JSON の口（契約 C7）: summary json を置き換えて evidence/ に置く */
async function jsonMode(args, env) {
  if (args.length !== 2) {
    REPORT.usage(env)
    return 1
  }
  const [input, output] = args
  const target = await resolveOutput(env, input, output)
  if (target === null) {
    return 1
  }
  const bytes = await readInput(env, input)
  if (bytes === null) {
    return 2
  }
  const text = decodeUtf8(bytes, false)
  if (text === null) {
    REPORT.inputNotUtf8(env, input)
    return 2
  }
  if (text.trim() === '') {
    REPORT.inputEmpty(env, input)
    return 2
  }
  let tree
  try {
    tree = JSON.parse(text)
  } catch (error) {
    if (error instanceof RangeError) {
      REPORT.inputTooDeep(env, input)
    } else {
      REPORT.inputNotJson(env, input, jsonErrorPosition(error))
    }
    return 2
  }
  const result = redactTree(tree)
  if (!result.ok) {
    REPORT.collision(env, result.collisionPath)
    return 3
  }
  let out
  try {
    out = JSON.stringify(result.tree, null, 2) + '\n'
  } catch {
    REPORT.inputTooDeep(env, input)
    return 2
  }
  const code = await checkAndWrite(out, { json: true }, target, env)
  if (code === 0) {
    REPORT.success(env, output, result.replacedCount, result.setupRedactedCount)
  }
  return code
}

/** テキストの口: k6 の標準出力・ログを置き換えて evidence/ に置く（BR1.8・BR3.4） */
async function textMode(args, env) {
  if (args.length !== 2) {
    REPORT.usage(env)
    return 1
  }
  const [input, output] = args
  if (path.extname(output).toLowerCase() === '.json') {
    REPORT.textJsonOutput(env, output)
    return 1
  }
  const target = await resolveOutput(env, input, output)
  if (target === null) {
    return 1
  }
  const bytes = await readInput(env, input)
  if (bytes === null) {
    return 2
  }
  if (bytes.includes(0)) {
    REPORT.inputHasNul(env, input)
    return 2
  }
  const text = decodeUtf8(bytes, true)
  if (text === null) {
    REPORT.inputNotUtf8(env, input)
    return 2
  }
  if (text.trim() === '') {
    REPORT.inputEmpty(env, input)
    return 2
  }
  let parsed = null
  try {
    parsed = JSON.parse(text.replace(/^﻿/, ''))
  } catch {
    // JSON として読めないのは普通のログ。そのまま進む
    parsed = null
  }
  if (hasTopLevelSetup(parsed)) {
    REPORT.textGotSummary(env, input)
    return 1
  }
  const result = redactJwt(text)
  const code = await checkAndWrite(result.text, { json: false }, target, env)
  if (code === 0) {
    REPORT.success(env, output, result.count, 0)
  }
  return code
}

/**
 * 道具の入口。
 * @param {string[]} argv 引数（node と道具のパスを除いたもの）
 * @param {object} [io] 表示と読み書きの差し替え（差し替えない口は本物）
 * @returns {Promise<number>} 終わりのコード
 */
export async function main(argv, io = {}) {
  const env = withDefaults(io)
  try {
    if (argv[0] === '--scan') {
      return await scanMode(argv.slice(1), env)
    }
    if (argv[0] === '--text') {
      return await textMode(argv.slice(1), env)
    }
    return await jsonMode(argv, env)
  } catch (error) {
    REPORT.unexpected(env, errorName(error))
    return 2
  }
}

// ---- 層 3: evidence の検査の口 ----

/** evidence の検査の表示（パスは根からの相対で、伏せてから出す） */
const SCAN_REPORT = {
  rootMissing: (io, root) => io.stderr(`調べる根のディレクトリがありません: ${shown(root)}`),
  link: (io, rel) => io.stdout(`リンク（たどらない）: ${shown(rel)}`),
  binary: (io, rel) => io.stdout(`バイナリとして読んだ（JWT の形だけを調べた）: ${shown(rel)}`),
  pathDetection: (io, rel, count) =>
    io.stderr(`ファイルのパスに JWT の形が ${count} 件あります: ${shown(rel)}`),
  textDetection: (io, rel, count, positions) => {
    io.stderr(`JWT の形が ${count} 件あります: ${shown(rel)}`)
    for (const { line, column } of positions.slice(0, MAX_SHOWN_POSITIONS)) {
      io.stderr(`  行 ${line} 桁 ${column}`)
    }
    if (count > MAX_SHOWN_POSITIONS) {
      io.stderr(`  ほか ${count - MAX_SHOWN_POSITIONS} 件`)
    }
  },
  binaryDetection: (io, rel, count, positions) => {
    io.stderr(`JWT の形が ${count} 件あります（バイナリ）: ${shown(rel)}`)
    for (const { offset } of positions.slice(0, MAX_SHOWN_POSITIONS)) {
      io.stderr(`  バイトの位置 ${offset}`)
    }
    if (count > MAX_SHOWN_POSITIONS) {
      io.stderr(`  ほか ${count - MAX_SHOWN_POSITIONS} 件`)
    }
  },
  tooLarge: (io, rel, size) =>
    io.stderr(
      `上限（${MAX_INPUT_BYTES} バイト）を超えるファイルです（${size} バイト）: ${shown(rel)}`,
    ),
  unreadable: (io, rel, code) => io.stderr(`読めないファイルです（${code}）: ${shown(rel)}`),
  notRegular: (io, rel) => io.stderr(`通常のファイルではありません: ${shown(rel)}`),
  nulText: (io, rel) =>
    io.stderr(
      `NUL を含むファイルです（バイナリの拡張子の一覧に無いため、記録に置けません）: ${shown(rel)}`,
    ),
  summary: (io, counts) =>
    io.stdout(
      `evidence の検査: 調べたファイル ${counts.checked} 件（うちバイナリとして読んだもの ${counts.binary} 件）、` +
        `リンク ${counts.links} 件、検出 ${counts.detections} 件（${counts.detectionFiles} ファイル）、` +
        `入力の誤り ${counts.errors} 件`,
    ),
}

/** evidence の下の 1 つのファイルを調べる（8.2節の判定の順） */
async function scanFile(env, full, rel, counts) {
  counts.checked++
  let found = 0
  const inPath = findResidual(rel)
  if (inPath.count > 0) {
    found += inPath.count
    SCAN_REPORT.pathDetection(env, rel, inPath.count)
  }
  let bytes = null
  try {
    const info = await env.stat(full)
    if (info.size > MAX_INPUT_BYTES) {
      counts.errors++
      SCAN_REPORT.tooLarge(env, rel, info.size)
    } else {
      bytes = await env.readFile(full)
    }
  } catch (error) {
    counts.errors++
    SCAN_REPORT.unreadable(env, rel, errorCode(error))
  }
  if (bytes !== null && bytes.length > MAX_INPUT_BYTES) {
    counts.errors++
    SCAN_REPORT.tooLarge(env, rel, bytes.length)
    bytes = null
  }
  if (bytes !== null && bytes.includes(0)) {
    if (BINARY_EXTENSIONS.includes(path.extname(full).toLowerCase())) {
      const inBinary = findResidual(bytes.toString('latin1'))
      if (inBinary.count > 0) {
        found += inBinary.count
        SCAN_REPORT.binaryDetection(env, rel, inBinary.count, inBinary.positions)
      } else {
        counts.binary++
        SCAN_REPORT.binary(env, rel)
      }
    } else {
      counts.errors++
      SCAN_REPORT.nulText(env, rel)
    }
  } else if (bytes !== null) {
    const inText = findResidual(bytes.toString('utf8'))
    if (inText.count > 0) {
      found += inText.count
      SCAN_REPORT.textDetection(env, rel, inText.count, inText.positions)
    }
  }
  if (found > 0) {
    counts.detections += found
    counts.detectionFiles++
  }
}

/**
 * evidence の検査の口（BR4.2・NFR8.68）: 根の下の、名前がちょうど evidence のディレクトリの下のすべての通常のファイルの
 * 中身とパスに JWT の形が無いかを調べる。記号のリンクはたどらない。`.` で始まる名前（一時ファイル）も調べる。
 * @returns {Promise<number>} 0 なし / 1 呼び出しの誤り / 2 入力の誤り / 4 検出
 */
async function scanMode(args, env) {
  if (args.length !== 1) {
    REPORT.usage(env)
    return 1
  }
  const [rootArg] = args
  let rootInfo = null
  try {
    rootInfo = await env.stat(rootArg)
  } catch {
    rootInfo = null
  }
  if (rootInfo === null || !rootInfo.isDirectory()) {
    SCAN_REPORT.rootMissing(env, rootArg)
    return 1
  }
  const counts = { checked: 0, binary: 0, links: 0, detections: 0, detectionFiles: 0, errors: 0 }
  const work = [
    { dir: rootArg, rel: '', inEvidence: path.basename(path.resolve(rootArg)) === 'evidence' },
  ]
  while (work.length > 0) {
    const { dir, rel, inEvidence } = work.pop()
    let entries
    try {
      entries = await env.readdir(dir)
    } catch (error) {
      counts.errors++
      SCAN_REPORT.unreadable(env, rel === '' ? '.' : rel, errorCode(error))
      continue
    }
    entries.sort((a, b) => (a.name < b.name ? -1 : a.name > b.name ? 1 : 0))
    const subdirs = []
    for (const entry of entries) {
      const childRel = rel === '' ? entry.name : `${rel}/${entry.name}`
      const childInEvidence = inEvidence || entry.name === 'evidence'
      if (entry.isSymbolicLink()) {
        if (childInEvidence) {
          counts.links++
          SCAN_REPORT.link(env, childRel)
        }
      } else if (entry.isDirectory()) {
        subdirs.push({
          dir: path.join(dir, entry.name),
          rel: childRel,
          inEvidence: childInEvidence,
        })
      } else if (!inEvidence) {
        continue
      } else if (entry.isFile()) {
        await scanFile(env, path.join(dir, entry.name), childRel, counts)
      } else {
        counts.errors++
        SCAN_REPORT.notRegular(env, childRel)
      }
    }
    work.push(...subdirs.reverse())
  }
  SCAN_REPORT.summary(env, counts)
  if (counts.detections > 0) {
    return 4
  }
  return counts.errors > 0 ? 2 : 0
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main(process.argv.slice(2)).then((code) => {
    process.exitCode = code
  })
}
