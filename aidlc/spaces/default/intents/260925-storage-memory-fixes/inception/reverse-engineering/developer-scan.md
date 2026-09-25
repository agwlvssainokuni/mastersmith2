# 開発担当のコードスキャン（Intent 260925-storage-memory-fixes）

- 対象: リポジトリ全体（`./`、依頼者の選択は Full rescan）。深さは Minimal のため、深く読んだのは依頼の4件に関わる箇所だけで、ほかは流し読みである（下の Scan Coverage で分ける）。
- 確かめ方: ファイルの読み取り（Read・grep・ls・`git ls-files`・`git submodule status`・`git log`）だけ。Gradle・npm・Docker のコマンドは実行していない。件数はファイルを数えた値で、テストの実行の件数ではない。
- `.env`・`.env.targetdb` と鍵ファイルは開いていない（`.env.example` は該当の行だけを読んだ）。`reference/` は対象の外。
- 前の Intent の記録（`aidlc/spaces/default/intents/260924-followup-fixes/` の Build and Test の結果）は、依頼の数字の出どころを確かめるためにだけ読んだ。コードの範囲には数えない。

## Developer Code Scan Results

### Scan Coverage

- **Analyzed deeply**:
  - `backend/src/main/resources/application.yaml`（内部DB の接続先 `DEFRAG_ALWAYS=TRUE`・Hikari・DSL の上限の設定）
  - `backend/src/main/resources/db/migration/V5__u4_dsl_management.sql`
  - `backend/src/main/resources/db/migration/V6__u4_dsl_audit_columns.sql`
  - `backend/src/main/java/cherry/mastersmith/dslmanage/repository/DslPreviewRepository.java`
  - `backend/src/main/java/cherry/mastersmith/dslmanage/repository/DslAppliedRevisionRepository.java`
  - `backend/src/main/java/cherry/mastersmith/dslmanage/service/DslRecordStore.java`
  - `backend/src/main/java/cherry/mastersmith/dslmanage/service/DslLifecycle.java`
  - `backend/src/main/java/cherry/mastersmith/dslmanage/service/DslPreviewCache.java`
  - `backend/src/main/java/cherry/mastersmith/dslmanage/domain/DslContent.java`
  - `backend/src/main/java/cherry/mastersmith/dslmanage/domain/DslPreviewRecord.java`
  - `backend/src/main/java/cherry/mastersmith/dslmanage/domain/DslAppliedRevisionRecord.java`
  - `backend/src/main/java/cherry/mastersmith/dslmanage/web/DslAdminController.java`
  - `backend/src/main/java/cherry/mastersmith/dslmanage/web/DslHeavyOperationGate.java`
  - `backend/src/main/java/cherry/mastersmith/dslmanage/web/DslWebConfig.java`
  - `backend/src/main/java/cherry/mastersmith/common/web/RequestSizeLimitFilter.java`
  - `backend/src/main/java/cherry/mastersmith/dsl/service/DefaultDslReader.java`
  - `backend/src/main/java/cherry/mastersmith/dsl/service/ActiveDslModelStore.java`
  - `backend/src/main/java/cherry/mastersmith/dsl/parse/SafeYamlParser.java`
  - `backend/src/main/java/cherry/mastersmith/dsl/parse/PositionMap.java`
  - `backend/src/test/java/cherry/mastersmith/auth/web/AccessTokenApiIT.java`
  - `backend/src/test/java/cherry/mastersmith/auth/testsupport/AuthApi.java`
  - `backend/src/test/java/cherry/mastersmith/common/testsupport/HttpTestClient.java`
  - `backend/src/test/java/cherry/mastersmith/common/testsupport/TestDatabase.java`
  - `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/SilentServer.java`
  - `backend/build.gradle.kts`（依存・テストのタスク・`integrationTest` の定義。20〜160 行）
  - `gradle/libs.versions.toml`・`backend/gradle.lockfile`（H2 の版の行）
  - `Dockerfile`
  - `perf/dsl-timing.sh`（1〜80 行と `--storage` の該当の行）
- **Skimmed only**:
  - `backend/src/main/java/cherry/mastersmith/`（上の深いファイル以外のすべて。`dsl/parse/YamlTreeConverter.java`・`dsl/validate/DslSchemaValidator.java` は grep で該当の行だけ）
  - `backend/src/main/resources/db/migration/`（V1〜V4）・`backend/src/main/resources/dsl/`・`backend/src/main/resources/logback-spring.xml`
  - `backend/src/test/`（上の深いファイル以外。`config/H2DefragOnCloseTest.java` は先頭の説明だけ、`targetdb/testsupport/TargetDbTestDatabase.java` は一部）
  - `backend/config/spotbugs-exclude.xml`
  - `compose.yaml`・`docker/perf/compose.yaml`・`.env.example`（メモリ・CPU・JVM・内部DB の接続先の行だけを grep）
  - `perf/k6/scenarios.js`（`dslMixed` の該当の行だけ）・`perf/README.md`（`--storage` と 1g・2g の行）・`perf/` の残り
  - `docker/`（`perf/compose.yaml` 以外）
  - `README.md`（見出しの一覧と、200〜238 行・312〜327 行・248 行・452 行）
  - `build.gradle.kts`（ルート。タスクの登録の行だけ）・`settings.gradle.kts`
  - `.github/workflows/ci.yml`・`.github/dependabot.yml`
  - `frontend/`（`package.json` の依存とスクリプトの名前だけ。`src/`・`e2e/`・設定は件数と一覧だけ）
  - `vendor/make-you-chic-ui/`（サブモジュール。固定先の確認だけで中身は読んでいない）
  - `.gitignore`・`.gitleaks.toml`・`.pre-commit-config.yaml`・`.editorconfig`・`.gitattributes`・`.dockerignore`・`config/`

### Packages Found

- `backend`（`cherry.mastersmith`）— Gradle のサブプロジェクト・実行可能 WAR — Java 25 — アプリの本体。main の Java は 279 ファイル。機能ごとのパッケージは次のとおり（括弧内はファイル数）。
  - `auth`（domain 19・service 15・repository 3・web 12）— ログイン・アクセストークン・リフレッシュトークン・ロック
  - `user`（domain 5・service 10・repository 2）— 利用者・初期管理者
  - `access`（domain 7・service 3・web 9）— 管理者だけの API の決まり、401・403
  - `audit`（domain 7・service 4・repository 2）— 監査イベントの記録（内部DB）
  - `targetdb`（config 5・domain 12・repository 5・service 3）— 対象DB（MySQL・MariaDB・PostgreSQL）のスキーマの読み取り
  - `dsl`（domain 30・parse 10・validate 5・service 7）— DSL（YAML）の安全な読み込み・JSON Schema と意味の検証・適用中のモデルの保持
  - `dslmanage`（domain 15・generate 8・service 16・repository 3・web 10）— DSL の生成・投入・プレビュー・適用・履歴・ダウンロード
  - `common`（error・health・i18n・observability・security・web）— 共通の部品
  - `config`（6）— Spring Security・画面の配信・外部エクスポートなどの全体の設定
- `frontend` — npm のパッケージ（Vite） — TypeScript・React 19 — 画面（SPA）。ソース 60・テスト 47 ファイル。`src/app`（枠・ルーティング・i18n）、`src/features/{auth,admin,dsl}`、`src/shared/api-client`。E2E は `frontend/e2e/` の 4 ファイル（Playwright）。
- `vendor/make-you-chic-ui` — Git サブモジュール（固定先 `edb1f943c0e66293494fa974605f34fcd7e258d7`） — React + TypeScript — デザインシステム。読み取りの対象外。
- `perf/` — シェル・Node.js のスクリプト・k6 — 負荷と時間の試験（使い捨ての環境 `docker/perf/compose.yaml`）。
- `docker/` — compose の補助（監視・OTLP の受け手・対象DB の見本のスキーマ・負荷の試験の compose・`check-container-limits.sh`）。

### Build System

- **Type**: Gradle（Kotlin DSL）。ルートの `verify` タスクが1コマンドの検査の入口で、npm（画面・サブモジュールのビルド）・Gitleaks・OSV-Scanner もルートの Gradle から呼ぶ。バックエンドの成果物は画面の `dist` を同梱した実行可能 WAR（`backend/build/libs/mastersmith.war`）。
- **Config Files**: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`gradle/libs.versions.toml`・`gradle/wrapper/gradle-wrapper.properties`・`backend/gradle.lockfile`・`settings-gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json`・`Dockerfile`・`compose.yaml`・`docker/perf/compose.yaml`
- **Build Dependencies**:
  - `backend:bootWar` → ルートの `frontendBuild`（`frontend/dist` を同梱） → `vendorBuild`（`vendor/make-you-chic-ui` を npm でビルド）
  - `verify` → フォーマット・リンタ・ライセンスヘッダー・型検査・`backend:test`（`*Test`）・`backend:integrationTest`（`*IT`）・JaCoCo の下限・画面のテストとカバレッジ・Gitleaks・SpotBugs・OSV-Scanner
  - `Dockerfile` はイメージの中でビルドせず、作った WAR を複写するだけ
  - 依存は lockfile で固定（`dependencyLocking { lockAllConfigurations() }`・`npm ci`）。組み込みの Tomcat は `libs.versions.tomcat`（11.0.26）にそろえる

### APIs Discovered

- REST（Spring MVC）— `dslmanage/web/DslAdminController.java` — 10（`/api/admin/dsl` の今の状態・プレビューの表示と投入と破棄・生成・プレビューのダウンロード・適用・履歴・履歴からの戻し・適用中のダウンロード）。重い4つ（表示・投入・生成・戻し）は `DslHeavyOperationGate`（同時に1件、取れなければ `DSL_BUSY`）を通る
- REST — `auth/web/AuthController.java` — 4（ログイン・トークンの更新・ログアウトなど）
- REST — `access/web/AdminCheckController.java` — 1（管理者の確認）
- REST — `common/error/web/ErrorPathController.java`・`ProblemTypeController.java` — 3（エラーの経路と Problem Details の type の説明）
- Actuator — `application.yaml` の `management.*` — `/actuator/health` だけを公開（状態だけ）
- 静的なファイル — `/dsl/dsl-schema-v1.json`（JSON Schema の公開）と画面（SPA）

### Frameworks & Libraries

- Java — 25（`gradle/libs.versions.toml`、実行は `eclipse-temurin:25.0.4_7-jre-noble`）— 言語と実行環境
- Spring Boot — 4.1.1 — Web MVC・Security（OAuth2 Resource Server で JWT）・Data JPA（Hibernate）・Flyway・Validation・AspectJ・Actuator・OpenTelemetry
- 組み込みの Tomcat — 11.0.26（Spring Boot の管理する版から上書き）— サーブレットコンテナ
- H2 — 2.4.240（`backend/gradle.lockfile`）— 内部DB（組み込み・ファイル保存、MVStore）
- HikariCP — Spring Boot の管理する版 — 内部DB の接続プール（上限 30、`minimum-idle` は未指定）
- Flyway — Spring Boot の管理する版 — 内部DB のスキーマの変更（V1〜V6、前進のみ）
- SnakeYAML — 2.6 — DSL（YAML）の安全な読み込み（`Composer` で節の木を作る）
- networknt json-schema-validator — 3.0.6 — DSL の JSON Schema（2020-12）の検証
- Jackson — 3 系（`tools.jackson`、Spring Boot の管理する版）— JSON と、YAML の節の木から作る JSON の木
- logstash-logback-encoder — 9.0 — 構造化ログ（1件1行の JSON）
- opentelemetry-logback-appender — 2.28.1-alpha — 外部エクスポートのログの送信（既定で無効）
- MySQL Connector/J・MariaDB Connector/J・PostgreSQL JDBC — Spring Boot の管理する版 — 対象DB の読み取り
- JUnit 5・AssertJ・jqwik 1.10.1・ArchUnit 1.5.0・Testcontainers（MySQL・MariaDB・PostgreSQL）— バックエンドのテスト
- Spotless（palantir-java-format 2.98.0）・SpotBugs 4.10.4＋FindSecBugs 1.14.0・JaCoCo 0.8.15 — 品質の検査
- React 19.2・react-router 8.3・i18next 26・Vite 8.2・TypeScript 6.0 — 画面
- Vitest 4.1・Testing Library・user-event・vitest-axe・fast-check 4.10・Playwright 1.63 — 画面のテスト
- Prettier 3.9・oxlint 1.78・ESLint 10.8（react-hooks）・Stylelint 17.14 — 画面の検査

### Test Coverage

- **Test Directories**: `backend/src/test/java/`（`*Test` 100 ファイル・`*IT` 69 ファイル、対象と同じパッケージの構成）、`backend/src/test/resources/`、`frontend/src/`（対象と同じ場所の `*.test.ts(x)` 47 ファイル）、`frontend/e2e/`（4 ファイル）
- **Test Frameworks**: JUnit 5（Spring Boot Test・`@SpringBootTest` は 45 クラス、うち `RANDOM_PORT` は 35 クラス、`@DirtiesContext` は 0）、jqwik、ArchUnit、Testcontainers、Vitest＋Testing Library（jsdom）、vitest-axe、fast-check、Playwright
- **Coverage Config**: present。JaCoCo の全体の下限（行 80%・分岐 70%）とパッケージごとの下限（新しいパッケージだけ。既存の 22 パッケージは一覧で外して全体で判定）が `backend/build.gradle.kts` の `jacocoTestCoverageVerification` にある。画面は `@vitest/coverage-v8` の `thresholds`（`frontend/vitest.config.ts`）
- テストの JVM の設定（`backend/build.gradle.kts` 111〜128 行）: すべての `Test` タスクで最大ヒープ `1g`、`-XX:+EnableDynamicAgentLoading -Xshare:off`、`jdk.httpclient.allowRestrictedHeaders=host`。`integrationTest` は1つの JVM で、並列のフォーク（`maxParallelForks`）の指定は無い
- 今回の依頼に関わる既存のテスト: `config/H2DefragOnCloseTest`（止めるときの詰め直し）、`dslmanage/repository/DslManageRepositoryIT`、`dslmanage/web/DslAdminApiIT`・`DslConcurrencyIT`、`dslmanage/service/DslStartupIT`、`auth/web/AccessTokenApiIT`

### Code Quality Indicators

- **Linting**: Java は Spotless（palantir-java-format、`licenseHeader`）と SpotBugs＋FindSecBugs（`backend/config/spotbugs-exclude.xml`、`SQL_` で始まる指摘は priority にかかわらず止める）と ArchUnit の層の検査。画面は `frontend/.prettierrc.json`・`frontend/.oxlintrc.json`・`frontend/eslint.config.js`・`frontend/.stylelintrc.json`・`frontend/tsconfig.json`、ライセンスヘッダーは `frontend/scripts/check-license-header.mjs`
- **CI/CD**: `.github/workflows/ci.yml`（`develop` へのプッシュで `./gradlew verify`、WAR を成果物として保存し、リリースに添付）、`.github/dependabot.yml`、`.pre-commit-config.yaml`（Gitleaks とフォーマットの検査）
- **Documentation**: `README.md`（約 86KB、起動・環境変数・既知の制約・戻し方）と `perf/README.md`。Javadoc・コメントは日本語で、決まりの番号（BR・NFR）と理由を書く形がそろっている。TODO・FIXME・HACK は `backend/src`・`frontend/src` に 0 件。警告の抑止は2か所（`common/observability/SanitizingLogRecordExporter.java` 141 行・`common/error/web/DefaultErrorResponseWriter.java` 79 行）

### Technical Debt Signals

- 動いている間の内部DB のファイルの伸び（依頼の1件目）: `README.md` 452 行が既知の制約として記録している（10MB の DSL の投入と適用で1回あたり約 10.8MB、21 回で約 278MB、40 回で約 483MB、頭打ちにならない。止めて起動し直すと `DEFRAG_ALWAYS=TRUE` で約 16MB）。今の対処は止めるときの詰め直し（`application.yaml` 137 行）だけで、動いている間に空いた場所を再利用させる仕組みは無い
- 本文の複写が多い（依頼の2件目）: `DslContent`（`dslmanage/domain/DslContent.java`）は作るときと `yamlBytes()` のたびに 10MB の本文を `clone()` する。`DslPreviewRepository.findContent()`・`DslAppliedRevisionRepository.detach()` は Hibernate が読んだ配列から `DslContent` を作り、ダウンロード（`DslAdminController.download`）や戻し（`DslLifecycle.restore`）で `yamlBytes()` がもう一度複写する
- 読み込みの途中の形が多い（依頼の2件目）: `SafeYamlParser.decode` はバイト列から `CharBuffer` と `String` を作り、SnakeYAML の `Composer` が文書全体の節の木（位置の `Mark` 付き）を作り、`YamlTreeConverter` が Jackson の木と、節ごとに JSON Pointer の文字列を鍵にした `PositionMap`（`HashMap`）を作る。これらが1回の投入の間は同時に生きる
- 大きなモデルを2つ持ち続ける: 適用中のモデル（`dsl/service/ActiveDslModelStore`）と、プレビューのモデル（`dslmanage/service/DslPreviewCache`）。どちらも 10MB の DSL なら大きい
- `DslLifecycle` は 462 行で、生成・投入・戻し・表示・破棄・適用・状態・履歴・ダウンロードを1つのクラスが持つ（責務はそろっているが大きい）
- 結合テストの基盤: `HttpTestClient` は `HttpClient.newBuilder()` の既定（HTTP/2 を試す）で `localhost` に送る。Spring の文脈のキャッシュの上限は既定（32）のままで、`RANDOM_PORT` の文脈がそれぞれ組み込みの Tomcat と Hikari のプール（上限 30、`minimum-idle` が未指定のため既定で上限と同じ数を保とうとする）を持ち続ける
- `perf/dsl-timing.sh` 33 行の説明が「要件の条件は 1g」のまま（55 行の既定の値と `perf/README.md` 109 行は 2g。依頼の4件目）

## Handoff Summary

- **Intent-relevant finding**:
  - (1) 内部DB のファイルの伸び: 本文は `dsl_previews.yaml_bytes`・`dsl_applied_revisions.yaml_bytes` の `BINARY LARGE OBJECT`（`V5__u4_dsl_management.sql` 26〜51 行）。投入は固定の鍵の1行への `MERGE`（`DslPreviewRepository` の `PLACE_SQL`）で毎回新しい本文を書き、適用は `INSERT ... SELECT` でプレビューの本文を履歴へ写し（`DslAppliedRevisionRepository` の `COPY_SQL`）、同じトランザクションでプレビューの行を消し、21 件目以降の古い履歴を消す（`DslRecordStore.apply`、`deleteOlderThanNewest`）。1回の投入と適用で、本文が少なくとも2回（置くときと写すとき）書かれる。H2 は 2.4.240（MVStore）で、接続先は `jdbc:h2:file:./data/mastersmith;DEFRAG_ALWAYS=TRUE`（`application.yaml` 137 行）。動いている間に空いた場所が再利用されない理由（MVStore の古いチャンクの保持や詰め直しの条件、LOB の消し方）は、コードからは確かめられない（仮説）。直し方の候補（どれも未検証）: 本文を写さずに参照を持たせる表の形（本文を1か所に置き、プレビューと履歴は識別で指す）、本文の圧縮、本文を DB の外のファイルに置く、H2 の設定での動いている間の詰め直し。表の形を変えるときは Flyway の V7 以降で前進のみ、かつ1つ前の版のアプリが動く後方互換（team.md の Deployment）を守る必要がある。
  - (2) 10MB の DSL の投入とログインの重ねのメモリ: 1回の投入で同時に生きるものは、要求の本文の `byte[]`（`DslAdminController.submit` の `@RequestBody byte[]`。`RequestSizeLimitFilter` は `Content-Length` があれば本文を読み込まず、チャンクのときだけ上限まで `ByteArrayOutputStream` に読む）、`CharBuffer` と `String`（`SafeYamlParser.decode`）、SnakeYAML の節の木、Jackson の木と `PositionMap`、検証の途中のもの、`DslModel`。さらに適用中とプレビューの2つのモデルを持ち続ける（`ActiveDslModelStore`・`DslPreviewCache`）。`dslMixed`（`perf/k6/scenarios.js`）の重い側は 10MB の投入とプレビューの表示（照合）を繰り返す。JVM は `-XX:MaxRAMPercentage=75.0`（`Dockerfile` の `ENTRYPOINT`）で、上限 2g（`compose.yaml` 63 行の既定）なら最大ヒープは約 1.5GiB。前の Intent の測定（`anon` 1,894MiB・上限の 93%）は、ヒープが最大近くまで広がった分とヒープ以外の分の合計と読めるが、内訳は測っていない（仮説）。`memory.events` の `max`（2,340 回）はコンテナの上限に当たって回収が起きた回数で、ページキャッシュ（H2 のファイルの書き込みで増える）の回収も含むため、プロセスのメモリが足りないことと同じとは限らない（仮説）。内訳は GC のログ・NMT・ヒープの内訳で分けて確かめる必要がある。
  - (3) `AccessTokenApiIT` の一時的な失敗: テストは `RANDOM_PORT` で起動したアプリに `HttpTestClient`（JDK の `HttpClient`、接続の待ち 5 秒・要求の待ち 30 秒、`http://localhost:<port>`）で送る。`@BeforeEach` のたびに新しい `AuthApi`（新しい `HttpClient`）を作るため、テストをまたいだ古い接続の再利用は起きない。前の Intent の記録では、6 件すべてが 0.587 秒のうちに `Connection reset`・`header parser received no bytes` で落ち、同じ時刻に Gradle の作業プロセスとの接続も時間切れで、クラスだけの再実行 3 回と verify の2回目は通った。原因の候補（どれも未検証）: (a) PC の負荷による一時的な失敗、(b) 空いた番号の衝突（Tomcat は全アドレスで待ち受け、`localhost` への要求は 127.0.0.1 へ行く。colima が Testcontainers のコンテナの番号を PC の 127.0.0.1 に転送していると、同じ番号を先に特定のアドレスで取った相手に要求が届き、すぐに切られうる。Gradle の作業プロセスの接続も同じ loopback の番号を使う）、(c) 1つの JVM（最大ヒープ 1g）にキャッシュされた多くの文脈（Tomcat と、それぞれ上限 30 の Hikari のプール）の資源の使い過ぎ。1回目の実行のテストの報告（`backend/build/test-results/`）は、その後の実行で上書きされている見込みで、原因を後から確かめる材料は残っていない可能性が高い。
  - (4) `perf/dsl-timing.sh` 33 行: 「（既定 2g。配備と同じ値。要件の条件は 1g）」。既定の値（55 行 `MASTERSMITH_CONTAINER_MEMORY:-2g`）と `perf/README.md` 109 行（条件は 2g とした経緯）はすでに 2g で、直すのはこの説明の文だけ。
- **Risks / follow-up**:
  - 保存の形を変える直しは、スキーマの変更（前進のみ・1つ前の版が動く後方互換）と、戻し（`README.md` の「戻し方」、前の版のイメージへの戻し）の両方に影響する。1つ前の版のアプリは `yaml_bytes` の列を読むため、列を消す・中身の形（圧縮など）を変えると戻せなくなる。要件の段で、後方互換をどう守るかを決める必要がある。
  - 既存の決まりを守る必要がある: 投入は検証を通ってから保存する（BR1.2・BR1.5）、本文は受け取ったバイト列のまま保存し、識別（SHA-256）とダウンロードがバイト単位で一致する（ADR-003、`V5` の説明）、適用は1つのトランザクションで履歴への追加・プレビューの削除・古い履歴の削除を行う（BR4.2・BR4.3）、確定の後だけモデルの差し替えと監査の出来事を行う（BR4.6・BR7.3）、ログに本文を出さない（NFR1.17）、プレビューと履歴 20 件で最大約 210MB（`V5` の説明）。
  - 読み込みのメモリの直しは、DSL を信頼できない入力として扱う決まり（大きさ・深さ・別名の上限、タグの拒否、重複キー、位置つきの誤り）を弱めてはいけない。`SafeYamlParser` の `LimitingParser` と `PositionMap`（誤りの行と列）はこの決まりのためにあるため、節の木を作らない読み方に変えるときも、誤りの位置と上限の検査を保つ必要がある。
  - メモリの判定は、`anon` と `file` を分けて記録している既存の手順（`perf/dsl-timing.sh` の `storage_row`、`perf/README.md`）に合わせ、前の Intent と同じ `dslMixed` の条件で直す前と直した後を比べるのがよい（project.md の Testing Posture の学び: 直す前の設定も同じ環境で流して前提を裏付ける、`memory.peak` の比較の前にコンテナを作り直す、長い試験は `caffeinate -i`）。
  - `AccessTokenApiIT` は原因が分からないまま「直した」とすることができない（team.md の「不安定なテストは原因を直すまで統合しない」）。再現の手段（負荷を掛けた状態での繰り返しの実行、待ち受けの番号の確認など）と、再現できなかったときの扱いを要件の段で決める必要がある。候補 (b) を確かめるなら、テストのアプリを `127.0.0.1` だけで待ち受けさせる・送り先を `127.0.0.1` にするなどの切り分けが考えられる（未検証）。
  - 前のコード知識ベース（`aidlc/spaces/default/codekb/mastersmith2/`、Intent `260924-followup-fixes` の partial）の深い範囲（`LoginService`・`AuditEventListener`・`ObservabilityConfig` など）は、今回は流し読みにした。今回の深い範囲は `dslmanage` の保存・`dsl` の読み込み・内部DB の設定・`AccessTokenApiIT` と結合テストの補助・`Dockerfile`・`perf/dsl-timing.sh` に移るため、範囲の比較では前回と重ならない部品が出る見込み。
