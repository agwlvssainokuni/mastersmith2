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
import '@testing-library/jest-dom/vitest'
import { afterEach, expect, vi } from 'vitest'
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

afterEach(() => {
  cleanup()
  vi.restoreAllMocks()
})
