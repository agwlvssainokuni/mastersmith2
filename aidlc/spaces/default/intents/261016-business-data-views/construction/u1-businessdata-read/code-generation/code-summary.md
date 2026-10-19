# コードの要約 — U1 u1-businessdata-read（Bolt B2）

承認済みの計画（`code-generation-plan.md`）の Step 1〜26 を行った。Step 27（コミット）と Step 28（統合）は、指揮役が依頼者の承認を得て行う。作業ブランチは `feature/261016-business-data-views-b2`。計画に無い変更と判断の詳細は `generation-notes.md`（N1〜N3・G1〜G34）にあり、ここではその要点を書く。

## 1. 作ったもの・変えたもの

作った・変えたアプリのソースは `source-manifest.json` に 151 のパスで並べた（消したものは無い）。

| 場所 | 中身 |
|---|---|
| `backend/src/main/java/cherry/mastersmith/targetdb/` | `ReadPurpose` に `LIST`・`DETAIL`。新しい `TargetReadSession`・`TargetReadOutcome`・`TargetReadUnexpectedException`（domain）、貸す形の読み取りの口 `TargetReadAccess`・`JdbcTargetReadAccess`（service）。`QueryTimeout` を4つの上限に、`TargetDbSettings` の点検、`TargetDataSourceConfig` の通信の待ち（4つの上限の最大 + 5 秒） |
| `backend/src/main/java/cherry/mastersmith/role/domain/PermissionSnapshot.java` | `Optional<DslModel> dsl()`（A3） |
| `backend/src/main/java/cherry/mastersmith/businessdata/` | 新しい機能。domain（値の型・出し分けと許す操作の純粋な関数・検索の演算子の表・本文の確かめ・部分一致のエスケープ・失敗の理由・code）、repository（DB の種類ごとの方言・型の対応・失敗の分け方・SQL を組み立てて実行する唯一の部品 `RowSqlExecutor`）、service（判定の順の流れ `BusinessDataReadService`・写しと判定 `TableAccessResolver`・code の一覧）、web（3本の API・要求と応答の record・本文の上限） |
| `backend/src/main/resources/application.yaml`・`.env.example` | `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_LIST`・`_DETAIL`（既定 5s）。配備の `.env` は開いておらず、変えていない |
| `backend/config/spotbugs-exclude.xml` | `RowSqlExecutor` の `search`・`detail` と、`SQL_INJECTION_JDBC`・`SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING` の3つで絞った除外（A2。理由つき） |
| `backend/src/test/java/cherry/mastersmith/` | `businessdata` の単体・性質ベース・境界・結合のテストと `businessdata/testsupport`、`targetdb` の新しいテスト、直した既存のテスト（`targetdb` の設定と読み取りの口・`PermissionSnapshotTest`・`RoleBoundaryArchitectureTest`（A1）・`ApiEndpointCounts`・`AbstractTargetSchemaReaderIT`） |
| `perf/`・`docker/perf/compose.yaml` | 負荷の試験の台本: 種の SQL を出す `perf/biz/make-biz-seed.sh`、DSL を作る `perf/make-biz-dsl.mjs`、`perf/k6/scenarios.js` の8場面（`bizPrepare`・`bizDefinition`・`bizSearchFirst`・`bizSearchContains`・`bizSearchLast`・`bizSearchLastSorted`・`bizDetail`・`bizSearchWide`）、`perf/README.md` の「業務データの場面」、compose の対象DB の設定の口（既定は今の値）、`perf/dsl-timing.sh` の始めの `unset` |
| `README.md` | 環境変数の表の2行、「対象DB」の節の業務データの読み取りの API の小見出し（3本の道・ログインだけ・テーブルごとの権限・読み取りの接続だけ・code と 503） |

触れていないもの: `packagesJudgedByTotal` の7パッケージの本体のソース、`docker/monitoring/`、`.github/workflows/ci.yml`、`vendor/`、配備した環境（アプリ・見本の PG・Mailpit とボリューム）。

## 2. 主な決定

- 判定の順は 401 → 404 DSL → 404 テーブル → 403 → 409 → 400 → 404 行 → 503・409 で、業務処理の層が結果の型（sealed interface の record）で返し、web の層が `switch` で場合を尽くして `BusinessException` に変える（team.md の Code Style）。
- SQL を組み立てて実行するのは `RowSqlExecutor` の `search`・`detail` だけ。識別子は写しの DSL にある名前と照らしてから引用し、値はバインド変数で渡す。`prepareStatement`・`createStatement` を呼べる場所を境界テストで `RowSqlExecutor` に絞った。
- 対象DB の接続は貸す形の口（`TargetReadAccess.withRead`）からだけ借り、1要求で1本、借りている間は内部DB の接続を持たない。貸すたびに `setNetworkTimeout`（目的の上限 + 5 秒）を付けて戻し、戻せなければ接続を捨てる。
- 一覧の深いページは主キーを先に読んでからつなぐ形。NULL は昇順で最後。部分一致は `LIKE ? ESCAPE '!'`。MySQL・MariaDB は貸し出しの始めに `SET time_zone = '+00:00'` を流し、日付・時刻は文字で受けて厳しく読む（G15）。
- 値を持つ型は toString で値を伏せ、JDBC の例外のメッセージは応答にもログにも出さない（ログは SQLState・業者の番号・例外のクラスの名前だけ）。

## 3. テストとカバレッジ

`:backend:cleanTest :backend:cleanIntegrationTest verify` を `caffeinate -dims` で包んで流した（眠りなし。実測の数字だけ）。

| 項目 | 結果 |
|---|---|
| `verify` | 通過。18 分 32 秒（直近 14 分 1 秒から 4 分 31 秒延びた。計画の見込みの約 45 秒より長いが、CI の上限の7割の約 42 分の線の内） |
| バックエンドの単体テスト | 2,358 件（失敗 0・飛ばし 0） |
| バックエンドの結合テスト | 1,253 件（失敗 0・飛ばし 4。飛ばしは G22 の `assumeTrue`（DB の種類に当たらない確かめ）で、対象DB のテストのコンテナの飛ばしではない） |
| フロントエンドのテスト | 184 ファイル・1,589 件 |
| カバレッジ（バックエンドの全体） | 行 97.8%・分岐 91.8% |
| カバレッジ（パッケージごと、行・分岐） | `businessdata.domain` 97.7%・90.7%、`businessdata.repository` 94.9%・86.0%、`businessdata.service` 98.6%・93.6%、`businessdata.web` 97.0%・74.3%、`targetdb.config` 100.0%・100.0%、`targetdb.domain` 100.0%・97.1%、`targetdb.repository` 96.0%・87.5%、`targetdb.service` 90.5%・92.5%、`role.domain` 94.2%・85.7%。どれも下限（行 80%・分岐 70%）を満たす。除外は増やしていない |
| カバレッジ（フロントエンドの全体） | Stmts 95.19%・Branch 90.38%・Funcs 96.73%・Lines 95.36% |
| E2E（`./gradlew e2eTest`） | 232 件 通過（10.8 分。失敗・flaky 0） |
| SpotBugs の関門 | 通過（`SQL_` の残り 0 件） |
| k6 の台本 | `k6 inspect` で8場面の名前と閾値の名前を確かめ、PG の試し走り（既定の設定、配備したアプリを止めない、各場面 1 VU・10 秒。判定ではない）を2回流して全場面が通った（G28・G29、`perf/README.md`） |

## 4. 計画との差

| # | 要点（詳細は `generation-notes.md`） |
|---|---|
| N1〜N3 | 進め方の差（Step 1 の未コミットの変更の扱い、Step 6 を Step 5 のテストの前に、値の型のテストを `ValueTypesTest` の1つに） |
| G1・G2 | `tinyint` をフォーム部品 checkbox・switch でも BOOLEAN に読む、主キーに BINARY と同じ扱いの型や BR7.9 の型を含むテーブルは DETAIL を許さず `restrictionReason` は null（依頼者の決定 A） |
| G3〜G14 | 設計で決まっていなかった細部の判断（`bit` の長さ、整数の範囲、数・真偽値の文字の形、既定の並びのキー、主キーが NONE のテーブルの並び、DB の種類が分からないときの型、失敗の分け方、`definitionChanged` は 400 だけ、設定が無いときのログ、詳細の `LIMIT 2`、値を読む列が無いときの定数、借りるときの非検査の例外） |
| G15 | MySQL・MariaDB の日付・日時・時刻は `getString` で受けて厳しく読む（MariaDB のドライバーがゼロ日付を NULL に、`838:59:59` を `22:59:59` にして返したため） |
| G16 | `SET time_zone` の文を方言の返す文字から `RowSqlExecutor` の定数に移した（SpotBugs の `SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE` を除外を広げずに消すため） |
| G17〜G20 | 定義の応答に `displayName`・選択肢の表示名・要求の `schema`・`table` を足し、`primaryKeyColumns` は DETAIL が許されるときだけ返す（契約 C2 との差）。要求の識別は形を確かめない型で受ける。利用者 ID は `role` と同じ形の要求の文脈で読む |
| G21〜G25 | 結合テストの作り（対象DB のタイムゾーン、DB の種類に当たらない確かめの `assumeTrue`、中継の止め方、見本のスキーマの `tag`、`SpringApplicationBuilder` での起動） |
| G26〜G34 | 負荷の試験の台本と文書（3つ目の表を `biz_wide` に、`bizSearchWide` は応答時間を記録だけ、compose の `command` の読み、試し走りのログの取り直し、DSL の型の名前、種の式、試験用の利用者とロール、VM の表の R-05、README の古い文の直し） |
| コミットの区切り | 7節の C1〜C7 の区切りは変えないが、中身が増える。C1 に `TargetReadUnexpectedException`、C3 に `businessdata/testsupport/BusinessDataDslFixture`（domain・repository の単体テストが使う）、C4 に `FakeBusinessDataPorts`・`BusinessDataServiceFixture`（service の単体テストが使う）を入れ、C5 は残りの `testsupport` と結合テストにする。`README.md` は環境変数の2行を C1、「対象DB」の節の直しと小見出しを C7 に分ける |

## 5. Bolt の計画との差

- B1（U5、不安定なテスト2件の直しと `memory.peak` の測り方の台本）が、Bolt の計画の1番目から U1・U4 の後（3番目）に移った。計画の理由は「後の Bolt の統合の後の CI で2件が落ちうるため先に安定させる」だったため、B2（この単位）と B3 の統合の後の CI で2件が落ちる危険が残る。依頼者の決定（今の順のまま）で進め、落ちたら team.md の不安定なテストの決まりで扱う（`construction/code-generation/memory.md` の Deviations）。
- B7（U4 の後半、axe-core 4.14 の引き上げと依頼（5）の取り込み）は、依頼者の決定（A）で U3 のコード生成の計画の最後の Bolt に移る。U1 には関わらない。

## 6. 依頼者に確かめたいこと

1. **G15**: MySQL・MariaDB の日付・日時・時刻を、ドライバーの型の変換を通さず文字で受けて厳しく読む形にした。範囲の外の値（ゼロ日付・`838:59:59` など）は D1: A の 409 `BUSINESS_TARGET_DB_MISMATCH`（理由 `READ_CONVERSION`）になる。設計の文書（ドライバーの `getObject` で読む書き方）との差。
2. **G16**: `SET time_zone = '+00:00'` を `RowSqlExecutor` の定数にした（方言は「要る・要らない」だけを返す）。SpotBugs の除外は計画の3つ（クラス・メソッド・パターン名）のまま広げていない。
3. **G17〜G20**: 定義の応答の項目を契約 C2 より足した（`schema`・`table`・`displayName`・選択肢の表示名）。U3 の画面はこの形で受ける。
4. **verify の時間**: 18 分 32 秒（直近から 4 分 31 秒、見込みの約 45 秒より長い）。42 分の線の内のため、テストの分け方は諮らなくてよい見込み。延びた内訳（中継の3つ・3種類の対象DB の結合テストの数）は Build and Test で CI の時間と並べて記録する。
5. **G26〜G33**: 負荷の試験の台本の形（`biz_wide`・`bizSearchWide` の判定・種の式・試験用の利用者とロール・compose の `command`）。

## 7. 後の段への引き継ぎ

- **Build and Test**: verify の時間（18 分 32 秒）と CI の時間を並べて記録する（NFR7.3）。E2E は 232 件の全件が通った。
- **Performance Validation**:
  - 3種類の対象DB を1つずつ、`perf/README.md` の「業務データの場面」の手順で流して判定する（NFR1.1・NFR1.3・NFR2.4・NFR2.5・NFR3.2）。MySQL のメモリの上限は U2 の試しの結果の 2304m（バッファープール 768M）を使う（U1 の設計の文書の 1792m とは違う）。
  - 4.1 の R-06: 部分一致（`bizSearchContains`）は、表の設定を直しても 10 VU で 1 秒を超える見込みが高い（試し走りの 1 VU・PG・既定の設定でも p95 162.7〜272.1 ms）。超えたら Not Met として扱いを諮る。目標は緩めない。
  - 4.2 の R-05: VM の表の行 3 は、MySQL が 1792m のとき 6,144 MiB、2304m のとき 6,656 MiB で、どちらも収まらない。MySQL・MariaDB の回では lgtm を動かさない。k6 のコンテナの使う量は `bizSearchWide` を流すときに記録する。
  - 4.1 の R-05（D2: A）: `bizSearchLastSorted` は索引の無い `c_001` で測る悪い側の条件。MySQL・MariaDB で 1 秒を超えたら Not Met として扱いを諮る。
  - 試し走りの値（判定に使わない）: `bizDefinition` 2.1 ms・`bizSearchFirst` 24.1 ms・`bizSearchContains` 162.7 ms・`bizSearchLast` 13.1 ms・`bizSearchLastSorted` 47.9 ms・`bizDetail` 24.6 ms・`bizSearchWide` 48.2 ms（2回目の p95）。`biz_item` は PG で 196 MiB（索引 2.2 MiB）。
  - MySQL・MariaDB の種の SQL は、出した SQL を読んで確かめただけで、まだ流していない（D4: A）。最初の回で SQL が通ることと行の数を確かめる。
- **Observability Setup**:
  - 4.2 の R-07: 対象DB のプールの指標のラベルは `pool="mastersmith-target-db"`。プールは最初に借りるときに作られるため、系列も最初の借用まで出ない。式を流す前に業務データの一覧か詳細の API を1回呼ぶ。
  - NFR8.1（`ms-forbidden` のしきい値の見直し）・NFR8.3（ダッシュボードの業務データの区画）は `traceability.json` で Deferred にした。

## Sources

- `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/code-generation/code-generation-plan.md`・`unit-test-instructions.md`・`generation-notes.md`
- `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/` の `functional-design/`・`nfr-requirements/`・`nfr-design/`・`infrastructure-design/`
- `aidlc/spaces/default/intents/261016-business-data-views/construction/code-generation/memory.md`（Deviations）、`inception/delivery-planning/bolt-plan.md`
- 実行の記録: `build/verify-b2.log`・`build/e2e-b2.log`・`backend/build/reports/jacoco/test/jacocoTestReport.xml`・`build/perf-results/biz-dry-*.json`・`biz-dry2-*.json`（どれも git の対象外）
