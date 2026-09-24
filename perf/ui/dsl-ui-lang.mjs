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

/** 対象DB（スキーマ large）に無いテーブル。表示名・名前はすべて ASCII にし、日本語の文字が DSL から来ないようにする。 */
function table(name, withError) {
  return `  ${name}:
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
const WARNING_DSL = `version: 1
menus:
  - label: { ja: lang_extra, en: lang_extra }
    table: lang_extra
tables:
${table('lang_extra', false)}`
const INVALID_DSL = `version: 1
menus:
  - label: { ja: lang_bad, en: lang_bad }
    table: lang_missing
tables:
${table('lang_bad', true)}`

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

  // 2. 投入の誤り（422）。
  stage = 'submitErrors'
  const rejected = await pasteAndSubmit(INVALID_DSL)
  const errorList = page.getByTestId('dsl-submit-errors')
  await errorList.waitFor({ state: 'visible', timeout: TIMEOUT })
  const serverErrors = (rejected.body.errors ?? []).map((e) => e.message)
  const shownErrors = await errorList.innerText()
  const alert = page.getByTestId('dsl-alert')
  const alertText = (await alert.isVisible()) ? await alert.innerText() : null
  result.steps.push({
    step: 'submitErrors',
    acceptLanguage: await rejected.request.headerValue('accept-language'),
    http: rejected.response.status(),
    code: rejected.body.code,
    serverMessages: serverErrors,
    serverMessagesJapanese: serverErrors.some((m) => JAPANESE.test(m)),
    shownText: shownErrors,
    shownTextJapanese: JAPANESE.test(shownErrors),
    shownContainsServerMessages: serverErrors.every((m) => shownErrors.includes(m)),
    alertText,
    alertTextJapanese: alertText === null ? null : JAPANESE.test(alertText),
  })

  const [w, e] = result.steps
  result.pass =
    w.http === 201 &&
    w.serverMessages.length > 0 &&
    !w.serverMessagesJapanese &&
    !w.shownTextJapanese &&
    w.shownContainsServerMessages &&
    e.http === 422 &&
    e.serverMessages.length > 0 &&
    !e.serverMessagesJapanese &&
    !e.shownTextJapanese &&
    e.shownContainsServerMessages
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
