## Developer Code Scan Results

対象: mastersmith2（プロジェクトルート、単一リポジトリ）。Intent `260928-quality-followup`（scope bugfix）。
記録するコミット: `develop` の HEAD `e68f54d4d6b0633c638668bec2622500021ea9bf`（`git rev-parse HEAD` で確かめた。`origin/develop` も同じ値）。
走査: 依頼者の選択は Full rescan、深さは Minimal。全体は `git ls-files` の一覧で見渡し、今回の Intent の5件（コントラスト・CI の2つの時間切れ・p95 のバケット・Dependabot・記録の誤り）に関わる範囲だけを深く読んだ。
確かめ方: ファイルの読み取りと、読み取りの git（`git rev-parse`・`git ls-files`・`git submodule status`・`git log`・`git diff`・`git branch -r`、サブモジュールの中の `git log`・`git show`）だけ。Gradle・npm・Docker・`gh` は実行していない。`.env` と鍵ファイルは開いていない。GitHub 上の状態（プルリクエストが開いているか、CI の実行）は問い合わせていない。
件数は、すべてファイルを数えた・`grep` で数えた値であり、テストを実行した値ではない。

### Scan Coverage
- **Analyzed deeply**:
  - `vendor/make-you-chic-ui`（サブモジュールの固定先と、`735ef04..310e1ec` の2コミットの差分: `packages/make-you-chic-ui/src/theme/semantic.css`・`src/components/Avatar/Avatar.css`・`src/components/FormField/FormField.css`・`src/components/Alert/Alert.css`・`src/components/Badge/Badge.css`・`src/components/Button/Button.css` の変更行・`docs/integration-guide.md` の追記。差分として読んだもので、部品のファイル全体は読んでいない）
  - `frontend/package.json`
  - `frontend/vitest.config.ts`
  - `frontend/vitest.setup.ts`
  - `frontend/e2e/support/axe.ts`
  - `frontend/e2e/support/displayCombos.ts`
  - `frontend/src/features/invitation/InvitationAdminPage.test.tsx`（テストの一覧と、時間切れになったテスト 749〜824 行）
  - `frontend/src/features/preferences/PreferencesForm.css`
  - `frontend/src/app/pages/Page.css`
  - `frontend/src/features/dsl/DslSubmitForm.css`
  - `backend/src/test/java/cherry/mastersmith/config/H2CompactionByPoolSuspensionIT.java`
  - `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`（イメージの digest の定数）
  - `backend/build.gradle.kts`（テストの JVM の設定・`integrationTest`・カバレッジの検証と `packagesJudgedByTotal`）
  - `build.gradle.kts`（サブモジュールの準備と変更なしの確かめ・OSV-Scanner の対象・`verify` の段の組み立て）
  - `backend/src/main/resources/application.yaml`（`spring.datasource`・`management` の節）
  - `backend/src/main/java/cherry/mastersmith/mail/service/SmtpMailSender.java`
  - `backend/src/main/java/cherry/mastersmith/dslmanage/service/DslOperationMetrics.java`
  - `backend/src/main/java/cherry/mastersmith/invitation/service/RegistrationService.java`（`complete`）
  - `docker/monitoring/provisioning/alerting/mastersmith.yaml`
  - `docker/monitoring/dashboards/mastersmith-overview.json`（パネルの題と、95 パーセンタイルの式）
  - `docker/otel-collector/config.yaml`
  - `.github/dependabot.yml`
  - `.github/workflows/ci.yml`
  - `gradle/libs.versions.toml`
  - `config/npm-build-tools.txt`
  - `Dockerfile`（`FROM` と `ENTRYPOINT`）
  - `README.md`（「手元の監視（Grafana）」・「API のアクセス制御（U3）」の終わり・「監査ログ（U4）」の招待と登録の行・「招待と登録の完了（U3）」・「既知の制約（ブランドカラーのコントラスト）」とその後のアバターと誤りの文字の節、ほかは見出しと `grep` の当たりだけ）
- **Skimmed only**:
  - `backend/gradle.lockfile`・`frontend/package-lock.json`（主な部品の版を `grep` で抜いただけ）
  - `compose.yaml`（イメージの行だけ）
  - `backend/src/main/java/cherry/mastersmith/`（パッケージとファイルの一覧。`invitation/`・`mail/`・`appearance/`・`user/` は一覧まで。`audit/service/AuditEventListener.java` と `audit/domain/AuditEventType.java` は `REGISTRATION_FAILED` の有無の `grep` だけ）
  - `backend/src/main/java/cherry/mastersmith/auth/service/`（`@Scheduled` の `grep` だけ）
  - `backend/src/test/java/cherry/mastersmith/`（ファイルの数と注釈の数）
  - `frontend/src/`（ファイルの一覧と `grep`。features の `invitation/`・`registration/`・`preferences/`・`app/display-settings/` は一覧だけ）
  - `frontend/e2e/`（ファイルの一覧、050 と 080 は `grep` だけ）
  - `perf/`・`docker/targetdb/`・`docker/jmx/`・`docker/hikari-pool.sh`・`.pre-commit-config.yaml`・`backend/config/spotbugs-exclude.xml`・`.gitleaks.toml`
  - `vendor/java-mustache-processor`（固定先の確認だけ）
  - Dependabot の作業ブランチ（`origin/dependabot/*` の 15 本。各ブランチの最後のコミットの差分の先頭行だけ）
  - 範囲の外として参照だけ: `aidlc/` の前の Intent の記録（`260925-user-management` の `test-results.md` 6節・`phase-check-construction.md` 4節・`drift-report.md` 2節・`feedback-loop.md`）。CI の失敗の中身と記録の誤りの所在を知るためにだけ読み、コードの走査の範囲には数えない。前回のコード知識ベース（`aidlc/spaces/default/codekb/mastersmith2/`）は `reverse-engineering-timestamp.md` と `component-inventory.md` の見出しだけ読んだ。

### Packages Found
（ファイルの数は `git ls-files` で数えた。バックエンドの本体 421・テスト 317、画面の本体 128・テスト 91、E2E 9）
- `backend`（`cherry.mastersmith`）— Spring Boot 4.1.1 のアプリ（実行可能 WAR）— Java 25 — 認証・認可・監査・利用者・DSL の管理・対象DB・招待とメール・見た目の設定
  - 前回のコード知識ベース（記録のコミット `c438dc0`、Intent `260925-user-management` の開始時）の後に増えたパッケージ（`c438dc0..HEAD` は 49 コミット）: `invitation`（`domain`・`repository`・`service`・`web`。招待・登録の完了・定期の削除 `InvitationCleanupJob`）、`mail`（`config`・`domain`・`service`・`template`・`transport`。SMTP の送信と Mustache のテンプレート）、`appearance`（`config`・`service`・`web`。`GET /api/appearance`）、`user.web`（`MeController`。プリファレンスとパスワードの変更）。スキーマの変更は V7（利用者のプリファレンス・監査の対象の列）と V8（招待の表）が増えた。
- `frontend`（`mastersmith-frontend`）— React 19 + TypeScript の SPA — TypeScript — 画面。前回の後に `features/invitation`・`features/registration`・`features/preferences`・`app/display-settings`・`app/login-handoff`・`shared/validation`・`shared/format` が増えた。
- `vendor/make-you-chic-ui` — Git サブモジュール（npm の `file:` の依存）— TypeScript — デザインシステム。固定先 `735ef04ce6eb618cb875f5c4b31c1645a1f84c28`（`git submodule status`）。
- `vendor/java-mustache-processor` — Git サブモジュール（Gradle の composite build）— Java — Mustache のエンジン。固定先 `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`（`0.1.0`）。今回の Intent では触れない見込み。
- `docker/`・`perf/`・`config/` — 実行環境・手元の監視・負荷の試験・検査の設定 — YAML・JSON・シェル・JS。

### Build System
- **Type**: Gradle（Kotlin DSL）＋ npm（`frontend/`・`vendor/make-you-chic-ui`）。入口は `./gradlew verify` の1つ。
- **Config Files**: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`settings-gradle.lockfile`・`gradle/wrapper/gradle-wrapper.properties`（9.7.1）・`frontend/package.json`・`frontend/package-lock.json`・`frontend/vite.config.ts`・`frontend/vitest.config.ts`・`frontend/playwright.config.ts`・`config/npm-build-tools.txt`・`config/license-header.txt`。
- **Build Dependencies**:
  - `verify` → `verifyPrepare`（`checkToolchain`・`vendorInstall`（`npm ci`）・`vendorBuild`・`vendorUnchanged`・`mustacheVendorUnchanged`・`frontendInstall`）→ `verifyFormat` → `verifyLint` → `verifyLicense` → `verifyBuild` → `verifyUnitTest`（`:backend:test`・Vitest）→ `verifyIntegrationTest`（`:backend:integrationTest`）→ `verifyCoverage`（JaCoCo・`@vitest/coverage-v8`）→ `verifySecurity`（`spotbugsGate`・`osvScan`・`gitleaksScan`）→ `verifyArtifact`（`bootWar`・`verifyDslSchemaInWar`・`frontendBundleSize`）。段は `mustRunAfter` で順に並ぶ（`build.gradle.kts` 340〜416 行）。
  - `frontend` → `make-you-chic-ui`（`file:../vendor/make-you-chic-ui/packages/make-you-chic-ui`。`vendorBuild` が作る `dist` を読む）。
  - `backend` → `java-mustache-processor`（composite build、`cherry-mustache-core` 0.1.0）。
  - `vendorUnchanged`・`mustacheVendorUnchanged` は、サブモジュールの中の `git status --porcelain` が空であることを確かめる（固定先の変更そのものは止めない）。
  - OSV-Scanner の対象の lockfile は `backend/gradle.lockfile`・`frontend/package-lock.json`・`vendor/make-you-chic-ui/package-lock.json` の3つ（`build.gradle.kts` 228〜229 行）。

### APIs Discovered
- REST（Spring MVC）— `backend/src/main/java/cherry/mastersmith/*/web/` — 今回の Intent に関わるのは次の6つ（警報の式と監査の記録の誤りの対象）:
  - `POST /api/auth/login`・`POST /api/auth/session/refresh`・`GET /api/admin/check`（p95 の警報 `ms-login-p95`・`ms-refresh-p95`・`ms-check-p95` の対象）
  - `POST /api/admin/invitations`・`POST /api/admin/invitations/{invitationId}/resend`（送信の失敗でも 201・200 で `sendResult: FAILED` を返す。README「招待と登録の完了（U3）」）
  - `POST /api/registration/complete`（誤ったトークンでも監査に `REGISTRATION_FAILED` が残る。下の Handoff を参照）
- 指標（Micrometer → OTLP）— `application.yaml` の `management.otlp.metrics.export`（`step: 60s`、`${mastersmith.observability.export.enabled}` で有効化、既定は無効）— 今回の対象の指標は `http.server.requests`（Spring MVC の観測）と `mastersmith.mail.send`（`SmtpMailSender` の Observation）。
- ほかの API（DSL の管理・プリファレンス・見た目の設定ほか）は数えていない（Minimal のため）。

### Frameworks & Libraries
（`backend/gradle.lockfile`・`gradle/libs.versions.toml`・`frontend/package-lock.json` から `grep` で抜いた値）
- Spring Boot — 4.1.1 — アプリの土台
- Micrometer（core・observation・registry-otlp）— 1.17.1 — 指標と観測
- OpenTelemetry API — 1.62.0 ／ opentelemetry-logback-appender — 2.28.1-alpha（project.md の Tech Stack で固定）— 外部エクスポート
- HikariCP — 7.0.2 — 接続プール（一時停止と破棄を MBean で使う）
- H2 — 2.4.240 — 内部DB（`-Dh2.compactThreads=1` を Dockerfile とテストの JVM でそろえている）
- JUnit Jupiter — 6.0.3 ／ Awaitility — 4.3.0 ／ Testcontainers — 2.0.5 ／ ArchUnit — 1.5.0 ／ jqwik — 1.10.1 — バックエンドのテスト
- SnakeYAML — 2.6 ／ networknt json-schema-validator — 3.0.6 — DSL の読み込み
- React — 19.3.0 ／ react-router — ^8.3.0 ／ i18next — ^26.4.2 — 画面
- TypeScript — 6.0.3 ／ Vite — 8.3.0 ／ Vitest — 4.1.11 ／ jsdom — 30.1.1 ／ @testing-library/user-event — 14.6.7 ／ axe-core — 4.13.0 ／ @playwright/test — 1.63.0 — 画面のビルドとテスト
- 実行環境のイメージ: `eclipse-temurin:25.0.4_7-jre-noble`（Dockerfile）、`grafana/otel-lgtm:0.33.1`、`otel/opentelemetry-collector:0.161.0`、`axllent/mailpit:v1.31.2`、見本の対象DB `mysql:8.4.11`・`mariadb:11.8.9`・`postgres:18.6`（いずれも digest 付き、compose.yaml）
- CI の道具: Gitleaks 8.30.1・OSV-Scanner 2.6.0（版と SHA-256 を `ci.yml` で固定）、JDK 25（Temurin）、Node.js 24

### Test Coverage
- **Test Directories**: `backend/src/test/java/`（`*Test` 151 ファイル、`*IT` 108 ファイル、補助を含め 317 ファイル。`@Test`・`@Property`・`@ParameterizedTest`・`@Example` の注釈は `grep` で 1,332 個）、`frontend/src/**/*.test.ts(x)`（91 ファイル、`it(`・`test(` の宣言は `grep` で 702 個）、`frontend/e2e/*.e2e.ts`（9 ファイル、`test(` の宣言 32 個）。前の Intent の記録では、CI の実行で結合テスト 562 件・画面のテスト 732 件だった（実行の値。ここでは確かめていない）。
- **Test Frameworks**: JUnit Jupiter・AssertJ・Awaitility・jqwik・ArchUnit・Testcontainers・Spring Boot Test（バックエンド）、Vitest・Testing Library・user-event・vitest-axe・fast-check（画面）、Playwright と Node の側で読む axe-core（E2E、`verify` と CI の外）。
- **Coverage Config**: present。JaCoCo は全体の合計とパッケージごとに行 80%・分岐 70%（`backend/build.gradle.kts` 229〜262 行）。全体の合計で判定する既存のパッケージ `packagesJudgedByTotal` は今 **12 個**（`access.domain`・`access.service`・`audit.repository`・`auth.domain`・`auth.repository`・`common.error.domain`・`common.error.service`・`common.error.web`・`common.health`・`common.i18n.domain`・`common.observability`・`common.web`）。team.md の Testing Posture の文は「22 パッケージ（`user.*` を含む）」のままだが、コードの注記どおり user-management の B2・U8 で 10 個が外れている。画面は `vitest.config.ts` の `thresholds`（行 80・分岐 70）。
- テストの JVM: `maxHeapSize = "1g"`・`-Dh2.compactThreads=1`・`-Djava.net.preferIPv4Stack=true`・`user.timezone=Asia/Tokyo`（`backend/build.gradle.kts` 116〜143 行）。Vitest は `testTimeout` を設定しておらず、既定の 5 秒で動く（`vitest.config.ts`・`vitest.setup.ts`、`frontend/src` に個別の上限の指定も無い）。

### Code Quality Indicators
- **Linting**: Spotless＋palantir-java-format（Java・Gradle の Kotlin DSL・メールのテンプレートのライセンスヘッダー）、Prettier・oxlint・ESLint・Stylelint（`frontend/`）、SpotBugs＋FindSecBugs（`backend/config/spotbugs-exclude.xml`）、Gitleaks（`.gitleaks.toml`、pre-commit と CI）、OSV-Scanner（`build.gradle.kts` の `osvScan`、画面の開発時の依存は `config/npm-build-tools.txt` の道具だけ止める）。
- **CI/CD**: `.github/workflows/ci.yml`（`develop` へのプッシュ・`v*` のタグ・手動。`./gradlew verify` を1回、`timeout-minutes: 60`、サブモジュールは `submodules: true` で固定先を取得、WAR を成果物として保存、タグでリリースに添付）。`.github/dependabot.yml`（gradle は `exclude-paths: vendor/**`、npm は `/frontend` だけ、github-actions・docker・docker-compose）。
- **Documentation**: `README.md`（約 950 行、運用・監視・監査・既知の制約まで含む）、`perf/README.md`、`frontend/src/features/README.md`、make-you-chic-ui の `docs/integration-guide.md`。コードのコメントは日本語で、決定の出どころ（Intent・単位・決定の番号）を丁寧に書いている。運用の手順書（runbook）・警報の説明（`alarms.md`）・ログの問い合わせ（`log-queries.md`）は **リポジトリの中（`aidlc/` の外）には無く**、`aidlc/spaces/default/intents/*/operation/` の記録の中にだけある。

### Technical Debt Signals
- **E2E の既知の違反の一覧（コントラスト）**: `frontend/e2e/support/axe.ts` の `KNOWN_VIOLATIONS`（green・orange の primary の Button、050）・`INVITATION_KNOWN_VIOLATIONS`（060）・`REGISTRATION_KNOWN_VIOLATIONS`（070）・`PREFERENCES_KNOWN_VIOLATIONS`・`AVATAR_KNOWN_COMBOS`・`withFormFieldErrorKnownViolation`（080）。どれも「当たるはずの違反が当たらなければ一覧と一致せず失敗する」作り（axe.ts 159〜288 行のコメント）。README 112・121・129・137・864・879・893・912 行と「既知の制約（ブランドカラーのコントラスト）」の節（927〜948 行）が同じ内容を持つ。
- **アプリ自身の CSS のコントラスト（make-you-chic-ui の直しの外）**: `frontend/src/features/preferences/PreferencesForm.css` 47 行の `.preferences-choice-error` は `color: var(--color-danger)`（README 948 行も「同じ色」と書く）。`frontend/src/features/dsl/DslSubmitForm.css` 27 行の `.dsl-link` と `frontend/src/app/pages/Page.css` 33 行の `.page-link` は `color: var(--color-primary)`（文字の色に塗りの色を使う）。`.page-link` は `frontend/src` のどの `.tsx` にも使われていない（`grep`）。
- **p95 のバケットが無い**: `http.server.requests`・`mastersmith.mail.send` にヒストグラムの設定が無い（`application.yaml` に `management.metrics.distribution` が無く、`backend/src/main/java` に `MeterFilter`・`MeterRegistryCustomizer` も無い）。DSL の指標だけはコードで `publishPercentileHistogram()` を付けている。
- **テストの中の時間の上限**: `H2CompactionByPoolSuspensionIT` の `ZERO_CONNECTIONS_WAIT`（10 秒）・`SHRINK_WAIT`（30 秒）は、運用の道具の既定に合わせた固定値（コメント 107 行）。`InvitationAdminPage.test.tsx` の1件は、1つのテストで招待 3 回（36 文字のメールアドレスを `user.type`）・送り直し 2 回・取り消し 2 回とページ送りを順に行う。
- **Dependabot の作業ブランチが溜まっている**: `origin/dependabot/*` が 15 本（下の Handoff）。前の Intent の記録では開いたプルリクエストが 11 件。
- **team.md の記述と今のビルドの差**: `packagesJudgedByTotal` の数（上の Test Coverage）。
- **Dockerfile の JDK の版と Dependabot**: `eclipse-temurin:25.0.4_7-jre-noble` に対し、Temurin 26 への更新のブランチがある（`gradle/libs.versions.toml` の `java = "25"`、CI も JDK 25）。

## Handoff Summary
- **Intent-relevant finding**:
  1. **コントラスト（make-you-chic-ui の固定先の更新）** — 確かめた事実:
     - 今の固定先は `735ef04`。依頼の `7865c28`（2026-09-28 21:59、「テキストコントラスト不足3件(+関連2件)をWCAG AA対応」）と `310e1ec`（22:06、「Badge: variant-successの白文字コントラスト不足を修正」）は、サブモジュールの手元の履歴にあり、`735ef04 → 7865c28 → 310e1ec` と一直線に続く（`310e1ec` は `origin/main` に含まれる）。更新の差は9ファイル・+199／−7 行で、`package.json`・`package-lock.json` は変わらない（`git diff --stat 735ef04 310e1ec`）。
     - 直しは文字用のトークンを足して部品の CSS を切り替える形: `--color-primary-text`（blue・purple は白、green・orange は `--gray-900`）、`--color-primary-subtle-text`（light `--brand-700`・dark `--brand-400`、Avatar の頭文字）、`--color-danger-subtle-text`（Alert の danger）、`--color-danger-text`（light は `--color-danger`、dark は `--red-400`。FormField の誤りの文字と必須の印）、`--color-success-text`（`--gray-900`、Badge の success）。塗りの色（`--color-primary` など）そのものは変えていない。green・orange の primary の Button の文字は白から濃い色に変わる（見た目の変化）。回帰テスト `src/theme/contrast.test.ts` が足された（このリポジトリの CI の対象外）。
     - 更新すると、E2E の既知の違反の一覧は「当たらなくなった」ことで失敗する作りのため、`frontend/e2e/support/axe.ts` の一覧・関数と README の該当行（上の Technical Debt Signals）を合わせて直す必要がある。E2E は `verify` と CI の外なので、CI だけでは気づけない。
     - `frontend/package-lock.json` の make-you-chic-ui は `link: true`（`resolved: ../vendor/...`）で、版の文字列は `0.0.0` のまま。lockfile の書き換えは要らない見込み（実行はしていない）。
     - make-you-chic-ui の直しは、アプリ自身の CSS（`.preferences-choice-error` の `--color-danger`、`.dsl-link` の `--color-primary`）には及ばない。
  2. **CI の2つの時間切れ** — 確かめた事実:
     - `H2CompactionByPoolSuspensionIT` の失敗の箇所は、前の Intent の記録（`test-results.md` 6節）では 196 行の `await().atMost(ZERO_CONNECTIONS_WAIT).until(() -> pool.getTotalConnections() == 0)`。`ZERO_CONNECTIONS_WAIT` は 10 秒で、ほかの2つのテストでも同じ定数を使う（235・251・264 行）。このテストは `SpringApplicationBuilder` でアプリ全体を起動しており、`@EnableScheduling` の2つの定期の処理（`RefreshTokenCleanupJob`・`InvitationCleanupJob`、cron で動く）も同じ文脈に入る。
     - `InvitationAdminPage.test.tsx` の「keeps addresses and names out of storage, the URL and the console in every flow」（749 行）は、`it` に上限の指定が無く Vitest の既定の 5 秒で動く。記録では、1回目の CI で 4,583 ms で通り、2回目で 5 秒を超えた。
     - 前の Intent の記録では、どちらも原因は確かめておらず、手元の `verify` では通っていた。
  3. **p95 のバケット** — 確かめた事実: 警報 `ms-login-p95`・`ms-refresh-p95`・`ms-check-p95`（`docker/monitoring/provisioning/alerting/mastersmith.yaml` 153〜248 行）とダッシュボードの3つのパネル（`mastersmith-overview.json` 280・330・380 行）は `http_server_requests_milliseconds_bucket` に `histogram_quantile(0.95, …)` を当てる。3つとも `noDataState: OK`、しきい値は `gt 1000`。`application.yaml` にはヒストグラムの設定が無い。招待と登録・メールの送信の 95 パーセンタイルのパネル（1055・1222 行）は、代わりにトレースから作る `traces_spanmetrics_latency_bucket` を使っている（README 673 行が既知の欠けとして書いている）。`mastersmith.mail.send` は `SmtpMailSender` の `Observation`（タグは `mail.template`・`mail.language`・`mail.outcome`・`mail.failure.kind`）。DSL の指標 `mastersmith.dsl.operation` は `publishPercentileHistogram()` を付けており、ダッシュボードは `mastersmith_dsl_operation_milliseconds_bucket` を問い合わせる。
  4. **Dependabot** — 確かめた事実（`git branch -r` の手元の追跡の参照。最後の取得は `origin/develop` と同じ時点で、GitHub 上でプルリクエストが開いているかは確かめていない）: `origin/dependabot/*` の 15 本。
     - gradle: `networknt-json-schema-validator` 3.0.6→3.0.7、`archunit` 1.5.0→1.5.1、`snakeyaml` 2.6→2.7、`opentelemetry-logback-appender` 2.28.1-alpha→2.31.1-alpha（lockfile の8行も変わる）、Gradle wrapper 9.7.1→9.8.0（`gradlew`・`gradlew.bat` も変わる）
     - npm（`frontend`）: `prettier` 3.9.8→3.9.9、`vite` 8.3.0→8.3.1、`typescript` ^6.0.3→^7.0.2、`vitest` ^4.1.11→^5.0.2、`@vitest/coverage-v8` ^4.1.11→^5.0.2
     - docker: `eclipse-temurin` 25.0.4_7→26.0.2_10（Dockerfile）
     - docker-compose: `grafana/otel-lgtm` 0.33.1→0.34.0、`mysql` 8.4.11→26.7.0、`mariadb` 11.8.9→13.0.2、`postgres` 18.6 の digest の更新
     - 一部のブランチは古い `develop`（`0d72ab8`・`fd44e79`・`37a3a4f`）から作られている。
  5. **記録の誤り（alarms.md・log-queries.md・runbooks.md の RB-17）** — 確かめた事実:
     - これらの文書はリポジトリの中（`aidlc/` の外）には無く、`aidlc/spaces/default/intents/260925-user-management/operation/`（と前の Intent）の記録にだけある。
     - 登録の完了で形の誤ったトークンを送ると、`InvitationToken.parse` が空を返し `Outcome.Refused(null, INVITATION_NOT_FOUND)` になり、`RegistrationFailedEvent` を出す（`RegistrationService.java` 152〜159 行）。`AuditEventListener`・`AuditEventFactory` が `RegistrationFailedEvent` を扱い、`AuditEventType` に `REGISTRATION_FAILED` がある。つまり「形の誤ったトークンの拒否は監査に残らない」は登録の完了については誤りで、監査に残る。README 726 行と 716 行（「誤ったトークンで完了を呼ぶたびに監査に `REGISTRATION_FAILED` が1行増えます」）はコードと合っている。
     - 送信の失敗は、招待が 201・送り直しが 200（`sendResult: FAILED`）で、ログは WARN と INFO（README 837 行）。5xx の割合（`ms-5xx-ratio`）と ERROR のログ（`ms-error-logs`）の警報の式には当たらない。README は「警報に当たらない」ことまでは書いていない。
- **Risks / follow-up**:
  - **仮説（未検証）: H2 のテストの時間切れの原因**。(a) 一時停止の直前に動いていた接続の補充（HikariCP の補充の処理）が、破棄の後に接続を1本足し、0 本にならない競合。(b) 同じ文脈の定期の処理や起動の後の処理が、その時点で接続を借りていて返すのが遅れた。(c) CI の runner が遅く、最後の接続を閉じるときの H2 の詰め直し（`DEFRAG_ALWAYS`・`compactThreads=1`）や `CHECKPOINT SYNC` の後の書き出しが 10 秒を超えた。どれも確かめていない。直し方を決める前に、失敗したときの `getTotalConnections`・`getActiveConnections`・`getIdleConnections`・`getThreadsAwaitingConnection` の値とスレッドの状態を出す診断を足して再現を試みることを勧める。上限を延ばすだけにすると、team.md の「不安定なテストは原因を直すまで統合しない」と project.md の学び（再現できなければ不安定と確かめられていない扱い）の扱いを要件で決める必要がある。
  - **仮説（未検証）: 画面のテストの時間切れの原因**。1つのテストに操作が多く（`user.type` の1文字ごとの描画を含む）、CI の runner で 5 秒すれすれになる。直し方の候補は、上限をそのテストだけ延ばす・入力を `user.paste` 等に変える・流れを複数のテストに分ける。ほかの画面のテストにも 5 秒に近いものがあるかは、実行の時間を測らないと分からない。
  - **p95 の直し方（見立て）**: `management.metrics.distribution.percentiles-histogram.http.server.requests: true` と `…mastersmith.mail.send: true` を `application.yaml` に置くのが最小の形と見られる（Spring Boot の設定の口。名前の前方一致で当たる）。バケットの数（Micrometer の既定は 1 ms〜30 秒の範囲の約 70 個と記憶）と `uri`・`method`・`status`・`outcome`・`exception` のタグの組み合わせで、Prometheus の系列が大きく増えうる。`minimum-expected-value`・`maximum-expected-value` か `slo` で絞るかを設計で決める。OTLP の登録先でのヒストグラムの形（明示のバケットか指数か）、Prometheus での名前が `_milliseconds_bucket` のままかは、実際に起動して確かめる必要がある（project.md の Corrections「警報やダッシュボードの式は、書く前に実際に起動して…確かめる」）。コードで `MeterFilter` を足す形にするなら、置き場が `common.observability` だと `packagesJudgedByTotal` の一覧のパッケージに手を入れることになり、team.md により下限を満たして一覧から外す作業が要る（`application.yaml` だけで済めば当たらない）。回帰のテストは、`MeterRegistry` から `http.server.requests` の Timer を探して `takeSnapshot().histogramCounts()` が空でないことを確かめる形が考えられる（外部エクスポートは既定で無効なので、テストで使われる登録先を確かめる）。
  - **p95 を直した後の警報**: 3つの警報が値を出し始める。しきい値 1 秒・`for: 5m`・`noDataState: OK` は変えずに鳴るかを確かめる。招待と登録・メールの送信のパネルを `traces_spanmetrics_latency` からアプリの指標に切り替えるかは、依頼の文に無い（決める点）。
  - **Dependabot を取り込むときに既存の決まりとぶつかるもの**: `opentelemetry-logback-appender` は project.md の Tech Stack で 2.28.1-alpha に固定（Spring Boot 4.1.1 の OpenTelemetry 1.62 と食い違うため）。`networknt` 3.0.7 は project.md の学びで「アプリ全体の Jackson を Spring Boot の版から引き上げる」ため 3.0.6 を選んだ経緯がある。`mysql`・`mariadb`・`postgres` のイメージは `TargetDbImages` の digest の定数（34・40・47 行、今は compose と同じ値）と一緒に上げる決まり（`dependabot.yml` のコメント）で、`mysql` 26.7.0・`mariadb` 13.0.2 は大きな版の飛びで、対象DB の読み取り（`information_schema` など）の互換は確かめていない。`eclipse-temurin` 26 は `java = "25"`・CI の JDK 25 と版が分かれる。`typescript` 7・`vitest` 5・`@vitest/coverage-v8` 5 は大きな版の更新で、`typescript` と `vite` は `config/npm-build-tools.txt` の対象（High 以上で止める道具）。`gradle-wrapper` 9.8.0 は `gradlew`・`gradlew.bat` の中身も変わる。どれを取り込み、どれを閉じる・見送るかは要件で決める点。GitHub 上の実際の開いた件数は確かめていない（手元の参照は 15 本、記録は 11 件）。
  - **コントラストの更新で要る周辺の直し**: E2E の既知の違反の一覧を外す（または空にする）直しと、README の「既知の制約」の節の書き直し。green・orange の primary の Button の文字が濃い色に変わることの README への反映。アプリ自身の `.preferences-choice-error`（dark で `--color-danger`）が 080 の検査でどう扱われるか（今は `withFormFieldErrorKnownViolation` の対象は make-you-chic-ui の `.mycui-form-field-error-text` だけで、`.preferences-choice-error` はサーバーが返したときだけ出るため 080 で出していない可能性がある。未確認）。`.dsl-link` はブラウザの axe の検査の対象の画面（050〜080）に入っていない（`grep`）。E2E は `verify` と CI の外なので、E2E の実行（`./gradlew e2eTest`、Mailpit を含む）を Build and Test の手順に入れる必要がある。
  - **サブモジュールの更新の手順の決まり**: project.md の Mandated（承認を得た専用のコミット、更新前後のハッシュ `735ef04` → `310e1ec` の記録）と team.md（サブモジュールの更新を含む変更は fast-forward で統合してよい）。`vendorUnchanged` は固定先の変更を止めないが、サブモジュールの中の作業フォルダの変更は止める。
  - **記録の誤りの直し方**: 誤りのある `alarms.md`・`log-queries.md`・`runbooks.md` は承認済みの前の Intent の記録（`aidlc/` の中）で、project.md の決まり（確定済みの成果物は書き換えず、差を明記して README などを直す）が当たる。リポジトリの中に手順書の置き場が無いため、「手順書で正す」の置き場（README の節か、この Intent の運用の記録か、新しい文書か）を要件で決める必要がある。
  - **team.md の記述の古さ**: `packagesJudgedByTotal` を「22 パッケージ（`user.*` を含む）」と書いているが、今は 12 個。今回の変更がこの一覧のパッケージ（例: `common.observability`・`common.web`）に触れるかで、カバレッジの作業が増える。
  - 前回のコード知識ベースは `c438dc0`（user-management の開始時）のもので、その後の 49 コミットで `invitation`・`mail`・`appearance`・画面の `invitation`・`registration`・`preferences`・`display-settings` が増えた。今回これらは一覧と `grep` までしか読んでおらず、部品の説明を書くなら流し読みの扱いにすること。
