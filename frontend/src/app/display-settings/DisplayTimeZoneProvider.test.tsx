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
// 表示のタイムゾーンの提供元のテスト（Intent 261102-timezone-ui-polish の U2、BR2.12〜BR2.16、RD4・PD1・SD5）。
// 読み取りは偽物に差し替え、遅れた答えは解決を手で決める約束で作る。待つ値は waitFor で待つ。
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { deferred, type Deferred, switchableProvider } from '../testing/frameTestSupport'
import {
  fakeProvider,
  renderWithProviders,
  resetDisplayTestState,
} from '../testing/renderWithProviders'
import {
  DisplayTimeZoneProvider,
  useDisplayTimeZone,
  useDisplayTimeZoneActions,
} from './DisplayTimeZoneProvider'
import type { DisplayTimeZoneFetcher } from './displayTimeZoneApi'

afterEach(() => {
  resetDisplayTestState()
  vi.restoreAllMocks()
})

function Probe() {
  const state = useDisplayTimeZone()
  const { applySaved, reload } = useDisplayTimeZoneActions()
  return (
    <div>
      <output data-testid="status">{state.status}</output>
      <output data-testid="zone">{state.status === 'ready' ? state.timeZone : '-'}</output>
      <output data-testid="source">{state.status === 'ready' ? state.source : '-'}</output>
      <button type="button" onClick={() => applySaved('Asia/Kolkata')}>
        apply-kolkata
      </button>
      <button type="button" onClick={() => applySaved(null)}>
        apply-null
      </button>
      <button type="button" onClick={reload}>
        reload
      </button>
    </div>
  )
}

function fakeFetcher(answers: readonly Deferred<string | null>[]): {
  fetcher: DisplayTimeZoneFetcher
  calls: () => number
} {
  let count = 0
  return {
    fetcher: () => {
      const answer = answers[count]
      count += 1
      if (answer === undefined) {
        return Promise.reject(new Error('unexpected extra request'))
      }
      return answer.promise
    },
    calls: () => count,
  }
}

function renderProvider(
  fetcher: DisplayTimeZoneFetcher,
  provider = fakeProvider({ loggedIn: true, admin: false }),
  browser = 'Asia/Tokyo',
) {
  return renderWithProviders(
    <DisplayTimeZoneProvider fetcher={fetcher} browserTimeZone={() => browser}>
      <Probe />
    </DisplayTimeZoneProvider>,
    { provider },
  )
}

const status = () => screen.getByTestId('status')
const zone = () => screen.getByTestId('zone')
const source = () => screen.getByTestId('source')

describe('DisplayTimeZoneProvider', () => {
  it('throws when used outside the provider', () => {
    vi.spyOn(console, 'error').mockImplementation(() => {})
    function Bare() {
      useDisplayTimeZone()
      return null
    }
    function BareActions() {
      useDisplayTimeZoneActions()
      return null
    }

    expect(() => render(<Bare />)).toThrow()
    expect(() => render(<BareActions />)).toThrow()
  })

  it('is loading until the answer, then ready with the saved name', async () => {
    const answer = deferred<string | null>()
    const { fetcher } = fakeFetcher([answer])
    renderProvider(fetcher)

    await waitFor(() => expect(status()).toHaveTextContent('loading'))
    await act(async () => answer.resolve('America/New_York'))

    await waitFor(() => expect(status()).toHaveTextContent('ready'))
    expect(zone()).toHaveTextContent('America/New_York')
    expect(source()).toHaveTextContent('saved')
  })

  it('uses the browser time zone when nothing is saved', async () => {
    const answer = deferred<string | null>()
    const { fetcher } = fakeFetcher([answer])
    renderProvider(fetcher, undefined, 'Europe/London')

    await act(async () => answer.resolve(null))

    await waitFor(() => expect(status()).toHaveTextContent('ready'))
    expect(zone()).toHaveTextContent('Europe/London')
    expect(source()).toHaveTextContent('browser')
  })

  it('shows the browser time zone with the unresolvable mark when the saved name cannot be interpreted', async () => {
    const answer = deferred<string | null>()
    const { fetcher } = fakeFetcher([answer])
    renderProvider(fetcher)

    await act(async () => answer.resolve('Mars/Olympus'))

    await waitFor(() => expect(status()).toHaveTextContent('ready'))
    expect(zone()).toHaveTextContent('Asia/Tokyo')
    expect(source()).toHaveTextContent('saved-unresolvable')
  })

  it.each([
    ['a failed request', () => Promise.reject(new Error('network'))],
    ['a failed request of another kind', () => Promise.reject('plain')],
  ])('is error after %s', async (_label, fetcher) => {
    renderProvider(fetcher)

    await waitFor(() => expect(status()).toHaveTextContent('error'))
  })

  it('recovers from error by reload', async () => {
    const first = deferred<string | null>()
    const second = deferred<string | null>()
    const { fetcher, calls } = fakeFetcher([first, second])
    renderProvider(fetcher)
    await act(async () => first.reject(new Error('down')))
    await waitFor(() => expect(status()).toHaveTextContent('error'))

    await userEvent.setup().click(screen.getByRole('button', { name: 'reload' }))
    await waitFor(() => expect(status()).toHaveTextContent('loading'))
    await act(async () => second.resolve('Asia/Kolkata'))

    await waitFor(() => expect(status()).toHaveTextContent('ready'))
    expect(zone()).toHaveTextContent('Asia/Kolkata')
    expect(calls()).toBe(2)
  })

  it('keeps the previous value when a re-read fails while ready', async () => {
    const first = deferred<string | null>()
    const second = deferred<string | null>()
    const { fetcher } = fakeFetcher([first, second])
    renderProvider(fetcher)
    await act(async () => first.resolve('Asia/Kolkata'))
    await waitFor(() => expect(status()).toHaveTextContent('ready'))

    await userEvent.setup().click(screen.getByRole('button', { name: 'reload' }))
    await act(async () => second.reject(new Error('down')))

    await waitFor(() => expect(status()).toHaveTextContent('ready'))
    expect(zone()).toHaveTextContent('Asia/Kolkata')
  })

  it('discards an old answer when a newer request has been made, whatever the order of answers', async () => {
    const first = deferred<string | null>()
    const second = deferred<string | null>()
    const { fetcher } = fakeFetcher([first, second])
    renderProvider(fetcher)
    await userEvent.setup().click(await screen.findByRole('button', { name: 'reload' }))

    await act(async () => second.resolve('Asia/Kolkata'))
    await waitFor(() => expect(zone()).toHaveTextContent('Asia/Kolkata'))
    await act(async () => first.resolve('America/New_York'))

    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(zone()).toHaveTextContent('Asia/Kolkata')
  })

  it('discards a pending answer when applySaved is called, and recovers from error with it', async () => {
    const first = deferred<string | null>()
    const { fetcher } = fakeFetcher([first])
    renderProvider(fetcher)

    await userEvent.setup().click(await screen.findByRole('button', { name: 'apply-kolkata' }))
    await waitFor(() => expect(zone()).toHaveTextContent('Asia/Kolkata'))
    await act(async () => first.resolve('America/New_York'))

    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(zone()).toHaveTextContent('Asia/Kolkata')
    expect(source()).toHaveTextContent('saved')
  })

  it('applySaved recovers from error without a request', async () => {
    const { fetcher, calls } = fakeFetcher([])
    renderProvider(fetcher)
    await waitFor(() => expect(status()).toHaveTextContent('error'))

    await userEvent.setup().click(screen.getByRole('button', { name: 'apply-null' }))

    await waitFor(() => expect(status()).toHaveTextContent('ready'))
    expect(source()).toHaveTextContent('browser')
    expect(calls()).toBe(1)
  })

  it('drops the value and discards a pending answer when logged out, and keeps nothing for another user', async () => {
    const first = deferred<string | null>()
    const second = deferred<string | null>()
    const { fetcher } = fakeFetcher([first, second])
    const switcher = switchableProvider({ loggedIn: true, admin: false })
    renderProvider(fetcher, switcher.provider)
    await act(async () => first.resolve('Asia/Kolkata'))
    await waitFor(() => expect(zone()).toHaveTextContent('Asia/Kolkata'))

    await act(async () => switcher.set({ loggedIn: false, admin: false }))
    await waitFor(() => expect(status()).toHaveTextContent('loading'))
    expect(zone()).toHaveTextContent('-')

    await act(async () => switcher.set({ loggedIn: true, admin: false }))
    await waitFor(() => expect(status()).toHaveTextContent('loading'))
    expect(zone()).toHaveTextContent('-')
    await act(async () => second.resolve('Europe/London'))
    await waitFor(() => expect(zone()).toHaveTextContent('Europe/London'))
  })

  it('does not request anything while logged out', async () => {
    const { fetcher, calls } = fakeFetcher([])
    renderProvider(fetcher, fakeProvider({ loggedIn: false, admin: false }))

    await new Promise((resolve) => setTimeout(resolve, 20))
    expect(calls()).toBe(0)
    expect(status()).toHaveTextContent('loading')
  })

  it('requests once after login and not again on re-render, saving or moving', async () => {
    const answer = deferred<string | null>()
    const { fetcher, calls } = fakeFetcher([answer])
    const { rerender } = renderProvider(fetcher)
    await act(async () => answer.resolve('Asia/Kolkata'))
    await waitFor(() => expect(status()).toHaveTextContent('ready'))

    rerender(
      <DisplayTimeZoneProvider fetcher={fetcher} browserTimeZone={() => 'Asia/Tokyo'}>
        <Probe />
      </DisplayTimeZoneProvider>,
    )
    await userEvent.setup().click(screen.getByRole('button', { name: 'apply-kolkata' }))

    expect(calls()).toBe(1)
  })

  it('never writes the value to the browser storage, the URL or the console', async () => {
    const log = vi.spyOn(console, 'log').mockImplementation(() => {})
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => {})
    const answer = deferred<string | null>()
    const { fetcher } = fakeFetcher([answer])
    renderProvider(fetcher)
    localStorage.clear()
    sessionStorage.clear()

    await act(async () => answer.resolve('Asia/Kolkata'))
    await waitFor(() => expect(status()).toHaveTextContent('ready'))

    expect(JSON.stringify({ ...localStorage })).not.toContain('Kolkata')
    expect(JSON.stringify({ ...sessionStorage })).not.toContain('Kolkata')
    expect(window.location.href).not.toContain('Kolkata')
    expect(log).not.toHaveBeenCalled()
    expect(warn).not.toHaveBeenCalled()
  })
})
