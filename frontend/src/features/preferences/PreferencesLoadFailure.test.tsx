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
// 読み込みの失敗の表示のテスト（functional-spec.md の W2、D11・D13、NFR7.2）。
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { useState } from 'react'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { PreferencesLoadFailure } from './PreferencesLoadFailure'
import { renderPreferencesPart } from './testing/renderPreferences'

beforeEach(() => {
  resetDisplayTestState()
})

describe('PreferencesLoadFailure', () => {
  it('shows the failure as an alert with the message of the feature', async () => {
    renderPreferencesPart(<PreferencesLoadFailure onRetry={() => {}} />)
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'プリファレンスを読み込めませんでした。',
    )
  })

  it('calls onRetry when the button is pressed', async () => {
    const user = userEvent.setup()
    const onRetry = vi.fn()
    renderPreferencesPart(<PreferencesLoadFailure onRetry={onRetry} />)
    await user.click(await screen.findByRole('button', { name: 'もう一度読み込む' }))
    expect(onRetry).toHaveBeenCalledTimes(1)
  })

  it('moves the focus to the button only when a retry focus is asked for', async () => {
    const user = userEvent.setup()
    function Harness() {
      const [seq, setSeq] = useState<number | undefined>(undefined)
      return (
        <>
          <button type="button" onClick={() => setSeq((value) => (value ?? 0) + 1)}>
            ask
          </button>
          <PreferencesLoadFailure onRetry={() => {}} retryFocusSeq={seq} />
        </>
      )
    }
    renderPreferencesPart(<Harness />)
    const retry = await screen.findByRole('button', { name: 'もう一度読み込む' })
    expect(retry).not.toHaveFocus()
    await user.click(screen.getByRole('button', { name: 'ask' }))
    expect(retry).toHaveFocus()
  })

  it('uses English messages on an English screen', async () => {
    renderPreferencesPart(<PreferencesLoadFailure onRetry={() => {}} />, ['en-US'])
    expect(await screen.findByRole('alert')).toHaveTextContent(
      'The preferences could not be loaded.',
    )
    expect(screen.getByRole('button', { name: 'Load again' })).toBeInTheDocument()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderPreferencesPart(<PreferencesLoadFailure onRetry={() => {}} />)
    await screen.findByRole('alert')
    expect(await axe(container)).toHaveNoViolations()
  })
})
