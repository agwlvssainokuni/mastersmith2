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
// 投入の入力のテスト（BR2.2・BR2.3、NFR1.20）。UTF-8 のバイト数は性質ベースのテスト（fast-check）で
// TextEncoder と比べる。失敗したときは fast-check が乱数の種（seed）を示すので、その値で再現できる。
import fc from 'fast-check'
import { describe, expect, it, vi } from 'vitest'
import {
  MAX_SUBMIT_BYTES,
  readSubmitPayload,
  submitReadiness,
  utf8ByteLength,
  type SubmitInput,
} from './submitInput'

/** 中身は小さく、大きさだけを size にしたファイル（10MB の中身を作らない） */
function sizedFile(size: number, name = 'master.yaml'): File {
  const file = new File(['version: 1\n'], name, { type: 'application/yaml' })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

function fileInput(file: File | null): SubmitInput {
  return { mode: 'file', file, pasteText: '' }
}

describe('submitInput', () => {
  it('counts the UTF-8 bytes of Japanese, emoji and lone surrogates like TextEncoder', () => {
    expect(utf8ByteLength('abc')).toBe(3)
    expect(utf8ByteLength('表示名')).toBe(9)
    expect(utf8ByteLength('é')).toBe(2)
    expect(utf8ByteLength('😀')).toBe(4)
    expect(utf8ByteLength('\ud800')).toBe(3)
    expect(utf8ByteLength('\ud800a')).toBe(4)
    expect(utf8ByteLength('\udc00')).toBe(3)
  })

  it('always agrees with the length of TextEncoder', () => {
    const encoder = new TextEncoder()
    const anyCodeUnit = fc.integer({ min: 0, max: 0xffff }).map((c) => String.fromCharCode(c))
    fc.assert(
      fc.property(
        fc.oneof(fc.string({ unit: 'grapheme' }), fc.string({ unit: anyCodeUnit })),
        (text) => utf8ByteLength(text) === encoder.encode(text).length,
      ),
    )
  })

  it('accepts a file of exactly 10MB and rejects one byte more without reading it', async () => {
    const exact = sizedFile(MAX_SUBMIT_BYTES)
    const over = sizedFile(MAX_SUBMIT_BYTES + 1)
    const readOver = vi.spyOn(over, 'text')

    expect(submitReadiness(fileInput(exact))).toBe('ready')
    expect(submitReadiness(fileInput(over))).toBe('tooLarge')
    expect(await readSubmitPayload(fileInput(over))).toBeNull()
    expect(readOver).not.toHaveBeenCalled()
    expect(await readSubmitPayload(fileInput(exact))).toEqual({
      text: 'version: 1\n',
      source: 'UPLOAD',
    })
  })

  it('judges a pasted text by its UTF-8 bytes', () => {
    const exact = 'あ'.repeat(MAX_SUBMIT_BYTES / 4) + 'a'.repeat(MAX_SUBMIT_BYTES / 4)
    expect(utf8ByteLength(exact)).toBe(MAX_SUBMIT_BYTES)
    expect(submitReadiness({ mode: 'paste', file: null, pasteText: exact })).toBe('ready')
    expect(submitReadiness({ mode: 'paste', file: null, pasteText: exact + 'a' })).toBe('tooLarge')
    // 文字の数は上限より少なくても、バイト数で上限を超える。
    const japanese = 'あ'.repeat(MAX_SUBMIT_BYTES / 3 + 1)
    expect(japanese.length).toBeLessThan(MAX_SUBMIT_BYTES)
    expect(submitReadiness({ mode: 'paste', file: null, pasteText: japanese })).toBe('tooLarge')
  })

  it('uses only the chosen input and is empty without it', async () => {
    const file = sizedFile(10)
    expect(submitReadiness({ mode: 'paste', file, pasteText: '' })).toBe('empty')
    expect(submitReadiness({ mode: 'file', file: null, pasteText: 'a: 1' })).toBe('empty')
    expect(await readSubmitPayload({ mode: 'paste', file, pasteText: 'a: 1' })).toEqual({
      text: 'a: 1',
      source: 'PASTE',
    })
  })
})
