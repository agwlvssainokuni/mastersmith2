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
// 行えない操作の理由の表示のテスト（Intent 261016-business-data-views の U3 の L7、D13、AC3.2.1〜AC3.2.5・AC3.3.9）。
import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import { renderWithProviders, resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { registration } from './registration'
import { RestrictionNotice, restrictionMessageKey } from './RestrictionNotice'

afterEach(() => resetDisplayTestState())

const NO_KEY = 'このテーブルは行を1件に決められないため、詳細・更新・削除はできません。'

describe('RestrictionNotice', () => {
  it('shows the same text for a table without a key and with a hidden key', async () => {
    const first = renderWithProviders(
      <RestrictionNotice reason="NO_PRIMARY_KEY" writeConfigured />,
      {
        registrations: [registration],
      },
    )
    expect(await screen.findByText(NO_KEY)).toBeInTheDocument()
    first.unmount()
    renderWithProviders(<RestrictionNotice reason="PRIMARY_KEY_HIDDEN" writeConfigured />, {
      registrations: [registration],
    })
    expect(await screen.findByText(NO_KEY)).toBeInTheDocument()
    expect(restrictionMessageKey('PRIMARY_KEY_HIDDEN')).toBe(
      restrictionMessageKey('NO_PRIMARY_KEY'),
    )
  })

  it('shows the read-only text for a view', async () => {
    renderWithProviders(<RestrictionNotice reason="VIEW" writeConfigured />, {
      registrations: [registration],
    })
    expect(await screen.findByText('このビューは読み取りだけです。')).toBeInTheDocument()
  })

  it('shows the write configuration notice when writing is not configured', async () => {
    renderWithProviders(<RestrictionNotice reason={null} writeConfigured={false} />, {
      registrations: [registration],
    })
    expect(await screen.findByText(/書き込みの設定がありません/)).toBeInTheDocument()
  })

  it('shows nothing without a reason when writing is configured', async () => {
    renderWithProviders(
      <main>
        <RestrictionNotice reason={null} writeConfigured />
      </main>,
      { registrations: [registration] },
    )
    await screen.findByRole('main')
    expect(screen.queryByTestId('tables-restriction')).toBeNull()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderWithProviders(
      <main>
        <RestrictionNotice reason="VIEW" writeConfigured={false} />
      </main>,
      { registrations: [registration] },
    )
    await screen.findByText('このビューは読み取りだけです。')
    expect(await axe(container)).toHaveNoViolations()
  })
})
