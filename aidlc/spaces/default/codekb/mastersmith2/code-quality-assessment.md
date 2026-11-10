# コードの品質（mastersmith2）

> 2026-11-11（記録のコミット `7f36d8a`）の Intent `261110-deps-forks-review` の Focused scan で、カバレッジの今の形（K-142。一覧 `packagesJudgedByTotal` は消え、すべてのパッケージにパッケージごとの下限が当たる）を「カバレッジ」の頭に足し、前の記録の一覧を今の仕組みとして書く行には撤去の前の記録と注した。古くなっている記述の一覧（K-143）を「CI と依存」に、今回の範囲を「技術的負債」に足した。
>
> 前回の記録: 2026-11-09（記録のコミット `4fe8e79`）の Intent `261108-ci-checks-release` の Focused scan で、ルートの `scripts/` の今の形と検査の対象（K-130）、R-07 の確かめのコマンド（K-131）、`packagesJudgedByTotal` の 7 パッケージの本体とテストの対応と薄い所（K-132）、`ProblemTypeRegistry` の辿れない分岐（K-133）、一覧を空にするときの書き方（K-135）、自動化 A1〜A3 に関わるもの（K-137）を足し、「CI と依存」「技術的負債」「ドキュメント」の節に今回の読みを足した。テストの件数・カバレッジの値・時間は取っていない（`./gradlew` を流していない）。ほかの節は前回以前の記録による。
>
> 前回の記録: 2026-11-08 の2回目（記録のコミット `9fd4f7f`）の Intent `261108-ci-size-coverage` の Focused scan で、カバレッジの報告の置き場と形（K-120）、ルートの `scripts/` の台本の形と検査の対象（K-121）、`docs/verification.md` の構成（K-125）を足し、「CI と依存」「技術的負債」「ドキュメント」の節に今回の読みを足した。テストの件数とカバレッジの実測の値、時間は取っていない（`./gradlew` を流していない）。ほかの節は前回以前の記録による。
>
> 前回の記録: 2026-11-08（記録のコミット `16f46f1`）の Intent `261107-verify-quick-local` の Focused scan で、1コマンドの検査の中の画面のテストの回数（K-112）、カバレッジの判定と結合テストの関わり（K-114）、CI の呼び方（K-115）、書き換える決まりと文書の箇所（K-117）を足し、「テスト」「CI と依存」の節を今の値に直した（Node 26・OSV-Scanner 2.7.0。K-118）。所見の本文はこの文書（K-112・K-114・K-115・K-117）と `architecture.md`（K-111・K-113・K-116）。テストの件数とカバレッジの実測の値、時間は取っていない（`./gradlew` を流していない）。ほかの節は前回以前の記録による。
>
> 前回の記録: 2026-11-06（記録のコミット `a789a6f`）の Intent `261104-tz-options-perf` の Focused scan で、表示のタイムゾーンの選択肢の性能のテストの測り方とカバレッジの設定（K-108）、直し方の候補と制約（K-109）、`frontend/vitest.config.ts` の今の形（K-110 の K-90 の訂正）を足した。所見の本文はこの文書（K-108・K-109）と `architecture.md`（K-106・K-107）。ほかの節は前回以前の記録による。
>
> 前回の記録: 2026-11-03（記録のコミット `fa0b01e`）の Intent `261102-timezone-ui-polish` の Focused scan で、今回の範囲（日時の型・接続の時差・サイドバーのアイコン・ホーム・管理の仮の画面・ページの頭）の技術的負債と、それを確かめているテストの置き場（ファイル名の grep）を足した。所見の本文は `architecture.md`（K-94〜K-99）と `code-structure.md`（K-100・K-102〜K-104）。テストの件数とカバレッジの実測の値は取っていない。
>
> 前回の記録: 2026-11-01（記録のコミット `6079f13`）の Intent `261101-maintenance-update` の Focused scan で更新した。今回は `build.gradle.kts`・`backend/build.gradle.kts`・`.github/workflows/ci.yml`・`README.md`・`perf/README.md` の構成・perf の伏せ字の道具・`businessdata` の一部を読み、所見 K-88〜K-91 を足した（本文はこの文書）。テストの件数とカバレッジの実測の値は取っていない（`./gradlew` を流していない）。
>
> 前回の記録: 2026-10-29（記録のコミット `15a25aa`）の Intent `261029-reference-lookup-cleanup` の Focused scan で更新した。今回の範囲の事実（`packagesJudgedByTotal`、境界テストの許可の一覧、SpotBugs の除外の形、perf の JWT）を読み直し、所見 K-77・K-80 を足した（本文はこの文書）。K-67〜K-73 は前回（`261027-perf-targets-review`）の記述のまま（今回は `perf/README.md` と `scenarios.js` の一部だけ読み、行数と構成を更新した）。テストの件数とカバレッジの実測の値は取っていない（`./gradlew` を流していない）。

## テスト

- 置き場: `backend/src/test/java/cherry/mastersmith/`（機能ごと、単体 `XxxTest`・結合 `XxxIT`、機能ごとの `testsupport/`）、`frontend/src/**/*.test.ts(x)`（対象と同じ場所）、`frontend/e2e/`（`*.e2e.ts` 22 本（010〜195、2026-11-01 に数えた）、`./gradlew e2eTest`、`verify` と CI の外）。
- 数（2026-11-08、`find`）: `*Test.java` 312・`*IT.java` 278。単体は `:backend:test`（`includeTestsMatching("*Test")`）、結合は `:backend:integrationTest`（`includeTestsMatching("*IT")`）で、同じ `src/test/java` から名前で分ける（`backend/build.gradle.kts` 135〜156 行）。結合テストの前提と並列の設定は `architecture.md` の K-113。
- 道具: JUnit 5・Spring Boot Test・Testcontainers（MySQL・MariaDB・PostgreSQL）・jqwik・ArchUnit／Vitest・Testing Library・user-event・vitest-axe・fast-check／Playwright と axe-core。
- 境界の構造の検査（ArchUnit）は 19 ファイル（`find` で数えた）: `ArchitectureTest`・`ApiAccessArchitectureTest` と、`access`・`appearance`・`audit`・`auth`・`businessdata`・`dsl`・`dslmanage`（2つ）・`group`・`invitation`・`mail`・`navigation`・`role`・`targetdb`（`TargetDbBoundaryArchitectureTest` と `ExtensionOrderArchitectureTest`）・`user`・`useradmin`。`businessdata` の境界テストの `@DisplayName` から読める規則: 読み取り側はトランザクションと `JdbcTemplate` を使わない、`RowSqlExecutor` だけが JDBC の接続から文を作る、`businessdata.repository` は `targetdb.service` に依存しない、`businessdata` に依存してよいのは `audit` の `domain` だけ。
- `businessdata` のテストは `domain`・`repository`（`RowSqlExecutorTest`・`RowDialectTest`・`RowSqlFailureClassifierTest` など）・`service`・`testsupport`（`CountingDataSource` など）に分かれる（ファイル名の一覧のみ。中身は読んでいない）。

## カバレッジ

**K-142 今の形: すべてのパッケージにパッケージごとの下限が当たる（2026-11-11）（`build-and-verify`）**

- 確かめた事実: バックエンドは JaCoCo。`backend/build.gradle.kts` の `jacocoTestCoverageVerification`（305〜338 行）は `rule` が2つで、1つ目が全体の合計、2つ目が `element = "PACKAGE"`。どちらも `LINE` の `COVEREDRATIO` 0.80・`BRANCH` の `COVEREDRATIO` 0.70。**2つ目に `excludes` は無く、すべてのパッケージに当たる（外すパッケージは無い）**。全体の合計で判定していた一覧 `packagesJudgedByTotal` は、Intent `261108-ci-checks-release` で `common.health` にテストを足して下限を満たし、一覧と除外の仕組みごと消えた（コミット `67f6f5f2`。298〜304 行のコメントに経緯だけが残る）。計測から外すのは `MastersmithApplication*`・`*Properties.class`・`*Properties$*.class`（213〜217 行）だけ。`scripts/ci-summary.mjs` の `parseGradleFloors`（174〜196 行）は2つの `rule` の下限だけを読み、一覧を読まない。`docs/verification.md` 22 行も「外すパッケージは無い」。
- この節の下の、`packagesJudgedByTotal` を今の仕組みとして書く行（2026-10-29〜2026-11-09 の記録、K-114・K-120・K-131・K-132・K-135 を含む）は、撤去の前の状態の記録として残す。行の番号もその時点のもの。

以下は前回以前の記録:

- バックエンドは JaCoCo。全体とパッケージごとに行 80%・分岐 70%。（2026-11-09 までの記録）全体の合計で判定する既存のパッケージ `packagesJudgedByTotal` は 7 つ（`backend/build.gradle.kts` 213 行〜の `packagesJudgedByTotal`: `access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`。`team.md` の記録と一致する。2026-11-08 にも同じ 7 つを確かめた）。`businessdata`・`targetdb` はこの一覧に無く、パッケージごとの下限が当たる。
- 今回（2026-10-29）も `packagesJudgedByTotal`（`backend/build.gradle.kts` 213〜221 行）は 7 パッケージで `team.md` と一致することを確かめた。`businessdata`・`dsl`・`role`・`targetdb` は一覧に無く、この Intent で手を入れても一覧は増減しない見込み（`common.web` などに手を入れる場合は `team.md` の外す義務が発生する）。
- 全体の下限（行 80%・分岐 70%）とパッケージごとの下限の規則は `backend/build.gradle.kts` 233〜253 行付近（`rule` が2つ。2つ目は `element = PACKAGE`・`excludes = packagesJudgedByTotal`）。
- 画面は `frontend/vitest.config.ts` の `thresholds`（前回の記録による。今回は読んでいない）（`lines: 80`・`branches: 70`、65〜68 行）。
- 2026-11-06 に読み直した `frontend/vitest.config.ts`（80 行）: `coverage` は `provider: 'v8'`・`allowExternal: true`・`include` は `src/**/*.{ts,tsx}` と `../perf/redact-k6-evidence.mjs`・`exclude` は `src/main.tsx`・`src/**/*.d.ts`・`src/**/*.test.{ts,tsx}`・`reporter` は `text`・`html`・`json-summary`・`thresholds` は `lines: 80`・`branches: 70`（66〜77 行）。`projects` は `eslint-config`（ESLint の設定を読む2本を先に1つだけで流す、`groupOrder: 0`）と `unit`（`groupOrder: 1`）。並列の制限（`pool`・`maxWorkers`・`fileParallelism`）は無い。K-90 の「`coverage.include` は `src/` だけ」は前の Intent の T1 で直っている（K-110）。2026-11-08 に読み直し、変わっていない（80 行）。

**K-112 画面のテスト（Vitest）が `verify` の中で2回流れる（2026-11-08）（`build-and-verify`・`frontend-feature-preferences`・`frontend-shared-format`）**

- 確かめた事実:
  - `build.gradle.kts` の `npmScript`（173〜181 行）が npm のスクリプトを呼ぶ Gradle のタスクを作る。`frontendTest`（216 行）は `npm run test`、`frontendCoverage`（217〜220 行）は `npm run test:coverage`。どちらも `frontendInstall` と `vendorBuild` に依る。
  - `frontend/package.json`（22〜23 行）: `test` は `NODE_OPTIONS=--no-experimental-webstorage vitest run`、`test:coverage` は同じに `--coverage` を足したもの。対象のテストは同じ（`vitest.config.ts` の projects 2 つ。`include` に差は無い）。
  - `verify` の段では `frontendTest` が 5 の段（`verifyUnitTest`）、`frontendCoverage` が 7 の段（`verifyCoverage`）にある（`build.gradle.kts` 466・469〜476 行）。画面の全件のテストが計測なしと計測つきで2回流れる。
  - 時間を測って目標を超えたら失敗にするテスト2つ（`frontend/src/shared/format/timeZone/conversionPerformance.test.ts`・`frontend/src/features/preferences/TimeZoneOptionsPerformance.test.ts`）も2回判定される（`docs/verification.md` 28 行が「カバレッジなし（5 の段）とカバレッジつき（7 の段の `frontendCoverage`）の2回流れ、どちらでも判定します」と書く。流し読み）。
  - npm のタスクは入力・出力を宣言しないため（`frontendBuild` などを除く）、Gradle の UP-TO-DATE で飛ばされず毎回流れる。
- 見立て（未検証）: 1回にするなら `frontendCoverage` だけを残す形が自然で、性能のテストは計測つきの1回だけで判定されることになる（前の Intent で CI の失敗が出たのも計測つきの回。K-108）。どの段に置くかは K-116 の順の決まりと合わせて決める。時間がどれだけ減るかは測っていない。

**K-114 バックエンドのカバレッジの判定は結合テストの実行記録に依る（2026-11-08）（`build-and-verify`）**

- 確かめた事実:
  - `coverageExecutionData`（`backend/build.gradle.kts` 175〜177 行）は `build/jacoco` の `test.exec` と `integrationTest.exec` を `fileTree` の `include` で拾う。jacoco のプラグインはすべての `Test` のタスクに計測を付けるため、`integrationTest` も `integrationTest.exec` を書く。
  - `jacocoTestReport`（187〜196 行）と `jacocoTestCoverageVerification`（223〜257 行）は、テストのタスクに `dependsOn` せず `mustRunAfter(tasks.test, integrationTest …)` だけを持つ。`verify` の中では 7 の段の要素として呼ばれ、5・6 の段の後に流れる。
  - 判定の規則は全体と `element = "PACKAGE"`（`excludes = packagesJudgedByTotal`）の2つで、どちらも行 0.80・分岐 0.70。（2026-11-11 の注: 今は `excludes` が無い。K-142）
  - （2026-11-08 の当時。今の `team.md` にこの決まりは無い。K-142）`team.md` の Testing Posture は、`packagesJudgedByTotal` のパッケージに手を入れる Bolt で、`:backend:cleanTest :backend:cleanIntegrationTest` を付けた `./gradlew verify` でカバレッジを実測して記録する決まりを持つ。`project.md` の Testing Posture は、Delivery Planning の実測を `:backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport` で行う決まりを持つ。
- 見立て（未検証）: `integrationTest` を流さずに判定すると、`fileTree` が無いファイルを黙って外すため報告は作れるが、IT でしか通らない層（`web` など）のパッケージは下限を下回り、`jacocoTestCoverageVerification` が落ちる見込み。`build/jacoco/integrationTest.exec` が前の回のまま残っていると、古い実行記録が混ざって判定・参考値がずれる（どちらも流して確かめていない）。

**K-115 CI は `./gradlew verify` を引数なしで1本流す（2026-11-08）（`build-and-verify`）**

- 確かめた事実（`.github/workflows/ci.yml` の全文）:
  - きっかけは `develop` への push・`v*` のタグ・手動（`workflow_dispatch`）。`concurrency` は ref ごとで取り消さない。
  - ジョブ `verify`（`ubuntu-latest`・`timeout-minutes: 60`）: サブモジュールごと全履歴を取得（`fetch-depth: 0`）→ JDK 25（Temurin）→ Node 26（npm のキャッシュは2つの lockfile）→ `gradle/actions/setup-gradle` v6.4.1 → Gitleaks 8.30.1 と OSV-Scanner 2.7.0 を版と SHA-256 で確かめて入れる → `./gradlew verify`（83〜84 行、`-P` の引数なし）→ WAR を 30 日保存。
  - ジョブ `release`（タグのとき）は検査を通った WAR を GitHub のリリースに添付する。
  - GitHub Actions は環境変数 `CI=true` を設定するため、対象DB の IT は Docker が無ければ失敗する（`ContainerRuntimeCheck`。`architecture.md` の K-113）。
- 今回の Intent への意味: CI を今までどおり `verify` の1本にするなら、`ci.yml` は変えずに済む見込み。`-PitForks` のような手元だけの口は、`-P` を渡さない CI では既定（並べない）になる形にする。`team.md` の「`verify` の時間と CI の時間を測って記録する（CI の上限の7割の約 42 分）」は、CI が `verify` のままなら物差しが変わらない。

**K-117 書き換える決まりの文と、手順を直す文書の箇所（2026-11-08）（`build-and-verify`）**

- 決まり（`aidlc/spaces/default/memory/`。どれも今の文は「統合の前に全件の `verify`」を前提にしている）:
  - `project.md` の Mandated: 「ALWAYS 統合前に、フォーマット・リンタ・ビルド・全テスト・秘密情報の検出・依存関係の脆弱性検査（重大度 High 以上）が通っていることを確認する」。
  - `project.md` の Way of Working: 「ローカルの1コマンドの検査は Gradle の1つのタスク（./gradlew verify）を入口にし…CI も同じタスクを呼ぶ」。
  - `project.md` の Testing Posture: `cleanTest`・`cleanIntegrationTest` を付けて実測する行、Delivery Planning の実測の行、colima の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` の行。
  - `team.md` の Way of Working: 「プルリクエストは使わない。統合の前に、CI と同じ検査（…全テスト・カバレッジ下限…）をローカルで1コマンドで実行し、すべて通ってから統合する」、「E2E は…統合は `./gradlew verify` で行う」、「Dependabot …1コマンドの検査（`./gradlew verify`）を通してから」、「統合前の1コマンドの検査は、コンテナの実行環境（colima）が動いていることを前提にする…飛ばした状態では統合しない」。
  - `team.md` の Testing Posture: 「対象DB（MySQL・MariaDB・PostgreSQL）の結合テストは、3種類すべてを `./gradlew verify` の中で毎回実行する（CI も同じ）」、`packagesJudgedByTotal` の行（`./gradlew verify` で実測。2026-11-08 の当時の文で、今は消えた。K-142）、「テストを足した Bolt の後と Build and Test で、`./gradlew verify` の時間と CI の時間を測って記録する」。
- 手順の文書（読み取りの grep）:
  - `README.md`: 15・16 行（Gitleaks・OSV-Scanner の使う場面）・18 行（Docker）・22 行（道具が無いと失敗）・24 行（colima が前提、対象DB の IT を毎回）・44 行（0 の段のサブモジュールの確かめ）・60・65 行（配備の WAR は `verify` を通して作る）。
  - `docs/verification.md`（範囲の外、流し読み）: 5〜33 行の「1コマンドの検査」の節（段の表 13〜24 行、28 行の「2回流れ」、33 行の段ごとの実行と報告の置き場）。
- 見立て: 決まりの文の書き換えは `team.md` の「Edit at the gate」の扱い（AI-DLC の決まりの書き方）に従い、どこまで書き換えるかは要件定義で決める。

**K-120 カバレッジの報告の置き場と形（2026-11-08 の2回目）（`build-and-verify`）**

- 確かめた事実:
  - バックエンドの `jacocoTestReport`（`backend/build.gradle.kts` 231〜240 行）は `test.exec` と `integrationTest.exec` を合わせ、`xml.required = true`・`html.required = true` で報告を作る。置き場を指定していないため Gradle の既定の場所（`backend/build/reports/jacoco/test/`。`docs/verification.md` 112 行と `backend/build.gradle.kts` 245 行のコメントも同じ）に出る。XML のファイル名は Gradle の既定（`jacocoTestReport.xml`）の見込みで、ファイルは開いていない（git の対象外）。
  - `jacocoQuickReport`（250〜296 行）は `test.exec` だけで `backend/build/reports/jacoco/quick/jacocoQuickReport.xml` と `html/` を作る。XML を `DocumentBuilderFactory` で、外部の DTD・外部の実体を読まない設定（271〜273 行）で読み、根（`report`）の直下の `counter` の `type`・`covered`・`missed` から全体の割合を求めて表示する（275〜294 行。パッケージ・クラスの `counter` は数えない）。全体の値を読む前例になる。
  - 判定（`jacocoTestCoverageVerification`、323〜357 行）は全体とパッケージごと（`packagesJudgedByTotal` の 7 つを除く）に行 0.80・分岐 0.70。報告とは別のタスクで、報告の XML を読まない。（2026-11-11 の注: 今は 305〜338 行で、除くパッケージは無い。K-142）
  - 画面の `frontend/vitest.config.ts`（66〜77 行）: `reporter` は `text`（端末に表）・`html`・`json-summary`、`reportsDirectory` は `coverage`（`frontend/coverage/`）、`thresholds` は `lines: 80`・`branches: 70`。`include` は `src/**/*.{ts,tsx}` と `../perf/redact-k6-evidence.mjs`（`allowExternal: true`）。`json-summary` のファイル名は道具の既定（`coverage-summary.json`）の見込みで、ファイルは開いていない。
  - `verify` では、どちらの報告も 7 の段（`verifyCoverage`）で作られる。`verifyQuick` では `jacocoTestReport` を流さないため `backend/build/reports/jacoco/test/` は作り直されず、画面の報告は作られる。CI は `verify` だけを流す（K-119）。
- 見立て（未検証）: 要約にバックエンドの値を載せるなら、`jacocoTestReport` の XML の根の直下の `counter`（`LINE`・`BRANCH`）を読む形が `jacocoQuickReport` と同じ読み方になる。画面は `json-summary` の全体（`total`）の `lines`・`branches` を読む形になる見込み。下限の判定（パッケージごと）は別のタスクで、要約の値と判定の結果は同じものではない。

**K-121 ルートの `scripts/` の台本の形と、検査の対象の外であること（2026-11-08 の2回目）（`build-and-verify`）**

- 確かめた事実:
  - `scripts/` は `verify-graph-check.mjs`（238 行）と `verify-graph-baseline.txt`（63 行）の2ファイル（`git ls-files scripts/`）。前の Intent `261107-verify-quick-local` のコミット `f067e2c0` で足された。
  - 台本の形: 先頭に Apache License 2.0 の `/* ... */` のヘッダー（1〜15 行）、`//` の日本語の説明と流し方（16〜26 行）、ES modules の `import`（`node:child_process`・`node:fs`・`node:path`・`node:url` だけ。npm の依存は使わない）、リポジトリのルートを `import.meta.url` から求める（32 行）、結果を PASS・FAIL で出して終了コードで知らせる（236〜238 行）。`frontend/scripts/` の台本（`check-license-header.mjs` ほか）も同じ形で、関数を `export` して `frontend/src/` のテストから読み、`main()` は直接流したときだけ呼ぶ（`check-license-header.mjs` 125〜127 行）。`scripts/verify-graph-check.mjs` は関数を `export` せず、読み込むとすぐ流れる形。
  - 検査の対象: ライセンスヘッダーの検査（`frontend/scripts/check-license-header.mjs` 105〜108 行）は `frontend/` と `perf/` だけを集める。Prettier・oxlint・ESLint・Stylelint は `frontend/` の中で流れる（`frontend/package.json` 15〜17 行）。`frontend/` の外のファイルは、perf の伏せ字の道具の1ファイルだけを別のタスク（`build.gradle.kts` 226〜283 行）で書式・リンタ・型の検査にかける。画面のカバレッジの `include` は `src/` と perf の伏せ字の道具だけ（`frontend/vitest.config.ts` 69 行）。そのため `scripts/` の台本は、ライセンスヘッダー・書式・リンタ・型・テスト・カバレッジのどれの対象でもない。
  - `docs/verification.md` 70 行: 「この台本は…`verify` にも CI にも入っていません（ライセンスヘッダーの検査の対象に `scripts/` を足して検査に載せるかは、後の Intent で決める）」。
- 今回の Intent への意味（見立て）: 予定の `scripts/program-size.mjs` は CI で毎回流す台本で、手で流す `verify-graph-check.mjs` と性質が違う。`scripts/` に置くなら、ライセンスヘッダーの検査の対象に `scripts/` を足すか（`docs/verification.md` 70 行の持ち越しの決定）、書式・リンタを perf の道具と同じ形のタスクで当てるか、テストをどこに置くか（`frontend/src/` の直下の前例。`coverage.include` に足すのは計測を広げる向き）を要件で決める。`frontend/scripts/` に置けば、ライセンスヘッダー・書式・リンタの対象に入る（カバレッジの `include` には入らない）。

**K-130 ルートの `scripts/` は 4 ファイルになり、まだ検査の対象の外（2026-11-09）（`build-and-verify`）**

- 確かめた事実:
  - `scripts/` は `ci-summary.mjs`（445 行）・`program-size.mjs`（533 行）・`verify-graph-check.mjs`（238 行）・`verify-graph-baseline.txt`（63 行）。上の K-121 は2ファイルの時点の記録。
  - 3つの `.mjs` はどれも先頭に `/* ... */` の Apache License 2.0 のヘッダー（1〜15 行）を持ち、`node:` の組み込みだけを使う（npm の依存なし）。`program-size.mjs` は直接流したときだけ `main()` を呼ぶ（518〜533 行）。`ci-summary.mjs` は読み込むと `main()` が流れる（439〜445 行）。`verify-graph-check.mjs` は読み込むと `./gradlew` を流す。テストはどれにも無い。
  - 検査の対象は K-121 のまま: ライセンスヘッダーは `frontend/` と `perf/` だけ（`check-license-header.mjs` 105〜108 行）、書式とリンタは `frontend/` の中と perf の伏せ字の道具の1ファイル（`build.gradle.kts` 229 行）、pre-commit の Prettier は `^frontend/` だけ（`.pre-commit-config.yaml`）。
  - `docs/verification.md` 70 行は「ライセンスヘッダーの検査の対象に `scripts/` を足して検査に載せるかは、後の Intent で決める」、176 行は「ルートの `scripts/` の台本（`program-size.mjs`・`ci-summary.mjs` を含む）は、CI の報告の道具であり本体の機能ではないため、単体テスト・ライセンスヘッダー・書式・リンタの検査の対象の外です（Intent 261108-ci-size-coverage での依頼者の決定）」。今回の Intent はこの決定を見直す。
- 見立て（未検証）: 足し方の候補は、(a) `check-license-header.mjs` の集める置き場に `scripts/` を足す（`perf/` と同じ `.mjs`・`.js` の扱い。テスト `frontend/src/licenseHeaderCheck.test.ts` が対象の置き場を確かめているかは読んでいない）、(b) 書式・リンタは perf の伏せ字の道具と同じ形の Gradle のタスク（`frontend/node_modules/.bin/` の道具に `frontend/` の設定を渡す）か、`frontend/eslint.config.js` に `scripts/**` の Node の決まりを足す形（perf の道具の決まり `perfToolRule`、301〜303 行が前例）、(c) 型の検査は `frontend/tsconfig.perf-tool.json` と同じ形。`verify-graph-check.mjs` は `./gradlew` を流す台本のため、検査に入れるのがライセンスヘッダー・書式・リンタだけか、`verify` の中で流すことまで含むかを分けて決める（`verify` の中で `./gradlew --dry-run` を流すと入れ子の Gradle になる）。単体テストを付けるなら、`ci-summary.mjs` の読み込み時の `main()` を直接流したときだけにする変更が要る。今の3ファイルが Prettier・oxlint・ESLint を通るかは流していない。

**K-131 R-07: `packagesJudgedByTotal` の確かめのコマンドは一覧を手で書いている（2026-11-09）（`build-and-verify`）** — 2026-11-11 の注: 一覧の撤去で、このコマンドと節は `docs/verification.md` から消えた（解消。K-142）。

- 確かめた事実:
  - `docs/verification.md` 78 行: `git diff --name-only develop... -- backend/src/main | grep -E 'cherry/mastersmith/(access/service|audit/repository|common/error/domain|common/error/service|common/health|common/i18n/domain|common/web)/[^/]+$'`。81 行: 「この一覧は `backend/build.gradle.kts` の `packagesJudgedByTotal` と同じ 7 パッケージです（2026-11-08 の時点）。一覧から外れたパッケージがあれば、このコマンドからも外します」。
  - `[^/]+$` はパッケージの直下のファイルだけに当てる（下位パッケージは当てない）。
  - 一覧をファイルから読む前例: `scripts/ci-summary.mjs` の `parseGradleFloors`（162〜182 行）。読み手の一覧は `dependencies.md` の「台本と一覧の読み手の依存」。
- 見立て（未検証）: 直し方の候補は、(a) コマンドの中で `backend/build.gradle.kts` から名前を取り出し（`sed`・`grep`）、`.` を `/` に替えて正規表現を組み立てる（bash と zsh の両方で動く形。`project.md` の zsh の決まり）、(b) `scripts/` に小さな台本を置き、`ci-summary.mjs` と同じ読み方で確かめる（K-130 の検査の対象の決定が効く）。一覧が空のとき（K-135）に「当たらない」と正しく出ることも確かめる。

**K-132 `packagesJudgedByTotal` にあった 7 パッケージの本体とテストの対応（2026-11-09。2026-11-11 の注: 一覧は消え、7 パッケージにもパッケージごとの下限が当たる。K-142）（`access`・`audit`・`common-error`・`common-health`・`common-i18n`・`common-web`）**

- 確かめた事実（本体の行の数は `wc -l`、ヘッダーと Javadoc を含む。置き場の一覧は `code-structure.md`）:

| パッケージ | 本体 | テスト | ソースから読める薄い所（見立て。カバレッジは測っていない） |
|---|---|---|---|
| `access.service` | `AccessDeniedEventPublisher`（60）・`AccessProblemTypeCatalog`（32） | `AccessDeniedEventPublisherTest`（5 件）・`AccessProblemTypeCatalogTest`（2 件） | 本体の `try`・`catch` は単体テストで両方を通る見込み。薄い所は読み取れない |
| `audit.repository` | `AuditEventRepository`（57、インターフェースだけ） | `AuditEventRepositoryIT`（4 件） | 実行できるコードが無い（K-134、`component-inventory.md` の `audit`） |
| `common.error.domain` | `BusinessException`（104）・`CommonProblemTypes`（121）・`LocalizedText`（48）・`ProblemType`（78）・`ProblemTypeCatalog`（33、インターフェース） | `ProblemTypeTest`（6 件と jqwik の性質1つ）。`CommonProblemTypes.all()` は `ProblemTypeRegistryTest` でも通る | `LocalizedText` の `en == null`（英語は空白 `" "` だけを確かめる）、`BusinessException` の `properties` が `null` のときと3引数の口に値を渡すとき |
| `common.error.service` | `CommonProblemTypeCatalog`（32）・`ProblemTypeRegistry`（104） | `ProblemTypeRegistryTest`（7 件）・`ProblemTypeDuplicateStartupIT`（3 件） | 辿れない分岐（K-133） |
| `common.health` | `TimeBoundedDbHealthIndicator`（142）・`HealthProperties`（28、計測の外） | `TimeBoundedDbHealthIndicatorTest`（6 件）・`HealthEndpointIT`（5 件） | `executor.submit` が断られたとき（`submit-failed` の WARN、94 行の `catch`）、`InterruptedException` の枝、`destroy()` を確かめるテストが見当たらない。`backend/build.gradle.kts` のコメントは U1 の実測で行 79.2% とする |
| `common.i18n.domain` | `AcceptLanguageResolver`（89）・`DisplayLanguage`（39） | `AcceptLanguageResolverTest`（4 件と jqwik の性質2つ） | 50 件を超える見出し（`MAX_ENTRIES`、32・47〜48 行）、`Q=` の大文字、`q=` 以外の引数、`*` を名指しで確かめる件は見当たらない（性質のテストが通す可能性はある） |
| `common.web` | `CacheControlFilter`（96）・`RequestBodyLimitRoute`（58）・`RequestSizeLimitFilter`（247）・`RequestSizeRejection`（44）・`RequestSizeRejectionListener`（36、インターフェース）・`MastersmithWebProperties`（33、計測の外） | `CacheControlFilterTest`（5 件）・`RequestSizeLimitFilterTest`（6 件）・`RequestSizeLimitRoutesTest`（5 件。受け取り側の例外を含む） | `BufferedBodyRequest` の `ServletInputStream` の `isFinished`・`isReady`・`setReadListener`・`read()`、`getReader` で文字コードを指定したとき、`Content-Length` も `Transfer-Encoding` も無いとき、`RequestSizeRejection` の `null` の検査を名指しで確かめる件は見当たらない |

- `access`・`audit` には機能の境界テスト（`AccessBoundaryArchitectureTest`・`AuditBoundaryArchitectureTest`）がある。`common` には無い（`*BoundaryArchitectureTest.java` 16 ファイル）。`team.md` の「手を入れる Bolt で、その機能の境界テストが無ければ足す」が共通部品の `common` に当たるかは決まりの読み方による（見立て）。今回の見込み（`src/main` を変えない）なら当たらない。
- 今の値は、`project.md` の Testing Posture の Delivery Planning の実測の形（`:backend:cleanTest :backend:cleanIntegrationTest :backend:test :backend:integrationTest :backend:jacocoTestReport` の後に `jacocoTestReport.xml` から読む）で測る。

**K-133 `ProblemTypeRegistry` の辿れない分岐（2026-11-09）（`common-error`）**

- 確かめた事実:
  - `ProblemType` の `code` は `^[A-Z][A-Z0-9_]*$` に限られ、`slug` は `code` を小文字にして `_` を `-` に替えたもの（`toSlug`）。この変換は1対1で、`code` が重ならずに `slug` だけが重なることは起きない。
  - `ProblemTypeRegistry` のコンストラクターの `if (!duplicateCodes.isEmpty() || !duplicateSlugs.isEmpty())` の右側が真になるのは左側が偽のときだけで、そのとき `slug` の重なりも無い。中の `if (!duplicateSlugs.isEmpty())` が偽になるのは `code` だけが重なるときで、これも起きない。
  - `ProblemTypeRegistryTest` 85〜93 行の「slug の重なり」の件は同じ `code`（`A_B`）を2つ渡しており、両方が重なる場合を確かめている。
- 見立て（未検証）: テストを足しても届かない分岐が少なくとも2つ残る。パッケージの分岐の下限 70% に届くかは分岐の総数による。届かないときに `src/main` を直すと、`team.md` の「`packagesJudgedByTotal` のパッケージに手を入れる Bolt」（全件の `verify`、一覧から外す）に当たる。

**K-135 一覧を空にするときの書き方と、一覧を読む台本（2026-11-09）（`build-and-verify`）** — 2026-11-11 の注: 一覧は空にするのではなく変数ごと消え、`parseGradleFloors` は一覧を読まない形に書き換わった（解消。K-142）。

- 確かめた事実: `packagesJudgedByTotal` は `listOf(…)` で書かれ、`excludes = packagesJudgedByTotal` に渡る（`backend/build.gradle.kts` 313〜357 行）。`scripts/ci-summary.mjs` の `parseGradleFloors` は `listOf\(` に当たらなければ例外を投げ、要約は下限の列を「—」にして警告を出す（204〜210 行。CI は落ちない）。`team.md` は「一度外したパッケージは一覧に戻さず、一覧を増やす変更はしない」とする。
- 見立て（未検証）: 7 つすべてを外すと、Kotlin では型を書いた `listOf<String>()`・`emptyList<String>()` にするか、一覧と2つ目の `rule` の `excludes` の行を消す形になる。どの形でも `ci-summary.mjs` の正規表現は当たらない（`listOf<String>(` は `listOf\(` に当たらない）。一部だけ外すなら今の書き方のまま読める。一覧を残すか消すかと、`ci-summary.mjs` と R-07（K-131）の合わせ方を要件で決める。

**K-137 自動化 A1〜A3 に関わるリポジトリの中のもの（2026-11-09）（`build-and-verify`・`container-runtime`）**

- 確かめた事実: A1〜A3 の今の道具はリポジトリの外（ホームの下）にあり、読んでいない（依頼による）。A1（振り返りの段の読み取りの確かめ）に当たる台本はリポジトリの中に無い（`scripts/` の4ファイルのどれでもない）。A2（`main` への取り込みの前の CI の試行ごとの確かめ）は `project.md` の Deployment の「`gh run view --json attempt,jobs` で試行ごとに確かめる」決まりで、台本は無い。A3（内部DB の複写を数える）は README の目次（109 行）が `docs/internal-db.md` に「内部DBの複写を数える」の節があるとする（`docs/internal-db.md` は読んでいない）。
- 見立て: 台本をリポジトリに置くなら、ルートの `scripts/` が置き場の候補で、K-130 の検査の対象の決定がそのまま効く。A3 はチームの知識（`aidlc/spaces/default/knowledge/aidlc-shared/ops-notes.md`）の「リポジトリの外の小さな Java の道具（H2 の JDBC、`ACCESS_MODE_DATA=r`）で値を表示せずに数える」決まりがあり、Node の台本にするか Java の道具のままかで、ライセンスヘッダーと検査の対象の扱いが変わる。A2 の台本は公開のリポジトリに置くことになるため、`gh` の出力のどの値を出すかを決める。

## リンタと静的解析

- Java: Spotless（palantir-java-format・ライセンスヘッダー）、SpotBugs＋FindSecBugs（`backend/config/spotbugs-exclude.xml`。`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority にかかわらず止める、`team.md`）。
- 画面: Prettier・oxlint・ESLint（`frontend/eslint.config.js`）・Stylelint。
- ライセンスヘッダー（2026-11-08 に読んだ）: ひな形は `config/license-header.txt` の1つで、ルートの `build.gradle.kts` が `/* ... */` の形（Java・Gradle の Kotlin DSL）と Mustache のコメントの形（メールのテンプレート）を作り、`frontend/scripts/check-license-header.mjs` が `frontend/` の TS・TSX・JS・MJS・CJS・CSS（`/* ... */`、`/** ... */` は認めない）と HTML・SVG（`<!-- ... -->`）、`perf/` の MJS・JS を確かめる。
- 毎回流れる検査（2026-11-08）: `gitleaksScan`（履歴全体、`build.gradle.kts` 287〜293 行）と `perfEvidenceScan`（`aidlc/` の記録の JWT の形、295〜302 行）は入力・出力を宣言せず毎回流れる。`osvScan`（327〜417 行）は lockfile と `config/npm-build-tools.txt` を入力にするため、変わらなければ UP-TO-DATE（`docs/verification.md` 49 行は `--rerun` で流し直す手順を書く）。

## CI と依存

- `.github/workflows/ci.yml`（2026-11-08 に全文を読んだ。本文は K-115）: `develop` への push と `v*` のタグと手動で動く。ジョブ `verify`（`ubuntu-latest`・`timeout-minutes: 60`）はサブモジュールごと全履歴を取得し、JDK 25・Node 26・Gradle を用意し、Gitleaks 8.30.1 と OSV-Scanner 2.7.0 を版と SHA-256 で確かめて入れ、`./gradlew verify` を流し、WAR を 30 日保存する。ジョブ `release`（タグのとき）は WAR を GitHub のリリースに添付する。actions はコミットの SHA で固定（版は `technology-stack.md`）。2026-11-01 の記録（Node 24・OSV-Scanner 2.6.0）は古い（K-118）。
- `.github/dependabot.yml`: 見送り（`ignore`）は gradle の2件（appender・networknt）・npm の `typescript` の大きな版・docker の `eclipse-temurin` の大きな版（K-66・K-72、本文は `dependencies.md`）。Dependabot が見ない固定先（`docker/perf/compose.yaml`・`TargetDbImages.java`・`perf/README.md` の grafana/k6 など）は手でそろえる運用で、`dependabot.yml` のコメントに列挙している。

- 2026-11-08 の2回目: `ci.yml` は 1回目と同じ（変わっていない。最後に変えたコミットは `a29f6dbf`（2026-11-02））。手順の並び・道具の入れ方・要約の無いこと・`verify` が落ちたときに後ろが流れないことは `architecture.md` の K-119。

- 2026-11-09: `ci.yml` は前の Intent のコミット `71a8b436` で 143 行・10 手順になった（報告だけの手順3つ。`architecture.md` の K-127）。版を固定していない所は `technology-stack.md` の K-128、時間の論点は `architecture.md` の K-129。

- 2026-11-11: `ci.yml` は 162 行。6 の手順が `./gradlew verify -PitForks="$IT_FORKS"` になり、既定は `IT_FORKS_DEFAULT: "3"`、手動の入力 `it_forks` は 1〜8（コミット `0f35e017`。本文は `architecture.md` の K-140）。`dependabot.yml` の `ignore` は `dependencies.md` の K-87（2026-11-11 に直した）、版の固定は `technology-stack.md` の K-138。

**K-143 依存の見直しで古くなっている記述（2026-11-11）（`build-and-verify`）**

- 確かめた事実:
  - `aidlc/spaces/default/memory/project.md` の Tech Stack の「Jackson の上書きは 3.1.7 とする（…Jackson の BOM を platform で読む形は同じ）」: 今のビルドに上書きは無い（コミット `98ef3e51` で外れた。`technology-stack.md` の K-138）。同じ節の opentelemetry-logback-appender の行は「Spring Boot 4.1.1 が持つ OpenTelemetry 1.62」と書く（今は 4.1.2 で、OpenTelemetry は 1.62.0 のまま。固定の理由は今も当たる）。
  - `.github/dependabot.yml` の gradle の `ignore` の説明の最初の段落も「Spring Boot 4.1.1 の OpenTelemetry 1.62」（続く段落は 4.1.2 の見直しの経緯）。
  - 知識ベースの `packagesJudgedByTotal` を今の仕組みとして書いていた行は、この回の候補で今の形に直すか、撤去の前の記録と注した（K-142）。
- 未検証: メモリーのファイル（`project.md`）の書き換えは、学びの手順か依頼者の判断による。この段では直していない。

## 静的解析の除外と境界テストの形（K-80、2026-10-29）

- SpotBugs の `SQL_` の除外（`backend/config/spotbugs-exclude.xml`）は `RowSqlExecutor` の `search`・`detail`・`precheckUnique`・`insert`・`update` ほかのメソッド単位で個別に書かれている（開発担当の読み）。`team.md` の決まりで、除外の追加・拡大はコード生成の計画に明記して依頼者の承認が要る。
- `BusinessDataBoundaryArchitectureTest` の `allowedDependencies` は `businessdata` が依存してよい先を `targetdb.service/domain`・`role.service/domain`・`dsl.domain/service`・`common` などに限る。`ALLOWED_STATEMENT_OWNERS` は文の作成・実行を許す先を `RowSqlExecutor` と `targetdb.repository` の数クラスに限る。
- 見立て（未検証）: 参照先の読み取りを `businessdata` の内側に置き、既存の `search`・`detail` を再利用するなら、これらの許可と除外は緩めずに済む。`targetdb` や `role` に手を入れるときは `TargetDbBoundaryArchitectureTest`・`RoleBoundaryArchitectureTest` の許可の一覧に触れうる。

## ドキュメント

- Javadoc・コメントは日本語で、要件・設計の ID（BR・NFR・契約の番号）と Intent の名前を細かく引く。`frontend/src/features/README.md` に機能の作り方の決まり、`perf/README.md` に負荷の試験の手順（1,753 行、2026-11-01）。
- 2026-11-08: `README.md` は 164 行に縮み、前提の道具・取得と準備・開発時の起動・コンテナでの起動と確認・文書の目次・ライセンスだけを持つ。検査とテストの手順は `docs/verification.md`、配備・内部DB・設定・メール・対象DB・DSL・利用者・ロール・運用・見た目・アイコン・差し込み口は `docs/` の主題ごとの文書へ分かれた（目次 102〜119 行）。下の K-88 の記述は 2026-11-01 の時点のもの（K-118）。
- 2026-11-09: `docs/verification.md` は 398 行（下の K-125 の 353 行から、前の Intent で「規模・テストの件数・カバレッジの報告（CI の要約）」の節 133〜176 行が足された。節の位置は `code-structure.md` の「CI と台本の置き場」）。README（165 行）はリリースの手順を持たず、リリースの前の確かめは `docs/verification.md` 178〜198 行（`architecture.md` の K-136）。

**K-88 README.md に経緯が溜まっている（2026-11-01）（`build-and-verify`・`container-runtime`）**

- 確かめた事実（`grep` と見出しの一覧による）:
  - 1,678 行・`##` と `###` の見出しが約 90。そのうち 25 個が見出しの名前に Intent・単位（U1〜U8）・Bolt を含む（例: 「メール（U1）」「監査ログ（U4）」「業務データの読み取りの API（Intent 261016-business-data-views の U1）」「ロールと権限の API（Intent 261004-role-menu の U4、Bolt B4・B5・B6）」）。
  - 本文で Intent・単位・Bolt・履歴を引く行は 164。
  - 節が機能の別でなく作った順に並ぶ（609 行の「環境変数」の後に、メール・対象DB・DSL・管理の画面・利用者の管理・グループ・ロール・メニュー・骨組み・外部エクスポート・監視・警報・プロキシ・Flyway・アクセス制御・例外のログ・監査・プリファレンス・招待・画面の各単位が続く）。
  - 骨格の時点の説明が残る: 5 行「現在の内容はアプリの骨格（U1）です」、1608〜1634 行「後の単位（U2・U3・U4）が使う差し込み口」。
  - 「契約との差」の節が3つ（1457・1487・1604 行）。契約の文書（`aidlc/` の下）との差の記録で、使い方の説明ではない。
  - 37 行に make-you-chic-ui の固定先の経緯の連なり（10 段）。値も古い（`ecb07e9`、実際は `cab4207`。K-86）。
  - 版の固定の値（Temurin の jre 7 か所・jdk・Mailpit）を手順の中に直接書く（`technology-stack.md` の「版の固定先」）。
- 見立て: 使い方（前提・準備・検査・起動・運用・設定・API の一覧）と経緯（Intent ごとの決定・契約との差）を分け、経緯は `aidlc/` の記録へ移すか削るのが今回の「最新化」の中心。版の値の行を減らすと、上げるときにそろえる場所も減る。どこまで削るかは要件定義で決める。

**K-89 perf/README.md に経緯が溜まっている（2026-11-01）（`perf-and-monitoring`）**

- 確かめた事実:
  - 1,753 行。1〜60 行が今の基準の一覧、60〜1470 行が場面ごとの手順、1470〜1562 行が線の測り方・記録の前の伏せ字、1563〜1753 行（約 190 行）が「## 履歴」（見出しの対応表と H1〜H65 前後の番号付きの経緯。前の Intent で本文から移した）。
  - 本文で Intent・単位・Bolt・履歴を引く行は 139（「履歴」の節を含む）。
  - 手順の番号が継ぎ足しの形（`0・1`・`1'`・`1''`・`2''`・`3''''`・`3G'`・`3R'`・`2B'` など）で、場面の節ごとに「上の手順の 0・1 と同じ」と前の節を引く。
  - k6・JRE のイメージは `perf/versions.env` に一本化済み（`"$K6_IMAGE"` で読む）。JDK（130 行）と lgtm（486 行）は本文に直接書く。
- 見立て: 「履歴」の節は手順を流すのに要らず、記録の場所（`aidlc/`）への参照に置き換えられる。手順の番号の継ぎ足しは、共通の準備を1節にまとめると減る。どこまで直すかは要件定義で決める。

**K-90 perf の伏せ字の道具の検査の抜け（T1・T2、2026-11-01）（`perf-and-monitoring`・`build-and-verify`）**

- 確かめた事実:
  - 道具は `perf/redact-k6-evidence.mjs`（907 行、Node の組み込みだけに依る。口は JSON・テキスト・`--scan` の3つ、終わりのコード 0〜5）。
  - テストは `frontend/src/perfEvidenceRedactor.test.ts`・`perfEvidenceRedactorCli.test.ts`・`perfEvidenceScan.test.ts` の3つ（名前の grep だけ。中身は読んでいない）で、Vitest（`verify` の 5・7 の段）に流れる。
  - `frontend/vitest.config.ts` の `coverage.include` は `src/**/*.{ts,tsx}` だけ（61 行）のため、道具の `.mjs` はカバレッジの計測と `thresholds`（80・70）の判定に入らない（T1。前の Intent の Build and Test の実測は行 94.2%・分岐 90.61%）。
  - `build.gradle.kts` は道具に Prettier（`perfToolFormatCheck`）と oxlint（`perfToolLint`）だけを当て、ESLint と `tsc` は当てない（T2）。ライセンスヘッダーは `check-license-header.mjs` が `perf/` の `.mjs`・`.js` も見る。`verify` の 8 の段の `perfEvidenceScan` は `aidlc/` の記録を毎回調べる。
- 見立て（未検証）: `coverage.include` に道具を足すと、全体の合計の `thresholds` に道具の値が混ざる。ESLint の設定（`frontend/eslint.config.js`）と `tsconfig.json` は `frontend/` の中を前提にしていると見られ、`perf/` に当てる形は設計で決める（どちらのファイルも今回は読んでいない）。
- 2026-11-08 の追記: T1・T2 は直っている（`vitest.config.ts` の `coverage.include` に道具がある。`build.gradle.kts` に `perfToolEslint`（258〜269 行）と `perfToolTypecheck`（272〜283 行）があり、`verify` の 2 の段と 4 の段に入っている）。

**K-91 書き込みの参照の段の `stage` の無い WARN（T3、2026-11-01）（`businessdata`）**

- 確かめた事実: `BusinessDataWriteService.warnReference`（908〜931 行）は `stage`・`sqlState`・`vendorCode`・`exceptionClass` が null のときそのキーを出さない。呼び出しは4か所（325・343・365・377 行）で、借りられない2つは `Stage.BORROW` を渡す。365 行（`StepAttempt.Unexpected`）は `unexpected.stage()` を渡し、これが null の経路では「保存の前に対象DB の参照先を読めませんでした」の WARN が `stage` 無しで出る。値（行・名前・メッセージ）は出ない。
- 未検証: `StepAttempt.Unexpected` の `stage` が null になる経路（`atStage` で包まれていない想定外の失敗か）は読んでいない。直す形（`stage` を必ず持たせる・`UNKNOWN` を出す）は設計で決める。

**K-108 表示のタイムゾーンの選択肢の性能のテストの測り方（2026-11-06）（`frontend-feature-preferences`・`build-and-verify`）**

- 確かめた事実:
  - `frontend/src/features/preferences/TimeZoneOptionsPerformance.test.ts`（53 行、NFR2.1.6）: 名前の一覧を1回読み、5 回くり返す。各回で `clearFormatterCache()` を呼び、`[...base]` で新しい配列（`WeakMap` のキャッシュに当たらない）にして、`performance.now()` で `buildTimeZoneOptions(names, null, NOW, 'browser')` の1回だけを挟む。並べ替えた 5 回の3番目（中央値）が 100 ms 以下で合格。100 ms は延ばさない（頭の説明）。`console.info` に個数・5 回の値・中央値・Node の版・CPU を出す。
  - 最初の回は ICU の時差のデータの読み込みも含む冷たい回、2〜5 回目は整形器のキャッシュだけが冷たい回（ICU の内部の状態は残る見込み。未検証）。中央値は 2〜5 回目の値に寄る。
  - 同じテストは `frontendTest`（`npm run test`、計測なし）と `frontendCoverage`（`npm run test:coverage`、V8 の計測つき）の両方で流れる（`build.gradle.kts` 216〜220 行）。どちらも `unit` の project の中で、ほかの jsdom のテストのファイルと同時に流れる。性能のテストだけを計測・並列から外す設定は無い。
  - 依頼の記述: f2f2eb9b の CI（AMD EPYC 7763、計測つき）で中央値 106 ms、2回目の失敗。`team.md` の「同じテストが二度目に落ちたら、原因を直すまで次へ進まない」に当たる。
- 見立て（未検証）: V8 のカバレッジの計測は JS の関数・分岐の実行に手間を足し、ICU の中の整形器の生成にはかからないと考えられる。CI の機械でほかのテストとの競り合いがどれだけ効くかは分からない。手元の値は今回は測っていない。

**K-109 直し方の候補と制約（2026-11-06）（`frontend-feature-preferences`・`frontend-shared-format`）**

- 候補（どれも未検証。Code Generation の前に、計測つき・ほかのテストと同時の条件で捨ての試しで測る）:
  - (a) 時差だけを求める軽い関数を `shared/format/timeZone` に足す: 時点の秒を名前の外で1回だけ求め、`wallAt` を1回だけ呼んで `wallToSeconds(wall) - epoch` で時差の秒を出し（`formatToParts` 2 回 → 1 回）、時差の文字を直接作る（表示の文字の組み立てと `offsetOf` の正規表現を省く）。名前の検査も1回にする。JS の仕事は大きく減るが、整形器の生成（1件 1 回）は減らない。
  - (b) 時差の計算に要る項目だけの整形器（または `timeZoneName: 'longOffset'`）で生成を軽くする。生成の時間が項目の数でどれだけ変わるかは未検証。`longOffset` は前の Intent の NFR Design の Q1: A（使わない）との差になるため、使うなら依頼者に確かめる。
  - (c) 組み立て用の整形器を共有のキャッシュに入れない（K-107 の追い出しを無くす。測りの値への効き目は小さい見込み）。
- 制約:
  - 目標 100 ms と測り方（5 回の中央値・各回でキャッシュを空にする・新しい配列）は変えない（依頼）。`clearFormatterCache()` で消えない別のキャッシュに時差を持つと 2〜5 回目が温かい回になり、測りの意図が変わる。採るなら依頼者の決定が要る（`team.md` の物差しの決まり）。
  - ラベルの形（`名前 (±HH:mm)`、秒の端数がある時差は `±HH:mm:ss`）・並び（コード点の順）・UTC の追加・解釈できない名前の除外・保存済みの名前の扱いは変えない（`buildTimeZoneOptions.test.ts`・`TimeZoneSelect.test.tsx` が確かめる）。
  - `buildTimeZoneOptions.test.ts` 111〜122 行は `vi.spyOn(timeZone, 'formatInstant')` で呼び出しを数え、1 回目が 0 より多いことを確かめる。`formatInstant` を呼ばない形にするなら、数える相手を変える必要がある。
  - 新しい関数は `shared/format/timeZone` に置き、公開するなら `index.ts` から名前付きで出す（ESLint の S2 の文言「時刻の整形は src/shared/format/timeZone を使ってください」と `timeZoneConversionRules`）。`team.md` の「不具合を直すときは再現するテストを同じコミットに含める」が当たる。

- **K-125 `docs/verification.md` の構成（2026-11-08 の2回目、353 行の全文）（`build-and-verify`）**: 見出しは「1コマンドの検査（統合の前の関門）」（5 行〜。段の表 13〜24 行、画面のテストが2回流れることと性能のテスト 28〜31 行、段ごとの実行と報告の置き場 33 行、`verify` と `verifyQuick` の使い分け 35〜115 行（`packagesJudgedByTotal` の確かめ・`-PitTests`・`-PitForks`（N=1〜4 の実測）・JaCoCo の参考値）、OSV-Scanner の判定 117〜131 行）、「リリースの前の確かめ」（133〜153 行）、「ビルドした WAR での画面の確認（E2E）」（155〜353 行）の3つ。CI に触れるのは 11 行（「CI も同じタスクを呼びます」）・39 行（「CI は引数なしのこれを流します」）・93 行（`-PitForks` は CI に付けない）・131 行（CI は毎回 OSV を確かめる）・157 行（E2E は CI に入れない）。CI の手順や要約を説明する節は無い。報告の置き場は 33 行（`backend/build/reports/`・`frontend/coverage/`・`build/reports/osv-scanner/osv.json`）と 112 行（`reports/jacoco/quick/` と `reports/jacoco/test/`）。回帰の台本は 69〜71 行。要約の手順を足すなら、33 行の報告の置き場の近くか、新しい節に書く形が考えられる（見立て）。

## 技術的負債

- **今回の範囲（2026-11-11）**: 版の固定が2つ（opentelemetry-logback-appender 2.28.1-alpha・networknt 3.0.6）残り、どちらも外す時期が Spring Boot の管理の版（OpenTelemetry 1.63 以上・Jackson 3.2 系以上）に依る（`technology-stack.md` の K-85・K-138）。H2 の `-Dh2.compactThreads=1` が3か所にあり、H2 を上げるときに要否を見直す（K-139）。`-PitForks` の CI の既定 3 は手元の実測からの値で、CI の上の 2 と 3 の比べは記録に無い（`architecture.md` の K-140）。決まりと設定のコメントに古い版の名前が残る（K-143）。前回までの K-131・K-135（一覧の確かめのコマンド・一覧を空にする書き方）は一覧の撤去で解消した（K-142）。

- **今回の範囲（2026-11-09）**: ルートの `scripts/` が 4 ファイルに増えたが、どの検査とテストの対象でもない（K-130）。R-07 の確かめのコマンドが一覧を手で写している（K-131）。CI の機械のイメージ・cloc・JDK と Node の小さな版を固定していない（K-128）。CI の結合テストは1つの JVM で流れる（K-129）。`ProblemTypeRegistry` に辿れない分岐がある（K-133）。一覧を空にすると要約の台本が一覧を読めない（K-135）。

- **今回の範囲（2026-11-08 の2回目）**: ルートの `scripts/` の台本がどの検査とテストの対象でもない（K-121）。CI はカバレッジの報告を作るが、読む手順も残す手順も無く、実行の要約に何も書かない（K-119・K-120）。前の知識ベースの `verifyQuick` が「無い」とする記述は古い（K-122）。

- **今回の範囲（2026-11-08）**: 1コマンドの検査で画面のテストが2回流れる（K-112）。結合テストは1つの JVM で 278 クラスを順に流し、並べる口も絞る口も無い（K-113、本文は `architecture.md`）。カバレッジの判定が結合テストの実行記録に依り、結合テストを除いた軽い入口では判定できない（K-114）。決まりの文が「統合の前に全件の `verify`」を前提にしている（K-117）。時間の内訳は測っていない。

- 表示のタイムゾーンの選択肢の組み立てが、1件ごとに表示の文字全体を作ってから時差を切り出し、`formatToParts` を2回呼ぶ（K-106、本文は `architecture.md`）。整形器のキャッシュの上限 128 が名前の数より小さい（K-107）。CI の計測つきで性能のテストが2回落ちた（K-108）。直し方の候補と制約は K-109。

- 一覧の `COUNT(*)` が上限なしで、MySQL・MariaDB の部分一致・深い OFFSET が重い（K-62、本文は `architecture.md`）。読み取りのプールは小さい（K-63）。件数の型が「多数」を持てない（K-64、本文は `api-documentation.md`）。
- 固定している部品の見直しは Spring Boot 4.1.2 で済み、Tomcat・Jackson の上書きは外れた。appender と networknt の固定が残る（K-66）。
- 負荷の試験の台本・手順書が大きい（K-73。2026-11-01 の構成は K-89）。README.md の経緯（K-88。2026-11-08 には README は 164 行に縮んでいる。K-118）。
- 版の固定先が分散している（K-81〜K-84、`technology-stack.md`）。Node 26 への引き上げは `engines`・`checkToolchain`・CI・README を同時にそろえる必要がある（2026-11-08 にはそろっている。K-118）。
- perf の伏せ字の道具の検査の抜け（K-90。2026-11-08 には直っている）と、`stage` の無い WARN（K-91）。
- **今回の範囲（2026-11-03）**:
  - 業務データの時差つきの日時を UTC のまま見せ、UTC で入れさせている。ほかの画面は `shared/format/formatDateTime` で端末の時差に直しており、日時の見せ方が二系統（K-96）。`ListFormat` の日時の書式は値だけあって画面が使わない。
  - 対象DB の接続の時差が MySQL・MariaDB（`+00:00`）と PostgreSQL（設定なし、JVM の既定に従う見込み）で違う（K-95）。
  - 既定の DSL の生成が MySQL・MariaDB の日時の精度を写さず、端数の上限が 0 になる見込み（K-98、未検証）。手書きの PostgreSQL の `timestamp(n) with time zone` は時差なしと誤る。
  - アイコンの名前の一覧が画面とサーバーの2か所にあり（テストで一致を確かめる）、管理の項目と生成した業務のメニューはすべて既定の `list`（K-100）。
  - favicon が無く、`/favicon.ico` に HTML が返る見込み（K-102、未検証）。ホームと管理の仮の画面の中身が空（K-103・K-104）。
  - テストの置き場（ファイル名の grep だけ、中身は読んでいない）: 日時はサーバーの `businessdata/repository/{ColumnValueCodecTest,RowDialectTest}`・`businessdata/domain/ValueTypesTest`・`businessdata/web/{AbstractBusinessDataReadIT,AbstractBusinessDataWriteIT}`（3種類の対象DB の結合テスト）・`targetdb/config/{TargetDataSourceConfigTest,TargetWriteDataSourceConfigTest}`・`dslmanage/generate/TypeCategoryMappingTest`、画面の `features/tables/{valueFormat,formDiff,formValidation,searchConditions,rowLabel.property,BusinessDataDetailPage}.test.*`。アイコンの `'list'` は `app/layout/ShellLayout.test.tsx`・`app/navigation/{buildNavSections,navigationApi,BusinessNavigationProvider}.test.*`・`app/registry/allowedNavIcons.test.ts` ほか。管理の仮の画面は `code-structure.md` の K-104 の一覧。
- 全体の合計で判定する7パッケージが残る（上記）。
- `ColumnWriteRulesFactory` が `formPart: lookup` を `text` に落とし、REFERENCE・LOOKUP の選択肢を検証から外している暫定（K-74）。
- k6 の summary json に JWT が出るのに、自動の伏せ字の仕組みが無い（K-77）。

**K-77 perf の k6 の summary json に使い捨ての環境の JWT が出る。自動の伏せ字の仕組みは無い（`perf-and-monitoring`）**

- 確かめた事実（開発担当の調査）:
  - k6 の `--summary-export` の JSON に、台本の `setup()` が返すデータ（`bizTokens` ほか）が `setup_data` として出る。実物のキー構造は前の Intent の証拠 `aidlc/spaces/default/intents/261027-perf-targets-review/construction/build-and-test/evidence/biz-mysql-bizSearchLast-r1.json`（177〜195 行、値は `[REDACTED-JWT]` に置換済み）で確かめた。
  - k6 の呼び出しは `perf/README.md` に約 15 か所（50・179・248・333・358・452・574・595・659・762・791・1042・1110・1254 行ほか）と `perf/dsl-mixed-memory.sh` の 312 行。出力先は `build/perf-results`（git の対象外）で、手で証拠のディレクトリへ複写する流れ。
  - 既存の自動の伏せ字の仕組みは `perf/` にも README にも無い（`REDACTED` の文字列検索は README・`scenarios.js`・perf の shell で 0 件）。決まりは `aidlc/spaces/default/memory/project.md` の記述（手で `[REDACTED-JWT]` に置換し、`eyJ…` の形の検索と `gitleaks dir` で確かめる）だけ。`scenarios.js` の `handleSummary` は未使用。
- 未検証の仮説: 自動化の置き場の候補は、k6 の実行の直後に通す小さな後処理スクリプト（例: `perf/lib/` の下）か、台本の `handleSummary`。k6 2.x の `--summary-export` が `handleSummary` で上書きできるかは未検証。JWT を含む `setup_data` が k6 の標準出力・ログ（`$R/$TAG-k6.log` の形で保存する箇所がある）にも出うるかも未検証で、伏せ字の対象ファイルの範囲は設計で確かめる必要がある。
- 今回の Intent への意味: README の k6 の呼び出し（約 15 か所）と `dsl-mixed-memory.sh` を一続きで直すことになる。K-73 の「同じ指標の記述を漏れなく探す」と同じ作業になる。

**K-67 perf の biz の場面・種・資源の上限と、読み取りのプールを変える口（`perf-and-monitoring`）**

- 確かめた事実:
  - 場面（`perf/k6/scenarios.js` 160〜165 行）: `bizSearchFirst`（1ページ目、100 件・100 カラム・条件なし・既定の並び、`total` は `BIZ_ROWS` と一致を確かめる）・`bizSearchContains`（`c_010` の部分一致、表の全部を読む形）・`bizSearchLast`（主キーの順の最後のページ）・`bizSearchLastSorted`（索引の無い `c_001` で並べ替えた最後のページ）・`bizDetail`・`bizSearchWide`（`biz_wide`、メモリの確かめ。応答時間は記録だけ）。判定は 1 繰り返しの `iteration_duration` の `p(95)<1000`（550〜554 行。`bizSearchWide` は `max>=0`）。
  - 種: `perf/biz/make-biz-seed.sh`（`biz_item` は 100 カラム × 既定 100,000 行、`c_001` に索引なし）、`perf/make-biz-dsl.mjs`。
  - 使い捨ての環境（`docker/perf/compose.yaml`）: app `mem_limit` 既定 2g（60 行）、PostgreSQL 512m（92 行）・MySQL 768m（118 行）・MariaDB 512m（143 行）が既定で、InnoDB のバッファープールは 128M が既定（101・127 行）。値はシェルの環境変数 `MASTERSMITH_PERF_TARGETDB_*` で上げられる。前の Intent の実績では MySQL を 2688m まで上げた（`project.md` の記録）。
  - 読み取りのプールの既定は今は 10（K-63）。値（`MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE`）を負荷の試験で変えるには、app の `env_file`（`MASTERSMITH_PERF_ENV_FILE` と `MASTERSMITH_PERF_APP_TARGETDB_ENV_FILE`）にその変数を足す。`docker/perf/compose.yaml` には直接の指定が無い。
  - 前の Intent の測定の記録（`project.md` の Interpretations）: Not Met は MySQL・MariaDB の部分一致と MariaDB の深い OFFSET の4件（U1-NFR1.1・1.3・3.2・2.4）で、PostgreSQL は全場面が満たした。今回は再測定していない。
- 今回の Intent への意味: 件数の取り方やプールを変える効果は、この4場面（`bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted` と、MariaDB の深い OFFSET）で測り直すことになる。物差し（`p(95)<1000` の `iteration_duration`）を変えるのは、要件定義で依頼者が明示に決めたときだけ（`team.md`）。

**K-69 k6 の合否の線の定義場所は `perf/k6/scenarios.js` の `thresholdsFor(name)`（`perf-and-monitoring`）**

- 確かめた事実:
  - `thresholdsFor` は 522 行に始まり、`thresholds: thresholdsFor(SCENARIO)`（639 行）で k6 の設定に渡る。場面の名前ごとの分岐で、業務データの読み取り（`BIZ_SCENARIOS`、406 行〜）は 582〜588 行の1か所: `iteration_duration{scenario:<名前>}` が `p(95)<1000`（`bizSearchWide` だけ `max>=0` で記録のみ）、`checks{scenario:<名前>}` が `rate==1`、`http_req_duration{name:<名前>}` は `max>=0`（記録のみ）。`bizSearchContains`（約 2280 行）・`bizSearchLast`（2289）・`bizSearchLastSorted`（2297）はこの共通の線で判定される。
  - 業務データの書き込み（`BIZ_WRITE_SCENARIOS`）は 590〜596 行: `BIZ_WRITE_JUDGED`（`bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit`、435 行）だけ `p(95)<1000`、ほかは `max>=0`。
  - `dslMixed` の k6 の閾値は `checks` の率 1 だけ（551 行付近）。`memory.peak` の判定は k6 の外（K-70）。
  - 共通の物差しが `iteration_duration`（単調な時計）なのは、colima の VM の時計のずれで `http_req_duration` が崩れたため（`project.md` の学び）。
  - 線に DB の種類の区別は無い（MySQL・MariaDB の部分一致も PostgreSQL と同じ `p(95)<1000`）。
- 今回の Intent への意味: 応答時間の線を変えるなら、この関数の分岐（と手順書の合否の記述、K-73）が変更点。物差し（何で測るか）を変えるのは要件定義で依頼者が明示に決めたときだけで、変えた後も1回は前の物差しの値を並べて記録する（`team.md`）。

**K-70 `memory.peak`・`anon` の物差しの記述箇所と現行の判定（`perf-and-monitoring`）**

- 確かめた事実:
  - `perf/README.md`: 119 行（コンテナのメモリの最大は cgroup の `memory.peak`）、151 行（`--storage` の `memory.current` はページキャッシュを含む。止まるかは `anon` と OOMKilled）、860〜865 行（FR9.4 の合否: OOM なし・`OOMKilled` が false・`dslMixed` の `memory.peak` が上限の 90% 未満。読み方は `docker exec … cat /sys/fs/cgroup/memory.peak`）、931〜935 行（`memory.peak / memory.max` が 0.9 未満）、942〜951 行（`dslMixed` の `memory.peak` と `memory.stat` の記録: 既定の2回で `memory.peak / memory.max < 0.9`、物差しは `anon` に替えない、ページキャッシュを減らす H2 の `RETENTION_TIME=0` の試しは合否に使わない）、1146 行（`bizSearchWide` は `memory.peak` が上限 2g の 90% 未満と `checks` 率 1。`anon` の最大も並べる）。
  - `perf/dsl-mixed-memory.sh` は `memory.stat` の `anon`・`file`・`inactive_file`・`active_file`・`kernel` を1秒ごとに記録し、`anon_max_ratio` を並べるが、合否は peak/max だけ。
  - 直近の測定の事実（`project.md`）: `dslMixed` の `memory.peak` は 94.4%・取り直し 93.1%（Not Met）。peak の時の内訳は `anon` 68.3%・回収できるページキャッシュ 23.7%。物差しを `anon` に替えると Met になるが、測り方を後から緩めることになるため選ばなかった。対象DB の `anon` の最大は MariaDB 85.01%・MySQL 90.3%（OOMKilled false）。
- 今回の Intent への意味: p95・`memory.peak`・`anon` の緩和は 2026-10-28 に依頼者が決めている（メモリの記録）。値と対象は要件定義で明示して記録し、前の物差しの値を並べる。変更の置き場は README の上記の記述箇所と `perf/dsl-mixed-memory.sh`、場面の記述（`scenarios.js`）。

**K-71 `docker/perf/compose.yaml` の資源の上限の既定と、`perf/README.md` の業務データの試験の値の差（`perf-and-monitoring`）**

- 確かめた事実:
  - 既定（`docker/perf/compose.yaml`）: app は `mem_limit` 2g・`cpus` 4（59〜60 行。`MASTERSMITH_CONTAINER_MEMORY`・`MASTERSMITH_CONTAINER_CPUS`）、PostgreSQL 512m・`shared_buffers` 128MB（75・92 行）、MySQL 768m・`innodb-buffer-pool-size` 128M（101・118 行）、MariaDB 512m・128M（127・143 行）、Mailpit 256m（152 行）。対象DB のイメージは postgres 18.6・mysql 26.10.0・mariadb 13.0.2（ダイジェストで固定）。
  - `perf/README.md` 1153〜1164 行の表が「既定」と「業務データの試験の値」を並べる: MySQL バッファープール 128M → 1280M・メモリ 768m → 2688m、MariaDB 128M → 896M・512m → 1408m、PostgreSQL 128MB → 256MB・512m → 768m。表の大きさ（データと索引）に合わせて測り直した値で、試験の前に環境変数を export して渡す（1208 行ほか）。
- 食い違いの読み方: 食い違いは設計のうえの二層（既定と、試験で渡す値）で、README が両方を書いている。ただし compose の既定は試験の値ではなく、何も渡さずに起動すると MySQL・MariaDB の業務データの試験は資源が足りない（見立て。今回は起動していない）。判定に使う資源の上限を「どれが基準か」要件定義で決める必要がある。

**K-73 `perf/README.md`（1,359 行。2026-10-29 に再計測）と `perf/k6/scenarios.js`（2595 行）が大きく、基準の値が読み取りにくい（`perf-and-monitoring`）**

- 確かめた事実: 行数は `wc -l`。Intent ごとの追記で、同じ指標（`memory.peak`・biz の場面など）に前の Intent の結果の記述と現行の合否が同じ節に混在する（例: 942 行の「Intent 261016-business-data-views の U5」の節と 860 行の FR9.4 の節）。`scenarios.js` の場面のコメントも Intent の名前で段が分かれている（158 行〜、404 行〜）。
- 今回の Intent への意味: 線や物差しを変えるときは、README の同じ指標の記述を漏れなく探して直す必要がある（K-69・K-70 の箇所の一覧が出発点）。

**K-68 依頼の「`perf/README.md` (c) の grep の形」の該当行は、(c) の節 1003 行の 1021 行**

- 確かめた事実:
  - `perf/README.md` 1003 行は見出し `### (c) ロールの3場面を手順書の順と単独で（FR7.2）`。1021 行は `grep -h -o '"checks": {[^}]*}' "build/perf-results/$s.json" "build/perf-results/$s-alone.json"`。
  - 前の Intent の記録（`aidlc/spaces/default/intents/261016-business-data-views/operation/feedback-optimization/feedback-loop.md` 34・152 行）は「(c) の `grep -o` が複数行の JSON で何も出さない（今も残っている見込み。読み取りの調べで、流して確かめてはいない）」とする。出どころは `261015-deps-ui-carryover` の Build and Test の 5.3。
- 見立て（未検証）: `grep` は1行ごとに照合するため、`--summary-export` の JSON が複数行に整形されていると `"checks": {…}` を1行で拾えず何も出ない。直し方の候補は、`jq` で読む、1行にしてから拾う、または手順を値の読み取りに替えること。流して確かめていない。

## Sources

- 2026-11-11: `backend/build.gradle.kts`・`.github/workflows/ci.yml`・`.github/dependabot.yml`・`gradle/libs.versions.toml`（どれも全文）、`scripts/ci-summary.mjs`（35 行と 174〜196 行）、`docs/verification.md`（22 行・79〜95 行）、`git log`（`67f6f5f2`・`0f35e017`・`98ef3e51`）、`aidlc/spaces/default/memory/project.md` の Tech Stack（照合のため）、開発担当の調査 `aidlc/spaces/default/intents/261110-deps-forks-review/inception/reverse-engineering/developer-scan.md`。
- 2026-11-09: `.github/workflows/ci.yml`・`scripts/`・`frontend/scripts/check-license-header.mjs`（どれも全文）・`build.gradle.kts`（1〜566 行）・`backend/build.gradle.kts`（100〜357 行）・`docs/verification.md`（1〜200 行）・7 パッケージの本体とテスト（全文）、`frontend/eslint.config.js`・`frontend/tsconfig.perf-tool.json`・`.pre-commit-config.yaml`・`README.md` の `grep`、`find`（テストの数と境界テストの一覧）、開発担当の調査 `aidlc/spaces/default/intents/261108-ci-checks-release/inception/reverse-engineering/developer-scan.md`。
- 2026-11-08 の2回目: `.github/workflows/ci.yml`・`scripts/`・`frontend/scripts/{check-license-header,check-bundle-size,check-dist-assets,make-ico}.mjs`・`frontend/vitest.config.ts`・`frontend/package.json`・`backend/build.gradle.kts`・`build.gradle.kts`・`docs/verification.md`（どれも全文）、`frontend/src/*.test.ts` のファイル名、`git log`・`git ls-files`（`scripts/`）、開発担当の調査 `aidlc/spaces/default/intents/261108-ci-size-coverage/inception/reverse-engineering/developer-scan.md`。
- 2026-11-08: `build.gradle.kts`（全文）・`backend/build.gradle.kts`（全文）・`frontend/package.json`・`frontend/vitest.config.ts`・`frontend/scripts/check-license-header.mjs`・`config/`・`.github/workflows/ci.yml`・`README.md`、流し読みの `docs/verification.md`・`backend/src/test/java/`（`*Test`・`*IT` の数）、`aidlc/spaces/default/memory/{team,project}.md`（決まりの文）、開発担当の調査 `aidlc/spaces/default/intents/261107-verify-quick-local/inception/reverse-engineering/developer-scan.md`。
- 2026-11-06: `frontend/vitest.config.ts`・`frontend/src/features/preferences/{TimeZoneOptionsPerformance,buildTimeZoneOptions}.test.ts`・`frontend/src/shared/format/timeZone/{conversionPerformance,timeZoneGate}.test.ts`・`build.gradle.kts`（216〜220 行）・`frontend/eslint.config.js`・`frontend/eslint-rules/timeZoneSelectors.js`、開発担当の調査 `aidlc/spaces/default/intents/261104-tz-options-perf/inception/reverse-engineering/developer-scan.md`。
- 2026-11-03: 開発担当の調査 `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/reverse-engineering/developer-scan.md`（テストはファイル名の grep だけ）。
- 2026-11-01: 開発担当の調査 `aidlc/spaces/default/intents/261101-maintenance-update/inception/reverse-engineering/developer-scan.md`（Technical Debt Signals の TD-1〜TD-14）。`README.md`・`perf/README.md` の見出しの一覧と grep の件数、`build.gradle.kts`・`.github/workflows/ci.yml`・`BusinessDataWriteService.java` の該当行。
- 開発担当の調査（今回: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/reverse-engineering/developer-scan.md`、2026-10-28 と前回）の Test Coverage・Code Quality Indicators・Technical Debt Signals・K-67〜K-73。
- アーキテクトの確かめ直し（今回: `backend/build.gradle.kts` の jacoco の規則、`perf/k6/scenarios.js` の `thresholdsFor`、`perf/README.md` の 1150〜1164 行、`docker/perf/compose.yaml`。前回: `frontend/vitest.config.ts`・`perf/README.md` の 1003〜1021 行・ArchUnit のファイル数）。
