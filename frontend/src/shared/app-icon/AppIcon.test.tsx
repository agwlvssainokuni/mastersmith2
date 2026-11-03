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
import { render, screen } from '@testing-library/react'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { loadSvgTool, type SvgSafetyModule } from '../../svgFixtures'
import { APP_ICON_PATHS, AppIcon } from './AppIcon'

let svgTool: SvgSafetyModule

beforeAll(async () => {
  svgTool = await loadSvgTool()
})

afterEach(() => {
  vi.restoreAllMocks()
})

const PUBLIC_APP_ICON = resolve(import.meta.dirname, '../../../public/app-icon.svg')

function renderedSvg(size?: number): SVGSVGElement {
  const { container } = render(<AppIcon size={size} />)
  const svg = container.querySelector('svg')
  if (!svg) throw new Error('svg was not rendered')
  return svg
}

describe('AppIcon', () => {
  it('renders markup that passes the svg allow-list check (component form)', () => {
    expect(svgTool.checkSvgContent(renderedSvg().outerHTML, { standalone: false })).toEqual({
      ok: true,
    })
  })

  it('uses only allow-listed attributes and none of the unsafe ones', () => {
    const svg = renderedSvg()
    const names = svg.getAttributeNames().sort()
    expect(names).toEqual(['aria-hidden', 'fill', 'focusable', 'height', 'viewBox', 'width'])
    expect(svg.getAttribute('aria-hidden')).toBe('true')
    expect(svg.getAttribute('focusable')).toBe('false')
    expect(svg.getAttribute('fill')).toBe('currentColor')
    for (const unsafe of ['title', 'aria-label', 'class', 'style', 'href', 'xlink:href']) {
      expect(svg.hasAttribute(unsafe)).toBe(false)
    }
    expect(names.some((n) => n.startsWith('on'))).toBe(false)
    expect(
      svg.querySelectorAll('title, desc, script, style, use, image, foreignObject'),
    ).toHaveLength(0)
  })

  it('is not exposed to the accessibility tree', () => {
    renderedSvg()
    expect(screen.queryByRole('img')).not.toBeInTheDocument()
  })

  it('draws exactly the paths of public/app-icon.svg', () => {
    const file = readFileSync(PUBLIC_APP_ICON, 'utf8')
    const fileData = [...file.matchAll(/<path d="([^"]*)"/g)].map((m) => m[1])
    const drawn = Array.from(renderedSvg().querySelectorAll('path')).map((p) => p.getAttribute('d'))
    expect(fileData.length).toBeGreaterThan(0)
    expect([...APP_ICON_PATHS]).toEqual(fileData)
    expect(drawn).toEqual(fileData)
  })

  it('reflects the size in width and height, and defaults to 24', () => {
    const big = renderedSvg(48)
    expect([big.getAttribute('width'), big.getAttribute('height')]).toEqual(['48', '48'])
    const small = renderedSvg()
    expect([small.getAttribute('width'), small.getAttribute('height')]).toEqual(['24', '24'])
  })

  it('does not call fetch while rendering (adds no HTTP request)', () => {
    const fetchSpy = vi.spyOn(globalThis, 'fetch')
    renderedSvg(48)
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('has no accessibility violations', async () => {
    const { container } = render(
      <main>
        <AppIcon size={48} />
        <p>MasterSmith</p>
      </main>,
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
