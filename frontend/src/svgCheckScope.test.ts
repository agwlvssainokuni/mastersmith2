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
// ライセンスヘッダーの検査と SVG の中身の検査が、同じ範囲の .svg を見ること（U6 の SD7.2、SD1.9）。
// 片方だけが見ない状態（例: e2e/ の下の .svg がヘッダーの検査にだけ見つかる）を作らない。
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'
import { goodSvg, loadSvgTool, type SvgSafetyModule } from './svgFixtures'

interface HeaderModule {
  collectFiles: (dir: string, accepts: (ext: string) => boolean) => string[]
}

let svgTool: SvgSafetyModule
let headerTool: HeaderModule
let work: string

beforeAll(async () => {
  svgTool = await loadSvgTool()
  const url = pathToFileURL(
    resolve(import.meta.dirname, '../scripts/check-license-header.mjs'),
  ).href
  headerTool = (await import(/* @vite-ignore */ url)) as unknown as HeaderModule
  work = mkdtempSync(join(tmpdir(), 'svg-scope-'))
  for (const dir of [
    'public',
    'src/shared',
    'e2e/support',
    'node_modules/x',
    'dist',
    'coverage',
    '.cache',
  ]) {
    mkdirSync(join(work, dir), { recursive: true })
  }
  for (const file of ['public/a.svg', 'src/shared/b.svg', 'e2e/support/c.svg']) {
    writeFileSync(join(work, file), goodSvg())
  }
  for (const file of ['node_modules/x/d.svg', 'dist/e.svg', 'coverage/f.svg', '.cache/g.svg']) {
    writeFileSync(join(work, file), goodSvg())
  }
})

afterAll(() => {
  rmSync(work, { recursive: true, force: true })
})

const relative = (files: string[]) => files.map((f) => f.slice(work.length + 1)).sort()

describe('scope of the svg checks', () => {
  it('finds svg files under public, src and e2e with the content check collector', () => {
    expect(relative(svgTool.collectSvgFiles(work))).toEqual([
      'e2e/support/c.svg',
      'public/a.svg',
      'src/shared/b.svg',
    ])
  })

  it('makes the header check and the content check see exactly the same files', () => {
    const header = relative(headerTool.collectFiles(work, (ext) => ext === '.svg'))
    const content = relative(svgTool.collectSvgFiles(work))
    expect(content).toEqual(header)
    expect(content).toContain('e2e/support/c.svg')
  })

  it('skips node_modules, dist, coverage and dot directories in both', () => {
    const found = relative(svgTool.collectSvgFiles(work)).join(' ')
    for (const skipped of ['node_modules', 'dist/', 'coverage', '.cache']) {
      expect(found).not.toContain(skipped)
    }
  })
})
