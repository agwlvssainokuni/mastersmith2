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
// E2E の trace の設定の読み方を1か所に置く（Intent 260930-user-admin の U5、security-design.md 3.2・3.7）。
// playwright.config.ts の use.trace と、110・120 の test.use({ trace: e2eTraceMode() }) が同じ読み方を使う。
// - 既定は off（trace を残さない）。手元で調べるときだけ E2E_TRACE=on か retain-on-failure で有効にし、その実行は
//   合否に使わず、見た後に frontend/test-results/ を消す（README の手順）。
// - 決まった3つの外の値は、黙って off にせず、設定の読み込みで誤りにして止める。
// - playwright.config.ts を読み込まない（adminLogin.ts が設定を読み込むため、循環させない）。
// - 報告の部品と使い捨ての確かめの台本が Node の型の取り除き（--experimental-strip-types）でも読めるよう、Node の外の
//   部品を読み込まない。

/** E2E_TRACE に置ける値 */
export const TRACE_MODES = ['off', 'on', 'retain-on-failure'] as const

/** trace の設定 */
export type TraceMode = (typeof TRACE_MODES)[number]

/** 環境変数の名前 */
export const TRACE_ENV_NAME = 'E2E_TRACE'

/** 環境変数 E2E_TRACE から trace の設定を読む（無い・空なら off、決まった3つの外なら誤り）。 */
export function traceModeFromEnv(
  env: Readonly<Record<string, string | undefined>> = process.env,
): TraceMode {
  const value = env[TRACE_ENV_NAME]
  if (value === undefined || value === '') {
    return 'off'
  }
  const mode = TRACE_MODES.find((candidate) => candidate === value)
  if (mode === undefined) {
    throw new Error(
      `${TRACE_ENV_NAME} は ${TRACE_MODES.join('・')} のどれかにしてください（指定: ${JSON.stringify(value)}）。`,
    )
  }
  return mode
}

/** 110・120 の test.use に置く trace の設定（設定のファイルと同じ読み方。既定の実行では off）。 */
export function e2eTraceMode(): TraceMode {
  return traceModeFromEnv()
}
