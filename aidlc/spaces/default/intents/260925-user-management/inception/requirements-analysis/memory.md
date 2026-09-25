<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-25T12:58:38Z — 依頼文の「ロードマップ」は、依頼者に確かめて参考資料の要件のドラフト（5.8・5.9・7・8・9 章）を読んだ。依頼文・ロードマップ・project.md の固い制約で決まっている点は質問にせず冒頭に書き、質問は利用者の状態の持ち方・招待の期限と操作・送信の失敗・入力項目・登録の完了の流れ・既定の値・言語の範囲・パスワード変更後・監査・設定の不足の 12 問にした。
- 2026-09-25T13:19:06Z — Q5 の「ユーザレコードの氏名にはメアドを入れておき」は、Q1 で招待を別の表に持つことにしたため、登録の完了の画面の氏名の初期値として読んだ（前提 A2）。
- 2026-09-25T13:19:06Z — Q11 で管理者の招待の操作を選ばず、登録の完了とパスワード変更だけを選んだため、DSL の管理者の操作を残している今の監査と食い違いうるとして追加の質問 F1 で確かめ、招待・送り直し・取り消しを残すことになった。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-25T13:19:06Z — 応答時間（招待 5 秒・ほか 1 秒）は質問で尋ねず [assumption] として要件に置き、NFR 要件の段で確かめる形にした。レビューで出典の RQ4 が誤解を招くと指摘された（R-01）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
