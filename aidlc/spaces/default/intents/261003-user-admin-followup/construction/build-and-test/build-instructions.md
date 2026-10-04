# ビルドの手順（261003-user-admin-followup）

- 範囲: bugfix（深さ Minimal、Test Strategy は Minimal）。zero-Unit のため、リポジトリ全体を1つの入口（`./gradlew verify`）でビルドし検査する（`project.md` の Way of Working の学び）。
- この Intent の変更はビルドの仕組み（`build.gradle.kts`・`backend/build.gradle.kts`・`frontend/package.json`・lockfile）に触れていない。手順は既存の `README.md` の「前提の道具」「取得と準備」「1コマンドの検査」を正とし、この文書は今回のビルドで使った形を記録する。
- 対象の版: `develop` の `a1b41e7`（コード生成の統合の版）。その後の `c50e64c` は `perf/README.md` だけの変更。

## 1. 前提の道具と環境

| 道具 | 版 | 使う場面 |
|---|---|---|
| JDK | 25（Temurin） | バックエンドのビルド・テスト |
| Node.js・npm | 24 | 画面のビルド・テスト（`verify` は 24 でなければ失敗する） |
| Gitleaks | 8.30.1 | 秘密情報の検出（`verify` の 8 の段） |
| OSV-Scanner | 2.6.0 | 依存関係の脆弱性の検査（`verify` の 8 の段） |
| colima（Docker・Compose v2） | VM は CPU 4・メモリ 6GiB | 対象DB（MySQL・MariaDB・PostgreSQL）の結合テスト、E2E の Mailpit、負荷の試験 |
| Playwright の Chromium | `@playwright/test` と同じ版 | E2E（`./gradlew e2eTest`） |

- サブモジュールを取得しておく（`git submodule update --init`）。make-you-chic-ui の固定先は、この Intent で `e82b651c53ac6e048066fcc049e49386c0eb282e` に上げた（`git ls-files -s vendor/make-you-chic-ui` で確かめた）。
- colima の PC では、Testcontainers に Docker の接続先を渡す（渡さないと対象DB のテストが `SKIPPED` になり、パッケージごとのカバレッジの下限で失敗する。`project.md` の学び）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- `.env`・鍵ファイルは、ビルドとテストには使わない。テストは一時の値を実行ごとに作る（`TestDatabase.randomSecret()` など）。

## 2. 依存の取得

- Gradle は Wrapper（`./gradlew`）を使う。依存の取得元は Maven Central だけ（`settings.gradle.kts` の `RepositoriesMode.FAIL_ON_PROJECT_REPOS`）。版は `backend/gradle.lockfile` で固定する。
- npm は `verify` の 0 の段（`verifyPrepare`）が `npm ci` で lockfile どおりに入れる（make-you-chic-ui と `frontend/` の両方）。パッケージのスクリプトは動かさない（`frontend/.npmrc` の `ignore-scripts=true`）。
- 固定先を上げた後に画面のテストだけを流すときは、先に make-you-chic-ui の成果物を作り直す。

```bash
./gradlew vendorBuild frontendInstall
```

## 3. ビルドと検査のコマンド

統合の前の関門と、テストの件数・カバレッジの実測は、clean 付きの `verify` で行う（`verify` はテストのタスクを UP-TO-DATE で飛ばすことがあるため。`project.md` の学び）。PC のスリープで結果が崩れないよう、全体を `caffeinate -i` で包む。

```bash
DOCKER_HOST=unix://$HOME/.colima/default/docker.sock \
TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock \
caffeinate -i ./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify
```

`verify` の段の順（1つでも失敗したら後ろは流れない）:

| 順 | 段 | 中身 |
|---|---|---|
| 0 | `verifyPrepare` | 道具の確認、make-you-chic-ui の `npm ci`・ビルド、2つのサブモジュールが変わっていないことの確認、画面の `npm ci` |
| 1 | `verifyFormat` | Spotless（palantir-java-format・ライセンスヘッダー）、Prettier |
| 2 | `verifyLint` | oxlint・ESLint・Stylelint |
| 3 | `verifyLicense` | 画面のファイルのライセンスヘッダー |
| 4 | `verifyBuild` | Java のコンパイル、`tsc --noEmit`、Vite のビルド |
| 5 | `verifyUnitTest` | JUnit（`*Test`）、Vitest |
| 6 | `verifyIntegrationTest` | `*IT`（組み込みの H2、対象DB のコンテナ、JVM の中の SMTP の受け手） |
| 7 | `verifyCoverage` | JaCoCo・`@vitest/coverage-v8`（行 80%・分岐 70%。バックエンドはパッケージごとにも当てる） |
| 8 | `verifySecurity` | `spotbugsGate`（SpotBugs＋FindSecBugs）、`osvScan`、`gitleaksScan` |
| 9 | `verifyArtifact` | 画面を同梱した実行可能 WAR（`backend/build/libs/mastersmith.war`） |

- 成果物（WAR）だけを作るとき: `./gradlew :backend:bootWar`。
- 負荷の試験のイメージは、配備したアプリのタグ `local` を上書きしないよう Intent ごとのタグで作る（`docker build -t mastersmith:user-admin-followup .`。`perf/README.md` の手順 0）。

## 4. ビルドの確かめ

- `verify` が `BUILD SUCCESSFUL` で終わること。この段の実測は `test-results.md` の 1節（9 分 6 秒）。
- 報告の置き場: `backend/build/reports/`（テスト・JaCoCo・SpotBugs）、`frontend/coverage/`、`build/reports/osv-scanner/osv.json`。
- バックエンドの結合テストの結果の XML（`backend/build/test-results/integrationTest/`）で、`skipped` が 0 であること（対象DB のテストが飛んでいないこと）。

## 5. よくある失敗と直し方

| 症状 | 原因 | 直し方 |
|---|---|---|
| 対象DB のテストが `SKIPPED`、続いてカバレッジの下限で失敗 | `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` が渡っていない、または colima が止まっている | `colima start` の後、1節の環境変数を渡してやり直す。飛ばした状態では統合しない（team.md） |
| `verify` が非常に長くかかる・時間切れのテストが出る | PC の自動のスリープ（コード生成の段で約 15 分のスリープが3回起きた） | `caffeinate -i` で全体を包む。遅れが出たら `pmset -g log` でスリープを確かめる |
| Gradle の構成の段で `vendor/java-mustache-processor` が見つからない | サブモジュールを取得していない | `git submodule update --init` |
| 画面のテストが make-you-chic-ui の部品で落ちる | 固定先を上げた後に成果物を作り直していない | `./gradlew vendorBuild frontendInstall` |
| `verifyPrepare` が Node の版で失敗 | Node.js が 24 でない | 24 に切り替える（`nvm use 24` など） |

## Sources

- `README.md`（「前提の道具」「取得と準備」「1コマンドの検査（統合の前の関門）」「対象DB の結合テストとコンテナの実行環境」）
- `build.gradle.kts`（`verifyStages`・`verify`・`e2eTest` のタスク）
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/code-summary.md`（1節 変えたファイル、3.4節、4.3節）
- `aidlc/spaces/default/intents/261003-user-admin-followup/construction/code-generation/unit-test-instructions.md`（1節）
- `aidlc/spaces/default/memory/team.md`（Way of Working・Testing Posture）、`aidlc/spaces/default/memory/project.md`（Testing Posture の学び）

## Assumptions & Open Questions

- `README.md` の「取得と準備」の節は、make-you-chic-ui の固定先を `077f5b4` と書いている。実際の固定先は `e82b651`（この Intent の C1 `a272fd3`）で、その前の `3d9521a` も記述に無い。ビルドの手順には影響しないが、記述が古い。直すかは承認の場で依頼者に確かめる（この段では直していない）。
