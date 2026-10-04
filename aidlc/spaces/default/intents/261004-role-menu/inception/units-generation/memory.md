<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-04T13:10:00Z — 計画の確認（Step 4）は、まとめの確認に分け方の計画（単位・種別・大きさ・依存）を含めて1回で行った（前の Intent と同じ）。配備の形は1つの WAR で決まっているとして質問にしなかった。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-04T13:10:00Z — 依頼者の答え（UQ1: A・UQ3: A）で、6つの案に横断の準備（API の分類の印・ESLint の制限）を独立させた7単位になった。横断の単位 U1 は主のストーリーを持たず、US1.1 の AC1.1.15 と要件 C5 を受け持つ。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-04T13:10:00Z — `role`（U4）は XL のまま1つの単位で設計を通し、コード生成の計画で Bolt に分ける（UQ2: A）。単位と境界の契約が少なく済む代わりに、Delivery Planning で Bolt の切れ目を決める必要がある。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-04T13:10:00Z — U1 で既存の API に印を付けると packagesJudgedByTotal のパッケージの本体に手が入りうる。手を入れるパッケージは Delivery Planning で実測して見積もる。
