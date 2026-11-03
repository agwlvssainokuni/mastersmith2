# セキュリティの設計 — u3-target-db-time

出典: セキュリティの要件（`construction/u3-target-db-time/nfr-requirements/security-requirements.md` の NFR1.4.1〜NFR1.4.6・NFR1.5.1・NFR1.5.2・NFR5.1.1・NFR5.2.1・NFR5.2.2）、技術の選択（同 `tech-stack-decisions.md`）、機能設計（`functional-design/functional-spec.md`・`rules.md` の BR3.1〜BR3.19）、質問と答え（`nfr-design-questions.md` の Q1: A）、捨ての試しの結果（同ファイル）、`team.md`・`project.md`。

U3 は、対象DB の日時・時刻の値の読み書き、接続のタイムゾーン、既定の DSL の生成の直し（種類 library。アプリに入る）。新しい API・表・画面・列・code は足さない。そのため、この設計は「接続の初期化を安全側に倒す」「読めない値・桁の値を黙って壊さず、外へ出さない」「書き込み・検索の値を信頼できない入力として扱う」「JVM の既定に頼らない」「依存とソースの決まりを守る」を、部品の中の検査の順序と置き場として決める。性能・拡張・信頼性・観測の独立した文書は、種類の決まりで作らない（性能の設計は `logical-components.md` の LC7 に置く）。

設計の決定には、追跡のため `SD<n>.<m>` の番号を付ける（この文書の中で定義する番号）。

## SD1. 接続の初期化（NFR1.4.5・NFR2.1.2・NFR5.2.2）

- **SD1.1（置き場と文）**: PostgreSQL の読み取りのプールと書き込みのプールの設定の両方に、接続を作るときに1回だけ流す初期化の文 `SET TIME ZONE 'UTC'`（定数）を置く。今は両方で、`product != POSTGRESQL` の条件が PostgreSQL を初期化の対象から外している。この条件を外し、種類ごとの初期化の文は「種類 → 文」の1か所の対応（LC1）から引く形にする。MySQL・MariaDB の文（`SET time_zone = '+00:00'`。書き込みは `sql_mode` の追加つき）は変えない。貸すたびには流さない。文は定数で、利用者の入力・設定の値を含まない。
- **SD1.2（初期化が失敗したら借りられない）**: 初期化の文が失敗した接続は、Hikari がプールに入れず、借りる側は `connectionTimeout`（既定 3 秒）の後に `SQLTransientConnectionException` を受ける。これは既存の分類（借りる段 → `UNAVAILABLE`）に入る。UTC でない接続で読み書きして時点がずれる状態は作らない。新しい code・状態コードは足さない。
- **SD1.3（失敗のログ）**: アプリは、借りる失敗の例外のメッセージも原因の文も、ログ・監査・トレースの属性・エラー応答に出さず、SQLState・例外のクラスの名前だけを出す（既存の決まり）。捨ての試しで、Hikari が警告以上で出すのは終了の待ちの警告だけで、初期化の失敗そのものは DEBUG だった。DEBUG のときも、出る文はサーバーの文（`invalid value for parameter "TimeZone"` など）とスタックトレースで、接続先・ユーザー名・パスワードは入らない。それでも、アプリは Hikari のロガーを DEBUG にしない（今の設定のまま）。
- **SD1.4（失敗の作り方。NFR Requirements の [assumption] を決める）**: 本番の設定を作る部品が返す Hikari の設定をテストで取り出し、`connectionInitSql` だけを必ず失敗する文に差し替えたプールを、テストの中で作って借りようとし、借りる失敗になること（分類が `UNAVAILABLE` であること）と、失敗の応答・アプリのログ・Hikari の DEBUG のログ（テストの中で水準を上げて取る）に、接続先・ユーザー名・パスワード・例外のメッセージが含まれないことを確かめる。本物の初期化の文は、別の結合テストで、読み取りと書き込みの両方の接続のセッションが UTC であること（`SHOW TimeZone`）と、同じ接続を続けて借りても初期化が1回だけであることを確かめる。失敗のために本番のコードに足す口は無い。
- **SD1.5（JVM の設定に頼らない）**: 接続のタイムゾーンは初期化の文で決め、JVM の `user.timezone` に預けない。実行時のタイムゾーンの設定（Dockerfile の `-Duser.timezone`・compose の `TZ`・テストの JVM・E2E の起動）の4か所は、この単位で変えない（BR3.19）。

## SD2. 読み取りの値（NFR1.4.1・NFR1.4.2・NFR1.4.4）

- **SD2.1（秒の端数の桁。Q2: A の具体化）**: 桁は、結果の列情報（`ResultSetMetaData`）だけで決め、新しい問い合わせは足さない。PostgreSQL・MariaDB は `getScale` がそのまま桁（0・3・6）を返す。MySQL は `getScale` が常に 0 を返すため、`getPrecision` から求める（`datetime`・`timestamp` は 19 なら 0 桁、そうでなければ値 − 20。`time` は 10 なら 0 桁、そうでなければ値 − 11）。この求め方は種類ごとの部品（LC2）に閉じ、service の層は種類で分岐しない。MariaDB のドライバーは `datetime(3)` の文字を 6 桁（`.123000`）で返すため、文字の長さからは桁を決めず、列情報の桁で整形する。ドライバーの版に頼る規則のため、桁 0・3・6 を3種類の対象DB の結合テストで固定し、版を上げたら再確認する。
- **SD2.2（読めない値。MySQL・MariaDB）**: 日時・日付・時刻は今までどおり文字で取り出し、解析する。ゼロの日時・ゼロの日付・24 時間を超える `time`・負の `time` は、`getString` で DB が送った文字のまま取れる（試しで確認）。解析できない文字は、読めなかったセルだけ DB が送った文字をそのまま返し（BR3.10）、ほかのセル・ほかの行・要求全体は失敗させない。読めない値の見分けに、例外を使う前に、ゼロの日時・日付を先頭の `0000-` で見分ける軽い確かめを置き、例外を投げない近道にする（費用の理由は LC7.2）。解析の例外は、型の名前だけを残して値を捨てる。
- **SD2.3（読めない値。PostgreSQL。Q1: A）**: PostgreSQL のドライバーは `infinity`・`24:00:00` を例外にせず、番兵の値か別の値で返す（試し: `infinity` の `timestamp` は最大の `LocalDateTime`、`-infinity` の `timestamptz` は最小の `OffsetDateTime`、`infinity` の `date` は最大の `LocalDate`、`time '24:00:00'` は端数が 999999999 ナノ秒の `23:59:59.999999999`）。そこで、型付きで読んだ値が番兵（`LocalDateTime`・`OffsetDateTime`・`LocalDate` の最大・最小、端数が 999999999 ナノ秒の `LocalTime`・`OffsetTime`）のときだけ、**同じセルを `getString` で読み直し**、読めないセル（DB が送った文字）として返す（LC4）。問い合わせも接続も増えない。番兵と取り違える正しい値は無い（PostgreSQL の `time` は最大 6 桁で、`timestamp`・`date` の最大・最小は表せる範囲の外）。型付きの読み取りは、桁の取り出し・時差つきの型の扱いも含めて今のまま。
- **SD2.4（セルだけが読めない形）**: 読めないセルは、応答の項目を増やさず、読めない値の文字だけを返す（契約 C4 の例外。機能設計の 5 節）。他の型のセルの扱いは変えない。文字の取り出し自体の失敗（`SQLException`）は、読み取りの失敗のまま（今までどおり）。
- **SD2.5（主キーが読めない行）**: 主キーの値が読めない行（BR3.12）の詳細・更新・削除は、行を特定せず、既存の「行を1件に決められない操作」の拒否にする。一覧にはその行を出す。拒否の後に状態が変わらない。

## SD3. 書き込み・検索の値（NFR1.4.3・NFR1.4.6）

- **SD3.1（読めない形は拒否）**: 書き込み・検索の日時・時刻の値は信頼できない入力として扱う。読めない形（ゼロの日時・24 時間を超える `time`・`infinity`・読めない文字）は、書き込みの文の前に 400 `VALIDATION_FAILED`（どの項目か）で拒否する。読んで返した読めない文字を送り返しても保存されない。新しい code・状態コードは足さず、値は応答・ログに含めない。
- **SD3.2（桁の超過は拒否）**: DSL に明示した桁（型の名前の `(n)` または `dbType.precision`）を超える端数は、400 `VALIDATION_FAILED` で拒否し、丸め・切りで黙って保存しない。DSL に桁が無い列は確かめない。確かめは DSL の値だけで行い、対象DB への問い合わせを足さない。桁ちょうどは受け付け、1桁超えると拒否する（境界）。
- **SD3.3（DSL の桁と DB の桁の食い違い。Q3: B）**: 読んだ値の桁は DB の実際の桁、書き込みの上限は DSL の桁で、出どころが違う。往復（読んだ値のまま更新しても変わらない。BR3.13）の保証は、DSL の桁が DB の桁以上のときだけ。DSL の桁が DB の桁より小さい列で、変えた欄の端数が DSL の桁を超えると 400 のまま（丸め・切りはしない）。変えていない欄は送らないため影響しない。DB の実際の桁を書き込みの前に問い合わせる・持ち回る仕組みは、問い合わせを増やさない決まりのため採らない。
- **SD3.4（`timestamp` の範囲外）**: 上限は製品と版で違う（試し: MySQL は 2038-01-19 03:14:08 をゼロの日時にし、MariaDB 13.0.2 は 2106 年まで保存できた）。サーバー側の検査で、上限を決め打ちしない。読み取りで範囲外・ゼロの値が来たら SD2.2 の読めないセルになる。書き込みで範囲外の値をサーバーが拒否したとき（書き込みのプールは `STRICT_ALL_TABLES` を付ける）の応答は、試しで確かめられていない（試しは `sql_mode` を空にして読み取りだけを見た）。**結合テストで、製品ごとの決めた側の動作（400 か既存の保存の拒否か、状態が変わらないこと）を明示してから固定する**。新しい code は足さない。
- **SD3.5（SQL の組み立て）**: 業務データの SQL は今までの決まり（識別子は適用中の DSL の名前と照らして引用し、値はバインド変数で渡す）のまま変えない。日時の値の読み書きの変更で、利用者の入力を SQL の文字に組み込む経路を足さない。`java.sql.Connection` の `prepareStatement`・`createStatement` を呼んでよい場所を広げず、既存の境界テスト（ArchUnit）と SpotBugs の `SQL_` の関門を緩めない。

## SD4. 漏えいの防止（NFR1.4.1）

- **SD4.1（型の `toString`）**: 日時・時刻の値を持つ型（読んだセルの値・読めないセルの文字・書き込みの束縛の値）は、`toString` で値を伏せる（TRACE の記録が各層の引数と戻り値を文字列にするため）。行を汎用の入れ物のまま層をまたいで渡さない（既存の決まり）。
- **SD4.2（例外とログ）**: JDBC の例外のメッセージ・解析の例外のメッセージは、エラー応答にも、アプリのログにも出さない（SQLState・ベンダーの番号・例外のクラスの名前だけ）。日時・時刻の値・読めない文字・主キーの日時の値は、ログ・監査・トレースの属性に出さない。読めない値は「値の型」だけを出す。
- **SD4.3（確かめ）**: TRACE を有効にした `XxxSecretLeakIT` を、読めない値・桁を超える値・主キーの日時の値を含めて通す。外へ送る設定を有効にした結合テストで、トレースの属性に値が出ないことを確かめる（業務データの道・属性の既存の決まりのまま）。

## SD5. JVM の既定に頼らない（NFR5.2.1）

- **SD5.1（時差と時計は明示）**: 日時の型の対応・読み書きの部品は、JVM の既定のタイムゾーンを暗に使わない。UTC は明示の定数（`ZoneOffset.UTC`）で使い、時計・時差が要る場所は注入する。
- **SD5.2（`user.timezone` を変えても不変）**: テストの JVM の `user.timezone` を `Asia/Tokyo` と別の値（`America/Los_Angeles`・`UTC`）にして同じ結果になることを、結合テスト・単体テストに残す（試しの K-95 の結果。PostgreSQL は初期化の後に値が一字も変わらなかった）。

## SD6. 依存とソース（NFR1.5.1・NFR1.5.2）

- **SD6.1（依存を足さない）**: JDBC ドライバー・Hikari の既存の機能と標準の `java.time` だけを使う。`backend/build.gradle.kts` と `backend/gradle.lockfile` は、この単位では変えない。足す場合は、採用の前にライセンスを確かめ、Apache License 2.0 と異なるものは採用の理由を設計の記録に残し、lockfile と OSV-Scanner の対象に入れる。
- **SD6.2（ソースの決まり）**: ソースの先頭に Apache License 2.0 のヘッダー（`/* ... */`、2026、`agwlvssainokuni`）を置き、`./gradlew verify`（Spotless・SpotBugs・ArchUnit）を通す。既存の境界テストと SpotBugs の除外は緩めず、足す形にする。日時の型の対応・種類ごとの差は repository の層に閉じ、service の層は種類で分岐しない。

## SD7. 確かめの設計

| ID | 確かめ | 対応する要件 | 方法 |
|---|---|---|---|
| SD7.1 | 初期化の後、PostgreSQL の読み取りと書き込みの両方の接続のセッションが UTC。同じ接続を続けて借りても初期化の文は1回 | NFR1.4.5・NFR2.1.2 | 結合テスト（PostgreSQL）。`SHOW TimeZone` と、初期化の回数の観察 |
| SD7.2 | 初期化を失敗する文に差し替えたプールで、借りる失敗（`UNAVAILABLE`）になり、応答・アプリのログ・Hikari の DEBUG のログに接続先・ユーザー名・パスワード・例外のメッセージが含まれない | NFR1.4.5・NFR1.4.1 | 結合テスト（SD1.4。本番のコードに口を足さない） |
| SD7.3 | MySQL・MariaDB・PostgreSQL の日時・時刻の型（`datetime`・`timestamp`・`date`・`time`・`timestamptz`・`timetz` ほか）を、一覧・詳細・作成・更新（と検索・主キー・参照の値の往復）で確かめる。桁 0・3・6 が読み・書き・往復で落ちない・丸められない。日付の変わり目・月末・年末・うるう日を含める | NFR5.1.1 | 結合テスト（3種類の対象DB を `./gradlew verify` の中で毎回。実行ごとに一意のスキーマ・データベース。書いた後の値は別の接続で読み直す） |
| SD7.4 | 桁の取り出し: PostgreSQL・MariaDB は `getScale`、MySQL は `getPrecision` から。0・3・6 を型ごとに固定 | NFR5.1.1 | 結合テスト（桁を決める部品の単体テストと、3種類の実物） |
| SD7.5 | 読めない値: MySQL・MariaDB のゼロの日時・ゼロの日付・24 時間を超える `time`・負の `time`、PostgreSQL の `infinity`・`-infinity`（`timestamp`・`timestamptz`・`date`）・`time '24:00:00'` が、読めなかったセルだけ DB が送った文字で返り、ほかのセル・要求は失敗しない。応答の項目は増えない | NFR1.4.2 | 結合テスト（3種類の対象DB。PostgreSQL は番兵 → `getString` の読み直しを固定。`+infinity` の `timestamptz` と `timetz` の端数 999999999 も含める） |
| SD7.6 | 書き込み・検索: 読めない形・桁の超過は 400（桁ちょうどは受け付け、1桁超は拒否）、拒否の後に対象DB の値が変わらない。DSL の桁が DB の桁より小さい列で、変えた欄が 400・変えていない欄は影響しない。読めない主キーの行の詳細・更新・削除が拒否され、状態が変わらない | NFR1.4.3・NFR1.4.4 | 結合テストと、jqwik の性質ベース（任意の文字列で例外が外へ出ず、読めない文字は拒否される。失敗時の乱数の種を記録） |
| SD7.7 | `timestamp` の範囲外: 製品ごとに、読み取り（読めないセル）と書き込み（決めた側の動作）を明示して固定する | NFR1.4.2・NFR5.1.1 | 結合テスト（MySQL・MariaDB。SD3.4） |
| SD7.8 | 漏えい: TRACE の `XxxSecretLeakIT` と、外へ送る設定を有効にした結合テストで、日時・時刻の値・読めない文字・主キーの日時の値・接続情報・例外のメッセージが、ログ・監査・トレースの属性・エラー応答に出ない | NFR1.4.1 | 結合テスト |
| SD7.9 | `user.timezone` を別の値にしても、読み書きの値が変わらない。時差を持たない型は、接続の時差の前後で値が変わらない（性質ベース） | NFR5.2.1・NFR5.1.1 | 結合テスト・単体テスト・jqwik |
| SD7.10 | SQL の決まり: `'`・`"`・`;`・`--`・改行を含む日時の検索の値が SQL として解釈されない。既存の境界テストと SpotBugs の関門が変更なしで通る | NFR1.4.6 | 結合テストと `./gradlew verify` |
| SD7.11 | 新しい依存が増えていない。実行時のタイムゾーンの設定の4か所が変わっていない | NFR1.5.1・NFR5.2.2 | コード生成の差分で確かめる |

## 脅威の見立ての更新（U3 の範囲）

| 脅威 | 対応する設計 |
|---|---|
| 読めない値・桁を超える値・主キーの日時の値がログ・トレース・応答に漏れる | SD4.1〜SD4.3・SD2.2〜SD2.4 |
| PostgreSQL の `infinity`・`24:00:00` が黙って別の値に化ける（読んだ値を信じて保存すると業務データを壊す） | SD2.3（番兵のときだけ文字を読み直す）・SD3.1（読めない文字は書き込みで拒否） |
| 読めない値を送り返して、壊れた値を保存する・行を取り違える | SD3.1・SD2.5 |
| 接続のタイムゾーンが UTC でないまま読み書きして、時点がずれる | SD1.1・SD1.2・SD5.2 |
| 初期化の失敗のログから接続情報・例外の文が漏れる | SD1.3・SD1.4・SD7.2 |
| 日時の検索の値による SQL の組み込み | SD3.5・SD7.10 |
| 桁の違いによる黙った値の食い違い | SD3.2・SD3.3・SD2.1 |
| `timestamp` の範囲外の値の黙った保存・取り違え | SD3.4・SD7.7 |

## Sources

- Initial description: N6（業務データの日時のタイムゾーンの変換）と画面の仕上げの Intent [desc]
- Workflow-selected scope: classic [scope]
- セキュリティの要件: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/nfr-requirements/security-requirements.md`
- 技術の選択: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/nfr-requirements/tech-stack-decisions.md`
- 機能設計: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/functional-design/functional-spec.md`・`rules.md`
- 質問と答え・捨ての試しの結果: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u3-target-db-time/nfr-design/nfr-design-questions.md`（Q1: A）[Q1]
- 前の単位の書き方（参考）: `aidlc/spaces/default/intents/261102-timezone-ui-polish/construction/u1-time-zone-conversion/nfr-design/security-design.md`

## Assumptions & Open Questions

- [assumption] PostgreSQL の番兵の値（最大・最小、端数 999999999 ナノ秒の `time`）は、正しい値としては現れない。pgjdbc 42.7.14 の振る舞いに頼り、`+infinity` の `timestamptz` と `timetz` の端数は試しで確かめていない。SD7.5 の結合テストで固定する。
- [assumption] MySQL の `getPrecision` から桁を求める規則は Connector/J 9.7.0 と MySQL 26.10.0 の組で確かめた。版を上げたら SD7.4 で再確認する。
- 未確認: 書き込みのプール（`STRICT_ALL_TABLES`）での `timestamp` 範囲外の拒否の動作（SD3.4。結合テストで決める）。アプリの本物のログの設定の下での Hikari の DEBUG のログ（SD1.4 のテストで確かめる）。MariaDB で `getObject` を使う経路（今は文字で読むため使わない）。
- 未解決の点は上の確認のほか無い。
