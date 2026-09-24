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
// プレビューのテスト（BR5.6〜BR5.9、NFR3.9・NFR9.1・NFR10.1、AC3.1.1〜AC3.1.7・AC3.2.1〜AC3.2.4・AC4.2.1・AC4.2.2）。
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createRef } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import type { MissingDisplayName } from './api/types'
import { DslPreviewPanel, type DslPreviewPanelProps } from './DslPreviewPanel'
import { previewOf } from './testing/fixtures'
import { renderDsl } from './testing/renderDsl'

function renderPanel(
  overrides: Partial<DslPreviewPanelProps> = {},
  languages: readonly string[] = ['ja-JP'],
) {
  const props: DslPreviewPanelProps = {
    preview: previewOf(),
    loadState: 'loaded',
    emptyReason: 'none',
    replacedByOther: false,
    busy: false,
    applying: false,
    discarding: false,
    headingRef: createRef<HTMLHeadingElement>(),
    onDownload: vi.fn(),
    onDiscard: vi.fn(),
    onApply: vi.fn(),
    onRetry: vi.fn(),
    onReadSchema: vi.fn(),
    onGoToSubmit: vi.fn(),
    onShowLatest: vi.fn(),
    ...overrides,
  }
  return { props, ...renderDsl(<DslPreviewPanel {...props} />, languages) }
}

function missing(n: number): MissingDisplayName[] {
  return Array.from({ length: n }, (_, i) => ({
    path: `tables.t${i}.label`,
    language: i % 2 === 0 ? 'ja' : 'en',
  }))
}

describe('DslPreviewPanel', () => {
  it('shows the validation result, the mismatch count, the summary, the differences and the menu', () => {
    renderPanel()

    expect(screen.getByTestId('dsl-preview-valid')).toHaveTextContent('検証を通りました')
    expect(screen.getByTestId('dsl-preview-mismatch')).toHaveTextContent(
      '対象DB との食い違いが 2件あります（適用はできます）',
    )
    expect(screen.queryByTestId('dsl-preview-not-compared')).not.toBeInTheDocument()
    expect(screen.getByTestId('dsl-preview-table-count')).toHaveTextContent(
      'テーブル 42（うちビュー 3）',
    )
    expect(screen.getByTestId('dsl-preview-column-count')).toHaveTextContent('カラム 1318')
    expect(screen.getByTestId('dsl-preview-missing-count')).toHaveTextContent('表示名の未設定 1件')
    expect(screen.getByTestId('dsl-diff-table')).toBeInTheDocument()
    expect(screen.getByTestId('dsl-warning-list')).toBeInTheDocument()
    expect(screen.getByTestId('dsl-menu-node-0.0')).toHaveTextContent('部署（dept_mst）')
    const headings = screen.getAllByRole('heading').map((h) => `${h.tagName}:${h.textContent}`)
    expect(headings.slice(0, 5)).toEqual([
      'H2:プレビュー',
      'H3:要約',
      'H3:適用中との違い',
      'H3:対象DB との食い違い（警告）',
      'H4:対象DB に無いカラム',
    ])
  })

  it('puts the not-compared warning first and still shows the preview', () => {
    renderPanel({
      preview: previewOf({
        warnings: [{ kind: 'TARGET_UNAVAILABLE', path: null, message: '接続できません' }],
      }),
    })

    expect(screen.getByTestId('dsl-preview-not-compared')).toHaveTextContent(
      '対象DB と照合できませんでした',
    )
    expect(screen.queryByTestId('dsl-preview-mismatch')).not.toBeInTheDocument()
    expect(screen.getByTestId('dsl-preview-apply')).toBeEnabled()
  })

  it('shows the first 100 missing display names with the total and the language', async () => {
    const user = userEvent.setup()
    renderPanel({
      preview: previewOf({
        summary: {
          ...previewOf().summary,
          missingDisplayNames: missing(100),
          missingDisplayNameTotal: 250,
        },
      }),
    })

    expect(screen.getByTestId('dsl-preview-missing-count')).toHaveTextContent('250件')
    expect(screen.queryByTestId('dsl-preview-missing-list')).not.toBeInTheDocument()
    const toggle = screen.getByRole('button', { name: '場所を見る' })
    await user.click(toggle)

    expect(toggle).toHaveAttribute('aria-expanded', 'true')
    expect(screen.getByTestId('dsl-preview-missing-truncated')).toHaveTextContent(
      '先頭の 100件を表示しています（全 250件）',
    )
    const table = screen.getByRole('table', { name: '表示名が埋まっていない場所' })
    expect(table.querySelectorAll('tbody tr')).toHaveLength(100)
    expect(table).toHaveTextContent('tables.t1.label英語')
  })

  it('uses the number of the list when the total is missing', () => {
    renderPanel({
      preview: previewOf({
        summary: {
          ...previewOf().summary,
          missingDisplayNames: missing(3),
          missingDisplayNameTotal: undefined,
        },
      }),
    })

    expect(screen.getByTestId('dsl-preview-missing-count')).toHaveTextContent('3件')
  })

  it('downloads, discards and applies the shown preview', async () => {
    const user = userEvent.setup()
    const { props } = renderPanel()

    await user.click(screen.getByRole('button', { name: 'ダウンロード' }))
    await user.click(screen.getByRole('button', { name: '破棄する' }))
    await user.click(screen.getByRole('button', { name: '適用する' }))

    expect(props.onDownload).toHaveBeenCalledTimes(1)
    expect(props.onDiscard).toHaveBeenCalledTimes(1)
    expect(props.onApply).toHaveBeenCalledWith('preview-1')
  })

  it('disables discard and apply while busy but keeps the download', () => {
    renderPanel({ busy: true, applying: true })

    expect(screen.getByTestId('dsl-preview-apply')).toBeDisabled()
    expect(screen.getByTestId('dsl-preview-apply')).toHaveAttribute('aria-busy', 'true')
    expect(screen.getByTestId('dsl-preview-discard')).toBeDisabled()
    expect(screen.getByTestId('dsl-preview-download')).toBeEnabled()
  })

  it('shows the empty state without the preview operations', async () => {
    const user = userEvent.setup()
    const { props } = renderPanel({ preview: null })

    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent('プレビューはありません')
    expect(screen.queryByTestId('dsl-preview-apply')).not.toBeInTheDocument()
    expect(screen.queryByTestId('dsl-preview-discard')).not.toBeInTheDocument()
    expect(screen.queryByTestId('dsl-preview-download')).not.toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'スキーマを読み込む' }))
    await user.click(screen.getByRole('button', { name: '投入のタブへ' }))
    expect(props.onReadSchema).toHaveBeenCalledTimes(1)
    expect(props.onGoToSubmit).toHaveBeenCalledTimes(1)
  })

  it('tells that another administrator discarded the preview', () => {
    renderPanel({ preview: null, emptyReason: 'discardedByOther' })

    expect(screen.getByRole('heading', { level: 2 })).toHaveTextContent(
      'プレビューは別の管理者によって破棄されました',
    )
  })

  it('shows the rejection by a replaced preview with the focus and a way to the latest', async () => {
    const user = userEvent.setup()
    const { props } = renderPanel({ replacedByOther: true })

    const message = screen.getByTestId('dsl-preview-replaced')
    expect(message).toHaveTextContent('別の管理者によって置き換えられました')
    expect(message.closest('[tabindex="-1"]')).toHaveFocus()
    await user.click(screen.getByRole('button', { name: '最新のプレビューを表示する' }))
    expect(props.onShowLatest).toHaveBeenCalledTimes(1)
  })

  it('shows the loading and the failed states', async () => {
    const user = userEvent.setup()
    const loading = renderPanel({ preview: null, loadState: 'loading' })
    expect(screen.getByTestId('dsl-preview-loading')).toBeInTheDocument()
    loading.unmount()

    const { props } = renderPanel({ preview: null, loadState: 'failed' })
    await user.click(screen.getByRole('button', { name: 'もう一度読み込む' }))
    expect(props.onRetry).toHaveBeenCalledTimes(1)
  })

  it('keeps server text that looks like HTML as text', () => {
    const { container } = renderPanel({
      preview: previewOf({
        warnings: [
          { kind: 'TABLE_MISSING', path: '<b>t</b>', message: '<script>alert(1)</script>' },
        ],
        summary: {
          ...previewOf().summary,
          menuTree: [
            { label: { ja: '<img src=x onerror=alert(1)>', en: 'x' }, table: 't', children: [] },
          ],
        },
      }),
    })

    expect(screen.getByText('<script>alert(1)</script>')).toBeInTheDocument()
    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument()
    expect(container.querySelector('script, img, b')).toBeNull()
  })

  it('shows English text', () => {
    renderPanel({}, ['en-US'])

    expect(screen.getByTestId('dsl-preview-valid')).toHaveTextContent('The DSL passed validation')
    expect(screen.getByRole('button', { name: 'Apply' })).toBeInTheDocument()
    expect(screen.getByTestId('dsl-menu-node-0.0')).toHaveTextContent('Departments（dept_mst）')
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPanel()

    expect(await axe(container)).toHaveNoViolations()
  })
})
