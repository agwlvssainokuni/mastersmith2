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
// 画面のテスト（Vitest）の設定。vite.config.ts の設定（resolve.dedupe を含む）を引き継ぐ。
// - テストは対象と同じ場所の *.test.ts / *.test.tsx。e2e/（Playwright）は対象外。
// - カバレッジの下限は 行 80%・分岐 70%。計測から外すのは入口（src/main.tsx）と型の宣言だけ
//   （テストのファイル自体は計測の対象ではない）。
import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config'

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      setupFiles: ['./vitest.setup.ts'],
      include: ['src/**/*.test.{ts,tsx}'],
      exclude: ['e2e/**', 'node_modules/**', 'dist/**'],
      css: false,
      coverage: {
        provider: 'v8',
        include: ['src/**/*.{ts,tsx}'],
        exclude: ['src/main.tsx', 'src/**/*.d.ts', 'src/**/*.test.{ts,tsx}'],
        reporter: ['text', 'html', 'json-summary'],
        reportsDirectory: 'coverage',
        thresholds: {
          lines: 80,
          branches: 70,
        },
      },
    },
  }),
)
