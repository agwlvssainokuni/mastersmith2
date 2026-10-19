# コードの要約 — U2 u2-businessdata-write（Bolt B4）

承認済みの計画（`code-generation-plan.md`）の Step 1〜31 を行った。Step 32（コミット）と Step 33（統合）は、指揮役が依頼者の承認を得て行う。作業ブランチは `feature/261016-business-data-views-b4`。コミット C1〜C8（ac101b6〜6de27b3）は依頼者の承認を得て指揮役が積み、その後にコードのレビュー（READY・Minor 3）の R-01〜R-03 を作業ツリーで直した（下の 4.1）。計画に無い変更と判断の詳細は `generation-notes.md`（N1〜N30・F1〜F2・G1〜G17・R-01〜R-03）にあり、ここではその要点を書く。

## 1. 作ったもの・変えたもの

作った・変えたアプリのソースは `source-manifest.json` に 157 のパスで並べた（消したものは無い）。

| 場所 | 中身 |
|---|---|
| `backend/src/main/java/cherry/mastersmith/targetdb/` | 書き込みの設定（`TargetDbProperties` の `write`・`QueryTimeout.write`、点検の `TargetDbWriteSettings`、作る条件）、名前つきの `targetWriteDataSource`・`targetWriteTransactionManager`（既定の候補にしない）、読み取りのプールの MySQL・MariaDB の接続を作るときの `SET time_zone`（D5）、書き込みの口 `TargetWriteAccess`・`JdbcTargetWriteAccess` と domain の型（`TargetWriteSession`・`WriteStep`・`TargetWriteOutcome` など） |
| `backend/src/main/java/cherry/mastersmith/businessdata/` | domain（書き込みの値の型・検証の材料 `ColumnWriteRules` とその関数・検証 `WriteRequestValidator`・締め切りのある pattern の照合・監査の出来事と理由・`detail` の組み立て・失敗の型）、repository（`RowSqlExecutor` の書き込みの5つのメソッド・`RowWriteSqlBuilder`・書き込みの失敗の分け方・型つきのバインド）、service（`BusinessDataWriteService`・`WriteResult`、`TableAccessResolver` に書き込みの口）、web（3本の API・要求と応答の record・道ごとの本文の上限・定義の API の検証の材料） |
| `backend/src/main/java/cherry/mastersmith/audit/` | 3つの種類・9つの理由の値・`UNKNOWN`、監査の表の3列、業務データの出来事の組み立てと受け取り、失敗の ERROR（主キーの値・`detail`・原因を載せない） |
| `backend/src/main/resources/` | `application.yaml` の書き込みの4項目、`db/migration/V13__u2_business_data_audit_columns.sql`（3列だけ） |
| `backend/config/spotbugs-exclude.xml` | `RowSqlExecutor` の書き込みの5つのメソッドと2つのパターンに絞った `SQL_` の除外（A3） |
| `.env.example`・`.env.targetdb.example` | 書き込みの4項目のコメントの行と、見本の DB の書き込みのパスワード |
| `docker/targetdb/{postgres,mysql,mariadb}/03-writer-account.sh` | 見本の DB の書き込みのアカウント `mastersmith_writer`（冪等。行の操作だけ。PG は DB の `TEMPORARY` を PUBLIC から取り上げる） |
| `perf/` | `make-biz-seed.sh`（`biz_ref`・`biz_new` と書き込みのアカウントへの付与）、`make-biz-dsl.mjs`（同じ2つの表、`required`・`unique`・外部キーの口）、`k6/scenarios.js` の5場面（`bizCreate`・`bizUpdate`・`bizDelete`・`bizWritePoolLimit`・`bizCreateLarge`）、`dsl-timing.sh` と `README.md` の一時の環境ファイルの書き込みのパスワード、`README.md` の「業務データの書き込みの場面」 |
| `README.md` | 環境変数の表の4行、「対象DB」の節（書き込みの接続、書き込みの API の小見出し、見本の書き込みのアカウントと既にあるボリュームへの足し方・消し方・初期化が止まったとき・残る広さ・既知の制約）、「コンテナでの起動と確認」の項目の数 |
| `backend/src/test/java/cherry/mastersmith/` | 単体・性質ベース・境界・結合のテスト。結合テストは3種類の DB の書き込み・認可と IDOR・本文の上限・時間切れ（3段 × 3種類）・設定の欠け・監査・漏えい・トレース・接続の数・見本の初期化の台本。直した既存のテストは計画 1.3 のとおり（加えて N12・N20・N28・N29） |

触れていないもの: `packagesJudgedByTotal` の7パッケージの本体のソース、`docker/monitoring/`、`.github/workflows/ci.yml`、`compose.yaml`・`docker/perf/compose.yaml`、`frontend/`、`vendor/`、配備した環境（アプリ・見本の PG・Mailpit とボリューム・`.env`・`.env.targetdb`）。

## 2. 主な決定

- 判定の順は ②〜④（U1 の解決の口）→ ⑤ 定義が古い → ⑥ 主キーとビューの決まり → ⑦ 補助権限・FULL → ⑧ 書き込みの設定 → ⑨ 入力の誤り → ⑩ 対象DB の結果。⑤〜⑨ で拒んだ要求は書き込みの接続を借りない（`BusinessDataWriteAuthorizationApiIT` で借りた回数 0 を確かめた）。
- 書き込みの口は1要求で1回だけ借り、READ COMMITTED の対象DB 用のトランザクションで、事前の問い合わせ・書き込みの文・セーブポイントへ戻した後の違反の確かめを同じ接続で行う。内部DB のトランザクションは開かず、監査は口から戻った後に出す。
- 503 の出し分けは D1〜D4: A のとおり（競合・書き込みの文の段・違反の後の段の時間切れは `UNAVAILABLE`、確定の段だけ `TIMEOUT` と監査 `UNKNOWN`）。確定の段の類 23 と形の誤りは 409 `BUSINESS_SAVE_REJECTED`。
- 違反の後の確かめが失敗したときは、誤りを付けずに 409 `BUSINESS_SAVE_REJECTED` だけを返す（依頼者の答え G5: A。BR6.4 の「field を空にした FieldViolation だけを付け」る、との差）。
- 秒の端数の精度は、型の名前の `(n)` → DSL の精度 → DB の種類の既定の順で決める（依頼者の答え G1: A）。

## 3. テストとカバレッジ

`:backend:cleanTest :backend:cleanIntegrationTest verify` を `caffeinate -dims` で包んで流した（2026-10-20 01:54〜02:14、眠り・DarkWake なし。実測の数字だけ）。

| 項目 | 結果 |
|---|---|
| `verify` | 通過。20 分 6 秒（U1 の B2 の後の 18 分 32 秒から 1 分 34 秒延びた。CI の上限の7割の約 42 分の線の内。NFR7.4） |
| バックエンドの単体テスト | 2,583 件（失敗 0・飛ばし 0） |
| バックエンドの結合テスト | 1,383 件（失敗 0・飛ばし 9。どれも DB の種類に当たらない確かめの `assumeTrue`（U1 の読み取り 4・U2 の書き込み 5）で、コンテナの実行環境の飛ばしではない） |
| フロントエンドのテスト | 188 ファイル・1,633 件 |
| カバレッジ（バックエンドの全体） | 行 97.6%・分岐 91.2% |
| カバレッジ（パッケージごと、行・分岐） | `businessdata.domain` 97.5%・89.8%、`businessdata.repository` 95.9%・87.9%、`businessdata.service` 95.2%・89.1%、`businessdata.web` 97.6%・77.9%、`targetdb.config` 100.0%・99.1%、`targetdb.domain` 100.0%・97.2%、`targetdb.repository` 96.0%・87.5%、`targetdb.service` 89.1%・75.4%、`audit.domain` 99.6%・95.4%、`audit.service` 98.2%・84.8%。どれも下限（行 80%・分岐 70%）を満たす。除外は増やしていない |
| カバレッジ（フロントエンドの全体） | Stmts 95.2%・Branch 90.32%・Funcs 96.73%・Lines 95.37% |
| `packagesJudgedByTotal` | 7パッケージの本体のソースに手を入れていない（一覧は変えない） |
| E2E（`./gradlew e2eTest`） | 232 件 通過（10.7 分。失敗・flaky 0。2026-10-20 02:15〜02:25、眠り・DarkWake なし）。E2E のファイルは足していない（業務データの代表の流れは U3） |
| SpotBugs の関門 | 通過（`SQL_` の残り 0 件。除外は A3 の5メソッド・2パターンだけ） |
| 構造の検査 | 境界テスト・`ArchitectureTest`・`ApiAccessArchitectureTest`・`ExtensionOrderArchitectureTest` の 92 件 通過 |
| k6 の台本（D8: A） | `k6 inspect --include-system-env-vars` で5場面の名前・実行の形・閾値を確かめた。PG の1種類・既定の設定・配備したアプリを止めずに、各場面 1 VU・10 秒（`bizDelete` は 10 回、`bizCreateLarge` は 1 回）を流し、閾値と checks をすべて通った（判定ではない）。繰り返しの p95 は `bizCreate` 4.5 ms・`bizUpdate` 1.3 ms・`bizDelete` 4.6 ms・`bizWritePoolLimit` 1.8 ms、`bizCreateLarge` 205.7 ms。書き込みのプールの待ちの時間切れの累計 0・待ちの最大 5.9 ms。`bizCreateLarge` の後のアプリの `memory.peak` 618 MiB（`anon` 610 MiB）、perf の PG は OOMKilled なし・`oom_kill` 0。ログに試験用の利用者と値は 0 件、ERROR 0 行（WARN は G16）。perf の PG の初期化（01〜03）が通り、`docker compose -f docker/perf/compose.yaml config` の既定の `command` と `mem_limit` が今のままであることを確かめた。MySQL・MariaDB の種の SQL は出したところまで |

### 3.1 コミットの区切りのコンパイル

7節の区切り（C1〜C6。C7・C8 は Java を含まない）を、HEAD を写した使い捨ての置き場に区切りの順に重ねて、区切りごとに `./gradlew :backend:compileJava :backend:compileTestJava` が通ることを確かめた（2026-10-20）。ファイルの数は C1 30・C2 63・C3 12・C4 21・C5 20・C6 5（`backend/`・`docker/`・`.env*` の 151 のすべて）。

## 4. 計画との差

| # | 要点（詳細は `generation-notes.md`） |
|---|---|
| N1〜N18 | 1回目の依頼の進め方と型の形の差（書き込みの設定の点検を別の型に、口の失敗の型、検証の材料を判定の材料に載せる、U1 の理由の値の数のテストの直し、A2 を早めに入れたこと など） |
| N19〜N30 | 2回目の依頼の進め方の差（競合の起こし方、U1 の `BusinessDataTimeoutIT` の通す数を MySQL・MariaDB とも 0 に、中継の `stallReport()`・`armAfter()`、時間切れの実測、トレースは集め手で、初期化の台本の IT、種の付与の置き場と書き込みのロールを setup で作ること、読み直しの CAST、MariaDB の TIMESTAMP の範囲、定義の API の結合テスト、接続の数の行） |
| F1 | PostgreSQL の `int8` の範囲を符号つきにした（BIGINT の上限 + 1 が 409 になっていた。BR3.5） |
| F2 | MySQL の Connector/J の類 22（SQLState 22001）を形の誤り（D2）に含めた（500 になっていた） |
| BR6.4 との差 | G5: A のとおり、違反の後の確かめの失敗に `fieldErrors` を付けない |
| コミットの区切り | 下の 7節。A2 は C2 に入れる（C2 の時点で境界テストを通すため）。README の環境変数の4行は Step 28 でまとめて書いたため、C1 と C8 に分けて積む |

### 4.1 コードのレビューの直し（R-01〜R-03）

| # | 直したこと |
|---|---|
| R-01 | 見本の DB の `03-writer-account.sh` の3本のライセンスヘッダーに最後の行を足し、`02-reader-account.sh` とそろえた。PostgreSQL の台本の mode を `100755` にした |
| R-02 | 名前をそのままドライバーに渡す今の形のまま。pgjdbc は `RETURNING` の名前を自分で引用し（既定の `quoteReturningIdentifiers`）、MySQL・MariaDB は名前を使わないため。大文字と小文字の混じる名前（`ItemId`）と予約語（`select`）の自動の採番の主キーの結合テストを3種類の DB に足した |
| R-03 | 生成された鍵を文字として読めないときは、作成を失敗にせず鍵を返さない（201）。単体テストを足した |

直した後に、`verify`（通過、21 分 4 秒。単体 2,584 件・結合 1,386 件（飛ばし 9）・フロントエンド 1,633 件、全体 行 97.6%・分岐 91.2%、`businessdata.repository` 96.2%・87.9%）と E2E（232 件 通過、10.6 分）を流し直した（眠りなし。`generation-notes.md` の 3.1）。3節の表は直す前の回の数字。

## 5. Bolt の計画との差

無い（B4 は U2 の1つの単位）。

## 6. 依頼者の答えと残る確かめ

G13〜G16 は依頼者が A で答えた（`generation-notes.md` の 2.5）。

| # | 決まったこと |
|---|---|
| G13 | 既知の制約: MySQL・MariaDB の `bigint`（と `int` などの整数の型）は DSL の型の名前から符号なしかが分からず、符号つきのカラムの範囲の外の値は 400 でなく DB の拒否の 409 になる |
| G14 | F2 の `22001` を保存の拒否（409）に含める読み方のまま |
| G15 | U1 の既知の制約として後の Intent へ持ち越す（MySQL の件数の問い合わせが止まると、時間切れの後に止まった接続がプールに戻り、次の1回が 503 になる） |
| G16 | Performance Validation の WARN の件数から、`bizWritePoolLimit` の想定どおりの 409（`BUSINESS_REFERENCE_NOT_FOUND`）を code で絞って外し、外した件数を並べて記録する |
| G6〜G12 | 1回目の依頼で記録した判断（承認の場で確かめる） |

## 7. コミットの区切り

依頼者の承認を得て、指揮役が C1〜C8（ac101b6〜6de27b3）として積んだ。区切りは依頼者の決定で「ファイルを部分で分けない形」にし、`README.md` は全体を C8、`BusinessDataBoundaryArchitectureTest` は全体を C4 に入れた。下は提案のときの案（記録として残す）。コードのレビューの直し（4.1）は、この後の作業ツリーにある（まだ積んでいない）。

計画の7節の C1〜C8 の区切りのまま、生成で増えたものを次のとおり入れる。どのコミットの時点でもコンパイルが通ることは、提案のときに区切りごとに `./gradlew :backend:compileJava :backend:compileTestJava` で確かめる（`project.md` の学び）。

| # | 中身 |
|---|---|
| C1 | `targetdb`（本体の config・domain・service の全部）と `targetdb/config`・`targetdb/domain`・`targetdb/service` のテスト（`TargetWriteTransactionManagerInjectionIT`・`TargetDbStartupIT`・`TargetDbSecretLeakIT` を含む）、`application.yaml`、`.env.example`、`README.md` の環境変数の表の4行 |
| C2 | `businessdata.domain`・`businessdata.repository`（F1・F2 を含む）とその単体・性質ベースのテスト、`businessdata/testsupport` の `BusinessDataDslFixture`・`SteppingClock`、`spotbugs-exclude.xml`（A3）、`BusinessDataBoundaryArchitectureTest` の A2 の部分、`BusinessDataTimeoutIT` の通す数の直し（N20）と `StallingRelay`（N21。`BusinessDataTimeoutIT` が `stallReport()` を使うため） |
| C3 | `audit`（本体と `audit` のテスト。`AuditSchemaIT`・`AuditMigrationCompatibilityIT`・`AuditSecretLeakIT` を含む）、`V13`、`BusinessDataBoundaryArchitectureTest` の A1 の部分 |
| C4 | `businessdata.service`・`businessdata.web` とその単体テスト、`BusinessDataReadControllerTest`・`BusinessDataReadServiceTest`・`TableAccessResolverTest`・`BusinessDataProblemTypeCatalogTest` の直し、`FakeBusinessDataPorts`、`ApiEndpointCounts`、`BusinessDataBoundaryArchitectureTest` の残り（`noTransactionsOnTheWriteSide`） |
| C5 | 書き込みの結合テスト（3種類の DB・認可・本文の上限・時間切れ・設定の欠け・監査・漏えい・トレース）、`BusinessDataConnectionUsageIT`・`AbstractBusinessDataReadIT` の直し、`businessdata/testsupport` の `BusinessDataTestApp`・`BusinessDataSampleSchema`・`BusinessDataWriteTables`・`CountingDataSourceConfig`、`targetdb/testsupport` の3つ（`createWriterAccount`） |
| C6 | `docker/targetdb/*/03-writer-account.sh`、`.env.targetdb.example`、`TargetDbSampleInitScriptIT` |
| C7 | `perf/`（`k6/scenarios.js`・`biz/make-biz-seed.sh`・`make-biz-dsl.mjs`・`dsl-timing.sh`・`README.md`） |
| C8 | `README.md` の残り（「対象DB」の節と「コンテナでの起動と確認」の項目） |

`BusinessDataBoundaryArchitectureTest` は1つのファイルを C2・C3・C4 に部分で分ける。決まりはどれも名前の文字で書いてあり、どの区切りでもコンパイルは通る（部分に分けないときは C4 にまとめて入れ、C2・C3 の時点ではその境界テストが落ちる）。

## 8. 後の段への引き継ぎ

計画の9節のとおり。加えて次のこと。

- **Performance Validation**: G16: A の WARN の数え方（想定どおりの 409 を code で絞って外し、外した件数を並べて記録する）。`bizWritePoolLimit` の setup は前の場面が残した書き込みのロールの設定を受け入れる（409 `ROLE_NO_CHANGE` が `http_req_failed` に入る。判定には使わない。G17）。
- **Deployment Execution**: 見本の PG に書き込みのアカウントを足す手順は README の「既にある見本の DB に書き込みのアカウントを足す」（初めての実地）。`.env` と `.env.targetdb` に1行ずつ足す前に、`.env` をリポジトリの外へ複写する。
- **U3**: 定義の API のカラムの検証の材料は、入力できないカラムでは null（`nullable` だけは DSL の値）。503 の出し分けは 2節のとおり。

## Sources

- 計画: `aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/code-generation/code-generation-plan.md`（D1〜D8: A）・`generation-notes.md`
- 設計: 同じ単位の `functional-design/`・`nfr-requirements/`・`nfr-design/`・`infrastructure-design/`
- 決まり: `aidlc/spaces/default/memory/team.md`・`project.md`
- 実測: `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify` と `./gradlew e2eTest` の出力、`backend/build/reports/jacoco/test/jacocoTestReport.xml`、試し走りの k6 の要約（`build/perf-results/biz-postgres-*.json`）
