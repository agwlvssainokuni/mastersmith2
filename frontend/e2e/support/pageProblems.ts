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
// 画面で起きた CSP の違反・スクリプトのエラーと、要求の一覧を集める（U4 の NFR 設計 performance-design.md 3.3、
// logical-components.md 5.1）。見方は既存の 010-skeleton.e2e.ts の collectProblems と同じ（010 は変えない）。
// page.goto より前に張る（NFR 設計の承認の場の U4 R-01）。
import type { Page } from '@playwright/test'

/** 集めた問題と要求 */
export interface PageWatch {
  /** CSP の違反・スクリプトのエラー（未ログインのトークンの更新の 401 の1件だけを除く） */
  problems: string[]
  /** CSP の違反だけ */
  cspViolations: string[]
  /** 送られた要求の URL */
  requests: string[]
}

/** ページの見張りを張る。 */
export async function watchPage(page: Page): Promise<PageWatch> {
  const watch: PageWatch = { problems: [], cspViolations: [], requests: [] }
  page.on('pageerror', (error) => watch.problems.push(`pageerror: ${error.message}`))
  page.on('console', (message) => {
    if (message.type() !== 'error') {
      return
    }
    // 未ログインで画面を開くと、ログイン状態の復元がトークンの更新を1回試みて 401 になる（BR8.4）。
    // ブラウザはこれを「資源の読み込みの失敗」として表示するため、その1件だけを除く。
    if (
      message.location().url.includes('/api/auth/session/refresh') ||
      message.text().includes('/api/auth/session/refresh')
    ) {
      return
    }
    const text = message.text()
    watch.problems.push(`console: ${text}`)
    if (text.startsWith('CSP violation:')) {
      watch.cspViolations.push(text)
    }
  })
  page.on('request', (request) => watch.requests.push(request.url()))
  await page.addInitScript(() => {
    document.addEventListener('securitypolicyviolation', (event) => {
      console.error(`CSP violation: ${event.violatedDirective} ${event.blockedURI}`)
    })
  })
  return watch
}
