<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-06T15:15:02Z — 画面だけの単位のため、基盤は WAR に同梱する画面の成果物・入口の data router・遅延読み込み・ApiDownload の headers・E2E の差し替えの口だけと読んだ
- 2026-10-06T15:15:02Z — traceability.json は NFR 設計の 38 の ID をすべて並べ、基盤（配信・段・E2E・記録）に当たる 27 件を OK、画面のコードの作りを理由つきの N/A にした

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-06T15:15:02Z — 承認済みの設計と違う作りは無い。画面の側の監視を置かないことは、設計に書かれていない事項を既存の画面と同じ扱いとして monitoring-design.md 1節に明記した

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-06T15:15:02Z — 画面の側の監視を置かない代わりに、画面の失敗は API の側の指標とログ、画面の時間は E2E の測りでしか見えない。配備先が決まったときに見直す

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-06T15:15:02Z — E2E の番号（140・150）は app-frame-ui（160・170）と合わせてコード生成の計画で確定する
- 2026-10-06T15:15:02Z — data router への差し替えが既存の画面と E2E を変えないかは B8 の最初に確かめる
