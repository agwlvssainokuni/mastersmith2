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
// CI の実行の要約（$GITHUB_STEP_SUMMARY）に、規模 → テストの件数 → カバレッジの順で報告を書く台本
// （Intent 261108-ci-size-coverage）。$GITHUB_STEP_SUMMARY が無ければ標準出力に書く。
//
// 流し方（リポジトリのルートで）:
//   node scripts/ci-summary.mjs [--base <commit>] [--backend-reports <dir>] [--frontend-coverage <dir>]
//   - --base: 規模の表の比べる元（CI は develop へのプッシュのときだけ github.event.before を渡す）。空なら差を「—」にする。
//   - --backend-reports: バックエンドのビルドの置き場（既定 backend/build）。test-results/test・test-results/integrationTest
//     の JUnit の XML と、reports/jacoco/test/jacocoTestReport.xml を読む。
//   - --frontend-coverage: 画面のカバレッジの置き場（既定 frontend/coverage）。coverage-summary.json を読む。
//
// 決まり:
// - どの欄が作れなくても（報告のファイルが無い・cloc が無いなど）、欄に理由を書いて警告（::warning::）を出し、
//   終了コードは 0 にする（要約の成否で CI を失敗にしない）。
// - 載せるのはパス（分類・パッケージ・領域の名前）・行数・件数・割合だけ。報告のファイルの中のほかの値（ホストの名前・
//   ファイルの絶対パスなど）は載せない。
// - JaCoCo の XML は正規表現で読む（XML の部品を使わないため、DOCTYPE が指す外部の DTD・実体を読まない）。
// - 割合は coverage-summary.json と同じく、小数 2 桁で切り捨てる（下限の近くで切り上げて「満たす」に見せないため）。
// - バックエンドの下限（行・分岐）は backend/build.gradle.kts から読む。パッケージごとの下限はすべてのパッケージに当たる
//   （外すパッケージの一覧 packagesJudgedByTotal は Intent 261108-ci-checks-release で消した）。
import { appendFileSync, existsSync, readdirSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { collectMeta, countBase, countSize, fixed, int, renderReport } from './program-size.mjs'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const NO_REPORT = '報告なし（./gradlew verify が途中で落ちた見込み）'
const PACKAGE_PREFIX = 'cherry.mastersmith.'

/** 出した警告（最後にまとめて数える）。 */
const warnings = []

/** GitHub Actions の警告を出す（ワークフローのコマンドの値の決まりに合わせて %・改行を逃がす）。 */
function warn(message) {
  warnings.push(message)
  const escaped = message.replaceAll('%', '%25').replaceAll('\r', '%0D').replaceAll('\n', '%0A')
  process.stdout.write(`::warning title=CI の要約::${escaped}\n`)
}

/** 割合（百分率）を小数 2 桁で切り捨てて返す。分母が 0 なら null。 */
export function pct(covered, total) {
  if (!total) return null
  return Math.floor((covered * 10000) / total) / 100
}

/** 百分率を「95.49%」の形で書く。null は「—」。 */
function pctText(p) {
  return p === null ? '—' : `${p.toFixed(2)}%`
}

/** 下限との差（ポイント、符号つき）を書く。 */
function gapText(p, floor) {
  if (p === null || floor === null) return '—'
  const d = Math.round((p - floor) * 100) / 100
  return `${d > 0 ? '+' : ''}${fixed(d, 2)}`
}

// ---- テストの件数（JUnit の XML） ----

/** 1つの置き場の TEST-*.xml の testsuite の属性を足し合わせる。置き場か XML が無ければ null。 */
export function readJUnitDir(dir) {
  if (!existsSync(dir)) return null
  const files = readdirSync(dir).filter((f) => /^TEST-.*\.xml$/.test(f))
  if (files.length === 0) return null
  const sum = { suites: 0, tests: 0, failures: 0, errors: 0, skipped: 0 }
  for (const f of files) {
    const xml = readFileSync(path.join(dir, f), 'utf8')
    const m = /<testsuite\b([^>]*)>/.exec(xml)
    if (!m) throw new Error(`JUnit の XML に testsuite が無い（${f}）`)
    const attrs = Object.fromEntries(
      [...m[1].matchAll(/(\w+)="([^"]*)"/g)].map((a) => [a[1], a[2]]),
    )
    sum.suites += 1
    for (const k of ['tests', 'failures', 'errors', 'skipped']) {
      const n = Number.parseInt(attrs[k] ?? '0', 10)
      if (!Number.isFinite(n)) throw new Error(`JUnit の XML の ${k} が数でない（${f}）`)
      sum[k] += n
    }
  }
  return sum
}

function renderTests(backendDir) {
  const out = ['## テストの件数（バックエンド）', '']
  const kinds = [
    ['単体テスト（test）', path.join(backendDir, 'test-results', 'test')],
    ['結合テスト（integrationTest）', path.join(backendDir, 'test-results', 'integrationTest')],
  ]
  const rows = []
  const total = { suites: 0, tests: 0, failures: 0, errors: 0, skipped: 0 }
  let any = false
  for (const [label, dir] of kinds) {
    let s
    try {
      s = readJUnitDir(dir)
    } catch (e) {
      warn(`テストの件数（${label}）を読めない: ${e.message}`)
      rows.push(`| ${label} | 読めない（${e.message}） | | | | |`)
      continue
    }
    if (!s) {
      warn(`テストの件数（${label}）: ${NO_REPORT}`)
      rows.push(`| ${label} | ${NO_REPORT} | | | | |`)
      continue
    }
    any = true
    for (const k of Object.keys(total)) total[k] += s[k]
    rows.push(testRow(label, s))
  }
  out.push(
    '| 種類 | テスト | 通った | 失敗 | 飛ばした | クラス |',
    '|---|---:|---:|---:|---:|---:|',
    ...rows,
  )
  if (any) out.push(testRow('**合計**', total))
  out.push(
    '',
    '- 出どころ: `backend/build/test-results/{test,integrationTest}/TEST-*.xml`。失敗は failures と errors の和。画面（Vitest）の件数は載せない。',
    '',
  )
  return out.join('\n')
}

function testRow(label, s) {
  const failed = s.failures + s.errors
  const passed = s.tests - failed - s.skipped
  return `| ${label} | ${int(s.tests)} | ${int(passed)} | ${int(failed)} | ${int(s.skipped)} | ${int(s.suites)} |`
}

// ---- バックエンドのカバレッジ（JaCoCo の XML） ----

/** 要素の中の counter を { 種類: { missed, covered } } にする。 */
function counters(text) {
  const out = {}
  for (const m of text.matchAll(
    /<counter\s+type="(\w+)"\s+missed="(\d+)"\s+covered="(\d+)"\s*\/>/g,
  )) {
    out[m[1]] = { missed: Number(m[2]), covered: Number(m[3]) }
  }
  return out
}

/** JaCoCo の XML から、全体（根の直下）とパッケージごと（package の直下）の counter を読む。 */
export function parseJacoco(xml) {
  if (!/<report\b/.test(xml)) throw new Error('JaCoCo の XML に report が無い')
  const packages = []
  const packageBlock = /<package\s+name="([^"]*)"\s*(?:\/>|>([\s\S]*?)<\/package>)/g
  for (const m of xml.matchAll(packageBlock)) {
    // クラスとソースファイルの中の counter を除き、package の直下の counter だけを残す。
    const own = (m[2] ?? '')
      .replace(/<class\b[^>]*?(?:\/>|>[\s\S]*?<\/class>)/g, '')
      .replace(/<sourcefile\b[^>]*?(?:\/>|>[\s\S]*?<\/sourcefile>)/g, '')
    packages.push({ name: m[1].replaceAll('/', '.'), counters: counters(own) })
  }
  const total = counters(xml.replace(packageBlock, ''))
  return { total, packages }
}

/** backend/build.gradle.kts から、全体とパッケージごとの下限（百分率）を読む。 */
export function parseGradleFloors(text) {
  const start = text.indexOf('tasks.jacocoTestCoverageVerification')
  if (start < 0) throw new Error('jacocoTestCoverageVerification の設定が見つからない')
  const block = text.slice(start)
  const split = block.indexOf('element = "PACKAGE"')
  const limits = (part) => {
    const out = {}
    for (const m of part.matchAll(
      /counter\s*=\s*"(LINE|BRANCH)"[\s\S]*?minimum\s*=\s*"([\d.]+)"/g,
    )) {
      if (!(m[1] in out)) out[m[1]] = Math.round(Number(m[2]) * 10000) / 100
    }
    return out
  }
  const total = limits(split < 0 ? block : block.slice(0, split))
  const pkg = split < 0 ? {} : limits(block.slice(split))
  if (total.LINE === undefined || total.BRANCH === undefined)
    throw new Error('全体の下限（LINE・BRANCH）が読めない')
  if (pkg.LINE === undefined || pkg.BRANCH === undefined)
    throw new Error('パッケージごとの下限（LINE・BRANCH）が読めない')
  return { total, pkg }
}

function shortPackage(name) {
  return name.startsWith(PACKAGE_PREFIX) ? name.slice(PACKAGE_PREFIX.length) : name
}

function renderBackendCoverage(backendDir) {
  const out = ['### バックエンド（JaCoCo）', '']
  const xmlPath = path.join(backendDir, 'reports', 'jacoco', 'test', 'jacocoTestReport.xml')
  if (!existsSync(xmlPath)) {
    warn(`バックエンドのカバレッジ: ${NO_REPORT}`)
    out.push(NO_REPORT, '')
    return out.join('\n')
  }
  let report
  try {
    report = parseJacoco(readFileSync(xmlPath, 'utf8'))
  } catch (e) {
    warn(`バックエンドのカバレッジを読めない: ${e.message}`)
    out.push(`読めない（${e.message}）`, '')
    return out.join('\n')
  }
  let floors = null
  try {
    floors = parseGradleFloors(
      readFileSync(path.join(repoRoot, 'backend', 'build.gradle.kts'), 'utf8'),
    )
  } catch (e) {
    warn(`カバレッジの下限を backend/build.gradle.kts から読めない: ${e.message}`)
    out.push(`- 下限を読めないため、下限との差は「—」にする（${e.message}）。`, '')
  }

  const line = report.total.LINE ?? { missed: 0, covered: 0 }
  const branch = report.total.BRANCH ?? { missed: 0, covered: 0 }
  const lineP = pct(line.covered, line.missed + line.covered)
  const branchP = pct(branch.covered, branch.missed + branch.covered)
  out.push(
    '| 項目 | 割合 | 通った / 全体 | 下限 | 下限との差（ポイント） |',
    '|---|---:|---:|---:|---:|',
    `| 行 | ${pctText(lineP)} | ${int(line.covered)} / ${int(line.missed + line.covered)} | ${floors ? `${fixed(floors.total.LINE, 0)}%` : '—'} | ${gapText(lineP, floors?.total.LINE ?? null)} |`,
    `| 分岐 | ${pctText(branchP)} | ${int(branch.covered)} / ${int(branch.missed + branch.covered)} | ${floors ? `${fixed(floors.total.BRANCH, 0)}%` : '—'} | ${gapText(branchP, floors?.total.BRANCH ?? null)} |`,
    '',
  )

  // パッケージごと（名前の順）。パッケージごとの下限はすべてのパッケージに当たる。
  const rows = []
  let below = 0
  let near = 0
  for (const p of [...report.packages].sort((a, b) => a.name.localeCompare(b.name))) {
    const l = p.counters.LINE ?? { missed: 0, covered: 0 }
    const b = p.counters.BRANCH ?? null
    const lp = pct(l.covered, l.missed + l.covered)
    const bp = b ? pct(b.covered, b.missed + b.covered) : null
    let mark = '—'
    if (floors) {
      const gaps = [
        lp === null ? null : lp - floors.pkg.LINE,
        bp === null ? null : bp - floors.pkg.BRANCH,
      ].filter((g) => g !== null)
      const min = gaps.length ? Math.min(...gaps) : null
      if (min !== null && min < 0) {
        mark = '下限を下回る'
        below += 1
      } else if (min !== null && min < 5) {
        mark = '下限まで 5 ポイント未満'
        near += 1
      } else {
        mark = '満たす'
      }
    }
    rows.push(
      `| ${shortPackage(p.name)} | ${pctText(lp)} | ${gapText(lp, floors?.pkg.LINE ?? null)} | ${pctText(bp)} | ${gapText(bp, floors?.pkg.BRANCH ?? null)} | ${mark} |`,
    )
  }
  if (floors) {
    out.push(
      `- パッケージ ${int(report.packages.length)} 個。` +
        `パッケージごとの下限（行 ${fixed(floors.pkg.LINE, 0)}%・分岐 ${fixed(floors.pkg.BRANCH, 0)}%）を下回るもの ${below} 個、下限まで 5 ポイント未満のもの ${near} 個。`,
      '',
    )
  }
  out.push(
    '<details><summary>パッケージごとの行・分岐（cherry.mastersmith. を省く）</summary>',
    '',
    '| パッケージ | 行 | 行の下限との差 | 分岐 | 分岐の下限との差 | 印 |',
    '|---|---:|---:|---:|---:|---|',
    ...rows,
    '',
    '</details>',
    '',
    '- 出どころ: `backend/build/reports/jacoco/test/jacocoTestReport.xml`（単体テストと結合テストを合わせた値）。分岐の無いパッケージの分岐は「—」。',
    '',
  )
  return out.join('\n')
}

// ---- 画面のカバレッジ（coverage-summary.json） ----

const METRICS = [
  ['lines', '行'],
  ['branches', '分岐'],
  ['functions', '関数'],
  ['statements', '文'],
]

/** ファイルの絶対パスから領域（features/<機能>・app/<名前>・shared/<名前>・src 直下・その他）を求める。 */
export function frontendArea(file) {
  const p = file.replaceAll('\\', '/')
  const m = /\/frontend\/src\/(features|shared|app)\/([^/]+)\//.exec(p)
  if (m) return `${m[1]}/${m[2]}`
  if (/\/frontend\/src\/[^/]+$/.test(p)) return '(src 直下)'
  return '(src の外)'
}

/** vitest.config.ts の thresholds（lines・branches）を読む。読めなければ null。 */
function readFrontendFloors() {
  try {
    const text = readFileSync(path.join(repoRoot, 'frontend', 'vitest.config.ts'), 'utf8')
    const m = /thresholds\s*:\s*\{([^}]*)\}/.exec(text)
    if (!m) return null
    const get = (k) => {
      const v = new RegExp(`\\b${k}\\s*:\\s*([\\d.]+)`).exec(m[1])
      return v ? Number(v[1]) : null
    }
    return { lines: get('lines'), branches: get('branches') }
  } catch {
    return null
  }
}

function renderFrontendCoverage(coverageDir) {
  const out = ['### 画面（Vitest）', '']
  const jsonPath = path.join(coverageDir, 'coverage-summary.json')
  if (!existsSync(jsonPath)) {
    warn(`画面のカバレッジ: ${NO_REPORT}`)
    out.push(NO_REPORT, '')
    return out.join('\n')
  }
  let summary
  try {
    summary = JSON.parse(readFileSync(jsonPath, 'utf8'))
    if (!summary.total) throw new Error('total が無い')
  } catch (e) {
    warn(`画面のカバレッジを読めない: ${e.message}`)
    out.push(`読めない（${e.message}）`, '')
    return out.join('\n')
  }
  const floors = readFrontendFloors()
  out.push(
    '| 項目 | 割合 | 通った / 全体 | 下限 | 下限との差（ポイント） |',
    '|---|---:|---:|---:|---:|',
  )
  for (const [k, label] of METRICS) {
    const t = summary.total[k] ?? { total: 0, covered: 0, pct: null }
    const p = typeof t.pct === 'number' && t.total > 0 ? t.pct : null
    const floor = floors?.[k] ?? null
    out.push(
      `| ${label} | ${pctText(p)} | ${int(t.covered)} / ${int(t.total)} | ${floor === null ? '—' : `${floor}%`} | ${gapText(p, floor)} |`,
    )
  }
  out.push('')

  // 領域ごと（ファイルごとの値を足し合わせる。名前の順）。
  const areas = new Map()
  for (const [file, v] of Object.entries(summary)) {
    if (file === 'total') continue
    const area = frontendArea(file)
    let a = areas.get(area)
    if (!a) {
      a = { files: 0, lines: [0, 0], branches: [0, 0], functions: [0, 0], statements: [0, 0] }
      areas.set(area, a)
    }
    a.files += 1
    for (const [k] of METRICS) {
      a[k][0] += v[k]?.covered ?? 0
      a[k][1] += v[k]?.total ?? 0
    }
  }
  const rows = [...areas.entries()]
    .sort((x, y) => x[0].localeCompare(y[0]))
    .map(
      ([name, a]) =>
        `| ${name} | ${int(a.files)} | ${METRICS.map(([k]) => pctText(pct(a[k][0], a[k][1]))).join(' | ')} |`,
    )
  out.push(
    '<details><summary>領域ごとの行・分岐・関数・文</summary>',
    '',
    '| 領域 | ファイル | 行 | 分岐 | 関数 | 文 |',
    '|---|---:|---:|---:|---:|---:|',
    ...rows,
    '',
    '</details>',
    '',
    '- 出どころ: `frontend/coverage/coverage-summary.json`（全体は total の値、領域はファイルごとの値の和）。下限は全体の合計にだけ当たる（`frontend/vitest.config.ts` の thresholds）。',
    '',
  )
  return out.join('\n')
}

// ---- 規模 ----

function renderSize(base) {
  try {
    const size = countSize(repoRoot)
    const meta = collectMeta(repoRoot)
    const baseResult = countBase(repoRoot, base)
    return renderReport(size, meta, { base: baseResult, headingOffset: 1, fold: true })
  } catch (e) {
    warn(`規模を数えられない: ${e.message}`)
    return ['## プログラムの規模', '', `作れなかった（${e.message}）`, ''].join('\n')
  }
}

// ---- 入口 ----

function parseArgs(argv) {
  const opts = {
    base: '',
    backendReports: path.join(repoRoot, 'backend', 'build'),
    frontendCoverage: path.join(repoRoot, 'frontend', 'coverage'),
  }
  const names = {
    '--base': 'base',
    '--backend-reports': 'backendReports',
    '--frontend-coverage': 'frontendCoverage',
  }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    const eq = a.indexOf('=')
    const flag = eq > 0 ? a.slice(0, eq) : a
    const key = names[flag]
    if (!key) throw new Error(`知らない引数: ${a}`)
    let value
    if (eq > 0) value = a.slice(eq + 1)
    else if (i + 1 < argv.length) value = argv[++i]
    else value = ''
    opts[key] = key === 'base' ? value : path.resolve(value)
  }
  return opts
}

function main() {
  let opts
  try {
    opts = parseArgs(process.argv.slice(2))
  } catch (e) {
    warn(`引数を読めない: ${e.message}`)
    opts = parseArgs([])
  }
  const sections = ['# CI の報告（規模・テストの件数・カバレッジ）', '']
  sections.push(renderSize(opts.base))
  sections.push(renderTests(opts.backendReports))
  sections.push('## カバレッジ', '')
  sections.push(renderBackendCoverage(opts.backendReports))
  sections.push(renderFrontendCoverage(opts.frontendCoverage))
  if (warnings.length)
    sections.push(
      `> 作れなかった欄が ${warnings.length} 個ある（各欄の理由と、この手順の警告を見る）。`,
      '',
    )
  const text = sections.join('\n')
  const target = process.env.GITHUB_STEP_SUMMARY
  if (target) appendFileSync(target, text)
  else process.stdout.write(text)
}

try {
  main()
} catch (e) {
  // 想定外の失敗も CI を失敗にしない（理由を警告に出す）。
  warn(`要約を書けない: ${e?.message ?? e}`)
}
process.exitCode = 0
