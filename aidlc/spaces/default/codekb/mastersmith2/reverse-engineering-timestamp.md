# Reverse Engineering Timestamp

## Run Record

- Date: 2026-11-08
- Commit: 9fd4f7f14618f27da3ac87a59ef89e8b062684b4

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-11-08（この日の2回目の Reverse Engineering） |
| Intent | `261108-ci-size-coverage`（scope express・深さ Minimal。GitHub Actions の CI の実行の要約（`$GITHUB_STEP_SUMMARY`）に、cloc で数えるプログラムの規模（前の回のプッシュのコミットとの差を含む。予定の台本は `scripts/program-size.mjs`）と、バックエンドの JaCoCo の XML・画面の Vitest の `coverage-summary.json` から読むカバレッジを載せる） |
| 対象 | mastersmith2（プロジェクトルート、単一のリポジトリ） |
| 記録するコミット | `9fd4f7f14618f27da3ac87a59ef89e8b062684b4`（`git rev-parse HEAD` と一致。依頼で渡されたスナップショットの値） |
| 走査の広さ・深さ | 範囲を絞ったスキャン（Focused scan）・Minimal。既存の知識ベースの判定は STALE（前の Intent `261107-verify-quick-local` の記録のコミット `16f46f1` の後に、`build.gradle.kts`・`backend/build.gradle.kts`・`docs/verification.md` が変わり、`scripts/` が足された）。前の散文を残して今回の範囲の節を足した |
| 手順 | 開発担当が1人でコードスキャン（`aidlc/spaces/default/intents/261108-ci-size-coverage/inception/reverse-engineering/developer-scan.md`）と9文書の更新を行った（協力者は無効で、設計担当は呼ばれていない） |

## 確かめ方

- ファイルの読み取り（`cat`・`sed`・`grep`・`ls`・`wc`）と、`git rev-parse`・`git log`・`git ls-files` だけ。テスト・ビルド・`npm`・`./gradlew`・`docker`・cloc は流していない。報告の置き場（`backend/build/`・`frontend/coverage/`）は git の対象外のため開いていない（ファイルの名前は道具の既定の見込みとして書いた）。
- 事実と見立てを分け、見立てには「未検証」「見立て」と書いた（前の回のプッシュのコミットの取り方、cloc の入れ方とライセンスと数える範囲、要約の手順の失敗のときの扱い、報告のファイル名）。
- 行の数は `wc -l` で確かめた（`build.gradle.kts` 913・`backend/build.gradle.kts` 546・`.github/workflows/ci.yml` 115・`scripts/verify-graph-check.mjs` 238・`scripts/verify-graph-baseline.txt` 63・`frontend/vitest.config.ts` 80・`frontend/package.json` 62・`docs/verification.md` 353）。
- ステージングの9文書は、前の知識ベースの8文書を写して今回の節を足した形（範囲の外の節は前の文のまま）。この文書は新しく書いた。

## 前の記録との関係

- 前のコード知識ベースは Intent `261107-verify-quick-local` のもの（記録のコミット `16f46f1`）。判定が STALE のため、今回の `analyzed.paths` と `analyzed.components` は今回の走査の分だけにし、前の `analyzed.paths`（12）のうち今回読み直さなかったもの（`settings.gradle.kts`・`config/`・`README.md`）を `shallow.paths` に下げた。前の `analyzed.paths` のほかの9つ（`build.gradle.kts`・`backend/build.gradle.kts`・`frontend/package.json`・`frontend/vitest.config.ts`・`frontend/scripts/` の4ファイル・`.github/workflows/`）は今回も深く読んだため、`analyzed.paths` に入れた。前の `shallow.paths` は残し、今回深く読んだ `docs/verification.md` だけを外した。
- 部品 ID は 49 個のまま（増減なし。ルートの `scripts/` は `build-and-verify` に含めた）。今回の `analyzed.components` は1つ（`build-and-verify`）。
- 所見は前回までの K-62〜K-118 に、今回 K-119〜K-126 を足した（一覧は `business-overview.md`）。K-122 は前の記録の訂正（`verifyQuick`・`-PitTests`・`-PitForks`・`jacocoQuickReport` は実装済み）。K-126 は行の数と版の確かめ直し。

## 範囲の要約

- 深く読んだ: 11 の道（ファイル 9 とディレクトリ 2）、1 部品。依頼のスナップショットの範囲（`.github/workflows/`・`scripts/`・`frontend/scripts/`・`frontend/vitest.config.ts`・`frontend/package.json`・`backend/build.gradle.kts`・`build.gradle.kts`・`docs/verification.md`）のうち、`frontend/scripts/` は全文を読んだ4ファイルだけを `analyzed.paths` に入れ、頭だけ読んだ `check-svg-safety.mjs`・`icon-binary.mjs` は `shallow.paths` に入れた。`.github/workflows/`（`ci.yml` の1つ）と `scripts/`（2ファイル）は中をすべて読んだためディレクトリで入れた。
- スナップショットの範囲の外で深く読んだものは無い。`frontend/src/*.test.ts` はファイル名の一覧だけで、前の `shallow.paths` の `frontend/src/` に含まれる。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` で、`./` は `analyzed.paths` に入れていない。`intent` は `intents.json` の `slug`（`ci-size-coverage`）。
- `fingerprint` は、最終の `analyzed.paths`（11）をカンマ区切りで `aidlc engine workspace codekb-scope-diff --repo mastersmith2 --mint --paths` に渡した出力をそのまま貼った。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: ci-size-coverage
fingerprint: 4cd80dfaf042a6decba8f88c408f0c46c726578e
analyzed:
  paths:
    - .github/workflows/
    - scripts/
    - frontend/scripts/check-license-header.mjs
    - frontend/scripts/check-bundle-size.mjs
    - frontend/scripts/check-dist-assets.mjs
    - frontend/scripts/make-ico.mjs
    - frontend/vitest.config.ts
    - frontend/package.json
    - backend/build.gradle.kts
    - build.gradle.kts
    - docs/verification.md
  components:
    - build-and-verify
shallow:
  paths:
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
