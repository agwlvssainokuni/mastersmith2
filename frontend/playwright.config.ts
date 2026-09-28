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
// - メールは手元の受け手 Mailpit（docker compose --profile mail up -d mailpit）へ送る。./gradlew e2eTest は始める前に Mailpit に
//   届くかを確かめ、届かなければ起動の手順を示して失敗する（Intent 260925-user-management の U1、基盤の設計の Q1 A）。
import { randomBytes } from 'node:crypto'
import { mkdtempSync } from 'node:fs'
import { tmpdir } from 'node:os'
import path from 'node:path'
import { defineConfig, devices } from '@playwright/test'

const port = Number(process.env.E2E_PORT ?? 18081)
const warPath = path.resolve(import.meta.dirname, '../backend/build/libs/mastersmith.war')
const dataDir = mkdtempSync(path.join(tmpdir(), 'mastersmith-e2e-'))

// 署名鍵と初期管理者の値は、実行のたびに作ってアプリへ環境変数で渡す（リポジトリに値を置かない。U2 計画の D1）。
// この設定のファイルはテストの実行の側でも読み込まれるため、作った値を環境変数に入れて両方で同じ値を使う。
export const adminEmail = 'e2e-admin@example.com'
process.env.E2E_ADMIN_PASSWORD ??= `e2e-${randomBytes(12).toString('hex')}`
process.env.E2E_SIGNING_KEY ??= randomBytes(32).toString('base64')
export const adminPassword = process.env.E2E_ADMIN_PASSWORD
const signingKey = process.env.E2E_SIGNING_KEY

export default defineConfig({
  testDir: './e2e',
  testMatch: '**/*.e2e.ts',
  fullyParallel: false,
  // 1つの WAR と内部DBを全ファイルで共有し、040 が DSL を適用して状態を変えるため、番号の順（ファイル名の順）に1本ずつ実行する。
  workers: 1,
  forbidOnly: true,
  retries: 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  use: {
    baseURL: `http://localhost:${port}`,
    locale: 'ja-JP',
    trace: 'retain-on-failure',
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
      MASTERSMITH_AUTH_SIGNING_KEY: signingKey,
      MASTERSMITH_AUTH_INITIAL_ADMIN_EMAIL: adminEmail,
      MASTERSMITH_AUTH_INITIAL_ADMIN_PASSWORD: adminPassword,
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
