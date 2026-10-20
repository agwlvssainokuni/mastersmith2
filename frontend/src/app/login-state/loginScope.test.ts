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
// ログインの範囲の知らせのテスト（Intent 261016-business-data-views の U3 の L4、計画 4.8 の P-1）。
import { describe, expect, it, vi } from 'vitest'
import { notifyLoginScopeChanged, subscribeLoginScope } from './loginScope'

describe('loginScope', () => {
  it('calls every subscribed listener on each notification', () => {
    const first = vi.fn()
    const second = vi.fn()
    const stopFirst = subscribeLoginScope(first)
    const stopSecond = subscribeLoginScope(second)
    notifyLoginScopeChanged()
    notifyLoginScopeChanged()
    expect(first).toHaveBeenCalledTimes(2)
    expect(second).toHaveBeenCalledTimes(2)
    stopFirst()
    stopSecond()
  })

  it('stops calling a listener after unsubscribing', () => {
    const listener = vi.fn()
    const stop = subscribeLoginScope(listener)
    stop()
    notifyLoginScopeChanged()
    expect(listener).not.toHaveBeenCalled()
  })

  it('lets a listener unsubscribe itself during a notification without skipping others', () => {
    const calls: string[] = []
    const stopSelf: { stop: () => void } = { stop: () => {} }
    stopSelf.stop = subscribeLoginScope(() => {
      calls.push('self')
      stopSelf.stop()
    })
    const stopOther = subscribeLoginScope(() => calls.push('other'))
    notifyLoginScopeChanged()
    notifyLoginScopeChanged()
    expect(calls).toEqual(['self', 'other', 'other'])
    stopOther()
  })
})
