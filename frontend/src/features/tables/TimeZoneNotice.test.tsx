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
// 表示のタイムゾーンの案内の1行のテスト（Intent 261102-timezone-ui-polish の U4、計画 Step 12、D9・D40、security-design.md 2.3）。
import { screen } from '@testing-library/react'
import { afterEach, describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import { renderWithProviders, resetDisplayTestState } from '../../app/testing/renderWithProviders'
import { registration } from './registration'
import { TimeZoneNotice } from './TimeZoneNotice'

afterEach(() => resetDisplayTestState())

function renderNotice(
  props: Parameters<typeof TimeZoneNotice>[0],
  languages: string[] = ['ja-JP'],
) {
  return renderWithProviders(<TimeZoneNotice {...props} />, {
    registrations: [registration],
    languages,
  })
}

describe('TimeZoneNotice', () => {
  it('shows the saved zone with a link to the preferences', async () => {
    const { container } = renderNotice({ timeZone: 'Asia/Tokyo', source: 'saved' })
    const notice = await screen.findByTestId('tables-time-zone-notice')
    expect(notice).toHaveTextContent('表示のタイムゾーン: Asia/Tokyo（プリファレンスで変更）')
    expect(screen.getByRole('link', { name: 'プリファレンスで変更' })).toHaveAttribute(
      'href',
      '/me/preferences',
    )
    expect(await axe(container)).toHaveNoViolations()
  })

  it('shows the browser zone text', async () => {
    renderNotice({ timeZone: 'America/New_York', source: 'browser' })
    expect(await screen.findByTestId('tables-time-zone-notice')).toHaveTextContent(
      '表示のタイムゾーン: ブラウザのタイムゾーン（America/New_York）（プリファレンスで変更）',
    )
  })

  it('shows the text for a saved zone that cannot be used', async () => {
    renderNotice({ timeZone: 'Asia/Kathmandu', source: 'saved-unresolvable' })
    expect(await screen.findByTestId('tables-time-zone-notice')).toHaveTextContent(
      '保存したタイムゾーンを使えないため、ブラウザのタイムゾーン（Asia/Kathmandu）で表示しています（プリファレンスで変更）',
    )
  })

  it('shows the texts in English', async () => {
    renderNotice({ timeZone: 'Asia/Tokyo', source: 'browser' }, ['en-US'])
    expect(await screen.findByTestId('tables-time-zone-notice')).toHaveTextContent(
      'Display time zone: browser time zone (Asia/Tokyo) (Change in Preferences)',
    )
    expect(screen.getByRole('link', { name: 'Change in Preferences' })).toBeInTheDocument()
  })

  it('renders a dangerous zone name as text and keeps the fixed link target', async () => {
    const dangerous = '<script>alert(1)</script><img src=x onerror="x">javascript:'
    const { container } = renderNotice({ timeZone: dangerous, source: 'saved' })
    expect(await screen.findByTestId('tables-time-zone-notice')).toHaveTextContent(dangerous)
    expect(container.querySelector('script')).toBeNull()
    expect(container.querySelector('img')).toBeNull()
    expect(screen.getByRole('link')).toHaveAttribute('href', '/me/preferences')
  })
})
