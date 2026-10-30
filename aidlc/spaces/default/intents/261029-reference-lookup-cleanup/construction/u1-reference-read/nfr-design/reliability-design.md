# 信頼性の設計 — U1 u1-reference-read

参照先の読み取りの時間の上限・失敗の分け方・失敗の後の扱い・互換を決め、境界の結合テストの一覧を置く。

## 出典

- この単位の NFR 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/reliability-requirements.md`（NFR3.4〜NFR3.7・NFR6.1・NFR7.1〜NFR7.3）、`scalability-requirements.md`（NFR2.6・NFR2.8）、`security-requirements.md`（NFR1.10・NFR1.11）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/`（`rules.md` の BR1.5・BR8.1・BR8.4・BR8.5・BR9.5・BR12）
- 契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C2・C5）
- この段の答え `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/nfr-design-questions.md`（Q1: A・Q2: A・F1: A）と試しの結果 `trial-results.md`
- コード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/targetdb/config/TargetDbProperties.java`・`TargetDataSourceConfig.java`、`businessdata/repository/RowSqlFailureClassifier.java`、`backend/src/main/resources/application.yaml`、`.env.example`

## 1. 時間の上限

### 1.1 問い合わせ1回の上限（NFR3.4）

- `ReadPurpose` に `REFERENCE` を足し、`TargetDbProperties.QueryTimeout` に `reference`（既定 5 秒）を足す。`application.yaml` は `reference: ${MASTERSMITH_TARGET_DB_QUERY_TIMEOUT_REFERENCE:5s}`、`.env.example` に名前だけ（値は空）を足す。
- `longest()` の比べる対象に `reference` を入れる。既定では最長は `generate` の 20 秒のままで、読み取りのプールの通信の待ち（`longest()` ＋ 5 秒）は変わらない。`reference` を 20 秒より長くしたときに、通信の待ちが問い合わせの上限より先に切れないようにするため。

### 1.2 定義の取得の全体の上限（NFR3.5、Q2: A）

- 設定にする。置き場は `TargetDbProperties` に足す `referenceReadTotal`（キー `mastersmith.target-db.reference-read-total`、環境変数 `MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL`、既定 10 秒）。対象DB の読み方の設定を1か所にまとめるため `targetdb.config` に置き、`QueryTimeout` には入れない（`QueryTimeout` は文1回の上限で、`longest()` の計算に混ざらないようにする）。
- 値は 1 秒以上に限る。1 秒未満（0・負の値を含む）は、設定の結び付けの時点で誤りにして起動を止める。止めないと、すべての定義の取得が流さずに 503 になり、気づきにくいため。既存の時間の設定（`Duration`）の誤りが起動を止めるのと同じ扱い。
- `application.yaml` と `.env.example` に、既定の値と下の 1.3 の最悪の値の注記を書く。

### 1.3 締め切りの作り方と最悪の値

```text
deadline = clock.instant() + referenceReadTotal        // 最初の選択肢を読む前に1回だけ
各問い合わせの前:
  remaining = deadline - clock.instant()
  seconds   = ceil(remaining を秒で)                    // 1.2 秒 → 2 秒
  if remaining < 1 秒 → 流さず定義の取得ごと 503 BUSINESS_TARGET_DB_TIMEOUT
  else session' = new TargetReadSession(product, connection,
                     min(REFERENCE の上限, seconds), 同じ brokenMark)
```

- 時計は注入した `Clock`（`team.md`）。`TargetReadSession` は変えず、既存の公開の4引数のコンストラクターで作る（BR8.4）。試しで、同じ形の record の作り直しは 7〜22 ns、同じ接続で文ごとに上限を変えると3種類のドライバーで効くと確かめた（`trial-results.md` 2.3）。
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
- `check`・`countState` は `FAILED(TIMEOUT | UNAVAILABLE | MISMATCH)` を返し、写しは U2 が持つ（BR9.5）。

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
| T-T2 | 全体の上限: 注入した時計で残り 0.5 秒・1.0 秒・2.3 秒 | 流さず 503／上限 1 秒／上限 min(5, 3) = 3 秒（単体テスト） | NFR3.5 |
| T-T3 | REFERENCE が3つで2つ目の途中で締め切りに達する | 3つ目を読まず 503、接続は壊れたものとして返る | NFR3.5・NFR3.6 |
| T-T4 | 設定: `reference` の既定 5 秒・環境変数で変えた値・`longest()` に入る。`referenceReadTotal` の既定 10 秒・変えた値・1 秒未満で起動が止まる | 単体テスト | NFR3.4・NFR3.5 |
| T-F1 | 失敗の種類ごと（時間切れ・接続の失敗・型の食い違い）の結合テストと、分ける関数の単体テスト | 2節の表のとおり | NFR3.7 |
| T-C1 | `purpose` を送らない定義の応答が、`reference: null` を除いて今と同じ。既存の定義の API のテストを変えずに通す | 同じ | NFR7.2 |
| T-C2 | 参照の項目を持つ表の一覧・詳細で、参照先への問い合わせが流れない | 文の数が今と同じ | NFR7.3 |
| T-C3 | `BusinessDataProblemTypeCatalog` の code の数と状態コードの組が変わらない | 同じ | NFR6.1 |

## 上流との差

| 上流 | 差 | 扱い |
|---|---|---|
| 機能設計 BR4.2・BR5.8 | 文字の型のラベルと値は先頭の 100 字で並べる（F1: A） | `performance-design.md` 2節、T-B8・T-B9 で確かめる |
| NFR 要件 NFR3.5「上限を定数か設定かは NFR 設計で決める」 | 設定 `MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL`（既定 10 秒、1 秒以上）にした（Q2: A） | 1.2節 |

## 後の段に回すこと

- **Code Generation**: 1節の設定の追加、4節のテスト。
- **Infrastructure Design・Deployment Pipeline**: `.env.example` と README の新しい2つの設定の説明（値は既定のままでよい）。
