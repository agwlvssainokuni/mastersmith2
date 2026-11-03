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
// 性能の測り（NFR2.1.1・NFR2.1.2）。固定の時点 1 万個を formatInstant に通し、5 回の測りの中央値で判定する。
// 目標（合計 500 ミリ秒・1 つあたり平均 0.05 ミリ秒）は緩めない。失敗時は 5 回の値と環境を出す。
import { cpus } from 'node:os'
import { describe, expect, it } from 'vitest'
import { formatInstant } from './formatInstant'
import { formatUtcInstant } from './instantText'

const COUNT = 10_000
const RUNS = 5
const TOTAL_LIMIT_MS = 500
const AVERAGE_LIMIT_MS = 0.05
const FRACTIONS = ['', '123', '123456']

/** 2026 年の夏時間の切り替えをまたぐ、端数の桁 0・3・6 を混ぜた固定の時点 */
function buildInstants(): string[] {
  const start = Date.UTC(2026, 0, 1) / 1000
  return Array.from({ length: COUNT }, (_, i) =>
    formatUtcInstant(start + i * 3137, FRACTIONS[i % 3] ?? ''),
  )
}

describe('conversion performance', () => {
  it('formats 10000 instants within 500 ms and 0.05 ms each (median of 5 runs)', () => {
    const instants = buildInstants()
    // 整形器の生成を測りから外すため、1 回だけ温めておく（本番でも名前ごとに 1 回だけ作られる）
    formatInstant(instants[0] ?? '', 'America/New_York')
    const durations: number[] = []
    for (let run = 0; run < RUNS; run++) {
      const started = performance.now()
      for (const instant of instants) {
        formatInstant(instant, 'America/New_York')
      }
      durations.push(performance.now() - started)
    }
    const median =
      [...durations].sort((a, b) => a - b)[Math.floor(RUNS / 2)] ?? Number.POSITIVE_INFINITY
    const average = median / COUNT
    const environment = `node ${process.version}, cpu ${cpus()[0]?.model ?? 'unknown'} x${cpus().length}`
    const detail = `durations(ms)=${durations.map((d) => d.toFixed(1)).join(', ')} median=${median.toFixed(1)} average=${average.toFixed(4)} env=${environment}`
    expect(median, detail).toBeLessThanOrEqual(TOTAL_LIMIT_MS)
    expect(average, detail).toBeLessThanOrEqual(AVERAGE_LIMIT_MS)
    // 測った値を、成果物に書けるよう出力に残す（日時を含まない値だけ）
    process.stdout.write(`[timeZone performance] ${detail}\n`)
  })
})
