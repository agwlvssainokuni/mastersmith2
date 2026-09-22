<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-22T12:40:00Z — U4 は独自の基盤を持たないため質問を作らず要約で確認した; 前の単位の確認の指摘を踏まえ、traceability.json に U4 の NFR の ID をすべて載せ、「指標を作らない」ものにはしきい値を指標として書かず、ログと監査ログから数える見方として書いた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-22T12:50:00Z — 依頼者の変更の依頼（運用の見方）により、監査の書き込みが 200 ミリ秒を超えたときに WARN を1回出す振る舞いを加えた; 指標を作らない決まり（NFR10.5）の中で、運用の中の遅れに気づけるようにするため。U4 の NFR Design には無い振る舞いのため、Code Generation の計画で揃える。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-22T12:50:00Z — コネクションプールの上限（10 本）は変えず、ログインと 403 を同時に流す組み合わせの試験で待ちを測ることにした; 各要求が接続を持つ時間が数十ミリ秒で、見積もりでは目標の内側に収まるため。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
