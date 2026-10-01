<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-30T20:30:00Z — 実現可能性の評価の段が無いため、最後の管理者の保護の同時性・リフレッシュトークンのまとめての無効化・管理の画面すべての 403 の扱いの3点を ADR-007 にまとめ、確かめる段と切り替え先を書いた（PM の学び）。最後の管理者の保護の守り方（何を排他するか）は質問にせず、機能設計・NFR 設計に回した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-30T20:30:00Z — ページ送りを共通に移す（DQ4 A）ことで招待のコードに手が入り、一覧で外しているパッケージに当たればカバレッジの作業が付く。403 の扱いを骨組みに置く（DQ5 A）ことで既存の3画面とテストの書き換えが今回に入る。どちらも Delivery Planning で見積もる。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
