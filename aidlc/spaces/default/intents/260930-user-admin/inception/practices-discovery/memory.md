<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-30T16:10:00Z — 再実行のため、リードの候補 P1〜P10 と支援役3名の追加（品質 Q-1〜Q-6、開発 C1〜C7・PD-1〜PD-7、セキュリティ X1〜X4・Q-S1〜Q-S6）のうち、チームの進め方に当たるものだけを 13 問にまとめた。処理の置き場・移行ファイルの名前・行の排他の既定（PD-1・PD-4〜PD-7）と ★の値は要件・設計の段に回した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-30T16:10:00Z — 決定の要約を出す道具（review-brief summary）が今回も動かず（main(argv) を持たないという誤り）、答えのまとめだけを示して確認した。複数選択で5つの選択肢を持つ Q2 は、選択肢の数の上限のため2つの問いに分けて尋ねた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
