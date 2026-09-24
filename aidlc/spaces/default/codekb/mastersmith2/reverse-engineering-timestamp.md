# Reverse Engineering の実施記録（mastersmith2）

| 項目 | 値 |
|---|---|
| 実施日 | 2026-09-25 |
| Intent | `260924-followup-fixes`（scope bugfix、前の Intent の後に回した小さな修正7件） |
| 対象 | mastersmith2（プロジェクトルート） |
| ブランチ・コミット | `develop`・`7370b77d1282fd750d4e63c4b45b82de0b63bceb`（`.git/HEAD` と `.git/refs/heads/develop` を読んで確認） |
| スキャン時点のソースの識別 | `git:9338137acd915a957043cc0a05122600d834fa71`（スナップショットの source） |
| サブモジュール `vendor/make-you-chic-ui` の checkout | `5258c8bb987b0fa6ffd0ad7c4eadc7d4006da52d`（`.git/modules/vendor/make-you-chic-ui/HEAD`。親のリポジトリの gitlink とは未照合） |
| 走査の広さ・深さ | 全体の読み直し（Full rescan、スナップショットの範囲は `./`）・Minimal |
| 手順 | 開発担当のコードスキャン → アーキテクトによる統合（この9つの成果物） |

## 確かめ方

- ファイルの読み取り（Read・grep・ls）だけで確かめた。git・Gradle・npm・Docker のコマンドは実行していない。テストの件数はファイルを数えた値である。
- `.env` と鍵ファイルは開いていない。`reference/`・`aidlc/`・`.claude/` と生成物（`frontend/coverage/`・`build/` など）は対象の外。
- アーキテクトは、7件の該当行（`LoginService`・`LoginAttemptStateRepository`・`ObservabilityConfig`・`compose.yaml`・`perf/k6/scenarios.js`・`V3__u2_authentication.sql`・`logback-spring.xml`・`application.yaml`・`AuditEventListener`・`InitialAdminInitializer`）と、パッケージ間の `import` を読み直して確かめた。図のために `DslLifecycle`・`DslAdminController`・`DslHeavyOperationGate` の一部も読んだが、開発担当の区分では流し読みの範囲のため、下の `analyzed` には足していない（範囲は開発担当の Scan Coverage どおり）。

## 前の記録との関係

前のコード知識ベースは Intent `260923-dsl-schema-loader` のときのもの（partial、17 部品。今は STALE）で、その後に作られた `targetdb`・`dsl`・`dslmanage`・`frontend-feature-dsl` を含まない。依頼者が全体の読み直しを選んだため、9つの成果物をすべて置き換えた。下の Scope of Analysis は今回のスキャンだけから作り、前の記録の範囲は足し合わせていない。深さが Minimal で、前回深く読んだ部品（`common-*`・`access`・`frontend-app-core` など）の多くを今回は流し読みにしたため、前回と比べると範囲は狭くなる（比べると部品が「失われた」と表示される見込み）。

## 範囲の要約

- 深く読んだ: ビルドの設定と lockfile の主な行、設定（`application.yaml`・`logback-spring.xml`）、7件に関わるバックエンドのファイル（一部の範囲だけのものを含む）、コンテナ・負荷の試験・OTLP の受け手の設定、CI、画面のビルドの設定と `DslConfirmDialog.tsx`。
- 流し読み: バックエンドの残りのすべて、スキーマ変更の残り、テストの大半、画面の残り、`vendor/make-you-chic-ui`、`docker/` の残り、`perf/` の残り、README、リポジトリの付属の設定。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` である。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: 260924-followup-fixes
fingerprint: a8098f96bd896014e1934bb252a960c8d76b1ac6
analyzed:
  paths:
    - settings.gradle.kts
    - build.gradle.kts
    - backend/build.gradle.kts
    - gradle/libs.versions.toml
    - gradle/wrapper/gradle-wrapper.properties
    - backend/gradle.lockfile
    - backend/src/main/resources/application.yaml
    - backend/src/main/resources/logback-spring.xml
    - backend/src/main/resources/db/migration/V3__u2_authentication.sql
    - backend/src/main/java/cherry/mastersmith/config/ObservabilityConfig.java
    - backend/src/main/java/cherry/mastersmith/auth/service/LoginService.java
    - backend/src/main/java/cherry/mastersmith/auth/service/LoginAttemptStateInitializer.java
    - backend/src/main/java/cherry/mastersmith/auth/repository/LoginAttemptStateRepository.java
    - backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/service/DslOperationMetrics.java
    - backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java
    - backend/src/test/java/cherry/mastersmith/common/observability/ExternalExportIT.java
    - compose.yaml
    - Dockerfile
    - .env.example
    - .dockerignore
    - .gitmodules
    - .github/workflows/ci.yml
    - docker/perf/compose.yaml
    - docker/otel-collector/config.yaml
    - perf/k6/scenarios.js
    - frontend/package.json
    - frontend/vite.config.ts
    - frontend/vitest.config.ts
    - frontend/src/features/dsl/DslConfirmDialog.tsx
  components:
    - config
    - auth
    - audit
    - container-runtime
    - perf-and-monitoring
    - build-and-verify
    - frontend-feature-dsl
shallow:
  paths:
    - backend/src/main/java/cherry/mastersmith/
    - backend/src/main/resources/db/migration/
    - backend/src/main/resources/dsl/
    - backend/src/test/
    - backend/config/spotbugs-exclude.xml
    - frontend/src/
    - frontend/e2e/
    - frontend/scripts/
    - frontend/.oxlintrc.json
    - frontend/eslint.config.js
    - frontend/.prettierrc.json
    - frontend/.stylelintrc.json
    - vendor/make-you-chic-ui/
    - docker/monitoring/
    - docker/targetdb/
    - docker/check-container-limits.sh
    - perf/
    - README.md
    - .github/dependabot.yml
    - .pre-commit-config.yaml
    - .gitleaks.toml
    - .gitignore
    - .editorconfig
    - .gitattributes
    - config/
```
