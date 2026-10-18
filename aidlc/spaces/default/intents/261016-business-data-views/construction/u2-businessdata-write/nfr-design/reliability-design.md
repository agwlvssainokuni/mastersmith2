# 信頼性の設計 — U2 u2-businessdata-write

## 1. 捨ての試しの結果（要点。Q1: A）

### 1.1 試しの形

**置き場と進め方**
- リポジトリの外の作業用の一時の場所に、試しのコード（Java の1ファイルと、正規表現の小さな確かめ1つ）と、対象DB を立てる台本を置いた。U1 の試しの部品（プール・中継・例外の連なりの出し方）を写して使った。
- 本物の作業フォルダでは、git・gradlew・npm を使っていない。
- 2026-10-18 22:11〜22:46 に行った（約 35 分。上限 3 時間の中）。全体を `caffeinate -dims` で包み、`pmset -g log` で Sleep・DarkWake が 0 件であることを確かめた。
- 対象DB は1種類ずつ `docker run` で立て、結果を見てから `docker rm -f -v` で消した。配備したアプリ・見本の PostgreSQL・Mailpit・既存のボリューム・`.env` には触れていない。値はすべて架空。

**版**
- Temurin 25.0.4（`-Duser.timezone=Asia/Tokyo`。W6 は UTC でも流した）
- HikariCP 7.0.2・postgresql 42.7.13・mysql-connector-j 9.7.0・mariadb-java-client 3.5.10・jackson 3.1.7・spring-jdbc・spring-tx・spring-core・spring-beans 7.0.9・h2 2.4.240（`backend/gradle.lockfile` と同じ）
- 対象DB は postgres 18.6・mysql 26.7.0・mariadb 13.0.2（perf/compose と同じダイジェスト、同じメモリの上限 512m・768m・512m）。サーバーのタイムゾーンは America/New_York

**書き込みのプールの設定（Q3: A の形）**
- 読み取り専用にしない・minIdle 0・借りる待ち 3 秒・確かめの待ち 3 秒・connectTimeout 5 秒
- **socketTimeout 10 秒をプールの設定で付ける**（貸すたびには変えない）
- MySQL・MariaDB は `connectionInitSql` を次の形にした。

  ```sql
  SET time_zone = '+00:00', sql_mode = CONCAT_WS(',', NULLIF(@@sql_mode, ''), 'STRICT_ALL_TABLES')
  ```

- トランザクションは Spring の `DataSourceTransactionManager` と `TransactionTemplate`。文は `setQueryTimeout(5)`
- 書き込みのアカウントには、表の SELECT・INSERT・UPDATE・DELETE だけを与えた

### 1.2 結果の表

| ID | 確かめたこと | 結果 | 設計への意味 |
|---|---|---|---|
| W1 | 一意の事前の問い合わせを1文に | 3種類とも同じ形の SQL で通る。100,000 行で、索引のあるカラムは 0.4〜0.6 ms、索引の無いカラムは 4〜9 ms。自分の行を除く形（単一・複合の主キー）も正しい。MySQL・MariaDB は大文字と小文字を区別しない | performance-design.md の 1節 |
| W2 | 違反の後の確かめ | **セーブポイント + READ COMMITTED で、3種類とも違反のカラムが分かる。同時の重なり（相手が同じ値を入れて確定・参照先を消して確定）でも分かる。** REPEATABLE READ では重なったときに分からない（PG・MySQL は見えない。PG の外部キーは 40001。MariaDB は 1020 で全体が巻き戻り、セーブポイントの戻しが 1305）。PG はセーブポイントを使わないと 25P02 | 3節（Q2: A） |
| W3 | SQLState・業者の番号 | 2節の表。秒の端数は3種類とも黙って変わる（PG・MySQL は丸め、MariaDB は切り捨て）。DECIMAL の小数の桁あふれも黙って丸める。MySQL は 1264・1292 の SQLState が 22001。両方のドライバーが、接続のときにセッションへ `STRICT_TRANS_TABLES` を足す | 2節（Q6: A・F3: A） |
| W4 | 件数 | 既定は「一致した行」。同じ値の更新は 1。`useAffectedRows=true` にすると 0。2行に当たると 2 | 2節の補足 |
| W6 | 時差の書き込み | PG（`OffsetDateTime`・`OffsetTime` を UTC で）と、MySQL・MariaDB（`connectionInitSql` の `SET time_zone` + UTC の `LocalDateTime`）で、オフセットの無い値・`Z`・`+09:00`・夏時間の重なりの時点がすべて正しい。JVM が UTC でも同じ。**`connectionInitSql` が無いと 4〜5 時間ずれる** | 6節（Q3: A） |
| W7 | 止まる場所ごとの時間 | 5節の表。確定の途中で止めると、応答は失敗なのに行は保存されていた | 4節・5節（Q4: A・Q5: A） |
| W11 | H2 の例外の文 | 長さを超えると、例外の文に `detail` の値が入る | security-design.md の 2節 |
| W12 | 接続を捨てる | 開いているトランザクションの中で捨てると、PG・MySQL では管理者の巻き戻しが 08003 で失敗した。管理者が返した後に捨てる形は、3種類とも通った。確定も巻き戻しもせずに返すと、Hikari が巻き戻した | 7節（U1 R-04 を先に避ける） |

W5・W8 は security-design.md に、W9 は performance-design.md に、W10 は scalability-design.md に書いた。

## 対応表

| U2 の NFR 要件の ID | この文書の節 | 扱い |
|---|---|---|
| NFR3.5 3種類の DB で同じ振る舞い | 2節・3節 | 番号の表・件数・セーブポイントを決めた |
| NFR3.6 時差つきの型の入力 | 6節 | 作りを決めた |
| NFR4.12 失敗の分け方と例外の通り道 | 2節・5節・7節 | 段ごとの分け方と、止まったときの時間 |
| NFR4.15 時間切れの監査の読み方 | 4節 | `AuditResult` に `UNKNOWN` を足す（Q5: A） |
| NFR4.4 書き込みの時間の上限（部分。値は security-design.md） | 5節 | 最悪の時間を算出し直した（Q4: A） |

## 2. 失敗の分け方（NFR4.12・NFR3.5、Q6: A・F3: A）

**段で分ける（U1 R-01 を先に避ける形）**

U1 の NFR 設計のレビュー（`.aidlc-reviews/nfr-design/units/u1-businessdata-read/df13e747868fc8fa/1.json` の R-01）は、値の型の誤りの SQLState を、バインドの誤りか保存済みの値の読み取りの誤りかで分けないと誤ると指摘した。

U2 は最初から、分類の部品（`RowSqlFailureClassifier` に書き込みの表を足す）に「どの段で起きたか」を渡す。段は次のとおり。

- 借りる（`connectionInitSql` を含む）
- 事前の問い合わせ
- 書き込みの文
- 確定
- セーブポイントへの戻し
- 確かめ
- 巻き戻し
- 結果の読み（生成された鍵）

結果の読みでの型の誤りは、400 にも 409 にもせず、想定外の失敗（メッセージの無い例外で 500）にする。

| 段 | 起きたこと | 応答 | 保存 |
|---|---|---|---|
| 借りる | どんな失敗も（SQLState では分けない） | 503 `BUSINESS_TARGET_DB_UNAVAILABLE` | されていない |
| 事前の問い合わせ | 時間切れ・通信の断絶（`SQLTimeoutException`・`SocketTimeoutException` が連なりにある、57014、類 08） | 503 `UNAVAILABLE`（書き込みの文を送る前） | されていない |
| 書き込みの文・確定 | 時間切れ・通信の断絶・確定の失敗 | 503 `BUSINESS_TARGET_DB_TIMEOUT` | 分からない |
| 事前の問い合わせ・書き込みの文 | 42P01・42703・42S02/1146・42S22/1054 | 409 `BUSINESS_TARGET_DB_MISMATCH` | されていない |
| 書き込みの文 | 一意の違反 | 409 `BUSINESS_DUPLICATE_VALUE`（確かめでカラムを決める） | されていない |
| 書き込みの文 | 作成・更新の外部キーの違反 | 409 `BUSINESS_REFERENCE_NOT_FOUND`（確かめでカラムを決める） | されていない |
| 書き込みの文 | 削除の外部キーの違反・NOT NULL・長さ・桁あふれ | 409 `BUSINESS_SAVE_REJECTED` | されていない |
| 書き込みの文 | **足すもの**: CHECK の違反・日時の範囲の外・文字集合・NUL・更新できないビュー | 409 `BUSINESS_SAVE_REJECTED`（カラムを示さない） | されていない |
| セーブポイントへの戻し・確かめ・巻き戻し（違反の後） | どんな失敗も | 409 `BUSINESS_SAVE_REJECTED`（BR6.4） | されていない |
| 巻き戻し（件数が 1 でないなどで巻き戻しを答えた後） | どんな失敗も | 元の答え（404・409）のまま | されていない |
| 結果の読み・そのほか | 想定外 | 500（メッセージも原因も持たない例外） | — |

**番号の表**（書き込みの文の段。MySQL・MariaDB は業者の番号で分ける。MySQL は 1264・1292 の SQLState が 22001 で、1406 と見分けられないため）

| 種類 | PG（SQLState） | MySQL・MariaDB（業者の番号） |
|---|---|---|
| 一意 | 23505 | 1062 |
| 外部キー（作成・更新） | 23503 | 1452 |
| 外部キー（削除） | 23503 | 1451 |
| NOT NULL | 23502 | 1048・1364 |
| 長さ | 22001 | 1406 |
| 桁あふれ | 22003 | 1264 |
| CHECK の違反（足す） | 23514 | 3819（MySQL）・4025（MariaDB） |
| 日時の範囲の外（足す） | 22008 | 1292 |
| 文字集合（足す） | — | 1366 |
| NUL（足す） | 22021 | — |
| 更新できないビュー（足す） | 55000 | 1471 |

**補足**
- PG の削除の外部キーは、文の種類（DELETE）で 1451 と同じ扱いにする。
- 試しの範囲では、MySQL の DATETIME の 999 年と MariaDB の TIMESTAMP の 2040 年は誤りにならなかった。日時の範囲は DB に任せ、拒まれたときだけ上の表で返す（Q6: A）。
- **件数（W4）**: 既定（「一致した行」）で足りる。書き込みの接続では、`useAffectedRows=false` を明示して固定する（BR8.2）。

## 3. 違反の後の確かめ（Q2: A。BR6.2 の切り替え先を先に使う）

- 書き込みのトランザクションは、分離のレベルを **READ COMMITTED** にする（管理者の定義で付ける）。
- 書き込みの文の直前に、`TransactionStatus.createSavepoint()` でセーブポイントを置く。
- 違反したら次の順にする。
  1. `rollbackToSavepoint` で戻す
  2. 同じトランザクションの中で確かめの1文を流す
  3. 全体を巻き戻す（`setRollbackOnly`）
- 試し（W2）で、3種類とも違反のカラムが分かることを確かめた。同時の重なりの場合も含む。
- REPEATABLE READ では重なりのときにカラムが分からない（MariaDB は失敗になる）。このため分離のレベルは必ず付ける。
- 2つ目のトランザクションの形（BR6.2 の第一の形）も試しでは通ったが、管理者を1本の接続に縛る包みが要るため採らない。外から見える振る舞いは同じ。

## 4. 時間切れの監査の読み方（NFR4.15、R-05 は Q5: A）

- `AuditResult` に `UNKNOWN` を足し、503 `BUSINESS_TARGET_DB_TIMEOUT` の監査の行の結果にする。
- 根拠: 試し（W7）で、確定の途中で止めると応答は失敗だったが、行は保存されていた（PG・MySQL・MariaDB とも、送る向きを止めても、返る向きを止めても）。
- 列は `VARCHAR(16)` で CHECK の制約は無いため、移行は要らない。アプリの本体は監査の行を読まないため、前の版へ戻しても動く。
- `AuditEventFactory` と `AuditEventListener` の結果の写しに `UNKNOWN` を足す。既存のテストへの影響（結果の値を並べるテストなど）は、コード生成の計画で洗い出す。
- 巻き戻しの失敗は「保存されていない」と扱い、結果は FAILURE のままにする（W7 の最後の巻き戻しは 0 行）。

## 5. 時間切れと最悪の時間（Q4: A・NFR4.4）

止まったときの実測（W7。中継でつないだ後に流れを止め、文が失敗したら巻き戻しを答えた）:

| 止まった所 | PG | MySQL | MariaDB |
|---|---|---|---|
| 借りるとき（プールが空） | 3.0 秒 | 3.0 秒 | 3.0 秒 |
| 事前の問い合わせ・書き込みの文・確かめ | 15.0 秒 | 20.0 秒 | 10.0 秒 |
| 確定・セーブポイントへの戻し・最後の巻き戻し | 10.0 秒 | 20.0 秒 | 10.0 秒 |

**読み方**
- 1回失敗すると接続は切れ、その後の巻き戻しはすぐに失敗する。
- このため、応答しない対象DB での最悪は「**止まる前に使った時間 + 上の値の1回分**」になる。
- 止まる前に使う時間は、ふつうは数 ms。
- **「およそ 18 秒」（NFR4.4）を、この形に読み替える。** 応答しない対象DB の結合テストの合格の線は、3種類とも **25 秒以内に 503** にする。MySQL の実測 20 秒に余裕 5 秒を足した値で、U1 の線とそろえる。

**机上の上限（すべての段が上限の直前まで遅く、それでも答える場合）**
- 成功の道: 借りる 3 秒（MySQL の確かめが走れば 6 秒）+ 文 5 秒 × 2 + 確定（PG・MariaDB 10 秒、MySQL 20 秒）＝ PG・MariaDB 23 秒、MySQL 36 秒
- 違反の道: さらにセーブポイントへの戻し・確かめ 5 秒・巻き戻しが足され、PG・MariaDB 38 秒、MySQL 61 秒
- どれも段ごとに上限の直前で答える極端な場合で、合格の線には使わない。全体の上限は置かない（Q4: A）。

**そのほか**
- 失敗した接続は、プールが捨てる（3種類とも、失敗の後の数は 0）。相手が戻れば、新しい接続ですぐに書ける。
- 再試行と遮断器は置かない。1台で動き、借りる待ち 3 秒が、詰まったときの上限になる。
- 対象DB の失敗は、`/actuator/health` に含めない。

## 6. 時差の書き込み（NFR3.6、Q3: A）

| DB | 型 | バインド |
|---|---|---|
| PG | `timestamptz` | UTC の `OffsetDateTime` を `setObject` |
| PG | `timetz` | UTC の `OffsetTime` を `setObject` |
| MySQL・MariaDB | `timestamp` | 書き込みのプールの `connectionInitSql` の `SET time_zone = '+00:00'` の上で、UTC の `LocalDateTime` を `setObject` |
| 3種類 | 時差の無い型 | DB の値のまま |

- オフセットの無い値は UTC とみなし、`Z` と `±hh:mm` の値も UTC にそろえてから渡す。
- `connectionInitSql` は接続を作るときに1回だけ流れる。セッションの状態は、書き込みのプールの中だけに残る（読み取りのプールと DSL の生成には響かない）。初期化が失敗すると、借りる失敗（UNAVAILABLE）になる。
- MySQL・MariaDB の `sql_mode` には、同じ初期化の文で `STRICT_ALL_TABLES` を足す（Q6: A）。InnoDB ではドライバーがすでに `STRICT_TRANS_TABLES` を足しているため、効くのはトランザクションを持たない表（MyISAM・Aria）だけになる。
- 秒の端数は3種類とも黙って変わるため、検証で拒む（Q6: A、BR3.5 に足す）。

## 7. 接続の後始末（U1 R-04 を先に避ける形）

U1 の NFR 設計のレビューの R-04 は、戻す処理が失敗した生きた接続が、通信の待ちを変えたままプールへ返ると指摘した。U2 は次の形で避ける。

- **戻す処理を持たない**: 通信の待ちと初期化をプールの設定で付け、貸すたびには変えない（Q3: A）。戻す処理が無いため、戻し損ねた接続が生まれない。
- **確定・巻き戻しが失敗した接続は捨てる**:
  - 口は、管理者が終わった後（`TransactionTemplate` が戻るか、例外を投げた後）に、借りた接続を `HikariDataSource.evictConnection` で捨てる。
  - 開いているトランザクションの中では捨てない（W12 で、PG・MySQL では管理者の巻き戻しが失敗した）。
  - 壊れた接続はプールも捨てるため、これは生きたまま失敗した場合の備えとする。
- **閉じ忘れの網**: 確定も巻き戻しもせずに返しても、Hikari が巻き戻し、次に借りたときの autoCommit は真に戻る（W12）。

## 8. 上流との差

- **BR6.1 の表に足す**: CHECK の違反（23514・3819・4025）・日時の範囲の外（22008・1292）・文字集合（1366）・NUL（22021）・更新できないビュー（55000・1471）を、409 `BUSINESS_SAVE_REJECTED` として足す（Q6: A・F3: A）。分け方に段を足す（U1 R-01 を先に避ける）。
- **BR6.2 の切り替え先を先に使う**: セーブポイント + READ COMMITTED（Q2: A）。外から見える振る舞いは同じ。
- **BR3.5 に日時の秒の端数を足す**: DB の型の精度を超える端数は `TOO_MANY_DIGITS`（Q6: A）。
- **U1 の「貸すたびに付ける」との差（Q3: A）**: U1 の reliability-design.md の 8節は「U2 も貸すたびに `setNetworkTimeout` を付ける」としていた。U2 の書き込みのプールは専用のため、プールの設定（socketTimeout 10 秒・`connectionInitSql`）で付ける。
- **NFR1.6・NFR4.7 の確かめの入力（F4: A）**: `(a+)+$` を `((a+)+)+c` と `(.*a){12}` に読み替える。
- **「およそ 18 秒」の読み替え（Q4: A）**: 5節のとおり。結合テストの線は 25 秒以内。
- **NFR4.15**: 結果の値に `UNKNOWN` を足す形を選んだ（Q5: A）。
- **BR10.1**: 事前の問い合わせの時間切れは、書き込みの文を送る前なので UNAVAILABLE とした（BR10.1 の「送る前」の読み）。
- **BR6.4**: 確かめの失敗に加えて、違反の後のセーブポイントへの戻しと最後の巻き戻しの失敗も、409 `BUSINESS_SAVE_REJECTED` とした。どれも書き込みは拒まれた後で、保存されていないことが確かなため（W7 で 0 行）。

## 9. 後の段に回すこと

- **Infrastructure Design**
  - F1: A の perf の MySQL のメモリの上限
  - F2: A の PG の `REVOKE TEMPORARY` と README
  - 機能設計のレビューの R-06 の表ごとの GRANT
- **コード生成の計画（B4）**
  - SpotBugs の `SQL_` の除外の承認
  - 名前つきの書き込みの管理者の注入の結合テスト
  - NFR 要件のレビューの R-06: 監査の列と `UNKNOWN` を足す移行の後方互換の結合テスト
  - `AuditMigrationCompatibilityIT` などの洗い出し
  - 応答しない対象DB の結合テスト（ServerSocket と、つないだ後に止まる中継。U1 の `businessdata/testsupport` と同じ部品）。置く DB の種類は U1 と合わせて決める。確定の途中で止める場合も足す。
- **Observability Setup**
  - NFR 要件のレビューの R-03 の内部DB の大きさの基準の値
  - `ms-pool-pending` の実測

## 10. U3 とそろえる点

- 503 の出し分け: `BUSINESS_TARGET_DB_TIMEOUT` は「保存されたか分からない」（確かめる入口を出す）、`UNAVAILABLE` は「保存されていない」。
- `BUSINESS_SAVE_REJECTED` はカラムを示さない誤りとして出す（Q6: A・F3: A の分も同じ）。
- 時差つきの型の入力は UTC の値をそのまま扱う。日時は秒まで（Q6: A）。
- 監査の `UNKNOWN` は画面に出ない。

## Sources

- 承認済みの NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/nfr-requirements/reliability-requirements.md`（NFR3.5・NFR3.6・NFR4.12・NFR4.15）・`security-requirements.md`（NFR4.4）
- 承認の場の決定: `construction/nfr-requirements/memory.md` の Open questions（U2 R-02）
- この段の答え
  - `construction/u2-businessdata-write/nfr-design/nfr-design-questions.md`（Q1〜Q7: A）
  - `construction/u2-businessdata-write/nfr-design/nfr-design-followup-questions.md`（F1〜F4: A）
- 機能設計: `construction/u2-businessdata-write/functional-design/rules.md`（BR3.5・BR6.1〜BR6.4・BR8.1・BR8.2・BR9.3・BR9.5・BR10.1・BR11.3）・`functional-spec.md`（4節・11.2 の R-05）
- U1 の NFR 設計: `construction/u1-businessdata-read/nfr-design/reliability-design.md`（2節・3節・5節・8節）
- レビュー
  - `.aidlc-reviews/nfr-design/units/u1-businessdata-read/df13e747868fc8fa/1.json`（R-01・R-04）
  - `.aidlc-reviews/nfr-requirements/units/u2-businessdata-write/00d44a7a2452e6b7/1.json`（R-02）
  - `.aidlc-reviews/functional-design/units/u2-businessdata-write/7dacbaa3e842d0af/1.json`（R-05・R-08）
- コード
  - `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java`（通信の待ちの計算、読み取り専用のプール）
  - `backend/src/main/java/cherry/mastersmith/audit/domain/AuditResult.java`
  - `backend/src/main/resources/db/migration/V4__u4_audit_event.sql`（`result VARCHAR(16)`）
- 捨ての試し: リポジトリの外の試しのコードと結果（W1〜W4・W6・W7・W11・W12）

## Assumptions & Open Questions

- 前提: 中継で作った「つないだ後に止まる」は、本物のネットワークの断や DB の停止と同じ待ち方をするとみなした（U1 と同じ）。
- 前提: MySQL の 20 秒（通信の待ちの2回分）の内訳は確かめていない。確定と巻き戻しでも 20 秒だった。
- 前提: 確定の途中で止めて行が残ったのは、中継の中にたまった確定が、後で届いたため。本物の断では残らないこともあるが、「分からない」ことに変わりはない。
- 依頼者の決定 Q2〜Q6・F3 は、2節〜7節に反映した。
- この文書に固有の、諮る点は無い。
