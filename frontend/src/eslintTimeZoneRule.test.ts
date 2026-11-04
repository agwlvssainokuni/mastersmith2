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
// @vitest-environment node
//
// 暗黙のタイムゾーンを止める ESLint の規則（eslint.config.js、eslint-rules/timeZoneSelectors.js。Intent 261102-timezone-ui-polish の
// U2、BR2.13・H4・SD6）の効きの確かめ。ESLint の部品に frontend/eslint.config.js を読ませ、lintText に見本の文と道を渡して
// 誤りの有無を見る（見本の文はこのファイルの中に置き、src/ に補助のファイルを作らない）。ESLint の部品の最初の読み込みは遅いため、
// 1回だけ作り、ほかのテストより先に 1 つだけで流す（vitest.config.ts の eslint-config プロジェクト）。
import { join, resolve } from 'node:path'
import { ESLint } from 'eslint'
import { beforeAll, describe, expect, it } from 'vitest'

const FRONTEND_DIR = resolve(import.meta.dirname, '..')

let eslint: ESLint

beforeAll(async () => {
  eslint = new ESLint({ cwd: FRONTEND_DIR })
  // 設定とプラグインの初回の読み込みは最初の lintText で起こる。各テストの時間の上限（既定の 5 秒）に
  // その時間を入れないよう、ここで一度空の文を通して済ませる（カバレッジの計測と並行の負荷の下で、
  // 最初のテストが 5 秒を超えたため。Build and Test の繰り戻し 1）。
  await eslint.lintText('', { filePath: join(FRONTEND_DIR, 'src/features/x/Warmup.ts') })
}, 60_000)

/** 見本の文を道 filePath のファイルとして ESLint にかけ、構文の禁止（no-restricted-syntax）の誤りを返す */
async function syntaxErrors(code: string, filePath = 'src/features/x/Sample.ts') {
  const [result] = await eslint.lintText(code, { filePath: join(FRONTEND_DIR, filePath) })
  return result.messages.filter((message) => message.ruleId === 'no-restricted-syntax')
}

describe('implicit time zone rule: forbidden forms', () => {
  it.each([
    ['resolvedOptions().timeZone in another file', 'export const z = x.resolvedOptions().timeZone'],
    ["new Intl.DateTimeFormat('ja')", "export const f = new Intl.DateTimeFormat('ja')"],
    ["Intl.DateTimeFormat('ja', {})", "export const f = Intl.DateTimeFormat('ja', {})"],
    ['toLocaleDateString()', 'export const s = d.toLocaleDateString()'],
    [
      "toLocaleTimeString('en', {hour: '2-digit'})",
      "export const s = d.toLocaleTimeString('en', { hour: '2-digit' })",
    ],
    ['toLocaleString()', 'export const s = d.toLocaleString()'],
    ["toLocaleString('ja-JP')", "export const s = d.toLocaleString('ja-JP')"],
    [
      "toLocaleString('ja', {hour: '2-digit'})",
      "export const s = d.toLocaleString('ja', { hour: '2-digit' })",
    ],
  ])('rejects %s', async (_label, code) => {
    expect(await syntaxErrors(code)).toHaveLength(1)
  })
})

describe('implicit time zone rule: allowed forms', () => {
  it.each([
    [
      'Intl.DateTimeFormat with an explicit timeZone',
      "export const f = new Intl.DateTimeFormat('en', { timeZone: name })",
    ],
    [
      'toLocaleString with an explicit timeZone',
      "export const s = d.toLocaleString('ja', { timeZone: tz })",
    ],
    [
      'number toLocaleString with fraction digits',
      "export const s = n.toLocaleString('ja', { minimumFractionDigits: 2 })",
    ],
    ['Intl.NumberFormat', "export const s = new Intl.NumberFormat('ja').format(n)"],
  ])('allows %s', async (_label, code) => {
    expect(await syntaxErrors(code)).toHaveLength(0)
  })

  it('does not apply to test files', async () => {
    expect(
      await syntaxErrors('export const s = d.toLocaleString()', 'src/features/x/Sample.test.ts'),
    ).toHaveLength(0)
  })
})

describe('implicit time zone rule: the one file that may read the browser time zone', () => {
  const READ = 'export const z = new Intl.DateTimeFormat().resolvedOptions().timeZone'

  it('lets browserTimeZone.ts read the browser time zone (S1 and S2 are off there)', async () => {
    expect(await syntaxErrors(READ, 'src/app/display-settings/browserTimeZone.ts')).toHaveLength(0)
  })

  it.each([
    ['src/shared/format/Sample.ts'],
    ['src/features/x/Sample.ts'],
    ['src/app/Sample.ts'],
    ['src/app/display-settings/DisplayTimeZoneProvider.tsx'],
  ])('rejects the same code in %s', async (filePath) => {
    expect((await syntaxErrors(READ, filePath)).length).toBeGreaterThanOrEqual(1)
  })

  it('still rejects toLocale calls without a time zone in browserTimeZone.ts', async () => {
    expect(
      await syntaxErrors(
        'export const s = d.toLocaleString()',
        'src/app/display-settings/browserTimeZone.ts',
      ),
    ).toHaveLength(1)
  })
})

describe('existing syntax rules still apply next to the new ones', () => {
  it.each([
    ['export default', 'export default function f() { return 1 }'],
    ['enum', 'export enum Color { Red }'],
  ])('rejects %s in src/', async (_label, code) => {
    expect(await syntaxErrors(code)).toHaveLength(1)
  })

  it('rejects export default and a bad time zone form together', async () => {
    expect(
      await syntaxErrors('export default function f() { return d.toLocaleString() }'),
    ).toHaveLength(2)
  })

  it('rejects export default in browserTimeZone.ts too', async () => {
    expect(
      await syntaxErrors(
        'export default function f() { return 1 }',
        'src/app/display-settings/browserTimeZone.ts',
      ),
    ).toHaveLength(1)
  })
})

describe('the real sources', () => {
  it('have no violation of the rule in src/', async () => {
    const results = await eslint.lintFiles(['src/**/*.{ts,tsx}'])
    const violations = results.flatMap((result) =>
      result.messages
        .filter((message) => message.ruleId === 'no-restricted-syntax')
        .map((message) => `${result.filePath}:${message.line} ${message.message}`),
    )

    expect(violations).toEqual([])
  }, 60_000)
})
