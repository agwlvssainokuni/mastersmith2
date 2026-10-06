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
// 画面の機能どうしの import の制限（eslint.config.js、BR2.1〜BR2.3、frontend-components.md 5節、NFR6.1・NFR6.2）の確かめ。
// ESLint の部品に frontend/eslint.config.js を読ませ、lintText に見本の文と道を渡して誤りの有無を見る。見本の文はこのファイルの
// 中に置き、src/ に補助のファイルを作らない（計測の分母に入れないため）。ESLint の部品の最初の読み込みは遅いため、1回だけ作る。
import { readdirSync } from 'node:fs'
import { join, resolve } from 'node:path'
import { pathToFileURL } from 'node:url'
import { ESLint } from 'eslint'
import { beforeAll, describe, expect, it } from 'vitest'

const FRONTEND_DIR = resolve(import.meta.dirname, '..')

interface ImportRulesModule {
  findFeatureNameOverlaps: (featureSubdirectories: Record<string, readonly string[]>) => string[]
  findTooDeepFeatureFiles: (relativePaths: readonly string[], maxDepth?: number) => string[]
  filesAtDepth: (feature: string, depth: number) => string[]
  MAX_FEATURE_DEPTH: number
}

let eslint: ESLint
let rules: ImportRulesModule

beforeAll(async () => {
  eslint = new ESLint({ cwd: FRONTEND_DIR })
  const configUrl = pathToFileURL(join(FRONTEND_DIR, 'eslint.config.js')).href
  rules = (await import(/* @vite-ignore */ configUrl)) as ImportRulesModule
})

/** 見本の文を道 filePath のファイルとして ESLint にかけ、import の制限の誤りの数を返す */
async function restrictedImports(filePath: string, importPath: string): Promise<number> {
  const code = `import { value } from '${importPath}'\nexport const used = value\n`
  const [result] = await eslint.lintText(code, { filePath: join(FRONTEND_DIR, filePath) })
  return result.messages.filter((message) => message.ruleId === 'no-restricted-imports').length
}

describe('import restrictions between screen features', () => {
  it('runs in the node environment without the DOM', () => {
    expect(typeof window).toBe('undefined')
  })

  it.each([
    ['src/features/registration/x.ts', '../auth/authSession'],
    ['src/features/registration/x.ts', '../../features/auth/authSession'],
    ['src/shared/tree/x.ts', '../../app/registry/types'],
    ['src/shared/tree/x.ts', '../../features/auth/authSession'],
  ])('rejects %s importing %s', async (filePath, importPath) => {
    expect(await restrictedImports(filePath, importPath)).toBe(1)
  })

  it.each([
    ['src/features/useradmin/testing/x.ts', '../api/types'],
    ['src/features/auth/x.ts', '../../app/i18n/i18n'],
    ['src/features/auth/x.ts', '../../shared/api-client/apiClient'],
    ['src/features/registration/x.test.ts', '../auth/authSession'],
  ])('allows %s importing %s', async (filePath, importPath) => {
    expect(await restrictedImports(filePath, importPath)).toBe(0)
  })

  it('does not mistake a file of the same feature for a sibling feature of the same name', async () => {
    // testing/ から自分の機能の registration.ts を指す道は、機能 registration への道ではない
    expect(await restrictedImports('src/features/useradmin/testing/x.ts', '../registration')).toBe(
      0,
    )
    expect(
      await restrictedImports('src/features/useradmin/testing/x.ts', '../../registration/x'),
    ).toBe(1)
  })
})

describe('import restrictions at every depth inside a feature', () => {
  it.each([
    ['src/features/useradmin/a/b/x.ts', '../../../auth/authSession'],
    ['src/features/useradmin/a/b/c/x.ts', '../../../../auth/authSession'],
    ['src/features/useradmin/a/b/c/d/e/f/x.ts', '../../../../../../../auth/authSession'],
    ['src/features/useradmin/a/b/c/x.ts', '../../../../../features/auth/authSession'],
  ])('rejects the deep file %s importing %s', async (filePath, importPath) => {
    expect(await restrictedImports(filePath, importPath)).toBe(1)
  })

  it.each([
    ['src/features/useradmin/a/b/x.ts', '../../registration'],
    ['src/features/useradmin/a/b/c/x.ts', '../../../api/types'],
    ['src/features/useradmin/a/b/x.ts', '../../../../app/i18n/i18n'],
  ])(
    'allows the deep file %s importing %s inside the feature or the frame',
    async (filePath, importPath) => {
      expect(await restrictedImports(filePath, importPath)).toBe(0)
    },
  )

  it('applies exactly the rule of the depth of the file, with one more ../ than the depth', async () => {
    for (let depth = 0; depth <= rules.MAX_FEATURE_DEPTH; depth += 1) {
      const filePath = join(FRONTEND_DIR, `src/features/useradmin/${'d/'.repeat(depth)}x.ts`)
      const config = (await eslint.calculateConfigForFile(filePath)) as {
        rules: Record<string, [unknown, { patterns: { group: string[] }[] }]>
      }
      const group = config.rules['no-restricted-imports'][1].patterns[0].group
      const relative = group.filter(
        (pattern) => pattern.startsWith('..') && pattern.endsWith('/auth'),
      )
      expect(relative).toEqual([`${'../'.repeat(depth + 1)}auth`])
    }
  })

  it('builds file globs that match one depth only, so the rules of two depths never overlap', () => {
    for (let depth = 0; depth <= rules.MAX_FEATURE_DEPTH; depth += 1) {
      const [glob] = rules.filesAtDepth('useradmin', depth)
      expect(glob).not.toContain('**')
      expect(glob.split('*/').length - 1).toBe(depth)
    }
  })

  it('reports feature files deeper than the limit so that the configuration fails to load', () => {
    const max = rules.MAX_FEATURE_DEPTH
    const atLimit = `a/${'d/'.repeat(max)}x.ts`
    const beyond = `a/${'d/'.repeat(max + 1)}x.ts`
    expect(rules.findTooDeepFeatureFiles(['a/x.ts', atLimit, beyond])).toEqual([beyond])
  })
})

describe('findFeatureNameOverlaps', () => {
  it('reports a subdirectory whose name is also a feature name', () => {
    expect(rules.findFeatureNameOverlaps({ a: ['b'], b: [] })).toEqual([
      'src/features/a/b/ と機能 b',
    ])
  })

  it('reports nothing for distinct names and for the features of today', () => {
    expect(rules.findFeatureNameOverlaps({ a: ['api'], b: ['testing'] })).toEqual([])
    const featuresDir = join(FRONTEND_DIR, 'src', 'features')
    const today = Object.fromEntries(
      readdirSync(featuresDir, { withFileTypes: true })
        .filter((entry) => entry.isDirectory())
        .map((entry) => [
          entry.name,
          readdirSync(join(featuresDir, entry.name), { withFileTypes: true })
            .filter((child) => child.isDirectory())
            .map((child) => child.name),
        ]),
    )
    expect(Object.keys(today).length).toBeGreaterThan(1)
    expect(rules.findFeatureNameOverlaps(today)).toEqual([])
  })
})
