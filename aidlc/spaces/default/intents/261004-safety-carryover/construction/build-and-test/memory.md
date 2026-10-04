<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-04T04:16:25Z — k6 の http_req_duration が、colima の VM の時計が負荷の間にホストより約 2 秒ずれて合わせ直されるため一部崩れた（待ちの最小 0 ms、繰り返しより長い約 3 秒の値）。1回の繰り返しが要求1つの loginSuccess では、単調な時計の iteration_duration とサーバー側の最大が合うため、判定は iteration_duration で行い、http_req_duration は並べて記録した。前の Intent の 939.6 ms も同じ影響を受けていた可能性がある。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-04T04:16:25Z — 1〜6回目で繰り返しの時間の p95 が2回 1 秒を超え、依頼者が VM の時計を合わせて測り直しを求めた（F1・F2 の Other）。測り直しの7〜9回目は 1 秒を下回ったため、1〜6回目を FR2.2a の条件がそろわない回として外した（承認の場で確かめる）。時計のずれを ssh の前後のホスト時刻の中間と比べる誤った測り方で 2.2 秒と読み、測り方を直して範囲（VM−終わり、VM−始め）で記録し直した。配備したアプリは2回止めた（約 12 分と約 4 分）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-04T04:16:25Z — R-01 の実機の確かめは、手元の監視を起動せず、上限 4・userAdminPoolLimit・VUS=12 を 2 分に縮めて、ログの JSON の logger と traceId の件数だけで判定した。警報の確かめは前の Intent で済んでいるため省き、配備したアプリを止める時間を短くした。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
