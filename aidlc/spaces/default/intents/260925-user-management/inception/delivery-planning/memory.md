<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-25T15:31:58Z — 体制は1人のチーム（Q4: A）、進め方は設計の段ごと（Q3: A、既定）のため、進め方と体制の設定は既定のまま書き込まなかった。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-25T15:31:58Z — Q2 B の選択肢に示したまとめ方の例（U1・U3、U2・U7、U8・U4、U5・U6）を依存の図と突き合わせずに出し、Bolt どうしがお互いを先に要する形になっていた。追加の質問 F1 で5つの Bolt に選び直した。まとめ方の選択肢は、出す前に依存の図の上で順に並べられるかを確かめる。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
