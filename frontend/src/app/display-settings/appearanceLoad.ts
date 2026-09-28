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
// 見た目の設定の読み取り（W3・D11、契約 C7、NFR6.2・NFR9.3）。
// React の描画の外で1回だけ GET /api/appearance を呼び、例外で終わらない約束を返す（StrictMode でも要求は1回）。
// トークンを付けないことは ApiClient の一覧（TOKENLESS_API_PATHS）が決める。
// 待ちに上限・再試行・中断は置かない（機能設計の Q2 A）。応答が返らないと全画面の最初の描画が止まりうるが、
// 依頼者が受け入れた決定で、今のセッションの復元と同じ待ち方である（W2 の5）。
import { apiFetch } from '../../shared/api-client/apiClient'
import type { Appearance } from './displaySettingsTypes'
import { parseAppearance } from './resolveDisplaySettings'

/** 見た目の設定の読み取りの結果（失敗・形の誤りは当てる値なしの空のオブジェクト） */
export type AppearanceResult = Appearance

/** 当てる値なし */
export const NO_APPEARANCE: AppearanceResult = Object.freeze({})

/** 答えが出た約束と、その答え（描画の中で答え済みかを見るため） */
const settled = new WeakMap<Promise<AppearanceResult>, AppearanceResult>()

let appearanceLoad: Promise<AppearanceResult> | null = null

/** 本文を読んで検証する。200 以外・JSON でない本文は当てる値なし。 */
async function readAppearance(): Promise<AppearanceResult> {
  const response = await apiFetch('/api/appearance')
  if (response.status !== 200) {
    return NO_APPEARANCE
  }
  return parseAppearance(await response.json())
}

/** 約束の答えを記録する。 */
function track(promise: Promise<AppearanceResult>): Promise<AppearanceResult> {
  void promise.then((result) => {
    settled.set(promise, result)
  })
  return promise
}

/**
 * 見た目の設定の読み取りを始める（2回目以降は同じ約束を返す）。約束は例外で終わらず、
 * 通信の失敗・200 以外・JSON でない本文・形の誤りは当てる値なしで終わる。
 */
export function startAppearanceLoad(): Promise<AppearanceResult> {
  appearanceLoad ??= track(readAppearance().catch(() => NO_APPEARANCE))
  return appearanceLoad
}

/** 答え済みの約束を作る（テストと、起動の誤りの画面などで使う）。 */
export function resolvedAppearance(
  result: AppearanceResult = NO_APPEARANCE,
): Promise<AppearanceResult> {
  const promise = Promise.resolve(result)
  settled.set(promise, result)
  return promise
}

/** 約束の答えがもう出ていればそれを返す。まだなら undefined。 */
export function peekAppearance(promise: Promise<AppearanceResult>): AppearanceResult | undefined {
  return settled.get(promise)
}

/** 読み取りの約束を消す（テストで使う）。 */
export function resetAppearanceLoad(): void {
  appearanceLoad = null
}
