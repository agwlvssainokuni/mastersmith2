# Reverse Engineering の実施記録（mastersmith2）

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-10-16 |
| Intent | `261016-business-data-views`（scope classic、深さ Standard、brownfield。業務データの一覧・詳細の画面（J・K）と、前の Intent `261015-deps-ui-carryover` からの持ち越し（make-you-chic-ui の固定先 `b0a1e79`、axe-core 4.14、不安定なテスト2件、`dslMixed` の `memory.peak`）） |
| 対象 | mastersmith2（プロジェクトルート、単一のリポジトリ） |
| 記録するコミット | `09b795d2a2801d603edbe4c1cd7a3bb5c9b85d77`（`develop` の HEAD。開発担当が読んだ時点と同じで、アーキテクトも `git rev-parse HEAD` で確かめた）。スキャンの前に取ったスナップショットの source の値（`git:d0c9…`）はコミットではないため、ここには書かない |
| 走査の広さ・深さ | 全体の読み直し（Full rescan）・Standard。既存の知識ベースの判定は UNVERIFIED。全体の構成は `git ls-files` とディレクトリの一覧で把握し、今回の Intent に関わる範囲だけを深く読んだ |
| 手順 | 開発担当のコードスキャン（`aidlc/spaces/default/intents/261016-business-data-views/inception/reverse-engineering/developer-scan.md`）→ アーキテクトによる統合。9 文書はこの回の調査から作り直した（wholesale replace） |
| サブモジュール | `vendor/make-you-chic-ui` `a9309c4`・`vendor/java-mustache-processor` `8d44c36`（0.1.0）。`git submodule status` で確かめた |

## 確かめ方

- ファイルの読み取り（Read・grep・`ls`・`find`・`git ls-files`・`git submodule status`・`git rev-parse`・サブモジュールの中の `git log`・`git diff --stat`）で確かめた。Gradle・npm・Docker・git の状態を変える操作は行っていない。`.env`・鍵ファイル・Git 管理外の参考資料は開いていない。
- アーキテクトが自分で確かめ直したもの: 深く読んだ 77 のパスがすべて存在し重なりが無いこと、`TargetDataSourceConfig.java` の `setReadOnly(true)`（105 行）と `defaultCandidate = false`（79 行）、`ReadPurpose` の2値、`targetdb/service/` のファイル、`TableAccessResponse` の項目（28 行）と `toString`（51 行）、`RoleBoundaryArchitectureTest` 129 行の `@DisplayName`、`AuditEventType` の 34 値、`AuditEventListener` の import の先、`packagesJudgedByTotal` の 7 パッケージ（230〜238 行）、axe-core の lockfile の版 4.13.0、`frontend/package.json` の版、`gradle/libs.versions.toml` の版、テストのソースで `admin_flag`・`adminFlag` を含むファイルの数（34）、`UserRowActions.tsx` 72〜80 行、`AdminForbiddenProvider.test.tsx` 231〜242 行と 67〜69 行、`RoleConcurrencyIT.java` 170〜178 行、`buildNavSections.ts` 158 行、`Avatar.tsx` の頭文字（32〜35 行）と `aria-label`（60 行）、`Topbar.tsx` のトリガー、`SAMPLE_DISPLAY_NAME`（27 行）、`a9309c4..b0a1e79` のコミットと差の統計、`NavigationController` の分類と code、`DslTable` の `view`・`primaryKey`、ファイルの数（本体 736・テスト 586・`src/app` 90・`src/shared` 34・`src/features` 351・`e2e/support` 26・E2E 18 本）、境界テストのファイルの一覧、機能の間の import（`dependencies.md` の表）、`@RestController` と `@ApiAccess` と道の定数（`api-documentation.md` の表）。
- 事実と見立て（仮説）は、開発担当の記録どおり各所見の本文で分けて書いた。CI の失敗の記録（K-57・K-58）と前の Intent の数値（K-59）は前の Intent の記録によるもので、アーキテクトは数え直していない。

## 前の記録との関係

- 前のコード知識ベースは Intent `261015-deps-ui-carryover` の開始時のもの（partial、11 部品・22 パス、記録のコミット `4f6c27d`。判定は UNVERIFIED）。
- 今回は Full rescan のため、9 文書を作り直し、前の文は残していない。前回までの所見 K-1〜K-48 の本文はこの知識ベースに無い（前の Intent の記録と git の履歴に残る）。
- Scope of Analysis はこの回の走査だけから作った。前回の `analyzed.paths` は引き継いでいない。
- 前の記録と変わったと分かったこと: `access` に境界テスト `AccessBoundaryArchitectureTest` がある（前の記録の K-38 は「`access` には無い」）。`AuditEventType` は 34 個（前の記録は 22 個）。make-you-chic-ui の固定先は `a9309c4`（前の記録は `5bf1ffe`）。DSL の JSON Schema は `dsl-schema-v2.json`。

## 開発担当の記録との差

- 境界の構造の検査（ArchUnit）の数: 開発担当は「17 個」と書いたが、挙げた内訳（全体の2つと機能ごとの 16 個）を足すと 18 個で、`git ls-files` でも 18 ファイルある。`targetdb` の2つは `TargetDbBoundaryArchitectureTest` と `targetdb/testsupport/ExtensionOrderArchitectureTest`。文書には 18 個と書いた。
- `targetdb/service/` の中身: 開発担当は「`JdbcTargetSchemaReader` だけ」と書いたが、口の `TargetSchemaReader`（`readSchema(ReadPurpose)`、39 行）と `package-info.java` もある。スキーマの情報を返す口しか無いという結論は変わらない。
- 画面の機能の数: 開発担当は「13 機能」と書いたが、`frontend/src/features/` の 13 項目のうち1つは `README.md` で、機能のディレクトリは 12 個。
- 部品: 開発担当の深く読んだ 16 部品をそのまま `analyzed.components` にした。`V10__u3_group.sql` は監査の `detail` の列のために読んだもので、`group` の部品は流し読みのままとした。
- 記録するコミットは開発担当と同じ `09b795d`。

## 所見の番号

前回の所見は K-48 で終わるため、今回は開発担当の番号どおり **K-49〜K-61** とした。一覧と持ち主の文書は `business-overview.md`、本文は持ち主の文書に1か所だけ書いた（`architecture.md`: K-49・K-50・K-52・K-54、`api-documentation.md`: K-51・K-61、`code-quality-assessment.md`: K-53・K-57・K-58・K-59、`component-inventory.md`: K-55・K-56、`dependencies.md`: K-60）。

## 部品 ID の扱い

- 前回までの 47 個の ID をそのまま使い、新しい ID は足していない（業務データの機能はまだコードに無い）。`component-inventory.md` の見出しは前回と文字どおり同じ 47 個である（アーキテクトが比べた）。
- 前の ID の場所に足したもの（ID は変えていない）: `app/testing/`・`shared/slow-notice` を `frontend-app-core` に、`.env.targetdb.example` を `container-runtime` に。
- `analyzed.components` は、今回深く読んだファイルを持つ 16 個。どれも部品の一部だけを読んだ「一部」の深さである。

## 範囲の要約

- 深く読んだ: 77 のパス（すべてファイル）、16 の部品。パスは開発担当の Scan Coverage の「Analyzed deeply」のとおり。
- 一部だけ読んだファイル（開発担当の「一部だけ深く読んだもの」）と流し読みの範囲は `shallow.paths` に入れた。リポジトリの外の読み取り（`frontend/node_modules/axe-core/axe.js`、Git 管理の外）と前の Intent の記録（`aidlc/` の下、コードではない）は `shallow` にも入れていない。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` で、`./` は `analyzed.paths` に入れていない（Full rescan を選んでも、前回までと同じ扱い）。
- 前回の深い範囲（`role/store`・`dsl/parse`・`frontend/src/shared/tree` など 22 パス）は今回確かめ直していないため、比較は前回と範囲が重ならない部分を含む。
- `fingerprint` は、最終の `analyzed.paths`（77）をカンマ区切りで `aidlc engine workspace codekb-scope-diff --mint --paths` に渡した出力をそのまま貼った。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: 261016-business-data-views
fingerprint: a1d5f703d89583d45b02969ce882f866a28f9366
analyzed:
  paths:
    - backend/src/main/java/cherry/mastersmith/role/domain/MainPermission.java
    - backend/src/main/java/cherry/mastersmith/role/domain/PermissionLevel.java
    - backend/src/main/java/cherry/mastersmith/role/domain/PermissionValues.java
    - backend/src/main/java/cherry/mastersmith/role/domain/EffectivePermission.java
    - backend/src/main/java/cherry/mastersmith/role/domain/EffectiveNode.java
    - backend/src/main/java/cherry/mastersmith/role/domain/PermissionTarget.java
    - backend/src/main/java/cherry/mastersmith/role/domain/PermissionInheritance.java
    - backend/src/main/java/cherry/mastersmith/role/domain/PermissionSnapshot.java
    - backend/src/main/java/cherry/mastersmith/role/service/EffectivePermissionResolver.java
    - backend/src/main/java/cherry/mastersmith/role/service/EffectivePermissionResolverImpl.java
    - backend/src/main/java/cherry/mastersmith/navigation/domain/MenuFilter.java
    - backend/src/main/java/cherry/mastersmith/navigation/domain/NavNode.java
    - backend/src/main/java/cherry/mastersmith/navigation/domain/NavTableRef.java
    - backend/src/main/java/cherry/mastersmith/navigation/domain/TableAccessResult.java
    - backend/src/main/java/cherry/mastersmith/navigation/domain/EmptyReason.java
    - backend/src/main/java/cherry/mastersmith/navigation/service/NavigationService.java
    - backend/src/main/java/cherry/mastersmith/navigation/web/NavigationController.java
    - backend/src/main/java/cherry/mastersmith/navigation/web/TableAccessResponse.java
    - backend/src/main/java/cherry/mastersmith/navigation/web/NavTableRefResponse.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslModel.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslSchema.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslTable.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslColumn.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DbType.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/FormPart.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/SearchSetting.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/SearchOperator.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/ListSetting.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/ListFormat.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DetailSetting.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/OptionSource.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/OptionSourceKind.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/Validation.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslForeignKey.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/LookupListItem.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/LookupSearchItem.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslMenuItem.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/TableRef.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslFormat.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/ActiveDsl.java
    - backend/src/main/java/cherry/mastersmith/dsl/service/ActiveDslModelProvider.java
    - backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java
    - backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbProperties.java
    - backend/src/main/java/cherry/mastersmith/targetdb/domain/ReadPurpose.java
    - backend/src/main/java/cherry/mastersmith/targetdb/domain/SqlIdentifiers.java
    - backend/src/main/java/cherry/mastersmith/targetdb/service/JdbcTargetSchemaReader.java
    - backend/src/main/java/cherry/mastersmith/common/security/ApiAccess.java
    - backend/src/main/java/cherry/mastersmith/common/security/ApiAccessLevel.java
    - backend/src/main/java/cherry/mastersmith/common/security/ApiDefaultAccess.java
    - backend/src/main/java/cherry/mastersmith/common/security/ErrorResponseWriter.java
    - backend/src/main/java/cherry/mastersmith/common/security/SecurityExtensionValidator.java
    - backend/src/main/java/cherry/mastersmith/common/security/SecurityRuleContributor.java
    - backend/src/main/java/cherry/mastersmith/access/web/AdminAuthorizationManager.java
    - backend/src/main/java/cherry/mastersmith/access/web/AdminSecurityContributor.java
    - backend/src/main/java/cherry/mastersmith/access/web/AdminApiDefaultAccess.java
    - backend/src/main/java/cherry/mastersmith/access/domain/AdminPaths.java
    - backend/src/main/java/cherry/mastersmith/access/domain/AccessDeniedReason.java
    - backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java
    - backend/src/main/resources/db/migration/V10__u3_group.sql
    - backend/src/main/resources/db/migration/V11__u4_role.sql
    - backend/src/main/resources/db/migration/V12__u4_role_assignment.sql
    - backend/src/test/java/cherry/mastersmith/role/web/RoleConcurrencyIT.java
    - backend/src/test/java/cherry/mastersmith/targetdb/TargetDbBoundaryArchitectureTest.java
    - frontend/src/features/tables/registration.ts
    - frontend/src/features/tables/TablePlaceholderPage.tsx
    - frontend/src/features/tables/useTableAccess.ts
    - frontend/src/features/tables/tableAccessApi.ts
    - frontend/src/app/navigation/tablePlaceholderPath.ts
    - frontend/src/app/registry/types.ts
    - frontend/src/features/useradmin/UserRowActions.tsx
    - frontend/src/features/groupadmin/GroupRowActions.tsx
    - frontend/src/features/roleadmin/RoleRowActions.tsx
    - frontend/src/app/admin-forbidden/AdminForbiddenProvider.test.tsx
    - frontend/e2e/support/axe.ts
    - frontend/package.json
    - gradle/libs.versions.toml
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Avatar/Avatar.tsx
  components:
    - role
    - navigation
    - dsl
    - targetdb
    - common-security
    - access
    - audit
    - frontend-feature-tables
    - frontend-registry
    - frontend-app-core
    - frontend-feature-useradmin
    - frontend-feature-groupadmin
    - frontend-feature-roleadmin
    - frontend-e2e
    - make-you-chic-ui
    - build-and-verify
shallow:
  paths:
    - backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java
    - backend/src/main/java/cherry/mastersmith/audit/service/AuditEventRecorder.java
    - backend/src/main/java/cherry/mastersmith/role/service/RoleAdminService.java
    - backend/src/test/java/cherry/mastersmith/navigation/NavigationBoundaryArchitectureTest.java
    - backend/src/test/java/cherry/mastersmith/role/RoleBoundaryArchitectureTest.java
    - backend/build.gradle.kts
    - backend/src/main/resources/application.yaml
    - frontend/src/app/navigation/buildNavSections.ts
    - frontend/eslint.config.js
    - .github/dependabot.yml
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Dropdown/Dropdown.tsx
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/AppShell/Topbar.tsx
    - perf/README.md
    - Dockerfile
    - backend/src/main/java/cherry/mastersmith/
    - backend/src/main/resources/db/migration/
    - backend/src/main/resources/dsl/
    - backend/src/main/resources/navigation/
    - backend/src/main/resources/mail/
    - backend/src/test/java/cherry/mastersmith/
    - frontend/src/app/
    - frontend/src/shared/
    - frontend/src/features/
    - frontend/e2e/
    - vendor/make-you-chic-ui/
    - vendor/java-mustache-processor/
    - docker/
    - compose.yaml
    - .env.example
    - .env.targetdb.example
    - perf/k6/scenarios.js
    - .github/workflows/ci.yml
    - settings.gradle.kts
    - build.gradle.kts
    - README.md
```
