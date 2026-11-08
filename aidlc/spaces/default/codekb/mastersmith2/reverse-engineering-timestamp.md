# Reverse Engineering Timestamp

## Run Record

- Date: 2026-11-09
- Commit: 4fe8e79065d8aa840ad25882509b5f061da0a663

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-11-09 |
| Intent | `261108-ci-checks-release`（scope bugfix・深さ Minimal。6 つの Bolt: ルートの `scripts/` を検査の対象に足すことと R-07 のコマンドの直し、`ubuntu-latest` の Ubuntu 26 への移りを受けた CI の道具の版の確かめ、CI の時間の内訳と分け方、`packagesJudgedByTotal` の 7 パッケージにテストを足して一覧から外す範囲、自動化 A1〜A3 の台本、`develop` から `main` への fast-forward のリリース） |
| 対象 | mastersmith2（プロジェクトルート、単一のリポジトリ） |
| 記録するコミット | `4fe8e79065d8aa840ad25882509b5f061da0a663`（`git rev-parse HEAD` と一致。依頼で渡されたスナップショットの値） |
| 走査の広さ・深さ | 範囲を絞ったスキャン（Focused scan）・Minimal。既存の知識ベースの判定は STALE（前の Intent `261108-ci-size-coverage` の記録のコミット `9fd4f7f` の後に、`.github/workflows/ci.yml` と `scripts/` が変わった）。前の散文を残して今回の範囲の節を足した |
| 手順 | 開発担当が1人でコードスキャン（`aidlc/spaces/default/intents/261108-ci-checks-release/inception/reverse-engineering/developer-scan.md`）と9文書の更新を行った（協力者は無効で、設計担当は呼ばれていない） |

## 確かめ方

- ファイルの読み取り（`cat`・`sed`・`grep`・`ls`・`wc`・`find`）と、`git rev-parse`・`git log`・`git merge-base`・`git status` だけ。テスト・ビルド・`npm`・`./gradlew`・`docker`・cloc・`gh` は流していない。CI の時間とカバレッジの値は測っていない（要件の段で測る）。
- 事実と見立てを分け、見立てには「見立て（未検証）」と書いた（`ubuntu-latest` の移りの影響、CI の時間の内訳と分け方、`scripts/` の検査の足し方、`audit.repository` が JaCoCo に数えられないこと、7 パッケージの薄い所の効き方、一覧を空にしたときの書き方）。
- 行の数は `wc -l` で確かめた（`.github/workflows/ci.yml` 143・`scripts/ci-summary.mjs` 445・`scripts/program-size.mjs` 533・`scripts/verify-graph-check.mjs` 238・`scripts/verify-graph-baseline.txt` 63・`frontend/scripts/check-license-header.mjs` 127・`build.gradle.kts` 913・`backend/build.gradle.kts` 546・`docs/verification.md` 398・`README.md` 165）。
- 深く読んだファイルのうち、`build.gradle.kts` は 1〜566 行を全文で読み、567〜913 行（E2E）は前の記録の範囲を `grep` で確かめただけ。`backend/build.gradle.kts` は 100〜357 行（テスト・JaCoCo・`packagesJudgedByTotal`）を全文で読み、ほかは `grep`。`docs/verification.md` は 1〜200 行を全文で読み、200〜398 行（E2E の節）は頭と見出しだけ。ファイルを単位にした `analyzed.paths` には入れたが、読みの範囲はこのとおり。
- ステージングの9文書は、前の知識ベースの8文書を写して今回の節を足した形（範囲の外の節は前の文のまま）。この文書は新しく書いた。

## 前の記録との関係

- 前のコード知識ベースは Intent `261108-ci-size-coverage` のもの（記録のコミット `9fd4f7f`）。判定が STALE のため、今回の `analyzed.paths` と `analyzed.components` は今回の走査の分だけにした。
- 前の `analyzed.paths`（11）のうち、今回も深く読んだ `.github/workflows/`・`scripts/`・`frontend/scripts/check-license-header.mjs`・`build.gradle.kts`・`backend/build.gradle.kts`・`docs/verification.md` は `analyzed.paths` に入れた。今回深く読まなかった `frontend/scripts/check-bundle-size.mjs`・`frontend/scripts/check-dist-assets.mjs`・`frontend/scripts/make-ico.mjs`・`frontend/vitest.config.ts`・`frontend/package.json` は `shallow.paths` に下げた。前の `shallow.paths` はすべて残し、今回流し読みした `frontend/tsconfig.perf-tool.json` と `common/error/web/`（本体とテスト）を足した。
- 部品 ID は 49 個のまま（増減なし）。今回の `analyzed.components` は 7 つ（`build-and-verify`・`access`・`audit`・`common-error`・`common-health`・`common-i18n`・`common-web`）。`common-health` 以外は部品の一部（7 パッケージに当たるパッケージと、`build-and-verify` は上のファイル）だけを深く読んだ（`component-inventory.md` の各部品の行）。
- 所見は前回までの K-62〜K-126 に、今回 K-127〜K-137 を足した（一覧は `business-overview.md`）。K-127 は前の記録の K-119（CI は 7 手順で要約を書かない）の更新、K-130 は K-121（`scripts/` は2ファイル）の更新。

## 範囲の要約

- 深く読んだ: 20 の道（ファイル 4 とディレクトリ 16）、7 部品。依頼のスナップショットの範囲（`.github/workflows/`・`scripts/`・`frontend/scripts/`・`build.gradle.kts`・`backend/build.gradle.kts`・`docs/verification.md`・`README.md`・7 パッケージの本体とテストの 14 ディレクトリ）のうち、`frontend/scripts/` は全文を読んだ `check-license-header.mjs` だけを `analyzed.paths` に入れ、`README.md` は流し読みのため `shallow.paths` に残した。
- スナップショットの範囲の外で深く読んだものは無い（`frontend/eslint.config.js`・`frontend/tsconfig.perf-tool.json`・`.pre-commit-config.yaml`・`.github/dependabot.yml`・`frontend/package.json`・`frontend/vitest.config.ts` は `grep` だけ）。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` で、`./` は `analyzed.paths` に入れていない。`intent` は `intents.json` の `slug`（`ci-checks-release`）。
- `fingerprint` は、最終の `analyzed.paths`（20）をカンマ区切りで `aidlc engine workspace codekb-scope-diff --repo mastersmith2 --mint --paths` に渡した出力をそのまま貼った。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: ci-checks-release
fingerprint: 60443eb819adfa32fb3f2663ace2beefea5b368c
analyzed:
  paths:
    - .github/workflows/
    - scripts/
    - frontend/scripts/check-license-header.mjs
    - build.gradle.kts
    - backend/build.gradle.kts
    - docs/verification.md
    - backend/src/main/java/cherry/mastersmith/access/service/
    - backend/src/test/java/cherry/mastersmith/access/service/
    - backend/src/main/java/cherry/mastersmith/audit/repository/
    - backend/src/test/java/cherry/mastersmith/audit/repository/
    - backend/src/main/java/cherry/mastersmith/common/error/domain/
    - backend/src/test/java/cherry/mastersmith/common/error/domain/
    - backend/src/main/java/cherry/mastersmith/common/error/service/
    - backend/src/test/java/cherry/mastersmith/common/error/service/
    - backend/src/main/java/cherry/mastersmith/common/health/
    - backend/src/test/java/cherry/mastersmith/common/health/
    - backend/src/main/java/cherry/mastersmith/common/i18n/domain/
    - backend/src/test/java/cherry/mastersmith/common/i18n/domain/
    - backend/src/main/java/cherry/mastersmith/common/web/
    - backend/src/test/java/cherry/mastersmith/common/web/
  components:
    - build-and-verify
    - access
    - audit
    - common-error
    - common-health
    - common-i18n
    - common-web
shallow:
  paths:
    - frontend/scripts/check-bundle-size.mjs
    - frontend/scripts/check-dist-assets.mjs
    - frontend/scripts/make-ico.mjs
    - frontend/vitest.config.ts
    - frontend/package.json
    - frontend/tsconfig.perf-tool.json
    - backend/src/main/java/cherry/mastersmith/common/error/web/
    - backend/src/test/java/cherry/mastersmith/common/error/web/
    - settings.gradle.kts
    - config/
    - README.md
    - frontend/scripts/check-svg-safety.mjs
    - frontend/scripts/icon-binary.mjs
    - backend/src/test/java/cherry/mastersmith/targetdb/testsupport/ContainerRuntimeCheck.java
    - backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbTestDatabase.java
    - backend/src/test/java/cherry/mastersmith/common/testsupport/TestDatabase.java
    - backend/src/test/resources/junit-platform.properties
    - frontend/src/features/preferences/buildTimeZoneOptions.ts
    - frontend/src/features/preferences/buildTimeZoneOptions.test.ts
    - frontend/src/features/preferences/TimeZoneOptionsPerformance.test.ts
    - frontend/src/features/preferences/TimeZoneSelect.tsx
    - frontend/src/features/preferences/registration.ts
    - frontend/src/features/preferences/PreferencesPage.tsx
    - frontend/src/features/preferences/preferencesApi.ts
    - frontend/src/features/preferences/failure.ts
    - frontend/src/features/preferences/errorMessages.ts
    - frontend/src/features/preferences/formChecks.ts
    - frontend/src/shared/format/timeZone/index.ts
    - frontend/src/shared/format/timeZone/timeZoneGate.ts
    - frontend/src/shared/format/timeZone/formatInstant.ts
    - frontend/src/shared/format/timeZone/wallClock.ts
    - frontend/src/shared/format/timeZone/instantText.ts
    - frontend/src/shared/format/timeZone/parseLocalDateTime.ts
    - frontend/src/shared/format/timeZone/types.ts
    - frontend/src/shared/format/timeZone/timeZoneGate.test.ts
    - frontend/src/shared/format/timeZone/conversionPerformance.test.ts
    - frontend/src/shared/format/formatInstantToMinutes.ts
    - frontend/src/shared/format/dateTimeInput.ts
    - frontend/src/features/preferences/
    - frontend/src/shared/format/
    - frontend/src/app/display-settings/
    - frontend/src/features/useradmin/
    - frontend/eslint-rules/timeZoneSelectors.js
    - backend/src/main/java/cherry/mastersmith/businessdata/repository/ColumnValueCodec.java
    - backend/src/main/java/cherry/mastersmith/businessdata/repository/RowDialect.java
    - backend/src/main/java/cherry/mastersmith/businessdata/repository/MysqlFamilyRowDialect.java
    - backend/src/main/java/cherry/mastersmith/businessdata/repository/PostgresRowDialect.java
    - backend/src/main/java/cherry/mastersmith/businessdata/domain/ValueType.java
    - backend/src/main/java/cherry/mastersmith/businessdata/domain/ColumnKind.java
    - backend/src/main/java/cherry/mastersmith/businessdata/web/ColumnDefinitionResponse.java
    - backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DbType.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/FormPart.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/ListFormat.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslColumn.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslTable.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslMenuItem.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/TableRef.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslModel.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/ListSetting.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DetailSetting.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/generate/TypeCategoryMapping.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/generate/TypeCategory.java
    - backend/src/main/java/cherry/mastersmith/navigation/domain/NavNode.java
    - backend/src/main/java/cherry/mastersmith/navigation/domain/NavTableRef.java
    - backend/src/main/java/cherry/mastersmith/navigation/domain/NavIconPolicy.java
    - backend/src/main/java/cherry/mastersmith/navigation/domain/MenuFilter.java
    - backend/src/main/java/cherry/mastersmith/navigation/domain/NavigationMenu.java
    - backend/src/main/java/cherry/mastersmith/navigation/service/NavigationService.java
    - backend/src/main/java/cherry/mastersmith/navigation/web/NavNodeResponse.java
    - backend/src/main/java/cherry/mastersmith/navigation/web/NavTableRefResponse.java
    - backend/src/main/java/cherry/mastersmith/navigation/web/NavigationController.java
    - backend/src/main/resources/navigation/allowed-icons.txt
    - frontend/index.html
    - frontend/src/app/registry/allowedIcons.ts
    - frontend/src/app/registry/types.ts
    - frontend/src/app/navigation/buildNavSections.ts
    - frontend/src/app/navigation/navigationItems.ts
    - frontend/src/app/pages/HomePage.tsx
    - frontend/src/app/i18n/messages/ja.ts
    - frontend/src/app/layout/ShellLayout.tsx
    - frontend/src/features/admin/registration.ts
    - frontend/src/features/admin/AdminAreaPage.tsx
    - frontend/src/features/admin/adminApi.ts
    - frontend/src/features/admin/adminAreaStatus.ts
    - frontend/src/features/admin/AdminPlaceholder.tsx
    - frontend/src/features/tables/valueFormat.ts
    - frontend/src/features/tables/formDiff.ts
    - frontend/src/features/tables/searchConditions.ts
    - frontend/src/features/tables/SearchPanel.tsx
    - frontend/src/features/tables/BusinessDataFormPage.tsx
    - frontend/src/shared/format/formatDateTime.ts
    - .gitmodules
    - gradle/
    - backend/gradle.lockfile
    - backend/config/spotbugs-exclude.xml
    - frontend/package-lock.json
    - frontend/.npmrc
    - frontend/vitest.setup.ts
    - frontend/vite.config.ts
    - Dockerfile
    - compose.yaml
    - docker/perf/compose.yaml
    - docker/e2e/compose.targetdb-port.yaml
    - docker/hikari-pool.sh
    - .github/
    - perf/README.md
    - perf/versions.env
    - perf/redact-k6-evidence.mjs
    - backend/src/main/java/cherry/mastersmith/businessdata/service/BusinessDataWriteService.java
    - backend/src/main/java/cherry/mastersmith/businessdata/repository/RowSqlBuilder.java
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
    - backend/src/main/java/cherry/mastersmith/dslmanage/
    - backend/src/main/java/cherry/mastersmith/navigation/
    - backend/src/main/java/cherry/mastersmith/access/web/AdminCheckController.java
    - backend/src/main/java/cherry/mastersmith/config/WebConfig.java
    - backend/src/main/resources/application.yaml
    - backend/src/main/resources/dsl/
    - docker/targetdb/
    - frontend/src/features/admin/
    - frontend/src/features/dsl/
    - backend/src/test/java/cherry/mastersmith/businessdata/
```
