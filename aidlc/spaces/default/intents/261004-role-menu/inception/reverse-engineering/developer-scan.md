# 開発担当のコードスキャン（Intent 261004-role-menu）

- Intent: `261004-role-menu`（F: ロールベースの権限の管理、I: メニュー・ナビゲーション（N階層））。scope classic、Brownfield、深さ Standard。
- 対象: mastersmith2（リポジトリのルート、単一のリポジトリ）。記録するコミットは `d5aea52b6b2df7d473a16394e66c77453bd591f0`（`develop` の HEAD、読み取りだけの `git rev-parse HEAD` で確かめた）。
- 走査の広さ: 依頼者の選択は Full rescan（スナップショットの paths は `./`）。深さ Standard のため、全体を流し読みで把握し、今回の Intent に関わる範囲（認可・利用者・監査・認証・画面の骨組み・make-you-chic-ui のナビゲーション・Flyway・ArchUnit・DSL のメニューの定義）を深く読んだ。
- 確かめ方: ファイルの読み取り（Read・grep・読み取りだけのシェル）だけ。Gradle・npm・Docker・git の書き込みの操作は行っていない。`.env`・鍵ファイル・Git 管理外の参考資料は開いていない。メールアドレス・秘密の値は写していない。
- 書き方: 「事実」はソースで確かめたこと（パスと行の番号を添える）、「仮説」は未検証の見立てとして分けて書く。

## Developer Code Scan Results

### Scan Coverage

- **Analyzed deeply**（実際に読んで理解したもの。ファイルの単位。ディレクトリは中のファイルをすべて読んだもの）:
  - `backend/src/main/java/cherry/mastersmith/access/domain/AdminPaths.java`
  - `backend/src/main/java/cherry/mastersmith/access/domain/AccessDeniedReason.java`
  - `backend/src/main/java/cherry/mastersmith/access/domain/AccessProblemTypes.java`
  - `backend/src/main/java/cherry/mastersmith/access/web/AdminAuthorizationManager.java`
  - `backend/src/main/java/cherry/mastersmith/access/web/AdminSecurityContributor.java`
  - `backend/src/main/java/cherry/mastersmith/access/web/AdminApiDefaultAccess.java`
  - `backend/src/main/java/cherry/mastersmith/access/web/AdminCheckController.java`
  - `backend/src/main/java/cherry/mastersmith/access/web/AdminAccessDeniedHandler.java`
  - `backend/src/main/java/cherry/mastersmith/common/security/`
  - `backend/src/main/java/cherry/mastersmith/config/SecurityConfig.java`
  - `backend/src/main/java/cherry/mastersmith/auth/domain/AuthenticatedUser.java`
  - `backend/src/main/java/cherry/mastersmith/auth/web/AuthenticatedUserToken.java`
  - `backend/src/main/java/cherry/mastersmith/auth/web/AccessTokenAuthenticationProvider.java`
  - `backend/src/main/java/cherry/mastersmith/auth/web/AuthSecurityContributor.java`
  - `backend/src/main/java/cherry/mastersmith/auth/web/CurrentUserResponse.java`
  - `backend/src/main/java/cherry/mastersmith/auth/web/TokenResponse.java`
  - `backend/src/main/java/cherry/mastersmith/auth/service/AccessTokenService.java`（発行と検証の 83〜140 行）
  - `backend/src/main/java/cherry/mastersmith/appearance/web/AppearanceSecurityContributor.java`（決まりの部分）
  - `backend/src/main/java/cherry/mastersmith/invitation/web/InvitationSecurityContributor.java`（決まりの部分）
  - `backend/src/main/java/cherry/mastersmith/user/domain/User.java`（列の定義と作る口）
  - `backend/src/main/java/cherry/mastersmith/user/service/UserSummary.java`
  - `backend/src/main/java/cherry/mastersmith/user/repository/UserRepository.java`（問い合わせの一覧）
  - `backend/src/main/java/cherry/mastersmith/useradmin/domain/AdminOperation.java`
  - `backend/src/main/java/cherry/mastersmith/useradmin/domain/RejectionReason.java`
  - `backend/src/main/java/cherry/mastersmith/useradmin/service/UserAdminService.java`（250〜398 行。管理者の行の排他と判定の流れ）
  - `backend/src/main/java/cherry/mastersmith/useradmin/web/AdminUser.java`
  - `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java`
  - `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java`（受け取りの一覧）
  - `backend/src/main/java/cherry/mastersmith/dsl/domain/DslMenuItem.java`
  - `backend/src/main/java/cherry/mastersmith/dsl/domain/DslModel.java`（メニューの項目）
  - `backend/src/main/java/cherry/mastersmith/dsl/domain/DslFormat.java`（上限の定数）
  - `backend/src/main/java/cherry/mastersmith/dsl/service/ActiveDslModelProvider.java`
  - `backend/src/main/java/cherry/mastersmith/dsl/validate/DslSemanticValidator.java`（メニューの検証の 78〜117 行）
  - `backend/src/main/java/cherry/mastersmith/dslmanage/generate/DslTreeBuilder.java`（既定のメニューの生成の 60〜84 行）
  - `backend/src/main/resources/dsl/dsl-schema-v1.json`（`menus`・`menuItem` の定義）
  - `backend/src/main/resources/db/migration/`（V1〜V9 のすべて）
  - `backend/src/test/java/cherry/mastersmith/ArchitectureTest.java`（決まりの一覧）
  - `backend/src/test/java/cherry/mastersmith/useradmin/UserAdminBoundaryArchitectureTest.java`（決まりの一覧）
  - `backend/src/test/java/cherry/mastersmith/auth/AuthBoundaryArchitectureTest.java`（決まりの一覧）
  - `backend/build.gradle.kts`（カバレッジの検証と `packagesJudgedByTotal`）
  - `gradle/libs.versions.toml`
  - `frontend/src/app/registry/types.ts`
  - `frontend/src/app/registry/registrationModules.ts`
  - `frontend/src/app/registry/loadRegistrations.ts`
  - `frontend/src/app/registry/validateRegistrations.ts`
  - `frontend/src/app/navigation/navigationItems.ts`
  - `frontend/src/app/layout/ShellLayout.tsx`
  - `frontend/src/app/routing/decideRoute.ts`
  - `frontend/src/app/routing/AppRouter.tsx`
  - `frontend/src/app/admin-forbidden/AdminForbiddenProvider.tsx`
  - `frontend/src/app/pages/HomePage.tsx`
  - `frontend/src/shared/api-client/adminForbidden.ts`
  - `frontend/src/features/auth/loginStateProvider.ts`
  - `frontend/src/features/admin/registration.ts`
  - `frontend/src/features/dsl/DslMenuTree.tsx`（16〜60 行）
  - `frontend/src/features/dsl/api/types.ts`（`MenuNode`）
  - `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/AppShell/AppShell.tsx`
  - `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/AppShell/Sidebar.tsx`
  - `vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Icon/registry.ts`（アイコンの名前の一覧）
- **Analyzed deeply（部品 ID）**: `access`・`common-security`・`config`・`auth`・`appearance`・`invitation`・`user`・`useradmin`・`audit`・`dsl`・`dslmanage`・`backend-test-support`・`build-and-verify`・`frontend-registry`・`frontend-app-core`・`frontend-app-layout-i18n`・`frontend-feature-auth`・`frontend-feature-admin`・`frontend-feature-dsl`・`make-you-chic-ui`
  - いずれも部品の一部だけを読んだ「一部」の深さ（例: `appearance`・`invitation` は `SecurityRuleContributor` の実装だけ、`dslmanage` は既定のメニューの生成だけ）。
  - `common-security` は前回の記録（`component-inventory.md`）に見出しがある ID をそのまま使う。新しい部品 ID は足していない。
- **Skimmed only**（ディレクトリの単位で目を通しただけ）:
  - `backend/src/main/java/cherry/mastersmith/access/service/`
  - `backend/src/main/java/cherry/mastersmith/auth/`（上で挙げたファイルの外。`LoginService`・`TokenRefreshService` は停止の判定の行だけ）
  - `backend/src/main/java/cherry/mastersmith/user/service/`・`user/web/`
  - `backend/src/main/java/cherry/mastersmith/useradmin/`（上で挙げたファイルの外）
  - `backend/src/main/java/cherry/mastersmith/audit/`（上で挙げたファイルの外）
  - `backend/src/main/java/cherry/mastersmith/common/`（`security` の外: `error`・`health`・`i18n`・`observability`・`paging`・`persistence`・`web`）
  - `backend/src/main/java/cherry/mastersmith/dsl/`・`dslmanage/`・`targetdb/`・`invitation/`・`mail/`・`appearance/`（上で挙げたファイルの外）
  - `backend/src/test/java/cherry/mastersmith/`（テストのファイル名・`testsupport` の一覧）
  - `frontend/src/features/`（上で挙げたファイルの外。各機能の `registration.ts` の経路とサイドバーの項目の値は grep で確かめた）
  - `frontend/src/shared/`・`frontend/src/app/display-settings/`・`frontend/src/app/i18n/`
  - `frontend/e2e/`（ファイルの一覧）
  - `frontend/package.json`・`frontend/vitest.config.ts`（版とカバレッジの下限）
  - `vendor/make-you-chic-ui/`（`AppShell`・`Icon` の外は部品の一覧と `Dropdown`・`Tabs` の props だけ）
  - `vendor/java-mustache-processor/`
  - `build.gradle.kts`（タスクの一覧）・`.github/workflows/ci.yml`（手順の一覧）
  - `compose.yaml`・`docker/`・`perf/`・`Dockerfile`・`README.md`（今回は読み直していない）

### Packages Found

バックエンド（Java、ルートパッケージ `cherry.mastersmith`。`src/main` に 480 ファイル、`src/test` に 380 ファイル）:

- `access`（domain・service・web）— 機能 — Java — 管理者のみの API（`/api/admin`・`/api/admin/**`）の判定、401・403 の処理、アクセス拒否の出来事の通知、`/api/**` の既定をログイン必須にする口
- `auth`（domain・repository・service・web）— 機能 — Java — ログイン・ロック・JWT（HS256）のアクセストークン・リフレッシュトークン・ログアウト・初期管理者の救済
- `user`（domain・repository・service・web）— 機能 — Java — 利用者（表 `users`）、表示の設定、パスワードの変更、初期管理者の作成、管理者の印・停止の列の書き換えの口
- `useradmin`（domain・service・web）— 機能 — Java — 利用者の管理の API（一覧・氏名と言語・印の付け外し・停止と再開・失敗回数の取り消し）
- `audit`（domain・repository・service）— 機能 — Java — 監査ログ（表 `audit_events`）。各機能の出来事を確定の後に記録
- `invitation`（domain・lock・repository・service・web）— 機能 — Java — 招待と登録の完了
- `mail`（config・domain・service・template・transport）— 機能 — Java — メールの送信
- `appearance`（config・service・web）— 機能 — Java — インスタンスの見た目（ブランドカラー）の公開
- `dsl`（domain・parse・service・validate）— 機能 — Java — DSL（YAML）の読み込み・検証・適用中のモデルの保持（メニューの木を含む）
- `dslmanage`（domain・generate・repository・service・web）— 機能 — Java — DSL の管理の API（プレビュー・適用・履歴・既定の DSL の生成）
- `targetdb`（config・domain・repository・service）— 機能 — Java — 対象DB（MySQL・MariaDB・PostgreSQL）のスキーマの読み取り
- `common`（error・health・i18n・observability・paging・persistence・security・web）— 共通 — Java — Problem Details、差し込み口、追跡、行の排他の失敗の扱いなど
- `config` — 設定 — Java — フィルターの連鎖（`SecurityConfig`）、Web、観測、転送ヘッダー

画面（TypeScript + React、`frontend/src/`。テスト 110 ファイル）:

- `app/`（registry・navigation・layout・routing・admin-forbidden・login-state・display-settings・i18n・pages）— 骨組み — 4つの差し込み口、サイドバーとユーザーメニューの項目の組み立て、振り分け、403 の画面（S6）
- `features/<featureId>/`（auth・admin・dsl・invitation・useradmin・registration・preferences の 7 機能）— 機能 — 各機能が `registration.ts` で骨組みに登録
- `shared/`（api-client・format・modal・paging・validation）— 共通

外部の取り込み:

- `vendor/make-you-chic-ui`（Git サブモジュール、固定先 `e82b651`）— デザインシステム — React + TypeScript — npm の `file:` の依存
- `vendor/java-mustache-processor`（Git サブモジュール）— Mustache のエンジン — Java — Gradle の composite build

### Build System

- **Type**: Gradle（Kotlin DSL）。ルートのタスク `verify` が1コマンドの検査の入口で、npm（フロントエンドの検査）・Gitleaks・OSV-Scanner も Gradle から呼ぶ。E2E は `e2eTest`（`verify` の外）。
- **Config Files**: `settings.gradle.kts`（取得元は Maven Central だけ、composite build）、`build.gradle.kts`（verify の段の並び: フォーマット → リンタ → ライセンスヘッダー → ビルド → 単体 → 結合 → カバレッジ → セキュリティ → 成果物）、`backend/build.gradle.kts`（`integrationTest`・JaCoCo・SpotBugs の関門・`bootWar`）、`gradle/libs.versions.toml`、`backend/gradle.lockfile`・`settings-gradle.lockfile`、`frontend/package.json`・`package-lock.json`・`vite.config.ts`・`vitest.config.ts`・`eslint.config.js`、`.github/workflows/ci.yml`（`./gradlew verify` を呼ぶ）、`.github/dependabot.yml`
- **Build Dependencies**: `backend` の WAR が `frontend` の `dist` を同梱する。`frontend` は `vendor/make-you-chic-ui/packages/make-you-chic-ui` に依存する（`file:`）。`backend` は composite build で `vendor/java-mustache-processor` を組む。

### APIs Discovered

REST（Spring MVC、`backend/src/main/java/cherry/mastersmith/`）。29 本（エラーの経路を除く）:

- 認証 — `auth/web/AuthController.java` — 3 本（`POST /api/auth/login`、`POST /api/auth/session/refresh`、`POST /api/auth/session/logout`）。ログインと更新の応答 `TokenResponse` に、ログイン中の利用者 `CurrentUserResponse`（`email`・`admin`・`displayName`・`language`・`theme`・`fontSize`）を含む。ログイン中の利用者を返す単独の API（`GET /api/me` など）は無い。
- 自分の設定 — `user/web/MeController.java` — 3 本（`GET`・`PUT /api/me/preferences`、`POST /api/me/password`）
- 管理者のみの確認 — `access/web/AdminCheckController.java` — 1 本（`GET /api/admin/check`、204）
- 利用者の管理 — `useradmin/web/UserAdminController.java` — 7 本（`GET /api/admin/users`、`PUT .../{userId}/profile`、`POST .../{userId}/grant-admin`・`revoke-admin`・`suspend`・`resume`・`reset-login-failures`）
- 招待の管理 — `invitation/web/InvitationAdminController.java` — 4 本（`/api/admin/invitations` の作成・一覧・送り直し・取り消し）
- 登録 — `invitation/web/RegistrationController.java` — 2 本（`POST /api/registration/verify`・`/complete`、ログインなし）
- DSL の管理 — `dslmanage/web/DslAdminController.java` — 10 本（`/api/admin/dsl/` の status・preview・generate・download・apply・history・restore・applied/download）
- 見た目 — `appearance/web/AppearanceController.java` — 1 本（`GET /api/appearance`、ログインなし）
- 問題の種類 — `common/error/web/ProblemTypeController.java` — 1 本（`GET /api/problems/{slug}`、ログインなし）

アプリの中の提供口（Java のインターフェース）:

- `common/security/SecurityRuleContributor`（追加のアクセスの決まり、order は機能ごとに 100 台。本番は auth 110・access 210・invitation 310・appearance 410）
- `common/security/ApiDefaultAccess`（`/api/**` の既定。`access` の `AdminApiDefaultAccess` が1つだけ置き、ログイン必須）
- `dsl/service/ActiveDslModelProvider`（契約 C8。適用中の DSL のモデル。Javadoc に「後続の Intent I・J・K が使う」とある）

画面の差し込み口（`frontend/src/app/registry/types.ts`）: 画面（ルート）・サイドバーの項目・ユーザーメニューの項目・ログイン状態の提供元の4つ。

### Frameworks & Libraries

バックエンド（`gradle/libs.versions.toml`）:

- Java — 25 — 言語
- Spring Boot — 4.1.1 — アプリの土台（webmvc・security・oauth2-resource-server・data-jpa・flyway・validation・aspectj・actuator・opentelemetry・mail）
- Tomcat — 11.0.26 — 組み込みの Web サーバー（脆弱性のため Spring Boot の管理の版を上書き）
- Jackson — 3.1.7 — JSON（BOM で上書き）
- H2 — Spring Boot の管理の版 — 内部DB（組み込み、ファイル）
- Flyway — Spring Boot の管理の版 — 内部DB の移行（V1〜V9）
- SnakeYAML 2.7・networknt json-schema-validator 3.0.6 — DSL の読み込みと検証
- logstash-logback-encoder 9.0・opentelemetry-logback-appender 2.28.1-alpha — 構造化ログと外部エクスポート
- cherry-mustache 0.1.0（サブモジュール）— メールのテンプレート
- テスト: JUnit（Spring Boot の管理の版）、ArchUnit 1.5.1、jqwik 1.10.1、subethasmtp 7.2.2、Testcontainers
- 品質: Spotless 8.10.3 ＋ palantir-java-format 2.98.0、SpotBugs 4.10.4 ＋ FindSecBugs 1.14.0、JaCoCo 0.8.15

画面（`frontend/package.json`）:

- React 19.2・react-router 8.3・i18next 26.4・react-i18next 17.0 — 画面の土台
- make-you-chic-ui（`file:` の依存）— デザインシステム
- Vite 8.3・TypeScript 6.0 — ビルド
- Vitest 5.0・Testing Library・user-event・vitest-axe・fast-check・jsdom 30 — テスト
- Playwright 1.63 — E2E
- Prettier 3.9・oxlint 1.78・ESLint 10.8・Stylelint 17.14 — 書式と静的検査

### Test Coverage

- **Test Directories**: `backend/src/test/java/cherry/mastersmith/`（対象と同じパッケージ構成。単体 `XxxTest`、結合 `XxxIT`。機能ごとの `testsupport`）、`frontend/src/**/*.test.ts(x)`（対象と同じ場所）、`frontend/e2e/`（010〜130 の 13 本）
- **Test Frameworks**: JUnit 5 ＋ Spring Boot Test ＋ spring-security-test、ArchUnit、jqwik、Testcontainers（対象DB）、subethasmtp（JVM の中の SMTP の受け手）、Vitest ＋ Testing Library ＋ vitest-axe ＋ fast-check、Playwright
- **Coverage Config**: present。
  - バックエンド: JaCoCo の検証（`backend/build.gradle.kts` の `jacocoTestCoverageVerification`）。全体の合計で行 80%・分岐 70%、パッケージごとにも同じ下限。全体で判定する既存のパッケージ `packagesJudgedByTotal` は 7 個（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）。
  - 画面: `frontend/vitest.config.ts` の `thresholds`（行 80・分岐 70）。
- 今回の論点に関わる既存のテスト（ファイル名で確かめた）:
  - 認可: `access/web/AdminAccessIT`・`AdminPathBoundaryIT`・`ApiDefaultAccessIT`・`AdminAuthorizationManagerTest`・`AccessDeniedEventsIT`・`AccessSecretLeakIT`、テスト用の決まり `access/testsupport/PublicApiTestRules`・`AdminTestUsers`
  - 認証: `auth/web/AccessTokenApiIT`・`AccessTokenAuthenticationProviderTest`・`SuspendedUserAuthenticationIT` など
  - 利用者の管理: `useradmin/web/UserAdminOperationsApiIT`・`UserAdminMassAssignmentIT`・`UserAdminSecretLeakIT` など
  - 構造の検査: `ArchitectureTest`（全体の層）、機能ごとの `*BoundaryArchitectureTest`（auth・useradmin・audit・invitation・mail・appearance・dsl・dslmanage・targetdb）。**`access` の境界テスト（`AccessBoundaryArchitectureTest`）は無い**。
  - 画面: `app/navigation/navigationItems.test.ts`・`app/routing/decideRoute.test.ts`・`AppRouter.test.tsx`・`app/layout/ShellLayout.test.tsx`・`app/registry/validateRegistrations.test.ts`
  - E2E: `030-admin-access`・`110-user-admin-flow`・`130-admin-forbidden-accessibility` など

### Code Quality Indicators

- **Linting**: Java は Spotless（palantir-java-format、ライセンスヘッダー）・SpotBugs ＋ FindSecBugs（`backend/config/spotbugs-exclude.xml`、`spotbugsGate`）。TypeScript は Prettier・oxlint・ESLint（`frontend/eslint.config.js`）・Stylelint・`scripts/check-license-header.mjs`。秘密情報は Gitleaks（`.gitleaks.toml`）。依存の脆弱性は OSV-Scanner。
- **CI/CD**: `.github/workflows/ci.yml`（checkout → setup-java・setup-node・setup-gradle → `./gradlew verify` → 成果物の保存。アクションはコミットのハッシュで固定）。`.github/dependabot.yml`。
- **Documentation**: `README.md` あり。Java の Javadoc・TypeScript の先頭のコメントは日本語で、設計の ID（BR・NFR・契約・Intent）を細かく引いている。`frontend/src/features/README.md` に機能の登録の決まり。

### Technical Debt Signals

- 権限は真偽値の列 `users.admin_flag` だけ（`backend/src/main/resources/db/migration/V2__u2_user_account.sql` 25 行）。役割・権限の表は無い。主体 `AuthenticatedUser(long userId, String email, boolean admin)`（`auth/domain/AuthenticatedUser.java` 25 行）と認証の結果 `AuthenticatedUserToken`（権限の一覧は空、`auth/web/AuthenticatedUserToken.java` 35 行の `super(List.of())`）も管理者の真偽値だけを運ぶ。Spring Security の `GrantedAuthority`・`hasRole`・`@PreAuthorize`・`@EnableMethodSecurity` は使っていない（grep で 0 件）。
- 「管理者」の判定が複数の場所に散っている: サーバー側は `AdminAuthorizationManager`（44 行 `user.admin()`）、`useradmin` の操作した人の確かめ直し（`UserAdminService` の `isActiveAdmin`・排他の後の `activeAdmins.contains(actorUserId)`）、最後の管理者の数え（`UserRepository.findActiveAdminIds` 182 行）。画面側は `navigationItems.ts` 43 行・`decideRoute.ts` 63 行の `loginState.admin`。
- 画面の 403 の扱いが「`/api/admin/` で始まるパス・403・`ACCESS_DENIED`」に固定（`shared/api-client/adminForbidden.ts`）。管理者のみの範囲の外で 403 が起きる作りは想定していない。
- make-you-chic-ui のサイドバーは平らな一覧で、`key={item.href}`（`Sidebar.tsx` 49 行）・`aria-label="メインナビゲーション"`（45 行、日本語の固定）。今の項目を示す `aria-current` や入れ子の口は無い。アイコンは 18 種類だけ（`Icon/registry.ts`）。
- 画面の骨組みのアイコンは「ホームは `home`、ほかはすべて `list`」に固定（`ShellLayout.tsx` 46 行）。登録の型 `SidebarItemRegistration` にアイコンの項目が無い。
- DSL のメニューの `icon` は文字列で、make-you-chic-ui のアイコンの名前の一覧と照らしていない（`DslSemanticValidator` の `menu` の検証は表の存在と空のまとまりだけ。102〜117 行）。
- 監査の種類の列 `audit_events.event_type` は `VARCHAR(32)`（V4 26 行）。今の種類は 22 個（`AuditEventType`）。
- `access` に機能ごとの境界テストが無い（team.md の「新しく作る機能は `<機能>BoundaryArchitectureTest` を置く」より前に作られた）。
- `access.service` は `packagesJudgedByTotal` に入っている（team.md の決まりで、手を入れる Bolt で下限を満たして一覧から外す作業が付く）。

## 今回の Intent に関わる所見

### R-1 権限の持ち方は「管理者の印」の真偽値1つだけ（事実）

- 内部DB: `users.admin_flag BOOLEAN NOT NULL`（V2）。役割・権限・役割の割り当ての表は無い（V1〜V9 を確かめた）。
- 判定の流れ: `AccessTokenAuthenticationProvider` が要求ごとに `UserAccountService.findById` で利用者を DB から読み、停止なら 401（`USER_SUSPENDED`）、そうでなければ `AuthenticatedUser(userId, email, admin)` を主体にする。トークンの中の主張は `sub`（利用者 ID）・`iat`・`exp` だけ（`AccessTokenService.issue`、92〜96 行）で、権限はトークンに入っていない。
- そのため、印の変更は次の要求でサーバー側に効く（トークンの作り直しは要らない）。役割・権限を足しても、要求ごとに DB から読む今の形を保てば、変更が即時に効く性質は残せる。
- `AdminAuthorizationManager` の Javadoc に「後続 Intent F で役割・権限の判定を足す場所はここになる（ADR-003）」とある（33 行）。

### R-2 URL による認可は「`/api/admin/**` は管理者だけ」の1つの決まり（事実）

- `AdminSecurityContributor`（order 210）が `AdminPaths.adminPatterns()`（`/api/admin`・`/api/admin/**`）に `AdminAuthorizationManager` を当てる（66 行）。それ以外の `/api/**` は `AdminApiDefaultAccess` でログイン必須だけ。
- Spring Security の `authorizeHttpRequests` は先に当たった決まりが勝つ。並びは `SecurityConfig` の公開の決まり → contributor の order の小さい順（auth 110 → access 210 → invitation 310 → appearance 410）→ `/api/**` の既定 → 画面の配信は許可。
- 管理の API は `/api/admin/users`・`/api/admin/invitations`・`/api/admin/dsl/**`・`/api/admin/check` の4つのまとまり。どれも「管理者なら全部できる」形で、操作ごと・画面ごとの権限の区別は無い。
- 403 の処理（`AdminAccessDeniedHandler`）は、管理者のみのパスなら理由 `NOT_ADMIN` の出来事（監査の `ACCESS_DENIED`）を出す。
- 仮説: 役割ごとに API の範囲を分けるなら、(a) `AdminAuthorizationManager` の判定を「パスに要る権限を持つか」に変える、(b) order 210 より前（200 台の中）に権限ごとの決まりを足す、のどちらかになる。どちらも 403 の出来事の理由（`AccessDeniedReason` は `NOT_ADMIN` だけ）と、画面の 403 の判定（`/api/admin/` の接頭辞）に波及する。

### R-3 「管理者」は利用者の管理の業務の決まりにも組み込まれている（事実）

- `useradmin` は、管理者の印の付け外しと停止で管理者の行を利用者 ID の昇順に排他し、排他の後の「有効な管理者」（印あり・停止なし）の集合で、最後の管理者の保護（`LAST_ACTIVE_ADMIN`）と操作した人の確かめ直し（`NOT_ADMIN`）を行う（`UserAdminService` 268〜303 行、`UserRepository.findActiveAdminIds`）。
- `project.md` の Forbidden「最後の有効な管理者を無くす操作を受け付けない」と Mandated「利用者の状態（停止・管理者の印）の判定は…サーバー側で行う」がこの形に結び付いている。
- 監査の種類に `USER_ADMIN_GRANTED`・`USER_ADMIN_REVOKED` があり、`useradmin` の境界テストが「印・停止を変える口を呼べるのは `useradmin.service` だけ」と固定している（`UserAdminBoundaryArchitectureTest` 191 行〜）。
- 仮説: 役割を入れても「管理者の印」を残すか（例: 管理者は役割の1つにする／印は全権の役割として残す）で、最後の管理者の保護・監査の種類・境界テストの書き換えの量が大きく変わる。要件定義の論点になる。

### R-4 画面の出し分けも真偽値 `admin` だけ（事実）

- ログイン状態 `LoginState { loggedIn, admin, displayName?, preferences? }`（`app/registry/types.ts`）。`admin` は認証の応答の `CurrentUserResponse.admin` から来る（`features/auth/loginStateProvider.ts`）。
- 画面の登録の見られる人 `AccessLevel = 'PUBLIC' | 'LOGGED_IN' | 'ADMIN'`、サイドバーの表示の条件 `VisibleWhen = 'LOGGED_IN' | 'ADMIN'`。`decideRoute` は `ADMIN` の画面を管理者でない利用者に開かれたら `ADMIN_FORBIDDEN`（S6）にする。
- 管理者でなくなったことは、管理の API の 403 を受けて `AdminForbiddenProvider` が S6 に切り替え、`refreshSessionOnce` でログイン状態を1回読み直す形で画面に届く。
- 仮説: 役割・権限で出し分けるには、ログイン状態に権限（または見てよい画面・メニュー）の一覧を足し、`AccessLevel`・`VisibleWhen` を権限の指定に広げ、403 の判定を `/api/admin/` の外にも広げる必要がある。U1 の骨組みのファイル（types.ts・navigationItems.ts・decideRoute.ts・adminForbidden.ts）の書き換えになる。

### R-5 サイドバーは平ら。N 階層のメニューの部品は make-you-chic-ui に無い（事実）

- 画面の骨組み: `buildSidebarEntries` は「ホーム」＋表示の条件を満たす登録の項目を `order` の順に並べた平らな一覧を作る（`navigationItems.ts`）。今の項目は、管理（200）・DSL（210）・招待（220）・利用者（230）の4つ（どれも `ADMIN`）。
- make-you-chic-ui: `AppShell` の `navItems: AppShellNavItem[]`（`label`・`icon?`・`href`・`onClick?`）を `Sidebar` が平らな `<ul>` で描く。子の項目・開閉・今の項目の表示（`aria-current`）・グループの見出しの口は無い。`Sidebar` は外へ出していない（AppShell の中だけ）。アイコンは 18 種類（menu・chevron-down・chevron-up・close・check・bell・user・search・edit・trash・download・settings・home・list・info・success・warning・danger）。
- 同じリポジトリの中に N 階層の木を描く部品が1つある: `features/dsl/DslMenuTree.tsx`（DSL のプレビューでメニューの木を見せる。入れ子のリストと `aria-expanded` の開閉のボタン、ARIA の tree の役割は使わない）。ただし機能の中の部品で、`src/shared/` にも骨組みにも無い。
- `vendor/make-you-chic-ui` は直接変えられない（project.md の Forbidden）。
- 仮説: N 階層のサイドバーには、(a) make-you-chic-ui に入れ子のナビゲーションを足してもらう、(b) AppShell の外で骨組みが自前で描く、のどちらかが要る。`project.md` の学び（自前で作る設計にしたときは make-you-chic-ui への取り込みを依頼者に諮る）に当たる。

### R-6 N 階層のメニューの定義は、すでに DSL の中にある（事実）

- DSL の `menus`（JSON Schema `dsl/dsl-schema-v1.json` の `menuItem`: `label`（表示名 ja・en）・`icon`・`table`・`items`、`additionalProperties: false`）と、モデル `DslMenuItem(DisplayName label, String icon, String table, List<DslMenuItem> items)`（Javadoc に「N 階層」）。テーブルか子の少なくとも一方を持つ（`DslMenuItem` の作る口、意味の検証 `SEMANTIC_MENU_EMPTY`・`SEMANTIC_MENU_UNKNOWN_TABLE`）。
- 深さの上限は DSL 全体の入れ子の深さ `DslFormat.MAX_DEPTH = 50`（メニュー専用の上限は無い）。
- 既定の DSL の生成は、テーブルごとに1段のメニュー（`label` と `table`）を作るだけ（`DslTreeBuilder` 70〜77 行）。
- 適用中のモデルは `ActiveDslModelProvider.current()` で読める（契約 C8、Javadoc に「後続の Intent I・J・K が使う」）。今の利用者は `dslmanage` のプレビューの分析だけで、画面のナビゲーションには使われていない。適用で差し替わる（読み手から見て一度に切り替わる）。
- メニューの項目が指す「テーブルの画面」（業務データの一覧・詳細）はまだ無い（画面の経路の登録に該当が無い）。
- 仮説: Intent I のメニューは、骨組みの静的な登録（機能の `sidebarItems`）と、DSL の動的なメニュー（テーブルへの項目）の2つの出どころを持つことになりうる。DSL のメニューを画面に渡す API（今は無い）と、まだ無いテーブルの画面への行き先の扱いが論点になる。DSL の項目に権限（役割）の指定は無い（Intent F と I の結び付け方が論点）。

### R-7 監査・境界テスト・カバレッジへの波及（事実と見立て）

- 事実: 監査の種類は 22 個の列挙（`AuditEventType`）で、`event_type VARCHAR(32)`。出来事は各機能が出し、`AuditEventListener` が確定の後（AFTER_COMMIT）に受ける。`project.md` の Mandated は「利用者の権限・状態を変える管理の操作は、操作した人・対象の利用者・結果を監査に残す」。
- 事実: `access` には機能ごとの境界テストが無い。`access` は `auth.domain`・`auth.web.ClientInfoResolver` に依存し（`AdminAccessDeniedHandler` の import）、`useradmin.web` は `access.domain.AccessProblemTypes` だけ使ってよい（`UserAdminBoundaryArchitectureTest` 113 行〜）。
- 事実: `access.service` は `packagesJudgedByTotal` に入っている。
- 仮説: 役割の割り当ての変更は Mandated の監査の対象になり、新しい種類が増える（名前は 32 文字以内）。役割の管理を新しい機能（パッケージ）にするなら、team.md の決まりで `<機能>BoundaryArchitectureTest` を置き、既存の境界テストは書き換えずに足す。`access.service` に手を入れるならカバレッジの作業が付く。

## Handoff Summary

- **Intent-relevant finding**: 権限は `users.admin_flag` の真偽値1つだけで（V2 25 行）、主体 `AuthenticatedUser(userId, email, admin)`（`auth/domain/AuthenticatedUser.java` 25 行）・URL の決まり（`access/web/AdminSecurityContributor.java` 66 行、`/api/admin/**` は管理者だけ）・判定（`access/web/AdminAuthorizationManager.java` 44 行）・画面の出し分け（`frontend/src/app/registry/types.ts` の `AccessLevel`・`VisibleWhen`、`navigationItems.ts` 43 行、`decideRoute.ts` 63 行）・画面の 403（`shared/api-client/adminForbidden.ts`）のすべてがこの真偽値を前提にしている。サイドバーは平らな一覧で、make-you-chic-ui の `Sidebar` に入れ子の口は無い（`Sidebar.tsx`）。一方で N 階層のメニューの定義は DSL の `menus`（`DslMenuItem`、JSON Schema の `menuItem`）にすでにあり、`ActiveDslModelProvider` で読めるが、画面のナビゲーションには使われていない。
- **Risks / follow-up**:
  - 「管理者の印」は最後の管理者の保護（Forbidden）・操作した人の確かめ直し・監査の種類・`useradmin` の境界テストに組み込まれている。役割を入れるときに印を残すか置き換えるかで、守るべき既存の決まりとテスト（`UserAdminOperationsApiIT`・`AdminAccessIT` など）の量が大きく変わる。
  - 権限は要求ごとに DB から読み、トークンには入れていない。この性質（変更の即時の反映、Mandated の「サーバー側で判定」）を保つ前提で設計する。
  - `vendor/make-you-chic-ui` は直接変えられない。N 階層のサイドバーは、make-you-chic-ui への取り込みを依頼者に諮るか、骨組みで自前に描くかの判断が要る。自前の木は `features/dsl/DslMenuTree.tsx` に前例がある（機能の中の部品）。
  - DSL のメニューの `icon` は make-you-chic-ui の 18 種類と照らしていない。メニューの深さの専用の上限は無い（全体の入れ子の上限 50 だけ）。DSL は適用で差し替わる。メニューが指すテーブルの画面はまだ無い。
  - `access` には境界テストが無く、`access.service` は `packagesJudgedByTotal` に入っている（手を入れる Bolt でカバレッジの作業が付く）。
  - 認可の変更は team.md の Testing Posture の「認可: 401・403・200」「管理の API の認可」「要求の改ざん」「管理の操作の監査」の必須のテストに当たる。
