# Domain Design の質問（Intent 261114-internal-db-postgresql）

## 決まっていること（質問にしない）

- 要件定義（承認済み）: 既定は H2 のまま、接続先に PostgreSQL を指定したときだけ PostgreSQL で動く。対応しない種類の接続先は起動を止める。H2 の適用済みの移行は書き換えない。PostgreSQL でも振る舞い（応答・違反の区分・待ちの上限・時刻・大きなバイナリ）を H2 と同じに保つ。インスタンスは1つの前提のまま。データの移し替えは範囲の外。
- Practices Discovery（承認済み）: PostgreSQL の結合テストは H2 の全件とは別のバリエーションとして作り、明示の指定を付けたときだけ流す。
- 設計担当が決めて記録すること（違えば承認の場で直せます）:
  - 内部DB の種類を接続先から決め、種類ごとの設定（移行の置き場・接続ごとの待ちの上限など）を選ぶ部品を、新しい部品として1つにまとめる（種類の判断を1か所に閉じ、業務処理の層は種類で分岐しない）。
  - 既存の機能（auth・user・invitation・role・group・dslmanage・audit）の持ち主は変えない。表とエンティティの持ち主も今のまま。
  - 対象DB の部品（targetdb・businessdata）には手を入れない。

以下は、作り方に複数の選び方があり、後の手間と危険が変わる点だけを尋ねます。

## Q1. [設計] PostgreSQL の移行（表を作る SQL）の置き場

今の移行 V1〜V14 は1つの置き場にあり、H2 にしか通らない書き方（`BINARY LARGE OBJECT`・`STORED` の無い生成列・`ON DELETE RESTRICT` など）を含みます。H2 の適用済みの移行は書き換えられません。

推奨: A。H2 の移行に一切触れずに済み、PostgreSQL の移行を PostgreSQL の書き方で素直に書けるためです。後の Intent で表を変えるときは両方に同じ版の番号で足す手間が増えますが、これは要件の FR2.3 ですでに受け入れています。

A. H2 の移行は今の置き場のまま残し、PostgreSQL 用に V1〜V14 と同じ版の番号の移行を別の置き場に一式置く。起動時に内部DB の種類で置き場を選ぶ
B. 両方に通る書き方の部分を共通の置き場に移し、違う部分だけを種類ごとの置き場に分ける（H2 の移行のファイルを動かす・分けることになり、適用済みの検証との整合を確かめる手間が要る）
X. Other (please specify)

[Answer]: A

## Q2. [設計] H2 にしか通らない SQL（4 か所）の直し方

`FINAL TABLE (UPDATE ...)`（自分の設定の保存）、`MERGE INTO ... KEY`（作業ロールの保存・DSL のプレビューの置き場）、`CAST(? AS ...)` を含む `INSERT ... SELECT`（DSL の適用の履歴）が PostgreSQL では通らない見込みです。標準の `MERGE INTO ... USING` は、すでにログインの状態の行で H2 でも使っています。

推奨: A。repository の中で DB の種類による分岐が要らず、H2 の結合テストの全件（普段の `verify`）でそのまま確かめられるためです。PostgreSQL のときだけ通るコードが増えないので、パッケージごとのカバレッジの下限（NFR6）も守りやすくなります。両方で同じ結果になることは、設計の段で捨ての試しで確かめます。

A. H2 と PostgreSQL の両方に通る書き方（標準の `MERGE INTO ... USING`、`UPDATE` と `SELECT` を分ける、など）に書き直し、DB の種類で分岐しない
B. DB の種類ごとに SQL を書き分け、repository の中で種類を見て選ぶ（H2 の今の SQL は変えない）
X. Other (please specify)

[Answer]: A

## Q3. [設計] 参照のある行を消したときの違反（PostgreSQL の `ON DELETE RESTRICT`）

下調べで、PostgreSQL の `ON DELETE RESTRICT` の外部キーでは、参照のある行（割り当てのあるロールなど）を消すと SQLState 23001 になり、今の区分の部品（23503・23506 だけを見る）では「想定外」に分けられることが分かっています。外部キーを指定なし（NO ACTION）にすると 23503 になり、今の区分のままで当たります。

推奨: A。区分の部品（role・group の2つ）を変えずに済み、H2 と同じ応答になるためです。1つの文の中では RESTRICT と NO ACTION の結果は同じで、違うのは確かめる時点（文の終わりか、すぐか）だけです。今のアプリは遅らせた確かめ（DEFERRABLE）を使っていません。

A. PostgreSQL の移行では外部キーを指定なし（NO ACTION）で作り、区分の部品は変えない（制約の名前は H2 と同じにする）
B. PostgreSQL の移行でも `ON DELETE RESTRICT` のまま作り、区分の部品（role・group）に 23001 を足す
X. Other (please specify)

[Answer]: A

## Q4. [品質] PostgreSQL の結合テストの作り方

要件の FR5.1 で、PostgreSQL では「内部DB を使う機能ごとの代表の API の流れ1本ずつ」と、違反・待ち・移行などの固有の振る舞いを確かめることにしました。どの形で作るかです。

推奨: A。今の H2 の結合テストをそのまま PostgreSQL に向けると、H2 にしか通らないテストの部品（待ち合わせに使う `INFORMATION_SCHEMA.SESSIONS`、H2 の Java のトリガー、`MERGE ... KEY` の準備の SQL など）を多く書き直すことになるためです。PostgreSQL 用に絞った新しいテストなら、明示して流すときの時間も短く保てます。

A. PostgreSQL 用の結合テストを新しく作り、機能ごとの代表の流れと固有の振る舞いだけを置く（既存の H2 のテストは変えない）
B. 既存の H2 の結合テストのうち代表のクラスを選び、内部DB の種類を切り替えて PostgreSQL でも流せる形にする（テストの部品の H2 に頼る書き方を直す）
X. Other (please specify)

[Answer]: A
