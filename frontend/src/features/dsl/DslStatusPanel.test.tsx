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
// 今の状態のテスト（BR3.1・BR5.10・BR6.3、AC3.1.6・AC3.1.7、NFR3.9・NFR9.1・NFR10.1）。
import { screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import type { DslStatus } from './api/types'
import { DslStatusPanel, type DslStatusPanelProps } from './DslStatusPanel'
import { hashOf, previewRef, statusOf } from './testing/fixtures'
import { renderDsl } from './testing/renderDsl'

function renderPanel(
  overrides: Partial<DslStatusPanelProps> = {},
  languages: readonly string[] = ['ja-JP'],
) {
  const props: DslStatusPanelProps = {
    status: statusOf(),
    loadState: 'loaded',
    busy: false,
    generating: false,
    onReadSchema: vi.fn(),
    onRetry: vi.fn(),
    timeZone: 'Asia/Tokyo',
    ...overrides,
  }
  return { props, ...renderDsl(<DslStatusPanel {...props} />, languages) }
}

describe('DslStatusPanel', () => {
  it('shows the applied DSL and the preview with short IDs, people, sources and local times', () => {
    renderPanel()

    const applied = screen.getByTestId('dsl-status-applied')
    expect(applied).toHaveTextContent('3f9a1c000000…')
    expect(applied).toHaveTextContent(hashOf('3f9a1c'))
    expect(applied).toHaveTextContent('taro@example.com')
    expect(applied).toHaveTextContent('2026-09-24 10:15 JST')
    const preview = screen.getByTestId('dsl-status-preview')
    expect(preview).toHaveTextContent('8b02d4000000…')
    expect(preview).toHaveTextContent('アップロード')
    expect(preview).toHaveTextContent('hanako@example.com')
    expect(preview).toHaveTextContent('2026-09-24 11:02 JST')
  })

  it('tells that nothing is applied and that there is no preview', () => {
    renderPanel({ status: { applied: null, preview: null } })

    expect(screen.getByTestId('dsl-status-applied')).toHaveTextContent('まだ適用していません')
    expect(screen.getByTestId('dsl-status-preview')).toHaveTextContent('プレビューはありません')
  })

  it('shows an unknown person when the user no longer exists and keeps a script as text', () => {
    const status: DslStatus = statusOf({
      preview: previewRef({ by: { userId: '9', email: null } }),
      applied: {
        revisionId: 'r',
        dslHash: '<script>alert(1)</script>',
        source: 'PASTE',
        by: { userId: '1', email: '<b>x</b>' },
        at: '2026-09-24T01:15:00Z',
      },
    })
    const { container } = renderPanel({ status })

    expect(screen.getByTestId('dsl-status-preview')).toHaveTextContent('不明')
    expect(screen.getByTestId('dsl-status-applied')).toHaveTextContent('<b>x</b>')
    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelector('b')).toBeNull()
  })

  it('shows the loading message and the error with a retry', async () => {
    const user = userEvent.setup()
    const { props, unmount } = renderPanel({ status: null, loadState: 'loading' })
    expect(screen.getByTestId('dsl-status-loading')).toHaveTextContent('読み込んでいます')
    unmount()

    const failed = renderPanel({ status: null, loadState: 'failed' })
    expect(screen.getByTestId('dsl-status-error')).toHaveTextContent('読み込めませんでした')
    await user.click(screen.getByRole('button', { name: 'もう一度読み込む' }))
    expect(failed.props.onRetry).toHaveBeenCalledTimes(1)
    expect(props.onRetry).not.toHaveBeenCalled()
  })

  it('reads the schema from the button and disables it while another operation runs', async () => {
    const user = userEvent.setup()
    const { props, unmount } = renderPanel()

    await user.click(screen.getByRole('button', { name: 'スキーマを読み込む' }))
    expect(props.onReadSchema).toHaveBeenCalledTimes(1)
    unmount()

    renderPanel({ busy: true, generating: true })
    const button = screen.getByTestId('dsl-read-schema-button')
    expect(button).toBeDisabled()
    expect(button).toHaveAttribute('aria-busy', 'true')
  })

  it('shows English text without translating the IDs', () => {
    renderPanel({}, ['en-US'])

    const region = screen.getByRole('region', { name: 'Current state' })
    expect(within(region).getByTestId('dsl-status-applied')).toHaveTextContent('3f9a1c000000…')
    expect(within(region).getByTestId('dsl-status-preview')).toHaveTextContent('Upload')
    expect(screen.getByRole('button', { name: 'Load schema' })).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPanel()

    expect(await axe(container)).toHaveNoViolations()
  })
})
