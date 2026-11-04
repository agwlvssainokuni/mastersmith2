# ビルドの手順 — Intent 261102-timezone-ui-polish

この Intent は新しい依存・ビルドのタスク・環境変数を足していない。手順は既存の `README.md`・`docs/verification.md` のとおりで、ここでは Build and Test で実際に流した形を記録する。

## 前提

| 項目 | 内容 |
|---|---|
| JDK・Node | 既存のとおり（`README.md` の「前提」） |
| コンテナの実行環境 | colima（Docker）。対象DB の結合テスト（Testcontainers）と E2E の見本の PostgreSQL・Mailpit に要る |
| サブモジュール | `vendor/make-you-chic-ui`（固定先 `d112795`）・`vendor/java-mustache-processor`。この Intent の中で make-you-chic-ui の固定先を `2aafe750` で上げた（専用のコミット） |
| 依存 | `frontend/` で `npm ci`（`ignore-scripts=true`）。Gradle は lockfile どおり |

## 1コマンドの検査（統合の前の関門）

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

- `DOCKER_HOST` と `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡さないと、対象DB の結合テストが `SKIPPED` になり、パッケージごとのカバレッジの下限で落ちる（`project.md`）。
- 件数とカバレッジを報告するときは `:backend:cleanTest :backend:cleanIntegrationTest` を付ける（UP-TO-DATE で飛ばさないため）。
- 長い流しは `caffeinate -dims` で包み、流した後に `pmset -g log` で Sleep・DarkWake が無いかを確かめる。眠った回の結果は使わない（Build and Test の前の確かめの場で、眠りで 30 分の上限に達した回があった。`test-results.md`）。

## E2E（`verify` と CI の外）

```bash
docker compose --profile mail up -d mailpit
docker compose -f compose.yaml -f docker/e2e/compose.targetdb-port.yaml --profile targetdb-postgres up -d targetdb-postgres
caffeinate -dims ./gradlew e2eTest
docker compose --profile targetdb-postgres up -d targetdb-postgres   # 流し終えたら公開の番号を閉じる
```

- 流す前に `frontend/test-results`・`frontend/playwright-report` を消しておく（git の対象外の生成物。コード生成の承認の場が止まるのを避ける。`project.md`）。
- 1ファイルだけ流すときは `-Pe2e.files=e2e/<ファイル>`。

## ビルドの確かめ

- 成果物は `verify` の中の `bootWar` が作る WAR（`frontend/dist` を同梱）。`verifyArtifact`・`verifyDslSchemaInWar`・`verifyFaviconInWar` が `verify` の中で通る。
- 画面の初回の読み込みの大きさ（`frontendBundleSize`）は `verify` の中で確かめる（2026-11-04 の時点で 169.7 KB（gzip））。

## よくある問題

| 症状 | 対処 |
|---|---|
| 対象DB の結合テストが `SKIPPED`、パッケージごとのカバレッジの下限で失敗 | `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` を渡して流し直す |
| 画面のテストが時間切れで散発的に落ちる | 眠りが入っていないかを `pmset -g log` で確かめる。入っていなければ `team.md` の不安定なテストの決まりで扱う（上限を原因を確かめずに延ばさない） |
| E2E が始まる前に止まる | Mailpit と見本の PG の公開の番号（`127.0.0.1:15432`）の起動を確かめる（`docs/verification.md`） |

## Sources

- 各単位の `construction/*/code-generation/unit-test-instructions.md`・`code-summary.md`
- `README.md`・`docs/verification.md`・`aidlc/spaces/default/memory/project.md`・`team.md`
