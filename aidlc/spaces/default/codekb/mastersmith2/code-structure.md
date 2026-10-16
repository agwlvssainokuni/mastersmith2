# コードの構成（mastersmith2）

> 2026-10-16（記録のコミット `09b795d`）の Full rescan で作り直した。ファイルの数は `git ls-files` と `find` の数（アーキテクトが数え直した）。所見の本文はこの文書には無く、各所見の場所は `business-overview.md` の一覧。

## リポジトリの最上位

| 場所 | 中身 |
|---|---|
| `backend/` | Java のアプリ（Gradle、実行可能 WAR）。本体のソース 736 ファイル・テスト 586 ファイル（リソースを含む `git ls-files` の数） |
| `frontend/` | React + TypeScript の SPA。`src/app/`（90）・`src/shared/`（34）・`src/features/`（13 機能・351）・`e2e/`（18 本と `support/` 26） |
| `vendor/make-you-chic-ui/` | デザインシステム（Git サブモジュール、固定先 `a9309c4`） |
| `vendor/java-mustache-processor/` | Mustache のエンジン（Git サブモジュール、固定先 `8d44c36`） |
| `docker/` | `monitoring/`（手元の監視）・`otel-collector/`・`perf/`（使い捨ての試験の環境）・`targetdb/`（見本の対象DB）・`jmx/`・`hikari-pool.sh`・`check-container-limits.sh` |
| `perf/` | k6 の負荷の試験（`perf/k6/scenarios.js`）と手順（`perf/README.md`） |
| `config/` | 検査の設定（`npm-build-tools.txt` など） |
| `.github/` | CI（`workflows/ci.yml`）と Dependabot（`dependabot.yml`） |
| 最上位のファイル | `settings.gradle.kts`・`build.gradle.kts`・`gradle/libs.versions.toml`・`Dockerfile`・`compose.yaml`・`.env.example`・`.env.targetdb.example`・`README.md` |
| `aidlc/` | AI-DLC の記録（コードではない） |

## バックエンドのパッケージ（`backend/src/main/java/cherry/mastersmith/`）

| パッケージ | ファイル数 | 下位のパッケージ |
|---|---|---|
| `access` | 20 | `domain`・`service`・`web` |
| `appearance` | 14 | `config`・`service`・`web` |
| `audit` | 15 | `domain`・`repository`・`service`（Web の層なし） |
| `auth` | 58 | `domain`・`repository`・`service`・`web` |
| `common` | 47 | `error`・`health`・`i18n`・`observability`・`paging`・`persistence`・`security`・`web` |
| `config` | 6 | なし |
| `dsl` | 64 | `domain`・`parse`・`service`・`validate` |
| `dslmanage` | 54 | `domain`・`generate`・`repository`・`service`・`web` |
| `group` | 55 | `domain`・`repository`・`service`・`store`・`web` |
| `invitation` | 65 | `domain`・`lock`・`repository`・`service`・`web` |
| `mail` | 28 | `config`・`domain`・`service`・`template`・`transport` |
| `navigation` | 23 | `domain`・`service`・`web`（repository なし） |
| `role` | 143 | `domain`・`repository`・`service`・`store`・`transfer`・`web` |
| `targetdb` | 26 | `config`・`domain`・`repository`・`service`（web なし） |
| `user` | 70 | `domain`・`repository`・`service`・`web` |
| `useradmin` | 27 | `domain`・`service`・`web`（repository なし） |

- 起動クラスは `MastersmithApplication.java`。
- リソース（`backend/src/main/resources/`）: `db/migration/`（Flyway の V1〜V12）・`dsl/`（`dsl-schema-v2.json` ほか）・`navigation/`（`allowed-icons.txt`）・`mail/`（テンプレート）・`META-INF/`・`application.yaml`。
- 移行の一覧: V1 baseline・V2 利用者・V3 認証・V4 監査・V5 DSL の管理・V6 DSL の監査の列・V7 利用者の設定・V8 招待・V9 利用停止・V10 グループ・V11 ロール・V12 ロールの割り当て。業務データの表はアプリの内部DB に無い（業務データは対象DB にある）。

## 層と型の決まり（コードで見られる形）

- `web`: controller と `record` の DTO（`XxxRequest`・`XxxResponse`）。口ごとに `@ApiAccess`。個人に関する値・秘密を持つ record は `toString` を上書きして伏せる（例: `navigation/web/TableAccessResponse.java` 51 行）。
- `service`: トランザクションの境界。新しい機能は想定内の失敗を結果の型（sealed interface の record）で返し、controller が `switch` で `BusinessException` に変える。
- `domain`: 変更できない値（`record`）と列挙。問題の種類の定数は `<機能>.domain.XxxProblemTypes`、起動時に集める一覧は `<機能>.service`。
- `repository`: Spring Data JPA と JDBC。`role`・`group` は行の排他を `store` に分ける。
- 用途名の下位パッケージ: `dsl/parse`・`dsl/validate`・`dslmanage/generate`・`role/transfer`・`invitation/lock`・`mail/template` など。
- 依存性の注入はコンストラクター注入、Lombok は使わない、ロガーは SLF4J（`team.md` の Code Style。今回深く読んだファイルはこの形）。

## 今回の Intent に関わるパッケージの中身（深く読んだもの）

- `targetdb`: `config/TargetDataSourceConfig.java`・`config/TargetDbProperties.java`（読み取り専用のプール・時間の上限）、`domain/ReadPurpose.java`・`domain/SqlIdentifiers.java`、`service/JdbcTargetSchemaReader.java`（口は `service/TargetSchemaReader.java`）。K-49・K-51。
- `role`: `domain/` の `MainPermission`・`PermissionLevel`・`PermissionValues`・`EffectivePermission`・`EffectiveNode`・`PermissionTarget`・`PermissionInheritance`・`PermissionSnapshot`、`service/EffectivePermissionResolver`・`EffectivePermissionResolverImpl`。K-50。
- `navigation`: `domain/` の `MenuFilter`・`NavNode`・`NavTableRef`・`TableAccessResult`・`EmptyReason`、`service/NavigationService`、`web/NavigationController`・`TableAccessResponse`・`NavTableRefResponse`。K-50・K-52。
- `dsl/domain/`: `DslModel`・`DslSchema`・`DslTable`・`DslColumn`・`DbType`・`FormPart`・`SearchSetting`・`SearchOperator`・`ListSetting`・`ListFormat`・`DetailSetting`・`OptionSource`・`OptionSourceKind`・`Validation`・`DslForeignKey`・`LookupListItem`・`LookupSearchItem`・`DslMenuItem`・`TableRef`・`DslFormat`・`ActiveDsl`、`dsl/service/ActiveDslModelProvider`。K-51。

## 画面の構成（`frontend/src/`）

- `app/`: 骨組み（起動・`registry/`・`navigation/`・`layout/`・`i18n/`・`routing/`・`work-role/`・`admin-forbidden/`・`display-settings/`・`login-handoff/`・`login-state/`・`pages/`・`testing/`）。
- `shared/`: 機能をまたぐ部品（`api-client/`・`format/`・`modal/`・`paging/`・`slow-notice/`・`tree/`・`validation/`）。`src/app/`・`src/features/` を読まない（ESLint）。
- `features/<featureId>/`: `admin`・`auth`・`dsl`・`groupadmin`・`invitation`・`mypermissions`・`preferences`・`registration`・`roleadmin`・`roletransfer`・`tables`・`useradmin` の 12 個と `README.md`（機能の作り方の決まり）。開発担当の「13 機能」はディレクトリの 13 項目の数で、機能は 12 個、残りの1つは `README.md` である（アーキテクトが `ls` で確かめた）。
- 機能は `registration.ts` で登録し、`access`（`PUBLIC`・`LOGGED_IN`・`ADMIN` の3値の表示の制御）とサイドバー・ユーザーメニューの項目を持つ（`app/registry/types.ts`）。
- テストは対象と同じ場所に `*.test.ts(x)`。E2E は `frontend/e2e/NNN-*.e2e.ts`（010〜170 の 18 本）と `support/`。

## Sources

- `git ls-files`・`find`・`ls` の数（アーキテクト、2026-10-16）と開発担当の Packages Found。
