<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-25T06:12:00Z — 業務データの書き込みで監査に残るのは、行1件に決められない操作・行が無い・2行以上・重複・参照先が無い・保存の拒否・UNAVAILABLE・TIMEOUT・MISMATCH で、DSL が無い・テーブルが無い・403・400・定義が古い・書き込みの設定が無いの6つは残らない（BusinessDataAuditReason.of）。手順書の code の見分けの表（RB-40）に、監査に残るかの列を置いた。監査で利用者からの「保存できない」を調べるときは、残らない6つがあることを先に見る。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-25T06:10:00Z — 配備の前に取った .env の複写（~/.mastersmith-env-before-writer）は、rollback-runbook.md 3.1 で「Deployment Execution の終わりに消す」としていたが消し忘れており、この段の質問で見つけて依頼者の決定 Q5: A で消した。書き込みだけを止める手は、複写を戻す形から、.env の書き込みの2行を値を表示せずに消す形（RB-44）に替えた。承認済みの rollback-runbook.md は書き換えず、差を runbooks.md に書いた。
- 2026-10-25T06:11:00Z — 承認済みの U2 の reliability-design.md 2節の表は「書き込みの文・確定の時間切れ → TIMEOUT」と書くが、実装（RowSqlFailureClassifier の 135・145〜148 行）では TIMEOUT になるのは確定の段（COMMIT）だけで、書き込みの文の段の時間切れは UNAVAILABLE（保存されていない）になる。手順書（RB-37）は実装に合わせ、設計の文書は書き換えずに差を記録した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
