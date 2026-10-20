<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-21T05:30:00Z — Q4: A の「ページ送り」は、見本の customers が2行で1ページの件数が最小 20 のため次のページへ移れず、スモークテストでは表示と件数の選択だけを確かめ、複数ページの送りは E2E 180 で確かめ済みとした（依頼者が受け入れた）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-21T05:35:00Z — 配備の前の k6 は流さず Performance Validation に任せる（Build and Test の Q3: A）。project.md の「試験済みのときだけ省く」との差を cd-config.md 3.3 に記録した。書き込みだけに問題が出たときに .env の複写を戻して 409 にする手を、まとめの確認に無かったが rollback-runbook.md 3.1 に足した（依頼者が受け入れた）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-21T05:40:00Z — 書き込みのアカウントと .env の書き足しを入れ替えの前の停止の間に行う形（Q1: A）にした。アプリの作り直しを1回で済ませる代わりに、停止の時間に見本の PG の作り直しと台本の実行が乗る。WAR の作り直しは停止の前に置いて止まる時間を短くした。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
