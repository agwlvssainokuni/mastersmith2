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
// 確かめる表示のテスト（BR7.1、NFR9.1・NFR10.1、AC1.1.6・AC2.1.3・AC3.3.1・AC4.1.2・AC5.1.4）。
import { fireEvent, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { DslConfirmDialog, type DslConfirm } from './DslConfirmDialog'
import { previewRef } from './testing/fixtures'
import { renderDsl } from './testing/renderDsl'

interface HarnessProps {
  confirm: DslConfirm
  onConfirm?: () => void
  onCancel?: () => void
}

/** 開く前のボタンと確かめる表示を持つ画面の代わり */
function Harness({ confirm, onConfirm = vi.fn(), onCancel = vi.fn() }: HarnessProps) {
  const [open, setOpen] = useState(false)
  return (
    <>
      <button type="button" onClick={() => setOpen(true)}>
        開く
      </button>
      <DslConfirmDialog
        confirm={open ? confirm : null}
        timeZone="Asia/Tokyo"
        onConfirm={() => {
          onConfirm()
          setOpen(false)
        }}
        onCancel={() => {
          onCancel()
          setOpen(false)
        }}
      />
    </>
  )
}

const replace: DslConfirm = { kind: 'replace', operation: 'submit', preview: previewRef() }
const apply: DslConfirm = {
  kind: 'apply',
  tables: { added: 1, removed: 1, changed: 3 },
  columns: { added: 12, removed: 5, changed: 2 },
  warningCount: 2,
}

async function openWith(props: HarnessProps, languages?: readonly string[]) {
  const user = userEvent.setup()
  const rendered = renderDsl(<Harness {...props} />, languages)
  await user.click(screen.getByRole('button', { name: '開く' }))
  return { user, ...rendered }
}

describe('DslConfirmDialog', () => {
  it('shows the source, the person and the time of the current preview before replacing', async () => {
    await openWith({ confirm: replace })

    const dialog = screen.getByRole('dialog', { name: '今のプレビューを置き換えますか' })
    expect(dialog).toHaveAttribute('aria-modal', 'true')
    expect(dialog).toHaveTextContent(
      'DSL を投入すると、今のプレビューは投入した DSL に置き換わります',
    )
    expect(within(dialog).getByTestId('dsl-confirm-preview')).toHaveTextContent(
      '出どころ アップロード置いた人 hanako@example.com2026-09-24 11:02 JST',
    )
    expect(within(dialog).getByRole('button', { name: '置き換える' })).toBeInTheDocument()
  })

  it('puts the first focus on cancel and sends nothing when cancelled', async () => {
    const onConfirm = vi.fn()
    const onCancel = vi.fn()
    const { user } = await openWith({ confirm: apply, onConfirm, onCancel })

    expect(screen.getByRole('button', { name: 'やめる' })).toHaveFocus()
    await user.keyboard('{Enter}')

    expect(onCancel).toHaveBeenCalledTimes(1)
    expect(onConfirm).not.toHaveBeenCalled()
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '開く' })).toHaveFocus()
  })

  it('closes with Escape and returns the focus to the opening button', async () => {
    const onCancel = vi.fn()
    const { user } = await openWith({ confirm: replace, onCancel })

    await user.keyboard('{Escape}')

    expect(onCancel).toHaveBeenCalledTimes(1)
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument()
    expect(screen.getByRole('button', { name: '開く' })).toHaveFocus()
  })

  it('does not close when the background is clicked', async () => {
    const onCancel = vi.fn()
    const onConfirm = vi.fn()
    const { user } = await openWith({ confirm: apply, onCancel, onConfirm })

    fireEvent.mouseDown(screen.getByTestId('modal-overlay'))

    expect(screen.getByRole('dialog')).toBeInTheDocument()
    expect(onCancel).not.toHaveBeenCalled()
    await user.keyboard('{Escape}')
    expect(onCancel).toHaveBeenCalledTimes(1)
    expect(onConfirm).not.toHaveBeenCalled()
  })

  it('keeps the focus inside the dialog with Tab', async () => {
    const { user } = await openWith({ confirm: apply })

    const dialog = screen.getByRole('dialog')
    for (let i = 0; i < 4; i += 1) {
      await user.tab()
      expect(dialog.contains(document.activeElement)).toBe(true)
    }
  })

  it('shows the counts of the differences and of the warnings before applying', async () => {
    const onConfirm = vi.fn()
    const { user } = await openWith({ confirm: apply, onConfirm })

    expect(screen.getByTestId('dsl-confirm-diff')).toHaveTextContent(
      'テーブル 増えた 1・減った 1・変わった 3カラム 増えた 12・減った 5・変わった 2',
    )
    expect(screen.getByTestId('dsl-confirm-warnings')).toHaveTextContent('対象DB との食い違い 2件')
    await user.click(screen.getByRole('button', { name: '適用する' }))
    expect(onConfirm).toHaveBeenCalledTimes(1)
  })

  it('tells that there is no warning', async () => {
    await openWith({ confirm: { ...apply, warningCount: 0 } as DslConfirm })

    expect(screen.getByTestId('dsl-confirm-warnings')).toHaveTextContent('ありません')
  })

  it('shows the discard confirmation with a destructive button', async () => {
    await openWith({
      confirm: { kind: 'discard', preview: previewRef({ by: { userId: '3', email: null } }) },
    })

    const dialog = screen.getByRole('dialog', { name: 'プレビューを破棄しますか' })
    expect(dialog).toHaveTextContent('破棄したプレビューは元に戻せません')
    expect(dialog).toHaveTextContent('不明')
    expect(within(dialog).getByRole('button', { name: '破棄する' })).toHaveClass('variant-danger')
  })

  it('names the confirm button after the operation for generate and restore in English', async () => {
    const generate: DslConfirm = { kind: 'replace', operation: 'generate', preview: previewRef() }
    const first = await openWith({ confirm: generate }, ['en-US'])
    expect(screen.getByRole('dialog', { name: 'Replace the current preview?' })).toHaveTextContent(
      'Loading the schema replaces the current preview',
    )
    expect(screen.getByRole('button', { name: 'Replace' })).toBeInTheDocument()
    first.unmount()

    await openWith({ confirm: { ...generate, operation: 'restore' } as DslConfirm })
    expect(screen.getByRole('dialog')).toHaveTextContent('履歴の版を戻すと')
  })

  it('names the close button in the display language', async () => {
    const first = await openWith({ confirm: replace })
    expect(
      within(screen.getByRole('dialog')).getByRole('button', { name: '閉じる' }),
    ).toBeInTheDocument()
    first.unmount()

    await openWith({ confirm: replace }, ['en-US'])
    const dialog = screen.getByRole('dialog')
    expect(within(dialog).getByRole('button', { name: 'Close' })).toBeInTheDocument()
    expect(within(dialog).queryByRole('button', { name: '閉じる' })).not.toBeInTheDocument()
  })

  it('describes the dialog with its body text', async () => {
    await openWith({ confirm: replace })

    expect(screen.getByRole('dialog')).toHaveAccessibleDescription(
      expect.stringContaining('DSL を投入すると、今のプレビューは投入した DSL に置き換わります'),
    )
  })

  it('has no accessibility violations', async () => {
    await openWith({ confirm: replace })

    expect(await axe(document.body)).toHaveNoViolations()
  })
})
