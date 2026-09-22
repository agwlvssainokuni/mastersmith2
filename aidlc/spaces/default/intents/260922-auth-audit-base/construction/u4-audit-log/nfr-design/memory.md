<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-22T11:50:00Z — 新しく決める論点が無かったため、質問を作らず、設計の要点を要約として依頼者に確認した; 決まり 1.3・3.1 と NFR Requirements の Q1・Q2 で方針が決まっていたため。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-22T11:50:00Z — 確定の後の経路では DB の接続を一時的に2本使うことを受け入れ、負荷の試験で待ちを確かめることにした; 別スレッドに移すとトレースIDの一致と「確定の後に記録」の決まりを変えることになるため。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-22T11:50:00Z — U3 の設計（security-design.md 4章）はアクセス拒否の出来事に「要求のパス」を載せるとしているが、U4 の AuditEvent には要求のパスの項目が無い; 記録するかどうかを Code Generation の計画の確認で依頼者に確かめる（記録するなら U4 の Functional Design の項目の追加になる）。
