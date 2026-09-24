<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-24T17:58:00Z — Hikari の値は、使い捨てのアプリにだけ MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,metrics を渡して /actuator/metrics から読んだ（配備したアプリの公開の範囲は変えない）。プールが尽きたかは、約 2 秒ごとの使用中の数ではなく、待ちの時間切れの累計と借りるまでの待ちの最大で判断した

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-24T17:58:00Z — 1回目の dslCycle は PC のスリープ（414 秒）で要求が止まったため、割り込みで止めて caffeinate -i でやり直した。1回目の dslMixed はロックの状態の行が無い利用者の同時の最初のログインで 500 が1件出たため、利用者を1人ずつ先にログインさせて行を作ってから 2g と 1g を流し直した（台本は変えていない）

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-24T17:58:00Z — dslLight は想定の規模の DSL をプレビューに置き、dslCycle で 2.4GB に膨らんだ内部DB のまま測った（悪い側の条件）。dslMixed の前はコンテナを作り直して memory.peak を消した

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-24T17:58:00Z — NFR1.12 は 1g で Not Met（OOMKilled）。要件の条件を配備の既定 2g に合わせるかを承認の場で依頼者に諮る。ロックの状態の行の MERGE の同時実行での 500 と、dslMixed の台本の利用者の割り当ては、後の Intent で直すかを諮る
