# 目標の在庫（案） — Build and Test（Intent 261029-reference-lookup-cleanup）

Step 1 の在庫。`build-and-test-summary.md` の `## Target Verification Matrix` の元になる（Actual・Evidence・Verdict はこの段の実行の後に埋め、初めはどれも `Pending`）。

読んだ出典: 要件 `inception/requirements-analysis/requirements.md`、各単位（u1〜u5）の `nfr-requirements/`・`nfr-design/`・`infrastructure-design/`、各単位の `code-generation-plan.md` の `## Testing Contract`、`code-summary.md`・`generation-notes.md`・`traceability.json`（Deferred の行）、Code Generation の承認の場で Build and Test に回したレビューの指摘（各単位の `code-generation/reviews/review-01.md`・`review-02.md`、U3 の計画の B7.4 の表）、`perf/README.md` の「今の基準の一覧」と履歴、`aidlc/spaces/default/memory/team.md`・`project.md`。

持ち主の段の書き方: `build-and-test`（この段で確かめる）、`performance-validation`・`observability-setup`（後の段。この段では `Unverified`）、`ci-pipeline`（後の段。この段では `N/A`）。2つ書いた行は、この段の分（種・試し走り・記録）と後の段の分（判定）に分かれる。

件数: 横断・負荷の試験・指摘・要件の対応の行 60 件 ＋ 単位の NFR 要件の行 135 件（U1 41・U2 36・U3 24・U4 21・U5 13）＝ **195 件**。

## 1. 横断の目標（テスト・カバレッジ・時間・CI）

| Target ID | Source | Expected | How checked | Owning Stage |
|---|---|---|---|---|
| BT-VERIFY | `team.md` Way of Working・`project.md` Mandated（統合前の検査）・要件 NFR5 | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` が終了コード 0、失敗 0、SKIPPED は DB の種類ごとの前からの `assumeTrue` の 10 件だけ（コンテナが無いための飛ばしは 0） | 指揮役が `develop` の先頭で1回流す（Q1）。colima の環境変数・`caffeinate -dims`・`pmset` | build-and-test |
| BT-SEC-GATES | `team.md` 静的解析とセキュリティ検査・`project.md` Mandated | verify の中の書式・リンタ・ライセンスヘッダー・`spotbugsGate`（priority 1 と `SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION`）・`gitleaksScan`・OSV-Scanner（High 以上）・`perfEvidenceScan` が通る。SpotBugs の除外と境界テストは緩めていない | BT-VERIFY の出力のタスクごとの結果、`backend/config/spotbugs-exclude.xml` と ArchUnit の境界テストの差が無いことを `git diff` で確かめる | build-and-test |
| BT-COV-BE | `team.md` Testing Posture・要件 NFR5 | バックエンドの全体の合計 行 80% 以上・分岐 70% 以上、新しいパッケージと `packagesJudgedByTotal` の外のパッケージごとに同じ下限 | BT-VERIFY の `backend/build/reports/jacoco/test/jacocoTestReport.xml` から全体と `businessdata.*`・`targetdb.*` の値を読む | build-and-test |
| BT-COV-FE | `team.md` Testing Posture | フロントエンドの全体の合計 行 80%・分岐 70%（`thresholds`）が通る | BT-VERIFY の `frontendCoverage` | build-and-test |
| BT-COV-TABLES | `team.md` Testing Posture（新しい機能のフォルダの値を記録）・U3 `code-summary.md` 3.4・6節 | `src/features/tables/`（`references/` を含む）の行・分岐の値を記録する（合否は全体の合計で決まる） | BT-VERIFY の frontend のカバレッジの報告から読む | build-and-test |
| BT-COV-JUDGED | `team.md` Testing Posture（`packagesJudgedByTotal`） | 一覧の 7 パッケージ（`access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`）の `src/main` に、この Intent で手を入れていない（入れたなら一覧から外して下限を満たす）。一覧を増やしていない | 前の `main` の取り込み以後の `git diff --stat` で該当のパスを数える。`backend/build.gradle.kts` の一覧の差 | build-and-test |
| BT-VERIFY-TIME | `team.md` Testing Posture（verify と CI の時間）・U1 NFR5.3・U2 NFR5.23・U3 NFR2.45 | verify の時間を記録し、前の値（U1 B2 23分34秒・U2 26分48秒・U3 27分48秒・B7 26分25秒）と並べる | BT-VERIFY の始めと終わりの時刻 | build-and-test |
| BT-CI-TIME | `team.md` Testing Posture・`project.md` Deployment（job の時間で測る） | すべての統合の後の CI の検査の job が 42 分（上限 60 分の7割）の内。今の記録: 32分25秒（549d38b）・27分59秒（9d1d2dd）・34分49秒（b048ec1）・35分25秒・35分32秒・37分45秒（c91d831）・36分03秒（f1f3c2a）・27分49秒（711ddd0）・36分32秒（a75baec の2回目）・28分27秒（6412122）。超えたら分け方を諮る | `gh run view --json attempt,jobs` で取り直す | build-and-test |
| BT-CI-ATTEMPTS | `project.md` Deployment（試行ごとに記録）・U5 R-04・U3 R-04 | 前の `main` の取り込みの後のすべてのコミットの CI を試行ごとに記録する。再実行で通ったものは1回目の失敗とテストを書く（`a75baec` の1回目 `ReferenceSameResponseIT`、`a3099f8` で直した）。同じテストの二度目の失敗が無い | `gh run list`・`gh run view --json attempt,jobs` | build-and-test |
| BT-FLAKY-FIX | `team.md` 不安定なテストの二段の確かめ | `ReferenceSameResponseIT` の直し（`a3099f8`）に、直す前に落ちて直した後に通る確かめ（または試みの範囲と見立て）の記録がある | U3 の `generation-notes.md` とコミットの記録を読み取りで確かめる | build-and-test |
| BT-CI-MERGE | 各単位の `## Testing Contract` の applicable_notes（org: classic は CI の実行を統合の前に求める） | CI Pipeline（この計画の後の段、まだ `[ ]`）が、これらのテストを統合の前に流す CI を整える | — | ci-pipeline（N/A: CI Pipeline, later in this plan, sets up CI to run these tests before merge） |
| BT-E2E | `team.md` Way of Working（Intent の最後に E2E を1回）・U4 H7・U5 7節・U3 6節 | `./gradlew e2eTest` の全件が通る（B7 の後は 277 件） | 指揮役が `develop` の先頭で1回流す（Q1）。見本の PG・Mailpit の前提、`pmset` | build-and-test |
| BT-E2E-TIME | U3 NFR2.45・U3 `infrastructure-design/cicd-pipeline.md` | E2E の全体の時間が変更の前（13.1 分）の 1.5 倍の 19.7 分の内。超えたらファイルごとの時間を記録して諮る | BT-E2E の時間 | build-and-test |
| BT-E2E-REPORT | `team.md` Testing Posture（報告の json の検索）・U3 NFR1.47 | E2E の報告の json に資格情報・接続先・参照の値が無い | 報告の部品の確かめと json の文字列の検索 | build-and-test |
| BT-AXE | U3 NFR4.44・`security-design.md`・`team.md`（axe の記録） | E2E 195 の全件の通過と axe の違反 0 件（12 の状態 × 20 組 = 240 回）、axe-core の版が lockfile で1つ | BT-E2E の結果と `frontend/package-lock.json` の検索 | build-and-test |
| BT-GITLEAKS-RECORD | `project.md` Forbidden（認証の値を perf の記録へ置かない）・U5 NFR8.90 | この段の記録と `evidence/` に JWT の形 0 件、`gitleaks dir` 0 件 | 記録を書いた後に `gitleaks dir` と `eyJ` の検索 | build-and-test |

## 2. 負荷の試験（k6）と種

| Target ID | Source | Expected | How checked | Owning Stage |
|---|---|---|---|---|
| BT-K6-SEED | U1 NFR2.4・U1 `infrastructure-specification.md` 4.2・4.3 | 3種類の対象DB で種を流し、`biz_code200` 200 行・`biz_code201` 201 行・`biz_item` 100,000 行・`c_010` の目印の当たり・表の大きさを数える。MySQL は `--default-character-set=utf8mb4` | 使い捨ての環境（Q3） | build-and-test |
| BT-K6-SEED0 | U2 R-03 の直し（B7）・U2 NFR2.24 の注記 | 種の台本の最後の 0 行の確かめが、MySQL・MariaDB でも 0 行で終了コード 0（PostgreSQL は B7 で確かめ済み） | 使い捨ての環境で種を流す（Q3） | build-and-test |
| BT-K6-INSPECT | U1 NFR2.2・U2 NFR2.22・U5 NFR8.87 | 78 場面の `k6 inspect --include-system-env-vars` の場面と閾値の名前、`perf/k6/thresholds-test.sh`・`perf/dsl-mixed-memory-test.sh` が終了コード 0 | `perf/versions.env` の `K6_IMAGE` | build-and-test |
| BT-K6-DRYRUN | U1 NFR2.2・U2 NFR2.22・U1 R-03 | 参照の6つの場面の試し走り（1 VU・10 秒）で、閾値の名前の指標（`iteration_duration{scenario:…}`・`checks`）が summary json に出る（判定ではない） | 使い捨ての環境（Q3） | build-and-test |
| BT-K6-POOL5 | U1 NFR2.3・U1 `infrastructure-specification.md` R-03 | 使い捨てのアプリの対象DB の読み取りのプールの上限が 5（`hikaricp.connections.max` が 5 でなければ流さない） | `/actuator/metrics` を使い捨てのアプリにだけ公開して読む | build-and-test・performance-validation |
| BT-K6-BASELINE | U1 `infrastructure-specification.md` 4.1・R-03、U2 同 4.4・R-02、`perf/README.md` 履歴 H61 | 比べる相手（Intent 261016 の Performance Validation の上限 5 の結果）と、上限 10 の回を流すかを決めて記録する | Q4 | build-and-test（決定）・performance-validation（並べた記録） |
| BT-K6-JUDGE | U1 NFR2.1・NFR2.3・NFR2.5、U2 NFR2.21・NFR2.25、AC3.5.4 | 3種類の対象DB で参照の場面の p95（1,000 ms、`refPickerContains` は 3,000 ms）と `checks` の率 1 | Performance Validation の判定の回。この段は `Unverified`（Q3 で B を選べばこの段） | performance-validation |
| BT-K6-REFCREATE | U2 NFR2.22〜NFR2.24・U2 `code-summary.md` 5節 | `refCreateChecked` の種（`biz_refcreate` 0 行）と試し走りで 201 が返り、指標が出る。既存の書き込みの場面の意味が変わらない | 使い捨ての環境（Q3）、判定は Performance Validation | build-and-test・performance-validation |
| BT-K6-H4 | U4 H4・NFR8.69・AC4.1.6・BR4.3 | 試し走りの実際の summary json を道具の JSON の口で `evidence/` に置き、`--scan` と `gitleaks dir` で 0 件 | Q3 の試し走りの出力を使う | build-and-test |
| BT-MYSQL-1038 | U1 NFR 設計 R-04（`logical-components.md` 6節・`performance-design.md` 6節） | 既存の業務データの一覧で、約 10,000 字の値を数十行以上持つ `sortable` の文字のカラムを MySQL で並べ替えて1回流し、状態コードと `sqlState`・`vendorCode` を記録（値・メッセージは記録しない）。直すかは後の Intent | 使い捨ての環境の MySQL（Q3） | build-and-test |
| BT-DEEP-PICKER | U1 R-04・U1 `performance-design.md` 6節・`perf/README.md` 流す順 (3) | 深いページのピッカーの参考の測りの扱いを決め、手順書が実行できる状態にする（外すか、場面に足して流すか） | Q6 | build-and-test |
| BT-MEMPEAK-REQ | コード生成の承認の場の決定 B（U5 `code-summary.md` 9.1・9.4、`perf/README.md` 履歴 H69）・`team.md`（物差しの決まり） | `bizSearchWide`・`bizCreateLarge` の `memory.peak` の線を要件として決め、緩めるなら理由と前の線 90% を並べた記録を残し、README の一覧・本文・履歴をそろえる | Q5 | build-and-test（決定）・performance-validation（判定） |
| BT-MEMPEAK-JUDGE | Intent 261016 の U1 NFR2.5・U2 NFR2.8（今の線 90% 未満） | `bizSearchWide`・`bizCreateLarge` のアプリの `memory.peak` / `memory.max` < 0.9（Q5 で変われば新しい線と前の線を並べる） | Performance Validation の判定の回 | performance-validation |
| BT-PERF-RELAX-RECORD | 2026-10-28 の緩和の決定（依頼者のメモ）・Intent 261027 の要件 FR1.1〜FR1.5・この Intent の要件 NFR2 | 読み取りの p95 3 秒・2 秒、`dslMixed` 95%、対象DB の `anon` 92% が要件として理由と前の線つきで記録済みであること | 読み取りで確かめる（Intent 261027 の `requirements.md` FR1.1〜FR1.5 に記録あり） | build-and-test |
| BT-RESCUE-WARN | U5 NFR8.84・U5 R-03（救済の WARN は未計測） | 配備したアプリを起動し直したときの救済の WARN の件数（値は表示しない） | Q3 でアプリを止めて起動し直すときに数える | build-and-test |

## 3. Build and Test に回したレビューの指摘

| Target ID | Source | Expected | How checked | Owning Stage |
|---|---|---|---|---|
| REV-U1-R01 | U1 `code-generation/reviews/review-01.md`・`review-02.md` R-01 | U2 の設計 T-T1（TIMEOUT）と実装（UNAVAILABLE）の差が、U2 のテストと記録で UNAVAILABLE に読み替えられている | `ReferenceWriteTimeoutIT` と U2 `code-summary.md` 4節を読み取りで確かめる | build-and-test |
| REV-U1-R02 | 同 R-02 | `ReferenceTimeoutIT` の経過時間の上限が、B7 の後の CI の試行で落ちていない（落ちたら `team.md` の不安定なテストの決まり） | BT-CI-ATTEMPTS | build-and-test |
| REV-U1-R03 | 同 R-03 | NFR2.2（試し走り）・NFR5.1（パッケージごとの実測）を、この段の実測で補う | BT-K6-DRYRUN・BT-COV-BE | build-and-test |
| REV-U1-R04 | 同 R-04 | 深いページのピッカーの手順の扱いを決める | Q6（BT-DEEP-PICKER） | build-and-test |
| REV-U2-R02 | U2 `code-generation/reviews/review-01.md`・`review-02.md` R-02 | 接続の数の確かめに MariaDB と内部DB の借りた回数を足すか、足さない理由を差として記録する | Q2 | build-and-test |
| REV-U2-R04 | 同 R-04 | NFR5.21 のカバレッジ（`businessdata.domain`・`businessdata.service`、B7 の後）を実測で記録する | BT-COV-BE | build-and-test |
| REV-U3-R03 | U3 `code-generation/reviews/review-01.md` R-03・計画 B7.4 | 使っていない文言の鍵 `tables.reference.loading`（日英）の扱いを決める | Q2 | build-and-test |
| REV-U3-R04 | 同 R-04・計画 B7.4 | E2E 180・190・195 の差分、日英の鍵の漏れ、バックエンドの DTO との照合を確かめ、B4（711ddd0）と B7（6412122）の CI を試行ごとに記録する | 読み取りの確かめ・BT-CI-ATTEMPTS | build-and-test |
| REV-U4-R01 | U4 `code-generation/reviews/review-01.md`・`review-02.md` R-01 | README の「置換とは別の検査」の書き方（同じ形の定義を使う）を直すか決める | Q2 | build-and-test |
| REV-U4-R02 | 同 R-02 | 道具のカバレッジ（行 94.2%・分岐 90.61%）が verify の下限の外にあることと、下がっても気づく手段が無いことを記録する（`coverage.include` に足すかは後の Intent の候補） | 記録 | build-and-test |
| REV-U4-R03 | 同 R-03 | 道具に当たらない検査（ESLint: frontend の設定が `perf/` を対象にしない、型の検査: `.mjs` は tsc の対象外）の一覧と理由を記録する | 記録 | build-and-test |
| REV-U4-B7DIFF | U4 `review-02.md` の新しい Minor | B7 のコミットの変更ファイルに、道具 `perf/redact-k6-evidence.mjs` と U4 のテストが無い | `git show --stat 6412122`（指揮役が確かめ済み。記録に写す） | build-and-test |
| REV-U5-R04 | U5 `review-01.md`・`review-02.md` R-04 | B6（c91d831）の CI の試行ごとの結果（1回目で成功・37分45秒）を gh で確かめて記録 | BT-CI-ATTEMPTS | build-and-test |
| REV-U5-README | U5 `review-02.md` の新しい Minor | README の本文に 2304m を今の値とする文が無い、5,504 MiB の計算がある、本文に Intent の名前が無い、JWT の形が無い | 検索（指揮役が確かめ済み）。この段の最後に `gitleaks dir` と CB5・CB6 を流し直す | build-and-test |
| REV-WARN-STAGE | U2・U3 `review-02.md` の新しい Minor（`BusinessDataWriteService` の referenceStep） | 手がかりの無い想定外で `stage` の無い WARN が出る経路が残ることを既知の制約として記録する（値は出ない） | 記録 | build-and-test |

## 4. 要件の水準の対応と、Deferred の受け入れ基準

| Target ID | Source | Expected | How checked | Owning Stage |
|---|---|---|---|---|
| REQ-NFR1 | 要件 NFR1 セキュリティ | 単位の行 U1-NFR1.1〜1.15・U2-NFR1.21〜1.32・U3-NFR1.41〜1.47 がすべて満たされる（`team.md` の (a)〜(k)） | 単位の行の結果の集計 | build-and-test |
| REQ-NFR2 | 要件 NFR2 性能 | 単位の行 U1-NFR2.1〜2.10・U2-NFR2.21〜2.29・U3-NFR2.41〜2.45 | 単位の行の結果の集計（性能の判定は後の段） | build-and-test・performance-validation |
| REQ-NFR3 | 要件 NFR3 接続 | U1-NFR3.1〜3.8・U2-NFR3.21〜3.28（1要求で読み取りの接続 1 本まで、内部DB を持ったまま待たない） | `ReferenceConnectionUsageIT`・`ReferenceWriteConnectionUsageIT` | build-and-test |
| REQ-NFR4 | 要件 NFR4 アクセシビリティ | U3-NFR4.41〜4.45 | 画面のテストの vitest-axe と E2E 195 | build-and-test |
| REQ-NFR5 | 要件 NFR5 テスト | BT-COV-BE・BT-COV-FE・E2E は参照ピッカーの代表の流れ 1 本（190）・権限を欠く利用者は結合テスト | BT-VERIFY・BT-E2E | build-and-test |
| REQ-NFR6 | 要件 NFR6 多言語 | U1-NFR6.1・U2-NFR6.21・U3-NFR6.41〜6.42・U4-NFR6.61 | verify と日英の鍵の照合（REV-U3-R04） | build-and-test |
| REQ-NFR7 | 要件 NFR7 互換性 | U1-NFR7.1〜7.3・U2-NFR7.21〜7.22・U3-NFR7.41・U4-NFR7.61（DSL の書式の版と内部DB のスキーマを変えない） | 移行ファイルと `dsl-schema-v2.json` の差が無いこと、verify | build-and-test |
| REQ-NFR8 | 要件 NFR8 運用 | U4-NFR8.61〜8.71・U5-NFR8.81〜8.90 | 単位の行の結果の集計 | build-and-test |
| REQ-NFR9 | 要件 NFR9 供給網 | U1-NFR9.1・U2-NFR9.21・U3-NFR9.41・U4-NFR9.61〜9.65・U5-NFR9.81〜9.82（新しい依存なし、`ignore-scripts`） | lockfile の差の確かめと verify の OSV-Scanner | build-and-test |
| REQ-FR8 | 要件 FR8.1・FR8.2（Dependabot #81・#82） | `41b8bf5` で版と lockfile を取り込み済み、`hasInstallScript` の増減の記録がある、PR が閉じている | gh で PR の状態を確かめ、U1 の `generation-notes.md` の記録を読む | build-and-test |
| AC-U1-AC3.5.4 | U1 `traceability.json`（Deferred） | 3種類の対象DB での参照の場面の応答時間 | BT-K6-JUDGE | performance-validation |
| AC-U4-AC4.1.6 | U4 `traceability.json`（Deferred、H2・H4） | U4 のテストのファイルごとの passed、実際の summary json の `--scan` と `gitleaks dir` 0 件 | BT-K6-H4・U4-NFR5.62 | build-and-test |
| AC-U4-BR4.3 | U4 `traceability.json`（Deferred、H4） | 置いた後に evidence の検査と `gitleaks dir` を流して 0 件を確かめてからコミットを提案する手順が実際に動く | BT-K6-H4 | build-and-test |
| OBS-PANELS | U1 NFR2.10・NFR3.8、U2 NFR2.29・NFR3.28 | ダッシュボードのパネル 46・47 と区画の説明、警報 `ms-biz-target-db` が新しい道・定義の道・保存前の確かめの 503 で firing | Observability Setup が uri を確かめてから書く。この段は `Unverified` | observability-setup |

## 5. 単位の NFR 要件（135 件）

`traceability` の欄は各単位の Code Generation の `traceability.json` の状態。OK の行は、Code Generation で書いたテストがこの段の verify・E2E の全件で通ることで確かめる。

### U1 u1-reference-read（41 件）

| Target ID | Source | Expected | How checked | Owning Stage |
|---|---|---|---|---|
| U1-NFR1.1 | `u1-reference-read/nfr-requirements/security-requirements.md` NFR1.1（traceability: OK） | 参照先を読む2本の API（定義の `purpose` が `CREATE`・`UPDATE` の選択肢と、`POST /api/tables/reference/rows` の検… | cleanTest 付き verify（`ReferenceAuthorizationApiIT.java` ほか） | build-and-test |
| U1-NFR1.2 | `u1-reference-read/nfr-requirements/security-requirements.md` NFR1.2（traceability: OK） | 権限が無い・参照元の名前が DSL に無い・参照のカラムでない・REFERENCE に検索・条件のカラムが `searchFields` に無い・ラベルの値を型として読めない・ラベ… | cleanTest 付き verify（`ReferenceSameResponseIT.java` ほか） | build-and-test |
| U1-NFR1.3 | `u1-reference-read/nfr-requirements/security-requirements.md` NFR1.3（traceability: OK） | 参照先のスキーマ・テーブル・`valueColumn`・ラベルのカラム・検索と一覧の列は、要求から受け取らず、読み出しのたびに同じ写しの DSL から引き、DSL の名前と照らして… | cleanTest 付き verify（`ReferenceSameResponseIT.java` ほか） | build-and-test |
| U1-NFR1.4 | `u1-reference-read/nfr-requirements/security-requirements.md` NFR1.4（traceability: OK） | 要求の大きさと入力値に上限を置く: 新しい道の本文の上限は、既存の読み取りの道と同じ 65,536 バイト（`RequestBodyLimitRoute` に道を1つ足す。超えたら… | cleanTest 付き verify（`ReferenceRequestLimitIT.java` ほか） | build-and-test |
| U1-NFR1.5 | `u1-reference-read/nfr-requirements/security-requirements.md` NFR1.5（traceability: OK） | 参照先の行の値・ラベル・セル・主キーの値と、対象DB の接続情報を、アプリのログ・トレースの属性・エラー応答に含めない（`team.md` の (d)、`project.md` の… | cleanTest 付き verify（`ReferenceSecretLeakIT.java` ほか） | build-and-test |
| U1-NFR1.6 | `u1-reference-read/nfr-requirements/security-requirements.md` NFR1.6（traceability: OK） | JDBC の例外のメッセージは、応答にもアプリのログにも出さない。既存の `RowQueryFailure`（メッセージと原因を捨てた形）だけを上へ渡す（AC1.4.3、BR10.… | cleanTest 付き verify（`ReferenceTimeoutIT.java` ほか） | build-and-test |
| U1-NFR1.7 | `u1-reference-read/nfr-requirements/security-requirements.md` NFR1.7（traceability: OK） | ピッカーの部分一致（`CONTAINS`）の値の `%`・`_`・エスケープの文字は、一致の条件として働かない（`team.md` の (e)、FR2.6、BR5.5）。既存の部分… | cleanTest 付き verify（`AbstractReferenceReadIT.java` ほか） | build-and-test |
| U1-NFR1.8 | `u1-reference-read/nfr-requirements/security-requirements.md` NFR1.8（traceability: OK） | ラベルと値は信頼できない入力として扱い、サーバーは加工せず JSON の文字列としてだけ返す（`team.md` の (f)）。HTML として描かない・リンクにしないことの確かめ… | cleanTest 付き verify（`AbstractReferenceReadIT.java` ほか） | build-and-test |
| U1-NFR1.9 | `u1-reference-read/nfr-requirements/security-requirements.md` NFR1.9（traceability: OK） | U2 が呼ぶ保存前の確かめの口 `check` は、参照でないカラム・`NO_PERMISSION`・型として読めない値・厳密に一致する行が無い場合・一致する行の値が 10,000… | cleanTest 付き verify（`ReferenceLookupTest.java` ほか） | build-and-test |
| U1-NFR1.10 | `u1-reference-read/nfr-requirements/security-requirements.md` NFR1.10（traceability: OK） | 参照先が複合の主キー・主キーの無いテーブル・ビュー・自己参照・`valueColumn` が一意でないテーブルのとき、REFERENCE と LOOKUP の両方で 500 にせず… | cleanTest 付き verify（`AbstractReferenceReadIT.java` ほか） | build-and-test |
| U1-NFR1.11 | `u1-reference-read/nfr-requirements/security-requirements.md` NFR1.11（traceability: OK） | 選択肢・検索・ラベル・`check` を MySQL・MariaDB・PostgreSQL のすべてで確かめる（`team.md` の (i)、AC2.1.16、AC1.4.5）。… | cleanTest 付き verify（`AbstractReferenceReadIT.java` ほか） | build-and-test |
| U1-NFR1.12 | `u1-reference-read/nfr-requirements/security-requirements.md` NFR1.12（traceability: OK） | 既存の境界テスト（`BusinessDataBoundaryArchitectureTest`・`TargetDbBoundaryArchitectureTest`・`RoleBo… | cleanTest 付き verify（`BusinessDataBoundaryArchitectureTest.java` ほか） | build-and-test |
| U1-NFR1.13 | `u1-reference-read/nfr-requirements/security-requirements.md` NFR1.13（traceability: OK） | 新しい API は「ログインだけ」（`@ApiAccess(AUTHENTICATED)`）に明示して分類し、権限は業務処理の層で判定する（BR11.1、AC2.1.14） | cleanTest 付き verify（`ApiAccessArchitectureTest.java` ほか） | build-and-test |
| U1-NFR1.14 | `u1-reference-read/nfr-requirements/security-requirements.md` NFR1.14（traceability: OK） | 判定の純粋な関数（`accessOf`・同じ値を落とす関数・使える条件を選ぶ関数・固定の並びを作る関数・`sameReferenceValue`）に、jqwik の性質ベースのテス… | cleanTest 付き verify（`ReferencePolicyPropertyTest.java` ほか） | build-and-test |
| U1-NFR1.15 | `u1-reference-read/nfr-requirements/observability-requirements.md` NFR1.15（traceability: OK） | 参照先の読み取りの対象DB の失敗は、code に写す所で WARN を1行だけ、スタックトレースなしで出し、キーと値で次だけを出す: `purpose`（= `REFERENCE… | cleanTest 付き verify（`ReferenceSecretLeakIT.java` ほか） | build-and-test |
| U1-NFR2.1 | `u1-reference-read/nfr-requirements/performance-requirements.md` NFR2.1（traceability: Deferred） | 参照先の読み取りの応答時間は、要件 NFR2 の線を目標とし、緩めない。場面ごとに k6 の `checks` の率 1 も満たす。 ・選択肢つきの定義の取得（REFERENCE … | Performance Validation の k6 の判定の回（3種類の対象DB）。この段は Unverified | performance-validation |
| U1-NFR2.2 | `u1-reference-read/nfr-requirements/performance-requirements.md` NFR2.2（traceability: OK） | k6 の場面の判定の決まり（`project.md` の学び）: どの場面も1回の繰り返しに要求1つとし、繰り返しの中に待ちを置かない。判定は単調な時計の `iteration_d… | `k6 inspect --include-system-env-vars` と短い試し走り（各場面 1 VU・10 秒）で閾値の名前の指標が出ること（レビュー R-03: traceability は OK だが試し走りは未実施） | build-and-test |
| U1-NFR2.3 | `u1-reference-read/nfr-requirements/performance-requirements.md` NFR2.3（traceability: Deferred） | 対象DB を使う場面は、MySQL・MariaDB・PostgreSQL の3種類で、1種類ずつ使い捨ての環境を作って流し、どの種類でも NFR2.1 を満たす（AC3.5.4）。… | Performance Validation の k6 の判定の回（3種類の対象DB）。この段は Unverified | performance-validation |
| U1-NFR2.4 | `u1-reference-read/nfr-requirements/performance-requirements.md` NFR2.4（traceability: Deferred） | 測るデータは既存の perf の種を使い回す（Q3: A）。LOOKUP の参照先は既存の大きな表 `biz_item`（100,000 行。部分一致は既存の `bizSearch… | 種を3種類の対象DB に流し、行の数（200・201・100,000）・`c_010` の目印の当たり・表の大きさを数える。MySQL は `utf8mb4` | build-and-test |
| U1-NFR2.5 | `u1-reference-read/nfr-requirements/performance-requirements.md` NFR2.5（traceability: Deferred） | 測るまでは各目標を `Unverified` とし、持ち主の段（Performance Validation）を明記して引き継ぐ。満たさないときに目標の値や物差し（`iterati… | Performance Validation の k6 の判定の回（3種類の対象DB）。この段は Unverified | performance-validation |
| U1-NFR2.6 | `u1-reference-read/nfr-requirements/scalability-requirements.md` NFR2.6（traceability: OK） | 読む行の数を、問い合わせの上限（`LIMIT`）と JDBC の取り出しの上限（`setMaxRows`）の両方で同じ値に絞る（`team.md` の (c)、BR8.6）: RE… | cleanTest 付き verify（`RowSqlExecutorTest.java` ほか） | build-and-test |
| U1-NFR2.7 | `u1-reference-read/nfr-requirements/scalability-requirements.md` NFR2.7（traceability: OK） | ラベルとピッカーの一覧の列は、既存の一覧と同じ 100 文字（`LIST_TEXT_LIMIT`）で切って読み、切ったことの印は返さない（Q4: A、BR7.3）。バイナリ・pas… | cleanTest 付き verify（`AbstractReferenceReadIT.java` ほか） | build-and-test |
| U1-NFR2.8 | `u1-reference-read/nfr-requirements/scalability-requirements.md` NFR2.8（traceability: OK） | 読み取り側の値の長さに上限を置く（F1: A）。`valueColumn` は SQL の側で 10,001 文字に切って読み（切った値は比べにも返す値にも使わない）、読んだ長さが… | cleanTest 付き verify（`AbstractReferenceReadIT.java` ほか） | build-and-test |
| U1-NFR2.9 | `u1-reference-read/nfr-requirements/scalability-requirements.md` NFR2.9（traceability: OK） | 1つの定義に置ける REFERENCE の数には上限を置かない（機能設計の Q3: A。DSL のカラムの数の既存の上限が実際の上限）。数が多いときの時間は、定義の取得の全体の上限… | cleanTest 付き verify（`ReferenceConnectionUsageIT.java` ほか） | build-and-test |
| U1-NFR2.10 | `u1-reference-read/nfr-requirements/observability-requirements.md` NFR2.10（traceability: Deferred） | 手元の監視（grafana/otel-lgtm）のダッシュボードの業務データの区画に、新しい道 `POST /api/tables/reference/rows` が既存の式（`u… | Observability Setup が uri・指標を確かめて書き、警報の firing を使い捨ての環境で確かめる（この段は Unverified） | observability-setup |
| U1-NFR3.1 | `u1-reference-read/nfr-requirements/scalability-requirements.md` NFR3.1（traceability: OK） | 1要求で借りる対象DB の読み取りの接続は1回まで。定義では読む REFERENCE をすべて同じ接続で順に読み、読む項目が無ければ借りない。検索・ラベルは1回借りて文を1つだけ流… | cleanTest 付き verify（`ReferenceConnectionUsageIT.java` ほか） | build-and-test |
| U1-NFR3.2 | `u1-reference-read/nfr-requirements/scalability-requirements.md` NFR3.2（traceability: OK） | 内部DB の接続を持ったまま対象DB を待たない。権限の写しを取り終えて内部DB の接続を返してから、対象DB の接続を借りる（BR8.3） | cleanTest 付き verify（`ReferenceConnectionUsageIT.java` ほか） | build-and-test |
| U1-NFR3.3 | `u1-reference-read/nfr-requirements/scalability-requirements.md` NFR3.3（traceability: OK） | 同時の数は、既存の対象DB の読み取りのプール（上限 既定 10）を一覧・詳細と共有する。1要求1本で、内部DB と2本を同時に持たないため、詰まりは「同時の要求の数 > プールの… | cleanTest 付き verify（`ReferenceConnectionUsageIT.java` ほか） | build-and-test |
| U1-NFR3.4 | `u1-reference-read/nfr-requirements/reliability-requirements.md` NFR3.4（traceability: OK） | `ReadPurpose` に `REFERENCE` を足し、問い合わせ1回の時間の上限を既定 5 秒にする（Q1: A、BR8.1）。既存と同じく `TargetDbPrope… | cleanTest 付き verify（`TargetDbSettingsTest.java` ほか） | build-and-test |
| U1-NFR3.5 | `u1-reference-read/nfr-requirements/reliability-requirements.md` NFR3.5（traceability: OK） | 定義の取得で参照先を読む全体の時間の上限を 10 秒にする（Q2: A、BR8.4）。注入した時計で最初の選択肢を読む前に締め切りを決め、各問い合わせの前に残りを秒に切り上げ、1 … | cleanTest 付き verify（`ReferenceReadDeadlineTest.java` ほか） | build-and-test |
| U1-NFR3.6 | `u1-reference-read/nfr-requirements/reliability-requirements.md` NFR3.6（traceability: OK） | 時間切れ・接続の失敗の後は、同じ接続で続けて読まない。既存の壊れた印を立て、残りの項目を読まずに定義の取得ごと失敗にする。項目ごとの 503 にはしない（BR8.5、AC1.1.4… | cleanTest 付き verify（`ReferenceLookupTest.java` ほか） | build-and-test |
| U1-NFR3.7 | `u1-reference-read/nfr-requirements/reliability-requirements.md` NFR3.7（traceability: OK） | 失敗の分け方は既存の業務データの読み取りと同じにする（BR1.5）: 時間切れと全体の上限の超過は 503 `BUSINESS_TARGET_DB_TIMEOUT`、接続の失敗・読… | cleanTest 付き verify（`ReferenceTimeoutIT.java` ほか） | build-and-test |
| U1-NFR3.8 | `u1-reference-read/nfr-requirements/observability-requirements.md` NFR3.8（traceability: Deferred） | 参照先の読み取りの 503（`TIMEOUT`・`UNAVAILABLE`）は、既存の警報 `ms-biz-target-db`（業務データの API の 503 が 5 分に1件… | Observability Setup が uri・指標を確かめて書き、警報の firing を使い捨ての環境で確かめる（この段は Unverified） | observability-setup |
| U1-NFR5.1 | `u1-reference-read/nfr-requirements/security-requirements.md` NFR5.1（traceability: OK） | 行カバレッジ 80% 以上・分岐カバレッジ 70% 以上を、全体と、この単位が足す・変えるパッケージごとに満たす（`team.md`、AC3.5.5）。`businessdata`… | `:backend:cleanTest :backend:cleanIntegrationTest verify` の jacoco の報告から `businessdata.*`・`targetdb.*` のパッケージごとの値を記録（レビュー R-03） | build-and-test |
| U1-NFR5.2 | `u1-reference-read/nfr-requirements/security-requirements.md` NFR5.2（traceability: OK） | この単位が手を入れる見込みのパッケージ（`businessdata.*`・`targetdb.domain`・`targetdb.config`）は、`packagesJudged… | verify と読み取りの確かめ（`build.gradle.kts`） | build-and-test |
| U1-NFR5.3 | `u1-reference-read/nfr-requirements/performance-requirements.md` NFR5.3（traceability: Deferred） | この単位のコード生成の後と Build and Test で、`./gradlew verify` の時間と CI の検査の job の時間を測って記録する。CI の上限（60 分… | verify の時間（cleanTest 付き）と CI の検査の job の時間を試行ごとに記録、42 分の線と比べる | build-and-test |
| U1-NFR6.1 | `u1-reference-read/nfr-requirements/reliability-requirements.md` NFR6.1（traceability: OK） | 新しい誤りの `code` は作らず、既存の code を使い回す（BR1.6）。1つの code に1つの状態コードを固定する決まり（`team.md`・NFR6）を崩さない。誤… | cleanTest 付き verify（`BusinessDataProblemTypeCatalogTest.java` ほか） | build-and-test |
| U1-NFR7.1 | `u1-reference-read/nfr-requirements/reliability-requirements.md` NFR7.1（traceability: OK） | DSL の書式の版と内部DB のスキーマ（移行ファイル）を変えない（NFR7、前提 A7）。この単位は移行ファイルを足さない | verify と読み取りの確かめ（`dsl-schema-v2.json`） | build-and-test |
| U1-NFR7.2 | `u1-reference-read/nfr-requirements/reliability-requirements.md` NFR7.2（traceability: OK） | 定義の応答は足すだけにする。`purpose` は任意で既定 `VIEW`、各カラムの `reference` は `VIEW` では `null`、`formPart` は `V… | cleanTest 付き verify（`AbstractReferenceReadIT.java` ほか） | build-and-test |
| U1-NFR7.3 | `u1-reference-read/nfr-requirements/reliability-requirements.md` NFR7.3（traceability: OK） | 一覧・詳細は参照先を読まない（FR4.1、BR12.4）。一覧・詳細の対象DB の接続の数と文の数は今と同じ | cleanTest 付き verify（`ReferenceConnectionUsageIT.java` ほか） | build-and-test |
| U1-NFR9.1 | `u1-reference-read/nfr-requirements/tech-stack-decisions.md` NFR9.1（traceability: OK） | この単位は新しい依存（Gradle の依存・npm のパッケージ・コンテナのイメージ）を足さない。要件はすべて採用済みの部品で満たす。足す必要が出たときは、採用の前にライセンスを確… | verify と読み取りの確かめ（`gradle.lockfile`） | build-and-test |


### U2 u2-reference-write-check（36 件）

| Target ID | Source | Expected | How checked | Owning Stage |
|---|---|---|---|---|
| U2-NFR1.21 | `u2-reference-write-check/nfr-requirements/security-requirements.md` NFR1.21（traceability: OK） | 保存前の参照の確かめは、作成・更新の要求ごとにサーバー側で、①〜④ で取った同じ権限の写し（作業ロール）で判定する（BR4.2・C6）。参照元のカラムを書ける権限だけを持ち、参照先… | cleanTest 付き verify（`ReferenceWriteAuthorizationApiIT.java` ほか） | build-and-test |
| U2-NFR1.22 | `u2-reference-write-check/nfr-requirements/security-requirements.md` NFR1.22（traceability: OK） | 参照先に無い値・読めない参照先の値・参照先の型として読めない値の拒否は、状態コード 409・code `BUSINESS_REFERENCE_NOT_FOUND`・項目の誤り `R… | cleanTest 付き verify（`AbstractReferenceWriteIT.java` ほか） | build-and-test |
| U2-NFR1.23 | `u2-reference-write-check/nfr-requirements/security-requirements.md` NFR1.23（traceability: OK） | 確かめる参照と参照先（スキーマ・テーブル・`valueColumn`）は、要求1件の権限の写しの DSL から引き、要求の本文の名前や値から参照先を作らない（`team.md` の… | cleanTest 付き verify（`ReferenceWriteAuthorizationApiIT.java` ほか） | build-and-test |
| U2-NFR1.24 | `u2-reference-write-check/nfr-requirements/security-requirements.md` NFR1.24（traceability: OK） | 参照先の値はバインド変数で渡し、識別子は DSL と照らした名前だけを引用する（`project.md` の Forbidden）。U2 は SQL を組み立てず、U1 の口（`c… | cleanTest 付き verify（`AbstractReferenceWriteIT.java` ほか） | build-and-test |
| U2-NFR1.25 | `u2-reference-write-check/nfr-requirements/security-requirements.md` NFR1.25（traceability: OK） | 参照の値・参照先の行の値・本文の行の値と、対象DB の接続情報を、アプリのログ・トレースの属性・エラー応答・監査の行に含めない（`team.md` の (d)、`project.m… | cleanTest 付き verify（`ReferenceWriteSecretLeakIT.java` ほか） | build-and-test |
| U2-NFR1.26 | `u2-reference-write-check/nfr-requirements/security-requirements.md` NFR1.26（traceability: OK） | 作成の必須の参照項目（NOT NULL）が読めない（`NO_PERMISSION`）・多すぎる（`TOO_MANY`）とき、値を送れば参照先を問い合わせずに NFR1.22 と同じ… | cleanTest 付き verify（`AbstractReferenceWriteIT.java` ほか） | build-and-test |
| U2-NFR1.27 | `u2-reference-write-check/nfr-requirements/security-requirements.md` NFR1.27（traceability: OK） | 参照先が複合の主キー・主キーの無いテーブル・ビュー・自己参照・`valueColumn` が一意でないテーブルのとき、保存前の確かめを 500 にせず、決めた動作（U1 の `ch… | cleanTest 付き verify（`AbstractReferenceWriteIT.java` ほか） | build-and-test |
| U2-NFR1.28 | `u2-reference-write-check/nfr-requirements/security-requirements.md` NFR1.28（traceability: OK） | 保存前の確かめを MySQL・MariaDB・PostgreSQL のすべてで確かめる（`team.md` の (i)）。参照元と参照先の型の違い（`INT` と `BIGINT`… | cleanTest 付き verify（`AbstractReferenceWriteIT.java` ほか） | build-and-test |
| U2-NFR1.29 | `u2-reference-write-check/nfr-requirements/security-requirements.md` NFR1.29（traceability: OK） | 確かめと保存の間に参照先の行が消えたときは、既存の外部キー違反の変換（409 `BUSINESS_REFERENCE_NOT_FOUND`）を最後の守りにし、行を保存しない（BR6… | cleanTest 付き verify（`AbstractReferenceWriteIT.java` ほか） | build-and-test |
| U2-NFR1.30 | `u2-reference-write-check/nfr-requirements/security-requirements.md` NFR1.30（traceability: OK） | 参照の確かめの拒否は、既存の作成・更新の出来事に結果 `FAILURE`・理由 `BUSINESS_REFERENCE_NOT_FOUND` で残し、確かめのための読み取りそのもの… | cleanTest 付き verify（`ReferenceWriteAuditIT.java` ほか） | build-and-test |
| U2-NFR1.31 | `u2-reference-write-check/nfr-requirements/security-requirements.md` NFR1.31（traceability: OK） | 判定の純粋な関数（値が更新前と同じかを型つきの値で比べる関数、必須の参照項目の状態と本文から結果を決める関数、確かめの結果を拒否と code に写す関数）に、jqwik の性質ベー… | cleanTest 付き verify（`ReferenceWritePolicyPropertyTest.java` ほか） | build-and-test |
| U2-NFR1.32 | `u2-reference-write-check/nfr-requirements/observability-requirements.md` NFR1.32（traceability: OK） | 保存前の段の対象DB の失敗（5xx 相当の `TIMEOUT`・`UNAVAILABLE` と 409 `MISMATCH`）のログは、`team.md` の「例外のログは変換す… | cleanTest 付き verify（`ReferenceWriteSecretLeakIT.java` ほか） | build-and-test |
| U2-NFR2.21 | `u2-reference-write-check/nfr-requirements/performance-requirements.md` NFR2.21（traceability: Deferred） | 参照の確かめが入った作成の応答時間は、既存の書き込みの線と同じ p95 < 1,000 ms を目標とし、緩めない（Q1: A）。k6 の `checks` の率 1 も満たす | Performance Validation の k6 の判定の回（3種類の対象DB）。この段は Unverified | performance-validation |
| U2-NFR2.22 | `u2-reference-write-check/nfr-requirements/performance-requirements.md` NFR2.22（traceability: Deferred） | 新しい場面（仮の名前 `refCreateChecked`。確定は Build and Test）は、参照のカラムを持つ専用の参照元の表への作成で、保存前の段のいちばん重い道（必須… | `refCreateChecked` の種の流し込みと試し走り（1 VU・10 秒）、`k6 inspect`（判定は Performance Validation） | build-and-test・performance-validation |
| U2-NFR2.23 | `u2-reference-write-check/nfr-requirements/performance-requirements.md` NFR2.23（traceability: Deferred） | 既存の場面の意味を保つ（Q3: A）: 性能の試験の DSL では `biz_new` の `ref_id` を参照（REFERENCE）にしない。`bizWritePoolLim… | 既存の書き込みの場面の意味が変わらないこと（試し走りと、判定の回の並べた記録。プール上限 10→5 の注記。Q4） | build-and-test・performance-validation |
| U2-NFR2.24 | `u2-reference-write-check/nfr-requirements/performance-requirements.md` NFR2.24（traceability: Deferred） | 判定の決まりは U1 の性能の要件と同じにする（`project.md` の学び）: 判定は単調な時計の `iteration_duration` で行い、`http_req_du… | iteration_duration で判定する閾値（`thresholds-test.sh`）と試し走りで指標が出ること。種の 0 行の確かめを MySQL・MariaDB でも流す | build-and-test・performance-validation |
| U2-NFR2.25 | `u2-reference-write-check/nfr-requirements/performance-requirements.md` NFR2.25（traceability: Deferred） | 測るまでは NFR2.21 を `Unverified` とし、持ち主の段（Performance Validation）を明記して引き継ぐ。満たさないときに目標の値や物差し（`i… | Performance Validation の k6 の判定の回（3種類の対象DB）。この段は Unverified | performance-validation |
| U2-NFR2.26 | `u2-reference-write-check/nfr-requirements/scalability-requirements.md` NFR2.26（traceability: OK） | 保存1回で読む行の数に上限を置く（`team.md` の (c)）。U2 は U1 の口の上限（問い合わせの上限と JDBC の取り出しの上限の両方で 201 行）をそのまま使い、… | cleanTest 付き verify（`BusinessDataWriteReferenceTest.java` ほか） | build-and-test |
| U2-NFR2.27 | `u2-reference-write-check/nfr-requirements/scalability-requirements.md` NFR2.27（traceability: OK） | 参照先の値のカラムは、U1 の決定のとおり SQL の側で 10,001 文字に切って読み、10,000 文字（書き込みの値の上限）を超えた値の行は確かめで「無い」とする（U1 の… | cleanTest 付き verify（`AbstractReferenceWriteIT.java` ほか） | build-and-test |
| U2-NFR2.28 | `u2-reference-write-check/nfr-requirements/scalability-requirements.md` NFR2.28（traceability: OK） | 1回の保存で値の違う参照が複数あるときに、`check` を参照ごとに順に呼ぶか、1つの問い合わせ（IN の条件など）に束ねるかは、問い合わせの回数と読む行の数を NFR 設計で実… | cleanTest 付き verify（`BusinessDataWriteReferenceTest.java` ほか） | build-and-test |
| U2-NFR2.29 | `u2-reference-write-check/nfr-requirements/observability-requirements.md` NFR2.29（traceability: Deferred） | 作成・更新の道（`/api/tables/rows`）は既存のダッシュボードの業務データの区画（`uri=~"/api/tables/.*"`）に入っているため、新しい区画・警報は… | Observability Setup が uri・指標を確かめて書き、警報の firing を使い捨ての環境で確かめる（この段は Unverified） | observability-setup |
| U2-NFR3.21 | `u2-reference-write-check/nfr-requirements/scalability-requirements.md` NFR3.21（traceability: OK） | 1要求で借りる対象DB の読み取りの接続は 0 か 1 回、書き込みの接続は 0 か 1 回（BR4.1・BR1.2、NFR3、ADR-003）。読み取りの接続は保存前の段（⑩）で… | verify（`ReferenceWriteConnectionUsageIT`）。MariaDB と内部DB の借りた回数を含まない点はレビュー R-02（Q2） | build-and-test |
| U2-NFR3.22 | `u2-reference-write-check/nfr-requirements/scalability-requirements.md` NFR3.22（traceability: OK） | 内部DB の接続を持ったまま対象DB を待たない（BR4.2）。権限の写しを取り終えて内部DB の接続を返してから ⑩ の読み取りの接続を借り、監査の書き込みは対象DB の接続を返… | verify（`ReferenceWriteConnectionUsageIT`・`AbstractReferenceWriteIT` の T-G1・T-G2）。内部DB の借りた回数はレビュー R-02（Q2） | build-and-test |
| U2-NFR3.23 | `u2-reference-write-check/nfr-requirements/scalability-requirements.md` NFR3.23（traceability: OK） | 同時の数は、既存の対象DB の読み取りのプール（上限 既定 10。一覧・詳細・U1 と共有）と書き込みのプール（上限 5）に任せる。1要求が同時に持つ対象DB の接続は1本だけのた… | cleanTest 付き verify（`ReferenceWriteConnectionUsageIT.java` ほか） | build-and-test |
| U2-NFR3.24 | `u2-reference-write-check/nfr-requirements/reliability-requirements.md` NFR3.24（traceability: OK） | 保存前の段（⑩）の読み取り（更新前の行・件数・確かめ）は、U1 が足す `REFERENCE` の目的の接続で行い、問い合わせ1回の時間の上限は U1 と同じ既定 5 秒にする（設… | cleanTest 付き verify（`ReferenceWriteTimeoutIT.java` ほか） | build-and-test |
| U2-NFR3.25 | `u2-reference-write-check/nfr-requirements/reliability-requirements.md` NFR3.25（traceability: OK） | 保存前の段の全体に時間の上限 10 秒を置く（Q2: A）。仕組みは U1 の定義の取得と同じ: 注入した時計で ⑩ の最初の読み取りの前に締め切りを決め、各問い合わせの前に残りを… | cleanTest 付き verify（`BusinessDataWriteReferenceTest.java` ほか） | build-and-test |
| U2-NFR3.26 | `u2-reference-write-check/nfr-requirements/reliability-requirements.md` NFR3.26（traceability: OK） | ⑩ で対象DB の失敗が起きたら、その場で止め、以後の読み取りをせず、何も保存せず、書き込みの接続を借りない（BR4.5）。時間切れ・断絶では U1 の壊れた印を立てて接続を返す。… | cleanTest 付き verify（`ReferenceWriteTimeoutIT.java` ほか） | build-and-test |
| U2-NFR3.27 | `u2-reference-write-check/nfr-requirements/reliability-requirements.md` NFR3.27（traceability: OK） | 確かめの後・保存の前に参照先が消えたときは、既存の外部キー違反の変換を最後の守りにする（BR6.1）。書き込みの段（⑪）の失敗の扱い（時間切れ・確定の段の扱いを含む）は既存のまま変… | cleanTest 付き verify（`AbstractReferenceWriteIT.java` ほか） | build-and-test |
| U2-NFR3.28 | `u2-reference-write-check/nfr-requirements/observability-requirements.md` NFR3.28（traceability: Deferred） | 保存前の確かめの 503（`TIMEOUT`・`UNAVAILABLE`）は、既存の警報 `ms-biz-target-db`（業務データの API の 503 が 5 分に1件以… | Observability Setup が uri・指標を確かめて書き、警報の firing を使い捨ての環境で確かめる（この段は Unverified） | observability-setup |
| U2-NFR5.21 | `u2-reference-write-check/nfr-requirements/security-requirements.md` NFR5.21（traceability: OK） | 行カバレッジ 80% 以上・分岐カバレッジ 70% 以上を、全体と、この単位が足す・変えるパッケージごとに満たす（`team.md`、AC3.5.5）。`businessdata`… | cleanTest 付き verify の jacoco の報告から `businessdata.domain`・`businessdata.service` の値を記録（B7 の後の値。レビュー R-04） | build-and-test |
| U2-NFR5.22 | `u2-reference-write-check/nfr-requirements/security-requirements.md` NFR5.22（traceability: OK） | 手を入れる見込みのパッケージ（`businessdata.service`・`businessdata.domain`・`businessdata.web` など）は `packa… | verify と読み取りの確かめ（`build.gradle.kts`） | build-and-test |
| U2-NFR5.23 | `u2-reference-write-check/nfr-requirements/performance-requirements.md` NFR5.23（traceability: Deferred） | この単位のコード生成の後と Build and Test で、`./gradlew verify` の時間と CI の検査の job の時間を測って記録する。CI の上限（60 分… | verify の時間と CI の検査の job の時間（f35b7b8 を含む試行ごと） | build-and-test |
| U2-NFR6.21 | `u2-reference-write-check/nfr-requirements/reliability-requirements.md` NFR6.21（traceability: OK） | 新しい誤りの `code`・項目の誤りの理由・監査の理由は作らず、既存のものを使い回す（BR1.4）。1つの code に1つの状態コードを固定する決まり（`team.md`・NF… | cleanTest 付き verify（`BusinessDataWriteControllerTest.java` ほか） | build-and-test |
| U2-NFR7.21 | `u2-reference-write-check/nfr-requirements/reliability-requirements.md` NFR7.21（traceability: OK） | DSL の書式の版と内部DB のスキーマ（移行ファイル）を変えない（NFR7）。作成・更新の API の道と本文の形は変えず、拒否の応答だけが増える（契約 C3） | cleanTest 付き verify（`BusinessDataWriteControllerTest.java` ほか） | build-and-test |
| U2-NFR7.22 | `u2-reference-write-check/nfr-requirements/reliability-requirements.md` NFR7.22（traceability: OK） | 参照の無い作成・更新、削除、一意の事前の問い合わせ・違反の変換は、今の振る舞いを変えない（BR6.2）。参照の値を送らない作成・更新は対象DB の読み取りの接続を借りない（BR1.… | cleanTest 付き verify（`ReferenceWriteConnectionUsageIT.java` ほか） | build-and-test |
| U2-NFR9.21 | `u2-reference-write-check/nfr-requirements/tech-stack-decisions.md` NFR9.21（traceability: OK） | この単位は新しい依存（Gradle の依存・npm のパッケージ・コンテナのイメージ）を足さない。要件はすべて採用済みの部品で満たす。足す必要が出たときは、採用の前にライセンスを確… | verify と読み取りの確かめ（`gradle.lockfile`） | build-and-test |


### U3 u3-reference-screens（24 件）

| Target ID | Source | Expected | How checked | Owning Stage |
|---|---|---|---|---|
| U3-NFR1.41 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR1.41（traceability: OK） | 参照先の行の値（選択肢の値とラベル、検索の結果のセル、選んだ行のラベル、ラベルの1件読みの答え）と、カラムの表示名を、信頼できない入力として文字で描く（`team.md` の必須テ… | verify と読み取りの確かめ（`referenceSamples.ts`） | build-and-test |
| U3-NFR1.42 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR1.42（traceability: OK） | 参照先の行の値を、次のどこにも置かない（`project.md` の Forbidden、D34）。 ・`data-testid`・DOM の `id`（テストの印は位置の番号 `… | verify と読み取りの確かめ（`LookupPickerDialog.tsx`） | build-and-test |
| U3-NFR1.43 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR1.43（traceability: OK） | 参照元のスキーマ・テーブル・カラム、検索の値、ラベルを引く値は、すべて要求の本文で送り、道（URL）にも問い合わせにも載せない（機能設計の 2節） | verify と読み取りの確かめ（`businessDataApi.ts`） | build-and-test |
| U3-NFR1.44 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR1.44（traceability: OK） | 画面の出し分けを、サーバーの判定の代わりにしない（`project.md` の Mandated）。部品は定義の応答（`reference.kind`・`status`、`edit… | verify と読み取りの確かめ（`referenceField.ts`） | build-and-test |
| U3-NFR1.45 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR1.45（traceability: OK） | 応答の本文は `unknown` で受け、形を確かめてから写す（D3・D18）。 ・定義の `reference` の形の崩れ、`searchFields`・`listColumn… | verify と読み取りの確かめ（`businessDataParse.ts`） | build-and-test |
| U3-NFR1.46 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR1.46（traceability: OK） | 誤りの表示に、DB の文・内部の語・参照先の表とカラムの名前を出さない（D27）。検索の 403 は、行が無いことと読めないことを見分けない同じ文にし（D18・D27）、保存の 4… | cleanTest 付き verify（`problemMessage.test.ts` ほか） | build-and-test |
| U3-NFR1.47 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR1.47（traceability: OK） | E2E（190・195）の資格情報（初期管理者・自作の利用者のパスワード、トークン、対象DB の読み取り・書き込みのアカウント）と接続先は、Playwright の `webSer… | E2E の報告の json の文字列の検索で資格情報・値が無いこと | build-and-test |
| U3-NFR2.41 | `u3-reference-screens/nfr-requirements/performance-requirements.md` NFR2.41（traceability: OK） | 画面の側の時間の目標は置かない（Q1: A）。応答時間の目標（選択肢の読み取りとピッカーの一致の検索は p95 1 秒未満、部分一致の検索は p95 3 秒未満）は U1 の k6… | cleanTest 付き verify（`LookupPickerDialog.test.tsx` ほか） | build-and-test |
| U3-NFR2.42 | `u3-reference-screens/nfr-requirements/performance-requirements.md` NFR2.42（traceability: OK） | 要求の数と形。 ・作成・更新の画面を開くたびに、定義を `purpose` つきで1回読む。機能の中の定義の記憶を読み書きしない（D1・D2） ・ピッカーの「検索」1回・ページ送り… | verify と読み取りの確かめ（`useReferenceLabels.ts`） | build-and-test |
| U3-NFR2.43 | `u3-reference-screens/nfr-requirements/performance-requirements.md` NFR2.43（traceability: OK） | 規模。 ・REFERENCE の選択肢は最大 200 件（U1 の上限）を、仮想化なしでサーバーの並びのまま描く（`select`・`radio`。D6）。`radio` で 20… | cleanTest 付き verify（`ReferenceSelectField.test.tsx` ほか） | build-and-test |
| U3-NFR2.44 | `u3-reference-screens/nfr-requirements/performance-requirements.md` NFR2.44（traceability: OK） | 初回の JavaScript。参照の部品は業務データの画面（`features/tables`、`lazy` で登録済み）の中に置き、入口に入れない。既存の確かめ `fronten… | cleanTest 付き verify（`registration.test.ts` ほか） | build-and-test |
| U3-NFR2.45 | `u3-reference-screens/nfr-requirements/performance-requirements.md` NFR2.45（traceability: Deferred） | 時間の記録。 ・E2E の 190（代表の流れ）・195（axe）の時間と E2E の全体の時間を、`caffeinate -dims` を付けて測る。流した後の `pmset -… | E2E の全体の時間（前 13.1 分の 1.5 倍の 19.7 分の内）・verify・CI の検査の job の時間（B4 711ddd0・B7 6412122 を試行ごと） | build-and-test |
| U3-NFR4.41 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR4.41（traceability: OK） | WCAG 2.1 AA を満たし、キーボードだけで、作成の画面 → 「選ぶ…」でピッカーを開く → 条件を入れる → Enter で検索 → ページを送る → 行の「選ぶ」 → ダ… | cleanTest 付き verify（`LookupPickerDialog.test.tsx` ほか） | build-and-test |
| U3-NFR4.42 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR4.42（traceability: OK） | 名前と状態の知らせ。 ・ボタンの読み上げの名前は見える文字で始める（WCAG 2.5.3。「選ぶ…（{項目名}）」「クリア（{項目名}）」。行の「選ぶ」は「{行のラベル}を選ぶ」。… | cleanTest 付き verify（`LookupField.test.tsx` ほか） | build-and-test |
| U3-NFR4.43 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR4.43（traceability: OK） | 画面の部品ごとに vitest-axe の検査を1件入れる（`ReferenceSelectField`・`ReadonlyReferenceField`・`LookupField… | cleanTest 付き verify（`ReferenceColumnField.test.tsx` ほか） | build-and-test |
| U3-NFR4.44 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR4.44（traceability: OK） | 実際のブラウザの axe（E2E 195。流れではないため本数に数えない）。 ・状態: ピッカーを開いた状態・結果の一覧・0 件・検索の失敗・入力不可（権限不足・候補が多い）・作成… | E2E 195 の全件の通過と axe の違反 0 件（件数で記録）。axe-core の版が lockfile で1つ | build-and-test |
| U3-NFR4.45 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR4.45（traceability: OK） | 作成の画面を開いた直後の、必須の入力不可の参照の項目の誤り（D13。`FormField` の誤りの文は `role="alert"`）を読み上げさせるかは、NFR 設計で必ず決め… | E2E の全件（`195-reference-picker-accessibility.e2e.ts`） | build-and-test |
| U3-NFR5.41 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR5.41（traceability: OK） | 画面のテストは対象と同じ場所の `*.test.ts(x)` に、Vitest＋Testing Library（jsdom）＋user-event＋vitest-axe で書く。テ… | cleanTest 付き verify（`useReferenceLabels.test.tsx` ほか） | build-and-test |
| U3-NFR5.42 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR5.42（traceability: OK） | 性質ベースのテスト（fast-check）を純粋な関数に当てる（D37）。部品の選び方（`AVAILABLE` 以外で入力の部品を返さない、用途で入力できないカラムでは `stat… | cleanTest 付き verify（`pickerSearch.test.ts` ほか） | build-and-test |
| U3-NFR5.43 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR5.43（traceability: OK） | E2E は参照ピッカーの代表の流れを1本だけ足す（`190-reference-picker-flow.e2e.ts`。F8）。`./gradlew e2eTest` で流し、`v… | E2E の全件（190 を含む）の通過 | build-and-test |
| U3-NFR6.41 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR6.41（traceability: OK） | 画面の文言と誤りのメッセージを日本語と英語の両方で `features/tables` の文言（`messages.ts`）に置き、既定は日本語（D36）。前提の6つ（権限なし・件… | verify と読み取りの確かめ（`messages.ts`） | build-and-test |
| U3-NFR6.42 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR6.42（traceability: OK） | 画面は誤りの `code` を新しく作らず、U1・U2 が決めた `code` と `reason`（`REFERENCE_NOT_FOUND`・`NOT_EDITABLE_NOT… | verify と読み取りの確かめ（`problemMessage.ts`） | build-and-test |
| U3-NFR7.41 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR7.41（traceability: OK） | 定義の応答に足す欄（`reference`）を読み落としても壊れないこと。 ・一覧・詳細の画面は `purpose` を付けず、`reference` を読まず、表示を変えない（D… | cleanTest 付き verify（`businessDataParse.test.ts` ほか） | build-and-test |
| U3-NFR9.41 | `u3-reference-screens/nfr-requirements/security-requirements.md` NFR9.41（traceability: OK） | 新しい依存を足さない。今の React・i18next・make-you-chic-ui（固定先 `4c63d11`）・Vitest・Testing Library・user-ev… | verify と読み取りの確かめ（`package.json`） | build-and-test |


### U4 u4-perf-evidence-redactor（21 件）

| Target ID | Source | Expected | How checked | Owning Stage |
|---|---|---|---|---|
| U4-NFR5.61 | `u4-perf-evidence-redactor/nfr-requirements/security-requirements.md` NFR5.61（traceability: OK） | 置換と残存の検査に fast-check の性質ベースのテストを当て、JSON の口の 5 つの性質と、テキストの口の 3 つの性質を確かめる。失敗のときは乱数の種と道（値を含まな… | cleanTest 付き verify（`perfEvidenceRedactor.test.ts` ほか） | build-and-test |
| U4-NFR5.62 | `u4-perf-evidence-redactor/nfr-requirements/security-requirements.md` NFR5.62（traceability: Deferred） | 仕組みのテスト（性質と例）は `frontend/` の Vitest の単体テストとして `./gradlew verify`（`verifyUnitTest`）と CI で毎回… | U4 の3つのテストのファイルごとの passed 件数（skipped・todo 0）を手元の verify と CI から記録（H2・H3） | build-and-test |
| U4-NFR5.63 | `u4-perf-evidence-redactor/nfr-requirements/security-requirements.md` NFR5.63（traceability: OK） | フロントエンドのカバレッジの下限（行 80%・分岐 70%、`thresholds`）とカバレッジの除外を、この単位のために下げない・増やさない。今の `coverage.incl… | verify の下限と除外が変わっていないこと。道具のカバレッジ（行 94.2%・分岐 90.61%）は verify の下限の外（レビュー R-02、記録） | build-and-test |
| U4-NFR6.61 | `u4-perf-evidence-redactor/nfr-requirements/tech-stack-decisions.md` NFR6.61（traceability: OK） | 終わりのコード（0 成功・1 呼び出しの誤り・2 入力の誤り・3 キーの衝突・4 残存の検出・5 書き出しの失敗）は一度決めたら意味を変えず、1 つのコードに 1 つの意味を固定す… | cleanTest 付き verify（`perfEvidenceRedactorCli.test.ts` ほか） | build-and-test |
| U4-NFR7.61 | `u4-perf-evidence-redactor/nfr-requirements/tech-stack-decisions.md` NFR7.61（traceability: OK） | 契約 C7 の既存の呼び方（`node perf/<伏せ字の道具>.mjs <入力の summary json> <evidence/ の中の出力>`、引数 2 つ）を変えない。テ… | cleanTest 付き verify（`perfEvidenceRedactorCli.test.ts` ほか） | build-and-test |
| U4-NFR8.61 | `u4-perf-evidence-redactor/nfr-requirements/security-requirements.md` NFR8.61（traceability: OK） | k6 の summary json と標準出力・ログを `evidence/` に置く唯一の入口はこの道具とする（summary json は JSON の口、標準出力・ログは `… | verify と読み取りの確かめ（`README.md`） | build-and-test |
| U4-NFR8.62 | `u4-perf-evidence-redactor/nfr-requirements/security-requirements.md` NFR8.62（traceability: OK） | JWT の形（BR1.1）は、文字列の値・配列の要素・キー名・数 MB の文字列・`Bearer` の後ろのどこでも、1 つの文字列の中のすべてを `[REDACTED-JWT]`… | verify と読み取りの確かめ（`redact-k6-evidence.mjs`） | build-and-test |
| U4-NFR8.63 | `u4-perf-evidence-redactor/nfr-requirements/security-requirements.md` NFR8.63（traceability: OK） | 置換とは独立の残存の検査（木をたどらず書き出す直前の文字列を端から調べる。JSON の口では読み直した木の `setup_data` の下も調べる）を当て、1 件でも見つかれば出力… | verify と読み取りの確かめ（`redact-k6-evidence.mjs`） | build-and-test |
| U4-NFR8.64 | `u4-perf-evidence-redactor/nfr-requirements/security-requirements.md` NFR8.64（traceability: Deferred） | 道具と evidence の検査の表示（標準出力・標準エラー）には、件数・ファイルのパス・位置（行と桁、またはバイトの位置）・衝突したオブジェクトの道（置換の後の名前）・evide… | H1: 架空の JWT の形を置いた1回の `perfEvidenceScan` が終了コード 4 で失敗し、出力の全体に値と3区間が0件。置いたファイルを消し `git status` に残らない | build-and-test |
| U4-NFR8.65 | `u4-perf-evidence-redactor/nfr-requirements/security-requirements.md` NFR8.65（traceability: OK） | 書き出しは、出力と同じディレクトリの一時ファイルに書いてから名前を替えて一度に置く。どの失敗でも出力のパスは起動の前と同じ（無ければ無いまま・あれば変わらない）で、一時ファイルは残… | cleanTest 付き verify（`perfEvidenceRedactorCli.test.ts` ほか） | build-and-test |
| U4-NFR8.66 | `u4-perf-evidence-redactor/nfr-requirements/security-requirements.md` NFR8.66（traceability: OK） | 道具の入力の大きさの上限を **64 MiB** とし、道具の中の定数に置く。上限を超える入力は中身を読む前（ファイルの大きさで）に判定し、何も書かず終わりのコード 2（入力の誤り… | cleanTest 付き verify（`perfEvidenceRedactorCli.test.ts` ほか） | build-and-test |
| U4-NFR8.67 | `u4-perf-evidence-redactor/nfr-requirements/security-requirements.md` NFR8.67（traceability: OK） | 出力のパスは `evidence` という名前のディレクトリの下に限り、入力と出力が同じなら受け付けない（コード 1）。テキストの口は出力の拡張子 `.json` と、上の階層に … | cleanTest 付き verify（`perfEvidenceRedactorCli.test.ts` ほか） | build-and-test |
| U4-NFR8.68 | `u4-perf-evidence-redactor/nfr-requirements/security-requirements.md` NFR8.68（traceability: OK） | `./gradlew verify` の安全の検査の段（`verifySecurity`）で、`aidlc/` の下のすべての `evidence/` の下のすべての通常のファイル… | 手元の verify と CI で `perfEvidenceScan` が流れて成功（H3、試行ごと） | build-and-test |
| U4-NFR8.69 | `u4-perf-evidence-redactor/nfr-requirements/security-requirements.md` NFR8.69（traceability: Deferred） | どちらの口で置いた後も、コミットの前に evidence の検査（単独で呼ぶ）と `gitleaks dir`（リポジトリの `.gitleaks.toml`、値を伏せる表示）を … | H4: 実際の k6 の summary json を道具の JSON の口で `evidence/` に置き、`--scan` と `gitleaks dir` で 0 件 | build-and-test |
| U4-NFR8.70 | `u4-perf-evidence-redactor/nfr-requirements/security-requirements.md` NFR8.70（traceability: OK） | k6 の台本は、秘密の値（トークン・Cookie・Authorization ヘッダー・招待のトークン・パスワード）を、`setup_data` の外に出る所（タグの値・check… | verify と読み取りの確かめ（`README.md`） | build-and-test |
| U4-NFR8.71 | `u4-perf-evidence-redactor/nfr-requirements/security-requirements.md` NFR8.71（traceability: OK） | テストの中の JWT の形の値は実行の中で `eyJ` と base64url の区間をつないで組み立て、テストのソース・見本のファイルに完成した JWT の形の文字列を書かない（… | H6: 統合の後の develop でテストのソースに完成した JWT の形が 0 件 | build-and-test |
| U4-NFR9.61 | `u4-perf-evidence-redactor/nfr-requirements/tech-stack-decisions.md` NFR9.61（traceability: OK） | 道具は Node の組み込みだけを読み込み、npm のパッケージを読み込まない。`frontend/package.json`・`frontend/package-lock.jso… | verify と読み取りの確かめ（`redact-k6-evidence.mjs`） | build-and-test |
| U4-NFR9.62 | `u4-perf-evidence-redactor/nfr-requirements/tech-stack-decisions.md` NFR9.62（traceability: OK） | テストは既存の Vitest と fast-check だけで書き、新しい devDependencies を足さない。足す必要が出たときは、採用の前にライセンスを確かめて依頼者に… | verify と読み取りの確かめ（`package.json`） | build-and-test |
| U4-NFR9.63 | `u4-perf-evidence-redactor/nfr-requirements/tech-stack-decisions.md` NFR9.63（traceability: OK） | 依存の取り込みは今の lockfile どおり（`npm ci`）で、`frontend/.npmrc` の `ignore-scripts=true` を変えない | verify と読み取りの確かめ（`.npmrc`） | build-and-test |
| U4-NFR9.64 | `u4-perf-evidence-redactor/nfr-requirements/tech-stack-decisions.md` NFR9.64（traceability: OK） | 道具は Node 24 で動く構文と組み込みの口だけを使い、`engines` の範囲（`>=24 <25`）を変えない | verify と読み取りの確かめ（`redact-k6-evidence.mjs`） | build-and-test |
| U4-NFR9.65 | `u4-perf-evidence-redactor/nfr-requirements/tech-stack-decisions.md` NFR9.65（traceability: OK） | 道具のファイルの先頭に Apache License 2.0 のヘッダー（`/* ... */` の形、年 `2026`、著作権者 `agwlvssainokuni`）を置き、ライ… | ライセンスヘッダーの検査が道具を見て通る（verify）。ESLint と型の検査は当たらない（レビュー R-03、一覧を記録） | build-and-test |


### U5 u5-container-perf-docs（13 件）

| Target ID | Source | Expected | How checked | Owning Stage |
|---|---|---|---|---|
| U5-NFR5.81 | `u5-container-perf-docs/nfr-requirements/security-requirements.md` NFR5.81（traceability: OK） | 統合の前に `./gradlew verify` が通ること（カバレッジの下限と除外は変えない）。この Bolt は文書・台本のコメント・`aidlc/` だけを変えるため、統合の… | Intent の最後の verify（cleanTest 付き）が通ること | build-and-test |
| U5-NFR8.81 | `u5-container-perf-docs/nfr-requirements/security-requirements.md` NFR8.81（traceability: OK） | Mailpit の作り直しの前に、溜まったメールが消えることを依頼者に伝え、了承を得てから作り直す。了承の取得を B6 の最初の手順に固定し、了承の日時を記録に残す | Code Generation の記録（`container-swap-record.md`）を引用し、必要な検索を流し直す | build-and-test |
| U5-NFR8.82 | `u5-container-perf-docs/nfr-requirements/security-requirements.md` NFR8.82（traceability: OK） | Mailpit（profile `mail`）と otel-lgtm（profile `monitoring`）を `compose.yaml` から `docker compos… | verify と読み取りの確かめ（`compose.yaml`） | build-and-test |
| U5-NFR8.83 | `u5-container-perf-docs/nfr-requirements/security-requirements.md` NFR8.83（traceability: OK） | 古い 0.34.0 の lgtm のイメージは戻し先として消さずに残す。この単位では `docker image rm`・`docker image prune`・`docker … | Code Generation の記録（`container-swap-record.md`）を引用し、必要な検索を流し直す | build-and-test |
| U5-NFR8.84 | `u5-container-perf-docs/nfr-requirements/security-requirements.md` NFR8.84（traceability: OK） | 作り直した Mailpit に SMTP で 1 通送り、Mailpit の API で取得できることを確かめる。宛先と差出人は予約のドメイン（`example.com`）だけにし… | Code Generation の記録（CA2・CA8）を引用。救済の WARN は未計測（作り直しなしの読み替え、依頼者が受け入れ済み）。アプリを起動し直す機会があれば件数を数える | build-and-test |
| U5-NFR8.85 | `u5-container-perf-docs/nfr-requirements/security-requirements.md` NFR8.85（traceability: OK） | lgtm は版を確かめ終えたら止め（`docker compose stop lgtm`）、`docker ps` で動いていないことを確かめて、確かめた結果と止めたことを記録する | Code Generation の記録（`container-swap-record.md`）を引用し、必要な検索を流し直す | build-and-test |
| U5-NFR8.86 | `u5-container-perf-docs/nfr-requirements/security-requirements.md` NFR8.86（traceability: OK） | 作業の間、`.env`・`.env.*` を開かず、値を表示しない。`docker compose config` の全体を表示しない。`docker inspect` は `--… | Code Generation の記録（`container-swap-record.md`）を引用し、必要な検索を流し直す | build-and-test |
| U5-NFR8.87 | `u5-container-perf-docs/nfr-requirements/security-requirements.md` NFR8.87（traceability: OK） | perf の文書と台本の整理では、合否の線の値を変えない。整理の前に `k6 inspect --include-system-env-vars` を場面ごとに流して出力をリポジト… | verify と読み取りの確かめ（`scenarios.js`） | build-and-test |
| U5-NFR8.88 | `u5-container-perf-docs/nfr-requirements/security-requirements.md` NFR8.88（traceability: OK） | 合否の線を定義する場所を `perf/k6/scenarios.js` の `thresholdsFor` の 1 か所に集め、`thresholdsFor` から取り出した値の表… | verify と読み取りの確かめ（`thresholds-test.sh`） | build-and-test |
| U5-NFR8.89 | `u5-container-perf-docs/nfr-requirements/security-requirements.md` NFR8.89（traceability: OK） | `perf/README.md` の整理は U4（B5）の後に行い、U4 が書く伏せ字の節（JSON の口とテキストの置換の口、伏せる対象と対象外、台本の決まり）を、意味を変えずに… | verify と読み取りの確かめ（`README.md`） | build-and-test |
| U5-NFR8.90 | `u5-container-perf-docs/nfr-requirements/security-requirements.md` NFR8.90（traceability: OK） | 過去の値を「履歴」の章へまとめるとき、移す中身は今の README にある文と値だけとし、前の Intent の記録（`evidence/` など）から新しく値を写さない。履歴の章… | verify と読み取りの確かめ（`README.md`） | build-and-test |
| U5-NFR9.81 | `u5-container-perf-docs/nfr-requirements/security-requirements.md` NFR9.81（traceability: OK） | この単位では、新しい依存（npm・Gradle）もイメージの版の変更も持ち込まない。作り直しは `compose.yaml` に固定済みのタグと index のダイジェスト（Mai… | verify と読み取りの確かめ（`scenarios.js`） | build-and-test |
| U5-NFR9.82 | `u5-container-perf-docs/nfr-requirements/security-requirements.md` NFR9.82（traceability: OK） | Dependabot が見ない固定先（`perf/README.md` の `grafana/otel-lgtm`、`README.md` の Mailpit、`docker/pe… | verify と読み取りの確かめ（`compose.yaml`） | build-and-test |

