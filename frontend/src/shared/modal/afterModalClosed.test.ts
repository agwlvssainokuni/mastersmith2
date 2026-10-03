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
// Modal が閉じ終わった後に処理を始める口のテスト（G1）。背景の inert は make-you-chic-ui の ModalStackProvider と同じく
// body の直下の要素に付け外しして作る。
import { afterEach, describe, expect, it, vi } from 'vitest'
import { runAfterModalClosed } from './afterModalClosed'

const created: HTMLElement[] = []

function backgroundElement(): HTMLElement {
  const element = document.createElement('div')
  document.body.appendChild(element)
  created.push(element)
  return element
}

afterEach(() => {
  for (const element of created.splice(0)) {
    element.remove()
  }
})

describe('runAfterModalClosed', () => {
  it('runs the callback at once when no background element is inert', () => {
    backgroundElement()
    const callback = vi.fn()
    const cancel = runAfterModalClosed(callback)
    expect(callback).toHaveBeenCalledTimes(1)
    cancel()
    expect(callback).toHaveBeenCalledTimes(1)
  })

  it('waits until every background element is no longer inert', async () => {
    const first = backgroundElement()
    const second = backgroundElement()
    first.setAttribute('inert', '')
    second.setAttribute('inert', '')
    const callback = vi.fn()
    runAfterModalClosed(callback)
    expect(callback).not.toHaveBeenCalled()
    first.removeAttribute('inert')
    await Promise.resolve()
    expect(callback).not.toHaveBeenCalled()
    second.removeAttribute('inert')
    await vi.waitFor(() => expect(callback).toHaveBeenCalledTimes(1))
  })

  it('runs after the focus restored in the same task that lifted inert', async () => {
    const page = backgroundElement()
    const opener = document.createElement('button')
    const heading = document.createElement('h2')
    heading.tabIndex = -1
    page.append(opener, heading)
    page.setAttribute('inert', '')
    runAfterModalClosed(() => heading.focus())
    // ModalStackProvider と同じく、inert を外した直後に同じ処理の中で開く前の要素へフォーカスを戻す。
    page.removeAttribute('inert')
    opener.focus()
    await vi.waitFor(() => expect(document.activeElement).toBe(heading))
  })

  it('does not run the callback after it is cancelled', async () => {
    const page = backgroundElement()
    page.setAttribute('inert', '')
    const callback = vi.fn()
    const cancel = runAfterModalClosed(callback)
    cancel()
    page.removeAttribute('inert')
    await Promise.resolve()
    await Promise.resolve()
    expect(callback).not.toHaveBeenCalled()
  })

  it('runs once when the inert element itself is removed from the document', async () => {
    const page = backgroundElement()
    page.setAttribute('inert', '')
    const callback = vi.fn()
    runAfterModalClosed(callback)
    page.remove()
    await vi.waitFor(() => expect(callback).toHaveBeenCalledTimes(1))
    backgroundElement().setAttribute('inert', '')
    await Promise.resolve()
    expect(callback).toHaveBeenCalledTimes(1)
  })
})
