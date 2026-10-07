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
// 英語のロケール（en-US）のブラウザーで DSL の管理画面を使い、サーバーが返す照合の警告と投入の誤りの message、
// 画面の文言が英語になることを確かめる（U5-LANG-E2E、u5 の code-summary.md 6節）。perf/dsl-timing.sh --lang が呼ぶ。
// 使い捨ての環境（対象DB の接続先を設定したもの）だけに向けて使う。
//   node perf/ui/dsl-ui-lang.mjs --env-file <PERF_ADMIN_EMAIL・PERF_ADMIN_PASSWORD のファイル> --base <URL> --out <結果の JSON>
// 流れ: ログイン → /admin/dsl → 投入のタブで、対象DB に無いテーブルを持つ正しい DSL を貼り付けて投入（照合の警告が出る）
//       → 誤りを含む DSL を貼り付けて投入（422 の誤りの一覧が出る）。応答の message と画面の文字に日本語の文字が無いことを見る。
// 書式の版 2（Intent 261004-role-menu の U2 dsl-v2、NFR 設計の読み直し R-04・計画の D-8）: DSL は版 2 で書き、次も英語で届くことを
// 確かめる。名前の違うスキーマの照合の警告（SCHEMA_MISMATCH）、書式の版 1 の誤り、メニューの深さの誤り。
import { readFileSync, writeFileSync } from 'node:fs'
import { createRequire } from 'node:module'
import path from 'node:path'
import { parseArgs } from 'node:util'
import { fileURLToPath } from 'node:url'

const here = path.dirname(fileURLToPath(import.meta.url))
const require = createRequire(path.resolve(here, '../../frontend/package.json'))
const { chromium } = require('@playwright/test')

const { values } = parseArgs({
  options: {
    'env-file': { type: 'string' },
    base: { type: 'string', default: 'http://127.0.0.1:18080' },
    out: { type: 'string' },
  },
})
if (!values['env-file'] || !values.out) {
  console.error('使い方: node perf/ui/dsl-ui-lang.mjs --env-file <ファイル> --base <URL> --out <JSON>')
  process.exit(1)
}
const env = Object.fromEntries(
  readFileSync(values['env-file'], 'utf8')
    .split('\n')
    .filter((line) => line.includes('='))
    .map((line) => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]),
)
const TIMEOUT = 60_000
// ひらがな・カタカナ・漢字・全角の記号
const JAPANESE = /[　-ヿ一-鿿＀-￯]/

/** 使い捨ての環境の対象DB の設定のスキーマ名 */
const TARGET_SCHEMA = 'large'

/** 対象DB に無いテーブル（スキーマの tables の下、字下げ 6）。表示名・名前はすべて ASCII にし、日本語の文字が DSL から来ないようにする。 */
function table(name, withError) {
  return `      ${name}:
        label: { ja: ${name}, en: ${name} }
        view: false
        primaryKey: [code]
        foreignKeys: []
        columns:
          code:
            label: { ja: code, en: code }
            dbType: { name: VARCHAR, length: 10, precision: null, scale: null, nullable: false }
            formPart: ${withError ? 'bogus_part' : 'text'}
            search: { enabled: true, operator: EQUALS, collapsed: false }
            list: { visible: true, order: 1, sortable: true }
            detail: { visible: true }
            validations: []
`
}

/** 書式の版 2 の DSL（メニュー1つ、スキーマ1つ、テーブル1つ） */
function dslV2({ schema, menuTable, tableName, withError = false, menus = null }) {
  const menuText =
    menus ??
    `  - label: { ja: ${tableName}, en: ${tableName} }
    table: { schema: ${schema}, name: ${menuTable} }
`
  return `version: 2
menus:
${menuText}schemas:
  ${schema}:
    label: { ja: ${schema}, en: ${schema} }
    tables:
${table(tableName, withError)}`
}

/** 深さ 6 の1本の枝のメニュー */
function deepMenus(schema, tableName) {
  let text = ''
  for (let level = 1; level <= 6; level++) {
    const indent = '    '.repeat(level - 1)
    text += `${indent}  - label: { ja: level${level}, en: level${level} }\n`
    text +=
      level === 6
        ? `${indent}    table: { schema: ${schema}, name: ${tableName} }\n`
        : `${indent}    items:\n`
  }
  return text
}

const WARNING_DSL = dslV2({ schema: TARGET_SCHEMA, menuTable: 'lang_extra', tableName: 'lang_extra' })
const MISMATCH_DSL = dslV2({ schema: 'lang_other', menuTable: 'lang_extra', tableName: 'lang_extra' })
const INVALID_DSL = dslV2({
  schema: TARGET_SCHEMA,
  menuTable: 'lang_missing',
  tableName: 'lang_bad',
  withError: true,
})
const VERSION_ONE_DSL = 'version: 1\nmenus: []\ntables: {}\n'
const DEPTH_DSL = dslV2({
  schema: TARGET_SCHEMA,
  menuTable: 'lang_extra',
  tableName: 'lang_extra',
  menus: deepMenus(TARGET_SCHEMA, 'lang_extra'),
})

const result = { base: values.base, locale: 'en-US', browser: '', steps: [], pass: false }
const browser = await chromium.launch()
result.browser = `chromium ${browser.version()}`
const context = await browser.newContext({ baseURL: values.base, locale: 'en-US' })
const page = await context.newPage()

/** 貼り付けで投入し、POST の要求と応答を返す（置き換えの確かめが出れば確定する）。 */
async function pasteAndSubmit(text) {
  await page.getByRole('tab', { name: 'Submit' }).click()
  await page.getByRole('radio', { name: 'Paste' }).check()
  await page.getByLabel('DSL (YAML, up to 10MB)').fill(text)
  const responsePromise = page.waitForResponse(
    (r) => r.url().includes('/api/admin/dsl/preview?') && r.request().method() === 'POST',
    { timeout: TIMEOUT },
  )
  await page.getByTestId('dsl-submit-button').click()
  const confirm = page.getByTestId('dsl-confirm-replace')
  if (await confirm.waitFor({ state: 'visible', timeout: 5_000 }).then(() => true, () => false)) {
    await page.getByTestId('dsl-confirm-ok').click()
  }
  const response = await responsePromise
  return { request: response.request(), response, body: await response.json() }
}

let stage = 'login'
try {
  await page.goto('/')
  await page.getByTestId('login-form-email-input').fill(env.PERF_ADMIN_EMAIL)
  await page.getByTestId('login-form-password-input').fill(env.PERF_ADMIN_PASSWORD)
  await page.getByTestId('login-form-submit-button').click()
  await page.getByTestId('home-page').waitFor({ state: 'visible', timeout: TIMEOUT })
  // 画面を開くとプレビューの表示（重い処理、照合を含む）を読みに行く。重い処理は同時に1つのため、読み終わってから投入する
  // （重なると 503 DSL_BUSY になる）。
  const initialPreview = page.waitForResponse(
    (r) => r.url().endsWith('/api/admin/dsl/preview') && r.request().method() === 'GET',
    { timeout: TIMEOUT },
  )
  await page.goto('/admin/dsl')
  await page.getByTestId('dsl-status-applied').waitFor({ state: 'visible', timeout: TIMEOUT })
  await initialPreview
  await page.locator('[data-testid="dsl-admin-page"][aria-busy="false"]').waitFor({ timeout: TIMEOUT })

  // 1. 照合の警告（対象DB に無いテーブル）。
  stage = 'compareWarning'
  const warned = await pasteAndSubmit(WARNING_DSL)
  const warningList = page.getByTestId('dsl-warning-list')
  await warningList.waitFor({ state: 'visible', timeout: TIMEOUT })
  const serverWarnings = (warned.body.warnings ?? []).map((w) => w.message)
  const shownWarnings = await warningList.innerText()
  result.steps.push({
    step: 'compareWarning',
    acceptLanguage: await warned.request.headerValue('accept-language'),
    http: warned.response.status(),
    serverMessages: serverWarnings,
    serverMessagesJapanese: serverWarnings.some((m) => JAPANESE.test(m)),
    shownText: shownWarnings,
    shownTextJapanese: JAPANESE.test(shownWarnings),
    shownContainsServerMessages: serverWarnings.every((m) => shownWarnings.includes(m)),
  })

  // 2. 名前の違うスキーマの照合の警告（SCHEMA_MISMATCH）。
  stage = 'schemaMismatch'
  const mismatched = await pasteAndSubmit(MISMATCH_DSL)
  await warningList.waitFor({ state: 'visible', timeout: TIMEOUT })
  const mismatchWarnings = (mismatched.body.warnings ?? []).filter((w) => w.kind === 'SCHEMA_MISMATCH')
  const shownMismatch = await warningList.innerText()
  result.steps.push({
    step: 'schemaMismatch',
    acceptLanguage: await mismatched.request.headerValue('accept-language'),
    http: mismatched.response.status(),
    serverMessages: mismatchWarnings.map((w) => w.message),
    serverMessagesJapanese: mismatchWarnings.some((w) => JAPANESE.test(w.message)),
    shownText: shownMismatch,
    shownTextJapanese: JAPANESE.test(shownMismatch),
    shownContainsServerMessages: mismatchWarnings.every((w) => shownMismatch.includes(w.message)),
  })

  // 3〜5. 投入の誤り（422）。書式の誤り、書式の版 1、メニューの深さ。
  for (const [step, text, expectKind] of [
    ['submitErrors', INVALID_DSL, null],
    ['versionError', VERSION_ONE_DSL, 'UNSUPPORTED_VERSION'],
    ['depthError', DEPTH_DSL, 'SEMANTIC'],
  ]) {
    stage = step
    const rejected = await pasteAndSubmit(text)
    const errorList = page.getByTestId('dsl-submit-errors')
    await errorList.waitFor({ state: 'visible', timeout: TIMEOUT })
    const errors = rejected.body.errors ?? []
    const serverErrors = errors.map((e) => e.message)
    const shownErrors = await errorList.innerText()
    const alert = page.getByTestId('dsl-alert')
    const alertText = (await alert.isVisible()) ? await alert.innerText() : null
    result.steps.push({
      step,
      acceptLanguage: await rejected.request.headerValue('accept-language'),
      http: rejected.response.status(),
      code: rejected.body.code,
      kinds: errors.map((e) => e.kind),
      kindMatches: expectKind === null || errors.some((e) => e.kind === expectKind),
      serverMessages: serverErrors,
      serverMessagesJapanese: serverErrors.some((m) => JAPANESE.test(m)),
      shownText: shownErrors,
      shownTextJapanese: JAPANESE.test(shownErrors),
      shownContainsServerMessages: serverErrors.every((m) => shownErrors.includes(m)),
      alertText,
      alertTextJapanese: alertText === null ? null : JAPANESE.test(alertText),
    })
  }

  const english = (s) =>
    s.serverMessages.length > 0 &&
    !s.serverMessagesJapanese &&
    !s.shownTextJapanese &&
    s.shownContainsServerMessages
  const [w, m, ...errorsSteps] = result.steps
  result.pass =
    w.http === 201 &&
    english(w) &&
    m.http === 201 &&
    english(m) &&
    errorsSteps.length === 3 &&
    errorsSteps.every((e) => e.http === 422 && e.kindMatches && english(e))
} catch (error) {
  result.error = `${stage}: ${String(error?.message ?? error).split('\n')[0]}`
  // 失敗したときの画面の文字（原因を見るため。秘密の値は画面に無い）
  result.pageTextOnError = await page.locator('body').innerText().catch(() => null)
  process.exitCode = 1
} finally {
  await browser.close()
  writeFileSync(values.out, JSON.stringify(result, null, 2) + '\n')
  console.log(JSON.stringify({ pass: result.pass, error: result.error ?? null }))
}
