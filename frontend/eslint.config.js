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
// ESLint は react-hooks の推奨ルールと、oxlint では書けないルール（構文の禁止、no-implied-eval）だけを受け持つ。
// ほかのルール（正しさ・TypeScript・React・アクセシビリティ・セキュリティ）は oxlint（.oxlintrc.json）が受け持つ。
// 設定ファイル（vite.config.ts など）は道具の決まりで default のエクスポートが要るため、構文の禁止は src/ と e2e/ だけに当てる。
import tsParser from '@typescript-eslint/parser'
import reactHooks from 'eslint-plugin-react-hooks'

export default [
  { ignores: ['dist', 'node_modules', 'coverage', 'playwright-report', 'test-results'] },
  {
    files: ['**/*.{ts,tsx}'],
    plugins: { 'react-hooks': reactHooks },
    languageOptions: {
      parser: tsParser,
      parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module',
        ecmaFeatures: { jsx: true },
      },
    },
    rules: reactHooks.configs.recommended.rules,
  },
  {
    files: ['src/**/*.{ts,tsx}', 'e2e/**/*.ts'],
    languageOptions: {
      // no-implied-eval がブラウザの大域の関数を見分けられるように宣言する。
      globals: {
        window: 'readonly',
        globalThis: 'readonly',
        setTimeout: 'readonly',
        setInterval: 'readonly',
      },
    },
    rules: {
      // 文字列をコードとして実行する setTimeout('...') などを禁じる（oxlint に無いセキュリティ系のルール）。
      'no-implied-eval': 'error',
      'no-restricted-syntax': [
        'error',
        {
          selector: 'ExportDefaultDeclaration',
          message: 'export default は使わず、名前付きのエクスポートにしてください。',
        },
        {
          selector: 'TSEnumDeclaration',
          message: 'enum は使わず、文字列リテラルの union で表してください。',
        },
      ],
    },
  },
]
