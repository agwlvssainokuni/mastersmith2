<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-03T00:00:00Z — Domain Design の部品16個のうち WorkRoleSelection は今回変えない既存の部品だが、ホームが読むため U5 の持つ部品に含めた（変えない・読むだけと明記）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-03T00:00:00Z — 6単位（Q1: A）にし、変換の純粋な部品（U1）と対象DB の型の直し（U3）を別にした。単位が増える代わりに、確かめ方（性質ベース・3種類の結合）の違いを単位ごとに閉じられる。
- 2026-11-03T00:00:00Z — U5 を U6（アイコン素材）に頼らせた（上のバーのロゴ用）。ロゴは Should のため、U6 の完了を待たなくても U5 の Must は進められるが、依存の辺としては残した。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
