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
// - テストは対象と同じ場所の *.test.ts / *.test.tsx。e2e/ の Playwright の E2E（*.e2e.ts）は対象外。
//   E2E の補助の純粋な部品の単体のテスト（e2e/support/**/*.test.ts）は対象にする（Intent 261016-business-data-views の U4 の
//   基盤の設計の Q2: A）。カバレッジの対象（src/）は変えない。
// - カバレッジの下限は 行 80%・分岐 70%。計測から外すのは入口（src/main.tsx）と型の宣言だけ
//   （テストのファイル自体は計測の対象ではない）。
// - ESLint の設定を読むテスト（src/eslintImportRules.test.ts・src/eslintTimeZoneRule.test.ts）は、ほかのテストより先に、1つだけで流す（project の groupOrder）。
//   設定の読み込み（@typescript-eslint/parser が TypeScript の本体を読む。単独で約 0.35 秒）は1つの CPU で重く、jsdom の
//   ほかのテストのファイルと同時に始まると、PC の負荷の下で beforeAll の上限（10 秒）を超えて落ちた（Intent
//   261016-business-data-views の B3・B7、Build and Test で負荷をかけて再現した）。時間の上限は延ばしていない。
import { defineConfig, mergeConfig } from 'vitest/config'
import viteConfig from './vite.config.ts'

/**
 * frontend/ の外にある、k6 の結果の JWT を伏せる道具（perf/redact-k6-evidence.mjs）。テストは src/perfEvidenceRedactor*.test.ts。
 * 全体の下限（行 80%・分岐 70%）の対象に入れる（Intent 261101-maintenance-update の T1）。frontend/ の外のファイルを計測するため
 * coverage.allowExternal を有効にする（計測の対象は include の一覧だけのまま）。
 */
const PERF_EVIDENCE_REDACTOR = '../perf/redact-k6-evidence.mjs'

/** ほかのテストより先に、1つだけで流すテストのファイル（ESLint の設定を読む 2 つ。表示のタイムゾーンの規則の確かめを含む） */
const ESLINT_CONFIG_TESTS = ['src/eslintImportRules.test.ts', 'src/eslintTimeZoneRule.test.ts']

export default mergeConfig(
  viteConfig,
  defineConfig({
    test: {
      environment: 'jsdom',
      setupFiles: ['./vitest.setup.ts'],
      css: false,
      projects: [
        {
          extends: true,
          test: {
            name: 'eslint-config',
            include: ESLINT_CONFIG_TESTS,
            sequence: { groupOrder: 0 },
          },
        },
        {
          extends: true,
          test: {
            name: 'unit',
            include: ['src/**/*.test.{ts,tsx}', 'e2e/support/**/*.test.ts'],
            exclude: ['e2e/**/*.e2e.ts', 'node_modules/**', 'dist/**', ...ESLINT_CONFIG_TESTS],
            sequence: { groupOrder: 1 },
          },
        },
      ],
      coverage: {
        provider: 'v8',
        allowExternal: true,
        include: ['src/**/*.{ts,tsx}', PERF_EVIDENCE_REDACTOR],
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
