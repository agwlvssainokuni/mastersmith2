# コードの構成（mastersmith2）

> 2026-10-28（記録のコミット `bb7e9ce`）の Intent `261027-perf-targets-review` の Focused scan で更新した。`businessdata` と `targetdb` の合計 156 ファイル（`git ls-files`）を再確認し、他のパッケージのファイル数は前回（2026-10-25、`find` と `git ls-files`）の数のまま。`perf/` の構成（`biz/`・`lib/`・`k6/`・`ui/` ほか）を足した。所見の本文はこの文書には無く、場所は `business-overview.md` の一覧。

## リポジトリの最上位

| 場所 | 中身 |
|---|---|
| `backend/` | Java のアプリ（Gradle、実行可能 WAR）。Java のパッケージ配下に 16 パッケージ（下表）。`git ls-files backend/src/main` は 867、`backend/src/test` は 669（リソースを含む） |
| `frontend/` | React + TypeScript の SPA。`src/app/`（92）・`src/shared/`（51）・`src/features/`（417）・`e2e/`（58 ファイル、`*.e2e.ts` は 20 本） |
| `vendor/make-you-chic-ui/` | デザインシステム（Git サブモジュール、固定先 `ecb07e9`） |
| `vendor/java-mustache-processor/` | Mustache のエンジン（Git サブモジュール、固定先 `8d44c36`、0.1.0） |
| `docker/` | `monitoring/`・`otel-collector/`・`perf/`（使い捨ての試験の環境）・`targetdb/`（見本の対象DB）ほか |
| `perf/` | k6 の負荷の試験（`perf/k6/scenarios.js` 2595 行、種 `perf/biz/make-biz-seed.sh`、DSL `perf/make-biz-dsl.mjs`・`make-*-dsl.mjs`）、`perf/dsl-mixed-memory.sh`（`memory.peak` の内訳の記録）、`perf/versions.env`（k6・JRE の版）、`perf/lib/`（接続先の組み立て）、`perf/ui/`、手順（`perf/README.md` 1320 行） |
| `config/` | 検査の設定（`npm-build-tools.txt`・`license-header.txt` など） |
| `.github/` | CI（`workflows/ci.yml`）と Dependabot（`dependabot.yml`） |
| 最上位のファイル | `settings.gradle.kts`・`build.gradle.kts`・`gradle/libs.versions.toml`・`Dockerfile`・`compose.yaml`・`.env.example`・`README.md` |
| `aidlc/` | AI-DLC の記録（コードではない） |

## バックエンドのパッケージ（`backend/src/main/java/cherry/mastersmith/`）

| パッケージ | ファイル数 | 下位のパッケージ |
|---|---|---|
| `access` | 20 | `domain`・`service`・`web` |
| `appearance` | 14 | `config`・`service`・`web` |
| `audit` | 15 | `domain`・`repository`・`service` |
| `auth` | 58 | `domain`・`repository`・`service`・`web` |
| `businessdata` | 113 | `domain`・`repository`・`service`・`web` |
| `common` | 47 | `error`・`health`・`i18n`・`observability`・`paging`・`persistence`・`security`・`web` |
| `config` | 6 | なし |
| `dsl` | 64 | `domain`・`parse`・`service`・`validate` |
| `dslmanage` | 54 | `domain`・`generate`・`repository`・`service`・`web` |
| `group` | 55 | `domain`・`repository`・`service`・`store`・`web` |
| `invitation` | 65 | `domain`・`lock`・`repository`・`service`・`web` |
| `mail` | 28 | `config`・`domain`・`service`・`template`・`transport` |
| `navigation` | 23 | `domain`・`service`・`web` |
| `role` | 143 | `domain`・`repository`・`service`・`store`・`transfer`・`web` |
| `targetdb` | 43 | `config`・`domain`・`repository`・`service` |
| `user` | 70 | `domain`・`repository`・`service`・`web` |
| `useradmin` | 27 | `domain`・`service`・`web` |

- 起動クラスは `MastersmithApplication.java`。リソースは `backend/src/main/resources/`: `db/migration/`（Flyway の V1〜V13。V13 は業務データの監査の列）・`dsl/dsl-schema-v2.json`・`navigation/allowed-icons.txt`・`mail/templates/`・`application.yaml`・`logback-spring.xml`。

## 今回深く読んだ `businessdata` と `targetdb` の中身

- `businessdata/repository/`: `RowSqlBuilder`（`where`・`count`・`page`・`detail` の SQL）、`RowSqlExecutor`（実行。`search`・詳細・書き込み）、`RowDialect`（`MysqlFamilyRowDialect`・`PostgresRowDialect`）、`RowSqlFailureClassifier`、`RowWriteSqlBuilder`、`ColumnValueCodec`、`RowSearchResult`（`Page(long total, rows)` か `Failed`）。
- `businessdata/service/`: `BusinessDataReadService`（`definition`・`search`・詳細）、`BusinessDataWriteService`、`TableAccessResolver`、`SearchResult`・`WriteResult`、`BusinessDataProblemTypeCatalog`。
- `businessdata/web/`: `BusinessDataReadController`・`BusinessDataWriteController`・`BusinessDataPaths`、要求・応答の `record`（`RowPageResponse`・`SearchRequest` ほか）。
- `businessdata/domain/`: `PageRequest`（ページの大きさ 20・50・100）、`ContainsPattern`、`SearchRequestValidator`、`TableAccessView`、`ReadRejection`、`BusinessDataChangedEvent` ほか。
- `targetdb/config/`: `TargetDataSourceConfig`（読み取り）・`TargetWriteDataSourceConfig`（書き込み）・`TargetDbProperties`（`Pool`・`QueryTimeout`・`Write`）・`TargetDbSettings`・`TargetDbUsableCondition`。`targetdb/service/`: `TargetReadAccess`・`JdbcTargetReadAccess`・`TargetWriteAccess`・`JdbcTargetWriteAccess`・`TargetSchemaReader`・`JdbcTargetSchemaReader`。

## 層と型の決まり（`team.md` の Code Style と一致する形）

- `web`: controller と `record` の DTO。口ごとに `@ApiAccess`。個人に関する値・行の値を持つ型は `toString` を上書きして伏せる。
- `service`: トランザクションの境界。新しい機能は想定内の失敗を結果の型（sealed interface）で返し、controller が `switch` で `BusinessException` に変える（`SearchResult`・`WriteResult` はこの形）。
- `domain`: 変更できない値と列挙。問題の種類は `<機能>.domain.XxxProblemTypes`。
- `repository`: 内部DB は Spring Data JPA、対象DB の SQL は `businessdata/repository` の `RowSqlExecutor` に閉じる。

## 画面の構成（`frontend/src/`）

- `app/`: 骨組み（`registry/`・`navigation/`・`layout/`・`i18n/`・`routing/`・`work-role/` ほか）。
- `shared/`: 機能をまたぐ部品（`api-client/`・`format/`・`modal/`・`null-value/`・`page-size/`・`paging/`・`row-actions/`・`scroll-region/`・`slow-notice/`・`tree/`・`unsaved-guard/`・`validation/`）。
- `features/<featureId>/`: `admin`・`auth`・`dsl`・`groupadmin`・`invitation`・`mypermissions`・`preferences`・`registration`・`roleadmin`・`roletransfer`・`tables`・`useradmin` の 12 個と `README.md`。業務データの画面は `tables`（一覧 `BusinessDataListPage`・詳細・作成/更新の `BusinessDataFormPage`・表 `BusinessDataTable`・検索 `SearchPanel`・ページの計算 `listPaging.ts`・API の呼び出し `businessDataApi.ts`）。
- テストは対象と同じ場所に `*.test.ts(x)`。E2E は `frontend/e2e/NNN-*.e2e.ts`（010〜185、20 本）と `support/`。

## Sources

- `find`・`ls`・`git ls-files` の数（アーキテクト、2026-10-25）と開発担当の Packages Found。
