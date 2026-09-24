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
// DSL の管理画面の時間を、手元の PC のブラウザー（Playwright の Chromium、画面なし）で測る（U5 の NFR1.18〜NFR1.20）。
// 使い捨ての環境（docker/perf/compose.yaml）だけに向けて使う。perf/dsl-timing.sh --ui が、想定の規模のプレビューを置いてから呼ぶ。
//   node perf/ui/dsl-ui-timing.mjs --env-file <PERF_ADMIN_EMAIL・PERF_ADMIN_PASSWORD のファイル> --base <URL>
//        --large <10MB の DSL> --out <結果の JSON> [--repeat 3]
// Playwright は frontend/ の依存（@playwright/test）を使う（事前に npm ci と npx playwright install chromium）。
// 時間は統合の関門にせず、測った値を記録するだけにする（u5 の NFR 要件）。パスワードは画面・ログ・結果に出さない。
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
    large: { type: 'string' },
    out: { type: 'string' },
    repeat: { type: 'string', default: '3' },
  },
})
if (!values['env-file'] || !values.large || !values.out) {
  console.error('使い方: node perf/ui/dsl-ui-timing.mjs --env-file <ファイル> --base <URL> --large <10MB の DSL> --out <JSON>')
  process.exit(1)
}
const env = Object.fromEntries(
  readFileSync(values['env-file'], 'utf8')
    .split('\n')
    .filter((line) => line.includes('='))
    .map((line) => [line.slice(0, line.indexOf('=')), line.slice(line.indexOf('=') + 1)]),
)
const repeat = Number(values.repeat)
const TIMEOUT = 60_000
const result = { base: values.base, browser: '', navigation: [], reload: [], diffExpand: null, fileSubmit: null }

/** GET /api/admin/dsl/preview の応答を待ち、サーバーの時間（要求を送ってから最初のバイトまで）を返す。 */
function previewResponse(page) {
  return page.waitForResponse(
    (r) => r.url().endsWith('/api/admin/dsl/preview') && r.request().method() === 'GET',
    { timeout: TIMEOUT },
  )
}
function serverMs(response) {
  const t = response.request().timing()
  return Math.round(t.responseStart - t.requestStart)
}

/** 画面を開いてから、今の状態とプレビューが出るまで（NFR1.18）。 */
async function measureOpen(page, open) {
  const pending = previewResponse(page)
  const t0 = performance.now()
  await open()
  await page.getByTestId('dsl-status-applied').waitFor({ state: 'visible', timeout: TIMEOUT })
  await page.getByTestId('dsl-status-preview').waitFor({ state: 'visible', timeout: TIMEOUT })
  const statusMs = performance.now() - t0
  await page.getByTestId('dsl-preview-panel').waitFor({ state: 'visible', timeout: TIMEOUT })
  const previewMs = performance.now() - t0
  const response = await pending
  const apiMs = serverMs(response)
  return {
    statusShownMs: Math.round(statusMs),
    previewShownMs: Math.round(previewMs),
    previewApiServerMs: apiMs,
    previewShownMinusApiMs: Math.round(previewMs - apiMs),
    previewHttp: response.status(),
  }
}

const browser = await chromium.launch()
result.browser = `chromium ${browser.version()}`
const context = await browser.newContext({ baseURL: values.base, locale: 'ja-JP' })
const page = await context.newPage()
try {
  await page.goto('/')
  await page.getByTestId('login-form-email-input').fill(env.PERF_ADMIN_EMAIL)
  await page.getByTestId('login-form-password-input').fill(env.PERF_ADMIN_PASSWORD)
  await page.getByTestId('login-form-submit-button').click()
  await page.getByTestId('home-page').waitFor({ state: 'visible', timeout: TIMEOUT })

  // NFR1.18: サイドバーの「DSL」から開く（画面の中の移動）と、/admin/dsl を読み直す（画面の読み込みとトークンの更新を含む）。
  for (let i = 0; i < repeat; i++) {
    await page.goto('/')
    await page.getByTestId('home-page').waitFor({ state: 'visible', timeout: TIMEOUT })
    result.navigation.push(
      await measureOpen(page, () => page.getByRole('link', { name: 'DSL', exact: true }).click()),
    )
  }
  for (let i = 0; i < repeat; i++) {
    result.reload.push(await measureOpen(page, () => page.goto('/admin/dsl')))
  }

  // NFR1.19: 違いの表の最初の開ける行を開き、カラムの表が出るまで。
  await page.getByTestId('dsl-diff-show-all').check().catch(() => {})
  const toggle = page.locator('[data-testid^="dsl-diff-toggle-"]').first()
  if ((await toggle.count()) > 0) {
    const name = (await toggle.getAttribute('data-testid')).replace('dsl-diff-toggle-', '')
    const t0 = performance.now()
    await toggle.click()
    const columns = page.getByTestId(`dsl-diff-columns-${name}`)
    await columns.waitFor({ state: 'visible', timeout: TIMEOUT })
    const ms = performance.now() - t0
    result.diffExpand = {
      table: name,
      columnRows: await columns.locator('tbody tr').count(),
      expandMs: Math.round(ms),
    }
  } else {
    result.diffExpand = { note: '開ける行がありませんでした（違いのカラムが無い）' }
  }

  // NFR1.20: 10MB のファイルを選び、「投入」→ 置き換えの確かめの「投入する」の後、送り始めるまで。
  await page.getByRole('tab', { name: '投入' }).click()
  await page.getByTestId('dsl-submit-form').waitFor({ state: 'visible', timeout: TIMEOUT })
  const tSelect = performance.now()
  await page.getByTestId('dsl-submit-file').setInputFiles(values.large)
  const selectMs = performance.now() - tSelect
  const request = page.waitForRequest(
    (r) => r.url().includes('/api/admin/dsl/preview?') && r.method() === 'POST',
    { timeout: TIMEOUT },
  )
  const tSubmit = performance.now()
  await page.getByTestId('dsl-submit-button').click()
  let tConfirm = null
  const confirm = page.getByTestId('dsl-confirm-replace')
  // 今のプレビューがあるときは置き換えの確かめが出る（isVisible は待たないため waitFor で待つ）。
  const confirmShown = await confirm
    .waitFor({ state: 'visible', timeout: 5_000 })
    .then(() => true)
    .catch(() => false)
  if (confirmShown) {
    tConfirm = performance.now()
    await page.getByTestId('dsl-confirm-ok').click()
  }
  const sent = await request
  const tSent = performance.now()
  const response = await sent.response()
  await page.getByTestId('dsl-preview-panel').waitFor({ state: 'visible', timeout: TIMEOUT })
  result.fileSubmit = {
    fileBytes: (await sent.postDataBuffer())?.length ?? null,
    setInputFilesMs: Math.round(selectMs),
    submitClickToSendMs: Math.round(tSent - tSubmit),
    confirmClickToSendMs: tConfirm === null ? null : Math.round(tSent - tConfirm),
    submitHttp: response ? response.status() : null,
  }
} catch (error) {
  // 途中で失敗しても、それまでに測った値は残す。
  result.error = String(error?.message ?? error).split('\n')[0]
  process.exitCode = 1
} finally {
  await browser.close()
  writeFileSync(values.out, JSON.stringify(result, null, 2) + '\n')
  console.log(JSON.stringify(result))
}
