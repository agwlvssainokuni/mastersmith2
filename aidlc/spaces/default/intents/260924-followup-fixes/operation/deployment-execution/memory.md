<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
- 2026-09-25T02:35:20Z — 戻し方の前提（Q2: A）は、前の版のイメージを新しい .env と一時のボリュームで起動して健全になることで確かめた。配備した内部DB のデータでの起動は確かめていない（スキーマの変更が無いため）。
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
- 2026-09-25T02:35:20Z — develop への取り込みは git switch ではなく git fetch . fix/260924-followup-fixes:develop（fast-forward だけを許す）で行い、その後 develop に切り替えた。作業ツリーのワークフローの記録（監査ログ）の未コミットの変更が2つのブランチで違い、switch で持ち越せないため。
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
