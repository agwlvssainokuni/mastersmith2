<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-22T12:50:00Z — 依頼者の変更の依頼により、「指標を作らない」値（ACCESS_DENIED の件数）を指標の表と警報から外し、監査ログから数える見方として分けて書いた。U2 のログインの失敗の割合も同じ形にそろえた。
- 2026-09-22T12:32:00Z — U3 は独自の基盤を持たないため、質問を作らず、設計の要点を要約として依頼者に確認した; 監視の指標は U1 と重なるものを繰り返さず、U3 に固有のものだけを書いた（U2 の確認の指摘 R-02 を踏まえて）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-22T12:32:00Z — 管理者でない利用者の画面の確認は、本Intentでは利用者を作る機能が無いため配備の確認に入れられない; E2E で管理者でない利用者をテスト用に作る方法を Code Generation で決める。
