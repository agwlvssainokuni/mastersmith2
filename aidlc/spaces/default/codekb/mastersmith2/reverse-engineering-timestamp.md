# Reverse Engineering の実施記録（mastersmith2）

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-09-25 |
| Intent | `260925-user-management`（scope classic、ロードマップの Intent G ユーザー登録・招待フローと Intent H ユーザーごとのプリファレンス） |
| 対象 | mastersmith2（プロジェクトルート） |
| 記録するコミット | `c438dc0008b1f06d0aed533f61cdee0e18a976fd`（`develop` の HEAD、`git rev-parse HEAD` で取得） |
| サブモジュール `vendor/make-you-chic-ui` の固定先 | `edb1f943c0e66293494fa974605f34fcd7e258d7` |
| 走査の広さ・深さ | 全体の読み直し（Full rescan）・Standard |
| 手順 | 開発担当のコードスキャン → アーキテクトによる統合（この9つの成果物） |

## 確かめ方

- ファイルの読み取りと、読み取りの git（`git rev-parse`・`git ls-files`）だけで確かめた。Gradle・npm・Docker は実行していない。テストの件数はファイルを数えた値である。
- `.env` と鍵ファイルは開いていない。Git 管理外の参考資料・`aidlc/`・`.claude/` と生成物は対象の外。
- アーキテクトは、開発担当の主な所見を次のファイルで読み直して確かめた: `V2__u2_user_account.sql`・V3〜V6 の列、`User`・`UserAccountService` の公開の操作、`LoginService`・`TokenRefreshService`・`LogoutService`・`AccessTokenAuthenticationProvider`、`RefreshTokenRepository` の操作、`SecurityConfig`・`AuthSecurityContributor`（`/api/auth/` でアクセストークンを読まないことはアーキテクトが新しく確かめた）、`ProblemBaseUrlResolver`、`AuditEventType`・`AuditEventListener`・`AuditEventRecorder`、`application.yaml`、`Dockerfile`（`MaxRAMPercentage=50.0`）、`compose.yaml`、`backend/build.gradle.kts` の `packagesJudgedByTotal`、`backend/gradle.lockfile` の主な版、make-you-chic-ui の `types.ts`・`storage.ts`・`ThemeProvider.tsx`、画面の `App.tsx`・`I18nProvider.tsx`・`authSession.ts`・`loginStateProvider.ts`。
- パッケージ間の依存は、各パッケージの `import cherry.mastersmith.*` を検索して数え直した（`dependencies.md`）。開発担当のスキャンとの食い違いは無かった。
- 開発担当のスキャンとの差: テストの道具の JUnit は lockfile では JUnit Jupiter 6.0.3 だった（スキャンは JUnit 5 と記載）。版は lockfile の値を載せた。

## 前の記録との関係

前のコード知識ベースは Intent `260925-storage-memory-fixes` のとき（partial、8 部品、記録のコミット `4970f3b`）のもの。依頼者が全体の読み直しを選んだため、9つの成果物をすべて置き換え、下の Scope of Analysis は今回のスキャンだけから作った。今回の深い範囲は利用者・認証・認可・監査・共通のエラーとセキュリティと表示言語・設定・スキーマ変更・画面の骨組みと認証・make-you-chic-ui の theme・ビルドと実行環境で、前回深く読んだ `dsl`・`dslmanage`・`backend-test-support`・`perf-and-monitoring` は流し読みにした。前回と比べると重ならない部品が出る（比べると部品が「失われた」と表示される見込み）。部品の ID は前回と同じ 27 個を使い、新しい ID は足していない。

前の記録から変わっていた事実: `Dockerfile` の JVM の指定は `-XX:MaxRAMPercentage=50.0`（前の記録は 75.0。前の Intent の Code Generation で変えた）。

## 範囲の要約

- 深く読んだ: 89 のパス（ディレクトリ 10・ファイル 79）、19 の部品。
- 流し読み: `dsl`・`dslmanage`（`DslRequestContextResolver` 以外）・`targetdb`・`common` の残り・`access`・`auth.domain`・`config` の残り、テストの残り、画面の残り、`vendor/make-you-chic-ui` の theme 以外、`docker/`・`perf/`・`config/`・`.pre-commit-config.yaml`。
- README は「スキーマの変更（Flyway）」以降の節を深く読み、前半は見出しだけである。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` である。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: 260925-user-management
fingerprint: bfb75745b088151a33504b163be9bded29d74ed8
analyzed:
  paths:
    - backend/src/main/java/cherry/mastersmith/user/
    - backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java
    - backend/src/main/java/cherry/mastersmith/auth/service/AccessTokenService.java
    - backend/src/main/java/cherry/mastersmith/auth/service/TokenRefreshService.java
    - backend/src/main/java/cherry/mastersmith/auth/service/LogoutService.java
    - backend/src/main/java/cherry/mastersmith/auth/service/LoginAttemptStateInitializer.java
    - backend/src/main/java/cherry/mastersmith/auth/service/SigningKeyProvider.java
    - backend/src/main/java/cherry/mastersmith/auth/service/AuthProperties.java
    - backend/src/main/java/cherry/mastersmith/auth/service/AuthClockConfig.java
    - backend/src/main/java/cherry/mastersmith/auth/web/AuthController.java
    - backend/src/main/java/cherry/mastersmith/auth/web/AuthSecurityContributor.java
    - backend/src/main/java/cherry/mastersmith/auth/web/AccessTokenAuthenticationProvider.java
    - backend/src/main/java/cherry/mastersmith/auth/web/RefreshCookies.java
    - backend/src/main/java/cherry/mastersmith/auth/web/OriginVerifier.java
    - backend/src/main/java/cherry/mastersmith/auth/web/ClientInfoResolver.java
    - backend/src/main/java/cherry/mastersmith/auth/web/TokenResponse.java
    - backend/src/main/java/cherry/mastersmith/auth/web/LoginRequest.java
    - backend/src/main/java/cherry/mastersmith/auth/web/CurrentUserResponse.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/AuthenticatedUser.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/AuthProblemTypes.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/LockPolicy.java
    - backend/src/main/java/cherry/mastersmith/auth/repository/LoginAttemptStateRepository.java
    - backend/src/main/java/cherry/mastersmith/auth/repository/RefreshTokenRepository.java
    - backend/src/main/java/cherry/mastersmith/access/domain/AdminPaths.java
    - backend/src/main/java/cherry/mastersmith/access/web/AdminSecurityContributor.java
    - backend/src/main/java/cherry/mastersmith/access/web/AdminAuthorizationManager.java
    - backend/src/main/java/cherry/mastersmith/access/web/AdminApiDefaultAccess.java
    - backend/src/main/java/cherry/mastersmith/access/web/AdminCheckController.java
    - backend/src/main/java/cherry/mastersmith/access/web/AdminAccessDeniedHandler.java
    - backend/src/main/java/cherry/mastersmith/access/web/AccessWebSecurityConfig.java
    - backend/src/main/java/cherry/mastersmith/audit/domain/AuditEvent.java
    - backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventFactory.java
    - backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java
    - backend/src/main/java/cherry/mastersmith/audit/domain/AuditFailureReason.java
    - backend/src/main/java/cherry/mastersmith/audit/service/
    - backend/src/main/java/cherry/mastersmith/audit/repository/
    - backend/src/main/java/cherry/mastersmith/common/error/domain/
    - backend/src/main/java/cherry/mastersmith/common/error/service/
    - backend/src/main/java/cherry/mastersmith/common/error/web/GlobalExceptionHandler.java
    - backend/src/main/java/cherry/mastersmith/common/error/web/ErrorResponseFactory.java
    - backend/src/main/java/cherry/mastersmith/common/security/
    - backend/src/main/java/cherry/mastersmith/common/i18n/
    - backend/src/main/java/cherry/mastersmith/common/observability/TraceAspect.java
    - backend/src/main/java/cherry/mastersmith/common/web/RequestBodyLimitRoute.java
    - backend/src/main/java/cherry/mastersmith/common/web/MastersmithWebProperties.java
    - backend/src/main/java/cherry/mastersmith/config/SecurityConfig.java
    - backend/src/main/java/cherry/mastersmith/config/WebConfig.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/web/DslRequestContextResolver.java
    - backend/src/main/resources/application.yaml
    - backend/src/main/resources/logback-spring.xml
    - backend/src/main/resources/db/migration/
    - backend/build.gradle.kts
    - backend/src/test/java/cherry/mastersmith/ArchitectureTest.java
    - backend/src/test/java/cherry/mastersmith/auth/AuthBoundaryArchitectureTest.java
    - backend/src/test/java/cherry/mastersmith/access/testsupport/PublicApiTestRules.java
    - build.gradle.kts
    - settings.gradle.kts
    - gradle/libs.versions.toml
    - frontend/package.json
    - frontend/vite.config.ts
    - frontend/vitest.config.ts
    - frontend/src/main.tsx
    - frontend/src/app/App.tsx
    - frontend/src/app/i18n/I18nProvider.tsx
    - frontend/src/app/i18n/i18n.ts
    - frontend/src/app/i18n/resolveLanguage.ts
    - frontend/src/app/routing/
    - frontend/src/app/layout/ShellLayout.tsx
    - frontend/src/app/login-state/LoginStateGate.tsx
    - frontend/src/app/registry/types.ts
    - frontend/src/app/registry/registrationModules.ts
    - frontend/src/app/navigation/navigationItems.ts
    - frontend/src/shared/api-client/
    - frontend/src/features/auth/authSession.ts
    - frontend/src/features/auth/authApi.ts
    - frontend/src/features/auth/loginStateProvider.ts
    - frontend/src/features/auth/registration.ts
    - frontend/src/features/admin/registration.ts
    - frontend/src/features/admin/AdminAreaPage.tsx
    - frontend/src/features/dsl/registration.ts
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/theme/types.ts
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/theme/validation.ts
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/theme/storage.ts
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/theme/ThemeProvider.tsx
    - compose.yaml
    - Dockerfile
    - .env.example
    - .github/workflows/ci.yml
    - README.md
  components:
    - config
    - common-error
    - common-security
    - common-web
    - common-i18n
    - common-observability
    - auth
    - access
    - audit
    - user
    - frontend-app-core
    - frontend-registry
    - frontend-app-layout-i18n
    - frontend-api-client
    - frontend-feature-auth
    - frontend-feature-admin
    - make-you-chic-ui
    - build-and-verify
    - container-runtime
shallow:
  paths:
    - backend/src/main/java/cherry/mastersmith/MastersmithApplication.java
    - backend/src/main/java/cherry/mastersmith/dsl/
    - backend/src/main/java/cherry/mastersmith/dslmanage/
    - backend/src/main/java/cherry/mastersmith/targetdb/
    - backend/src/main/java/cherry/mastersmith/common/health/
    - backend/src/main/java/cherry/mastersmith/common/observability/
    - backend/src/main/java/cherry/mastersmith/common/web/
    - backend/src/main/java/cherry/mastersmith/common/error/web/
    - backend/src/main/java/cherry/mastersmith/access/
    - backend/src/main/java/cherry/mastersmith/auth/domain/
    - backend/src/main/java/cherry/mastersmith/config/
    - backend/src/test/java/cherry/mastersmith/
    - backend/config/spotbugs-exclude.xml
    - frontend/src/features/dsl/
    - frontend/src/features/auth/
    - frontend/src/features/admin/
    - frontend/src/app/
    - frontend/e2e/
    - vendor/make-you-chic-ui/
    - docker/
    - perf/
    - .pre-commit-config.yaml
    - config/
```
