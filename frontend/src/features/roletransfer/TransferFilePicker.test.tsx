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
// ファイルの選択のテスト（Intent 261004-role-menu の U6 の D23、AC3.1.7）。見える label と結んだ input、名前と大きさの
// 文字、上限を超えた文言（上限の値は定数から）、選んだファイルを渡す、押せない形を確かめる。
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { fileOf, renderRoleTransfer } from './testing/fixtures'
import { TransferFilePicker, type TransferFilePickerProps } from './TransferFilePicker'

async function renderPicker(props: Partial<TransferFilePickerProps> = {}) {
  const onSelect = vi.fn()
  const view = renderRoleTransfer(
    <TransferFilePicker
      fileInfo={null}
      tooLarge={false}
      disabled={false}
      onSelect={onSelect}
      {...props}
    />,
  )
  await screen.findByTestId('roletransfer-picker')
  return { ...view, onSelect }
}

describe('TransferFilePicker', () => {
  afterEach(() => {
    resetDisplayTestState()
    vi.restoreAllMocks()
  })

  it('labels the file input and passes the chosen file', async () => {
    const user = userEvent.setup()
    const { onSelect } = await renderPicker()
    const input = screen.getByLabelText('ファイルを選ぶ')
    expect(input).toHaveAttribute('accept', '.yaml,.yml')
    expect(input).toHaveAccessibleDescription('ファイルが選ばれていません。')
    const file = fileOf()
    await user.upload(input, file)
    expect(onSelect).toHaveBeenCalledWith(file)
  })

  it('shows the chosen name and size as text', async () => {
    await renderPicker({ fileInfo: { name: '<b>roles</b>.yaml', size: 2048 } })
    expect(screen.getByTestId('roletransfer-file-status')).toHaveTextContent(
      '選んだファイル: <b>roles</b>.yaml（2048 バイト）',
    )
  })

  it('shows the too-large message with the limit from the constant', async () => {
    await renderPicker({ tooLarge: true, fileInfo: { name: 'big.yaml', size: 10_485_761 } })
    expect(screen.getByRole('alert')).toHaveTextContent('ファイルが大きすぎます（上限 10 MiB）。')
  })

  it('cannot be used while disabled', async () => {
    await renderPicker({ disabled: true })
    expect(screen.getByLabelText('ファイルを選ぶ')).toBeDisabled()
  })

  it('has no accessibility violations with the too-large message', async () => {
    const view = await renderPicker({
      tooLarge: true,
      fileInfo: { name: 'big.yaml', size: 10_485_761 },
    })
    expect(await axe(view.container)).toHaveNoViolations()
  })
})
