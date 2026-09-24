<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-24T22:20:00Z — AWS の Cost Explorer・Config・Trusted Advisor は対象が無いため、費用は VM の資源の上限と実測、ずれは docker inspect・compose・.env の項目の有無と設計の値の比べに読み替えた

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-24T22:20:00Z — ずれの確かめで、compose のメモリの上限の既定が 1g で、2g はこの PC の .env の値だと分かった。承認済みの Performance Validation の記録（配備の既定は既に 2g）とは食い違うため、記録は書き換えず drift-report の D1 に根拠とともに明記した

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-24T22:20:00Z — SLO は 30 日の実測が無いため Unverified とし、配備の直後・監視の確かめ・負荷の試験・この段の時点の値を基準の値として並べた。1 日だけ監視を動かして測る案は、VM のメモリと .env の変更の手間から採らなかった（Q1: A）

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-24T22:20:00Z — D1（compose の既定 1g）と D2（アプリが見本の DB の管理者のパスワードを環境変数で持つ）を直すかと時期、次の Intent の束と順は依頼者が決める
