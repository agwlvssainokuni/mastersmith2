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
// 送信の待ちの案内と、送信中の2回目の呼び出しを捨てる口のテスト（Intent 261004-role-menu の U6 の L5、NFR2.10）。
// groupadmin・roleadmin・roletransfer の複製にあった3つのテストを、共有のフックのテスト1つにまとめた（Intent
// 261015-deps-ui-carryover の FR6.1・FR6.2。観点は前の Build and Test の Q3 C、role-admin-ui の R-02 のまま）。
// 時計は Vitest の偽の時計で進め、実時刻と sleep に頼らない。
import { act, renderHook } from '@testing-library/react'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { SLOW_NOTICE_MS, useSlowNotice } from './useSlowNotice'

/** 答えを外から返す約束を作る（src/shared/ は機能の testing を読まないため、このテストの中に置く）。 */
function deferred<T>(): {
  promise: Promise<T>
  resolve: (value: T) => void
  reject: (error: unknown) => void
} {
  let resolve!: (value: T) => void
  let reject!: (error: unknown) => void
  const promise = new Promise<T>((res, rej) => {
    resolve = res
    reject = rej
  })
  return { promise, resolve, reject }
}

describe('useSlowNotice', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    vi.useRealTimers()
  })

  it('shows the slow notice at 5 seconds but not at 4.9 seconds, and clears it on the answer', async () => {
    const { result } = renderHook(() => useSlowNotice())
    const answer = deferred<string>()
    let sent: Promise<string> | undefined
    act(() => {
      sent = result.current.run(() => answer.promise)
    })
    expect(result.current.submitting).toBe(true)
    act(() => {
      vi.advanceTimersByTime(SLOW_NOTICE_MS - 100)
    })
    expect(result.current.slow).toBe(false)
    act(() => {
      vi.advanceTimersByTime(100)
    })
    expect(result.current.slow).toBe(true)
    await act(async () => {
      answer.resolve('done')
      await sent
    })
    expect(result.current.slow).toBe(false)
    expect(result.current.submitting).toBe(false)
  })

  it('ignores a second run while the first one is in flight', async () => {
    const { result } = renderHook(() => useSlowNotice())
    const answer = deferred<void>()
    const task = vi.fn(() => answer.promise)
    const second = vi.fn(() => Promise.resolve())
    let first: Promise<void> | undefined
    act(() => {
      first = result.current.run(task)
    })
    let dropped: Promise<void> | undefined = Promise.resolve()
    act(() => {
      dropped = result.current.run(second)
    })
    expect(dropped).toBeUndefined()
    expect(second).not.toHaveBeenCalled()
    await act(async () => {
      answer.resolve()
      await first
    })
    expect(task).toHaveBeenCalledTimes(1)
  })

  it('clears the state and rethrows when the task fails', async () => {
    const { result } = renderHook(() => useSlowNotice())
    const failure = new Error('failed')
    let caught: unknown
    await act(async () => {
      await result.current
        .run(() => Promise.reject(failure))
        ?.catch((error: unknown) => {
          caught = error
        })
    })
    expect(caught).toBe(failure)
    expect(result.current.submitting).toBe(false)
    expect(result.current.slow).toBe(false)
  })

  it('treats a task that throws synchronously as a failure and accepts the next run', async () => {
    const { result } = renderHook(() => useSlowNotice())
    let caught: unknown
    await act(async () => {
      await result.current
        .run<void>(() => {
          throw new Error('sync')
        })
        ?.catch((error: unknown) => {
          caught = error
        })
    })
    expect(caught).toBeInstanceOf(Error)
    const next = vi.fn(() => Promise.resolve('ok'))
    await act(async () => {
      await result.current.run(next)
    })
    expect(next).toHaveBeenCalledTimes(1)
  })

  it('does not update the state after unmount, and advancing the clock calls nothing', () => {
    const { result, unmount } = renderHook(() => useSlowNotice())
    const answer = deferred<void>()
    const task = vi.fn(() => answer.promise)
    act(() => {
      void result.current.run(task)
    })
    unmount()
    expect(() => {
      vi.advanceTimersByTime(SLOW_NOTICE_MS * 3)
    }).not.toThrow()
    expect(task).toHaveBeenCalledTimes(1)
    expect(vi.getTimerCount()).toBe(0)
  })
})
