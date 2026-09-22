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
import { describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import { stubBrowserLanguages } from '../testing/renderWithProviders'
import { I18nProvider, useDisplayLanguage, useMessages } from './I18nProvider'

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

function renderProbe(messageKey?: string, featureMessages = []) {
  return render(
    <I18nProvider featureMessages={featureMessages}>
      <Probe messageKey={messageKey} />
    </I18nProvider>,
  )
}

describe('I18nProvider', () => {
  it('shows English text for an English browser setting', () => {
    stubBrowserLanguages(['en-US', 'en'])
    renderProbe()
    expect(screen.getByTestId('probe-text')).toHaveTextContent('Home')
    expect(screen.getByTestId('probe-language')).toHaveTextContent('en')
  })

  it('shows Japanese text when the browser prefers neither Japanese nor English', () => {
    stubBrowserLanguages(['fr-FR', 'de'])
    renderProbe()
    expect(screen.getByTestId('probe-text')).toHaveTextContent('ホーム')
    expect(screen.getByTestId('probe-language')).toHaveTextContent('ja')
  })

  it('sets the lang attribute of the document to the display language', () => {
    stubBrowserLanguages(['en-GB'])
    renderProbe()
    expect(document.documentElement.lang).toBe('en')
    stubBrowserLanguages(['ja'])
    renderProbe()
    expect(document.documentElement.lang).toBe('ja')
  })

  it('uses navigator.language when navigator.languages is empty', () => {
    vi.spyOn(window.navigator, 'languages', 'get').mockReturnValue([])
    vi.spyOn(window.navigator, 'language', 'get').mockReturnValue('en-US')
    renderProbe('notFound.heading')
    expect(screen.getByTestId('probe-text')).toHaveTextContent('Page not found')
  })

  it('looks up feature messages by key', () => {
    stubBrowserLanguages(['ja'])
    render(
      <I18nProvider
        featureMessages={[{ ja: { 'users.title': '利用者' }, en: { 'users.title': 'Users' } }]}
      >
        <Probe messageKey="users.title" />
      </I18nProvider>,
    )
    expect(screen.getByTestId('probe-text')).toHaveTextContent('利用者')
  })

  it('has no accessibility violations', async () => {
    stubBrowserLanguages(['ja'])
    const { container } = renderProbe()
    expect(await axe(container)).toHaveNoViolations()
  })
})
