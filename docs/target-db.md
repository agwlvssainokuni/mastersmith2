# 対象DB と見本の DB

利用者の業務データの DB（対象DB）の接続の決まり、業務データの読み取りと書き込みの API、結合テスト、手元で試す見本の DB と書き込みのアカウントをまとめた文書です。

## 対象DB（利用者の業務データの DB）

アプリは、利用者の業務データの DB（対象DB。MySQL・MariaDB・PostgreSQL）を、読み取り専用の接続で読みます（設定したスキーマのテーブル・ビュー・カラム・キー・コメントのメタデータと、業務データの読み取りの API が読む行）。業務データの書き込み（作成・更新・削除）は、読み取りとは別の書き込みのアカウントの接続で、行の操作（`INSERT`・`UPDATE`・`DELETE` と、確かめの `SELECT`）だけを発行します。接続先と資格情報は環境変数 `MASTERSMITH_TARGET_DB_*`（`docs/configuration.md` の「環境変数」の表）だけから受け取り、画面・API から受け取りません。

- 7つの項目がすべて空なら対象DB を使わず、起動は続きます。一部だけ空・不正なら、問題のある項目の名前（例: `mastersmith.target-db.schema`）だけを WARN で1件出し、対象DB を使いません（値はログに出しません）。直したら再起動します。
- 起動時には対象DB に接続しません。ヘルスチェックは内部DB だけで判断し、対象DB の状態は応答に含めません。
- ログイン・監査ログ・Flyway は内部DB を使い続け、対象DB には表を作りません。
- 待ちの上限は、接続の待ち（既定 3 秒）と、問い合わせ1回ごとの待ち（既定の DSL の生成 20 秒・照合 5 秒・業務データの一覧 5 秒・詳細 5 秒）だけです。ドライバーの通信の待ちは、4つの問い合わせの上限の最も長いもの + 5 秒（既定 25 秒）です。読み取りの全体の上限はありません。業務データの書き込みは、文1回ごとの待ち（既定 5 秒）と、書き込みのドライバーの通信の待ち（その値 + 5 秒、既定 10 秒）を、読み取りとは別に持ちます。読み取りは問い合わせ4回のため、応答しない対象DB では、照合でも最悪 接続 3 秒＋5 秒×4 回（約 23 秒）かかることがあります（照合の 10 秒の目標を超えることを許す決定です）。
- 読めなかったときは、原因の種類（`TIMEOUT`・`CONNECTION_FAILED`）と SQLState だけを WARN で出します。接続先・ユーザー名・パスワード・ドライバーの例外の文言は出しません。
- 3つのドライバー（MariaDB・PostgreSQL・MySQL）自身のログは、`backend/src/main/resources/application.yaml` の `logging.level` で止めています（`org.mariadb.jdbc`・`org.postgresql`・`com.mysql.cj` を `OFF`）。ドライバーのログは接続先・ユーザー名・例外の文言を含むことがあり、MariaDB のドライバーは認証の失敗をユーザー名つきで WARN に出すことを結合テストで確かめました。MySQL・PostgreSQL のドライバーも同じ扱いにそろえています。
- 読めない原因を調べるときは、まず読み取りの口（`JdbcTargetSchemaReader`）の WARN の `reason` と `sqlState` で絞ります（`08` で始まる: 接続できない、`28` で始まる: 認証の失敗、`57014`: 問い合わせの打ち切り、`TIMEOUT` で SQLState が無い: 接続の待ちの間に応答が無い）。さらに調べるときは、同じネットワークから DB の付属のクライアント（`psql`・`mysql`・`mariadb`）で接続を試すか、対象DB の側のログを見てください。配備した環境でドライバーのログを有効にしないでください（接続情報がログに残ります）。
- 対象DB の読み取りのアカウントは、読み取りの権限だけにしてください（MySQL・MariaDB は `GRANT SELECT, SHOW VIEW ON <DB>.*`、PostgreSQL は `GRANT USAGE ON SCHEMA` と `GRANT SELECT ON ALL TABLES IN SCHEMA`）。読み取りの接続は読み取り専用にしています。
- 書き込みのアカウント（`MASTERSMITH_TARGET_DB_WRITE_*`）には、DSL の対象のスキーマの行の操作（`SELECT`・`INSERT`・`UPDATE`・`DELETE`。PostgreSQL は自動の採番の sequence の `USAGE` も）だけを与え、DDL（表・索引・ビュー・スキーマを作る・消す・変える、`TRUNCATE`）・一時の表・`LOCK TABLES` の権限を与えないでください。アプリはアカウントの権限を調べません。書き込みの設定が無ければ、書き込みの API は 409 `BUSINESS_WRITE_NOT_CONFIGURED` を返し、読み取りは動き続けます。

### 業務データの読み取りの API

適用中の DSL にあるテーブルの定義・一覧（検索と並べ替え）・1行の詳細と、他テーブル参照の参照先の検索とラベルを、4本の道で返します。どれも `POST` で、スキーマ名・テーブル名・検索の値・主キーの値は道や問い合わせに載せず、本文（JSON）で受け取ります（トレースの道の属性に値を残さないため）。

| 道 | すること |
|---|---|
| `POST /api/tables/definition` | テーブルの定義（一覧・詳細に出すカラム、検索の項目、並べ替えられるカラム、許す操作）。本文の任意の `purpose`（`VIEW`・`CREATE`・`UPDATE`。無ければ `VIEW`）が `VIEW` なら対象DB には接続しません。`purpose` が `CREATE`・`UPDATE` で参照の項目（他テーブル参照の `REFERENCE`）があるときだけ、読み取りの接続を1本借りて参照先の選択肢を読みます（各カラムの `reference` に状態・選択肢・ピッカーの情報を載せ、状態が `AVAILABLE` のときだけ、参照先のラベルのカラムの受け渡しの型 `labelValueType`（`STRING`・`DATETIME_TZ` など。画面がラベルの日時を書式にするのに使う。DSL から決め、問い合わせは増えない）も載せます） |
| `POST /api/tables/rows/search` | 一覧（条件・並べ替え・ページ。1ページは 20・50・100 件、全体の件数を含む）。文字の値は一覧では 100 字で切り、切ったカラムを示します |
| `POST /api/tables/rows/get` | 主キーで1行の詳細（文字の値は 10,000 字で切ります） |
| `POST /api/tables/reference/rows` | 他テーブル参照の参照先の検索とラベルの1件読み。本文は参照元の `schema`・`table`・`column` と、`conditions`＋`page`（LOOKUP のピッカーの検索。1ページ 20 件、件数を数えず `hasNext` を返す）か `value`（ラベルの1件読み。REFERENCE・LOOKUP の両方）のどちらか一方。参照先の名前は受け取らず DSL から引きます。読み取りの接続を1本借り、文を1つだけ流します。本文の形の誤り（両方・どちらも無い、値が 1,000 文字を超える、`page` が 1〜1,000,000 の外など）は 400、名前・権限・DSL・ラベルの行が無いときの拒否はすべて同じ 403（`ACCESS_DENIED`）、完全一致の条件の値を型として読めないときは 400（`fieldErrors` に `INVALID_VALUE`）。本文の上限は 64 KB |

- ログインだけが要ります（管理の権限は要りません）。読めるかはテーブルごとに、作業ロールの権限（主権限が `NONE` でないこと）で、要求ごとにサーバー側で判定します。読めないカラム（主権限 `NONE`）は応答に含めません。
- 対象DB へは読み取りの接続（読み取りだけのアカウント）だけを使い、書き込みはしません。1要求で借りる対象DB の接続は1本までで、借りている間は内部DB の接続を持ちません。
- 識別子（スキーマ名・テーブル名・カラム名・並べ替えの列）は適用中の DSL にある名前と照らしてから引用し、値はバインド変数で渡します。
- 失敗は Problem Details の `code` で返します。DSL が適用されていない・テーブルが DSL に無い・行が無いときは 404（`BUSINESS_DSL_NOT_APPLIED`・`BUSINESS_TABLE_NOT_FOUND`・`BUSINESS_ROW_NOT_FOUND`）、権限が無いときは 403（`ACCESS_DENIED`）、主キーの無いテーブル・ビューなど行を1件に決められないテーブルの詳細は 409（`BUSINESS_ROW_OPERATION_NOT_ALLOWED`）、主キーで2行以上が当たったときは 409（`BUSINESS_ROW_NOT_UNIQUE`）、DSL と対象DB が合わないときは 409（`BUSINESS_TARGET_DB_MISMATCH`）、入力の誤りは 400（`VALIDATION_FAILED`）、対象DB に届かない・借りる待ち（3 秒。MySQL で接続の確かめが走るときは 6 秒）を超えた・時間切れのときは 503（`BUSINESS_TARGET_DB_UNAVAILABLE`・`BUSINESS_TARGET_DB_TIMEOUT`）です。行の値・接続先・ドライバーの例外の文言は、応答にもログにも出しません（ログは SQLState・業者のエラーの番号・例外のクラスの名前だけ）。

- **部分一致の検索は索引が効かない（運用の注意）**: 文字のカラムの部分一致は `LIKE '%…%'`（前にも後ろにも `%`）で、MySQL・MariaDB・PostgreSQL のどの DB でも通常の索引を使えず、表の全部を読みます。一覧は件数（`COUNT`）とページの2つの問い合わせで返すため、**1回の検索で表の全部を2回読みます**。行が多い表（数十万行以上）では、読み取りのプールの接続を長く借り（上限 10・借りる待ち 3 秒）、同時の検索が重なると 503（`BUSINESS_TARGET_DB_UNAVAILABLE`）や対象DB のメモリ（MySQL・MariaDB のバッファープール）の使い切りにつながりえます。DSL では、行が多い表の文字のカラムは `search.enabled` を `false` にする（検索の項目から外す）か、`search.operator` を `EQUALS`（完全一致）にしてください。同じ注意を DSL の JSON Schema の `description`（`searchOperator`・`search`）にも書いています。画面は変えません（検索の項目の出し分けは DSL の `search` の設定に従います）。負荷の試験の数字（`bizSearchContains`）は `perf/README.md` の「業務データの場面」にあります。

### 日時・時刻の値

業務データの API（読み取り・書き込み・検索・参照）は、日時・時刻の値を次の形の文字で受け渡します。型は DSL の `dbType.name` と DB の種類から決めます（桁つきの `timestamp(3) with time zone`・`time(3) with time zone` も時差つきの型として扱います）。

| 型（受け渡しの型） | 当たる型 | 応答の形 | 要求の値 |
|---|---|---|---|
| 時点（`DATETIME_TZ`） | MySQL・MariaDB の `timestamp`、PostgreSQL の `timestamptz` | UTC にそろえた `YYYY-MM-DDTHH:mm:ss[.端数]+00:00` | 時差つき（`Z`・`z`・`±hh:mm`）は UTC の時点に直す。時差の無い値は UTC とみなす |
| 時差を持たない日時（`DATETIME`） | MySQL・MariaDB の `datetime`、PostgreSQL の `timestamp` | `YYYY-MM-DDTHH:mm:ss[.端数]`（DB の値のまま） | 時差なしの同じ形 |
| 日付（`DATE`） | `date` | `YYYY-MM-DD` | 同じ形 |
| 時刻（`TIME`） | `time` | `HH:mm:ss[.端数]` | 同じ形 |
| 時差つきの時刻（`TIME_TZ`） | PostgreSQL の `timetz` | `HH:mm:ss[.端数]±hh:mm`（DB に入っている時差のまま。UTC にそろえない） | 時差つきは時差ごと保存する。時差の無い値は UTC の時差を付ける |

- **接続のタイムゾーン**: 対象DB の接続は、読み取りと書き込みのどちらのプールも、接続を作るときに1回だけセッションのタイムゾーンを UTC にします（MySQL・MariaDB は `SET time_zone = '+00:00'`、PostgreSQL は `SET TIME ZONE 'UTC'`。`backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java` の `connectionInitSql`）。JVM の既定のタイムゾーン（`-Duser.timezone`）には頼らず、3種類の対象DB で同じ時点は同じ UTC の文字になります（`TargetDbTimeZoneIT` が JVM の既定のタイムゾーンを変えても値が変わらないことを確かめます）。初期化の文が失敗した接続は借りられず、ほかの借りる失敗と同じ扱いです（業務データの API では 503 `BUSINESS_TARGET_DB_UNAVAILABLE`。ログに接続先・ユーザー名・パスワードは出ません）。
- **秒の端数**: 応答の端数は、結果の列情報（DB の定義の桁）の桁ちょうどで出します（末尾の 0 を落とさない。例: `timestamptz(6)` は `.000000`。桁 0 の列は端数を付けない）。結果の列情報が桁を返さないときは、DB が送った桁のままです。
- **書き込みの端数の桁**: DSL に桁を明示した列（型の名前の `(n)` か `dbType.precision`）は、値の端数がその桁を超えると 400 `VALIDATION_FAILED`（`fieldErrors` の `reason` は `TOO_MANY_DIGITS`）で断ります（丸めない・切らない）。桁を明示していない列は確かめず、DB に任せます。
- **読めない値**: 型として読めない値（MySQL・MariaDB のゼロの日時 `0000-00-00 …`、`time` の 24 時間を超える値・負の値、PostgreSQL の `infinity` など）は、そのセルだけ DB が送った文字のまま返し、ほかの行・セルは普通に読みます（一覧全体を失敗させません）。読めない値の形を書き込み・検索・主キーの値として送ると、日時として読めない値として 400 `VALIDATION_FAILED` になり、保存しません。そのため、主キーの値が読めない行は、一覧には出ても詳細・更新・削除はできません。
- **範囲の外の値**: DB が日時の範囲の外として保存を拒んだとき（MySQL の `timestamp` の 2038 年の上限など）は、ほかの保存の拒否と同じ 409 `BUSINESS_SAVE_REJECTED` です。サーバーは上限の値を決め打ちして先に断りません（MariaDB は版によって `timestamp` の上限が違うため）。
- **夏時間**: 夏時間で存在しない時刻・2回ある時刻の判定は画面の側だけで行います。サーバーは時差つき（または UTC とみなした）の時点として受けます。
- **往復**: 読んだ値の文字をそのまま更新・検索・主キー・参照の値に送り返しても、対象DB の値と行の特定は変わりません（端数の桁も保つ）。
- **漏えい**: 日時の値（読めない値・桁を超えた値を含む）は、ほかの行の値と同じく、ログ・監査・トレースの属性・エラー応答に出しません（誤りは項目の名前と種類だけ）。

### 業務データの書き込みの API

適用中の DSL にあるテーブルの行の作成・更新・削除を、3本の道で行います。読み取りと同じく、スキーマ名・テーブル名・主キー・値・定義の識別（`definitionId`。定義の API が返したもの）は道や問い合わせに載せず、本文（JSON）で受け取ります。

| 道 | すること | 本文の上限 |
|---|---|---|
| `POST /api/tables/rows` | 作成（送ったカラムだけを入れる。自動の採番の主キーは作った値を返す）。201 | 4 MiB |
| `PATCH /api/tables/rows` | 更新（主キーで1行を指し、変えたカラムだけを入れる）。200 | 4 MiB |
| `POST /api/tables/rows/delete` | 削除（主キーで1行を指す。外部キーの `ON DELETE CASCADE` は DB に任せる）。200 | 64 KB |

- ログインだけが要ります（管理の権限は要りません）。作成はテーブルの補助権限 CREATE、削除は DELETE、更新は主権限 FULL のカラムが要り、要求ごとにサーバー側で判定します。入力できないカラム（主権限 FULL でない・詳細に出さない・主キーの更新）を本文に足すと 400 `VALIDATION_FAILED`（`NOT_ALLOWED`）になり、反映されません。
- 書き込みは書き込みの接続（書き込みのアカウント）だけを使い、1要求で1回だけ借ります。借りている間は内部DB の接続を持ちません。監査の記録は、書き込みの接続を返した後に内部DB へ書きます。
- 値は DSL の検証の材料（必須・長さ・桁・範囲・正規表現・選択肢・一意）でサーバー側で確かめます（定義の API の各カラムに同じ材料を返すため、画面と同じ判定になります）。値1つは 10,000 字、主キーの値は 1,000 字までです。DB 由来の必須・一意は、事前の問い合わせと DB の制約の違反のどちらでも同じ code で返します。
- 失敗の code: 読み取りの API と同じもの（403・404・409・400・503）に加えて、定義が古い・定義の識別が無いときは 409 `BUSINESS_DEFINITION_CHANGED`、書き込みの設定が無いときは 409 `BUSINESS_WRITE_NOT_CONFIGURED`、同じ値があるときは 409 `BUSINESS_DUPLICATE_VALUE`、参照先に無い値は 409 `BUSINESS_REFERENCE_NOT_FOUND`（作成・更新。どちらもカラムを `fieldErrors` で示す）、DB が保存を受け付けなかったとき（CHECK・NOT NULL・値の範囲・文字集合・削除の外部キーなど）は 409 `BUSINESS_SAVE_REJECTED`、主キーで0行は 404 `BUSINESS_ROW_NOT_FOUND`・2行以上は 409 `BUSINESS_ROW_NOT_UNIQUE`（巻き戻す）、本文が上限を超えると 413 `PAYLOAD_TOO_LARGE`（400 より先）です。
- 保存前の参照の確かめ: 作成・更新では、保存の前に DSL の REFERENCE・LOOKUP のカラムの値を参照先で確かめます（外部キーの無い参照先も確かめます。更新は更新前と値が変わった参照だけ）。参照先に無い値と、参照先を読めない値はどちらも同じ 409 `BUSINESS_REFERENCE_NOT_FOUND`（`fieldErrors` の `reason` は `REFERENCE_NOT_FOUND`）で、拒否した参照をすべて並べます。作成で、必須（NOT NULL）の参照項目の参照先を読めない・候補が 200 を超えるときは、値を送れば `REFERENCE_NOT_FOUND`、空なら `NOT_EDITABLE_NOT_NULL` の 409 `BUSINESS_REFERENCE_NOT_FOUND` で、`field` はそのカラムの名前です（DB の NOT NULL 違反の既存の `NOT_EDITABLE_NOT_NULL` は `field` が空のまま）。更新前の行が無ければ参照の拒否より先に 404 `BUSINESS_ROW_NOT_FOUND` です。保存前の確かめの 503（`BUSINESS_TARGET_DB_TIMEOUT`・`BUSINESS_TARGET_DB_UNAVAILABLE`）と 409 `BUSINESS_TARGET_DB_MISMATCH` は、どれも「保存されていない」です。
- 503 の出し分け: `BUSINESS_TARGET_DB_UNAVAILABLE` は「保存されていない」（借りられない・書き込みの文の段の時間切れ・断絶・競合（デッドロック・直列化の失敗・ロックの待ち切れ）・違反の後の確かめの段の時間切れ）、`BUSINESS_TARGET_DB_TIMEOUT` は「保存されたか分からない」（確定の段の失敗だけ）です。もう一度試す前に、一覧で行を確かめてください。
- 監査: 作成・更新・削除の成功（`BUSINESS_ROW_CREATED`・`BUSINESS_ROW_UPDATED`・`BUSINESS_ROW_DELETED`）と、対象DB に関わる失敗（同じ値・参照先に無い・保存の拒否・行が無い・2行以上・接続できない・時間切れ・食い違い、主キーとビューの決まりによる拒否は補助権限を持つ人の分だけ）を、操作した人・作業ロール・スキーマ・テーブル・主キー（`detail`。主キー以外の値は残さない）とともに残します。確定の段の失敗の結果は `UNKNOWN` です。401・403・400・定義が古い・設定が無いは残しません。監査の書き込みの失敗は操作を失敗させず、ERROR を1行出します（主キーの値・例外の原因は載せません）。
- 行の値・主キーの値・接続先・ドライバーの例外の文言は、応答にもログにもトレースの属性にも出しません（ログは段・理由・SQLState・業者のエラーの番号・例外のクラスの名前だけ）。

### 対象DB の結合テストとコンテナの実行環境

対象DB の結合テストは、版とダイジェストを固定したイメージ（`backend/src/test/java/cherry/mastersmith/targetdb/testsupport/TargetDbImages.java`）の MySQL 26.10.0・MariaDB 13.0.2・PostgreSQL 18.6 を Testcontainers で起動して行います（固定の正は `TargetDbImages.java`。`compose.yaml` と `docker/perf/compose.yaml` の対象DB のイメージも同じ版とダイジェストにそろえる）。`./gradlew verify` の中で3種類とも毎回実行します（CI も同じ）。

Testcontainers は Docker の context を読まないため、colima を使う PC では、Docker の接続先を環境変数で渡してください（シェルの設定ファイルに書いておくと便利です）。

```bash
export DOCKER_HOST="unix://${HOME}/.colima/default/docker.sock"
# 後片付けのコンテナ（Ryuk）が VM の中の Docker の接続口を使うため
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

- Docker に届かないとき、開発中（環境変数 `CI` が無い）は、対象DB のテストだけを中断（`SKIPPED`）にし、「コンテナの実行環境が無いため対象DB のテストを飛ばした。この状態では統合しない」という警告を出します。**この状態では統合しません。** `colima start` で起動し、上の環境変数を確かめてから `./gradlew verify` をやり直してください。
- CI（GitHub Actions は `CI=true` を設定する）では、Docker に届かなければ飛ばさずに失敗します。
- **MySQL・MariaDB の「つないだ後に止まる」テストの、ドライバーの版への依存**: `BusinessDataTimeoutIT` の中継（`StallingRelay`）は、通信が暗号化されていて文の文字が見えない MySQL・MariaDB では、「合図の後の大きなまとまりの何番目で止めるか」で止める段を指します。まとまりの分け方は JDBC ドライバーの版（MySQL の Connector/J、MariaDB Connector/J）で変わりえます。そのため、止めた後に一般ログ（`general_log`）で止める前に対象DB に届いた文を読み、想定の段か（MySQL の件数の問い合わせは直前に `SET sql_select_limit=1` だけが届く、MariaDB は何も届かない）を確かめます。ドライバーを上げて「止まった段が想定と違います」の失敗が出たら、失敗の文に出る届いた文と中継の診断を見て、`armAfterLargeChunks` に渡す数を直してください（確かめの部品は `StatementLog`・`StallStage`）。
- 単位のテストだけを実行するとき: `./gradlew :backend:integrationTest --tests 'cherry.mastersmith.targetdb.*'`（1種類だけなら `'cherry.mastersmith.targetdb.*Postgres*'` など）。

### 手元で試す対象DB（compose の profile）

画面からスキーマの読み込みを試すときや、読み取りの時間を測るときは、見本の対象DB を compose の profile で1つずつ起動します。ポートは PC に開けず、アプリのコンテナから compose の中の名前で接続します。

配備したアプリには、見本のうち PostgreSQL（`targetdb-postgres`）をつなぎ、アプリと一緒に起動・停止します（`README.md` の「コンテナでの起動と確認」の `docker compose --profile targetdb-postgres up -d --build` と `docker compose --profile targetdb-postgres down`）。下の手順 1 の `.env.targetdb` と手順 3 の `.env` の設定は、その配備でも同じです。手順 2・5 の見本の DB だけの起動・停止は、ほかの種類を試すときや、見本の DB だけを止める・作り直すときに使います。ほかの種類に切り替えるときは、手順 3 の値を替えてアプリのコンテナを作り直し、使わなくなった見本の DB は止めます。見本の DB が持つのは、見本のスキーマ・読み取りのアカウント・書き込みのアカウントと、アプリの書き込みの API で書いた見本の表の行です。手順 5 で消すと、書いた行は失われます（内部DB と監査ログは失われません）。

1. `.env.targetdb` に `MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD`・`MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD`・`MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD`を入れる（`(umask 077 && cp .env.targetdb.example .env.targetdb)` で作る。空だと DB を作れません。書き込みのパスワードが空だと、新しいボリュームの初期化が `03-writer-account.sh` で止まります）。見本の対象DB のコンテナは起動の入口で管理者のパスワードを各イメージの変数（`POSTGRES_PASSWORD`・`MYSQL_ROOT_PASSWORD`・`MARIADB_ROOT_PASSWORD`）に写すため、`docker compose exec` の中ではそれらの変数ではなく `MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD` を使います。
2. 起動する（初めての起動で、見本のスキーマ・読み取りだけのアカウント `mastersmith_reader`・書き込みのアカウント `mastersmith_writer`（見本のスキーマの行の操作だけ）を `docker/targetdb/<種類>/` の SQL と台本で作ります）。

   ```bash
   docker compose --profile targetdb-postgres up -d targetdb-postgres   # PostgreSQL（DB business・スキーマ sales）
   docker compose --profile targetdb-mysql up -d targetdb-mysql         # MySQL（DB・スキーマ business）
   docker compose --profile targetdb-mariadb up -d targetdb-mariadb     # MariaDB（DB・スキーマ business）
   ```

3. アプリに設定する（例: PostgreSQL）。`.env` に次を書き、`docker compose up -d app` でアプリのコンテナを作り直します。

   ```text
   MASTERSMITH_TARGET_DB_TYPE=postgresql
   MASTERSMITH_TARGET_DB_HOST=targetdb-postgres
   MASTERSMITH_TARGET_DB_PORT=5432
   MASTERSMITH_TARGET_DB_DATABASE=business
   MASTERSMITH_TARGET_DB_SCHEMA=sales
   MASTERSMITH_TARGET_DB_USERNAME=mastersmith_reader
   MASTERSMITH_TARGET_DB_PASSWORD=<MASTERSMITH_SAMPLE_TARGETDB_READER_PASSWORD と同じ値>
   MASTERSMITH_TARGET_DB_WRITE_USERNAME=mastersmith_writer
   MASTERSMITH_TARGET_DB_WRITE_PASSWORD=<MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD と同じ値>
   ```

   書き込みの2行を書かなければ、アプリは読み取りだけで動きます（書き込みの API は 409 `BUSINESS_WRITE_NOT_CONFIGURED`）。

   MySQL・MariaDB は `TYPE` を `mysql`・`mariadb`、`HOST` を `targetdb-mysql`・`targetdb-mariadb`、`PORT` を `3306`、`DATABASE` と `SCHEMA` を `business` にします。

4. 読み取りの時間を測るための、テーブル 100 個 × カラム 100 個のスキーマ `large` を作るとき（アプリの `SCHEMA`（MySQL・MariaDB は `DATABASE` も）を `large` にする）:

   ```bash
   ./docker/targetdb/generate-large-schema.sh postgres \
     | docker compose exec -T targetdb-postgres psql -v ON_ERROR_STOP=1 -U target_admin -d business
   ./docker/targetdb/generate-large-schema.sh mysql \
     | docker compose exec -T targetdb-mysql sh -c 'MYSQL_PWD="$MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD" mysql --user=root'
   ./docker/targetdb/generate-large-schema.sh mariadb \
     | docker compose exec -T targetdb-mariadb sh -c 'MYSQL_PWD="$MASTERSMITH_SAMPLE_TARGETDB_ADMIN_PASSWORD" mariadb --user=root'
   ```

5. 止める・消す: `docker compose --profile targetdb-postgres stop targetdb-postgres`。データごと消すときは `docker compose --profile targetdb-postgres rm -sf targetdb-postgres` の後に `docker volume rm mastersmith_mastersmith-targetdb-postgres`（mysql・mariadb も同じ形）。

#### 既にある見本の DB に書き込みのアカウントを足す

初期化の台本は、見本の DB のボリュームが無い状態で初めて起動したときだけ動きます。書き込みのアカウントが無いボリュームには、次の手順で足します（PostgreSQL の例。mysql・mariadb は名前を読み替えます）。台本は何度流しても同じ結果になり（無ければ作り、毎回パスワードと付与を当て直す）、パスワードを替えたときも同じ手順で当て直せます。どのコマンドも値を画面に出しません。

```bash
(umask 077 && cp .env "$HOME/.mastersmith-env-before-writer")                  # 戻すときのために .env をリポジトリの外へ複写する
W=$(openssl rand -hex 24)                                                      # 乱数を変数に入れる（表示しない）
(umask 077 && printf 'MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD=%s\n' "$W" >> .env.targetdb)
(umask 077 && printf 'MASTERSMITH_TARGET_DB_WRITE_USERNAME=mastersmith_writer\nMASTERSMITH_TARGET_DB_WRITE_PASSWORD=%s\n' "$W" >> .env)
unset W
docker compose --profile targetdb-postgres up -d --force-recreate targetdb-postgres   # 見本の DB のコンテナに新しい変数を渡す（データは残る）
docker compose --profile targetdb-postgres exec -T targetdb-postgres bash /docker-entrypoint-initdb.d/03-writer-account.sh
docker compose --profile targetdb-postgres exec -T targetdb-postgres psql -U target_admin -d business -tAc \
  "SELECT COUNT(*) FROM pg_roles WHERE rolname = 'mastersmith_writer'"          # 1 と出ること
docker compose --profile targetdb-postgres up -d --force-recreate app            # アプリに書き込みの設定を渡す
```

E2E（`./gradlew e2eTest`）も、見本の PG の DB `business` の PUBLIC に `TEMPORARY` が無いことを前提にします（`docs/verification.md` の「E2E の見本の PostgreSQL」の (c)）。書き込みのアカウントを足さずに取り上げだけを行うときは、台本の最後の文と同じ次の1文を流します（共有の見本の PG を変える操作です。何度流しても同じ結果になります）。

```bash
docker compose --profile targetdb-postgres exec -T targetdb-postgres psql -U target_admin -d business -v ON_ERROR_STOP=1 \
  -c 'REVOKE TEMPORARY ON DATABASE business FROM PUBLIC'
docker compose --profile targetdb-postgres exec -T targetdb-postgres psql -U target_admin -d business -tAc \
  "SELECT has_database_privilege('public','business','TEMP')"                  # f と出ること
```

- 確かめ: アプリの定義の API の `writeConfigured` が `true` になり、起動のログに書き込みの設定の WARN が無いこと。問題が無ければ、複写した `$HOME/.mastersmith-env-before-writer` を消します。戻すときは、その複写を `.env` に戻してアプリのコンテナを作り直します（書き込みのアカウントは残っても使われません）。
- 消し方: PostgreSQL は管理者で `REVOKE ALL ON ALL TABLES IN SCHEMA sales FROM mastersmith_writer; REVOKE ALL ON ALL SEQUENCES IN SCHEMA sales FROM mastersmith_writer; REVOKE USAGE ON SCHEMA sales FROM mastersmith_writer; ALTER DEFAULT PRIVILEGES IN SCHEMA sales REVOKE ALL ON TABLES FROM mastersmith_writer; ALTER DEFAULT PRIVILEGES IN SCHEMA sales REVOKE ALL ON SEQUENCES FROM mastersmith_writer; DROP ROLE mastersmith_writer;`、MySQL・MariaDB は `DROP USER 'mastersmith_writer'@'%';`。台本が PUBLIC から取り上げた PostgreSQL の DB の `TEMPORARY` は、戻す必要があるときだけ `GRANT TEMPORARY ON DATABASE business TO PUBLIC` で戻します。あわせて `.env` の書き込みの2行を消してアプリのコンテナを作り直します。
- 初期化が止まったとき（新しいボリュームで `03-writer-account.sh` が「…が空です」で終わる）: `.env.targetdb` に `MASTERSMITH_SAMPLE_TARGETDB_WRITER_PASSWORD` を入れ、手順 5 でボリュームごと消してから起動し直します（初期化が途中で止まったボリュームは使わない）。
- 残る広さ: 見本の付与はスキーマ（MySQL・MariaDB は DB）の単位のため、書き込みのアカウントは DSL に無い表も DB の上では書けます。アプリはサーバー側で、適用中の DSL にある表とカラムだけに書きます。利用者の DB で表ごとに絞るときは、DSL の表ごとに `GRANT SELECT, INSERT, UPDATE, DELETE ON <表>` を与え、外部キーの参照先の表にも `SELECT` を与えます（違反の後の確かめで参照先を読むため）。PostgreSQL 14 以前では、`public` スキーマの `CREATE` が PUBLIC に与えられているため、`REVOKE CREATE ON SCHEMA public FROM PUBLIC` も行います。
- 既知の制約: 一意の制約の無いカラムを DSL で unique にしたときは、同時の作成・更新の重なりを防げません（事前の問い合わせだけで見るため）。同じ行の同時の更新は後勝ちです。索引の無い unique のカラムの事前の問い合わせは、表の全部を読みます。大きな本文の同時の書き込みは、対象DB のメモリを使い切りうります（書き込みのプールの上限 5 で絞ります）。補助権限を持つ人が同じ失敗をくり返すと、監査の行が増えます。

メモリの上限は PostgreSQL・MariaDB が 512MB、MySQL が 768MB です。3つを同時に起動すると、colima の VM（6GiB）のうち約 2GB を使います。配備したアプリ（上限 2GB）と PostgreSQL の見本（512MB）を一緒に動かすと約 2.5GB です。
