<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T09:44:43Z — 再実行のため、支援役3名が挙げた約30の論点のうち、チームの進め方に当たるものだけを9問にし、「適用」の中身・接続設定の足し方・パッケージの切り方・E2E の2本目・検証エラーの形は要件・設計の段に回した。Q4 の「すべてのパッケージ」はバックエンド（JaCoCo のパッケージ単位）だけに当てると解釈した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T09:44:43Z — Q3（コンテナが無ければ飛ばす）と Q4（すべてのパッケージに下限）の答えが team.md の「全検査を通してから統合」「除外を増やさない」と食い違いうるため、追加の質問 F1・F2 で確かめた。まとめの確認の場で依頼者が F2 を B（既存が下回れば新しいパッケージだけ）に変えた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T09:44:43Z — 統合で、面談で聞いていない初稿の行（対象DB の設定の型の伏せ字、401／403／200 のテスト、Flyway V5、Playwright）は基準から外し、evidence.md に記録した。基準は依頼者が確かめたことだけにし、今回の Intent だけに関わる事項は要件・設計で扱う。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
