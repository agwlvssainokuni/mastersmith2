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
// main への取り込みの前に、前の取り込みの後のすべてのコミットの CI（.github/workflows/ci.yml）を試行ごとに確かめる台本
// （Intent 261108-ci-checks-release の A2、FR5.2）。git と gh を読むだけで、CI を動かしたり再実行したりしない。
//
// 流し方（リポジトリのルートで。gh にログインしておく）:
//   node scripts/ci-attempts.mjs [--from <commit>] [--to <commit>] [--workflow <ファイル名>] [--no-logs]
//   - --from: 範囲の元（このコミットは含まない）。既定は origin/main（前の取り込み）。
//   - --to: 範囲の先頭（このコミットまで含む）。既定は origin/develop。
//   - --workflow: 既定は ci.yml。
//   - --no-logs: 失敗した試行のログ（落ちたテストの名前を拾うため）を読まない。
//
// 出すもの（Markdown）:
//   - コミットごとの表: CI の実行の有無、実行ごとの試行と結論（例「1: failure → 2: success」）。
//   - 失敗した試行の中身: 落ちた job と手順の名前、ログから拾った落ちたテストとタスクの名前（Gradle の `> Task … FAILED`、
//     JUnit の `… > … FAILED`、Vitest の ` FAIL ` の行）。再実行で通った実行の1回目の失敗も出す。
//   - まとめ: 実行の無いコミット・再実行で通った実行・通っていない実行のあるコミットの数と、先頭のコミットの結論。
//
// 終了コード:
//   0 先頭のコミットの CI が通っている（push の実行のうち最も新しいものの最後の試行が success。手動の実行は判定に
//     使わない）。前のコミットの失敗はまとめに出すだけ。
//   1 先頭のコミットの CI が通っていない（push の実行が無い・最後の試行が success でない）。
//   2 先頭のコミットの CI が走っている最中（終わるのを待って流し直す）。
//   3 道具の誤り（git・gh が無い、範囲のコミットが見つからない、gh の答えが読めないなど）。
//
// 決まり:
// - 出すのはコミットのハッシュと件名・実行の番号・job と手順の名前・テストの名前・結論だけ。ログのほかの行は出さない
//   （リポジトリは公開で、件名とテストの名前は公開済みの値）。
// - 外の道具は配列の引数で起動し、シェルを通さない。
import { spawnSync } from 'node:child_process'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

/** 落ちたテストの名前として1つの試行から出す行の上限（多すぎるときは件数だけを足す）。 */
const MAX_FAILURE_LINES = 20
/** 件名を表に載せる長さの上限。 */
const MAX_SUBJECT = 60

/** 道具の誤り（終了コード 3）。 */
class ToolError extends Error {}

/** 引数を読む。分からない引数は道具の誤りにする。 */
function parseArgs(argv) {
  const options = { from: 'origin/main', to: 'origin/develop', workflow: 'ci.yml', logs: true }
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]
    const value = () => {
      const next = argv[i + 1]
      if (next === undefined || next.startsWith('--')) {
        throw new ToolError(`${arg} の値がありません`)
      }
      i++
      return next
    }
    if (arg === '--from') options.from = value()
    else if (arg === '--to') options.to = value()
    else if (arg === '--workflow') options.workflow = value()
    else if (arg === '--no-logs') options.logs = false
    else throw new ToolError(`分からない引数です: ${arg}`)
  }
  return options
}

/** 外の道具を起動して標準出力を返す。失敗したら道具の誤りにする（標準エラーは最初の1行だけを出す）。 */
function run(command, args, { allowFailure = false } = {}) {
  const result = spawnSync(command, args, {
    cwd: repoRoot,
    encoding: 'utf8',
    maxBuffer: 64 * 1024 * 1024,
  })
  if (result.error) {
    throw new ToolError(
      `${command} を起動できませんでした（${result.error.code ?? result.error.message}）`,
    )
  }
  if (result.status !== 0) {
    if (allowFailure) return null
    const firstLine = (result.stderr || '').split('\n').find((line) => line.trim() !== '') ?? ''
    throw new ToolError(
      `${command} ${args[0]} が失敗しました（終了コード ${result.status}）: ${firstLine}`,
    )
  }
  return result.stdout
}

/** gh の JSON の答えを読む。 */
function ghJson(args) {
  const out = run('gh', args)
  try {
    return JSON.parse(out)
  } catch {
    throw new ToolError(`gh ${args.slice(0, 2).join(' ')} の答えを JSON として読めませんでした`)
  }
}

/** 範囲のコミット（古い順）を、ハッシュと件名で返す。 */
function listCommits(from, to) {
  const fromSha = run('git', ['rev-parse', '--verify', '--quiet', `${from}^{commit}`], {
    allowFailure: true,
  })
  if (fromSha === null) throw new ToolError(`範囲の元のコミットが見つかりません: ${from}`)
  const toSha = run('git', ['rev-parse', '--verify', '--quiet', `${to}^{commit}`], {
    allowFailure: true,
  })
  if (toSha === null) throw new ToolError(`範囲の先頭のコミットが見つかりません: ${to}`)
  const out = run('git', [
    'log',
    '--reverse',
    '--topo-order',
    '--format=%H%x09%s',
    `${fromSha.trim()}..${toSha.trim()}`,
  ])
  return out
    .split('\n')
    .filter((line) => line !== '')
    .map((line) => {
      const tab = line.indexOf('\t')
      return { sha: line.slice(0, tab), subject: line.slice(tab + 1) }
    })
}

/** 表のセルに入れる文字を整える（`|` と改行を逃がし、長さを切る）。 */
function cell(text, max = Infinity) {
  const flat = String(text).replaceAll('\n', ' ').replaceAll('|', '\\|')
  return flat.length > max ? `${flat.slice(0, max - 1)}…` : flat
}

/** 色の指定の形（ESC の文字は正規表現のリテラルに書かず、文字の番号から作る）。 */
const ANSI_COLOR = new RegExp(`${String.fromCharCode(27)}\\[[0-9;]*m`, 'g')

/** 色の指定（本物の ESC と、表示用に写した ^[ の両方）を消す。 */
function stripAnsi(line) {
  return line.replace(ANSI_COLOR, '').replace(/\^\[\[[0-9;]*m/g, '')
}

/**
 * 失敗した試行のログから、落ちたタスクとテストの名前の行だけを拾う。
 * ログの行は「job の名前<TAB>手順の名前<TAB>時刻 本文」の形。本文から決まった形の行だけを返す。
 */
function failedLinesFromLog(runId, attempt) {
  const out = run(
    'gh',
    ['run', 'view', String(runId), '--attempt', String(attempt), '--log-failed'],
    {
      allowFailure: true,
    },
  )
  if (out === null) return { readable: false, lines: [] }
  const picked = []
  for (const raw of out.split('\n')) {
    const fields = raw.split('\t')
    const body = stripAnsi(fields[fields.length - 1] ?? '')
      .replace(/^\d{4}-\d{2}-\d{2}T[\d:.]+Z\s?/, '')
      .trim()
    if (
      /^> Task \S+ FAILED$/.test(body) ||
      /^\S+ > .+ FAILED$/.test(body) ||
      /^FAIL\s+\S/.test(body)
    ) {
      if (!picked.includes(body)) picked.push(body)
    }
  }
  return { readable: true, lines: picked }
}

/** 1つの実行のすべての試行を読む（1 から最後まで）。 */
function readAttempts(runSummary, withLogs) {
  const attempts = []
  for (let n = 1; n <= runSummary.attempt; n++) {
    const view = ghJson([
      'run',
      'view',
      String(runSummary.databaseId),
      '--attempt',
      String(n),
      '--json',
      'attempt,conclusion,status,jobs',
    ])
    const failedJobs = (view.jobs ?? [])
      .filter(
        (job) => job.conclusion && !['success', 'skipped', 'neutral'].includes(job.conclusion),
      )
      .map((job) => ({
        name: job.name,
        conclusion: job.conclusion,
        steps: (job.steps ?? [])
          .filter(
            (step) =>
              step.conclusion && !['success', 'skipped', 'neutral'].includes(step.conclusion),
          )
          .map((step) => `${step.name}（${step.conclusion}）`),
      }))
    const failed = view.status === 'completed' && view.conclusion !== 'success'
    const log = failed && withLogs ? failedLinesFromLog(runSummary.databaseId, n) : null
    attempts.push({
      attempt: n,
      status: view.status,
      conclusion: view.conclusion || view.status,
      failedJobs,
      log,
    })
  }
  return attempts
}

/** 試行の並びを「1: failure → 2: success」の形にする。 */
function attemptChain(attempts) {
  return attempts.map((a) => `${a.attempt}: ${a.conclusion}`).join(' → ')
}

function main() {
  const options = parseArgs(process.argv.slice(2))
  run('git', ['--version'])
  run('gh', ['--version'])
  const commits = listCommits(options.from, options.to)
  const out = []
  out.push(`# CI の試行ごとの確かめ（${options.workflow}）`)
  out.push('')
  out.push(`- 範囲: \`${options.from}..${options.to}\`（${commits.length} コミット、古い順）`)
  // 範囲の両端の SHA を出す（手元の origin/* が古いと範囲がずれるため。先に git fetch しておく。R-03）。
  out.push(
    `- 範囲の両端: \`${run('git', ['rev-parse', '--short=8', options.from]).trim()}\`（含まない）..\`${run('git', ['rev-parse', '--short=8', options.to]).trim()}\``,
  )
  out.push(`- 調べた時刻: ${new Date().toISOString()}`)
  out.push('')
  if (commits.length === 0) {
    out.push('範囲にコミットがありません。')
    process.stdout.write(`${out.join('\n')}\n`)
    return 0
  }

  const rows = []
  const details = []
  let noRun = 0
  let rerunPassed = 0
  let notPassedCommits = 0
  let tip = null
  commits.forEach((commit, index) => {
    const runs = ghJson([
      'run',
      'list',
      '--commit',
      commit.sha,
      '--workflow',
      options.workflow,
      '--limit',
      '50',
      '--json',
      'databaseId,attempt,conclusion,status,event,createdAt',
    ]).sort((a, b) => String(a.createdAt).localeCompare(String(b.createdAt)))
    const short = commit.sha.slice(0, 8)
    const isTip = index === commits.length - 1
    if (runs.length === 0) {
      noRun++
      rows.push(
        `| ${index + 1} | \`${short}\` | ${cell(commit.subject, MAX_SUBJECT)} | 実行なし | — | — |`,
      )
      if (isTip) tip = { state: 'none' }
      return
    }
    let commitNotPassed = false
    for (const runSummary of runs) {
      const attempts = readAttempts(runSummary, options.logs)
      const last = attempts[attempts.length - 1]
      const lastOk = last.status === 'completed' && last.conclusion === 'success'
      if (attempts.length > 1 && lastOk) rerunPassed++
      if (last.status === 'completed' && !lastOk) commitNotPassed = true
      rows.push(
        `| ${index + 1} | \`${short}\` | ${cell(commit.subject, MAX_SUBJECT)} | ${runSummary.databaseId} | ${runSummary.event} | ${attemptChain(attempts)} |`,
      )
      for (const a of attempts.filter(
        (x) => x.status === 'completed' && x.conclusion !== 'success',
      )) {
        details.push(
          `### \`${short}\` の実行 ${runSummary.databaseId} の試行 ${a.attempt}（${a.conclusion}）`,
        )
        details.push('')
        if (a.failedJobs.length === 0) details.push('- 落ちた job: なし（job の結論が読めない）')
        for (const job of a.failedJobs) {
          details.push(
            `- job「${job.name}」（${job.conclusion}）: 手順 ${job.steps.join('・') || '—'}`,
          )
        }
        if (a.log === null) {
          details.push('- ログ: 読んでいない（--no-logs）')
        } else if (!a.log.readable) {
          details.push('- ログ: 読めない（保存の期限切れなど）')
        } else if (a.log.lines.length === 0) {
          details.push('- 落ちたテスト・タスク: 拾えなかった（ログの形が決まった形でない）')
        } else {
          details.push('- 落ちたテスト・タスク:')
          for (const line of a.log.lines.slice(0, MAX_FAILURE_LINES)) {
            details.push(`  - \`${line.replaceAll('`', "'")}\``)
          }
          if (a.log.lines.length > MAX_FAILURE_LINES) {
            details.push(`  - ほか ${a.log.lines.length - MAX_FAILURE_LINES} 行`)
          }
        }
        details.push('')
      }
      if (isTip && runSummary.event === 'push') {
        // 先頭のコミットは、push の実行のうち最も新しいもの（並べた最後）の最後の試行で判定する。手動の実行
        // （workflow_dispatch。it_forks の比べなど）は表に出すだけで判定に使わない（Code Generation のレビュー R-03）。
        tip = { state: last.status !== 'completed' ? 'running' : lastOk ? 'success' : 'failure' }
      }
    }
    if (isTip && tip === null) tip = { state: 'none' }
    if (commitNotPassed) notPassedCommits++
  })

  out.push('| # | コミット | 件名 | 実行 | きっかけ | 試行ごとの結論 |')
  out.push('|---|---|---|---|---|---|')
  out.push(...rows)
  out.push('')
  out.push('## 失敗した試行')
  out.push('')
  if (details.length === 0) out.push('なし。')
  else out.push(...details)
  out.push('')
  out.push('## まとめ')
  out.push('')
  out.push(
    `- CI の実行の無いコミット: ${noRun} 件（まとめてプッシュした途中のコミットには実行が無い）`,
  )
  out.push(`- 再実行で通った実行: ${rerunPassed} 件（1回目の失敗は上の「失敗した試行」）`)
  out.push(`- 最後の試行が通っていない実行のあるコミット: ${notPassedCommits} 件`)
  const tipLabel = {
    none: 'push の実行なし（通っていない）',
    running: '走っている最中',
    success: '通っている',
    failure: '通っていない',
  }[tip.state]
  out.push(`- 先頭のコミット \`${commits[commits.length - 1].sha.slice(0, 8)}\`: ${tipLabel}`)
  process.stdout.write(`${out.join('\n')}\n`)
  if (tip.state === 'success') return 0
  if (tip.state === 'running') return 2
  return 1
}

try {
  process.exitCode = main()
} catch (error) {
  if (error instanceof ToolError) {
    process.stderr.write(`ci-attempts: ${error.message}\n`)
    process.exitCode = 3
  } else {
    throw error
  }
}
