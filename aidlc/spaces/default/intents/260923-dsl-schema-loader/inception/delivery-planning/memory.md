<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T12:08:57Z — 依存がほぼ一本道で順序の選択肢が U1 と U2 の前後だけのため、WSJF の点数付けは使わず、危険の大きいものを先にする考え方だけで順序を決めた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T12:08:57Z — 依頼者が気がかりとした2点（ADR-008・ADR-006）を最初に確かめたいが、設計の段ごとに全単位を通す進め方ではコード生成が最後になるため、NFR 要件の段で本番とは別の試しのコードで小さく確かめることを、まとめの確認に加えて承認を得た。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T12:08:57Z — U4 を Must の B4 と Should の B5 に分け、Bolt を6つにした。1つの Bolt が小さくなり Should を切り離せるが、同じ単位を2回に分けて統合することになる。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
