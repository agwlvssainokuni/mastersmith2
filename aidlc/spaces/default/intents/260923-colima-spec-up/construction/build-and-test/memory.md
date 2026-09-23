<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T07:35:00Z — この Intent には Performance Validation の段が無いため、k6 の試験と NMT の測定の持ち主をこの段とし、Unverified で引き継がずにここで実行した; 要件 FR5 もこの段での確かめを前提にしていた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T07:35:00Z — 負荷の試験の後の片付けで、perf/README.md の手順 4 の `docker compose up -d --wait` ではなく `docker compose start app` を使った; up は新しいイメージ・設定で配備したアプリを作り直してしまい、配備（Deployment Execution）を先取りするため。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T07:35:00Z — 修正の前の上限 1g を CPU 4 の VM でも流し（pre1g）、VM を上げても F3 が起きることを実測で示した; 1回分の時間は増えるが、「VM の拡張だけでは直らない」という要件の前提を裏付けられる。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-23T07:35:00Z — 2g で refresh を続けたときの docker stats のメモリ（最大 1.609GiB）と JVM の確保（約 0.8GB）の差の内訳（H2 のページキャッシュか）は未確認。長い時間の負荷も未実施。
