# リバースエンジニアリングの実施記録（mastersmith2）

| 項目 | 値 |
|---|---|
| 実施日 | 2026-09-23 |
| リポジトリ | mastersmith2（プロジェクトルート `./`） |
| ブランチ | `develop` |
| コミット | `6afbf9798f5b2854c50dc7f4b3719f5ffc1db152` |
| Intent | `260923-audit-pool-exhaustion`（同時10件のログインでコネクションプールが尽き、監査の書き込みが失敗する（F2）を直す） |
| 深さ | Minimal |
| 走査の広さ | 全体の再走査（既存の CodeKB なし） |
| 走査の担当 | developer（走査）→ architect（統合と9つの成果物） |

## 範囲についての注記

全体を走査したが、行番号まで深く読んだのは F2 に関わる範囲（ログインの流れ・監査の記録・トランザクションと接続の扱い・データソースの設定・関連するテスト・ビルドの設定）だけである。そのため Scope of Analysis は `kind: partial` とし、深く読んだ場所だけを `analyzed.paths` に、流し読みの場所を `shallow.paths` に記す。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: 260923-audit-pool-exhaustion
fingerprint: 12c042ca24fa775d4fde1375a013deb0a1b17091
analyzed:
  paths:
    - backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java
    - backend/src/main/java/cherry/mastersmith/auth/service/LogoutService.java
    - backend/src/main/java/cherry/mastersmith/auth/service/TokenRefreshService.java
    - backend/src/main/java/cherry/mastersmith/auth/web/AuthController.java
    - backend/src/main/java/cherry/mastersmith/auth/repository/
    - backend/src/main/java/cherry/mastersmith/auth/domain/AuthenticationEvent.java
    - backend/src/main/java/cherry/mastersmith/audit/
    - backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java
    - backend/src/main/java/cherry/mastersmith/user/repository/UserRepository.java
    - backend/src/main/java/cherry/mastersmith/access/service/AccessDeniedEventPublisher.java
    - backend/src/main/resources/application.yaml
    - backend/src/main/resources/db/migration/V4__u4_audit_event.sql
    - backend/build.gradle.kts
    - build.gradle.kts
    - settings.gradle.kts
    - gradle/libs.versions.toml
    - backend/gradle.lockfile
    - backend/src/test/java/cherry/mastersmith/auth/service/LoginConcurrencyIT.java
    - backend/src/test/java/cherry/mastersmith/audit/
    - backend/src/test/java/cherry/mastersmith/common/testsupport/TestDatabase.java
    - backend/src/test/resources/
  components:
    - LoginService
    - LogoutService
    - TokenRefreshService
    - AuthController
    - LoginAttemptStateRepository
    - RefreshTokenRepository
    - AuditEventListener
    - AuditEventRecorder
    - AuditEventRepository
    - AuditConfig
    - UserAccountService
    - UserRepository
    - AccessDeniedEventPublisher
    - mastersmith-db コネクションプール
shallow:
  paths:
    - backend/src/main/java/cherry/mastersmith/
    - backend/src/test/java/cherry/mastersmith/
    - frontend/
    - .github/
    - Dockerfile
    - compose.yaml
    - docker/
    - perf/
    - config/
    - .pre-commit-config.yaml
    - .gitmodules
    - README.md
    - vendor/make-you-chic-ui/
```
