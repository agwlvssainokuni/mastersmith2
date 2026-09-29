# テストの手順（Intent 260929-log-deps-cleanup）

- Test Strategy は Minimal（要件ごとに1つの確かめ、部品ごとに正常系の下限）。scope は bugfix のため、不具合（初期管理者の作成のログにメールアドレスそのものが出る）を再現するテストを、直しと同じコミットに入れる（`project.md` の Mandated）。
- methodology は test-after（計画の Testing Contract）。層ごとに実装してから、その層のテストを書いて流す。
- この文書のコマンドは、どれも変更の前のコードでも流せる（計画の Step 2 で確かめる）。変更の前は、足すテストがまだ無いだけで、既存のテストが通る。
- 監査の記録の失敗の ERROR（FR2.1・FR2.2）は、依頼者の決定（R-01、2026-09-29）で行わないため、テストを足さない（計画の 1.1）。

## 1. 道具と設定

| 対象 | 道具 | 設定の場所 |
|---|---|---|
| バックエンドの単体テスト（`*Test`） | JUnit 5・AssertJ・Mockito・jqwik | `backend/build.gradle.kts` の `tasks.test`（`includeTestsMatching("*Test")`） |
| バックエンドの結合テスト（`*IT`） | Spring Boot Test（`OutputCaptureExtension`）、組み込みの H2 | `backend/build.gradle.kts` の `integrationTest`（`includeTestsMatching("*IT")`） |
| ログの取り込み | `cherry.mastersmith.common.testsupport.LogEvents`（単体）、`JsonLogRecords`（標準出力の JSON） | `backend/src/test/java/cherry/mastersmith/common/testsupport/` |
| カバレッジ | JaCoCo（全体とパッケージごとの下限） | `backend/build.gradle.kts` の `jacocoTestCoverageVerification`・`packagesJudgedByTotal` |
| E2E | Playwright・axe-core | `frontend/playwright.config.ts`、`./gradlew e2eTest`（`build.gradle.kts`） |
| 画面のファイルの書式・リンタ | Prettier・oxlint・ESLint | `frontend/` の設定 |

- 新しい道具・設定は足さない。既存の仕組みで足りる。
- `./gradlew verify` と対象DB の結合テストは colima が動いていることを前提にする。colima の PC では、流す前にシェルに次を渡す（渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗する。`project.md` の Testing Posture）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

## 2. この Intent のテストだけを流すコマンド

どのコマンドもリポジトリのルートで実行する。全体のコマンド（`./gradlew test`・`npm test` など）は使わない。

### 2.1 バックエンドの単体テスト（FR1.1・FR1.2・NFR1）

```bash
./gradlew :backend:test \
  --tests 'cherry.mastersmith.user.domain.EmailAddressTest' \
  --tests 'cherry.mastersmith.user.service.InitialAdminInitializerTest'
```

- `EmailAddressTest`: 伏せ字の関数 `EmailAddress.mask` の例と性質（FR1.2）。部品 `EmailAddress` の正常系の下限。
- `InitialAdminInitializerTest`: INFO の3か所（作った・既にいる・同時の起動で既にいた）に、メールアドレスそのものとキー `email` が無く、キー `maskedEmail` に伏せ字が載ること（FR1.1、再現のテスト。キーの名前は依頼者の答え Q-B: B）。部品 `InitialAdminInitializer` の正常系の下限。

### 2.2 バックエンドの結合テスト（FR1.1・FR1.3・NFR1）

```bash
./gradlew :backend:integrationTest \
  --tests 'cherry.mastersmith.user.service.InitialAdminIT'
```

- `InitialAdminIT.createsOnce`: 実際に2回起動し、標準出力の JSON のログに、そろえたメールアドレスも設定の値も無く、初期管理者の INFO のキー `maskedEmail` に伏せ字があり、キー `email` が無いこと。文言（「初期管理者を作成しました」「初期管理者は既にいるため」）が出ること（FR1.3）。組み込みの H2 を使い、コンテナは使わない。

### 2.3 画面の E2E のファイルの書式・リンタ（FR3.2）

```bash
(cd frontend && npx prettier --check e2e/100-app-text-contrast.e2e.ts \
  && npx oxlint e2e/100-app-text-contrast.e2e.ts \
  && npx eslint e2e/100-app-text-contrast.e2e.ts)
```

- 型の検査はファイル単位で流せない（`frontend/tsconfig.json` の設定で検査する）ため、計画の Step 15（`npm run typecheck`）と Step 21（`verify`）で確かめる。

### 2.4 E2E 100（FR3.2、A3）

WAR を作り、Mailpit を起動してから、100 だけを流す。

```bash
./gradlew :backend:bootWar
docker compose --profile mail up -d mailpit
(cd frontend && npx playwright test e2e/100-app-text-contrast.e2e.ts)
```

- 変更の前（計画の Step 4、固定先 `310e1ec`）: 通る。既知の違反の2件が一覧どおりに当たる。
- 固定先を上げた直後（Step 10、一覧はまだ2件）: 2件が当たらなくなっていれば「既知の違反が一覧と違います」で失敗する見込み。結果は `frontend/test-results/e2e-results.json` の注記（`axe`）の `knownViolations`・`unexpected` で読む。
- 一覧を直した後（Step 11）: 20 組・すべての状態で通る。Step 10 で別の違反が新しく出たときは、その組・状態・要素だけを一覧に足して通す（依頼者の答え Q-E: B。足したものは code-summary.md と README の既知の制約に書く）。
- 終わったら Mailpit を `docker compose stop mailpit` と `docker compose rm -f mailpit` で片付ける。

### 2.5 統合の前の関門（NFR2・NFR3。計画の Step 3・Step 21・Step 22）

絞ったコマンドの代わりではなく、関門として流す。テストの件数とカバレッジを報告するときは、UP-TO-DATE で飛ばさないよう次の形で流し、実測の数字だけを書く（`project.md` の Testing Posture）。

```bash
./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

画面に関わる変更（固定先の更新）を統合する前に、Mailpit を起動して E2E の全体（010〜100）を流す（`team.md` の Testing Posture）。

```bash
docker compose --profile mail up -d mailpit
./gradlew e2eTest
```

## 3. カバレッジの目標

- 全体とパッケージごとの下限: 行 80% 以上・分岐 70% 以上（`team.md` の Testing Posture）。下限と除外は変えない。
- 変えるパッケージの `cherry.mastersmith.user.domain`・`cherry.mastersmith.user.service` は、どちらもパッケージごとの下限の対象（`packagesJudgedByTotal` に無い）。`EmailAddress.mask` の分岐（null・空、`@` が無い、ローカル部が空、通常）は `EmailAddressTest` の例ですべて通す。
- 値は `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` の JaCoCo の報告（`backend/build/reports/jacoco/`）で読み、変更の前（Step 3）と後（Step 21）を並べて記録する。
- frontend のカバレッジ（`@vitest/coverage-v8`）は、この Intent で画面のソースを変えないため、`verify` の中で下限を保つことだけを確かめる。

## 4. モックと差し替えの方針

- `InitialAdminInitializerTest`: 既存のとおり `UserAccountService` を Mockito で差し替え、`existsByEmail`・`createUser` の答えで3つの経路（作った・既にいる・同時の起動で既にいた）を作る。ログは `LogEvents.capture(InitialAdminInitializer.class)` で取り込み、本文とキー・値（`getFormattedMessage()` と `getKeyValuePairs()`）を確かめる。キー・値はキーの名前ごとに読み、`maskedEmail` の値と `email` が無いことを分けて確かめる。
- `EmailAddressTest`: 純粋な関数のため差し替えは無い。
- `InitialAdminIT`: 差し替えずに実際にアプリを起動する（ログの出口の標準出力まで確かめるため。bugfix の再現を最も狭く確かめられるのは単体テストだが、キー・値が JSON の出力に出ないことは結合テストで確かめる）。
- E2E 100: 既存のとおり `GET /api/me/preferences`・`PUT /api/me/preferences`・`/api/appearance` の答えを見本に差し替える（変えない）。
- 送信の部品・監査の書き込みは、この Intent のテストでは差し替えない（関わらない）。

## 5. テストデータ

- メールアドレスは既存のテストと同じく `example.com` のドメインの架空の値を使う（例 `admin@example.com`、設定の値 `Admin@Example.COM`）。実在の宛先は使わない。
- 伏せ字の期待値はテストの中に文字どおり書く（例 `a***@example.com`）。計算で作らない（実装と同じ誤りを持ち込まないため）。
- 境界の例: ローカル部が1文字（`a@example.com`）、先頭がサロゲートペアの文字（例 `𠮷田@example.jp` → `𠮷***@example.jp`）、`@` が無い値、ローカル部が空の値、null と空。
- 性質ベースのテストは、英数字のローカル部（2文字以上）とドメインから値を作る（ローカル部が `a***` のような伏せ字の形そのものになる値は作らない）。失敗したときは jqwik が表示する乱数の種を記録して再現する。
- パスワードは既存のテストと同じく、テストの中の定数か `TestDatabase.randomSecret()` から作り、ログに出ないことを既存の確かめのまま残す。
- `InitialAdminIT` は `@TempDir` の一時の内部DB を使い、テストごとに作って捨てる。
- E2E の初期管理者の資格情報は、既存のとおり `./gradlew e2eTest` がプロセスの環境変数で渡す。json の報告にパスワード・トークン・メールアドレスが残らないことは `frontend/playwright-secret-check-reporter.ts` が確かめる。報告（`frontend/test-results/`・`frontend/playwright-report/`）はコミット・共有しない。

## 6. 要件ごとの確かめ

| 要件 | 確かめるテスト・コマンド |
|---|---|
| FR1.1 | 2.1 の `InitialAdminInitializerTest`（再現のテスト）、2.2 の `InitialAdminIT` |
| FR1.2 | 2.1 の `EmailAddressTest` |
| FR1.3 | 2.2 の `InitialAdminIT`（文言の確かめ、既存） |
| FR2.1・FR2.2 | 行わない（計画の 1.1） |
| FR3.2 | 2.3 と 2.4 の E2E 100 |
| FR4.1・FR4.2 | 2.5 の `verify`（書式の検査・型の検査・脆弱性の検査） |
| NFR1 | 2.1・2.2 |
| NFR2 | 2.5 の `verify` |
| NFR3 | 2.5 の `./gradlew e2eTest` |

FR1.4・FR3.1・FR3.3・FR4.3〜FR4.5・FR5・NFR4 はテストのコードではなく、計画の手順の中の確かめ（`git diff`・YAML と JSON の読み込み・検索・手元の監視の API。監視には要求を送らない。Q-D: A）で扱う（計画の 8節）。FR6 はこの段では行わず、Build and Test で team.md を直す（Q-C: A）。
