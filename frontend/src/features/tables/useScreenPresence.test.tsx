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
// 送った画面にまだ居るかの確かめ（useScreenPresence。コードのレビューの R-01）の単体のテスト。data router で描く。
import { act, render } from '@testing-library/react'
import { useEffect } from 'react'
import { createMemoryRouter, RouterProvider } from 'react-router'
import { describe, expect, it } from 'vitest'
import { useScreenPresence } from './useScreenPresence'

const holder: { begin: () => () => boolean } = { begin: () => () => false }

function Probe() {
  const begin = useScreenPresence()
  useEffect(() => {
    holder.begin = begin
  }, [begin])
  return <p>probe</p>
}

function begin(): () => boolean {
  return holder.begin()
}

function renderProbe() {
  const router = createMemoryRouter(
    [
      { path: '/form', element: <Probe /> },
      { path: '/other', element: <p>other</p> },
    ],
    { initialEntries: ['/form'] },
  )
  const view = render(<RouterProvider router={router} />)
  return { router, view }
}

describe('useScreenPresence', () => {
  it('stays true while the screen and the location do not change', () => {
    renderProbe()
    const stillHere = begin()
    expect(stillHere()).toBe(true)
    expect(stillHere()).toBe(true)
  })

  it('becomes false after the location changes even on the same screen', async () => {
    const { router } = renderProbe()
    const stillHere = begin()
    await act(async () => {
      await router.navigate('/form?page=2')
    })
    expect(stillHere()).toBe(false)
    expect(begin()()).toBe(true)
  })

  it('becomes false after leaving to another screen', async () => {
    const { router } = renderProbe()
    const stillHere = begin()
    await act(async () => {
      await router.navigate('/other')
    })
    expect(stillHere()).toBe(false)
  })

  it('becomes false after the screen is unmounted', () => {
    const { view } = renderProbe()
    const stillHere = begin()
    view.unmount()
    expect(stillHere()).toBe(false)
  })
})
