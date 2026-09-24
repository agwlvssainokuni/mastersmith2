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
// 投入のテスト（BR2.2・BR2.3、NFR1.20・NFR3.11・NFR9.1・NFR10.1、AC2.1.4・AC2.1.5・AC2.2.9）。
import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { DslSubmitForm } from './DslSubmitForm'
import {
  DSL_SCHEMA_PATH,
  EMPTY_SUBMIT_INPUT,
  MAX_SUBMIT_BYTES,
  type SubmitInput,
  type SubmitPayload,
} from './submitInput'
import { renderDsl } from './testing/renderDsl'

interface HarnessProps {
  initial?: SubmitInput
  busy?: boolean
  onSubmit: (payload: SubmitPayload) => void
}

/** 入力を持つ画面の代わり */
function Harness({ initial = EMPTY_SUBMIT_INPUT, busy = false, onSubmit }: HarnessProps) {
  const [input, setInput] = useState(initial)
  return (
    <DslSubmitForm
      input={input}
      onChange={setInput}
      busy={busy}
      submitting={false}
      onSubmit={onSubmit}
    />
  )
}

function sizedFile(size: number, name = 'master-dsl-v3.yaml'): File {
  const file = new File(['version: 1\n'], name, { type: 'application/yaml' })
  Object.defineProperty(file, 'size', { value: size })
  return file
}

describe('DslSubmitForm', () => {
  it('cannot submit without input and guides the user', () => {
    renderDsl(<Harness onSubmit={vi.fn()} />)

    const button = screen.getByRole('button', { name: '投入する' })
    expect(button).toBeDisabled()
    expect(button).toHaveAccessibleDescription('ファイルを選ぶか、貼り付けてください')
    expect(screen.getByTestId('dsl-submit-file-name')).toHaveTextContent(
      'ファイルが選ばれていません',
    )
  })

  it('reads a file of exactly 10MB as text and submits it as UPLOAD', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    renderDsl(<Harness onSubmit={onSubmit} />)
    const file = sizedFile(MAX_SUBMIT_BYTES)

    await user.upload(screen.getByLabelText('DSL のファイル（YAML、10MB まで）'), file)
    expect(screen.getByTestId('dsl-submit-file-name')).toHaveTextContent(
      'master-dsl-v3.yaml（10.0MB）',
    )
    await user.click(screen.getByRole('button', { name: '投入する' }))

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({ text: 'version: 1\n', source: 'UPLOAD' }),
    )
  })

  it('tells before sending that a file over 10MB is too large and never reads it', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    renderDsl(<Harness onSubmit={onSubmit} />)
    const file = sizedFile(MAX_SUBMIT_BYTES + 1)
    const read = vi.spyOn(file, 'text')

    const input = screen.getByLabelText('DSL のファイル（YAML、10MB まで）')
    await user.upload(input, file)

    expect(screen.getByTestId('dsl-submit-too-large')).toHaveTextContent(
      'ファイルが 10MB を超えています',
    )
    expect(input).toHaveAccessibleDescription(/10MB を超えています/)
    expect(screen.getByRole('button', { name: '投入する' })).toBeDisabled()
    expect(read).not.toHaveBeenCalled()
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('submits the pasted text as PASTE and judges it by UTF-8 bytes', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    renderDsl(<Harness onSubmit={onSubmit} />)

    await user.click(screen.getByRole('radio', { name: '貼り付ける' }))
    await user.type(screen.getByLabelText('DSL（YAML、10MB まで）'), 'version: 1')
    await user.click(screen.getByRole('button', { name: '投入する' }))

    await waitFor(() =>
      expect(onSubmit).toHaveBeenCalledWith({ text: 'version: 1', source: 'PASTE' }),
    )
  })

  it('tells that a pasted Japanese text over 10MB in bytes is too large', () => {
    const pasteText = 'あ'.repeat(MAX_SUBMIT_BYTES / 3 + 1)
    renderDsl(<Harness initial={{ mode: 'paste', file: null, pasteText }} onSubmit={vi.fn()} />)

    expect(screen.getByRole('alert')).toHaveTextContent('貼り付けた DSL が 10MB を超えています')
    expect(screen.getByRole('button', { name: '投入する' })).toBeDisabled()
  })

  it('keeps the other input when switching and sends only the chosen one', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    const file = sizedFile(20, 'kept.yaml')
    renderDsl(<Harness initial={{ mode: 'paste', file, pasteText: 'a: 1' }} onSubmit={onSubmit} />)

    await user.click(screen.getByRole('radio', { name: 'ファイルを選ぶ' }))
    expect(screen.getByTestId('dsl-submit-file-name')).toHaveTextContent('kept.yaml')
    await user.click(screen.getByRole('radio', { name: '貼り付ける' }))
    expect(screen.getByLabelText('DSL（YAML、10MB まで）')).toHaveValue('a: 1')
    await user.click(screen.getByRole('button', { name: '投入する' }))

    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith({ text: 'a: 1', source: 'PASTE' }))
  })

  it('shows a message when the file cannot be read', async () => {
    const user = userEvent.setup()
    const onSubmit = vi.fn()
    const file = sizedFile(10)
    vi.spyOn(file, 'text').mockRejectedValueOnce(new Error('NotReadableError'))
    renderDsl(<Harness initial={{ mode: 'file', file, pasteText: '' }} onSubmit={onSubmit} />)

    await user.click(screen.getByRole('button', { name: '投入する' }))

    expect(await screen.findByTestId('dsl-submit-read-failed')).toHaveTextContent(
      'ファイルを読み込めませんでした',
    )
    expect(onSubmit).not.toHaveBeenCalled()
  })

  it('cannot submit while another operation runs', () => {
    renderDsl(
      <Harness
        busy
        initial={{ mode: 'paste', file: null, pasteText: 'a: 1' }}
        onSubmit={vi.fn()}
      />,
    )

    expect(screen.getByRole('button', { name: '投入する' })).toBeDisabled()
  })

  it('links to the JSON Schema on the same origin with a fixed path', () => {
    renderDsl(<Harness onSubmit={vi.fn()} />)

    const link = screen.getByRole('link', { name: 'DSL の書式（JSON Schema）' })
    expect(link).toHaveAttribute('href', DSL_SCHEMA_PATH)
    expect(link.getAttribute('href')).toBe('/dsl/dsl-schema-v1.json')
    expect(link).toHaveAttribute('download')
  })

  it('shows English text', () => {
    renderDsl(<Harness onSubmit={vi.fn()} />, ['en-US'])

    expect(screen.getByLabelText('DSL file (YAML, up to 10MB)')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'DSL format (JSON Schema)' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Submit' })).toBeDisabled()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderDsl(<Harness onSubmit={vi.fn()} />)

    expect(await axe(container)).toHaveNoViolations()
  })
})
