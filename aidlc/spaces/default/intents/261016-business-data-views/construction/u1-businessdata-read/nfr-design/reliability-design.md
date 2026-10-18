# 信頼性の設計 — U1 u1-businessdata-read

## 1. 捨ての試しの結果（要点。Q1: A・F2: A）

### 1.1 試しの形

**置き場**
- リポジトリの外の作業用の一時の場所に、試しのコード（Java の1ファイル）と、対象DB を立てる台本を置いた。
- 本物の作業フォルダでは、git・gradlew・npm を使っていない。

**版**
- Temurin 25.0.4
- HikariCP 7.0.2・postgresql 42.7.13・mysql-connector-j 9.7.0・mariadb-java-client 3.5.10・jackson 3.1.7（`backend/gradle.lockfile` と同じ）
- 対象DB は postgres 18.6・mysql 26.7.0・mariadb 13.0.2。perf/compose と同じダイジェスト、同じメモリの上限（512m・768m・512m）。

**設定**
- アプリと同じ: readOnly・minIdle 0・借りる待ち 3 秒・確かめの待ち 3 秒・connectTimeout 5 秒・socketTimeout 25 秒・`setQueryTimeout(5)`
- JVM は `-Duser.timezone=Asia/Tokyo`
- サーバーのタイムゾーンは America/New_York（夏時間の重なりを作るため）

**進め方**
- 対象DB は1種類ずつ `docker run` で立て、終えたら消した。
- 配備したアプリ・見本の対象DB・Mailpit には触れていない。
- 値はすべて架空。
- かかった時間は約 30 分。追加の F2 の試しが約 5 分で、どちらも上限 3 時間の中に収まった。
- 流している間に、PC のスリープは無かった。

### 1.2 結果の表

| ID | 確かめたこと | 結果 | 設計への意味 |
|---|---|---|---|
| T1 | 時差 | PG は JDBC の型（`OffsetDateTime`）で読み書きとも正しい。`timetz` の `=` は時刻と時差の両方を比べるため、同じ時点でも 0 行になる。`(列 AT TIME ZONE 'UTC') = ?` なら当たる。MySQL は、JDBC の型ではずれ、SQL の式では夏時間の重なりで2行に当たる。`SET time_zone='+00:00'` なら正しい。MariaDB は、JDBC の型ではずれ、SQL の式と `SET` は正しい | 3節。MySQL・MariaDB は `SET`（Q2: A の2つ目の形） |
| T2 | OTHER の EQUALS・主キー | PG は `Types.OTHER` で `uuid`・`jsonb`・列挙・`inet`・`cidr`・`macaddr`・配列・`interval`・`money` が通る。`json`・`xml`・`point` は 42883。MySQL は `enum`・`set`・`year` が通る。`json` は誤りにならずに 0 行になり、`bit`・`point` は 0 行で、読みも画面に出せない。MariaDB は `uuid`・`inet6`・`inet4`・`json`・`enum`・`set`・`year` が通る。`bit` は 0 行 | 4節。BR7.9 を型ごとに当てる。`bit` と空間の型は BINARY と同じ扱い（F3: A） |
| T3 | SQLState と例外の文 | 無い表は 42P01・42S02/1146。無いカラムは 42703・42S22/1054。型の誤りは、PG が 22P02・22007・22003・22008・42883、MySQL が HY000/1525。MySQL・MariaDB は、比べるだけでは誤りにならないことが多い（警告 1292・0 行）。**PG の 22P02・22007・22003 の文には入力の値が入る**。どの文にも接続先は入らない | 2節の表。security-design.md の 3節 |
| T7 | 応答しない対象DB | 受け付けて何も返さない相手に、プールが空のまま借りる: 3.0 秒・原因なし（3種類）。つないだ後に止まる（25 秒の形）: PG 25.0 秒・MariaDB 25.0 秒・**MySQL 50.0 秒**。止まった後に確かめが走る借りる: PG・MariaDB 3.0 秒、MySQL 6.0 秒（原因あり。MariaDB は 42000/-1）。本物の遅い問い合わせの打ち切り: 5.0 秒（PG は 57014、MySQL は `MySQLTimeoutException` で SQLState なし、MariaDB は `SQLTimeoutException` 70100/1969） | 2節・5節 |
| F2 | 貸すたびに `setNetworkTimeout(10 秒)` を付け、返すときに戻す | つないだ後に止まると、**MySQL 20.0 秒・PG 15.0 秒・MariaDB 10.0 秒**で失敗した（08S01・08006・08000。原因は `SocketTimeoutException`。文に接続先は入らない）。失敗した接続は閉じていて、戻す処理は `SQLException` になる。プールは接続を捨てた（数 0）。相手が戻った後に借り直すと、すぐ成功した（新しい接続、通信の待ちは既定の 25 秒）。止まっていない普段の要求 100 回の上乗せは、1回あたり 1ms 未満 | 5節。3種類とも 25 秒以内に収まるため、F2 は A のまま採る |
| T8 | プールの借りる待ちの時間切れ | 借りきったとき: 3.0 秒・原因なし。拒まれる番号: 3.0 秒・原因あり（08001・08S01・08000） | 2節。Q3: A のとおり、すべて UNAVAILABLE |
| T9 | NULL の並び・`ESCAPE '!'` | 3種類でそろった | performance-design.md の 1節 |

T4・T5・T6 は、performance-design.md と scalability-design.md の冒頭に書いた。

## 対応表

| U1 の NFR 要件の ID | この文書の節 | 扱い |
|---|---|---|
| NFR3.1 3種類の DB で同じ振る舞い | 1節・3節・4節 | 型ごとの差を設計に入れた |
| NFR3.3 時差つきの型 | 3節 | 作りは決めた |
| NFR4.4 時間の上限（値） | 5節 | security-design.md と共有。貸すときの通信の待ちを足した（F2: A） |
| NFR4.8 失敗の返し方と例外の通り道 | 2節・5節 | 3種類とも 25 秒以内に 503 |

## 2. 失敗の分け方と例外の通り道

例外の通り道は、JDBC を呼んだメソッドの中で受けて結果の型に変え、メッセージと原因を捨てる形にする（決まっていること）。

| どこで | 何を受けるか | 何に変えるか |
|---|---|---|
| `JdbcTargetReadAccess.withRead`（targetdb.service） | `getConnection` の失敗（すべての `SQLException`） | `TargetReadOutcome.Unavailable`。持つのは SQLState・業者の番号・例外のクラスの名前だけ。**原因の有無と SQLState では分けない**（Q3: A。T7・T8 で、借りる失敗の SQLState がばらばらだったため。MariaDB の確かめの失敗は 42000 だった） |
| 同上 | 設定が無い | `NotConfigured` |
| 同上 | 通信の待ちを付ける・戻す処理の失敗 | 付ける処理の失敗は `Unavailable`。戻す処理の失敗（F2 の試しで、壊れて閉じた接続で起きた）は、例外のクラスの名前だけを WARN で出し、接続は閉じる（プールが捨てる） |
| `RowSqlExecutor` の各メソッド（businessdata.repository） | 文の `SQLException` | `RowQueryFailure`（理由・SQLState・業者の番号・例外のクラスの名前）を結果として返す |

`RowSqlFailureClassifier`（repository の層の純粋な部品）は、例外のメッセージを読まずに、次の順で理由を決める。

1. **時間切れ（TIMEOUT）**: 次のどれかに当たるとき。
   - 例外か原因の連なりに、`SQLTimeoutException`（MySQL の `MySQLTimeoutException` を含む）か `SocketTimeoutException` がある。
   - SQLState が 57014。
2. **接続の失敗（UNAVAILABLE）**: SQLState の類が 08。
3. **定義との食い違い（MISMATCH）**: SQLState が 42P01・42703・42S02/1146・42S22/1054。
4. **値の型の誤り（400）**:
   - PG: 22P02・22003・22007・22008・42883
   - MySQL・MariaDB: 22001・22003・22007・22018、HY000 のうち業者の番号が 1366 と **1525**（1525 は T3 で足す）
5. **想定外**: そのほか。業務処理の層が、メッセージも原因も持たない例外にして投げ、500 にする。

文と接続の後始末:

- 文が時間切れになった後は、その接続を処理の中で使い続けない。続く問い合わせは読まずに、失敗を返す。
- 貸した接続は、口が `finally` で閉じる。閉じる処理の失敗は、例外のクラスの名前だけを WARN で出す（決まりの「捕捉して何もしない」を避けるため）。

## 3. 時差の作り（Q2: A の順の結果）

| DB | 型 | 読み | バインド（検索の値・主キー） |
|---|---|---|---|
| PG | `timestamptz` | `getObject(OffsetDateTime)` を UTC にして `+00:00` | UTC の `OffsetDateTime` を `setObject` |
| PG | `timetz` | `getObject(OffsetTime)` を `withOffsetSameInstant(UTC)` | 条件を `(列 AT TIME ZONE 'UTC') = ?` にし、UTC の `OffsetTime` を `setObject` |
| MySQL・MariaDB | `timestamp` | 貸した後、最初の文の前に `SET time_zone = '+00:00'` を1回流す（RowSqlExecutor が流す）。その後は `LocalDateTime` で読み、UTC として `+00:00` を付ける | UTC の `LocalDateTime` を `setObject` |
| 3種類 | 時差の無い型（`DATE`・`DATETIME`・`TIME`・PG の `timestamp`） | DB の値のまま（セッションのタイムゾーンの影響を受けない） | そのまま |

- MySQL・MariaDB の `SET time_zone` は、プールの接続のセッションの状態として残る。
  - 同じプールのスキーマの読み取り（DSL の生成・照合）は時刻の値を読まないため、影響は無い。
  - 接続の設定（URL・`connectionInitSql`）は変えない（X8 の決まりを守る）。
- MariaDB だけなら、SQL の式でも通る。MySQL と1つの形にそろえるため、`SET` を使う。
- 読み取りだけにする切り替え（ADR-007）は使わない。

## 4. OTHER と BINARY に寄せる型の作り（T2 の結果。BR7.1・BR7.7・BR7.9、F3: A）

| DB | EQUALS と主キーで行を指せる型 | BR7.9 を当てる型（EQUALS から外し、主キーに含むテーブルの DETAIL を許さない） | BINARY と同じ扱いにする型（F3: A） |
|---|---|---|---|
| PG | `=` を持つ型（`setObject(…, Types.OTHER)`） | `json`・`xml`・幾何の型（`point`・`line`・`lseg`・`box`・`path`・`polygon`・`circle`） | 無し |
| MySQL | `enum`・`set`・`year`（`setString`） | `json`（誤りにならずに 0 行になるため） | `bit`（長さ 2 以上）・空間の型（`geometry`・`point`・`linestring`・`polygon`・`multipoint`・`multilinestring`・`multipolygon`・`geometrycollection`） |
| MariaDB | `uuid`・`inet4`・`inet6`・`json`・`enum`・`set`・`year`（`setString`） | 無し | `bit`（長さ 2 以上）・空間の型（MySQL と同じ名前） |

**BINARY と同じ扱い（F3: A）**
- 選ばない（値を読まない）。
- 検索と並べ替えの対象にしない。
- 定義の `valueType` は `BINARY` で返す。画面は読めない印を出す。
- 主キーにこれらの型を含むテーブルでは、BINARY の主キーと同じく DETAIL を許さない。
- 分類は、`ColumnValueCodec` の型の対応（BR7.1 の1か所）で、DB の種類と DSL の `dbType` の名前（大文字と小文字を区別しない）から決める。`bit(1)` は BR7.1 のとおり BOOLEAN のまま。

**BR7.9 の型**
- 一覧は、RowDialect に DB の種類ごとに持たせる。
- 一覧に無い型で 42883 などが返ったときは、2節の4に当たり、400 になる（BR8.6）。

## 5. 時間切れと最悪の時間（R-01 の読み替え・F2: A）

**貸すときの通信の待ち（F2: A）**
- `JdbcTargetReadAccess` は、借りた直後に `Connection.setNetworkTimeout(executor, 目的の上限 + 5 秒)` を付ける。一覧・詳細はどちらも 10 秒。
- 返す前に、元の値（ドライバーの通信の待ち 25 秒）へ `finally` の中で戻す。
- executor は、口が持つ1つのスレッドプールにする。
- DSL の生成・照合（`JdbcTargetSchemaReader`）は、この口を通らない。生成の 20 秒の上限と、ドライバーの通信の待ち 25 秒は変わらない。

| 場合 | 最悪の時間（実測） | 返す code |
|---|---|---|
| 設定が無い | すぐ | 503 `BUSINESS_TARGET_DB_UNAVAILABLE` |
| 接続できない・借りきった・プールが空で相手が答えない・確かめが失敗する | 3.0 秒（MySQL の確かめは 6.0 秒） | 503 `BUSINESS_TARGET_DB_UNAVAILABLE`（Q3: A） |
| 本物の遅い問い合わせ | 問い合わせごとに 5.0 秒 | 503 `BUSINESS_TARGET_DB_TIMEOUT` |
| つないだ後に相手が止まる（F2 の形） | MySQL 20.0 秒・PG 15.0 秒・MariaDB 10.0 秒。件数が通った後のページで止まると「件数の時間 + これ」 | 503 `BUSINESS_TARGET_DB_TIMEOUT` |

- **3種類とも、R-01 の読み替え（25 秒以内に 503）を満たす**。MySQL の最悪は、件数の時間（試しで最大約 0.45 秒）を足しても約 20.5 秒。
- 失敗までの時間は「通信の待ち 10 秒」ちょうどではなかった。MySQL は2回分の 20 秒、PG は約 15 秒かかった。MySQL は、取り消しの接続と元の接続がそれぞれ待つと見立てている（未検証）。
- 失敗した接続は、プールが捨てる。相手が戻れば、新しい接続ですぐに読める（F2 の試し）。
- 再試行と遮断器は置かない（決まっていること）。1台で動き、借りる待ち 3 秒が、詰まったときの上限になる。
- 対象DB の失敗は、`/actuator/health` に含めない。

## 6. 上流との差

- **NFR4.8 の約 13 秒**: NFR4.8 の「応答しない対象DB では、およそ 13 秒以内」は、承認の場の決定（R-01）で「25 秒以内に 503」に読み替えた。F2: A の作りで、3種類とも 10〜20 秒で失敗することを実測した。
- **貸すときの通信の待ち**: NFR4.4（時間の上限の値）と BR8.2（ドライバーの通信の待ちの計算）に、貸すときの `setNetworkTimeout`（目的の上限 + 5 秒）を足した（F2: A）。
- **BR8.6 の表**: MySQL の HY000/1525 を足す。コード生成の計画で表を確定するときに入れる。
- **BR7.9 の細かさ**: BR7.9 は「DB ごと」の切り替えだったが、T2 の結果で「DB と型ごと」に細かくした（4節）。切り替えの向き（狭める）は BR7.9 のとおり。AC1.4.8 との差（X9）は、表の型についてだけ残る。
- **BR7.1 の分類との差（F3: A）**: 機能設計の BR7.1 は「表に無い型は OTHER」としている。この設計では、MySQL・MariaDB の `bit`（長さ 2 以上）と空間の型を BINARY と同じ扱いにする。BR7.4〜BR7.5 の BINARY の決まり（読まない・入力にしない）と、BR5.2 の並べ替えの除外が、これらの型にも当たる。AC1.4.8（型の分類の外の型でも主キーで行を指せる）との差は、主キーにこれらの型を持つテーブルについて残る。

## 7. 後の段に回すこと

- **コード生成の計画（B2）**:
  - `RowSqlFailureClassifier` の表を確定する（既存の `SchemaQueries` の分け方と合わせる）。
  - BR7.9 の型の一覧と、BINARY と同じ扱いにする型の一覧（`ColumnValueCodec`）を作る。
  - `setNetworkTimeout` の executor の持ち方と、戻す処理の失敗のログを作る。
  - 応答しない対象DB の結合テストを置く。
    - ServerSocket で、借りる失敗を確かめる。
    - 試しで使った「つないだ後に止まる中継」を `businessdata/testsupport` に置き、10〜20 秒で失敗することを確かめる。
    - 中継のテストは1回で 10〜20 秒かかるため、置く DB の種類（1種類か3種類か）は計画で決める。
- **Performance Validation**: 借りる待ちの時間切れの累計を読む（performance-design.md の 4節）。

## 8. U2・U3 とそろえる点

- **U2 の書き込みの口**: 読み取りと同じにそろえる。
  - 貸す形・`setNetworkTimeout`（書き込みの目的の上限 + 5 秒）・`RowSqlFailureClassifier`
  - 借りる失敗は、すべて UNAVAILABLE
  - U2 は、一意・外部キーの違反の SQLState を表に足す。
- **U2 の最悪の時間（U2 R-02）**: 書き込みでは、止まったときの最悪の時間が、確定・巻き戻しの待ちに乗る。MySQL が通信の待ちの2回分になることを、計算に入れる。
- **U2 の時差つきの型の入力**: 3節と同じバインドの形にする（MySQL・MariaDB は `SET time_zone`）。BINARY と同じ扱いの型は、入力にしない。
- **U3 の画面**:
  - 503 の2つの code を出し分ける。
  - 4節の型（EQUALS に出ない型）は、定義の API の検索の項目に従う。
  - BINARY と同じ扱いの型は、`valueType: BINARY` の読めない印で出す。

## Sources

- 承認済みの NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/nfr-requirements/reliability-requirements.md`（NFR3.1・NFR3.3・NFR4.8）・`security-requirements.md`（NFR4.4）
- 承認の場の決定: `construction/nfr-requirements/memory.md` の Open questions（U1 R-01・R-02）
- この段の答え
  - `construction/u1-businessdata-read/nfr-design/nfr-design-questions.md`（Q1〜Q3: A）
  - `construction/u1-businessdata-read/nfr-design/nfr-design-followup-questions.md`（F2・F3: A）
- 機能設計: `construction/u1-businessdata-read/functional-design/rules.md`（BR5.2・BR7.1・BR7.3〜BR7.9・BR8.1〜BR8.6）・`functional-spec.md`（X8・X9）
- コード
  - `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java`（通信の待ち = 最も長い上限 + 5 秒）
  - `backend/src/main/java/cherry/mastersmith/targetdb/service/JdbcTargetSchemaReader.java`（今の分け方）
- 捨ての試し: リポジトリの外の試しのコードと結果（T1〜T3・T7〜T9・F2）

## Assumptions & Open Questions

- **依頼者の決定（F2: A）**: 試しで、MySQL では、つないだ後に相手が止まると 50 秒かかると分かった（25 秒の形）。
  - 貸すたびに `setNetworkTimeout` を目的の上限 + 5 秒（一覧・詳細は 10 秒）で付け、返すときに戻す。
  - 追加の試しで、3種類とも 25 秒以内（MySQL 20.0 秒・PG 15.0 秒・MariaDB 10.0 秒）に失敗した。そのため、B（MySQL は 50 秒を既知の制約）には切り替えない。
- **依頼者の決定（F3: A）**: MySQL・MariaDB の `bit`（長さ 2 以上）と空間の型は、BINARY と同じ扱いにする。読まない、検索と並べ替えの対象にしない、画面では読めない印にする。BR7.1 との差は 6節に書いた。
- 前提: 中継で作った「つないだ後に止まる」は、本物のネットワークの断や DB の停止と同じ待ち方をするとみなした。
- 前提: MySQL の 20 秒（通信の待ちの2回分）の内訳は見立てで、確かめていない。
- 前提: 壊れていない接続を返すときに、HikariCP が通信の待ちを元に戻すかは確かめていない。そのため、口が自分で `finally` の中で戻す。
