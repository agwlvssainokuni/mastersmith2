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
// ログアウトの印のテスト（Intent 261004-role-menu の U7 の L11、security-design.md 5.1・5.3、依頼者の決定 G1: A）。
import { act, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it } from 'vitest'
import { AppRouter } from '../../app/routing/AppRouter'
import { NavigateButton } from '../../app/testing/frameTestSupport'
import { renderWithProviders, resetDisplayTestState } from '../../app/testing/renderWithProviders'
import type { FeatureRegistration } from '../../app/registry/types'
import { loginStateProvider } from './loginStateProvider'
import { consumeLogoutIntent, markLogoutIntent, peekLogoutIntent } from './logoutIntent'

afterEach(() => {
  consumeLogoutIntent()
  resetDisplayTestState()
})

describe('logoutIntent', () => {
  it('marks, peeks without clearing and consumes once', () => {
    expect(peekLogoutIntent()).toBe(false)
    markLogoutIntent()
    expect(peekLogoutIntent()).toBe(true)
    expect(peekLogoutIntent()).toBe(true)
    expect(consumeLogoutIntent()).toBe(true)
    expect(consumeLogoutIntent()).toBe(false)
    expect(peekLogoutIntent()).toBe(false)
  })

  it('is cleared through the provider hook of the shell', () => {
    markLogoutIntent()
    loginStateProvider.clearLogoutIntent?.()
    expect(peekLogoutIntent()).toBe(false)
  })

  describe('with the router of the shell', () => {
    function Stay() {
      return <NavigateButton to="/other" label="move" />
    }
    const feature: FeatureRegistration = {
      featureId: 'probe',
      routes: [
        { path: '/logout', screen: Stay, layout: 'STANDALONE', access: 'LOGGED_IN' },
        { path: '/stay', screen: Stay, layout: 'STANDALONE', access: 'LOGGED_IN' },
        { path: '/other', screen: Stay, layout: 'STANDALONE', access: 'LOGGED_IN' },
      ],
    }
    const provider = {
      getLoginState: () => ({ loggedIn: true, admin: false }),
      clearLogoutIntent: () => {
        consumeLogoutIntent()
      },
    }

    it('keeps the intent on /logout', async () => {
      markLogoutIntent()
      renderWithProviders(<AppRouter />, { route: '/logout', registrations: [feature], provider })
      await screen.findByRole('button', { name: 'move' })
      await act(async () => {
        await Promise.resolve()
      })
      expect(peekLogoutIntent()).toBe(true)
    })

    it('leaves the intent while staying on the same path and clears it on the next path change', async () => {
      const user = userEvent.setup()
      renderWithProviders(<AppRouter />, { route: '/stay', registrations: [feature], provider })
      await screen.findByRole('button', { name: 'move' })
      // ［留まる］と同じく、印を立てた後に道が変わらない場合（G1: A）
      markLogoutIntent()
      await act(async () => {
        await Promise.resolve()
      })
      expect(peekLogoutIntent()).toBe(true)
      await user.click(screen.getByRole('button', { name: 'move' }))
      await waitFor(() => expect(screen.getByTestId('location')).toHaveTextContent('/other'))
      expect(peekLogoutIntent()).toBe(false)
    })
  })
})
