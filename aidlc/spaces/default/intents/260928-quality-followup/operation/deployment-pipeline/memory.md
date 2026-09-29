<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-29T13:10:00Z — CI Pipeline・Infrastructure Design の段が無いため、前の Intent（260925-user-management）の配備の手順を正として今回の差だけを書いた。スキーマの変更と .env の変更が無いため、バックアップと戻しの練習は行わず、戻しはイメージだけにした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-29T13:10:00Z — 依頼者の決定（Q2: A・F1: B）で、main へは fast-forward で取り込むがタグは付けない。team.md の Deployment「リリース時に main にタグを付ける」との差を cd-config.md の 4節に記録した。v* のタグのプッシュで CI が公開の GitHub のリリースを作り WAR を添付するため、追加の質問 F1 で確かめた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-29T13:10:00Z — スモークテストの S6 に既定の DSL の生成を入れたが、生成はプレビューを置き換え DSL_GENERATED が監査に残ると、書いた後に AuditEventType で確かめて分かった。送る前にプレビューの有無を確かめ、確かめた後に破棄する形に直した。監査に残る確かめは書く前にソースで確かめる。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-29T13:10:00Z — 見本の対象DB（PostgreSQL 18.6）を古い digest で起動し直せることは確かめていない（戻しの練習は行わない）。

