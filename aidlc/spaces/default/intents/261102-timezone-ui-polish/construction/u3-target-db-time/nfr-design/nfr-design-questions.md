# NFR Design の質問（U3 u3-target-db-time）

U3 は、業務データの日時を対象DB から読み書きする部分と、接続のタイムゾーンの直し（種類は library。アプリに入る）です。新しい API・表・画面は足しません。この段で設計するのは、セキュリティ（読めない値・漏えい・接続の初期化の失敗）と、論理の部品の分け方・性能（変換の費用）に絞ります。性能・拡張・信頼性・観測の独立した文書は、種類の決まりで作りません（性能の設計は `logical-components.md` に置きます）。

## 決まっていること（質問にしない）

- **接続の初期化の文**（NFR Requirements の Q1: A）: PostgreSQL の読み取りのプールと書き込みのプールに、接続を作るときに1回だけ `SET TIME ZONE 'UTC'`（定数）を置く。今は両方のプールで PostgreSQL だけ初期化の文が無いため（`TargetDataSourceConfig` と `TargetWriteDataSourceConfig` の `if (product != POSTGRESQL)`）、この条件を外して PostgreSQL にも付ける。MySQL・MariaDB の `SET time_zone = '+00:00'` は変えない。
- **秒の端数の桁**（Q2: A）: 結果の列情報（`ResultSetMetaData`）だけで決め、新しい問い合わせは足さない。MySQL では `getScale` が 0 を返すため `getPrecision` から求める（下の試しの結果）。この求め方は、ドライバーの版（Connector/J 9.7.0）の振る舞いに頼るため、0・3・6 桁を3種類の対象DB の結合テストで固定する。
- **書き込みの端数の上限**（Q3: B）・**変換の費用の目標**（Q4: B。1万のセルで 500 ミリ秒以内・平均 0.05 ミリ秒以内、新しい依存は足さない）は、NFR Requirements のとおり。
- **接続の初期化が失敗したときの結合テストの作り方**（NFR Requirements の [assumption] を、この段で決める）: 本番の設定を作る部品が返す Hikari の設定を、テストで取り出し、初期化の文だけを必ず失敗する文に差し替えたプールを作って、借りようとして失敗することを確かめる。本物の初期化の文は、別の結合テストで、読み取りと書き込みの両方の接続のセッションが UTC であることを確かめる。失敗の作り方のために、本番のコードに足す口は無い。
- **読み取りで例外にならない「読めない値」の見分け方**は、質問にせず方針にする: 型付きの読み取りで例外が出る場合（MySQL・MariaDB のゼロの日時）は、今までどおり例外を読めないセルに写す。例外が出ない場合は、下の Q1 で決める。

## 捨ての試しの結果（リポジトリの外。設計の前提の確かめ。BR3.18・ADR-009）

使い捨ての PostgreSQL 18.6・MySQL 26.10.0・MariaDB 13.0.2（`compose.yaml` が固定している版のイメージ。終わったら削除。見本の対象DB・既存のデータは使っていない）を、ドライバー pgjdbc 42.7.14・Connector/J 9.7.0・MariaDB Connector/J 3.5.10（アプリの lockfile と同じ版）、HikariCP 7.0.2、JDK 25.0.5 で確かめました。

- **K-95（PostgreSQL の接続の時差）**: pgjdbc は接続の開始時に、JVM の `user.timezone` をそのままセッションの `TimeZone` として送ります（初期化の文なしで `Asia/Tokyo` なら `SHOW TimeZone` は `Asia/Tokyo`）。`SET TIME ZONE 'UTC'` を接続の初期化に置くと、`user.timezone` を `Asia/Tokyo` と `America/New_York` にしても、`SHOW TimeZone` は `UTC` で、`timestamp`・`timestamptz`・`time`・`timetz`（桁 0・3・6）の書き込み・読み取りの結果が、一字も変わりませんでした。初期化が無いときは、型付きの読み取り（`getObject` の `LocalDateTime`・`OffsetDateTime`・`LocalTime`・`OffsetTime`）の値は変わらず、`timestamptz` を文字で読んだ値（`getString`）だけが JVM の時差（`+09`・`-05`）で変わりました。つまり前提（初期化の文が開始時の指定を上書きする）は成り立ち、今のコードが型付きで読むため、初期化の文は「文字で読む経路と将来の変更でも値がずれない」ための守りです。置き場は NFR Requirements の Q1: A のまま、切り替え（開始時の指定）は要りません。
- **K-98（結果の列情報の桁）**: PostgreSQL と MariaDB は、`getScale` が桁（0・3・6）を返しました（PostgreSQL の `timestamp(n)`・`timestamptz(n)`・`time(n)`・`timetz(n)`、MariaDB の `datetime(n)`・`timestamp(n)`・`time(n)`）。**MySQL は `getScale` が全部 0 を返します**が、`getPrecision` に桁が入っており（`datetime`・`timestamp` は 19・23・26、`time` は 10・14・17。桁 0・3・6 に対応）、桁を求められます。桁を得られない型は、3種類の DB とも無く、機能設計に戻す点はありません。MariaDB のドライバーは `datetime(3)` の文字を `.123000`（6桁）で返すため、文字の長さからは桁を決めず、列情報の桁で整形します。
- **読めない値**: MySQL・MariaDB は、ゼロの日時（`0000-00-00 00:00:00`）・ゼロの日付・24 時間を超える `time`（`838:59:59`）・負の `time`（`-01:02:03.500`）を、`getString` で DB が送った文字のまま取り出せました（今の読み取りが日時を文字で読んでから解析するため、解析の例外が出て読めないセルになる）。範囲の外の `timestamp`（2038-01-19 03:14:08）は、`sql_mode` を空にして試したところ、MySQL は読めない値（`0000-00-00 00:00:00`）として保存し、MariaDB 13.0.2 は 2106 年まで保存できました（`timestamp` の上限は DB の製品と版で違います。書き込みのプールは `STRICT_ALL_TABLES` を付けるため、範囲の外はサーバーが拒否します）。**PostgreSQL は例外にならず、別の値が返ります**: `infinity`・`-infinity` を `getObject` で読むと、`LocalDateTime` は `+999999999-12-31T23:59:59.999999999`、`OffsetDateTime` は `-999999999-01-01T00:00+18:00` のような番兵の値になり、`getString` なら DB が送った `infinity` です。`time '24:00:00'`（PostgreSQL では正しい値）は `getObject` の `LocalTime` が `23:59:59.999999999` になり、**黙って別の値になります**（`getString` は `24:00:00`）。この2点が、前提が崩れた点です（Q1）。
- **変換の費用**: 文字から `LocalDateTime`・`LocalTime`・`OffsetTime` を作り、桁つきの形式で整形する最小の実装で、1万のセル（4種類を等分、桁 0・3・6、日時の 5% は読めない文字）は、暖機の後で約 5〜6 ミリ秒（1つあたり約 0.0006 ミリ秒）、読めない文字を先頭の `0000-` で見分けて例外を避ける形でも約 5 ミリ秒でした。暖機の前（1回目の1万個）でも約 53 ミリ秒、段階の低いコンパイル（`-XX:TieredStopAtLevel=1`）でも約 14 ミリ秒でした。いずれも目標（500 ミリ秒・平均 0.05 ミリ秒）の約 10 分の 1 未満で、読めないセルがあっても費用の差は小さい（例外の費用は目標に響かない）。JDBC の読み取りの時間は含みません（JDK 25.0.5、手元の Mac）。
- **接続の初期化の失敗**（PostgreSQL の読み取りで、初期化の文を失敗する文にして試した）: `getConnection` は、`connectionTimeout` が過ぎてから `SQLTransientConnectionException`（`Connection is not available, request timed out after …ms`）で失敗し、原因の `ConnectionSetupException` の文は初期化の失敗の内容（PostgreSQL の `ERROR: invalid value for parameter "TimeZone"` など、サーバーの文）です。メッセージに接続先・ユーザー名・パスワードは入りませんでした。Hikari が警告以上で出すのは、終了の待ちの警告だけで、失敗そのものは **DEBUG** です。DEBUG にすると、原因のスタックトレースが出ます（文は同じで、資格情報は入らない）。借りる失敗は、既存の分類（`RowSqlFailureClassifier` の借りる段 → `UNAVAILABLE`）に入ります。初期化の失敗は、再び借りるたびに待ちの時間がかかり、アプリのログには何も残らない点は、MySQL・MariaDB の既存の初期化と同じです（既存の警報 `ms-biz-target-db` が 503 で拾います）。新しい code は要りません。

試しで前提が崩れた点は、PostgreSQL の `infinity`・`24:00:00` が例外にならないことの1つだけです（K-95・K-98 は設計を変えません）。次を質問にします。

## Q1. [開発] PostgreSQL の `infinity`・`24:00:00` を、読めないセルとして DB が送った文字で返す方法

文脈: 機能設計の BR3.10 は、読めない日時・時刻（`infinity` を含む）を、読めなかったセルだけ「DB が送った文字」で返し、要求全体は失敗させないとしています。ところが PostgreSQL のドライバーは、`infinity` と `24:00:00` を例外にせず、番兵の値（`+999999999-12-31…`）か別の値（`23:59:59.999999999`）として返すため、今のままだと画面に壊れた値が出て、`24:00:00` は黙って別の値に化けます（試しの結果）。この値を読んで、そのまま更新に送ることは、BR3.11（読めない値は書き込みで拒否）が止めます。読み方を決める必要があります。

**推奨: A。** 理由: 型付きの読み取りを今のまま使い（読み取りの経路が変わらず、桁の取り出し・時差つきの型の扱いも今のまま）、番兵の値のときだけ同じセルを `getString` で読み直すので、問い合わせも接続も増えません。`time` の番兵は、PostgreSQL の `time` が最大 6 桁のため、9 桁の端数（`nano = 999999999`）が正しい値としては現れず、取り違えません。`timestamp`・`timestamptz`・`date` の番兵（`MAX`・`MIN`）も同様に、正しい値と区別できます。読み直しは番兵のときだけなので、費用はかかりません。B は PostgreSQL の文字の形（`BC`・`infinity`・時差の付け方）を自前で全部解析し、確かめる形が増えます。C は壊れた値が画面に出るため、BR3.10 に反します。

- A. 型付きで読み、番兵の値（`infinity`・`-infinity` を表す `MAX`・`MIN`、端数が 999999999 ナノ秒の `time`・`timetz`）のときだけ、同じセルを `getString` で読み直して「読めないセル」（DB が送った文字）として返す。
- B. PostgreSQL の日時・時刻を最初からすべて `getString` で読み、MySQL・MariaDB と同じ形（文字を解析）にそろえる。読み方は1通りになるが、PostgreSQL の文字の形の解析を自前で作る。
- C. 番兵の値をそのまま整形して返す（`infinity` は壊れた値の文字、`24:00:00` は `23:59:59.999999999` のまま）。BR3.10 を PostgreSQL では取り下げる形になる。
- X. Other (please specify)

[Answer]: A

## 設計の要点（これから成果物に書くこと）

`security-design.md`（セキュリティの設計。NFR1.4.x を、部品とテストに割り付ける）、`logical-components.md`（論理の部品・性能の設計・依存）、`traceability.json` を作ります。内容の要点は次のとおりです。

- **接続のタイムゾーン**: PostgreSQL の読み取り・書き込みの両プールに定数の初期化の文を付け、MySQL・MariaDB と同じ置き場にそろえる。UTC でないセッションで読み書きする状態は作らない（初期化が失敗した接続は借りられない）。初期化の文は利用者の入力を含まない。JVM の設定（4か所）は変えない。
- **読み取りの部品**（`RowDialect` の DB の種類ごとのクラスと `ColumnValueCodec`。repository の層の1か所）: 桁は結果の列情報（MySQL は `getPrecision` から導出、PostgreSQL・MariaDB は `getScale`）、読めないセルは DB が送った文字で、ほかのセルと要求は失敗させない。読めない値の見分けに例外を使う場合も使わない場合も、値はログ・トレース・エラー応答に出さず、値の型だけを出す。日時・時刻の値を持つ型は `toString` で値を伏せる。例外のメッセージは外へ出さず、SQLState・クラスの名前だけ。
- **書き込み・検索の入力**: 読めない形と、DSL に明示した桁を超える端数は、書き込みの文の前に、DSL の値だけで確かめて 400 `VALIDATION_FAILED`（問い合わせを足さない）。SQL は今までの決まり（識別子は DSL の名前と照らして引用、値はバインド変数）のまま、`prepareStatement`・`createStatement` を呼ぶ場所を広げない。
- **性能の設計**: 変換は結果の1つのセルから値を作る小さな部品にし、整形の書式は桁ごとに使い回す（0・3・6 桁の3つ）。新しい問い合わせは足さない。試しの値（1万のセルで約 5〜6 ミリ秒）は目標の約 10 分の 1 未満で、`NFR2.1.3`・`NFR2.1.4` の確かめは単体テスト（暖機の後と、暖機の前の1回目の両方を記録）に置く。接続の数は `*ConnectionUsageIT` と同じ形で、変更の前後で同じことを確かめる。
- **確かめ（恒常のテストに残す）**: `user.timezone` を別の値にして同じ結果になること（K-95）、0・3・6 桁（K-98）、読めない値（MySQL・MariaDB のゼロの日時・`time` の 24 時間超と負、PostgreSQL の `infinity`・`24:00:00`）、`timestamp` の範囲の外（製品と版で上限が違うため、決めた側の動作を明示）、初期化の失敗が借りる失敗になり資格情報・例外のメッセージが応答とログに出ないこと、TRACE の `XxxSecretLeakIT`。

## 確かめられなかったこと

- アプリの本物の Spring Boot の設定（ログの水準の既定）の下での Hikari の失敗のログは、単体の実行（`slf4j-simple`）でしか見ていません。アプリの `logback` の設定で Hikari を DEBUG にした場合の出力は、結合テストで確かめます（NFR1.4.5）。
- MySQL・MariaDB の `timestamp` の厳密な上限の拒否（`STRICT_ALL_TABLES` の書き込みのプール）は、試しでは `sql_mode` を空にして読み取りの動作だけを見ました。拒否の動作は結合テストで確かめます。
- MariaDB の読み取りで `getObject` を使う経路は試していません（今は文字で読む）。
- 見本の PostgreSQL（`127.0.0.1:15432`）は、資格情報を読まず、使いませんでした（使い捨てのコンテナで足りたため）。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/functional-design/functional-spec.md`・`rules.md`（BR3.1〜BR3.19）
- NFR Requirements: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`・`nfr-requirements-questions.md`（Q1: A・Q2: A・Q3: B・Q4: B）
- 前の単位の NFR Design（書き方の参考）: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u1-time-zone-conversion/nfr-design/`
- 既存のコード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java`・`TargetWriteDataSourceConfig.java`・`backend/src/main/java/cherry/mastersmith/businessdata/repository/ColumnValueCodec.java`・`PostgresRowDialect.java`・`MysqlFamilyRowDialect.java`
- 捨ての試し: リポジトリの外（コミットしない）。pgjdbc 42.7.14・MySQL Connector/J 9.7.0・MariaDB Connector/J 3.5.10・HikariCP 7.0.2・JDK 25.0.5・PostgreSQL 18.6・MySQL 26.10.0・MariaDB 13.0.2 [Q1]

## Assumptions & Open Questions

- [assumption] PostgreSQL の番兵の値（`MAX`・`MIN`、端数 999999999 ナノ秒の `time`）は、正しい値としては現れない（`time` は最大 6 桁、`timestamp`・`date` の `MAX`・`MIN` は表せる範囲の外）という見立て。pgjdbc の版（42.7.14）の振る舞いに頼るため、結合テストで固定する（[Q1]）。
- [assumption] MySQL の `getPrecision` から桁を求める規則（`datetime`・`timestamp` は 19 なら 0 桁、そうでなければ値 − 20、`time` は 10 なら 0 桁、そうでなければ値 − 11）は、Connector/J 9.7.0 と MySQL 26.10.0 の組で確かめた。版を上げたら、0・3・6 桁の結合テストで再確認する。
- 未解決の点: Q1（PostgreSQL の読めない値の読み方）。
