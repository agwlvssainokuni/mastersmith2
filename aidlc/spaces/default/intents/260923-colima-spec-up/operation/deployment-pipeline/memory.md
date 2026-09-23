<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T07:45:00Z — 戻しの第一の手（.env を元に戻す）は元の CPU の値が要るが .env は開かないため、変更の前に .env をホームの下へ中身を表示せずに複写し、戻すときはその複写を戻す形にした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T07:45:00Z — 前の Intent と違い、配備の前の k6 を省いた（Q1: A）; Build and Test で同じソースのイメージを配備と同じ上限で試験済みのため。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T07:45:00Z — 直前の版への戻しで、前回の手順（WAR だけ複写して今の Dockerfile で作る）では起動の形が新しいまま残るため、取り出した場所に .env のシンボリックリンクを置いてそこで起動する手を runbook に足した; 複写しないので秘密情報のファイルが増えない。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-23T07:45:00Z — 直前の版を取り出した場所で起動する戻しの手順は動かしていない。Deployment Execution で練習するか決める。
