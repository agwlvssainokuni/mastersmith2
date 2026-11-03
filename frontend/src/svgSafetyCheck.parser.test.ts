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
// scripts/check-svg-safety.mjs が「受け入れた」SVG を、jsdom の厳密な XML パーサー（image/svg+xml）で読み直す（U6 の SD7.1b）。
// 検査の字句の切り出しと本物のパーサーの食い違い（検査は通したが、パーサーは別の要素・属性・処理命令を見る形）を拾う。
// 本物のパーサーは検査の道具には使わず、テストだけで使う。jsdom は既存の開発用の依存。
import fc from 'fast-check'
import { beforeAll, describe, expect, it } from 'vitest'
import { goodFragment, goodSvg, loadSvgTool, type SvgSafetyModule } from './svgFixtures'

let tool: SvgSafetyModule

beforeAll(async () => {
  tool = await loadSvgTool()
})

const EXAMPLES = [
  goodSvg(),
  goodSvg(
    '<g fill="#2563eb" transform="translate(1 2)"><path d="M0 0L4 4Z"/><circle cx="1" cy="2" r="3"/></g>',
  ),
  goodSvg(
    '<title>App icon</title><defs><clipPath id="c1"><rect x="0" y="0" width="8" height="8"/></clipPath></defs><path d="M0 0H8V8Z" clip-path="url(#c1)"/>',
  ),
  goodSvg("<path d='M0 0H8Z' />"),
]

/** パーサーで読み、要素と属性の名前・処理命令・コメント・外への参照を集める */
function inspect(source: string) {
  const doc = new DOMParser().parseFromString(source, 'image/svg+xml')
  const parserError = doc.getElementsByTagName('parsererror').length > 0
  const elements: string[] = []
  const attributes: string[] = []
  const prefixed: string[] = []
  const nodeKinds = new Set<number>()
  const walk = (node: Node) => {
    nodeKinds.add(node.nodeType)
    if (node.nodeType === 1) {
      const el = node as Element
      elements.push(el.localName)
      if (el.prefix) prefixed.push(`${el.prefix}:${el.localName}`)
      for (const attr of Array.from(el.attributes)) {
        attributes.push(attr.name)
        if (attr.prefix) prefixed.push(attr.name)
      }
    }
    node.childNodes.forEach(walk)
  }
  walk(doc)
  return { parserError, elements, attributes, prefixed, nodeKinds, doc }
}

describe('accepted svg read by a strict XML parser', () => {
  it.each(EXAMPLES.map((s, i) => [i, s] as const))(
    'example %i is accepted by the check and read by the parser inside the allow list',
    (_i, source) => {
      expect(tool.checkSvgContent(source)).toEqual({ ok: true })
      const read = inspect(source)

      expect(read.parserError).toBe(false)
      expect(read.elements.length).toBeGreaterThan(0)
      for (const name of read.elements) {
        expect(tool.ALLOWED_ELEMENTS.has(name)).toBe(true)
      }
      for (const name of read.attributes) {
        expect(tool.ATTRIBUTE_GRAMMARS.has(name)).toBe(true)
      }
      expect(read.prefixed).toEqual([])
      // 7 = 処理命令, 4 = CDATA, 10 = DOCTYPE
      for (const kind of [4, 7, 10]) {
        expect(read.nodeKinds.has(kind)).toBe(false)
      }
      // 外部の参照（href・url(http…)・@import）が属性にも本文にも無い
      // （ヘッダーのコメントはライセンスの URL を含むため除き、根の xmlns の名前空間の値も除く）
      const body = source.slice(source.indexOf('-->') + 3)
      expect(body.replace(/xmlns="http:\/\/www\.w3\.org\/2000\/svg"/, '')).not.toMatch(
        /https?:|\/\/|@import|href/i,
      )
    },
  )

  it('keeps the header as the only comment of the document', () => {
    const read = inspect(goodSvg())
    expect(read.nodeKinds.has(8)).toBe(true)
    const comments: string[] = []
    read.doc.childNodes.forEach((n) => {
      if (n.nodeType === 8) comments.push(n.nodeValue ?? '')
    })
    expect(comments).toHaveLength(1)
    expect(comments[0]).toContain('Apache License')
  })

  it('reads the fragment form (component output) as svg elements only', () => {
    const fragment = goodFragment()
    expect(tool.checkSvgContent(fragment, { standalone: false })).toEqual({ ok: true })
    const read = inspect(fragment.replace('<svg ', '<svg xmlns="http://www.w3.org/2000/svg" '))
    expect(read.parserError).toBe(false)
    expect(read.elements).toEqual(['svg', 'path'])
  })

  it('agrees on every lightly mutated input that the check accepts', () => {
    const base = EXAMPLES[1]
    const mutation = fc.record({
      at: fc.nat({ max: base.length }),
      cut: fc.nat({ max: 3 }),
      insert: fc.constantFrom(
        '',
        ' ',
        '\n',
        '<g/>',
        '<path/>',
        ' fill="none"',
        '<script/>',
        '<!-- x -->',
        '&amp;',
        '"',
      ),
    })
    let accepted = 0
    const result = fc.check(
      fc.property(mutation, ({ at, cut, insert }) => {
        const mutated = base.slice(0, at) + insert + base.slice(at + cut)
        if (!tool.checkSvgContent(mutated).ok) return true
        accepted++
        const read = inspect(mutated)
        return (
          !read.parserError &&
          read.elements.every((n) => tool.ALLOWED_ELEMENTS.has(n)) &&
          read.attributes.every((n) => tool.ATTRIBUTE_GRAMMARS.has(n)) &&
          read.prefixed.length === 0 &&
          !read.nodeKinds.has(7) &&
          !read.nodeKinds.has(4)
        )
      }),
      { numRuns: 300 },
    )
    if (result.failed) {
      throw new Error(
        `property failed: seed=${result.seed} path=${result.counterexamplePath ?? ''}`,
      )
    }
    expect(accepted).toBeGreaterThan(0)
  })
})
