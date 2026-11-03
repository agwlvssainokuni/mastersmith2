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
// @vitest-environment node
//
// scripts/check-svg-safety.mjs の性質ベースのテスト（U6 の SD7.1a。fast-check）。
//
// 失敗したときの再現の手順:
//   1. 失敗の表示 `property failed: seed=<数> path=<道> runs=<回数>` の seed と path を控える（反例の値は表示しない）。
//   2. 落ちた性質の assertProperty(…, { seed: <数>, path: '<道>' }) のように 2 つ目の引数を一時に足して流す。
//   3. 確かめた後に引数を元に戻す。
import fc from 'fast-check'
import { beforeAll, describe, expect, it } from 'vitest'
import { goodSvg, loadSvgTool, type SvgSafetyModule } from './svgFixtures'

let tool: SvgSafetyModule

beforeAll(async () => {
  tool = await loadSvgTool()
})

/** fast-check の結果を受け、失敗のときは種・道・回数だけを載せて投げる */
function assertProperty<Ts>(prop: fc.IProperty<Ts>, params?: fc.Parameters<Ts>): void {
  const result = fc.check(prop, params)
  if (result.failed) {
    throw new Error(
      `property failed: seed=${result.seed} path=${result.counterexamplePath ?? ''} runs=${result.numRuns}`,
    )
  }
}

const INNER =
  '<g fill="#2563eb"><path d="M4 4H20V20H4Z"/><circle cx="8" cy="8" r="2"/></g><rect x="1" y="1" width="3" height="3"/>'
const GOOD = goodSvg(INNER)

/** 良い例の中で、子要素を入れてよい位置（開始タグの終わりの直後）の一覧 */
function insertionPoints(content: string): number[] {
  const points: number[] = []
  const rootEnd = content.indexOf('>', content.indexOf('<svg')) + 1
  points.push(rootEnd)
  for (const m of content.matchAll(/<g [^>]*>/g)) {
    points.push((m.index ?? 0) + m[0].length)
  }
  return points
}

const POINTS = insertionPoints(GOOD)
const FORBIDDEN_NAMES = fc.oneof(
  fc.constantFrom(
    'script',
    'style',
    'a',
    'use',
    'image',
    'foreignObject',
    'iframe',
    'object',
    'embed',
    'animate',
    'set',
    'SCRIPT',
    'svg:script',
  ),
  fc.stringMatching(/^[A-Za-z][A-Za-z0-9:]{0,9}$/).filter((n) => !tool.ALLOWED_ELEMENTS.has(n)),
)

describe('svg safety check properties', () => {
  it('accepts the good example (the baseline of the other properties)', () => {
    expect(tool.checkSvgContent(GOOD)).toEqual({ ok: true })
  })

  it('never throws and returns a result quickly for any string', () => {
    assertProperty(
      fc.property(
        fc.oneof(fc.string({ maxLength: 300 }), fc.string({ unit: 'binary', maxLength: 300 })),
        fc.boolean(),
        (input, standalone) => {
          const started = performance.now()
          const result = tool.checkSvgContent(input, { standalone })
          const quick = performance.now() - started < 200
          return quick && typeof result.ok === 'boolean'
        },
      ),
      { numRuns: 300 },
    )
  })

  it('always rejects an element outside the allow list put anywhere in the good example', () => {
    assertProperty(
      fc.property(
        fc.constantFrom(...POINTS),
        FORBIDDEN_NAMES,
        fc.boolean(),
        (point, name, selfClosing) => {
          const element = selfClosing ? `<${name}/>` : `<${name}></${name}>`
          const mutated = GOOD.slice(0, point) + element + GOOD.slice(point)
          return !tool.checkSvgContent(mutated).ok
        },
      ),
      { numRuns: 300 },
    )
  })

  it('rejects an allowed element name when one letter changes its case', () => {
    const openTags = [...GOOD.matchAll(/<(g|path|circle|rect)[ />]/g)]
    expect(openTags.length).toBeGreaterThan(0)
    assertProperty(
      fc.property(
        fc.integer({ min: 0, max: openTags.length - 1 }),
        fc.nat({ max: 20 }),
        (tagIndex, letterSeed) => {
          const m = openTags[tagIndex]
          const name = m[1]
          const at = (m.index ?? 0) + 1 + (letterSeed % name.length)
          const flipped =
            GOOD[at] === GOOD[at].toUpperCase() ? GOOD[at].toLowerCase() : GOOD[at].toUpperCase()
          return !tool.checkSvgContent(GOOD.slice(0, at) + flipped + GOOD.slice(at + 1)).ok
        },
      ),
      { numRuns: 200 },
    )
  })

  it('rejects any non-blank text put before the header or after the root', () => {
    const nonBlank = fc.string({ minLength: 1, maxLength: 12 }).filter((s) => s.trim() !== '')
    assertProperty(
      fc.property(nonBlank, fc.boolean(), (extra, before) => {
        const mutated = before ? extra + GOOD : GOOD + extra
        return !tool.checkSvgContent(mutated).ok
      }),
      { numRuns: 300 },
    )
  })

  it('accepts only values without angle brackets, ampersands or colons (xmlns excepted)', () => {
    const mutation = fc.record({
      at: fc.nat({ max: GOOD.length }),
      cut: fc.nat({ max: 3 }),
      insert: fc.oneof(
        fc.constant(''),
        fc.constantFrom('<', '>', '&', ':', '"', "'", ' ', '/', '=', 'x'),
        fc.string({ maxLength: 3 }),
      ),
    })
    let accepted = 0
    assertProperty(
      fc.property(mutation, ({ at, cut, insert }) => {
        const mutated = GOOD.slice(0, at) + insert + GOOD.slice(at + cut)
        if (!tool.checkSvgContent(mutated).ok) {
          return true
        }
        accepted++
        const body = mutated.slice(mutated.indexOf('-->') + 3)
        for (const m of body.matchAll(/ ([A-Za-z:-]+)=(["'])(.*?)\2/gs)) {
          if (m[1] !== 'xmlns' && /[<>&:]/.test(m[3])) {
            return false
          }
        }
        return true
      }),
      { numRuns: 500 },
    )
    expect(accepted).toBeGreaterThan(0)
  })
})
