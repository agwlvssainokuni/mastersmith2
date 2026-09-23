<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T23:36:58Z — U2 のレビューで、今の SecurityConfig は /api/** の外をすでにログインなしで通すため、JSON Schema の静的なファイルを許す設定は要らないと指摘された。公開の範囲を設計するときは、既存のセキュリティの決まり（許す道と既定の扱い）をコードで確かめてから書く。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T23:36:58Z — U4 のレビュー役に決定 C（既存のテスト3件は直さない）の根拠（テスト用の決まり PublicApiTestRules）を伝えず、1回目は NOT-READY になった。前の段の承認で決めたことをレビューの前提に書くときは、根拠のファイルの場所も伝える。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T23:36:58Z — レビュー役の書いた表のセルに `|` や「 / 」で区切った列名が入り、記録が2回受け付けられなかった（書き直しの依頼でファイルが消え、やり直しが要った）。レビューの依頼には、セルの中に `|` を書かず並べるときは「・」でつなぐことを最初から書く。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
