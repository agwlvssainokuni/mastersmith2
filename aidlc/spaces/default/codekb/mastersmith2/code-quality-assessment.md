# コードの品質（mastersmith2）

> 2026-11-01（記録のコミット `6079f13`）の Intent `261101-maintenance-update` の Focused scan で更新した。今回は `build.gradle.kts`・`backend/build.gradle.kts`・`.github/workflows/ci.yml`・`README.md`・`perf/README.md` の構成・perf の伏せ字の道具・`businessdata` の一部を読み、所見 K-88〜K-91 を足した（本文はこの文書）。テストの件数とカバレッジの実測の値は取っていない（`./gradlew` を流していない）。
>
> 前回の記録: 2026-10-29（記録のコミット `15a25aa`）の Intent `261029-reference-lookup-cleanup` の Focused scan で更新した。今回の範囲の事実（`packagesJudgedByTotal`、境界テストの許可の一覧、SpotBugs の除外の形、perf の JWT）を読み直し、所見 K-77・K-80 を足した（本文はこの文書）。K-67〜K-73 は前回（`261027-perf-targets-review`）の記述のまま（今回は `perf/README.md` と `scenarios.js` の一部だけ読み、行数と構成を更新した）。テストの件数とカバレッジの実測の値は取っていない（`./gradlew` を流していない）。

## テスト

- 置き場: `backend/src/test/java/cherry/mastersmith/`（機能ごと、単体 `XxxTest`・結合 `XxxIT`、機能ごとの `testsupport/`）、`frontend/src/**/*.test.ts(x)`（対象と同じ場所）、`frontend/e2e/`（`*.e2e.ts` 22 本（010〜195、2026-11-01 に数えた）、`./gradlew e2eTest`、`verify` と CI の外）。
- 道具: JUnit 5・Spring Boot Test・Testcontainers（MySQL・MariaDB・PostgreSQL）・jqwik・ArchUnit／Vitest・Testing Library・user-event・vitest-axe・fast-check／Playwright と axe-core。
- 境界の構造の検査（ArchUnit）は 19 ファイル（`find` で数えた）: `ArchitectureTest`・`ApiAccessArchitectureTest` と、`access`・`appearance`・`audit`・`auth`・`businessdata`・`dsl`・`dslmanage`（2つ）・`group`・`invitation`・`mail`・`navigation`・`role`・`targetdb`（`TargetDbBoundaryArchitectureTest` と `ExtensionOrderArchitectureTest`）・`user`・`useradmin`。`businessdata` の境界テストの `@DisplayName` から読める規則: 読み取り側はトランザクションと `JdbcTemplate` を使わない、`RowSqlExecutor` だけが JDBC の接続から文を作る、`businessdata.repository` は `targetdb.service` に依存しない、`businessdata` に依存してよいのは `audit` の `domain` だけ。
- `businessdata` のテストは `domain`・`repository`（`RowSqlExecutorTest`・`RowDialectTest`・`RowSqlFailureClassifierTest` など）・`service`・`testsupport`（`CountingDataSource` など）に分かれる（ファイル名の一覧のみ。中身は読んでいない）。

## カバレッジ

- バックエンドは JaCoCo。全体とパッケージごとに行 80%・分岐 70%。全体の合計で判定する既存のパッケージ `packagesJudgedByTotal` は 7 つ（`backend/build.gradle.kts` 213 行〜の `packagesJudgedByTotal`: `access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`。`team.md` の記録と一致する）。`businessdata`・`targetdb` はこの一覧に無く、パッケージごとの下限が当たる。
- 今回（2026-10-29）も `packagesJudgedByTotal`（`backend/build.gradle.kts` 213〜221 行）は 7 パッケージで `team.md` と一致することを確かめた。`businessdata`・`dsl`・`role`・`targetdb` は一覧に無く、この Intent で手を入れても一覧は増減しない見込み（`common.web` などに手を入れる場合は `team.md` の外す義務が発生する）。
- 全体の下限（行 80%・分岐 70%）とパッケージごとの下限の規則は `backend/build.gradle.kts` 233〜253 行付近（`rule` が2つ。2つ目は `element = PACKAGE`・`excludes = packagesJudgedByTotal`）。
- 画面は `frontend/vitest.config.ts` の `thresholds`（前回の記録による。今回は読んでいない）（`lines: 80`・`branches: 70`、65〜68 行）。

## リンタと静的解析

- Java: Spotless（palantir-java-format・ライセンスヘッダー）、SpotBugs＋FindSecBugs（`backend/config/spotbugs-exclude.xml`。`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority にかかわらず止める、`team.md`）。
- 画面: Prettier・oxlint・ESLint（`frontend/eslint.config.js`）・Stylelint。

## CI と依存

- `.github/workflows/ci.yml`（2026-11-01 に全文を読んだ）: `develop` への push と `v*` のタグと手動で動く。ジョブ `verify`（`ubuntu-latest`・`timeout-minutes: 60`）はサブモジュールごと全履歴を取得し、JDK 25・Node 24・Gradle を用意し、Gitleaks 8.30.1 と OSV-Scanner 2.6.0 を版と SHA-256 で確かめて入れ、`./gradlew verify` を流し、WAR を 30 日保存する。ジョブ `release`（タグのとき）は WAR を GitHub のリリースに添付する。actions はコミットの SHA で固定（版は `technology-stack.md`）。
- `.github/dependabot.yml`: 見送り（`ignore`）は gradle の2件（appender・networknt）・npm の `typescript` の大きな版・docker の `eclipse-temurin` の大きな版（K-66・K-72、本文は `dependencies.md`）。Dependabot が見ない固定先（`docker/perf/compose.yaml`・`TargetDbImages.java`・`perf/README.md` の grafana/k6 など）は手でそろえる運用で、`dependabot.yml` のコメントに列挙している。

## 静的解析の除外と境界テストの形（K-80、2026-10-29）

- SpotBugs の `SQL_` の除外（`backend/config/spotbugs-exclude.xml`）は `RowSqlExecutor` の `search`・`detail`・`precheckUnique`・`insert`・`update` ほかのメソッド単位で個別に書かれている（開発担当の読み）。`team.md` の決まりで、除外の追加・拡大はコード生成の計画に明記して依頼者の承認が要る。
- `BusinessDataBoundaryArchitectureTest` の `allowedDependencies` は `businessdata` が依存してよい先を `targetdb.service/domain`・`role.service/domain`・`dsl.domain/service`・`common` などに限る。`ALLOWED_STATEMENT_OWNERS` は文の作成・実行を許す先を `RowSqlExecutor` と `targetdb.repository` の数クラスに限る。
- 見立て（未検証）: 参照先の読み取りを `businessdata` の内側に置き、既存の `search`・`detail` を再利用するなら、これらの許可と除外は緩めずに済む。`targetdb` や `role` に手を入れるときは `TargetDbBoundaryArchitectureTest`・`RoleBoundaryArchitectureTest` の許可の一覧に触れうる。

## ドキュメント

- Javadoc・コメントは日本語で、要件・設計の ID（BR・NFR・契約の番号）と Intent の名前を細かく引く。`frontend/src/features/README.md` に機能の作り方の決まり、`perf/README.md` に負荷の試験の手順（1,753 行、2026-11-01）。

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

**K-91 書き込みの参照の段の `stage` の無い WARN（T3、2026-11-01）（`businessdata`）**

- 確かめた事実: `BusinessDataWriteService.warnReference`（908〜931 行）は `stage`・`sqlState`・`vendorCode`・`exceptionClass` が null のときそのキーを出さない。呼び出しは4か所（325・343・365・377 行）で、借りられない2つは `Stage.BORROW` を渡す。365 行（`StepAttempt.Unexpected`）は `unexpected.stage()` を渡し、これが null の経路では「保存の前に対象DB の参照先を読めませんでした」の WARN が `stage` 無しで出る。値（行・名前・メッセージ）は出ない。
- 未検証: `StepAttempt.Unexpected` の `stage` が null になる経路（`atStage` で包まれていない想定外の失敗か）は読んでいない。直す形（`stage` を必ず持たせる・`UNKNOWN` を出す）は設計で決める。

## 技術的負債

- 一覧の `COUNT(*)` が上限なしで、MySQL・MariaDB の部分一致・深い OFFSET が重い（K-62、本文は `architecture.md`）。読み取りのプールは小さい（K-63）。件数の型が「多数」を持てない（K-64、本文は `api-documentation.md`）。
- 固定している部品の見直しは Spring Boot 4.1.2 で済み、Tomcat・Jackson の上書きは外れた。appender と networknt の固定が残る（K-66）。
- 負荷の試験の台本・手順書が大きい（K-73。2026-11-01 の構成は K-89）。README.md の経緯（K-88）。
- 版の固定先が分散している（K-81〜K-84、`technology-stack.md`）。Node 26 への引き上げは `engines`・`checkToolchain`・CI・README を同時にそろえる必要がある。
- perf の伏せ字の道具の検査の抜け（K-90）と、`stage` の無い WARN（K-91）。
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

- 2026-11-01: 開発担当の調査 `aidlc/spaces/default/intents/261101-maintenance-update/inception/reverse-engineering/developer-scan.md`（Technical Debt Signals の TD-1〜TD-14）。`README.md`・`perf/README.md` の見出しの一覧と grep の件数、`build.gradle.kts`・`.github/workflows/ci.yml`・`BusinessDataWriteService.java` の該当行。
- 開発担当の調査（今回: `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/reverse-engineering/developer-scan.md`、2026-10-28 と前回）の Test Coverage・Code Quality Indicators・Technical Debt Signals・K-67〜K-73。
- アーキテクトの確かめ直し（今回: `backend/build.gradle.kts` の jacoco の規則、`perf/k6/scenarios.js` の `thresholdsFor`、`perf/README.md` の 1150〜1164 行、`docker/perf/compose.yaml`。前回: `frontend/vitest.config.ts`・`perf/README.md` の 1003〜1021 行・ArchUnit のファイル数）。
