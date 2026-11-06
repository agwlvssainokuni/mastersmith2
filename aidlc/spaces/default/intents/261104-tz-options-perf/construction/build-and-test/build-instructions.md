# ビルドの手順 — Intent 261104-tz-options-perf

この Intent は新しい依存・ビルドのタスク・環境変数・設定を足していない（Temporal は Node 26 とブラウザの標準の機能）。手順は既存の `README.md`・`docs/verification.md`・`docs/target-db.md` のとおりで、ここでは Build and Test で流す形を記録する。

## 前提

| 項目 | 内容 |
|---|---|
| JDK・Node | 既存のとおり（`README.md` の「前提」）。Node 26（`frontend/package.json` の `engines`。Temporal を持つ） |
| コンテナの実行環境 | colima（Docker）。対象DB の結合テスト（Testcontainers）と E2E の見本の PostgreSQL・Mailpit に要る |
| サブモジュール | `vendor/make-you-chic-ui`・`vendor/java-mustache-processor`（この Intent では固定先を変えていない） |
| 依存 | `frontend/` で `npm ci`（`ignore-scripts=true`）。Gradle は lockfile どおり。この Intent で依存は変えていない |

## 1コマンドの検査（統合の前の関門）

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

- `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡さないと、対象DB の結合テストが `SKIPPED` になり、パッケージごとのカバレッジの下限で落ちる（`project.md`）。
- 件数とカバレッジを報告するときは `:backend:cleanTest :backend:cleanIntegrationTest` を付ける。
- 流す前に `pmset -g assertions`、流した後に `pmset -g log` で Sleep・DarkWake が無いかを確かめる。

## E2E（`verify` と CI の外。Intent の最後に全体を1回）

```bash
docker compose --profile mail up -d mailpit
docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml --profile targetdb-postgres up -d targetdb-postgres
caffeinate -dims ./gradlew e2eTest
docker compose --profile targetdb-postgres up -d targetdb-postgres   # 流し終えたら公開の番号を閉じる
```

- 流す前に `frontend/test-results`・`frontend/playwright-report` が無いことを確かめる（`project.md`）。
- 報告の json（`frontend/test-results/e2e-results.json`）を文字列で検索して、パスワード・トークン・メールアドレス・対象DB の資格情報が入っていないことを確かめる（`team.md`・`project.md`）。

## 性能のテストの値の読み方

- 選択肢の組み立て（`TimeZoneOptionsPerformance.test.ts`）は `[time-zone-options] … median=…` の行、変換（`conversionPerformance.test.ts`）は `[timeZone performance] …` の行を読む。Vitest 5 はエージェントの中で動くと通ったテストの console 出力を隠すため、画面のテストだけを流すときは `(cd frontend && npm run test:coverage -- --reporter=default)` で流す。`verify` の中の出力はログに残る。

## CI

- `develop` へ統合した後、依頼者が `git push` する。CI（`.github/workflows/ci.yml` の `verify` の job）の結果は `gh run list`・`gh run view --json attempt,jobs` と job のログで、試行ごとに確かめる（`project.md`）。

## Sources

- `construction/code-generation/unit-test-instructions.md`・`code-summary.md`・`code-generation-plan.md`
- `README.md`・`docs/verification.md`・`docs/target-db.md`・`aidlc/spaces/default/memory/project.md`・`team.md`
