<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-03T00:00:00Z — 知識ベースが STALE のため依頼者は Focused scan を選んだ。範囲は N6 の日時の扱い（businessdata・targetdb・dsl・dslmanage・navigation）と画面の骨組み（app・admin・tables・shared・index.html）に絞った。比べた結果は NARROWER で、前回の深い範囲（版の定義・Dockerfile・compose・CI・perf、部品 build-and-verify・container-runtime・perf-and-monitoring）は流し読みに下がった。所見は K-94〜K-105 とした。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
