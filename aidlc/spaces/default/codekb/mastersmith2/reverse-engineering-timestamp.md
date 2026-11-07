# Reverse Engineering Timestamp

## Run Record

- Date: 2026-11-08
- Commit: 16f46f1268c07d141ade76d620d1858819384967

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-11-08 |
| Intent | `261107-verify-quick-local`（scope bugfix・深さ Minimal・Test Strategy Minimal・brownfield。手元の `./gradlew verify` の時間を縮める案 3: 統合の前は `verifyQuick`（結合テストとサーバーのカバレッジの判定を除き、`-PitTests` で絞った IT を流せ、JaCoCo は参考値）、全件の `verify` は Build and Test・リリースの前と `packagesJudgedByTotal` のパッケージに手を入れる Bolt で流し、`-PitForks` で手元だけ結合テストを並べる。画面のテストを1回にする。CI は `verify` を1本のまま。関わる決まりを書き換える） |
| 対象 | mastersmith2（プロジェクトルート、単一のリポジトリ） |
| 記録するコミット | `16f46f1268c07d141ade76d620d1858819384967`（`git rev-parse HEAD` と同じ。依頼で渡された値） |
| 走査の広さ・深さ | 範囲を絞ったスキャン（Focused scan）・Minimal。既存の知識ベースの判定は STALE（前の Intent `261104-tz-options-perf` で深く読んだ範囲がその後に変わった）で、前の散文を残して今回の範囲の節を更新・追加した |
| 手順 | 開発担当が1人でコードスキャン（`aidlc/spaces/default/intents/261107-verify-quick-local/inception/reverse-engineering/developer-scan.md`）と9文書の更新を行った（設計担当は今回の構成に無い） |

## 確かめ方

- ファイルの読み取り（Read と `ls`・`grep`・`find`・`wc`）だけ。テスト・ビルド・`npm`・`./gradlew`・`docker` は実行していない。git も使っていない（記録のコミットは依頼の値）。時間の実測は無い。
- 事実と見立てを分け、見立てには「未検証」と書いた（`verifyQuick` の段の組み方・IT を除いたカバレッジ・並べたときのコンテナと番号の取り合い・時間の内訳）。
- 行の数は `wc -l` で確かめ直した（`build.gradle.kts` 860・`backend/build.gradle.kts` 446・`settings.gradle.kts` 37・`frontend/vitest.config.ts` 80・`README.md` 164）。
- ステージングの9文書は、前の知識ベースの9文書を読んで今回の節を足した形で書き直した（範囲の外の節は前の文のまま）。この文書は新しく書いた。

## 前の記録との関係

- 前のコード知識ベースは Intent `261104-tz-options-perf` のもの（記録のコミット `a789a6f`）。判定が STALE のため、今回の `analyzed.paths` と `analyzed.components` は今回の走査の分だけにし、前の `analyzed.paths`（22）は `shallow.paths` に下げた（前の `shallow.paths` とあわせて残した）。前の `analyzed.paths` の `frontend/vitest.config.ts` と、前の `shallow.paths` の `build.gradle.kts`・`settings.gradle.kts`・`backend/build.gradle.kts`・`frontend/package.json`・`config/`・`README.md` は今回深く読んだため、`shallow.paths` から外して `analyzed.paths` に入れた。
- 部品 ID は 49 個のまま（増減なし）。今回の `analyzed.components` は1つ（`build-and-verify`）。`backend-test-support` は結合テストの数と前提を流し読みしただけのため入れていない。
- 所見は前回までの K-62〜K-110 に、今回 K-111〜K-118 を足した（一覧は `business-overview.md`）。K-118 は前の記録の訂正（Node 26・OSV-Scanner 2.7.0・`setup-gradle` v6.4.1・README 164 行・`frontend/scripts/` 6 ファイル）。

## 範囲の要約

- 深く読んだ: 12 の道（ファイル 10 とディレクトリ 2）、1 部品。依頼のスナップショットの範囲（`build.gradle.kts`・`settings.gradle.kts`・`backend/build.gradle.kts`・`frontend/package.json`・`frontend/vitest.config.ts`・`frontend/scripts`・`.github/workflows`・`config`・`README.md`）のうち、`frontend/scripts` は全文を読んだ4ファイルだけを `analyzed.paths` に入れ、頭だけ読んだ `check-svg-safety.mjs`・`icon-binary.mjs` は `shallow.paths` に入れた。`.github/workflows/`（`ci.yml` の1つ）と `config/`（2ファイル）は中をすべて読んだためディレクトリで入れた。
- スナップショットの範囲の外は流し読みだけで、`shallow.paths` に記録した（`docs/verification.md`・`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/{ContainerRuntimeCheck,TargetDbTestDatabase}.java`・`backend/src/test/java/cherry/mastersmith/common/testsupport/TestDatabase.java`・`backend/src/test/resources/junit-platform.properties`。`backend/src/test/java/cherry/mastersmith/` は前の記録に既にある）。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` で、`./` は `analyzed.paths` に入れていない。`intent` は `intents.json` の `slug`（`verify-quick-local`）。
- `fingerprint` は、最終の `analyzed.paths`（12）をカンマ区切りで `aidlc engine workspace codekb-scope-diff --mint --paths` に渡した出力をそのまま貼った。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: verify-quick-local
fingerprint: f11d198a15c52de3b0998f469a45b8c76d94e725
analyzed:
  paths:
    - build.gradle.kts
    - settings.gradle.kts
    - backend/build.gradle.kts
    - frontend/package.json
    - frontend/vitest.config.ts
    - frontend/scripts/check-license-header.mjs
    - frontend/scripts/check-bundle-size.mjs
    - frontend/scripts/check-dist-assets.mjs
    - frontend/scripts/make-ico.mjs
    - .github/workflows/
    - config/
    - README.md
  components:
    - build-and-verify
shallow:
  paths:
    - frontend/scripts/check-svg-safety.mjs
    - frontend/scripts/icon-binary.mjs
    - docs/verification.md
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
