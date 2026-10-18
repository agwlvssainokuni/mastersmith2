# 性能の設計 — U1 u1-businessdata-read

## 捨ての試しの結果（要点）

試しの形と全部の数値は reliability-design.md の 1節にまとめた。ここには性能に関わるものだけを書く。試しは Q1: A のとおり、リポジトリの外で 2026-10-18 に行った（約 30 分、追加の F2 の試しは約 5 分）。

**受け取る量（T4）**
- 2,000,000 字の値 10 行を読むと、`SUBSTRING(列, 1, 101)` で割り当てが 267MB から 0.016MB に下がった（3種類の DB とも）。
- `SUBSTRING` はコードポイントで数える（絵文字 150 字 → 101 コードポイント）。
- MariaDB は、絞っても、サーバーが大きな値を読む時間（約 120ms）が残る。

**重さ（T6）**
- 100,000 行・100 カラムの種で測った（Q5: A。PG の行の平均は 1,820 バイト）。表の大きさは PG 198MB・MySQL 532MB・MariaDB 372MB。
- MySQL・MariaDB のバッファープールは既定の 128MB で、表が入りきらない。

1本で流した時間（ms）:

| 問い合わせ | PG | MySQL | MariaDB |
|---|---|---|---|
| 件数（条件なし） | 3〜4 | **394〜449** | 241〜246 |
| 最初のページ | 2〜15 | 11〜19 | 117〜147 |
| 最後のページ（そのまま） | 641〜706 | 1,356〜1,549 | 399〜636 |
| 最後のページ（主キーを先に読む形） | 4〜5 | 1,118〜1,228 | 121〜130 |
| 部分一致の件数 | 124〜211 | 1,272〜1,362 | 224〜290 |
| 部分一致のページ | 129〜136 | 1,450〜1,528 | 431〜466 |

10 本同時（プール 5）の p95（ms。アプリの処理と HTTP を含まない）:

| 場面 | PG | MySQL | MariaDB |
|---|---|---|---|
| bizSearchFirst | 23 | **1,482** | 877 |
| bizSearchLast（主キーを先に読む形） | 35 | **4,186** | 556 |
| bizSearchContains | **1,168** | **4,096**（すべて借りる待ちの時間切れ） | **1,976** |

**結論**
- MySQL の件数の1回は、Q5: A の線（300ms）を超えた。
- 試しだけで、NFR1.1（p95 < 1 秒）を超える場面があった。
- 依頼者の決定は F1: A。アプリの作りは変えない。Performance Validation の使い捨ての対象DB を表が入る設定にし、その設定を記録してから測る。それでも超える場面は Not Met として、その段で扱いを諮る。目標は緩めない。
- 深いページは、主キーを先に読む形で PG・MariaDB が大きく速くなるため、この形を採る（2節）。

## 対応表

| U1 の NFR 要件の ID | この文書の節 | 扱い |
|---|---|---|
| NFR1.1 応答時間 | 1節・2節・3節 | 作りは決めた。合否は Performance Validation（F1: A） |
| NFR1.2 k6 の判定の決まり | 4節 | そのまま。対象DB の設定の記録を足した |
| NFR1.3 件数と深い OFFSET を測りに含める | 1節・3節 | 測りに含める |
| NFR2.2 読む行の数の上限 | 1節 | scalability-design.md と共有 |
| NFR2.5 長い文字の絞り | 1節 | 受け取る量の作り。メモリの判定は scalability-design.md |
| NFR3.2 3種類の DB で性能を測る | 4節 | そのまま |
| NFR7.3 `verify` と CI の時間 | 5節 | そのまま |

## 1. 問い合わせの形

一覧と検索は、1回の貸し出し（接続1本）の中で、次の順に読む。

1. 件数を読む: `SELECT COUNT(*) FROM <表> WHERE <条件>`。`setQueryTimeout(5)` を付ける。
2. 読み飛ばす行の数を、long で `(page − 1) × size` として求める。最大は 99,999,900 で、桁あふれしない（Q4: A）。それが件数以上なら、行を読まずに空のページと件数を返す（BR5.7）。
3. ページを読む。
   - **主キーのあるテーブル**（ビューは DSL の主キーの設定）: 主キーだけを先に並べて読み、その主キーで行をつなぐ。

     ```sql
     SELECT <列…> FROM t
       JOIN (SELECT <主キー> FROM t WHERE <条件> ORDER BY <キー…, 主キー> LIMIT ? OFFSET ?) p
         ON <主キーの一致>
      ORDER BY <キー…, 主キー>
     ```

   - **主キーの無いテーブル**: そのまま `ORDER BY … LIMIT ? OFFSET ?` で読む。順は約束しない（BR5.3）。
4. `setMaxRows(size)` と `LIMIT ?` の両方を置く。詳細は `setMaxRows(2)`（BR5.8・BR6.3）。

選ぶ列の作り（RowDialect が組み立てる）:

| 値の種類 | 選ぶ式 |
|---|---|
| STRING | `SUBSTRING(列, 1, N+1)`。一覧は N = 100、詳細は N = 10,000 |
| OTHER | PG は `SUBSTRING(CAST(列 AS TEXT), 1, N+1)`、MySQL・MariaDB は `SUBSTRING(CAST(列 AS CHAR), 1, N+1)`。T2 で、`year` が `2026` と出ることを確かめた |
| BINARY・password | 選ばない（BR7.4・BR7.5）。MySQL・MariaDB の `bit`（長さ 2 以上）と空間の型も BINARY と同じ扱いで、選ばない（F3: A。reliability-design.md の 4節） |
| 時差つきの型 | reliability-design.md の 3節 |
| そのほか | 列のまま |

- 並べ替えのキーは元の列のまま置く。NULL の並びは RowDialect が組み立てる（T9 で、3種類の DB でそろうことを確かめた）。
- fetchSize は使わない。行は最大 100 で、PG では autocommit を外さないと使えないため。
- ストリームで読む形は採らない。3種類のドライバーは、行を受け取りきってから渡すため（T4 で、そのまま読むと 267MB を割り当てた）。

## 2. 深いページの作り

- 最後のページ（OFFSET 99,900）を、そのままの形で読んだ時間（T6）:
  - PG は 641ms。読み飛ばす行の分も、選ぶ列の式（`SUBSTRING` など）を計算するため。
  - MariaDB は 399〜636ms。
- 主キーを先に読む形にすると、PG は 4ms、MariaDB は 122ms になった。
- MySQL は、主キーを先に読む形でも 1,118〜1,228ms だった。InnoDB の主キーは行そのものの並びで、主キーだけを読むときも行の頁を読むため。試しでは、表（532MB）が既定のバッファープール（128MB）に入りきらなかった。Performance Validation では、表が入る設定で測る（F1: A）。
- キーセットのページ送りは採らない。ページの番号の要件があるため（決まっていること）。

## 3. 時間の予算

p95 1 秒（NFR1.1）の配分。試しの PG の値をもとにした目安。MySQL・MariaDB の値は、F1: A のとおり Performance Validation の設定で測り直す。

| 部分 | 予算 | 試しの値（PG、10 本同時の p95 から見立てた値） |
|---|---|---|
| 認証・権限の写し（内部DB の短い読み取り） | 50ms | 前の Intent の、メニューの p95 181ms の内訳のうち写しの部分 |
| 借りる待ち（10 VU・プール 5） | 300ms | 最初のページで、試しの全体の p95 が 23ms |
| 件数 | 200ms | 3〜4ms（部分一致は 124〜211ms） |
| ページ | 300ms | 2〜15ms（深いページは、主キーを先に読む形で 4〜5ms） |
| 値の変換と JSON | 150ms | 1要求の JSON は 2.86MB（T5） |

- 部分一致は、件数とページの両方で表の全部を読む。試しの 10 本同時の p95 は、PG で 1,168ms になり、この予算に収まらない。表が入る設定でも超えるなら、Not Met としてその段で諮る（F1: A）。
- 件数とページの2回は、同じ接続で、トランザクションを張らずに読む。2つが別の時点になりうることは既知の制約（決まっていること）。

## 4. 負荷の試験の確かめ方

NFR1.2 のとおり。この段で足すことは次の3つ。

- **台本の種**: 試しの種の形を、`bizPrepare` の台本の下書きにする。
  - 中心は型ごとの短い値。TEXT の5本に 150〜270 字。大きな値は 10 行だけ 2,000,000 字。NULL を含める。
  - 試しの `Trial.java` の `seed()` が、3種類の DB の SQL の形を持つ。
  - 行の数と値は、コード生成（B2）の台本で決める。
- **対象DB の設定の記録**（F1: A）: 合否の回の前に、使い捨ての対象DB の設定と表の大きさを記録する。
  - MySQL・MariaDB の `innodb_buffer_pool_size`
  - PG の `shared_buffers`
  - コンテナのメモリの上限
  - 表の大きさ（データと索引）
- **借りる待ちの時間切れ**: 繰り返しの間が 0ms のとき、HikariCP は返した直後の同じスレッドに接続を戻しやすい。上限に届くと、待っている側が 3 秒の時間切れになった（T6 の PG で 29〜48 回）。間を 2ms 置くと 0 回だった。k6 では HTTP の往復が間になるが、それでも NFR2.4 のとおり、Performance Validation で待ちの時間切れの累計を必ず読む。

## 5. `verify` と CI の時間

NFR7.3 のとおり。

- 3種類の対象DB の結合テストでは、100 カラム・数行の小さな表を作る。
- 100,000 行の種は `verify` に入れない。負荷の試験の環境だけで使う。

## 6. 上流との差

- 承認済みの NFR 要件の NFR1.1 は、場面 `bizSearchLast` を「最後のページ（読み飛ばす行の数が多い OFFSET）」としている。この設計は、主キーのあるテーブルでは主キーを先に読む形にする。問い合わせの作りの差で、測るものは変わらず、目標を緩める変更でもない。
- 下書きの「1要求 6〜10MB」は、T5 の実測で、一時の割り当て約 18MB・残る量約 2.3MB と分かった。値は scalability-design.md に書いた。
- 機能設計の BR7.1 では、MySQL・MariaDB の `bit`（長さ 2 以上）と空間の型を OTHER として読む。この設計では BINARY と同じ扱いにし、選ばない（F3: A。reliability-design.md の 6節）。

## 7. 後の段に回すこと

- **Infrastructure Design・Performance Validation（F1: A）**:
  - 使い捨ての対象DB の設定の値は、`bizPrepare` の後に表の大きさを実測して決める。MySQL・MariaDB はバッファープール、PG は共有バッファー。
  - `docker/perf/compose.yaml` の対象DB のメモリの上限（今は 512m・768m・512m）とあわせて決める。
  - colima の VM（CPU 4・6GiB）で、配備したアプリを止めた状態でも収まるかを確かめる。
  - 決めた値は、Performance Validation の記録に書く。
- **Performance Validation（F1: A）**:
  - 今回の試しの値（1節の2つの表）を、基準として並べて記録する。
  - NFR1.1・NFR1.3・NFR3.2 の合否の回を行う。表が入る設定でも超える場面は Not Met として、その段で扱いを諮る。目標は緩めない。
- **コード生成の計画（B2）**:
  - 主キーを先に読む形を、RowDialect の3種類で組み立てる（複合の主キーのつなぎ方を含む）。
  - `bizPrepare` の種の台本（`perf/` の下）。

## 8. U2・U3 とそろえる点

- U2 の、更新・削除の前の行の読み直しも、同じ RowDialect の選ぶ列の作り（`SUBSTRING`・`CAST`・BINARY と同じ扱いの型）と、時差の扱いを使う。
- U2 の k6 の場面（`bizCreate`・`bizUpdate`・`bizDelete`）は、同じ `bizPrepare` の種と、同じ対象DB の設定（F1: A）で測る。
- U3 の一覧の画面の時間の目標は、U1 の Performance Validation の結果（Not Met の扱いを含む）に合わせる。

## Sources

- 承認済みの NFR 要件
  - `aidlc/spaces/default/intents/261016-business-data-views/construction/u1-businessdata-read/nfr-requirements/performance-requirements.md`（NFR1.1〜NFR1.3・NFR3.2・NFR7.3）
  - `scalability-requirements.md`（NFR2.1・NFR2.2・NFR2.5）
- この段の答え
  - `construction/u1-businessdata-read/nfr-design/nfr-design-questions.md`（Q1・Q4・Q5: A、まとめの確認 Looks correct）
  - `construction/u1-businessdata-read/nfr-design/nfr-design-followup-questions.md`（F1・F3: A）
- 機能設計: `construction/u1-businessdata-read/functional-design/rules.md`（BR5.1〜BR5.8・BR6.3・BR7.1・BR7.4〜BR7.6）・`functional-spec.md`（3.2節・8節）
- ADR-007: `inception/domain-design/decisions.md`
- 捨ての試し: リポジトリの外の試しのコードと結果（T4・T5・T6・T9）。要点は、この文書と reliability-design.md の 1節に写した
- 前の Intent の実測: `aidlc/spaces/default/intents/261004-role-menu/operation/performance-validation/test-results.md`

## Assumptions & Open Questions

- **依頼者の決定（F1: A）**: 試しで MySQL・MariaDB の重さの前提が崩れた（1節の結論）。
  - アプリの作りは変えない。
  - Performance Validation の使い捨ての対象DB を表が入る設定にし、その設定を記録してから測る。
  - それでも超える場面は Not Met として、その段で扱いを諮る。目標は緩めない。
- **依頼者の決定（F3: A）**: MySQL・MariaDB の `bit`（長さ 2 以上）と空間の型は、BINARY と同じ扱いにし、選ばない。
- 前提: 試しは、アプリの処理（Spring・権限の写し・JSON の応答）と HTTP を含まない。本番の k6 の値は、試しの値より大きくなる。
- 前提: 試しの対象DB は、perf/compose と同じメモリの上限で、DB の設定は既定のまま。表が入る設定では、件数と深いページの時間が下がると見立てた。部分一致は表の全部を読むため、CPU の分の時間は残る見込み（未検証）。
