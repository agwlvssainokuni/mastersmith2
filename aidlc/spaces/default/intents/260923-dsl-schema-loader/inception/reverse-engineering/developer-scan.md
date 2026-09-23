# 開発者によるコードスキャン（Reverse Engineering・pipeline の第1リンク）

- Intent: `260923-dsl-schema-loader`（ロードマップの Intent A・D・E の統合。DSL のスキーマ定義、対象DBのメタデータからの既定 DSL の生成、DSL の読み込み・検証とプレビュー→適用）
- 対象: mastersmith2（プロジェクトルート）、ブランチ `develop`、コミット `42def8c667a1e3955316727d2bd1b613b6655d1e`（`.git/HEAD` と `.git/refs/heads/develop` を読んで確認）
- 走査の広さ: 全体の読み直し（Full rescan、スナップショットの範囲は `./`）／深さ: Standard
- 確かめ方: ファイルの読み取り（Read・grep・ls）だけ。git・Gradle・npm・Docker のコマンドは実行していない。`.env` は開いていない（`.env.example` だけを読んだ）。
- 依頼者の要件資料（Git 管理外の参考資料）は、対象DBの接続設定と DSL の保存方法の節の見出しと本文の一部だけを、既存コードとの照合のために読んだ。

## Developer Code Scan Results

### Scan Coverage

- **Analyzed deeply**（実際に読んで理解したもの）:
  - `settings.gradle.kts`、`build.gradle.kts`（1コマンドの検査 `verify` の段の組み立て、npm・Gitleaks・OSV-Scanner の呼び出し、OSV の判定の決まり、`e2eTest`）
  - `backend/build.gradle.kts`（依存関係、単体テストと結合テストの分け方、JaCoCo の下限と除外、Spotless、SpotBugs の関門、`bootWar` への `frontend/dist` の同梱）
  - `gradle/libs.versions.toml`、`gradle/wrapper/gradle-wrapper.properties`、`backend/gradle.lockfile`（対象の部品の有無を grep で確認）、`backend/config/spotbugs-exclude.xml`
  - `backend/src/main/java/cherry/mastersmith/MastersmithApplication.java`
  - `backend/src/main/java/cherry/mastersmith/config/`（全ファイル）
  - `backend/src/main/java/cherry/mastersmith/common/`（全ファイル: `error/`・`security/`・`web/`・`health/`・`i18n/`・`observability/`）
  - `backend/src/main/java/cherry/mastersmith/access/`（全ファイル）
  - `backend/src/main/java/cherry/mastersmith/auth/web/`（全ファイル）
  - `backend/src/main/java/cherry/mastersmith/auth/repository/`（全ファイル）
  - `backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java`、`AuthClockConfig.java`、`AuthProperties.java`、`AuthSchedulingConfig.java`
  - `backend/src/main/java/cherry/mastersmith/auth/domain/AuthenticatedUser.java`、`ClientInfo.java`、`RefreshToken.java`、`LoginAttemptState.java`、`AuthProblemTypes.java`
  - `backend/src/main/java/cherry/mastersmith/audit/package-info.java`、`audit/service/`（全ファイル）、`audit/repository/`（全ファイル）、`audit/domain/AuditEvent.java`・`AuditEventType.java`・`AuditEventFactory.java`
  - `backend/src/main/resources/`（`application.yaml`、`logback-spring.xml`、`db/migration/V1〜V4`）
  - `backend/src/test/java/cherry/mastersmith/ArchitectureTest.java`、`common/testsupport/TestDatabase.java`・`HttpTestClient.java`、`access/web/AdminAccessIT.java`（結合テストの書き方の見本として）
  - `backend/src/test/resources/`（`archunit.properties`、`junit-platform.properties`、`META-INF/spring.factories`）
  - `frontend/package.json`、`frontend/vite.config.ts`、`frontend/vitest.config.ts`、`frontend/vitest.setup.ts`、`frontend/tsconfig.json`、`frontend/.oxlintrc.json`、`frontend/eslint.config.js`、`frontend/.prettierrc.json`、`frontend/.stylelintrc.json`、`frontend/.npmrc`、`frontend/playwright.config.ts`、`frontend/index.html`
  - `frontend/src/main.tsx`、`frontend/src/features/README.md`、`frontend/src/app/App.tsx`、`frontend/src/app/registry/`（全ファイル）、`frontend/src/app/routing/AppRouter.tsx`・`decideRoute.ts`、`frontend/src/app/navigation/navigationItems.ts`
  - `frontend/src/shared/api-client/apiClient.ts`・`apiError.ts`
  - `frontend/src/features/admin/`（`registration.ts`・`adminApi.ts`・`adminAreaStatus.ts`・`AdminAreaPage.tsx`・`AdminPlaceholder.tsx`）
  - `frontend/src/features/auth/registration.ts`・`authSession.ts`
  - `Dockerfile`、`compose.yaml`、`.env.example`、`.dockerignore`、`.gitignore`、`.gitmodules`、`.gitleaks.toml`、`.pre-commit-config.yaml`
  - `.github/workflows/ci.yml`、`.github/dependabot.yml`、`config/npm-build-tools.txt`
  - `README.md` の「スキーマの変更（Flyway）」「API のアクセス制御（U3）」「監査ログ（U4）」「後の単位が使う差し込み口」の節
- **Skimmed only**（見出し・宣言・注記だけを見たもの）:
  - `backend/src/main/java/cherry/mastersmith/auth/service/`（上の4ファイル以外。クラスの宣言と `@Transactional`・`@Scheduled`・出来事の通知の位置だけ）
  - `backend/src/main/java/cherry/mastersmith/auth/domain/`（上の5ファイル以外）
  - `backend/src/main/java/cherry/mastersmith/audit/domain/`（`AuditText`・`AuditResult`・`AuditFailureReason`）
  - `backend/src/main/java/cherry/mastersmith/user/`（全体。クラスの宣言とトランザクション・出来事の位置だけ）
  - `backend/src/test/java/cherry/mastersmith/`（上の4ファイル以外。ファイルの一覧、件数、`@SpringBootTest`・jqwik の注釈の数）
  - `frontend/src/app/layout/`、`frontend/src/app/i18n/`、`frontend/src/app/login-state/`、`frontend/src/app/pages/`、`frontend/src/app/testing/`、`frontend/src/features/auth/`（上の2ファイル以外）、`frontend/src/types/`、テストのファイル（`*.test.ts(x)`）
  - `frontend/e2e/`、`frontend/scripts/`
  - `vendor/make-you-chic-ui/`（取り込み方と `packages/make-you-chic-ui/src/index.ts` の公開部品の一覧だけ。中身は読んでいない）
  - `docker/`（`check-container-limits.sh` の説明、`perf/compose.yaml`・`monitoring/`・`otel-collector/` はファイルの存在だけ）、`perf/`（見出しと k6 の場面の名前）
  - `README.md`（上の節以外は見出しだけ）、`config/license-header.txt`、`LICENSE`

### Packages Found

バックエンド（Java、ルートパッケージ `cherry.mastersmith`、機能ごとのパッケージ×層 `web`・`service`・`domain`・`repository`）:

- `cherry.mastersmith`（`MastersmithApplication`）— 起動クラス — Java — 実行可能 WAR と外部のサーブレットコンテナの両方で起動できる（`SpringBootServletInitializer`）。`@ConfigurationPropertiesScan` で設定の型を集める。
- `cherry.mastersmith.config` — 設定 — Java — Spring Security のフィルターの連鎖（1つ、状態を持たない）、SPA の配信と見つからない URL への `index.html` の返却、転送元ヘッダーの扱い、外部エクスポート（OTLP）の組み立て、応答ヘッダーの設定の型。
- `cherry.mastersmith.common.error.{domain,service,web}` — 共通部品 — Java — RFC 9457 Problem Details に `code`・`traceId` を足したエラー応答。`BusinessException`＋`ProblemType`（日英の説明つき）＋各機能の `ProblemTypeCatalog` の Bean を起動時に集める `ProblemTypeRegistry`（重複で起動失敗）。例外の変換は `GlobalExceptionHandler`（`@RestControllerAdvice`）の1か所、フィルターの段階は `DefaultErrorResponseWriter`、`/error` は `ErrorPathController`。説明ページ `GET /api/problems/{slug}`。
- `cherry.mastersmith.common.security` — 差し込み口 — Java — `SecurityRuleContributor`（order で並べる。U2 は 100 台、U3 は 200 台）、`ApiDefaultAccess`（0個か1個）、`ErrorResponseWriter`、起動時の検査 `SecurityExtensionValidator`。
- `cherry.mastersmith.common.web` — フィルター・設定 — Java — `CacheControlFilter`（`/api/**` は `no-store` など）、`RequestSizeLimitFilter`（本文の上限。既定 1MB、超えたら 413）、`MastersmithWebProperties`（`mastersmith.web.*`）。
- `cherry.mastersmith.common.health` — ヘルスチェック — Java — 制限時間付きの内部DBの確認（`SELECT 1`）。Actuator の既定の DB の確認を置き換える。
- `cherry.mastersmith.common.i18n.domain` — ドメイン — Java — `Accept-Language` から表示言語（ja／en、既定 ja）を決める。
- `cherry.mastersmith.common.observability` — 観測性 — Java — トレースIDの参照、送るトレースから例外のメッセージを除く包み、URL の問い合わせの部分を除くフィルター、メソッドの呼び出しの追跡（`TraceAspect`。`web`・`service`・`domain`・`repository` の層の Bean の引数・戻り値を TRACE のときだけ文字列化）。
- `cherry.mastersmith.auth.{domain,service,repository,web}` — 機能（U2 認証）— Java — ログイン（ロック判定、ダミーの行による存在の秘匿）、JWT（HS256）のアクセストークン、リフレッシュトークン（ハッシュだけを保存、Cookie で受け渡し）、ログアウト、使い終わったトークンの定期削除。`Clock` の Bean（UTC）を全体に提供。
- `cherry.mastersmith.access.{domain,service,web}` — 機能（U3 管理画面のアクセス制御）— Java — `/api/admin` と `/api/admin/**` を管理者のみにする決まり（`AdminSecurityContributor`、order 210）、401・403・400（正規化されていないパス）の処理、アクセス拒否の出来事の通知、確認用 API `GET /api/admin/check`。
- `cherry.mastersmith.audit.{domain,service,repository}` — 機能（U4 監査ログ）— Java — 認証の出来事とアクセス拒否の出来事を受け取り、内部DBの `audit_events` に確定の後で追記（`REQUIRES_NEW`、失敗は受け止めて ERROR）。追記と読み取りだけの repository。
- `cherry.mastersmith.user.{domain,service,repository}` — 機能（U2 利用者）— Java — 利用者のエンティティ、メールアドレス・パスワードの決まり、bcrypt、初期管理者の自動作成（`SmartInitializingSingleton`）。
- **対象DB（`mastersmith.target-db.*`）・DSL・スキーマ読み込みに関するパッケージ・クラス・設定・画面は存在しない**（下の「Handoff Summary」を参照）。

フロントエンド（TypeScript + React、`frontend/src`）:

- `app/`（骨組み、U1）— 画面の骨組み — TS/React — 起動（登録の読み込みと検査）、表示言語（i18next）、ログイン状態の受け取り、URL の振り分け（`decideRoute`）、レイアウト（AppShell の中 `ShellLayout`／外 `StandaloneLayout`・`LoginLayout`）、ホーム・「ページが見つかりません」・起動エラーの画面。
- `app/registry/` — 差し込み口 — TS — `FeatureRegistration`（画面・サイドバー・ユーザーメニュー・ログイン状態の提供元・文言）。`features/*/registration.ts` を `import.meta.glob`（eager）で自動で読み込み、重複や決まり違反で起動を止める。
- `shared/api-client/` — 共通部品 — TS — 同じオリジンの API 呼び出し、アクセストークンの付与、401／`AUTHENTICATION_REQUIRED` での更新1回と送り直し、エラーを `{ status, code }` の形にする。
- `features/auth/` — 機能（U2）— TS/React — ログイン画面（`/login`、STANDALONE・PUBLIC・role LOGIN）、トークンのメモリ保持、ログイン状態の提供元、ログアウトのメニュー。
- `features/admin/` — 機能（U3）— TS/React — 管理者向け領域 `/admin`（SHELL・ADMIN、サイドバー「管理」order 200）。表示のたびに `GET /api/admin/check` を呼ぶ。中身は「今後の管理機能はこの画面に加わります」の置き場（プレースホルダ）。

その他:

- `vendor/make-you-chic-ui` — Git サブモジュール（外部のデザインシステム）— TS/React — `frontend/package.json` から `file:../vendor/make-you-chic-ui/packages/make-you-chic-ui` で参照し、`build.gradle.kts` の `vendorInstall`・`vendorBuild` でビルドしてから使う。`vendorUnchanged` でサブモジュールの変更が無いことを確かめる。
- `docker/`・`compose.yaml`・`Dockerfile` — コンテナ — 開発者の PC で動かす app コンテナと、profile で起動する otel-collector・lgtm（監視）。
- `perf/`・`docker/perf/` — 負荷の試験（k6）と使い捨ての環境。

### Build System

- **Type**: Gradle 9.7.1（Kotlin DSL、ラッパー）でルート＋`backend` サブプロジェクト。フロントエンドは npm（Node.js 24、`engine-strict`）を Gradle の `Exec` タスクから呼ぶ。
- **Config Files**:
  - `settings.gradle.kts`（取得元は Maven Central のみ、`FAIL_ON_PROJECT_REPOS`）、`settings-gradle.lockfile`
  - `build.gradle.kts`（ルート。`verify` の段、npm・vendor・安全の検査のタスク、`e2eTest`）
  - `backend/build.gradle.kts`、`backend/gradle.lockfile`（全構成を lockfile で固定。更新は `:backend:resolveAndLockAll --write-locks`）
  - `gradle/libs.versions.toml`（版の一覧。Spring Boot 管理の部品は BOM に任せる）
  - `frontend/package.json`・`frontend/package-lock.json`、`vendor/make-you-chic-ui/package-lock.json`
  - `config/license-header.txt`（Apache 2.0 ヘッダーのひな形）、`config/npm-build-tools.txt`（OSV の判定で devDependencies でも止める道具の一覧）
- **Build Dependencies**:
  - `verify` → 段 0〜9 を `mustRunAfter` で順に並べる: `verifyPrepare`（`checkToolchain`→`vendorInstall`→`vendorBuild`→`vendorUnchanged`、`frontendInstall`）→ `verifyFormat`（Spotless×2・Prettier）→ `verifyLint`（oxlint＋ESLint・Stylelint）→ `verifyLicense`（画面のヘッダー。Java と Gradle は Spotless が段 1 で確認）→ `verifyBuild`（`compileJava`・`compileTestJava`・`tsc --noEmit`・Vite のビルド）→ `verifyUnitTest`（`:backend:test`・Vitest）→ `verifyIntegrationTest`（`:backend:integrationTest`、組み込みの H2）→ `verifyCoverage`（JaCoCo・`@vitest/coverage-v8`）→ `verifySecurity`（`spotbugsGate`・`osvScan`・`gitleaksScan`）→ `verifyArtifact`（`bootWar`・初回読み込み量の確認）
  - `:backend:bootWar` → `:frontendBuild` → `vendorBuild`（`frontend/dist` を WAR の `WEB-INF/classes/static` に同梱。成果物名 `mastersmith.war`）
  - `frontendTypecheck`・`frontendTest`・`frontendCoverage` → `vendorBuild`（make-you-chic-ui の `dist` が要る）
  - Tomcat の版は `resolutionStrategy` で 11.0.26 に上書き（Spring Boot 管理の 11.0.24 の脆弱性回避）
  - `e2eTest` → `:backend:bootWar`（`verify` と CI には入れない）

### APIs Discovered

- REST（Spring MVC）— `cherry.mastersmith.auth.web.AuthController` — 3件: `POST /api/auth/login`（公開、JSON、200 と `TokenResponse`＋リフレッシュ Cookie）、`POST /api/auth/session/refresh`（公開・Origin の確認、Cookie で認証）、`POST /api/auth/session/logout`（公開・Origin の確認、204）
- REST — `cherry.mastersmith.access.web.AdminCheckController` — 1件: `GET /api/admin/check`（管理者のみ、204）
- REST — `cherry.mastersmith.common.error.web.ProblemTypeController` — 1件: `GET /api/problems/{slug}`（公開、Accept で HTML／JSON、Accept-Language で日英）
- REST — `cherry.mastersmith.common.error.web.ErrorPathController` — `/error`（GET・HEAD・OPTIONS と POST・PUT・PATCH・DELETE。サーブレットの段階のエラーを共通の形にする）
- Actuator — `GET /actuator/health`（公開、状態だけ。ほかの窓口は公開しない）
- 静的配信 — `WebConfig` — `/**`（`classpath:/static/`、`/api/`・`/actuator/` 以外の見つからない URL は `index.html`）
- アクセスの決まり（`SecurityConfig` の並び）: U1 の公開（`/actuator/health`・`/api/problems/**`）→ U2 の公開（`/api/auth/login`・`/api/auth/session/**`）＋Bearer の検証 → U3 の `/api/admin`・`/api/admin/**` を管理者のみ → `/api/**` は既定でログイン必須（`AdminApiDefaultAccess`）→ それ以外（画面）は許可。
- 内部の差し込み口（型の契約）: `SecurityRuleContributor`、`ApiDefaultAccess`、`ErrorResponseWriter`、`BusinessException`＋`ProblemTypeCatalog`、`TraceIdProvider`、`Clock` の Bean、`AuthenticatedUser`（主体）、`AuthenticationEvent`（確定の後に受け取る）、`AdminAccessDeniedEvent`（要求と同じスレッドで受け取る）、画面の `FeatureRegistration`、`apiFetch`／`apiRequest`（README の「後の単位が使う差し込み口」に一覧がある）。
- 画面から呼ぶ API: `frontend/src/features/auth/authApi.ts`（3件）、`frontend/src/features/admin/adminApi.ts`（1件）。

### Frameworks & Libraries

バックエンド（`gradle/libs.versions.toml` と `backend/gradle.lockfile` より）:

- Java — 25（ツールチェーン）— 言語
- Spring Boot — 4.1.1 — アプリの基盤（starter: webmvc・security・actuator・data-jpa・flyway・validation・aspectj・opentelemetry・oauth2-resource-server、tomcat-runtime は `providedRuntime`）
- Spring Framework — 7.0.9 ／ Spring Security — 7.1.1 — Web・セキュリティ
- Hibernate ORM — 7.4.5.Final — JPA（`ddl-auto: validate`）
- Flyway — 12.4.0 — 内部DBのスキーマ変更（`classpath:db/migration`、起動時に適用・検証）
- H2 — 2.4.240 — 内部DB（組み込み・ファイル保存、`runtimeOnly`）
- HikariCP — 7.0.2 — 接続プール（`mastersmith-db`、上限 30、借りる待ち 5 秒）
- Jackson — 3.1.5（`tools.jackson`、Spring Boot 4 の既定）。`com.fasterxml.jackson.core:jackson-annotations` 2.21 だけが併存
- SnakeYAML — 2.6 — Spring Boot の推移依存（`application.yaml` の読み込み用）。アプリのコードからは使っていない
- Nimbus JOSE + JWT — 10.9.1 — JWT（HS256）の発行と検証
- Tomcat（組み込み）— 11.0.26（上書き）— サーブレットコンテナ
- logstash-logback-encoder — 9.0 — 1行1件の JSON ログ
- opentelemetry-logback-appender — 2.28.1-alpha（固定。Boot の OpenTelemetry 1.62 に合わせる）— ログの OTLP 送信（有効時のみ）
- テスト: JUnit Platform（Spring Boot BOM 管理）、spring-boot-starter-test・webmvc-test・security-test、jqwik 1.10.1、ArchUnit 1.5.0
- 品質: Spotless 8.10.2＋palantir-java-format 2.98.0、SpotBugs 4.10.4（プラグイン 6.5.11）＋FindSecBugs 1.14.0、JaCoCo 0.8.15
- **無いもの**: MySQL・MariaDB・PostgreSQL の JDBC ドライバー、Testcontainers、JSON Schema の検証の部品、Jackson の YAML 形式（`jackson-dataformat-yaml`）、Flyway の MySQL・PostgreSQL 用モジュール

フロントエンド（`frontend/package.json`、宣言の範囲）:

- React・react-dom — ^19.2.8、react-router — ^8.3.0、i18next — ^26.4.2、react-i18next — ^17.0.15、@fontsource/noto-sans-jp — ^5.3.0、make-you-chic-ui — `file:` 参照（サブモジュールの固定先 `5258c8bb987b0fa6ffd0ad7c4eadc7d4006da52d` を `.git/modules/vendor/make-you-chic-ui/HEAD` で確認）
- ビルド: Vite ^8.2.1、@vitejs/plugin-react ^6.0.5、TypeScript ^6.0.3
- テスト: Vitest ^4.1.11、@vitest/coverage-v8 ^4.1.11、jsdom ^30.1.1、@testing-library/react ^16.3.3・dom ^10.4.2・jest-dom ^7.0.1・user-event ^14.6.7、vitest-axe ^0.1.0、fast-check ^4.10.2、@playwright/test ^1.63.0
- 品質: oxlint ^1.78.0、ESLint ^10.8.1＋eslint-plugin-react-hooks ^7.1.1、Prettier ^3.9.6、Stylelint ^17.14.1＋stylelint-config-standard ^40.0.0
- make-you-chic-ui の公開部品: ThemeProvider・Icon・Button・FormField・TextInput・Textarea・Select・Checkbox・Switch・RadioGroup・Avatar・Badge・Card・Modal・Toast・Alert・Tooltip・Tabs・Dropdown・AppShell・Table（`DefaultCellEditor` つき）。現在の画面で使っているのは AppShell・Alert・Button・FormField・TextInput・Toast・Modal・Theme の各 Provider だけ。ファイルの選択（アップロード）や差分表示の専用部品は無い
- **無いもの**: YAML の解析の部品、JSON Schema の検証の部品、コードエディター

### Test Coverage

- **Test Directories**:
  - `backend/src/test/java/cherry/mastersmith/`（本体と同じパッケージ構成。130 ファイル／約 16,000 行。単体テスト `*Test` 60 件、結合テスト `*IT` 46 件、ほかはテストの補助 `testsupport/`）
  - `backend/src/test/resources/`（`archunit.properties`、`junit-platform.properties` で jqwik の記録を `build/jqwik-database`、`META-INF/spring.factories` でテスト用の署名鍵を入れる `EnvironmentPostProcessor`、`static/` にテスト用の画面ファイル）
  - `frontend/src/**/*.test.ts(x)`（対象と同じ場所、29 ファイル）
  - `frontend/e2e/*.e2e.ts`（Playwright、3 ファイル: 骨組み・認証・管理者向け領域）
  - `perf/k6/scenarios.js`（負荷の試験。テストの関門の外）
- **Test Frameworks**: JUnit 5（Platform）、Spring Boot Test（`@SpringBootTest(webEnvironment = RANDOM_PORT)` と `HttpTestClient` で実際の Tomcat に HTTP を送る形が中心）、AssertJ、jqwik（`@Property` 18 件）、ArchUnit（`ArchitectureTest` と機能ごとの境界の検査）、Vitest＋Testing Library（jsdom）＋user-event＋vitest-axe＋fast-check、Playwright（Chromium、`e2eTest` だけ）。
- **結合テストの DB**: 組み込みの H2 をテストのクラスごとの一時ディレクトリに作る（`@TempDir` と `TestDatabase.register` の `@DynamicPropertySource`）。Testcontainers は使っていない（team.md の決まりでは、内部DBは H2、コンテナは後続の対象DB のテストに使う）。
- **Coverage Config**: present。
  - バックエンド: JaCoCo（`test.exec` と `integrationTest.exec` を合わせる）、行 80%・分岐 70% の下限を `jacocoTestCoverageVerification` で検証。除外は `MastersmithApplication*` と `*Properties`（設定値の record）だけ。
  - フロントエンド: `@vitest/coverage-v8`、`thresholds` で行 80%・分岐 70%。除外は `src/main.tsx`・`*.d.ts`・テストのファイル。
- テストの説明文は英語（`@DisplayName`・`describe`／`it`）、時刻は `Clock`（テストでは `MutableClock`）で差し替える。

### Code Quality Indicators

- **Linting**:
  - Java: Spotless（palantir-java-format、ライセンスヘッダー、末尾の空白）— `backend/build.gradle.kts`。Gradle の Kotlin DSL のヘッダーはルートの Spotless — `build.gradle.kts`。
  - Java の静的解析: SpotBugs（effort MAX・報告は LOW から）＋FindSecBugs、`spotbugsGate` で priority 1（High）があれば失敗、テストのコードは対象外。除外の設定 `backend/config/spotbugs-exclude.xml` は空。
  - 層の境界: ArchUnit — `backend/src/test/java/cherry/mastersmith/ArchitectureTest.java`（web は repository を使わない、`@Transactional` は service の層だけ、コントローラーはエンティティを返さない、フィールド・メソッドへの注入の禁止、Lombok の禁止）。
  - 画面: oxlint — `frontend/.oxlintrc.json`（correctness を error、react・jsx-a11y・typescript、`react/no-danger`・`no-eval`・`no-new-func`・`no-script-url`）、ESLint — `frontend/eslint.config.js`（react-hooks の推奨、`no-implied-eval`、`export default` と `enum` の禁止）、Prettier — `frontend/.prettierrc.json`、Stylelint — `frontend/.stylelintrc.json`、TypeScript `strict` — `frontend/tsconfig.json`、ヘッダー — `frontend/scripts/check-license-header.mjs`。
  - 秘密情報: Gitleaks — `.gitleaks.toml`（既定の規則＋AI-DLC の記録の目印の誤検出の除外）、pre-commit — `.pre-commit-config.yaml`（Gitleaks・Spotless・Prettier）。
  - 依存関係: OSV-Scanner（`osvScan`。Gradle は CVSS 7.0 以上、npm の実行時は High 以上、開発用は警告、ただし成果物を作る道具と `MAL-` は失敗）、Dependabot — `.github/dependabot.yml`（gradle・npm・github-actions・docker、毎週）。
- **CI/CD**: `.github/workflows/ci.yml`（`develop` へのプッシュと `v*` のタグで `./gradlew verify`、サブモジュールは固定先、Gitleaks と OSV-Scanner は版と SHA-256 で固定、Actions はコミットのハッシュで固定、WAR を成果物として保存、タグではリリースに添付）。配備はコンテナ（`Dockerfile` は WAR をコピーするだけの1段、`compose.yaml`）で、開発者の PC に限る。
- **Documentation**: `README.md`（前提の道具、1コマンドの検査、E2E、開発時とコンテナでの起動、バックアップと戻し方、環境変数、監視、Flyway、アクセス制御、監査ログ、差し込み口の一覧）、`perf/README.md`、`frontend/src/features/README.md`。Java のクラス・公開メソッドには日本語の Javadoc があり、設計の番号（BR・NFR・ADR）への参照が多い。TypeScript のファイルの先頭にも目的の説明がある。

### Technical Debt Signals

- 内部DB の SQL が H2 に依存している: `backend/src/main/java/cherry/mastersmith/auth/repository/LoginAttemptStateRepository.java:104` の `MERGE INTO ... USING (VALUES ...)`、`RefreshTokenRepository.java:62` の `FETCH FIRST :limit ROWS ONLY`、Hibernate の `SKIP LOCKED` の指定。内部DB は H2 のまま使う前提なので現状は問題ないが、同じ書き方を対象DB（MySQL・MariaDB・PostgreSQL）へのアクセスに流用すると動かない。
- 監査ログが認証とアクセス拒否に特化している: `audit/domain/AuditEventType.java` は `LOGIN_SUCCEEDED`・`LOGIN_FAILED`・`LOGGED_OUT`・`ACCESS_DENIED` の4種、`AuditEventFactory` と `AuditEventListener` は2種類の出来事の型だけを網羅の `switch` で受ける。表 `audit_events` にも操作対象を表す列が無い。`project.md` の DECIDED で「監査記録の共通の仕組みは業務データの CRUD を扱う後続 Intent で共通化を検討する」とされており、共通化はまだされていない。
- ログイン・ログアウトでは確定の後の監査の記録のために接続を2本使う（README の既知の制約、プールの上限 30）。対象DB 用の接続プールを別に足す場合、コンテナのメモリの上限（既定 1g、推奨 2g）とプールの数の見積もりに関わる。
- `TraceAspect` が `web`・`service`・`domain`・`repository` の層の Bean の引数と戻り値を TRACE のときに文字列化する（`common/observability/TraceAspect.java:48`）。既存の秘密情報を持つ型（`AuthProperties`・`LoginRequest`・`TokenResponse` など）は `toString` で伏せ字にしている。同じ扱いを新しい型にも求める決まりが README にある。
- 画面の振り分けは登録された URL と完全一致（`matchPath(..., end: true)`）で1画面を表示する形で、入れ子のルートや画面の中のタブ・段階（プレビュー→適用のような）の URL は機能側で工夫が要る。
- 管理者向け領域 `/admin` の中身はプレースホルダだけ（`features/admin/AdminPlaceholder.tsx`）。管理機能を置く場所として用意されている。
- TODO・FIXME・HACK は見当たらない。`@SuppressWarnings` は `DefaultErrorResponseWriter` の1か所（型の変換）だけ。1ファイルの行数の最大は約 220 行（`AuditEvent.java`・`GlobalExceptionHandler.java`）で、大きすぎるクラスは無い。
- 作業ツリーに生成物（`frontend/coverage/`・`frontend/playwright-report/`・`frontend/test-results/`・`build/`・`.idea/`）があるが、いずれも `.gitignore` の対象。

## Handoff Summary

- **Intent-relevant finding**:
  1. **対象DB・DSL の既存コードは無い。** `mastersmith.target-db` などの設定、対象DB 用の `DataSource`、JDBC `DatabaseMetaData` の読み取り、DSL（YAML）の読み込み・検証・保存、プレビュー・適用の仕組みと画面は、バックエンド・フロントエンド・設定（`application.yaml`・`.env.example`・`compose.yaml`）・テストのどこにも無い（grep で `target-db`・`dsl`・`json schema`・`mysql`・`mariadb`・`postgres`・`testcontainers` を検索して、Gradle の「Kotlin DSL」の文言以外に当たりなし）。すべて新しく作る。
  2. **内部DB は Spring Boot の自動構成の `DataSource` が1つだけ**（`backend/src/main/resources/application.yaml:97-106`、H2 の組み込み・ファイル保存、HikariCP `mastersmith-db`）。JPA・Flyway（`application.yaml:114`・`121`）・ヘルスチェック・監査もこの1つに結び付いている。対象DB 用の `DataSource` を足すと、自動構成の内部DB の `DataSource`・JPA・Flyway が引かれなくならないよう、内部DB 側を `@Primary` にするなどの配線が要る。
  3. **依存関係に対象DB のドライバー（MySQL・MariaDB・PostgreSQL）、Testcontainers、JSON Schema の検証の部品、YAML の Jackson 形式が無い**（`backend/build.gradle.kts:54-84`、`backend/gradle.lockfile`）。Jackson は 3 系（`tools.jackson`）で、SnakeYAML 2.6 は推移依存としてだけある。JSON Schema の検証の部品の多くは Jackson 2 系を前提にするため、部品を選ぶときに Jackson 3 との組み合わせを確かめる必要がある。追加した部品は `backend/gradle.lockfile` の更新と OSV-Scanner の関門（High 以上で失敗）を通す必要がある。
  4. **管理者のみの API は `/api/admin/` の下に置くだけで守られる**（`access/domain/AdminPaths.java:30-36`、`access/web/AdminSecurityContributor.java`）。DSL の生成・読み込み・プレビュー・適用の API をここに置けば、401／403 の応答と拒否の監査が既存の仕組みで付く。画面は `frontend/src/features/<featureId>/registration.ts` に `access: 'ADMIN'`・`layout: 'SHELL'` で登録し、サイドバーの項目は `visibleWhen: 'ADMIN'` にする（`frontend/src/app/registry/types.ts`）。
  5. **エラー応答・例外・ログの形が決まっている**: 業務エラーは `BusinessException`＋機能ごとの `ProblemTypeCatalog`（code は `^[A-Z][A-Z0-9_]*$`、日英の title・description・resolution、重複で起動失敗）。例外の変換は `GlobalExceptionHandler` だけで行う。DSL の検証エラーを行・項目ごとに返す場合、今の Problem Details には `code`・`traceId` 以外の拡張項目が無く、`detail` は1つの文字列だけなので、複数の検証エラーを返す形は新しく決める必要がある。
  6. **要求の本文の上限は既定 1MB**（`application.yaml:25`、`RequestSizeLimitFilter`）。完成品の DSL の投入（アップロード）が 1MB を超えうるなら、上限の設定か API の分け方を決める必要がある。マルチパートの上限の設定は `application.yaml` に無く（Spring Boot の既定のまま）、`MaxUploadSizeExceededException` は 413 に変換される（`GlobalExceptionHandler.java:176`）。
  7. **内部DB のスキーマ変更は Flyway の `V<番号>__<単位>_<内容>.sql`、前進のみ**（既存は `V1`〜`V4`）。DSL（プレビューと適用済みの版）を内部DB に保存するなら `V5` 以降に足す。Hibernate は `validate` のため、エンティティと表の食い違いは起動時に失敗する。
- **Risks / follow-up**:
  - team.md の Testing Posture は「Testcontainers は、コンテナで動かす対象DB（後続 Intent D・E で扱う業務DB）のテストに使う」としている。今回は Testcontainers の依存関係・ローカル（colima）と CI（GitHub Actions）でのコンテナの実行環境の前提の文書化・`verify` の結合テストの段への組み込みが新しく要る。3種類の DB（MySQL・MariaDB・PostgreSQL）をすべてテストするかは、`verify` の時間とコンテナのメモリ（colima の VM）への影響があるため、要件か設計で決める必要がある。
  - ArchUnit の決まり（`@Transactional` は service の層だけ、web は repository を使わない、エンティティを返さない、コンストラクター注入だけ）は `cherry.mastersmith` の下の全クラスに当たる。対象DB への読み書きの層も同じ決まりに従う。対象DB と内部DB の2つのトランザクションマネージャーを持つ場合、既存の `@Transactional`（名前の指定なし）と `LoginService` の `PlatformTransactionManager` の注入が内部DB 側を指し続けるよう注意する。
  - 対象DB の接続情報（パスワード）は秘密情報。project.md の Forbidden に従い、`application.yaml` には環境変数の参照だけを置き、`.env.example` には値を空にして足し、設定の型の `toString` で伏せ字にする（`TraceAspect` とログへの漏えいを防ぐ）。ヘルスチェック（`/actuator/health` は内部DB だけを見ている）に対象DB を含めるかも決める必要がある（含めると対象DB の停止でアプリ全体が DOWN になる）。
  - 監査ログは認証とアクセス拒否に特化している。DSL の生成・リセット・適用を監査の対象にするなら、`AuditEventType`・`audit_events` の表（Flyway の追加）・`AuditEventFactory`・`AuditEventListener` の拡張が要り、project.md の DECIDED（共通化は CRUD を扱う後続 Intent で検討）との関係を要件で整理する必要がある。
  - `compose.yaml` には対象DB のコンテナが無い。開発者の PC で動かして確かめるには、対象DB のサービス（profile で起動するなど）と、アプリのコンテナから対象DB への接続の設定が要る。
  - フロントエンドには YAML の解析の部品も差分表示・ファイル選択の部品も無く、make-you-chic-ui にも無い。プレビューの表示の形（サーバーで解釈した結果を JSON で返して表で見せるか、YAML の本文を見せるか）を決める必要がある。make-you-chic-ui の中身は変更できない（project.md の Forbidden）。
  - 既存のコード知識ベース（`aidlc/spaces/default/codekb/mastersmith2/`）は前回 `260923-colima-spec-up` のコミット `aeeaf73` 時点の partial（コンテナ・負荷試験・設定中心）。その後もアプリのソースの変更があったかは git を使わずには確かめていないため、今回の全体の読み直しで置き換える前提で扱ってほしい。今回の深い範囲は上の Scan Coverage のとおりで、リポジトリ全体を深く読んだわけではない（`user/` パッケージ、テストの大半、画面のレイアウト・i18n、`vendor/` は流し読み）。
