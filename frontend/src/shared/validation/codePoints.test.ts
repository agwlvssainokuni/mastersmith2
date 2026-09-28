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
// 文字の数え方と前後の空白の除き方のテスト（U6 の部品 8節、NFR9.9）。性質ベースのテストは fast-check で、
// 失敗したときは fast-check が seed と path を出力に示す（再現は fc.assert の第2引数に一時的に渡す）。
import fc from 'fast-check'
import { describe, expect, it } from 'vitest'
import {
  countCodePoints,
  isWhiteSpaceCodePoint,
  trimDisplayName,
  utf8ByteLength,
} from './codePoints'

/** 任意の UTF-16 の単位（組になっていないサロゲートを含む） */
const anyCodeUnit = fc.integer({ min: 0, max: 0xffff }).map((c) => String.fromCharCode(c))

/** 組になっていないサロゲートも含む任意の文字列 */
const anyText = fc.oneof(fc.string({ unit: 'grapheme' }), fc.string({ unit: anyCodeUnit }))

/** サーバーの White_Space の集合の文字 */
const whiteSpaceChars = [
  '\u0009',
  '\u000a',
  '\u000b',
  '\u000c',
  '\u000d',
  ' ',
  '\u0085',
  ' ',
  ' ',
  ' ',
  ' ',
  ' ',
  ' ',
  ' ',
  ' ',
  '　',
]

describe('countCodePoints', () => {
  it('counts a surrogate pair as one and a lone surrogate as one', () => {
    expect(countCodePoints('')).toBe(0)
    expect(countCodePoints('abc')).toBe(3)
    expect(countCodePoints('😀')).toBe(1)
    expect(countCodePoints('😀😀')).toBe(2)
    expect(countCodePoints('\ud800')).toBe(1)
    expect(countCodePoints('\udc00\ud800')).toBe(2)
    expect(countCodePoints('a\ud800b')).toBe(3)
  })

  it('counts combining characters as separate code points', () => {
    // 「か」と結合用の濁点（U+3099）は2つのコードポイント
    expect(countCodePoints('が')).toBe(2)
    expect(countCodePoints('é')).toBe(2)
  })

  it('always agrees with the length of Array.from', () => {
    fc.assert(fc.property(anyText, (text) => countCodePoints(text) === Array.from(text).length))
  })
})

describe('utf8ByteLength', () => {
  it('counts bytes like TextEncoder, including lone surrogates as three bytes', () => {
    expect(utf8ByteLength('abc')).toBe(3)
    expect(utf8ByteLength('é')).toBe(2)
    expect(utf8ByteLength('あ')).toBe(3)
    expect(utf8ByteLength('😀')).toBe(4)
    expect(utf8ByteLength('\ud800')).toBe(3)
    expect(utf8ByteLength('\ud800a')).toBe(4)
  })

  it('always agrees with the length of TextEncoder', () => {
    const encoder = new TextEncoder()
    fc.assert(fc.property(anyText, (text) => utf8ByteLength(text) === encoder.encode(text).length))
  })
})

describe('trimDisplayName', () => {
  it('removes the Unicode White_Space characters at both ends only', () => {
    expect(trimDisplayName('　山田 花子 ')).toBe('山田 花子')
    expect(trimDisplayName('\u0085  名前 \t')).toBe('名前')
    expect(trimDisplayName(' 山田　花子 ')).toBe('山田　花子')
    expect(trimDisplayName('   ')).toBe('')
    expect(trimDisplayName('')).toBe('')
  })

  it('does not remove U+FEFF and U+200B, which are not White_Space', () => {
    expect(trimDisplayName('﻿名前')).toBe('﻿名前')
    expect(trimDisplayName('名前​')).toBe('名前​')
  })

  it('uses the same set of characters as the server', () => {
    for (const char of whiteSpaceChars) {
      expect(isWhiteSpaceCodePoint(char.codePointAt(0) ?? -1)).toBe(true)
    }
    expect(isWhiteSpaceCodePoint(0xfeff)).toBe(false)
    expect(isWhiteSpaceCodePoint(0x200b)).toBe(false)
    expect(isWhiteSpaceCodePoint(0x0041)).toBe(false)
  })

  it('is idempotent, leaves no White_Space at the ends and keeps the inside', () => {
    const padding = fc.string({ unit: fc.constantFrom(...whiteSpaceChars), maxLength: 4 })
    fc.assert(
      fc.property(anyText, padding, padding, (text, before, after) => {
        const once = trimDisplayName(before + text + after)
        const twice = trimDisplayName(once)
        const first = once.codePointAt(0)
        const last = once.length > 0 ? once.charCodeAt(once.length - 1) : undefined
        return (
          once === twice &&
          (first === undefined || !isWhiteSpaceCodePoint(first)) &&
          (last === undefined || !isWhiteSpaceCodePoint(last)) &&
          (before + text + after).includes(once) &&
          once === trimDisplayName(text)
        )
      }),
    )
  })
})
