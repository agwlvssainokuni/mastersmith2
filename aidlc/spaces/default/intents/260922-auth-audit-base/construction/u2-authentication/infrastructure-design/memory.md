<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-22T12:25:00Z — コンテナのタイムゾーン（Asia/Tokyo）は全単位に効く設定だが、削除の時刻を持つ U2 の段で決めた; 保存する時刻は UTC の時点とし、判定がタイムゾーンで変わらないようにした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-22T12:25:00Z — 確定回答 Q2 により、U1 の基盤の設計のコンテナの CPU の上限（2）を U2 の要件のために 4 に上書きした; U1 の文書は確認済みで書き換えないため、U2 の infrastructure-specification.md 4章に優先を明記し、Code Generation の計画で compose に反映する。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
