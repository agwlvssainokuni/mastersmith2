# リバースエンジニアリングの実施記録（mastersmith2）

| 項目 | 値 |
|---|---|
| 実施日 | 2026-09-23 |
| リポジトリ | mastersmith2（プロジェクトルート） |
| ブランチ | `develop` |
| コミット | `aeeaf73e9971a48d502291b94c26df84ad306cc1`（アプリのソースに未コミットの変更なし。ワークフローの記録だけに変更あり） |
| Intent | `260923-colima-spec-up`（高い負荷でアプリのコンテナがメモリの上限で止まる（F3）と、CPU 2 でログインが目標の 1 秒を超える（F4）を、colima の VM の性能を上げて直す） |
| 深さ | Minimal |
| 走査の広さ | Focused scan（コンテナの資源・JVM・ログインの CPU 負荷・負荷の試験・監視に絞る） |
| 既存の CodeKB | STALE（前回 Intent `260923-audit-pool-exhaustion`、コミット `6afbf97` で作成。その後 F2 の修正で分析済みのファイルが変わった） |
| 走査の担当 | developer（走査）→ architect（統合と9つの成果物） |

## 範囲についての注記

- 今回深く読んだのは、コンテナと起動の定義（`Dockerfile`・`compose.yaml`・`docker/`・`.dockerignore`・`.env.example`）、負荷の試験（`perf/`）、`README.md` の関係する節、`application.yaml`、`config/` パッケージ、`LoginService` である。`docker/monitoring/` のダッシュボードの JSON はパネルの題と式だけを読んだ。
- 既存の CodeKB が STALE のため、前回の `analyzed.paths` は再確認できないものとして `shallow.paths` に下げた。前回の記述（監査と接続）は残し、今回確かめた値（プールの上限 30 など）だけを更新した。
- この PC の実行環境は読み取りだけで確かめた（`colima list` で CPU 2・メモリ 2GiB・aarch64、`docker info` で NCPU 2・MemTotal 約 1.9GiB）。`.env` は開いていない。
- 前の Intent の記録（性能の試験の結果・配備の記録）の F3・F4 とメモリの行は流し読みで参照した（ワークフローの記録のため、下の範囲には含めない）。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: 260923-colima-spec-up
fingerprint: 6d54a0d29462ad3e6f28efa601ac4cb491aacb70
analyzed:
  paths:
    - Dockerfile
    - compose.yaml
    - docker/perf/compose.yaml
    - docker/monitoring/
    - docker/otel-collector/config.yaml
    - perf/
    - README.md
    - .env.example
    - .dockerignore
    - backend/src/main/resources/application.yaml
    - backend/src/main/java/cherry/mastersmith/config/
    - backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java
  components:
    - LoginService
    - mastersmith-db コネクションプール
    - app コンテナ
    - mastersmith-perf 負荷試験環境
    - lgtm 監視コンテナ
    - otel-collector コンテナ
    - SecurityConfig
    - WebConfig
    - ObservabilityConfig
    - ForwardedHeaderConfig
    - SecurityHeaderProperties
shallow:
  paths:
    - backend/src/main/java/cherry/mastersmith/auth/service/LogoutService.java
    - backend/src/main/java/cherry/mastersmith/auth/service/TokenRefreshService.java
    - backend/src/main/java/cherry/mastersmith/auth/web/AuthController.java
    - backend/src/main/java/cherry/mastersmith/auth/repository/
    - backend/src/main/java/cherry/mastersmith/auth/domain/AuthenticationEvent.java
    - backend/src/main/java/cherry/mastersmith/audit/
    - backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java
    - backend/src/main/java/cherry/mastersmith/user/service/UserAccountConfig.java
    - backend/src/main/java/cherry/mastersmith/user/repository/UserRepository.java
    - backend/src/main/java/cherry/mastersmith/access/service/AccessDeniedEventPublisher.java
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
    - backend/src/main/java/cherry/mastersmith/
    - backend/src/test/java/cherry/mastersmith/
    - frontend/
    - .github/
    - config/
    - .pre-commit-config.yaml
    - .gitmodules
    - vendor/make-you-chic-ui/
```
