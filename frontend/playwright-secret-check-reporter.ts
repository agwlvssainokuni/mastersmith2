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
// E2E の json の結果（test-results/e2e-results.json）に、実行ごとに作る仮の資格情報（署名鍵・初期管理者のメールアドレス・
// 仮のパスワード）が含まれないことを確かめる Playwright の報告の部品（Intent 260925-user-management の U4、依頼者の決定）。
// json の報告の後に並べて使い、含まれていれば実行の結果を失敗にする。値は表示せず、環境変数の名前だけを示す。
// Playwright の決まりで default のエクスポートが要るため、構文の禁止を当てない frontend/ の直下に置く（設定のファイルと同じ扱い）。
import { existsSync, readFileSync } from 'node:fs'
import type { FullResult, Reporter } from '@playwright/test/reporter'

/** 報告の部品の設定 */
export interface SecretCheckOptions {
  /** 確かめる json の結果のファイル（frontend/ からの相対パス） */
  outputFile: string
  /** 値を確かめる環境変数の名前 */
  envNames: readonly string[]
}

/** json の結果に仮の資格情報が含まれないことを確かめる。 */
export default class SecretCheckReporter implements Reporter {
  private readonly options: SecretCheckOptions

  constructor(options: SecretCheckOptions) {
    this.options = options
  }

  async onEnd(): Promise<{ status: FullResult['status'] } | undefined> {
    const { outputFile, envNames } = this.options
    if (!existsSync(outputFile)) {
      console.error(
        `E2E の json の結果（${outputFile}）が見つからず、仮の資格情報の確かめができません。`,
      )
      return { status: 'failed' }
    }
    const text = readFileSync(outputFile, 'utf8')
    const leaked = envNames.filter((name) => {
      const value = process.env[name]
      return value !== undefined && value !== '' && text.includes(value)
    })
    if (leaked.length > 0) {
      console.error(
        `E2E の json の結果に仮の資格情報が含まれています（値は表示しません）: ${leaked.join(', ')}`,
      )
      return { status: 'failed' }
    }
    console.log(
      `E2E の json の結果に仮の資格情報は含まれていません（${envNames.length} 項目を確かめた）。`,
    )
    return undefined
  }
}
