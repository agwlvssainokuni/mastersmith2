<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T15:05:48Z — 部品の候補は Maven Central で最新の版と依存を確かめてから試した。networknt の最新の版 3.0.7 はアプリ全体の Jackson を Spring Boot の版から引き上げるため、Spring Boot と同じ 3.1 系で動く 3.0.6 を選んだ。新しい依存は、推移依存で既存の部品の版を引き上げないかを依存の木で確かめる。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T15:05:48Z — U3 の決定（DSL の上限を 10MB）に合わせて、レビューの済んだ U2 の NFR 要件の文書を書き換えてしまい、レビューが今の中身を確かめない状態になった。依頼者の Request Changes で直し、U1 から順にレビューし直した。別の単位の決定で、レビューの済んだ文書を直す必要が出たときは、先に依頼者に何を変えるかを尋ね、Request Changes を経てから直す。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T15:05:48Z — 想定の規模の既定の DSL の大きさを試算したところ、要件の上限 5MB を超えることが分かった（約 5.3MB）。依頼者は書式を変えずに上限を 10MB に上げることを選び、承認済みの文書は書き換えずに差の一覧を U3 に記録した。数値の上限を決める段では、上限と、機能が作る最大の出力の大きさを突き合わせる。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
