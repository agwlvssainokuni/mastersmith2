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
// perf/redact-k6-evidence.mjs の JSON の口とテキストの口（終わりのコード 0〜5、出力と一時ファイルの前後、
// 表示に値が出ないこと、上限、中断の片付け、本物のプロセスの終わりのコード）の確かめ。
// ファイルは各テストで os.tmpdir() の下に作った一時のディレクトリ（その下に evidence/）に置き、終わりに消す。
// JWT の形の値は makeJwtLike() が実行の時に組み立てる（このファイルに完成した JWT の形を書かない）。
import { spawnSync } from 'node:child_process'
import { mkdir, mkdtemp, open, readdir, readFile, rm, symlink, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'

interface PerfEvidenceRedactorModule {
  JWT_MARK: string
  SETUP_MARK: string
  MAX_INPUT_BYTES: number
  redactJwt: (text: string) => { text: string; count: number }
  checkAndWrite: (
    text: string,
    options: { json: boolean },
    outputPath: string,
    io?: object,
  ) => Promise<number>
  main: (argv: string[], io?: object) => Promise<number>
}

const TOOL_PATH = resolve(import.meta.dirname, '..', '..', 'perf', 'redact-k6-evidence.mjs')
const MARK = '[REDACTED-JWT]'

/** JWT の形の値を実行の時に組み立てる */
function makeJwtLike(first: string, second: string, third: string): string {
  return ['eyJ' + first, second, third].join('.')
}

const JWT = makeJwtLike('hdrCli7', 'payloadCli_8', 'sigCli-9')
const OTHER_JWT = makeJwtLike('hdrOther', 'payloadOther', 'sigOther')
/** 招待のトークンに似た架空の値（JWT の形ではない） */
const FAKE_INVITE = 'inv_' + 'K'.repeat(24)
/** 表示に出てはいけない断片 */
const SECRET_PARTS = [...JWT.split('.'), ...OTHER_JWT.split('.'), FAKE_INVITE]

let tool: PerfEvidenceRedactorModule
let root: string
let evidence: string
let out: string[]
let err: string[]

beforeAll(async () => {
  tool = (await import(
    /* @vite-ignore */ pathToFileURL(TOOL_PATH).href
  )) as unknown as PerfEvidenceRedactorModule
})

beforeEach(async () => {
  root = await mkdtemp(join(tmpdir(), 'u4-cli-'))
  evidence = join(root, 'evidence')
  await mkdir(evidence)
  out = []
  err = []
})

afterEach(async () => {
  await rm(root, { recursive: true, force: true })
})

/** 表示を捕まえる io（ほかの口は本物） */
function capture(extra: object = {}): object {
  return {
    stdout: (line: string) => out.push(line),
    stderr: (line: string) => err.push(line),
    ...extra,
  }
}

/** 表示のすべて */
function shownText(): string {
  return [...out, ...err].join('\n')
}

/** evidence/ に残った一時ファイルの名前 */
async function tempFiles(): Promise<string[]> {
  return (await readdir(evidence)).filter((name) => name.endsWith('.tmp'))
}

/** ファイルの中身（無ければ null） */
async function contentOrNull(file: string): Promise<Buffer | null> {
  try {
    return await readFile(file)
  } catch {
    return null
  }
}

/** k6 の summary json に似た架空の入力 */
function summaryJson(): string {
  return JSON.stringify({
    root_group: { name: '', checks: [{ name: 'status is 200', passes: 3, fails: 0 }] },
    metrics: { http_reqs: { count: 3, rate: 1.5 }, [`note ${JWT}`]: { value: 1 } },
    setup_data: { token: JWT, invites: [FAKE_INVITE], users: 3 },
  })
}

describe('json entry', () => {
  it('runs in the node environment without the DOM', () => {
    expect(typeof window).toBe('undefined')
  })

  it('writes 2-space indented LF JSON with one trailing newline and no BOM', async () => {
    const input = join(root, 'summary.json')
    const output = join(evidence, 'summary.json')
    await writeFile(input, summaryJson())
    expect(await tool.main([input, output], capture())).toBe(0)
    const bytes = await readFile(output)
    const text = bytes.toString('utf8')
    expect(bytes[0]).not.toBe(0xef)
    expect(text.endsWith('}\n')).toBe(true)
    expect(text.includes('\r')).toBe(false)
    expect(text.split('\n')[1].startsWith('  "root_group"')).toBe(true)
    const tree = JSON.parse(text) as Record<string, Record<string, unknown>>
    expect(tree.setup_data).toEqual({
      token: '[REDACTED-SETUP]',
      invites: ['[REDACTED-SETUP]'],
      users: 3,
    })
    expect(Object.keys(tree.metrics)).toEqual(['http_reqs', `note ${MARK}`])
    expect(out).toEqual([
      `置き換えた JWT の形: 1 件、[REDACTED-SETUP] にした値: 2 件、出力: ${output}`,
    ])
    expect(err).toEqual([])
    expect(await tempFiles()).toEqual([])
  })

  it('writes the same bytes as checkAndWrite would for the redacted text', async () => {
    const input = join(root, 'summary.json')
    await writeFile(input, summaryJson())
    const viaMain = join(evidence, 'a.json')
    expect(await tool.main([input, viaMain], capture())).toBe(0)
    const text = (await readFile(viaMain)).toString('utf8')
    const direct = join(evidence, 'b.json')
    expect(await tool.checkAndWrite(text, { json: true }, direct, capture())).toBe(0)
    expect((await readFile(direct)).equals(await readFile(viaMain))).toBe(true)
  })

  it.each([
    ['one argument', (input: string) => [input]],
    ['three arguments', (input: string, output: string) => [input, output, output]],
    ['an output outside evidence/', (input: string) => [input, join(root, 'out.json')]],
    ['a missing output parent', (input: string) => [input, join(evidence, 'missing', 'out.json')]],
    ['the same input and output', (_input: string, output: string) => [output, output]],
  ])('exits with 1 for %s and writes nothing', async (_label, args) => {
    const input = join(root, 'summary.json')
    const output = join(evidence, 'summary.json')
    await writeFile(input, summaryJson())
    const argv = args(input, output)
    if (argv[0] === output) {
      await writeFile(output, summaryJson())
    }
    const before = await contentOrNull(output)
    expect(await tool.main(argv, capture())).toBe(1)
    expect(await contentOrNull(output)).toEqual(before)
    expect(await contentOrNull(join(root, 'out.json'))).toBeNull()
    expect(await tempFiles()).toEqual([])
  })

  it('exits with 1 when a symbolic link named evidence points outside', async () => {
    const outside = join(root, 'outside')
    await mkdir(outside)
    const linked = join(root, 'linked')
    await mkdir(linked)
    await symlink(outside, join(linked, 'evidence'))
    const input = join(root, 'summary.json')
    await writeFile(input, summaryJson())
    expect(await tool.main([input, join(linked, 'evidence', 'out.json')], capture())).toBe(1)
    expect(await readdir(outside)).toEqual([])
  })

  it.each([
    ['a missing input', null],
    ['an empty input', Buffer.from('')],
    ['a whitespace-only input', Buffer.from(' \n\t\r\n')],
    ['an input that is not JSON', Buffer.from(`{"token": ${JWT}}`)],
    ['an input that is not UTF-8', Buffer.from([0x7b, 0xff, 0xfe, 0x7d])],
    ['a too deeply nested input', Buffer.from('['.repeat(200_000) + ']'.repeat(200_000))],
  ])('exits with 2 for %s and writes nothing', async (_label, content) => {
    const input = join(root, 'input.json')
    if (content !== null) {
      await writeFile(input, content)
    }
    const output = join(evidence, 'out.json')
    expect(await tool.main([input, output], capture())).toBe(2)
    expect(await contentOrNull(output)).toBeNull()
    expect(await tempFiles()).toEqual([])
    expect(shownText()).not.toContain('eyJ')
  })

  it('shows only the position for a JSON syntax error', async () => {
    const input = join(root, 'input.json')
    await writeFile(input, `{"a": 1, "b": "${FAKE_INVITE}" "c"}`)
    expect(await tool.main([input, join(evidence, 'out.json')], capture())).toBe(2)
    expect(err).toHaveLength(1)
    expect(err[0]).toMatch(/^入力が JSON として読めません（位置 \d+）: /)
    expect(err[0]).not.toContain(FAKE_INVITE)
  })

  it('shows no position when the runtime message has none', async () => {
    const input = join(root, 'input.json')
    await writeFile(input, `{"a": 1, "b": ${FAKE_INVITE}}`)
    expect(await tool.main([input, join(evidence, 'out.json')], capture())).toBe(2)
    expect(err).toEqual([`入力が JSON として読めません: ${input}`])
  })

  it('exits with 2 for an input one byte over the limit without reading it', async () => {
    const input = join(root, 'sparse.json')
    const handle = await open(input, 'w')
    await handle.truncate(tool.MAX_INPUT_BYTES + 1)
    await handle.close()
    const output = join(evidence, 'out.json')
    expect(await tool.main([input, output], capture())).toBe(2)
    expect(err[0]).toContain(`${tool.MAX_INPUT_BYTES + 1} バイト`)
    expect(await contentOrNull(output)).toBeNull()
  })

  it('exits with 3 on a key collision and shows only the object path', async () => {
    const input = join(root, 'input.json')
    await writeFile(input, JSON.stringify({ outer: { [JWT]: 1, [OTHER_JWT]: 2 } }))
    const output = join(evidence, 'out.json')
    expect(await tool.main([input, output], capture())).toBe(3)
    expect(err.join('\n')).toContain('$["outer"]')
    expect(await contentOrNull(output)).toBeNull()
    expect(await tempFiles()).toEqual([])
  })

  it('exits with 5 when the rename fails and keeps the previous output', async () => {
    const input = join(root, 'summary.json')
    const output = join(evidence, 'summary.json')
    await writeFile(input, summaryJson())
    await writeFile(output, 'previous')
    const failingRename = () => Promise.reject(Object.assign(new Error('x'), { code: 'EXDEV' }))
    expect(await tool.main([input, output], capture({ rename: failingRename }))).toBe(5)
    expect((await readFile(output)).toString()).toBe('previous')
    expect(await tempFiles()).toEqual([])
    expect(err.join('\n')).toContain('EXDEV')
  })
})

describe('checkAndWrite (independent residual check)', () => {
  it('exits with 4 for unredacted text without creating the output or a temporary file', async () => {
    const output = join(evidence, 'out.log')
    expect(await tool.checkAndWrite(`line\n  ${JWT}\n`, { json: false }, output, capture())).toBe(4)
    expect(await contentOrNull(output)).toBeNull()
    expect(await tempFiles()).toEqual([])
    expect(err).toContain('  行 2 桁 3')
  })

  it('exits with 4 for an unredacted setup_data string and keeps the previous output', async () => {
    const output = join(evidence, 'out.json')
    await writeFile(output, 'previous')
    const text = JSON.stringify({ setup_data: { invite: FAKE_INVITE } }, null, 2) + '\n'
    expect(await tool.checkAndWrite(text, { json: true }, output, capture())).toBe(4)
    expect((await readFile(output)).toString()).toBe('previous')
    expect(await tempFiles()).toEqual([])
    expect(err.join('\n')).toContain('$["setup_data"]["invite"]')
  })

  it('exits with 4 when the JSON text cannot be read back', async () => {
    const output = join(evidence, 'out.json')
    expect(await tool.checkAndWrite('not json', { json: true }, output, capture())).toBe(4)
    expect(await contentOrNull(output)).toBeNull()
  })

  it('shows at most ten positions and the remaining count', async () => {
    const output = join(evidence, 'out.log')
    const text = Array.from({ length: 12 }, () => JWT).join('\n')
    expect(await tool.checkAndWrite(text, { json: false }, output, capture())).toBe(4)
    expect(err.filter((line) => line.startsWith('  行 '))).toHaveLength(10)
    expect(err).toContain('  ほか 2 件')
  })
})

describe('text entry', () => {
  it('keeps CRLF, a missing trailing newline and a leading BOM while replacing tokens', async () => {
    const input = join(root, 'k6.log')
    const output = join(evidence, 'k6.log')
    const body = `﻿start\r\nAuthorization: Bearer ${JWT} and ${OTHER_JWT}\r\nlast line`
    await writeFile(input, body)
    expect(await tool.main(['--text', input, output], capture())).toBe(0)
    const written = (await readFile(output)).toString('utf8')
    expect(written).toBe(`﻿start\r\nAuthorization: Bearer ${MARK} and ${MARK}\r\nlast line`)
    expect(out).toEqual([
      `置き換えた JWT の形: 2 件、[REDACTED-SETUP] にした値: 0 件、出力: ${output}`,
    ])
  })

  it('keeps LF and the trailing newline', async () => {
    const input = join(root, 'k6.log')
    const output = join(evidence, 'k6.txt')
    await writeFile(input, `a\n${JWT}\n\n`)
    expect(await tool.main(['--text', input, output], capture())).toBe(0)
    expect((await readFile(output)).toString('utf8')).toBe(`a\n${MARK}\n\n`)
  })

  it('uses the same replacement as redactJwt', async () => {
    const body = `x=${JWT}; y=z${OTHER_JWT}; w=${OTHER_JWT}=\n`
    const input = join(root, 'k6.log')
    const output = join(evidence, 'k6.log')
    await writeFile(input, body)
    expect(await tool.main(['--text', input, output], capture())).toBe(0)
    expect((await readFile(output)).toString('utf8')).toBe(tool.redactJwt(body).text)
  })

  it.each([
    [
      'a missing output argument',
      (input: string, output: string) => ['--text', input, output].slice(0, 2),
    ],
    ['an extra argument', (input: string, output: string) => ['--text', input, output, output]],
    ['an output outside evidence/', (input: string) => ['--text', input, join(root, 'out.log')]],
    ['a .json output', (input: string) => ['--text', input, join(evidence, 'out.json')]],
    ['a .JSON output', (input: string) => ['--text', input, join(evidence, 'out.JSON')]],
  ])('exits with 1 for %s', async (_label, args) => {
    const input = join(root, 'k6.log')
    await writeFile(input, `token ${JWT}\n`)
    expect(await tool.main(args(input, join(evidence, 'out.log')), capture())).toBe(1)
    expect(await readdir(evidence)).toEqual([])
    expect(await contentOrNull(join(root, 'out.log'))).toBeNull()
  })

  it('exits with 1 for a summary json passed to the text entry', async () => {
    const input = join(root, 'summary.json')
    await writeFile(input, summaryJson())
    expect(await tool.main(['--text', input, join(evidence, 'summary.log')], capture())).toBe(1)
    expect(await readdir(evidence)).toEqual([])
  })

  it.each([
    ['an empty input', Buffer.from('')],
    ['a whitespace-only input', Buffer.from('  \r\n\t')],
    ['an input that is not UTF-8', Buffer.from([0x61, 0xc3, 0x28])],
    ['an input with NUL', Buffer.from(`ok\u0000${JWT}`)],
  ])('exits with 2 for %s', async (_label, content) => {
    const input = join(root, 'k6.log')
    await writeFile(input, content)
    expect(await tool.main(['--text', input, join(evidence, 'out.log')], capture())).toBe(2)
    expect(await readdir(evidence)).toEqual([])
  })

  it('exits with 5 when the rename fails', async () => {
    const input = join(root, 'k6.log')
    const output = join(evidence, 'out.log')
    await writeFile(input, `token ${JWT}\n`)
    const failingRename = () => Promise.reject(Object.assign(new Error('x'), { code: 'EACCES' }))
    expect(await tool.main(['--text', input, output], capture({ rename: failingRename }))).toBe(5)
    expect(await readdir(evidence)).toEqual([])
  })

  it('accepts an input of exactly the limit', async () => {
    const input = join(root, 'big.log')
    const output = join(evidence, 'big.log')
    await writeFile(input, Buffer.alloc(tool.MAX_INPUT_BYTES, 'a'))
    expect(await tool.main(['--text', input, output], capture())).toBe(0)
    expect((await readFile(output)).length).toBe(tool.MAX_INPUT_BYTES)
  })
})

describe('what the tool shows', () => {
  it('never shows built values, their segments or the fake invite in any outcome', async () => {
    const summary = join(root, 'summary.json')
    await writeFile(summary, summaryJson())
    const notJson = join(root, 'broken.json')
    await writeFile(notJson, `{"setup_data": "${FAKE_INVITE}", "t": ${JWT}`)
    const colliding = join(root, 'collide.json')
    await writeFile(colliding, JSON.stringify({ [JWT]: 1, [OTHER_JWT]: 2 }))
    const failingRename = () => Promise.reject(Object.assign(new Error(JWT), { code: 'EIO' }))
    const outcomes = [
      await tool.main([summary, join(evidence, 'ok.json')], capture()),
      await tool.main([summary, join(root, JWT, 'x.json')], capture()),
      await tool.main([notJson, join(evidence, 'x.json')], capture()),
      await tool.main([colliding, join(evidence, 'y.json')], capture()),
      await tool.checkAndWrite(
        `${JWT} ${OTHER_JWT}`,
        { json: false },
        join(evidence, 'z.log'),
        capture(),
      ),
      await tool.main([summary, join(evidence, 'w.json')], capture({ rename: failingRename })),
    ]
    expect(outcomes).toEqual([0, 1, 2, 3, 4, 5])
    const shown = shownText()
    for (const part of SECRET_PARTS) {
      expect(shown).not.toContain(part)
    }
  })

  it('shows an output path containing the shape with the mark', async () => {
    const input = join(root, 'k6.log')
    await writeFile(input, 'plain line\n')
    const output = join(evidence, `${JWT}.log`)
    expect(await tool.main(['--text', input, output], capture())).toBe(0)
    expect(out[0]).toContain(`${MARK}.log`)
    expect(shownText()).not.toContain('eyJ')
  })

  it('shows the unexpected error name only', async () => {
    const input = join(root, 'k6.log')
    await writeFile(input, 'plain\n')
    const brokenStat = () => {
      throw new TypeError(JWT)
    }
    expect(
      await tool.main(['--text', input, join(evidence, 'o.log')], capture({ stat: brokenStat })),
    ).toBe(2)
    expect(err).toEqual(['想定外の誤りで止まりました（種類: TypeError）。'])
  })
})

describe('interruption', () => {
  it('removes the temporary file when the registered cleanup runs', async () => {
    const input = join(root, 'k6.log')
    const output = join(evidence, 'out.log')
    await writeFile(input, `token ${JWT}\n`)
    let cleanup: (() => Promise<void>) | null = null
    let tempsAtSignal: string[] = []
    let unregistered = false
    const io = capture({
      onSignal: (handler: () => Promise<void>) => {
        cleanup = handler
        return () => {
          unregistered = true
        }
      },
      rename: async () => {
        expect(await tempFiles()).toHaveLength(1)
        if (cleanup === null) throw new Error('cleanup was not registered')
        await cleanup()
        tempsAtSignal = await tempFiles()
        throw Object.assign(new Error('interrupted'), { code: 'EINTR' })
      },
    })
    expect(await tool.main(['--text', input, output], io)).toBe(5)
    expect(tempsAtSignal).toEqual([])
    expect(await readdir(evidence)).toEqual([])
    expect(unregistered).toBe(true)
  })
})

describe('real process exit codes', () => {
  it('returns the same exit code as main for the json entry', async () => {
    const input = join(root, 'summary.json')
    await writeFile(input, summaryJson())
    const run = spawnSync(process.execPath, [TOOL_PATH, input, join(evidence, 'child.json')], {
      encoding: 'utf8',
    })
    expect(run.status).toBe(await tool.main([input, join(evidence, 'main.json')], capture()))
    expect(run.status).toBe(0)
    expect(run.stdout).toContain('[REDACTED-SETUP] にした値: 2 件')
  })

  it('returns the same exit code as main for the text entry', async () => {
    const input = join(root, 'k6.log')
    await writeFile(input, `token ${JWT}\n`)
    const run = spawnSync(process.execPath, [TOOL_PATH, '--text', input], { encoding: 'utf8' })
    expect(run.status).toBe(await tool.main(['--text', input], capture()))
    expect(run.status).toBe(1)
    expect(run.stderr).not.toContain('eyJ')
  })
})
