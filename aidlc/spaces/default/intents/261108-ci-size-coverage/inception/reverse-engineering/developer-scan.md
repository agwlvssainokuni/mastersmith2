# 開発担当のコードスキャン（Intent 261108-ci-size-coverage）

- 段: Reverse Engineering（Focused scan・Minimal）。知識ベースの判定は STALE（前の Intent `261107-verify-quick-local` の記録のコミット `16f46f1` の後に、`build.gradle.kts`・`backend/build.gradle.kts`・`docs/verification.md` が変わり、`scripts/` が足された。コミット `f067e2c0`・`9fd4f7f1`）。
- 記録のコミット: `9fd4f7f14618f27da3ac87a59ef89e8b062684b4`（`git rev-parse HEAD` と一致）。
- 確かめ方: ファイルの読み取り（`cat`・`sed`・`grep`・`ls`・`wc`・`git log`・`git ls-files`）だけ。テスト・ビルド・`./gradlew`・`npm`・`docker`・`cloc` は流していない。報告の置き場（`backend/build/`・`frontend/coverage/`）は git の対象外のため開いていない。
- 所見の番号は前の記録の K-118 から続けて K-119〜K-126 とした。

## Developer Code Scan Results

### Scan Coverage
- **Analyzed deeply**:
  - `.github/workflows/`（`ci.yml` の1つ、115 行、全文）
  - `scripts/`（`verify-graph-check.mjs` 238 行・`verify-graph-baseline.txt` 63 行、全文）
  - `frontend/scripts/check-license-header.mjs`（127 行、全文）
  - `frontend/scripts/check-bundle-size.mjs`（70 行、全文）
  - `frontend/scripts/check-dist-assets.mjs`（179 行、全文）
  - `frontend/scripts/make-ico.mjs`（64 行、全文）
  - `frontend/vitest.config.ts`（80 行、全文）
  - `frontend/package.json`（62 行、全文）
  - `backend/build.gradle.kts`（546 行、全文）
  - `build.gradle.kts`（913 行、全文）
  - `docs/verification.md`（353 行、全文。E2E のファイルの表は見出しと行の種類だけを確かめた）
- **Skimmed only**:
  - `frontend/scripts/check-svg-safety.mjs`（350 行、頭と export の一覧だけ）・`frontend/scripts/icon-binary.mjs`（154 行、export の一覧だけ）
  - `frontend/src/*.test.ts`（ファイル名の一覧だけ。`frontend/scripts/` と `perf/` の道具のテストの置き場の確かめ）
  - 前の知識ベースの9文書（`aidlc/spaces/default/codekb/mastersmith2/`）
- **Left out**:
  - `backend/build/`・`build/`・`frontend/coverage/`・`frontend/dist/`・`frontend/node_modules/`（ビルドの出力と入れた依存。`.gitignore` が外す）
  - `vendor/make-you-chic-ui/`・`vendor/java-mustache-processor/`（サブモジュール）
  - `.github/` の `workflows/` 以外（`dependabot.yml`。スナップショットの範囲の外）、`.claude/` ほか AI-DLC の置き場、`reference/`、`.env`

### Packages Found
- `mastersmith`（ルートの Gradle のビルド）— Gradle の多重プロジェクトの根 — Kotlin DSL — 1コマンドの検査（`verify`・`verifyQuick`）、画面の npm と外部の道具（Gitleaks・OSV-Scanner）の呼び出し、E2E（`e2eTest`）
- `:backend` — Spring Boot のアプリ — Java 25 — 実行可能 WAR、テスト（`test`・`integrationTest`）、JaCoCo、SpotBugs
- `mastersmith-frontend`（`frontend/package.json`）— npm のパッケージ（private、ES modules）— TypeScript・React — 画面と、検査の道具の Node の台本（`frontend/scripts/`）
- `scripts/`（リポジトリのルート）— 手で流す Node の台本の置き場 — JavaScript（`.mjs`、ES modules、`node:` の組み込みだけ）— 今は `verify-graph-check.mjs`（`verify`・`verifyQuick` のタスクの一覧を `--dry-run` で比べる回帰の確かめ）

### Build System
- **Type**: Gradle 9.8.2（Kotlin DSL、Wrapper）＋ npm（Node 26、`frontend/`）
- **Config Files**: `build.gradle.kts`・`backend/build.gradle.kts`・`frontend/package.json`・`frontend/vitest.config.ts`・`.github/workflows/ci.yml`
- **Build Dependencies**:
  - `verify`（`build.gradle.kts` 509〜513 行）→ 段のタスク 10 個（`verifyStages`、425〜494 行）。順は各タスクに付けた `mustRunAfter`（496〜507 行）。
  - `verifyQuick`（548〜555 行）→ `verifyStages` から `frontendTest`・`jacocoTestCoverageVerification`・（`-PitTests` が無ければ）`integrationTest` を外し、`jacocoTestReport` を `jacocoQuickReport` に置き換えた並び（523〜546 行）。`verify` に `-PitTests` を付けると失敗（559〜566 行）。
  - `:backend:bootWar` → `:frontendBuild`（`backend/build.gradle.kts` 461〜468 行）。CI の成果物の WAR は `backend/build/libs/mastersmith.war`。
  - `frontendCoverage`（`build.gradle.kts` 217〜220 行）→ `npm run test:coverage`（`frontend/package.json` 23 行）→ `frontend/coverage/` に報告。

### APIs Discovered
- HTTP の口は今回の範囲に無い（読んでいない）。
- 開発者が呼ぶ口: Gradle のタスク（`verify`・`verifyQuick`・段のタスク・`:backend:integrationTest`・`:backend:jacocoTestReport`・`:backend:jacocoQuickReport`・`osvScan`・`e2eTest`）と `-P` の値（`-PitTests`・`-PitForks`・`-Pe2e.files`）、npm のスクリプト 14 個（`frontend/package.json` 10〜25 行）、手で流す台本 `node scripts/verify-graph-check.mjs`・`node frontend/scripts/make-ico.mjs`。
- CI の口: `ci.yml` のジョブ `verify`（7 手順）と `release`（タグのとき）。`$GITHUB_STEP_SUMMARY` への書き込みは無い（`grep` で 0 件）。

### Frameworks & Libraries
- GitHub Actions（どれもコミットの SHA で固定、コメントが版）— `actions/checkout` v7.0.1・`actions/setup-java` v6.1.0・`actions/setup-node` v7.1.0・`gradle/actions/setup-gradle` v6.4.1・`actions/upload-artifact` v7.0.2・`actions/download-artifact` v8.0.2 — CI
- Gitleaks 8.30.1・OSV-Scanner 2.7.0 — `ci.yml` の `env`（32〜36 行）で版と SHA-256 を固定し、`curl` → `sha256sum -c` → `$HOME/.local/bin` → `$GITHUB_PATH` で入れる（69〜81 行）
- JaCoCo（版は `libs.versions.jacoco`、`backend/build.gradle.kts` 209〜211 行。値は前の記録で 0.8.15）— バックエンドのカバレッジ
- Vitest `^5.0.5`・`@vitest/coverage-v8` `^5.0.5` — 画面のテストとカバレッジ
- cloc — 今のリポジトリに無い（`ci.yml`・`build.gradle.kts`・`frontend/package.json`・`docs/verification.md` の `grep` で 0 件）

### Test Coverage
- **Test Directories**: `backend/src/test/java/`（`*Test`・`*IT`）、`frontend/src/**/*.test.ts(x)`、`frontend/e2e/support/**/*.test.ts`。`frontend/scripts/` の道具のテストは `frontend/src/` の直下に置く前例がある（`licenseHeaderCheck.test.ts`・`distAssetsCheck.test.ts`・`iconBinary.test.ts`・`svgCheckScope.test.ts`・`appIconAssets.test.ts`。perf の道具は `perfEvidenceRedactor*.test.ts`・`perfEvidenceScan.test.ts`）。`scripts/verify-graph-check.mjs` のテストは無い。
- **Test Frameworks**: JUnit 5・jqwik・ArchUnit・Testcontainers／Vitest・Testing Library・vitest-axe・fast-check／Playwright
- **Coverage Config**: present
  - バックエンド: `jacocoTestReport`（`backend/build.gradle.kts` 231〜240 行）は `test.exec`・`integrationTest.exec` を合わせ、XML と HTML を作る（置き場は Gradle の既定。`docs/verification.md` 112 行と `backend/build.gradle.kts` 245 行のコメントは `backend/build/reports/jacoco/test/`）。`jacocoQuickReport`（250〜296 行）は `test.exec` だけで `backend/build/reports/jacoco/quick/jacocoQuickReport.xml` と `html/` を作り、XML の根の直下の `counter` から全体の行・分岐を読んで表示する。判定は `jacocoTestCoverageVerification`（323〜357 行、全体とパッケージごと、行 0.80・分岐 0.70、`packagesJudgedByTotal` 7 つ）。
  - 画面: `frontend/vitest.config.ts` 66〜77 行（`provider: 'v8'`・`allowExternal: true`・`include` は `src/**/*.{ts,tsx}` と `../perf/redact-k6-evidence.mjs`・`reporter` は `text`・`html`・`json-summary`・`reportsDirectory: 'coverage'`・`thresholds` は `lines: 80`・`branches: 70`）。

### Code Quality Indicators
- **Linting**: Prettier・oxlint・ESLint・Stylelint は `frontend/` の中（`npm run format:check`・`lint`・`lint:css`）と、perf の伏せ字の道具 `perf/redact-k6-evidence.mjs` の1ファイル（`perfToolFormatCheck`・`perfToolLint`・`perfToolEslint`・`perfToolTypecheck`、`build.gradle.kts` 226〜283 行）。ライセンスヘッダーは `frontend/scripts/check-license-header.mjs`（`frontend/` の TS・TSX・JS・MJS・CJS・CSS・HTML・SVG と `perf/` の MJS・JS）と Spotless（Java・Gradle の Kotlin DSL・メールのテンプレート）。ルートの `scripts/` はどれの対象でもない（K-121）。
- **CI/CD**: `.github/workflows/ci.yml`（`develop` への push・`v*` のタグ・手動）。
- **Documentation**: `docs/verification.md`（検査とテストの手順。見出しは「1コマンドの検査」「リリースの前の確かめ」「ビルドした WAR での画面の確認（E2E）」の3つ）。台本のファイルの頭に日本語の説明と流し方がある。

### Technical Debt Signals
- ルートの `scripts/` の台本はライセンスヘッダー・書式・リンタ・型の検査・テスト・カバレッジのどれにも入っていない（`docs/verification.md` 70 行が「`scripts/` をライセンスヘッダーの検査に足すかは後の Intent で決める」と書く）。`scripts/program-size.mjs` を足すと同じ状態の台本が増える（K-121）。
- 前の知識ベースの記述のうち、`verifyQuick`・`-PitTests`・`-PitForks` が「無い」「足す見込み」とする箇所は古い（K-122）。
- CI は `./gradlew verify` が失敗すると後ろの手順（WAR の保存）を流さない（手順に `if:` が無く、GitHub Actions の既定は前の手順の成功のときだけ。K-119）。

## Handoff Summary
- **Intent-relevant finding**:
  - CI のジョブ `verify` は 7 手順（取得 → JDK 25 → Node 26 → Gradle → Gitleaks・OSV-Scanner → `./gradlew verify` → WAR の保存）で、`fetch-depth: 0` で全履歴を取る（`.github/workflows/ci.yml` 44〜92 行）。要約（`$GITHUB_STEP_SUMMARY`）への書き込みは今は無い。外部の道具は `env` の版と SHA-256 で固定して入れる型がある（32〜36・69〜81 行）（K-119）。
  - カバレッジの元は、バックエンドが `jacocoTestReport` の XML（`backend/build/reports/jacoco/test/jacocoTestReport.xml`。Gradle の既定の名前で、ファイルは開いていない）、画面が Vitest の `json-summary`（`frontend/vitest.config.ts` 71〜72 行。置き場は `frontend/coverage/`、名前は道具の既定の `coverage-summary.json` で、ファイルは開いていない）。全体の値を XML の根の直下の `counter` から読む前例が `jacocoQuickReport`（`backend/build.gradle.kts` 266〜295 行。外部の DTD を読まない設定つき）にある（K-120）。
  - ルートの `scripts/` には `verify-graph-check.mjs` の前例があり、`.mjs`・Apache License 2.0 の `/* */` ヘッダー・`node:` の組み込みだけ・`import.meta.url` からリポジトリのルートを求める形。ただし検査とテストの対象の外（K-121）。
- **Risks / follow-up**:
  - cloc が `ubuntu-latest` に入っているか、cloc のライセンス（GPL-2.0 と思われる）、数える範囲（`vendor/`・`node_modules/`・ビルドの出力・`aidlc/` を外すか）は確かめていない。ライセンスが Apache License 2.0 と違えば、`team.md` の決まりで採用の理由を ADR などに残す要がある（K-124）。
  - 「前の回のプッシュのコミット」の取り方（`github.event.before` など）は確かめていない。手動・タグ・最初の push・force-push のときの扱いを要件で決める要がある（K-123）。
  - `./gradlew verify` が途中で落ちるとカバレッジの報告は作られない（7 の段より前で落ちたとき）。要約の手順を失敗のときも流すか、ファイルが無いときにどう書くかを決める要がある（K-124）。
  - 新しい台本を `scripts/` に置くとき、ライセンスヘッダー・書式・リンタ・テストの対象に入れるか（`frontend/scripts/` に置く・`frontend/src/` にテストを置き `coverage.include` に足す前例がある）を決める要がある。`coverage.include` に足すのは計測を広げる向きで、除外を増やす決まりには当たらない（K-121）。
  - リポジトリは公開のため、要約に載せる値（パスの名前・数）に秘密や個人に関する値が入らないことを確かめる（K-124）。
