# セキュリティの設計 — U2 u2-businessdata-write

## 捨ての試しの結果（要点）

試しの全体（版・設定）は reliability-design.md の 1節にある。セキュリティに関わるものは次の6つ。

- **W3（例外の文の中の値）**
  - **PG**: 23505・23503・23502・23514・22008 の例外の文に、入力した値が入った。
  - **MySQL・MariaDB**: 1062 の文に値が入った。MySQL は 1292 の文にも入った。
  - どの文にも接続先は入らなかった。
  - 設計への意味: 例外は JDBC を呼んだメソッドの中で捨てる、という決まりの根拠になる。
- **W5（書き込みのアカウントの権限）**
  - DSL の行の操作（SELECT・INSERT・UPDATE・DELETE）だけを与えると、CREATE・DROP・ALTER・TRUNCATE・CREATE INDEX・CREATE VIEW・CREATE SCHEMA/DATABASE は、3種類とも拒まれた（PG は 42501、MySQL・MariaDB は 1142・1044）。
  - **PG では一時の表を作れた**。既定で PUBLIC が TEMP の権限を持つため（F2: A で取り上げる）。
  - PG の `serial` は sequence の USAGE が要り、`identity` は表の INSERT だけで足りた。
  - MySQL・MariaDB は表ごとの GRANT が効く。付与の無い表は 1142 になり、情報スキーマに見える表も付与した表だけになる。
- **W8（生成された鍵）**: PG の `RETURN_GENERATED_KEYS` は、全部のカラムの値（`note` を含む）を返した。主キーの名前を渡す形なら、主キーだけを返す。
- **W11（H2）**
  - 監査の `detail` の長さを超えると、例外の文に `detail` の値（主キーを含む）が入った。
  - 設計への意味: 業務データの出来事では、監査の書き込みの失敗の ERROR に例外（cause）を付けない。
- **W7（止まった対象DB）**: どの段で止めても、例外の文に接続先は入らなかった。
- **W10（本文の大きさ）**
  - 4 バイトの文字で埋めた最悪の本文は 4,001,149 バイトで、4 MiB に 193,155 バイトの余りがある。
  - 制御の文字（`\u0001` として 6 バイト）で埋めると 6,001,149 バイトになり、超える。

## 対応表

| U2 の NFR 要件の ID | この文書の節 | 扱い |
|---|---|---|
| NFR4.1 書き込みの SQL の組み立て | 1節 | そのまま |
| NFR4.2 漏えいの防止 | 2節 | U1 R-02 を先に避ける形を足した |
| NFR4.3 書き込みの接続の分離 | 3節 | F2: A を足した |
| NFR4.4 書き込みの時間の上限 | 4節 | 値はそのまま。最悪の時間は reliability-design.md の 5節（Q4: A） |
| NFR4.7 pattern の照合の時間の上限 | 5節 | 確かめの入力は F4: A |
| NFR4.9 本文と値の長さ | 6節 | R-04 の手当て（Q7: A） |
| NFR4.10 認可・一括代入・IDOR | 7節 | そのまま |
| NFR4.11 書き込みの設定の欠け | 3節 | そのまま |
| NFR4.13 監査の中身と切り詰め | 8節 | 承認の場の U2 R-01 のとおり |
| NFR4.14 主キー・ビューの 409 の監査の範囲 | 8節 | そのまま |
| NFR4.16 MySQL・MariaDB の付与の範囲 | 3節 | 部分。W5 で材料を得た。決めるのは Infrastructure Design |
| NFR7.5 業務データの必須のテスト（書き込みの分） | 9節 | 確かめを足した |
| NFR7.6 性質ベースのテスト | 9節 | そのまま |

## 1. 書き込みの SQL の組み立て（NFR4.1）

| 層 | 守り |
|---|---|
| 入口 | 道を固定し、スキーマ名・テーブル名・主キー・値は本文で運ぶ |
| 業務処理 | 権限の写しの DSL で、スキーマ・テーブル・カラムを種類ごとの一覧と照らす。無い名前は SQL に届かない |
| repository | `RowSqlExecutor` の書き込みのメソッドだけが SQL を組み立てて実行する。識別子は `SqlIdentifiers.quoteKnown` で引用し、値は型ごとのセッターでバインドする。`stringtype=unspecified` に頼らない |
| 構造の検査 | `BusinessDataBoundaryArchitectureTest` で、`prepareStatement`・`createStatement` を呼んでよいのを `RowSqlExecutor` だけにする |
| 静的解析 | SpotBugs の `SQL_` の除外は、`RowSqlExecutor` の書き込みのメソッドとパターン名に絞って広げる（コード生成の計画で承認を得る） |

- 利用者の入力を含まない文は、定数の形で書く。
  - セーブポイントの作成・戻しは、JDBC の `Connection.setSavepoint`・`rollback(Savepoint)` を管理者が呼ぶ。文の文字列を組み立てない。
  - `connectionInitSql`（Q3: A）は、設定のクラスの中の定数とする。
- 事前の問い合わせと違反の後の確かめも、同じ部品で組み立てる。

## 2. 漏えいの防止（NFR4.2）

**例外の通り道（U1 R-02 を先に避ける形）**

U1 の NFR 設計のレビュー（`.aidlc-reviews/nfr-design/units/u1-businessdata-read/df13e747868fc8fa/1.json` の R-02）は、`SQLException` だけを受けると、非検査例外と `Done<T>` の `toString` から値が出うると指摘した。U2 は最初から次の形にする。

- `RowSqlExecutor` の書き込みのメソッドは、`SQLException` に加えて非検査例外（`NumberFormatException`・`DateTimeParseException`・ドライバーの `RuntimeException`）もメソッドの中で受ける。受けたら、理由・SQLState・業者の番号・例外のクラスの名前だけを持つ結果の型にして返す。
- 管理者に渡す処理（`TransactionTemplate` のコールバック）からは例外を投げない。`WriteStep` で答える。
- 書き込みの口（`JdbcTargetWriteAccess`、targetdb.service）は、`TransactionTemplate` の外側で、`TransactionSystemException` を含むすべての例外を受け、メッセージも原因も持たない結果の型（`TargetWriteOutcome`）にする。targetdb.service は TraceAspect の対象のため、口のメソッドからは例外を出さない。
- 結果の型（`TargetWriteSession`・`WriteStep`・`TargetWriteOutcome`・`WriteOutcome` と、その中の型）の `toString` は、DB の種類・カラムの名前・件数だけを出す。型の引数に頼らない。

**値を持つ型**
- `WriteRequest`・`WriteValues`・`TypedValue`・`RowKey`・`BusinessDataChangedEvent` の `toString` は、カラムの名前と件数だけを出す。
- 生成された鍵は、主キーの名前を渡して主キーだけを読む（W8）。全部のカラムの値をドライバーから受け取らない。

**監査の書き込みの失敗の ERROR（NFR8.4）**
- 業務データの出来事では、`detail`・主キーの値・例外（cause）を載せない。
- 載せるのは、主キーのカラムの数・`keyTruncated`・例外のクラスの名前だけ（W11 の根拠）。
- ほかの出来事の ERROR は、今のまま変えない。

**確かめ方**
- TRACE を有効にした `BusinessDataWriteSecretLeakIT` で、作成・更新・削除・違反・時間切れ・監査の書き込みの失敗の場合を確かめる。
- 非検査例外の場合（不正な数・日時の文字）と、結果の型を通る戻り値の場合も足す。
- 外部へ送る設定を有効にした結合テストで、トレースの属性に値が無いことを確かめる。

## 3. 書き込みの接続の分離（NFR4.3・NFR4.11・NFR4.16）

- 書き込みの接続は、読み取りの接続先と、書き込みの資格情報（`.env` だけから受け取る）で作る、別の名前つきのプール `targetWriteDataSource` にする。既定の候補にせず、読み取り専用にしない。
- 書き込みの資格情報の片方だけがあるときは、項目の名前だけを WARN で1回出し、書き込みは 409 `BUSINESS_WRITE_NOT_CONFIGURED` にする。
- **見本の書き込みのアカウント**
  - PG
    - スキーマの USAGE、表の SELECT・INSERT・UPDATE・DELETE、sequence の USAGE（`serial` のため）を与える。
    - `TEMPORARY` を PUBLIC から取り上げる（F2: A。`REVOKE TEMPORARY ON DATABASE … FROM PUBLIC`）。
  - MySQL・MariaDB: 表の SELECT・INSERT・UPDATE・DELETE を与える。
  - DDL の権限は、どの DB にも与えない。
- **表ごとの付与（NFR4.16、機能設計のレビューの R-06）**
  - W5 で、MySQL・MariaDB でも表ごとの GRANT にできると分かった。
  - DSL の対象のテーブルに絞るかと、初期化の台本の形は、Infrastructure Design で決める。
- **確かめ方**
  - 書き込みのアカウントで表を作る・消す・変える・一時の表を作る操作が、3種類の DB で拒まれることの結合テスト（F2: A で一時の表を足す）。

## 4. 書き込みの時間の上限（NFR4.4）

- 文ごとの上限は 5 秒（`setQueryTimeout(5)`）。事前の問い合わせ・書き込みの文・違反の後の確かめのすべてに付ける。
- 書き込みのプールのドライバーの通信の待ちは、プールの設定で 10 秒にする（Q3: A）。読み取りのプールの 25 秒は変えない。
- 最悪の時間（Q4: A）は reliability-design.md の 5節にある。

## 5. pattern の照合（NFR4.7）

- 締め切りを見る包みで照合し、別のスレッドを残さない。値ごとに 100 ms、合計 300 ms。締め切りは注入した時計で決め、256 文字ごとに見る（performance-design.md の 3節）。
- 包みの `toString` は値を出さない（`***`）。試しでは、伏せても `matches()` の結果は変わらなかった（17 の式）。
- 確かめの入力は、`((a+)+)+c` と `(.*a){12}`（F4: A）。

包みの形（説明用の断片）:

```java
record DeadlineSeq(CharSequence v, LongSupplier now, long deadline, int[] count) implements CharSequence {
    public char charAt(int i) {
        if (++count[0] % 256 == 0 && now.getAsLong() - deadline >= 0) throw new PatternDeadline();
        return v.charAt(i);
    }
    public int length() { return v.length(); }
    public CharSequence subSequence(int s, int e) { return new DeadlineSeq(v.subSequence(s, e), now, deadline, count); }
    @Override public String toString() { return "***"; }
}
```

`PatternDeadline` は、メッセージもスタックトレースも持たない例外とし、照合の部品の中で `PATTERN_TIMEOUT` に変える（層の外へ出さない）。

## 6. 本文と値の長さ（NFR4.9、R-04 は Q7: A）

| 確かめ | 上限 | 超えたとき |
|---|---|---|
| 作成・更新の本文 | 4 MiB（4,194,304 バイト） | 413 `PAYLOAD_TOO_LARGE`（400 より先に出る） |
| 削除の本文 | 64 KB | 413 |
| 書き込みの値 | 1つ 10,000 コードポイント（DSL・DB の長さが短ければそちら） | 400 `TOO_LONG` |
| 主キーの値 | 1つ 1,000 コードポイント | 400 `INVALID_VALUE` |
| 日時の秒の端数 | DB の型の精度まで | 400 `TOO_MANY_DIGITS`（Q6: A） |

**R-04 の手当て**
- 4 MiB は、UTF-8 のまま送る前提で、最悪の本文（4 バイトの文字で埋めた 4,001,149 バイト）に収まる。
- エスケープが多い値（`\u0001` などの制御の文字）では、10,000 字の上限の中でも 413 が先に出る。これを文書と README に書く。
- 境界のテストは、全部のカラムを 4 バイトの文字で埋めた最大の本文が通ることを確かめる形にする。

## 7. 認可・一括代入・IDOR（NFR4.10）

- 3本の API は `@ApiAccess(AUTHENTICATED)` にし、API の分類の網羅のテストに載せる。
- テーブルの権限は、業務処理の層で要求ごとに判定する。
- 書けないカラムは 400 で拒む。`definitionId` を差し替えた要求は 409 で拒む。
- 画面で入口を隠すことを、判定の代わりにしない。

## 8. 監査の中身と範囲（NFR4.13・NFR4.14）

- 監査の行に、`target_schema`・`target_table`・`work_role_id` の3列と、`detail` の主キーを置く。
- **切り詰めは businessdata の側で行う**（承認の場の U2 R-01）。
  - `detail` を 16,384 字（UTF-16 の単位）に収めてから、`keyTruncated` を付けて渡す。
  - `AuditEvent` の「超えたら作らない」確かめは変えない。
- 監査の理由の値は、応答の code とは別の列挙にする（32 字以内）。
- 時間切れは、結果を `UNKNOWN` にする（Q5: A。reliability-design.md の 4節）。
- 主キー・ビューの決まりによる 409 を残すのは、その操作の補助権限を持つ人の拒否だけにする。

## 9. 必須のテスト（NFR7.5・NFR7.6）

承認済みの NFR7.5 の一覧のとおり。この段で足す確かめは次のとおり。

- 書き込みのアカウントで一時の表を作れないこと（PG、F2: A）
- 非検査例外の場合（不正な数・日時の文字）で、応答とログに値が無いこと（U1 R-02 を先に避ける形）
- PG の生成された鍵が、主キーだけを返すこと
- 日時の秒の端数の境界（精度ちょうどは受け付け、1桁超えると `TOO_MANY_DIGITS`）（Q6: A）
- CHECK の違反・日時の範囲の外・文字集合・NUL・更新できないビューで、409 `BUSINESS_SAVE_REJECTED` になり、応答とログに値が無いこと（Q6: A・F3: A）
- 4 バイトの文字で埋めた最大の本文が 4 MiB に収まって通ること（R-04）

性質ベースのテスト（jqwik）は NFR7.6 のとおり。`WriteRequestValidator` と、pattern の打ち切り（任意の値と式で、合計の締め切りを超えて照合が続かない）に当てる。

## 10. 上流との差

- **BR6.1 の表に足す**: CHECK の違反・日時の範囲の外・文字集合・NUL・更新できないビューを足す（Q6: A・F3: A）。
- **BR6.2 の切り替え先を先に使う**: セーブポイントで確かめる（Q2: A）。
- **BR3.5 に日時の秒の端数を足す**（Q6: A）。
- **U1 の「貸すたびに付ける」との差（Q3: A）**: 通信の待ちと初期化は、プールの設定で付ける。
- **NFR1.6・NFR4.7 の確かめの入力（F4: A）**: `((a+)+)+c` と `(.*a){12}` に読み替える。
- **「およそ 18 秒」の読み替え（Q4: A）**: reliability-design.md の 5節。
- **BR13.4（見本のアカウント）**: PG の sequence の USAGE を足し、TEMPORARY を PUBLIC から取り上げる（F2: A）。AC3.6.7 の「表を作る操作は拒まれる」を、一時の表まで広げて読む。
- **NFR8.4（監査の書き込みの失敗の ERROR）**: 業務データの出来事では、例外（cause）も外すことを足した（W11 の根拠）。

## 11. 後の段に回すこと

- **Infrastructure Design**
  - F1: A の perf の MySQL のメモリの上限
  - F2: A の `REVOKE TEMPORARY ON DATABASE … FROM PUBLIC` と、利用者の DB での手順の README
  - 機能設計のレビューの R-06 の表ごとの GRANT（W5 の結果を材料にする）
  - `.env.example` の書き込みの項目
- **コード生成の計画（B4）**
  - SpotBugs の `SQL_` の除外を広げることの承認
  - 名前つきの書き込みの管理者が、型で注入する内部DB の管理者とぶつからないことの結合テスト
  - NFR 要件のレビューの R-06: 監査に列と値（`UNKNOWN` を含む）を足す移行の後方互換の結合テスト
  - `AuditMigrationCompatibilityIT`・`AuditSecretLeakIT`・`AuditEventTest` などの洗い出し
- **Observability Setup**
  - NFR 要件のレビューの R-03: 内部DB の大きさの基準の値
  - `ms-pool-pending` の実測

## 12. U3 とそろえる点

- 413 の文と、制御の文字が多い値では 413 が先に出ること。
- `TOO_MANY_DIGITS` の秒の端数の文。日時の入力は秒まで（Q6: A）。
- カラムを示さない `BUSINESS_SAVE_REJECTED` の文。
- pattern は画面で当てず、`PATTERN_TIMEOUT` の文を用意する。
- 漏えいの確かめの `*SecretLeakIT` の形を、3単位でそろえる。

## Sources

- 承認済みの NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/nfr-requirements/security-requirements.md`（NFR4.1〜NFR4.4・NFR4.7・NFR4.9〜NFR4.11・NFR4.13・NFR4.14・NFR4.16・NFR7.5・NFR7.6）・`observability-requirements.md`（NFR8.4）
- 承認の場の決定: `construction/nfr-requirements/memory.md` の Open questions（U2 R-01）
- この段の答え
  - `construction/u2-businessdata-write/nfr-design/nfr-design-questions.md`（Q1〜Q7: A）
  - `construction/u2-businessdata-write/nfr-design/nfr-design-followup-questions.md`（F1〜F4: A）
- 機能設計: `construction/u2-businessdata-write/functional-design/rules.md`（BR3・BR4.2・BR7・BR11・BR12・BR13）
- レビュー
  - `.aidlc-reviews/nfr-design/units/u1-businessdata-read/df13e747868fc8fa/1.json`（R-02）
  - `.aidlc-reviews/nfr-requirements/units/u2-businessdata-write/00d44a7a2452e6b7/1.json`（R-01・R-04）
  - `.aidlc-reviews/functional-design/units/u2-businessdata-write/7dacbaa3e842d0af/1.json`（R-06）
- コード
  - `backend/src/main/java/cherry/mastersmith/audit/domain/AuditEvent.java`（`MAX_DETAIL_LENGTH`・`withRoleGroupTarget` の確かめ）
  - `backend/src/main/java/cherry/mastersmith/audit/service/AuditEventListener.java`（`logFailure` の `setCause`）
  - `backend/src/main/java/cherry/mastersmith/common/observability/TraceAspect.java`（対象の層）
  - `backend/src/main/java/cherry/mastersmith/targetdb/domain/SqlIdentifiers.java`
  - `docker/targetdb/postgres/02-reader-account.sh`・`docker/targetdb/mysql/02-reader-account.sh`
- 捨ての試し: リポジトリの外の試しのコードと結果（W3・W5・W7・W8・W10・W11）

## Assumptions & Open Questions

- 前提: 試しは架空の値と管理者が作った表で行った。利用者の対象DB では、付与の形が違いうる。
- 前提: MySQL・MariaDB では、NUL の入った文字列を誤りにせず受け付ける見込み（試していない）。PG だけが 22021 で拒む。
- 依頼者の決定 Q1〜Q7・F1〜F4 は、この文書に反映した。
- この文書に固有の、諮る点は無い。
