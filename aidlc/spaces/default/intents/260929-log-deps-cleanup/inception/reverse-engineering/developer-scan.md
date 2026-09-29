# 開発担当のコードスキャン（260929-log-deps-cleanup）

- 対象のコミット: `develop` の HEAD `c1ed553`（`git rev-parse --short HEAD`）
- 幅と深さ: Focused scan（今回の Intent の範囲）、深さ Minimal
- 表記: 事実はファイルと行で示す。推測は「仮説」と明記する。ログの値・個人に関する値は表示しない（テストの見本の `example.com` のアドレスも本文には写さない）。

## Developer Code Scan Results

### Scan Coverage
- **Analyzed deeply**:
  - `backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java`
  - `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminInitializerTest.java`
  - `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminIT.java`
  - `backend/src/main/java/cherry/mastersmith/user/`（ファイルの一覧と、ログのキー `email` の検索）
  - `backend/src/test/java/cherry/mastersmith/user/`（ファイルの一覧）
  - `vendor/make-you-chic-ui/`（固定先、`310e1ec..077f5b4` の履歴と差分）
  - `.gitmodules`
  - `frontend/e2e/100-app-text-contrast.e2e.ts`
  - `frontend/e2e/support/axe.ts`
  - `frontend/e2e/`（050〜080 の既知の違反の注記の検索）
  - `frontend/package.json`
  - `frontend/package-lock.json`（typescript・@types/node・@typescript-eslint の項目）
  - `.github/dependabot.yml`
  - `.github/workflows/ci.yml`
  - `gradle/libs.versions.toml`
  - `build.gradle.kts`（spotless の適用、osvScan の判定）
  - `backend/build.gradle.kts`（Jackson の BOM、`packagesJudgedByTotal`）
  - `backend/gradle.lockfile`（Jackson の行）
  - `settings-gradle.lockfile`
  - `config/npm-build-tools.txt`
  - `docker/monitoring/`（警報の決まり、ダッシュボードの確認用 API のパネル）
  - `README.md`（サブモジュール・E2E・手元の監視・警報・コントラストの既知の制約の節）
- **Skimmed only**:
  - `backend/src/main/resources/application.yaml`（`management.metrics.distribution.slo` の行だけを grep と前後の数行で確認。スナップショットの範囲の外）
  - `backend/src/main/java/cherry/mastersmith/common/observability/SanitizingLogRecordExporter.java`（`MASKED_KEYS` の1行だけを grep で確認。範囲の外）
  - `backend/src/test/java/`（`*SecretLeakIT` のファイル名の一覧だけ。範囲の外）
  - GitHub の開いているプルリクエストの一覧（`gh pr list`、読み取りだけ）
  - `frontend/playwright.config.ts`（読んでいない。今回の7件に関わる記述は見つからなかった）

部品（`aidlc/spaces/default/codekb/mastersmith2/component-inventory.md` の ID）: 深く読んだのは `user`・`frontend-e2e`・`make-you-chic-ui`・`build-and-verify`・`perf-and-monitoring`。`common-observability`・`backend-test-support` は流し読み。

### 件ごとの確かめ

#### 1. 初期管理者の作成のログのメールアドレス（部品 `user`）

事実:
- `backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java` の INFO のログ3か所が、キー `email` にそろえたメールアドレスを載せる。
  - 88 行: 既にいるとき「初期管理者は既にいるため、作成しませんでした」
  - 96 行: 作成の結果が `CreateUserResult.EmailAlreadyUsed`（同時の起動）のとき、同じ文言
  - 99 行: 作成したとき「初期管理者を作成しました」
- 81〜84 行の WARN（設定が無い・正しくない）は `reason`・`resolution` だけで、メールアドレスの値は載せない（問題の文言は固定の文）。
- クラスの Javadoc（38〜40 行）に「ログの項目（キー `email` を含む）は前の Intent のまま（依頼者の判断で据え置き。Intent 260925-user-management の U2 のコード生成の計画 9節の決定 3）」とある。直すときはこの説明も書き換えが要る。
- `backend/src/main/java` の中でキー `email` をログに渡すのは、この3か所だけ（`addKeyValue("email"` の検索）。
- 外部エクスポートでは、`common/observability/SanitizingLogRecordExporter.java` 45 行の `MASKED_KEYS`（`email`・`enteredEmail`・`sourceIp`・`userAgent`）で値を `[REDACTED]` に置き換える。標準出力のログは元の値のまま（`README.md` 664 行）。
- これは `project.md` の Forbidden「NEVER メールアドレスをアプリのログとエラー応答に含めず…」に反する状態。前の Intent の配備の段で、起動のログの確かめの出力に値が出た記録がある（`project.md` の Corrections、260925-user-management の deployment-execution）。

周りのテスト:
- `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminInitializerTest.java`
  - `creates()`（97〜121 行）が、INFO のログの本文とキー・値に、そろえたメールアドレスが **含まれること** を確かめている（118〜120 行の `render(event)).contains(...)`）。ログからメールアドレスを外すと、この確かめは逆向き（含まれないこと）に直す必要がある。
  - `existing()`（84〜95 行）・`duplicate()`（123〜137 行）はパスワードが含まれないことだけを確かめ、メールアドレスは確かめていない。
  - ログの取り込みは `cherry.mastersmith.common.testsupport.LogEvents.capture(...)`（49 行）、描き方は `getFormattedMessage() + getKeyValuePairs()`（44〜46 行）。
- `backend/src/test/java/cherry/mastersmith/user/service/InitialAdminIT.java`
  - `OutputCaptureExtension` で標準出力を取り、`JsonLogRecords.assertContainsNoSecret(output.getAll(), password)`（85・112 行）でパスワードが出ないことを確かめる。メールアドレスが出ないことの確かめは無い。
  - 83 行で「初期管理者を作成しました」「初期管理者は既にいるため」の文言が出ることを確かめている（文言を変えるとこちらも直す）。
- 既存の `*SecretLeakIT`（名前だけ）: `AccessSecretLeakIT`・`AuditSecretLeakIT`・`AuthSecretLeakIT`・`InvitationSecretLeakIT`・`MailSecretLeakIT`・`TargetDbSecretLeakIT`・`MeSecretLeakIT`。`user` の中では `user/web/MeSecretLeakIT.java` が `JsonLogRecords` を使う。

カバレッジの決まりとの関係:
- `backend/build.gradle.kts` 221〜234 行の `packagesJudgedByTotal` は今 **12 パッケージ** で、`user.domain`・`user.repository`・`user.service` を含まない（210〜220 行の説明: Intent 260925-user-management の B2 で `user.*` を含む7つ、U8 で3つを外した。コミット `9a9a633` で `user.service` が外れた）。
- したがって `user.service` は既にパッケージごとの下限（行 80%・分岐 70%）の対象。
- `team.md` の Testing Posture は「`packagesJudgedByTotal` の一覧。`user.domain`・`user.repository`・`user.service` を含む 22 パッケージ」と書いており、今のビルドと食い違う（Intent の「team.md も修正する」に当たる候補。仮説）。

#### 2. make-you-chic-ui の固定先と E2E の既知の違反（部品 `make-you-chic-ui`・`frontend-e2e`）

事実:
- `.gitmodules`: `vendor/make-you-chic-ui`（`https://github.com/agwlvssainokuni/make-you-chic-ui`）と `vendor/java-mustache-processor`。
- 今の固定先（`git ls-tree HEAD`）: make-you-chic-ui は `310e1ecf2fe8b4a388cb8ae098a59144dc93acc5`、java-mustache-processor は `8d44c36b2bbaf36a35fe0ce397ac1c0fb7bc0ba4`（タグ 0.1.0）。サブモジュールの作業ツリーは固定先のまま、変更なし。
- `077f5b4` はサブモジュールの手元の履歴にある（fetch はしていない）。`origin/main` に含まれ、`310e1ec` の子孫（`merge-base --is-ancestor` で確認）。
- `310e1ec..077f5b4` のコミットは2つ:
  - `a34d611` Tabs: active タブの文字色コントラスト不足を修正
  - `077f5b4` Button: primary ボタンの hover/active 時の文字色コントラスト不足を修正
- 差分は4ファイル（+44・−5）。`package.json`・lockfile の変更は無い。
  - `packages/make-you-chic-ui/src/components/Tabs/Tabs.css`: `.mycui-tab.active` の文字を `--color-primary` から `--color-primary-emphasis-text` に。
  - `packages/make-you-chic-ui/src/components/Button/Button.css`: primary の `:hover`・`:active` に `color: var(--color-primary-hover-text)` を追加。
  - `packages/make-you-chic-ui/src/theme/semantic.css`: `--color-primary-emphasis-text`（light は `--brand-700`、dark は `--brand-400`）と `--color-primary-hover-text: #fff` を追加。
  - `packages/make-you-chic-ui/src/theme/contrast.test.ts`: コントラストの検査の追加。

E2E の既知の違反の扱い:
- `frontend/e2e/100-app-text-contrast.e2e.ts` 55〜83 行の `STATE_KNOWN_VIOLATIONS` に2件:
  - `dsl-schema-link` の状態の `color-contrast tab-1`（組: green・orange の light、blue・purple の dark）。Tabs の選ばれたタブ。
  - `primary-button-hover` の状態の `color-contrast preferences-save-button`（組: green・orange の light・dark）。primary の Button の hover。
  - 27〜33 行と 66〜71 行の注記が、この2件と「make-you-chic-ui が直したら外す」を説明している。
  - 145〜172 行の `checkState` は、既知の違反が一覧どおりに当たらないとき（当たらなくなったときも）失敗にする（170〜172 行）。固定先を上げて当たらなくなれば、一覧を空にしないと 100 は失敗する。
- `frontend/e2e/support/axe.ts`:
  - `KNOWN_VIOLATIONS`（111〜115 行）・`INVITATION_KNOWN_VIOLATIONS`（157〜164 行）・`REGISTRATION_KNOWN_VIOLATIONS`（172〜178 行）・`PREFERENCES_KNOWN_VIOLATIONS`（188〜196 行）は、どれも既に空（310e1ec で解消済み）。
  - 仕組み `splitKnownViolations`（122〜150 行）は「次に既知の制約を受け入れるときのために残す」と説明されている（103〜110 行）。
- 050〜080 の各ファイルの冒頭の注記（`050-display-accessibility.e2e.ts` 23〜25 行、`060-…` 29 行、`070-…` 25 行、`080-…` 26 行）は、固定先を `310e1ec` に上げて一覧を空にした経緯を書いている。
- 仮説: 077f5b4 の値（白の文字と brand-600／brand-700、brand-700／brand-400 の文字）で2件とも当たらなくなる見込みだが、実際の組ごとの当たりは E2E（100）の実測で確かめる必要がある。hover の文字が white になるため、green・orange の hover は濃い文字から白に替わる（README 976 行の表の前提が変わる）。

README の関連の記述:
- 38 行「make-you-chic-ui の固定先は `735ef04`」は、今の固定先 `310e1ec` と食い違う（前の Intent で更新漏れ。事実）。
- 158 行（100 の説明）・957 行（DSL の管理の画面のコントラスト）・990〜993 行（「残る既知の制約」の2件）が、今回外す既知の違反を説明している。
- 113・122・130・138・909・924・938・974〜988 行は `310e1ec` の解消を説明している（固定先を上げた後の書き直しの対象の候補）。

#### 3. Dependabot の設定と、対象の部品の版の置き場（部品 `build-and-verify`）

`.github/dependabot.yml` の今の ignore:
- gradle（22〜49 行、`exclude-paths: vendor/**`）:
  - `io.opentelemetry.instrumentation:opentelemetry-logback-appender-1.0` … すべての版
  - `com.networknt:json-schema-validator` … すべての版
  - `tools.jackson:jackson-bom` … `semver-major`・`semver-minor` だけ（3.1 系のパッチは知らせる。42〜44 行の説明）
- npm（50〜54 行）: ignore は **無い**。typescript・typescript-eslint・@types/node の扱いは書かれていない。
- docker（59〜70 行）: `eclipse-temurin` の `semver-major`。github-actions・docker-compose は ignore なし。
- Jackson を「すべての版で ignore」にするには、48〜49 行の `update-types` を外し、42〜44 行の説明を書き換えることになる（仮説: 対象の名前は宣言している `tools.jackson:jackson-bom` の1つで足りる。他の Jackson の部品は Spring Boot の BOM が管理し、直接は宣言していない）。

開いているプルリクエスト（`gh pr list`、2026-09-28〜29 に作成）:
- #20 `tools.jackson:jackson-bom` 3.1.6 → 3.1.7
- #19 `com.diffplug.spotless` 8.10.2 → 8.10.3
- #18 `@types/node` 26.6.2 → 26.6.3（/frontend）
- #5 `typescript` 6.0.3 → 7.0.2（/frontend）
- typescript-eslint（`@typescript-eslint/parser`）のプルリクエストは無い。

spotless（Gradle のプラグイン）:
- 版は `gradle/libs.versions.toml` 29 行 `spotless = "8.10.2"`、97 行 `[plugins] spotless = { id = "com.diffplug.spotless", version.ref = "spotless" }`。
- 適用は `build.gradle.kts` 24 行と `backend/build.gradle.kts` 29 行（`alias(libs.plugins.spotless)`）。
- プラグインは lockfile に載らない（`settings-gradle.lockfile` は `empty=incomingCatalogForLibs0` だけ、`backend/gradle.lockfile` に spotless の行は無い）。更新は toml の1行だけ。
- palantir-java-format は 30 行 `2.98.0`（別の版の固定）。

@types/node:
- `frontend/package.json` の devDependencies `"@types/node": "^26.2.0"`、lock の解決は `26.6.2`（`undici-types` 8.9.0 を連れる）。
- `frontend/package.json` の `engines.node` は `>=24 <25`、CI の `node-version: "24"`（`.github/workflows/ci.yml` 60 行）。型の大きな版（26）と実行の Node（24）がずれている（事実。影響は仮説として後の段で判断）。
- @types/node を求めるのは root と、vite の peer（`^20.19.0 || >=22.12.0`）・vitest の peer（`^22.0.0 || >=24.0.0`）。

TypeScript と typescript-eslint:
- `typescript`: `package.json` の devDependencies `^6.0.3`、lock の解決は `6.0.3`。lock の項目に `dev: true` が付かない（i18next・react-i18next の peer `^5 || ^6 || ^7` から届くため。`build.gradle.kts` 233〜242 行の判定では「実行時」の扱いになる）。
- `@typescript-eslint/parser`: `^8.67.0`、lock の解決は `8.70.1`。`parser`・`project-service`・`tsconfig-utils`・`typescript-estree` の peer は `typescript >=4.8.4 <6.1.0`（6.1 以上を受け付けない）。プロジェクトは `typescript-eslint`（まとめのパッケージ）ではなく `@typescript-eslint/parser` だけを直接持つ。
- 仮説: TypeScript 7.0.2（#5）を取り込むと、`@typescript-eslint/*` の peer の範囲を外れ、`npm ci` の peer の検査または ESLint の実行が失敗しうる。確かめは Construction で行う。
- `config/npm-build-tools.txt` に `typescript` が載っている（成果物を作る道具。devDependencies でも High 以上で統合を止める）。ほかに vite・rolldown・@rolldown/*・@oxc-project/*・lightningcss*・postcss・picomatch・tinyglobby・fdir・esbuild・@esbuild/*・rollup・@rollup/*・@vitejs/plugin-react・vite-plugin-dts・unplugin-dts。typescript-eslint と @types/node は載っていない。

Jackson の版の上書きの今の形:
- `gradle/libs.versions.toml` 28 行 `jackson = "3.1.6"`、55 行 `jackson-bom = { module = "tools.jackson:jackson-bom", version.ref = "jackson" }`。説明のコメントは toml の上部（Spring Boot 4.1.1 の管理の 3.1.5 に GHSA-q4xh-88c3-wmh7、3.1.6 で修正）。
- `backend/build.gradle.kts` 61〜67 行: `platform(libs.jackson.bom)` を `implementation`・`providedRuntime`・`testImplementation`・`testRuntimeOnly` に足す。
- `backend/gradle.lockfile` 254〜256 行: `tools.jackson.core:jackson-core`・`jackson-databind`・`tools.jackson:jackson-bom` が 3.1.6。9 行の `com.fasterxml.jackson.core:jackson-annotations` は 2.21。
- networknt は 3.0.6 に固定（toml 45 行。3.0.7 は Jackson を 3.2 系に上げるため）。

OSV-Scanner の判定（`build.gradle.kts` 226〜340 行）:
- lockfile の `dev`・`devOptional` の印で実行時か開発時かを分け（233〜242 行）、開発時は警告だけ、`config/npm-build-tools.txt` の道具と `MAL-` は失敗（280〜281・317 行）。

#### 4. ms-check-p95 の警報（部品 `perf-and-monitoring`）

事実:
- `docker/monitoring/provisioning/alerting/mastersmith.yaml` 217〜247 行 `uid: ms-check-p95`（題「確認用 API の遅れ」、`for: 5m`、`noDataState: OK`、severity 低）。
  - 式: `histogram_quantile(0.95, sum by (le) (rate(http_server_requests_milliseconds_bucket{service_name="mastersmith",uri="/api/admin/check"}[5m])))`
  - しきい値: `gt 300`（ミリ秒）。summary は「95 パーセンタイルが 300 ミリ秒を超えた」。
- 同じ形の `ms-login-p95`（157〜186 行）・`ms-refresh-p95`（187〜216 行）は `gt 1000`。
- バケットの境界: `backend/src/main/resources/application.yaml` 281〜284 行 `http.server.requests: 100ms,250ms,500ms,1000ms,2000ms,5000ms`（と `+Inf`）。コメントは「警報のしきい値 1000 ms を境界に含める」。**300 は境界に無い**（250 と 500 の間）。
- 仮説（決まっている事実ではない）: 境界の無い 300 では、`histogram_quantile` の値は 250〜500 の間の按分の見積もりになり、300 を超えたかの判定が粗い。境界を直すには、しきい値を境界に合わせる（例 250・500）か、`slo` に 300ms を足す（系列が1つ増える）かのどちらか。どちらを選ぶかは要件で決める。
- ダッシュボード `docker/monitoring/dashboards/mastersmith-overview.json`:
  - 367 行のパネル「確認用 API（/api/admin/check）」、380 行に同じ式、しきい値の段（386〜396 行付近）が赤 `300`。
  - 83 行の SLI の表に「確認用 API の 95 パーセンタイル | 300 ミリ秒以内」。
  - しきい値を変えるときは、警報・パネルの段・SLI の表の3か所をそろえる必要がある。
- README:
  - 682 行: 境界 100・250・500・1000・2000・5000 ms（「警報のしきい値 1000 ms を含む」）。
  - 683 行: 3つの p95 の警報はこのバケットで値を出し、境界の刻みより細かい値は出ない。
  - 699〜706 行（「警報と対応の手順」の表）: `ms-check-p95` は 300 ms。
- 前の Intent の記録（`project.md` Testing Posture の学び、260928-quality-followup の build-and-test）に「ms-check-p95 のしきい値が 1000 ms ではなく 300 ms（バケットの境界に無い）と分かり、要件の前提 A3 と食い違った」とある。

#### 5. CI と README の概要（部品 `build-and-verify`）

- `.github/workflows/ci.yml`: `push` の `develop` とタグ `v*`、`workflow_dispatch`。`permissions: contents: read`。Actions はコミットのハッシュで固定。
  - verify の仕事: checkout（`submodules: true`）→ setup-java（temurin 25）→ setup-node（24、`npm` のキャッシュは `frontend/package-lock.json` と `vendor/make-you-chic-ui/package-lock.json`）→ setup-gradle → Gitleaks 8.30.1・OSV-Scanner 2.6.0 を SHA-256 を確かめて入れる → `./gradlew verify` → WAR を成果物として保存。
  - release の仕事: タグのときだけ、WAR を GitHub のリリースに添付。
  - E2E（`./gradlew e2eTest`）は CI の外（team.md の決まりどおり）。
- README の今回に関わる節: サブモジュール（36〜40 行、固定先の記述が古い）、検査の段の表（52 行の 0 の段でサブモジュールの変更なしを確かめる）、E2E（82〜161 行）、手元の監視（664〜690 行付近）、警報と対応の手順（692 行〜）、コントラストの既知の制約（972〜993 行）、初期管理者の起動の確かめ（215 行「初期管理者を作成しました」の INFO を確かめる）。

### Packages Found
- `cherry.mastersmith.user`（`domain`・`repository`・`service`・`web`）— Java のパッケージ — Java 25 — 利用者のアカウント、初期管理者の作成、プリファレンス・パスワードの変更
- `frontend`（`mastersmith-frontend`）— npm のパッケージ — TypeScript・React 19 — 画面の SPA
- `frontend/e2e` — Playwright のテスト — TypeScript — WAR を起動した E2E とアクセシビリティの検査
- `vendor/make-you-chic-ui/packages/make-you-chic-ui` — npm の `file:` の依存（サブモジュール）— TypeScript・CSS — デザインシステム
- `vendor/java-mustache-processor` — Gradle の composite build（サブモジュール）— Java — メールのテンプレートの描画（今回は固定先の確認だけ）
- `docker/monitoring` — Grafana のプロビジョニング — YAML・JSON — 手元の監視の警報とダッシュボード

### Build System
- **Type**: Gradle（Kotlin DSL、version catalog）＋ npm（frontend・make-you-chic-ui）。入口は `./gradlew verify`。
- **Config Files**: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`gradle/libs.versions.toml`・`backend/gradle.lockfile`・`settings-gradle.lockfile`・`frontend/package.json`・`frontend/package-lock.json`・`config/npm-build-tools.txt`
- **Build Dependencies**: `backend` → `vendor/java-mustache-processor`（composite build）、`frontend` → `vendor/make-you-chic-ui/packages/make-you-chic-ui`（`file:`）、`backend` の bootWar → `frontend/dist` を同梱。

### APIs Discovered
- REST — `GET /api/admin/check`（`ms-check-p95` の対象）・`POST /api/auth/login`・`POST /api/auth/session/refresh`（p95 の警報の対象）。今回は API の中身を読んでいない。

### Frameworks & Libraries
- Spring Boot — 4.1.1 — バックエンド（toml 21 行）
- Jackson（tools.jackson）— 3.1.6（BOM で上書き）— JSON
- Spotless（Gradle のプラグイン）— 8.10.2 — フォーマットとライセンスヘッダー
- palantir-java-format — 2.98.0 — Java の書式
- networknt json-schema-validator — 3.0.6 — DSL の検証
- opentelemetry-logback-appender — 2.28.1-alpha — ログの外部エクスポート
- TypeScript — 6.0.3 — 型の検査・ビルド
- @typescript-eslint/parser — 8.70.1（`^8.67.0`）— ESLint の TS の読み込み
- @types/node — 26.6.2（`^26.2.0`）— Node の型
- Playwright（@playwright/test）— `^1.63.0` — E2E
- axe-core — `^4.13.0` — E2E のアクセシビリティの検査
- make-you-chic-ui — サブモジュールの `310e1ec` — デザインシステム

### Test Coverage
- **Test Directories**: `backend/src/test/java/cherry/mastersmith/user/`（単体 `XxxTest`・結合 `XxxIT`、testsupport）、`frontend/e2e/`（010〜100 と `support/`）
- **Test Frameworks**: JUnit 5・AssertJ・Mockito・Spring Boot Test（`OutputCaptureExtension`）、共通の `LogEvents`・`JsonLogRecords`、Playwright＋axe-core
- **Coverage Config**: 有り。JaCoCo の全体の合計とパッケージごとの下限（行 80%・分岐 70%、`backend/build.gradle.kts` 236〜270 行）、`packagesJudgedByTotal` は 12 パッケージ。

### Code Quality Indicators
- **Linting**: Spotless（palantir・licenseHeader）、SpotBugs＋FindSecBugs、frontend の Prettier・oxlint・ESLint（`@typescript-eslint/parser`）・Stylelint。
- **CI/CD**: `.github/workflows/ci.yml`（develop へのプッシュで `./gradlew verify`、タグで WAR をリリースに添付）。Dependabot は `.github/dependabot.yml`。
- **Documentation**: README は詳しいが、固定先の記述（38 行）が古い。コードの Javadoc・コメントは日本語で、決定の出典（Intent・決定の番号）を書く形。

### Technical Debt Signals
- `InitialAdminInitializer.java` 88・96・99 行が、Forbidden に反してメールアドレスをアプリのログに出す（据え置きの判断が Javadoc 38〜40 行に残る）。
- `InitialAdminInitializerTest.java` 118〜120 行が、ログにメールアドレスが **含まれること** を確かめている（直しの向きと逆の確かめ）。
- `README.md` 38 行の make-you-chic-ui の固定先 `735ef04` が、今の `310e1ec` と食い違う。
- `team.md` Testing Posture の `packagesJudgedByTotal`（「user.* を含む 22 パッケージ」）が、今のビルドの 12 パッケージと食い違う。
- `ms-check-p95` のしきい値 300 ms が `http.server.requests` のバケットの境界に無い。
- `.github/dependabot.yml` の npm に ignore が無く、TypeScript 7 のプルリクエスト（#5）が開いたまま。`@typescript-eslint/*` の peer は `typescript <6.1.0`。
- `@types/node` の大きな版 26 と、実行の Node 24（engines・CI）がずれている。

## Handoff Summary
- **Intent-relevant finding**:
  - 1件目: `backend/src/main/java/cherry/mastersmith/user/service/InitialAdminInitializer.java` 88・96・99 行の INFO がキー `email` で値を出す。ほかに `email` をログに渡す箇所は main に無い。`InitialAdminInitializerTest.java` 118〜120 行（含まれることの確かめ）と `InitialAdminIT.java`（パスワードだけ確かめる）を直す必要がある。`user.service` は既にパッケージごとのカバレッジの下限の対象。
  - 2件目: 固定先は `310e1ec`、`077f5b4` は手元にあり子孫（`a34d611`・`077f5b4` の2コミット、CSS と contrast.test の4ファイル、依存の変更なし）。外す対象は `frontend/e2e/100-app-text-contrast.e2e.ts` 72〜83 行の `STATE_KNOWN_VIOLATIONS` の2件と、README 158・957・990〜993 行の説明。
  - 3件目: 開いているプルリクエストは #20 jackson-bom 3.1.7・#19 spotless 8.10.3・#18 @types/node 26.6.3・#5 typescript 7.0.2。spotless は toml 29 行の1行（lockfile の外）、@types/node は package.json と package-lock.json。Jackson の ignore は今 major・minor だけ（dependabot.yml 48〜49 行）。npm の ignore は無い。
  - 4件目: `ms-check-p95` は `gt 300`、バケットは 100・250・500・1000・2000・5000 ms で 300 は境界に無い。警報・ダッシュボードのパネルのしきい値・SLI の表・README の表の4か所が 300 を持つ。
  - team.md: `packagesJudgedByTotal` の記述が今のビルドと食い違う（直す候補。仮説）。
- **Risks / follow-up**:
  - `typescript` は lock で実行時の扱い（`dev` の印なし）で、`config/npm-build-tools.txt` にも載る。TypeScript 7 を ignore にしても、6 系の High は統合を止める。
  - TypeScript 7 は `@typescript-eslint/*` の peer（`<6.1.0`）の外（仮説として、取り込むと npm ci または ESLint が失敗しうる）。ignore の対象を `typescript` だけにするか、`@typescript-eslint/*` も含めるかは要件で決める。
  - 固定先の更新は承認を得た専用のコミットで、前後のハッシュ（`310e1ec` → `077f5b4`）を記録する（project.md Mandated）。統合は fast-forward でよい（team.md）。E2E（100）の実測で2件が当たらなくなることを確かめてから一覧を空にする。hover の文字が白に替わるため、README 976〜988 行の表の前提も変わる。
  - README の外で、監査の書き込みの失敗の ERROR（README 774 行）もメールアドレスをキーと値で載せると README に書かれている（`audit` 部品。範囲の外のため読んでいない）。今回の1件目の範囲に含めるかは要件で決める。
  - `application.yaml`・`SanitizingLogRecordExporter.java`・`*SecretLeakIT` はスナップショットの範囲の外で、grep の範囲だけを見た。深く読む必要があれば範囲を広げる。
