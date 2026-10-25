# 依存関係（mastersmith2）

> 2026-10-25（記録のコミット `bc484c9`）の Full rescan・深さ Minimal で作り直した。機能の間の依存は `businessdata` と `audit` を今回の `import` の検索で確かめ、ほかは前回の記録（`261016-business-data-views`、`import` の検索）による。見送りの依存（K-66）の本文はこの文書に書いた。

## 機能の間の依存（バックエンド）

| 機能 | 依存する機能（import の先） | 境界テスト |
|---|---|---|
| `auth` | `common`・`user` | `AuthBoundaryArchitectureTest` |
| `access` | `auth`・`common`・`config` | `AccessBoundaryArchitectureTest` |
| `audit` | `access`・`auth`・`businessdata`・`dslmanage`・`group`・`invitation`・`role`・`user`・`useradmin`（各機能の出来事の型） | `AuditBoundaryArchitectureTest` |
| `user` | `common` | `UserBoundaryArchitectureTest` |
| `useradmin` | `access`・`auth`・`common`・`user` | `UserAdminBoundaryArchitectureTest` |
| `invitation` | `common`・`mail`・`user` | `InvitationBoundaryArchitectureTest` |
| `mail` | なし | `MailBoundaryArchitectureTest` |
| `appearance` | `common` | `AppearanceBoundaryArchitectureTest` |
| `targetdb` | なし | `TargetDbBoundaryArchitectureTest`・`ExtensionOrderArchitectureTest` |
| `dsl` | なし | `DslBoundaryArchitectureTest` |
| `dslmanage` | `auth`・`common`・`dsl`・`targetdb`・`user` | `DslManageBoundaryArchitectureTest`・`DslManageGenerateBoundaryArchitectureTest` |
| `role` | `common`・`dsl`・`group`・`user` | `RoleBoundaryArchitectureTest` |
| `group` | `common`・`user` | `GroupBoundaryArchitectureTest` |
| `navigation` | `auth`・`common`・`dsl`・`role` | `NavigationBoundaryArchitectureTest` |
| `businessdata` | `common`・`dsl`・`role`・`targetdb`（`import` の検索。`role` は `role.service`・`role.domain` だけ） | `BusinessDataBoundaryArchitectureTest` |
| `config` | `common` | 全体の `ArchitectureTest` |

- 依存される側の制限: `role` に依存してよいのは `audit`（`role.domain`）・`navigation`・`businessdata`（`role.service`・`role.domain`）だけ（`RoleBoundaryArchitectureTest` 129 行の `@DisplayName`）。`businessdata` に依存してよいのは `audit` の `businessdata.domain`（出来事と理由）だけ（`BusinessDataBoundaryArchitectureTest` 174 行）。`targetdb` の外から `targetdb.config`・`targetdb.repository` に依存しない（前回の記録）。
- 逆向きの知らせは Spring の出来事（監査は `audit` が各機能の `domain` の出来事の型を import して受ける）。

## 画面の依存

- 機能（`frontend/src/features/<featureId>/`、12 機能）どうしの import は ESLint の `no-restricted-imports` で止める。`src/shared/` から `src/app/`・`src/features/` を読むことも止める（前回の記録）。
- 画面は make-you-chic-ui を npm の `file:../vendor/make-you-chic-ui/packages/make-you-chic-ui` で使う。

## ビルドの依存

- `backend` の WAR は `frontend` のビルド結果（`dist`）を同梱する。`frontend` は `vendor/make-you-chic-ui` に、`backend` は `vendor/java-mustache-processor`（Gradle の composite build）に依存する。
- 依存の取得元は Maven Central だけに固定（`team.md`）。lockfile は `backend/gradle.lockfile`・`settings-gradle.lockfile`・`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json`。
- 依存の脆弱性の関門は `./gradlew verify` の中の OSV-Scanner（`vendor/make-you-chic-ui` の lockfile を含む）。更新の知らせは Dependabot。

## サブモジュールと見送りの依存

| 依存 | 今の固定 | メモ |
|---|---|---|
| `vendor/make-you-chic-ui` | `ecb07e9248af9f1203a0bd2d79673d35547a4416`（`git submodule status`） | `vendor/make-you-chic-ui/package-lock.json` の `"hasInstallScript": true` は 1 件（3112 行付近。どのパッケージかは未確認）。固定先を上げるときは `team.md` の手順で増減を確かめる |
| `vendor/java-mustache-processor` | `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`（0.1.0） | 今回の Intent では触れない見込み |
| `axe-core`（npm、dev） | `^4.14.0` | 以前の `>= 4.14.0` の見送りは外れている（`dependabot.yml` 65 行のコメント） |

**K-66 Dependabot の見送り（`ignore`）3件は、どれも「外す時期: Spring Boot を上げるとき」（`.github/dependabot.yml`）**

- 確かめた事実:
  - gradle の項（`dependabot.yml` 26〜53 行）の `ignore` は `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0`（外部エクスポートの有効時の失敗を確かめられないため、すべての更新を知らせない）、`com.networknt:json-schema-validator`（3.0.7 以降が Jackson を 3.2 系に上げるため。外す時期は管理の Jackson が 3.2 系以上になったとき）、`tools.jackson:jackson-bom`（脆弱性の直しのパッチの版を上書きしているため、すべての版の更新を知らせない）。Jackson については Dependabot の知らせではなく次の `./gradlew verify`・CI の OSV-Scanner で気づく形。
  - 同じ項に `exclude-paths: vendor/**`（composite build のサブモジュールの中は更新できないため）。npm の項は `typescript` の大きな版を見送る（TypeScript 7 を受け付けるまで）。docker の項は `eclipse-temurin` の更新を見送る（JDK をビルド・CI とそろえて上げるとき）。
  - 版の一覧は `technology-stack.md` の K-66 の表。
- 今回の Intent への意味: Spring Boot を 4.1.2 に上げたら、Tomcat の上書き・Jackson の上書き・appender の固定・networknt の固定を1つずつ見直し、不要になったものは `libs.versions.toml` の上書き・`dependabot.yml` の `ignore` とコメントを同じ変更で外す（`team.md`: 見送りを足す・外すときは理由と時期をコメントに書く）。

## Sources

- `import` の検索（アーキテクト、2026-10-25: `businessdata`・`audit`）、境界テストの `@DisplayName`、`git submodule status`、`.github/dependabot.yml`。
