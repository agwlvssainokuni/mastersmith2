<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
- 2026-10-16: 入れ替えの前の複写を読み取りで開く道具は、リポジトリの外の小さな Java の道具（H2 2.4.240 の JDBC、ACCESS_MODE_DATA=r）にし、DSL の最も長い行・初期管理者の停止と印・T0 以降の監査の種類ごとの件数を、値を表示せずに1つの道具で数えた。配備の前と後の両方の複写に同じ道具を使えた。
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
- 2026-10-16: 依頼者の決定で、この段の配備の記録をコミットしてから main を fast-forward した（記録も main に入る）。代わりに、取り込みと push の結果は main の後の develop だけのコミットで追記する。
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
