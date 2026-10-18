<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-18T12:00:00Z — U1・U2・U3 で捨ての試しを先に行い、前提が崩れた点（U1 は MySQL・MariaDB の重さ、応答しない MySQL の 50 秒、bit と空間の型。U2 は perf の MySQL の OOMKilled、PG の TEMP の権限、更新できないビュー、JDK 25 で重くならない式。U3 は測り終わりの時点、T3 が代わりの案にならない）を追加の質問（F）で依頼者に確かめてから文書に反映した。
- 2026-10-18T14:00:00Z — U2・U3 では試しを文書より先に流し、前提が崩れた点を確かめてから文書を書いた（U1 では書いた後に諮り、保存し直しが要った）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-18T12:20:00Z — 追加の質問のファイルを分けたとき、レビューの依頼の確かめが単位によって本体の質問のファイルと追加の質問のファイルのどちらにもまとめの確認を求め（U1 は追加、U3 は本体）、まとめの確認をもう一度取り、成果物を保存し直した。
- 2026-10-18T14:30:00Z — U2 の書き込みのプールは、通信の待ちと時差・sql_mode を接続を作るときに付ける形（Q3: A）にし、U1 の「貸すたびに付けて戻す」と違う。U1 の R-03・R-04 の誤りを避けるため。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-18T15:20:00Z — 各単位のレビューは READY だったが Major が残った（U1: 読む側の型の誤りの 400・非検査の例外、U2: デッドロックなどの分類の抜け、U3: 書き込みの 5xx の分け方と二重の作成の手当て、U4: menuOwner の選択子・鍵の関数の置き場、U5: 一時ファイルの一覧・PERF_DB_URL の確かめ）。その場で直さず承認の場へ持ち越した。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-18T15:22:00Z — Infrastructure Design へ: perf の MySQL・MariaDB のメモリとバッファープール（U1 F1・U2 F1）、PG の TEMP の取り上げ（U2 F2）、E2E 180 の対象DB と 040 の前提の衝突・専用のスキーマ・管理者の資格情報の渡し方（U3）。
- 2026-10-19T00:30:00Z — 承認の場の決定（Approve）: 各単位のレビューの Major（U1 R-01 読む側の型の誤り・R-02 非検査の例外、U2 R-01 デッドロックなどの分類、U3 R-01 書き込みの 5xx の分け方・R-02 二重の作成の手当て、U4 R-01 menuOwner の選択子・R-02 鍵の関数の置き場、U5 R-01 一時ファイルの一覧・R-02 PERF_DB_URL の確かめ）は文書を直さず、レビューの推奨の方向でコード生成の計画で直す。U2 の文書は U1 の R-01・R-02・R-04 を先に避けた形のままとする。
