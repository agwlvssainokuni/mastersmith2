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
// 確かめ中・読み込めないの表示のテスト（W4、D5、NFR6.2・NFR7.2・NFR8.1）。
import { screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { RegistrationStatus } from './RegistrationStatus'
import { renderRegistration } from './testing/renderRegistration'

beforeEach(() => {
  resetDisplayTestState()
})

describe('RegistrationStatus', () => {
  it('shows the checking text as a status while verifying', () => {
    renderRegistration(<RegistrationStatus kind="verifying" onReload={vi.fn()} />)
    expect(screen.getByRole('status')).toHaveTextContent('リンクを確かめています')
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
    expect(screen.queryByRole('button')).not.toBeInTheDocument()
  })

  it('shows the load failure as an alert with the reload button', () => {
    renderRegistration(<RegistrationStatus kind="loadFailed" onReload={vi.fn()} />)
    expect(screen.getByRole('alert')).toHaveTextContent(
      '読み込めませんでした。しばらくしてから、もう一度お試しください。',
    )
    expect(screen.getByRole('button', { name: 'もう一度読み込む' })).toBeInTheDocument()
  })

  it('calls onReload when the reload button is pressed', async () => {
    const user = userEvent.setup()
    const onReload = vi.fn()
    renderRegistration(<RegistrationStatus kind="loadFailed" onReload={onReload} />)
    await user.click(screen.getByRole('button', { name: 'もう一度読み込む' }))
    expect(onReload).toHaveBeenCalledTimes(1)
  })

  it('shows the texts in English for an English screen', () => {
    const { unmount } = renderRegistration(
      <RegistrationStatus kind="verifying" onReload={vi.fn()} />,
      { languages: ['en-US'] },
    )
    expect(screen.getByRole('status')).toHaveTextContent('Checking your link')
    unmount()
    renderRegistration(<RegistrationStatus kind="loadFailed" onReload={vi.fn()} />, {
      languages: ['en-US'],
    })
    expect(screen.getByRole('alert')).toHaveTextContent('The page could not be loaded.')
    expect(screen.getByRole('button', { name: 'Reload' })).toBeInTheDocument()
  })

  it('has no accessibility violations in both kinds', async () => {
    const { container, unmount } = renderRegistration(
      <RegistrationStatus kind="verifying" onReload={vi.fn()} />,
    )
    expect(await axe(container)).toHaveNoViolations()
    unmount()
    const failed = renderRegistration(<RegistrationStatus kind="loadFailed" onReload={vi.fn()} />)
    expect(await axe(failed.container)).toHaveNoViolations()
  })
})
