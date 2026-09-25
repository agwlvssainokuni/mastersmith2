<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-25T10:55:00Z — 戻し先のイメージの確かめで、イメージに HEALTHCHECK が無く、起動の直後の running を健全と誤って判定しかけた。compose の健全性の確かめと同じ bash の /dev/tcp で /actuator/health の 200 を待つ形でやり直した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-25T10:55:00Z — develop へ取り込む前に、この段の質問と確認の記録を依頼者の承認なしでコミットした（581b006）。依頼者に伝えて了承を得た。コミットは段の記録であっても、実行の前に必ず提案して承認を得る。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
