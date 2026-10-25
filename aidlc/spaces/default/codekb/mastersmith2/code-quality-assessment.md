# コードの品質（mastersmith2）

> 2026-10-25（記録のコミット `bc484c9`）の Full rescan・深さ Minimal で作り直した。所見 K-67・K-68 の本文はこの文書に書いた。テストの件数とカバレッジの実測の値はこの段では取っていない（`./gradlew` を流していない）。

## テスト

- 置き場: `backend/src/test/java/cherry/mastersmith/`（機能ごと、単体 `XxxTest`・結合 `XxxIT`、機能ごとの `testsupport/`）、`frontend/src/**/*.test.ts(x)`（対象と同じ場所）、`frontend/e2e/`（`*.e2e.ts` 20 本、`./gradlew e2eTest`、`verify` と CI の外）。
- 道具: JUnit 5・Spring Boot Test・Testcontainers（MySQL・MariaDB・PostgreSQL）・jqwik・ArchUnit／Vitest・Testing Library・user-event・vitest-axe・fast-check／Playwright と axe-core。
- 境界の構造の検査（ArchUnit）は 19 ファイル（`find` で数えた）: `ArchitectureTest`・`ApiAccessArchitectureTest` と、`access`・`appearance`・`audit`・`auth`・`businessdata`・`dsl`・`dslmanage`（2つ）・`group`・`invitation`・`mail`・`navigation`・`role`・`targetdb`（`TargetDbBoundaryArchitectureTest` と `ExtensionOrderArchitectureTest`）・`user`・`useradmin`。`businessdata` の境界テストの `@DisplayName` から読める規則: 読み取り側はトランザクションと `JdbcTemplate` を使わない、`RowSqlExecutor` だけが JDBC の接続から文を作る、`businessdata.repository` は `targetdb.service` に依存しない、`businessdata` に依存してよいのは `audit` の `domain` だけ。
- `businessdata` のテストは `domain`・`repository`（`RowSqlExecutorTest`・`RowDialectTest`・`RowSqlFailureClassifierTest` など）・`service`・`testsupport`（`CountingDataSource` など）に分かれる（ファイル名の一覧のみ。中身は読んでいない）。

## カバレッジ

- バックエンドは JaCoCo。全体とパッケージごとに行 80%・分岐 70%。全体の合計で判定する既存のパッケージ `packagesJudgedByTotal` は 7 つ（`backend/build.gradle.kts` 230〜238 行: `access.service`・`audit.repository`・`common.error.domain`・`common.error.service`・`common.health`・`common.i18n.domain`・`common.web`。`team.md` の記録と一致する）。`businessdata`・`targetdb` はこの一覧に無く、パッケージごとの下限が当たる。
- 画面は `frontend/vitest.config.ts` の `thresholds`（`lines: 80`・`branches: 70`、65〜68 行）。

## リンタと静的解析

- Java: Spotless（palantir-java-format・ライセンスヘッダー）、SpotBugs＋FindSecBugs（`backend/config/spotbugs-exclude.xml`。`SQL_`・`PREDICTABLE_RANDOM`・`SMTP_HEADER_INJECTION` は priority にかかわらず止める、`team.md`）。
- 画面: Prettier・oxlint・ESLint（`frontend/eslint.config.js`）・Stylelint。

## CI と依存

- `.github/workflows/ci.yml`（ジョブ `./gradlew verify` の `timeout-minutes: 60`。actions はコミットの SHA で固定。構成だけ読んだ）。
- `.github/dependabot.yml`: 見送り（`ignore`）は gradle の3件・npm の `typescript`・docker の `eclipse-temurin`（K-66、本文は `dependencies.md`）。Dependabot が見ない固定先（`docker/perf/compose.yaml`・`TargetDbImages.java`・`perf/README.md` の grafana/k6 など）は手でそろえる運用で、`dependabot.yml` のコメントに列挙している。

## ドキュメント

- Javadoc・コメントは日本語で、要件・設計の ID（BR・NFR・契約の番号）と Intent の名前を細かく引く。`frontend/src/features/README.md` に機能の作り方の決まり、`perf/README.md` に負荷の試験の手順（1100 行超）。

## 技術的負債

- 一覧の `COUNT(*)` が上限なしで、MySQL・MariaDB の部分一致・深い OFFSET が重い（K-62、本文は `architecture.md`）。読み取りのプールは小さい（K-63）。件数の型が「多数」を持てない（K-64、本文は `api-documentation.md`）。
- 固定している部品の見直しが Spring Boot の更新に結びついている（K-66）。
- 負荷の試験の台本・手順書が大きい（`perf/README.md` は 1100 行超、`perf/k6/scenarios.js` も大きい）。
- 全体の合計で判定する7パッケージが残る（上記）。

**K-67 perf の biz の場面・種・資源の上限と、読み取りのプールを変える口（`perf-and-monitoring`）**

- 確かめた事実:
  - 場面（`perf/k6/scenarios.js` 160〜165 行）: `bizSearchFirst`（1ページ目、100 件・100 カラム・条件なし・既定の並び、`total` は `BIZ_ROWS` と一致を確かめる）・`bizSearchContains`（`c_010` の部分一致、表の全部を読む形）・`bizSearchLast`（主キーの順の最後のページ）・`bizSearchLastSorted`（索引の無い `c_001` で並べ替えた最後のページ）・`bizDetail`・`bizSearchWide`（`biz_wide`、メモリの確かめ。応答時間は記録だけ）。判定は 1 繰り返しの `iteration_duration` の `p(95)<1000`（550〜554 行。`bizSearchWide` は `max>=0`）。
  - 種: `perf/biz/make-biz-seed.sh`（`biz_item` は 100 カラム × 既定 100,000 行、`c_001` に索引なし）、`perf/make-biz-dsl.mjs`。
  - 使い捨ての環境（`docker/perf/compose.yaml`）: app `mem_limit` 既定 2g（60 行）、PostgreSQL 512m（92 行）・MySQL 768m（118 行）・MariaDB 512m（143 行）が既定で、InnoDB のバッファープールは 128M が既定（101・127 行）。値はシェルの環境変数 `MASTERSMITH_PERF_TARGETDB_*` で上げられる。前の Intent の実績では MySQL を 2688m まで上げた（`project.md` の記録）。
  - 読み取りのプールの値（`MASTERSMITH_TARGET_DB_POOL_MAXIMUM_SIZE`）を負荷の試験で変えるには、app の `env_file`（`MASTERSMITH_PERF_ENV_FILE` と `MASTERSMITH_PERF_APP_TARGETDB_ENV_FILE`）にその変数を足す。`docker/perf/compose.yaml` には直接の指定が無い。
  - 前の Intent の測定の記録（`project.md` の Interpretations）: Not Met は MySQL・MariaDB の部分一致と MariaDB の深い OFFSET の4件（U1-NFR1.1・1.3・3.2・2.4）で、PostgreSQL は全場面が満たした。今回は再測定していない。
- 今回の Intent への意味: 件数の取り方やプールを変える効果は、この4場面（`bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted` と、MariaDB の深い OFFSET）で測り直すことになる。物差し（`p(95)<1000` の `iteration_duration`）を変えるのは、要件定義で依頼者が明示に決めたときだけ（`team.md`）。

**K-68 依頼の「`perf/README.md` (c) の grep の形」の該当行は、(c) の節 1003 行の 1021 行**

- 確かめた事実:
  - `perf/README.md` 1003 行は見出し `### (c) ロールの3場面を手順書の順と単独で（FR7.2）`。1021 行は `grep -h -o '"checks": {[^}]*}' "build/perf-results/$s.json" "build/perf-results/$s-alone.json"`。
  - 前の Intent の記録（`aidlc/spaces/default/intents/261016-business-data-views/operation/feedback-optimization/feedback-loop.md` 34・152 行）は「(c) の `grep -o` が複数行の JSON で何も出さない（今も残っている見込み。読み取りの調べで、流して確かめてはいない）」とする。出どころは `261015-deps-ui-carryover` の Build and Test の 5.3。
- 見立て（未検証）: `grep` は1行ごとに照合するため、`--summary-export` の JSON が複数行に整形されていると `"checks": {…}` を1行で拾えず何も出ない。直し方の候補は、`jq` で読む、1行にしてから拾う、または手順を値の読み取りに替えること。流して確かめていない。

## Sources

- 開発担当の調査の Test Coverage・Code Quality Indicators・Technical Debt Signals・K-67・K-68。
- アーキテクトの確かめ直し（`backend/build.gradle.kts`・`frontend/vitest.config.ts`・`perf/README.md` の 1003〜1021 行・`docker/perf/compose.yaml`・ArchUnit のファイル数）。
