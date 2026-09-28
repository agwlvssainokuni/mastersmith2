# Reverse Engineering の実施記録（mastersmith2）

## 実施の情報

| 項目 | 値 |
|---|---|
| 実施日 | 2026-09-29 |
| Intent | `260928-quality-followup`（scope bugfix、深さ Minimal。前の Intent で受け入れた失敗・未達と運用の記録の誤りの直し） |
| 対象 | mastersmith2（プロジェクトルート、単一リポジトリ） |
| 記録するコミット | `e68f54d4d6b0633c638668bec2622500021ea9bf`（`develop` の HEAD、`git rev-parse HEAD` で取得。`origin/develop` も同じ値） |
| サブモジュール `vendor/make-you-chic-ui` の固定先 | `735ef04ce6eb618cb875f5c4b31c1645a1f84c28`（更新の候補 `310e1ec` との差分も読んだ、K-1） |
| サブモジュール `vendor/java-mustache-processor` の固定先 | `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`（`0.1.0`） |
| 走査の広さ・深さ | 全体の読み直し（Full rescan）・Minimal |
| 手順 | 開発担当のコードスキャン（`inception/reverse-engineering/developer-scan.md`）→ アーキテクトによる統合（この9つの成果物）。統合の担当が途中で止まり（4つを書いた時点）、別のアーキテクトの担当が引き継いで残りの5つを書き、一式の食い違いを確かめた |

## 確かめ方

- ファイルの読み取りと、読み取りの git（`git rev-parse`・`git ls-files`・`git submodule status`・`git log`・`git diff`・`git branch -r`、サブモジュールの中の `git log`・`git show`）だけで確かめた。Gradle・npm・Docker・`gh` は実行していない。GitHub 上の状態（開いたプルリクエスト・CI の実行）は問い合わせていない。
- 件数はファイルを数えた値・`grep` で数えた値で、テストを実行した値ではない。
- `.env`・鍵ファイル・Git 管理外の参考資料は開いていない。`aidlc/` の前の Intent の記録は、CI の失敗の中身と記録の誤りの所在を知るためにだけ読み、走査の範囲には数えない。
- アーキテクトが自分で確かめ直したもの: バックエンドのパッケージ間の依存（各パッケージの `import cherry.mastersmith.*` の検索、`dependencies.md`。前回の記録から `user` が `common` を使うようになった変化を見つけた）、make-you-chic-ui の `735ef04..310e1ec` の差の件数（`git diff --stat`）、サブモジュールの固定先（`git submodule status`）、主な部品の版（`backend/gradle.lockfile`・`gradle/libs.versions.toml`・Wrapper の `distributionUrl`）、`application.yaml` と `backend/src/main/java` に指標の分布の設定が無いこと（K-6）。
- 事実と見立て（仮説）は各所見の本文で分けて書いた。K-4・K-5・K-6 の原因と直し方は未検証の見立てである。

## 前の記録との関係

前のコード知識ベースは Intent `260925-user-management` の開始時のもの（partial、19 部品・89 パス、記録のコミット `c438dc0`）。その後の 49 コミットで `invitation`・`mail`・`appearance`・`user.web`、画面の `invitation`・`registration`・`preferences`・`display-settings`、サブモジュール `java-mustache-processor`、スキーマの V7・V8 が増えた。

依頼者が全体の読み直し（Full rescan）を選んだため、9つの成果物をすべて置き換え、下の Scope of Analysis は今回のスキャンだけから作った。深さ Minimal のため、深く読んだのは今回の5件（コントラスト・CI の2つの時間切れ・p95 のバケット・Dependabot・記録の誤り）に関わる 33 のファイルだけである。前回深く読んだ `user`・`auth`・`access`・`audit`・`common-*`・`config` の大半・画面の骨組みは今回流し読みにしたため、前回と比べると範囲は狭く（NARROWER）、比べると多くの部品が「失われた」と表示される見込みである。

## 部品 ID の扱い

- 前回の 27 個の ID（英数字）をそのまま使い、名前を変えていない。前回の記録と照合でき、部品の説明を追いやすくするため。
- 前回の後に増えたコードに、ID を 9 個足した: `invitation`・`mail`・`appearance`（バックエンドのパッケージ）、`frontend-app-display-settings`・`frontend-feature-invitation`・`frontend-feature-registration`・`frontend-feature-preferences`（画面）、`frontend-e2e`（前回は `perf-and-monitoring` などに含めず部品にしていなかった E2E。K-2 の持ち主）、`java-mustache-processor`（サブモジュール）。既存の ID に押し込むと、所見の持ち主と読みの深さが部品の単位で書けないため。ID は 36 個になった。
- `analyzed.components` には、その部品の責務の中心のファイルを深く読んだものだけを入れた（8 個）。CSS やテストのファイル1つだけを深く読んだ部品（`config`・`dslmanage`・`frontend-app-core`・`frontend-feature-dsl`・`frontend-feature-invitation`・`frontend-feature-preferences`）は、そのファイルを `analyzed.paths` に入れ、部品は流し読みの扱いにした。

## 範囲の要約

- 深く読んだ: 33 のファイル、8 の部品（`make-you-chic-ui` は差分だけ、`invitation`・`mail`・`backend-test-support`・`container-runtime`・`perf-and-monitoring` は一部のファイルだけ）。
- README は今回の所見に関わる節（手元の監視・API のアクセス制御の終わり・監査ログの招待と登録の行・招待と登録の完了・既知の制約）だけを深く読み、ほかは見出しと検索だけである。
- 深く読んだ範囲がリポジトリの全体ではないため、`kind` は `partial` である。

## Scope of Analysis

```yaml
scope_version: 1
kind: partial
intent: 260928-quality-followup
fingerprint: f655a3839d365fd377fe74d2c2565393d751997a
analyzed:
  paths:
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/theme/semantic.css
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Avatar/Avatar.css
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/FormField/FormField.css
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Alert/Alert.css
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Badge/Badge.css
    - vendor/make-you-chic-ui/packages/make-you-chic-ui/src/components/Button/Button.css
    - vendor/make-you-chic-ui/docs/integration-guide.md
    - frontend/package.json
    - frontend/vitest.config.ts
    - frontend/vitest.setup.ts
    - frontend/e2e/support/axe.ts
    - frontend/e2e/support/displayCombos.ts
    - frontend/src/features/invitation/InvitationAdminPage.test.tsx
    - frontend/src/features/preferences/PreferencesForm.css
    - frontend/src/app/pages/Page.css
    - frontend/src/features/dsl/DslSubmitForm.css
    - backend/src/test/java/cherry/mastersmith/config/H2CompactionByPoolSuspensionIT.java
    - backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java
    - backend/build.gradle.kts
    - build.gradle.kts
    - backend/src/main/resources/application.yaml
    - backend/src/main/java/cherry/mastersmith/mail/service/SmtpMailSender.java
    - backend/src/main/java/cherry/mastersmith/dslmanage/service/DslOperationMetrics.java
    - backend/src/main/java/cherry/mastersmith/invitation/service/RegistrationService.java
    - docker/monitoring/provisioning/alerting/mastersmith.yaml
    - docker/monitoring/dashboards/mastersmith-overview.json
    - docker/otel-collector/config.yaml
    - .github/dependabot.yml
    - .github/workflows/ci.yml
    - gradle/libs.versions.toml
    - config/npm-build-tools.txt
    - Dockerfile
    - README.md
  components:
    - invitation
    - mail
    - backend-test-support
    - frontend-e2e
    - make-you-chic-ui
    - build-and-verify
    - container-runtime
    - perf-and-monitoring
shallow:
  paths:
    - backend/gradle.lockfile
    - frontend/package-lock.json
    - compose.yaml
    - backend/src/main/java/cherry/mastersmith/
    - backend/src/test/java/cherry/mastersmith/
    - frontend/src/
    - frontend/e2e/
    - perf/
    - docker/targetdb/
    - docker/jmx/
    - docker/hikari-pool.sh
    - .pre-commit-config.yaml
    - backend/config/spotbugs-exclude.xml
    - .gitleaks.toml
    - vendor/make-you-chic-ui/
    - vendor/java-mustache-processor/
```
