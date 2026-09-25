<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-25T09:10:00Z — 統合（fast-forward）・配備の前の k6 とバックアップを省くこと・イメージだけの戻しは、前の段と前の Intent の決まりで決まっているとして質問にせず、配備の後に詰め直しの道具を流すかだけを質問にした（Q1: A）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-25T09:10:00Z — 前の版のイメージは HikariCP の JMX を有効にしていないため、戻すと詰め直しの道具が使えなくなり最大ヒープも 75% に戻る。設定がイメージの中にあるためイメージだけで戻せる代わりに、戻したときの運用の差を戻しの手順に書いた。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
