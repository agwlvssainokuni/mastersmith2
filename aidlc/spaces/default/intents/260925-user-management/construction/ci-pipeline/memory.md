<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-28T16:00:00Z — Dependabot の gradle の実行の failure は、composite build のサブモジュールの中の依存を更新できないことが原因だった。サブモジュールを Gradle の composite build で組むときは、dependabot.yml の gradle の設定に exclude-paths（vendor/**）を置く。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-28T16:05:00Z — Dependabot alerts が無効で脆弱性の知らせが届いていないと分かったが、依頼者の決定（Q2: B）で無効のままとし、脆弱性の関門は OSV-Scanner だけとした。team.md の「High 以上の知らせは次の Bolt の前に取り込む」との差を quality-gates.md に記録した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
