# Reverse Engineering Timestamp

## Run Record

- Date: 2026-11-03
- Commit: fa0b01e6942b183247022169adc1aded47c4e225

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-11-03 |
| Intent | `261102-timezone-ui-polish`（scope classic・深さ Standard・brownfield。N6 の業務データの日時のタイムゾーンの変換（MySQL・MariaDB・PostgreSQL の型を含む）と画面の仕上げ（アプリのアイコンと favicon、ホームの中身、管理の仮の画面、管理メニューと業務メニューのアイコン）） |
| 対象 | mastersmith2（プロジェクトルート、単一のリポジトリ） |
| 記録するコミット | `fa0b01e6942b183247022169adc1aded47c4e225`（`develop` の HEAD。依頼の指示の値。`git rev-parse HEAD` でも確かめた。スナップショットの source の値とは別） |
| 走査の広さ・深さ | 範囲を絞ったスキャン（Focused scan）・Standard。既存の知識ベースは STALE で、前の散文を残して今回の範囲の節を更新・追加した |
| 手順 | 開発担当が1人でコードスキャン（`aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/reverse-engineering/developer-scan.md`）と9文書の更新を行った（設計担当は今回の構成に無い） |
| サブモジュール | 固定先のハッシュ（`git submodule status`）と、make-you-chic-ui の `Icon/registry.ts` と `AppShell/{AppShell,Topbar,Sidebar}.tsx` の props だけ（読み取り）。make-you-chic-ui は `cae5fed`、java-mustache-processor は `d61ba6d`（`0.1.0-6`） |

## 確かめ方

- ファイルの読み取り（cat・sed・grep・find・ls・wc）と、読み取りの git（`rev-parse`・`submodule status`・`log` のサブモジュールの2行）だけ。ビルド・テスト・`gradlew`・docker の操作はしていない。`.env`・`evidence/`・`~/.mastersmith-backup`・`reference/` は開いていない。
- 事実と見立てを分け、見立てには「未検証」と書いた（K-95 の PgJDBC のセッションの時差と `CURRENT_TIMESTAMP` の意味、K-97 の夏時間、K-98 の端数の上限 0、K-101 の印の足し方、K-102 の `/favicon.ico` の応答）。
- 引いた行の番号は、書いた後に grep で確かめ直した（`ColumnValueCodec.valueType` 266〜330 行、`MysqlFamilyRowDialect` 89〜98 行、`valueFormat.ts` 69〜75 行、`formDiff.ts` 115〜120 行など）。

## 前の記録との関係

- 前のコード知識ベースは Intent `261101-maintenance-update` のもの（記録のコミット `6079f13`）。その後に同 Intent の後の段の変更が入り（`develop` が `fa0b01e` に進んだ）、判定が STALE のため、今回の範囲の節を更新・追加した。
- 部品 ID は 48 個のまま（増減なし）。今回の `analyzed.components` は 10 個（`targetdb`・`dsl`・`dslmanage`・`navigation`・`businessdata`・`frontend-app-core`・`frontend-registry`・`frontend-app-layout-i18n`・`frontend-feature-admin`・`frontend-feature-tables`）。範囲の外の部品の記述は前回以前のまま残した。
- 所見は前回までの K-62〜K-93 に、今回 K-94〜K-105 を足した（一覧は `business-overview.md`）。K-105 は K-86 のサブモジュールの固定先の訂正（`cab4207`・`8d44c36` → `cae5fed`・`d61ba6d`）。
- 前回の記録との食い違い: サブモジュールの固定先（K-105）を `code-structure.md`・`component-inventory.md`・`technology-stack.md`・`dependencies.md` で直した。`java-mustache-processor` の版の定義は `0.1.1`（`gradle/libs.versions.toml` 46 行、grep）。

## 範囲の要約

- 深く読んだ: 50 のファイル、10 部品。依頼の範囲のディレクトリ（`businessdata/`・`targetdb/`・`dsl/`・`dslmanage/`・`navigation/`・`frontend/src/app/`・`frontend/src/features/{admin,tables,dsl}/`・`frontend/src/shared/`）は、中のファイルを選んで読んだため、ディレクトリではなくファイルで `analyzed.paths` に入れた（残りは `shallow.paths`）。`backend/src/main/resources/dsl/`・`backend/src/main/resources/application.yaml`・`docker/targetdb/`・`frontend/src/features/dsl/` は grep だけのため `shallow.paths`。
- 前回の `analyzed.paths`（26）は、今回の `analyzed.paths` に含まれるもの（`.gitmodules`・`businessdata/repository/RowDialect.java`）を除き、STALE の規則で `shallow.paths` に下げた。
- スナップショットの範囲の外は grep の流し読みだけで、`shallow.paths` に記録した（`access/web/AdminCheckController.java`・`config/WebConfig.java`・`backend/src/test/java/cherry/mastersmith/businessdata/`・`frontend/e2e/`・`perf/`・`Dockerfile`・`compose.yaml`・`backend/build.gradle.kts`・`gradle/`・`vendor/make-you-chic-ui/`）。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` で、`./` は `analyzed.paths` に入れていない。`intent` は `intents.json` の `slug`（`timezone-ui-polish`）。
- `fingerprint` は、最終の `analyzed.paths`（50）をカンマ区切りで `aidlc engine workspace codekb-scope-diff --repo mastersmith2 --mint --paths` に渡した出力をそのまま貼った。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: timezone-ui-polish
fingerprint: 37133d238722fa73863065354f5ada933b4c2ed8
analyzed:
  paths:
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
  components:
    - targetdb
    - dsl
    - dslmanage
    - navigation
    - businessdata
    - frontend-app-core
    - frontend-registry
    - frontend-app-layout-i18n
    - frontend-feature-admin
    - frontend-feature-tables
shallow:
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
