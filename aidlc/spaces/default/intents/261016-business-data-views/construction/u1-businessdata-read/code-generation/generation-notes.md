# 生成の記録 — U1 u1-businessdata-read（Bolt B2）

承認済みの計画（`code-generation-plan.md`）を書き換えず、計画に無い変更・計画と違う判断・依頼者に確かめたいことをここに記録する（`project.md` の学び）。計画のチェックボックスの印のほかは、計画を変えていない。

## 1. 1回目の依頼（Step 1〜12）

### 1.1 進め方の差

| # | 計画 | 実際 | 理由 |
|---|---|---|---|
| N1 | Step 1「`aidlc/` の下に未コミットの変更が無いことを確かめる（あれば依頼者にコミットの承認を得てから進む）」 | 始めの時点で `aidlc/spaces/default/intents/261016-business-data-views/audit/sakura-local-4e42a93f87ce.md`（監査ログ）に未コミットの変更があった。途中で `construction/code-generation/memory.md` にも変更が出た。どちらも指揮役とフックが書く記録で、生成の担当はコミットしない決まりのため、触れずに進めた | 監査ログは指揮役の流れの中で書き足されるもの。統合の前にコミットする（計画の Step 28） |
| N2 | Step 6（`targetdb.config` の実装）は Step 5（domain の層のテスト）の後 | Step 6 の実装を Step 5 のテストを流す前に行った。あわせて、Step 7 の既存のテストのうち `QueryTimeout` の作り方を使う2か所（`TargetDbSettingsTest`・`TargetSchemaReaderTest`）を、Step 5 の前に4つの引数に直した | Step 2 で `ReadPurpose` に `LIST`・`DETAIL` を足すと、`TargetDbSettings.Usable#queryTimeout` の `switch` が網羅されずコンパイルが通らない。テストのコンパイルも同じ。層ごとの「実装 → その層のテスト → 実行」の順（Testing Contract）は、domain の層・設定・repository・service のそれぞれで守った |
| N3 | 値の型のテストを `RowKeyTest`・`RowValuesTest`・`BoundValueTest`・`SearchConditionTest`・`DefinitionIdTest` の5つに分ける | `businessdata/domain/ValueTypesTest` の1つにまとめた（8件） | 確かめる中身（伏せ字の toString・変更できない・必須の値）が同じ形のため。件数は5つの合計の目安を満たす |

### 1.2 計画の部品の一覧（1.1節）に無い型（足したもの）

部品の名前は設計の仮の名前のまま使った。計画の表に無い次の型を足した。どれも計画の Step の中身を作るための型で、境界・依存の向きは変えていない。

| 置き場 | 型 | 理由 |
|---|---|---|
| `targetdb.domain` | `TargetReadUnexpectedException` | 計画 4.1 の R-02 (1) の「メッセージも原因も持たない例外」の置き場。`businessdata` から見える `targetdb.domain` に置いた（`targetdb.config` は外から使えない） |
| `targetdb.config` | `TargetDbProperties.QueryTimeout#longest()` | 4つの上限の最大を1か所で求める（基盤の設計のレビュー R-01）。`TargetDataSourceConfig.driverProperties` が使う |
| `businessdata.domain` | `SearchMode`・`SearchRule` | 読み替えの後の演算子（契約 C2 の `SearchField.operator` の4つ）と CHOICE の選択肢。DSL の `SearchOperator` は `IN` を持つため別にした |
| `businessdata.domain` | `ColumnKind`・`TableAccessInput` | 出し分けの関数（`TableAccessPolicy`）を純粋な関数のまま保つため、DB の種類から決まる型（BR7.1・BR7.9）を入力として渡す |
| `businessdata.domain` | `TableQuery`・`SearchInput`・`RawCondition`・`RawSort`・`KeyInput` | 本文の確かめる前の値を、値を伏せた toString の型で業務処理の層に渡す（TraceAspect が service の引数を TRACE に出すため。BR10.1） |
| `businessdata.domain` | `RowRecord` | 1行の主キー（切らない）と値（切る）の組 |
| `businessdata.domain` | `SearchValidation`・`KeyValidation`・`BusinessDataFieldError` | 確かめの結果の型と fieldErrors の1件 |
| `businessdata.domain` | `RowQueryFailureReason`・`RowQueryStage`・`ReadRejection` | 失敗の理由・段階、読み取りの拒否（判定の順の ②〜⑧）。web の層が `switch` で場合を尽くす |
| `businessdata.repository` | `CellValue` | 結果の1つの値を文字にした結果（読めないときは `Unreadable`。R-02 (3)） |
| `businessdata.repository` | `RowSqlBuilder`（パッケージの中だけ） | SQL の文字の組み立て。文（`prepareStatement`・`createStatement`）は作らない（A2 のとおり、文を作るのは `RowSqlExecutor#search`・`#detail` の本体だけ） |
| `businessdata.service` | `TableAccessResolution`・`ReadRejected` | 解決の結果と、3つの結果の型に共通の拒否 |

### 1.3 計画・設計で決まっていなかった点の判断（依頼者に確かめたいこと）

| # | 論点 | 判断 | 確かめたいこと |
|---|---|---|---|
| G1 | 既定の DSL の生成は、MySQL・MariaDB の `tinyint(1)` を型の名前 `tinyint`（`DATA_TYPE`）だけで写し、`(1)` を落とす（`NUMERIC_PRECISION` も 3）。BR7.1 の「`tinyint(1)` は BOOLEAN」を型の名前だけで見分けられない | `ColumnValueCodec` で、名前が `tinyint(1)…` のときに加えて、**フォーム部品が checkbox・switch の `tinyint`** も BOOLEAN にした（生成は `tinyint(1)` に checkbox を付けるため） | **依頼者の決定（A）**: この読み方で決まり。フォーム部品が checkbox・switch の `tinyint` も BOOLEAN として扱い、BR7.1（型の名前で分ける）との差として記録する |
| G2 | 主キーに BINARY と同じ扱いの型（`bytea`・MySQL の `bit(8)` など）や BR7.9 の型（PG の `json` など）を含むテーブルは、DETAIL を許さない（計画 4.1）。そのときの `restrictionReason` の値が設計で決まっていない（BR2.7 の3つに当たる値が無い） | `restrictionReason` は **null** にした（理由の Alert は出ないが、DETAIL は許さず、詳細の API は 409 `BUSINESS_ROW_OPERATION_NOT_ALLOWED`）。新しい値は足していない（契約 C2 の列挙を変えないため） | **依頼者の決定（A）**: `restrictionReason` は null のままとし、新しい値は足さない |
| G3 | `bit` の長さが DSL に無いとき | MySQL の既定の `BIT(1)` として BOOLEAN にした。長さは名前の `(n)` → 精度 → 長さの順に読む（生成の DSL は精度に長さが入る） | — |
| G4 | 整数の型の範囲の確かめ（BR4.7 の桁あふれ） | DSL は `unsigned` を持たないため、型ごとに「符号つきの下限〜符号なしの上限」の和で確かめた（例 `int` は −2,147,483,648〜4,294,967,295。PG の `int2`・`int4` は符号つきの範囲）。範囲の外は 400 `INVALID_VALUE`。和の範囲の中で DB の型の範囲を超える値は、DB の型の誤りの SQLState（22003 など）で 400 になる | — |
| G5 | 数・真偽値として読む文字の形 | DECIMAL は指数の形（`1e5`）と `NaN` を受け付けず、`[+-]?数字[.数字]` だけにした。BOOLEAN は `true`・`false` だけ（大文字は受け付けない）。日付・時刻は ISO の形 | — |
| G6 | 既定の並び（`defaultSort`）に password・BINARY・OTHER のカラムが書かれているとき | 並べ替えの決まり（BR5.2）と同じく、既定の並びのキーからも外した | — |
| G7 | 主キーのカラムが NONE のテーブルの並び | 名前だけを使い、値は返さない形で、主キーの順のキー（BR5.4）と深いページの主キーを先に読む形に使った（名前の一覧 `KnownIdentifiers.columns` はテーブルのすべてのカラムの名前） | — |
| G8 | DB の種類が分からないとき（設定が無い）の型 | 3種類のどれかで BINARY なら BINARY、3種類で同じならその型、違えば OTHER（例 `timestamp` は OTHER）。BR7.9 は3種類の一覧の和（計画の Assumptions のとおり狭める向き） | — |
| G9 | 失敗の分け方の細部 | MySQL・MariaDB の `70100`（問い合わせの中断）も時間切れに数えた。結果を読む段階は、時間切れと接続の失敗（08）のほかは、SQLState によらず `READ_CONVERSION`（409 `MISMATCH`。D1: A）。バインドと実行の段階の非検査の例外は `UNEXPECTED` | — |
| G10 | `definitionChanged` | BR9.2 のとおり 400 にだけ付け、503・409・404・403 の拒否では false にした | — |
| G11 | 設定が無いとき（`NotConfigured`）のログ | 業務処理の層の WARN の1行を、理由 `NOT_CONFIGURED`（SQLState などは null）で出す。借りる失敗は理由 `UNAVAILABLE` | — |
| G12 | 詳細の SQL | `setMaxRows(2)` に加えて、文の末尾に定数の `LIMIT 2` を置いた（BR6.3 の取り出しの上限を DB の側でも当てる） | — |
| G13 | 一覧で値を読む列が1つも無いとき（すべて password・BINARY など） | 行の数を数えられるよう、定数 `1` を1つ選ぶ（応答の値には使わない） | — |
| G14 | 借りるときの非検査の例外 | `getConnection` の非検査の例外も `Unavailable` にした（例外のクラスの名前だけ） | — |

### 1.4 流したテスト（この依頼の範囲）

colima が動いていることと、README の `DOCKER_HOST`・`TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` をシェルに渡したことを確かめて流した。結合テストは `caffeinate -dims` で包み、流した後に `pmset -g log` で Sleep・DarkWake が無いことを確かめた。

| Step | コマンド | 結果 |
|---|---|---|
| 1 | `./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.domain.*' --tests 'cherry.mastersmith.targetdb.config.*'` | 61 件 通過（変更の前） |
| 5 | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.domain.*' --tests 'cherry.mastersmith.targetdb.domain.*' --tests 'cherry.mastersmith.role.domain.PermissionSnapshotTest'` | 102 件 通過 |
| 7（単体） | `./gradlew :backend:test --tests 'cherry.mastersmith.targetdb.config.*' --tests 'cherry.mastersmith.targetdb.service.TargetSchemaReaderTest'` | 55 件 通過 |
| 7（結合） | `caffeinate -dims ./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*'` | 50 件 通過（飛ばし 0。3種類の対象DB のコンテナ） |
| 9 | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.repository.*'` | 79 件 通過 |
| 12 | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.service.*' --tests 'cherry.mastersmith.targetdb.service.JdbcTargetReadAccessTest'` | 40 件 通過 |
| 確かめ | `./gradlew :backend:test`（バックエンドの単体テストの全体） | 2,329 件のうち 1 件失敗（`RoleBoundaryArchitectureTest`） |

`RoleBoundaryArchitectureTest` の失敗は、`businessdata` が `role.domain`・`role.service` に依存したことによる 34 件の違反で、計画のとおり Step 16 の A1（承認済み）で依存してよい相手に `businessdata` を足すまで続く。ほかの単体テストは通っている。

### 1.5 次の依頼（Step 13 以降）への引き継ぎ

- web の層は、業務処理の層の結果の型（`DefinitionResult`・`SearchResult`・`RowReadResult` と共通の拒否 `ReadRejected`）を `switch` で場合を尽くして `BusinessException` に変える。`ReadRejection.ACCESS_DENIED` は `ProblemTypeRegistry` から code で引き、`VALIDATION_FAILED` には `fieldErrors` と `definitionChanged` を載せる。
- 本文のページの番号と件数は `BigDecimal` で受ける（`SearchInput`）。整数でない値も 400（fieldErrors なし）にするため。
- `RoleBoundaryArchitectureTest` の A1 の変更は Step 16 で行う（それまで単体テストの全体は1件落ちる）。

## 2. 2回目の依頼（Step 13〜20）

### 2.1 依頼者の決定

- G1・G2 は、どちらも依頼者の決定（A）で、1回目の判断のままに決まった（1.3 の表に書き足した）。

### 2.2 計画に無い変更・計画と違う判断

| # | 中身 | 理由 |
|---|---|---|
| G15 | MySQL・MariaDB の日付・日時・時刻（時差つきを含む）は、ドライバーの型の変換（`getObject(LocalDate…)`）を通さず、`getString` で文字として受けて厳しく読む形にした（`RowDialect#readsTemporalAsText`、`ColumnValueCodec`・`MysqlFamilyRowDialect#readDateTimeTz`）。読めない値は D1: A のとおり 409 `BUSINESS_TARGET_DB_MISMATCH`（理由 `READ_CONVERSION`）。PostgreSQL は今までどおり JDBC の型で読む | Step 15 の結合テストで、MariaDB のドライバーがゼロ日付（`0000-00-00`）を黙って NULL にし、`838:59:59` を黙って `22:59:59`（24 時間で割った余り）にして 200 で返すと分かった（誤った値を見せる）。MySQL も同じ形にそろえた。設計（reliability-design.md の 3節）は MySQL・MariaDB の `timestamp` を `LocalDateTime` で読むとしているが、`SET time_zone = '+00:00'` の後にサーバーが送る文字は UTC の日時のため、読む値は変わらない（Step 15 の時差の確かめで3種類とも通った） |
| G16 | 貸し出しの始めの `SET time_zone = '+00:00'` を、方言が返す文字（`Optional<String> sessionInitSql()`）から、`RowSqlExecutor` の定数 `SET_TIME_ZONE_UTC` に移した（方言は `needsUtcSession()` だけを返す） | SpotBugs が、方言から受けた文字を `Statement.execute` に渡す所を `SQL_NONCONSTANT_STRING_PASSED_TO_EXECUTE`（計画の A2 の見込みに無いパターン名）として報告した。除外を広げずに済むよう、文を定数にして指摘を消した。直した後に報告されたのは、`RowSqlExecutor` の `search`・`detail` の `SQL_INJECTION_JDBC`・`SQL_PREPARED_STATEMENT_GENERATED_FROM_NONCONSTANT_STRING` だけで、計画の見込みと同じだった。`RowSqlBuilder` には指摘が無い |
| G17 | `TableAccessView` にテーブルの表示名（`displayName`）、`SearchRule` に選択肢の表示名（`options`。`choices()` は値の一覧）を足した。真偽値の選択肢（`true`・`false`）の表示名は値のまま | 定義の応答（契約 C2 の `TableDefinition.displayName`・`SearchField.options[].label`）に要るため。真偽値の表示の文言は画面（U3）が `listFormat` に合わせて当てる |
| G18 | 要求の定義の識別を、形を確かめない `RequestedDefinitionId` で受けるようにした（`TableQuery.definitionId` の型を変えた。`DefinitionId#changedFrom` も同じ型を受ける） | 画面が形の正しくない識別を送っても、判定の順（403 などが 400 より先）を変えず、「今と違う」として扱うため |
| G19 | 利用者 ID は、`auth` の `AuthenticatedUser` ではなく、`role` の `RoleRequestContextResolver` と同じ形の `BusinessDataRequestContext`（認証の結果の名前を利用者 ID として読む）で読む | 境界（BR11.2）で `businessdata` が依存してよい相手に `auth` が無いため |
| G20 | 定義の応答に、契約 C2 に無い `schema`・`table`（要求の名前の写し）を足した。`primaryKeyColumns` は DETAIL が許されるときだけ、`hasPrimaryKey` は主キー（ビューは主キーの設定）の有無 | 画面が応答だけで表を見分けられるように。名前は要求の写しで、行の値ではない |
| G21 | 結合テストの対象DB のサーバーのタイムゾーンは、PostgreSQL が `America/New_York`（夏時間のある地域）、MySQL・MariaDB は `-05:00` にした | MySQL・MariaDB のコンテナは名前のタイムゾーンの表を持たないため。アプリは貸し出しの始めに `+00:00` にするため、サーバーの既定によらないことは確かめられる |
| G22 | 3種類の対象DB の結合テストで、DB の種類に当たらない確かめ（PostgreSQL の `timetz`、MySQL・MariaDB のゼロ日付と `bit`・空間の型）は `assumeTrue` で飛ばした。結果の「skipped」4件はこれで、コンテナの実行環境が無いための飛ばしではない | 1つの共通の確かめのクラス（`AbstractBusinessDataReadIT`）で3種類を流すため |
| G23 | つないだ後に止まる中継（`StallingRelay`）は、PostgreSQL では問い合わせの文字（`COUNT(`）で、MySQL・MariaDB では「大きなまとまり（40 バイト以上）を N 個通した次」で止める。N は手元の確かめで MySQL 9.7（Connector/J）が 2、MariaDB（3.5）が 1 | MySQL・MariaDB は既定で TLS の通信になり、問い合わせの文字が見えなかった。止めた段が件数の問い合わせであることは、503 TIMEOUT（貸し出しの始めで止めると UNAVAILABLE）で確かめる。ドライバーやサーバーの版を上げて N が変わると、このテストが落ちて気づける |
| G24 | 見本のスキーマ（`BusinessDataSampleSchema`）に、PostgreSQL の 22P02 を起こすための `tag`（PostgreSQL は `uuid`、MySQL は `varchar`）を足した | Step 17 の「PG の 22P02 を起こす OTHER の値」の確かめに要るため |
| G25 | 結合テストのアプリは `@SpringBootTest` ではなく、既存の `AbstractTargetSchemaReaderIT`・`DslTargetDbIT` と同じく `SpringApplicationBuilder` で起動する（`businessdata/testsupport/BusinessDataTestApp`） | クラスごとに対象DB のコンテナを起動してから、その接続先を渡してアプリを起動するため |

### 2.3 確かめの結果の要点

- つないだ後に止まる相手の 503 TIMEOUT は、3種類とも約 10 秒だった（試しの MySQL 20 秒・PG 15 秒・MariaDB 10 秒より短い。25 秒の線の中）。止めを解いた後の次の要求は、どれも 5 秒以内に 200 になった。
- 本物の遅い問い合わせは、件数とページのそれぞれで約 5 秒の 503 TIMEOUT になった。
- 接続の数（PostgreSQL・MySQL）: 定義は内部DB の同時の最大 1・対象DB 0、一覧と詳細は内部DB の同時の最大 1・対象DB を借りた回数 1。対象DB を借りている間に内部DB を借りていない。

### 2.4 流したテスト（この依頼の範囲）

colima と README の2つの変数を渡して流した。結合テストは `caffeinate -dims` で包み、流した後に `pmset -g log` で Sleep・DarkWake が無いことを確かめた。

| Step | コマンド | 結果 |
|---|---|---|
| 14（単体） | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.web.*' --tests 'cherry.mastersmith.ApiAccessArchitectureTest'` | 22 件 通過 |
| 16 | 境界テストの5つのクラス | 26 件 通過 |
| 16 | `./gradlew :backend:spotbugsMain :backend:spotbugsGate` | 通過（`SQL_` の残り 0 件） |
| 20 | `./gradlew :backend:test --tests 'cherry.mastersmith.businessdata.*PropertyTest'` | 7 件 通過 |
| 確かめ | `./gradlew :backend:test`（バックエンドの単体テストの全体） | 2,358 件 通過（失敗 0） |
| 14・15・17〜19 と既存 | `caffeinate -dims ./gradlew :backend:cleanIntegrationTest :backend:integrationTest --tests 'cherry.mastersmith.businessdata.*' --tests 'cherry.mastersmith.targetdb.*' --tests 'cherry.mastersmith.ApiAccessConsistencyIT' --tests 'cherry.mastersmith.common.error.service.ProblemTypeDuplicateStartupIT'` | 138 件（通過 134・G22 の飛ばし 4・失敗 0）。約 4 分 |

カバレッジと `verify` の時間は Step 24 で測る。

## 3. 3回目の依頼（Step 21〜26）

### 3.1 計画に無い変更・計画と違う判断

| # | 計画・設計の書き方 | 生成で行ったこと | 理由 |
|---|---|---|---|
| G26 | Step 21「100 カラムの表 `biz_item`（主キー1つ）・複合の主キーの表・少しの行の表」 | 3つ目の「少しの行の表」を `biz_wide`（主キー `id` と 100 字を超える文字の `w_001`〜`w_099`、200 行）にし、`bizSearchWide`（100 件 × 100 カラム × 100 字。NFR2.5）の表に兼ねた。複合の主キーの表は `biz_pair`（1,000 行） | `biz_item` は型を回すため、100 カラムすべてが 100 字を超える1ページを作れない。表を増やさずに NFR2.5 の悪い側の応答を作るため |
| G27 | 場面の判定は `iteration_duration{scenario:…}` の p95 < 1000 と `checks` の率 1（NFR1.2） | `bizSearchWide` だけは `iteration_duration` の閾値を `max>=0`（記録だけ）にし、`checks` の率 1 は残した | NFR2.5 と `performance-requirements.md` の「応答の大きさの悪い側はメモリの確かめの場面で、応答時間の判定の場面には数えない」のとおり。判定は `memory.peak`（Performance Validation） |
| G28 | Step 22「変数を渡さないときの `command` と `mem_limit` が今の値と同じ」 | `mem_limit` は3つとも同じ値（512m・768m・512m）だった。`command` は今まで書いていなかった（イメージの既定）ため、`config` の出力は「無し」から `postgres -c shared_buffers=128MB`・`mysqld --innodb-buffer-pool-size=128M`・`mariadbd --innodb-buffer-pool-size=128M` に変わる。イメージの既定の CMD（`postgres`・`mysqld`・`mariadbd`）と、3つの DB の既定の値（128MB・128M・128M）と同じであることを `docker image inspect` で確かめ、PG は試し走りで `SHOW shared_buffers` が 128MB であることを確かめた（MySQL・MariaDB は D4: A のとおり起動していない） | 設定の口を足すには `command` を書くほかない。実際に効く値は変わらない（基盤の R-04 の確かめを「実効の値が同じ」と読んだ） |
| G29 | Step 22「使い捨ての環境は、確かめの結果（WARN の件数、…）を見てから片付ける」 | 1回目の試し走りは、`bizSearchWide` の前にアプリのコンテナを作り直したため、作り直す前のコンテナのログを残しておらず数えられなかった。README の台本を「作り直す前にログを `$D` に残す」形に直し、試し走りをもう一度流して、作り直す前と後のログの両方を数えた（利用者 0・行の値 0・WARN は起動ごとの2行だけ・ERROR 0） | project.md の「確かめの結果を見てから片付ける」の漏れを、同じ段の中で埋めた |
| G30 | `make-biz-dsl.mjs` は「種の表に合わせた DSL を作る」 | 型の名前は既定の DSL の生成が写す名前（PG は `int4`・`int8`・`numeric`・`timestamp`・`bool`、MySQL・MariaDB は `int`・`bigint`・`decimal`・`datetime`・`tinyint`（フォーム部品 checkbox。G1 と同じ BOOLEAN の読み）・`longtext`）にした。検索は `id` の EQUALS と `c_010` の CONTAINS、並べ替えは `id`（既定 ASC）と `c_001`。`biz_pair` は全カラム、`biz_wide` は 100 カラムすべてを一覧に出す | 種の台本（`perf/biz/make-biz-seed.sh`）と1対1にそろえる。片方を変えるときは両方を変えることを両方の先頭に書いた |
| G31 | 種の値の決め方（performance-design.md 4節。「行の数と値は B2 の台本で決める」） | 乱数を使わず、行の番号 n とカラムの番号 i だけから決める式にした。TEXT の5本は 150〜270 字に行の番号を付けた値、n が 100 の倍数の行は目印の語「目印」を含む（`bizSearchContains` の当たり。試し走りで total を setup で数えて毎回一致を確かめる）、`c_010` の `n % 10000 = 5` の 10 行は 2,000,000 字、`(n + i) % 11 = 0` は NULL。並べ替えのカラムは `c_001`（値 `MOD(n × 7919 + 1, 100000)`。0〜99,999 を1回ずつ取り、主キーの順と違う並び）。MySQL・MariaDB のデータベースの照合順序は `utf8mb4_bin` にそろえた | team.md の「決めた種から作る」。照合順序を2つの DB で同じにし、部分一致の当たりの件数が DB の既定の照合順序で変わらないようにした |
| G32 | 試験用の利用者 | `bizPrepare` が使う試験用の利用者を `perf-biz-01`〜`10`、操作する管理者を既存の場面と同じ `perf-roleop01`、ロールを `perf-biz-reader`（スキーマ `bizperf` に READ を明示）にした。前の回が残したロール・割り当て（409 `ROLE_NO_CHANGE`）を受け入れ、何度流しても同じ状態にする | project.md の「k6 の場面の setup は前の場面が残した状態に頼らない」 |
| G33 | 基盤の設計 2.3 の VM の表（R-05） | 承認済みの文書は書き換えない。行 3（perf のアプリ ＋ perf の MySQL ＋ lgtm ＋ 見本の PG・Mailpit）は、MySQL が 1792m のときは 6,144 MiB で、文書の 6,656 MiB は 2304m のときの値。どちらでも 5,910 MiB に収まらない判定は変わらない。MySQL の回は lgtm を動かさないことを README に書いた | 計画の 4.2 の R-05 の決定 |
| G34 | Step 23 の README | 「対象DB」の節の冒頭の「今は、メタデータだけ」の文と、待ちの上限の文（生成・照合だけ）も、一覧・詳細を足した形に直した。新しい小見出し「業務データの読み取りの API」に、計画の段落の中身（3本の道・ログインだけ・権限はテーブルごと・読み取りの接続だけ・時間切れと 503）に加えて、ページの件数・切り詰めの長さ・code の一覧を書いた | 足す段落と食い違う古い文を残さないため |

### 3.2 確かめの結果の要点

- `k6 inspect --include-system-env-vars`: 8つの場面のどれも、場面の名前（1つ）・executor（`bizPrepare` は per-vu-iterations、ほかは constant-vus 3 分）・閾値の名前（`iteration_duration{scenario:…}`・`checks{scenario:…}`・`http_req_duration{name:…}`）・`setupTimeout` 10 分が出た。既存の場面（`health`・`navMenu`・`roleTreeRead`・`dslLight`）も変わらず読めた。
- `docker compose config`（ダミーの環境ファイルと3つの profile）: G28 のとおり。業務データの試験の値を export すると `shared_buffers=256MB`・768 MiB、`--innodb-buffer-pool-size=768M`・2,304 MiB、`512M`・1,024 MiB になった。
- 試し走り（PG、既定の設定、配備したアプリを止めない、各場面 1 VU・10 秒。判定ではない）: 2回とも全場面が通った（checks の失敗 0、`http_req_failed` 0）。数値は `perf/README.md` の業務データの節の「試し走りの結果」。種の SQL は 7 秒、`biz_item` は 196 MiB（索引 2.2 MiB）で試しの表（198MB）に近い（計画の Assumptions）。
- MySQL・MariaDB の種の SQL は、出すだけにした（D4: A）。2つの出力は同じ（34,621 バイト・15 文）で、行の数の指定は 100,000・1,000・200、`c_010` は `LONGTEXT`、真偽値は `TINYINT(1)`。

### 3.3 流したテスト（Step 24・25）

どれも colima と README の2つの変数を渡し、`caffeinate -dims` で包んだ。流す前に `pmset -g assertions` で眠らない状態（`PreventUserIdleSystemSleep`・`PreventUserIdleDisplaySleep` が 1）を、流した後に `pmset -g log` で窓の中に Sleep・DarkWake が無いこと（最後の Sleep は前日の 21:57）を確かめた。

| Step | コマンド | 結果 |
|---|---|---|
| 24 | `./gradlew :backend:cleanTest :backend:cleanIntegrationTest verify`（2026-10-19 09:19〜09:37） | 通過。18 分 32 秒（Gradle の表示 18m 31s。直近の 14 分 1 秒から 4 分 31 秒延びた。見込みの約 45 秒より長いが、CI の上限の7割の約 42 分の線の内） |
| 24（件数） | 同上 | バックエンドの単体 2,358 件（失敗 0・飛ばし 0）、結合 1,253 件（失敗 0・飛ばし 4。飛ばしはどれも G22 の `assumeTrue` で、対象DB のテストのコンテナの飛ばしではない）、フロントエンド 184 ファイル・1,589 件 |
| 24（カバレッジ。バックエンドは JaCoCo、行・分岐） | 同上 | 全体 行 97.8%（12,008/12,278）・分岐 91.8%（4,828/5,259）。`businessdata.domain` 97.7%・90.7%、`businessdata.repository` 94.9%・86.0%、`businessdata.service` 98.6%・93.6%、`businessdata.web` 97.0%・74.3%、`targetdb.config` 100.0%・100.0%、`targetdb.domain` 100.0%・97.1%、`targetdb.repository` 96.0%・87.5%、`targetdb.service` 90.5%・92.5%、`role.domain` 94.2%・85.7%。どれも下限（行 80%・分岐 70%）を満たす。フロントエンドの全体（Stmts・Branch・Funcs・Lines）は 95.19%・90.38%・96.73%・95.36% |
| 25 | `./gradlew e2eTest`（Mailpit は配備の側で動いていたものを使うだけ。09:42〜09:53） | 232 件 通過（10.8 分）。失敗・flaky 0 |
