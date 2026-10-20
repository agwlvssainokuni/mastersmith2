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
// ビルドした WAR での画面の確認（Playwright）の設定（計画の P2 の決定: Gradle の別のタスク e2eTest で実行し、
// ./gradlew verify と CI には入れない）。
// - 事前に ./gradlew :backend:bootWar で backend/build/libs/mastersmith.war を作っておく。
// - WAR は一時ディレクトリの内部DB（H2 のファイル）で、使っていない番号（既定 18081）で起動する。
// - ブラウザは Chromium だけを使う（npx playwright install chromium）。
// - 対象DB は見本の PostgreSQL（上乗せの compose で 127.0.0.1:15432 に開ける）。./gradlew e2eTest が実行のスキーマと一時のロールを
//   作って環境変数で渡し、終わったら消す。直接 npx playwright test を流すと、その値が無いため下で止まる（Intent 261016-business-data-views の U3）。
// - メールは手元の受け手 Mailpit（docker compose --profile mail up -d mailpit）へ送る。./gradlew e2eTest は始める前に Mailpit に
//   届くかを確かめ、届かなければ起動の手順を示して失敗する（Intent 260925-user-management の U1、基盤の設計の Q1 A）。
// - 結果は list と json（test-results/e2e-results.json）に書く。html の報告は作らない（Intent 260930-user-admin の U5、
//   security-design.md 3.2。html の報告は fill の手順の題に入れた値を書くため）。050 などの組ごとの成否・違反の件数と、
//   画面の時間を Build and Test が json から写す（Intent 260925-user-management の U4、基盤の設計の Q2 A）。
//   結果のファイルはコミット・共有しない。
// - trace の既定は off（e2e/support/traceMode.ts）。手元で調べるときだけ E2E_TRACE=on か retain-on-failure で有効にし、
//   その実行は合否に使わず、見た後に test-results/ を消す。決まった3つの外の値は、この設定の読み込みで誤りにして止める。
// - json の報告の後に、報告に残してはならない値（仮の資格情報・110 が作る利用者の値と形）が json の報告・test-results/ の下・
//   前の playwright-report/ に含まれないことを確かめる報告の部品を並べ、含まれていれば実行を失敗にする
//   （playwright-secret-check-reporter.ts、security-design.md 3.5）。
import { randomBytes } from 'node:crypto'
import { mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { defineConfig, devices } from '@playwright/test'
import { SECRET_VALUES_FILE } from './e2e/support/secretValues'
import { traceModeFromEnv } from './e2e/support/traceMode'

const port = Number(process.env.E2E_PORT ?? 18081)
const warPath = path.resolve(import.meta.dirname, '../backend/build/libs/mastersmith.war')
const dataDir = mkdtempSync(path.join(tmpdir(), 'mastersmith-e2e-'))

// 署名鍵と初期管理者の値は、実行のたびに作ってアプリへ環境変数で渡す（リポジトリに値を置かない。U2 計画の D1）。
// この設定のファイルはテストの実行の側でも読み込まれるため、作った値を環境変数に入れて両方で同じ値を使う。
// 値は webServer.env に置かず、このプロセスの環境変数に置く（WAR の起動はこのプロセスの環境変数を引き継ぐ）。
// webServer.env は json の結果の config にそのまま書かれるため（Intent 260925-user-management の U4、依頼者の決定）。
export const adminEmail = 'e2e-admin@example.com'
process.env.E2E_ADMIN_PASSWORD ??= `e2e-${randomBytes(12).toString('hex')}`
process.env.E2E_SIGNING_KEY ??= randomBytes(32).toString('base64')
export const adminPassword = process.env.E2E_ADMIN_PASSWORD
process.env.MASTERSMITH_AUTH_SIGNING_KEY = process.env.E2E_SIGNING_KEY
process.env.MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL = adminEmail
process.env.MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD = adminPassword

// 業務データの E2E は見本の PostgreSQL を前提にする（Intent 261016-business-data-views の U3、基盤の設計 cicd-pipeline.md 3.3）。
// 対象DB の値（実行のスキーマと2つの一時のロール）は ./gradlew e2eTest が作り、このプロセスの環境変数で渡す（webServer.env に
// 置かない）。値が無いまま npx playwright test を直接流すと、対象DB の無い前提で黙って流れてしまうため、ここで止める（Q7: A）。
const TARGET_DB_ENV_NAMES = [
  'MASTERSMITH_TARGET_DB_TYPE',
  'MASTERSMITH_TARGET_DB_HOST',
  'MASTERSMITH_TARGET_DB_PORT',
  'MASTERSMITH_TARGET_DB_DATABASE',
  'MASTERSMITH_TARGET_DB_SCHEMA',
  'MASTERSMITH_TARGET_DB_USERNAME',
  'MASTERSMITH_TARGET_DB_PASSWORD',
  'MASTERSMITH_TARGET_DB_WRITE_USERNAME',
  'MASTERSMITH_TARGET_DB_WRITE_PASSWORD',
  'E2E_BIZ_SCHEMA',
] as const
const missingTargetDbEnv = TARGET_DB_ENV_NAMES.filter((name) => !process.env[name])
if (missingTargetDbEnv.length > 0) {
  throw new Error(
    `E2E は ./gradlew e2eTest から流してください（見本の PostgreSQL の実行のスキーマと一時のロールを作って渡します。` +
      `ファイルを選ぶときは -Pe2e.files=e2e/<ファイル>）。足りない環境変数: ${missingTargetDbEnv.join(', ')}`,
  )
}
// 対象DB の接続先（ホストと番号の組）も報告に残さない値として探す（基盤の設計 3.3 の「コード生成で決める」。秘密ではないが、
// 接続先の情報を報告に出さない決まり（project.md の Forbidden）を確かめるため）。
process.env.E2E_TARGETDB_ENDPOINT = `${process.env.MASTERSMITH_TARGET_DB_HOST}:${process.env.MASTERSMITH_TARGET_DB_PORT}`

/** 報告に含まれてはならない値の環境変数の名前と、その種類の名前（値は表示しない） */
const SECRET_ENV_NAMES = [
  { name: 'MASTERSMITH_AUTH_SIGNING_KEY', kind: 'signingKey' },
  { name: 'MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL', kind: 'adminEmail' },
  { name: 'MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD', kind: 'adminPassword' },
  // 一時の2つのロール（ユーザー名とパスワード）と接続先（Intent 261016-business-data-views の U3）。管理者の値はプロセスに無い。
  { name: 'MASTERSMITH_TARGET_DB_USERNAME', kind: 'targetDbUsername' },
  { name: 'MASTERSMITH_TARGET_DB_PASSWORD', kind: 'targetDbPassword' },
  { name: 'MASTERSMITH_TARGET_DB_WRITE_USERNAME', kind: 'targetDbWriteUsername' },
  { name: 'MASTERSMITH_TARGET_DB_WRITE_PASSWORD', kind: 'targetDbWritePassword' },
  { name: 'E2E_TARGETDB_ENDPOINT', kind: 'targetDbEndpoint' },
]
const resultsDir = 'test-results'
const jsonResultsFile = `${resultsDir}/e2e-results.json`
/** trace の設定（既定は off。決まった3つの外の値なら、ここで誤りにして止める） */
const traceMode = traceModeFromEnv()

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  fullyParallel: false,
  // 1つの WAR と内部DBを全ファイルで共有し、040 が DSL を適用して状態を変えるため、番号の順（ファイル名の順）に1本ずつ実行する。
  workers: 1,
  forbidOnly: true,
  retries: 0,
  outputDir: resultsDir,
  reporter: [
    ['list'],
    ['json', { outputFile: jsonResultsFile }],
    [
      './playwright-secret-check-reporter.ts',
      {
        outputFile: jsonResultsFile,
        envNames: SECRET_ENV_NAMES,
        resultsDir,
        secretValuesFile: SECRET_VALUES_FILE,
        previousHtmlReportDir: 'playwright-report',
        traceMode,
      },
    ],
  ],
  use: {
    baseURL: `http://localhost:${port}`,
    locale: 'ja-JP',
    trace: traceMode,
  },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: {
    command: [
      'java',
      // 内部DB（H2 2.4.240）の閉じるときの詰め直しのスレッドを 1 本に固定する（本番の Dockerfile と同じ。並べると H2 の中の
      // 競合で詰め直しが中断されることがある）。H2 を上げるときは、この指定が要るかを見直す。
      '-Dh2.compactThreads=1',
      '-jar',
      JSON.stringify(warPath),
      `--server.port=${port}`,
      `--spring.datasource.url=jdbc:h2:file:${path.join(dataDir, 'mastersmith')}`,
    ].join(' '),
    env: {
      // メールの送り先は手元の受け手 Mailpit だけ（暗号化なし・資格情報なし。実在の宛先・外部の SMTP へは送らない）。
      SPRING_MAIL_HOST: 'localhost',
      SPRING_MAIL_PORT: '1025',
      MASTERSMITH_MAIL_FROM: 'e2e-noreply@example.com',
      // 招待のリンクの元（U3）。Origin の確かめとエラー応答の type にも使われるため、E2E が開く URL と同じにする。
      MASTERSMITH_WEB_BASE_URL: `http://localhost:${port}`,
    },
    url: `http://localhost:${port}/actuator/health`,
    timeout: 120_000,
    reuseExistingServer: false,
    stdout: 'ignore',
    stderr: 'pipe',
  },
})
