# 信頼性の設計 — U2 u2-reference-write-check

保存前の参照の段（⑩）の時間の上限・失敗の扱い・確かめと保存の間のすき間・互換を決め、境界と失敗の結合テストの一覧を置く。

## 出典

- この単位の NFR 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/reliability-requirements.md`（NFR3.24〜NFR3.27・NFR6.21・NFR7.21・NFR7.22）、`security-requirements.md`（NFR1.29）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/rules.md`（BR1.3・BR1.4・BR4.5・BR6.1・BR6.2）
- 契約 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/inception/contract-design/contract-summary.md`（C1・C3・C5）
- U1 の NFR 設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/reliability-design.md`（1節の時間の上限と締め切りの作り方）・`logical-components.md`
- この段の答え `nfr-design-questions.md`（Q2: A・Q3: A）と試しの結果 `trial-results.md`（3節・4節）
- コード（読み取りだけ）: `businessdata/service/BusinessDataWriteService.java`、`targetdb/service/TargetWriteAccess.java`・`JdbcTargetWriteAccess.java`、`businessdata/testsupport/CountingDataSourceConfig.java`・`BusinessDataTestApp.java`

## 1. 時間の上限

### 1.1 文1回の上限（NFR3.24）

⑩ の読み取り（更新前の行・`countState`・`check`）は、U1 が足す `ReadPurpose.REFERENCE` の目的で借りた接続で行い、文1回の上限は U1 と同じ `REFERENCE` の上限（既定 5 秒）にする。U2 は新しい設定を足さない。

### 1.2 ⑩ の全体の上限（NFR3.25、Q2: A）

- 上限の値は U1 と同じ設定 `MASTERSMITH_TARGET_DB_REFERENCE_READ_TOTAL`（`TargetDbProperties.referenceReadTotal`、既定 10 秒）を使う。値の確かめ（範囲外の値の扱い）は U1 の設定の扱いに従う。
- 締め切りを数える部品も U1 と同じものを使う（仮の名前「参照の読み取りの締め切り」。U1 の `ReferenceLookup` が定義の取得で使うもの）。U2 は ⑩ の中から直接使うため、この部品は `ReferenceLookup` の中に閉じず、`businessdata` の中から呼べる純粋な値（置き場の候補は `businessdata.domain`）にする（上流との差）。

```text
⑩ の withRead(REFERENCE, session -> {
  deadline = 締め切り.start(clock, referenceReadTotal)   // ⑩ の最初の読み取りの前に1回（借りる待ちは含めない）
  各読み取り（detail・countState・check）の前:
    narrowed = deadline.narrow(session)                  // 残りを秒に切り上げ、min(REFERENCE の上限, 残り)
    if narrowed が「期限切れ」（残り 1 秒未満） → 流さず FAILED(TIMEOUT, budgetExceeded)
    else 絞った session を渡して1文を流す              // U1 の口と detail はその session の上限で流す
})
```

- 絞った session は、同じ `product`・`connection`・壊れた印を持ち `queryTimeoutSeconds` だけを絞った `TargetReadSession`（既存の4引数のコンストラクター。U1 の BR8.4 と同じ）。`check`・`countState`・`detail` はそれぞれ1文だけ流すため、呼ぶ前に絞れば文ごとに絞ることになる。U1 の口の引数は変えない。
- 時計は注入した `Clock`（`BusinessDataWriteService` が既に持つもの）。
- 最悪の値: ⑩ だけで約 11 秒（切り上げで最後の文が締め切りを最大 1 秒未満超えうる。U1 の試しで文の上限の誤差は 30 ms 未満）。これに、前に権限の写しの読み取りと読み取りの接続を借りる待ち（上限 3 秒）が乗る。この時間切れの経路（503）は k6 の p95 の判定の対象の外で、結合テストで確かめる。

## 2. 失敗の扱い（NFR3.26・NFR6.21）

| ⑩ の失敗 | 写し | 応答 | その後 |
|---|---|---|---|
| 読み取りの設定が無い（`withRead` が `NotConfigured`） | `TARGET_DB_UNAVAILABLE` | 503 `BUSINESS_TARGET_DB_UNAVAILABLE` | 何も保存せず、書き込みの接続を借りない |
| 読み取りの接続を借りられない（`Unavailable`） | 同上 | 同上 | 同上 |
| 文の時間切れ（`FAILED(TIMEOUT)`。3種類の形は U1 の試しで `TIMEOUT` に分かれる） | `TARGET_DB_TIMEOUT` | 503 `BUSINESS_TARGET_DB_TIMEOUT` | 以後の読み取りをせず、壊れた印を立てて接続を返す |
| 全体の上限（残り 1 秒未満） | 同上 | 同上 | 流さずに止める |
| 接続の失敗（`FAILED(UNAVAILABLE)`） | `TARGET_DB_UNAVAILABLE` | 503 | 壊れた印を立てて返す |
| 型の食い違い（`FAILED(MISMATCH)`） | `TARGET_DB_MISMATCH` | 409 `BUSINESS_TARGET_DB_MISMATCH` | 以後の読み取りをしない |
| 更新前の行の `detail` の失敗 | 上と同じ分け方（既存の `RowSqlFailureClassifier`） | 上と同じ | 上と同じ |

- 失敗が起きたら ⑩ をその場で止め、残りの `countState`・`check` を流さない（BR4.5）。503 は「保存されていない」ことが確かな失敗で、送り直してよい。
- 新しい code・項目の誤りの理由・監査の理由を作らず、1つの code に1つの状態コードの決まりを崩さない（NFR6.21）。監査は既存の `BusinessDataAuditReason.of` の写しのまま（BR5.3）。
- ⑩ の失敗は `Attempt`（書き込みの口の中の結果）を通さずに応答にするため、既存の書き込みの失敗の WARN（`finish` の中）と重ならない（`observability-design.md` 2節）。
- 自動のやり直し（再試行）は置かない。⑩ は読み取りだけで副作用が無いが、503 は利用者が送り直す扱いのまま（既存の書き込みと同じ）。

## 3. 確かめと保存の間のすき間（NFR1.29・NFR3.27）

- 外部キーのある参照先では、既存の外部キーの違反の変換（セーブポイントへ戻して違反のカラムを確かめ、409 `BUSINESS_REFERENCE_NOT_FOUND`）を最後の守りにする（BR6.1）。⑪ の中身は変えない。
- 試しで3種類とも、作成のすき間（確かめの後に参照先を消す）と、更新の「変わらないと判定した参照」のすき間（別の利用者が参照を変えてから元の参照先を消す）が、書き込みの外部キーの違反で拒否され、行が変わらないことを確かめた（`trial-results.md` 4節）。
- 更新では、参照先の行はその行自身が指している間は外部キーで消せないため、すき間は「参照を変える＋元の参照先を消す」の組でだけ現れる。
- 外部キーの無い参照先では、どちらのすき間も守るものが無く、宙に浮いた値が残りうる（既知の制約。テストを置かない）。

## 4. すき間の待ち合わせの差し込み口（Q3: A）

### 4.1 置き場

- テストだけの部品 2 つを `backend/src/test/java/cherry/mastersmith/businessdata/testsupport/` に置く（仮の名前）。
  - 包む部品 `PausingTargetWriteAccess`: `TargetWriteAccess` を実装し、元の Bean に任せる。合図（`arm`）が立っているときだけ、`withWrite` の入口で「着いた」の合図を出し、「進め」の合図を待ってから元の `withWrite` を呼ぶ。`product()` は止めずに任せる（`TableAccessResolver` が使う）。
  - 設定 `PausingWriteAccessConfig`: `@TestConfiguration` の static な `BeanPostProcessor` で、名前 `jdbcTargetWriteAccess` の `TargetWriteAccess` を包む（既存の `CountingDataSourceConfig` と同じ形）。`BusinessDataTestApp.start(..., sources, ...)` で足す。
- 待ちは `CountDownLatch` の上限つきの `await`（例 20 秒）で作り、`sleep` と実時刻に頼らない。合図は1回の要求だけに効かせ、ほかの要求は止めない。

### 4.2 なぜ作成と更新の両方で止められるか

- U2 は ⑩（読み取りの接続での更新前の行・`countState`・`check`）を `withWrite` を呼ぶ前に終え、読み取りの接続を返してから `withWrite` を呼ぶ（BR4.1）。そのため `withWrite` の入口は、作成では確かめの後、更新では変わらないと判定した後で、どちらも書き込みの接続を借りる前に当たる。
- この順は U2 の作りの決まり（BR4.1）で、⑩ を `withWrite` の中に置く作りにはしない。止めた時点で読み取りの接続が返っていることを、`CountingDataSourceConfig` を一緒に足して確かめる（読み取りの使用中が 0、読み取りを借りた回数が 1）。

### 4.3 条件を守っていること

| 条件 | 守り方 |
|---|---|
| 本番のクラスを変えない（公開の口を足さない） | `TargetWriteAccess` は既にある公開の interface で、本番の注入は interface の型だけ |
| 境界テストの許可の一覧を変えない | `ArchitectureTest`・`BusinessDataBoundaryArchitectureTest` は `src/test` を読まない（`DO_NOT_INCLUDE_TESTS`） |
| TraceAspect に入らない | `testsupport` は TraceAspect の対象の層の外 |

### 4.4 テストの手順

| テスト | 手順 | 期待 |
|---|---|---|
| T-G1 作成のすき間（外部キーあり、3種類） | 合図を立てて作成を送る → 着いたら別の接続で参照先の行を消す → 進める | 409 `BUSINESS_REFERENCE_NOT_FOUND`（field は参照のカラム）、行が増えない（別の接続で読み直す）。止める前に `check` の文が流れていた |
| T-G2 更新の「変わらない」のすき間（外部キーあり、3種類） | 行 R が参照 X を指す。R の別のカラムの新しい値と、更新前と同じ値の参照 X を送る更新に合図を立てる → 着いたら別の接続で R の参照を Y に変え、X を消す → 進める | 409 `BUSINESS_REFERENCE_NOT_FOUND`、R は別の接続が変えた形のまま（更新の値が入らない）。止める前に `check` の文が流れていない（`detail` だけ） |

## 5. 互換（NFR7.21・NFR7.22）

- 移行ファイル・DSL の書式と JSON Schema・作成と更新の API の道と本文の形は変えない。拒否の応答だけが増える（C3）。
- 参照の値を送らない作成・更新は、読み取りの接続を借りず、判定の順・応答・監査・接続の数が今と同じ（BR1.2・BR6.2）。削除は ⑩ を通らない。
- 戻しは前の版のイメージだけで済む（移行と保存が無い）。

## 6. 境界と失敗の結合テストの一覧

3種類の対象DB（版を固定したイメージの Testcontainers）で `./gradlew verify` の中で流す。表はテストのクラスごとに一意の名前のスキーマ（MySQL・MariaDB はデータベース）に作って消し、書いた後の値はアプリとは別の接続で読み直す（`team.md`）。

| ID | テスト | 期待 | 要件 |
|---|---|---|---|
| T-T1 | 応答しない対象DB（テストの中の `ServerSocket`）で、参照つきの作成・更新 | 503 `BUSINESS_TARGET_DB_TIMEOUT`、行が保存されない、書き込みを借りた回数 0 | NFR3.24 |
| T-T2 | 締め切りの部品の単体テスト: 注入した時計で残り 0.5・1.0・2.3 秒 | 流さず期限切れ／上限 1 秒／上限 min(5, 3) = 3 秒（U1 の T-T2 と同じ部品） | NFR3.25 |
| T-T3 | 参照 3 つの作成で、2つ目の前に締め切りに達する（時計を進める） | 3つ目を流さず 503、書き込みを借りない、行が増えない | NFR3.25・NFR3.26 |
| T-F1 | 失敗の種類ごと（接続の失敗・借りられない・読み取りの設定が無い・型の食い違い）の作成・更新 | 2節の表のとおり。行が増えない・変わらない、書き込みを借りた回数 0 | NFR3.26 |
| T-F2 | 参照 3 つの2つ目で時間切れ | 3つ目の問い合わせが流れない（文の記録） | NFR3.26 |
| T-G1・T-G2 | 4.4 | 4.4 | NFR1.29・NFR3.27 |
| T-C1 | 既存の書き込みの結合テスト・E2E・`BusinessDataConnectionUsageIT` を変えずに通す | 同じ | NFR7.22 |
| T-C2 | `BusinessDataProblemTypeCatalog` の code の数と状態コードの組 | 変わらない | NFR6.21 |
| T-C3 | コード生成の差分に移行ファイルと DSL の JSON Schema の変更が無い | 無い | NFR7.21 |

## 上流との差

| 上流 | 差 | 扱い |
|---|---|---|
| U1 の NFR 設計 `logical-components.md` 1節（全体の締め切りは `ReferenceLookup` の中に置く） | 締め切りの部品を、`ReferenceLookup` の外からも呼べる純粋な値にする | Q2: A（同じ部品を使う）のため。U2 は更新前の行の `detail` も ⑩ の締め切りで絞る。部品の名前と置き場は U1 のコード生成で決め、U2 の計画が使う。U1 の承認の場の決定（設定の値の確かめの扱い）には従うだけで、ここでは扱いを書かない |
| NFR 要件 NFR1.29 の確かめ方（判定の後に別の接続から参照先の行を消す） | 更新の側は「行の参照を別の値に変えてから元の参照先を消す」にした | 外部キーが消す操作を止めるため（`trial-results.md` 4節）。確かめる中身（外部キーのある参照先で、確かめなかった値の更新が 409 になり行が変わらない）は同じ |
| NFR 要件 NFR1.29 の差し込み口の例（`ReferenceLookup` を包む部品） | `TargetWriteAccess` の Bean を包む | 4節 |

## 後の段に回すこと

- **Code Generation**: 1.2 の締め切りの部品の使い方（U1 のコード生成で作る部品を ⑩ から使う）、4節のテストだけの部品、6節のテスト。
