# 開発担当のコードスキャン（Intent 261108-ci-checks-release）

- 日付: 2026-11-09
- リポジトリ: `mastersmith2`（プロジェクトルート、単一のリポジトリ）
- 記録するコミット: `4fe8e79065d8aa840ad25882509b5f061da0a663`（`git rev-parse HEAD` と一致。スナップショットの `head_commit`）
- 走査の広さ・深さ: Focused scan・Minimal。既存の知識ベースの判定は STALE（前の Intent `261108-ci-size-coverage` の記録のコミット `9fd4f7f` の後に、`.github/workflows/ci.yml` と `scripts/` が変わった）。
- 確かめ方: ファイルの読み取り（`cat`・`sed`・`grep`・`ls`・`wc`・`find`）と、`git rev-parse`・`git log`・`git merge-base`・`git status` だけ。テスト・ビルド・`./gradlew`・`npm`・`docker`・cloc・`gh` は流していない。カバレッジと CI の時間は測っていない（要件の段で測る）。
- 書き方: 確かめた事実と、確かめていない見立て（「見立て（未検証）」と書く）を分けた。所見の番号は前の知識ベースの K-126 の続き（K-127〜K-137）。

## Developer Code Scan Results

### Scan Coverage

- **Analyzed deeply**:
  - `.github/workflows/`（`ci.yml` の1ファイル、143 行の全文）
  - `scripts/`（`ci-summary.mjs` 445 行・`program-size.mjs` 533 行・`verify-graph-check.mjs` 238 行・`verify-graph-baseline.txt` 63 行。どれも全文）
  - `frontend/scripts/check-license-header.mjs`（127 行の全文）
  - `build.gradle.kts`（913 行。1〜566 行は全文、567〜913 行の E2E の部分は前の記録のまま。`grep` で範囲を確かめた）
  - `backend/build.gradle.kts`（546 行。100〜357 行のテスト・JaCoCo・`packagesJudgedByTotal` は全文、ほかは前の記録の範囲を `grep` で確かめた）
  - `docs/verification.md`（398 行。1〜200 行は全文、200〜398 行の E2E の節は頭と見出しだけ）
  - `backend/src/main/java/cherry/mastersmith/access/service/`（3 ファイル）・`backend/src/test/java/cherry/mastersmith/access/service/`（2 ファイル）
  - `backend/src/main/java/cherry/mastersmith/audit/repository/`（2 ファイル）・`backend/src/test/java/cherry/mastersmith/audit/repository/`（1 ファイル）
  - `backend/src/main/java/cherry/mastersmith/common/error/domain/`（5 ファイル）・`backend/src/test/java/cherry/mastersmith/common/error/domain/`（1 ファイル）
  - `backend/src/main/java/cherry/mastersmith/common/error/service/`（2 ファイル）・`backend/src/test/java/cherry/mastersmith/common/error/service/`（2 ファイル）
  - `backend/src/main/java/cherry/mastersmith/common/health/`（2 ファイル）・`backend/src/test/java/cherry/mastersmith/common/health/`（2 ファイル）
  - `backend/src/main/java/cherry/mastersmith/common/i18n/domain/`（2 ファイル）・`backend/src/test/java/cherry/mastersmith/common/i18n/domain/`（1 ファイル）
  - `backend/src/main/java/cherry/mastersmith/common/web/`（6 ファイル）・`backend/src/test/java/cherry/mastersmith/common/web/`（3 ファイル）
- **Skimmed only**:
  - `README.md`（「前提の道具」9〜25 行と文書の目次 103〜121 行、リリースに関わる語の `grep`）
  - `frontend/scripts/` のほかの5ファイル（一覧と行の数。テストのファイル名は `frontend/src/` の `grep`）
  - `frontend/package.json`（`scripts` の 10〜24 行）・`frontend/vitest.config.ts`（`include` の `grep`）・`frontend/eslint.config.js`・`frontend/tsconfig.perf-tool.json`・`.pre-commit-config.yaml`（`perf`・`scripts` の `grep`）
  - `.github/dependabot.yml`（`package-ecosystem` の行の `grep`）
  - `backend/src/main/java/cherry/mastersmith/common/error/web/`・`backend/src/test/java/cherry/mastersmith/common/error/web/`（ファイル名の一覧だけ。`packagesJudgedByTotal` の外）
  - `backend/src/test/java/`（`*Test.java`・`*IT.java` の数と、`*BoundaryArchitectureTest.java` の一覧）
- **Left out**: `vendor/`（サブモジュール）、`node_modules/`、ビルドの出力（`backend/build/`・`frontend/dist/`・`frontend/coverage/`・`build/`）、`.gradle/`、AI-DLC の置き場（`.claude/` ほか、`aidlc/` の中のコード知識ベース以外）、`reference/`、`.env`

### Packages Found

- `.github/workflows/ci.yml` — CI の定義 — YAML（GitHub Actions） — 統合の後の再確認（`./gradlew verify`）と、規模・テストの件数・カバレッジの要約
- `scripts/` — 手で流す台本と CI の報告の台本 — JavaScript（Node の ES modules、組み込みだけ） — `ci-summary.mjs`・`program-size.mjs`（CI の要約）、`verify-graph-check.mjs`（検査の入口の回帰の確かめ）
- `frontend/scripts/` — 画面の検査の道具 — JavaScript（Node） — ライセンスヘッダー・SVG・`dist` の素材・量
- `cherry.mastersmith.access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web` — `packagesJudgedByTotal` の 7 パッケージ — Java — 下の K-132

### Build System

- **Type**: Gradle（Kotlin DSL）9.8.2 の Wrapper。画面は npm（Gradle から呼ぶ）。外部の道具（Gitleaks・OSV-Scanner）も Gradle から呼ぶ。
- **Config Files**: `build.gradle.kts`・`backend/build.gradle.kts`・`frontend/package.json`・`frontend/vitest.config.ts`・`.github/workflows/ci.yml`
- **Build Dependencies**: `verify` は `verifyStages`（0〜9 の 10 段）に `dependsOn` し、段の順は各タスクに付けた `mustRunAfter`（`build.gradle.kts` 496〜507 行）。`verifyQuick` は同じ表から `frontendTest`・`jacocoTestCoverageVerification`・（`-PitTests` が無ければ）`integrationTest` を外し、`jacocoTestReport` を `jacocoQuickReport` に替えたもの（515〜555 行）。CI はジョブ `verify` の中で `./gradlew verify` を引数なしで流す（`ci.yml` 84〜85 行）。

### APIs Discovered

- HTTP の口は今回読んでいない（範囲の外）。
- 開発者が呼ぶ口: `./gradlew verify`・`verifyQuick`・`-PitTests`・`-PitForks`・`node scripts/program-size.mjs [--base <commit>]`・`node scripts/ci-summary.mjs [--base <commit>] [--backend-reports <dir>] [--frontend-coverage <dir>]`・`node scripts/verify-graph-check.mjs`。
- `scripts/program-size.mjs` は関数を `export` する（`classify`・`clocVersion`・`countSize`・`countBase`・`computeDiff`・`int`・`fixed`・`collectMeta`・`renderReport`）。`scripts/ci-summary.mjs` も `pct`・`readJUnitDir`・`parseJacoco`・`parseGradleFloors`・`frontendArea` を `export` するが、読み込むと `main()` が流れる（439〜445 行。直接流したときだけ呼ぶ形になっていない）。`verify-graph-check.mjs` は `export` せず、読み込むとすぐ `./gradlew` を流す。

### Frameworks & Libraries

- GitHub Actions: `actions/checkout` v7.0.1・`actions/setup-java` v6.1.0・`actions/setup-node` v7.1.0・`gradle/actions/setup-gradle` v6.4.1・`actions/upload-artifact` v7.0.2・`actions/download-artifact` v8.0.2（どれもコミットの SHA で固定）
- 道具: Gitleaks 8.30.1・OSV-Scanner 2.7.0（ワークフローの `env` に版と SHA-256）、cloc（`apt-get install -y cloc`、版を固定しない）、JDK 25（`java-version: "25"`）、Node 26（`node-version: "26"`）
- テスト: JUnit 5・jqwik（`ProblemTypeTest`・`AcceptLanguageResolverTest` の性質のテスト）・Spring Boot Test・Testcontainers・JaCoCo

### Test Coverage

- **Test Directories**: `backend/src/test/java/cherry/mastersmith/`（`*Test.java` 312・`*IT.java` 278。2026-11-09 の `find`）、`frontend/src/`（`frontend/scripts/` のテストは `frontend/src/` の直下: `licenseHeaderCheck.test.ts`・`distAssetsCheck.test.ts`・`iconBinary.test.ts`・`svgSafetyCheck*.test.ts`・`svgCheckScope.test.ts`・`appIconAssets.test.ts`）。ルートの `scripts/` のテストは無い。
- **Test Frameworks**: JUnit 5・jqwik・Spring Boot Test（バックエンド）、Vitest（画面と `frontend/scripts/`）
- **Coverage Config**: present（`backend/build.gradle.kts` 206〜357 行の JaCoCo と `packagesJudgedByTotal`、`frontend/vitest.config.ts` の `thresholds`）

### Code Quality Indicators

- **Linting**: 画面は Prettier・oxlint・ESLint・Stylelint（`frontend/` の中で `prettier --check .`・`oxlint . && eslint .`）。`frontend/` の外は `perf/redact-k6-evidence.mjs` の1ファイルだけを別のタスク（`perfToolFormatCheck`・`perfToolLint`・`perfToolEslint`・`perfToolTypecheck`、`build.gradle.kts` 226〜283 行）で検査する。ライセンスヘッダーは `frontend/scripts/check-license-header.mjs` が `frontend/` と `perf/` を集める（105〜108 行）。ルートの `scripts/` はどれの対象でもない（K-130）。
- **CI/CD**: `.github/workflows/ci.yml`（ジョブ `verify` は 10 手順、ジョブ `release` はタグのときだけ。K-127）
- **Documentation**: `docs/verification.md` に検査の入口・使い分け・`packagesJudgedByTotal` の確かめ（73〜81 行）・`-PitForks` の実測（90〜102 行）・CI の要約（133〜176 行）・リリースの前の確かめ（178〜198 行）。README はリリースの手順を持たない（K-136）。

### Technical Debt Signals

- `docs/verification.md` 78 行の確かめのコマンドが 7 パッケージを手で書いた正規表現で持ち、81 行が「一覧から外れたら、このコマンドからも外す」と手で合わせる運用（R-07。K-131）。
- `ProblemTypeRegistry` に辿れない分岐がある（K-133）。
- CI の `cloc` は `ubuntu-latest` の apt の版で、版が固定されていない（K-128）。
- `scripts/ci-summary.mjs` の `parseGradleFloors` が `packagesJudgedByTotal` を正規表現 `listOf\(` で読むため、一覧の書き方を変えると読めなくなる（K-135）。

### 所見（K-127〜K-137）

**K-127 CI は 10 手順になった（前の記録 K-119 の 7 手順は古い）（`build-and-verify`）**

- 確かめた事実（`.github/workflows/ci.yml` 143 行）:
  - 前の Intent のコミット `71a8b436` で、ジョブ `verify` の 7 手順の後に3つの手順が足された: 8「規模を数える道具（cloc）を入れる」（96〜99 行、`sudo apt-get update && sudo apt-get install -y cloc`）、9「規模・テストの件数・カバレッジを実行の要約に書く」（101〜108 行、`node scripts/ci-summary.mjs --base "$SIZE_BASE"`。`SIZE_BASE` は `develop` へのブランチの push のときだけ `github.event.before`）、10「カバレッジの HTML の報告を保存する」（110〜120 行、`coverage-html-${{ github.sha }}`、14 日、`if-no-files-found: ignore`）。3つとも `if: always()` と `continue-on-error: true`（95 行のコメント: 報告だけの手順で、成否で CI を失敗にしない）。
  - 1〜7 の手順・きっかけ（`develop` への push・`v*` のタグ・手動）・`permissions: contents: read`・`concurrency`（ref ごとで取り消さない）・`timeout-minutes: 60`・ジョブ `release`（`needs: verify`、タグのときだけ）は前の記録と同じ。
  - 先頭のコメント（15〜17 行）は「CI は秘密情報を使わない。使う Actions はコミットのハッシュで固定する」。

**K-128 `ubuntu-latest` に頼る所と、版を固定していない所（`build-and-verify`）**

- 確かめた事実:
  - `runs-on: ubuntu-latest` は2か所（ジョブ `verify` の 42 行、ジョブ `release` の 126 行）。
  - 版を固定しているもの: Actions（コミットの SHA）、Gitleaks・OSV-Scanner（`env` の版と SHA-256、`sha256sum -c -` で確かめる。70〜82 行。取得の URL は `linux_x64`・`linux_amd64` の決め打ち）、Gradle（Wrapper）、npm の依存（lockfile と `npm ci`）、Node の大きな版（`checkToolchain` が `v26.` で始まらないと失敗。`build.gradle.kts` 88〜99 行）。
  - 版を固定していないもの: 走らせる機械のイメージ（`ubuntu-latest` の指す Ubuntu の版と、入っている Docker・`curl`・`sha256sum`・`tar`・`gh`）、cloc（apt の版。`docs/verification.md` 175 行が「CI では版を固定せず、`ubuntu-latest` の apt の版を入れます。報告に版が出ます」と書く）、JDK（`java-version: "25"` の大きな版だけ）、Node（`node-version: "26"` の大きな版だけ）。
  - 結合テストは Testcontainers で対象DB のコンテナを起動するため、機械の Docker に頼る（`team.md` の決まり。CI では Docker が無いと失敗）。
  - `.github/dependabot.yml` は `github-actions`・`docker`・`docker-compose`・`gradle`・`npm` を週ごとに見る（`package-ecosystem` の行）。Actions の SHA の固定は Dependabot が上げるが、`runs-on` のラベルは対象の外（見立て。Dependabot の仕様で、確かめてはいない）。
- 見立て（未検証）: `ubuntu-latest` が Ubuntu 26 に移ったこと（依頼の「apt の取得元が resolute」）で動きが変わりうるのは、apt の cloc の版（規模の値がずれる。報告に版が出る）、Docker の版（Testcontainers）、事前に入っている道具。固定するなら `runs-on: ubuntu-24.04`・`ubuntu-26.04` のような版つきのラベルと、cloc の版の固定（apt の版の指定か、Gitleaks と同じ取得と SHA-256 の確かめ）が候補。どちらも流して確かめる必要がある。

**K-129 CI の時間の内訳の手がかり（`build-and-verify`・`backend-test-support`）**

- 確かめた事実:
  - CI はジョブ `verify` の1本で、手順は直列。`./gradlew verify` は `-PitForks` を付けずに流す（結合テストは1つの JVM）。
  - 手元の実測の記録（`docs/verification.md` 96〜102 行、2026-11-08、8 CPU・16GiB の PC、colima 4 CPU・6GiB、各 N を1回）: 結合テストは N=1 で 23分05秒、N=2 で 15分26秒、N=3 で 9分43秒、N=4 で 13分53秒。どの N でも 1,716 件が通り、飛ばし 10 件。
  - 画面のテスト（Vitest）は `verify` の中で2回流れる（`frontendTest` と `frontendCoverage`。`build.gradle.kts` 216〜220・466・469〜476 行。前の記録の K-112 と同じ）。
  - ほかに時間がかかりうる手順: 0 の段の make-you-chic-ui の `npm ci` とビルド、Gitleaks の履歴全体（`fetch-depth: 0`）、SpotBugs、OSV-Scanner、9 の手順の `program-size.mjs --base`（比べる元を `git worktree add` で取り出して cloc をもう1回流す。`program-size.mjs` 250〜286 行）。
  - CI の手順ごとの時間はリポジトリの中に無い（GitHub の実行の記録にある）。
- 見立て（未検証）: 一番大きいのは結合テストの1つの JVM の時間と見る（手元で N=1 が 23 分）。CI の機械の CPU・メモリ（GitHub の公開のリポジトリの標準の機械）で `-PitForks` が効くか、コンテナと番号の取り合いが出ないかは流して確かめる必要がある。ジョブを分ける（結合テストを別のジョブにする）と、カバレッジの判定（`test.exec` と `integrationTest.exec` を合わせる）と要約の台本（同じ機械の `backend/build/` を読む）を作り直す必要がある。内訳は `gh run view --json jobs` の手順ごとの時刻で測れる見込み。

**K-130 ルートの `scripts/` は 4 ファイルになり、まだ検査の対象の外（`build-and-verify`）**

- 確かめた事実:
  - `scripts/` は `ci-summary.mjs`（445 行）・`program-size.mjs`（533 行）・`verify-graph-check.mjs`（238 行）・`verify-graph-baseline.txt`（63 行）。前の記録の K-121 は2ファイルの時点のもの。
  - 3つの `.mjs` はどれも先頭に `/* ... */` の Apache License 2.0 のヘッダー（1〜15 行）、`node:` の組み込みだけを使う（npm の依存なし）。`program-size.mjs` は直接流したときだけ `main()` を呼ぶ（518〜533 行）。`ci-summary.mjs` は読み込むと `main()` が流れる（439〜445 行）。`verify-graph-check.mjs` は読み込むと `./gradlew` を流す。
  - 検査の対象: `check-license-header.mjs` は `frontend/`（`.ts`・`.tsx`・`.js`・`.mjs`・`.cjs`・`.css`・`.html`・`.svg`）と `perf/`（`.mjs`・`.js`）だけを集める（39〜41・105〜108 行）。書式とリンタは `frontend/` の中（`frontend/package.json` 15〜16 行）と perf の伏せ字の道具の1ファイル（`build.gradle.kts` 229 行の `perfRedactorTool`）。pre-commit の Prettier は `^frontend/` だけ（`.pre-commit-config.yaml`）。画面のカバレッジの `include` は `src/**/*.{ts,tsx}` と perf の伏せ字の道具（`frontend/vitest.config.ts` 69 行）。
  - `docs/verification.md` 70 行は「ライセンスヘッダーの検査の対象に `scripts/` を足して検査に載せるかは、後の Intent で決める」、176 行は「ルートの `scripts/` の台本（`program-size.mjs`・`ci-summary.mjs` を含む）は、CI の報告の道具であり本体の機能ではないため、単体テスト・ライセンスヘッダー・書式・リンタの検査の対象の外です（Intent 261108-ci-size-coverage での依頼者の決定）」と書く。今回の Intent はこの決定を見直す。
- 見立て（未検証）: 足し方の候補は、(a) `check-license-header.mjs` の集める置き場に `scripts/` を足す（`perf/` と同じ拡張子の扱い。`verify-graph-baseline.txt` は対象外の拡張子）、(b) 書式・リンタは perf の伏せ字の道具と同じ形の Gradle のタスク（`frontend/node_modules/.bin/` の道具に `frontend/` の設定を渡す）か、`frontend/eslint.config.js` に `scripts/**` の Node の決まりを足す形、(c) 型の検査は `frontend/tsconfig.perf-tool.json` と同じ形。`verify-graph-check.mjs` は `./gradlew` を流す台本のため、検査に「入れる」のがライセンスヘッダー・書式・リンタだけか、`verify` の中で流すことまで含むかを分けて決める必要がある（`verify` の中で `./gradlew --dry-run` を流すと入れ子の Gradle になる）。単体テストを付けるなら、`ci-summary.mjs` の読み込み時の `main()` を直接流したときだけにする変更が要る。今のヘッダー・書式で検査が通るかは流していない。

**K-131 R-07: `packagesJudgedByTotal` の確かめのコマンドは一覧を手で書いている（`build-and-verify`）**

- 確かめた事実:
  - `docs/verification.md` 78 行: `git diff --name-only develop... -- backend/src/main | grep -E 'cherry/mastersmith/(access/service|audit/repository|common/error/domain|common/error/service|common/health|common/i18n/domain|common/web)/[^/]+$'`。81 行: 「この一覧は `backend/build.gradle.kts` の `packagesJudgedByTotal` と同じ 7 パッケージです（2026-11-08 の時点）。一覧から外れたパッケージがあれば、このコマンドからも外します」。
  - 一覧の持ち主は `backend/build.gradle.kts` 313〜321 行（`val packagesJudgedByTotal = listOf("cherry.mastersmith.access.service", …)`）。`team.md` の Testing Posture にも 7 パッケージの名前がある。
  - 一覧をファイルから読む前例: `scripts/ci-summary.mjs` の `parseGradleFloors`（162〜182 行）が `backend/build.gradle.kts` を読み、正規表現 `val\s+packagesJudgedByTotal\s*=\s*listOf\(([\s\S]*?)\)` と `"([^"]+)"` で名前の集まりを作る。
  - 正規表現の `[^/]+$` は、パッケージの直下のファイルだけに当てる（下位パッケージは当てない）。
- 見立て（未検証）: 直し方の候補は、(a) コマンドの中で `sed`・`grep` で `backend/build.gradle.kts` から名前を取り出し、`.` を `/` に替えて正規表現を組み立てる（zsh・bash の両方で動く形にする。`project.md` の「zsh ではコマンドを変数に入れても単語に分かれない」）、(b) `scripts/` に小さな台本を置き、`ci-summary.mjs` と同じ読み方で確かめる。一覧が空になったとき（K-135）に「何も出ない（当たらない）」と正しく出るかも確かめる。

**K-132 `packagesJudgedByTotal` の 7 パッケージの本体とテストの対応（`access`・`audit`・`common-error`・`common-health`・`common-i18n`・`common-web`）**

- 確かめた事実（`src/main` の行の数は `wc -l`、ヘッダーと Javadoc を含む）:

| パッケージ | 本体（`src/main`） | テスト（`src/test`） | ソースから読める薄い所（見立て。カバレッジは測っていない） |
|---|---|---|---|
| `access.service` | `AccessDeniedEventPublisher`（60）・`AccessProblemTypeCatalog`（32）・`package-info` | `AccessDeniedEventPublisherTest`（5 件: 1 回の発行・受け取り側の例外を伝えない・WARN は1回で型だけ・メッセージとスタックを出さない・成功で WARN なし）・`AccessProblemTypeCatalogTest`（2 件） | 本体の分岐（`try`・`catch`）は単体テストで両方を通る見込み。薄い所は読み取れない |
| `audit.repository` | `AuditEventRepository`（57。Spring Data の `Repository` を継ぐインターフェースだけ）・`package-info` | `AuditEventRepositoryIT`（4 件: 追記と読み戻し・時刻の順・挿入1回だけ・更新しない） | 実行できるコードが無い（K-134） |
| `common.error.domain` | `BusinessException`（104）・`CommonProblemTypes`（121、定数と `all()`）・`LocalizedText`（48）・`ProblemType`（78）・`ProblemTypeCatalog`（33、インターフェース） | `ProblemTypeTest`（6 件と jqwik の性質1つ）。`CommonProblemTypes.all()` は `ProblemTypeRegistryTest` の 96 行の件でも通る | `LocalizedText` の `en == null`（`en` は空白の `" "` だけを確かめる。74 行）、`BusinessException` の `properties` が `null` のとき、3 引数の口に値を渡すとき |
| `common.error.service` | `CommonProblemTypeCatalog`（32）・`ProblemTypeRegistry`（104） | `ProblemTypeRegistryTest`（7 件）・`ProblemTypeDuplicateStartupIT`（3 件） | 辿れない分岐（K-133） |
| `common.health` | `TimeBoundedDbHealthIndicator`（142）・`HealthProperties`（28。`*Properties` で計測の外） | `TimeBoundedDbHealthIndicatorTest`（6 件: UP・接続の失敗・問い合わせの失敗・借りる待ちで時間切れ・問い合わせの時間切れ・前の確かめが走っている間）・`HealthEndpointIT`（5 件） | `executor.submit` が断られたとき（`submit-failed` の WARN、94 行の `catch`）、`InterruptedException`（`Thread.currentThread().interrupt()` の枝）、`destroy()`（`shutdownNow`）を確かめるテストが見当たらない |
| `common.i18n.domain` | `AcceptLanguageResolver`（89）・`DisplayLanguage`（39） | `AcceptLanguageResolverTest`（4 件と jqwik の性質2つ） | 50 件を超える見出し（`MAX_ENTRIES`）、`Q=` の大文字、`q=` 以外の引数、`*` の扱いを名指しで確かめる件は見当たらない（性質のテストが偶然通る可能性はある） |
| `common.web` | `CacheControlFilter`（96）・`MastersmithWebProperties`（33、計測の外）・`RequestBodyLimitRoute`（58）・`RequestSizeLimitFilter`（247）・`RequestSizeRejection`（44）・`RequestSizeRejectionListener`（36、インターフェース） | `CacheControlFilterTest`（5 件）・`RequestSizeLimitFilterTest`（6 件）・`RequestSizeLimitRoutesTest`（5 件。受け取り側の例外は 63 行で確かめる） | `BufferedBodyRequest` の `ServletInputStream` の `isFinished`・`isReady`・`setReadListener`（例外）・`read()`（1 バイト）、`getReader` で文字コードを指定したとき、`Content-Length` が無く `Transfer-Encoding` も無いとき、`RequestSizeRejection` の `null` の検査を名指しで確かめる件は見当たらない |

- `access` と `audit` には機能の境界テスト（`AccessBoundaryArchitectureTest`・`AuditBoundaryArchitectureTest`）がある。`common` には機能の境界テストが無い（`*BoundaryArchitectureTest.java` 16 ファイルの一覧に無い）。`common` は機能ではなく共通部品のため、`team.md` の「手を入れる Bolt で境界テストが無ければ足す」が `common` に当たるかは決まりの読み方による（見立て）。
- 計測の外の型（`*Properties`）は `backend/build.gradle.kts` 213〜217 行の `coverageExclusions`。

**K-133 `ProblemTypeRegistry` の辿れない分岐（`common-error`）**

- 確かめた事実:
  - `ProblemType` の `code` は `^[A-Z][A-Z0-9_]*$` に限られ（`ProblemType.java`）、`slug` は `code` を小文字にして `_` を `-` に替えたもの（`toSlug`）。この変換は1対1のため、`code` が重ならずに `slug` だけが重なることは起きない。
  - `ProblemTypeRegistry` のコンストラクターは `if (!duplicateCodes.isEmpty() || !duplicateSlugs.isEmpty())` と、その中の `if (!duplicateSlugs.isEmpty())` を持つ。前者の右側が真になるのは左側が偽（`code` の重なりなし）のときだけで、そのとき `slug` の重なりも無い。後者が偽になるのは `code` が重なって `slug` が重ならないときだけで、これも起きない。
  - `ProblemTypeRegistryTest` 85〜93 行の「slug の重なり」の件は、同じ `code`（`A_B`）を2つ渡しており、`code` と `slug` の両方が重なる場合を確かめている。
- 見立て（未検証）: このパッケージの分岐のカバレッジには、テストを足しても届かない分岐が少なくとも2つ残る。パッケージの分岐の下限 70% に届くかは、全体の分岐の数（ほかの分岐がすべて通るか）による。届かないときに `src/main` を直す（辿れない条件を消す）と、`team.md` の「`packagesJudgedByTotal` のパッケージに手を入れる Bolt」に当たり、全件の `verify` と、そのパッケージを一覧から外す義務が生じる（外すのは今回の目的と同じ向き）。

**K-134 `audit.repository` は実行できるコードを持たない（`audit`）**

- 確かめた事実: `AuditEventRepository` は Spring Data の `Repository<AuditEvent, Long>` を継ぐインターフェースで、`save`・`findById`・`findAllByOrderByOccurredAtAsc` を宣言するだけ。`default` のメソッドは無い。ほかは `package-info.java` だけ。
- 見立て（未検証）: JaCoCo はコードの無いインターフェースを数えず、このパッケージは報告に現れないか、数が 0 になる見込み。その場合、一覧から外してもパッケージごとの下限で落ちない。`jacocoTestReport.xml` にこのパッケージの `counter` があるかを、要件の段の実測で確かめる。

**K-135 一覧を空にしたときの書き方と、一覧を読む台本（`build-and-verify`）**

- 確かめた事実:
  - `packagesJudgedByTotal` は `listOf(…)` で書かれ、`jacocoTestCoverageVerification` の2つ目の `rule` の `excludes = packagesJudgedByTotal` に渡る（`backend/build.gradle.kts` 313〜357 行）。`team.md` は「一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない」とする。
  - `scripts/ci-summary.mjs` の `parseGradleFloors` は `val\s+packagesJudgedByTotal\s*=\s*listOf\(` に当たらなければ例外を投げ、要約は下限の列を「—」にして警告を出す（204〜210 行。CI は失敗しない）。
  - 前の Intent の回帰の台本（`verify-graph-check.mjs`）は `packagesJudgedByTotal` を読まない。
- 見立て（未検証）: 7 つすべてを外すと、Kotlin では型を書いた `listOf<String>()` か `emptyList<String>()` にするか、一覧と `excludes` の行そのものを消す形になる。どの形でも `ci-summary.mjs` の正規表現は当たらなくなり（`listOf<String>(` は `listOf\(` に当たらない）、要約の下限の列が「—」になる。一覧を消すか残すか、`ci-summary.mjs` と R-07 の確かめ（K-131）をどの形に合わせるかを要件で決める。一部だけを外すなら今の書き方のまま読める。

**K-136 リリース（`develop` から `main`）に関わる今の形（`build-and-verify`）**

- 確かめた事実:
  - README にはリリースの手順が無い（`grep`。「前提の道具」24 行に「リリースの前などで流す」とあるだけ）。リリースの前の確かめは `docs/verification.md` 178〜198 行（`./gradlew verify` と E2E に加えて、`./gradlew osvScan --rerun` と、`frontend/dist`・WAR に axe-core が入っていないことの `grep`。結果は Deployment Execution の記録に書く）。fast-forward で取り込むこと・依頼者の承認・push は依頼者が行うことは `team.md` の Way of Working と Deployment にある。
  - CI のきっかけに `main` は無い（`ci.yml` 20〜24 行）。`main` へ push しても CI は流れない。ジョブ `release` は `v*` のタグのときだけで、`team.md` はタグを配備先が決まるまで付けないとする。
  - 2026-11-09 の時点で `git merge-base --is-ancestor main develop` は真（fast-forward で取り込める）。`main..develop` は 23 コミット。`main` の先頭は `b2990415`。
  - `program-size.mjs` の `collectMeta` は `origin/main` の先頭を報告に載せる（356 行〜）。
- 見立て: リリースの前の確かめ（全件の `verify`・E2E・`osvScan --rerun`・axe-core の確かめ・`main` への取り込みの前の CI の試行ごとの確かめ（`project.md` の Deployment））は、今回の Intent の最後の段で行う。配備の段を飛ばすかは範囲を決めるときに決める（`project.md` の Deployment の「配備に響かない Intent」）。今回の変更（CI・台本・テスト・文書）は WAR の中身を変えない見込みだが、Bolt 4 で `src/main` を変えると WAR が変わる。

**K-137 自動化 A1〜A3 に関わるリポジトリの中のもの（`build-and-verify`・`container-runtime`）**

- 確かめた事実:
  - A1〜A3 の今の道具はリポジトリの外（ホームの下）にあり、読んでいない（依頼による）。
  - リポジトリの中で関わるもの: A2（`main` への取り込みの前の CI の試行ごとの確かめ）は `project.md` の Deployment の「`gh run view --json attempt,jobs` で試行ごとに確かめる」決まりがあり、リポジトリの中に台本は無い。A3（内部DB の複写を数える）は README の目次（109 行）が `docs/internal-db.md` に「内部DBの複写を数える」の節があるとする（`docs/internal-db.md` は今回読んでいない）。A1（振り返りの段の読み取りの確かめ）に当たる台本はリポジトリの中に見当たらない（`scripts/` の4ファイルのどれでもない）。
- 見立て: 台本をリポジトリに置くなら、ルートの `scripts/` が置き場の候補で、K-130 の検査の対象の決定がそのまま効く。A3 の手順は `ops-notes.md`（チームの知識）の「リポジトリの外の小さな Java の道具（H2 の JDBC、`ACCESS_MODE_DATA=r`）で値を表示せずに数える」決まりがあり、Node の台本にするか Java の道具のままかで、ライセンスヘッダー・検査の対象が変わる。

## Handoff Summary

- **Intent-relevant finding**: 6 つの Bolt のどれにも、前の知識ベースの CI の記述（K-119 の 7 手順）は古い（今は 10 手順。`ci.yml` 96〜120 行。K-127）。Bolt 1 の R-07 は `docs/verification.md` 78 行の手書きの正規表現で、`scripts/ci-summary.mjs` 162〜182 行に `backend/build.gradle.kts` から一覧を読む前例がある（K-131）。ルートの `scripts/` は今 4 ファイルで、`docs/verification.md` 176 行に前の Intent での依頼者の「検査の対象の外」の決定が書かれている（K-130）。Bolt 4 の 7 パッケージのうち、`audit.repository` はコードの無いインターフェース（K-134）、`common.error.service` には辿れない分岐が2つある（K-133）。
- **Risks / follow-up**: (1) `scripts/` を検査に入れることは前の Intent の依頼者の決定（`docs/verification.md` 176 行）を変えるため、要件で明示に決める。(2) `packagesJudgedByTotal` を空にすると `ci-summary.mjs` の正規表現が当たらなくなる（K-135）。(3) `common.error.service` の分岐の下限に届かないときは `src/main` の変更になり、`team.md` の全件の `verify` と境界テストの決まりが当たる（K-133）。(4) CI の時間と 7 パッケージのカバレッジの値は今回測っていない。要件の段で `gh run view --json jobs` と `:backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport`（`project.md` の Testing Posture）で測る。(5) `ubuntu-latest` の Ubuntu の版の移りと cloc の版は流して確かめていない（K-128）。
