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
// 招待を使えないときの警告のテスト（W3、AC1.1.6・AC2.2.9、NFR7.2）。知らない理由の値と設定の値は出さない。
import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { axe } from 'vitest-axe'
import { InvitationUnavailableAlert } from './InvitationUnavailableAlert'
import { renderInvitation } from './testing/renderInvitation'

describe('InvitationUnavailableAlert', () => {
  it('shows the sentence of each single reason', () => {
    const smtp = renderInvitation(
      <InvitationUnavailableAlert
        list={{ invitationEnabled: false, unavailableReasons: ['SMTP_NOT_CONFIGURED'] }}
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent(
      '招待を使えません: メールの送り先が設定されていません。運用者に設定を依頼してください。',
    )
    smtp.unmount()
    renderInvitation(
      <InvitationUnavailableAlert
        list={{ invitationEnabled: false, unavailableReasons: ['BASE_URL_NOT_CONFIGURED'] }}
      />,
    )
    expect(screen.getByRole('alert')).toHaveTextContent(
      '招待のリンクに使うアプリの URL が設定されていません',
    )
  })

  it('lists both reasons under the title', () => {
    renderInvitation(
      <InvitationUnavailableAlert
        list={{
          invitationEnabled: false,
          unavailableReasons: ['BASE_URL_NOT_CONFIGURED', 'SMTP_NOT_CONFIGURED'],
        }}
      />,
    )
    const text = screen.getByRole('alert').textContent ?? ''
    expect(text).toContain('招待を使えません')
    expect(text.indexOf('アプリの URL')).toBeLessThan(text.indexOf('メールの送り先'))
  })

  it('shows the title and the general sentence, never the unknown value, for unknown reasons only', () => {
    renderInvitation(
      <InvitationUnavailableAlert
        list={{
          invitationEnabled: false,
          unavailableReasons: ['DATABASE_DOWN'] as unknown as ['SMTP_NOT_CONFIGURED'],
        }}
      />,
      ['en-US'],
    )
    const alert = screen.getByRole('alert')
    expect(alert).toHaveTextContent('Invitations are unavailable')
    expect(alert).toHaveTextContent('A required setting is missing.')
    expect(alert).not.toHaveTextContent('DATABASE_DOWN')
  })

  it('draws nothing when invitations are available or the list is not read yet', () => {
    const { container, unmount } = renderInvitation(
      <InvitationUnavailableAlert list={{ invitationEnabled: true, unavailableReasons: [] }} />,
    )
    expect(screen.queryByTestId('invitation-unavailable-alert')).toBeNull()
    expect(container.querySelector('[role="alert"]')).toBeNull()
    unmount()
    renderInvitation(<InvitationUnavailableAlert list={null} />)
    expect(screen.queryByTestId('invitation-unavailable-alert')).toBeNull()
  })

  it('has no accessibility violations', async () => {
    const { container } = renderInvitation(
      <InvitationUnavailableAlert
        list={{
          invitationEnabled: false,
          unavailableReasons: ['BASE_URL_NOT_CONFIGURED', 'SMTP_NOT_CONFIGURED'],
        }}
      />,
    )
    expect(await axe(container)).toHaveNoViolations()
  })
})
