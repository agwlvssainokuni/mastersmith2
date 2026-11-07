# 開発担当の調査（Intent 261107-verify-quick-local）

- 実施日: 2026-11-08
- 記録のコミット: `16f46f1268c07d141ade76d620d1858819384967`（`git rev-parse HEAD` と同じ）
- 広さ: Focused scan（依頼のスナップショットの範囲に絞った）。依頼の主題は、手元の `./gradlew verify` の時間を縮めるため、Bolt の統合の前に流す軽い入口 `verifyQuick` を足し、全件の `verify` を流す時点と、結合テストを手元だけ並べて流す口（`-PitForks`）、IT を絞る口（`-PitTests`）を決め、画面のテスト（Vitest）を1回にすること。CI は今までどおり `verify` を1本で流す。
- 確かめ方: ファイルの読み取りと `ls`・`grep`・`find` の数え上げだけ。テスト・ビルド・`./gradlew`・`npm`・`docker` は動かしていない。時間の実測は無い。
- 「確かめた事実」と「見立て（未検証）」を分けて書く。行の番号は記録のコミットのもの。

## Developer Code Scan Results

### Scan Coverage

- **Analyzed deeply**:
  - `build.gradle.kts`（全文 860 行）
  - `settings.gradle.kts`（全文）
  - `backend/build.gradle.kts`（全文 446 行）
  - `frontend/package.json`（全文）
  - `frontend/vitest.config.ts`（全文 80 行）
  - `frontend/scripts/check-license-header.mjs`・`check-bundle-size.mjs`・`check-dist-assets.mjs`・`make-ico.mjs`（全文）
  - `.github/workflows/`（`ci.yml` の全文。中のファイルはこの1つ）
  - `config/`（`license-header.txt`・`npm-build-tools.txt` の全文。中のファイルはこの2つ）
  - `README.md`（全文 164 行）
- **Skimmed only**:
  - `frontend/scripts/check-svg-safety.mjs`・`frontend/scripts/icon-binary.mjs`（頭の説明と定数だけ）
  - `docs/verification.md`（「1コマンドの検査」の節 5〜34 行と見出しの grep。スナップショットの範囲の外）
  - `backend/src/test/java/cherry/mastersmith/targetdb/testsupport/ContainerRuntimeCheck.java`・`TargetDbTestDatabase.java`（頭と判定の部分。範囲の外）
  - `backend/src/test/resources/junit-platform.properties`（jqwik の1行だけ。範囲の外）
  - `backend/src/test/java/`（`*IT.java`・`*Test.java` の数、Testcontainers・`ContainerRuntimeCheck`・`TestDatabase.register`・`ServerSocket` の grep。範囲の外）
- **Left out**:
  - `build/`・`backend/build/`・`frontend/dist/`・`frontend/coverage/`・`frontend/node_modules/`・`node_modules/`（ビルドの出力と依存）
  - `.gradle/`・`.idea/`（道具と IDE のキャッシュ）
  - `vendor/`（今回の範囲の外）
  - `reference/`・`.env`・`.env.targetdb`（開かない決まり）
  - AI-DLC の導入物（`.claude/` ほか）

### Packages Found

- `backend` — Gradle のサブプロジェクト — Java 25 — Spring Boot の実行可能 WAR。単体テスト `test`（`*Test`）と結合テスト `integrationTest`（`*IT`）を同じ `src/test/java` から名前で分ける
- ルートのプロジェクト（`mastersmith`）— Gradle — Kotlin DSL — 1コマンドの検査 `verify`、画面の npm・外部の道具（Gitleaks・OSV-Scanner）の呼び出し、E2E（`e2eTest`）
- `frontend` — npm（Gradle のサブプロジェクトではない）— TypeScript — React の SPA。Gradle のルートから `npm run <script>` で呼ばれる
- `vendor/java-mustache-processor` — Gradle の composite build（`settings.gradle.kts` 37 行）
- `vendor/make-you-chic-ui` — npm の `file:` の依存（`vendorInstall`・`vendorBuild` がビルドする）

### Build System

- **Type**: Gradle 9.8.2（Wrapper、Kotlin DSL）＋ npm（Node 26）
- **Config Files**: `settings.gradle.kts`・`build.gradle.kts`・`backend/build.gradle.kts`・`gradle/libs.versions.toml`（今回は読んでいない）・`frontend/package.json`・`frontend/vitest.config.ts`・`config/`
- **Build Dependencies**:
  - `verify` → 段のタスク 10 個（`verifyPrepare` → `verifyFormat` → `verifyLint` → `verifyLicense` → `verifyBuild` → `verifyUnitTest` → `verifyIntegrationTest` → `verifyCoverage` → `verifySecurity` → `verifyArtifact`）（`build.gradle.kts` 425〜513 行）
  - 段の順は `dependsOn` ではなく `mustRunAfter` で決める。段の中の各タスクに「それより前の段のすべてのタスクの後」を、段のタスクどうしにも前の段の後を付ける（496〜507 行）。`--continue` を付けなければ、1つ失敗すると後ろは流れない
  - `:backend:bootWar` → `:frontendBuild`（`backend/build.gradle.kts` 364 行）。`frontendBuild`・`frontendTypecheck`・`frontendTest`・`frontendCoverage` → `vendorBuild`。npm のタスクはすべて → `frontendInstall` → `checkToolchain`
  - `tasks.check` → `integrationTest`（`backend/build.gradle.kts` 158〜160 行）。`verify` は `check` を使わない
  - `gradle.properties` は無く、`org.gradle.parallel` などの並列の設定は無い

### APIs Discovered

- HTTP の口は今回読んでいない（今回の主題はビルドと検査の入口で、アプリの口は変わらない見込み）
- Gradle のタスクの口（今回の主題の口）: `verify` と 10 の段のタスク（段ごとに単独で流せる。例 `./gradlew verifyFormat`）、`e2eTest`（`-Pe2e.files=…` でファイルを選べる。`build.gradle.kts` 812〜818 行。`-P` で絞る前例はこれだけ）、`osvScan`・`gitleaksScan`・`perfEvidenceScan`、`:backend:test`・`:backend:integrationTest`・`:backend:jacocoTestReport`・`:backend:jacocoTestCoverageVerification`・`:backend:spotbugsGate`・`:backend:resolveAndLockAll`
- npm のスクリプト（`frontend/package.json` 10〜25 行）: `test`（`vitest run`）と `test:coverage`（`vitest run --coverage`）が別にある。どちらも `NODE_OPTIONS=--no-experimental-webstorage`

### Frameworks & Libraries

- Gradle — 9.8.2 — ビルドと検査の入口
- JaCoCo — 0.8.15（前の記録）— バックエンドのカバレッジ。`test.exec` と `integrationTest.exec` を合わせる
- JUnit Platform・Testcontainers — 前の記録の版 — 単体と結合。対象DB の3種類をコンテナで
- Vitest・`@vitest/coverage-v8` — `^5.0.5` — 画面の単体テストとカバレッジ（`thresholds` 行 80・分岐 70）
- SpotBugs＋FindSecBugs・Spotless・Gitleaks 8.30.1・OSV-Scanner 2.7.0 — 検査（CI は版と SHA-256 で固定）

### Test Coverage

- **Test Directories**: `backend/src/test/java/cherry/mastersmith/`（`*Test.java` 312・`*IT.java` 278、`find` で数えた）、`frontend/src/**/*.test.ts(x)` と `frontend/e2e/support/**/*.test.ts`（Vitest）、`frontend/e2e/*.e2e.ts`（Playwright、`verify` の外）
- **Test Frameworks**: JUnit 5（`useJUnitPlatform`）・Spring Boot Test・Testcontainers・jqwik・ArchUnit／Vitest（projects 2 つ）・Testing Library・vitest-axe・fast-check
- **Coverage Config**: present。バックエンドは `backend/build.gradle.kts` 165〜257 行（全体と、`packagesJudgedByTotal` の 7 パッケージを除くパッケージごと、行 80%・分岐 70%）。画面は `frontend/vitest.config.ts` 66〜77 行（全体の合計、行 80・分岐 70）

### Code Quality Indicators

- **Linting**: Spotless（Java・Gradle の Kotlin DSL・メールのテンプレート）・Prettier・oxlint・ESLint・Stylelint・ライセンスヘッダー（`frontend/scripts/check-license-header.mjs`、ひな形は `config/license-header.txt`）・SVG の許可リスト（`check-svg-safety.mjs`）
- **CI/CD**: `.github/workflows/ci.yml`。ジョブ `verify` が `./gradlew verify` を1本で流す（83〜84 行、`timeout-minutes: 60`）。タグのときはジョブ `release`
- **Documentation**: `README.md`（164 行。検査の手順の本文は `docs/verification.md` へ移り、README は目次と前提の道具だけを持つ）。ビルドのスクリプトは日本語のコメントで決まりの出どころ（Intent・単位）を細かく書く

### Technical Debt Signals

- **S1 画面のテスト（Vitest）が `verify` の中で2回流れる**: 5 の段の `frontendTest`（`npm run test`、`build.gradle.kts` 216 行）と 7 の段の `frontendCoverage`（`npm run test:coverage`、217〜220 行）。中身は同じテストの全件で、後者は V8 の計測つき。性能のテスト2つ（`TimeZoneOptionsPerformance.test.ts`・`conversionPerformance.test.ts`）も2回判定される（`docs/verification.md` 28 行が明記）。
- **S2 結合テストの並列の設定が無い**: `maxParallelForks`・`forkEvery`・`gradle.properties` の並列・JUnit の並列（`junit-platform.properties` は jqwik の1行だけ）のどれも無い。`integrationTest` は1つの JVM で 278 クラスを順に流す。
- **S3 IT を絞る口が無い**: `integrationTest` の `filter` は `includeTestsMatching("*IT")` だけ（`backend/build.gradle.kts` 152〜154 行）。Gradle の標準の `--tests` は使えるが、`verify` の中の段から渡す口は無い。`-P` で絞る前例は E2E の `e2e.files` だけ。
- **S4 カバレッジの判定が結合テストの結果に依る**: `jacocoTestReport`・`jacocoTestCoverageVerification` は `test.exec` と `integrationTest.exec` を合わせる（175〜177 行）。どちらもテストのタスクに `dependsOn` せず `mustRunAfter` だけ（189・225 行）。
- **S5 毎回流れる検査**: `gitleaksScan`（履歴全体、287〜293 行）と `perfEvidenceScan`（295〜302 行）は入力・出力を宣言せず、毎回流れる。`osvScan` は lockfile と `npm-build-tools.txt` を入力にするため、変わらなければ UP-TO-DATE。
- **S6 前の記録との差**: Node は 26（`engines` `>=26 <27`・`checkToolchain` の `v26.`・CI の `node-version: "26"`・README 14 行）、OSV-Scanner は 2.7.0（`ci.yml` 35 行・README 16 行）、`gradle/actions/setup-gradle` は v6.4.1（`ci.yml` 67 行）、README は 164 行（前の記録の 1,678 行から縮んだ）、`frontend/scripts/` は 6 ファイル。

## 確かめた事実

### F1 `verify` の段と中身（`build.gradle.kts` 425〜513 行）

| 順 | 段 | 含めるタスク |
|---|---|---|
| 0 | `verifyPrepare` | `checkToolchain`・`vendorInstall`（`npm ci`）・`vendorBuild`・`vendorUnchanged`・`mustacheVendorUnchanged`・`frontendInstall`（`npm ci`） |
| 1 | `verifyFormat` | ルートと `:backend` の `spotlessCheck`・`frontendFormatCheck`（Prettier）・`perfToolFormatCheck` |
| 2 | `verifyLint` | `frontendLint`（oxlint・ESLint）・`frontendLintCss`（Stylelint）・`perfToolLint`・`perfToolEslint` |
| 3 | `verifyLicense` | `frontendLicenseCheck`・`frontendSvgCheck` |
| 4 | `verifyBuild` | `:backend:compileJava`・`compileTestJava`・`frontendTypecheck`・`perfToolTypecheck`・`frontendBuild`・`frontendDistCheck` |
| 5 | `verifyUnitTest` | `:backend:test`・`frontendTest`（Vitest 1 回目） |
| 6 | `verifyIntegrationTest` | `:backend:integrationTest` |
| 7 | `verifyCoverage` | `:backend:jacocoTestReport`・`:backend:jacocoTestCoverageVerification`・`frontendCoverage`（Vitest 2 回目） |
| 8 | `verifySecurity` | `:backend:spotbugsGate`・`osvScan`・`gitleaksScan`・`perfEvidenceScan` |
| 9 | `verifyArtifact` | `:backend:bootWar`・`verifyDslSchemaInWar`・`verifyFaviconInWar`・`frontendBundleSize` |

- 段の表（`verifyStages`）は `Triple(名前, 説明, タスクの一覧)` の `List`。`stageTasks` の組み立てで、各段の各タスクに `mustRunAfter(earlier)`（それより前の段のすべてのタスク）を `configure` で付ける（496〜505 行）。順の決まりはタスクそのものに付くため、段の表を別に作ると、そこにも同じ形の決まりが付く。
- `verify` は `dependsOn(stageTasks)` だけ（509〜513 行）。説明の文字に段の順が書いてある。

### F2 バックエンドのテストのタスク（`backend/build.gradle.kts` 103〜160 行）

- 全 `Test` に共通: `useJUnitPlatform`、ヒープ 1g、`-XX:+EnableDynamicAgentLoading -Xshare:off`、`h2.compactThreads=1`・`user.timezone=Asia/Tokyo`・`jdk.httpclient.allowRestrictedHeaders=host`・`java.net.preferIPv4Stack=true`、`testLogging` は `failed`・`skipped` と FULL の例外。
- `test`: ヒープ 1536m、`includeTestsMatching("*Test")`。
- `integrationTest`: `Test` の新しいタスク、`sourceSets.test` の出力と実行時のクラスパス、`includeTestsMatching("*IT")`、`shouldRunAfter(tasks.test)`。並列（`maxParallelForks`）の指定は無い（Gradle の既定は 1）。
- jacoco のプラグインはすべての `Test` に計測を付けるため、`integrationTest.exec` ができる（`coverageExecutionData` が `test.exec`・`integrationTest.exec` を拾う、175〜177 行）。

### F3 カバレッジ（`backend/build.gradle.kts` 162〜257 行）

- 除外は起動クラスと `*Properties`（169〜173 行）。
- `packagesJudgedByTotal`（213〜221 行）は 7 パッケージ: `access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`（`team.md` の記録と一致）。
- `jacocoTestCoverageVerification`（223〜257 行）は規則2つ: 全体（行 0.80・分岐 0.70）と `element = "PACKAGE"`・`excludes = packagesJudgedByTotal`（同じ下限）。
- 画面（`frontend/vitest.config.ts` 66〜77 行）: `provider: 'v8'`・`allowExternal: true`・`include` は `src/**/*.{ts,tsx}` と `../perf/redact-k6-evidence.mjs`・`thresholds` は `lines: 80`・`branches: 70`。projects は `eslint-config`（`groupOrder: 0`、ESLint の設定を読む2本を先に1つだけで）と `unit`（`groupOrder: 1`）。

### F4 結合テストと Testcontainers（範囲の外、流し読み）

- `*IT.java` は 278。`TestDatabase.register`（内部DB の H2 をファイルで置く。`jdbc:h2:file:<dir>/mastersmith`）を使うクラスが 172、`@SpringBootTest` を含むファイルが 187。
- 対象DB のコンテナを使う IT は 39 クラス（`TargetDbTestDatabase.of`・`ContainerRuntimeCheck` の grep）。`TargetDbTestDatabase` はテストのクラスごとに1つコンテナを起動し、名前の重ならないスキーマと読み取りのアカウントを作ってクラスの終わりに消す。
- `ContainerRuntimeCheck`（JUnit の `BeforeAllCallback`）: Docker に届かず `CI=true` なら失敗、手元なら WARN と `TestAbortedException`（飛ばした扱い）。GitHub Actions は `CI=true` を設定する。
- 待ち受けの番号は `ServerSocket(0)` で空きを取る形（`SilentServer`・`StallingRelay`・`SilentSmtpServer`）。`TargetDbStartupIT` 91 行と `MailTestApplication` 97 行は `ServerSocket(0)` で番号を取ってすぐ閉じ、その番号を後で使う形。

### F5 CI（`.github/workflows/ci.yml`）

- きっかけ: `develop` への push・`v*` のタグ・手動。`concurrency` は ref ごと、取り消しなし。
- ジョブ `verify`: `ubuntu-latest`・`timeout-minutes: 60`。サブモジュールごと全履歴を取得 → JDK 25 → Node 26（npm のキャッシュ）→ `setup-gradle` v6.4.1 → Gitleaks 8.30.1 と OSV-Scanner 2.7.0 を版と SHA-256 で確かめて入れる → `./gradlew verify`（引数なし）→ WAR を 30 日保存。
- ジョブ `release`（タグのとき）: WAR を GitHub のリリースに添付。

### F6 README と文書

- `README.md` 164 行。`verify` に触れるのは 15・16・18・22・24・44・60・65 行。24 行は「`./gradlew verify` はコンテナの実行環境（colima）が動いていることを前提にします。対象DB の結合テストを…毎回実行するためです」。検査の段の表と手順の本文は `docs/verification.md`（目次 106 行）。
- `docs/verification.md` 13〜24 行が段の表、28 行が「画面のテストは `verify` の中でカバレッジなし（5 の段）とカバレッジつき（7 の段の `frontendCoverage`）の2回流れ、どちらでも判定します」。
- `frontend/scripts/`: `check-license-header.mjs`（`frontend/` の TS・JS・CSS・HTML・SVG と `perf/` の MJS・JS のヘッダー。`collectFiles` を `check-svg-safety.mjs` が共有）・`check-svg-safety.mjs`・`check-dist-assets.mjs`（`public/` と `dist` の一致、favicon の `<link>`）・`icon-binary.mjs`・`check-bundle-size.mjs`（初回の JS の gzip の合計、500KB 超えで警告だけ）・`make-ico.mjs`（手で流す道具、ビルドに組み込まない）。
- `config/`: `license-header.txt`（Spotless と画面のヘッダーの共通のひな形）・`npm-build-tools.txt`（OSV-Scanner で devDependencies でも High 以上で止める道具の一覧）。

## 見立て（未検証）

- **H1 `verifyQuick` の段の組み方**: 段の順の決まり（`mustRunAfter`）はタスクそのものに付くため、`verifyQuick` 用に段の表を別に作り、そこで同じタスクを違う順に置くと、両方の決まりが同じタスクに重なる。例: 全件の `verify` は `frontendCoverage` を `integrationTest` の後に置く。`verifyQuick` で `frontendCoverage` を単体テストの段に移し、全件の `verify` でも同じ形にするなら矛盾しないが、片方だけ変えると順の決まりが循環しうる（Gradle は循環を誤りにする見込み）。段の表を1つにして「含めるかどうか」だけを切り替える形のほうが安全と見る。
- **H2 結合テストを除いたときのカバレッジ**: `verifyQuick` で `integrationTest` を流さないと、`integrationTest.exec` が無い（か古い）まま報告が作られる。`fileTree` は無いファイルを黙って外すため報告は作れるが、IT でしか通らないパッケージ（`web` の層など）は下限を下回る見込み。参考値として出すなら `jacocoTestCoverageVerification` を外し、`jacocoTestReport` だけにする形になる。前の回の `integrationTest.exec` が `build/` に残ると、古い実行記録が混ざりうる。
- **H3 `-PitForks`（手元だけ並べる）**: `maxParallelForks` を `-P` の値で決めれば CI（引数なし）は今までどおり 1 になる。ただし、(a) 対象DB の IT はクラスごとにコンテナを起動するため、並べた数だけ同時のコンテナが増え、colima の VM（README 23 行: CPU 4・メモリ 6GiB）に響く、(b) テストの JVM のヒープ 1g が並べた数だけ要る、(c) `ServerSocket(0)` で番号を取って閉じてから使う形（`TargetDbStartupIT`・`MailTestApplication`）は、別の JVM と番号を取り合いうる、(d) H2 のファイルは実行ごとの一時の場所に置く形で重ならない見込み、(e) jacoco の実行記録は1つのファイルに複数の JVM が追記する形で、Gradle の標準の扱いでは問題ない見込み。どれも流して確かめていない。
- **H4 `-PitTests`（IT を絞る）**: `integrationTest` の `filter` に `-P` の値を足す形（`includeTestsMatching` の追加）が素直と見る。`*IT` の外の名前を指したときに何も流れない（Gradle の「テストが見つからない」の誤り）扱いを決める必要がある。
- **H5 画面のテストを1回にする**: `frontendTest` を段から外して `frontendCoverage` だけにし、単体テストの段に置く形か、7 の段に残す形が考えられる。どちらでも性能のテスト2つは計測つきの1回だけで判定されることになる（CI の失敗が出たのも計測つきの回。前の Intent の K-108）。
- **H6 時間の内訳**: どの段がどれだけかかるかは測っていない。`team.md` の記録の物差し（CI の上限 60 分の 7 割の約 42 分）に対し、前の記録では CI の検査の job が 28 分 37 秒（`project.md`）。手元の内訳は Build and Test などの記録を読むか、計測で確かめる必要がある。

## Handoff Summary

- **Intent-relevant finding**: `verify` は 10 の段を `mustRunAfter` でつないだ1本の入口で（`build.gradle.kts` 425〜513 行）、画面のテストは 5 の段の `frontendTest` と 7 の段の `frontendCoverage` で2回流れる（216〜220 行）。結合テスト `:backend:integrationTest` は `*IT` 278 クラスを1つの JVM で流し、並列の設定も絞る口も無い（`backend/build.gradle.kts` 147〜156 行）。バックエンドのカバレッジの判定は単体と結合の実行記録を合わせて行う（175〜177・223〜257 行）。CI は `./gradlew verify` を引数なしで1本流す（`ci.yml` 83〜84 行）。
- **Risks / follow-up**:
  - `verifyQuick` を足すときは、段の順の決まりがタスクに付く作りのため、全件の `verify` と順が食い違わない形にする（H1）。
  - IT を除いたカバレッジは参考値にしかならず、`jacocoTestCoverageVerification` は `verifyQuick` から外す必要がある見込み（H2）。古い `integrationTest.exec` の扱いも決める。
  - `-PitForks` はコンテナの同時の数・ヒープ・番号の取り合いに響く（H3）。CI では使わない。
  - 書き換える決まりの文: `project.md` の Mandated「ALWAYS 統合前に、フォーマット・リンタ・ビルド・全テスト…」、`team.md` の Way of Working の「CI と同じ検査を…1コマンドで実行し、すべて通ってから統合する」「統合前の1コマンドの検査は、コンテナの実行環境（colima）が動いていることを前提にする」、Testing Posture の「対象DB の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する」「`packagesJudgedByTotal` …`./gradlew verify` で実測」、`project.md` の Testing Posture の `cleanTest`・`cleanIntegrationTest` と colima の環境変数の行。README（22・24・60・65 行）と `docs/verification.md`（段の表・28 行）も手順に合わせて直す対象になる。
  - `team.md` の「CI の時間を測って記録する」の物差しは変えない（CI は今までどおり）。
