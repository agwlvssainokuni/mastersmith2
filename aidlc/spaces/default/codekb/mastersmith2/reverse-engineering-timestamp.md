# Reverse Engineering の実施記録（mastersmith2）

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-09-30 |
| Intent | `260930-user-admin`（scope classic、深さ Standard。利用者の管理の画面: 一覧・管理者の印・利用停止・ロックの解除） |
| 対象 | mastersmith2（プロジェクトルート、単一リポジトリ） |
| 記録するコミット | `31b980b1205b9dc3884565eacbbe7c9b8eb3ed90`（`develop` の HEAD、`git rev-parse HEAD` で取得）。スキャンの前に取ったスナップショットの source の値（`git:8dca…`）はコミットではない値で、この値とは別のもの |
| サブモジュール `vendor/make-you-chic-ui` の固定先 | `077f5b48ce84cd020ecec2d925836a085f9d9e11` |
| サブモジュール `vendor/java-mustache-processor` の固定先 | `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`（`0.1.0`） |
| 走査の広さ・深さ | 全体の読み直し（Full rescan、スナップショットの paths は `./`）・Standard。既存の知識ベースは STALE で、9 文書を丸ごと置き換える |
| 手順 | 開発担当のコードスキャン（`inception/reverse-engineering/developer-scan.md`）→ アーキテクトによる統合（この9つの成果物） |

## 確かめ方

- ファイルの読み取りと、読み取りの git（`git rev-parse`・`git log`・`git submodule status`）だけで確かめた。Gradle・npm・Docker は実行していない。`.env`・鍵ファイル・Git 管理外の参考資料は開いていない。個人に関する値は本文に写していない。
- アーキテクトが自分で確かめ直したもの: 記録するコミットとサブモジュールの固定先、バックエンドのパッケージ間の `import` の数（`dependencies.md`、前回と同じ）、`LockPolicy` の判定（K-2）、失敗の理由の列挙（`LoginFailureReason`・`TokenFailureReason`・`AccessDeniedReason.of`・`AuditEventType`・`AuditFailureReason`、K-1・K-6）、移行 V2・V3・V4・V7 の列と外部キー（K-1）、認証の3つの入口のコード（`AccessTokenAuthenticationProvider`・`TokenRefreshService`・`LoginService.decide`・`UserAccountService.verifyPassword`）、`AdminAuthorizationManager`（K-4）、`packagesJudgedByTotal` と注記（K-7）、境界の検査の決まりの名前（K-3）、`TraceAspect` の対象の指定（K-8）、監査の記録のトランザクションの形、差し込み口の order、招待の管理の controller と結果の型（K-5）、画面の登録の order、主な部品の版（`libs.versions.toml`・Wrapper・`frontend/package.json`）、ファイルの数。
- 開発担当の記録との差: `refresh_tokens` と `invitations` の外部キーは V3・V8 の `ALTER TABLE` で足されていることを確かめた（開発担当の記録どおり）。要求の文脈の読み取りの複製は、開発担当の挙げた2つのほかに `dslmanage/web/DslRequestContextResolver.java` があることをファイル名の検索で見つけた（中身は読んでいない。`code-quality-assessment.md`）。`appearance` の差し込み口の order は 410 と確かめた。
- 事実と見立て（仮説）は各所見の本文で分けて書いた。

## 前の記録との関係

前のコード知識ベースは Intent `260929-log-deps-cleanup` の開始時のもの（partial、5 部品・22 パス、記録のコミット `c1ed553`）。その Intent の中で所見 K-10〜K-16 が直され、知識ベースの一部（make-you-chic-ui の固定先 `310e1ec`、E2E 100 の既知の違反2件、`ms-check-p95` の 300 ms、初期管理者の INFO のキー `email`）が今の HEAD とずれていた（STALE）。前回の所見の扱いは `business-overview.md`。

依頼者が Full rescan を選んだため、9 文書を丸ごと置き換え、Scope of Analysis はこの走査の実績だけから作った。前回の `analyzed.paths` と `shallow.paths` は引き継いでいない。前回までの文のうち今回確かめていないもの（`mail`・`dsl` 系・`perf-and-monitoring` などの責務）は、流し読みの扱いとして各部品に短く残した。

## 所見の番号

全体の読み直しのため、所見の番号は K-1 から振り直した（開発担当の S-1〜S-9 を同じ順で K-1〜K-9）。前回までの K の番号とは対応しない。一覧は `business-overview.md`。

## 部品 ID の扱い

- 前回の 36 個の ID をそのまま使い、足しも名前の変更もしていない（見出しの集合は前回と同じ）。
- `analyzed.components` には、開発担当が深く読んだとした 11 個を入れた: `user`・`auth`・`access`・`audit`・`invitation`・`config`・`frontend-registry`・`frontend-feature-admin`・`frontend-feature-invitation`・`frontend-feature-auth`・`build-and-verify`。このうち `invitation`・`config`・`frontend-feature-invitation` は、今回の Intent の型として要る部分だけを深く読んだ（`component-inventory.md` の「深い（一部）」）。

## 範囲の要約

- 深く読んだ: 74 のパス、11 の部品。パスは開発担当の Scan Coverage の「Analyzed deeply」のとおりで、それより広げていない。ただし、開発担当がディレクトリで挙げながら「ほかはファイル名だけ」とした `auth/domain/`（10 ファイル）と `access/web/`（5 ファイル）は、読んだファイルを1つずつ `analyzed.paths` に入れ、ディレクトリそのものは `shallow.paths` に置いた（範囲を狭める向きの直し）。
- 部分だけを読んだファイル（`AuditEventFactory.java` の 1〜260 行、`AuditEventListener.java` の 1〜200 行、`InvitationService.java` の一覧の部分、`invitationApi.ts` の 1〜80 行、境界の検査の決まりの名前と対象、`backend/build.gradle.kts` の関わる節、`vitest.config.ts` の下限の行）は、開発担当の記録どおり `analyzed.paths` に入れ、読んだ範囲を各文書の本文に書いた。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` で、`./` は `analyzed.paths` に入れていない（前回までと同じ扱い）。
- `fingerprint` は、最終の `analyzed.paths`（74）をカンマ区切りで `aidlc engine workspace codekb-scope-diff --mint --paths` に渡した出力をそのまま貼った。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: 260930-user-admin
fingerprint: 8c444e8df425a92b3a1e1b8911107884d8c66e93
analyzed:
  paths:
    - backend/src/main/java/cherry/mastersmith/user/domain/User.java
    - backend/src/main/java/cherry/mastersmith/user/domain/UserProblemTypes.java
    - backend/src/main/java/cherry/mastersmith/user/repository/
    - backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java
    - backend/src/main/java/cherry/mastersmith/user/service/UserSummary.java
    - backend/src/main/java/cherry/mastersmith/user/service/PasswordVerification.java
    - backend/src/main/java/cherry/mastersmith/user/service/CreateUserResult.java
    - backend/src/main/java/cherry/mastersmith/user/service/NewUser.java
    - backend/src/main/java/cherry/mastersmith/user/service/UserCreatedEvent.java
    - backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java
    - backend/src/main/java/cherry/mastersmith/user/service/UserAccountConfig.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/LoginAttemptState.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/LockPolicy.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/LockState.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/LockDecision.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/AuthenticatedUser.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/AuthenticationEvent.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/AuthenticationEventType.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/LoginFailureReason.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/TokenFailureReason.java
    - backend/src/main/java/cherry/mastersmith/auth/domain/AuthProblemTypes.java
    - backend/src/main/java/cherry/mastersmith/auth/repository/
    - backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java
    - backend/src/main/java/cherry/mastersmith/auth/service/AccessTokenService.java
    - backend/src/main/java/cherry/mastersmith/auth/service/TokenRefreshService.java
    - backend/src/main/java/cherry/mastersmith/auth/service/LogoutService.java
    - backend/src/main/java/cherry/mastersmith/auth/service/LoginAttemptStateInitializer.java
    - backend/src/main/java/cherry/mastersmith/auth/service/AuthProperties.java
    - backend/src/main/java/cherry/mastersmith/auth/web/AccessTokenAuthenticationProvider.java
    - backend/src/main/java/cherry/mastersmith/auth/web/AuthController.java
    - backend/src/main/java/cherry/mastersmith/auth/web/AuthSecurityContributor.java
    - backend/src/main/java/cherry/mastersmith/auth/web/CurrentUserResponse.java
    - backend/src/main/java/cherry/mastersmith/access/domain/AdminPaths.java
    - backend/src/main/java/cherry/mastersmith/access/domain/AccessDeniedReason.java
    - backend/src/main/java/cherry/mastersmith/access/web/AdminSecurityContributor.java
    - backend/src/main/java/cherry/mastersmith/access/web/AdminAuthorizationManager.java
    - backend/src/main/java/cherry/mastersmith/access/web/AdminCheckController.java
    - backend/src/main/java/cherry/mastersmith/access/web/AdminApiDefaultAccess.java
    - backend/src/main/java/cherry/mastersmith/access/web/AccessWebSecurityConfig.java
    - backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java
    - backend/src/main/java/cherry/mastersmith/audit/domain/AuditResult.java
    - backend/src/main/java/cherry/mastersmith/audit/domain/AuditFailureReason.java
    - backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventFactory.java
    - backend/src/main/java/cherry/mastersmith/audit/repository/
    - backend/src/main/java/cherry/mastersmith/audit/service/AuditEventRecorder.java
    - backend/src/main/java/cherry/mastersmith/audit/service/AuditConfig.java
    - backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java
    - backend/src/main/java/cherry/mastersmith/invitation/web/InvitationAdminController.java
    - backend/src/main/java/cherry/mastersmith/invitation/web/InvitationSecurityContributor.java
    - backend/src/main/java/cherry/mastersmith/invitation/web/InvitationRequestContextResolver.java
    - backend/src/main/java/cherry/mastersmith/invitation/web/InvitationPageResponse.java
    - backend/src/main/java/cherry/mastersmith/invitation/domain/InvitationPaging.java
    - backend/src/main/java/cherry/mastersmith/invitation/service/InvitationBarrier.java
    - backend/src/main/java/cherry/mastersmith/invitation/service/InvitationService.java
    - backend/src/main/java/cherry/mastersmith/config/SecurityConfig.java
    - backend/src/main/resources/db/migration/
    - backend/src/test/java/cherry/mastersmith/ArchitectureTest.java
    - backend/src/test/java/cherry/mastersmith/auth/AuthBoundaryArchitectureTest.java
    - backend/src/test/java/cherry/mastersmith/audit/AuditBoundaryArchitectureTest.java
    - backend/src/test/java/cherry/mastersmith/invitation/InvitationBoundaryArchitectureTest.java
    - backend/build.gradle.kts
    - backend/config/spotbugs-exclude.xml
    - gradle/libs.versions.toml
    - settings.gradle.kts
    - .github/dependabot.yml
    - frontend/package.json
    - frontend/vitest.config.ts
    - frontend/src/app/registry/types.ts
    - frontend/src/app/navigation/navigationItems.ts
    - frontend/src/features/README.md
    - frontend/src/features/admin/registration.ts
    - frontend/src/features/invitation/registration.ts
    - frontend/src/features/invitation/api/invitationApi.ts
    - frontend/src/features/auth/authSession.ts
  components:
    - user
    - auth
    - access
    - audit
    - invitation
    - config
    - frontend-registry
    - frontend-feature-admin
    - frontend-feature-invitation
    - frontend-feature-auth
    - build-and-verify
shallow:
  paths:
    - backend/src/main/java/cherry/mastersmith/
    - backend/src/main/java/cherry/mastersmith/auth/domain/
    - backend/src/main/java/cherry/mastersmith/access/web/
    - backend/src/main/java/cherry/mastersmith/user/domain/
    - backend/src/main/java/cherry/mastersmith/user/web/
    - backend/src/main/java/cherry/mastersmith/invitation/
    - backend/src/main/resources/
    - backend/src/test/java/cherry/mastersmith/
    - frontend/src/
    - frontend/e2e/
    - build.gradle.kts
    - .github/workflows/ci.yml
    - README.md
    - Dockerfile
    - compose.yaml
    - backend/gradle.lockfile
    - frontend/package-lock.json
    - vendor/make-you-chic-ui/
    - vendor/java-mustache-processor/
    - perf/
    - docker/
    - config/
    - .pre-commit-config.yaml
    - .gitleaks.toml
```
