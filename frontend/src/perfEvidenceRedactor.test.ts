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
// perf/redact-k6-evidence.mjs（k6 の結果の JWT を伏せる道具）の純粋な関数の確かめ。
// 道具は frontend/ の外にあるため、テストの中で import() で読む（src/eslintImportRules.test.ts と同じ形）。
//
// JWT の形の値は makeJwtLike() が実行の時に組み立てる。このファイルに完成した JWT の形の文字列を書かない
// （公開のリポジトリで gitleaks と evidence の検査を自分で落とさないため）。
//
// 性質ベースのテストが落ちたときの再現の手順:
//   1. 失敗の表示 `property failed: seed=<数> path=<道> runs=<回数>` の seed と path を控える
//      （反例の値は表示しない）。
//   2. 落ちた性質の assertProperty(…, { seed: <数>, path: '<道>' }) のように 2 つ目の引数を一時に足し、
//      `npx --no-install vitest run --project unit src/perfEvidenceRedactor.test.ts` で流す。
//   3. 確かめた後に引数を元に戻す。
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import fc from 'fast-check'
import { beforeAll, describe, expect, it } from 'vitest'

/** 突き合わせの正解に使う JWT の形の定義（道具の説明の正規表現と同じ） */
const REFERENCE_SHAPE = /(?<![A-Za-z0-9])eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+/g
const MARK = '[REDACTED-JWT]'
const B64 = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_'

/** JWT の形の値を実行の時に組み立てる（3 区間を . でつなぐ） */
function makeJwtLike(first: string, second: string, third: string): string {
  return ['eyJ' + first, second, third].join('.')
}

/** 正解の定義で置き換えた文字列 */
function referenceRedact(text: string): string {
  return text.replace(REFERENCE_SHAPE, MARK)
}

/** 正解の定義で見つかる件数 */
function referenceCount(text: string): number {
  return [...text.matchAll(REFERENCE_SHAPE)].length
}

/** fast-check の結果を受け、失敗のときは種・道・回数だけを載せて投げる（反例の値は出さない） */
function assertProperty<Ts>(prop: fc.IProperty<Ts>, params?: fc.Parameters<Ts>): void {
  const result = fc.check(prop, params)
  if (result.failed) {
    throw new Error(
      `property failed: seed=${result.seed} path=${result.counterexamplePath ?? ''} runs=${result.numRuns}`,
    )
  }
}

const b64Segment = fc.string({ unit: fc.constantFrom(...B64), minLength: 1, maxLength: 10 })
const jwtLike = fc
  .tuple(b64Segment, b64Segment, b64Segment)
  .map(([a, b, c]) => makeJwtLike(a, b, c))
/** JWT に近い文字列（2 区間・直前に英数字・末尾の =・Bearer の前置き・. が 4 つ以上） */
const nearJwt = fc.oneof(
  fc.tuple(b64Segment, b64Segment).map(([a, b]) => ['eyJ' + a, b].join('.')),
  jwtLike.map((j) => 'x' + j),
  jwtLike.map((j) => j + '='),
  jwtLike.map((j) => 'Bearer ' + j),
  fc.tuple(jwtLike, b64Segment, b64Segment).map(([j, d, e]) => [j, d, e].join('.')),
)
const textPiece = fc.oneof(
  fc.string({ maxLength: 12 }),
  fc.string({ unit: 'grapheme', maxLength: 8 }),
  jwtLike,
  nearJwt,
  fc.constantFrom('\n', '\r\n', ' ', '\t', '{"a":', '}', '[1,', 'é', '日本語'),
)
/** JWT の形と JWT に近い文字列を混ぜた文字列 */
const mixedString = fc.array(textPiece, { maxLength: 6 }).map((pieces) => pieces.join(''))
const keyArb = fc.oneof(
  { weight: 8, arbitrary: fc.string({ maxLength: 8 }) },
  { weight: 1, arbitrary: jwtLike },
  { weight: 1, arbitrary: nearJwt },
)
const { value: jsonValue } = fc.letrec((tie) => ({
  value: fc.oneof(
    { depthSize: 'small' },
    mixedString,
    fc.integer(),
    fc.double({ noNaN: true, noDefaultInfinity: true }),
    fc.boolean(),
    fc.constant(null),
    tie('array'),
    tie('object'),
  ),
  array: fc.array(tie('value'), { maxLength: 4 }),
  object: fc.dictionary(keyArb, tie('value'), { maxKeys: 4 }),
}))
/** 任意の JSON。半分は上の階層に setup_data を持つ */
const topLevelJson = fc
  .oneof(
    jsonValue,
    fc
      .tuple(fc.dictionary(keyArb, jsonValue, { maxKeys: 3 }), jsonValue)
      .map(([object, setup]) => ({ ...object, setup_data: setup })),
  )
  .map((value) => JSON.parse(JSON.stringify(value) ?? 'null') as unknown)

/** 配列でないオブジェクトか */
function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

/** 値の種類（構造の比べに使う） */
function kindOf(value: unknown): string {
  if (value === null) return 'null'
  if (Array.isArray(value)) return 'array'
  return typeof value
}

/**
 * 入力と出力を並べてたどり、各組に check を当てる。outsideSetup は上の階層の setup_data の外か。
 * check が false を返せば false。
 */
function walkPairs(
  input: unknown,
  output: unknown,
  check: (a: unknown, b: unknown, outsideSetup: boolean) => boolean,
): boolean {
  const topSetup = isRecord(input) && Object.hasOwn(input, 'setup_data')
  const work: { a: unknown; b: unknown; outside: boolean; top: boolean }[] = [
    { a: input, b: output, outside: true, top: true },
  ]
  while (work.length > 0) {
    const item = work.pop()
    if (item === undefined) break
    const { a, b, outside, top } = item
    if (!check(a, b, outside)) return false
    if (Array.isArray(a) && Array.isArray(b)) {
      a.forEach((element, i) => work.push({ a: element, b: b[i], outside, top: false }))
    } else if (isRecord(a) && isRecord(b)) {
      const keysA = Object.keys(a)
      const keysB = Object.keys(b)
      keysA.forEach((key, i) => {
        const stillOutside = outside && !(top && topSetup && key === 'setup_data')
        work.push({ a: a[key], b: b[keysB[i]], outside: stillOutside, top: false })
      })
    }
  }
  return true
}

/** 道具の公開の名前（3 つのテストのファイルで同じ形） */
interface PerfEvidenceRedactorModule {
  JWT_MARK: string
  SETUP_MARK: string
  MAX_INPUT_BYTES: number
  BINARY_EXTENSIONS: readonly string[]
  redactJwt: (text: string) => { text: string; count: number }
  findResidual: (text: string) => {
    count: number
    positions: { line: number; column: number; offset: number }[]
  }
  redactTree: (
    value: unknown,
  ) =>
    | { ok: true; tree: unknown; replacedCount: number; setupRedactedCount: number }
    | { ok: false; collisionPath: string }
  findUnredactedSetup: (tree: unknown) => { count: number; paths: string[] }
  checkAndWrite: (
    text: string,
    options: { json: boolean },
    outputPath: string,
    io?: object,
  ) => Promise<number>
  main: (argv: string[], io?: object) => Promise<number>
}

const TOOL_PATH = resolve(import.meta.dirname, '..', '..', 'perf', 'redact-k6-evidence.mjs')

let tool: PerfEvidenceRedactorModule
let exportedNames: string[]

beforeAll(async () => {
  const loaded = (await import(/* @vite-ignore */ pathToFileURL(TOOL_PATH).href)) as Record<
    string,
    unknown
  >
  exportedNames = Object.keys(loaded).sort()
  tool = loaded as unknown as PerfEvidenceRedactorModule
})

describe('perf evidence redactor module', () => {
  it('runs in the node environment without the DOM', () => {
    expect(typeof window).toBe('undefined')
  })

  it('exports exactly the planned names and no default export', () => {
    expect(exportedNames).toEqual(
      [
        'BINARY_EXTENSIONS',
        'JWT_MARK',
        'MAX_INPUT_BYTES',
        'SETUP_MARK',
        'checkAndWrite',
        'findResidual',
        'findUnredactedSetup',
        'main',
        'redactJwt',
        'redactTree',
      ].sort(),
    )
    expect(exportedNames).not.toContain('default')
  })

  it('exposes the marks, the input limit and the frozen binary extension list', () => {
    expect(tool.JWT_MARK).toBe('[REDACTED-JWT]')
    expect(tool.SETUP_MARK).toBe('[REDACTED-SETUP]')
    expect(tool.MAX_INPUT_BYTES).toBe(64 * 1024 * 1024)
    expect(tool.BINARY_EXTENSIONS).toEqual([
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
    expect(Object.isFrozen(tool.BINARY_EXTENSIONS)).toBe(true)
  })
})

describe('redactJwt and findResidual (examples)', () => {
  const jwt = makeJwtLike('hdrX9', 'payload-Y_1', 'sigZ')
  const other = makeJwtLike('abc', 'def', 'ghi')

  it('keeps a two-segment value', () => {
    const twoSegments = ['eyJ' + 'abc', 'def'].join('.')
    expect(tool.redactJwt(twoSegments)).toEqual({ text: twoSegments, count: 0 })
  })

  it('keeps a value whose eyJ is preceded by an alphanumeric character', () => {
    const glued = 'z' + jwt
    expect(tool.redactJwt(glued)).toEqual({ text: glued, count: 0 })
    expect(tool.findResidual(glued).count).toBe(0)
  })

  it('replaces every occurrence in one string and keeps the surrounding text', () => {
    const r = tool.redactJwt(`a=${jwt}; b=${other}!`)
    expect(r).toEqual({ text: `a=${MARK}; b=${MARK}!`, count: 2 })
  })

  it('replaces only the token after Bearer', () => {
    expect(tool.redactJwt(`Authorization: Bearer ${jwt}`).text).toBe(
      `Authorization: Bearer ${MARK}`,
    )
  })

  it('keeps a trailing equals sign outside the shape', () => {
    expect(tool.redactJwt(`${jwt}==`).text).toBe(`${MARK}==`)
  })

  it('replaces only the first three segments when more dots follow', () => {
    expect(tool.redactJwt([jwt, 'four', 'five'].join('.')).text).toBe(`${MARK}.four.five`)
  })

  it('does not treat the marks themselves as the shape', () => {
    const marks = `${tool.JWT_MARK} ${tool.SETUP_MARK}`
    expect(tool.redactJwt(marks)).toEqual({ text: marks, count: 0 })
    expect(tool.findResidual(marks).count).toBe(0)
  })

  it('replaces a token inside a 2 MiB string', () => {
    const filler = 'a '.repeat(512 * 1024)
    const r = tool.redactJwt(filler + jwt + ' ' + filler)
    expect(r.count).toBe(1)
    expect(r.text === filler + MARK + ' ' + filler).toBe(true)
  })

  it('reports only the count and positions without returning the value', () => {
    const text = `line one\n  ${jwt} and ${other}\nthird ${jwt}`
    const result = tool.findResidual(text)
    expect(result.count).toBe(3)
    expect(result.positions.map(({ line, column }) => [line, column])).toEqual([
      [2, 3],
      [2, 3 + jwt.length + 5],
      [3, 7],
    ])
    expect(result.positions[0].offset).toBe(text.indexOf(jwt))
    const shown = JSON.stringify(result)
    for (const part of jwt.split('.')) {
      expect(shown).not.toContain(part)
    }
  })

  it('finishes a 1 MiB run of eyJ candidates without dots within the default time limit', () => {
    const candidates = '-eyJ'.repeat(256 * 1024)
    expect(tool.redactJwt(candidates).count).toBe(0)
    expect(tool.findResidual(candidates).count).toBe(0)
    const withShape = candidates + '.' + 'a.b'
    expect(tool.findResidual(withShape).count).toBe(1)
  })
})

describe('redactTree and findUnredactedSetup (examples)', () => {
  const jwt = makeJwtLike('hdrQ', 'bodyQ', 'sigQ')
  const another = makeJwtLike('hdrR', 'bodyR', 'sigR')

  /** redactTree が成功したときの結果を返す */
  function redacted(value: unknown) {
    const r = tool.redactTree(value)
    if (!r.ok) throw new Error('unexpected collision')
    return r
  }

  it('replaces tokens in values, array elements and key names and keeps other types', () => {
    const r = redacted({ [jwt]: [jwt, 1, true, null], plain: `x ${another}`, n: 2.5 })
    expect(r.tree).toEqual({ [MARK]: [MARK, 1, true, null], plain: `x ${MARK}`, n: 2.5 })
    expect(r.replacedCount).toBe(3)
    expect(r.setupRedactedCount).toBe(0)
  })

  it('fails on a key collision and returns only the redacted object path', () => {
    const r = tool.redactTree({ outer: { [jwt]: 1, [another]: 2 } })
    expect(r).toEqual({ ok: false, collisionPath: '$["outer"]' })
  })

  it('fails when a redacted key collides with an existing mark key', () => {
    const r = tool.redactTree([{ [MARK]: 1, [jwt]: 2 }])
    expect(r.ok).toBe(false)
    if (!r.ok) {
      expect(r.collisionPath).toBe('$[0]')
      expect(r.collisionPath).not.toContain('eyJ')
    }
  })

  it('walks ten thousand levels of nesting and a large array without stopping', () => {
    let deep: unknown = jwt
    for (let i = 0; i < 10_000; i++) {
      deep = { level: [deep] }
    }
    const big = Array.from({ length: 200_000 }, (_, i) => (i % 50_000 === 0 ? jwt : i))
    const r = redacted({ deep, big })
    expect(r.replacedCount).toBe(1 + 4)
    let cursor: unknown = (r.tree as Record<string, unknown>).deep
    for (let i = 0; i < 10_000; i++) {
      cursor = ((cursor as Record<string, unknown>).level as unknown[])[0]
    }
    expect(cursor).toBe(MARK)
    expect(((r.tree as Record<string, unknown>).big as unknown[])[1]).toBe(1)
  })

  it('replaces every string under the top-level setup_data and keeps numbers, booleans and null', () => {
    const fakeInvite = 'inv_' + 'Q'.repeat(20)
    const input = {
      setup_data: {
        [jwt]: jwt,
        invites: [fakeInvite, 3, false, null],
        nested: { email: 'a@example.com' },
      },
      state: { note: jwt },
    }
    const r = redacted(input)
    expect(r.tree).toEqual({
      setup_data: {
        [MARK]: '[REDACTED-SETUP]',
        invites: ['[REDACTED-SETUP]', 3, false, null],
        nested: { email: '[REDACTED-SETUP]' },
      },
      state: { note: MARK },
    })
    expect(r.setupRedactedCount).toBe(3)
    expect(r.replacedCount).toBe(2)
  })

  it('replaces setup_data itself when it is a string or an array', () => {
    expect(redacted({ setup_data: 'plain' }).tree).toEqual({ setup_data: '[REDACTED-SETUP]' })
    expect(redacted({ setup_data: ['a', 1] }).tree).toEqual({ setup_data: ['[REDACTED-SETUP]', 1] })
  })

  it('does not treat a deeper setup_data specially', () => {
    expect(redacted({ wrapper: { setup_data: 'plain' } }).tree).toEqual({
      wrapper: { setup_data: 'plain' },
    })
    expect(redacted([{ setup_data: 'plain' }]).tree).toEqual([{ setup_data: 'plain' }])
  })

  it('keeps the JSON.parse key order including integer-like keys', () => {
    const input = JSON.parse('{"b":1,"2":2,"a":3,"1":4}') as unknown
    expect(Object.keys(redacted(input).tree as object)).toEqual(['1', '2', 'b', 'a'])
  })

  it('keeps a __proto__ key as an own property', () => {
    const input = JSON.parse('{"__proto__":{"x":1},"y":2}') as unknown
    const tree = redacted(input).tree as Record<string, unknown>
    expect(Object.keys(tree)).toEqual(['__proto__', 'y'])
    expect(JSON.stringify(tree)).toBe('{"__proto__":{"x":1},"y":2}')
  })

  it('keeps metrics, group names and check names of a k6-like summary', () => {
    const summary = {
      root_group: {
        name: '',
        path: '',
        groups: [{ name: 'login', checks: [{ name: 'status is 200', passes: 10, fails: 0 }] }],
        checks: [],
      },
      metrics: {
        http_reqs: { count: 120, rate: 3.98 },
        'iteration_duration{scenario:loginSuccess}': { 'p(95)': 812.4, max: 1203.1 },
      },
      setup_data: { tokens: [jwt, another], users: 10 },
    }
    const r = redacted(summary)
    const tree = r.tree as typeof summary
    expect(tree.metrics).toEqual(summary.metrics)
    expect(tree.root_group).toEqual(summary.root_group)
    expect(tree.setup_data).toEqual({ tokens: ['[REDACTED-SETUP]', '[REDACTED-SETUP]'], users: 10 })
    expect(tool.findResidual(JSON.stringify(tree)).count).toBe(0)
  })

  it('reports unredacted setup strings by count and path only', () => {
    const tree = {
      setup_data: { a: 'plain-secret', b: ['[REDACTED-SETUP]', 'leak-value'], [jwt]: 'v', n: 1 },
      other: 'outside',
    }
    const result = tool.findUnredactedSetup(tree)
    expect(result.count).toBe(3)
    expect(result.paths).toEqual([
      '$["setup_data"]["a"]',
      '$["setup_data"]["b"][1]',
      `$["setup_data"]["${MARK}"]`,
    ])
    const shown = JSON.stringify(result)
    expect(shown).not.toContain('plain-secret')
    expect(shown).not.toContain('leak-value')
    expect(shown).not.toContain('eyJ')
  })

  it('finds nothing when there is no top-level setup_data or everything is redacted', () => {
    expect(tool.findUnredactedSetup({ wrapper: { setup_data: 'x' } }).count).toBe(0)
    expect(tool.findUnredactedSetup(['x']).count).toBe(0)
    expect(tool.findUnredactedSetup({ setup_data: ['[REDACTED-SETUP]', 1, null] }).count).toBe(0)
    expect(tool.findUnredactedSetup({ setup_data: 'x' })).toEqual({
      count: 1,
      paths: ['$["setup_data"]'],
    })
  })
})

describe('redactor properties (fast-check)', () => {
  it('(1) leaves no shape in the serialized tree', () => {
    assertProperty(
      fc.property(topLevelJson, (input) => {
        const r = tool.redactTree(input)
        if (!r.ok) return true
        const out = JSON.stringify(r.tree) ?? ''
        return tool.findResidual(out).count === 0 && referenceCount(out) === 0
      }),
    )
  })

  it('(2) is idempotent inside and outside setup_data', () => {
    assertProperty(
      fc.property(topLevelJson, (input) => {
        const once = tool.redactTree(input)
        if (!once.ok) return true
        const twice = tool.redactTree(once.tree)
        return twice.ok && JSON.stringify(twice.tree) === JSON.stringify(once.tree)
      }),
    )
  })

  it('(3) keeps values without the shape outside setup_data unchanged', () => {
    assertProperty(
      fc.property(topLevelJson, (input) => {
        const r = tool.redactTree(input)
        if (!r.ok) return true
        return walkPairs(input, r.tree, (a, b, outside) => {
          if (!outside || typeof a !== 'string') return true
          return referenceCount(a) > 0 || a === b
        })
      }),
    )
  })

  it('(4) keeps the key set, key order and value types without collisions', () => {
    assertProperty(
      fc.property(topLevelJson, (input) => {
        const r = tool.redactTree(input)
        fc.pre(r.ok)
        if (!r.ok) return true
        const reread = JSON.parse(JSON.stringify(r.tree) ?? 'null') as unknown
        return walkPairs(input, reread, (a, b) => {
          if (kindOf(a) !== kindOf(b)) return false
          if (isRecord(a) && isRecord(b)) {
            const expected = Object.keys(a).map(referenceRedact)
            return JSON.stringify(Object.keys(b)) === JSON.stringify(expected)
          }
          return !Array.isArray(a) || (a as unknown[]).length === (b as unknown[]).length
        })
      }),
    )
  })

  it('(5) turns every string under setup_data into the setup mark and keeps other primitives', () => {
    assertProperty(
      fc.property(topLevelJson, (input) => {
        fc.pre(isRecord(input) && Object.hasOwn(input, 'setup_data'))
        const r = tool.redactTree(input)
        if (!r.ok || !isRecord(input) || !isRecord(r.tree)) return true
        return walkPairs(input.setup_data, r.tree.setup_data, (a, b) => {
          if (typeof a === 'string') return b === '[REDACTED-SETUP]'
          if (a === null || typeof a !== 'object') return a === b
          return true
        })
      }),
    )
  })

  it('(t1) leaves no shape in redacted text', () => {
    assertProperty(
      fc.property(mixedString, (text) => {
        const out = tool.redactJwt(text).text
        return tool.findResidual(out).count === 0 && referenceCount(out) === 0
      }),
    )
  })

  it('(t2) is idempotent on text character by character', () => {
    assertProperty(
      fc.property(mixedString, (text) => {
        const once = tool.redactJwt(text).text
        return tool.redactJwt(once).text === once
      }),
    )
  })

  it('(t3) keeps text without the shape unchanged character by character', () => {
    assertProperty(
      fc.property(mixedString, (text) => {
        fc.pre(referenceCount(text) === 0)
        return tool.redactJwt(text).text === text
      }),
    )
  })

  it('matches the reference regular expression on short random strings', () => {
    const shortString = fc
      .array(
        fc.oneof(
          fc.string({ unit: fc.constantFrom(...'eyJaZ0-_.= x\n'.split('')), maxLength: 16 }),
          jwtLike,
          nearJwt,
        ),
        { maxLength: 5 },
      )
      .map((pieces) => pieces.join(''))
    assertProperty(
      fc.property(shortString, (text) => {
        const expectedOffsets = [...text.matchAll(REFERENCE_SHAPE)].map((m) => m.index)
        const residual = tool.findResidual(text)
        const redactedText = tool.redactJwt(text)
        return (
          redactedText.text === referenceRedact(text) &&
          redactedText.count === expectedOffsets.length &&
          JSON.stringify(residual.positions.map((p) => p.offset)) ===
            JSON.stringify(expectedOffsets)
        )
      }),
      { numRuns: 1000 },
    )
  })
})
