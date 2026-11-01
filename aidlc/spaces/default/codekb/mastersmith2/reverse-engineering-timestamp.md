# Reverse Engineering Timestamp

## Run Record

- Date: 2026-11-01
- Commit: 6079f13826853c7564ac284cad43f6d63311c09b

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-11-01 |
| Intent | `261101-maintenance-update`（scope bugfix・深さ Minimal。依存の最新化（Java 25 系・Node 24 → 26・サブモジュール2つ・GitHub Actions・コンテナのイメージ・検査の道具・Gradle の Wrapper・Spring Boot と連動する固定・`dependabot.yml` の `ignore`）、古いコンテナのイメージの片付け、前の Intent の小さな直し（P1・T1〜T3・A2・A3）、README.md と perf/README.md の最新化） |
| 対象 | mastersmith2（プロジェクトルート、単一のリポジトリ） |
| 記録するコミット | `6079f13826853c7564ac284cad43f6d63311c09b`（`develop` の HEAD。スナップショットの head_commit と同じ。`git rev-parse HEAD` でも確かめた） |
| 走査の広さ・深さ | 範囲を絞ったスキャン（Focused scan）・Minimal。既存の知識ベースは STALE で、前の散文を残して今回の範囲の節を更新・追加した |
| 手順 | 開発担当が1人でコードスキャン（`aidlc/spaces/default/intents/261101-maintenance-update/inception/reverse-engineering/developer-scan.md`）と9文書の更新を行った（アーキテクトは入っていない） |
| サブモジュール | 固定先のハッシュだけ（`git submodule status`）。`vendor/make-you-chic-ui` は `cab4207`、`vendor/java-mustache-processor` は `8d44c36`（0.1.0）。中身は読んでいない |

## 確かめ方

- ファイルの読み取り（cat・sed・grep・ls・wc）と、読み取りの git（`rev-parse`・`submodule status`）だけ。ビルド・テスト・`gradlew`・docker の操作はしていない。`frontend/package-lock.json` の解決済みの版は、lockfile を読むだけの短い node の式で取り出した。`.env`・`.env.targetdb`・`reference/`・`vendor/` の中は開いていない。
- 事実と見立てを分け、見立てには「未検証」と書いた（K-81 の Node 26 での旗、K-85 の固定を外せる時期、K-90 の直し方、K-91 の null の経路、K-92 の 1038、K-93 の手元のイメージ）。

## 前の記録との関係

- 前のコード知識ベースは Intent `261029-reference-lookup-cleanup` のもの（記録のコミット `15a25aa`）。その後に同 Intent の後の段の変更が入り（`develop` が `6079f13` に進んだ）、判定が STALE のため、今回の範囲の節を更新した。
- 部品 ID は 48 個のまま（増減なし）。今回の `analyzed.components` は `build-and-verify`・`container-runtime`・`perf-and-monitoring`・`businessdata` の4つ。範囲の外の部品の記述は前回以前のまま残した。
- 所見は前回までの K-62〜K-80 に、今回 K-81〜K-93 を足した（一覧は `business-overview.md`）。K-78（コンテナの版の固定先）は K-83 に、K-66・K-72（固定と Dependabot）は K-85・K-87 に引き継いだ。K-79 の i18next 系2件は取り込み済みと確かめた。
- 前回の記録との食い違い: make-you-chic-ui の固定先（前回 `ecb07e9` → 今回 `cab4207`）を各文書で直した。README.md 37 行は今も `ecb07e9` と書く（K-86・K-88）。
- 依頼の「Jackson の BOM の上書き 3.1.7 の見直し」は、上書きが既に無いと確かめた（K-85）。

## 範囲の要約

- 深く読んだ: 26 のパス（ディレクトリ 3・ファイル 23）、4 部品。`perf/`・`docker/`・`frontend/e2e/`・`frontend/scripts/`・`backend/config/`・`businessdata/` は、中のファイルを選んで読んだため、ディレクトリではなくファイルで `analyzed.paths` に入れた（残りは `shallow.paths`）。
- 前回の `analyzed.paths`（14）は、今回の `analyzed.paths` に含まれるもの（`.github/`・`compose.yaml`・`frontend/package.json`・`backend/build.gradle.kts`・`README.md`）を除き、STALE の規則で `shallow.paths` に下げた。
- スナップショットの範囲の外で今回の Intent に関わる固定先（`TargetDbImages.java`・`frontend/playwright.config.ts`・`frontend/vitest.config.ts`・`.pre-commit-config.yaml`・`frontend/eslint.config.js`・`frontend/tsconfig.json`）は grep の流し読みだけで、`shallow.paths` に記録した。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` で、`./` は `analyzed.paths` に入れていない。`intent` は `intents.json` の `slug`（`maintenance-update`）。
- `fingerprint` は、最終の `analyzed.paths`（26）をカンマ区切りで `aidlc engine workspace codekb-scope-diff --repo mastersmith2 --mint --paths` に渡した出力をそのまま貼った。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: maintenance-update
fingerprint: 6c5391e69e23795ceb65a86734e25b7a6a5cb96b
analyzed:
  paths:
    - gradle/
    - build.gradle.kts
    - settings.gradle.kts
    - backend/build.gradle.kts
    - backend/gradle.lockfile
    - backend/config/spotbugs-exclude.xml
    - frontend/package.json
    - frontend/package-lock.json
    - frontend/.npmrc
    - frontend/vitest.setup.ts
    - frontend/vite.config.ts
    - Dockerfile
    - compose.yaml
    - .gitmodules
    - docker/perf/compose.yaml
    - docker/e2e/compose.targetdb-port.yaml
    - docker/hikari-pool.sh
    - .github/
    - config/
    - perf/README.md
    - perf/versions.env
    - perf/redact-k6-evidence.mjs
    - README.md
    - backend/src/main/java/cherry/mastersmith/businessdata/service/BusinessDataWriteService.java
    - backend/src/main/java/cherry/mastersmith/businessdata/repository/RowSqlBuilder.java
    - backend/src/main/java/cherry/mastersmith/businessdata/repository/RowDialect.java
  components:
    - build-and-verify
    - container-runtime
    - perf-and-monitoring
    - businessdata
shallow:
  paths:
    - backend/src/main/java/cherry/mastersmith/businessdata/
    - backend/src/main/java/cherry/mastersmith/dsl/
    - backend/src/main/java/cherry/mastersmith/targetdb/
    - backend/src/main/java/cherry/mastersmith/role/
    - backend/src/main/resources/
    - frontend/src/features/tables/
    - frontend/src/shared/
    - perf/
    - docker/
    - docker/perf/
    - frontend/e2e/
    - frontend/scripts/
    - frontend/vitest.config.ts
    - frontend/playwright.config.ts
    - frontend/eslint.config.js
    - frontend/tsconfig.json
    - frontend/src/
    - frontend/src/app/
    - frontend/src/features/
    - backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java
    - backend/src/main/java/cherry/mastersmith/
    - backend/src/test/java/cherry/mastersmith/
    - backend/config/
    - .pre-commit-config.yaml
    - vendor/make-you-chic-ui/
    - vendor/java-mustache-processor/
    - .env.example
    - settings-gradle.lockfile
```
