## Developer Code Scan Results

Intent `260924-followup-fixes`（scope: bugfix）の Reverse Engineering で、開発担当が行ったコードの調査の結果。調べる広さは Full rescan（スナップショットの paths は `./`）、深さは Minimal。修正案は作らず、事実だけを記録する。`aidlc/`・`.claude/`（ワークフローの枠組み）と `reference/`（Git 管理外の参考資料）は対象から外した。`.env` は開いていない。コマンドは読み取り（ファイルの一覧・内容の表示・文字列の検索）だけを使い、`git`・`./gradlew`・`npm`・`docker` は実行していない。

### Scan Coverage

- **Analyzed deeply**（実際に読んで理解したもの。「一部」と書いたものは、示した範囲だけを読んだ）:
  - `settings.gradle.kts`
  - `build.gradle.kts`
  - `backend/build.gradle.kts`
  - `gradle/libs.versions.toml`
  - `gradle/wrapper/gradle-wrapper.properties`
  - `backend/gradle.lockfile`（主な部品の版の行だけ）
  - `backend/src/main/resources/application.yaml`
  - `backend/src/main/resources/logback-spring.xml`
  - `backend/src/main/resources/db/migration/V3__u2_authentication.sql`（一部: `login_attempt_states` の定義とダミーの行）
  - `backend/src/main/java/cherry/mastersmith/config/ObservabilityConfig.java`
  - `backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java`
  - `backend/src/main/java/cherry/mastersmith/auth/service/LoginAttemptStateInitializer.java`
  - `backend/src/main/java/cherry/mastersmith/auth/repository/LoginAttemptStateRepository.java`
  - `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java`（一部: 記録と失敗のログの部分、100〜175 行）
  - `backend/src/main/java/cherry/mastersmith/dslmanage/service/DslOperationMetrics.java`（一部: `record`）
  - `backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java`（一部: `createIfNeeded`）
  - `backend/src/test/java/cherry/mastersmith/common/observability/ExternalExportIT.java`（一部: 外部エクスポートの確かめの部分）
  - `compose.yaml`
  - `Dockerfile`
  - `.env.example`
  - `.dockerignore`
  - `.gitmodules`
  - `.github/workflows/ci.yml`
  - `docker/perf/compose.yaml`
  - `docker/otel-collector/config.yaml`
  - `perf/k6/scenarios.js`
  - `frontend/package.json`
  - `frontend/vite.config.ts`
  - `frontend/vitest.config.ts`
  - `frontend/src/features/dsl/DslConfirmDialog.tsx`
- **Skimmed only**（ディレクトリの単位で、ファイルの一覧・パッケージの説明・検索の結果だけを見たもの）:
  - `backend/src/main/java/cherry/mastersmith/`（上に挙げたファイル以外のすべて: `access/`・`audit/`・`auth/`・`common/`・`config/`・`dsl/`・`dslmanage/`・`targetdb/`・`user/`。パッケージの説明、エンドポイントの一覧、キーと値のログの呼び出しの検索、大きなファイルの一覧だけ）
  - `backend/src/main/resources/db/migration/`（V3 の上記以外）・`backend/src/main/resources/dsl/`
  - `backend/src/test/`（テストの置き場・件数・名前、`LoginAttemptStateRepositoryIT`・`LoginServiceTest`・`LoginConcurrencyIT` の `@DisplayName` だけ）
  - `backend/config/spotbugs-exclude.xml`
  - `frontend/src/`（上に挙げたファイル以外。make-you-chic-ui の読み込みと `Modal`・`Alert` の使用箇所の検索だけ）
  - `frontend/e2e/`・`frontend/scripts/`・`frontend/` のリンタ・フォーマッタの設定
  - `vendor/make-you-chic-ui/`（外部の部品。固定先のコミット、公開部品の一覧、`Modal.tsx`・`Alert.tsx` の Props と閉じるボタンの行だけ）
  - `docker/monitoring/`・`docker/targetdb/`・`docker/check-container-limits.sh`
  - `perf/README.md`・`perf/dsl-timing.sh`（メモリの上限と試験用の利用者の行だけ）・`perf/` の残り
  - `README.md`（節の見出しと、検索で当たった行だけ）
  - `.github/dependabot.yml`・`.pre-commit-config.yaml`・`.gitleaks.toml`・`.gitignore`・`.editorconfig`・`.gitattributes`・`config/`
- **読んでいないもの**: `.env`（秘密情報）、`reference/`、`aidlc/`・`.claude/`（ワークフローの記録は、前の Intent の後に回した修正の出どころ（`feedback-loop.md` など）を確かめるためだけに参照した）、`frontend/coverage/`・`frontend/playwright-report/`・`frontend/test-results/`・`build/`・`.idea/`（生成物・道具の設定）。
- **部品（前回のコード知識ベースの ID に合わせる）**:
  - 深く読んだ部分を含む: `config`・`auth`・`audit`・`container-runtime`・`perf-and-monitoring`・`build-and-verify`・`frontend-feature-dsl`（`DslConfirmDialog` だけ）
  - 流し読み: `app-bootstrap`・`common-error`・`common-security`・`common-web`・`common-health`・`common-i18n`・`common-observability`・`access`・`user`・`targetdb`・`dsl`・`dslmanage`・`frontend-app-core`・`frontend-registry`・`frontend-app-layout-i18n`・`frontend-api-client`・`frontend-feature-auth`・`frontend-feature-admin`・`make-you-chic-ui`
  - 新しい ID（前回のコード知識ベースの後に Intent `260923-dsl-schema-loader` で作られた部品）: `targetdb`（対象DB）・`dsl`（DSL の定義）・`dslmanage`（DSL の生成と管理）・`frontend-feature-dsl`（DSL の管理画面 `frontend/src/features/dsl/`）

### Packages Found

- `mastersmith`（ルート）— Gradle のルートプロジェクト — Kotlin DSL — 1コマンドの検査 `verify`、npm・Gitleaks・OSV-Scanner の呼び出し、E2E のタスク
- `backend` — Gradle のサブプロジェクト（実行可能 WAR） — Java 25 — Spring Boot のアプリ。ルートパッケージ `cherry.mastersmith`、main 277 ファイル
  - `access`（web・service・domain）— 管理者の API の認可と 401・403 の応答、アクセス拒否の出来事
  - `audit`（service・repository・domain）— 監査イベントの追記（確定の後、2本目の接続）
  - `auth`（web・service・repository・domain）— ログイン・トークン・ロック・ログアウト
  - `common`（error・health・i18n・observability・security・web）— 共通部品
  - `config` — Security・Web・転送元ヘッダー・外部エクスポートの組み立て
  - `dsl`（domain・parse・service・validate）— DSL の書式・安全な読み込み・検証・適用中のモデル
  - `dslmanage`（domain・generate・repository・service・web）— 既定の DSL の生成、投入・プレビュー・適用・履歴
  - `targetdb`（config・domain・repository・service）— 対象DB の接続とスキーマの読み取り
  - `user`（service・repository・domain）— 利用者と照合、初期管理者の作成
- `mastersmith-frontend`（`frontend/`）— npm — TypeScript（React 19）— 画面。`app/`（起動・登録・振り分け・レイアウト・表示言語）、`features/`（`auth`・`admin`・`dsl`）、`shared/api-client/`
- `make-you-chic-ui`（`vendor/make-you-chic-ui/packages/make-you-chic-ui`）— npm（`file:` 参照）— TypeScript — 外部のデザインシステム（Git サブモジュール）
- `perf/`・`docker/` — 台本と設定 — JavaScript（k6）・シェル・YAML — 負荷の試験、使い捨ての環境、手元の監視、見本の対象DB

### Build System

- **Type**: Gradle 9.7.1（Kotlin DSL、依存は Maven Central だけ、`backend/gradle.lockfile` で固定）＋ npm（Node.js 24、`package-lock.json` と `npm ci`）
- **Config Files**: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`settings-gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json`・`frontend/vite.config.ts`・`frontend/vitest.config.ts`・`frontend/playwright.config.ts`・`Dockerfile`・`compose.yaml`・`docker/perf/compose.yaml`
- **Build Dependencies**: `verify`（`build.gradle.kts` 311〜379 行）は段 0〜9（準備 → フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体テスト → 結合テスト → カバレッジ → 安全の検査 → 成果物）の順。`vendorBuild` → `frontendBuild` → `:backend:bootWar`（`frontend/dist` を `WEB-INF/classes/static` に同梱、`backend/build.gradle.kts` 338〜345 行）。`vendorUnchanged` がサブモジュールの変更の無さを確かめる（`build.gradle.kts` 111〜125 行）。`e2eTest` は `verify` と CI の外（381〜389 行）。イメージは WAR をコピーするだけの1段（`Dockerfile` 27 行、`.dockerignore` は WAR だけを通す）。

### APIs Discovered

- REST（Spring MVC）— `auth/web/AuthController.java` — 3本: `POST /api/auth/login`・`POST /api/auth/session/refresh`・`POST /api/auth/session/logout`
- REST — `access/web/AdminCheckController.java` — 1本: `GET /api/admin/check`
- REST — `dslmanage/web/DslAdminController.java`（パスは `DslAdminPaths.java`）— 10本: `GET /api/admin/dsl/status`・`GET/POST/DELETE /api/admin/dsl/preview`・`POST /api/admin/dsl/preview/generate`・`GET /api/admin/dsl/preview/download`・`POST /api/admin/dsl/apply`・`GET /api/admin/dsl/history`・`POST /api/admin/dsl/history/{revisionId}/restore`・`GET /api/admin/dsl/applied/download`
- REST — `common/error/web/ProblemTypeController.java`・`ErrorPathController.java` — 問題の種類の説明 `GET /api/problems/{slug}` と `/error`
- 静的 — `GET /dsl/dsl-schema-v1.json`（ビルドで `static/dsl/` へ複写、ログインなし）
- Actuator — `GET /actuator/health`（公開は health だけ、`application.yaml` 183〜206 行）
- 内部の提供口 — `dsl/service`（`DslReader`・`ActiveDslModelHolder`・`ActiveDslModelProvider`）、`targetdb/service`（`TargetSchemaReader`）、`user/service`（照合と利用者の要約）。境界は ArchUnit の `*BoundaryArchitectureTest` で守る。

### Frameworks & Libraries

- Java — 25（toolchain）— 言語
- Spring Boot — 4.1.1（Spring 7.0.9、Spring Security 7.1.1）— アプリの枠組み
- Tomcat（組み込み）— 11.0.26（脆弱性のため Boot の管理の版から引き上げ、`backend/build.gradle.kts` 46〜53 行）— サーブレットコンテナ
- Hibernate ORM — 7.4.5.Final — JPA（`ddl-auto: validate`）
- H2 — 2.4.240 — 内部DB（組み込み・ファイル、`DEFRAG_ALWAYS=TRUE`）
- HikariCP — 7.0.2 — 接続プール（内部DB 上限 既定 30）
- Flyway — 12.4.0 — スキーマの変更（V1〜V6）
- logback-classic — 1.5.38 ／ logstash-logback-encoder — 9.0 — 1行1件の JSON のログ
- OpenTelemetry API — 1.62.0 ／ opentelemetry-logback-appender-1.0 — 2.28.1-alpha（固定）— ログの OTLP の送信
- Micrometer Tracing — 1.7.1 — トレースID
- MySQL Connector/J 9.7.0・MariaDB Connector/J 3.5.10・PostgreSQL JDBC 42.7.13 — 対象DB の読み取り
- SnakeYAML — 2.6 ／ networknt json-schema-validator — 3.0.6 ／ Jackson — 3.1.5 — DSL の読み込みと検証
- JUnit 5・jqwik 1.10.1・ArchUnit 1.5.0・Testcontainers 2.0.5 — バックエンドのテスト
- React 19.2・react-router 8.3・i18next 26.4・react-i18next 17.0 — 画面
- Vite 8.2・TypeScript 6.0・Vitest 4.1・@vitest/coverage-v8・Testing Library・vitest-axe・fast-check 4.10・Playwright 1.63 — 画面のビルドとテスト
- make-you-chic-ui — 0.0.0（サブモジュールの checkout のコミット `5258c8bb987b0fa6ffd0ad7c4eadc7d4006da52d`。`.git/modules/vendor/make-you-chic-ui/HEAD` の値で、`git` を実行していないため親のリポジトリの固定先（gitlink）とは照合していない）— デザインシステム
- 実行環境 — `eclipse-temurin:25.0.4_7-jre-noble`、`otel/opentelemetry-collector:0.161.0`、`grafana/otel-lgtm:0.33.1`、見本の対象DB `postgres:18.6`・`mysql:8.4.11`・`mariadb:11.8.9`（ダイジェストで固定）

### Test Coverage

- **Test Directories**: `backend/src/test/java`（208 ファイル。`*Test` 99・`*IT` 68 ほか支援の型。対象と同じパッケージ構成）・`backend/src/test/resources`・`frontend/src/**/*.test.{ts,tsx}`（47 ファイル）・`frontend/e2e/`（4 本、`verify` と CI の外）
- **Test Frameworks**: JUnit 5・Spring Boot Test・jqwik・ArchUnit（8 クラス）・Testcontainers（MySQL・MariaDB・PostgreSQL、`targetdb/testsupport/TargetDbImages.java`）・Vitest＋Testing Library（jsdom）＋user-event＋vitest-axe・fast-check・Playwright
- **Coverage Config**: 有り。JaCoCo（`backend/build.gradle.kts` 155〜253 行。全体の合計 行 80%・分岐 70%、パッケージごとの下限は既存の 22 パッケージを一覧 `packagesJudgedByTotal` で外し新しいパッケージだけに当てる）。`@vitest/coverage-v8`（`frontend/vitest.config.ts` の `thresholds` 行 80・分岐 70）。
- 今回の7件に関わる既存のテスト:
  - ロックの状態: `auth/repository/LoginAttemptStateRepositoryIT`（`createIfAbsent` の冪等は1スレッドで確かめる、214〜230 行付近）、`auth/service/LoginServiceTest`（行が無いときの作成を模擬で確かめる、241〜250 行付近）、`auth/service/LoginConcurrencyIT`（行がある利用者の同時の失敗だけ）。行が無い利用者の同時の初めてのログインを確かめるテストは見当たらない。
  - 外部エクスポート: `common/observability/ExternalExportIT`（`/v1/logs` が届くことは確かめるが、秘密の値が入らないことを確かめるのは `/v1/traces` だけ、192〜202 行付近）。ログの秘密情報の確かめは `*SecretLeakIT`（auth・access・audit・targetdb）と `common/testsupport/LogEvents` で、標準出力側のログの出来事が対象。
  - 画面: `frontend/src/features/dsl/DslAdminPage.test.tsx` 535 行が `Alert` の閉じるボタンを名前「閉じる」で探す。

### Code Quality Indicators

- **Linting**: Java は Spotless（palantir-java-format 2.98.0、ライセンスヘッダー）と SpotBugs 4.10.4＋FindSecBugs 1.14.0（`spotbugsGate`、priority 1 と `SQL_` で失敗、`backend/build.gradle.kts` 288〜329 行）、ArchUnit。画面は Prettier・oxlint・ESLint（react-hooks）・Stylelint・`scripts/check-license-header.mjs`・`tsc --noEmit`。
- **CI/CD**: `.github/workflows/ci.yml`（`develop` へのプッシュと `v*` のタグで `./gradlew verify`、サブモジュールを取得、Gitleaks と OSV-Scanner は版と SHA-256 を固定）。`.github/dependabot.yml`（gradle・npm `/frontend`・github-actions・docker、毎週）。`.pre-commit-config.yaml`（gitleaks・Spotless・Prettier）。配備は手元の `docker compose` だけ。
- **Documentation**: `README.md`（約 600 行、道具・検査・起動・環境変数・対象DB・DSL・監視・監査・差し込み口・ライセンス）、`perf/README.md`、`frontend/src/features/README.md`。Java は Javadoc、TypeScript はファイル先頭の説明が日本語で丁寧。`TODO`・`FIXME`・`HACK` は見当たらない。抑止は `DefaultErrorResponseWriter.java` 79 行の `@SuppressWarnings("unchecked")` と `frontend/src/types/vitest-axe-matchers.d.ts` の oxlint の1件だけ。

### Technical Debt Signals

今回の Intent の7件に関わる事実（番号は Intent の説明の番号）:

1. **Loki でキーと値を絞り込めない** — `backend/src/main/java/cherry/mastersmith/config/ObservabilityConfig.java` 93〜101 行（`OtlpLogAppenderInstaller.afterPropertiesSet`）が `OpenTelemetryAppender` を作って名前・コンテキストを設定し `start()` するだけで、キーと値を属性として送る設定（`setCaptureKeyValuePairAttributes`）を呼んでいない。出力の取り付けは外部エクスポートを有効にしたときだけ（67〜71 行、`@ConditionalOnBooleanProperty("mastersmith.observability.export.enabled")`）。標準出力の JSON には `logback-spring.xml` 48 行の `<keyValuePairs/>` でキーと値が出る。OTLP の送り先は `application.yaml` 221〜244 行。キーと値を送るようにした場合に送られうる値（`addKeyValue` の呼び出しの検索の結果）:
   - `audit/service/AuditEventListener.java` 151〜158 行: 監査の書き込みの失敗の ERROR に、記録しようとした全項目（入力されたメールアドレス・送り元の IP・User-Agent・要求のパスなど）を載せる（README 541 行に明記）。
   - `user/service/InitialAdminInitializer.java` 82・88・91 行: 初期管理者のメールアドレスを INFO に載せる。
   - `auth/service/LoginService.java` 146 行: ロックした利用者の ID。
   - `dslmanage/service/DslOperationMetrics.java` 85〜91 行: `dsl.operation`・`dsl.outcome`・`dsl.durationMs`・`dsl.hash`（先頭 12 文字）・`dsl.source`。
   - ほかは `code`・`status`・`reason`・`exceptionType`・時間・件数など（`common/error/web/GlobalExceptionHandler.java` 223〜230 行、`targetdb/service/JdbcTargetSchemaReader.java` 86〜88 行、`targetdb/config/TargetDataSourceConfig.java` 67 行 など）。パスワード・トークンの値を載せる呼び出しは見当たらない。
   - 手元の監視の警報（`docker/monitoring/provisioning/alerting/mastersmith.yaml` 139・364・397・430・463・496・529 行）は Loki で本文の文字列だけを絞り込んでいる。確認用の受け手 `docker/otel-collector/config.yaml` は debug の出力だけ。
2. **ロックの状態の行が無い利用者の同時の初めてのログインが 500** — `auth/service/LoginService.java` 161〜168 行（`lockUserRow`）: 排他つきの読み取りで行が無ければ `createIfAbsent` の後にもう一度読む。`auth/repository/LoginAttemptStateRepository.java` 65〜73 行（`lockForUpdate`。行が無いと排他を取る対象が無い）と 102〜110 行（`createIfAbsent`。H2 の `MERGE INTO ... WHEN NOT MATCHED THEN INSERT`）。表は `V3__u2_authentication.sql` 21〜26 行で `subject_id` が主キー。同時の2つの試みがどちらも行を見つけられず MERGE を行うと、後の方が主キーの重複になりうる（前の Intent の Performance Validation の F3 と同じ筋）。行は通常 `auth/service/LoginAttemptStateInitializer.java`（利用者の作成と同じトランザクション）で作られるため、行が無いのは SQL で直接入れた利用者（`perf/README.md` 25 行の試験用の利用者）など。`decide` は `login` の中の `transaction.execute` の中で動く（`LoginService.java` 119 行）。
3. **`dslMixed` が同じ利用者を2つの VU に割り当てる** — `perf/k6/scenarios.js` 49〜58 行で `dslMixed` は2つの場面（`dslHeavy` 1 VU・`logins` VUS）を同時に動かし、163 行の `loginLoop` は `exec.vu.idInTest`（場面をまたいだ通しの番号、合計 VUS＋1）から `((id - 1) % 10) + 1` で利用者を選ぶ。どの番号が `dslHeavy` に割り当たるかで、`logins` 側の2つの VU が同じ `perf-userNN` になりうる。`loginSuccess`・`refresh` の場面（173・180 行）は単独の場面のため同じ式でも重ならない。
4. **起動時の Hibernate の案内が改行を含む1件のログ** — `logback-spring.xml` はルートの INFO と JSON の出力1つだけで（56〜58 行）、ロガーごとの水準の指定は無い。`application.yaml` の `logging.level`（246〜259 行）もルートと JDBC ドライバー3つだけで、`org.hibernate` の指定は無い。前の Intent の記録では `Database JDBC URL`・`Database driver` など 11 行の案内。どのロガーの名前で出ているかは、このスキャン（読み取りだけ）では確かめていない（実際のログの `logger` の項目で確かめる必要がある）。Hibernate は 7.4.5.Final。
5. **メモリの上限の既定が 1g** — `compose.yaml` 63 行 `mem_limit: ${MASTERSMITH_CONTAINER_MEMORY:-1g}`（説明は 57〜61 行）。同じ既定が `docker/perf/compose.yaml` 57 行にもある。`.env.example` 24〜26 行、README 204〜208 行（既知の制約）・230 行（環境変数の表の既定 `1g`）、`docker/check-container-limits.sh` 20・93 行（変数なしで 1g＝1073741824 を期待する確かめ）も 1g を前提にしている。一方 `perf/dsl-timing.sh` 55 行は既定 2g、`perf/README.md` 18・109 行は 2g で流す手順。`compose.yaml` 99〜101 行の lgtm の説明は「アプリ（上限 2GB）」を前提にしている。
6. **アプリのコンテナが見本の対象DB の管理者のパスワードを持つ** — `compose.yaml` 32〜34 行で `app` は `.env` の全体を `env_file` で読む。`.env.example` 72〜73 行に `MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD`・`MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD` があり、見本の対象DB の3つのサービス（`compose.yaml` 122・135・148 行）は `${...}` の展開で同じ値を受ける。そのため `app` のコンテナの環境変数にも管理者のパスワードが入る。アプリが対象DB の接続に使うのは `MASTERSMITH_TARGET_DB_*`（`application.yaml` 75〜103 行）。`docker/perf/compose.yaml` は対象DB 用の別の環境ファイル（`MASTERSMITH_PERF_TARGETDB_ENV_FILE`）を使い、アプリには渡していない（`app` の `env_file` は 29〜34 行、`targetdb-postgres` の `env_file` は 68〜70 行）。
7. **make-you-chic-ui の Modal・Alert の取り込み** — 今の checkout（`5258c8bb…`）では `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Modal/Modal.tsx` の Props（23〜32 行）に閉じるボタンの文言や説明の結び付け（`aria-describedby`）の口が無く、閉じるボタンは `aria-label="閉じる"` 固定（97 行）、ダイアログは `aria-labelledby` だけ（87〜88 行）。`Alert.tsx` の閉じるボタンも `aria-label="閉じる"` 固定（69 行）。画面での使用箇所:
   - `Modal`: `frontend/src/features/dsl/DslConfirmDialog.tsx` 108 行だけ（本文の `<p className="dsl-confirm-text">` は 112・122・144 行）。
   - 閉じるボタンの出る `Alert`（`onDismiss` 付き）: `frontend/src/features/dsl/DslAdminPage.tsx` 134 行だけ。ほかの `Alert`（`LoginForm.tsx` 80 行・`AdminAreaPage.tsx` 71 行・`DslPreviewPanel.tsx` 70・137・145・149・154 行・`DslStatusPanel.tsx` 75 行・`DslHistoryTable.tsx` 66 行・`DslSubmitForm.tsx` 184 行）は閉じるボタンを持たない。
   - 取り込み方: `frontend/package.json` の `"make-you-chic-ui": "file:../vendor/make-you-chic-ui/packages/make-you-chic-ui"`、`vite.config.ts` の `resolve.dedupe`。ビルドは `vendorBuild`（`build.gradle.kts` 99〜109 行）。CI はサブモジュールを固定先で取得（`ci.yml` の checkout の `submodules: true`）。
   - 更新先の make-you-chic-ui の版（直した後のコミット）は、このリポジトリの中からは確かめられない（`git` を実行していない）。

そのほかの兆し（今回の範囲の外、記録だけ）:

- 大きなファイル: `frontend/src/features/dsl/useDslAdmin.ts`（492 行）・`dslmanage/service/DslLifecycle.java`（462 行）・`frontend/src/features/dsl/messages.ts`（418 行）。
- `.github/dependabot.yml` はサブモジュール（gitsubmodule）と `vendor/make-you-chic-ui` の npm を対象にしていない（脆弱性は OSV-Scanner が vendor の lockfile も検査する）。
- 前の Intent で後に回した束 2（内部DB のファイルの伸び、10MB の DSL とログインの重ねでのメモリ）は未解決のまま。

## Handoff Summary

- **Intent-relevant finding**: 7件はどれも小さく、変更の場所が特定できる。1 は `config/ObservabilityConfig.java` 93〜101 行で出力の設定が足りない（キーと値を送る設定を呼んでいない）ことが原因とみられる。ただし送るようにすると、`AuditEventListener.java` 151〜158 行（監査の書き込みの失敗の ERROR に入力のメールアドレス・IP・User-Agent）と `InitialAdminInitializer.java` 82・88・91 行（初期管理者のメールアドレス）の個人に関する値が Loki に送られる。2 は `LoginService.java` 161〜168 行と `LoginAttemptStateRepository.java` 102〜110 行の「行が無ければ MERGE で作ってから読み直す」手順に、同時の作成への備えが無い。3 は `perf/k6/scenarios.js` 163 行の `exec.vu.idInTest` の使い方。5・6 は `compose.yaml` 63 行と 32〜34 行。7 の使用箇所は `DslConfirmDialog.tsx` 108 行と `DslAdminPage.tsx` 134 行の2か所だけ。
- **Risks / follow-up**:
  - 1 の変更は、project.md の Forbidden（パスワード・トークン・署名鍵を外部へのエクスポートに含めない）に加え、個人に関する値（メールアドレスなど）を送るかどうかの判断が要る。既存の `ExternalExportIT` は送ったログ（`/v1/logs`）の中身を確かめていない。
  - 5 の既定を変えると、`docker/check-container-limits.sh` 93 行（既定 1g を期待）・README 230 行・`.env.example` 24〜26 行・`docker/perf/compose.yaml` 57 行の説明と確かめも合わせて直す必要がある。
  - 2 の修正は認証に関わるため、失敗の場合のテストと不具合を再現するテスト（project.md の Mandated）が要る。既存に同時の初めてのログインのテストは無い。
  - 4 の案内を出すロガーの名前は、このスキャンでは確かめていない。
  - 7 はサブモジュールの固定先の更新を専用のコミットで行い、前後のハッシュを記録する（project.md の Mandated）。今の checkout は `5258c8bb987b0fa6ffd0ad7c4eadc7d4006da52d`（親のリポジトリの gitlink とは未照合）。`DslAdminPage.test.tsx` 535 行は閉じるボタンの名前「閉じる」に依存する。
  - 深く読んだ範囲はリポジトリの全体ではない（Minimal の深さ）。Scope of Analysis は `kind: partial` とし、`./` を含めないこと。
