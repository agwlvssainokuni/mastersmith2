## Developer Code Scan Results

対象: リポジトリ `mastersmith2`（プロジェクトルート `./`）の全体の再走査。基準のコミット `6afbf97`（`develop`）。深さは Minimal。
Intent: 「同時10件のログインでコネクションプールが尽き、監査の書き込みが失敗する（F2）を直す」。F2 に関わる範囲（ログインの流れ・監査の記録・トランザクションと接続の扱い・データソースの設定・関連するテスト）は行番号まで読んだ。

### Scan Coverage
- **Analyzed deeply**:
  - `backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java`
  - `backend/src/main/java/cherry/mastersmith/auth/service/LogoutService.java`
  - `backend/src/main/java/cherry/mastersmith/auth/service/TokenRefreshService.java`
  - `backend/src/main/java/cherry/mastersmith/auth/web/AuthController.java`
  - `backend/src/main/java/cherry/mastersmith/auth/repository/`
  - `backend/src/main/java/cherry/mastersmith/auth/domain/AuthenticationEvent.java`
  - `backend/src/main/java/cherry/mastersmith/audit/`（`service/AuditEventListener.java`・`service/AuditEventRecorder.java`・`service/AuditConfig.java`・`repository/AuditEventRepository.java`）
  - `backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java`
  - `backend/src/main/java/cherry/mastersmith/user/repository/UserRepository.java`
  - `backend/src/main/java/cherry/mastersmith/access/service/AccessDeniedEventPublisher.java`
  - `backend/src/main/resources/application.yaml`
  - `backend/src/main/resources/db/migration/V4__u4_audit_event.sql`
  - `backend/build.gradle.kts`、`build.gradle.kts`（`verify` の段の構成）、`settings.gradle.kts`、`gradle/libs.versions.toml`、`backend/gradle.lockfile`（主要な版）
  - `backend/src/test/java/cherry/mastersmith/auth/service/LoginConcurrencyIT.java`
  - `backend/src/test/java/cherry/mastersmith/audit/`（`service/AuditWriteFailureIT.java`・`AuditWriteTimingIT.java`・`AuditRollbackIT.java`・`AuditAuthenticationEventsIT.java` のテスト名、`testsupport/FailingAuditEventRepositoryConfig.java`・`SlowAuditWriteConfig.java`、`AuditBoundaryArchitectureTest.java`）
  - `backend/src/test/java/cherry/mastersmith/common/testsupport/TestDatabase.java`
  - `backend/src/test/resources/`
  - コンポーネント: LoginService、LogoutService、TokenRefreshService、AuthController、LoginAttemptStateRepository、RefreshTokenRepository、AuditEventListener、AuditEventRecorder、AuditEventRepository、UserAccountService、AccessDeniedEventPublisher、データソース／HikariCP の設定
- **Skimmed only**:
  - `backend/src/main/java/cherry/mastersmith/`（上記以外: `access/web/`・`auth/web/` の残り・`common/`・`config/`・`user/domain/`・`auth/domain/` の残り。ファイル名・注釈・一部の grep だけ）
  - `backend/src/test/java/cherry/mastersmith/`（上記以外。ファイル名と `@DisplayName` の一覧）
  - `frontend/`（`package.json`・`vite.config.ts`・`vitest.config.ts`・`src/` のファイル一覧・`src/features/auth/authApi.ts` の API パス）
  - `.github/`、`Dockerfile`、`compose.yaml`、`docker/`、`perf/`、`config/`、`.pre-commit-config.yaml`、`.gitmodules`、`README.md`
  - `vendor/make-you-chic-ui/`（サブモジュールの固定先の確認だけ。中身は読んでいない）
  - 前の Intent の記録（`aidlc/spaces/default/intents/260922-auth-audit-base/` の performance-validation の F2 の記述と、U4 の nfr-design の「接続を2本使う」の記述。grep で該当行だけ）

### Packages Found
- `backend` — Gradle サブプロジェクト（実行可能 WAR）— Java 25 — Spring Boot のバックエンド。ルートパッケージ `cherry.mastersmith`。main 142 ファイル、test 127 ファイル
  - `cherry.mastersmith.auth` — web/service/domain/repository — ログイン・トークンの更新・ログアウト・ロック（U2）
  - `cherry.mastersmith.user` — service/domain/repository — 利用者、パスワードの照合、初期管理者（U2）
  - `cherry.mastersmith.access` — web/service/domain — 管理者の API の認可と 401/403、アクセス拒否の出来事（U3）
  - `cherry.mastersmith.audit` — service/domain/repository（web なし）— 監査イベントの追記（U4）
  - `cherry.mastersmith.common` — error（Problem Details）・health・i18n・observability・security・web の共通部品（U1）
  - `cherry.mastersmith.config` — Spring の設定（セキュリティ・Web・観測）
- `frontend` — npm パッケージ — TypeScript（React 19）— SPA。`src/app`（枠・ルーティング・i18n）、`src/features/auth`・`src/features/admin`、`src/shared/api-client`。非テスト 36 ファイル、テスト 29 ファイル
- `vendor/make-you-chic-ui` — Git サブモジュール（固定先 `5258c8b`）— TypeScript — デザインシステム。`frontend` から `file:` 参照
- `perf` — k6 のシナリオ（`perf/k6/scenarios.js`、`constant-vus`）と手順
- `docker` — 監視（grafana/otel-lgtm のダッシュボード・警報）、OTel Collector、負荷の試験用 compose

### Build System
- **Type**: Gradle（Kotlin DSL、ラッパーあり）＋ npm（`frontend`、Gradle の Exec タスクから呼ぶ）
- **Config Files**: `settings.gradle.kts`、`build.gradle.kts`（ルート: `verify`・`e2eTest`・npm と Gitleaks・OSV-Scanner の呼び出し）、`backend/build.gradle.kts`、`gradle/libs.versions.toml`、`backend/gradle.lockfile`、`settings-gradle.lockfile`、`frontend/package.json`・`package-lock.json`、`Dockerfile`、`compose.yaml`
- **Build Dependencies**:
  - `:backend:bootWar` → `:frontendBuild` → `vendorBuild` → `vendorInstall`（`frontend/dist` を WAR の `WEB-INF/classes/static` に同梱）
  - `verify` → `verifyFormat`…`verifyArtifact` の9段（フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体テスト → 結合テスト → カバレッジ → 安全の検査 → 成果物）。各段は前の段の後に走る
  - `:backend:check` → `integrationTest`（`*IT`）。`test` は `*Test` だけ
  - Tomcat の版を `11.0.26` に強制（`backend/build.gradle.kts` 45〜52 行）

### APIs Discovered
- REST — `backend/src/main/java/cherry/mastersmith/auth/web/AuthController.java` — 3 件: `POST /api/auth/login`（98 行）、`POST /api/auth/session/refresh`（114 行）、`POST /api/auth/session/logout`（135 行）
- REST — `backend/src/main/java/cherry/mastersmith/access/web/AdminCheckController.java` — 1 件: `GET /api/admin/check`
- REST — `backend/src/main/java/cherry/mastersmith/common/error/web/ProblemTypeController.java` — 1 件: `GET /api/problems/{slug}`（エラーの種類の説明）
- エラーの経路 — `common/error/web/ErrorPathController.java` — 2 件の `@RequestMapping`
- Actuator — `GET /actuator/health`（公開は health だけ。DB の確認は `common/health/TimeBoundedDbHealthIndicator.java` が同じプールから1本借りる）
- 内部の出来事（アプリ内の API）— `AuthenticationEvent`（U2 → U4、トランザクションの中で publish）、`AdminAccessDeniedEvent`（U3 → U4、トランザクションの外で publish）、`UserCreatedEvent`（user → auth の `LoginAttemptStateInitializer`、`@EventListener` ＋ `Propagation.MANDATORY`）
- 画面側の呼び出し先 — `frontend/src/features/auth/authApi.ts`（35〜41 行に上記3つのパス）

### Frameworks & Libraries
- Java — 25（toolchain）— 言語
- Spring Boot — 4.1.1 — webmvc・security・actuator・data-jpa・flyway・validation・aspectj・opentelemetry・oauth2-resource-server
- Spring Framework（spring-tx・spring-orm）— 7.0.9 — トランザクション、JPA の統合
- Spring Data JPA — 4.1.1 — リポジトリ
- Hibernate ORM — 7.4.5.Final — JPA 実装
- HikariCP — 7.0.2 — コネクションプール（`mastersmith-db`、最大 10、借りる待ち 5000ms）
- H2 — 2.4.240 — 内部DB（組み込み・ファイル）
- Flyway — 12.4.0 — スキーマの変更（V1〜V4）
- Tomcat（組み込み）— 11.0.26 — 強制した版
- logstash-logback-encoder — 9.0 — JSON の構造化ログ
- opentelemetry-logback-appender — 2.28.1-alpha — ログの外部エクスポート（固定の理由は project.md の Tech Stack）
- JUnit（Boot の BOM）・jqwik 1.10.1・ArchUnit 1.5.0 — テスト
- JaCoCo 0.8.15、Spotless 8.10.2 ＋ palantir-java-format 2.98.0、SpotBugs 4.10.4 ＋ FindSecBugs 1.14.0 — 品質の道具
- React 19.2 / react-router 8.3 / i18next 26・react-i18next 17 — 画面
- Vite 8.2・TypeScript 6.0・Vitest 4.1・@vitest/coverage-v8・Testing Library・vitest-axe・fast-check 4.10・Playwright 1.63 — 画面のビルドとテスト
- oxlint 1.78・ESLint 10.8（react-hooks）・Prettier 3.9・Stylelint 17.14 — 画面の検査

### Test Coverage
- **Test Directories**: `backend/src/test/java/cherry/mastersmith/`（`*Test` 60 件＝単体、`*IT` 44 件＝Spring と組み込みの H2 を起動する結合、ほか testsupport）、`backend/src/test/resources/`、`frontend/src/**/*.test.ts(x)`（29 件）、`frontend/e2e/`（Playwright 3 件。`verify` と CI の外）
- **Test Frameworks**: JUnit 5 ＋ Spring Boot Test、AssertJ、jqwik、ArchUnit（`ArchitectureTest`・`AuthBoundaryArchitectureTest`・`AuditBoundaryArchitectureTest`）、Vitest ＋ Testing Library（jsdom）＋ user-event ＋ vitest-axe、fast-check、Playwright、k6（負荷）
- **Coverage Config**: あり。バックエンドは JaCoCo（`backend/build.gradle.kts` 131〜186 行。行 80%・分岐 70%、除外は起動クラスと `*Properties`）、フロントエンドは `frontend/vitest.config.ts` 33〜43 行（lines 80・branches 70）
- 結合テストの DB は `TestDatabase.register`（`@TempDir` の H2 ファイル）。プールの大きさは本番の設定（10）のまま

### Code Quality Indicators
- **Linting**: Java は Spotless（palantir-java-format ＋ ライセンスヘッダー）と SpotBugs ＋ FindSecBugs（`spotbugsGate` で High のみ失敗、`backend/config/spotbugs-exclude.xml`）。画面は `frontend/.oxlintrc.json`・`eslint.config.js`・`.prettierrc.json`・`.stylelintrc.json`、ライセンスヘッダーは `frontend/scripts/check-license-header.mjs`。秘密情報は Gitleaks（`.gitleaks.toml`）、依存の脆弱性は OSV-Scanner（`config/npm-build-tools.txt`）。`.pre-commit-config.yaml` に gitleaks・spotless・prettier
- **CI/CD**: `.github/workflows/ci.yml`（`develop` へのプッシュと `v*` タグで `./gradlew verify`、WAR を成果物として保存、タグでリリースに添付。アクションは SHA で固定）、`.github/dependabot.yml`（gradle・npm・github-actions・docker）
- **Documentation**: `README.md`（326 行。起動・環境変数・U 間の連携の表）、`perf/README.md`、`frontend/src/features/README.md`。Java は全クラスに日本語の Javadoc があり、設計の文書（BR・NFR 番号）への参照が多い
- TODO/FIXME/HACK は 0 件。`@SuppressWarnings("unchecked")` が main に1件（`common/error/web/DefaultErrorResponseWriter.java` 79 行）

### Technical Debt Signals
- **【F2 の原因】ログインの接続を持ったまま、確定の後に監査が2本目を借りる**（詳しくは下の「F2 の範囲の詳細」）。設計の段で「一時的に2本使う」ことを受け入れ、負荷の試験で確かめるとしていた（前の Intent の U4 の `nfr-design/performance-design.md` 28 行、`logical-components.md` 40 行）。見積もりは「重なる時間は短い」だったが、前の Intent の performance-validation の `test-results.md` 46 行（F2）で、同時 10 件で抜けられない待ち合いになることが確かめられた
- 同じ形の2本使いがログイン以外にもある: 失敗のログイン（`LOGIN_FAILED`、`LoginService.java` 134・152 行）、ログアウト（`LogoutService.java` 76 行の `@Transactional` の中の 93 行の publish）
- 同時の数を確かめる結合テストは、失敗のログイン5件・4件だけで、スレッドは8本（`LoginConcurrencyIT.java` 78 行）。成功のログインを同時にプールの数だけ流す場合のテストが無い
- ヘルスチェックの DB の確認も同じプールから借りるため、プールが尽きている間は DOWN になりうる（`TimeBoundedDbHealthIndicator.java` 127 行）。別の見つかったこと F1 とも関係する
- ルートに `mastersmith-data-*.tgz`（内部DBのバックアップ）が3つある。`.gitignore` 100 行で管理外にしてあり、コミットはされていない。`.env` も存在する（中身は読んでいない。`.gitignore` 91 行で管理外）
- 監査の書き込みの失敗は ERROR を出して捨てる（再試行なし、`AuditEventListener.java` 124〜126 行）。F2 の状況では監査イベントが欠けるだけで、応答は 200 のまま（設計どおり。BR3.1）

#### F2 の範囲の詳細（トランザクションの境界と接続の借り方）

1. **入口**: `AuthController.login`（`auth/web/AuthController.java` 98〜104 行）はトランザクションを持たずに `LoginService.login` を呼ぶ。`spring.jpa.open-in-view: false`（`application.yaml` 108 行）のため、要求の全体で接続を持ち越す仕組みは無い。
2. **照合（トランザクションの外）**: `LoginService.login`（`auth/service/LoginService.java` 117〜124 行）の 118 行 `userAccountService.verifyPassword(...)`。`UserAccountService.verifyPassword`（`user/service/UserAccountService.java` 78〜92 行）には `@Transactional` が無く、`userRepository.findByEmail`（Spring Data の既定の読み取りのトランザクション）で接続を借りてすぐ返す。bcrypt（cost 12）の照合は接続を持たない。
3. **判定と書き込み（1つの短いトランザクション）**: 119 行 `transaction.execute(status -> decide(...))`。`TransactionTemplate` は 106 行でコンストラクターの `PlatformTransactionManager`（JPA の `JpaTransactionManager`）から作る。`decide`（127〜159 行）の中で:
   - `LoginAttemptStateRepository.lockForUpdate`（`SELECT ... FOR UPDATE`、待ち 3 秒）またはダミーの行の `SKIP LOCKED`（`auth/repository/LoginAttemptStateRepository.java` 65〜95 行）
   - `attemptRepository.update`（明示の更新、120〜130 行）
   - 成功なら `issueTokens`（182〜188 行。`RefreshTokenRepository.save` でリフレッシュトークンの行を追加）
   - **156〜157 行で `AuthenticationEvent`（`LOGIN_SUCCEEDED`）を publish**（失敗は 170〜173 行の `publishFailure` で `LOGIN_FAILED`）。受け取り側は AFTER_COMMIT のため、ここではトランザクションの同期に登録されるだけ
4. **確定と監査の記録（ここで2本目を借りる）**: `TransactionTemplate.execute` が確定すると、Spring の `AbstractPlatformTransactionManager` は「確定 → afterCommit の呼び出し → afterCompletion → 後始末（`JpaTransactionManager.doCleanupAfterCompletion` で EntityManager を閉じ、接続をプールへ返す）」の順に進む。afterCommit の時点では、**ログインの接続はまだ返されていない**。
   - 根拠: spring-orm 7.0.9 の `HibernateJpaVendorAdapter` が `hibernate.connection.handling_mode=DELAYED_ACQUISITION_AND_HOLD` を設定する（jar の中の文字列で確認済み）。このため Hibernate は確定しただけでは接続を返さず、EntityManager を閉じるまで持ち続ける。
   - afterCommit で `AuditEventListener.onAuthenticationEvent`（`audit/service/AuditEventListener.java` 87〜91 行、`@TransactionalEventListener(phase = AFTER_COMMIT, fallbackExecution = true)`、`@Order(HIGHEST_PRECEDENCE)`）が**要求と同じスレッドで**呼ばれ、`record`（110〜127 行）→ 115 行 `recorder.record(auditEvent)`。
   - `AuditEventRecorder.record`（`audit/service/AuditEventRecorder.java` 51〜54 行）は `@Transactional(propagation = REQUIRES_NEW)`。まだ紐づいている元のトランザクションの資源を一時的に外し、**プールから新しい接続を1本借りて** `INSERT INTO audit_events` を1回行う（REQUIRED だと確定済みのトランザクションに加わって書き込みが確定されないため、REQUIRES_NEW にしている。27〜28 行の説明）。
   - 監査の確定の後に戻り、元のトランザクションの後始末でログインの接続がやっと返される。
5. **尽きる仕組み**: 同時 10 件の成功のログインが 3. を終えると、10 本すべてがログインの接続として持たれたまま、それぞれが 4. で11本目以降を待つ。どれも返らないため、Hikari の `connection-timeout: 5000`（`application.yaml` 105 行）で10件とも `CannotCreateTransactionException` になり、`AuditEventListener` 124〜126 行が受け止めて ERROR「監査イベントの記録に失敗しました」（60 行の固定の文）を出す。例外は呼び出し元へ伝わらないため、後始末で接続が返り、応答は 200 になる（約 5 秒＋照合などで約 7.2 秒）。
6. **同じ経路の別の出来事**:
   - `LOGIN_FAILED`（ユーザーが無い・パスワード誤り・ロック中）も 3.〜4. と同じく2本使う。前回の負荷の試験で失敗のログインが尽きなかったのは、この経路の偏りによる可能性があるが、未確認。
   - `LOGGED_OUT`: `LogoutService.logout`（`auth/service/LogoutService.java` 76〜101 行、`@Transactional` の中で 93 行 publish）も同じ形。
   - `TokenRefreshService.refresh`（74 行 `@Transactional`）は出来事を publish しないため、監査による2本目は無い。
   - `ACCESS_DENIED`: `AccessDeniedEventPublisher.publish`（`access/service/AccessDeniedEventPublisher.java` 53〜59 行）はトランザクションの外で publish するため、`fallbackExecution = true` でその場で受け取り、使うのは1本だけ。
7. **プールと設定**: `application.yaml` 97〜105 行（`pool-name: mastersmith-db`、`maximum-pool-size: 10`、`connection-timeout: 5000`）、108 行（`open-in-view: false`）、114 行（問い合わせの上限 10 秒）。Tomcat のスレッドの上限は既定（設定なし）のため、プールの数より多い要求が同時に DB に来うる。
8. **関連するテストと、直すときに守るべき決まり**:
   - `AuditAuthenticationEventsIT`（165〜175 行）「the audit insert runs on the request thread and issues one insert only」: 記録は**要求と同じスレッド**で行い、INSERT は1回。
   - `AuditTraceIdIT`（121・134・147 行）: 監査の行のトレースIDが要求のものと一致する。
   - `AuditRollbackIT`（133〜167 行）: 元のトランザクションが取り消されたら記録しない（BEFORE_COMMIT で例外を投げて確かめる）。
   - `AuditWriteFailureIT`（112〜195 行）: 書き込みの失敗（`APPEND_FAILURE`・`CONNECTION_FAILURE`）でも応答が変わらず、ERROR は1件、再試行なし。`CONNECTION_FAILURE` は `FailingAuditEventRepositoryConfig`（76〜78 行）で `CannotCreateTransactionException` を投げる模擬で、本物のプールの枯渇ではない。
   - `AuditWriteTimingIT`・`SlowAuditWriteConfig`: 200ms 超の WARN（`LongSupplier` の差し替え）。
   - `AuditEventRecorderTest`（54 行）「the append runs in a new transaction」: REQUIRES_NEW であることを確かめている。
   - `LoginServiceTest`（119〜241 行）: `decide` の中で出来事を publish することを確かめている（単体、モック）。
   - `LoginConcurrencyIT`: 失敗の同時5件・4件と、ほかの利用者の待ちなし（8 スレッド）。成功の同時 10 件は無い。
   - `AuditBoundaryArchitectureTest`（94〜121 行）: audit の `@Transactional` は `audit.service` だけ。`ArchitectureTest`（102 行）: トランザクションの境界は service の層だけ。
9. **設計の上の前提（前の Intent の記録）**: U4 の nfr-design の記録に「別スレッドに移すとトレースIDの一致と『確定の後に記録』の決まりを変えることになるため、接続を一時的に2本使うことを受け入れた」とある（`aidlc/spaces/default/intents/260922-auth-audit-base/construction/u4-audit-log/nfr-design/memory.md` 14 行）。監査の書き込みの失敗の扱いは BR3.1（受け止めて ERROR、操作は失敗させない）。

## Handoff Summary
- **Intent-relevant finding**: F2 は、`LoginService.login` の `TransactionTemplate.execute`（`backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java` 119 行）の確定の後、後始末で接続を返す前の afterCommit で、`AuditEventListener.onAuthenticationEvent`（`backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java` 87〜91 行、AFTER_COMMIT・同じスレッド）が `AuditEventRecorder.record`（`backend/src/main/java/cherry/mastersmith/audit/service/AuditEventRecorder.java` 51〜54 行、REQUIRES_NEW）を呼び、2本目の接続を借りることで起きる。spring-orm が設定する `DELAYED_ACQUISITION_AND_HOLD` により、ログインの接続は EntityManager を閉じるまで返らない。プールは 10 本・借りる待ち 5 秒（`backend/src/main/resources/application.yaml` 101〜105 行）。同時の数がプールの数に達すると全員が待ち合い、監査の書き込みがすべて失敗する（受け止められるため応答は 200）。
- **Risks / follow-up**:
  - 同じ2本使いは `LOGIN_FAILED`（`LoginService.java` 134・152 行）と `LOGGED_OUT`（`LogoutService.java` 76・93 行）にもある。直す範囲をログインの成功だけにするか、この経路全体にするかを決める必要がある。
  - 直し方を選ぶときに守るべき既存の決まりとテスト: 記録は要求と同じスレッドで INSERT 1回（`AuditAuthenticationEventsIT` 165 行）、トレースIDの一致（`AuditTraceIdIT`）、取り消されたら記録しない（`AuditRollbackIT`）、失敗は受け止めて ERROR 1件で再試行なし（`AuditWriteFailureIT`）、REQUIRES_NEW であること（`AuditEventRecorderTest` 54 行）、トランザクションの境界は service の層だけ（ArchUnit）。
  - 不具合を再現するテストが今は無い（`LoginConcurrencyIT` は失敗の同時5件まで、8 スレッド）。project.md の Mandated（不具合を再現するテストを同じコミットに含める）により、同時にプールの数の成功のログインを流し、監査の行が欠けないこと（と接続の待ちが起きないこと）を確かめる結合テストが必要になる。結合テストのプールの大きさは本番と同じ 10。
  - ヘルスチェックも同じプールを使う（`TimeBoundedDbHealthIndicator.java` 127 行）。プールの大きさを変える直し方は、F1・メモリ（F3、1GB のコンテナ）への影響も見る必要がある。
  - 監視の側には `hikaricp_connections_pending` の警報が既にある（`docker/monitoring/provisioning/alerting/mastersmith.yaml` 268 行）。直した後の確かめに使える。
  - ルートに管理外の内部DBのバックアップ（`mastersmith-data-*.tgz`）と `.env` がある。どちらも `.gitignore` で除外済みで、コミットの対象にしないこと。
