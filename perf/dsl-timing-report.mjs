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
// perf/dsl-timing.sh の結果の置き場から、まとめ（Markdown）を標準出力に出す。
//   node perf/dsl-timing-report.mjs <結果の置き場>
// - 要求ごとの時間（curl の time_total）と状態コード・大きさ
// - 要求の間（開始から終了の 1 秒後まで）に起きた GC の直前のヒープ使用量の最大（GC の記録 gc.log、エポックのミリ秒つき）
// - 既定の DSL の生成の内訳（TargetSchemaDslGenerator の DEBUG のログ）
// - コンテナのメモリの最大（cgroup の memory.peak）と、止まったかどうか（OOMKilled）
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'

const dir = process.argv[2]
if (!dir) {
  console.error('使い方: node perf/dsl-timing-report.mjs <結果の置き場>')
  process.exit(1)
}
const read = (file) => (existsSync(file) ? readFileSync(file, 'utf8') : '')
const UNITS = { B: 1 / 1024 / 1024, K: 1 / 1024, M: 1, G: 1024 }

function parseGc(text) {
  const events = []
  for (const line of text.split('\n')) {
    const m = line.match(/^\[(\d+)ms\].*?(\d+)([BKMG])->(\d+)([BKMG])\((\d+)([BKMG])\)/)
    if (m) {
      events.push({
        at: Number(m[1]),
        beforeMb: Number(m[2]) * UNITS[m[3]],
        afterMb: Number(m[4]) * UNITS[m[5]],
        committedMb: Number(m[6]) * UNITS[m[7]],
      })
    }
  }
  return events
}

const rows = read(path.join(dir, 'timings.tsv'))
  .trim()
  .split('\n')
  .slice(1)
  .filter(Boolean)
  .map((line) => {
    const [kind, name, http, seconds, bytesDown, bytesUp, start, end] = line.split('\t')
    return { kind, name, http, seconds: Number(seconds), bytesDown, bytesUp, start: Number(start), end: Number(end) }
  })
const kinds = [...new Set(rows.map((r) => r.kind))]

console.log('# DSL の1回ずつの時間（使い捨ての環境）\n')
console.log('```text\n' + read(path.join(dir, 'env.txt')).trim() + '\n```\n')

for (const kind of kinds) {
  const kindDir = path.join(dir, kind)
  const gc = parseGc(read(path.join(kindDir, 'gc.log')))
  console.log(`## ${kind}\n`)
  console.log('| 要求 | HTTP | 秒 | 受信バイト | 送信バイト | 区間の GC の回数 | 区間の GC 直前のヒープの最大（MB） |')
  console.log('|---|---|---|---|---|---|---|')
  for (const r of rows.filter((x) => x.kind === kind)) {
    const inRange = gc.filter((e) => e.at >= r.start && e.at <= r.end + 1000)
    const peak = inRange.length ? Math.max(...inRange.map((e) => e.beforeMb)).toFixed(0) : '—'
    console.log(`| ${r.name} | ${r.http} | ${r.seconds.toFixed(3)} | ${r.bytesDown} | ${r.bytesUp} | ${inRange.length} | ${peak} |`)
  }
  const breakdown = read(path.join(kindDir, 'generate-breakdown.log'))
    .split('\n')
    .filter(Boolean)
    .map((line) => {
      try {
        return JSON.parse(line.slice(line.indexOf('{')))
      } catch {
        return null
      }
    })
    .filter(Boolean)
  if (breakdown.length) {
    console.log('\n既定の DSL の生成の内訳（DEBUG のログ、ミリ秒）\n')
    console.log('| 時刻 | tables | bytes | readMillis | buildMillis | writeMillis | validateMillis | build+write |')
    console.log('|---|---|---|---|---|---|---|---|')
    for (const b of breakdown) {
      console.log(
        `| ${b.timestamp} | ${b.tables} | ${b.bytes} | ${b.readMillis} | ${b.buildMillis} | ${b.writeMillis} | ${b.validateMillis} | ${b.buildMillis + b.writeMillis} |`,
      )
    }
  }
  const large = read(path.join(kindDir, 'large-dsl.txt')).trim()
  if (large) console.log('\n10MB の DSL の作り方\n\n```text\n' + large + '\n```')
  const heapMax = gc.length ? Math.max(...gc.map((e) => e.beforeMb)).toFixed(0) : '—'
  const committedMax = gc.length ? Math.max(...gc.map((e) => e.committedMb)).toFixed(0) : '—'
  const memPeak = read(path.join(kindDir, 'memory-peak.txt')).trim()
  console.log('\n資源\n')
  console.log(`- GC の回数: ${gc.length}、GC 直前のヒープの最大: ${heapMax} MB、確保したヒープの最大: ${committedMax} MB`)
  console.log(
    `- コンテナのメモリの最大（memory.peak）: ${memPeak ? (Number(memPeak) / 1024 / 1024).toFixed(0) + ' MB' : '取れず'}`,
  )
  console.log(`- コンテナの状態: ${read(path.join(kindDir, 'state.txt')).trim() || '取れず'}`)
  const patternDsl = read(path.join(kindDir, 'pattern-dsl.txt')).trim()
  if (patternDsl) console.log('\n重い正規表現の DSL（perf/make-pattern-dsl.mjs）\n\n```text\n' + patternDsl + '\n```')
  const storage = read(path.join(kindDir, 'storage.tsv')).trim()
  if (storage) {
    const [head, ...lines] = storage.split('\n').map((l) => l.split('\t'))
    console.log('\n保存の量（storage.tsv。バイトは MB に直した値）\n')
    console.log('| ' + head.join(' | ') + ' |')
    console.log('|' + head.map(() => '---').join('|') + '|')
    const mb = (v) => (/^\d+$/.test(v) ? (Number(v) / 1024 / 1024).toFixed(1) : v)
    for (const l of lines) {
      console.log(`| ${l[0]} | ${mb(l[1])} | ${(Number(l[2]) / 1024).toFixed(1)} | ${l.slice(3, -2).map(mb).join(' | ')} | ${l.at(-2)} | ${l.at(-1)} |`)
    }
    const before = read(path.join(kindDir, 'memory-peak-before-restart.txt')).trim()
    if (before) console.log(`\n- 起動し直す前のコンテナのメモリの最大（memory.peak）: ${(Number(before) / 1024 / 1024).toFixed(0)} MB`)
  }
  const lang = read(path.join(kindDir, 'ui-lang.json')).trim()
  if (lang) console.log('\n英語のロケールの確かめ（perf/ui/dsl-ui-lang.mjs）\n\n```json\n' + lang + '\n```')
  const ui = read(path.join(kindDir, 'ui-timing.json')).trim()
  if (ui) console.log('\n画面の時間（perf/ui/dsl-ui-timing.mjs）\n\n```json\n' + ui + '\n```')
  console.log('')
}
