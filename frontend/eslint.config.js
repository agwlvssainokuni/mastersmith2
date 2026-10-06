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
//
// 画面の機能どうしの import の制限（frontend-components.md 5節、BR2.1〜BR2.3）: src/features/ のディレクトリの一覧から、
// 機能ごとに兄弟の機能を指す相対の道を止める決まりを作り、src/shared/ には app/・features/ を指す道を止める決まりを当てる。
// テストのファイル（*.test.ts・*.test.tsx）は対象外。道の別名（tsconfig の paths・vite の alias）は今は無い。
import { readdirSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import tsParser from '@typescript-eslint/parser'
import reactHooks from 'eslint-plugin-react-hooks'

const FEATURES_DIR = join(dirname(fileURLToPath(import.meta.url)), 'src', 'features')

const TEST_FILES = ['**/*.test.ts', '**/*.test.tsx']

/** 直下のディレクトリの名前を並べる */
function subdirectoryNames(dir) {
  return readdirSync(dir, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort()
}

/**
 * 機能の中の下位のディレクトリの名前と、機能の名前の重なりを返す（計画 D-8）。重なると、下位のディレクトリへの道
 * （例 ../api/types）を兄弟の機能への道と取り違えて止めてしまうため、重なりがあれば設定の読み込みで失敗させる。
 *
 * @param {Record<string, readonly string[]>} featureSubdirectories 機能の名前ごとの下位のディレクトリの名前
 * @returns {string[]} 重なりの説明（無ければ空）
 */
export function findFeatureNameOverlaps(featureSubdirectories) {
  const features = new Set(Object.keys(featureSubdirectories))
  const overlaps = []
  for (const [feature, subdirectories] of Object.entries(featureSubdirectories)) {
    for (const name of subdirectories) {
      if (features.has(name)) {
        overlaps.push(`src/features/${feature}/${name}/ と機能 ${name}`)
      }
    }
  }
  return overlaps.sort()
}

/**
 * 決まりを作る深さの上限（機能の直下を 0 とする。今のファイルは最大 1）。深さごとに、その深さちょうどのファイルにだけ当たる決まりを
 * 作り（files が重ならない）、これより深いファイルが機能の中にあれば設定の読み込みで失敗させる（上限を上げる直しを促す）。
 */
export const MAX_FEATURE_DEPTH = 6

/**
 * 機能のディレクトリからの相対の道（例 useradmin/api/types.ts）の一覧のうち、深さが上限を超えるものを返す。
 *
 * @param {readonly string[]} relativePaths src/features/ からの相対の道（区切りは /）
 * @param {number} maxDepth 深さの上限
 * @returns {string[]} 上限を超えるファイルの道
 */
export function findTooDeepFeatureFiles(relativePaths, maxDepth = MAX_FEATURE_DEPTH) {
  return relativePaths.filter((path) => path.split('/').length - 2 > maxDepth).sort()
}

/**
 * 機能の中の深さ depth のファイルから、兄弟の機能を指す道の型を返す。相対の道の「..」の数は深さで決まるため、深さごとに
 * 作る（深さによらず同じ型にすると、下位のディレクトリのファイルから機能の中の別のファイル、例 ../registration を指す道を、
 * 同じ名前の兄弟の機能への道と取り違える）。features/ を通る道は、深さによらず止める。
 *
 * @param {string} feature 機能の名前
 * @param {readonly string[]} features すべての機能の名前
 * @param {number} depth 機能の中の深さ（機能の直下が 0）
 * @returns {string[]} 止める道の型
 */
export function siblingFeaturePatterns(feature, features, depth) {
  const up = '../'.repeat(depth + 1)
  return features
    .filter((other) => other !== feature)
    .flatMap((other) => [
      `${up}${other}`,
      `${up}${other}/**`,
      `**/features/${other}`,
      `**/features/${other}/**`,
    ])
}

/** src/features/ の下の .ts・.tsx のファイルの道（src/features/ からの相対、区切りは /）を集める */
function featureSourceFiles(dir, prefix = '') {
  return readdirSync(dir, { withFileTypes: true }).flatMap((entry) => {
    const path = `${prefix}${entry.name}`
    if (entry.isDirectory()) {
      return featureSourceFiles(join(dir, entry.name), `${path}/`)
    }
    return /\.tsx?$/.test(entry.name) ? [path] : []
  })
}

/** 機能の名前ごとの下位のディレクトリの名前を読み、重なりや深すぎるファイルがあれば設定の読み込みを失敗させる */
function readFeatures() {
  const featureSubdirectories = Object.fromEntries(
    subdirectoryNames(FEATURES_DIR).map((feature) => [
      feature,
      subdirectoryNames(join(FEATURES_DIR, feature)),
    ]),
  )
  const overlaps = findFeatureNameOverlaps(featureSubdirectories)
  if (overlaps.length > 0) {
    throw new Error(
      `機能の中の下位のディレクトリの名前が、機能の名前と重なっています（import の制限が誤るため、どちらかの名前を変えてください）: ${overlaps.join('、')}`,
    )
  }
  const tooDeep = findTooDeepFeatureFiles(featureSourceFiles(FEATURES_DIR))
  if (tooDeep.length > 0) {
    throw new Error(
      `機能の中の深さが ${MAX_FEATURE_DEPTH} を超えるファイルがあります（import の制限が当たらないため、eslint.config.js の MAX_FEATURE_DEPTH を上げてください）: ${tooDeep.join('、')}`,
    )
  }
  return Object.keys(featureSubdirectories)
}

const features = readFeatures()

/**
 * 機能 feature の中の深さ depth ちょうどのファイルの files の型（機能の直下が 0）。** を使わないため、深さが違う決まりどうしは
 * 同じファイルに当たらない。
 *
 * @param {string} feature 機能の名前
 * @param {number} depth 機能の中の深さ
 * @returns {string[]} files の型
 */
export function filesAtDepth(feature, depth) {
  return [`src/features/${feature}/${'*/'.repeat(depth)}*.{ts,tsx}`]
}

const featureImportRules = features.flatMap((feature) =>
  Array.from({ length: MAX_FEATURE_DEPTH + 1 }, (_, depth) => ({
    files: filesAtDepth(feature, depth),
    ignores: TEST_FILES,
    rules: {
      'no-restricted-imports': [
        'error',
        {
          patterns: [
            {
              group: siblingFeaturePatterns(feature, features, depth),
              message:
                '機能どうしは直接 import しないでください。複数の機能で共有するものは src/shared/ へ移し、ログアウトなどの骨組みの口は src/app/ から使ってください。',
            },
          ],
        },
      ],
    },
  })),
)

const sharedImportRule = {
  files: ['src/shared/**/*.{ts,tsx}'],
  ignores: TEST_FILES,
  rules: {
    'no-restricted-imports': [
      'error',
      {
        patterns: [
          {
            group: ['**/app', '**/app/**', '**/features', '**/features/**'],
            message:
              'src/shared/ は src/app/ と src/features/ を読まないでください。要る型や値は呼ぶ側から引数で受け取ってください。',
          },
        ],
      },
    ],
  },
}

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
  ...featureImportRules,
  sharedImportRule,
]
