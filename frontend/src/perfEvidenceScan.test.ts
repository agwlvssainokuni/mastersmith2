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
// perf/redact-k6-evidence.mjs の evidence の検査の口（--scan）の確かめ（security-design.md 8.5節の表の行のすべて）。
// ファイルは各テストで os.tmpdir() の下に作った根（その下に evidence/）に置き、終わりに消す。リポジトリの aidlc/ には置かない。
// JWT の形の値は makeJwtLike() が実行の時に組み立てる（このファイルに完成した JWT の形を書かない）。
import { spawnSync } from 'node:child_process'
import { mkdir, mkdtemp, open, rm, symlink, writeFile } from 'node:fs/promises'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'

interface PerfEvidenceRedactorModule {
  MAX_INPUT_BYTES: number
  main: (argv: string[], io?: object) => Promise<number>
}

const TOOL_PATH = resolve(import.meta.dirname, '..', '..', 'perf', 'redact-k6-evidence.mjs')
const MARK = '[REDACTED-JWT]'

/** JWT の形の値を実行の時に組み立てる */
function makeJwtLike(first: string, second: string, third: string): string {
  return ['eyJ' + first, second, third].join('.')
}

const JWT = makeJwtLike('hdrScan1', 'payloadScan2', 'sigScan3')
const SECRET_PARTS = JWT.split('.')

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
  root = await mkdtemp(join(tmpdir(), 'u4-scan-'))
  evidence = join(root, 'records', 'evidence')
  await mkdir(evidence, { recursive: true })
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

/** 根を調べた終わりのコード */
function scan(io: object = capture()): Promise<number> {
  return tool.main(['--scan', root], io)
}

/** 表示に値の断片が出ていないことを確かめる */
function expectNoValueShown(): void {
  const shown = [...out, ...err].join('\n')
  for (const part of SECRET_PARTS) {
    expect(shown).not.toContain(part)
  }
}

/** 終わりの集計の行 */
function summaryLine(): string {
  return out[out.length - 1] ?? ''
}

describe('evidence scan', () => {
  it('runs in the node environment without the DOM', () => {
    expect(typeof window).toBe('undefined')
  })

  it('passes a clean evidence directory and reports the counts', async () => {
    await writeFile(join(evidence, 'a.md'), 'plain [REDACTED-JWT] text\n')
    await writeFile(join(evidence, 'b.json'), '{"token":"[REDACTED-SETUP]"}\n')
    expect(await scan()).toBe(0)
    expect(summaryLine()).toBe(
      'evidence の検査: 調べたファイル 2 件（うちバイナリとして読んだもの 0 件）、リンク 0 件、検出 0 件（0 ファイル）、入力の誤り 0 件',
    )
    expect(err).toEqual([])
  })

  it.each(['found.md', 'found.log', 'found.csv', 'no-extension'])(
    'exits with 4 for the shape in %s and shows only the path and position',
    async (name) => {
      await writeFile(join(evidence, name), `header\n  value=${JWT}\n`)
      expect(await scan()).toBe(4)
      expect(err).toEqual([`JWT の形が 1 件あります: records/evidence/${name}`, '  行 2 桁 9'])
      expectNoValueShown()
    },
  )

  it('exits with 4 for the shape in a file name and shows the path with the mark', async () => {
    await writeFile(join(evidence, `${JWT}.txt`), 'plain\n')
    expect(await scan()).toBe(4)
    expect(err).toEqual([`ファイルのパスに JWT の形が 1 件あります: records/evidence/${MARK}.txt`])
    expectNoValueShown()
  })

  it.each([
    'a.json',
    'a.md',
    'a.txt',
    'a.log',
    'a.tsv',
    'a.csv',
    'a.yaml',
    'no-extension',
    '.gitkeep',
  ])('exits with 2 for NUL in %s and shows only the path', async (name) => {
    await writeFile(join(evidence, name), Buffer.from('secret-like-text\u0000more'))
    expect(await scan()).toBe(2)
    expect(err).toEqual([
      `NUL を含むファイルです（バイナリの拡張子の一覧に無いため、記録に置けません）: records/evidence/${name}`,
    ])
    expect([...out, ...err].join('\n')).not.toContain('secret-like-text')
  })

  it.each(['shot.png', 'SHOT.PNG', 'trace.zip', 'logs.tar.gz', 'report.pdf'])(
    'reads NUL-containing %s as binary and passes when no shape is inside',
    async (name) => {
      await writeFile(join(evidence, name), Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x00, 0xff, 0x41]))
      expect(await scan()).toBe(0)
      expect(out).toContain(
        `バイナリとして読んだ（JWT の形だけを調べた）: records/evidence/${name}`,
      )
      expect(summaryLine()).toContain('うちバイナリとして読んだもの 1 件')
    },
  )

  it('exits with 4 for an ASCII shape inside a NUL-containing png and shows the byte position', async () => {
    const prefix = Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x00, 0xc3, 0x20])
    await writeFile(
      join(evidence, 'shot.png'),
      Buffer.concat([prefix, Buffer.from(JWT), Buffer.from([0])]),
    )
    expect(await scan()).toBe(4)
    expect(err).toEqual([
      'JWT の形が 1 件あります（バイナリ）: records/evidence/shot.png',
      `  バイトの位置 ${prefix.length}`,
    ])
    expectNoValueShown()
  })

  it('checks a png without NUL as text', async () => {
    await writeFile(join(evidence, 'fake.png'), `${JWT}\n`)
    expect(await scan()).toBe(4)
    expect(err[1]).toBe('  行 1 桁 1')
  })

  it('exits with 2 for a file one byte over the limit and shows only the path and size', async () => {
    const handle = await open(join(evidence, 'huge.log'), 'w')
    await handle.truncate(tool.MAX_INPUT_BYTES + 1)
    await handle.close()
    expect(await scan()).toBe(2)
    expect(err).toEqual([
      `上限（${tool.MAX_INPUT_BYTES} バイト）を超えるファイルです（${tool.MAX_INPUT_BYTES + 1} バイト）: records/evidence/huge.log`,
    ])
  })

  it('does not follow a symbolic link and shows its path', async () => {
    const outside = join(root, 'outside')
    await mkdir(outside)
    await writeFile(join(outside, 'secret.log'), `${JWT}\n`)
    await symlink(outside, join(evidence, 'linked-dir'))
    await symlink(join(outside, 'secret.log'), join(evidence, 'linked.log'))
    expect(await scan()).toBe(0)
    expect(out).toContain('リンク（たどらない）: records/evidence/linked-dir')
    expect(out).toContain('リンク（たどらない）: records/evidence/linked.log')
    expect(summaryLine()).toContain('リンク 2 件')
  })

  it('ignores files outside any evidence directory', async () => {
    await writeFile(join(root, 'records', 'notes.md'), `${JWT}\n`)
    expect(await scan()).toBe(0)
    expect(summaryLine()).toContain('調べたファイル 0 件')
  })

  it('exits with 4 and shows both counts when a detection and a NUL file coexist', async () => {
    await writeFile(join(evidence, 'found.log'), `${JWT}\n`)
    await writeFile(join(evidence, 'broken.txt'), Buffer.from('x\u0000y'))
    expect(await scan()).toBe(4)
    expect(summaryLine()).toContain('検出 1 件（1 ファイル）、入力の誤り 1 件')
  })

  it.each([
    ['a missing root', ['--scan', '/nonexistent-u4-root']],
    ['a file as the root', ['--scan', TOOL_PATH]],
    ['no root argument', ['--scan']],
    ['two root arguments', ['--scan', '.', '.']],
  ])('exits with 1 for %s', async (_label, argv) => {
    expect(await tool.main(argv, capture())).toBe(1)
  })

  it('shows at most ten positions per file and the remaining count', async () => {
    await writeFile(join(evidence, 'many.log'), Array.from({ length: 11 }, () => JWT).join('\n'))
    expect(await scan()).toBe(4)
    expect(err.filter((line) => line.startsWith('  行 '))).toHaveLength(10)
    expect(err).toContain('  ほか 1 件')
    expect(summaryLine()).toContain('検出 11 件（1 ファイル）')
  })

  it('also checks dot-prefixed temporary files', async () => {
    await writeFile(join(evidence, '.out.json.4242.0a1b2c3d.tmp'), `{"t":"${JWT}"}`)
    expect(await scan()).toBe(4)
    expect(err[0]).toBe('JWT の形が 1 件あります: records/evidence/.out.json.4242.0a1b2c3d.tmp')
  })

  it('treats the root itself as evidence when it is named evidence', async () => {
    await writeFile(join(evidence, 'found.log'), `${JWT}\n`)
    expect(await tool.main(['--scan', evidence], capture())).toBe(4)
    expect(err[0]).toBe('JWT の形が 1 件あります: found.log')
  })

  it('counts unreadable files and directories as input errors with the code only', async () => {
    await writeFile(join(evidence, 'locked.log'), 'plain\n')
    await mkdir(join(evidence, 'closed'))
    const failing = (code: string) => () => Promise.reject(Object.assign(new Error(JWT), { code }))
    const realReaddir = (await import('node:fs/promises')).readdir
    const io = capture({
      readFile: failing('EACCES'),
      readdir: (dir: string) =>
        dir.endsWith('closed') ? failing('EPERM')() : realReaddir(dir, { withFileTypes: true }),
    })
    expect(await scan(io)).toBe(2)
    expect(err).toEqual([
      '読めないファイルです（EACCES）: records/evidence/locked.log',
      '読めないファイルです（EPERM）: records/evidence/closed',
    ])
    expectNoValueShown()
  })

  it('returns the same exit code from a real process', async () => {
    await writeFile(join(evidence, 'found.log'), `${JWT}\n`)
    const run = spawnSync(process.execPath, [TOOL_PATH, '--scan', root], { encoding: 'utf8' })
    expect(run.status).toBe(await scan())
    expect(run.status).toBe(4)
    for (const part of SECRET_PARTS) {
      expect(run.stdout + run.stderr).not.toContain(part)
    }
  })
})
