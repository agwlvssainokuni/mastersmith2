# 開発担当のコード走査（Intent 260930-user-admin）

- 対象: リポジトリ mastersmith2（プロジェクトルートの単一リポジトリ）、ブランチ `develop`、HEAD `31b980b1205b9dc3884565eacbbe7c9b8eb3ed90`
- Intent: 260930-user-admin（scope classic、深さ Standard、Brownfield）。説明の原文は「利用者の管理の画面を作る（一覧・管理者の印・利用停止・ロックの解除）」
- 依頼者の選択: Full rescan（事前のスナップショットの paths は `./`）
- 確かめ方: 読み取りだけ（ファイルの読み取り・検索・`git log`・`git submodule status`）。`gradlew`・`npm`・`docker` は実行していない。`.env`・`.env.targetdb`・鍵のファイルと `reference/` は開いていない。
- 既存の知識ベース（`aidlc/spaces/default/codekb/mastersmith2/`、前回 Intent 260929-log-deps-cleanup、STALE）は参考として読んだだけで、書き換えていない。部品 ID はその `component-inventory.md` の英数字 ID に合わせた。
- 記号: 「事実」はコードで確かめたこと、「見立て」は確かめていない推測。

## Developer Code Scan Results

### Scan Coverage

深さ Standard のため、全体は一覧と見出しの読み取りで把握し、今回の Intent に関わる範囲（利用者・認証・ロック・認可・監査・招待の管理の型・画面の差し込み口・関係するテストの境界の決まり・ビルドのカバレッジの決まり）を深く読んだ。深く読んだ範囲は `./` の中に収まる。全体を深く読んではいないため、記録上の範囲は partial にあたる（`./` は analyzed に入れない）。

- **Analyzed deeply**（実際に読んで理解したもの。部品 ID は括弧の中）:
  - `backend/src/main/java/cherry/mastersmith/user/domain/User.java`（user）
  - `backend/src/main/java/cherry/mastersmith/user/domain/UserProblemTypes.java`（user）
  - `backend/src/main/java/cherry/mastersmith/user/repository/`（user）
  - `backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java`（user）
  - `backend/src/main/java/cherry/mastersmith/user/service/UserSummary.java`（user）
  - `backend/src/main/java/cherry/mastersmith/user/service/PasswordVerification.java`（user）
  - `backend/src/main/java/cherry/mastersmith/user/service/CreateUserResult.java`（user）
  - `backend/src/main/java/cherry/mastersmith/user/service/NewUser.java`（user）
  - `backend/src/main/java/cherry/mastersmith/user/service/UserCreatedEvent.java`（user）
  - `backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java`（user）
  - `backend/src/main/java/cherry/mastersmith/user/service/UserAccountConfig.java`（user）
  - `backend/src/main/java/cherry/mastersmith/auth/domain/`（auth。`LoginAttemptState`・`LockPolicy`・`LockState`・`LockDecision`・`AuthenticatedUser`・`AuthenticationEvent`・`AuthenticationEventType`・`LoginFailureReason`・`TokenFailureReason`・`AuthProblemTypes` を読んだ。ほかはファイル名だけ）
  - `backend/src/main/java/cherry/mastersmith/auth/repository/`（auth）
  - `backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java`（auth）
  - `backend/src/main/java/cherry/mastersmith/auth/service/AccessTokenService.java`（auth）
  - `backend/src/main/java/cherry/mastersmith/auth/service/TokenRefreshService.java`（auth）
  - `backend/src/main/java/cherry/mastersmith/auth/service/LogoutService.java`（auth）
  - `backend/src/main/java/cherry/mastersmith/auth/service/LoginAttemptStateInitializer.java`（auth）
  - `backend/src/main/java/cherry/mastersmith/auth/service/AuthProperties.java`（auth）
  - `backend/src/main/java/cherry/mastersmith/auth/web/AccessTokenAuthenticationProvider.java`（auth）
  - `backend/src/main/java/cherry/mastersmith/auth/web/AuthController.java`（auth）
  - `backend/src/main/java/cherry/mastersmith/auth/web/AuthSecurityContributor.java`（auth）
  - `backend/src/main/java/cherry/mastersmith/auth/web/CurrentUserResponse.java`（auth）
  - `backend/src/main/java/cherry/mastersmith/access/domain/AdminPaths.java`（access）
  - `backend/src/main/java/cherry/mastersmith/access/domain/AccessDeniedReason.java`（access）
  - `backend/src/main/java/cherry/mastersmith/access/web/`（access。`AdminSecurityContributor`・`AdminAuthorizationManager`・`AdminCheckController`・`AdminApiDefaultAccess`・`AccessWebSecurityConfig` を読んだ。ほかはファイル名だけ）
  - `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`（audit）
  - `backend/src/main/java/cherry/mastersmith/audit/domain/AuditResult.java`（audit）
  - `backend/src/main/java/cherry/mastersmith/audit/domain/AuditFailureReason.java`（audit）
  - `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventFactory.java`（audit。1〜260 行）
  - `backend/src/main/java/cherry/mastersmith/audit/repository/`（audit）
  - `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventRecorder.java`（audit）
  - `backend/src/main/java/cherry/mastersmith/audit/service/AuditConfig.java`（audit）
  - `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java`（audit。1〜200 行）
  - `backend/src/main/java/cherry/mastersmith/invitation/web/InvitationAdminController.java`（invitation）
  - `backend/src/main/java/cherry/mastersmith/invitation/web/InvitationSecurityContributor.java`（invitation）
  - `backend/src/main/java/cherry/mastersmith/invitation/web/InvitationRequestContextResolver.java`（invitation）
  - `backend/src/main/java/cherry/mastersmith/invitation/web/InvitationPageResponse.java`（invitation）
  - `backend/src/main/java/cherry/mastersmith/invitation/domain/InvitationPaging.java`（invitation）
  - `backend/src/main/java/cherry/mastersmith/invitation/service/InvitationBarrier.java`（invitation）
  - `backend/src/main/java/cherry/mastersmith/invitation/service/InvitationService.java`（invitation。一覧の `list`・`readPage`・`displayName` の部分だけ）
  - `backend/src/main/java/cherry/mastersmith/config/SecurityConfig.java`（config）
  - `backend/src/main/resources/db/migration/`（user・auth・audit・invitation。V1〜V8 のすべて）
  - `backend/src/test/java/cherry/mastersmith/ArchitectureTest.java`（build-and-verify。決まりの名前と対象だけ）
  - `backend/src/test/java/cherry/mastersmith/auth/AuthBoundaryArchitectureTest.java`（auth。決まりの名前と対象だけ）
  - `backend/src/test/java/cherry/mastersmith/audit/AuditBoundaryArchitectureTest.java`（audit。同上）
  - `backend/src/test/java/cherry/mastersmith/invitation/InvitationBoundaryArchitectureTest.java`（invitation。同上）
  - `backend/build.gradle.kts`（build-and-verify。テストの分け方・カバレッジ・`packagesJudgedByTotal`・SpotBugs の関門）
  - `backend/config/spotbugs-exclude.xml`（build-and-verify）
  - `gradle/libs.versions.toml`（build-and-verify）
  - `settings.gradle.kts`（build-and-verify）
  - `.github/dependabot.yml`（build-and-verify）
  - `frontend/package.json`（build-and-verify）
  - `frontend/vitest.config.ts`（build-and-verify。カバレッジの下限の行だけ）
  - `frontend/src/app/registry/types.ts`（frontend-registry）
  - `frontend/src/app/navigation/navigationItems.ts`（frontend-registry）
  - `frontend/src/features/README.md`（frontend-registry）
  - `frontend/src/features/admin/registration.ts`（frontend-feature-admin）
  - `frontend/src/features/invitation/registration.ts`（frontend-feature-invitation）
  - `frontend/src/features/invitation/api/invitationApi.ts`（frontend-feature-invitation。1〜80 行）
  - `frontend/src/features/auth/authSession.ts`（frontend-feature-auth）
- **部品 ID（深く読んだもの）**: `user`・`auth`・`access`・`audit`・`invitation`・`config`・`frontend-registry`・`frontend-feature-admin`・`frontend-feature-invitation`・`frontend-feature-auth`・`build-and-verify`
  - `invitation`・`frontend-feature-invitation`・`config` は、今回の Intent の型（管理の一覧・ページ送り・安全の決まりの差し込み口）として必要な部分だけを深く読んだ。部品の全体は読んでいない。
- **Skimmed only**（ディレクトリの一覧・ファイル名・見出し・検索だけ）:
  - `backend/src/main/java/cherry/mastersmith/`（ほかのパッケージ: `common/`・`dsl/`・`dslmanage/`・`targetdb/`・`mail/`・`appearance/`・`MastersmithApplication.java`。ファイル名と行数、`import` と注釈の検索だけ）
  - `backend/src/main/java/cherry/mastersmith/user/domain/`・`user/web/`（上に挙げたファイルのほかはファイル名だけ）
  - `backend/src/main/java/cherry/mastersmith/invitation/`（上に挙げたファイルのほかはファイル名と `public` のメソッドの検索だけ）
  - `backend/src/main/resources/`（`application.yaml`・`logback-spring.xml`・`mail/`・`dsl/` は一覧だけ）
  - `backend/src/test/java/cherry/mastersmith/`（318 ファイル。ファイル名の一覧だけ。`user`・`auth`・`access`・`audit`・`invitation`・`common/testsupport` はファイル名を確かめた）
  - `frontend/src/`（上に挙げたファイルのほかは一覧と `import` の検索だけ。`features/invitation/InvitationList.tsx` は冒頭の注記と `import`、`shared/api-client/apiClient.ts` と `features/admin/adminAreaStatus.ts` は 401・403 の検索だけ）
  - `frontend/e2e/`（一覧と `100-app-text-contrast.e2e.ts` の既知の違反の一覧の行、`support/registeredUser.ts` の冒頭だけ）
  - `build.gradle.kts`（タスクの登録と `dependsOn` の検索だけ）
  - `.github/workflows/ci.yml`（段の名前と `run` の検索だけ）
  - `README.md`（見出しとロックの行の検索だけ）
  - `Dockerfile`・`compose.yaml`（イメージの行だけ）
  - `backend/gradle.lockfile`・`frontend/package-lock.json`（主な部品の版の検索だけ）
  - `vendor/make-you-chic-ui/`（固定先・直近3コミット・部品のディレクトリの一覧だけ）
  - `vendor/java-mustache-processor/`（固定先だけ）
  - `perf/`・`docker/`・`config/`・`.pre-commit-config.yaml`・`.gitleaks.toml`（読んでいない。名前だけ）

### Packages Found

バックエンドは Gradle の1つのサブプロジェクト `backend`（ルートパッケージ `cherry.mastersmith`）、画面は npm の `frontend/`。バックエンドの本体は 421 ファイル・約 30,100 行、テストは 318 ファイル（`*IT` 109・`*Test` 151、ほかは補助）。画面の本体は 128 ファイル・約 13,600 行、テストは 91 ファイル。

バックエンド（Java 25、`backend/src/main/java/cherry/mastersmith/`）:

- `user`（`domain`・`repository`・`service`・`web`）— 機能 — Java — 利用者（表 `users`）、パスワード、氏名と表示の設定、自分の設定の API（`/api/me/**`）、初期管理者の自動作成。約 3,370 行。**今回の中心**。
- `auth`（`domain`・`repository`・`service`・`web`）— 機能 — Java — ログイン、アカウントロック（表 `login_attempt_states`）、アクセストークン（HS256 の JWT）とリフレッシュトークン（表 `refresh_tokens`）、ログアウト、定期の削除。約 3,020 行。**今回の中心（ロックの解除）**。
- `access`（`domain`・`service`・`web`）— 機能 — Java — `/api/admin` と `/api/admin/**` を管理者だけにする決まり、401・403 の応答と拒否の出来事。DB を読まない。約 1,040 行。
- `audit`（`domain`・`repository`・`service`）— 機能 — Java — 各機能の出来事を確定の後に表 `audit_events` へ追記する。Web の層を持たない。約 1,590 行。
- `invitation`（`domain`・`repository`・`service`・`web`）— 機能 — Java — 招待・送り直し・取り消し・一覧（`/api/admin/invitations`）、登録の完了（`/api/registration/**`、ログインなし）。約 4,340 行。**今回の管理の画面の型**。
- `mail`（`config`・`domain`・`service`・`template`・`transport`）— 機能 — Java — SMTP の送信と Mustache のテンプレート。約 1,880 行。
- `appearance`（`config`・`service`・`web`）— 機能 — Java — インスタンスの見た目の設定（`GET /api/appearance`）。約 580 行。
- `targetdb`・`dsl`・`dslmanage` — 機能 — Java — 対象DB の読み取り、DSL の読み込み・検証・管理。今回は流し読み。
- `common`（`error`・`health`・`i18n`・`observability`・`security`・`web`）— 共通部品 — Java — Problem Details、ヘルスチェック、言語、観測（`TraceAspect` ほか）、安全の決まりの差し込み口の型、本文の大きさの上限。約 3,070 行。
- `config` — 全体の設定 — Java — Spring Security の連鎖（`SecurityConfig`）、SPA の配信、観測の設定。約 440 行。
- `MastersmithApplication.java` — 起動クラス。

DB の移行（`backend/src/main/resources/db/migration/`、Flyway）: V1（空の土台）・V2（`users`）・V3（`login_attempt_states`・`refresh_tokens`）・V4（`audit_events`）・V5（DSL の表）・V6（監査の DSL の列）・V7（利用者の氏名と表示の設定、監査の `target_user_id`・`target_invitation_id`）・V8（`invitations`）。

画面（React + TypeScript、`frontend/src/`）:

- `app/`（`registry`・`navigation`・`routing`・`layout`・`i18n`・`display-settings`・`login-state`・`login-handoff`・`pages`）— 骨組み — TS/React — 機能の登録の差し込み口、サイドバーとユーザーメニュー、振り分け、表示の設定。
- `features/admin` — 機能 — 管理の入口（`/admin`）。`features/auth` — ログインとログインの状態。`features/dsl` — DSL の管理（`/admin/dsl`）。`features/invitation` — 招待の管理（`/admin/invitations`）。`features/registration` — 登録の完了（ログインなし）。`features/preferences` — 自分の設定とパスワードの変更。
- `shared/`（`api-client`・`format`・`validation`）— 共通 — API の呼び出し（401 で1回だけ更新して送り直す）、日時の書式、氏名とパスワードの確かめ。
- `frontend/e2e/` — Playwright の E2E（`010`〜`100` の 10 本と補助 13 ファイル）。

取り込み（Git サブモジュール）:

- `vendor/make-you-chic-ui` — デザインシステム（npm の `file:` の依存）。固定先 `077f5b48ce84cd020ecec2d925836a085f9d9e11`。部品は Alert・AppShell・Avatar・Badge・Button・Card・Checkbox・Dropdown・FormField・Icon・Modal・RadioGroup・Select・Switch・Table・Tabs・Textarea・TextInput・Toast・Tooltip。
- `vendor/java-mustache-processor` — Mustache のエンジン（Gradle の composite build）。固定先 `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`（`0.1.0`）。

### Build System

- **Type**: Gradle（Kotlin DSL、ラッパー `gradlew`）を入口にし、画面は npm（Node.js 24、`package-lock.json`）を Gradle から呼ぶ。1コマンドの検査は `./gradlew verify`（`build.gradle.kts` 412 行の登録）。E2E は `./gradlew e2eTest`（422 行、`:backend:bootWar` に依存）で `verify` の外。
- **Config Files**:
  - `settings.gradle.kts`（依存の取得元を Maven Central だけにする `RepositoriesMode.FAIL_ON_PROJECT_REPOS`、`include("backend")`、`includeBuild("vendor/java-mustache-processor")` の composite build）
  - `build.gradle.kts`（ルート。`checkToolchain`・`vendorInstall`・`vendorBuild`・`vendorUnchanged`・`mustacheVendorUnchanged`・`frontendInstall`・npm の各段（`frontendTypecheck`・`frontendTest`・`frontendBuild` ほか）・`gitleaksScan`・`osvScan`・`verify`・`e2eTest`）
  - `backend/build.gradle.kts`（単体テスト `tasks.test` と結合テスト `integrationTest`（`*IT`）の分離、JaCoCo の全体とパッケージごとの下限、`packagesJudgedByTotal`、Spotless（palantir-java-format・ライセンスヘッダー）、SpotBugs ＋ FindSecBugs と関門 `spotbugsGate`、`bootWar` に画面の `dist` を同梱、`verifyDslSchemaInWar`、`resolveAndLockAll`）
  - `gradle/libs.versions.toml`（版の一覧）、`backend/gradle.lockfile`・`settings-gradle.lockfile`（解決した版の固定）
  - `frontend/package.json`・`frontend/package-lock.json`・`frontend/vitest.config.ts`・`frontend/vite.config.ts`・`frontend/tsconfig.json`・`frontend/playwright.config.ts`・`.prettierrc.json`・`.oxlintrc.json`・`eslint.config.js`・`.stylelintrc.json`
  - `backend/config/spotbugs-exclude.xml`（除外は1件だけ: `LoginAttemptStateRepository.lockDummyForUpdate` の `PREDICTABLE_RANDOM`、理由つき）
  - `config/npm-build-tools.txt`（OSV-Scanner で止める成果物の道具の一覧。名前だけ確かめた）
  - `Dockerfile`・`compose.yaml`・`.env.example`（実行環境）
- **Build Dependencies**:
  - `verify` → フロントエンドの段（`frontendInstall` → `vendorInstall` → `vendorBuild` → 型検査・リンタ・テスト・ビルド）→ `:backend` の検査・単体テスト・結合テスト・カバレッジ・`spotbugsGate` → `gitleaksScan`・`osvScan`（段の厳密な並びは今回確かめていない。前回の知識ベースの `architecture.md` の Interaction Diagrams 4 に記録がある）
  - `:backend:bootWar` → `:frontendBuild`（WAR に `dist` を同梱）
  - `backend` → `cherry.mastersmith:cherry-mustache-core`（composite build で `vendor/java-mustache-processor` の成果物に置き換わる）
  - `frontend` → `make-you-chic-ui`（`file:../vendor/make-you-chic-ui/packages/make-you-chic-ui`）

### APIs Discovered

REST（JSON、Spring MVC）。認証は `Authorization: Bearer <アクセストークン>`。セッションなし・CSRF の仕組みなし（`SecurityConfig`）。`/api/**` の既定はログインが必要（`access` の `AdminApiDefaultAccess`）。エラーは RFC 9457 Problem Details に `code` を足した形（`common/error`）。

- 認証 — `auth/web/AuthController.java` — 3 本: `POST /api/auth/login`（ログインなし）、`POST /api/auth/session/refresh`（Cookie のリフレッシュトークン、Origin の確かめ）、`POST /api/auth/session/logout`（同）
- 管理者の確かめ — `access/web/AdminCheckController.java` — 1 本: `GET /api/admin/check`（204）
- 自分の設定 — `user/web/MeController.java` — 3 本: `GET /api/me/preferences`、`PUT /api/me/preferences`、`POST /api/me/password`
- 招待の管理（管理者だけ） — `invitation/web/InvitationAdminController.java` — 4 本: `POST /api/admin/invitations`、`GET /api/admin/invitations?page=`、`POST /api/admin/invitations/{invitationId}/resend`、`POST /api/admin/invitations/{invitationId}/cancel`
- 登録の完了（ログインなし） — `invitation/web/RegistrationController.java` — 2 本: `POST /api/registration/verify`、`POST /api/registration/complete`
- DSL の管理（管理者だけ、根は `/api/admin/dsl`（`DslAdminPaths.ROOT`）） — `dslmanage/web/DslAdminController.java` — 10 本（状態・プレビューの取得・投入・破棄・生成・ダウンロード・適用・履歴・戻し・適用済みのダウンロード）
- 見た目の設定 — `appearance/web/AppearanceController.java` — 1 本: `GET /api/appearance`
- 問題の種類の説明 — `common/error/web/ProblemTypeController.java` — 1 本: `GET /api/problems/{slug}`（ログインなし）
- 静的: `GET /dsl/dsl-schema-v1.json`、`/actuator/health`（ログインなし）、SPA の配信
- **利用者の管理の API は無い**（利用者の一覧・管理者の印の変更・利用停止・ロックの解除のどれも、controller・service・repository のどの層にも無い。`UserRepository` は `findByEmail`・`existsByRedactedEmail`・`updatePreferences`・`updatePasswordHashIfUnchanged` と `JpaRepository` の標準の操作だけ）

アプリの中の部品の契約（前の Intent の契約 C2 など。今回の Intent で使う見込みのもの）:

- `UserAccountService`（`user.service`）: `verifyPassword`・`findById`・`existsByEmail`（文字列と `RedactedText` の2つ）・`findDisplayName`・`findLanguage`・`createUser`。戻り値は `UserSummary`（`toString` でメールアドレスと氏名を伏せる）。
- 出来事（Spring の `ApplicationEventPublisher`）: `UserCreatedEvent`（→ `auth` の `LoginAttemptStateInitializer` が同じトランザクションでロックの状態の行を作る）、`AuthenticationEvent`・`AdminAccessDeniedEvent`・`PasswordChangedEvent`・招待と登録の出来事・`DslOperationEvent`（→ `audit` の `AuditEventListener` が確定の後に記録）。
- 安全の決まりの差し込み口 `SecurityRuleContributor`（order は機能ごとに 100 台: auth 110・access 210・invitation 310・appearance 400 台。x00・x50 はテストの決まり）。

### Frameworks & Libraries

バックエンド（版は `gradle/libs.versions.toml` と `backend/gradle.lockfile` の検索で確かめた）:

- Java — 25 — 言語（ツールチェーン）。実行のイメージは `eclipse-temurin:25.0.4_7-jre-noble`
- Spring Boot — 4.1.1 — アプリの土台（webmvc・security・data-jpa・flyway・validation・aspectj・opentelemetry・oauth2-resource-server・mail・actuator）
- Spring Framework — 7.0.9 — （lockfile）
- Spring Security — 7.1.1 — 認証・認可（Bearer の検証は OAuth2 Resource Server）
- Nimbus JOSE + JWT — 10.9.1 — JWT（HS256）の発行と検証
- Hibernate ORM — 7.4.5.Final — JPA
- H2 — 2.4.240 — 内部DB（組み込み、単一インスタンス前提）。`-Dh2.compactThreads=1` をそろえる決まり（`project.md` の Tech Stack）
- Flyway — 12.4.0 — DB の移行
- Tomcat（組み込み） — 11.0.26 — 脆弱性のため Spring Boot の管理の版から上書き
- Jackson — 3.1.6 — 脆弱性のため Jackson の BOM で上書き
- logstash-logback-encoder — 9.0 — 構造化ログ（JSON）
- opentelemetry-logback-appender — 2.28.1-alpha — 外部エクスポート（固定の理由は `libs.versions.toml` の注記）
- SnakeYAML 2.7・networknt json-schema-validator 3.0.6 — DSL の読み込みと検証
- JDBC ドライバー（mysql-connector-j・mariadb-java-client・postgresql） — Spring Boot の BOM — 対象DB
- cherry-mustache-core — 0.1.0 — メールのテンプレート（サブモジュール）
- テスト: JUnit Jupiter 6.0.3、Spring Boot Test、Spring Security Test、ArchUnit 1.5.1、jqwik 1.10.1、Testcontainers 2.0.5（対象DB だけ）、subethasmtp 7.2.2（JVM の中の SMTP の受け手）
- 静的検査: Spotless 8.10.3 ＋ palantir-java-format 2.98.0、SpotBugs 4.10.4（プラグイン 6.5.12）＋ FindSecBugs 1.14.0、JaCoCo 0.8.15

画面（版は `frontend/package.json` と `package-lock.json` の検索）:

- React — 19.3.0（lockfile。`package.json` は `^19.2.8`）— 画面
- react-router — 8.4.0（lockfile）— 振り分け
- i18next 26.4.2・react-i18next — 日英の文言
- make-you-chic-ui — サブモジュールの固定先 `077f5b4` — デザインシステム
- Vite 8.3.1・TypeScript 6.0.3 — ビルドと型
- Vitest 5.0.2 ＋ `@vitest/coverage-v8`、Testing Library（react・dom・user-event・jest-dom）、jsdom、vitest-axe・axe-core、fast-check — テスト
- Playwright 1.63.0 — E2E
- Prettier・oxlint・ESLint（react-hooks）・Stylelint — 書式とリンタ

### Test Coverage

- **Test Directories**:
  - `backend/src/test/java/cherry/mastersmith/`（本体と同じパッケージ構成。`*Test` は単体、`*IT` は Spring と組み込みの H2 を起動する結合テスト。`testsupport` の下に機能ごとの補助）
  - 今回の Intent に関わるもの（ファイル名で確かめた）:
    - `user/`: `UserAccountServiceTest`・`UserCreationIT`・`UserRepositoryIT`・`UserSchemaIT`・`V7MigrationIT`・`V7BackwardCompatibilityIT`・`InitialAdminInitializerTest`・`InitialAdminIT`・`MePasswordApiIT`・`MePreferencesApiIT`・`MeSecretLeakIT`、補助 `user/testsupport/TestUserAccounts`・`MeApi`・`TestPasswordChangeBarrier`
    - `auth/`: `LockPolicyTest`・`LoginServiceTest`・`LoginConcurrencyIT`・`LoginAttemptStateRepositoryIT`・`RefreshTokenRepositoryIT`・`TokenRefreshServiceTest`・`RefreshConcurrencyIT`・`AccessTokenApiIT`・`LoginApiIT`・`TokenApiIT`・`AuthEventsIT`・`AuthSecretLeakIT`・`AuthBoundaryArchitectureTest`、補助 `auth/testsupport/MutableClock`・`AuthApi`・`AuthTestTokens`・`CapturedAuthenticationEvents`
    - `access/`: `AdminAccessIT`（401・403・200）・`AdminPathBoundaryIT`・`ApiDefaultAccessIT`・`AccessDeniedEventsIT`・`AccessSecretLeakIT`、補助 `access/testsupport/AdminTestUsers`・`PublicApiTestRules`
    - `audit/`: `AuditEventFactoryTest`・`AuditEventListenerTest`・`AuditAuthenticationEventsIT`・`AuditRollbackIT`・`AuditWriteFailureIT`・`AuditTraceIdIT`・`AuditSecretLeakIT`・`PasswordChangedAuditIT`・`AuditBoundaryArchitectureTest`、補助 `audit/testsupport/AuditRows`
    - `invitation/`: `InvitationAdminApiIT`・`InvitationAuditIT`・`InvitationConcurrencyIT`・`InvitationPagingTest`・`InvitedPersonAuthenticationIT`・`InvitationSecretLeakIT`・`InvitationBoundaryArchitectureTest`
    - 全体: `ArchitectureTest`（web は repository を使わない・トランザクションは service だけ・controller はエンティティを返さない・コンストラクター注入だけ・Lombok なし）
    - 共通の補助: `common/testsupport/`（`LogEvents`・`JsonLogRecords`・`HttpTestClient`・`TestDatabase` ほか）
  - `frontend/src/**/*.test.ts(x)`（対象と同じ場所、91 ファイル）
  - `frontend/e2e/*.e2e.ts`（10 本。`030-admin-access` は管理の入口、`060-invitation-accessibility` は招待の管理の画面の axe、`090-invitation-registration-flow` は代表の流れ）
- **Test Frameworks**: JUnit Jupiter・Spring Boot Test・ArchUnit・jqwik・Testcontainers・subethasmtp（バックエンド）、Vitest・Testing Library・vitest-axe・fast-check（画面）、Playwright ＋ axe（E2E）
- **Coverage Config**: present
  - バックエンド: JaCoCo。単体と結合を合わせた全体で行 80%・分岐 70%、加えてパッケージごとに同じ下限（`backend/build.gradle.kts` 236〜270 行）。パッケージごとの下限から外して全体で判定する `packagesJudgedByTotal`（221〜234 行）は 12 個: `access.domain`・`access.service`・`audit.repository`・`auth.domain`・`auth.repository`・`common.error.domain`・`common.error.service`・`common.error.web`・`common.health`・`common.i18n.domain`・`common.observability`・`common.web`。`user.*`・`invitation.*`・`auth.service`・`auth.web`・`access.web`・`audit.domain`・`audit.service` はパッケージごとの下限の対象。
  - 画面: `frontend/vitest.config.ts` の `coverage.thresholds`（`lines: 80`・`branches: 70`）。除外は `src/main.tsx`・`*.d.ts`・テスト自身。

### Code Quality Indicators

- **Linting**:
  - Java: Spotless（palantir-java-format、インデント4・1行120文字、`/* */` のライセンスヘッダー）、SpotBugs ＋ FindSecBugs と関門 `spotbugsGate`（priority 1 と、`SQL_` で始まるもの・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority によらず失敗。`backend/build.gradle.kts` 295〜368 行）、ArchUnit の構造の検査（全体の `ArchitectureTest` と機能ごとの `*BoundaryArchitectureTest` 9 本）
  - 画面: Prettier（`frontend/.prettierrc.json`）、oxlint（`frontend/.oxlintrc.json`、correctness を error）、ESLint（`frontend/eslint.config.js`、react-hooks）、Stylelint（`frontend/.stylelintrc.json`）、`tsc --noEmit`、ライセンスヘッダーの検査（`frontend/scripts/check-license-header.mjs`）、バンドルの大きさ（`scripts/check-bundle-size.mjs`）
  - 秘密情報: Gitleaks（`.gitleaks.toml`、pre-commit と `verify`）。依存: OSV-Scanner（`verify` の `osvScan`）と Dependabot（`.github/dependabot.yml`、gradle・npm・github-actions・docker）
- **CI/CD**: `.github/workflows/ci.yml` の1本。`develop` へのプッシュ（ほかにタグ `v*` と手動の実行、`ci.yml` 22〜23 行）で `./gradlew verify` を実行し、WAR を成果物として保存、タグのときはリリースに添付する。Actions はコミットのハッシュで固定。E2E は CI の外（`team.md`）。
- **Documentation**:
  - `README.md`（1,082 行）: 取得と準備、1コマンドの検査、E2E、起動、戻し方、環境変数（ロックのしきい値 `MASTERSMITH_AUTH_LOCK_THRESHOLD` 既定 5・時間 `MASTERSMITH_AUTH_LOCK_DURATION` 既定 30m、407〜408 行）、API のアクセス制御、監査ログ、各機能の節、ライセンス。利用者の管理（一覧・停止・解除）の節は無い。
  - コードのコメント: Javadoc・JSDoc は日本語で、設計の文書の番号（BR・NFR・契約 C・ADR）と決定の経緯を丁寧に書く。`TODO`・`FIXME`・`HACK` は本体（`backend/src/main`・`frontend/src`）に 0 件。
  - `frontend/src/features/README.md`: 機能の登録・表示の設定・入力の確かめの関数・ユーザーメニューの使い方。

### Technical Debt Signals

- 抑止の注記は少ない: `@SuppressWarnings` が本体に 2 件（`common/observability/SanitizingLogRecordExporter.java` 141 行の `deprecation`、`common/error/web/DefaultErrorResponseWriter.java` 79 行の `unchecked`）、`oxlint-disable` が1件（`frontend/src/types/vitest-axe-matchers.d.ts` 23 行、型の宣言のため）、SpotBugs の除外は理由つきの1件だけ。
- `audit` が機能を足すたびに大きくなる: `audit/service/AuditEventListener.java`（406 行）・`audit/domain/AuditEvent.java`（374 行）・`audit/domain/AuditEventFactory.java`（363 行）が、機能ごとの出来事の受け取り・写し取りを1か所に積み上げる形。`audit` は各機能の `domain`（と `user.domain` など）の出来事の型に依存する。網羅の `switch` で種類の足し漏れはコンパイルで気づける作りだが、新しい出来事の型を足すたびに3ファイルと境界の検査を直すことになる。
- 要求の文脈の読み取りが機能ごとに複製されている: `user/web/MeRequestContextResolver.java`（111 行）と `invitation/web/InvitationRequestContextResolver.java`（98 行）が同じ形（`Authentication#getName()` を数として読む、送り手の情報を作る、401 の問題の種類を code で起動時に引く）。`invitation` は `auth` に依存できない境界（`InvitationBoundaryArchitectureTest` 50 行）のため複製している。新しい管理の機能を足すと3つ目になりやすい。
- 画面の管理の機能が大きめの hook を持つ: `features/invitation/useInvitationAdmin.ts`（472 行）・`features/dsl/useDslAdmin.ts`（492 行）。同じ型の管理の画面を足すと、似た状態の管理（読み込み・ページ送り・トースト・フォーカスの行き先）を複製しやすい（見立て）。
- 内部DB は組み込みの H2 で、単一インスタンスが前提（行の排他 `SELECT ... FOR UPDATE` と待ちの上限 3 秒、`auth/repository/LoginAttemptStateRepository.java`）。複数インスタンスにすると前提が崩れる（前の Intent からの既知の決定）。
- ロックの状態の表 `login_attempt_states` は `users` への外部キーを持たず、ダミーの行（ID -1〜-8、V3 で作成）と同じ表に置かれる（`db/migration/V3__u2_authentication.sql`）。利用者ごとの行は、利用者の作成の知らせで作られるほか、行が無ければ初めてのログインのときに作られる（`auth/service/LoginService.java` 163〜165 行と `createRow`）。行の無い利用者がありうる。
- パスワードの変更（`user/service/UserPreferencesService.java`）は、ほかの端末のリフレッシュトークンを無効にしない（`PasswordChangedEvent` を受けるのは `audit` だけ。`auth` に受け取り側が無い）。利用者のリフレッシュトークンをまとめて無効にする問い合わせも `auth/repository/RefreshTokenRepository.java` に無い（1件ずつの `revokeIfActive` 50 行と、期限切れの削除 `deleteExpiredBefore` 64 行だけ）。
- 前回の知識ベースとのずれ（STALE の中身）: 知識ベースの `component-inventory.md` は make-you-chic-ui の固定先を `310e1ec`、E2E 100 の既知の違反を2件、警報 `ms-check-p95` のしきい値を 300 ms、初期管理者の INFO のキーを `email` と記録するが、今の HEAD では固定先 `077f5b4`（`git submodule status`）、既知の違反の一覧は空（`frontend/e2e/100-app-text-contrast.e2e.ts` 73 行）、キーは `maskedEmail`（`user/service/InitialAdminInitializer.java` 92・100・103 行）で、`ms-check-p95` は 500 ms に上げられた（コミット `27be598` の件名。ファイルは今回読んでいない）。前の Intent 260929-log-deps-cleanup の直しが反映された結果で、今回の Intent の範囲の外。

### 今回の Intent に関わる所見（S-1〜S-9）

所見の番号 S は、この走査の中だけの仮の番号（知識ベースの K の番号は、アーキテクトの統合で振り直す想定）。パスは `backend/src/main/java/cherry/mastersmith/` を省いて書く（画面・テスト・移行のファイルは省かない）。

#### S-1 利用停止を表す状態が無い（事実）

- `users` の列は `user_id`・`email`・`password_hash`・`admin_flag`・`created_at`（V2）と `display_name`・`language`・`theme`・`font_size`（V7）だけ。停止・無効・状態の列は無い（`backend/src/main/resources/db/migration/V2__u2_user_account.sql`・`V7__u2_user_preferences.sql`、`user/domain/User.java` 39〜74 行）。
- 利用者を削除する操作も無い。`refresh_tokens.user_id`（V3）と `invitations.invited_by_user_id`・`completed_user_id`（V8）が `users` を外部キーで参照し、DSL の表（V5）と監査（V6・V7）も利用者 ID を持つため、削除ではなく状態で止める形が前提になりやすい（見立て）。
- 利用停止を効かせるには、少なくとも次の3つの入口で状態を見る必要がある（事実: どれも今は「利用者がいるか」だけを見る）。
  1. ログイン: `UserAccountService.verifyPassword`（`user/service/UserAccountService.java` 91〜105 行）→ `LoginService.decide`（`auth/service/LoginService.java` 157〜198 行）。失敗は理由によらず `AUTHENTICATION_FAILED`（401）で、理由は出来事（`LoginFailureReason`: `USER_NOT_FOUND`・`PASSWORD_MISMATCH`・`ACCOUNT_LOCKED`）にだけ載る。停止の理由を足すと `LoginFailureReason` → `AuditFailureReason` の網羅の `switch`（`audit/domain/AuditEventFactory.java`）に波及する。
  2. トークンの更新: `TokenRefreshService.refresh`（`auth/service/TokenRefreshService.java` 89 行）は `findById` の有無だけで判定する。
  3. アクセストークンの認証: `AccessTokenAuthenticationProvider.authenticate`（`auth/web/AccessTokenAuthenticationProvider.java` 54〜62 行）は要求ごとに `findById` で DB から読む。いなければ `TokenFailureReason.USER_NOT_FOUND`。停止の区分を足すと `AccessDeniedReason.of(TokenFailureReason)`（`access/domain/AccessDeniedReason.java` 47〜54 行、網羅の `switch`）と監査の理由に波及する。
- 要求ごとに DB から読むため、停止はアクセストークンの有効期限（既定 5 分、`auth/service/AuthProperties.java`）を待たずに次の要求から効かせられる（見立て。3 の入口で状態を見れば）。ログアウトはアクセストークンを失効させない決定（`project.md` の Decided）との関係は要件で決める。
- 停止した利用者のリフレッシュトークンをまとめて無効にする問い合わせは無い（Technical Debt Signals を参照）。
- 招待: `InvitationService` は招待先のメールアドレスの利用者がいれば `INVITATION_EMAIL_REGISTERED`（409）にする（`existsByEmail`）。停止した利用者のメールアドレスも「登録済み」として招待できない（事実: 状態を見ない）。

#### S-2 ロックの状態は `auth` にあり、時刻で決まる（事実）

- 表 `login_attempt_states`（V3）: `subject_id`（利用者 ID、または -1〜-8 のダミー）・`consecutive_failures`・`locked_until`。`users` への外部キーは無い。
- ロック中の判定は `now < locked_until`（`auth/domain/LockPolicy.java` 52 行）。解除時刻を過ぎた後は、次の試みで失敗回数を 0 から数え直す（58 行）。つまり解除時刻を過ぎた行は、次のログインまで `locked_until` と失敗回数が残ったままで、表の値だけを見て「ロック中」と判断すると誤る。一覧でロックを示すには、注入した時計（`Clock`）と比べる必要がある。
- しきい値と時間は設定（`mastersmith.auth.lock.threshold` 既定 5、`duration` 既定 30m。`auth/service/AuthProperties.java`）。
- 行の書き込みは明示の更新 `update(subjectId, failures, lockedUntil)`（`auth/repository/LoginAttemptStateRepository.java` 120〜131 行）と、排他つきの読み取り `lockForUpdate`（65〜74 行、待ちの上限 3 秒）。ロックの解除は、既存の口で「排他つきで読み、`update(id, 0, null)`」と書ける（見立て。ログインの判定と同じ行の排他で順番がそろう）。
- 利用者の行は無いことがある（S-2 の前提、Technical Debt Signals）。一覧・解除は「行が無い＝ロックしていない」として扱う必要がある。
- 既存のロックの解除は時間の経過だけ（`README.md` の環境変数の表 407〜408 行）。管理者による解除の操作・監査の種類は無い。

#### S-3 境界の決まり: `user` は `auth` を知らない（事実）

- `AuthBoundaryArchitectureTest`（`backend/src/test/java/cherry/mastersmith/auth/AuthBoundaryArchitectureTest.java`）: `user..` は `auth..` に依存しない（62 行 `userDoesNotDependOnAuth`）、`auth..` は `user.repository..` を使わない（48 行 `authUsesOnlyUserServiceOperations`）、`auth..`・`user..` は `audit..` に依存しない（74 行）、パスワードのハッシュは `user` の外へ出さない（87 行）。
- `InvitationBoundaryArchitectureTest` 50 行: `invitation..` は `auth..`・`audit..` に依存しない。
- `AuditBoundaryArchitectureTest` 125 行: `audit` は Web の層を持たない（API を公開しない）。
- 帰結（見立て）: 利用者（`user` の表）とロックの状態（`auth` の表）を1つの一覧にまとめる処理は、`user` の側には置けない。置き場の候補は、`auth` の側（`user` の service の口を使う）、新しい機能のパッケージ（`user` と `auth` の service の口を使う）、または知らせ（Spring の出来事）による分担。どれにするかと、ArchUnit の境界の検査を足す・変える扱い（`team.md` の Code Style: 緩める・消すときは計画に明記し承認を得る）は設計の段で決める。
- 逆向きの知らせの前例: `UserCreatedEvent`（`user`）→ `LoginAttemptStateInitializer`（`auth`、`@EventListener` と `Propagation.MANDATORY`、同じトランザクション）。

#### S-4 管理者の印は要求ごとに DB から読まれる（事実）

- `AccessTokenAuthenticationProvider` が DB の `admin_flag` から `AuthenticatedUser(userId, email, admin)` を作り（`auth/web/AccessTokenAuthenticationProvider.java` 61 行）、`AdminAuthorizationManager` がその値だけで判定する（`access/web/AdminAuthorizationManager.java` 40〜45 行）。トークンは管理者の印を持たない（`sub`・`iat`・`exp` だけ、`auth/service/AccessTokenService.java`）。印を変えれば、サーバー側では次の要求から効く。
- 画面の `LoginState.admin` は、ログインと更新の応答の `user.admin`（`auth/web/CurrentUserResponse.java`）から作り、モジュールの変数に持つ（`frontend/src/features/auth/authSession.ts`）。印が変わっても、画面は次の更新（アクセストークンの期限の 401 → 更新）かログインまで古い値のまま。印を外された人が管理の画面の API を呼ぶと 403 で、管理の入口は 403 を「ページが見つかりません」として扱う（`frontend/src/features/admin/adminAreaStatus.ts` 23〜27 行）。
- 「最後の管理者」を守る仕組みは無い。初期管理者の自動作成は、設定のメールアドレスの利用者が「いるか」だけを見る（`user/service/InitialAdminInitializer.java` 91 行 `existsByEmail`）。すべての管理者の印を外す・停止すると、画面から管理者を戻す道が無くなる（見立て。DB を直接直すしかない）。自分自身の印を外す・自分を停止する操作の扱いも決まっていない。
- 管理者の印を変える口（`UserRepository` の更新の問い合わせ）は無い。既存の更新はどれも列を絞った `@Modifying` の問い合わせ（`updatePreferences`・`updatePasswordHashIfUnchanged`）で、全列を書かない決まり（`user/repository/UserRepository.java` の注記、BR3.4）。

#### S-5 一覧・ページ送り・結果の型の前例は `invitation` にある（事実）

- API の形: `GET /api/admin/invitations?page=`（page は文字列で受け、不正なら 400 `VALIDATION_FAILED`）、1ページ 20 件（`invitation/domain/InvitationPaging.java` 27 行）、応答は `items`・`page`・`size`・`total` ほか（`invitation/web/InvitationPageResponse.java`）。
- 操作は `POST /api/admin/invitations/{id}/resend`・`/cancel` の形（動詞の下位パス）。業務処理は結果の型（sealed interface、例 `CancelResult.Cancelled`・`NotFound`）を返し、controller が `switch` で業務エラーにする（`invitation/web/InvitationAdminController.java`）。
- 管理者の ID と送り手の情報は `InvitationRequestContextResolver` で読む（Technical Debt Signals の複製を参照）。
- 安全の決まり: `/api/admin/**` は `access` の決まりで管理者だけになるため、新しい管理の API は決まりを足さずに乗れる（`invitation` の管理の API も決まりを足していない。足しているのは公開の2本だけ、`invitation/web/InvitationSecurityContributor.java`）。
- 一覧の中の「招待した管理者の氏名」は、ページの行ごとに `UserAccountService.findDisplayName` を呼ぶ（利用者 ID ごとに1回、`invitation/service/InvitationService.java` 269・286 行）。利用者の一覧に同じ形を使うと、1ページ 20 件で利用者とロックの状態を別々に読むことになる（見立て。件数が少ない前提なら問題になりにくい）。
- `UserRepository` に一覧・件数の問い合わせは無い。`users` の索引は主キーとメールアドレスの一意の制約だけ（V2）。

#### S-6 監査の列は足りている、種類と出来事は無い（事実）

- `audit_events` は `actor_user_id`（V6）・`target_user_id`（V7）を持ち、`AuditEvent.withTarget(...)` で操作した人と対象の利用者を記録できる（`audit/domain/AuditEventFactory.java` のパスワードの変更・招待の例）。管理者の利用者の操作のために列を足す必要は無い見込み。
- `AuditEventType`（`audit/domain/AuditEventType.java`）に、管理者の印の変更・利用停止・停止の解除・ロックの解除の種類は無い。列 `event_type` は `VARCHAR(32)`、`failure_reason` は `VARCHAR(32)`（V4）で、名前の長さに上限がある。
- 記録の仕組み: 出来事を業務のトランザクションの中で知らせ、`AuditEventListener` が確定の後（`AFTER_COMMIT`、`fallbackExecution = true`）に新しいトランザクション（`AuditEventRecorder`、`REQUIRES_NEW`）で追記する。1件の要求で接続を2本使う形で、同時の数がプールの上限に達する場合は負荷の試験で確かめる決まり（`project.md` の Corrections）。
- 監査を見る画面・API は無い（`audit` は Web の層を持たない決まり）。

#### S-7 触るとカバレッジの作業が付くパッケージ（事実）

- `packagesJudgedByTotal`（`backend/build.gradle.kts` 221〜234 行）に `auth.domain`・`auth.repository`・`access.domain`・`access.service`・`audit.repository` がある。`team.md` の Testing Posture により、これらに手を入れる Bolt では、テストを足してそのパッケージの下限（行 80%・分岐 70%）を満たし、一覧から外す必要がある（カバレッジは `:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` で実測して記録）。
- S-1・S-2 の直しは `auth.domain`（`LoginFailureReason`・`TokenFailureReason`）・`auth.repository`（ロックの解除・リフレッシュトークンの無効化の問い合わせ）・`access.domain`（`AccessDeniedReason`）に及びうる（見立て）。前回の記録では `auth.repository` は単独で分岐 50.0% だった（`project.md` の学び、Intent 260923-dsl-schema-loader の時点。今の値は測っていない）。
- `user.*`・`invitation.*`・`auth.service`・`auth.web`・`audit.domain`・`audit.service` と新しいパッケージは、既にパッケージごとの下限の対象。

#### S-8 秘密と個人に関する値の決まりが一覧の応答とログに当たる（事実）

- `TraceAspect` は `web`・`service`・`domain`・`repository` の層のメソッドの引数と戻り値を、ロガーが TRACE のときに文字列にして出す（`common/observability/TraceAspect.java` 48〜56 行。`record` 自身と設定のクラスは対象外）。そのため既存の型は `toString` でメールアドレスと氏名を伏せる（`user/service/UserSummary.java`・`NewUser.java`）か、`RedactedText` で受け渡す（`UserRepository.existsByRedactedEmail`、`UserAccountService.findDisplayName`）。利用者の一覧の型（要約の一覧・ページ）も同じ扱いが要る（`project.md` の Corrections の TraceAspect の学び）。
- `project.md` の Forbidden: メールアドレスをアプリのログとエラー応答に含めない（伏せ字 `EmailAddress.mask` は可）。一覧の応答（管理者に見せる画面）にメールアドレスを載せること自体は、招待の一覧が既に行っている（`InvitationPageResponse` の `email`）。
- 既存の漏えいの確かめ `*SecretLeakIT`（`AuthSecretLeakIT`・`AccessSecretLeakIT`・`AuditSecretLeakIT`・`InvitationSecretLeakIT`・`MeSecretLeakIT` ほか）と `common/testsupport/JsonLogRecords` の形が使える（ファイル名だけ確かめた）。

#### S-9 画面の差し込み口と部品（事実）

- 機能は `frontend/src/features/<featureId>/registration.ts` に `registration: FeatureRegistration` を名前付きで置くだけで読み込まれる（`frontend/src/app/registry/types.ts`、骨組みのファイルは書き換えない）。画面は `access: 'ADMIN'`・`layout: 'SHELL'`、サイドバーは `visibleWhen: 'ADMIN'`。今の順番は管理 200（`features/admin/registration.ts`）・DSL 210・利用者の招待 220（`features/invitation/registration.ts`）。表示を隠すのは表示の切り替えだけで、判定はサーバー（`types.ts` の注記）。
- 文言は機能ごとに日英をそろえ、鍵は `<featureId>.` で始める（`FeatureMessages`）。
- 招待の管理の画面が同じ型の見本: make-you-chic-ui の `Table`（ページ送りつき）・`Badge`・`Button`・`Modal`（確かめ）・`Toast`・`Alert` を使う（`frontend/src/features/invitation/InvitationList.tsx` 25 行ほか）。`Switch`・`Checkbox`・`Select` も make-you-chic-ui にある。
- API の呼び出しは `shared/api-client/apiClient.ts` の `apiRequest`（401 `AUTHENTICATION_REQUIRED` で1回だけ更新して送り直す）。応答は項目を1つずつ確かめて決まった形の値にする（`features/invitation/api/invitationApi.ts` の `toInvitation`）。
- E2E: 代表の流れは Intent ごとに1本まで（`team.md`）。管理者のログインと利用者を作る補助がある（`frontend/e2e/support/adminLogin.ts`・`invitationSeed.ts`・`registeredUser.ts`）。axe の検査（`060`〜`080` の形）と文字のコントラスト（`100`）は流れの本数に数えない読み方（`project.md` の Corrections）。

## Handoff Summary

- **Intent-relevant finding**: 利用者の管理（一覧・管理者の印・利用停止・ロックの解除）の API・業務処理・問い合わせ・画面は、今のコードにどれも無い。とくに次の3点が設計の前提を決める。
  1. 利用停止を表す列が `users` に無い（`backend/src/main/resources/db/migration/V2__u2_user_account.sql`・`V7__u2_user_preferences.sql`、`user/domain/User.java`）。停止を効かせる入口は、ログインの照合（`user/service/UserAccountService.java` 91〜105 行 → `auth/service/LoginService.java` 157〜198 行）、トークンの更新（`auth/service/TokenRefreshService.java` 89 行）、アクセストークンの認証（`auth/web/AccessTokenAuthenticationProvider.java` 54〜62 行）の3つで、どれも今は「利用者がいるか」だけを見る（S-1）。
  2. ロックの状態は `auth` の表 `login_attempt_states` にあり、ロック中かどうかは `now < locked_until` の時刻の比べで決まる（`auth/domain/LockPolicy.java` 52 行）。行が無い利用者がありうる。解除は既存の `lockForUpdate`・`update`（`auth/repository/LoginAttemptStateRepository.java` 65〜74・120〜131 行）で書ける見込み（S-2）。
  3. `user` は `auth` に依存できない（`backend/src/test/java/cherry/mastersmith/auth/AuthBoundaryArchitectureTest.java` 62 行）。利用者とロックの状態を合わせた一覧の置き場は `user` の外になる（S-3）。
- **Risks / follow-up**:
  - 最後の管理者を失う操作（すべての管理者の印を外す・停止する、自分自身の印を外す・自分を停止する）を止める仕組みが無い。初期管理者の自動作成はメールアドレスの有無だけを見るため、回復の道にならない（`user/service/InitialAdminInitializer.java` 91 行、S-4）。要件で決める必要がある。
  - 管理者の印の変更はサーバー側では次の要求から効くが、画面の `LoginState.admin` は次の更新かログインまで古い（`frontend/src/features/auth/authSession.ts`、S-4）。
  - 停止した利用者のリフレッシュトークンをまとめて無効にする問い合わせが無い（`auth/repository/RefreshTokenRepository.java`）。停止の後に更新で入り直せないことは、S-1 の入口 2 で状態を見るか、トークンを無効にするかで守る必要がある。アクセストークンは要求ごとの DB の読み取り（入口 3）で止められる見込み。
  - 停止の区分を足すと、網羅の `switch`（`access/domain/AccessDeniedReason.java` 47〜54 行、`audit/domain/AuditEventFactory.java` の理由の写し取り）と、応答で理由を推測させない決まり（ログインは理由によらず `AUTHENTICATION_FAILED`）に波及する。
  - 監査の列（`actor_user_id`・`target_user_id`）はあるが、管理の操作の種類（`AuditEventType`）と出来事の型は無い。`event_type`・`failure_reason` は 32 文字まで（V4）。`audit` の3ファイル（リスナー・エンティティ・写し取り）と境界の検査を足すことになる（S-6）。
  - `auth.domain`・`auth.repository`・`access.domain`・`access.service`・`audit.repository` は `packagesJudgedByTotal` にあり、手を入れる Bolt ではテストを足して下限を満たし一覧から外す作業が付く（`backend/build.gradle.kts` 221〜234 行、`team.md`、S-7）。
  - 一覧の応答・業務処理の型は `TraceAspect` の TRACE に文字列で出るため、メールアドレスと氏名を伏せる型にする（`common/observability/TraceAspect.java` 48〜56 行、S-8）。
  - 停止した利用者のメールアドレスは、今の招待の確かめでは「登録済み」として扱われ招待できない（S-1）。停止と再招待の関係を要件で決める。
  - 既存の ArchUnit の境界テストを緩める・変える場合は、コード生成の計画に明記して依頼者の承認を得る（`team.md` の Code Style）。
  - 知識ベース（STALE）の記録は、make-you-chic-ui の固定先・E2E 100 の既知の違反・`ms-check-p95` のしきい値・初期管理者のログのキーについて今の HEAD とずれている（Technical Debt Signals の最後の項目）。全体の読み直しで置き換える前提。
