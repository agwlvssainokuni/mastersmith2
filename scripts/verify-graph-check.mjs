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
// 1コマンドの検査の入口（verify・verifyQuick）のタスクの一覧と、-PitTests・-PitForks の誤りの扱いを確かめる台本
// （Intent 261107-verify-quick-local の計画の Step 6。8 項目）。
// ./gradlew を子のプロセスで --dry-run だけで流す（テストは流さない）。1 つでも違えば終了コード 1 で終わる。
// 比べの元は scripts/verify-graph-baseline.txt（変える前の ./gradlew verify --dry-run のタスクの一覧）。
//
// 流し方（リポジトリのルートで）: node scripts/verify-graph-check.mjs
//
// 合成ビルド（composite build）で取り込む java-mustache-processor のタスク（:java-mustache-processor:...）は、verify の段の
// 順の決まり（mustRunAfter）の外にあり、Gradle が回ごとに置く位置を変える。そのため、これらのタスクは「含まれるか」だけを比べ、
// 順の比べからは外す。
import { spawnSync } from 'node:child_process'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const repoRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const gradlew = path.join(repoRoot, 'gradlew')
const baselinePath = path.join(repoRoot, 'scripts', 'verify-graph-baseline.txt')

/** 合成ビルドで取り込むビルドのタスクの接頭辞（順の比べから外す）。 */
const includedBuildPrefixes = [':java-mustache-processor:']

/** verify の段のタスクと入口（中身を持たない集まりのタスク。verifyQuick の一覧には現れない）。 */
const verifyLifecycleTasks = [
  ':verifyPrepare',
  ':verifyFormat',
  ':verifyLint',
  ':verifyLicense',
  ':verifyBuild',
  ':verifyUnitTest',
  ':verifyIntegrationTest',
  ':verifyCoverage',
  ':verifySecurity',
  ':verifyArtifact',
  ':verify',
]

/** verifyQuick で外すタスク（-PitTests なし）。 */
const quickExcludedTasks = [
  ':frontendTest',
  ':backend:integrationTest',
  ':backend:jacocoTestCoverageVerification',
  ':backend:jacocoTestReport',
]

/** ./gradlew を流し、終了コードと出力（標準出力と標準エラー）と、--dry-run のタスクのパスの一覧を返す。 */
function runGradle(args) {
  const result = spawnSync(gradlew, [...args, '--console=plain'], {
    cwd: repoRoot,
    encoding: 'utf8',
    maxBuffer: 16 * 1024 * 1024,
  })
  if (result.error) {
    throw new Error(`./gradlew を起動できませんでした: ${result.error.message}`)
  }
  const output = `${result.stdout}\n${result.stderr}`
  const tasks = result.stdout
    .split('\n')
    .map((line) => /^(:\S+) SKIPPED$/.exec(line.trim()))
    .filter((match) => match !== null)
    .map((match) => match[1])
  return { status: result.status, output, tasks }
}

function readBaseline() {
  return readFileSync(baselinePath, 'utf8')
    .split('\n')
    .map((line) => line.trim())
    .filter((line) => line.length > 0)
}

function isIncludedBuildTask(task) {
  return includedBuildPrefixes.some((prefix) => task.startsWith(prefix))
}

/** 順の比べに使う並び（合成ビルドのタスクを除く）。 */
function ownBuildOrder(tasks) {
  return tasks.filter((task) => !isIncludedBuildTask(task))
}

/** 2 つの一覧の差（含まれるかだけ）。 */
function setDiff(actual, expected) {
  const actualSet = new Set(actual)
  const expectedSet = new Set(expected)
  return {
    missing: expected.filter((task) => !actualSet.has(task)),
    extra: actual.filter((task) => !expectedSet.has(task)),
  }
}

/** 同じタスクの集まりで、合成ビルドのタスクを除いた順が同じかを確かめる。違えば理由の文の一覧を返す。 */
function compareTaskLists(actual, expected) {
  const problems = []
  const { missing, extra } = setDiff(actual, expected)
  if (missing.length > 0) problems.push(`missing: ${missing.join(', ')}`)
  if (extra.length > 0) problems.push(`unexpected: ${extra.join(', ')}`)
  if (problems.length === 0) {
    const actualOrder = ownBuildOrder(actual).join('\n')
    const expectedOrder = ownBuildOrder(expected).join('\n')
    if (actualOrder !== expectedOrder) problems.push('order differs from the expected list')
  }
  return problems
}

/** 失敗の出力に理由の文が含まれるかを確かめる。違えば理由の文の一覧を返す。 */
function expectFailure(run, reason) {
  const problems = []
  if (run.status === 0) problems.push('gradle succeeded but was expected to fail')
  if (!run.output.includes(reason)) problems.push(`output does not contain the reason: ${reason}`)
  return problems
}

const results = []

function check(name, body) {
  let problems
  try {
    problems = body()
  } catch (error) {
    problems = [`error: ${error.message}`]
  }
  results.push({ name, problems })
  const mark = problems.length === 0 ? 'PASS' : 'FAIL'
  console.log(`${mark} ${name}`)
  for (const problem of problems) console.log(`     - ${problem}`)
}

const baseline = readBaseline()
const verifyRun = runGradle(['verify', '--dry-run'])
const quickRun = runGradle(['verifyQuick', '--dry-run'])

check('1. verify --dry-run task list matches the baseline (FR4.1, R-03)', () => {
  if (verifyRun.status !== 0) return [`gradle failed:\n${verifyRun.output}`]
  return compareTaskLists(verifyRun.tasks, baseline)
})

check('2. verifyQuick equals verify minus excluded plus jacocoQuickReport (FR1.1, FR3)', () => {
  if (quickRun.status !== 0) return [`gradle failed:\n${quickRun.output}`]
  const removed = new Set([...verifyLifecycleTasks, ...quickExcludedTasks])
  const expected = [
    ...baseline.filter((task) => !removed.has(task)),
    ':backend:jacocoQuickReport',
    ':verifyQuick',
  ]
  const { missing, extra } = setDiff(quickRun.tasks, expected)
  const problems = []
  if (missing.length > 0) problems.push(`missing: ${missing.join(', ')}`)
  if (extra.length > 0) problems.push(`unexpected: ${extra.join(', ')}`)
  return problems
})

check('3. verifyQuick keeps the verify order, jacocoQuickReport in stage 7 (FR1.2)', () => {
  if (quickRun.status !== 0) return [`gradle failed:\n${quickRun.output}`]
  const problems = []
  const quickOrder = ownBuildOrder(quickRun.tasks)
  const quickSet = new Set(quickOrder)
  const commonInVerifyOrder = ownBuildOrder(baseline).filter((task) => quickSet.has(task))
  const commonSet = new Set(commonInVerifyOrder)
  const commonInQuickOrder = quickOrder.filter((task) => commonSet.has(task))
  if (commonInQuickOrder.join('\n') !== commonInVerifyOrder.join('\n')) {
    problems.push('the order of the shared tasks differs from verify')
  }
  const report = quickOrder.indexOf(':backend:jacocoQuickReport')
  const test = quickOrder.indexOf(':backend:test')
  const gate = quickOrder.indexOf(':backend:spotbugsGate')
  if (report < 0 || test < 0 || gate < 0) {
    problems.push('jacocoQuickReport, test or spotbugsGate is missing')
  } else if (!(test < report && report < gate)) {
    problems.push(`unexpected position: test=${test}, report=${report}, spotbugsGate=${gate}`)
  }
  return problems
})

check('4. verifyQuick -PitTests adds integrationTest after test (FR2.1)', () => {
  const run = runGradle(['verifyQuick', '-PitTests=AuditTraceIdIT', '--dry-run'])
  if (run.status !== 0) return [`gradle failed:\n${run.output}`]
  const problems = []
  const it = run.tasks.indexOf(':backend:integrationTest')
  const test = run.tasks.indexOf(':backend:test')
  if (it < 0) {
    problems.push(':backend:integrationTest is missing')
  } else if (test < 0 || !(test < it)) {
    problems.push(':backend:integrationTest does not come after :backend:test')
  }
  if (run.tasks.includes(':backend:jacocoTestCoverageVerification')) {
    problems.push(':backend:jacocoTestCoverageVerification must not be included')
  }
  return problems
})

check('5. verify -PitTests=AuditTraceIdIT fails with a reason (FR2.3, R-01)', () =>
  expectFailure(
    runGradle(['verify', '-PitTests=AuditTraceIdIT', '--dry-run']),
    '-PitTests は verify には付けられません',
  ),
)

check('6. verifyQuick -PitTests=" , " fails with a reason (R-05)', () =>
  expectFailure(
    runGradle(['verifyQuick', '-PitTests= , ', '--dry-run']),
    '-PitTests に結合テストのクラスの形がありません',
  ),
)

check('7. verify -PitForks=0 and -PitForks=x fail with a reason (FR4.2)', () => {
  const reason = '-PitForks は 1 以上の整数で指定してください'
  return ['0', 'x'].flatMap((value) =>
    expectFailure(runGradle(['verify', `-PitForks=${value}`, '--dry-run']), reason).map(
      (problem) => `=${value}: ${problem}`,
    ),
  )
})

check('8. verify -PitForks=2 keeps the baseline task list (FR4.1, FR4.2)', () => {
  const run = runGradle(['verify', '-PitForks=2', '--dry-run'])
  if (run.status !== 0) return [`gradle failed:\n${run.output}`]
  return compareTaskLists(run.tasks, baseline)
})

const failed = results.filter((result) => result.problems.length > 0)
console.log(`\n${results.length - failed.length} passed, ${failed.length} failed`)
process.exit(failed.length === 0 ? 0 : 1)
