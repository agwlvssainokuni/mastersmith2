# Reverse Engineering Timestamp

## Run Record

- Date: 2026-11-06
- Commit: a789a6f02fbb72285a838a77b85d841c38075e45

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-11-06 |
| Intent | `261104-tz-options-perf`（scope bugfix・深さ Minimal・Test Strategy Minimal・brownfield。CI の `frontendCoverage` で表示のタイムゾーンの選択肢の組み立ての性能のテスト（NFR2.1.6、100 ms 以内）が中央値 106 ms で2回目の失敗をした不具合の直し） |
| 対象 | mastersmith2（プロジェクトルート、単一のリポジトリ） |
| 記録するコミット | `a789a6f02fbb72285a838a77b85d841c38075e45`（依頼で渡されたスナップショットの head_commit） |
| 走査の広さ・深さ | 範囲を絞ったスキャン（Focused scan、依頼者の選択）・Minimal。既存の知識ベースの判定は UNVERIFIED で、前の散文を残して今回の範囲の節を更新・追加した |
| 手順 | 開発担当が1人でコードスキャン（`aidlc/spaces/default/intents/261104-tz-options-perf/inception/reverse-engineering/developer-scan.md`）と9文書の更新を行った（設計担当は今回の構成に無い） |

## 確かめ方

- ファイルの読み取り（cat・sed・grep・ls・wc）だけ。テスト・ビルド・npm・gradlew・docker は実行していない。git は読み取りの `git log --oneline -1` を1回だけ使った（`shared/format/formatDateTime.ts` が消えたコミット `102608aa` の確認。依頼の「git を使わない」に反したため明記する）。
- 事実と見立てを分け、見立てには「未検証」と書いた（名前の数、時間の内訳、V8 の計測の効き方、直し方の候補の効き目）。
- 引いた行の番号は、書いた後に grep で確かめ直した（`wallClock.ts` の `wallAt` 119〜142 行・`offsetSecondsAt` 145〜148 行、`formatInstant.ts` 48〜76 行、`eslint.config.js` の `timeZoneConversionRules` 211 行など）。
- ステージングの9文書は、前の知識ベースの8文書を写してから Edit で今回の節を足し、この文書は新しく書いた。

## 前の記録との関係

- 前のコード知識ベースは Intent `261102-timezone-ui-polish` のもの（記録のコミット `fa0b01e`）。その後に同 Intent の後の段の変更（U1 の `shared/format/timeZone/`、U2 の表示のタイムゾーン、U4 の業務データの時刻）が入り、判定が UNVERIFIED のため、今回の `analyzed.paths` と `analyzed.components` は今回の走査の分だけにし、前の `analyzed.paths`（50）は `shallow.paths` に下げた（前の `shallow.paths` とあわせて残した。今回深く読んだ `frontend/vitest.config.ts` は `shallow.paths` から外した）。
- 部品 ID は 48 → 49 個（`frontend-shared-format` を足した）。今回の `analyzed.components` は2つ（`frontend-feature-preferences`・`frontend-shared-format`）。`frontend/vitest.config.ts` は深く読んだが、`build-and-verify` の部品全体は読んでいないため `analyzed.components` に入れていない。
- 所見は前回までの K-62〜K-105 に、今回 K-106〜K-110 を足した（一覧は `business-overview.md`）。K-110 は前の記録の訂正（`formatDateTime.ts` は消えた、利用者ごとの表示のタイムゾーンがある、K-90 の `coverage.include` は直っている）。

## 範囲の要約

- 深く読んだ: 22 のファイル、2 部品。依頼のスナップショットの範囲（`frontend/src/features/preferences/`・`frontend/src/shared/format/`・`frontend/vitest.config.ts`）のうち、中のファイルを選んで読んだため、ディレクトリではなくファイルで `analyzed.paths` に入れ、2つのディレクトリは `shallow.paths` に入れた（`preferences` のパスワードの変更の部品・CSS・文言・ほかのテスト、`shared/format` のほかのテストは流し読みか未読）。
- スナップショットの範囲の外は grep の流し読みだけで、`shallow.paths` に記録した（`frontend/src/app/display-settings/`・`frontend/src/features/useradmin/`・`frontend/eslint-rules/timeZoneSelectors.js`・`build.gradle.kts`・`.github/` ほか。前の記録に既にあるものは重ねて書いていない）。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` で、`./` は `analyzed.paths` に入れていない。`intent` は `intents.json` の `slug`（`tz-options-perf`）。
- `fingerprint` は、最終の `analyzed.paths`（22）をカンマ区切りで `aidlc engine workspace codekb-scope-diff --repo mastersmith2 --mint --paths` に渡した出力をそのまま貼った。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: tz-options-perf
fingerprint: 563c9f851852be7deef36ec714bd9bdac51f8454
analyzed:
  paths:
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
    - frontend/vitest.config.ts
  components:
    - frontend-feature-preferences
    - frontend-shared-format
shallow:
  paths:
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
