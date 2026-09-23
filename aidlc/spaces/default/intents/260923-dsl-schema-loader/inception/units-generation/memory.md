<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T11:36:44Z — 対象DB（U1）・DSL の定義（U2）・既定の DSL の生成（U3）は、単独では動かずアプリの中で使う部品なので種別を library とした。API を持つ DSL の管理（U4）だけを service、画面（U5）を ui とした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T11:36:44Z — ステージの手順にある計画の承認（Approve Plan / Revise Plan）は、単位の一覧・種別・依存を載せたまとめの確認（Looks correct / Request changes）で兼ねた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T11:36:44Z — 依存の上では U1 と U2 を並行して作れるが、依頼者の判断（Q4: B）で依存の順に1つずつ作る前提とし、依存の図には並行できることだけを記録した（順序は Delivery Planning）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
