<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-04T04:43:58Z — 配備の前の複写で、初期管理者が停止中（印あり）と分かった。依頼者は「止めた・印を外したことがある」（Q1: C）と答えていたが、今も停止中かは複写で初めて分かった。救済が働く見込みとして了承を得て（F1: A）入れ替え、救済の WARN 1 行（条件 SUSPENDED）・監査の INITIAL_ADMIN_RESCUED 1 行・2回目の起動では働かないことを確かめた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-04T04:43:58Z — 計画では配備の前の複写をアプリを止めた後に取り、入れ替えまでの間に救済の見込みを確かめる形だったため、依頼者の了承を待つ間もアプリは止まっていた（04:39:32〜04:41:11、約 1 分 39 秒）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-04T04:43:58Z — 停止中の初期管理者を救済で戻すと、有効な管理者は 2 人になる。わざと止めていたのであれば .env を先に替える必要があったが、依頼者はこのまま戻すことを選んだ。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
