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
// scripts/check-license-header.mjs（ライセンスヘッダーの検査）の純粋な関数の確かめ。
// U6 で `.svg` を HTML 形の対象に足したことと、集める関数を export したことを守る。
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'

interface Expected {
  block: string
  html: string
}
interface HeaderModule {
  SKIP_DIRS: Set<string>
  buildExpectedHeaders: (lines: string[]) => Expected
  checkContent: (fileName: string, content: string, expected: Expected) => string | null
  collectFiles: (dir: string, accepts: (ext: string) => boolean) => string[]
}

const SCRIPT = resolve(import.meta.dirname, '../scripts/check-license-header.mjs')
const TEMPLATE = resolve(import.meta.dirname, '../../config/license-header.txt')

let tool: HeaderModule
let expected: Expected
let work: string

beforeAll(async () => {
  tool = (await import(/* @vite-ignore */ pathToFileURL(SCRIPT).href)) as unknown as HeaderModule
  const lines = readFileSync(TEMPLATE, 'utf8')
    .replace(/\r\n/g, '\n')
    .replace(/\n+$/, '')
    .split('\n')
  expected = tool.buildExpectedHeaders(lines)
  work = mkdtempSync(join(tmpdir(), 'license-header-'))
})

afterAll(() => {
  rmSync(work, { recursive: true, force: true })
})

const BODY = '<svg xmlns="http://www.w3.org/2000/svg"></svg>\n'

describe('checkContent for .svg', () => {
  it('accepts an svg that starts with the html-style header', () => {
    expect(tool.checkContent('a.svg', `${expected.html}\n${BODY}`, expected)).toBeNull()
  })

  it('accepts an svg with CRLF line breaks', () => {
    const crlf = `${expected.html}\n${BODY}`.replace(/\n/g, '\r\n')
    expect(tool.checkContent('a.svg', crlf, expected)).toBeNull()
  })

  it('rejects an svg without the header', () => {
    expect(tool.checkContent('a.svg', BODY, expected)).not.toBeNull()
  })

  it('rejects an svg with the block-comment style header', () => {
    expect(tool.checkContent('a.svg', `${expected.block}\n${BODY}`, expected)).not.toBeNull()
  })

  it('rejects an svg that starts with an XML declaration', () => {
    const content = `<?xml version="1.0"?>\n${expected.html}\n${BODY}`
    expect(tool.checkContent('a.svg', content, expected)).not.toBeNull()
  })
})

describe('checkContent for other extensions (unchanged)', () => {
  it('still judges .html by the html-style header', () => {
    expect(tool.checkContent('a.html', `${expected.html}\n<html></html>\n`, expected)).toBeNull()
    expect(tool.checkContent('a.html', '<html></html>\n', expected)).not.toBeNull()
  })

  it('still judges .ts by the block-comment header and rejects the javadoc style', () => {
    expect(tool.checkContent('a.ts', `${expected.block}\nexport {}\n`, expected)).toBeNull()
    expect(tool.checkContent('a.ts', 'export {}\n', expected)).not.toBeNull()
    expect(tool.checkContent('a.ts', '/**\n * x\n */\n', expected)).toContain('/**')
  })

  it('does not judge unknown extensions', () => {
    expect(tool.checkContent('a.png', 'x', expected)).toBeNull()
  })
})

describe('collectFiles', () => {
  it('finds nested .svg files and skips node_modules, dist and dot directories', () => {
    for (const dir of ['e2e/nested', 'node_modules/pkg', 'dist', '.hidden', 'coverage']) {
      mkdirSync(join(work, dir), { recursive: true })
    }
    writeFileSync(join(work, 'e2e/nested/found.svg'), BODY)
    writeFileSync(join(work, 'top.svg'), BODY)
    writeFileSync(join(work, 'node_modules/pkg/skip.svg'), BODY)
    writeFileSync(join(work, 'dist/skip.svg'), BODY)
    writeFileSync(join(work, '.hidden/skip.svg'), BODY)
    writeFileSync(join(work, 'coverage/skip.svg'), BODY)
    writeFileSync(join(work, 'e2e/nested/not-svg.txt'), BODY)

    const found = tool
      .collectFiles(work, (ext) => ext === '.svg')
      .map((f) => f.slice(work.length + 1))
      .sort()

    expect(found).toEqual(['e2e/nested/found.svg', 'top.svg'])
  })

  it('exports the skip list that the svg content check shares', () => {
    expect(tool.SKIP_DIRS.has('node_modules')).toBe(true)
    expect(tool.SKIP_DIRS.has('dist')).toBe(true)
  })

  it('does not run the check when imported', () => {
    // import 済み（beforeAll）でも process.exit が呼ばれていないこと（呼ばれればテストが止まる）
    expect(typeof tool.collectFiles).toBe('function')
  })
})
