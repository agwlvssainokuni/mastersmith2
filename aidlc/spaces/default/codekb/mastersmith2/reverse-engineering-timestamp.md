# Reverse Engineering の実施記録（mastersmith2）

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-09-29 |
| Intent | `260929-log-deps-cleanup`（scope bugfix、深さ Minimal。初期管理者のログのメールアドレス、make-you-chic-ui の固定先と E2E の既知の違反、Dependabot の取り込みと ignore、ms-check-p95 の境界、team.md の直し） |
| 対象 | mastersmith2（プロジェクトルート、単一リポジトリ） |
| 記録するコミット | `c1ed553218e2090a5df004f27c79c59bb94b49ed`（`develop` の HEAD、`git rev-parse HEAD` で取得）。スキャンの前に取ったスナップショットの source の値（`git:d830846a…`）はコミットではない値で、この値とは別のもの |
| サブモジュール `vendor/make-you-chic-ui` の固定先 | `310e1ecf2fe8b4a388cb8ae098a59144dc93acc5`（更新の候補 `077f5b4` との差分も読んだ、K-12） |
| サブモジュール `vendor/java-mustache-processor` の固定先 | `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`（`0.1.0`） |
| 走査の広さ・深さ | 範囲を絞った走査（Focused scan）・Minimal。既存の知識ベースは STALE |
| 手順 | 開発担当のコードスキャン（`inception/reverse-engineering/developer-scan.md`）→ アーキテクトによる統合（この9つの成果物） |

## 確かめ方

- ファイルの読み取りと、読み取りの git（`git rev-parse`・`git log`・`git diff --stat`・`git submodule status`）だけで確かめた。Gradle・npm・Docker は実行していない。GitHub の開いたプルリクエストは、開発担当が `gh pr list`（読み取りだけ）で見た結果を使った。
- ログの値・メールアドレスなど個人に関する値は、開発担当もアーキテクトも本文に写していない。`.env`・鍵ファイル・Git 管理外の参考資料は開いていない。
- アーキテクトが自分で確かめ直したもの: 記録するコミット、サブモジュールの固定先、`backend/build.gradle.kts` の `packagesJudgedByTotal` が 12 個であること（K-10）、`application.yaml` の `management.metrics.distribution.slo` の境界（K-15 の前提。流し読みの範囲）、主な部品の版（`gradle/libs.versions.toml`・`frontend/package.json`・`frontend/package-lock.json`・Wrapper・`compose.yaml` のイメージ）、`.github/dependabot.yml` の ignore（K-14）、前回の記録のコミット `e68f54d` から HEAD までのコミットの件名と変わったファイル（前回の所見 K-1〜K-9 の扱いの根拠）。
- 事実と見立て（仮説）は各所見の本文で分けて書いた。開発担当が仮説とした点（TypeScript 7 と `@typescript-eslint/*` の peer の衝突、`077f5b4` で E2E の既知の違反が当たらなくなること、300 ms の按分の粗さ）は仮説のまま書いた。

## 前の記録との関係

前のコード知識ベースは Intent `260928-quality-followup` の開始時のもの（partial、8 部品・33 パス、記録のコミット `e68f54d`）。その後の 12 コミットで、前回の所見の大半が直された（make-you-chic-ui の固定先を `310e1ec` へ、E2E の既知の違反の一覧とアプリ独自の CSS、CI の時間切れの2件、`http.server.requests`・`mastersmith.mail.send` の境界のバケット、Dependabot の取り込みと Jackson の脆弱性、README の「警報と対応の手順」の節）。前回の所見の扱いの一覧は `business-overview.md`。

既存の知識ベースが STALE のため、今回の範囲（Focused scan）に当たる節だけを直し・足し、範囲の外の節（`auth`・`access`・`audit`・`invitation`・`mail`・`dsl` 系・画面の多くなど）は前回の文を残した。範囲の外の文は `e68f54d` の時点の記録で、今回は確かめ直していない。

## 部品 ID の扱い

- 前回の 36 個の ID をそのまま使い、足しも名前の変更もしていない。
- `analyzed.components` には、今回その部品の責務の中心を深く読んだもの（5 個）だけを入れた: `user`（初期管理者の作成）・`frontend-e2e`（100 のコントラストの検査と既知の違反の仕組み）・`make-you-chic-ui`（固定先と `077f5b4` までの差分）・`build-and-verify`（版の定義・lockfile・Dependabot・CI・OSV の判定）・`perf-and-monitoring`（警報の決まりとダッシュボード）。
- `common-observability`（`SanitizingLogRecordExporter` の1行）・`backend-test-support`（`*SecretLeakIT` の名前）・`config`（`application.yaml` の数行）は流し読みの扱い。

## 範囲の要約

- 深く読んだ: 22 のパス、5 の部品。開発担当が「ファイルの一覧と検索だけ」とした `backend/src/main/java/cherry/mastersmith/user/`・`backend/src/test/java/cherry/mastersmith/user/`・`frontend/e2e/`・`docker/monitoring/`・`vendor/make-you-chic-ui/` は、中を深く読んだファイルだけを `analyzed.paths` に入れ、ディレクトリそのものは `shallow.paths` に置いた。
- `vendor/make-you-chic-ui/` の4ファイルは `310e1ec..077f5b4` の差分として読んだ。作業ツリーは `310e1ec` のままで、fingerprint は今の作業ツリーの中身で取った。
- 開発担当が深く読んだとした `settings-gradle.lockfile` は、スナップショットの paths の外のため `analyzed.paths` に入れず `shallow.paths` に置いた。スナップショットの paths にある `frontend/playwright.config.ts`・`settings.gradle.kts` は、今回読んでいないため `shallow.paths` に置いた。
- README は今回の所見に関わる節（サブモジュール・E2E・手元の監視・警報と対応の手順・コントラストの既知の制約・初期管理者の起動の確かめ）だけを深く読んだ。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` である。前回の `analyzed.paths` は、今回深く読み直したものを除き `shallow.paths` に下げた。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: 260929-log-deps-cleanup
fingerprint: c3e533ce5b435a1245073c3c943a5a8354e7667c
analyzed:
  paths:
    - backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java
    - backend/src/test/java/cherry/mastersmith/user/service/InitialAdminInitializerTest.java
    - backend/src/test/java/cherry/mastersmith/user/service/InitialAdminIT.java
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Tabs/Tabs.css
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Button/Button.css
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/theme/semantic.css
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/theme/contrast.test.ts
    - .gitmodules
    - frontend/e2e/100-app-text-contrast.e2e.ts
    - frontend/e2e/support/axe.ts
    - frontend/package.json
    - frontend/package-lock.json
    - .github/dependabot.yml
    - .github/workflows/ci.yml
    - gradle/libs.versions.toml
    - build.gradle.kts
    - backend/build.gradle.kts
    - backend/gradle.lockfile
    - config/npm-build-tools.txt
    - docker/monitoring/provisioning/alerting/mastersmith.yaml
    - docker/monitoring/dashboards/mastersmith-overview.json
    - README.md
  components:
    - user
    - frontend-e2e
    - make-you-chic-ui
    - build-and-verify
    - perf-and-monitoring
shallow:
  paths:
    - backend/src/main/java/cherry/mastersmith/user/
    - backend/src/test/java/cherry/mastersmith/user/
    - frontend/e2e/
    - docker/monitoring/
    - vendor/make-you-chic-ui/
    - vendor/java-mustache-processor/
    - frontend/playwright.config.ts
    - settings.gradle.kts
    - settings-gradle.lockfile
    - backend/src/main/resources/application.yaml
    - backend/src/main/java/cherry/mastersmith/common/observability/SanitizingLogRecordExporter.java
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Avatar/Avatar.css
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/FormField/FormField.css
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Alert/Alert.css
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Badge/Badge.css
    - vendor/make-you-chic-ui/docs/integration-guide.md
    - frontend/vitest.config.ts
    - frontend/vitest.setup.ts
    - frontend/e2e/support/displayCombos.ts
    - frontend/src/features/invitation/InvitationAdminPage.test.tsx
    - frontend/src/features/preferences/PreferencesForm.css
    - frontend/src/app/pages/Page.css
    - frontend/src/features/dsl/DslSubmitForm.css
    - backend/src/test/java/cherry/mastersmith/config/H2CompactionByPoolSuspensionIT.java
    - backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java
    - backend/src/main/java/cherry/mastersmith/mail/service/SmtpMailSender.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/service/DslOperationMetrics.java
    - backend/src/main/java/cherry/mastersmith/invitation/service/RegistrationService.java
    - docker/otel-collector/config.yaml
    - Dockerfile
    - compose.yaml
    - backend/src/main/java/cherry/mastersmith/
    - backend/src/test/java/cherry/mastersmith/
    - frontend/src/
    - perf/
    - docker/targetdb/
    - docker/jmx/
    - docker/hikari-pool.sh
    - .pre-commit-config.yaml
    - backend/config/spotbugs-exclude.xml
    - .gitleaks.toml
```
