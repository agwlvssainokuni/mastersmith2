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
// scripts/check-dist-assets.mjs（dist の素材の検査。U6 の SD4.2 の L2、SD7.5）の確かめ。
// 一時のディレクトリに public/ と dist を作り、素材が欠けた・バイト列が違う・HTML だった、などで実際に失敗することを確かめる。
import { cpSync, mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { afterEach, beforeAll, beforeEach, describe, expect, it } from 'vitest'

interface DistModule {
  checkDistAssets: (dirs: { publicDir: string; distDir: string }) => string[]
}

const REAL_PUBLIC = resolve(import.meta.dirname, '../public')
const INDEX_OK =
  '<!doctype html><html><head><link rel="icon" href="/favicon.ico" sizes="32x32" />' +
  '<link rel="icon" href="/favicon.svg" type="image/svg+xml" /></head><body></body></html>'

let tool: DistModule
let work: string
let publicDir: string
let distDir: string

beforeAll(async () => {
  const url = pathToFileURL(resolve(import.meta.dirname, '../scripts/check-dist-assets.mjs')).href
  tool = (await import(/* @vite-ignore */ url)) as unknown as DistModule
})

beforeEach(() => {
  work = mkdtempSync(join(tmpdir(), 'dist-assets-'))
  publicDir = join(work, 'public')
  distDir = join(work, 'dist')
  // 実際の素材を public/ と dist に写した、正しい状態から始める
  cpSync(REAL_PUBLIC, publicDir, { recursive: true })
  cpSync(REAL_PUBLIC, distDir, { recursive: true })
  writeFileSync(join(distDir, 'index.html'), INDEX_OK)
})

afterEach(() => {
  rmSync(work, { recursive: true, force: true })
})

const run = () => tool.checkDistAssets({ publicDir, distDir })
const withIndex = (html: string) => writeFileSync(join(distDir, 'index.html'), html)
const linkIndex = (...hrefs: string[]) =>
  `<html><head>${hrefs.map((h) => `<link rel="icon" href="${h}" />`).join('')}</head></html>`

describe('checkDistAssets', () => {
  it('passes for a correct dist', () => {
    expect(run()).toEqual([])
  })

  it('fails when an asset is missing from dist', () => {
    rmSync(join(distDir, 'favicon.ico'))
    expect(run().join('\n')).toContain('dist に favicon.ico がありません')
  })

  it('fails when the bytes differ from public/', () => {
    writeFileSync(join(distDir, 'app-icon.svg'), '<svg/>')
    expect(run().join('\n')).toContain('dist の app-icon.svg が public/ と違います')
  })

  it('fails when favicon.ico is an HTML body (the shape of index.html served for a missing file)', () => {
    const html = '<!doctype html><html><head></head><body></body></html>'
    writeFileSync(join(publicDir, 'favicon.ico'), html)
    writeFileSync(join(distDir, 'favicon.ico'), html)
    expect(run().join('\n')).toContain('ICO の形に合いません: ico-header')
  })

  it('fails when a png is not a png', () => {
    writeFileSync(join(publicDir, 'extra.png'), 'not a png at all, just text........')
    writeFileSync(join(distDir, 'extra.png'), 'not a png at all, just text........')
    expect(run().join('\n')).toContain('PNG の形に合いません')
  })

  it('fails when public/ is empty or missing', () => {
    rmSync(publicDir, { recursive: true })
    mkdirSync(publicDir)
    expect(run().join('\n')).toContain('public/ が空です')
    rmSync(publicDir, { recursive: true })
    expect(run().join('\n')).toContain('public/ が空です')
  })

  it('fails when an svg does not pass the content check', () => {
    const bad = readSvgWithScript()
    writeFileSync(join(publicDir, 'favicon.svg'), bad)
    writeFileSync(join(distDir, 'favicon.svg'), bad)
    expect(run().join('\n')).toContain('favicon.svg が SVG の検査に通りません: unknown-element')
  })

  it('fails when dist itself or its index.html is missing', () => {
    rmSync(join(distDir, 'index.html'))
    expect(run().join('\n')).toContain('dist/index.html がありません')
    rmSync(distDir, { recursive: true })
    expect(run().join('\n')).toContain('dist がありません')
  })
})

describe('checkDistAssets: favicon links in index.html', () => {
  it.each([
    [
      'an external https URL',
      ['https://example.com/favicon.svg', '/favicon.ico'],
      '外部を指しています',
    ],
    [
      'a protocol-relative URL',
      ['//cdn.example.com/favicon.svg', '/favicon.ico'],
      '外部を指しています',
    ],
    ['a data: URL', ['data:image/svg+xml,x', '/favicon.ico'], '外部を指しています'],
    ['a path that climbs out of dist', ['/../favicon.svg', '/favicon.ico'], 'dist の外へ出ます'],
    [
      'a relative path that climbs out',
      ['../public/favicon.svg', '/favicon.ico'],
      'dist の外へ出ます',
    ],
    [
      'a file that does not exist in dist',
      ['/missing.svg', '/favicon.ico'],
      'dist に無いファイルを指しています',
    ],
  ])('fails for %s', (_name, hrefs, message) => {
    withIndex(linkIndex(...hrefs))
    expect(run().join('\n')).toContain(message)
  })

  it('fails when there is no favicon link at all', () => {
    withIndex('<html><head></head></html>')
    const joined = run().join('\n')
    expect(joined).toContain('SVG の favicon の <link> がありません')
    expect(joined).toContain('.ico（か .png）の favicon の <link> がありません')
  })

  it('fails when only the svg link exists, and when only the ico link exists', () => {
    withIndex(linkIndex('/favicon.svg'))
    expect(run().join('\n')).toContain('.ico（か .png）の favicon の <link> がありません')
    withIndex(linkIndex('/favicon.ico'))
    expect(run().join('\n')).toContain('SVG の favicon の <link> がありません')
  })

  it('fails when a favicon link has no href', () => {
    withIndex('<html><head><link rel="icon" /></head></html>')
    expect(run().join('\n')).toContain('href がありません')
  })

  it('ignores links that are not icons and accepts a query or hash on an icon href', () => {
    withIndex(
      '<html><head><link rel="stylesheet" href="https://example.com/a.css" />' +
        '<link rel="icon" href="/favicon.ico?v=1" /><link rel="shortcut icon" href="/favicon.svg#x" /></head></html>',
    )
    expect(run()).toEqual([])
  })
})

/** favicon.svg に script を足した、検査に通らない SVG（テストの中の文字列。.svg のファイルとして置かない） */
function readSvgWithScript(): string {
  const real = readFileSync(join(REAL_PUBLIC, 'favicon.svg'), 'utf8')
  const header = real.slice(0, real.indexOf('-->') + 3)
  return `${header}\n<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 8 8"><script/></svg>\n`
}
