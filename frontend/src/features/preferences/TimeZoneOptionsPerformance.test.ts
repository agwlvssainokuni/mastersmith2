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
// 選択肢の組み立ての時間の測り（Intent 261102-timezone-ui-polish の U2、NFR2.1.6、PD3）。名前の一覧と現在時刻を注入し、各回の前に
// 整形器のキャッシュを空にして、新しい配列で 5 回組み立てて中央値が 100 ミリ秒以内であることを確かめる。100 ミリ秒は延ばさない。
// 失敗したときは 5 回の値・個数・Node の版・CPU を出す。冷たい最初の 1 回の値も出力に残す（記録用）。
import os from 'node:os'
import { describe, expect, it } from 'vitest'
import { clearFormatterCache } from '../../shared/format/timeZone/timeZoneGate'
import { buildTimeZoneOptions, readSupportedTimeZoneNames } from './buildTimeZoneOptions'

const NOW = new Date('2026-07-15T00:00:00Z')
const LIMIT_MS = 100
const RUNS = 5

describe('time zone options performance', () => {
  it('builds the whole list within 100 ms by the median of 5 cold runs', () => {
    const base = readSupportedTimeZoneNames()
    const times: number[] = []
    for (let run = 0; run < RUNS; run++) {
      clearFormatterCache()
      const names = [...base]
      const started = performance.now()
      const options = buildTimeZoneOptions(names, null, NOW, 'browser')
      times.push(performance.now() - started)
      expect(options.length).toBeGreaterThan(100)
    }
    const sorted = [...times].sort((a, b) => a - b)
    const median = sorted[Math.floor(RUNS / 2)] ?? Number.POSITIVE_INFINITY
    // 記録用（Build and Test の記録に写す）。冷たい最初の 1 回の値は times[0]。
    console.info(
      `[time-zone-options] names=${base.length} runs(ms)=${times.map((t) => t.toFixed(1)).join(',')} median=${median.toFixed(1)} node=${process.version} cpu=${os.cpus()[0]?.model ?? 'unknown'}`,
    )

    expect(
      median,
      `5 回の値 ${times.map((t) => t.toFixed(1)).join(',')} ms、個数 ${base.length}、Node ${process.version}、CPU ${os.cpus()[0]?.model ?? 'unknown'}`,
    ).toBeLessThanOrEqual(LIMIT_MS)
  })
})
