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
// scripts/icon-binary.mjs（PNG・ICO の形の確かめと ICO を詰める純粋な関数。U6 の SD3・SD7.4）と
// scripts/make-ico.mjs（手で流す道具）の確かめ。
import { mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { afterAll, beforeAll, describe, expect, it } from 'vitest'

type Fail = { ok: false; code: string }
interface IcoImage {
  width: number
  height: number
  offset: number
  size: number
}
interface BinaryModule {
  PNG_SIGNATURE: Buffer
  MAX_PNG_BYTES: number
  MAX_ICO_BYTES: number
  readPngSize: (b: Buffer) => { ok: true; width: number; height: number } | Fail
  inspectPng: (
    b: Buffer,
    o?: { limit?: number },
  ) => { ok: true; width: number; height: number } | Fail
  packIco: (pngs: Buffer[]) => Buffer
  inspectIco: (b: Buffer, o?: { limit?: number }) => { ok: true; images: IcoImage[] } | Fail
}
interface MakeIcoModule {
  makeIco: (
    output: string,
    inputs: string[],
  ) => { ok: true; bytes: number } | { ok: false; message: string }
}

let bin: BinaryModule
let make: MakeIcoModule
let work: string

beforeAll(async () => {
  const dir = resolve(import.meta.dirname, '../scripts')
  bin = (await import(
    /* @vite-ignore */ pathToFileURL(join(dir, 'icon-binary.mjs')).href
  )) as unknown as BinaryModule
  make = (await import(
    /* @vite-ignore */ pathToFileURL(join(dir, 'make-ico.mjs')).href
  )) as unknown as MakeIcoModule
  work = mkdtempSync(join(tmpdir(), 'icon-binary-'))
})

afterAll(() => {
  rmSync(work, { recursive: true, force: true })
})

/** 署名と IHDR だけを持つ、形の確かめ用の PNG（画像として開く必要はない） */
function fakePng(width: number, height: number, padding = 0): Buffer {
  const ihdr = Buffer.alloc(25)
  ihdr.writeUInt32BE(13, 0)
  ihdr.write('IHDR', 4, 'latin1')
  ihdr.writeUInt32BE(width, 8)
  ihdr.writeUInt32BE(height, 12)
  return Buffer.concat([bin.PNG_SIGNATURE, ihdr, Buffer.alloc(padding)])
}

describe('readPngSize and inspectPng', () => {
  it('reads the width and height from the IHDR', () => {
    expect(bin.readPngSize(fakePng(32, 16))).toEqual({ ok: true, width: 32, height: 16 })
  })

  it('reports a wrong signature as a result, not an exception', () => {
    const wrong = fakePng(8, 8)
    wrong[1] = 0x00
    expect(bin.readPngSize(wrong)).toEqual({ ok: false, code: 'png-signature' })
  })

  it('reports an html body, a short buffer, a missing IHDR and a zero size', () => {
    expect(bin.readPngSize(Buffer.from('<!doctype html><html></html>'))).toEqual({
      ok: false,
      code: 'png-signature',
    })
    expect(bin.readPngSize(Buffer.from([1, 2, 3]))).toEqual({ ok: false, code: 'png-too-short' })
    expect(bin.readPngSize('x' as unknown as Buffer)).toEqual({ ok: false, code: 'png-too-short' })
    const noIhdr = fakePng(8, 8)
    noIhdr.write('XXXX', 12, 'latin1')
    expect(bin.readPngSize(noIhdr)).toEqual({ ok: false, code: 'png-ihdr' })
    expect(bin.readPngSize(fakePng(0, 8))).toEqual({ ok: false, code: 'png-size' })
  })

  it('accepts a png up to the limit and rejects one byte more', () => {
    const exact = fakePng(8, 8, bin.MAX_PNG_BYTES - fakePng(8, 8).length)
    expect(exact.length).toBe(bin.MAX_PNG_BYTES)
    expect(bin.inspectPng(exact)).toMatchObject({ ok: true })
    expect(bin.inspectPng(Buffer.concat([exact, Buffer.alloc(1)]))).toEqual({
      ok: false,
      code: 'png-too-large',
    })
    expect(bin.inspectPng(fakePng(8, 8, 100), { limit: 50 })).toEqual({
      ok: false,
      code: 'png-too-large',
    })
  })
})

describe('packIco', () => {
  it.each([1, 2, 3])('packs %i png(s) into an ico that passes inspectIco', (count) => {
    const sizes = [16, 32, 48].slice(0, count)
    const pngs = sizes.map((s) => fakePng(s, s, 40))
    const ico = bin.packIco(pngs)

    expect(ico.subarray(0, 4)).toEqual(Buffer.from([0, 0, 1, 0]))
    expect(ico.readUInt16LE(4)).toBe(count)
    const checked = bin.inspectIco(ico)
    expect(checked.ok).toBe(true)
    if (checked.ok) {
      expect(checked.images.map((i) => i.width)).toEqual(sizes)
      // ディレクトリの直後から PNG がそのままの並びで続く
      const directoryEnd = 6 + 16 * count
      expect(checked.images[0].offset).toBe(directoryEnd)
      checked.images.forEach((image, i) => {
        expect(image.size).toBe(pngs[i].length)
        expect(ico.subarray(image.offset, image.offset + image.size).equals(pngs[i])).toBe(true)
      })
    }
  })

  it('writes 0 for a width or height of 256 or more', () => {
    const ico = bin.packIco([fakePng(256, 512)])
    expect(ico[6]).toBe(0)
    expect(ico[7]).toBe(0)
  })

  it('refuses no png, nine pngs and something that is not a png', () => {
    expect(() => bin.packIco([])).toThrow(RangeError)
    expect(() => bin.packIco(Array.from({ length: 9 }, () => fakePng(8, 8)))).toThrow(RangeError)
    expect(() => bin.packIco([Buffer.from('<!doctype html>................')])).toThrow(TypeError)
  })
})

describe('inspectIco rejects', () => {
  const good = () => bin.packIco([fakePng(16, 16, 10), fakePng(32, 32, 10)])

  it('an html body (what index.html looks like when favicon.ico is missing)', () => {
    expect(bin.inspectIco(Buffer.from('<!doctype html><html><head></head></html>'))).toEqual({
      ok: false,
      code: 'ico-header',
    })
  })

  it('a wrong reserved field or type', () => {
    for (const [at, value] of [
      [0, 1],
      [2, 2],
    ] as const) {
      const ico = good()
      ico.writeUInt16LE(value, at)
      expect(bin.inspectIco(ico)).toEqual({ ok: false, code: 'ico-header' })
    }
  })

  it('an image count of 0 and of 9', () => {
    for (const count of [0, 9]) {
      const ico = good()
      ico.writeUInt16LE(count, 4)
      expect(bin.inspectIco(ico)).toEqual({ ok: false, code: 'ico-count' })
    }
  })

  it('a directory that runs past the end and an entry outside the file', () => {
    expect(bin.inspectIco(good().subarray(0, 20))).toEqual({ ok: false, code: 'ico-directory' })
    const outside = good()
    outside.writeUInt32LE(outside.length, 6 + 12)
    expect(bin.inspectIco(outside)).toEqual({ ok: false, code: 'ico-entry-range' })
    const tooLong = good()
    tooLong.writeUInt32LE(tooLong.length, 6 + 8)
    expect(bin.inspectIco(tooLong)).toEqual({ ok: false, code: 'ico-entry-range' })
    const insideDirectory = good()
    insideDirectory.writeUInt32LE(2, 6 + 12)
    expect(bin.inspectIco(insideDirectory)).toEqual({ ok: false, code: 'ico-entry-range' })
  })

  it('an entry without the png signature', () => {
    const ico = good()
    const offset = ico.readUInt32LE(6 + 12)
    ico[offset] = 0x00
    expect(bin.inspectIco(ico)).toEqual({ ok: false, code: 'ico-entry-png' })
  })

  it('a buffer that is too short or over the size limit', () => {
    expect(bin.inspectIco(Buffer.from([0, 0, 1]))).toEqual({ ok: false, code: 'ico-too-short' })
    const big = bin.packIco([fakePng(16, 16, bin.MAX_ICO_BYTES)])
    expect(big.length).toBeGreaterThan(bin.MAX_ICO_BYTES)
    expect(bin.inspectIco(big)).toEqual({ ok: false, code: 'ico-too-large' })
    expect(bin.inspectIco(good(), { limit: 10 })).toEqual({ ok: false, code: 'ico-too-large' })
  })
})

describe('make-ico', () => {
  it('writes an ico that passes the check from png files', () => {
    const a = join(work, 'a.png')
    const b = join(work, 'b.png')
    writeFileSync(a, fakePng(16, 16, 20))
    writeFileSync(b, fakePng(32, 32, 20))
    const out = join(work, 'out.ico')

    const result = make.makeIco(out, [a, b])

    expect(result).toMatchObject({ ok: true })
    expect(bin.inspectIco(readFileSync(out))).toMatchObject({ ok: true })
  })

  it('does not write anything when an input is not a png or missing', () => {
    const html = join(work, 'not.png')
    writeFileSync(html, '<!doctype html><html></html>.....................')
    const out = join(work, 'never.ico')

    expect(make.makeIco(out, [html])).toMatchObject({ ok: false })
    expect(make.makeIco(out, [join(work, 'missing.png')])).toMatchObject({ ok: false })
    expect(() => readFileSync(out)).toThrow()
  })
})
