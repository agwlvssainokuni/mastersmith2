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
// Vitest の共通の準備。jest-dom と vitest-axe の照合を登録し、テストごとに描画と差し替えを片付ける。
//
// axe の規則のうち、描いた後の CSS を要る color-contrast（色のコントラスト、canvas で文字の形を見る）と
// link-in-text-block（擬似要素の CSS を見る）は、jsdom（css: false）では判定できず「未確定」になるうえ、
// jsdom の未実装の警告（HTMLCanvasElement の getContext・擬似要素つきの getComputedStyle）を出すため、
// ここで止める。コントラストは実際のブラウザの E2E（e2e/100-app-text-contrast.e2e.ts）で確かめる。
//
// テストの中で予約されたまま残った時計（window.setTimeout・setInterval）は、描画の片付けの後にすべて取り消す。
// 外された部品の予約（make-you-chic-ui の Tooltip の 300 ms の表示の予約は外されても取り消されない）が jsdom の片付けの
// 後に走ると、react-dom が window を読んで「window is not defined」の処理されない誤りになり、すべてのテストが通っても
// Vitest が失敗にするため（Intent 261029-reference-lookup-cleanup の CI Pipeline、run 41264314230）。
// 包むのは本物の時計だけで、偽の時計（vi.useFakeTimers）はこの包みの上に重なり、vi.useRealTimers でこの包みに戻る。
import '@testing-library/jest-dom/vitest'
import { afterEach, beforeEach, expect, vi } from 'vitest'
import { configureAxe } from 'vitest-axe'
import * as axeMatchers from 'vitest-axe/matchers'
import { cleanup } from '@testing-library/react'

expect.extend(axeMatchers)

configureAxe({
  globalOptions: {
    rules: [
      { id: 'color-contrast', enabled: false },
      { id: 'link-in-text-block', enabled: false },
    ],
  },
})

/** テストの中で予約した時計の記録の操作 */
interface PendingTimers {
  /** 記録を忘れる（取り消さない） */
  forget(): void
  /** 記録した時計のうち、まだ走っていない（取り消されていない）ものをすべて取り消す */
  cancelAll(): void
}

/**
 * target の setTimeout・setInterval を包み、予約したまま走っていない時計を記録する。
 * window の型は Node の時計の型と重なり、そのままでは代入できないため、Object.assign で差し替える。
 */
function trackPendingTimers(target: Window): PendingTimers {
  const pendingTimeouts = new Set<number>()
  const pendingIntervals = new Set<number>()
  const realSetTimeout = target.setTimeout
  const realClearTimeout = target.clearTimeout
  const realSetInterval = target.setInterval
  const realClearInterval = target.clearInterval
  Object.assign(target, {
    setTimeout: (handler: TimerHandler, timeout?: number, ...args: unknown[]): number => {
      // 文字列の処理は包めないため、記録せずにそのまま渡す
      if (typeof handler !== 'function') return realSetTimeout(handler, timeout, ...args)
      const id = realSetTimeout(() => {
        pendingTimeouts.delete(id)
        handler(...args)
      }, timeout)
      pendingTimeouts.add(id)
      return id
    },
    clearTimeout: (id?: number): void => {
      if (id !== undefined) pendingTimeouts.delete(id)
      realClearTimeout(id)
    },
    setInterval: (handler: TimerHandler, timeout?: number, ...args: unknown[]): number => {
      const id = realSetInterval(handler, timeout, ...args)
      pendingIntervals.add(id)
      return id
    },
    clearInterval: (id?: number): void => {
      if (id !== undefined) pendingIntervals.delete(id)
      realClearInterval(id)
    },
  })
  return {
    forget() {
      pendingTimeouts.clear()
      pendingIntervals.clear()
    },
    cancelAll() {
      for (const id of pendingTimeouts) realClearTimeout(id)
      for (const id of pendingIntervals) realClearInterval(id)
      this.forget()
    },
  }
}

// 包むのは jsdom の環境だけ（node の環境のテスト（@vitest-environment node）には、片付けで消える window が無い）
const pendingTimers: PendingTimers =
  typeof window === 'undefined' ? { forget() {}, cancelAll() {} } : trackPendingTimers(window)

beforeEach(() => {
  // テストの前（beforeAll など）に予約した時計は、このテストの後片付けの対象にしない
  pendingTimers.forget()
})

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
  pendingTimers.cancelAll()
})
