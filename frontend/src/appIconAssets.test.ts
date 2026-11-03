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
// 実際の素材のファイル（frontend/public/ と index.html）を読んで確かめる（U6 の SD7.3・SD7.4 の実ファイル側）。
// 素材の名前の一覧は、ここと docs/app-icon.md の 2 か所だけに書く（Gradle には書かない）。
import { readFileSync, readdirSync } from 'node:fs'
import { resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { beforeAll, describe, expect, it } from 'vitest'
import { loadSvgTool, type SvgSafetyModule } from './svgFixtures'

interface BinaryModule {
  MAX_ICO_BYTES: number
  inspectIco: (
    b: Buffer,
    o?: { limit?: number },
  ) =>
    | { ok: true; images: { width: number; height: number; size: number }[] }
    | { ok: false; code: string }
  inspectPng: (b: Buffer) => { ok: boolean }
}

const FRONTEND = resolve(import.meta.dirname, '..')
const PUBLIC = resolve(FRONTEND, 'public')
/** public/ に置いてよい素材の名前の一覧（これ以外のファイルは公開されるため置かない） */
const ASSET_NAMES = ['app-icon.svg', 'favicon.ico', 'favicon.svg']
const SVG_NAMES = ASSET_NAMES.filter((n) => n.endsWith('.svg'))

let svg: SvgSafetyModule
let bin: BinaryModule

const read = (name: string) => readFileSync(resolve(PUBLIC, name))
const text = (name: string) => read(name).toString('utf8')
/** SVG の path の d の一覧（出てくる順） */
const pathData = (name: string) => [...text(name).matchAll(/<path d="([^"]*)"/g)].map((m) => m[1])

beforeAll(async () => {
  svg = await loadSvgTool()
  bin = (await import(
    /* @vite-ignore */ pathToFileURL(resolve(FRONTEND, 'scripts/icon-binary.mjs')).href
  )) as unknown as BinaryModule
})

describe('frontend/public/', () => {
  it('contains exactly the planned asset names and nothing else', () => {
    expect(readdirSync(PUBLIC).sort()).toEqual(ASSET_NAMES)
  })

  it.each(SVG_NAMES)('%s passes the svg content check as a standalone file', (name) => {
    expect(svg.checkSvgContent(text(name), { standalone: true })).toEqual({ ok: true })
  })

  it.each(SVG_NAMES)('%s is within the size limit', (name) => {
    expect(text(name).length).toBeLessThanOrEqual(svg.MAX_SVG_LENGTH)
  })

  it('favicon.ico passes the ico check and holds the 16, 32 and 48 px images', () => {
    const result = bin.inspectIco(read('favicon.ico'))
    expect(result.ok).toBe(true)
    if (result.ok) {
      expect(result.images.map((i) => `${i.width}x${i.height}`)).toEqual([
        '16x16',
        '32x32',
        '48x48',
      ])
    }
  })

  it('favicon.ico starts with the ico signature and is within the size limit', () => {
    const ico = read('favicon.ico')
    expect(ico.subarray(0, 4)).toEqual(Buffer.from([0, 0, 1, 0]))
    expect(ico.length).toBeLessThanOrEqual(bin.MAX_ICO_BYTES)
  })

  it('draws the same shapes in app-icon.svg and favicon.svg', () => {
    expect(pathData('app-icon.svg').length).toBeGreaterThan(0)
    expect(pathData('favicon.svg')).toEqual(pathData('app-icon.svg'))
  })

  it('uses one fixed color in favicon.svg and currentColor in app-icon.svg', () => {
    expect(text('favicon.svg')).toMatch(/ fill="#2563eb"/)
    expect(text('app-icon.svg')).toMatch(/ fill="currentColor"/)
    expect(text('favicon.svg')).not.toMatch(/currentColor/)
    expect(text('app-icon.svg')).not.toMatch(/#[0-9A-Fa-f]{3,8}/)
  })

  it('has the license header at the top of both svg files and no XML declaration', () => {
    for (const name of SVG_NAMES) {
      expect(text(name).startsWith('<!--\n  Copyright 2026 agwlvssainokuni')).toBe(true)
      expect(text(name)).not.toMatch(/<\?xml/)
    }
  })
})

describe('index.html', () => {
  const html = readFileSync(resolve(FRONTEND, 'index.html'), 'utf8')
  const links = [...html.matchAll(/<link\b[^>]*rel="icon"[^>]*>/g)].map((m) => m[0])
  const hrefs = links.map((l) => /href="([^"]*)"/.exec(l)?.[1] ?? '')

  it('has an svg favicon link and an ico favicon link', () => {
    expect(links).toHaveLength(2)
    expect(hrefs).toContain('/favicon.svg')
    expect(hrefs).toContain('/favicon.ico')
    expect(links.find((l) => l.includes('/favicon.svg'))).toContain('type="image/svg+xml"')
  })

  it('puts the ico link before the svg link (the svg wins in browsers that read both)', () => {
    expect(hrefs.indexOf('/favicon.ico')).toBeLessThan(hrefs.indexOf('/favicon.svg'))
  })

  it.each(hrefs)('link %s does not point outside and names a file in public/', (href) => {
    expect(href).not.toMatch(/^(?:[A-Za-z][A-Za-z0-9+.-]*:|\/\/)/)
    expect(ASSET_NAMES).toContain(href.replace(/^\//, ''))
  })
})
