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
// 110・120 の失敗のときの、値を伏せた手がかり（Intent 260930-user-admin の U5、security-design.md 3.6）。110・120 は trace を
// 残さないため、テストの間に値を含まない記録を集め、テストが期待と違う結果になったときだけ注記と添付
// （名前 user-admin-diagnostics）に残す。成功したテストには残さない。
// - 通った手順の題（step で通した test.step の題。題には値を入れない決まり）
// - /api/admin/・/api/auth/ の下の要求のメソッドと道の型（利用者 ID は {id}、問い合わせと # の後は載せない）と状態コード
// - 見張り（watchPage）の問題の件数と CSP の違反の件数（文は載せない）
// - 今の画面の道の型（決まった道に当たらなければ「その他」）と、一覧の行の数
// 添付は body で書くため json の報告に base64 で入る。報告の部品が復号して探すため、誤って値が混ざれば部品が見つける。
import { test, type Page, type TestInfo } from '@playwright/test'
import type { PageWatch } from './pageProblems'

/** 決まった画面の道 */
const KNOWN_SCREEN_PATHS = [
  '/',
  '/login',
  '/admin',
  '/admin/users',
  '/admin/invitations',
  '/admin/dsl',
  '/me/preferences',
  '/me/password',
  '/register',
] as const

/** 記録する要求の道の根 */
const RECORDED_API_PREFIXES = ['/api/admin/', '/api/auth/'] as const

/** 1つのページの記録 */
interface PageRecord {
  label: string
  page: Page
  watch?: PageWatch
}

/** 値を伏せた手がかりを集める */
export interface UserAdminDiagnostics {
  /** ページを見張る（label は決まった名前。値を入れない） */
  watch(label: string, page: Page, watch?: PageWatch): void
  /** test.step を通し、題を記録する */
  step<T>(title: string, body: () => Promise<T>): Promise<T>
  /** テストが期待と違う結果のときだけ、注記と添付に残す */
  attachOnFailure(testInfo: TestInfo): Promise<void>
}

/** 道の型（数字だけの部分を {id} にする） */
function apiTemplate(pathname: string): string {
  return pathname
    .split('/')
    .map((part) => (/^\d+$/.test(part) ? '{id}' : part))
    .join('/')
}

/** 今の画面の道の型 */
function screenPath(url: string): string {
  try {
    const { pathname } = new URL(url)
    return (KNOWN_SCREEN_PATHS as readonly string[]).includes(pathname) ? pathname : 'その他'
  } catch {
    return 'その他'
  }
}

/** 手がかりを集め始める。 */
export function startUserAdminDiagnostics(): UserAdminDiagnostics {
  const steps: string[] = []
  const requests: { page: string; method: string; path: string; status: number | 'failed' }[] = []
  const pages: PageRecord[] = []

  const record = (label: string, method: string, url: string, status: number | 'failed') => {
    let pathname: string
    try {
      pathname = new URL(url).pathname
    } catch {
      return
    }
    if (RECORDED_API_PREFIXES.some((prefix) => pathname.startsWith(prefix))) {
      requests.push({ page: label, method, path: apiTemplate(pathname), status })
    }
  }

  return {
    watch(label, page, watch) {
      pages.push({ label, page, watch })
      page.on('response', (response) =>
        record(label, response.request().method(), response.url(), response.status()),
      )
      page.on('requestfailed', (request) =>
        record(label, request.method(), request.url(), 'failed'),
      )
    },
    async step(title, body) {
      steps.push(title)
      return test.step(title, body)
    },
    async attachOnFailure(testInfo) {
      if (testInfo.status === testInfo.expectedStatus) {
        return
      }
      const screens = []
      for (const entry of pages) {
        let rows = -1
        let screen = 'closed'
        if (!entry.page.isClosed()) {
          screen = screenPath(entry.page.url())
          rows = await entry.page
            .locator('[data-testid="useradmin-table"] tbody tr')
            .count()
            .catch(() => -1)
        }
        screens.push({
          page: entry.label,
          screen,
          rows,
          problems: entry.watch?.problems.length ?? null,
          cspViolations: entry.watch?.cspViolations.length ?? null,
        })
      }
      const diagnostics = { steps, requests, screens }
      testInfo.annotations.push({
        type: 'user-admin-diagnostics',
        description: JSON.stringify({ steps: steps.length, requests: requests.length, screens }),
      })
      await testInfo.attach('user-admin-diagnostics', {
        body: JSON.stringify(diagnostics, null, 2),
        contentType: 'application/json',
      })
    },
  }
}
