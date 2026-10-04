# Reverse Engineering の実施記録（mastersmith2）

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-10-04 |
| Intent | `261004-safety-carryover`（scope bugfix、深さ Minimal。安全の機能の判断と持ち越し: 初期管理者の救済と監査、ログインの p95、BUSY の traceId、Tomcat の ERROR、言語の欄のフォーカス、`team.md` の 12 パッケージの記述、対象DB のイメージの固定先と `Dockerfile` のダイジェスト） |
| 対象 | mastersmith2（プロジェクトルート、単一のリポジトリ） |
| 記録するコミット | `47ec27b3efb54a60187d7f47ad9ce72041088154`（`develop` の HEAD）。スキャンの前に取ったスナップショットの source の値はコミットではないため、ここには書かない |
| 走査の広さ・深さ | 全体の読み直し（Full rescan）・Minimal。スナップショットの paths は `./`。深く読んだのは今回の Intent の論点に関わるファイルだけ |
| 手順 | 開発担当のコードスキャン（`inception/reverse-engineering/developer-scan.md`）→ アーキテクトによる統合（9 文書をすべて新しく書き直した） |

## 確かめ方

- ファイルの読み取り（Read・Grep・読み取りだけのシェル）で確かめた。Gradle・npm・Docker は実行していない。`.env`・鍵ファイル・Git 管理外の参考資料は開いていない。メールアドレス・秘密の値は本文に写していない。
- アーキテクトが自分で確かめ直したもの: 深く読んだ 42 のパスがすべて存在すること、`InitialAdminInitializer` の作成の判定と INFO のキー（92〜105 行）、`InitialAdminProperties.toString()`（33 行）、`AuditEventType` の 20 種類、`UserCreatedEvent` を受けるのが `auth.service.LoginAttemptStateInitializer` だけであること、`audit_events.event_type`・`source_ip` の型（V4）、`LoginService` の停止の判定（189 行）、`ErrorPathController` の 5xx の ERROR（84〜91 行）、`UserRowLockRepository` の待ちの上限 3000 ms、`packagesJudgedByTotal` の 7 個、`common/persistence`・`common/paging` のファイルとそれを import する側、`compose.yaml`・`docker/perf/compose.yaml` のイメージの行、`Dockerfile` の `FROM`、`.github/dependabot.yml` の `directory`、移行 V1〜V9 の一覧、記録するコミット（読み取りだけの `git rev-parse HEAD` で依頼の値と一致）。
- 事実と見立て（仮説）は、開発担当の記録どおり各所見の本文で分けて書いた。

## 前の記録との関係

前のコード知識ベースは Intent `261003-user-admin-followup` の開始時のもの（partial、8 部品・20 パス、記録のコミット `47541e3`）。依頼者が Full rescan を選んだため、9 文書は前回の文を引き継がずに書き直した。前回までに深く読み、今回は確かめ直していない内容は、本文で「前回までの記録」と書き分けた。Scope of Analysis は今回の走査だけから作り、前回の `analyzed.paths` は取り込んでいない。

## 開発担当の記録との差

- `common-observability`: 開発担当は「ログの項目の設定の範囲」として深く読んだ部品に挙げたが、読んだのは `backend/src/main/resources/logback-spring.xml` で、`common/observability/` のファイル（`TraceAspect` など）は読んでいない。`component-inventory.md` では `logback-spring.xml`・`application.yaml` は `config` の場所に入るため、`analyzed.components` には `config` を入れ、`common-observability` は入れていない（範囲を狭める向きの直し）。
- `V4__u4_audit_event.sql` と一緒に挙がった V6・V7 は `audit_events` の列の追加の行だけを見たため、`analyzed.paths` には V4 だけを入れ、移行のディレクトリは `shallow.paths` に置いた。
- 括弧で範囲を限って読んだファイル（`UserAccountService.java`・`UserRepository.java`・`AuditEventFactory.java`・`TokenRefreshService.java`・`UserAdminService.java`・`UserAdminController.java`・`GlobalExceptionHandler.java`・`application.yaml`・`logback-spring.xml`・`InitialAdminIT.java`・`UserAdminBusyApiIT.java`・`backend/build.gradle.kts`・`compose.yaml`・`docker/perf/compose.yaml`・`perf/k6/scenarios.js`・`.env.example`・`README.md`・`gradle/libs.versions.toml`）は、前回までと同じくファイルの単位で `analyzed.paths` に入れた。どの範囲を読んだかは各文書の本文に書いた。

## 所見の番号

前回の所見は K-17〜K-24 で終わるため、今回は開発担当の番号どおり **K-25〜K-31** とした。一覧は `business-overview.md`、本文は持ち主の文書に1か所だけ書いた。

## 部品 ID の扱い

- 前回の 38 個の ID をそのまま使い、開発担当の提案どおり `common-persistence`（`backend/src/main/java/cherry/mastersmith/common/persistence/`）を足して 39 個にした。K-27 の L4 のログの持ち主で、`user`・`auth`・`invitation`・`useradmin`・`common-error`・`common-observability` から使われる独立した部品のため。名前の変更は無い。
- `common/paging`（`Paging.java` の1ファイル）は独立した ID にせず、`common-web` の場所に寄せた（小さく、今回の論点に関わらないため）。
- `analyzed.components` は、今回深く読んだファイルを持つ 13 個。どれも部品の一部だけを読んだ「一部」の深さである。

## 範囲の要約

- 深く読んだ: 42 のパス（すべてファイル）、13 の部品。パスは開発担当の Scan Coverage の「Analyzed deeply」で、スナップショットの paths（`./`）の中にある。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` で、`./` は `analyzed.paths` に入れていない（前回までと同じ扱い）。
- `fingerprint` は、最終の `analyzed.paths`（42）をカンマ区切りで `aidlc engine workspace codekb-scope-diff --mint --paths` に渡した出力をそのまま貼った。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: 261004-safety-carryover
fingerprint: aa7539e171fdaa09417c1cea0d2c41829effcff3
analyzed:
  paths:
    - backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java
    - backend/src/main/java/cherry/mastersmith/user/service/InitialAdminProperties.java
    - backend/src/main/java/cherry/mastersmith/user/service/UserAccountService.java
    - backend/src/main/java/cherry/mastersmith/user/service/UserCreatedEvent.java
    - backend/src/main/java/cherry/mastersmith/user/service/UserSummary.java
    - backend/src/main/java/cherry/mastersmith/user/service/UserAccountConfig.java
    - backend/src/main/java/cherry/mastersmith/user/service/PasswordProperties.java
    - backend/src/main/java/cherry/mastersmith/user/repository/UserRowLockRepository.java
    - backend/src/main/java/cherry/mastersmith/user/repository/UserRepository.java
    - backend/src/main/java/cherry/mastersmith/audit/domain/AuditEvent.java
    - backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventType.java
    - backend/src/main/java/cherry/mastersmith/audit/domain/AuditEventFactory.java
    - backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java
    - backend/src/main/java/cherry/mastersmith/audit/service/AuditEventRecorder.java
    - backend/src/main/resources/db/migration/V4__u4_audit_event.sql
    - backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java
    - backend/src/main/java/cherry/mastersmith/auth/service/LoginAttemptStateInitializer.java
    - backend/src/main/java/cherry/mastersmith/auth/web/AccessTokenAuthenticationProvider.java
    - backend/src/main/java/cherry/mastersmith/auth/service/TokenRefreshService.java
    - backend/src/main/java/cherry/mastersmith/useradmin/service/UserAdminService.java
    - backend/src/main/java/cherry/mastersmith/useradmin/service/UserAdminBarrier.java
    - backend/src/main/java/cherry/mastersmith/useradmin/web/UserAdminController.java
    - backend/src/main/java/cherry/mastersmith/common/persistence/RowLockFailures.java
    - backend/src/main/java/cherry/mastersmith/common/persistence/RowLockAttempt.java
    - backend/src/main/java/cherry/mastersmith/common/error/web/GlobalExceptionHandler.java
    - backend/src/main/java/cherry/mastersmith/common/error/web/ErrorPathController.java
    - backend/src/main/resources/application.yaml
    - backend/src/main/resources/logback-spring.xml
    - backend/src/test/java/cherry/mastersmith/user/service/InitialAdminIT.java
    - backend/src/test/java/cherry/mastersmith/useradmin/web/UserAdminBusyApiIT.java
    - backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java
    - backend/build.gradle.kts
    - frontend/src/features/useradmin/EditProfileDialog.tsx
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/RadioGroup/RadioGroup.tsx
    - compose.yaml
    - docker/perf/compose.yaml
    - Dockerfile
    - .github/dependabot.yml
    - perf/k6/scenarios.js
    - .env.example
    - README.md
    - gradle/libs.versions.toml
  components:
    - user
    - audit
    - auth
    - useradmin
    - common-error
    - common-persistence
    - config
    - frontend-feature-useradmin
    - make-you-chic-ui
    - container-runtime
    - build-and-verify
    - perf-and-monitoring
    - backend-test-support
shallow:
  paths:
    - backend/src/main/java/cherry/mastersmith/access/
    - backend/src/main/java/cherry/mastersmith/appearance/
    - backend/src/main/java/cherry/mastersmith/common/health/
    - backend/src/main/java/cherry/mastersmith/common/i18n/
    - backend/src/main/java/cherry/mastersmith/common/observability/
    - backend/src/main/java/cherry/mastersmith/common/paging/
    - backend/src/main/java/cherry/mastersmith/common/security/
    - backend/src/main/java/cherry/mastersmith/common/web/
    - backend/src/main/java/cherry/mastersmith/config/
    - backend/src/main/java/cherry/mastersmith/dsl/
    - backend/src/main/java/cherry/mastersmith/dslmanage/
    - backend/src/main/java/cherry/mastersmith/invitation/
    - backend/src/main/java/cherry/mastersmith/mail/
    - backend/src/main/java/cherry/mastersmith/targetdb/
    - backend/src/main/java/cherry/mastersmith/auth/domain/
    - backend/src/main/java/cherry/mastersmith/auth/repository/
    - backend/src/main/resources/db/migration/
    - backend/src/test/java/cherry/mastersmith/
    - frontend/src/
    - frontend/e2e/
    - frontend/package.json
    - perf/README.md
    - perf/ui/
    - docker/
    - .github/workflows/ci.yml
    - vendor/java-mustache-processor/
    - vendor/make-you-chic-ui/
```
