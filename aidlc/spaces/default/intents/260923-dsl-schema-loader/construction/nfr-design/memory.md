<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T16:07:03Z — U4 のレビューで、本文の大きさを確かめる既存の仕組みはログインの確認より前で動くため、そこでは監査に要る「操作した人」が分からないと指摘された。既存の仕組みに手を足す設計では、その仕組みが要求の流れのどこで動くか（ログインの前か後か）をコードで確かめてから書く。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T16:07:03Z — U5 の NFR 設計で、大きさの判定の順序（文字列にする前に判定）だけが求められていたのに、送り方（バイト列のまま送る）まで変えてしまい、承認済みの機能設計（テキストとして読んで送る）と食い違った。NFR 設計で承認済みの設計と違う作りにするときは、差の一覧に載せ、依頼者に確かめてから書く。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T16:07:03Z — 照合の全体の上限（8 秒）を強制する作りが要るとレビューで指摘されたが、依頼者は上限を作らず、応答しない対象DB では 10 秒の目標を超えること（最悪 23〜28 秒）を許すと決めた。文書は直さず、承認の場で決定を記録する（決定 B）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
