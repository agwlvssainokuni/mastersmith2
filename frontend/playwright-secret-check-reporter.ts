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
// E2E の報告に、残してはならない値が含まれないことを確かめる Playwright の報告の部品（Intent 260925-user-management の U4 で
// 作り、Intent 260930-user-admin の U5 で広げた。security-design.md 3.5）。json の報告の後に並べて使い、見つかれば実行の結果を
// 失敗にする。値は表示せず、値の種類とファイルの種類ごとの件数だけを出す。
// 1. 探す値: 環境変数の値（署名鍵・初期管理者のメールアドレスとパスワード）、値のファイル（e2e/support/secretValues.ts）の値、
//    値の形（110 が作る宛先・パスワードの形と氏名）。値はそのままの形に加えて、URL の形と JSON の形（\u の形を含む）でも探す。
//    runTag は 24 文字以上のときだけ単独で探し、短いときは宛先の形（u7-perf-<runTag>@）の中だけで探して警告する。
// 2. 探す先: json の報告（JSON として読み、すべての試験の結果の attachments[].body と stdout・stderr の buffer の base64 を
//    復号して探す）、test-results/ の下のすべてのファイル（trace.zip・error-context.md・添付を含む。値のファイルは除く）、
//    前の実行の playwright-report/（index.html に埋め込まれた base64 の zip と data/ の下）。zip は中央の目録を読み、
//    node:zlib の inflateRawSync で展開する（新しい依存を足さない）。
// 3. 読めない・展開できない・復号できないものがあれば、黙って通さず失敗にする。
// 4. 値のファイルは、見つかったかどうかと関係なく最後に消す。
// Playwright の決まりで default のエクスポートが要るため、構文の禁止を当てない frontend/ の直下に置く（設定のファイルと同じ扱い）。
// 使い捨ての確かめの台本から Node の型の取り除き（--experimental-strip-types）でも読めるよう、Node の部品と
// e2e/support/secretValues.ts（拡張子つき）だけを読み込む。
import { existsSync, readdirSync, readFileSync, rmSync, statSync } from 'node:fs'
import path from 'node:path'
import { inflateRawSync } from 'node:zlib'
import type { FullResult, Reporter } from '@playwright/test/reporter'
import {
  readSecretValues,
  RUN_TAG_MIN_SEARCH_LENGTH,
  SECRET_FIXED_VALUES,
  SECRET_VALUE_SHAPES,
  type SecretEntry,
} from './e2e/support/secretValues.ts'

/** 値を確かめる環境変数と、その種類の名前 */
export interface SecretEnvName {
  name: string
  kind: string
}

/** 報告の部品の設定（パスは frontend/ からの相対パスか絶対パス） */
export interface SecretCheckOptions {
  /** json の報告のファイル */
  outputFile: string
  /** 値を確かめる環境変数 */
  envNames: readonly SecretEnvName[]
  /** 探す結果のディレクトリ（test-results/） */
  resultsDir: string
  /** 値のファイル */
  secretValuesFile: string
  /** 前の実行の html の報告のディレクトリ（もう作らない） */
  previousHtmlReportDir: string
  /** この実行の trace の設定（off でなければ知らせる） */
  traceMode: string
}

/** 探したファイルの種類 */
type FileCategory =
  'jsonReport' | 'jsonAttachment' | 'trace' | 'errorContext' | 'other' | 'previousHtmlReport'

const CATEGORY_NAMES: Readonly<Record<FileCategory, string>> = {
  jsonReport: 'json の報告',
  jsonAttachment: 'json の報告の添付',
  trace: 'trace',
  errorContext: '失敗の画面の写し',
  other: 'そのほか',
  previousHtmlReport: '前の html の報告',
}

const CATEGORIES = Object.keys(CATEGORY_NAMES) as FileCategory[]

/** 探す値（そのままの形を広げたバイト列） */
interface Needle {
  kind: string
  bytes: Buffer
}

/** zip の中を展開する深さの上限（zip の中の zip） */
const MAX_ZIP_DEPTH = 3

/** 値の形を広げる（そのまま・URL の形・JSON の形・\u の形）。 */
function expandForms(value: string): string[] {
  const forms = new Set<string>([value])
  const url = encodeURIComponent(value)
  forms.add(url)
  forms.add(url.replace(/%[0-9A-F]{2}/g, (part) => part.toLowerCase()))
  forms.add(JSON.stringify(value).slice(1, -1))
  const unicodeEscape = (upper: boolean) =>
    value.replace(/[^\x20-\x7e]/g, (char) => {
      const hex = char.charCodeAt(0).toString(16).padStart(4, '0')
      return `\\u${upper ? hex.toUpperCase() : hex}`
    })
  forms.add(unicodeEscape(false))
  forms.add(unicodeEscape(true))
  return [...forms].filter((form) => form !== '')
}

/** 厳しい base64 の復号（形が違えば誤り）。 */
function decodeBase64Strict(text: string): Buffer {
  const cleaned = text.replace(/\s+/g, '')
  if (cleaned.length % 4 !== 0 || !/^[A-Za-z0-9+/]*={0,2}$/.test(cleaned)) {
    throw new Error('base64 を復号できません')
  }
  return Buffer.from(cleaned, 'base64')
}

/** zip の先頭の印か */
function looksLikeZip(bytes: Buffer): boolean {
  return bytes.length >= 4 && bytes.readUInt32LE(0) === 0x04034b50
}

/** zip の中央の目録を読み、各ファイルを展開する（stored と deflate だけ。ほかは誤り）。 */
function unzip(bytes: Buffer): { name: string; content: Buffer }[] {
  const lowest = Math.max(0, bytes.length - 65_557)
  let end = -1
  for (let index = bytes.length - 22; index >= lowest; index -= 1) {
    if (bytes.readUInt32LE(index) === 0x06054b50) {
      end = index
      break
    }
  }
  if (end < 0) {
    throw new Error('zip の目録が見つかりません')
  }
  const count = bytes.readUInt16LE(end + 10)
  let offset = bytes.readUInt32LE(end + 16)
  if (count === 0xffff || offset === 0xffffffff) {
    throw new Error('zip64 には対応していません')
  }
  const entries: { name: string; content: Buffer }[] = []
  for (let entry = 0; entry < count; entry += 1) {
    if (offset + 46 > bytes.length || bytes.readUInt32LE(offset) !== 0x02014b50) {
      throw new Error('zip の目録が壊れています')
    }
    const method = bytes.readUInt16LE(offset + 10)
    const compressedSize = bytes.readUInt32LE(offset + 20)
    const nameLength = bytes.readUInt16LE(offset + 28)
    const extraLength = bytes.readUInt16LE(offset + 30)
    const commentLength = bytes.readUInt16LE(offset + 32)
    const localOffset = bytes.readUInt32LE(offset + 42)
    const name = bytes.toString('utf8', offset + 46, offset + 46 + nameLength)
    if (compressedSize === 0xffffffff || localOffset === 0xffffffff) {
      throw new Error('zip64 には対応していません')
    }
    if (localOffset + 30 > bytes.length || bytes.readUInt32LE(localOffset) !== 0x04034b50) {
      throw new Error('zip の項目の見出しが壊れています')
    }
    const start =
      localOffset + 30 + bytes.readUInt16LE(localOffset + 26) + bytes.readUInt16LE(localOffset + 28)
    const data = bytes.subarray(start, start + compressedSize)
    if (start + compressedSize > bytes.length) {
      throw new Error('zip の項目が途中で切れています')
    }
    if (!name.endsWith('/')) {
      if (method === 0) {
        entries.push({ name, content: Buffer.from(data) })
      } else if (method === 8) {
        entries.push({ name, content: inflateRawSync(data) })
      } else {
        throw new Error('知らない圧縮の方式です')
      }
    }
    offset += 46 + nameLength + extraLength + commentLength
  }
  return entries
}

/** ディレクトリの下のすべてのファイル（再帰） */
function listFiles(dir: string): string[] {
  if (!existsSync(dir)) {
    return []
  }
  const files: string[] = []
  for (const name of readdirSync(dir)) {
    const full = path.join(dir, name)
    if (statSync(full).isDirectory()) {
      files.push(...listFiles(full))
    } else {
      files.push(full)
    }
  }
  return files
}

/** 報告に値が残っていないことを確かめる部品 */
export default class SecretCheckReporter implements Reporter {
  private readonly options: SecretCheckOptions
  private needles: Needle[] = []
  private readonly scanned = new Map<FileCategory, number>()
  private readonly found = new Map<string, number>()
  private readonly unreadable = new Map<FileCategory, number>()

  constructor(options: SecretCheckOptions) {
    this.options = options
  }

  onBegin(): void {
    if (this.options.traceMode !== 'off') {
      console.warn(
        `trace を有効にした実行です（${this.options.traceMode}）。この実行は合否に使わず、見た後に frontend/test-results/ を消してください。`,
      )
    }
  }

  async onEnd(): Promise<{ status: FullResult['status'] } | undefined> {
    const { outputFile, resultsDir, secretValuesFile, previousHtmlReportDir } = this.options
    const warnings: string[] = []
    try {
      let entries: SecretEntry[] = []
      try {
        entries = readSecretValues(secretValuesFile)
      } catch {
        console.error('値のファイルを読めません（値は表示しません）。')
        this.countUnreadable('other')
      }
      const shortRunTags = this.collectNeedles(entries)
      if (shortRunTags > 0) {
        warnings.push(
          `値のファイルの runTag が ${RUN_TAG_MIN_SEARCH_LENGTH} 文字より短いため、単独では探さず宛先の形の中だけで探しました（${shortRunTags} 件）。`,
        )
      }

      if (!existsSync(outputFile)) {
        console.error(`E2E の json の報告（${outputFile}）が見つからず、値の確かめができません。`)
        return { status: 'failed' }
      }
      this.scanJsonReport(readFileSync(outputFile))

      const skip = new Set([path.resolve(outputFile), path.resolve(secretValuesFile)])
      for (const file of listFiles(resultsDir)) {
        if (skip.has(path.resolve(file))) {
          continue
        }
        const base = path.basename(file)
        const category: FileCategory =
          base === 'trace.zip' || base.endsWith('.trace.zip')
            ? 'trace'
            : base === 'error-context.md'
              ? 'errorContext'
              : 'other'
        this.scanFile(file, category)
      }

      const previousFiles = listFiles(previousHtmlReportDir)
      const foundBeforeHtml = this.foundIn('previousHtmlReport')
      for (const file of previousFiles) {
        this.scanPreviousHtmlFile(file)
      }
      if (previousFiles.length > 0) {
        if (this.foundIn('previousHtmlReport') > foundBeforeHtml) {
          console.error(
            `前の実行の html の報告（${previousHtmlReportDir}）に値が残っています。中を開かずに frontend/playwright-report/ を消してください。`,
          )
        } else {
          warnings.push(
            `前の実行の html の報告（${previousHtmlReportDir}）が残っています。もう作らない報告のため、frontend/playwright-report/ を消してください。`,
          )
        }
      }

      for (const warning of warnings) {
        console.warn(warning)
      }
      return this.report()
    } finally {
      rmSync(secretValuesFile, { force: true })
    }
  }

  /** 探す値を集める。短い runTag の件数を返す。 */
  private collectNeedles(entries: readonly SecretEntry[]): number {
    const needles: Needle[] = []
    const add = (kind: string, value: string) => {
      for (const form of expandForms(value)) {
        needles.push({ kind, bytes: Buffer.from(form, 'utf8') })
      }
    }
    for (const { name, kind } of this.options.envNames) {
      const value = process.env[name]
      if (value !== undefined && value !== '') {
        add(kind, value)
      }
    }
    let shortRunTags = 0
    for (const entry of [...SECRET_FIXED_VALUES, ...entries]) {
      if (entry.kind === 'runTag' && entry.value.length < RUN_TAG_MIN_SEARCH_LENGTH) {
        shortRunTags += 1
        add('runTag', `u7-perf-${entry.value}@`)
        continue
      }
      add(entry.kind, entry.value)
    }
    this.needles = needles
    return shortRunTags
  }

  /** 値の種類の数（環境変数・値のファイル・値の形・決まった値） */
  private kindCount(): number {
    const kinds = new Set<string>(this.needles.map((needle) => needle.kind))
    for (const shape of SECRET_VALUE_SHAPES) {
      kinds.add(shape.kind)
    }
    return kinds.size
  }

  /** バイト列を探す（zip なら展開して中も探す）。 */
  private scanBytes(bytes: Buffer, category: FileCategory, zipName: boolean, depth = 0): void {
    const text = bytes.toString('latin1')
    const hits = new Set<string>()
    for (const needle of this.needles) {
      if (bytes.includes(needle.bytes)) {
        hits.add(needle.kind)
      }
    }
    for (const shape of SECRET_VALUE_SHAPES) {
      if (shape.pattern.test(text)) {
        hits.add(shape.kind)
      }
    }
    for (const kind of hits) {
      const key = `${kind}\u0000${category}`
      this.found.set(key, (this.found.get(key) ?? 0) + 1)
    }
    if ((zipName || looksLikeZip(bytes)) && depth < MAX_ZIP_DEPTH) {
      for (const entry of unzip(bytes)) {
        this.scanBytes(entry.content, category, entry.name.endsWith('.zip'), depth + 1)
      }
    }
  }

  /** 1つのファイルを探す。読めない・展開できなければ数える。 */
  private scanFile(file: string, category: FileCategory): void {
    this.count(this.scanned, category)
    try {
      this.scanBytes(readFileSync(file), category, file.endsWith('.zip'))
    } catch {
      this.countUnreadable(category)
    }
  }

  /** json の報告をそのまま探し、JSON として読んで添付と標準出力の base64 を復号して探す。 */
  private scanJsonReport(bytes: Buffer): void {
    this.count(this.scanned, 'jsonReport')
    try {
      this.scanBytes(bytes, 'jsonReport', false)
    } catch {
      this.countUnreadable('jsonReport')
    }
    let report: unknown
    try {
      report = JSON.parse(bytes.toString('utf8'))
    } catch {
      this.countUnreadable('jsonReport')
      return
    }
    const suites = (report as { suites?: unknown } | null)?.suites
    if (!Array.isArray(suites)) {
      this.countUnreadable('jsonReport')
      return
    }
    for (const encoded of this.collectEncoded(suites)) {
      this.count(this.scanned, 'jsonAttachment')
      try {
        const decoded = decodeBase64Strict(encoded)
        this.scanBytes(decoded, 'jsonAttachment', false)
      } catch {
        this.countUnreadable('jsonAttachment')
      }
    }
  }

  /** すべての試験の結果の attachments[].body と stdout・stderr の buffer を集める。 */
  private collectEncoded(suites: readonly unknown[]): string[] {
    const encoded: string[] = []
    const asArray = (value: unknown): unknown[] => (Array.isArray(value) ? value : [])
    const record = (value: unknown): Record<string, unknown> =>
      typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {}
    const visitSuite = (suite: unknown) => {
      const node = record(suite)
      for (const spec of asArray(node.specs)) {
        for (const testEntry of asArray(record(spec).tests)) {
          for (const result of asArray(record(testEntry).results)) {
            const resultNode = record(result)
            for (const attachment of asArray(resultNode.attachments)) {
              const body = record(attachment).body
              if (body !== undefined) {
                encoded.push(typeof body === 'string' ? body : '\u0000')
              }
            }
            for (const stream of [resultNode.stdout, resultNode.stderr]) {
              for (const chunk of asArray(stream)) {
                const buffer = record(chunk).buffer
                if (buffer !== undefined) {
                  encoded.push(typeof buffer === 'string' ? buffer : '\u0000')
                }
              }
            }
          }
        }
      }
      for (const child of asArray(node.suites)) {
        visitSuite(child)
      }
    }
    for (const suite of suites) {
      visitSuite(suite)
    }
    return encoded
  }

  /** 前の html の報告の1ファイルを探す（index.html は埋め込みの base64 の zip を取り出して探す）。 */
  private scanPreviousHtmlFile(file: string): void {
    this.count(this.scanned, 'previousHtmlReport')
    try {
      const bytes = readFileSync(file)
      this.scanBytes(bytes, 'previousHtmlReport', file.endsWith('.zip'))
      if (path.basename(file) === 'index.html') {
        const text = bytes.toString('utf8')
        const embedded = [...text.matchAll(/data:application\/zip;base64,([A-Za-z0-9+/=]+)/g)]
        if (text.includes('playwrightReportBase64') && embedded.length === 0) {
          throw new Error('埋め込みの報告を取り出せません')
        }
        for (const match of embedded) {
          this.scanBytes(decodeBase64Strict(match[1] ?? ''), 'previousHtmlReport', true)
        }
      }
    } catch {
      this.countUnreadable('previousHtmlReport')
    }
  }

  private count(map: Map<FileCategory, number>, category: FileCategory): void {
    map.set(category, (map.get(category) ?? 0) + 1)
  }

  private countUnreadable(category: FileCategory): void {
    this.count(this.unreadable, category)
  }

  private foundIn(category: FileCategory): number {
    let total = 0
    for (const [key, value] of this.found) {
      if (key.endsWith(`\u0000${category}`)) {
        total += value
      }
    }
    return total
  }

  /** 結果を出す（値は出さず、種類と件数だけ）。 */
  private report(): { status: FullResult['status'] } | undefined {
    const scannedText = CATEGORIES.map(
      (category) => `${CATEGORY_NAMES[category]} ${this.scanned.get(category) ?? 0}`,
    ).join('・')
    let failed = false
    if (this.unreadable.size > 0) {
      failed = true
      const text = CATEGORIES.filter((category) => this.unreadable.has(category))
        .map((category) => `${CATEGORY_NAMES[category]} ${this.unreadable.get(category)}`)
        .join('・')
      console.error(
        `読めない・展開できない・復号できないものがあり、値の確かめができません: ${text}`,
      )
    }
    if (this.found.size > 0) {
      failed = true
      const text = [...this.found.entries()]
        .map(([key, value]) => {
          const [kind, category] = key.split('\u0000') as [string, FileCategory]
          return `${kind}（${CATEGORY_NAMES[category]}）${value}`
        })
        .join('・')
      console.error(`E2E の報告に残してはならない値が含まれています（値は表示しません）: ${text}`)
    }
    if (failed) {
      console.error(
        `確かめた値の種類 ${this.kindCount()}・確かめたファイル: ${scannedText}。失敗の画面の写し（error-context.md）で見つかったときは、写しを読んで原因を確かめた後に frontend/test-results/ を消してください。`,
      )
      return { status: 'failed' }
    }
    console.log(
      `E2E の報告に残してはならない値は含まれていません（値の種類 ${this.kindCount()}・確かめたファイル: ${scannedText}・見つかった件数 0）。`,
    )
    return undefined
  }
}
