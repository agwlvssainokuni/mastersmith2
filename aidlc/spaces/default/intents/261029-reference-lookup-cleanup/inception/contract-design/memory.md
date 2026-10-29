<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
- 2026-10-29T16:35:33Z — 要件 FR3.2a の監査の理由「REFERENCE_NOT_FOUND」は、コードで確かめると監査の理由が BusinessDataAuditReason.BUSINESS_REFERENCE_NOT_FOUND、項目の誤りの理由が BusinessDataFieldErrorReason.REFERENCE_NOT_FOUND だった。C3・C4 では既存の名前のまま書き、要件の名前はこの理由を指すと読んだ。
- 2026-10-29T16:35:33Z — Q1: A（同じ応答を 403 ACCESS_DENIED）により、ストーリーの X13 の読み替えが要らなくなった。DSL が無いときも参照先の検索 API は 403 にし、定義の API の 404 とは揃えないことを前提として置いた。
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
