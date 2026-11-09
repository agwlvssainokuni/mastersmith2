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
// mastersmith2 のプログラムの規模を、ファイルを分類して cloc で数え、Markdown の報告を標準出力に出す台本
// （Intent 261108-ci-size-coverage）。CI の要約（scripts/ci-summary.mjs）からも関数として呼ぶ。
//
// 流し方（リポジトリのルートで）:
//   node scripts/program-size.mjs                    # 今の作業ツリーを数える
//   node scripts/program-size.mjs --base <commit>    # 大分類と中分類の表に、そのコミットとの差の列を足す
//
// 前提: git と cloc（手元は `brew install cloc`、CI は `apt-get install cloc`）。Node の組み込みだけで動く（npm の依存なし）。
// 数えるのは git が管理するファイルだけ（作業ツリーの未コミットの変更は、管理しているファイルの今の中身として数える。
// 管理していない新しいファイルや .env などの git の対象外のファイルは数えない）。
// 失敗したとき（git・cloc が無いなど）は、理由を標準エラーに出して終了コード 1 で終わる。
//
// ## 対象から外すもの（EXCLUDE）
// - `aidlc/`（AI-DLC の記録）、`vendor/`（サブモジュール: make-you-chic-ui・java-mustache-processor）
// - AI の道具の設定（`.claude/`・`.codex/`・`.kiro/`・`.cursor/`・`.agents/`・`.aidlc/`・`.github/skills/`）と `.idea/`
// - lockfile（`package-lock.json`・`*.lockfile`）: 自動で作られるため
// - cloc が言語として数えないもの（`.env.example`・`.gitignore`・`LICENSE`・バイナリ）は、結果に出ない
//
// ## 分類の決まり（classify。上から順に当て、最初に当たったもので決める）
// 1. 拡張子 `.md` → 文書（置き場によらない。`docs/`・`README.md`・`perf/README.md`・`frontend/README.md` など）
// 2. `backend/src/main/java/` → 製品 / サーバー（Java）。機能は `cherry/mastersmith/<機能>/`、層はその次の階層
//    （`web`・`service`・`domain`・`repository` のどれでもなければ「その他」: `config`・用途名の下位パッケージ・機能の直下）
// 3. `backend/src/main/resources/` → 製品 / サーバーの資源（`db/migration/` は移行、`mail/` はメールのテンプレート、ほかは設定など）
// 4. `backend/src/test/` → テスト / サーバーのテスト。ファイル名が `*IT.java` なら結合テスト、パスに `/testsupport/` があれば
//    テストの手伝い、ほかの `.java` は単体テスト、`.java` 以外はテストの資源。機能は `cherry/mastersmith/<機能>/`
// 5. `frontend/src/` → 次のどれかならテスト / 画面の単体テスト（Vitest）、それ以外は製品 / 画面（TypeScript・CSS）
//    - ファイル名が `*.test.ts`・`*.test.tsx`
//    - パスに `/test/` か `/testing/` を含む（`app/testing/`・`features/<機能>/testing/` などのテストの手伝い）
//    - ファイル名に `setupTests`・`test-utils`・`testUtils` を含む、または `fixtures`・`Fixtures` で終わる（`svgFixtures.ts` など）
//    領域は `features/<機能>`・`app/<名前>`・`shared/<名前>`、どれでもなければ「src 直下」
// 6. `frontend/e2e/` → 次の3つに分ける（どれも大分類はテスト）
//    - 名前が `*.test.ts` → E2E の補助の単体テスト（Vitest で流れる）
//    - 名前が `*.e2e.ts` → E2E の流れ（Playwright で流れる）
//    - それ以外（`support/` の部品・SQL など）→ E2E の補助
//    Playwright の設定（`frontend/playwright.config.ts`）・報告の道具（`frontend/playwright-secret-check-reporter.ts`）・
//    `docker/e2e/` は 9 のビルド・検査・運用に入れ、報告の要点に「E2E に関わる設定」として行数を注記する
// 7. `frontend/public/` と `frontend/index.html` → 製品 / 画面の静的な素材
// 8. `perf/` → テスト / 負荷の試験（k6 の台本・シェル）
// 9. それ以外（ルートの Gradle・`frontend/` の設定と `scripts/`・`eslint-rules/`・`docker/`・`config/`・`.github/workflows/`・
//    `Dockerfile`・`compose.yaml` など）→ ビルド・検査・運用
//
// ## 数える量（cloc の定義）
// - code: 空の行とコメントを除いた行。comment: コメントの行。blank: 空の行。
// - Java の製品のコメントには、全ファイルの先頭の Apache License のヘッダー（約 13 行）が入る。報告ではその見込みを別に出す。
//
// ## 前の回との差（--base）
// - 比べる元のコミットを `git worktree add --detach` で `build/program-size-base` に取り出し、同じ決まりで数えて、
//   大分類と中分類の表に「コードの差」「ファイルの差」（符号つき）の列を足す。取り出した作業ツリーは、失敗しても終わったら消す。
// - 比べる元が空・0 が並ぶ値・取り出せないときは、差の列を「—」にし、理由を1行出す。
import { execFileSync } from 'node:child_process'
import { existsSync, realpathSync, rmSync } from 'node:fs'
import path from 'node:path'
import { pathToFileURL } from 'node:url'

const EXCLUDE =
  /(package-lock\.json|\.lockfile)$|^(aidlc|vendor|\.claude|\.idea|\.codex|\.kiro|\.cursor|\.agents|\.aidlc)\/|^\.github\/skills\//
const LAYERS = ['web', 'service', 'domain', 'repository']
const E2E_RELATED = new Set([
  'frontend/playwright.config.ts',
  'frontend/playwright-secret-check-reporter.ts',
])
const LICENSE_HEADER_LINES = 13
const ZERO_SHA = /^0+$/
/** 比べる元に受け付ける値（コミットのハッシュ・ブランチの名前・HEAD~1 など）。 */
const SAFE_REF = /^[0-9A-Za-z._/~^@-]{1,200}$/
/** 比べる元を取り出す置き場（リポジトリのルートからの相対。build/ は git の対象外）。 */
const BASE_WORKTREE = path.join('build', 'program-size-base')
/** 集計の鍵の区切り（分類の名前に現れない文字）。 */
const SEP = '\u001f'
/** 子のプロセスの出力の上限（cloc の JSON は数百 KB。余裕を持たせる）。 */
const MAX_BUFFER = 256 * 1024 * 1024

/** git を root で流し、標準出力を返す。失敗したら理由を付けて投げる。 */
function git(root, args, { trim = true } = {}) {
  try {
    const out = execFileSync('git', ['-C', root, ...args], {
      encoding: 'utf8',
      maxBuffer: MAX_BUFFER,
      stdio: ['ignore', 'pipe', 'pipe'],
    })
    return trim ? out.trim() : out
  } catch (e) {
    if (e.code === 'ENOENT') throw new Error('git が見つからない')
    const detail = (e.stderr ?? '').toString().trim().split('\n')[0]
    throw new Error(`git ${args[0]} が失敗した${detail ? `（${detail}）` : ''}`)
  }
}

/** git を流し、失敗したら null を返す（無くてもよい値を読むとき）。 */
function gitOrNull(root, args) {
  try {
    return git(root, args) || null
  } catch {
    return null
  }
}

/** ファイルの (大分類, 中分類, 小分類, 細分類) を返す。 */
export function classify(f) {
  if (f.endsWith('.md')) return ['文書', '文書', '-', '-']
  if (f.startsWith('backend/src/main/java/')) {
    const m = /^backend\/src\/main\/java\/cherry\/mastersmith\/([^/]+)(?:\/([^/]+))?\//.exec(f)
    const feat = m ? m[1] : '(root)'
    const layer = m && LAYERS.includes(m[2]) ? m[2] : 'その他'
    return ['製品', 'サーバー（Java）', feat, layer]
  }
  if (f.startsWith('backend/src/main/resources/')) {
    const sub = f.includes('/db/migration/')
      ? '移行（SQL）'
      : f.includes('/mail/')
        ? 'メールのテンプレート'
        : '設定・DSL の定義など'
    return ['製品', 'サーバーの資源', sub, '-']
  }
  if (f.startsWith('backend/src/test/')) {
    const name = f.slice(f.lastIndexOf('/') + 1)
    let kind
    if (name.endsWith('IT.java')) kind = '結合テスト（*IT）'
    else if (f.includes('/testsupport/')) kind = 'テストの手伝い（testsupport）'
    else if (name.endsWith('.java')) kind = '単体テスト（*Test など）'
    else kind = 'テストの資源'
    const m = /^backend\/src\/test\/java\/cherry\/mastersmith\/([^/]+)\//.exec(f)
    return ['テスト', 'サーバーのテスト', kind, m ? m[1] : '-']
  }
  if (f.startsWith('frontend/src/')) {
    const name = f.slice(f.lastIndexOf('/') + 1)
    const isTest =
      /\.test\.(ts|tsx)$/.test(name) ||
      f.includes('/test/') ||
      f.includes('/testing/') ||
      /setupTests|test-utils|testUtils|[fF]ixtures\.(ts|tsx)$/.test(name)
    const m = /^frontend\/src\/(features|shared|app)\/([^/]+)\//.exec(f)
    const area = m ? `${m[1]}/${m[2]}` : '(src 直下)'
    return isTest
      ? ['テスト', '画面の単体テスト（Vitest）', area, '-']
      : ['製品', '画面（TypeScript・CSS）', area, '-']
  }
  if (f.startsWith('frontend/e2e/')) {
    const name = f.slice(f.lastIndexOf('/') + 1)
    if (name.endsWith('.test.ts')) return ['テスト', 'E2E の補助の単体テスト（Vitest）', '-', '-']
    if (name.endsWith('.e2e.ts')) return ['テスト', 'E2E の流れ（Playwright）', '-', '-']
    return ['テスト', 'E2E の補助', '-', '-']
  }
  if (f.startsWith('frontend/public/') || f === 'frontend/index.html') {
    return ['製品', '画面の静的な素材', '-', '-']
  }
  if (f.startsWith('perf/')) return ['テスト', '負荷の試験（k6 など）', '-', '-']
  if (E2E_RELATED.has(f) || f.startsWith('docker/e2e/')) {
    return ['ビルド・検査・運用', 'ビルド・検査・運用', 'E2E に関わる設定', '-']
  }
  return ['ビルド・検査・運用', 'ビルド・検査・運用', f.split('/')[0], '-']
}

/** cloc の版を返す。無ければ投げる。 */
export function clocVersion() {
  try {
    return execFileSync('cloc', ['--version'], {
      encoding: 'utf8',
      stdio: ['ignore', 'pipe', 'pipe'],
    }).trim()
  } catch (e) {
    if (e.code === 'ENOENT')
      throw new Error('cloc が見つからない（手元は brew install cloc、CI は apt-get install cloc）')
    throw new Error('cloc --version が失敗した')
  }
}

/**
 * root（作業ツリーのルート）の git が管理するファイルを分類して cloc で数える。
 * 戻り値の agg は、集計の鍵（種類と分類を SEP でつないだもの）から [ファイル, コード, コメント, 空行] への Map。
 * 鍵の並びは cloc の出力の順に初めて現れた順（表の同じ行数の並びを移す元と同じにするため）。
 */
export function countSize(root) {
  const absRoot = path.resolve(root)
  const files = git(absRoot, ['ls-files', '-z'], { trim: false })
    .split('\0')
    .filter((f) => f && !EXCLUDE.test(f))
  const cats = new Map(files.map((f) => [f, classify(f)]))
  const version = clocVersion()
  let stdout
  try {
    stdout = execFileSync('cloc', ['--by-file', '--json', '--quiet', '--list-file=-'], {
      input: files.map((f) => `${absRoot}/${f}`).join('\n'),
      encoding: 'utf8',
      maxBuffer: MAX_BUFFER,
      stdio: ['pipe', 'pipe', 'pipe'],
    })
  } catch (e) {
    if (e.code === 'ENOENT') throw new Error('cloc が見つからない')
    throw new Error('cloc が失敗した')
  }
  let data
  try {
    data = JSON.parse(stdout)
  } catch {
    throw new Error('cloc の出力を JSON として読めない')
  }

  const agg = new Map()
  const add = (key, row) => {
    const k = key.join(SEP)
    let a = agg.get(k)
    if (!a) {
      a = [0, 0, 0, 0]
      agg.set(k, a)
    }
    a[0] += 1
    a[1] += row.code
    a[2] += row.comment
    a[3] += row.blank
  }
  const prefix = `${absRoot}/`
  for (const [p, v] of Object.entries(data)) {
    if (p === 'header' || p === 'SUM') continue
    const rel = p.startsWith(prefix) ? p.slice(prefix.length) : p
    const c = cats.get(rel)
    if (!c) continue
    const row = { code: v.code, comment: v.comment, blank: v.blank }
    add(['大', c[0]], row)
    add(['中', c[0], c[1]], row)
    add(['言語', c[0], c[1], v.language], row)
    if (c[1] === 'サーバー（Java）') {
      add(['Java製品の機能', c[2]], row)
      add(['Java製品の層', c[3]], row)
    }
    if (c[1] === 'サーバーのテスト') {
      add(['Javaテストの種類', c[2]], row)
      add(['Javaテストの機能', c[3]], row)
    }
    if (c[1] === '画面（TypeScript・CSS）') add(['画面製品の領域', c[2]], row)
    if (c[1] === '画面の単体テスト（Vitest）') add(['画面テストの領域', c[2]], row)
    if (c[2] === 'E2E に関わる設定') add(['E2E設定'], row)
  }
  return { root: absRoot, fileCount: files.length, agg, clocVersion: version }
}

/**
 * 比べる元のコミットを build/program-size-base に取り出して数える。
 * 戻り値は { ref, short, date, size }（数えられたとき）か { ref, reason }（出せないとき）。取り出した作業ツリーは必ず消す。
 */
export function countBase(root, ref) {
  const value = (ref ?? '').trim()
  if (!value)
    return { ref: value, reason: '比べる元が渡されていない（手動の実行・タグのプッシュなど）' }
  if (ZERO_SHA.test(value)) {
    return {
      ref: value,
      reason: '比べる元が 0 の並び（ブランチの最初のプッシュなど）で、前の回が無い',
    }
  }
  // 報告に載せるのはコミットの名前に使う文字だけ（引数の値をそのまま Markdown に入れない）。
  if (!SAFE_REF.test(value) || value.startsWith('-')) {
    return { ref: value, reason: '比べる元の値がコミットの名前の形でない' }
  }
  const absRoot = path.resolve(root)
  let repoRoot
  try {
    repoRoot = git(absRoot, ['rev-parse', '--show-toplevel'])
  } catch (e) {
    return { ref: value, reason: e.message }
  }
  const commit = gitOrNull(repoRoot, ['rev-parse', '--verify', '--quiet', `${value}^{commit}`])
  if (!commit)
    return { ref: value, reason: `比べる元のコミット ${value} を取り出せない（履歴に無い）` }
  const dir = path.join(repoRoot, BASE_WORKTREE)
  removeWorktree(repoRoot, dir)
  try {
    git(repoRoot, ['worktree', 'add', '--detach', dir, commit])
    const size = countSize(dir)
    return {
      ref: value,
      short: git(repoRoot, ['rev-parse', '--short=8', commit]),
      date: git(repoRoot, ['log', '-1', '--format=%ci', commit]),
      size,
    }
  } catch (e) {
    return { ref: value, reason: `比べる元のコミット ${value} を数えられない（${e.message}）` }
  } finally {
    removeWorktree(repoRoot, dir)
  }
}

/** 取り出した作業ツリーを消す。git で消せなければ、フォルダを消して git の記録を片付ける。 */
function removeWorktree(repoRoot, dir) {
  if (existsSync(dir)) {
    try {
      git(repoRoot, ['worktree', 'remove', '--force', dir])
    } catch {
      rmSync(dir, { recursive: true, force: true })
    }
  }
  gitOrNull(repoRoot, ['worktree', 'prune'])
}

/** 今の集計と比べる元の集計の差（鍵から [ファイルの差, コードの差]）を返す。片方にしか無い鍵は 0 として扱う。 */
export function computeDiff(curAgg, baseAgg) {
  const diff = new Map()
  for (const k of new Set([...curAgg.keys(), ...baseAgg.keys()])) {
    const c = curAgg.get(k) ?? [0, 0, 0, 0]
    const b = baseAgg.get(k) ?? [0, 0, 0, 0]
    diff.set(k, [c[0] - b[0], c[1] - b[1]])
  }
  return diff
}

/** 整数を 3 桁ごとのカンマで書く（Python の `{:,}` と同じ）。 */
export function int(n) {
  return String(n).replace(/\B(?=(\d{3})+(?!\d))/g, ',')
}

/** 符号つきの整数（差の列）。 */
function signed(n) {
  return n > 0 ? `+${int(n)}` : int(n)
}

/**
 * 小数を d 桁で書く（Python の `{:.Nf}` と同じ丸め。ちょうど半分のときだけ偶数へ丸める）。
 * JavaScript の toFixed はちょうど半分を大きい方へ丸めるため、そのときだけ直す。
 */
export function fixed(x, d) {
  if (!Number.isFinite(x)) return '—'
  // toFixed の桁は ES2018 から 0〜100 まで使える。oxlint の number-arg-out-of-range は上限を 20 とみる古い決まりで、
  // ここは誤検知のため、この1行だけ外す（桁を減らすと、ちょうど半分かの判定が正しくなくなる）。
  // oxlint-disable-next-line oxc/number-arg-out-of-range
  const exact = Math.abs(x).toFixed(100)
  const dot = exact.indexOf('.')
  const rest = exact.slice(dot + 1 + d)
  if (/^50*$/.test(rest)) {
    const kept = exact.slice(0, dot + 1 + d).replace(/\.$/, '')
    const lastDigit = Number(kept[kept.length - 1])
    if (lastDigit % 2 === 0) return (x < 0 ? '-' : '') + kept
  }
  return x.toFixed(d)
}

/** 日時を「YYYY-MM-DD HH:MM 時間帯」で書く（移す元の `%Y-%m-%d %H:%M %Z` と同じ形）。 */
function formatNow(now) {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('ja-JP', {
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hourCycle: 'h23',
      timeZoneName: 'short',
    })
      .formatToParts(now)
      .map((p) => [p.type, p.value]),
  )
  return `${parts.year}-${parts.month}-${parts.day} ${parts.hour}:${parts.minute} ${parts.timeZoneName}`
}

/** 数えた対象の欄の値（ブランチ・先頭・main の先頭）を読む。 */
export function collectMeta(root) {
  const absRoot = path.resolve(root)
  return {
    now: new Date(),
    branch: process.env.GITHUB_REF_NAME || git(absRoot, ['rev-parse', '--abbrev-ref', 'HEAD']),
    head: git(absRoot, ['rev-parse', '--short=8', 'HEAD']),
    headDate: git(absRoot, ['log', '-1', '--format=%ci', 'HEAD']),
    mainHead: gitOrNull(absRoot, ['rev-parse', '--short=8', '--verify', '--quiet', 'origin/main']),
  }
}

/**
 * 規模の報告（Markdown）を組み立てる。
 * - base: null（差を求めない。列を足さない）・{ size, short, date }（差の列を足す）・{ reason }（差の列を「—」にする）
 * - headingOffset: 見出しの段を下げる数（CI の要約に入れるとき 1）
 * - fold: 大分類・中分類より後の表を <details> で畳む（CI の要約に入れるとき）
 */
export function renderReport(size, meta, { base = null, headingOffset = 0, fold = false } = {}) {
  const { agg } = size
  const h = (n) => '#'.repeat(n + headingOffset)
  const get = (...key) => agg.get(key.join(SEP)) ?? [0, 0, 0, 0]
  const code = (...key) => get(...key)[1]
  const diff = base?.size ? computeDiff(agg, base.size.agg) : null
  const withDiff = base !== null

  const table = (prefix, title, { diffColumns = false } = {}) => {
    const items = []
    for (const [k, v] of agg) {
      const parts = k.split(SEP)
      if (parts[0] === prefix) items.push([parts.slice(1), v, k])
    }
    // 比べる元にだけある分類も、今の値を 0 として載せる（消えた分類の差を見せるため）。
    if (diffColumns && diff) {
      for (const k of base.size.agg.keys()) {
        const parts = k.split(SEP)
        if (parts[0] === prefix && !agg.has(k)) items.push([parts.slice(1), [0, 0, 0, 0], k])
      }
    }
    items.sort((a, b) => b[1][1] - a[1][1])
    const cols = ['ファイル', 'コード', 'コメント', '空行']
    if (diffColumns) cols.push('コードの差', 'ファイルの差')
    const out = [
      `${h(3)} ${title}`,
      '',
      `| 分類 | ${cols.join(' | ')} |`,
      `|---|${'---:|'.repeat(cols.length)}`,
    ]
    for (const [k, v, key] of items) {
      const cells = [int(v[0]), int(v[1]), int(v[2]), int(v[3])]
      if (diffColumns) {
        const d = diff?.get(key)
        cells.push(d ? signed(d[1]) : '—', d ? signed(d[0]) : '—')
      }
      out.push(`| ${k.join(' / ')} | ${cells.join(' | ')} |`)
    }
    return out.join('\n') + '\n'
  }

  const prod = code('大', '製品')
  const test = code('大', 'テスト')
  const javaP = code('中', '製品', 'サーバー（Java）')
  const javaT = code('中', 'テスト', 'サーバーのテスト')
  const feP = code('中', '製品', '画面（TypeScript・CSS）')
  const feT = code('中', 'テスト', '画面の単体テスト（Vitest）')
  const e2eFlow = code('中', 'テスト', 'E2E の流れ（Playwright）')
  const e2eSupport = code('中', 'テスト', 'E2E の補助')
  const e2eUnit = code('中', 'テスト', 'E2E の補助の単体テスト（Vitest）')
  const e2e = e2eFlow + e2eSupport
  const e2eConf = get('E2E設定')
  const javaFiles = get('中', '製品', 'サーバー（Java）')[0]
  const javaComment = get('中', '製品', 'サーバー（Java）')[2]

  // 機能の横並び（サーバーの製品とテスト）。並びは製品のコードの行の多い順（同じなら名前の順）。
  const featSet = new Set()
  for (const k of agg.keys()) {
    const parts = k.split(SEP)
    if ((parts[0] === 'Java製品の機能' || parts[0] === 'Javaテストの機能') && parts[1] !== '-')
      featSet.add(parts[1])
  }
  const feats = [...featSet]
    .sort()
    .sort((a, b) => code('Java製品の機能', b) - code('Java製品の機能', a))
  const cross = [
    `${h(3)} サーバーの機能ごとの製品とテスト（コードの行）`,
    '',
    '| 機能 | 製品 | テスト | テスト ÷ 製品 |',
    '|---|---:|---:|---:|',
  ]
  for (const f of feats) {
    const p = code('Java製品の機能', f)
    const t = code('Javaテストの機能', f)
    cross.push(
      p ? `| ${f} | ${int(p)} | ${int(t)} | ${fixed(t / p, 1)} |` : `| ${f} | 0 | ${int(t)} | — |`,
    )
  }

  const nowText = formatNow(meta.now)
  const lines = []
  const print = (s = '') => lines.push(s)
  print(`${h(1)} プログラムの規模（${nowText}）\n`)
  print(`${h(2)} 数えた対象\n`)
  print('| 項目 | 値 |\n|---|---|')
  print(`| 数えた日時 | ${nowText} |`)
  print(`| ブランチと先頭 | \`${meta.branch}\` の \`${meta.head}\`（${meta.headDate}） |`)
  print(`| \`main\` の先頭 | ${meta.mainHead ? `\`${meta.mainHead}\`` : '—'} |`)
  if (withDiff) {
    print(
      `| 比べる元 | ${base.size ? `\`${base.short}\`（${base.date}）` : `—（${base.reason}）`} |`,
    )
  }
  print(
    `| 数えたファイル | git が管理するファイル ${int(size.fileCount)} 件（外したもの・分類の決まりは \`scripts/program-size.mjs\` の冒頭） |`,
  )
  print(`| 道具 | cloc ${size.clocVersion}、\`scripts/program-size.mjs\` |\n`)
  print(`${h(2)} 要点\n`)
  print(
    `- 製品のコード ${int(prod)} 行、テストのコード ${int(test)} 行。テスト ÷ 製品 = ${fixed(test / prod, 2)}。`,
  )
  print(`- サーバー: 製品 ${int(javaP)}・テスト ${int(javaT)}（${fixed(javaT / javaP, 1)} 倍）。`)
  print(`- 画面: 製品 ${int(feP)}・単体テスト ${int(feT)}（${fixed(feT / feP, 1)} 倍）。`)
  print(
    `- E2E: 流れ ${int(e2eFlow)}・補助 ${int(e2eSupport)}・補助の単体テスト ${int(e2eUnit)}。` +
      `画面の単体テストに E2E の流れと補助を足すと、画面の製品の ${fixed((feT + e2e) / feP, 1)} 倍。`,
  )
  print(
    `- E2E に関わる設定（Playwright の設定・報告の道具・\`docker/e2e/\`）${e2eConf[0]} ファイル・${int(e2eConf[1])} 行は、` +
      'ビルド・検査・運用に含む。',
  )
  print(
    `- サーバーの製品のコメント ${int(javaComment)} 行のうち、ライセンスのヘッダーは約 ${int(javaFiles * LICENSE_HEADER_LINES)} 行` +
      `（${LICENSE_HEADER_LINES} 行 × ${int(javaFiles)} ファイルの見込み）。\n`,
  )
  print(`${h(2)} 集計\n`)
  if (withDiff && !base.size) print(`- 前の回との差は出せない: ${base.reason}\n`)
  print(table('大', '大分類', { diffColumns: withDiff }))
  print(table('中', '中分類', { diffColumns: withDiff }))
  if (fold) print('<details><summary>中分類と言語・サーバーと画面の内訳の表</summary>\n')
  print(table('言語', '中分類と言語'))
  print(table('Java製品の層', 'サーバーの製品の層'))
  print(table('Java製品の機能', 'サーバーの製品の機能'))
  print(table('Javaテストの種類', 'サーバーのテストの種類'))
  print(cross.join('\n') + '\n')
  print(table('画面製品の領域', '画面の製品の領域'))
  print(table('画面テストの領域', '画面の単体テストの領域'))
  if (fold) print('</details>\n')
  return lines.join('\n') + '\n'
}

/** 引数を読む（--base <commit>、残りは数える場所）。 */
function parseArgs(argv) {
  const opts = { base: null, root: '.' }
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (a === '--base') {
      if (i + 1 >= argv.length) throw new Error('--base に比べる元のコミットを渡す')
      opts.base = argv[++i]
    } else if (a.startsWith('--base=')) {
      opts.base = a.slice('--base='.length)
    } else if (a.startsWith('--')) {
      throw new Error(`知らない引数: ${a}`)
    } else {
      opts.root = a
    }
  }
  return opts
}

function main() {
  const opts = parseArgs(process.argv.slice(2))
  const size = countSize(opts.root)
  const meta = collectMeta(opts.root)
  const base = opts.base === null ? null : countBase(opts.root, opts.base)
  process.stdout.write(renderReport(size, meta, { base }))
  if (base?.reason) process.stderr.write(`前の回との差は出せない: ${base.reason}\n`)
}

const invokedDirectly = (() => {
  try {
    return process.argv[1] && import.meta.url === pathToFileURL(realpathSync(process.argv[1])).href
  } catch {
    return false
  }
})()

if (invokedDirectly) {
  try {
    main()
  } catch (e) {
    process.stderr.write(`規模を数えられない: ${e.message}\n`)
    process.exitCode = 1
  }
}
