# 依存関係（mastersmith2）

> 2026-10-28（記録のコミット `bb7e9ce`）の Intent `261027-perf-targets-review` の Focused scan で更新した。機能の間の依存の表は前回（2026-10-25）の `import` の検索による（今回は `businessdata`・`targetdb` の境界を読んだが依存の先は変わっていない）。Dependabot の現状（K-66・K-72）の本文はこの文書に書いた。

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
| `vendor/make-you-chic-ui` | `ecb07e9248af9f1203a0bd2d79673d35547a4416`（前回の `git submodule status`。今回は読んでいない） | `vendor/make-you-chic-ui/package-lock.json` の `"hasInstallScript": true` は 1 件（3112 行付近。どのパッケージかは未確認）。固定先を上げるときは `team.md` の手順で増減を確かめる |
| `vendor/java-mustache-processor` | `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`（0.1.0） | 今回の Intent では触れない見込み |
| `axe-core`（npm、dev） | `^4.14.0` | 以前の `>= 4.14.0` の見送りは外れている（`dependabot.yml` 65 行のコメント） |

**K-66・K-72 Dependabot の現状（`.github/dependabot.yml`、2026-10-28 に再読）**

- 確かめた事実:
  - エコシステムは gradle・npm（`/frontend`）・github-actions・docker・docker-compose の5つ（いずれも weekly、`open-pull-requests-limit: 10`）。`groups` は使っていない。
  - gradle: `exclude-paths: vendor/**`（composite build のサブモジュールの依存を更新できないため）。`ignore` は `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0` と `com.networknt:json-schema-validator` の2件（すべての更新）。前回の記録にあった `tools.jackson:jackson-bom` の `ignore` は無くなっている（Jackson の上書きが外れたため）。外す時期はコメントに書いてある（`technology-stack.md` の K-66 の表）。
  - npm: `typescript` の大きな版だけを見送る（`@typescript-eslint/*` が TypeScript 7 を受け付けるまで）。`axe-core` は `ignore` に入れず、更新時は版と lockfile だけでも E2E の全件と vitest-axe を流し違反 0 件を件数で記録する例外の手順がある。
  - docker: `eclipse-temurin` の大きな版だけを見送る（JDK をビルド・CI とそろえて上げるとき）。
  - Dependabot が見ない固定先（手でそろえる）: `docker/perf/compose.yaml`、`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`、`perf/versions.env`（k6・JRE）、`perf/README.md`、`README.md`、`docker/hikari-pool.sh`。
  - `team.md` の受け方: Dependabot の PR は GitHub の画面でマージせず、手元で版と lockfile をまとめて更新し `./gradlew verify` を通してから `develop` に統合し、PR は閉じる。重大度 High 以上は次の Bolt の前に取り込む。見送りを足すときは理由と外す時期を同じファイルのコメントに書く。
- 未確認: 今開いている Dependabot の PR の一覧と重大度（`gh` を使っていないため。後の段で確かめる）。

## Sources

- `import` の検索（前回: `businessdata`・`audit`）、境界テストの `@DisplayName`、`git submodule status`（前回）、`.github/dependabot.yml`（今回）。
