# NFR Design の追加の質問 — U2 u2-businessdata-write

捨ての試し（Q1: A、W1〜W12、約 35 分）では、Q2: A・Q3: A・Q5: A の形が3種類の DB で通り、Q4: A の時間も実測できました。崩れた前提が4つあるため、成果物を書く前に確かめます。

## F1. 4 MiB 近い同時の書き込みで、perf の MySQL（768m）が止まる

**試しの結果**
- 10 本の同時の INSERT で、MySQL が OOMKilled になった（2回とも再現）。
- 書き込みのプールの上限の 5 本では止まらなかったが、メモリは最大 742/768 MiB（97%）まで使った。

**ほかの決まりとの関係**
- NFR2.7 の「満たせなければプールを 10 に上げる」案をとると、止まる。
- U1 の F1 で表が入るバッファープールにすると、さらにメモリが要る。

A. 書き込みのプールの既定は 5 のままとし、MySQL でプールを 10 に上げる案は採らない（推奨）。
- perf の MySQL のメモリの上限は、U1 の F1 とあわせて Infrastructure Design で余裕を持った値に決め、記録する。
- 利用者の対象DB でも起きうるため、既知の制約として README に書く。

B. perf の MySQL は 768m のままとし、NFR2.8 の場面を MySQL だけ小さくする。差を記録する。

C. 今の形のまま Performance Validation で測り、止まったら Not Met として、その段で諮る。

X. Other (please specify)

[Answer]: A

## F2. PG の書き込みのアカウントが一時の表を作れる

PG は既定で PUBLIC に TEMP の権限を与えています。そのため、project.md の Mandated「DDL の権限を与えない」と食い違って読めます。

A. 見本の対象DB の初期化で、`REVOKE TEMPORARY ON DATABASE … FROM PUBLIC` を行う。書き込みのアカウントでは一時の表を作れないことを、結合テストで確かめる。利用者の対象DB での手順を README に書く。持ち主の段は Infrastructure Design（推奨）。

B. 一時の表は、対象のスキーマへの DDL ではないとして受け入れ、Mandated の読み方として記録する。

X. Other (please specify)

[Answer]: A

## F3. 更新できないビューへの作成と、文字列の中の NUL

DB は、更新できないビューへの作成を 55000・HY000/1471 で拒み、PG は文字列の中の NUL を 22021 で拒みます。どちらも今の分け方に当たらず、500 になります。

A. 更新できないビューと NUL のどちらも、409 `BUSINESS_SAVE_REJECTED`（カラムを示さない）にし、BR6.1 の表に足す（推奨）。

B. ビューは 409 `BUSINESS_TARGET_DB_MISMATCH` にし、NUL は検証で 400 `INVALID_VALUE` として先に拒む（3種類の DB で同じ扱い）。

X. Other (please specify)

[Answer]: A

## F4. pattern の時間の確かめの入力

承認済みの NFR1.6・NFR4.7 の確かめの入力 `(a+)+$` は、JDK 25 では重くならず、0 ms で終わります。重くなるのは `((a+)+)+c` と `(.*a){12}` です。

なお、打ち切り（256 文字ごとに締め切りを見る）は、締め切りを過ぎてから最大 27 µs で止まりました。

A. 確かめには `((a+)+)+c` と `(.*a){12}` を使う。`(a+)+$` は「重くない式」の場合として残す。承認済みの要件との差として記録する（推奨）。

B. 重い式は、テストの中で時間の上限を超えることを確かめてから選ぶ形にする（式の名前を要件に固定しない）。

X. Other (please specify)

[Answer]: A

## Consolidated Summary Confirmation

`nfr-design-questions.md` の Q1〜Q7 のまとめ（Looks correct）に、試しの結果と F1〜F4 の決定を足したものです。

**試しで通った形**
- Q2: A。セーブポイントと READ COMMITTED で、3種類の DB とも違反のカラムが分かった（同時の重なりも含む）。
- Q3: A。書き込みのプールの設定で、通信の待ち 10 秒と `connectionInitSql`（`SET time_zone`・`sql_mode`）を付ける。
- Q5: A。確定の途中で止めると、失敗の応答なのに行は保存されていた。
- Q4: A。止まった所ごとの失敗までの時間は、次のとおり。最悪の時間は「止まる前の時間＋1回分」で書く。

| 止まった所 | 失敗までの時間 |
|---|---|
| 借りるとき | 3 秒 |
| 文 | 10〜20 秒 |
| 確定・戻し | 10〜20 秒 |

- Q6: A の SQLState の表を足す（CHECK の違反・日時の範囲の外・文字集合）。秒の端数は3種類とも黙って丸めるため、検証で拒む。DECIMAL の小数の桁あふれも、検証で拒む（BR3.5）。
- 生成された鍵は、主キーの名前を渡す形で読む。
- 失敗した接続は、管理者が返した後に捨てる。
- pattern は 256 文字ごとに締め切りを見る。
- アプリのヒープは、10 本で約 80 MB。

**F1: A**
- 書き込みのプールは 5 のままにし、MySQL でプールを 10 に上げる案は採らない。
- perf の MySQL のメモリの上限は、U1 の F1 とあわせて Infrastructure Design で決める。
- 既知の制約として README に書く。

**F2: A**
- 見本の対象DB の初期化で、PG の `TEMPORARY` を PUBLIC から取り上げる。
- 書き込みのアカウントで一時の表を作れないことを、結合テストで確かめる。
- 利用者の DB での手順を README に書く（Infrastructure Design）。

**F3: A**
- 更新できないビューへの作成と、文字列の中の NUL は、409 `BUSINESS_SAVE_REJECTED` にする。
- BR6.1 の表に足す。

**F4: A**
- pattern の時間の確かめは、`((a+)+)+c` と `(.*a){12}` で行う。
- `(a+)+$` は重くない式の場合として残し、要件との差を記録する。

Does this all look correct before I generate the NFR design?

- Looks correct
- Request changes

[Answer]: Looks correct
