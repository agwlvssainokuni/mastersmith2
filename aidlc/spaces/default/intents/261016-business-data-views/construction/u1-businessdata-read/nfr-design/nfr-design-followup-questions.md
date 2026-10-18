# NFR Design の追加の質問 — U1 u1-businessdata-read

捨ての試し（Q1: A、約 30 分）で、設計の前提が3点崩れました。この3点を確かめます。どちらで決めるかによって、レビューの前に成果物を直すことがあります。

## F1. MySQL・MariaDB の重さ（試し T6）

試しの条件は次のとおりです。

- 100,000 行の種（1行 約 1.8KB）
- 10 本同時・プール 5
- アプリと HTTP を含めない

この条件で、次の場面が p95 1 秒を超えました。

- MySQL
  - 件数の1回: 394〜449ms
  - 最初のページ: 1,482ms
  - 最後のページ: 4,186ms
  - 部分一致: 4,096ms（すべて借りる待ちの時間切れ）
- MariaDB
  - 部分一致: 1,976ms
- PostgreSQL
  - 部分一致: 1,168ms（ほかの場面は 4〜35ms）

MySQL・MariaDB はバッファープールが既定の 128MB で、表（532MB・372MB）が入りきっていません。

A. アプリの作りは変えない（推奨）。
- Performance Validation の使い捨ての対象DB の設定を、表が入る大きさ（バッファープール）にし、その設定を記録してから測る。
- それでも超える場面は Not Met として、その段で扱いを諮る（目標は緩めない）。

B. 件数に上限を置く（例: 10,001 行で打ち切り、「10,000 件以上」と出す）。件数の重さを下げるが、部分一致の重さは残る。要件・機能設計との差になる。

C. 今の時点で、MySQL・MariaDB の重さを既知の制約として受け入れる。

X. Other (please specify)

[Answer]: A

## F2. 応答しない MySQL の時間（試し T7）

つないだ後に相手が止まると、失敗するまでの時間は次のとおりでした。MySQL だけが、R-01 の読み替え「25 秒以内に 503」を満たしません。

| DB | 失敗までの時間 |
|---|---|
| PostgreSQL | 25.0 秒 |
| MariaDB | 25.0 秒 |
| MySQL | 50.0 秒（ドライバーの設定を変えても同じ） |

A. 貸すたびに、目的に合わせて接続の通信の待ち（`setNetworkTimeout`）を付ける（推奨）。
- 一覧・詳細では「上限＋5 秒」にする。
- コード生成の前に試しで確かめる。
- 通らなければ B にする。
- DSL の生成の 20 秒の上限には影響しない。

B. MySQL では「50 秒以内に 503」を既知の制約として記録する（R-01 との差）。

X. Other (please specify)

[Answer]: A

## F3. MySQL・MariaDB の `bit` と空間の型（試し T2）

この2つの型は、文字にしても画面に出せる値になりません。

A. BINARY と同じ扱い（読まない・検索と並べ替えの対象にしない・画面では読めない印）に寄せる。BR7.1 の分類との差を記録する（推奨）。

B. 表示の形（`bit` は 0/1 の文字、空間の型は WKT の文字など）を設計に足す。

X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

Q1〜Q6 のまとめ（`nfr-design-questions.md` で Looks correct）に、F1〜F3 の決定と追加の試しの結果を足した形で書く。

**F1: A（重さ）**
- アプリの作りは変えない。
- Performance Validation の使い捨ての対象DB は、MySQL・MariaDB のバッファープールを表が入る大きさに設定し、その値を記録してから測る。値は表の大きさの実測から、`docker/perf/compose.yaml` のメモリの上限とあわせて Infrastructure Design・Performance Validation で決める。
- 超えた場面は Not Met として諮る。今回の試しの値は基準として並べる。
- 深いページは、主キーを先に読んでから行をつなぐ形を採る。

**F2: A（応答しない対象DB）**
- 貸すたびに `setNetworkTimeout(10 秒)` を付け、返すときに元へ戻す。戻す処理の失敗は口の中で受け、クラスの名前だけを WARN に出す。
- 試しでは、失敗までの時間が MySQL 20.0 秒・PG 15.0 秒・MariaDB 10.0 秒で、3種類とも「25 秒以内に 503」を満たした。
- 壊れた接続はプールが捨てた。普段の要求への上乗せは 1 回あたり 1ms 未満だった。

**F3: A（型）**
- MySQL・MariaDB の長さ 2 以上の `bit` と空間の型は、BINARY と同じ扱いにする。`bit(1)` は BOOLEAN のまま。
- BR7.1 との差を記録する。

**試しで決まったほかの形**
- 時差: PG は JDBC の型（`timetz` は `AT TIME ZONE 'UTC'` で比べる）。MySQL・MariaDB は、貸すたびに `SET time_zone='+00:00'`（Q2: A の2つ目の形）。
- OTHER: `=` を持たない型（PG の `json`・`xml`・`point`、MySQL の `json`）は、EQUALS の対象から外す。
- SQLState の表に HY000/1525 を足す。
- 長い値は `SUBSTRING` で絞る。ヒープは 10 本で残る量が約 23MB。

Does this all look correct before I generate the NFR design?

- Looks correct
- Request changes

[Answer]: Looks correct
