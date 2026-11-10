# 業務の概要（mastersmith2）

> 2026-11-11、Intent `261110-deps-forks-review`（bugfix・Minimal）の Reverse Engineering（Focused scan、記録のコミット `7f36d8a`）で更新した。今回は版の定義と固定先（`gradle/`・`settings.gradle.kts`・`settings-gradle.lockfile`・`backend/build.gradle.kts`・`frontend/package.json`・`Dockerfile`・`compose.yaml`・`.github/`・`config/`）・`docs/verification.md` の `-PitForks` の節・`common/health/` の本体を読み、所見 K-138〜K-143 を足した。アプリの業務の作りは読み直していない（下の「何をするアプリか」「利用者と役割」は前回以前の記録による）。
>
> 前回の記録: 2026-11-09、Intent `261108-ci-checks-release`（bugfix・Minimal）の Reverse Engineering（Focused scan、記録のコミット `4fe8e79`）で更新した。今回は CI（`.github/workflows/`）・ルートの `scripts/`（4 ファイル）・`frontend/scripts/check-license-header.mjs`・`build.gradle.kts`・`backend/build.gradle.kts`・`docs/verification.md` と、`packagesJudgedByTotal` の 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）の本体とテストを読み、所見 K-127〜K-137 を足した。アプリの機能（HTTP の口）は読み直していない。前回までの散文と所見の一覧は残した。
>
> 前回の記録: 2026-11-08（2回目）、Intent `261108-ci-size-coverage`（express・Minimal）の Reverse Engineering（Focused scan、記録のコミット `9fd4f7f`）で更新した。今回は CI（`.github/workflows/`）・ルートの `scripts/`・`frontend/scripts/` の4ファイル・`frontend/vitest.config.ts`・`frontend/package.json`・`backend/build.gradle.kts`・`build.gradle.kts`・`docs/verification.md` だけを読み、所見 K-119〜K-126 を足した。アプリの機能は読み直していない。前回までの散文と所見の一覧は残した。
>
> 前回の記録: 2026-11-08、Intent `261107-verify-quick-local`（bugfix・Minimal）の Reverse Engineering（Focused scan、記録のコミット `16f46f1`）で更新した。今回は1コマンドの検査 `./gradlew verify` の作り（`build.gradle.kts`・`settings.gradle.kts`・`backend/build.gradle.kts`・`frontend/package.json`・`frontend/vitest.config.ts`・`frontend/scripts/`・`.github/workflows/`・`config/`・`README.md`）だけを読み、所見 K-111〜K-118 を足した。アプリの機能は前回から変わっていない（今回は機能を読み直していない）。前回までの散文と所見の一覧は残した。
>
> 前回の記録: 2026-11-06、Intent `261104-tz-options-perf`（bugfix・Minimal）の Reverse Engineering（Focused scan、記録のコミット `a789a6f`）で更新した。今回は表示のタイムゾーンの選択肢の組み立て（`frontend/src/features/preferences/`）・日時の変換の共有の関数（`frontend/src/shared/format/`）・`frontend/vitest.config.ts` だけを読み、所見 K-106〜K-110 を足した。アプリの機能は前回から変わっていない。前回までの散文と所見の一覧は残した（前の Intent `261102-timezone-ui-polish` の後の段で、利用者ごとの表示のタイムゾーンが入った。K-110）。
>
> 前回の記録: 2026-11-03、Intent `261102-timezone-ui-polish`（classic・Standard）の Reverse Engineering（Focused scan、記録のコミット `fa0b01e`）で更新した。今回は業務データの日時の型の対応と接続の時差（`businessdata`・`targetdb`・`dsl`・`dslmanage` の一部）、業務のメニューとアイコン（`navigation`、画面の骨組みの `app/registry`・`app/navigation`）、ホーム・管理の仮の画面（`features/admin`）・ページの頭（`index.html`）だけを読み、所見 K-94〜K-105 を足した。アプリの機能は前回から変わっていない。前回までの散文と所見の一覧は残した。
>
> 前回の記録: 2026-11-01、Intent `261101-maintenance-update`（bugfix・Minimal）の Reverse Engineering（Focused scan、記録のコミット `6079f13`）で更新した。今回は版の定義と固定先・検査の道具・README と perf/README の構成・`businessdata` の一部（P1・T3）だけを読み、所見 K-81〜K-93 を足した。アプリの機能は前回から変わっていない（今回は機能を読み直していない）。前回までの散文と所見の一覧は残した。
>
> 前回の記録: 2026-10-29、Intent `261029-reference-lookup-cleanup`（Standard・brownfield）の Reverse Engineering（Focused scan、記録のコミット `15a25aa`）で更新した。既存の散文と前回の所見の一覧は残し、今回の範囲（業務データの選択肢 REFERENCE・LOOKUP、`dsl` の選択肢の型、`targetdb`・`role` の口、`frontend/src/features/tables`、perf の JWT、コンテナの版、i18next の2件）の所見 K-74〜K-80 を足した。深く読んだのは今回の範囲だけで、それ以外は前回（`261027-perf-targets-review`）以前の記録による（`reverse-engineering-timestamp.md` の Scope of Analysis）。

## 何をするアプリか

mastersmith2 は、別に動く業務の DB（対象DB: MySQL・MariaDB・PostgreSQL）のテーブルを、DSL（YAML）で定義した見せ方に従って画面から扱う管理のアプリである。今のコードがすること:

- 利用者の認証（ログイン・アカウントロック・トークン・ログアウト）と、招待から登録の完了までの流れ。
- 利用者・招待・グループ・ロールの管理、ロールの権限（スキーマ・テーブル・カラムへの主権限と、テーブルの補助権限 `CREATE`・`DELETE`）、作業ロールの切り替え。
- 対象DB のスキーマの読み取りと、既定の DSL の生成・投入・プレビュー・適用・履歴。
- 適用中の DSL のメニューを、作業ロールの権限で枝を落としてサイドバーに出す。
- 業務データ（対象DB の行）の定義・一覧（検索・並べ替え・ページ送り）・詳細・作成・更新・削除（`businessdata`。前の Intent `261016-business-data-views` で入った）。
- 自分の設定（氏名・言語・テーマ・文字の大きさ・表示のタイムゾーン）とパスワードの変更（`features/preferences`）。表示のタイムゾーンは「ブラウザに合わせる」か、ブラウザが知る IANA の名前から選ぶ（2026-11-06 に確かめた。K-110）。
- 管理の操作・認証・DSL・ロール・業務データの書き込みなどの出来事を、内部DB（組み込みの H2）の監査の表に追記する。

開発の進め方の上では、統合の前の関門として1コマンドの検査 `./gradlew verify`（フォーマットから成果物の確認まで 10 の段）を手元で流し、CI（GitHub Actions）も同じタスクを1本で流す（2026-11-08 に確かめた。K-111・K-115）。Bolt の統合の前の速い入口 `./gradlew verifyQuick` も今はある（K-122）。CI の実行の要約（`$GITHUB_STEP_SUMMARY`）には今は何も書かない（K-119。2026-11-09 の時点では古い: 前の Intent で規模・テストの件数・カバレッジを要約に書く手順が足され、CI は 10 手順になった。K-127）。

## 利用者と役割

| 人 | すること |
|---|---|
| 管理者（`admin_flag` が真で停止していない利用者） | 利用者・招待・DSL・ロール・グループの管理（`/api/admin/**`） |
| ログインした利用者 | 自分の設定、作業ロールの切り替え、メニューをたどる、権限に応じて業務データを一覧・詳細・作成・更新・削除する（`/api/me/**`・`/api/tables/**`） |
| 招待を受けた人（ログインなし） | 招待のリンクの確かめと登録の完了（`/api/registration/**`） |
| 開発者（依頼者と AI） | 統合の前に `./gradlew verifyQuick` か全件の `./gradlew verify` を流す（使い分けは `team.md`。コンテナの実行環境 colima が前提）。`origin` への push は依頼者だけ（`team.md`） |

## 所見の一覧（K-62〜K-143）

番号は K-61 から続く（K-62〜K-126 は前々回まで、K-127〜K-137 が前回 2026-11-09、K-138〜K-143 が今回 2026-11-11）。本文は持ち主の文書に1回だけ書き、ここには一覧だけを置く。前回までの K-1〜K-61 の本文はこの知識ベースに無い（前の Intent の記録と git の履歴に残る）。

### 今回（Intent 261110-deps-forks-review）の主題

依存の定期の見直し（Spring Boot・Jackson の上書き・opentelemetry-logback-appender の固定・H2 と `-Dh2.compactThreads=1`・コンテナのイメージのダイジェスト）、知識ベースの古い記述（`packagesJudgedByTotal` を今の仕組みとして書く箇所。前の Intent のコード生成のレビュー R-06）の更新、結合テストの並べ方 `-PitForks` の値（2 と 3）の再評価。

| 番号 | 要点 | 主な部品 | 本文の場所 |
|---|---|---|---|
| K-138 | 依存の見直しの起点: Spring Boot 4.1.2。**Jackson の BOM の上書きは今のビルドに無い**（コミット `98ef3e51` で外れた。Jackson 3.1.7 は BOM の値）。固定は opentelemetry-logback-appender 2.28.1-alpha（OpenTelemetry 1.62.0）と networknt 3.0.6 の2つ。イメージの版とダイジェストはすべての固定先で同じ | `build-and-verify`・`container-runtime` | `technology-stack.md`（K-138・版の固定先） |
| K-139 | `-Dh2.compactThreads=1` は `Dockerfile`・`backend/build.gradle.kts`（テストの JVM）・`frontend/playwright.config.ts`（E2E の起動）の3か所と、確かめの `H2CompactThreadsTest`。E2E の WAR の JVM に `-Duser.timezone` は見当たらない（未検証） | `build-and-verify`・`container-runtime`・`frontend-e2e` | `technology-stack.md`（K-139） |
| K-140 | `-PitForks`: `maxParallelForks` を N にするだけ（1 以上の整数、上限なし）。CI は既定 3・入力 `it_forks` 1〜8。手元の実測は N=3 が最速。CI の上の 2 と 3 の比べの結果は記録に無い | `build-and-verify`・`backend-test-support` | `architecture.md`（K-140） |
| K-141 | lockfile の SLF4J の2つの版（2.0.17 は SpotBugs の道具の構成だけ） | `build-and-verify` | `technology-stack.md`（K-141） |
| K-142 | `packagesJudgedByTotal` は消え、すべてのパッケージにパッケージごとの下限（行 80%・分岐 70%）が当たる。要約の台本も一覧を読まない。知識ベースの古い行は直すか、撤去の前の記録と注した（K-131・K-135 は解消） | `build-and-verify` | `code-quality-assessment.md`（K-142）・`dependencies.md` |
| K-143 | 古くなっている記述: `project.md` の Tech Stack の Jackson の上書きの行と「Spring Boot 4.1.1」、`dependabot.yml` の gradle の説明の「4.1.1」 | `build-and-verify` | `code-quality-assessment.md`（K-143） |

### 前の Intent（261108-ci-checks-release）の主題（今回は K-131・K-132・K-135 を K-142 で更新）

CI と検査の手直しとリリース。6 つの Bolt: (1) ルートの `scripts/` をライセンスヘッダー・書式・リンタの対象に足し、`docs/verification.md` の `packagesJudgedByTotal` の確かめのコマンド（R-07）を `backend/build.gradle.kts` から一覧を読む形に直す、(2) CI の `ubuntu-latest` が Ubuntu 26 に移ったことを受けて道具の版と動きを確かめ、固定するかを決める、(3) CI の job の時間（35〜39 分）の内訳を測り、分け方を決める、(4) `packagesJudgedByTotal` の 7 パッケージにテストを足して一覧から外す範囲を決める、(5) 自動化 A1〜A3 を台本にする、(6) `develop` から `main` へ fast-forward でリリースする。今回は CI・台本・検査の対象・R-07 のコマンド・7 パッケージの本体とテスト・リリースに関わる文書を読んだ。CI の時間とカバレッジは測っていない（要件の段で測る）。「未検証」の項目は要件定義・Code Generation の前に確かめる。

| 番号 | 要点 | 主な部品 | 本文の場所 |
|---|---|---|---|
| K-127 | CI のジョブ `verify` は 10 手順になった（cloc を apt で入れる・`scripts/ci-summary.mjs` で要約を書く・カバレッジの HTML を保存する、の3つが `if: always()`・`continue-on-error: true` で足された）。前の記録の K-119 の 7 手順は古い | `build-and-verify` | `architecture.md`（CI の今の形 2026-11-09・Interaction Diagrams 10） |
| K-128 | `ubuntu-latest` に頼る所（2 つのジョブの `runs-on`、機械のイメージの Docker と道具、apt の cloc）と、版を固定している所（Actions の SHA・Gitleaks と OSV-Scanner の SHA-256・Wrapper・lockfile・Node の大きな版の強制）。JDK と Node は大きな版だけ | `build-and-verify` | `technology-stack.md`（K-128） |
| K-129 | CI の時間の手がかり: 手順は直列、結合テストは1つの JVM（手元の実測で N=1 は 23分05秒、N=3 は 9分43秒）、画面のテストは2回、`program-size.mjs --base` は cloc を2回。手順ごとの時間はリポジトリに無い | `build-and-verify`・`backend-test-support` | `architecture.md`（Improvement Opportunities） |
| K-130 | ルートの `scripts/` は 4 ファイル（`ci-summary.mjs`・`program-size.mjs`・`verify-graph-check.mjs`・比べの元）で、ライセンスヘッダー・書式・リンタ・型・テストのどれの対象でもない。`docs/verification.md` 176 行に前の Intent の依頼者の「対象の外」の決定がある | `build-and-verify` | `code-quality-assessment.md`（K-130） |
| K-131 | R-07: `docs/verification.md` 78 行の確かめのコマンドは 7 パッケージを手書きの正規表現で持つ。一覧を読む前例は `ci-summary.mjs` の `parseGradleFloors` | `build-and-verify` | `code-quality-assessment.md`（K-131） |
| K-132 | 7 パッケージの本体とテストの対応と、ソースから読める薄い所 | `access`・`audit`・`common-error`・`common-health`・`common-i18n`・`common-web` | `code-quality-assessment.md`（K-132） |
| K-133 | `ProblemTypeRegistry` に辿れない分岐が2つある（`code` が重ならずに `slug` だけ重なることは起きない） | `common-error` | `code-quality-assessment.md`（K-133） |
| K-134 | `audit.repository` は Spring Data のインターフェースだけで、実行できるコードが無い | `audit` | `component-inventory.md` の `audit` |
| K-135 | 一覧を空にすると Kotlin の書き方が変わり、`ci-summary.mjs` の正規表現（`listOf\(`）が当たらなくなる | `build-and-verify` | `code-quality-assessment.md`（K-135） |
| K-136 | リリース: README に手順は無く、リリースの前の確かめは `docs/verification.md` 178〜198 行。CI は `main` の push で流れない。2026-11-09 の時点で fast-forward で取り込める（23 コミット） | `build-and-verify` | `architecture.md`（リリースの今の形） |
| K-137 | 自動化 A1〜A3 に当たる台本はリポジトリの中に無い。A2 は `project.md` の決まり、A3 は `docs/internal-db.md` の節（読んでいない）が関わる | `build-and-verify`・`container-runtime` | `code-quality-assessment.md`（K-137） |

### 前の Intent（261108-ci-size-coverage）の主題（今回は CI と `scripts/` を読み直し、K-119・K-121 を K-127・K-130 で更新）

GitHub Actions の CI（`.github/workflows/ci.yml`）の実行の要約（`$GITHUB_STEP_SUMMARY`）に、プログラムの規模とカバレッジを載せる。規模は cloc で数える Node の台本（予定は `scripts/program-size.mjs`）で、前の回のプッシュのコミットとの差も出す。カバレッジはバックエンドの JaCoCo の XML と画面の Vitest の `coverage-summary.json` から読む。今回は CI の手順の並び、道具の入れ方、カバレッジの報告の置き場と形、ルートの `scripts/` の台本の形と検査の対象、`docs/verification.md` の構成を読んだ。「未検証」の項目は要件定義・Code Generation の前に確かめる。

| 番号 | 要点 | 主な部品 | 本文の場所 |
|---|---|---|---|
| K-119 | CI のジョブ `verify` は 7 手順（取得 `fetch-depth: 0`・JDK 25・Node 26・Gradle・Gitleaks と OSV-Scanner（版と SHA-256 で固定）・`./gradlew verify`・WAR の保存）。要約への書き込みは無い。`verify` が落ちると後ろの手順は流れない | `build-and-verify` | `architecture.md`（検査の入口の今の形と CI・Interaction Diagrams 9） |
| K-120 | カバレッジの報告: バックエンドは `jacocoTestReport` の XML・HTML（`backend/build/reports/jacoco/test/`）、`verifyQuick` は `reports/jacoco/quick/`。画面は Vitest の `text`・`html`・`json-summary`（`frontend/coverage/`）。全体の値を XML の根の直下の `counter` から読む前例が `jacocoQuickReport` にある | `build-and-verify` | `code-quality-assessment.md`（K-120） |
| K-121 | ルートの `scripts/` は `verify-graph-check.mjs` と比べの元の2ファイル。`.mjs`・`/* */` のヘッダー・`node:` の組み込みだけの形だが、ライセンスヘッダー・書式・リンタ・型・テスト・カバレッジのどれの対象でもなく、`verify` と CI にも入っていない | `build-and-verify` | `code-quality-assessment.md`（K-121） |
| K-122 | 前の記録の訂正: `verifyQuick`・`-PitTests`・`-PitForks`・`jacocoQuickReport` は実装済み（前の記録の K-113 の「並べる口も絞る口も無い」、K-116 の見立て、`api-documentation.md` の「今は無い」は古い） | `build-and-verify` | `architecture.md`（検査の入口の今の形と CI） |
| K-123 | 「前の回のプッシュのコミット」の取り方の論点（きっかけ3種・`concurrency`・全履歴の取得）。どれも未検証 | `build-and-verify` | `architecture.md`（Improvement Opportunities） |
| K-124 | cloc と要約の手順を足すときの論点（入れ方・ライセンス・数える範囲・失敗のときの扱い・置き場とテスト・公開のリポジトリ）。どれも未検証 | `build-and-verify` | `architecture.md`（Improvement Opportunities） |
| K-125 | `docs/verification.md` の構成（見出し3つ、CI の記述は 11・39・93 行、報告の置き場は 33 行、台本は 69〜71 行） | `build-and-verify` | `code-quality-assessment.md`（ドキュメント） |
| K-126 | 行の数と版の確かめ直し: `build.gradle.kts` 913 行・`backend/build.gradle.kts` 546 行（前の記録の 860・446 から増えた）、CI の版は K-118 と同じ、cloc はどこにも無い | `build-and-verify` | `technology-stack.md`（K-126） |

### 前の Intent（261107-verify-quick-local）の主題（今回は検査の入口の範囲を読み直し、K-113・K-116 の一部を K-122 で訂正）

手元の `./gradlew verify` の時間を縮める（案 3）。Bolt の統合の前は軽い入口 `verifyQuick`（結合テストとサーバーのカバレッジの判定を除き、`-PitTests` で絞った IT を流せるようにし、JaCoCo は参考値として表示する）にし、全件の `verify` は Build and Test・リリースの前と `packagesJudgedByTotal` のパッケージに手を入れる Bolt で流し（当時の決まり。一覧は Intent 261108-ci-checks-release で消え、この場面も決まりから外れた。K-142）、`-PitForks` で手元だけ結合テストを並べて流せるようにする。画面のテスト（Vitest）を1回にする。CI は今までどおり `verify` を1本で流す。関わる決まり（`project.md` の Mandated、`team.md` の Way of Working・Testing Posture）を書き換える。今回は `verify` が何をどの順で流すか、結合テストの分け方と並列の設定、カバレッジの判定、CI、README を読んだ。「未検証」の項目は要件定義・Code Generation の前に確かめる。

| 番号 | 要点 | 主な部品 | 本文の場所 |
|---|---|---|---|
| K-111 | `verify` は 10 の段（準備・フォーマット・リンタ・ライセンスヘッダー・ビルド・単体テスト・結合テスト・カバレッジ・安全の検査・成果物）。段の順は `dependsOn` でなく、各タスクに「前の段のすべてのタスクの後」の `mustRunAfter` を付けて決める | `build-and-verify` | `architecture.md`（検査の入口 `verify` の流れ・Interaction Diagrams 8） |
| K-112 | 画面のテスト（Vitest）が 5 の段の `frontendTest` と 7 の段の `frontendCoverage`（計測つき）で2回流れ、性能のテスト2つも2回判定される | `build-and-verify`・`frontend-feature-preferences`・`frontend-shared-format` | `code-quality-assessment.md`（K-112） |
| K-113 | 結合テスト `integrationTest` は `*IT` 278 クラスを1つの JVM で順に流す。並列の設定（`maxParallelForks`・`gradle.properties`・JUnit の並列）も、IT を絞る `-P` の口も無い。対象DB の IT 39 クラスはクラスごとにコンテナを起動し、Docker が無いと CI では失敗・手元では飛ばす | `build-and-verify`・`backend-test-support` | `architecture.md`（検査の入口 `verify` の流れ） |
| K-114 | バックエンドのカバレッジの判定は `test.exec` と `integrationTest.exec` を合わせて行い、報告と判定のタスクはテストに `dependsOn` しない（`mustRunAfter` だけ）。IT を除くと判定は落ちる見込み（未検証） | `build-and-verify` | `code-quality-assessment.md`（K-114） |
| K-115 | CI（`ci.yml`）は `./gradlew verify` を引数なしで1本流す（`timeout-minutes: 60`、GitHub は `CI=true`） | `build-and-verify` | `code-quality-assessment.md`（K-115） |
| K-116 | `verifyQuick`・`-PitTests`・`-PitForks` を足すときの論点（段の表と順の決まりの重なり、古い実行記録、同時のコンテナ・ヒープ・番号の取り合い）。どれも未検証 | `build-and-verify`・`backend-test-support` | `architecture.md`（Improvement Opportunities） |
| K-117 | 書き換える決まりの文と、手順を直す文書の箇所（`project.md`・`team.md`・README・`docs/verification.md`） | `build-and-verify` | `code-quality-assessment.md`（K-117） |
| K-118 | 前の記録の訂正: Node は 26（K-81 は解消）、OSV-Scanner 2.7.0、`setup-gradle` v6.4.1、README は 164 行（K-88 の 1,678 行から縮み、手順は `docs/`）、`frontend/scripts/` は 6 ファイル | `build-and-verify`・`container-runtime` | `technology-stack.md`（K-118） |

### 前の Intent（261104-tz-options-perf）の主題（今回は再確認していない）

CI の `frontendCoverage` で、表示のタイムゾーンの選択肢の組み立ての性能のテスト（`TimeZoneOptionsPerformance.test.ts`、NFR2.1.6、100 ms 以内）が中央値 106 ms で2回目の失敗をした不具合の直し。目標と測り方は変えず、`buildTimeZoneOptions` を速くする。今回は組み立てが1件の名前ごとに何をしているか、整形器のキャッシュ、測り方とカバレッジの設定を読んだ。「未検証」の項目は要件定義・Code Generation の前に確かめる。

| 番号 | 要点 | 主な部品 | 本文の場所 |
|---|---|---|---|
| K-106 | 選択肢の組み立ては、数百の名前の1件ごとに `Intl.DateTimeFormat` の生成 1 回（7 項目）・`formatToParts` 2 回（同じ時点で壁時計を求め直す）・`getFormatter` 4 回・同じ時点の文字の読み直し 1 回を行い、表示の文字全体を組んでから時差だけを正規表現で切り出す | `frontend-feature-preferences`・`frontend-shared-format` | `architecture.md`（表示のタイムゾーンの選択肢の組み立て・Interaction Diagrams 7） |
| K-107 | 整形器のキャッシュの上限 128 が名前の数より小さく、組み立ての中で全体の捨て直しが起き、組み立ての後に表示のタイムゾーンの整形器が追い出されうる（結果は変わらない） | `frontend-shared-format` | `architecture.md`（表示のタイムゾーンの選択肢の組み立て） |
| K-108 | 測り方: 5 回の中央値、各回で整形器のキャッシュを空にし新しい配列で組み立てる。`frontendTest` と `frontendCoverage` の両方で、ほかのテストのファイルと同時に流れる。性能のテストを計測や並列から外す設定は無い | `frontend-feature-preferences`・`build-and-verify` | `code-quality-assessment.md`（K-108） |
| K-109 | 直し方の候補と制約: 時差だけを求める軽い関数（`formatToParts` 1 回・検査 1 回）、項目の少ない整形器（`longOffset` は前の Intent の NFR Design Q1: A との差）。`clearFormatterCache()` で消えない別のキャッシュは測り方を変える。`buildTimeZoneOptions.test.ts` の `formatInstant` の呼び出しを数えるテストが影響を受ける。新しい関数は `shared/format/timeZone` に置く（ESLint の決まり） | `frontend-feature-preferences`・`frontend-shared-format` | `code-quality-assessment.md`（K-109） |
| K-110 | 前の記録の訂正: `shared/format/formatDateTime.ts` は消え（U2）、利用者ごとの表示のタイムゾーンの設定がある（K-96 の「設定は無い」は古い）。K-90 の `coverage.include` は今は perf の伏せ字の道具を含む | `frontend-app-core`・`frontend-feature-preferences`・`build-and-verify` | `component-inventory.md`・`code-quality-assessment.md` |

### 前の Intent（261102-timezone-ui-polish）の主題（今回は再確認していない。K-96 の一部は K-110 で訂正）

N6（業務データの日時のタイムゾーンの変換。MySQL・MariaDB・PostgreSQL の3種類の型を含む）と画面の仕上げ（アプリのアイコンと favicon、ホームの中身、管理の仮の画面、管理メニューと業務メニューのアイコン）。今回は、日時の値が対象DB から画面まで・画面から対象DB までどう流れるかと、サイドバー・ホーム・管理の仮の画面・ページの頭の今の形を読んだ。「未検証」の項目は要件定義・設計の段で確かめる。

| 番号 | 要点 | 主な部品 | 本文の場所 |
|---|---|---|---|
| K-94 | 日時の型の対応は `ColumnValueCodec.valueType` の1か所。時差つきは MySQL・MariaDB の `timestamp` と PostgreSQL の `timestamptz`・`timetz` だけで、UTC にそろえ `+00:00` 付きの文字で返す。時差の無い型（`datetime`・`timestamp`・`date`・`time`）は DB の値のまま | `businessdata` | `architecture.md`（日時の値の流れ・Interaction Diagrams 5） |
| K-95 | 接続の時差: MySQL・MariaDB は両方のプールで接続を作るときに `SET time_zone = '+00:00'`。PostgreSQL はセッションの時差を設定せず、JVM の既定（`Asia/Tokyo`）に従う見込み（未検証）。時差の無い型の値がどの時差の壁時計かはアプリから分からない | `targetdb`・`container-runtime` | `architecture.md`（日時の値の流れ） |
| K-96 | 画面の日時の見せ方が二系統: 業務データは UTC の文字のまま（`T` を空白に）で「UTC で入れる」の案内つき入力、DSL・招待・利用者の管理は `shared/format/formatDateTime` で端末の時差に直す。`ListFormat` の `DATE`・`DATETIME`・`TIME` は画面で使われていない。利用者ごとの時差の設定は無い | `frontend-feature-tables`・`frontend-app-core` | `architecture.md`（日時の値の流れ） |
| K-97 | 主キー・参照の値・検索の値・作成の応答の主キー・監査の値は UTC の文字のまま往復する。表示だけを替えるなら往復は変えない。入力を地方時にするならオフセットを付けて送る（サーバーは `±hh:mm` を受け付ける）か、どこで直すかを設計で決める | `businessdata`・`frontend-feature-tables` | `architecture.md`（日時の値の流れ） |
| K-98 | 秒の端数: 既定の DSL の生成は MySQL・MariaDB の日時の精度を読まず、`datetime(6)` でも端数の上限 0 になる見込み（未検証）。手書きの `timestamp(3) with time zone` は DATETIME と誤る。更新の初めの値は端数を落とす | `businessdata`・`dslmanage`・`frontend-feature-tables` | `architecture.md`（日時の値の流れ） |
| K-99 | 範囲の端: MySQL の `time` の 24 時間を超える値・負の値は読めない値（`Unreadable`）、MySQL の `year`・PostgreSQL の `interval` は `OTHER`（読み取りだけの文字） | `businessdata` | `architecture.md`（日時の値の流れ） |
| K-100 | アイコンは make-you-chic-ui の 18 種だけで、テーブル・ビューらしい名前は無い。写しは画面の `allowedIcons.ts` とサーバーの `allowed-icons.txt` の2か所。管理の区画の 7 項目はどれも `icon` を置かず `list`、生成した DSL も `icon` を書かず `list` | `frontend-registry`・`navigation`・`make-you-chic-ui` | `code-structure.md`（画面の骨組みの仕上げ） |
| K-101 | 業務のメニューの応答 `GET /api/me/navigation` はテーブルかビューかの印を持たない。`MenuFilter` は `DslModel.findTable` で `DslTable.view` を引ける位置にある | `navigation`・`dsl` | `api-documentation.md` |
| K-102 | アプリのアイコン: `index.html` に favicon の `<link>` が無く `frontend/public/` も無い。見つからない画面の URL は `index.html` を返すため `/favicon.ico` に HTML が返る見込み（未検証）。`title` は `MasterSmith` に固定。AppShell にロゴの口は無く `topbarStart` だけ | `frontend-app-core`・`config`・`make-you-chic-ui` | `code-structure.md`（画面の骨組みの仕上げ） |
| K-103 | ホームは見出しと1文の説明と、業務のメニューが空・読み込みの失敗のときの案内だけ。メニューがあるときは中身が実質空 | `frontend-app-core` | `code-structure.md`（画面の骨組みの仕上げ） |
| K-104 | 管理の仮の画面（`/admin`）は確認の API を呼んで「今後の管理機能はこの画面に加わります」を出すだけ。管理の区画の先頭の項目「管理」（区画の見出しと同じ名前）。E2E 030・090・130 と単体テストの多くが頼る。`GET /api/admin/check` は k6 と E2E 130 も使う | `frontend-feature-admin`・`access` | `code-structure.md`（画面の骨組みの仕上げ） |
| K-105 | サブモジュールの固定先が知識ベースの記録より進んでいる: make-you-chic-ui `cae5fed`、java-mustache-processor `d61ba6d`（`0.1.0-6`）。K-86 の値は古い | `make-you-chic-ui`・`java-mustache-processor` | `dependencies.md` |

### 前の Intent（261101-maintenance-update）の所見（今回は再確認していない。K-86 の固定先は K-105 で訂正。K-81・K-88 は K-118 で訂正）

メンテナンスの Intent。依存の最新化（Java 25 系の最新、Node 24 → 26、サブモジュール2つ、GitHub Actions、コンテナのイメージ、検査の道具、Gradle の Wrapper、Spring Boot と連動する固定の見直し、`dependabot.yml` の `ignore` の見直し）、古いコンテナのイメージの片付け、前の Intent の小さな直し（P1・T1〜T3・A2・A3）、README.md と perf/README.md の最新化。今回はコードの振る舞いではなく、版がどこに何か所書かれているかと文書の構成を主に読んだ。「未検証」の項目は要件定義・設計の段で確かめる。

| 番号 | 要点 | 主な部品 | 本文の場所 |
|---|---|---|---|
| K-81 | Node の版が6つのファイル・10 か所に散らばり、`engines` の `<25` と `build.gradle.kts` 95 行の `startsWith("v24.")` が Node 26 を拒む。`test` の `NODE_OPTIONS=--no-experimental-webstorage` が Node 26 で使えるかは未検証 | `build-and-verify` | `technology-stack.md`（版の固定先） |
| K-82 | Java 25 と Temurin の JRE・JDK のイメージの固定先（toolchain・CI・Dockerfile・`perf/versions.env`・`docker/hikari-pool.sh`・README・perf/README）。JDK のイメージは Dependabot の知らせが来ない | `build-and-verify`・`container-runtime` | `technology-stack.md`（版の固定先） |
| K-83 | コンテナのイメージ（対象DB 3種・Mailpit・lgtm・collector・k6）の固定先が `compose.yaml`・`docker/perf/compose.yaml`・`TargetDbImages.java`・README・perf/README・`perf/versions.env` に分かれる（K-78 の後継）。今の値どうしは一致 | `container-runtime`・`perf-and-monitoring` | `technology-stack.md`（版の固定先）・`dependencies.md` |
| K-84 | 検査の道具の版: Gitleaks は `ci.yml`・`.pre-commit-config.yaml`・README、OSV-Scanner は `ci.yml`・README。Gradle Wrapper 9.8.2、Actions は SHA で固定、Playwright のブラウザは `@playwright/test` と同じ版 | `build-and-verify` | `technology-stack.md`（版の固定先） |
| K-85 | Spring Boot と連動する固定: **Jackson の BOM の上書きは既に無い**（lockfile の 3.1.7 は管理の版）。appender 2.28.1-alpha と networknt 3.0.6 は残り、H2 の `-Dh2.compactThreads=1` は3か所 | `build-and-verify` | `technology-stack.md`（K-85） |
| K-86 | サブモジュールの固定先は make-you-chic-ui `cab4207`・java-mustache-processor `8d44c36`（0.1.0）。README 37 行は `ecb07e9` のまま古い。A2（`braces` 3.0.3）と A3（依頼（5））は上流のソースで確かめる必要がある | `make-you-chic-ui`・`java-mustache-processor` | `dependencies.md` |
| K-87 | `dependabot.yml` の `ignore` は5つ（appender・networknt・`typescript` の大きな版・`eclipse-temurin` の大きな版・collector の `0.163.2-386`）。見直しの外す時期はコメントにある | `build-and-verify` | `dependencies.md` |
| K-88 | README.md（1,678 行）に経緯が溜まっている: 見出しの 25 個が Intent・単位・Bolt を名前に含み、本文の経緯の参照は 164 か所、「契約との差」3節、骨格の時点の説明（5 行・1608〜1634 行） | `build-and-verify`・`container-runtime` | `code-quality-assessment.md` |
| K-89 | perf/README.md（1,753 行）の末尾の「## 履歴」（1563〜1753 行、約 190 行）と本文の経緯の参照 139 か所、継ぎ足しの手順の番号 | `perf-and-monitoring` | `code-quality-assessment.md` |
| K-90 | T1・T2: perf の伏せ字の道具のカバレッジは Vitest の `coverage.include`（`src/` だけ）の外で、ESLint と `tsc` も当たらない（Prettier と oxlint だけ） | `perf-and-monitoring`・`build-and-verify` | `code-quality-assessment.md` |
| K-91 | T3: `BusinessDataWriteService.warnReference` は `stage` が null のときキーを出さず、`StepAttempt.Unexpected` の経路で `stage` の無い WARN になりうる（値は出ない） | `businessdata` | `code-quality-assessment.md` |
| K-92 | P1: 参照先の並びは文字の型を `SUBSTRING` で切って 1038 を避けるが、業務データの一覧の `page` の並べ替えはカラムのまま `LIMIT ? OFFSET ?`。深いページで 1038 が起きるかは未検証 | `businessdata` | `architecture.md` |
| K-93 | 古いコンテナのイメージ・ボリュームの片付けの対象は、今回は一覧していない（docker の操作をしない決まり） | `container-runtime` | `technology-stack.md`（手元の運用） |

### 前の Intent（261029-reference-lookup-cleanup）の所見（今回は再確認していない）

業務データの選択肢の他テーブル参照（REFERENCE）と参照ピッカー（LOOKUP）の本実装（前の Intent `261016-business-data-views` で意図的に先送りした部分）と、持ち越しの片付け（perf の JWT の自動の伏せ字、コンテナの版、Dependabot の i18next 系2件）。今回の範囲の事実はここに一覧し、本文は持ち主の文書に1回だけ書く。「未検証」と書いた項目は、要件定義・設計の段で確かめる仮説である。

| 番号 | 要点 | 主な部品 | 本文の場所 |
|---|---|---|---|
| K-74 | REFERENCE・LOOKUP は DSL（JSON Schema・モデル・意味の検証・既定の DSL の生成）までは完成しているが、業務データでは `ColumnWriteRulesFactory` が `lookup` を `text` に置き換え、選択肢は FIXED だけを返す（前の Intent の意図的な暫定 BR3.7）。定義の応答にも参照先の情報を載せる欄が無い | `businessdata`・`dsl` | `architecture.md`（Interaction Diagrams 4）・`api-documentation.md` |
| K-75 | 参照先の行を読む専用の API は無い。権限の引き口 `PermissionSnapshot.main(schema, table, column)`・`TableAccessResolver`・`RowSqlExecutor.search/detail` が流用の候補（未検証の仮説）。読み取りの目的 `ReadPurpose` の新設要否・参照先の権限が NONE のときの扱い・件数の上限は設計の判断 | `businessdata`・`role`・`targetdb` | `architecture.md`（Interaction Diagrams 4） |
| K-76 | 画面の流用点: `SearchPanel`・`BusinessDataTable`・`BusinessDataFormPage` の部品の選び方（formPart の分岐）・`shared/modal`。参照ピッカーに近い形だが未検証。make-you-chic-ui に無い部品は先に上流へ依頼する（`team.md`） | `frontend-feature-tables` | `component-inventory.md` |
| K-77 | perf の k6 の summary json に使い捨ての環境の JWT が `setup_data` として出る。自動の伏せ字の仕組みは `perf/` にも README にも無く、決まりは `project.md` の記述だけ（手で置換） | `perf-and-monitoring` | `code-quality-assessment.md` |
| K-78 | コンテナの版の固定先が分散している。`compose.yaml` の lgtm 0.38.0・Mailpit v1.32.0 のタグは動いている（または止まっている）コンテナと一致して見えるが、ダイジェストの一致は未検証 | `container-runtime`・`perf-and-monitoring` | `dependencies.md`・`technology-stack.md` |
| K-79 | Dependabot の i18next 系2件（#81、#82）は patch。画面での使い方は狭く影響は小さい見込み（未検証）。`dependabot.yml` の npm の `ignore` はこの2件を妨げない | `build-and-verify`・`frontend-app-layout-i18n` | `dependencies.md` |
| K-80 | 境界テスト・関門の確かめ: 参照先の読み取りを `businessdata` の内側に置く限り `BusinessDataBoundaryArchitectureTest` の許可は緩めずに済む見込み。`SQL_` の SpotBugs 除外は `RowSqlExecutor` のメソッド単位のため、新しい SQL のメソッドは除外の追加（計画に明記して承認）か既存の `search`／`detail` の再利用が要る | `businessdata`・`build-and-verify` | `code-quality-assessment.md` |

### 前の Intent（261027-perf-targets-review）の所見（今回は再確認していない範囲を含む）

性能・資源の目標（k6 の応答時間の線、`memory.peak`・`anon` の物差し）の見直しと、Dependabot の知らせの洗い出し。bugfix のため `backend/src/main` を変える見込みは低い。物差しを変えるのは、`team.md` により要件定義で依頼者が明示に決めたときだけで、変えた後も1回は前の物差しの値を並べて記録する（緩和の決定は 2026-10-28 に出ている。値と対象は要件定義で記録する）。

| 番号 | 要点 | 主な部品 | 本文の場所 |
|---|---|---|---|
| K-69 | k6 の合否の線の定義場所は `perf/k6/scenarios.js` の `thresholdsFor`。biz の読み取りは `iteration_duration` の `p(95)<1000` と `checks` の率 1、`http_req_duration` は記録だけ。DB の種類別の線は無い | `perf-and-monitoring` | `code-quality-assessment.md` |
| K-70 | `memory.peak`・`anon` の物差しの記述箇所（`perf/README.md` と `perf/dsl-mixed-memory.sh`）と現行の判定（peak/max < 0.9、anon に替えない） | `perf-and-monitoring` | `code-quality-assessment.md` |
| K-71 | `docker/perf/compose.yaml` の資源の上限の既定と、`perf/README.md` の運用値（MySQL 2304m・2688m など）が食い違う | `perf-and-monitoring` | `code-quality-assessment.md` |
| K-72 | Dependabot の現状: ignore は gradle 2件・npm 1件・docker 1件、groups 未使用。Tomcat・Jackson の上書きは外れて Spring Boot 4.1.2 の管理の版になっている。開いている PR は未確認 | `build-and-verify` | `dependencies.md`（一覧）・`technology-stack.md`（版） |
| K-73 | `perf/README.md`（1,359 行。2026-10-29 に再計測）・`perf/k6/scenarios.js`（2595 行）が大きく、同じ指標に前の Intent の結果と現行の判定が混在して、どの値が基準か読み取りにくい | `perf-and-monitoring` | `code-quality-assessment.md` |

### 前の Intent（261025-deps-perf-carryover）から続く所見（今回は再確認した範囲のみ更新）

| 番号 | 要点 | 主な部品 | 本文の場所 |
|---|---|---|---|
| K-62 | 一覧は同じ接続で `COUNT(*)`（上限なし）とページの文の2回を実行する（今回も `RowSqlBuilder.count`・`RowSqlExecutor.search` は変わっていないことを確かめた） | `businessdata` | `architecture.md` の Interaction Diagrams 1 |
| K-63 | 読み取りのプールの既定は上限 10・借りる待ち 3 秒（前回 5 から引き上げ済み）で、1要求が1接続を使い続ける | `targetdb` | `architecture.md` の Interaction Diagrams 1 |
| K-64 | 件数 `total` は確定した `long` だけで、「多数」を持てる形が API・画面・テストのどこにも無い | `businessdata`・`frontend-feature-tables` | `api-documentation.md` |
| K-65 | MySQL と MariaDB は同じ方言クラスで、別の最適化に使う口が無い | `businessdata`・`dsl` | `component-inventory.md` の `businessdata` |
| K-66 | 固定している部品の見直し: Spring Boot 4.1.2 に上がり、Tomcat・Jackson の上書きは外れた。appender と networknt の固定は残る | `build-and-verify` | `technology-stack.md`（一覧）・`dependencies.md` |
| K-67 | perf の biz の場面・種・資源の上限と、読み取りのプールを変える口 | `perf-and-monitoring` | `code-quality-assessment.md` |
| K-68 | `perf/README.md` の依頼の「(c) の grep の形」は、(c) の節 1003 行の 1021 行（今回は読み直していない） | `perf-and-monitoring` | `code-quality-assessment.md` |

前回の所見のうち今回も効く未確認の持ち越し（K-53 管理の可否が `users.admin_flag` だけで決まる件、K-59 `memory.peak` の測り方、K-60 make-you-chic-ui と axe-core）は、K-59 を除き今回も深く読んでいない（K-59 の測り方は K-70 で読み直した）。前の記録と `aidlc/spaces/default/memory/project.md` の記録を参照すること。

## Sources

- 開発担当の調査（2026-11-11）: `aidlc/spaces/default/intents/261110-deps-forks-review/inception/reverse-engineering/developer-scan.md`（読んだファイルの一覧はその Scan Coverage）。
- 開発担当の調査（2026-11-09）: `aidlc/spaces/default/intents/261108-ci-checks-release/inception/reverse-engineering/developer-scan.md`。
- 開発担当の調査（2026-11-08 の2回目）: `aidlc/spaces/default/intents/261108-ci-size-coverage/inception/reverse-engineering/developer-scan.md`。
- 開発担当の調査（2026-11-08）: `aidlc/spaces/default/intents/261107-verify-quick-local/inception/reverse-engineering/developer-scan.md`。
- 開発担当の調査（2026-11-06）: `aidlc/spaces/default/intents/261104-tz-options-perf/inception/reverse-engineering/developer-scan.md`。
- 開発担当の調査（2026-11-03）: `aidlc/spaces/default/intents/261102-timezone-ui-polish/inception/reverse-engineering/developer-scan.md`。
- 開発担当の調査（2026-11-01）: `aidlc/spaces/default/intents/261101-maintenance-update/inception/reverse-engineering/developer-scan.md`。
- 開発担当の調査（前回まで）: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/reverse-engineering/developer-scan.md`（今回）、`aidlc/spaces/default/intents/261027-perf-targets-review/inception/reverse-engineering/developer-scan.md`（今回）、`aidlc/spaces/default/intents/261025-deps-perf-carryover/inception/reverse-engineering/developer-scan.md`（前回）
- アーキテクトが読み取りで確かめ直したもの: `reverse-engineering-timestamp.md` の「確かめ方」
