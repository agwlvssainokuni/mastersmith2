<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-25T14:54:01Z — Q2 A の「最初に監査に手を入れる単位」は、U3 が U2 に依存するため U2 と読み、監査の対象・結果の列の移行を U2 に置いた。計画の確認（Step 4）はまとめの確認に分け方の案を含めて1回で行った。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-25T14:54:01Z — InstanceAppearance の小さなバックエンドを、単位を増やさずに ui の U4 に入れた。単位が少なく済む代わりに、ui の種類では機能設計の決まり（rules）と NFR の設計の文書が作られず、業務規則（既定への置き換え・警告）の記録の置き場が決まっていない（レビューの R-01）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
