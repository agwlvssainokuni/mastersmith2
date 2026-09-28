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
import { describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import type { FeatureMessages } from '../registry/types'
import { I18nProvider, useDisplayLanguage, useMessages } from './I18nProvider'
import type { DisplayLanguage } from './resolveLanguage'

// 表示言語をブラウザの言語設定から決める確かめは、表示の設定の土台（DisplaySettingsProvider・resolveDisplaySettings）の
// テストへ移した（U4 の部品 4節・8節）。ここでは受けた言語で文言を引くことを確かめる。

function Probe({ messageKey = 'home.heading' }: { messageKey?: string }) {
  const t = useMessages()
  const language = useDisplayLanguage()
  return (
    <main>
      <h1 data-testid="probe-text">{t(messageKey)}</h1>
      <p data-testid="probe-language">{language}</p>
    </main>
  )
}

function renderProbe(
  language: DisplayLanguage,
  messageKey?: string,
  featureMessages: FeatureMessages[] = [],
) {
  return render(
    <I18nProvider language={language} featureMessages={featureMessages}>
      <Probe messageKey={messageKey} />
    </I18nProvider>,
  )
}

describe('I18nProvider', () => {
  it('shows English text for the English language', () => {
    renderProbe('en')
    expect(screen.getByTestId('probe-text')).toHaveTextContent('Home')
    expect(screen.getByTestId('probe-language')).toHaveTextContent('en')
  })

  it('shows Japanese text for the Japanese language', () => {
    renderProbe('ja')
    expect(screen.getByTestId('probe-text')).toHaveTextContent('ホーム')
    expect(screen.getByTestId('probe-language')).toHaveTextContent('ja')
  })

  it('switches the text in the same render when the language changes', () => {
    const { rerender } = renderProbe('ja', 'notFound.heading')
    expect(screen.getByTestId('probe-text')).toHaveTextContent('ページが見つかりません')

    rerender(
      <I18nProvider language="en">
        <Probe messageKey="notFound.heading" />
      </I18nProvider>,
    )

    expect(screen.getByTestId('probe-text')).toHaveTextContent('Page not found')
    expect(screen.getByTestId('probe-language')).toHaveTextContent('en')
  })

  it('does not change the lang attribute of the document by itself', () => {
    document.documentElement.lang = 'ja'
    renderProbe('en')
    expect(document.documentElement.lang).toBe('ja')
  })

  it('looks up feature messages by key', () => {
    renderProbe('ja', 'users.title', [
      { ja: { 'users.title': '利用者' }, en: { 'users.title': 'Users' } },
    ])
    expect(screen.getByTestId('probe-text')).toHaveTextContent('利用者')
  })

  it('has the display theme and font size texts in both languages', () => {
    renderProbe('en', 'display.theme.system')
    expect(screen.getByTestId('probe-text')).toHaveTextContent('Match OS')
  })

  it('has no accessibility violations', async () => {
    const { container } = renderProbe('ja')
    expect(await axe(container)).toHaveNoViolations()
  })
})
