# 信頼性の設計 — U1 u1-reference-read

参照先の読み取りの時間の上限・失敗の分け方・失敗の後の扱い・互換を決め、境界の結合テストの一覧を置く。

## 出典

- この単位の NFR 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/reliability-requirements.md`（NFR3.4〜NFR3.7・NFR6.1・NFR7.1〜NFR7.3）、`scalability-requirements.md`（NFR2.6・NFR2.8）、`security-requirements.md`（NFR1.10・NFR1.11）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/`（`rules.md` の BR1.5・BR8.1・BR8.4・BR8.5・BR9.5・BR12）
- 契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C2・C5）
- この段の答え `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/nfr-design-questions.md`（Q1: A・Q2: A・F1: A）と試しの結果 `trial-results.md`
- コード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbProperties.java`・`TargetDbSettings.java`・`TargetDataSourceConfig.java`、`targetdb/service/TargetReadAccess.java`・`JdbcTargetReadAccess.java`、`targetdb/domain/TargetReadSession.java`、`businessdata/repository/RowSqlFailureClassifier.java`、`businessdata/domain/RowQueryFailure.java`、`businessdata/service/BusinessDataReadService.java`、`backend/src/test/java/cherry/mastersmith/businessdata/BusinessDataBoundaryArchitectureTest.java`、`backend/src/main/resources/application.yaml`、`.env.example`
- U2 の NFR 設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-design/reliability-design.md`（1.2・上流との差）・`observability-design.md`（2.1）・`logical-components.md`（上流との差）。U2 のレビューの R-01 で求められた継ぎ目（5節）

## 1. 時間の上限

### 1.1 問い合わせ1回の上限（NFR3.4）

- `ReadPurpose` に `REFERENCE` を足し、`TargetDbProperties.QueryTimeout` に `reference`（既定 5 秒）を足す。`application.yaml` は `reference: ${MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE:5s}`、`.env.example` に名前だけ（値は空）を足す。
- `longest()` の比べる対象に `reference` を入れる。既定では最長は `generate` の 20 秒のままで、読み取りのプールの通信の待ち（`longest()` ＋ 5 秒）は変わらない。`reference` を 20 秒より長くしたときに、通信の待ちが問い合わせの上限より先に切れないようにするため。
- `TargetDbSettings.addTimingProblems` に `reference` の点検を足す。1 秒未満（0・負の値・`500ms` など）は、既存の `generate`・`compare`・`list`・`detail` と同じく点検の誤り（項目名 `mastersmith.target-db.query-timeout.reference` を `QUERY_TIMEOUT_REFERENCE` として足す）にし、`Invalid` で対象DB を使わない（項目名だけを WARN に出し、起動は続く。BR1.3 の既存の扱い）。`TargetReadSession` は秒が 1 未満だと `IllegalArgumentException` を投げ、秒への切り捨てで上限が 0 になるため、読み取りのたびの 500 にしないよう起動時の点検で止める（レビュー R-02）。
- `TargetDbSettings.Usable.queryTimeout(ReadPurpose)` の `switch` に `case REFERENCE -> properties.queryTimeout().reference()` を足す（足さないと列挙を尽くさずコンパイルできない）。
- `JdbcTargetReadAccess.withRead` が受け付ける目的に `REFERENCE` を足す（今は `LIST`・`DETAIL` だけを受け付け、ほかは拒否する）。`TargetReadAccess` の説明文の目的の一覧も合わせる。

### 1.2 定義の取得の全体の上限（NFR3.5、Q2: A）

- 設定にする。置き場は `TargetDbProperties` に足す `referenceReadTotal`（キー `mastersmith.target-db.reference-read-total`、環境変数 `MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL`、既定 10 秒）。対象DB の読み方の設定を1か所にまとめるため `targetdb.config` に置き、`QueryTimeout` には入れない（`QueryTimeout` は文1回の上限で、`longest()` の計算に混ざらないようにする）。
- 値は 1 秒以上に限る。1 秒未満（0・負の値・`500ms` などを含む）は、`TargetDbSettings.addTimingProblems` の点検の誤り（項目名 `mastersmith.target-db.reference-read-total` を `REFERENCE_READ_TOTAL` として足す）にし、`Invalid` で対象DB を使わない。項目名だけを WARN に出し（値は出さない）、起動は続ける（レビュー R-01）。既存の対象DB の設定は、範囲外の値でも起動を止めず点検で対象DB を使わない扱い（`TargetDbProperties` の注記、BR1.3）で、`connectTimeout`・各 `queryTimeout`・プールの値と同じにする。起動を止めると、1つの環境変数の打ち間違いでログインを含むアプリ全体が上がらなくなるため。`Duration` として読めない値（`abc` など）が結び付けで起動を止めるのは既存の時間の設定と同じで、変えない。
- 点検を通ったときだけ使う値のため、点検を通らないときにすべての定義の取得が流さずに 503 になる経路は無い（`Invalid` では業務データの読み取りそのものが 503 `BUSINESS_TARGET_DB_UNAVAILABLE` で、既存の扱い）。
- `businessdata` は `targetdb.config` に依存できない（`BusinessDataBoundaryArchitectureTest` の `allowedDependencies` は `targetdb.service`・`targetdb.domain` だけを許す）。そのため値は `TargetDbSettings.Usable.referenceReadTotal()` を足し、`targetdb.service` の口 `TargetReadAccess.referenceReadTotal()`（`Duration` を返す）で `businessdata` に渡す。境界テストは緩めない。
- `application.yaml` と `.env.example` に、既定の値と下の 1.3 の最悪の値の注記を書く。

### 1.3 締め切りの作り方と最悪の値

締め切りは `businessdata.domain` の純粋な値 `ReferenceReadDeadline` に閉じる（5節。U2 も同じ値を使う）。

```text
withRead(REFERENCE, session -> {                        // session の上限は REFERENCE の上限（既定 5 秒）
  deadline = ReferenceReadDeadline.start(clock, targetReadAccess.referenceReadTotal())
                                                        // 最初の選択肢を読む前に1回だけ（借りる待ちは含めない）
  各問い合わせの前:
    narrowed = deadline.narrow(session)                 // Optional<TargetReadSession>
      // 中では remaining = deadline - clock.instant()
      //        seconds   = ceil(remaining を秒で)       // 1.2 秒 → 2 秒
      //        remaining < 1 秒 → Optional.empty()
      //        else new TargetReadSession(product, connection,
      //               min(session.queryTimeoutSeconds(), seconds), 同じ brokenMark)
    if narrowed が空 → 流さず定義の取得ごと 503 BUSINESS_TARGET_DB_TIMEOUT（budgetExceeded）
    else narrowed の session で1文を流す
})
```

- 時計は注入した `Clock`（`team.md`）。`TargetReadSession` は変えず、既存の公開の4引数のコンストラクターで作る（BR8.4）。文1回の上限（REFERENCE の上限）は `withRead` が渡す session の `queryTimeoutSeconds` から取り、`ReferenceReadDeadline` は `targetdb.config` を読まない。試しで、同じ形の record の作り直しは 7〜22 ns、同じ接続で文ごとに上限を変えると3種類のドライバーで効くと確かめた（`trial-results.md` 2.3）。
- 試しで、秒の単位の上限は3種類で誤差 30 ms 未満で切れた（PostgreSQL 1,020〜3,021 ms、MySQL 1,021〜3,027 ms、MariaDB 1,005〜3,009 ms。どのドライバーも上限を無視しない）。
- 最悪の値（上限の値の横に書く）: 全体の上限 10 秒に対し、切り上げのため最後の文が締め切りを最大 1 秒未満超えうるため、選択肢の読み取りだけで約 11 秒（＋ 文の上限の誤差 30 ms 前後）。これに、前に権限の写しの読み取りと接続を借りる待ち（上限 3 秒）が乗る。この時間切れ・借りられない経路（503）は NFR2.1 の k6 の p95 の判定の対象の外とし、結合テストで確かめる。
- ピッカーの検索・ラベルの1件読みは文が1つだけのため、全体の上限は使わず、`REFERENCE` の上限（5 秒）だけを付ける。

## 2. 失敗の分け方と失敗の後

| 失敗 | 試しで見た形 | 分け方（既存の `RowSqlFailureClassifier` のまま） | 応答 |
|---|---|---|---|
| 問い合わせの時間切れ | PostgreSQL `PSQLException` `57014`、MySQL `MySQLTimeoutException`、MariaDB `SQLTimeoutException` `70100`/1969 | `TIMEOUT` | 503 `BUSINESS_TARGET_DB_TIMEOUT` |
| 全体の上限（残り 1 秒未満） | — | `TIMEOUT`（流さない） | 同じ |
| 接続の失敗・借りられない・読み取りの設定が無い | SQLState `08` 始まりなど | `UNAVAILABLE` | 503 `BUSINESS_TARGET_DB_UNAVAILABLE` |
| DSL と対象DB の型の食い違い | 既存の `MISMATCH_STATES` | `MISMATCH` | 409 `BUSINESS_TARGET_DB_MISMATCH` |
| 想定外 | 上のどれでもない | `UNEXPECTED` | 既存の 500 |

- 新しい code を作らず、分け方も変えない（NFR6.1）。
- MySQL の 1038（`HY001`、Out of sort memory）は今の分け方では `UNEXPECTED`（500）になる。この単位では並びの項を先頭の 100 字で作って起こさない形にし（`performance-design.md` 2節、F1: A）、分け方は変えない。
- 失敗の後は同じ接続で続けて読まない。時間切れ・断絶では壊れた印を立て、残りの項目を読まずに定義の取得ごと失敗にする（NFR3.6）。試しでは時間切れの後も接続は使えたが、ドライバーと DB の組で後始末が違うため、既存の決まりどおり捨てる。
- `check`・`countState` は `FAILED(TIMEOUT | UNAVAILABLE | MISMATCH)` を返し、写しは U2 が持つ（BR9.5）。`FAILED` は理由に加えて、メッセージを捨てた失敗の値 `ReferenceReadFailure`（`sqlState`・`vendorCode`・`exceptionClass`・`budgetExceeded`。5節）を持つ。U2 が診断の WARN の1行にこれらを出すため（U2 のレビュー R-01）。例外のメッセージ・行の値は持たない。

## 3. 互換（NFR7.1〜NFR7.3）

- 移行ファイルと DSL の書式・JSON Schema は変えない。
- 定義の要求の `purpose` は任意で既定 `VIEW`。`VIEW` では参照先を読まず、応答は `reference: null` を足すだけで今と同じ（BR12.1）。U3 が入るまで、今の画面は変えずに動く。
- 一覧・詳細は参照先を読まない。`BusinessDataConnectionUsageIT` の一覧・詳細の数は変わらない。
- 戻しは前の版のイメージだけで済む（移行と保存が無い）。新しい設定 `MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE`・`MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL` は前の版では読まれないだけ。

## 4. 境界と失敗の結合テストの一覧

3種類の対象DB（版を固定したイメージの Testcontainers）で `./gradlew verify` の中で流す。表はテストのクラスごとに一意の名前のスキーマ（MySQL・MariaDB はデータベース）に作り、終わったら消す（`team.md`）。

| ID | テスト | 期待 | 要件 |
|---|---|---|---|
| T-B1 | 選択肢: 参照先 199・200・201・10,000 行 | 200 まで `AVAILABLE`、201 以上で `TOO_MANY`、取り出した行は 201 を超えない。文の `LIMIT` と `setMaxRows` が 201 | NFR2.6 |
| T-B2 | 選択肢: 10,000 字以下の行がちょうど 200 行と、10,001 字の行・2,000,000 字の行が混ざる参照先 | `AVAILABLE`（200 件）。長い行は入らず `TOO_MANY` にならない（U1 の NFR 要件のレビュー R-05） | NFR2.6・NFR2.8 |
| T-B3 | 選択肢: 10,000 字以下の行がちょうど 201 行と、長い値の行が混ざる参照先 | `TOO_MANY`（長い行を数えずに 201 行目がある） | NFR2.6・NFR2.8 |
| T-B4 | `countState`: T-B2・T-B3 と同じ参照先 | それぞれ `AVAILABLE`・`TOO_MANY` | NFR2.6・NFR2.8 |
| T-B5 | ピッカー: 条件に当たる 10,000 字以下の行が 20・21・40・41 行で、長い値の行が混ざる | 21 行目があるときだけ `hasNext` が真。長い行は `rows` に入らず、次のページの判定にも数えない。最後のページより後は 0 行・偽 | NFR2.6・NFR2.8 |
| T-B6 | 値の長さの境界: 10,000 字・10,001 字（半角・日本語・補助面の文字）と 2,000,000 字 | 10,000 字は選択肢・ピッカーに入り `check` で `FOUND`。10,001 字以上は外れ、`check` で `NOT_FOUND_OR_DENIED`。取り出した値は 10,001 字を超えない | NFR2.8・NFR1.9 |
| T-B7 | MariaDB の PAD SPACE: `x` と、`x`＋空白 9,999 個・10,000 個の行に `x` を送る | `check` は `FOUND`（`x` の行）。空白つきの行は DB の比べ方では当たるが、厳密な一致（BR9.4）と長さの条件で決めた結果になる。3種類で同じ結果 | NFR1.11 |
| T-B8 | 並び: ラベルの先頭 100 字が同じで 101 字目から違う行と、値の先頭 100 字が同じ行 | 先頭 100 字が同じ行は主キーの順（F1: A）。3種類で同じ | NFR1.11（F1: A） |
| T-B9 | MySQL の 1038 の再現の形: 2,000 行の参照先の 200 行に約 10,000 字のラベル（と値）を入れ、選択肢・ピッカー・ラベルの1件読み・`check`・`countState` を流す | どれも 500 にならず、決めた結果を返す（3種類で流す。試しの形） | NFR1.10・NFR1.11（F1: A） |
| T-B10 | ラベルの境界: 100・101 字 | 100 字で切られ、切った印が無い | NFR2.7 |
| T-T1 | 時間切れ: 応答しない対象DB（テストの中の `ServerSocket`）で定義・検索・ラベル | 503 `BUSINESS_TARGET_DB_TIMEOUT`。応答とログに例外のメッセージが無い | NFR3.4・NFR1.6 |
| T-T2 | 全体の上限: 注入した時計で残り 0.5 秒・1.0 秒・2.3 秒（`ReferenceReadDeadline.narrow` の単体テスト。jqwik で、絞った上限が 1 以上で session の上限を超えない性質も確かめる） | 空（流さず 503）／上限 1 秒／上限 min(5, 3) = 3 秒。どれも同じ接続と壊れた印を持つ | NFR3.5 |
| T-T3 | REFERENCE が3つで2つ目の途中で締め切りに達する | 3つ目を読まず 503、接続は壊れたものとして返る | NFR3.5・NFR3.6 |
| T-T4 | 設定: `reference` の既定 5 秒・環境変数で変えた値・`longest()` に入る・`Usable.queryTimeout(REFERENCE)` が `reference` を返す。`reference` が 1 秒未満（0・`500ms`）。`referenceReadTotal` の既定 10 秒・変えた値・1 秒未満（0・負・`500ms`）、ちょうど 1 秒 | 単体テスト（`TargetDbSettings.inspect`）。1 秒未満は `Invalid` で、項目名 `query-timeout.reference`・`reference-read-total` が問題の一覧に入り、起動（設定の結び付けとコンテキストの起動）は止まらない。WARN は項目名だけで値を出さない。ちょうど 1 秒は `Usable` | NFR3.4・NFR3.5 |
| T-F1 | 失敗の種類ごと（時間切れ・接続の失敗・型の食い違い）の結合テストと、分ける関数の単体テスト | 2節の表のとおり | NFR3.7 |
| T-C1 | `purpose` を送らない定義の応答が、`reference: null` を除いて今と同じ。既存の定義の API のテストを変えずに通す | 同じ | NFR7.2 |
| T-C2 | 参照の項目を持つ表の一覧・詳細で、参照先への問い合わせが流れない | 文の数が今と同じ | NFR7.3 |
| T-C3 | `BusinessDataProblemTypeCatalog` の code の数と状態コードの組が変わらない | 同じ | NFR6.1 |
| T-S1 | `ReferenceReadFailure` の作り方: `RowQueryFailure` の各理由から作る・`budgetExceeded()`・必須の値の欠け | `TIMEOUT`・`UNAVAILABLE`・`MISMATCH`（`READ_CONVERSION` は `MISMATCH`）だけを作り、`INVALID_VALUE`・`UNEXPECTED` は作らない。`budgetExceeded()` は `TIMEOUT`・`budgetExceeded=true`・`exceptionClass` なし。`toString` と項目に例外のメッセージ・行の値が無い（単体テスト） | NFR3.7・NFR1.15 |

## 5. U2 から受けた継ぎ目

U2 の NFR 設計のレビュー R-01 で、U2 が ⑩ の中で U1 と同じ締め切りを使い（更新前の行の `detail` もその締め切りで絞る）、`check`・`countState` の `FAILED` から診断の WARN の値を出せるよう、次の2つを U1 の側で受けた。名前と置き場はここで決め、U2 はこの名前で使う。置き場はどちらも `businessdata.domain` で、依存する相手は `targetdb.domain`（`TargetReadSession`）と `java..` だけのため、`BusinessDataBoundaryArchitectureTest` の `allowedDependencies`（`businessdata` → `targetdb.domain` は許されている）と `noTransactionsOnTheReadSide` を緩めない。どちらも record のため `TraceAspect` の対象の外（`!within(java.lang.Record+)`）で、値も持たない。

### 5.1 締め切り `ReferenceReadDeadline`

| 項目 | 決めたこと |
|---|---|
| 型 | `public record ReferenceReadDeadline(Clock clock, Instant deadline)`（`cherry.mastersmith.businessdata.domain`）。`ReferenceLookup` の中に閉じず、`businessdata` のどこからでも呼べる純粋な値 |
| 作り方 | `public static ReferenceReadDeadline start(Clock clock, Duration total)`。`deadline = clock.instant() + total`。`total` は `TargetReadAccess.referenceReadTotal()` の値（点検で 1 秒以上と決まっている） |
| U2 が呼ぶメソッド | `public Optional<TargetReadSession> narrow(TargetReadSession session)`。残り（`deadline - clock.instant()`）が 1 秒未満なら空。そうでなければ、残りを秒に切り上げた値と `session.queryTimeoutSeconds()` の小さい方を上限にし、同じ `product`・`connection`・`brokenMark` で作った `TargetReadSession`（既存の公開の4引数のコンストラクター）を返す |
| 定数 | `public static final Duration MIN_REMAINING = Duration.ofSeconds(1)` |
| 使い方 | 呼ぶ側は `withRead(REFERENCE, …)` の中で、最初の読み取りの前に1回だけ `start` し、各文の前に `narrow` する。空なら流さず `ReferenceReadFailure.budgetExceeded()` の失敗にする |
| 値の受け取り | `businessdata` は `targetdb.config` に依存できないため、全体の上限は `targetdb.service` の口 `Duration TargetReadAccess.referenceReadTotal()`（実装は `JdbcTargetReadAccess` が `TargetDbSettings.Usable.referenceReadTotal()` を返す）で受ける。テストの差し替えの `TargetReadAccess` の実装も合わせる |

### 5.2 `FAILED` の失敗の値 `ReferenceReadFailure`

既存の型に、メッセージを持たない失敗の値として `RowQueryFailure`（`businessdata.domain`。`reason`・`stage`・`sqlState`・`vendorCode`・`exceptionClass`）と `TargetWriteFailure`（`targetdb.domain`）がある。`RowQueryFailure` はそのまま使わない。理由は、(1) `exceptionClass` が必須で、全体の上限で流さずに止めたとき（例外が無い）を表せない、(2) `reason` が `INVALID_VALUE`・`READ_CONVERSION`・`UNEXPECTED` を含み、U2 の写しが3つの理由で尽くせない、(3) `stage`（`BIND`・`EXECUTE`・`READ`）は U2 の WARN の `stage`（`BORROW`・`PREVIOUS_ROW`・`COUNT_STATE`・`CHECK`）と別のもので、紛らわしい。そのため新しい record を足し、`RowQueryFailure` から作る。

| 項目 | 決めたこと |
|---|---|
| 理由の型 | `public enum ReferenceFailureReason { TIMEOUT, UNAVAILABLE, MISMATCH }`（`businessdata.domain`。機能設計 `entities.md` の `failureReason` の3つの値） |
| 失敗の値の型 | `public record ReferenceReadFailure(ReferenceFailureReason reason, String sqlState, Integer vendorCode, String exceptionClass, boolean budgetExceeded)`（`businessdata.domain`） |
| 項目 | `reason`（必須）、`sqlState`（無ければ null）、`vendorCode`（無ければ null）、`exceptionClass`（例外のクラスの名前。`budgetExceeded` が偽のとき必須、真のとき null）、`budgetExceeded`（全体の上限で流さずに止めたときだけ真。そのとき `reason` は `TIMEOUT`）。例外のメッセージ・原因・行の値・識別子の名前は持たない |
| 作り方 | `public static ReferenceReadFailure of(RowQueryFailure failure)`（`TIMEOUT`→`TIMEOUT`、`UNAVAILABLE`→`UNAVAILABLE`、`MISMATCH`・`READ_CONVERSION`→`MISMATCH`。`INVALID_VALUE`・`UNEXPECTED` は `FAILED` にせず `IllegalArgumentException`。呼ぶ側の `ReferenceLookup` は、既存の読み取りと同じくこの2つを想定外（500、`BusinessDataUnexpectedException`）として扱う。`check`・`countState` の値は型つきで渡るため、`INVALID_VALUE` は想定外）と `public static ReferenceReadFailure budgetExceeded()` |
| `FAILED` の形 | `ReferenceCheckResult.Failed(ReferenceReadFailure failure)`・`ReferenceCountState.Failed(ReferenceReadFailure failure)`。理由は `failure.reason()` で読む（機能設計の `FAILED(理由)` の理由を、失敗の値の中に持たせる形） |
| U1 の定義の取得 | `ReferenceLookup` の WARN の1行（`observability-design.md` 2節）も同じ値から出す |

## 上流との差

| 上流 | 差 | 扱い |
|---|---|---|
| 機能設計 BR4.2・BR5.8 | 文字の型のラベルと値は先頭の 100 字で並べる（F1: A） | `performance-design.md` 2節、T-B8・T-B9 で確かめる |
| NFR 要件 NFR3.5「上限を定数か設定かは NFR 設計で決める」 | 設定 `MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL`（既定 10 秒、1 秒以上）にした（Q2: A）。1 秒未満は起動を止めず、点検の誤りで対象DB を使わない（レビュー R-01） | 1.2節 |
| 機能設計 5節（締め切りは `ReferenceLookup` の中） | 締め切りを `businessdata.domain` の `ReferenceReadDeadline` に出し、U2 からも使う。全体の上限は `TargetReadAccess.referenceReadTotal()` で受ける | 5.1節（U2 のレビュー R-01） |
| 機能設計 BR9.5・`entities.md`（`FAILED` は理由を持つ） | `FAILED` は理由を含む失敗の値 `ReferenceReadFailure`（`sqlState`・`vendorCode`・`exceptionClass`・`budgetExceeded`）を持つ | 5.2節（U2 のレビュー R-01）。理由の3つの値と U2 の code への写しは変わらない |

## 後の段に回すこと

- **Code Generation**: 1節の設定の追加と点検、5節の2つの型と `TargetReadAccess.referenceReadTotal()`、4節のテスト。新しいクラスを足すことは計画に明記して依頼者の承認を得る（`team.md`）。U2 の計画はこの名前を使う。
- **Infrastructure Design・Deployment Pipeline**: `.env.example` と README の新しい2つの設定の説明（値は既定のままでよい）。
