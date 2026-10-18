# セキュリティの設計 — U1 u1-businessdata-read

## 捨ての試しの結果（要点）

試しの全体は reliability-design.md の 1節にある。セキュリティに関わるものは次の5つ。

- **T3（例外の文）**: PG の 22P02（JSON・uuid の不正な値）・22007（日付）・22003（数）の例外の文に、**入力した値が入った**。MySQL・MariaDB の例外の文には、試した範囲では値が入らなかった。どの例外の文にも、接続先は入らなかった。
  - 設計への意味: 例外は JDBC を呼んだメソッドの中で捨てる、という決まりの根拠になる。
- **T3（黙って 0 行になる）**: MySQL・MariaDB では、整数のカラムに文字を渡しても、範囲の外の日付を渡しても、誤りにならずに 0 行になることが多かった（警告 1292）。
  - 設計への意味: 値を先にカラムの型として読み、型ごとのセッターで渡す決まり（BR4.7・BR7.8）は、黙って違う意味になることを防ぐ。
- **T9（部分一致）**: `LIKE ? ESCAPE '!'` に、`%`・`_`・`!` の前に `!` を付けた値を渡すと、3種類ともその文字を文字として扱った。`\` も文字のまま扱った。
- **T2（OTHER と BINARY に寄せる型）**:
  - MySQL の `json` は、文字で比べると誤りにならずに 0 行になる。そのため EQUALS から外す。
  - MySQL・MariaDB の `bit` と空間の型は、BINARY と同じ扱いにする（F3: A。reliability-design.md の 4節）。
- **F2（通信の待ち）**: 貸すときに `setNetworkTimeout` を付けた形でも、例外の文に接続先は入らなかった。

## 対応表

| U1 の NFR 要件の ID | この文書の節 | 扱い |
|---|---|---|
| NFR4.1 SQL の組み立て | 1節・2節 | そのまま |
| NFR4.2 行の値の漏えい | 3節 | そのまま |
| NFR4.3 読み取りの接続（部分。書き込みは U2） | 4節 | 読み取りの側だけ |
| NFR4.4 時間の上限 | 5節 | 値は承認済みのとおり。貸すときの通信の待ちを足した（F2: A） |
| NFR4.5 要求の大きさと値の長さ | 6節 | Q4・Q6 の答えを反映 |
| NFR4.6 認可と API の分類 | 7節 | そのまま |
| NFR7.1 必須のテスト（読み取りの分） | 8節 | そのまま |
| NFR7.2 性質ベースのテスト | 8節 | そのまま |

## 1. SQL の組み立て（多層の守り）

| 層 | 守り |
|---|---|
| 入口 | 道を固定し、スキーマ名・テーブル名・値は本文で運ぶ（BR10.4） |
| 業務処理 | 写し1つの DSL で、スキーマ・テーブル・カラムを文字どおり照らす。DSL に無い名前は 404 か 400 にし、SQL まで届かない（BR1.3・BR3） |
| repository | `RowSqlExecutor` だけが SQL を組み立てて実行する。識別子は、種類ごとの名前の一覧（`KnownIdentifiers`）を渡して、既存の `SqlIdentifiers.quoteKnown` で引用する。一覧に無い名前は、問い合わせをせずに想定外の失敗にする。値はすべて型ごとのセッターでバインドする（BR7.8）。部分一致は `LIKE ? ESCAPE '!'` |
| 構造の検査 | `BusinessDataBoundaryArchitectureTest` で、`java.sql.Connection` の `prepareStatement`・`createStatement` を呼んでよいのを `RowSqlExecutor` だけにする（BR11.2） |
| 静的解析 | SpotBugs の `SQL_` は priority によらず止める。除外は `RowSqlExecutor` のクラス・メソッド・パターン名の3つで絞り、理由（名前は種類ごとの DSL の一覧で照らしたものだけ）を書く（BR11.4） |

利用者の入力を含まない、定数の形の式と文:

- MySQL・MariaDB の `SET time_zone = '+00:00'`（reliability-design.md の 3節）は、定数の文として `RowSqlExecutor` の中で流す。
- 時差つきの型（`timetz`）の `AT TIME ZONE 'UTC'` と、OTHER の `CAST`（performance-design.md の 1節）は、RowDialect が定数の形で組み立てる。

## 2. 値の受け渡し

- 検索の値と主キーの値は、`ColumnValueCodec` でカラムの型として読んでからバインドする。読めない値は 400（`INVALID_VALUE`）にする。
- OTHER の値は、PG では `setObject(位置, 文字, Types.OTHER)`、MySQL・MariaDB では `setString` で渡す。型ごとの例外（BR7.9 と、BINARY と同じ扱いの型）は、reliability-design.md の 4節にある。
- BINARY と同じ扱いの型（MySQL・MariaDB の `bit`（長さ 2 以上）と空間の型）は、読まない。入力（検索・主キー）にもしない。
- 部分一致の値は、`!` → `!!`、`%` → `!%`、`_` → `!_` の順に置き換えてから、前後に `%` を付ける。この関数は純粋な関数にし、性質ベースのテストを当てる（8節）。

## 3. 漏えいの防止

**例外の通り道**（決まっていること。reliability-design.md の 2節）
- `JdbcTargetReadAccess.withRead` と `RowSqlExecutor` の各メソッドは、`SQLException` をメソッドの中で受ける。メッセージと原因を捨て、SQLState・業者の番号・例外のクラスの名前だけを持つ結果の型にして返す。
- 通信の待ちを戻す処理の失敗も、口の中で受け、例外のクラスの名前だけを出す。
- 根拠は2つ。
  - TraceAspect は、web・service・domain・repository の例外を、文とスタックトレースつきで TRACE に出す（`backend/src/main/resources/application.yaml` の `mastersmith.trace`）。
  - T3 で、PG の例外の文に入力の値が入ることを確かめた。
- 想定外の失敗は、業務処理の層が「メッセージも原因も持たない例外」にして投げる。共通の仕組みが 500 の ERROR として出しても、値は残らない。

**値を持つ型と道**
- 値を持つ型（`RowKey`・`RowValues`・`SearchCondition`・`BoundValue`、要求と応答の record）の `toString` は、カラムの名前と件数だけを出す。
- `TargetReadSession` の `toString` は、DB の種類だけを出す（BR10.1）。
- 道は固定する（BR10.4）。トレースの `uri` の属性に、値が載らない。
- 3種類のドライバー自身のログは OFF のままにする（前の Intent の決まり）。

**確かめ方**
- TRACE を有効にした `BusinessDataSecretLeakIT`。一覧・詳細・400・503 で、PG の 22P02 を起こす値を含めて、ログに値が出ないこと。
- 外部へ送る設定を有効にした結合テスト。トレースの属性に値が無いこと。

## 4. 読み取りの接続（NFR4.3、部分）

- 読み取りの API は、今の読み取りの `targetDataSource`（既定の候補にしない）を、`targetdb.service` の `TargetReadAccess` からだけ使う。新しい接続の Bean は作らない。
- 書き込みの接続と資格情報の分離は、U2 が受け持つ。

## 5. 時間の上限（NFR4.4）

- `ReadPurpose` に `LIST`・`DETAIL` を足し、`TargetDbProperties.QueryTimeout` に `list`（既定 5 秒）と `detail`（既定 5 秒）を足す。
- 足した2つの値は、設定の点検（1 秒以上）と、ドライバーの通信の待ちの計算（最も長い上限 + 5 秒）に入れる。最も長いのは生成の 20 秒のままなので、ドライバーの通信の待ちは 25 秒で変わらない。
- 貸すときは、口が `setNetworkTimeout`（目的の上限 + 5 秒 = 10 秒）を付け、返すときに戻す（F2: A）。
- 応答しない相手での実測は、reliability-design.md の 5節にある。3種類とも 10〜20 秒で失敗した。

## 6. 要求の大きさと値の長さ（NFR4.5、Q4: A・Q6: A）

| 確かめ | 上限 | 超えたとき | 確かめる所と順 |
|---|---|---|---|
| 本文の大きさ | 3本の道ごとに 64KB（`businessdata.web` の `BusinessDataWebConfig` に `RequestBodyLimitRoute` を3つ置く） | 413 `PAYLOAD_TOO_LARGE` | `RequestSizeLimitFilter`（認証と認可の後、コントローラーの前）。**413 が 400 より先に出る** |
| 検索の値・主キーの値の長さ | 1つ 1,000 コードポイント | 400 `VALIDATION_FAILED`（`INVALID_VALUE`、`definitionChanged` つき） | `SearchRequestValidator` |
| page | 1〜1,000,000 | 400 `VALIDATION_FAILED`（fieldErrors なし、`definitionChanged` つき） | `SearchRequestValidator`。読み飛ばす行の数は long で計算する |
| CHOICE の選んだ値 | 重複を除く。除いた後の数が選択肢の数を超えたら拒否する | 400 `VALIDATION_FAILED`（`NOT_A_CHOICE`） | `SearchRequestValidator` |

64KB の上限は、理屈の上の最大より意図して小さくしている（R-04）。
- 理屈の上の最大は約 800KB（100 項目 × 範囲の2つの値 × 1,000 字 × UTF-8 で最大 4 バイト）。
- 実際の検索の要求は数 KB で、64KB を超えるのは、普通の画面からは来ない要求だけと見立てた。

## 7. 認可と API の分類（NFR4.6）

- 3本の API は `@ApiAccess(AUTHENTICATED)` にし、API の分類の網羅のテストに載せる。
- テーブルの権限は、業務処理の層で、要求ごとに写しを1回取って判定する（判定の順は機能設計の 2節）。
- 画面の出し分けを、判定の代わりにしない。

## 8. 必須のテスト（NFR7.1・NFR7.2）

承認済みの NFR7.1 の一覧のとおり。この段で足す確かめは次の6つ。

- page の上限の境界（1,000,000 は受け付け、1,000,001 は 400）
- 本文の大きさの境界（64KB ちょうどは受け付け、超えると 413）。413 が 400 より先に出ること
- CHOICE の重複を除くこと
- PG の 22P02 を起こす OTHER の値で、応答とログに値が無いこと
- MySQL の `json` のカラムが、検索の項目に出ないこと（BR7.9）
- MySQL・MariaDB の `bit`（長さ 2 以上）と空間の型が、値を返さず、検索・並べ替えの対象に出ず、定義で `valueType: BINARY` になること（F3: A）

性質ベースのテスト（jqwik）を当てるものは次の3つ。

- 許す操作の関数
- 識別子の照合
- 部分一致のエスケープの関数（任意の文字列で、エスケープの後に `!` の前置の無い `%`・`_` が残らないこと）

## 9. 上流との差

- **R-04 の手当て**: CHOICE の重複の除去と、64KB を意図して小さくしていることは、Q6: A で取り込んだ。BR4.5 に「重複を除く」の1行を足す差になる。
- **page の上限**: Q4: A で、BR5.1 に上限 1,000,000 を足す差になる。
- **EQUALS から外す型**: MySQL の `json` と、PG の `json`・`xml`・幾何の型を EQUALS から外すのは、BR7.9 を型ごとに細かくした差（reliability-design.md の 6節）。
- **BR7.1 の分類との差（F3: A）**: MySQL・MariaDB の `bit`（長さ 2 以上）と空間の型を、OTHER ではなく BINARY と同じ扱いにする（reliability-design.md の 6節）。
- **NFR4.4 の通信の待ち**: 貸すときの `setNetworkTimeout` を足した（F2: A）。

## 10. 後の段に回すこと

- **コード生成の計画（B2）で依頼者の承認を得るもの**:
  - SpotBugs の `SQL_` の除外の絞り込み（BR11.4）
  - `RoleBoundaryArchitectureTest` の変更と、`PermissionSnapshot` の口（BR11.3）
- **コード生成の計画（B2）で決めるもの**:
  - `ReadPurpose`・`QueryTimeout`・`TargetDbSettings` に手を入れることと、それを使う既存のテストの洗い出し
  - `setNetworkTimeout` の executor の持ち方

## 11. U2・U3 とそろえる点

- **U2 の書き込みの口**:
  - 例外をメソッドの中で受けて捨てる形と、`setNetworkTimeout` を付けて戻す形を、読み取りと同じにする。
  - `RowSqlExecutor` に書き込みのメソッドを足し、SpotBugs の除外もそのメソッドの名前で絞る。
  - BINARY と同じ扱いの型は、入力にしない。
- **上限の分け方**: U2 の書き込みの本文の上限は、別の道の決まりとして U2 で決める。値の長さの 1,000 字は、検索の値と主キーの値にだけ当てる。
- **U3 の画面**: 413・400 の応答を扱い、page の上限（1,000,000）を画面のページの番号に当てる。
- **漏えいの確かめ**: `*SecretLeakIT` の形を、3つの単位でそろえる。

## Sources

- 承認済みの NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/nfr-requirements/security-requirements.md`（NFR4.1〜NFR4.6・NFR7.1・NFR7.2）
- NFR 要件のレビュー: `.aidlc-reviews/nfr-requirements/units/u1-businessdata-read/00d44a7a2452e6b7/1.json`（R-02・R-04）
- この段の答え
  - `construction/u1-businessdata-read/nfr-design/nfr-design-questions.md`（Q4・Q6: A）
  - `construction/u1-businessdata-read/nfr-design/nfr-design-followup-questions.md`（F2・F3: A）
- 機能設計: `construction/u1-businessdata-read/functional-design/rules.md`（BR1・BR3・BR4・BR5.1・BR7.1・BR7.8・BR10・BR11）
- コード
  - `backend/src/main/java/cherry/mastersmith/targetdb/domain/SqlIdentifiers.java`
  - `backend/src/main/java/cherry/mastersmith/common/observability/TraceAspect.java`
  - `backend/src/main/java/cherry/mastersmith/common/web/RequestSizeLimitFilter.java`（認証と認可の後に置く）
  - `backend/src/main/java/cherry/mastersmith/role/web/RoleWebConfig.java`（`RequestBodyLimitRoute` の形）
- 決まり
  - `aidlc/spaces/default/memory/project.md`（Forbidden の SQL・行の値・JDBC の例外）
  - `team.md`（Code Style の SQL の組み立て・SpotBugs）
- 捨ての試し: リポジトリの外の試しのコードと結果（T2・T3・T9・F2）

## Assumptions & Open Questions

- 前提: 普通の画面からの検索の要求は、64KB を超えない（6節の見立て）。超える要求が要るとなったら、上限を見直す。
- **依頼者の決定（F2: A・F3: A）**: この文書に反映した（5節・2節・8節・9節）。依頼者の決定 F1: A は、performance-design.md にある。
- この文書だけに固有の、諮る点は無い。
