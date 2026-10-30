# 性能の設計 — U2 u2-reference-write-check

作成・更新の保存前の参照の段（⑩）を、既存の書き込みの線（p95 1,000 ms 未満）の内で行う作りを決める。数字は、この段の捨ての試し（`trial-results.md`）で測った値。

## 出典

- この単位の NFR 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/nfr-requirements/`（`performance-requirements.md` の NFR2.21〜NFR2.25・NFR5.23、`scalability-requirements.md` の NFR2.26〜NFR2.28）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u2-reference-write-check/functional-design/`（`functional-spec.md` の 4節・9節、`rules.md` の BR4.1・BR4.7）
- この段の答え `nfr-design-questions.md`（Q1: A・Q4: A）と試しの結果 `trial-results.md`
- U1 の NFR 設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/performance-design.md`（`check`・`countState` の SQL の形、長さの条件 `lengthWithin`、並びの項 `sortExpression`）

## 1. 値の違う参照の確かめ方: 参照ごとに順に呼ぶ（Q1: A、NFR2.28）

- `check` は、確かめる参照ごとに、⑩ で借りた1本の読み取りの接続で DSL のカラムの並びの順に呼ぶ（BR2.7・BR4.1）。束ねる形（`IN`・`UNION ALL`）は作らない。
- 理由: 試しで、順に呼ぶ形は参照 5 個・10 本の同時でも保存の p95 が 14.89〜25.70 ms で、目標 1,000 ms の 3% 未満だった（`trial-results.md` 2.3）。束ねる形の速さの差は 1〜12 ms で、SQL の部品・SpotBugs の `SQL_` の除外・境界テストを変える費用に見合わない。順に呼ぶ形は U1 の `check` をそのまま使え、1文で読む行が 201 行に留まる。
- `countState` は、同じ参照先（スキーマ・テーブル・`valueColumn`）ごとに要求1件の中で1回だけ呼び、結果を使い回す（BR4.7）。

### 1.1 1要求の文の数

| 要求 | 文の数 |
|---|---|
| 参照の値が無い作成・更新（必須の参照項目も無い） | 0（読み取りの接続を借りない。BR1.2） |
| 作成 | 必須の参照項目の参照先の数（同じ参照先は1） ＋ 確かめる参照の数（`NO_PERMISSION`・型として読めない値は問い合わせない） |
| 更新 | 1（更新前の行の `detail`） ＋ 値が変わった参照の数 |

例: 必須の参照項目1つと、ほかの参照4つに値を入れた作成は 1 ＋ 5 ＝ 6 文（試しの「順に・参照 5」の形）。

## 2. 時間の見込み（試しの値）

| 部分 | 見込み（1本ずつ） | 根拠 |
|---|---|---|
| `countState`（参照先 200 行） | 0.8〜2.4 ms | `trial-results.md` 2.2。200 行を並べて読む |
| `check`（値の一致、索引あり） | 0.4〜0.5 ms／文 | 同上 |
| 更新前の行の `detail`（主キー） | `check` と同じ程度 | 主キーの一致の1行。試しでは測っていない（U1 の試しのラベルの1件読み 0.4〜0.5 ms が近い形） |
| 読み取りの段の全体（借りる〜返す、参照 5） | 2.9〜4.4 ms | 同上 |
| 10 本の同時の保存の全体（参照 5） | p95 14.89〜25.70 ms | 2.3 |

値のカラムに索引が無い参照先では、`check` は参照先の行の全部を読む。参照先が 200 行なら `countState` と同じ程度（数 ms）で、件数の多い参照先でも U1 の試しの 100,000 行の選択肢（20〜63 ms）を超えない見込み。文1回の上限（5 秒）と ⑩ の全体の上限（10 秒）が上の限り（`reliability-design.md` 1節）。

## 3. 性能の予算（1要求）

| 部分 | 予算 | 根拠 |
|---|---|---|
| ①〜⑨（権限の写しの読み取りを含む） | 既存の作成・更新と同じ | 変えない |
| ⑩ の読み取りの接続を借りる待ち | 平常は 0 に近い。上限は既存の借りる待ち（3 秒） | 読み取りのプール（既定 10）を一覧・詳細・U1 と共有 |
| ⑩ の読み取り | 平常 30 ms 未満（参照 5 個、10 本の同時） | 2節 |
| ⑩ の全体の上限 | 10 秒（U1 と同じ設定。`reliability-design.md` 1節） | Q2: A |
| ⑪ 書き込み | 既存の作成・更新と同じ | 変えない |

## 4. メモリ（NFR2.27）

- 1回の `check`・`countState` で読む行は最大 201 行で、値のカラムは U1 の決定どおり SQL の側で 10,001 字に切り、10,000 字を超える行を長さの条件で除く（U1 の `performance-design.md` 1節）。
- 順に呼ぶため、前の `check` の行を持ち続けない。U2 が持ち続けるのは結果（`FOUND`・`NOT_FOUND_OR_DENIED`・`FAILED`）と項目の誤りだけ。1要求の確かめの残る量は、U1 の試しの長い値の形（201 行 × 10,000 字で GC の後 3.9 MB）と同じく参照の数によらず約 4 MB に収まる。
- 束ねる形を選ばないため、要件の見積もり（NFR2.27）は変わらない。ヒープの設定は変えない。

## 5. 測り方（Build and Test・Performance Validation に渡すこと）

- 合否の場面は NFR2.22 のとおり（仮の名前 `refCreateChecked`。必須の参照項目1つ、参照先ちょうど 200 行、10 VU・3 分、`iteration_duration` の p95、`checks` の率 1）。場面の文の数は `countState` 1 ＋ `check` 1 ＝ 2 文。
- 判定の決まり（NFR2.24）・既存の場面の意味を保つこと（NFR2.23）・測らない範囲は NFR 要件のまま。
- 試しの値（参照 5 個で数 ms 増）から、参照が多い作成の場面は足さない。参照の数による増え方は、試しの値を記録として渡す。
- `verify` と CI の時間（NFR5.23）は、コード生成の後と Build and Test で `:backend:cleanTest :backend:cleanIntegrationTest` を付けて測る。

## 上流との差

| 上流 | 差 | 扱い |
|---|---|---|
| NFR 要件 NFR2.28（順に呼ぶか束ねるかは NFR 設計で決める） | 順に呼ぶ形に決めた | 1節。試しの値を `trial-results.md` 2節に記録した |

## 後の段に回すこと

- **Code Generation**: ⑩ の順に呼ぶ流れと、同じ参照先の `countState` を1回にまとめる部品。
- **Build and Test**: `refCreateChecked` の台本と最初の値（NFR2.22）。
- **Performance Validation**: NFR2.21 の合否（3種類の DB）。
