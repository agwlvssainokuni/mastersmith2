# Reverse Engineering の実施記録（mastersmith2）

| 項目 | 値 |
|---|---|
| 実施日 | 2026-09-23 |
| Intent | `260923-dsl-schema-loader`（ロードマップの Intent A・D・E の統合） |
| 対象 | mastersmith2（プロジェクトルート `./`） |
| ブランチ・コミット | `develop`・`42def8c667a1e3955316727d2bd1b613b6655d1e`（`.git/HEAD` が `ref: refs/heads/develop`、`.git/refs/heads/develop` の値を読んで確認） |
| スキャン時点のソースの識別 | `git:4ff24036ecf8f0e7f57c7a334101472c4e634192` |
| サブモジュール `vendor/make-you-chic-ui` の固定先 | `5258c8bb987b0fa6ffd0ad7c4eadc7d4006da52d` |
| 走査の広さ・深さ | 全体の読み直し（Full rescan、スナップショットの範囲は `./`）・Standard |
| 手順 | 開発者のコードスキャン（pipeline の第1リンク）→ アーキテクトによる統合（この9つの成果物） |

## 確かめ方

- ファイルの読み取り（Read・grep・ls）だけで確かめた。git・Gradle・npm・Docker のコマンドは実行していない。テストの件数はファイルを数えた値で、実行した結果ではない。
- `.env` と鍵ファイルは開いていない（`.env.example` だけを読んだ）。
- アーキテクトは、開発者のスキャンの要点をソースで確かめ直した（`SecurityConfig`・`AuthController`・`LoginService`・`TokenRefreshService`・`LogoutService`・`AuthSecurityContributor`・`AccessTokenAuthenticationProvider`・`AdminSecurityContributor`・`AdminAuthorizationManager`・`AdminAuthenticationEntryPoint`・`AdminAccessDeniedHandler`・`AccessDeniedEventPublisher`・`AdminPaths`・`AuditEventListener`・`AuditEventRecorder`・`ErrorResponseFactory`・問題の種類の定義・`application.yaml`・Flyway のスキーマ変更・`apiClient.ts`・`registry/types.ts`・`Dockerfile`・`compose.yaml`・`ci.yml`・パッケージ間の `import`）。このうち `auth/service/TokenRefreshService.java`・`LogoutService.java` は開発者の区分では流し読みの範囲にあるため、下の `analyzed` には足していない（範囲は開発者の Scan Coverage どおりとした）。

## 前の記録との関係

前のコード知識ベースは、Intent `260923-colima-spec-up` のときのもの（コミット `aeeaf73` の時点、partial。コンテナ・負荷試験・設定が中心）である。依頼者が全体の読み直しを選んだため、9つの成果物をすべて置き換えた。下の Scope of Analysis は今回のスキャンだけから作り、前の記録の範囲は足し合わせていない。

## 範囲の要約

- 深く読んだ: ビルドと検査の設定、バックエンドの `config`・`common`・`access` の全体、`auth` の `web`・`repository` と中心のファイル、`audit` の `service`・`repository` と中心のファイル、設定とスキーマ変更、テストの見本（`ArchitectureTest`・`TestDatabase`・`HttpTestClient`・`AdminAccessIT`）とテストの設定、画面の設定・起動・登録・振り分け・API の呼び出し・管理者向け領域、コンテナと CI の設定。
- 流し読み: `user` の全体、`auth`・`audit` の残り、テストの大半、画面のレイアウト・表示言語・ログイン状態・ページ、認証の画面の大半、E2E とスクリプト、`vendor/make-you-chic-ui`、`docker/`・`perf/`、README（4つの節を除く）。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` である。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: 260923-dsl-schema-loader
fingerprint: fa0e0e2120b0f8612bf1180b5abde10a486c44ce
analyzed:
  paths:
    - settings.gradle.kts
    - build.gradle.kts
    - backend/build.gradle.kts
    - gradle/libs.versions.toml
    - gradle/wrapper/gradle-wrapper.properties
    - backend/gradle.lockfile
    - backend/config/spotbugs-exclude.xml
    - backend/src/main/java/cherry/mastersmith/MastersmithApplication.java
    - backend/src/main/java/cherry/mastersmith/config/
    - backend/src/main/java/cherry/mastersmith/common/
    - backend/src/main/java/cherry/mastersmith/access/
    - backend/src/main/java/cherry/mastersmith/auth/web/
    - backend/src/main/java/cherry/mastersmith/auth/repository/
    - backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java
    - backend/src/main/java/cherry/mastersmith/auth/service/AuthClockConfig.java
    - backend/src/main/java/cherry/mastersmith/auth/service/AuthProperties.java
    - backend/src/main/java/cherry/mastersmith/auth/service/AuthSchedulingConfig.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/AuthenticatedUser.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/ClientInfo.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/RefreshToken.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/LoginAttemptState.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/AuthProblemTypes.java
    - backend/src/main/java/cherry/mastersmith/audit/package-info.java
    - backend/src/main/java/cherry/mastersmith/audit/service/
    - backend/src/main/java/cherry/mastersmith/audit/repository/
    - backend/src/main/java/cherry/mastersmith/audit/domain/AuditEvent.java
    - backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java
    - backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventFactory.java
    - backend/src/main/resources/application.yaml
    - backend/src/main/resources/logback-spring.xml
    - backend/src/main/resources/db/migration/
    - backend/src/test/java/cherry/mastersmith/ArchitectureTest.java
    - backend/src/test/java/cherry/mastersmith/common/testsupport/TestDatabase.java
    - backend/src/test/java/cherry/mastersmith/common/testsupport/HttpTestClient.java
    - backend/src/test/java/cherry/mastersmith/access/web/AdminAccessIT.java
    - backend/src/test/resources/archunit.properties
    - backend/src/test/resources/junit-platform.properties
    - backend/src/test/resources/META-INF/spring.factories
    - frontend/package.json
    - frontend/vite.config.ts
    - frontend/vitest.config.ts
    - frontend/vitest.setup.ts
    - frontend/tsconfig.json
    - frontend/.oxlintrc.json
    - frontend/eslint.config.js
    - frontend/.prettierrc.json
    - frontend/.stylelintrc.json
    - frontend/.npmrc
    - frontend/playwright.config.ts
    - frontend/index.html
    - frontend/src/main.tsx
    - frontend/src/features/README.md
    - frontend/src/app/App.tsx
    - frontend/src/app/registry/
    - frontend/src/app/routing/AppRouter.tsx
    - frontend/src/app/routing/decideRoute.ts
    - frontend/src/app/navigation/navigationItems.ts
    - frontend/src/shared/api-client/apiClient.ts
    - frontend/src/shared/api-client/apiError.ts
    - frontend/src/features/admin/registration.ts
    - frontend/src/features/admin/adminApi.ts
    - frontend/src/features/admin/adminAreaStatus.ts
    - frontend/src/features/admin/AdminAreaPage.tsx
    - frontend/src/features/admin/AdminPlaceholder.tsx
    - frontend/src/features/auth/registration.ts
    - frontend/src/features/auth/authSession.ts
    - Dockerfile
    - compose.yaml
    - .env.example
    - .dockerignore
    - .gitignore
    - .gitmodules
    - .gitleaks.toml
    - .pre-commit-config.yaml
    - .github/workflows/ci.yml
    - .github/dependabot.yml
    - config/npm-build-tools.txt
  components:
    - app-bootstrap
    - config
    - common-error
    - common-security
    - common-web
    - common-health
    - common-i18n
    - common-observability
    - auth
    - access
    - audit
    - frontend-app-core
    - frontend-registry
    - frontend-api-client
    - frontend-feature-admin
    - build-and-verify
    - container-runtime
shallow:
  paths:
    - backend/src/main/java/cherry/mastersmith/auth/service/
    - backend/src/main/java/cherry/mastersmith/auth/domain/
    - backend/src/main/java/cherry/mastersmith/audit/domain/
    - backend/src/main/java/cherry/mastersmith/user/
    - backend/src/test/java/cherry/mastersmith/
    - frontend/src/app/layout/
    - frontend/src/app/i18n/
    - frontend/src/app/login-state/
    - frontend/src/app/pages/
    - frontend/src/app/testing/
    - frontend/src/features/auth/
    - frontend/src/types/
    - frontend/e2e/
    - frontend/scripts/
    - vendor/make-you-chic-ui/
    - docker/
    - perf/
    - README.md
    - config/license-header.txt
    - LICENSE
```
