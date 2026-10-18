# 性能の設計 — U2 u2-businessdata-write

## 捨ての試しの結果（要点）

試しは Q1: A のとおり、リポジトリの外で 2026-10-18 22:11〜22:46 に行った（約 35 分。上限 3 時間の中）。版・設定・全部の数値は reliability-design.md の 1節にまとめた。ここには性能に関わるものだけを書く。

**版と設定**
- Temurin 25.0.4。HikariCP 7.0.2・postgresql 42.7.13・mysql-connector-j 9.7.0・mariadb-java-client 3.5.10・jackson 3.1.7・spring-jdbc と spring-tx 7.0.9（`backend/gradle.lockfile` と同じ版）
- 対象DB は postgres 18.6・mysql 26.7.0・mariadb 13.0.2。perf/compose と同じダイジェストで、メモリの上限も同じ（512m・768m・512m）
- 書き込みのプールは Q3: A の形（通信の待ち 10 秒をプールの設定で付け、MySQL・MariaDB は `connectionInitSql`）。トランザクションは READ COMMITTED

**W1: 一意の事前の問い合わせを1文にまとめた形（100,000 行）**

| 問い合わせ | PG | MySQL | MariaDB |
|---|---|---|---|
| 索引のある1カラム | 0.43 ms | 0.56 ms | 0.40 ms |
| 索引のあるカラム＋索引の無いカラム | 4.1〜4.2 ms | 5.3〜9.1 ms | 3.7〜6.9 ms |

**W9: pattern の打ち切り（256 文字ごとに締め切りを見る形）**
- 締め切りを過ぎてから止まるまでの時間（超え）は、中央 6 µs・最大 27 µs だった。毎回見る形でも最大 43 µs、4,096 文字ごとでも最大 49 µs。
- 10,000 字の値に軽い式を当てると、包まないとき 74 µs、包むと 84 µs だった。
- 990 字の式の組み立ては、平均 81 µs だった。
- JDK 25 では `(a+)+$` が重くならない（0 ms）。重いのは `((a+)+)+c` と `(.*a){12}`（F4: A）。

**W10: 最悪の本文（100 カラム × 絵文字 10,000 字。4,001,149 バイト）**
- 1回の INSERT の時間は、PG 36 ms・MySQL 81〜92 ms・MariaDB 29〜38 ms。
- 同時に流したときの1回の時間は、PG 330〜349 ms（10 本）・MySQL 132〜298 ms（5 本）・MariaDB 194〜301 ms（10 本）。

**設計への意味**
- 事前の問い合わせと違反の後の確かめは、それぞれ1文で数 ms に収まる。p95 1 秒の予算の中で小さい。
- pattern の打ち切りの上乗せは小さい。超えは余裕 100 ms に十分に収まる（NFR 要件のレビューの R-05 の手当て）。
- 大きな本文の重さは、アプリより対象DB の側に出る（scalability-design.md の 4節、F1: A）。

## 対応表

| U2 の NFR 要件の ID | この文書の節 | 扱い |
|---|---|---|
| NFR1.4 作成・更新・削除の応答時間 | 1節・2節・4節 | 作りと予算を決めた。合否は Performance Validation |
| NFR1.5 違反の経路の応答時間 | 1節・2節・4節 | 同上 |
| NFR1.6 検証の時間 | 3節 | 締め切りの見方と合格の線を決めた。確かめの入力は F4: A |
| NFR3.4 3種類の対象DB で測る | 4節 | そのまま |
| NFR7.4 verify と CI の時間 | 5節 | そのまま |

## 1. 文の流れと数

書き込みの口が1回だけ借りた接続の中で、次の順に行う。

| 操作 | 流れ（括弧は場合による） | 文の数（確定・戻しを除く） |
|---|---|---|
| 作成 | （事前の問い合わせ）→ セーブポイント → INSERT（主キーだけを返す）→ 確定 | 1〜2 |
| 更新 | （事前の問い合わせ。自分の行を除く）→ セーブポイント → UPDATE → 件数の確かめ → 確定 | 1〜2 |
| 削除 | DELETE → 件数の確かめ → 確定 | 1 |
| 違反の道（作成・更新） | 上の流れ → セーブポイントへ戻す → 確かめの問い合わせ（1文）→ 全体を巻き戻す | 2〜3 |

- 事前の問い合わせは、送られた unique のカラムのうち値が NULL でないものがあるときだけ流す。全部のカラムを1文にまとめる（W1）。

  ```sql
  SELECT (SELECT 1 FROM t WHERE c1 = ? [AND NOT (主キーの条件)] LIMIT 1) AS c0,
         (SELECT 1 FROM t WHERE c2 = ? [AND NOT (主キーの条件)] LIMIT 1) AS c1
  ```

- 違反の後の確かめは、一意なら同じ形の1文で行う。外部キーなら、参照先ごとの `(SELECT 1 FROM 参照先 WHERE … LIMIT 1)` を1文に並べる。
- セーブポイントは、書き込みの文の直前に必ず置く（Q2: A）。往復が1回増えるが、試しでは測れないほど小さい。
- MySQL・MariaDB の `SET time_zone`・`sql_mode` は、接続を作るときに `connectionInitSql` で1回だけ流す（Q3: A）。要求ごとの文には数えない。
- 生成された鍵は、主キーのカラムの名前を渡す形 `prepareStatement(sql, String[] 主キー)` で読む。PG の `RETURN_GENERATED_KEYS` は全部のカラムの値を返すため使わない（W8）。

## 2. p95 1 秒の予算（NFR1.4・NFR1.5）

| 部分 | 見積もり（ふつうの1行の作成） | 根拠 |
|---|---|---|
| 権限の写しの読み取り（内部DB） | 数 ms | U1 と同じ |
| JSON の読み込みと検証（pattern を含む） | 数 ms。最悪で 400 ms（NFR1.6） | W9・W10 |
| 借りる待ち | 0 ms（空きがあるとき）。上限は 3 秒 | プール 5 |
| 事前の問い合わせ | 0.4〜9 ms | W1 |
| 書き込みの文 | 数 ms。4 MiB の本文で 29〜92 ms | W10 |
| 確定 | 数 ms | — |
| 監査の書き込み（内部DB の短いトランザクション） | 数 ms（200 ms を超えたら遅いとみなす） | 既存の `AuditEventListener` |

- 違反の道（`bizWritePoolLimit`）は、セーブポイントへの戻しと確かめの1文が増える。それでも予算の中で小さい。プールの上限に届いたときの借りる待ちが、主な重さになる（scalability-design.md の 3節）。
- 索引の無い unique のカラムは表の全部を読む（100,000 行で 4〜9 ms）。行が多い表では重くなる。既知の制約として記録する（DSL の unique は、DB の一意の索引を前提にしない）。

## 3. pattern の照合の時間（NFR1.6・NFR4.7）

- 照合は、締め切りを見る `CharSequence` の包みで行う。**締め切りは 256 文字ごとに見る**（W9）。値ごとに 100 ms、1要求の合計で 300 ms。合計を使い切ったら、残りのカラムは照合しない。
- 長さの検証（`TOO_LONG`）を先に当て、長すぎる値には照合しない。
- 組み立てた式は、DSL の識別（dslHash）ごとに数を限って取っておく。組み立ては1つ 81 µs で小さいが、100 カラムでは毎回の組み立てを避ける。
- **合格の線**: 締め切りの判定は、注入した時計で決める。実時間は、合計の締め切り 300 ms に「超え」と CI の揺れの分を足した **400 ms 以内**とし、要件の値を緩めない。超えは最大 0.05 ms のため、残りの 100 ms は CI の揺れに使える。
- **確かめの入力（F4: A）**: 重い式として `((a+)+)+c` と `(.*a){12}` を `a` の 30 字の並び＋`!` に当てる。`(a+)+$` は「重くない式」の場合として残す。

## 4. 負荷の試験の場面（NFR1.4・NFR1.5・NFR3.4）

| 場面 | VU と長さ | 中身 |
|---|---|---|
| `bizCreate` | 10 VU・3 分 | 100 カラムの表の FULL のカラム。値は架空 |
| `bizUpdate` | 10 VU・3 分 | VU ごとに別の行の1カラムを変える |
| `bizDelete` | 既定 100 回 | setup で回数以上の行を自分で作る |
| `bizWritePoolLimit` | 10 VU・3 分 | 参照先に無い値での作成（違反の道） |
| `bizCreateLarge`（NFR2.8） | 10 VU・短い回 | 4 MiB 近い本文の作成（成功の道）。上限に届く回と分けて、p95 に大きな本文を混ぜない |

- 判定の決まりは U1 の NFR1.2 のとおり。`iteration_duration` で判定し、1回の繰り返しに要求1つ、トークンは `setup()` で取る。`caffeinate -dims` で包み、使い捨ての環境で流す。
- 対象DB は、U1 の F1: A と同じ表が入る設定で測る。MySQL のメモリの上限は F1: A により Infrastructure Design で決める。
- 3種類の DB で1つずつ流し、結果を並べる。1種類でも満たさなければ Not Met とする。

## 5. verify と CI の時間（NFR7.4）

- B4 の後と Build and Test で、`:backend:cleanTest :backend:cleanIntegrationTest` を付けて実測する。
- 止まった対象DB の結合テストは、1回で 10〜20 秒かかる（reliability-design.md の 5節）。置く DB の種類と数は、U1 と合わせてコード生成の計画で決める。

## 6. 上流との差

- **BR6.1 の表に足す**: CHECK の違反・日時の範囲の外・文字集合・NUL・更新できないビューの番号を足す（Q6: A・F3: A。表は reliability-design.md の 2節）。
- **BR6.2 の切り替え先を先に使う**: 違反の後の確かめを、2つ目のトランザクションではなく、セーブポイントで行う（Q2: A）。外から見える振る舞いは同じ。
- **BR3.5 に日時の秒の端数を足す**: 3種類とも黙って丸める・切り捨てるため、DB の型の精度を超える秒の端数は `TOO_MANY_DIGITS` で拒む（Q6: A）。
- **U1 の「貸すたびに付ける」との差（Q3: A）**: 書き込みのプールは専用のため、通信の待ちと時差の初期化をプールの設定で付け、貸すたびには変えない。
- **NFR1.6・NFR4.7 の確かめの入力（F4: A）**: `(a+)+$` を `((a+)+)+c` と `(.*a){12}` に読み替える。
- **「およそ 18 秒」の読み替え（Q4: A）**: 最悪の時間は「止まる前の時間＋1回分の失敗」で書く（reliability-design.md の 5節）。
- 場面 `bizCreateLarge` を足した（NFR2.8 の形を決めた）。

## 7. 後の段に回すこと

- **Infrastructure Design**
  - F1: A の perf の MySQL のメモリの上限（U1 の F1: A のバッファープールとあわせて決める）
  - F2: A の PG の `REVOKE TEMPORARY` と README
  - 機能設計のレビューの R-06 の表ごとの GRANT
- **コード生成の計画（B4）**
  - SpotBugs の `SQL_` の除外を書き込みのメソッドに広げることの承認
  - 名前つきの書き込みの管理者が、型で注入する内部DB の管理者とぶつからないことの結合テスト
  - NFR 要件のレビューの R-06: 監査に列と値を足す移行の後方互換の結合テスト
  - `AuditMigrationCompatibilityIT`・`AuditSecretLeakIT` などの既存のテストの洗い出し
  - k6 の5つの場面と `bizPrepare` に足す行
- **Observability Setup**
  - NFR 要件のレビューの R-03: 内部DB の大きさの基準の値
  - `ms-pool-pending` が書き込みのプールを拾うかの実測
- **Performance Validation**: NFR1.4・NFR1.5・NFR3.4 の合否の回

## 8. U3 とそろえる点

- U3 の画面の時間の要件は、この文書の書き込みの API の時間（p95 1 秒）を前提にする。
- pattern は画面で当てない。`PATTERN_TIMEOUT` の文を画面が用意する。
- 日時の入力は、秒より細かい値を送らない（Q6: A）。
- 413（4 MiB を超えた本文）の文を画面が用意する。

## Sources

- 承認済みの NFR 要件: `aidlc/spaces/default/intents/261016-business-data-views/construction/u2-businessdata-write/nfr-requirements/performance-requirements.md`（NFR1.4〜NFR1.6・NFR3.4・NFR7.4）・`security-requirements.md`（NFR4.7）・`scalability-requirements.md`（NFR2.8）
- この段の答え
  - `construction/u2-businessdata-write/nfr-design/nfr-design-questions.md`（Q1〜Q7: A）
  - `construction/u2-businessdata-write/nfr-design/nfr-design-followup-questions.md`（F1〜F4: A）
- 機能設計: `construction/u2-businessdata-write/functional-design/functional-spec.md`（3節・8節）・`rules.md`（BR4.2・BR5.1・BR6.2・BR7.2）
- U1 の NFR 設計: `construction/u1-businessdata-read/nfr-design/performance-design.md`（4節）・`nfr-design-followup-questions.md`（F1: A）
- NFR 要件のレビュー: `.aidlc-reviews/nfr-requirements/units/u2-businessdata-write/00d44a7a2452e6b7/1.json`（R-04・R-05）
- コード: `perf/k6/scenarios.js`、`docker/perf/compose.yaml`
- 捨ての試し: リポジトリの外の試しのコードと結果（W1・W8・W9・W10）

## Assumptions & Open Questions

- 前提: 試しの時間には、アプリの処理と HTTP の時間を含めていない。本番の k6 の値はこれより大きい。
- 前提: 監査の書き込みの時間は、既存の測りのとおり数 ms とみなした。
- 依頼者の決定 Q1〜Q7・F1〜F4 はすべて A で、この文書に反映した。
- この文書に固有の、諮る点は無い。
