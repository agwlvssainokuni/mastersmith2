# 論理の部品 — u3-target-db-time

出典: 機能設計（`construction/u3-target-db-time/functional-design/functional-spec.md`・`rules.md`・`entities.md`）、NFR Requirements（`nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`）、`security-design.md`（同じ段）、質問と答え（`nfr-design-questions.md` の Q1: A）、既存のコード（`targetdb/config` の `TargetDataSourceConfig`・`TargetWriteDataSourceConfig`、`businessdata/repository` の `ColumnValueCodec`・`RowDialect` と種類ごとのクラス）。

U3 は、対象DB の日時・時刻の読み書きと接続のタイムゾーンの直し（種類 library）。新しい API・表・画面は足さず、既存の部品の中の置き場を決める。この文書は、論理の部品の一覧・部品どうしの依存・失敗の及ぶ範囲・共有する資源と、性能の設計（種類 library は独立した性能の設計の文書を作らないため LC7 に置く）を決める。実装のファイル名・関数名は Code Generation で決める（ここの部品の名前は論理の名前）。

設計の決定の番号は `LC<n>`（この文書の中で定義する番号）。

## LC1〜LC6. 部品の一覧

| ID | 論理の部品 | 置き場（層） | 責務 | 持つ状態 |
|---|---|---|---|---|
| LC1 | 接続の初期化の対応 | `targetdb.config`（既存の2つの設定の部品の中） | 種類（MySQL・MariaDB・PostgreSQL）と用途（読み取り・書き込み）から、接続を作るときに1回だけ流す初期化の文（定数）を引く。PostgreSQL は `SET TIME ZONE 'UTC'`（読み取りと書き込みで同じ）。今の `if (product != POSTGRESQL)` の条件を外し、3種類とも引く | なし（定数） |
| LC2 | 秒の端数の桁の決定 | `businessdata.repository`（種類ごとの方言のクラス） | 結果の列情報から、日時・時刻のセルの桁（0・3・6）を決める。PostgreSQL・MariaDB は `getScale`、MySQL は `getPrecision` から導出（`security-design.md` の SD2.1）。取れない型は無い（試し）が、取れないときの扱いは決めず、結合テストで見つかったら設計に戻す（Q2: A） | なし |
| LC3 | 日時・時刻のセルの読み取り | `businessdata.repository`（`ColumnValueCodec` の日時・時刻の分岐） | セルを文字にする。MySQL・MariaDB は文字で取り出して解析し、先頭 `0000-` のゼロの日時・日付は例外を投げずに読めないセルにする。解析の例外は読めないセル（型の名前だけ残す）。PostgreSQL は型付きで読み、LC4 に番兵の確かめを任せる。結果は「文字」か「読めないセル（DB が送った文字）」で、要求全体は失敗させない | なし |
| LC4 | 番兵の確かめ（PostgreSQL） | `businessdata.repository`（PostgreSQL の方言のクラス） | 型付きで読んだ値が番兵（`LocalDateTime`・`OffsetDateTime`・`LocalDate` の最大・最小、端数が 999999999 ナノ秒の `LocalTime`・`OffsetTime`）のときだけ、同じセルを `getString` で読み直して読めないセルにする（Q1: A）。ほかの値は何もしない | なし |
| LC5 | 整形の書式 | `businessdata.repository`（`ColumnValueCodec` の中の定数） | 日時・時差つきの日時・時刻・時差つきの時刻の整形の書式を、桁（0・3・6）ごとに1つずつ用意し、使い回す。時差つきの日時・時刻の出力は UTC。端数は桁ちょうどで落とさない・丸めない | 書式の定数（変わらない） |
| LC6 | 書き込み・検索の入力の確かめ | `businessdata.repository`（`ColumnValueCodec` の読み取りの逆の方向）と service の層の入力の確かめ | 読めない形（ゼロの日時・24 時間を超える `time`・`infinity`・読めない文字）と、DSL に明示した桁を超える端数を、書き込みの文の前に DSL の値だけで確かめ、400 `VALIDATION_FAILED`（項目の名前）にする。問い合わせを足さない。時差つきの時刻は時差ごと保存し、時差の無い値は UTC とみなす（BR3.5・BR3.9・BR3.11・BR3.14） | なし |

依存（text の図）:

- 初期化の対応（LC1）← 読み取りのプールと書き込みのプールの設定（`targetdb.config`）。ほかの部品に依存しない。
- 読み取り（LC3）→ 桁の決定（LC2）・整形の書式（LC5）。PostgreSQL の方言の LC3 → 番兵の確かめ（LC4）。
- 書き込み・検索の確かめ（LC6）→ 整形の書式（LC5）・DSL の桁（既存の DSL の部品）。
- service の層は種類で分岐しない。種類ごとの差（LC1 の文・LC2 の桁・LC4 の番兵）は、`targetdb.config` と repository の層の方言のクラスに閉じる。既存の境界テストの許可の一覧は広げない。

## LC7. 性能の設計（NFR2.1.1〜NFR2.1.5）

| ID | パターン | 設計 | 根拠 |
|---|---|---|---|
| LC7.1 | 書式の使い回し | 整形の書式は、桁 0・3・6 の3つを種類ごとにモジュールの定数として1回だけ作り、変換のたびに作らない。1つのセルの変換は、取り出し → 解析 → 整形の1回だけ | 試し: 1万のセル（4種類を等分、桁 0・3・6）で約 5〜6 ミリ秒（1つあたり約 0.0006 ミリ秒）。暖機前の1回目で約 53 ミリ秒、段階の低いコンパイルでも約 14 ミリ秒で、目標（500 ミリ秒・平均 0.05 ミリ秒）の約 10 分の 1 未満 |
| LC7.2 | 読めない値の見分けは例外に頼らない | MySQL・MariaDB のゼロの日時・日付は、先頭 `0000-` の確かめで例外を使わずに読めないセルにする。ほかの解析の失敗だけを例外で受ける | 試し: 日時の 5% が読めないとき、例外で受ける形が約 5.6 ミリ秒、確かめで避ける形が約 4.7 ミリ秒。差は小さいが、ページ全体が読めない値のとき（最悪）も目標内に収まるよう、近道を置く。全部が読めない場合の値は Code Generation の単体テストで測って記録する（この段では未測定） |
| LC7.3 | 問い合わせを増やさない | 桁は読む文の結果から取る。番兵の読み直しは同じ結果の行の同じセルを読むだけ。接続の初期化は接続を作るときに1回だけ | NFR2.1.1・NFR2.1.2。`*ConnectionUsageIT` と同じ形で、接続の数・問い合わせの数が変更の前後で同じことを決定的に確かめる |
| LC7.4 | 既存の目標を変えない | 業務データの読み取りの p95 などの既存の目標は変えない。新しい k6 の場面・perf の種は足さない。Build and Test で、既存の業務データの読み取りの場面を直前直後で並べて記録する | NFR2.1.5・`team.md`（物差しを変えるのは要件定義で依頼者が決めたときだけ） |

**測りの再現（Code Generation と Build and Test）**: NFR2.1.3・NFR2.1.4 は、単体テストで、結果の1つのセルから値を作る部品（コンテナ・ネットワークを含めない）を、固定の値 1 万個（時点・時差を持たない日時・日付を除く時刻・時差つきの時刻・端数 0・3・6 桁・読めないセルを混ぜる）に通して測る。暖機の後と、暖機の前の1回目の両方を記録し、測った環境（JDK の版・CPU）を成果物に書く。試しの値は、書式を使い回した最小の実装の値で、本番の実装（桁の決定・番兵の確かめ・入力の検査を足す）では増えるが、目標に対する余裕は約 10 倍ある。外れたときは、目標を緩めず、原因を確かめて依頼者に諮る（`team.md`）。

## LC8. 失敗の領域と、及ぶ範囲（爆発半径）

| ID | 失敗 | 及ぶ範囲 | 守り |
|---|---|---|---|
| LC8.1 | 接続の初期化の失敗（PostgreSQL の読み取り・書き込み） | そのプールを使う業務データの要求（借りる失敗 → 503 `UNAVAILABLE`。借りるたびに `connectionTimeout` の待ち）。内部DB・ほかの機能には及ばない | 初期化に失敗した接続は借りられず、UTC でない接続では読み書きしない（SD1.2）。既存の警報（業務データの 503）で拾う。アプリのログには残らず、再び借りるたびに待ちが出る点は、MySQL・MariaDB の既存の初期化と同じ |
| LC8.2 | 読めない値・桁の取り違え | その1つのセルだけ（ほかのセル・ほかの行・要求は普通に読む） | 読めないセルは DB が送った文字で返す（SD2.2・SD2.3）。書き込みでは読めない形を拒否し、壊れた値を保存しない（SD3.1） |
| LC8.3 | PostgreSQL の番兵の取り違え（正しい値を読めない値にする・読めない値を通す） | 該当するセルだけ | 番兵は表せる範囲の外・端数 999999999 ナノ秒で、正しい値と区別できる（SD2.3）。結合テストで `infinity`・`24:00:00`・番兵に近い正しい値を固定する（SD7.5） |
| LC8.4 | 書式・桁のずれによる値の食い違い | 日時・時刻の列を読む画面と、読んだ値を送り返す更新 | 桁 0・3・6 の読み・書き・往復を3種類の実物で確かめる（SD7.3・SD7.4）。DSL の桁が DB の桁より小さいときは 400 のまま（SD3.3） |
| LC8.5 | `timestamp` の範囲外 | 該当する列・行だけ | 上限を決め打ちせず、製品ごとの動作を結合テストで明示する（SD3.4・SD7.7） |

U3 は、状態・新しい外部の依存・ネットワークを持たず、可用性・拡張・復旧の設計は要らない（失敗は1つの要求の中で完結し、ほかの要求に伝わらない。初期化の失敗はプール単位で、既存の借りる失敗の扱いに乗る）。

## LC9. 共有する資源

- 共有する資源は、整形の書式の定数（変わらない。LC5）と、対象DB の読み取りと書き込みの2つのプール（既存。プールの上限・接続の待ちは変えない）だけ。新しい共有の状態・キャッシュは足さない。
- 対象DB のタイムゾーンは接続ごとのセッションの設定で、プールの接続はどれも初期化の文で UTC に固定される（LC1）。接続を貸すたびには変えないため、共有の競り合いは無い。
- ドライバーの振る舞い（MySQL の桁の返し方・PostgreSQL の番兵・MariaDB の文字の桁）は版ごとに違いうる。pgjdbc 42.7.14・Connector/J 9.7.0・MariaDB 3.5.10 で確かめ、版を上げたときは結合テストで再確認する。

## NFR のパターンの当てはめ

| NFR | パターン | 当てはめる部品 |
|---|---|---|
| NFR1.4.1 | 値を外へ出さない（`toString` の伏せ字・例外のメッセージを出さない） | LC3・LC4・LC6（SD4） |
| NFR1.4.2 | 読めないセルは DB が送った文字・要求は失敗させない | LC3・LC4（SD2.2〜SD2.4） |
| NFR1.4.3 | 書き込みの値を信頼できない入力として拒否 | LC6（SD3.1〜SD3.3） |
| NFR1.4.4 | 読めない主キーの行は行を特定しない | LC3・既存の「行を1件に決められない操作」の拒否（SD2.5） |
| NFR1.4.5 | 初期化の失敗は借りる失敗 | LC1（SD1） |
| NFR1.4.6 | SQL の組み立てを変えない | 既存の SQL の部品（SD3.5） |
| NFR2.1.x | 書式の使い回し・問い合わせを増やさない | LC1・LC2・LC3・LC5（LC7） |
| NFR5.1.1 | 日時の必須のテスト | 全部品（SD7） |
| NFR5.2.x | JVM の既定に頼らない・設定の4か所を変えない | LC1・LC5（SD5・SD1.5） |
| NFR1.5.x | 依存を足さない・ソースの決まり | 全部品（SD6） |

## 承認済みの記録との差（承認済みの文書は書き換えず、ここに記録する）

| ID | 差 | 理由 | 決定 |
|---|---|---|---|
| D1 | 機能設計 BR3.10 は、読めない日時・時刻（`infinity` を含む）を DB が送った文字で返すとするが、PostgreSQL のドライバーは `infinity`・`24:00:00` を例外にしない（番兵の値・別の値で返る）。この段で、型付きで読んだ値が番兵のときだけ、同じセルを `getString` で読み直す仕組みを足す。`time '24:00:00'`（PostgreSQL では正しい値）も、読めないセル（文字 `24:00:00`）として返し、書き込みでは拒否される（BR3.11） | 捨ての試しで、番兵の値が壊れた値として画面に出る、`24:00:00` が黙って `23:59:59.999999999` になることが分かった | Q1: A |
| D2 | NFR Requirements の Q2: A は桁を「結果の列情報だけ」で決めるとしたが、MySQL は `getScale` が常に 0 のため、同じ列情報の `getPrecision` から導出する。PostgreSQL・MariaDB は `getScale` | 捨ての試しで、MySQL Connector/J 9.7.0 の `getScale` が全部 0、`getPrecision` に桁が入っていることが分かった。列情報だけで決める方針は保たれる。桁を得られない型は無く、設計に戻す点は無い | 質問にせず決定（Q2: A の範囲内の具体化） |
| D3 | `security-requirements.md` の脅威の見立ては「PostgreSQL は JVM の既定を送る見込み。ずれた書き込みは業務データの誤りになる」としていたが、試しでは、型付きの読み書き（`LocalDateTime`・`OffsetDateTime`・`LocalTime`・`OffsetTime`）は初期化が無くても値が変わらず、変わるのは `timestamptz` を文字で読んだ値だけだった。初期化の文は、文字で読む経路と将来の変更でも値がずれないための守りとして置く | 捨ての試し（K-95）。置き場・文は NFR Requirements の Q1: A のまま。開始時の指定への切り替えは不要 | 質問にせず決定 |
| D4 | `security-requirements.md` の [assumption]（初期化の失敗の作り方は NFR 設計で決める）を、本番の設定を作る部品が返す Hikari の設定の `connectionInitSql` だけを失敗する文に差し替える形に決める（SD1.4）。NFR1.4.5 の「失敗のログに例外のメッセージを出さない」は、アプリのログについては既存の決まり（SQLState・クラスの名前だけ）、Hikari の DEBUG のログについては、サーバーの文は出るが資格情報は入らないことを試しで確かめ、テストで固定する | 捨ての試し（Hikari 7.0.2）。Hikari は警告以上では失敗を出さず、DEBUG で文とスタックトレースを出す | 質問にせず決定 |
| D5 | `tech-stack-decisions.md` の「桁を結果の列情報だけで決める」「MariaDB の `timestamp` の上限は版によって違う」に加え、MariaDB のドライバーは `datetime(3)` の文字を 6 桁で返すため、文字の長さではなく列情報の桁で整形する。`timestamp` の範囲外の書き込みの拒否の動作は、この段では未確認で、結合テストで決めた側の動作を明示する（SD3.4） | 捨ての試し。試しは `sql_mode` を空にして読み取りだけ | 質問にせず決定 |

## 捨ての試しの結果（要点。詳細は `nfr-design-questions.md`）

- 使い捨ての PostgreSQL 18.6・MySQL 26.10.0・MariaDB 13.0.2、pgjdbc 42.7.14・Connector/J 9.7.0・MariaDB 3.5.10・HikariCP 7.0.2、JDK 25.0.5。見本の対象DB は使っていない。試しのコードはリポジトリの外に置き、コミットしていない。
- K-95: 初期化の文で、`user.timezone` を変えても PostgreSQL の4種類の型（桁 0・3・6）の結果が変わらない。初期化が無いと `timestamptz` の文字だけが変わる。
- K-98: PostgreSQL・MariaDB は `getScale`、MySQL は `getPrecision` から桁が取れる。取れない型は無い。
- 読めない値: MySQL・MariaDB は `getString` で取れる。PostgreSQL の `infinity`・`24:00:00` は例外にならず、番兵・別の値になる（D1）。
- 変換の費用: 1万のセルで約 5〜6 ミリ秒（暖機前の1回目で約 53 ミリ秒）。
- 初期化の失敗: 借りる失敗（`SQLTransientConnectionException`）になり、資格情報は含まれない。Hikari は DEBUG でだけ文とスタックトレースを出す。

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/functional-design/functional-spec.md`・`rules.md`・`entities.md`
- NFR Requirements: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/nfr-requirements/security-requirements.md`・`tech-stack-decisions.md`
- セキュリティの設計（同じ段）: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/nfr-design/security-design.md`
- 質問と答え・捨ての試しの結果: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/nfr-design/nfr-design-questions.md`（Q1: A）[Q1]
- 既存のコード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDataSourceConfig.java`・`TargetWriteDataSourceConfig.java`、`backend/src/main/java/cherry/mastersmith/businessdata/repository/ColumnValueCodec.java`・`PostgresRowDialect.java`・`MysqlFamilyRowDialect.java`

## Assumptions & Open Questions

- [assumption] 部品の置き場（LC1〜LC6 を既存のどのクラスの中に置くか）は論理の名前で、実際のクラス・メソッドの分け方は Code Generation の計画で決める。既存の境界テストを緩めない前提。
- [assumption] ページ全体が読めない値（最悪）でも 1 万のセルが 500 ミリ秒以内に収まるという見立ては、5% の混在の試しからの見通しで、この段では最悪の場合を測っていない。Code Generation の単体テストで測って記録する（LC7.2）。
- 未確認: 書き込みのプールでの `timestamp` の範囲外の拒否の動作・アプリの本物のログの設定の下での Hikari の DEBUG のログ・MariaDB で `getObject` を使う経路（SD の未確認の節と同じ）。
- 未解決の点は上の確認のほか無い。
