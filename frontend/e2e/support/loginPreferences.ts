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
// ログインの後の画面に表示の設定の組を当てる手伝い（Intent 260925-user-management の B5 の共用の手伝い。U5 の計画の Step 16 で
// 依頼者が決めた方法 A）。ログインの後は、ログインの応答の利用者の設定（theme・fontSize）が当たり、ブラウザの保存の値は
// 使われない（U4 の W5・D8）。そのため、ログイン（POST /api/auth/login）と Cookie での復元（POST /api/auth/session/refresh）の
// 本物の応答を受けてから、user.theme と user.fontSize の2項目だけを組の値に書き換えて返す。
// - サーバーの状態（内部DB の利用者の設定）は変えない。ほかの項目（アクセストークン・氏名など）とほかの API は書き換えない。
// - 応答の Cookie（リフレッシュトークン）は本物の応答のまま返す。書き換えはこのページの中だけで効く。
// - 失敗の応答や、形が違う応答（user が無い）は書き換えずにそのまま返す。
// - 応答の値を注記・添付・標準出力に出さない。U6・U7 の検査（070・080）も同じ当て方を使う。
// - 省略できる displayName を渡したときだけ user.displayName も書き換える（Intent 260930-user-admin の U4 の 130 が、上の帯の
//   Avatar の頭文字を2文字にする架空の2語の氏名を当てるために使う。NFR 設計の logical-components.md 6.2）。同じ要求に
//   差し替えを重ねると先に応答した1つしか効かないため、組の値と氏名を1つの差し替えで当てる。値が undefined の項目は
//   重ねないため、渡さない呼び出し（060）の動作は今までと同じで、displayName: undefined を渡しても今の氏名を消さない。
import type { Page } from '@playwright/test'
import type { FontSize } from '../../src/app/display-settings/displaySettingsTypes'

/** 当てる組の値 */
export interface LoginPreferences {
  theme: 'light' | 'dark'
  fontSize: FontSize
  /** 渡したときだけ user.displayName を書き換える（130 だけが渡す） */
  displayName?: string
}

/** 書き換える API のパス */
export const LOGIN_RESPONSE_PATHS = ['/api/auth/login', '/api/auth/session/refresh'] as const

/**
 * ログインと復元の応答の user.theme・user.fontSize を組の値に書き換える差し替えを置く（ログインの画面を開く前に呼ぶ）。
 * 書き換えた回数を返す（組が当たる道を通ったことの確かめに使う）。
 */
export async function routeLoginPreferences(
  page: Page,
  preferences: LoginPreferences,
): Promise<{ rewritten: number }> {
  const counter = { rewritten: 0 }
  // 値が undefined の項目は重ねない（渡さない呼び出しの動作を今と同じに保つ）。
  const overrides = Object.fromEntries(
    Object.entries(preferences).filter(([, value]) => value !== undefined),
  )
  await page.route(
    (url) => (LOGIN_RESPONSE_PATHS as readonly string[]).includes(url.pathname),
    async (route) => {
      if (route.request().method() !== 'POST') {
        return route.fallback()
      }
      const response = await route.fetch()
      if (!response.ok()) {
        return route.fulfill({ response })
      }
      let body: unknown
      try {
        body = await response.json()
      } catch {
        return route.fulfill({ response })
      }
      const user =
        typeof body === 'object' && body !== null ? (body as { user?: unknown }).user : undefined
      if (typeof user !== 'object' || user === null) {
        return route.fulfill({ response })
      }
      const rewritten = {
        ...(body as Record<string, unknown>),
        user: { ...(user as Record<string, unknown>), ...overrides },
      }
      counter.rewritten += 1
      return route.fulfill({ response, json: rewritten })
    },
  )
  return counter
}
