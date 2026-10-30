# 性能の設計 — U1 u1-reference-read

参照先の読み取り（REFERENCE の選択肢・LOOKUP のピッカーの検索・ラベルの1件読み・U2 が呼ぶ `check` と `countState`）を、NFR2.1 の線の内で、読む量を絞って行う作りを決める。数字は、この段の捨ての試し（`trial-results.md`）で測った値。

## 出典

- この単位の NFR 要件 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-requirements/`（`performance-requirements.md` の NFR2.1〜NFR2.5・NFR5.3、`scalability-requirements.md` の NFR2.6〜NFR2.9）
- この単位の機能設計 `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/functional-design/`（`functional-spec.md` 4節・5節、`rules.md` の BR4.2・BR5.7・BR5.8・BR7.3・BR8.6・BR11.4）
- この段の答え `aidlc/spaces/default/intents/261029-reference-lookup-cleanup/construction/u1-reference-read/nfr-design/nfr-design-questions.md`（Q1: A・Q3: A・F1: A）と試しの結果 `trial-results.md`
- コード（読み取りだけ）: `backend/src/main/java/cherry/mastersmith/businessdata/repository/RowSqlBuilder.java`・`RowDialect.java`・`MysqlFamilyRowDialect.java`・`PostgresRowDialect.java`・`ColumnValueCodec.java`

## 1. 読む量を絞る作り

### 1.1 `ReferenceReadSpec` の中身（BR11.4 に足す点）

既存の `RowSqlExecutor` の `search` に渡す `ReferenceReadSpec` は、機能設計の項目（切らない値のカラム・切る表示の列・値が NULL の行を除く・固定の並び・件数を数えない・読む行・読み飛ばす数）に、次の2つを足す。

| 足す項目 | 中身 | 要件 |
|---|---|---|
| 値の長さの条件 | 値のカラムが文字の型（STRING と、文字にして読む OTHER）のとき、`CHAR_LENGTH(値の式) <= 10000` を WHERE に足す。10,000 は `ColumnWriteRulesFactory.MAX_VALUE_LENGTH` と同じ値 | NFR2.8（Q1: A） |
| 並びの項の作り方 | 文字の型のラベルと値は、並びの項を先頭の 100 字（`LIST_TEXT_LIMIT`）の式で作る（2節） | F1: A |

値のカラムを選ぶ式は、主キーと同じ「切らない」選び方をやめ、`SUBSTRING(値の式, 1, 10001)` にする（NFR2.8。長さの条件で 10,000 字を超える行は来ないため、切るのは守りの重ね）。ラベルと一覧の列は今までどおり `selectExpression(..., LIST_TEXT_LIMIT)`。

### 1.2 SQL の形（方言のクラスに閉じる）

長さの関数と並びの項の式は、DB の種類ごとの方言のクラス（`RowDialect` とその2つの実装）に閉じ、service の層は DB の種類で分岐しない（`team.md` の Code Style）。試しで3種類とも `CHAR_LENGTH` が文字（コードポイント）で数えると確かめた（`trial-results.md` 2.1）。書き方が同じでも、方言の口を通す。

```text
RowDialect に足す口（既定の実装は3種類で同じ式）:
  String lengthWithin(String valueExpr, int maxChars)   // "CHAR_LENGTH(" + valueExpr + ") <= " + maxChars
  String sortExpression(String columnRef, ColumnAccess c) // STRING: SUBSTRING(列, 1, 100)
                                                         // OTHER : SUBSTRING(castToText(列), 1, 100)
                                                         // ほか  : 列のまま
```

上限の数（10,000・10,001・100）は既存の書き方と同じく SQL の中の定数とし、利用者の入力を文字のまま入れない（`project.md` の Forbidden を守る。値は今までどおりバインド変数）。

選択肢の SQL の形（主キーのある参照先。既存の `page` と同じ主キーで結ぶ形）:

```sql
SELECT t.主キー, SUBSTRING(t.値,1,10001), SUBSTRING(t.ラベル,1,101) FROM 表 t
 JOIN (SELECT 主キー FROM 表
        WHERE 値 IS NOT NULL AND CHAR_LENGTH(値) <= 10000
        ORDER BY 並びの項(ラベル) NULL は最後, 並びの項(値), 主キー
        LIMIT 201) p ON t.主キー = p.主キー
 ORDER BY 同じ並び
```

主キーの無い参照先は結ばない形（既存の `page` と同じ）。ピッカーは `LIMIT 21 OFFSET (page-1)*20`、ラベルの1件読みは `値 = ? ... LIMIT 1`、`check` は `値 = ? ... LIMIT 201`。どれも `setMaxRows` に同じ数を当てる（NFR2.6）。

### 1.3 数え方の決まり（U1 の NFR 要件のレビュー R-05 の解き方）

長い値の行を SQL の条件で先に除くため、読んだ行の数がそのまま使える行の数になる。

| 判定 | 決まり |
|---|---|
| `TOO_MANY` | 長さの条件を通った行を 201 行読めたとき（200 は `AVAILABLE`） |
| ピッカーの次のページ | 長さの条件を通った行で 21 行目があるとき |
| `countState` | 選択肢と同じ条件・同じ数え方 |
| `check` | 長さの条件を通った行だけを比べる。10,000 字を超える行しか一致しないときは `NOT_FOUND_OR_DENIED` |

Java の側で行を落とす処理は置かない（同じ値の2行目以降を落とす BR4.4 だけは残る。これは 201 行目の判定の後に行い、`TOO_MANY` の判定には同じ値の行も数える。BR4.3 のまま）。

## 2. 並びの項を先頭 100 字で作る（F1: A）

### 2.1 なぜか

試しで、MySQL 26.10.0（既定の照合 `utf8mb4_0900_ai_ci`、`sort_buffer_size` 262,144）では、`ORDER BY` に約 10,000 字の値を数十行以上持つ文字のカラムを置くと `HY001`・1038（Out of sort memory）で失敗した（`trial-results.md` 4節）。型（VARCHAR(16000)〜LONGTEXT）・長さの条件・`id` だけを選ぶ形によらず起き、約 8,000 字までは成功した。`SUBSTRING(列,1,100)` で並べる形は3種類とも成功した。

### 2.2 決まり

- 文字の型のラベルと値の並びの項は `sortExpression`（先頭 100 字）で作る。NULL を最後にする書き方は既存の `orderTerm` に、この式を渡して作る。
- 数・日時・真偽の型と主キーは、カラムのまま並べる。
- 3種類の DB で同じ形にする（PostgreSQL と MariaDB では失敗しないが、作りと試験を1つにそろえる）。
- 先頭 100 字が同じ行どうしは、次の項（値の先頭 100 字）→ 主キーの順になる。主キーの無い参照先では順を約束しない（BR5.8 のまま）。
- ラベルは画面に 100 字で切って出すため、見える文字で並ぶ。

## 3. 時間の見込み（試しの値）

1 本の接続で、各 5 回の中央値（`trial-results.md` 2.2）。10 VU の同時の値は Performance Validation の k6 で測る。

| 読み方 | PostgreSQL 18.6 | MySQL 26.10.0 | MariaDB 13.0.2 | 線（NFR2.1） |
|---|---|---|---|---|
| 選択肢 201 行（100,000 行の表、長さの条件あり） | 63.4 ms | 21.2 ms | 48.2 ms | 1,000 ms |
| 同じ・長さの条件なし（比べるため） | 59.7 ms | 19.2 ms | 45.9 ms | — |
| 値のカラムが長い文字の型（長さの条件あり、主キーで結ぶ形） | 173.8 ms | 1038（2節の直しの前） | 200.7 ms | 1,000 ms |
| ピッカー `EQUALS` 1ページ目 | 5.5 ms | 13.4 ms | 15.6 ms | 1,000 ms |
| ピッカー `CONTAINS` 1ページ目 | 7.1 ms | 15.2 ms | 18.0 ms | 3,000 ms |
| ピッカー 条件なし 1,000 ページ目 | 535.6 ms | 39.5 ms | 98.8 ms | 1,000 ms |
| `check`・ラベルの1件読み（値の一致、索引あり） | 0.4〜0.5 ms | 0.5 ms | 0.5 ms | 1,000 ms |

EXPLAIN の読み: 選択肢とピッカーは、並びがラベルから始まるため、もともと表の全部を読んで並べる（索引で並びを作れない）。長さの条件は Filter が1つ増えるだけで、増えた時間は 1.3〜3.7 ms。値の一致の `check`・ラベルの1件読みは、長さの条件があっても値のカラムの索引を使う。並びの項を `SUBSTRING` にしても、選択肢とピッカーのプランは変わらない（もともと並べ替えが要る）。

注意: 条件を空にしたピッカーで深いページ（1,000 ページ目、`OFFSET 19980`）を読むと、PostgreSQL では 1 本の接続でも約 0.5 秒かかる。k6 の場面（`EQUALS`・`CONTAINS` の1ページ目）には入らないため合否には関わらないが、10 VU では線に近づきうる。Performance Validation で深いページの値を参考に1回測り、記録する（判定の対象にはしない）。

## 4. 性能の予算（1要求）

| 部分 | 予算 | 根拠 |
|---|---|---|
| 権限の写しの読み取り（内部DB） | 既存の一覧と同じ | 既存の `TableAccessResolver` を1回だけ（BR2.2） |
| 対象DB の接続を借りる待ち | 平常は 0 に近い。上限 3 秒 | 既存の `connectTimeout`（3 秒） |
| 選択肢の読み取り（REFERENCE 1つ） | 平常 200 ms 未満 | 3節の最悪の形（長い文字の値）で 174〜201 ms |
| 全体の上限 | 10 秒（設定。`reliability-design.md` 1節） | Q2: A |

## 5. メモリ（NFR2.8 の裏付け）

`long_ref`（201 行が 10,000 字の日本語の値・ラベル 93 字）を読んだ試しの値（`trial-results.md` 2.4）。

| 読み方 | 割り当て | GC の後に残る量 | 応答の JSON の見込み |
|---|---|---|---|
| 長さの条件＋10,001 字の切り取り（この設計） | 38.8 MB | 3.9 MB | 5.8 MB |
| 切らずに読む（2,000,000 字の行を含む。比べるため） | 418〜424 MB | 43.7 MB | 62.7 MB |

1要求で残る量（値の文字列 3.9 MB）に、ドライバーの行の写しと JSON に書く写し（それぞれ約 6 MB）を足しても、要件の見積もり（10〜15 MB）の内。10 VU では残る量が約 40 MB、一時の割り当てが1巡で約 390 MB（若い世代ですぐ回収される）。要件の見積もり（100〜150 MB）の内のため、ヒープの設定は変えない。

## 6. 測り方（Build and Test・Performance Validation に渡すこと）

- k6 の場面と判定の決まりは NFR2.1〜NFR2.5 のまま（場面の名前 `refDefinition200`・`refDefinition201`・`refPickerEquals`・`refLabel`・`refPickerContains`、10 VU・3 分、`iteration_duration` の p95）。使い捨ての環境の読み取りのプールの上限は 5。
- 種（NFR2.4）: REFERENCE の参照先の 200 行・201 行の表を足す。`biz_item` の `c_010`（2,000,000 字の 10 行を含む）を値のカラムにした参照は、長さの条件で外れることを確かめる参考の場面にしてよい（判定の対象の外）。
- `verify` の時間（NFR5.3）: 3種類の DB の結合テストが増えるため、コード生成の後と Build and Test で測る。MySQL の 1038 を再現する結合テスト（2,000 行の中に約 10,000 字の行が 200 行）は、表を作って行を入れる時間が掛かるため、1つのテストのクラスで1回だけ作る。

## 上流との差

| 上流 | 差 | 扱い |
|---|---|---|
| 機能設計 BR4.2・BR5.8（並びは「ラベルの昇順 → 値の昇順 → 主キー」で、カラムそのもので並べる） | 文字の型のラベルと値は、先頭の 100 字で並べる。先頭 100 字が同じ行は主キーの順（F1: A） | 承認済みの機能設計の文書は書き換えない。この差をここに記録し、結合テストで決めた動作を確かめる（`reliability-design.md` 4節） |
| 機能設計 BR7.3・BR11.4（値のカラムは主キーと同じく切らずに選ぶ） | 値の式を `SUBSTRING(…, 1, 10001)` で切り、`CHAR_LENGTH(…) <= 10000` で行を除く（NFR2.8・Q1: A） | NFR 要件で決めた差。10,000 字以下の値は切らずに返る点は BR7.3 のまま |
| NFR 要件 NFR2.8「外した行は数えない（細部は NFR 設計）」 | SQL で先に除くため、Java の側で外す処理を置かない | 1.3節の数え方 |

## 後の段に回すこと

- **Code Generation**: `RowDialect` の2つの口と `ReferenceReadSpec` の足す項目の実装、1.3節と2節の結合テスト（`reliability-design.md` 4節の一覧）。
- **Build and Test**: k6 の場面と種の台本、深いページのピッカーの参考の値。
- **Performance Validation**: NFR2.1・NFR2.3 の合否。
- **Feedback & Optimization**（F2: A）: 既存の業務データの一覧で、DSL で `sortable` にした長い文字のカラムで並べると、MySQL で同じ 1038 になり 500 を返しうる（試しからの見立てで、一覧そのものでは確かめていない）。直すかは後の Intent の要件で決める。
