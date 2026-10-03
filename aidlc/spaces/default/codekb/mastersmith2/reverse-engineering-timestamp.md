# Reverse Engineering の実施記録（mastersmith2）

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-10-04 |
| Intent | `261003-user-admin-followup`（scope bugfix、深さ Minimal。前の Intent `260930-user-admin` の振り返りの第1の束 K1〜K3・T1〜T4・S1 の直し） |
| 対象 | mastersmith2（プロジェクトルート、単一リポジトリ） |
| 記録するコミット | `47541e3b5a7035892ffee81d9b93a174166abb86`（`develop` の HEAD、`git rev-parse HEAD` で取得）。スキャンの前に取ったスナップショットの source の値とは別のもの |
| サブモジュール `vendor/make-you-chic-ui` の固定先 | `3d9521aa54b1d6277de473f9e935a496fb56ac1b`（`git ls-files -s` で確かめた。上流の `e82b651` は手元に取得済みで未固定） |
| サブモジュール `vendor/java-mustache-processor` の固定先 | `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`（`0.1.0`、変わっていない） |
| 走査の広さ・深さ | 範囲を絞った走査（Focused scan）・Minimal。スナップショットの paths は `frontend/src/features/useradmin/`・`frontend/e2e/`・`frontend/playwright.config.ts`・`backend/src/main/java/cherry/mastersmith/useradmin/`・`backend/src/test/java/cherry/mastersmith/useradmin/`・`backend/src/main/java/cherry/mastersmith/{audit,user,invitation}/`・`backend/src/test/java/cherry/mastersmith/mail/config/`・`perf/`・`docker/monitoring/`。既存の知識ベースの判定は UNVERIFIED |
| 手順 | 開発担当のコードスキャン（`inception/reverse-engineering/developer-scan.md`）→ アーキテクトによる統合（既存の9文書を読み、今回の範囲の節を足し・直し、範囲の外の文は残した） |

## 確かめ方

- ファイルの読み取りと、読み取りの git（`git rev-parse`・`git ls-files -s`・`git -C vendor/make-you-chic-ui log -1`）だけで確かめた。Gradle・npm・Docker は実行していない。`.env`・鍵ファイル・Git 管理外の参考資料は開いていない。個人に関する値は本文に写していない。
- アーキテクトが自分で確かめ直したもの: 記録するコミットとサブモジュールの固定先、`e82b651` がサブモジュールに取得済みであることとその件名、`useradmin` の本体とテストのファイルの一覧、`UserAdminController` のマッピングの注釈（`api-documentation.md`）、`useradmin` の import と `useradmin` を import する側（`dependencies.md`）、`packagesJudgedByTotal`（7 個）と注記（K-7）、移行の一覧（V9 の存在）、`frontend/e2e` と `support/` のファイルの一覧、画面の登録の order 230、Jackson の版（3.1.7）、`OutputCaptureExtension` を使うテストのファイルの数（31）。
- 事実と見立て（仮説）は、開発担当の記録どおり各所見の本文で分けて書いた。
- 開発担当の記録との差: `backend/src/main/java/cherry/mastersmith/useradmin/` は、開発担当は「一意の制約に当たる処理が無いことの確かめ」（検索）として深く読んだ側に挙げたが、読んだファイルの名前が無いため、記録上は `shallow.paths` に置いた（範囲を狭める向きの直し）。`frontend/playwright.config.ts` はスナップショットにあるが、開発担当が読んでいないとしたため、どちらにも入れていない。

## 前の記録との関係

前のコード知識ベースは Intent `260930-user-admin` の開始時のもの（partial、11 部品・74 パス、記録のコミット `31b980b`）。その Intent で利用者の管理（`useradmin`・`frontend-feature-useradmin`、移行 V9、E2E 110〜130 など）が作られたため、前の記録の「利用者の管理が無い」「利用停止の状態が無い」などの文と、make-you-chic-ui の固定先 `077f5b4`・Jackson 3.1.6・`packagesJudgedByTotal` 12 個・E2E 10 本は、今の HEAD とずれていた。

判定が UNVERIFIED のため、今回は次の扱いにした。

- 9 文書を丸ごと置き換えず、前回の文を残したうえで、今回の範囲の節を足し、確かめ直した古い事実（固定先・Jackson・`packagesJudgedByTotal`・E2E の数・API の有無・移行の数）を直した。確かめ直していない前回の文には「前回の記録」と書き添えた。
- Scope of Analysis の `analyzed.paths` と `analyzed.components` は今回の走査の実績だけから作った。前回の `analyzed.paths`（74）は、今回深く読み直した3つ（`AuditEventListener.java`・`UserAccountService.java`・`InvitationService.java`）を除いて `shallow.paths` に下げ、前回の `shallow.paths` と今回の流し読みの範囲（`common/`・`JsonLogRecords.java`・`useradmin` の両側のディレクトリ）を合わせた。
- `component-inventory.md` の前回「深い」とした部品は、「流し読み（前回は深い）」に書き換えた。

## 所見の番号

前回の所見は K-1〜K-9。`business-overview.md` の「前々回の所見の扱い」の表にその前の番号 K-10〜K-16 が残っているため、取り違えを避けて今回の所見は K-17〜K-24 とした（開発担当の束の項目 K1・K2・K3・T1・T2・T3・T4・S1 を、K1 を K-17・K-18 に分け、ほかを同じ順で当てた）。一覧は `business-overview.md`。

## 部品 ID の扱い

- 前回の 36 個の ID をそのまま使い、開発担当の提案どおり `useradmin`（`backend/src/main/java/cherry/mastersmith/useradmin/`）と `frontend-feature-useradmin`（`frontend/src/features/useradmin/`）を足して 38 個にした。名前の変更は無い。
- `analyzed.components` には、今回深く読んだファイルを持つ 8 個を入れた: `useradmin`（テスト2クラス）・`frontend-feature-useradmin`・`frontend-e2e`・`audit`・`user`・`invitation`・`mail`（`MailConfigurationIT` だけ）・`perf-and-monitoring`。どれも部品の一部だけを読んだ。

## 範囲の要約

- 深く読んだ: 20 のパス（すべてファイル）、8 の部品。パスは開発担当の Scan Coverage の「Analyzed deeply」の括弧の中のファイルで、スナップショットの paths の中にある。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` で、`./` は `analyzed.paths` に入れていない（前回までと同じ扱い）。
- `fingerprint` は、最終の `analyzed.paths`（20）をカンマ区切りで `aidlc engine workspace codekb-scope-diff --mint --paths` に渡した出力をそのまま貼った。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: 261003-user-admin-followup
fingerprint: 942ce8a577919397e70026b68fda78979951d674
analyzed:
  paths:
    - frontend/src/features/useradmin/ConfirmActionDialog.tsx
    - frontend/src/features/useradmin/EditProfileDialog.tsx
    - frontend/src/features/useradmin/UserRowActions.tsx
    - frontend/src/features/useradmin/UserAdminPage.tsx
    - frontend/src/features/useradmin/useUserAdmin.ts
    - frontend/src/features/useradmin/focusTarget.ts
    - frontend/src/features/useradmin/UserAdminPage.test.tsx
    - frontend/src/features/useradmin/EditProfileDialog.test.tsx
    - frontend/e2e/110-user-admin-flow.e2e.ts
    - frontend/e2e/120-user-admin-accessibility.e2e.ts
    - frontend/e2e/support/overflow.ts
    - backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminOperationsApiIT.java
    - backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminListApiIT.java
    - backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java
    - backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java
    - backend/src/main/java/cherry/mastersmith/invitation/service/InvitationService.java
    - backend/src/test/java/cherry/mastersmith/mail/config/MailConfigurationIT.java
    - perf/README.md
    - perf/k6/scenarios.js
    - docker/monitoring/provisioning/alerting/mastersmith.yaml
  components:
    - useradmin
    - frontend-feature-useradmin
    - frontend-e2e
    - audit
    - user
    - invitation
    - mail
    - perf-and-monitoring
shallow:
  paths:
    - backend/src/main/java/cherry/mastersmith/user/domain/User.java
    - backend/src/main/java/cherry/mastersmith/user/domain/UserProblemTypes.java
    - backend/src/main/java/cherry/mastersmith/user/repository/
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
    - backend/src/main/java/cherry/mastersmith/invitation/web/InvitationAdminController.java
    - backend/src/main/java/cherry/mastersmith/invitation/web/InvitationSecurityContributor.java
    - backend/src/main/java/cherry/mastersmith/invitation/web/InvitationRequestContextResolver.java
    - backend/src/main/java/cherry/mastersmith/invitation/web/InvitationPageResponse.java
    - backend/src/main/java/cherry/mastersmith/invitation/domain/InvitationPaging.java
    - backend/src/main/java/cherry/mastersmith/invitation/service/InvitationBarrier.java
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
    - backend/src/main/java/cherry/mastersmith/useradmin/
    - frontend/src/features/useradmin/
    - backend/src/test/java/cherry/mastersmith/useradmin/
    - backend/src/main/java/cherry/mastersmith/common/
    - backend/src/test/java/cherry/mastersmith/common/testsupport/JsonLogRecords.java
```
