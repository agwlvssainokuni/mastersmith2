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
// scripts/check-svg-safety.mjs（SVG の中身の許可リスト検査。U6 の SD1）の表を使う確かめ。
// 悪い例はこのファイルの中の文字列で、.svg のファイルとして frontend/ の下に置かない（置くと検査が実際に失敗するため）。
import { beforeAll, describe, expect, it } from 'vitest'
import {
  ROOT_OPEN,
  goodFragment,
  goodSvg,
  loadSvgTool,
  svgLicenseHeader,
  type SvgSafetyModule,
} from './svgFixtures'

let tool: SvgSafetyModule

beforeAll(async () => {
  tool = await loadSvgTool()
})

const HEADER = svgLicenseHeader()
// no-script-url の規則に当たらないよう、悪い例の URL は組み立てて作る（規則は外さない）
const JS_SCHEME = ['java', 'script:'].join('')
const NS = 'xmlns="http://www.w3.org/2000/svg"'
const wrap = (inner: string) => `${HEADER}\n${ROOT_OPEN}${inner}</svg>\n`
const fileWithRoot = (root: string) => `${HEADER}\n${root}`

describe('good examples', () => {
  it('accepts the shape of the real public/ files (standalone)', () => {
    expect(tool.checkSvgContent(goodSvg())).toEqual({ ok: true })
  })

  it('accepts every allowed element and attribute together', () => {
    const inner =
      '<title>App icon</title><desc>decor, only.</desc><defs><clipPath id="c1"><rect x="0" y="0" width="8" height="8"/></clipPath></defs>' +
      '<g transform="translate(1 2) scale(2)" opacity="0.5" fill="#2563eb" stroke="none" stroke-width="2" stroke-linecap="round" stroke-linejoin="bevel">' +
      '<circle cx="1" cy="2" r="3"/><ellipse cx="1" cy="2" rx="3" ry="4"/><line x1="0" y1="0" x2="4" y2="4"/>' +
      '<polyline points="0,0 4,4 8,0"/><polygon points="0 0 4 4 8 0"/>' +
      '<path d="M0 0L4 4Z" fill-rule="evenodd" clip-rule="nonzero" clip-path="url(#c1)" fill="url(#c1)" stroke="#fff"/></g>'
    expect(tool.checkSvgContent(wrap(inner))).toEqual({ ok: true })
  })

  it('accepts single-quoted values, an end tag with a space and a self-closing root', () => {
    expect(tool.checkSvgContent(wrap("<path d='M0 0H8Z' />"))).toEqual({ ok: true })
    expect(tool.checkSvgContent(`${HEADER}\n<svg ${NS}/>\n`)).toEqual({ ok: true })
    expect(tool.checkSvgContent(`${HEADER}\n<svg ${NS}></svg >\n`)).toEqual({ ok: true })
  })

  it('accepts role and aria attributes on a decorative icon', () => {
    const svg = `${HEADER}\n<svg ${NS} role="presentation" aria-hidden="true" focusable="false"/>`
    expect(tool.checkSvgContent(svg)).toEqual({ ok: true })
  })
})

describe('standalone and fragment forms', () => {
  it('requires the header comment and the root xmlns only when standalone', () => {
    const fragment = goodFragment()
    expect(tool.checkSvgContent(fragment, { standalone: false })).toEqual({ ok: true })
    expect(tool.checkSvgContent(fragment)).toMatchObject({ ok: false, code: 'header-missing' })
    expect(tool.checkSvgContent(`${HEADER}\n${fragment}`)).toMatchObject({
      ok: false,
      code: 'xmlns-missing',
    })
  })

  it('applies the other rules to the fragment form too', () => {
    expect(tool.checkSvgContent('<svg><script/></svg>', { standalone: false })).toMatchObject({
      ok: false,
      code: 'unknown-element',
    })
    expect(tool.checkSvgContent('<svg class="x"/>', { standalone: false })).toMatchObject({
      ok: false,
      code: 'unknown-attribute',
    })
  })

  it('defaults to the standalone form', () => {
    expect(tool.checkSvgContent(goodFragment())).toMatchObject({ ok: false })
  })
})

interface BadCase {
  name: string
  content: string
  code: string
}

const BAD_CASES: BadCase[] = [
  // 大文字小文字・接頭辞・名前の直後の空白（SD1.6）
  {
    name: 'upper-case SCRIPT element',
    content: wrap('<SCRIPT>x</SCRIPT>'),
    code: 'unknown-element',
  },
  { name: 'mixed-case Script element', content: wrap('<Script/>'), code: 'unknown-element' },
  {
    name: 'prefixed svg:script element',
    content: wrap('<svg:script>x</svg:script>'),
    code: 'unknown-element',
  },
  { name: 'prefixed x:path element', content: wrap('<x:path d="M0"/>'), code: 'unknown-element' },
  {
    name: 'script followed by a newline',
    content: wrap('<script\n>x</script>'),
    code: 'unknown-element',
  },
  {
    name: 'script followed by a tab',
    content: wrap('<script\t>x</script>'),
    code: 'unknown-element',
  },
  {
    name: 'plain script element',
    content: wrap('<script>alert(1)</script>'),
    code: 'unknown-element',
  },
  { name: 'empty element name', content: wrap('< />'), code: 'unknown-element' },
  // イベントの属性・外への参照
  { name: 'onload attribute', content: wrap('<path onload="x()"/>'), code: 'unknown-attribute' },
  {
    name: 'upper-case ONLOAD attribute',
    content: wrap('<path ONLOAD="x()"/>'),
    code: 'unknown-attribute',
  },
  {
    name: 'onclick attribute on the root',
    content: fileWithRoot(`<svg ${NS} onclick="x()"/>`),
    code: 'unknown-attribute',
  },
  {
    name: 'href to an external URL',
    content: wrap('<path href="https://example.com/a"/>'),
    code: 'unknown-attribute',
  },
  { name: 'xlink:href', content: wrap('<path xlink:href="#a"/>'), code: 'unknown-attribute' },
  {
    name: 'xmlns:xlink',
    content: fileWithRoot(`<svg ${NS} xmlns:xlink="http://www.w3.org/1999/xlink"/>`),
    code: 'unknown-attribute',
  },
  {
    name: 'href to a fragment is not allowed either',
    content: wrap('<path href="#a"/>'),
    code: 'unknown-attribute',
  },
  { name: 'style attribute', content: wrap('<path style="fill:red"/>'), code: 'unknown-attribute' },
  { name: 'class attribute', content: wrap('<path class="a"/>'), code: 'unknown-attribute' },
  { name: 'xmlns on a child element', content: wrap(`<g ${NS}/>`), code: 'unknown-attribute' },
  {
    name: 'style element',
    content: wrap('<style>@import url(http://example.com/a.css);</style>'),
    code: 'unknown-element',
  },
  {
    name: 'foreignObject element',
    content: wrap('<foreignObject><div/></foreignObject>'),
    code: 'unknown-element',
  },
  { name: 'a element', content: wrap('<a><path d="M0"/></a>'), code: 'unknown-element' },
  { name: 'use element', content: wrap('<use/>'), code: 'unknown-element' },
  { name: 'image element', content: wrap('<image/>'), code: 'unknown-element' },
  { name: 'animate element', content: wrap('<animate/>'), code: 'unknown-element' },
  { name: 'iframe element', content: wrap('<iframe/>'), code: 'unknown-element' },
  // 値の文法
  {
    name: 'url() to an external URL',
    content: wrap('<path fill="url(http://example.com/a)"/>'),
    code: 'bad-value:fill',
  },
  {
    name: 'data: URI in fill',
    content: wrap('<path fill="data:image/svg+xml,x"/>'),
    code: 'bad-value:fill',
  },
  {
    name: 'script scheme URL in fill',
    content: wrap(`<path fill="${JS_SCHEME}x()"/>`),
    code: 'bad-value:fill',
  },
  {
    name: 'character reference in fill',
    content: wrap('<path fill="&#x6A;avascript:x"/>'),
    code: 'bad-value:fill',
  },
  { name: 'ampersand entity in d', content: wrap('<path d="M0 &amp; 1"/>'), code: 'bad-value:d' },
  {
    name: 'greater-than inside an id value',
    content: wrap('<path id="a>b"/>'),
    code: 'bad-value:id',
  },
  {
    name: 'tag smuggled after a greater-than in a value',
    content: wrap('<path id="x>"/><script>1</script>'),
    code: 'bad-value:id',
  },
  {
    name: 'wrong xmlns value',
    content: fileWithRoot('<svg xmlns="http://example.com/x"/>'),
    code: 'bad-value:xmlns',
  },
  { name: 'bad role value', content: wrap('<path role="button"/>'), code: 'bad-value:role' },
  {
    name: 'bad fill-rule value',
    content: wrap('<path fill-rule="x"/>'),
    code: 'bad-value:fill-rule',
  },
  {
    name: 'bad aria-hidden value',
    content: wrap('<path aria-hidden="yes"/>'),
    code: 'bad-value:aria-hidden',
  },
  {
    name: 'clip-path without url()',
    content: wrap('<path clip-path="none"/>'),
    code: 'bad-value:clip-path',
  },
  // コメント・CDATA・処理命令・DOCTYPE・ENTITY
  {
    name: 'comment after the header',
    content: wrap('<!-- <script>x</script> -->'),
    code: 'comment-or-decl',
  },
  {
    name: 'second comment before the root',
    content: `${HEADER}\n<!-- x -->\n${ROOT_OPEN}</svg>`,
    code: 'comment-or-decl',
  },
  { name: 'CDATA section', content: wrap('<![CDATA[<script>x</script>]]>'), code: 'cdata' },
  {
    name: 'xml-stylesheet processing instruction',
    content: `${HEADER}\n<?xml-stylesheet href="http://example.com/a.css"?>\n${ROOT_OPEN}</svg>`,
    code: 'processing-instruction',
  },
  {
    name: 'processing instruction inside the root',
    content: wrap('<?php x ?>'),
    code: 'processing-instruction',
  },
  {
    name: 'DOCTYPE with an external entity',
    content: `${HEADER}\n<!DOCTYPE svg [<!ENTITY x SYSTEM "http://example.com">]>\n${ROOT_OPEN}</svg>`,
    code: 'doctype',
  },
  {
    name: 'lower-case doctype',
    content: `${HEADER}\n<!doctype svg>\n${ROOT_OPEN}</svg>`,
    code: 'doctype',
  },
  { name: 'ENTITY declaration', content: wrap('<!ENTITY x "y">'), code: 'entity' },
  { name: 'other declaration', content: wrap('<!ELEMENT x>'), code: 'comment-or-decl' },
  {
    name: 'XML declaration before the header',
    content: `<?xml version="1.0"?>\n${goodSvg()}`,
    code: 'header-missing',
  },
  // ヘッダー
  { name: 'no header at all', content: `${ROOT_OPEN}</svg>`, code: 'header-missing' },
  { name: 'unterminated header comment', content: '<!-- never closed', code: 'header-missing' },
  {
    name: 'header comment containing a double hyphen',
    content: `<!-- a -- b -->\n${ROOT_OPEN}</svg>`,
    code: 'comment-body',
  },
  {
    name: 'header comment containing a less-than',
    content: `<!-- <script> -->\n${ROOT_OPEN}</svg>`,
    code: 'comment-body',
  },
  // 引用符・続け書き・二重の属性
  { name: 'unquoted value', content: wrap('<path d=M0/>'), code: 'unquoted' },
  { name: 'unterminated value', content: wrap('<path d="M0/>'), code: 'unterminated-value' },
  {
    name: 'attribute glued to the previous value',
    content: wrap('<path d="M0"id="a"/>'),
    code: 'attr-spacing',
  },
  {
    name: 'duplicate attribute',
    content: wrap('<path d="M0" d="M1"/>'),
    code: 'duplicate-attribute',
  },
  { name: 'attribute without a value', content: wrap('<path d/>'), code: 'no-equals' },
  { name: 'space around the equals sign', content: wrap('<path d = "M0"/>'), code: 'no-equals' },
  // 文字
  { name: 'BOM at the start', content: `﻿${goodSvg()}`, code: 'non-ascii-or-control' },
  { name: 'NUL character', content: wrap('<path d="M0\u0000"/>'), code: 'non-ascii-or-control' },
  {
    name: 'bidirectional control character',
    content: wrap('<title>a‮b</title>'),
    code: 'non-ascii-or-control',
  },
  { name: 'full-width text', content: wrap('<title>日本語</title>'), code: 'non-ascii-or-control' },
  { name: 'vertical tab', content: wrap('<path d="M0\u000B1"/>'), code: 'non-ascii-or-control' },
  // 全体の形
  {
    name: 'element after the root',
    content: `${goodSvg().trimEnd()}<svg ${NS}/>`,
    code: 'after-root',
  },
  {
    name: 'second root after a self-closing root',
    content: `${HEADER}\n<svg ${NS}/><svg ${NS}/>`,
    code: 'after-root',
  },
  { name: 'root that is not svg', content: `${HEADER}\n<path d="M0"/>`, code: 'root-not-svg' },
  { name: 'header only', content: `${HEADER}\n`, code: 'no-root' },
  { name: 'unclosed element', content: `${HEADER}\n${ROOT_OPEN}<g>`, code: 'unclosed' },
  {
    name: 'mismatched end tag',
    content: `${HEADER}\n${ROOT_OPEN}<g></path></svg>`,
    code: 'nesting',
  },
  {
    name: 'end tag with an extra attribute',
    content: `${HEADER}\n${ROOT_OPEN}</svg x>`,
    code: 'bad-close',
  },
  { name: 'end tag without any open element', content: `${HEADER}\n</svg>`, code: 'nesting' },
  {
    name: 'slash not followed by greater-than',
    content: wrap('<path d="M0" / >'),
    code: 'bad-slash',
  },
  {
    name: 'tag cut by the end of the input',
    content: `${HEADER}\n<svg ${NS}`,
    code: 'unterminated-tag',
  },
  { name: 'text outside title and desc', content: wrap('hello'), code: 'text' },
  { name: 'text in title that is not plain', content: wrap('<title>a_b</title>'), code: 'text' },
  { name: 'stray greater-than', content: wrap('>'), code: 'stray-gt' },
  { name: 'text before the root', content: `${HEADER}\nhello\n${ROOT_OPEN}</svg>`, code: 'text' },
  {
    name: 'xmlns missing on the root',
    content: `${HEADER}\n<svg viewBox="0 0 1 1"/>`,
    code: 'xmlns-missing',
  },
]

describe('bad examples (each one is rejected with its own code)', () => {
  it.each(BAD_CASES)('rejects: $name', ({ content, code }) => {
    const result = tool.checkSvgContent(content)
    expect(result).toMatchObject({ ok: false, code })
  })

  it('reports a position inside the input and never returns the content', () => {
    for (const { content } of BAD_CASES) {
      const result = tool.checkSvgContent(content)
      if (!result.ok) {
        expect(Object.keys(result).sort()).toEqual(['code', 'ok', 'position'])
        expect(result.position).toBeGreaterThanOrEqual(0)
        expect(result.position).toBeLessThanOrEqual(content.length)
      }
    }
  })

  it('stops at the first violation (the earliest one wins)', () => {
    const result = tool.checkSvgContent(wrap('<script/><path onload="x"/>'))
    expect(result).toMatchObject({ ok: false, code: 'unknown-element' })
  })
})

describe('depth and size limits', () => {
  const nested = (levels: number) => wrap('<g>'.repeat(levels) + '</g>'.repeat(levels))

  it('accepts the maximum depth and rejects one more', () => {
    // 根の svg を 1 として、g を 7 段（合計 8）まで
    expect(tool.checkSvgContent(nested(tool.MAX_DEPTH - 1))).toEqual({ ok: true })
    expect(tool.checkSvgContent(nested(tool.MAX_DEPTH))).toMatchObject({
      ok: false,
      code: 'too-deep',
    })
  })

  it('accepts exactly the size limit and rejects one character more', () => {
    const base = wrap('<path d=""/>')
    const pad = tool.MAX_SVG_LENGTH - base.length
    const exact = wrap(`<path d="${'1'.repeat(pad)}"/>`)
    expect(exact.length).toBe(tool.MAX_SVG_LENGTH)
    expect(tool.checkSvgContent(exact)).toEqual({ ok: true })
    const over = wrap(`<path d="${'1'.repeat(pad + 1)}"/>`)
    expect(tool.checkSvgContent(over)).toMatchObject({ ok: false, code: 'too-large' })
  })

  it('rejects a value that is not a string', () => {
    expect(tool.checkSvgContent(undefined)).toMatchObject({ ok: false, code: 'too-large' })
    expect(tool.checkSvgContent(42)).toMatchObject({ ok: false, code: 'too-large' })
  })
})

describe('time to scan hostile inputs', () => {
  it.each([
    ['many less-than signs', '<'.repeat(4000)],
    ['many unclosed svg tags', '<svg '.repeat(800)],
    ['many open quotes', 'a="'.repeat(1300)],
  ])('returns quickly for %s', (_name, input) => {
    const started = performance.now()
    const result = tool.checkSvgContent(input, { standalone: false })
    expect(result.ok).toBe(false)
    expect(performance.now() - started).toBeLessThan(500)
  })
})

describe('allow lists', () => {
  it('has no href, style or class attribute in the allow list', () => {
    for (const name of ['href', 'xlink:href', 'xmlns:xlink', 'style', 'class']) {
      expect(tool.ATTRIBUTE_GRAMMARS.has(name)).toBe(false)
    }
  })

  it('has no script-like element in the allow list', () => {
    for (const name of [
      'script',
      'style',
      'foreignObject',
      'iframe',
      'object',
      'embed',
      'image',
      'use',
      'a',
    ]) {
      expect(tool.ALLOWED_ELEMENTS.has(name)).toBe(false)
    }
  })
})
