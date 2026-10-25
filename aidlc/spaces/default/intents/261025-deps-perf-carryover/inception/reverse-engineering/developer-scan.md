## Developer Code Scan Results

Conversation language: 日本語。以下、「確認」はソースを読んで確かめた事実、「仮説」は未検証の見立て。

### Scan Coverage
- **Analyzed deeply**:
  - `gradle/libs.versions.toml`、`backend/build.gradle.kts`、`backend/gradle.lockfile`（版の抜き出し）
  - `.github/dependabot.yml`、`.github/workflows/ci.yml`（構成のみ）
  - `backend/src/main/java/cherry/mastersmith/businessdata/`（`repository/RowSqlBuilder.java`、`repository/RowSqlExecutor.java` の一覧・件数の部分、`service/BusinessDataReadService.java` の `search`、`web/RowPageResponse.java`、クラス一覧）
  - `backend/src/main/java/cherry/mastersmith/targetdb/`（`config/TargetDataSourceConfig.java`、`config/TargetDbProperties.java`、クラス一覧）
  - `frontend/src/features/tables/`（ファイル一覧、`BusinessDataListPage.tsx`・`BusinessDataTable.tsx` の件数の表示箇所）、`frontend/package.json`、`frontend/vitest.config.ts`（thresholds）
  - `perf/k6/scenarios.js`（biz 場面の説明部）、`perf/README.md`（biz の場面の表と grep の行）、`docker/perf/compose.yaml`（app・対象DB の資源の上限）
  - `vendor/make-you-chic-ui` の固定先（git のコミットの確認のみ）
- **Skimmed only**: `backend/src/main` の `auth`・`access`・`role`・`group`・`user`・`audit`・`navigation`・`dsl*`・`invitation`・`mail`・`common`、`backend/src/test` の大半（businessdata のテストはファイル名の一覧のみ）、`frontend/src` の businessdata 以外、`frontend/e2e`、`docker/monitoring`・`docker/otel-collector`・`docker/targetdb`、`compose.yaml`、`Dockerfile`、`vendor/java-mustache-processor`、`backend/src/main/resources`（Flyway の移行）、`perf/` の businessdata 以外の場面。

### Packages Found
- `cherry.mastersmith.<機能>` — Java パッケージ — Java 25 — 機能ごとに `web`・`service`・`domain`・`repository`（必要なら `config`）に分ける構成（team.md の Code Style と一致。機能の一覧は流し読み）。
- `businessdata` — Java 機能 — 業務データの一覧・詳細・作成・更新・削除。`repository` に SQL の組み立て・実行・DB の種類ごとの方言（`RowSqlBuilder`・`RowSqlExecutor`・`RowDialect`・`MysqlFamilyRowDialect`・`PostgresRowDialect`・`RowSqlFailureClassifier`）。
- `targetdb` — Java 機能 — 対象DB（MySQL・MariaDB・PostgreSQL）の接続・スキーマ読み取り・読み書きの貸し出し（`config` に `TargetDataSourceConfig`・`TargetWriteDataSourceConfig`・`TargetDbProperties`、`service` に `JdbcTargetReadAccess`・`JdbcTargetWriteAccess`）。
- `frontend/src/features/<id>/` — React 機能 — 業務データの画面は `features/tables`（一覧・詳細・作成/更新・削除、`BusinessDataTable`・`SearchPanel`・`listPaging` など）。
- `vendor/make-you-chic-ui`・`vendor/java-mustache-processor` — Git サブモジュール。`git submodule status` で make-you-chic-ui = `ecb07e9248af9f1203a0bd2d79673d35547a4416`、java-mustache-processor = `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`（0.1.0）。
- `perf/`・`docker/perf/` — 負荷の試験（k6 の台本 `perf/k6/scenarios.js`、種 `perf/biz/make-biz-seed.sh`、DSL `perf/make-biz-dsl.mjs`、使い捨ての環境 `docker/perf/compose.yaml`）。

### Build System
- **Type**: Gradle（Kotlin DSL）。`./gradlew verify` が1コマンドの検査（フォーマット・リンタ・ライセンスヘッダー・ビルド・テスト・カバレッジ・Gitleaks・SpotBugs・OSV-Scanner。フロントの npm も Gradle から呼ぶ）。
- **Config Files**: `build.gradle.kts`、`backend/build.gradle.kts`、`settings.gradle.kts`、`gradle/libs.versions.toml`、`backend/gradle.lockfile`、`settings-gradle.lockfile`、`frontend/package.json`・`package-lock.json`、`config/npm-build-tools.txt`、`config/license-header.txt`。
- **Build Dependencies**: frontend のビルド結果（`dist`）を backend の WAR に同梱。java-mustache-processor は Gradle の composite build。make-you-chic-ui は `frontend/package.json` の `file:../vendor/make-you-chic-ui/packages/make-you-chic-ui`。

### APIs Discovered
- 業務データの HTTP API — `backend/.../businessdata/web/BusinessDataReadController.java`・`BusinessDataWriteController.java`（`BusinessDataPaths`）— 定義・一覧（search）・詳細（rows/get）・作成・更新・削除。主キーを道に載せない POST 中心（project.md の決定）。個別のエンドポイント数は数えていない（未確認）。
- 一覧の応答は `RowPageResponse`（`items`・`page`・`size`・`total`）。`total` は必ず確定した件数（`long`）。「多数」を表す値の入る余地は現状の型に無い（確認）。
- そのほかの API（auth・user・role・group・dsl・navigation・invitation など）は流し読み。

### Frameworks & Libraries
- Spring Boot — **4.1.1**（`gradle/libs.versions.toml` の `spring-boot`、`backend/gradle.lockfile` の `spring-boot:4.1.1`）— 確認。
- Tomcat — 11.0.26（`libs.versions.toml` の `tomcat` で上書き。lockfile `tomcat-embed-core:11.0.26`）— 確認。
- Jackson — **3.1.7**（`jackson` を `tools.jackson:jackson-bom` として platform で上書き。`backend/build.gradle.kts` 61〜67 行の `jacksonBom`）。コメントでは「Spring Boot の管理の 3.1.5 から」と書いてあり、Spring Boot 4.1.1 の管理の版は 3.1.5 と読める（確認）。4.1.2 の管理の版は未確認（仮説: 3.1.7 以上なら上書きは不要）。
- opentelemetry-logback-appender-1.0 — **2.28.1-alpha**（固定。lockfile で `io.opentelemetry:opentelemetry-api:1.62.0` などの SDK は 1.62.0、`opentelemetry-instrumentation-api:2.28.1`）。固定の理由（Spring Boot 4.1.1 の OpenTelemetry 1.62 との整合。外部エクスポート有効時に失敗）は project.md の Tech Stack と `dependabot.yml` のコメントに記載。4.1.2 が持つ OpenTelemetry の版は未確認（仮説: 上がっていれば appender の版も合わせて見直し、外部エクスポートを有効にして確かめる必要がある）。
- networknt json-schema-validator — 3.0.6（固定、3.0.7 以降は Jackson を 3.2 系に引き上げるため）。`dependabot.yml` の `ignore` に入っている。Jackson の上書きが不要になる場合は、この固定の理由も合わせて見直す対象（仮説）。
- 内部DB — H2 2.4.240（lockfile）。接続プール HikariCP 7.0.2、Hibernate 7.4.5.Final、Flyway 12.4.0。
- JDBC ドライバー — mysql-connector-j 9.7.0、mariadb-java-client 3.5.10、postgresql 42.7.13（lockfile。Spring Boot の BOM が管理）。
- snakeyaml 2.7（固定）、jqwik 1.10.2、archunit 1.5.1、subethasmtp 7.2.2、logstash-logback-encoder 9.0、JaCoCo 0.8.15、SpotBugs 4.10.4（プラグイン 6.5.12）、findsecbugs 1.14.0、Spotless 8.10.4（palantir-java-format 2.98.0）。
- frontend — React ^19.2.8、Vite ^8.3.4、Vitest ^5.0.4、`@vitest/coverage-v8` ^5.0.4、axe-core ^4.14.0、fast-check ^4.10.2、TypeScript ^6.0.3、ESLint ^10.12.0、oxlint ^1.87.0（`frontend/package.json`）。
- lockfile の状況 — `backend/gradle.lockfile`・`settings-gradle.lockfile`・`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json` が存在。vendor の lockfile の `"hasInstallScript"` は1件（`grep -c` による数。どのパッケージかは未確認）。

### Test Coverage
- **Test Directories**: `backend/src/test/java`（`XxxTest` 単体、`XxxIT` 結合）、`frontend/src/**/*.test.ts(x)`（対象と同じ場所）、`frontend/e2e`（Playwright、`./gradlew e2eTest`）。businessdata のテストは `domain`・`repository`（`RowSqlExecutorTest`・`RowDialectTest`・`RowSqlFailureClassifierTest` など）・`service`・`testsupport`（`CountingDataSource` など）と `BusinessDataBoundaryArchitectureTest`。
- **Test Frameworks**: JUnit 5、Spring Boot Test、Testcontainers（MySQL・MariaDB・PostgreSQL）、jqwik、ArchUnit。frontend は Vitest・Testing Library・user-event・vitest-axe・fast-check。
- **Coverage Config**: あり。バックエンドは JaCoCo（全体 行 80%・分岐 70%、パッケージごとの下限は `packagesJudgedByTotal` の7パッケージを除いて適用。`backend/build.gradle.kts` 230〜240 行付近）。frontend は `frontend/vitest.config.ts` の `thresholds`（lines 80・branches 70）。

### Code Quality Indicators
- **Linting**: Spotless（palantir-java-format）、SpotBugs＋FindSecBugs（`backend/config/spotbugs-exclude.xml`）、frontend は Prettier・oxlint・ESLint（`frontend/eslint.config.js`）・Stylelint、ライセンスヘッダー検査、Gitleaks。
- **CI/CD**: `.github/workflows/ci.yml`（ジョブの `timeout-minutes: 60` と 10。検査のジョブは `./gradlew verify` を呼ぶ）。actions はコミットの SHA で固定（checkout v7.0.1、setup-java v6.0.1、setup-node v7.1.0、setup-gradle v6.4.0、upload-artifact v7.0.2、download-artifact v8.0.2）。
- **Documentation**: `README.md`、`perf/README.md`（負荷の手順書。biz の場面は 1116〜1129 行付近）、コード中の日本語の Javadoc は厚い。

### Technical Debt Signals
- 依存の「見送り」が `.github/dependabot.yml` の `ignore` に3件（opentelemetry-logback-appender、networknt、Jackson の BOM）。いずれも「外す時期: Spring Boot を上げるとき」と書いてあり、今回の Intent の見直し対象に当たる（確認）。
- `perf/README.md` の grep の形: 189 行 `grep -o 'loginLoop-user vu=[0-9]* user=[^ "]*' … | sed …`、442 行 `grep -q '"value"'`、619・686・715 行 `grep -h -o '"…PoolLimit_…": {[^}]*}'`、1021 行 `grep -h -o '"checks": {[^}]*}'` などが並ぶ。依頼の「(c) の grep の形」がどの行か、元の振り返り（`feedback-loop.md`）を読んでいないため特定できていない（未確認。要件定義で突き合わせる）。
- Dependabot が見ない固定先（`docker/perf/compose.yaml`、`TargetDbImages.java`、`perf/README.md` の grafana/k6 など）を手で揃える運用。`dependabot.yml` のコメントで列挙済み。
- 全体の合計で判定している7パッケージ（`packagesJudgedByTotal`）が残る。今回の第2の束が触る `businessdata`・`targetdb` はこの一覧に無く、パッケージごとの下限が当たる（確認）。
- 負荷の試験の台本・手順書が大きい（`perf/README.md` は 1100 行超、`perf/k6/scenarios.js` も大きい）。

### 第2の束に関わる箇所（確認）
- 件数の取得: `RowSqlBuilder.count(Where)`（`backend/src/main/java/cherry/mastersmith/businessdata/repository/RowSqlBuilder.java` 142〜144 行）が `SELECT COUNT(*) FROM <表><WHERE>` を作る。上限・LIMIT のない完全な数え方。
- 一覧の実行: `RowSqlExecutor.search`（`RowSqlExecutor.java` 78〜122 行）が、(1) 件数の文（`setMaxRows(1)`、`setQueryTimeout(session.queryTimeoutSeconds())`）→ (2) `page.offset() >= total` なら空のページ → (3) ページの文（`LIMIT ? OFFSET ?`、`setMaxRows(page.size())`）の順で、同じ接続で2回問い合わせる。`RowSearchResult.Page(long total, rows)` が全件数を運ぶ。
- ページの文: `RowSqlBuilder.page`（147〜175 行）。主キーのある表は「主キーだけを内側で並べて `LIMIT/OFFSET`、外側で主キーに JOIN して列を読む」形。深い OFFSET は MariaDB で重い（project.md の Interpretations に Not Met 4件の記録）。
- 部分一致: `RowSqlBuilder.where` の `CONTAINS` が `LIKE ? ESCAPE '…'`（`ContainsPattern.of` でエスケープ）。前方にワイルドカードが付く形のため索引は使えない（仮説。パターンは `ContainsPattern` で確認していない）。
- 「多数」と出す案が触る境界: 応答 `businessdata/web/RowPageResponse.java`（`long total`）、サービス `BusinessDataReadService.search`（`SearchResult` を返す。129 行付近で `page.total()` を渡す）、画面 `frontend/src/features/tables/BusinessDataListPage.tsx`（207〜225 行の `t('tables.list.count', { total … })`・`isPageOverflow`・`previousPageFor`）と `BusinessDataTable.tsx`（213・251 行の `pageStatus` と `totalCount={page.total}`）、`frontend/src/shared/paging/paging.ts`（`pageCount`・`pageRange`）、`frontend/src/features/tables/listPaging.ts`。件数の型を数だけでなく「多数」を持てる形に広げるときは、これらの契約と画面のテスト（`BusinessDataListPage.test.tsx` など）に波及する（確認した範囲での見立て）。
- 読み取りのプール: `targetdb/config/TargetDbProperties.java` の `Pool`（`maximumSize` 既定 5、`idleTimeout` 既定 60 秒、116〜127 行）。`TargetDataSourceConfig`（135〜148 行）が HikariCP に `minimumIdle 0`・`maximumPoolSize`・`connectionTimeout`（`connectTimeout` 既定 3 秒）を渡す。環境変数 `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE`・`…_POOL_IDLE_TIMEOUT` が `.env.example` 66〜67 行にある。書き込みのプールは `WritePool.maximumSize` 既定 5（別の設定）。
- 問い合わせの上限: `TargetDbProperties.QueryTimeout` の既定は `generate 20s`・`compare 5s`・`list 5s`・`detail 5s`・`write 5s`（81〜85 行）。一覧の件数とページは `ReadPurpose.LIST` の同じ上限を共有する。
- 方言: 読みの SQL は `RowDialect` の `MysqlFamilyRowDialect`（MySQL と MariaDB）と `PostgresRowDialect`。MySQL と MariaDB で別の最適化をするには、方言のクラスの切り方（現状は同じクラス）の見直しが要る（見立て）。
- 索引の案内: `businessdata` の `web`/`domain` に索引を案内する部品は見当たらない（クラス一覧のみ。未確認）。DSL 側に索引の情報があるかは未確認。
- perf: `perf/k6/scenarios.js` 152〜165 行に biz の場面の一覧（`bizSearchFirst`・`bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted`・`bizDetail`・`bizSearchWide` など）。種 `perf/biz/make-biz-seed.sh`（`biz_item` 100 カラム × 既定 100,000 行、`c_001` に索引なし）、DSL `perf/make-biz-dsl.mjs`。使い捨ての環境 `docker/perf/compose.yaml`: app `mem_limit` 2g、MySQL 768m（既定。Intent 261016 の実績では 2688m まで上げた記録）、MariaDB 512m、PostgreSQL 512m、InnoDB のバッファープール 128M（既定）。読み取りのプールの値を負荷の試験で変えるには、app の `env_file`（`MASTERSMITH_PERF_ENV_FILE`）に `MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE` を足す（確認: compose 側に直接の指定は無い）。

### 仮説（未検証）
- MySQL・MariaDB の Not Met は、部分一致の `COUNT(*)` が表の全部を読む1回目と、ページの文の2回目で、読み取りのプール 5 に 10 VU が集まり借りる待ち 3 秒を超えるため（project.md の Interpretations の記録を要約したもの。今回は再測定していない）。
- Spring Boot 4.1.2 に上げると Jackson の管理の版が 3.1.7 以上になり、`libs.versions.toml` の `jackson` の上書きと `dependabot.yml` の Jackson の `ignore` が不要になりうる。逆に 3.1.7 未満なら上書きを残すか下げる判断になる。
- 4.1.2 の OpenTelemetry が 1.62 より新しければ、`opentelemetry-logback-appender` の固定（2.28.1-alpha）を新しい版に合わせられる見込み。ただし外部エクスポートの有効時の失敗は `./gradlew verify` では現れないため、有効にした手元の確かめが要る。
- 4.1.2 の BOM と release notes は読んでいない（ソースにも無い）。上記3点は、上げた後に `./gradlew dependencies` と lockfile の差で確かめる。

## Handoff Summary
- **Intent-relevant finding**: 第2の束の中心は `RowSqlExecutor.search`（件数の `COUNT(*)` とページの `LIMIT/OFFSET` の2回の問い合わせ、同じ接続）と `RowSqlBuilder.count`/`page`/`where`（`LIKE … ESCAPE`）、読み取りのプール（`TargetDbProperties.Pool.maximumSize` 既定 5、`TargetDataSourceConfig`）、応答 `RowPageResponse.total`（long）と画面 `BusinessDataListPage.tsx`/`BusinessDataTable.tsx` の件数表示。固定している部品は Spring Boot 4.1.1・opentelemetry-logback-appender 2.28.1-alpha・Jackson 3.1.7（platform の上書き）・networknt 3.0.6・Tomcat 11.0.26 で、`.github/dependabot.yml` の3つの `ignore` の「外す時期」が Spring Boot を上げるときになっている。
- **Risks / follow-up**: (1) 4.1.2 の BOM・OpenTelemetry・Jackson の版は未確認なので、上げた後の lockfile の差で確かめ、appender は外部エクスポートを有効にして確かめる。(2) 「多数」と出す案は API の型・画面・テスト・perf の判定に波及する。(3) 依頼の「perf/README.md (c) の grep の形」の該当行は未特定（前の Intent の `feedback-loop.md` と突き合わせる）。(4) vendor の lockfile の `hasInstallScript` は1件あり、固定先を上げるときに増減を team.md の手順で確かめる。(5) 今回の Full rescan で深く読んだのは上の範囲だけで、それ以外は流し読み（Scope of Analysis は partial にし `./` を入れないこと）。
