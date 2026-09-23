# NFR Design — U1 対象DB（u1-target-db）— 質問

この段では、U1 の非機能の要件（`construction/u1-target-db/nfr-requirements/`）を満たす作りの方針を決めます。前の段から、次の2点が引き継がれています。

- 照合（U4）だけ、接続 3 秒・問い合わせ 5 秒で打ち切る。生成は接続 5 秒・問い合わせ 20 秒（U1 の NFR1.1・NFR1.2、U4 の NFR1.9）。接続の待ち時間（HikariCP の `connectionTimeout`）はプールに1つの値で、呼び出しごとには変えられない（U1 のレビューの R-01）
- メタデータの問い合わせの方式と回数（テーブルの数に比例させない。正常なときの読み取りは 15 秒以内。U1 の NFR1.3）

問い合わせの待ち時間（`Statement.setQueryTimeout`）は呼び出しごとに変えられます。

---

## Q1. 照合の接続の待ち時間の作り

A. プールの接続の待ち時間を、生成・照合とも 3 秒にそろえる（プールは1つのまま。生成の接続の待ちが 5 秒から 3 秒に短くなる。手元の PC とコンテナの間なら 3 秒で足りる）。問い合わせの待ち時間だけ、生成 20 秒・照合 5 秒で分ける
B. プールを2つにする（生成用: 最大 5・接続の待ち 5 秒、照合用: 最大 1・接続の待ち 3 秒。どちらも既定の候補にしない）。数値は要件のとおりだが、接続の Bean と設定が2つになる
C. プールは1つ（接続の待ち 5 秒）のまま、照合は別のスレッドで接続を借り、3 秒で待つのをやめる（待ちをやめた後も借りる処理は裏で続くため、作りが込み入る）
X. Other (please specify)

[Answer]: A

## Q2. メタデータの読み方

A. DB の種類ごとに、情報スキーマ（MySQL・MariaDB は `information_schema`、PostgreSQL は `information_schema` と `pg_catalog`）へ、スキーマ全体をまとめて読む問い合わせを種類ごとに1回ずつ行う（テーブル・カラム・主キー・外部キー・コメントで 4〜5 回。スキーマ名は `PreparedStatement` の値で渡す）
B. JDBC の標準の `DatabaseMetaData` を使う（DB の種類の違いをドライバーが吸収するが、主キー・外部キーはテーブルごとに問い合わせる形になり、100 テーブルで約 200 回になる）
X. Other (please specify)

[Answer]: A

## Q3. 生成と照合で待ち時間を分ける渡し方

A. 読み取りの口に「目的」（生成 `GENERATE`・照合 `COMPARE`）を渡し、U1 が目的に合った待ち時間を使う（数値は U1 の設定に置く。契約 C1・C3 の `readSchema` に引数が1つ増える。承認済みの契約は書き換えず、差を記録する）
B. 読み取りの口に待ち時間そのものを渡す（呼び出し側が数値を持つ。U4 が U1 の都合を知ることになる）
X. Other (please specify)

[Answer]: A

---

## Consolidated Summary Confirmation

- **接続の待ち**（Q1: A）: 対象DB のプールは1つ（`targetDataSource`、既定の候補にしない、読み取り専用、最大 5・最小 0、使わない接続は 60 秒で閉じる）。接続の待ちは生成・照合とも **3 秒**。承認済みの U1 の NFR 要件の NFR1.1（5 秒）は書き換えず、差をこの段の成果物に記録する
- **問い合わせの待ち**: 生成 20 秒・照合 5 秒（`Statement.setQueryTimeout`、目的ごと）
- **目的の渡し方**（Q3: A）: 読み取りの口を `readSchema(目的)`（`GENERATE`・`COMPARE`）にし、目的ごとの待ち時間は U1 の設定（`mastersmith.target-db.*` の下、既定値つき）に置く。承認済みの契約 C1・C3（引数なし）は書き換えず、差を記録する
- **メタデータの読み方**（Q2: A）: DB の種類ごとの読み手（MySQL・MariaDB 用、PostgreSQL 用）が、情報スキーマへ種類ごとにまとめて1回ずつ問い合わせる（テーブルとビュー、カラムとコメント、主キー、外部キー。4〜5 回）。スキーマ名はプレースホルダーの値で渡し、識別子を SQL に組み込まない（NFR6.1 の引用符の決まりは、識別子を組み込む問い合わせが要るときだけに使う）。結果を U1 の写し（TargetSchema）に揃える
- **失敗の見分け**: 接続の待ちの打ち切りと問い合わせの打ち切りは TIMEOUT、認証・接続の失敗は CONNECTION_FAILED。例外の文言・接続先は結果とログに出さず、原因の種類だけを WARN で出す
- **残る危険の表**（U1 のレビューの R-02）: 最悪の待ちは、生成で 3 秒＋20 秒×問い合わせ 4〜5 回、照合で 3 秒＋5 秒×4〜5 回。照合は 10 秒を超えうるため、照合の全体に 8 秒の上限を置き、超えたら残りの問い合わせをやめて「照合できなかった」の警告にする
- **成果物**: U1 は library のため、security-design.md・logical-components.md・traceability.json を作る

Does this all look correct before I generate the artifact?

- Looks correct
- Request changes

[Answer]: Looks correct
