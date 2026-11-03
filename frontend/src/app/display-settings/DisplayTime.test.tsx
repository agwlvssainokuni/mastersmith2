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
import { act, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { axe } from 'vitest-axe'
import * as timeZone from '../../shared/format/timeZone'
import { deferred } from '../testing/frameTestSupport'
import {
  fakeProvider,
  renderWithProviders,
  resetDisplayTestState,
} from '../testing/renderWithProviders'
import { DisplayTime, TimeValue, useDisplayTimeText } from './DisplayTime'
import { DisplayTimeZoneErrorBar } from './DisplayTimeZoneErrorBar'
import {
  DisplayTimeZoneProvider,
  useDisplayTimeZoneActions,
  type DisplayTimeZoneActions,
} from './DisplayTimeZoneProvider'

afterEach(() => {
  resetDisplayTestState()
  vi.restoreAllMocks()
})

const ISO = '2026-09-24T01:15:30Z'

describe('DisplayTime', () => {
  it('draws a time element with the minute form when ready', () => {
    renderWithProviders(<DisplayTime iso={ISO} />, {
      displayTimeZone: { timeZone: 'Asia/Kolkata' },
    })

    const time = screen.getByText('2026-09-24 06:45 +05:30')
    expect(time.tagName).toBe('TIME')
    expect(time).toHaveAttribute('datetime', ISO)
  })

  it('draws a short note and does not format while loading or on error', () => {
    const spy = vi.spyOn(timeZone, 'formatInstant')
    const first = renderWithProviders(<DisplayTime iso={ISO} />, {
      displayTimeZone: { state: { status: 'loading' } },
    })
    expect(screen.getByText('読み込み中')).toHaveClass('display-time-note')
    first.unmount()
    renderWithProviders(<DisplayTime iso={ISO} />, {
      displayTimeZone: { state: { status: 'error' } },
    })

    expect(screen.getByText('時刻を表示できません')).toBeInTheDocument()
    expect(document.querySelector('time')).toBeNull()
    expect(spy).not.toHaveBeenCalled()
  })

  it('formats once per time and no more than the number of times plus one on a page', () => {
    const spy = vi.spyOn(timeZone, 'formatInstant')
    renderWithProviders(
      <>
        <DisplayTime iso="2026-09-24T01:15:30Z" />
        <DisplayTime iso="2026-09-25T01:15:30Z" />
        <DisplayTime iso="2026-09-26T01:15:30Z" />
      </>,
    )

    expect(spy.mock.calls.length).toBeLessThanOrEqual(3 + 1)
    expect(spy).toHaveBeenCalledTimes(3)
  })

  it('formats once per time when the text is shared with a label', () => {
    function Row() {
      const text = useDisplayTimeText()
      const at = text(ISO)
      return (
        <p aria-label={at}>
          <TimeValue iso={ISO} text={at} />
        </p>
      )
    }
    const spy = vi.spyOn(timeZone, 'formatInstant')
    renderWithProviders(<Row />)

    expect(spy).toHaveBeenCalledTimes(1)
  })

  it('shows an English note for an English browser', () => {
    renderWithProviders(<DisplayTime iso={ISO} />, {
      languages: ['en-US'],
      displayTimeZone: { state: { status: 'loading' } },
    })

    expect(screen.getByText('Loading')).toBeInTheDocument()
  })

  it('draws a hostile value as text only', () => {
    renderWithProviders(<DisplayTime iso="<img src=x onerror=alert(1)>" />)

    expect(document.querySelectorAll('img')).toHaveLength(0)
    expect(screen.getByText('<img src=x onerror=alert(1)>')).toBeInTheDocument()
  })

  it('does not depend on the machine default time zone', () => {
    const original = process.env.TZ
    try {
      const texts = ['Asia/Tokyo', 'America/Los_Angeles', 'UTC'].map((machine) => {
        process.env.TZ = machine
        const view = renderWithProviders(<DisplayTime iso={ISO} />, {
          displayTimeZone: { timeZone: 'Europe/London' },
        })
        const text = document.querySelector('time')?.textContent
        view.unmount()
        return text
      })
      expect(new Set(texts)).toEqual(new Set(['2026-09-24 02:15 +01:00']))
    } finally {
      if (original === undefined) {
        delete process.env.TZ
      } else {
        process.env.TZ = original
      }
    }
  })

  it('has no accessibility violations in the three states', async () => {
    for (const state of [
      { status: 'ready', timeZone: 'Asia/Tokyo', source: 'saved' } as const,
      { status: 'loading' } as const,
      { status: 'error' } as const,
    ]) {
      const view = renderWithProviders(<DisplayTime iso={ISO} />, { displayTimeZone: { state } })
      expect(await axe(view.container)).toHaveNoViolations()
      view.unmount()
    }
  })
})

describe('DisplayTimeZoneErrorBar', () => {
  it('draws nothing unless the state is error', () => {
    const view = renderWithProviders(<DisplayTimeZoneErrorBar />, {
      displayTimeZone: { state: { status: 'loading' } },
    })
    expect(screen.queryByTestId('display-time-zone-error')).toBeNull()
    view.unmount()
    renderWithProviders(<DisplayTimeZoneErrorBar />)
    expect(screen.queryByTestId('display-time-zone-error')).toBeNull()
  })

  it('shows the notice and a reload button on error, and the button calls reload', async () => {
    const reload = vi.fn()
    renderWithProviders(<DisplayTimeZoneErrorBar />, {
      displayTimeZone: { state: { status: 'error' }, actions: { reload } },
    })

    expect(screen.getByRole('alert')).toHaveTextContent('表示のタイムゾーンを読み込めませんでした')
    await userEvent.setup().click(screen.getByRole('button', { name: 'もう一度読み込む' }))
    expect(reload).toHaveBeenCalledTimes(1)
  })

  it('has no accessibility violations on error', async () => {
    const view = renderWithProviders(<DisplayTimeZoneErrorBar />, {
      displayTimeZone: { state: { status: 'error' } },
    })

    expect(await axe(view.container)).toHaveNoViolations()
  })
})

describe('DisplayTime with the real provider', () => {
  function Switch() {
    const actions: DisplayTimeZoneActions = useDisplayTimeZoneActions()
    return (
      <button type="button" onClick={() => actions.applySaved('Asia/Kolkata')}>
        apply
      </button>
    )
  }

  it('switches an open time when the preferences screen tells the saved name', async () => {
    const answer = deferred<string | null>()
    renderWithProviders(
      <DisplayTimeZoneProvider fetcher={() => answer.promise} browserTimeZone={() => 'Asia/Tokyo'}>
        <DisplayTime iso={ISO} />
        <Switch />
      </DisplayTimeZoneProvider>,
      { provider: fakeProvider({ loggedIn: true, admin: false }) },
    )
    await screen.findByText('読み込み中')
    await act(async () => answer.resolve(null))
    await waitFor(() => expect(screen.getByText('2026-09-24 10:15 +09:00')).toBeInTheDocument())

    await userEvent.setup().click(screen.getByRole('button', { name: 'apply' }))

    expect(await screen.findByText('2026-09-24 06:45 +05:30')).toBeInTheDocument()
  })

  it('shows the time with the browser offset when the saved name cannot be interpreted', async () => {
    renderWithProviders(
      <DisplayTimeZoneProvider
        fetcher={() => Promise.resolve('Mars/Olympus')}
        browserTimeZone={() => 'America/New_York'}
      >
        <DisplayTime iso={ISO} />
      </DisplayTimeZoneProvider>,
      { provider: fakeProvider({ loggedIn: true, admin: false }) },
    )

    expect(await screen.findByText('2026-09-23 21:15 -04:00')).toBeInTheDocument()
  })

  it('reads as plain render without a provider placed by the test helper', () => {
    render(<p>none</p>)
    expect(screen.getByText('none')).toBeInTheDocument()
  })
})

describe('DisplayTime cost', () => {
  it('formats 10,000 times in 500 ms (0.05 ms each on average)', () => {
    const started = performance.now()
    for (let i = 0; i < 10_000; i++) {
      timeZone.formatInstant(
        new Date(Date.UTC(2026, 0, 1, 0, 0, i % 3600)).toISOString(),
        'Asia/Tokyo',
      )
    }
    const elapsed = performance.now() - started

    expect(elapsed).toBeLessThanOrEqual(500)
  })
})
