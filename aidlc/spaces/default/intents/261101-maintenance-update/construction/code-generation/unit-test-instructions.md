# 単位のテストの手順書（Intent 261101-maintenance-update）

Test Strategy は Minimal、scope は bugfix。版の更新は新しいテストを足さず、既存の `./gradlew verify`（全テスト・3種類の対象DB の結合テスト）と E2E の全件で確かめる。新しいテストは、要件ごとの1件と、bugfix の回帰の確かめ（P1・T3）に絞る。

## テストの道具

- backend: JUnit 5・AssertJ・Testcontainers（MySQL・MariaDB・PostgreSQL、版を固定したイメージ）、JaCoCo。単体は `XxxTest`、Spring や DB を起動する結合は `XxxIT`。
- frontend: Vitest・Testing Library（jsdom）・user-event・vitest-axe、`@vitest/coverage-v8`。テストは対象と同じ場所の `*.test.ts(x)`。伏せ字の道具 `perf/redact-k6-evidence.mjs` のテストは `frontend/src/perfEvidenceRedactor.test.ts`・`frontend/src/perfEvidenceRedactorCli.test.ts`。
- E2E: Playwright（`./gradlew e2eTest`）。
- colima の PC では、Gradle のコマンドに `DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock` を付ける。長いコマンドは `caffeinate -dims` で包む。

## この作業のテストだけを流すコマンド

最初のテストの前に、次のコマンドが今の形で動くことを確かめる（既存の作りのため、走らせる道具は既にある）。

1. P1（FR9.1。Step 18 で足す MySQL の結合テスト。クラスの名前は生成の時点で決め、`code-summary.md` に書く。仮の名前は `MysqlDeepPageSortIT`）:

   ```bash
   DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock ./gradlew :backend:integrationTest --tests 'cherry.mastersmith.businessdata.*MysqlDeepPageSortIT'
   ```

2. T3（FR9.4。Step 21 で足す単体テスト。`BusinessDataWriteService` の既存のテストのクラスに足すか、新しいクラスにするかは生成の時点で決める）:

   ```bash
   ./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.service.BusinessDataWriteService*'
   ```

3. T1・T2（FR9.2・FR9.3。伏せ字の道具のテスト）:

   ```bash
   cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/perfEvidenceRedactor.test.ts src/perfEvidenceRedactorCli.test.ts
   ```

4. T1 のカバレッジ（下限の閾値で終わりうるため、独立したコマンド。道具のファイルが報告に載ることを確かめる）:

   ```bash
   cd frontend && NODE_OPTIONS=--no-experimental-webstorage npx vitest run --coverage --coverage.include='../perf/redact-k6-evidence.mjs' src/perfEvidenceRedactor.test.ts src/perfEvidenceRedactorCli.test.ts
   ```

   このコマンドは、全体の `thresholds` が絞った範囲に当たると、テストがすべて通っても閾値で非ゼロで終わることがある。その場合はテストの結果と閾値の失敗を分けて記録する（テストの失敗ではない）。

5. T2 の ESLint と型の検査（Step 20 の決定どおりの対象）:

   ```bash
   cd frontend && npx eslint ../perf/redact-k6-evidence.mjs
   ```

   型の検査のコマンド（承認の場の Q-B で案 A のとき `npx tsc --noEmit -p <道具の tsconfig>`）は、Step 20 で決めた形を `code-summary.md` に書く。

6. Node 26 の起動の旗（FR2.2。Step 7 で `NODE_OPTIONS` を確かめる）:

   ```bash
   cd frontend && node --version && NODE_OPTIONS=--no-experimental-webstorage npx vitest run src/perfEvidenceRedactor.test.ts
   ```

## カバレッジの目標

- 下限は、すべての Intent に共通の 行 80%・分岐 70%（backend は全体の合計とパッケージごと、frontend は全体の合計）。この作業のコマンドでは判定せず、Build and Test が全体のテストで確かめる（`:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify`）。
- T1 で伏せ字の道具を frontend の計測の対象に入れる。下限を下げる・除外を足すことはしない。
- `packagesJudgedByTotal` の一覧のパッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）の `src/main` には手を入れない見込み。手を入れることになったら、`team.md` の決まり（テストを足して一覧から外す）を当てる。

## モックの方針

- 対象DB はモックにせず、版を固定したイメージのコンテナで動かす（P1）。
- T3 は `BusinessDataWriteService` の単体テストで、参照の確かめの段の失敗を起こす部品を差し替え、`StepAttempt.Unexpected` の経路を作る。ログは既存のテストと同じ方法（Logback の ListAppender など、生成の時点で既存のテストの形を確かめる）で受け、WARN のキーを確かめる。値（行の値・参照先の値）がログに出ないことも確かめる。

## テストデータ

- P1 は、テストのクラスごとに名前の重ならない MySQL のデータベースを作り、終わったら消す（`team.md`）。行の数・文字の長さ・`OFFSET`・1ページの件数は、生成の時点で既存の業務データの上限（1ページの件数の上限・文字の上限）から決めて記録する。値は架空の文字だけ。
- メールアドレスを使うときは `example.com` だけ。秘密の値・JWT は使わない（伏せ字の道具のテストは既存の作り物の値を使う）。
