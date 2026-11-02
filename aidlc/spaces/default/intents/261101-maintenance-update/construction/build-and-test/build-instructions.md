# ビルドの手順 — Build and Test（Intent 261101-maintenance-update）

依存・実行環境・道具・文書の最新化と小さな直し（P1・T1〜T3）を、`develop` の最後の形（`45a5872a`）で組んで確かめる手順。手順の正は `README.md`（前提・取得と準備）と `docs/verification.md`（1コマンドの検査・E2E）で、ここでは今回の確かめに使った形だけを書く。パスはプロジェクトのルートからの相対。

## 1. 前提（道具と依存）

| 道具 | 版 | 入れ方・確かめ方 |
|---|---|---|
| JDK | 25（Temurin。CI は 25.0.5+8、Dockerfile の JRE も 25.0.5_8） | `README.md` の前提の表 |
| Node.js・npm | 26（手元は v26.13.0） | `./gradlew verify` の `checkToolchain` が `v26.` を確かめ、違えば止まる |
| Gradle | wrapper 9.8.2 | `./gradlew` |
| Gitleaks・OSV-Scanner | 8.30.1・2.7.0 | 見つからなければ `verify` が入れ方を示して止まる |
| コンテナの実行環境 | colima（CPU 4・メモリ 6GiB） | 対象DB 3種類の結合テスト・E2E・負荷の試験の前提 |
| Playwright の Chromium | `@playwright/test` と同じ版 | `cd frontend && npx playwright install chromium`（E2E だけ） |
| サブモジュール | `vendor/make-you-chic-ui` `cae5fed`・`vendor/java-mustache-processor` `d61ba6d`（0.1.1） | `git submodule update --init` |

依存は lockfile どおりに入れる（`verify` の準備が `npm ci` を流す。`frontend/.npmrc` の `ignore-scripts=true`）。Gradle の依存は `backend/gradle.lockfile` と Maven Central だけ。

## 2. 環境の設定

colima の PC では次の2つを渡す（渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で落ちる）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

長い流し（verify・E2E・k6）は `caffeinate -dims` で包み、流す前に `pmset -g assertions`、流した後に `pmset -g log` で `Sleep`・`DarkWake` が無いことを確かめる。`.env`・`.env.targetdb` は開かない。

## 3. ビルドと1コマンドの検査

```bash
./gradlew --stop
DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock" TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
  caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

`verify` は準備 → 書式 → リンタ（伏せ字の道具の `perfToolEslint`・`perfToolTypecheck` を含む）→ ライセンスヘッダー → ビルド → 単体 → 結合（Testcontainers の対象DB 3種類）→ カバレッジの下限 → 安全の検査（`spotbugsGate`・`gitleaksScan`・`perfEvidenceScan`・`osvScan`）を流す。WAR だけは `./gradlew :backend:bootWar`（`backend/build/libs/mastersmith.war`）。

## 4. ビルドの確かめ

- `verify` が `BUILD SUCCESSFUL`、テストの失敗 0、SKIPPED は DB の種類ごとの前からの `assumeTrue` の 10 件だけ。
- カバレッジ: `backend/build/reports/jacoco/test/jacocoTestReport.xml`（全体と `packagesJudgedByTotal` の外のパッケージごとに 行 80%・分岐 70%）、`frontend/coverage/coverage-summary.json`（全体。T1 で `perf/redact-k6-evidence.mjs` を含む）。
- 負荷の試験のイメージは、配備のタグ `local` を上書きしないよう別のタグで作る: `docker build -t mastersmith:perf-maintenance-update .`。中の WAR の SHA-256 が手元の WAR と同じことを確かめる。
- 実測は `test-results.md`。

## 5. よくあるつまずき

| 症状 | 原因と手当て |
|---|---|
| 対象DB の結合テストが SKIPPED になり、パッケージごとのカバレッジで落ちる | 2節の環境変数が無い。付けて流し直す |
| 件数がいつもより少ない | テストのタスクが UP-TO-DATE。`:backend:cleanTest :backend:cleanIntegrationTest` を付ける |
| `checkToolchain` で止まる | 手元の Node が 26 でない（この Intent で 24 から 26 に上げた）。26 に替える |
| E2E が始まる前に止まる | Mailpit か見本の PG（上乗せ `docker/e2e/compose.targetdb-port.yaml` 付き）が動いていない。`docs/verification.md` の E2E の節の手順で起動する |
| 画面のテストが長く止まって時間切れになる | PC の眠り。`caffeinate -dims` と `pmset -g log` で確かめ、結果を使わずに流し直す |

## Sources

- `README.md`・`docs/verification.md`・`perf/README.md`（今の手順）
- `aidlc/spaces/default/intents/261101-maintenance-update/construction/code-generation/code-summary.md`（版の前後）
- `aidlc/spaces/default/memory/project.md`（colima の環境変数・clean 付きの verify・眠りの確かめ）

## Assumptions & Open Questions

None.
