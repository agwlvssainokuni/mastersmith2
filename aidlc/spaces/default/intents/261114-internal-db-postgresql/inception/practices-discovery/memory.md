<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-11-14T12:57:11Z — Q2 の A の「CI では必ず流す」は、Q1・Q7 の「PostgreSQL の結合テストは明示したときだけ流す」と合わせて、「明示して CI で流したときは飛ばさない」と読んだ。依頼者には統合の前にこの読みを伝え、承認の場で直せることを示した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-14T12:57:11Z — 依頼者が Q1 で推奨（PostgreSQL の結合テストを verify と CI で毎回流す）ではなく、明示したときだけ流す形を選び、Q7 で流す場面も決めない形を選んだ。対象DB の結合テストを毎回流す決まりとは扱いが分かれる。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-14T12:57:11Z — PostgreSQL の結合テストを普段流さないことで verify と CI の時間は増えない代わりに、壊れても気づくのが遅れうる（evidence.md の残る不確かさ）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-11-14T12:57:11Z — PostgreSQL のイメージを上げたときに内部DB の PostgreSQL の結合テストを流すか、flyway-database-postgresql のライセンスと Spring Boot の BOM の管理は、設計・Build and Test で扱う。
