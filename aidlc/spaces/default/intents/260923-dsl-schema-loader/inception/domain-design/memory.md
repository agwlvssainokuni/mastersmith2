<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T11:11:12Z — 実現可能性の評価（Feasibility）の段が無いため、ADR-010 に実現できるかの判断と、確かめる条件と持ち主の段（NFR 要件・最初の Bolt・Build and Test）をまとめた。ADR-006（既定の候補にしない指定）と ADR-008（位置の対応表）には、確かめられなかったときの切り替え先を書いた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T11:11:12Z — 既存の部品は、前の Intent の Domain Design の部品名（UserAccount・AccessControl・AuditLog・AppFrame・ApiClient）で書いた。今回の Reverse Engineering で作り直したコード知識ベースの部品名（user・access・audit・frontend-* など）とは対応表を置いていない。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T11:11:12Z — 後続の Intent が dsl に依存し dslmanage に依存しないよう、ActiveDslModel は保持と提供口だけを持ち、起動時の読み込みと差し替えを DslLifecycle に任せた。依存の循環は無くなるが、モデルの中身が起動の順序に依存する。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
