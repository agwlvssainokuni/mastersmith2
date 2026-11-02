<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-02T22:40:00Z — Q1 で依頼者は推奨（ブラウザのタイムゾーン）でなく「利用者ごとに選べる」（B）を選んだ。効く範囲・保存の場所・選べる一覧が決まらないため追加の質問 F1〜F3 で確かめ、すべての画面に効かせ、利用者の表に列を足して保存し、IANA のすべてから選ぶ形になった。夏時間の地域を選べるため、team.md の★（夏時間の確かめを部品の単体テストに限るか）は画面を通す形で解いた（NFR5.1）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
