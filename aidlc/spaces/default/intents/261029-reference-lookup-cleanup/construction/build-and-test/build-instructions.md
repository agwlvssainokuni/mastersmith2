# ビルドの手順 — Build and Test（Intent 261029-reference-lookup-cleanup）

この Intent（他テーブル参照の REFERENCE・LOOKUP、k6 の結果の JWT の伏せ字の道具、手元のコンテナの版の整理と perf の文書の整理、Dependabot #81・#82 の取り込み）を組んで確かめる手順。手順の正は `README.md` の「前提」「1コマンドの検査」「画面からの一連の操作のテスト（E2E）」で、ここでは今回の確かめに使った形だけを書く。パスはプロジェクトのルートからの相対で書く。

## 1. 前提（道具と依存の入れ方）

| 道具 | 版 | 入れ方・確かめ方 |
|---|---|---|
| JDK | 25（Temurin） | `README.md` の前提の表のとおり |
| Node.js・npm | 24 | `./gradlew verify` の `checkToolchain` が版を確かめ、違えば止まる |
| Gitleaks・OSV-Scanner | `README.md` の前提の表 | 見つからなければ `verify` が入れ方を示して止まる（黙って飛ばさない） |
| コンテナの実行環境 | colima（この PC は CPU 4・メモリ 6GiB） | 対象DB（MySQL・MariaDB・PostgreSQL）の結合テストと E2E・負荷の試験の前提 |
| Playwright の Chromium | `@playwright/test` と同じ版 | `cd frontend && npx playwright install chromium`（E2E だけ） |
| サブモジュール | 固定先のコミット | `git submodule update --init`（`vendor/make-you-chic-ui` は 4c63d11、`vendor/java-mustache-processor`） |

依存は lockfile どおりに入れる（`verify` の `verifyPrepare` が `vendor/make-you-chic-ui` と `frontend/` で `npm ci` を流す。`frontend/.npmrc` の `ignore-scripts=true`）。Gradle の依存は `backend/gradle.lockfile` と Maven Central だけ。この Intent で増えた依存は無い（Dependabot #81・#82 の版の更新と lockfile の入れ直しは `41b8bf5`、`hasInstallScript` の付いた項目は fsevents の1つのまま）。

## 2. 環境の設定

colima の PC では、次の2つをシェルに渡す（渡さないと対象DB のテストが SKIPPED になり、パッケージごとのカバレッジの下限で失敗する。`project.md`）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

長い流し（verify・E2E・k6）は `caffeinate -dims` で包み、流す前に `pmset -g assertions`、流した後に `pmset -g log` で `Sleep`・`DarkWake` が無いことを確かめる。入っていたら結果を使わずに流し直す（`project.md`）。

`.env`・`.env.*` は開かない・表示しない。E2E と負荷の試験の資格情報はプロセスの環境変数で渡す（`README.md`）。

## 3. ビルドと1コマンドの検査

件数とカバレッジを報告するときは、テストのタスクが UP-TO-DATE で飛ばされないよう、必ず次の形で流す（`project.md`）。

```bash
caffeinate -dims ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

`verify` の中身（`README.md` の表のとおり）: 準備（道具の確認・`npm ci`・サブモジュールが変わっていないこと）→ 書式（Spotless・Prettier、perf の道具は `perfToolFormatCheck`）→ リンタ（oxlint・ESLint・Stylelint、perf の道具は `perfToolLint`）→ ライセンスヘッダー → ビルド（型の検査を含む）→ 単体テスト → 結合テスト（Testcontainers の対象DB 3種類）→ カバレッジの下限（JaCoCo の全体とパッケージごと、Vitest の `thresholds`）→ 安全の検査（`spotbugsGate`・`gitleaksScan`・`perfEvidenceScan`・`osvScan`）。

画面を同梱した WAR だけを作るときは `./gradlew :backend:bootWar`（成果物は `backend/build/libs/mastersmith.war`）。

## 4. ビルドの確かめ

- `verify` が `BUILD SUCCESSFUL` で終わり、単体・結合・画面のテストの失敗が 0、SKIPPED は DB の種類ごとの前からの `assumeTrue` の 10 件だけ（コンテナが無いための飛ばしは 0）。
- カバレッジ: `backend/build/reports/jacoco/test/jacocoTestReport.xml` の全体 行 80%・分岐 70% 以上と、`packagesJudgedByTotal` の外のパッケージごとに同じ下限。`frontend/coverage/coverage-summary.json` の全体が同じ下限以上。
- WAR の中に、使っていない文言の鍵（今回消した `tables.reference.loading`）が残っていない（`unzip -p` と検索。今回は 0 件）。
- この段の実測は `test-results.md` に書いた。

## 5. よくあるつまずき

| 症状 | 原因と手当て |
|---|---|
| 対象DB の結合テストが SKIPPED になり、パッケージごとのカバレッジで落ちる | 2節の2つの環境変数が無い。付けて流し直す |
| verify の件数がいつもより少ない | テストのタスクが UP-TO-DATE。`:backend:cleanTest :backend:cleanIntegrationTest` を付ける |
| E2E が始まる前に止まる | Mailpit か見本の PG（E2E の口の上乗せ `docker/e2e/compose.targetdb-port.yaml` 付き）が動いていない。`README.md` の E2E の節の手順で起動し直す（今回、見本の PG が上乗せ無しで作り直されていたため1回止まった） |
| E2E の途中で多くの件が落ちる・未実行になる | Docker のデーモンが止まった・起動し直した（今回、colima の VM の中の apt upgrade でデーモンが起動し直し、配備したアプリ・Mailpit・見本の PG が止まった）。`docker ps` で状態を確かめ、コンテナを起動し直してから全件を流し直す |
| 画面のテストが長く止まって時間切れになる | PC の眠り（Thermal の DarkWake を含む）。`caffeinate -dims` と `pmset -g log` で確かめ、結果を使わずに流し直す |
| `gitleaksScan` が履歴で落ちる | 記録に認証の値（JWT）を置いた。`perf/README.md` の「記録（evidence/）に置く前の伏せ字」の手順で置き直す |
