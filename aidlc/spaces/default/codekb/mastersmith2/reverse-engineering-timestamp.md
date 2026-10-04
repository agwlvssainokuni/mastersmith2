# Reverse Engineering の実施記録（mastersmith2）

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-10-04 |
| Intent | `261004-role-menu`（scope classic、深さ Standard。F: ロールベースの権限の管理、I: メニュー・ナビゲーション（N 階層）） |
| 対象 | mastersmith2（プロジェクトルート、単一のリポジトリ） |
| 記録するコミット | `d5aea52b6b2df7d473a16394e66c77453bd591f0`（`develop` の HEAD。アーキテクトも読み取りだけの `git rev-parse HEAD` で一致を確かめた）。スキャンの前に取ったスナップショットの source の値はコミットではないため、ここには書かない |
| 走査の広さ・深さ | 全体の読み直し（Full rescan）・Standard。スナップショットの paths は `./`。全体を流し読みで把握し、今回の Intent の論点（認可・利用者・監査・認証・画面の骨組み・make-you-chic-ui のナビゲーション・Flyway・ArchUnit・DSL のメニューの定義）に関わるファイルを深く読んだ |
| 手順 | 開発担当のコードスキャン（`inception/reverse-engineering/developer-scan.md`）→ アーキテクトによる統合（9 文書をすべて新しく書き直した） |

## 確かめ方

- ファイルの読み取り（Read・Grep・読み取りだけのシェル）で確かめた。Gradle・npm・Docker・git の書き込みの操作は行っていない。`.env`・鍵ファイル・Git 管理外の参考資料は開いていない。メールアドレス・秘密の値は本文に写していない。
- アーキテクトが自分で確かめ直したもの: 深く読んだ 59 のパスがすべて存在すること、`users.admin_flag`（V2 25 行）、`audit_events.event_type VARCHAR(32)`（V4 26 行）、`AuditEventType` の 22 個、`AdminAuthorizationManager` の注記（33 行）と判定、`AdminSecurityContributor` の `contribute`、`AuthenticatedUser`（25 行）と `AuthenticatedUserToken` の `super(List.of())`（35 行）、`AccessTokenService.issue` の主張（92〜96 行）、`GrantedAuthority`・`hasRole`・`@PreAuthorize`・`@EnableMethodSecurity` がアプリのソースに 0 件、各 `*SecurityContributor` の `ORDER`、`SecurityConfig` の公開の決まりと `/api/**` の既定、`AccessDeniedReason` の値、`types.ts` の `AccessLevel`・`VisibleWhen`・`LoginState`、`navigationItems.ts` 43 行、`decideRoute.ts` 63 行、`ShellLayout.tsx` 46 行、`adminForbidden.ts` の判定、`Sidebar.tsx` の `aria-label`（45 行）と `key`（49 行）、アイコン 18 種類、各機能のサイドバーの項目の `order`、`DslMenuItem`（29 行）・`DslFormat.MAX_DEPTH`・JSON Schema の `menuItem`・`DslTreeBuilder` のメニューの生成・`MenuNode`、`ActiveDslModelProvider` の読み手、`packagesJudgedByTotal` の 7 個、境界テストの一覧、`UserAdminBoundaryArchitectureTest` の 113 行・191 行、`findActiveAdminIds`（182 行）、controller ごとの API の本数、バックエンドのパッケージ間の import、骨組みが機能を import しないこと、サブモジュールの固定先（`e82b651`・`8d44c36`）、テストのファイルの数。
- 事実と見立て（仮説）は、開発担当の記録どおり各所見の本文で分けて書いた。

## 前の記録との関係

前のコード知識ベースは Intent `261004-safety-carryover` の開始時のもの（partial、13 部品・42 パス、記録のコミット `47ec27b`）。依頼者が Full rescan を選んだため、9 文書は前回の文を引き継がずに書き直した。前回までに深く読み、今回は確かめ直していない内容は、本文で「前回までの記録」と書き分けた。Scope of Analysis は今回の走査だけから作り、前回の `analyzed.paths` は取り込んでいない。

## 開発担当の記録との差

- API の本数: 開発担当は総数を「29 本」と書いたが、同じ記録の内訳の合計は 32 で、アーキテクトの数え直し（controller ごとのメソッドの対応づけの注釈）も 32 だった。文書は 32 本とした（`api-documentation.md`）。
- 拒否の理由: 開発担当は「`AccessDeniedReason` は `NOT_ADMIN` だけ」と書いたが、列挙は5つ（`NOT_ADMIN`・`TOKEN_MISSING`・`TOKEN_MALFORMED`・`TOKEN_INVALID`・`USER_NOT_FOUND`）で、403 の理由が `NOT_ADMIN` だけである。文書はこの形で書いた（K-33）。
- 境界テスト: 開発担当の一覧（9 機能）に加えて `dslmanage` には `DslManageGenerateBoundaryArchitectureTest` もあり、境界テストは 10 個である。`access` に無いことは開発担当の記録どおり（K-38）。
- `AppShellNavItem` の定義の場所は `AppShell.tsx` ではなく `Sidebar.tsx`（20〜25 行付近）だった。中身は開発担当の記録どおり（K-36）。
- 部品: 開発担当が深く読んだ `frontend/src/shared/api-client/adminForbidden.ts` は `frontend-api-client` の場所にあるため、`analyzed.components` に `frontend-api-client` を足した（深く読んだファイルに合わせて広げる向きの直し）。`frontend/src/app/admin-forbidden/` は前回の部品の一覧のどの場所にも無かったため、`frontend-app-core` の場所に足した（ID は変えていない）。
- `backend/src/main/resources/db/migration/` と `backend/src/main/java/cherry/mastersmith/common/security/` は、開発担当の記録どおり中のファイルをすべて読んだディレクトリとして `analyzed.paths` に入れた。範囲を限って読んだファイル（`AccessTokenService.java`・`UserAdminService.java`・`DslSemanticValidator.java`・`DslTreeBuilder.java`・`DslMenuTree.tsx` など）は、前回までと同じくファイルの単位で入れ、どの範囲を読んだかは `component-inventory.md` に書いた。

## 所見の番号

前回の所見は K-25〜K-31 で終わるため、今回は開発担当の R-1〜R-7 を順に **K-32〜K-38** とした。一覧は `business-overview.md`、本文は持ち主の文書に1か所だけ書いた。

## 部品 ID の扱い

- 前回の 39 個の ID をそのまま使った（追加・名前の変更は無い）。
- `analyzed.components` は、今回深く読んだファイルを持つ 21 個（開発担当の 20 個に `frontend-api-client` を足した）。どれも部品の一部だけを読んだ「一部」の深さである。

## 範囲の要約

- 深く読んだ: 59 のパス（ファイル 57・ディレクトリ 2）、21 の部品。パスは開発担当の Scan Coverage の「Analyzed deeply」で、すべてスナップショットの paths（`./`）の中にある。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` で、`./` は `analyzed.paths` に入れていない（前回までと同じ扱い）。
- `fingerprint` は、最終の `analyzed.paths`（59）をカンマ区切りで `aidlc engine workspace codekb-scope-diff --repo mastersmith2 --mint --paths` に渡した出力をそのまま貼った。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: 261004-role-menu
fingerprint: 97f2f80b3e956a71ddd9ccef39790b32dc628bbe
analyzed:
  paths:
    - backend/src/main/java/cherry/mastersmith/access/domain/AdminPaths.java
    - backend/src/main/java/cherry/mastersmith/access/domain/AccessDeniedReason.java
    - backend/src/main/java/cherry/mastersmith/access/domain/AccessProblemTypes.java
    - backend/src/main/java/cherry/mastersmith/access/web/AdminAuthorizationManager.java
    - backend/src/main/java/cherry/mastersmith/access/web/AdminSecurityContributor.java
    - backend/src/main/java/cherry/mastersmith/access/web/AdminApiDefaultAccess.java
    - backend/src/main/java/cherry/mastersmith/access/web/AdminCheckController.java
    - backend/src/main/java/cherry/mastersmith/access/web/AdminAccessDeniedHandler.java
    - backend/src/main/java/cherry/mastersmith/common/security/
    - backend/src/main/java/cherry/mastersmith/config/SecurityConfig.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/AuthenticatedUser.java
    - backend/src/main/java/cherry/mastersmith/auth/web/AuthenticatedUserToken.java
    - backend/src/main/java/cherry/mastersmith/auth/web/AccessTokenAuthenticationProvider.java
    - backend/src/main/java/cherry/mastersmith/auth/web/AuthSecurityContributor.java
    - backend/src/main/java/cherry/mastersmith/auth/web/CurrentUserResponse.java
    - backend/src/main/java/cherry/mastersmith/auth/web/TokenResponse.java
    - backend/src/main/java/cherry/mastersmith/auth/service/AccessTokenService.java
    - backend/src/main/java/cherry/mastersmith/appearance/web/AppearanceSecurityContributor.java
    - backend/src/main/java/cherry/mastersmith/invitation/web/InvitationSecurityContributor.java
    - backend/src/main/java/cherry/mastersmith/user/domain/User.java
    - backend/src/main/java/cherry/mastersmith/user/service/UserSummary.java
    - backend/src/main/java/cherry/mastersmith/user/repository/UserRepository.java
    - backend/src/main/java/cherry/mastersmith/useradmin/domain/AdminOperation.java
    - backend/src/main/java/cherry/mastersmith/useradmin/domain/RejectionReason.java
    - backend/src/main/java/cherry/mastersmith/useradmin/service/UserAdminService.java
    - backend/src/main/java/cherry/mastersmith/useradmin/web/AdminUser.java
    - backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java
    - backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslMenuItem.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslModel.java
    - backend/src/main/java/cherry/mastersmith/dsl/domain/DslFormat.java
    - backend/src/main/java/cherry/mastersmith/dsl/service/ActiveDslModelProvider.java
    - backend/src/main/java/cherry/mastersmith/dsl/validate/DslSemanticValidator.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/generate/DslTreeBuilder.java
    - backend/src/main/resources/dsl/dsl-schema-v1.json
    - backend/src/main/resources/db/migration/
    - backend/src/test/java/cherry/mastersmith/ArchitectureTest.java
    - backend/src/test/java/cherry/mastersmith/useradmin/UserAdminBoundaryArchitectureTest.java
    - backend/src/test/java/cherry/mastersmith/auth/AuthBoundaryArchitectureTest.java
    - backend/build.gradle.kts
    - gradle/libs.versions.toml
    - frontend/src/app/registry/types.ts
    - frontend/src/app/registry/registrationModules.ts
    - frontend/src/app/registry/loadRegistrations.ts
    - frontend/src/app/registry/validateRegistrations.ts
    - frontend/src/app/navigation/navigationItems.ts
    - frontend/src/app/layout/ShellLayout.tsx
    - frontend/src/app/routing/decideRoute.ts
    - frontend/src/app/routing/AppRouter.tsx
    - frontend/src/app/admin-forbidden/AdminForbiddenProvider.tsx
    - frontend/src/app/pages/HomePage.tsx
    - frontend/src/shared/api-client/adminForbidden.ts
    - frontend/src/features/auth/loginStateProvider.ts
    - frontend/src/features/admin/registration.ts
    - frontend/src/features/dsl/DslMenuTree.tsx
    - frontend/src/features/dsl/api/types.ts
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/AppShell/AppShell.tsx
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/AppShell/Sidebar.tsx
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Icon/registry.ts
  components:
    - access
    - common-security
    - config
    - auth
    - appearance
    - invitation
    - user
    - useradmin
    - audit
    - dsl
    - dslmanage
    - backend-test-support
    - build-and-verify
    - frontend-registry
    - frontend-app-core
    - frontend-app-layout-i18n
    - frontend-api-client
    - frontend-feature-auth
    - frontend-feature-admin
    - frontend-feature-dsl
    - make-you-chic-ui
shallow:
  paths:
    - backend/src/main/java/cherry/mastersmith/access/service/
    - backend/src/main/java/cherry/mastersmith/auth/
    - backend/src/main/java/cherry/mastersmith/user/service/
    - backend/src/main/java/cherry/mastersmith/user/web/
    - backend/src/main/java/cherry/mastersmith/useradmin/
    - backend/src/main/java/cherry/mastersmith/audit/
    - backend/src/main/java/cherry/mastersmith/common/error/
    - backend/src/main/java/cherry/mastersmith/common/health/
    - backend/src/main/java/cherry/mastersmith/common/i18n/
    - backend/src/main/java/cherry/mastersmith/common/observability/
    - backend/src/main/java/cherry/mastersmith/common/paging/
    - backend/src/main/java/cherry/mastersmith/common/persistence/
    - backend/src/main/java/cherry/mastersmith/common/web/
    - backend/src/main/java/cherry/mastersmith/dsl/
    - backend/src/main/java/cherry/mastersmith/dslmanage/
    - backend/src/main/java/cherry/mastersmith/targetdb/
    - backend/src/main/java/cherry/mastersmith/invitation/
    - backend/src/main/java/cherry/mastersmith/mail/
    - backend/src/main/java/cherry/mastersmith/appearance/
    - backend/src/test/java/cherry/mastersmith/
    - frontend/src/features/
    - frontend/src/shared/
    - frontend/src/app/display-settings/
    - frontend/src/app/i18n/
    - frontend/e2e/
    - frontend/package.json
    - frontend/vitest.config.ts
    - vendor/make-you-chic-ui/
    - vendor/java-mustache-processor/
    - build.gradle.kts
    - .github/workflows/ci.yml
    - compose.yaml
    - docker/
    - perf/
    - Dockerfile
    - README.md
```
