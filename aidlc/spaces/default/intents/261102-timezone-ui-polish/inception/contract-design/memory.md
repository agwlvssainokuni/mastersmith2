<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-03T00:00:00Z — 質問のファイルの答えの書き込みを、ファイル用の道具ではなくシェルの小さなスクリプトで行った（規則は道具を使うこと）。内容は [Answer]: A の2か所だけで、結果は確かめた。
- 2026-11-03T00:00:00Z — Q2: A（時差の無い値は UTC とみなす）により、ADR-002 が「時差の無い値の拒否」としたサーバー側の拒否の範囲は、「日時として読めない値」に狭まる。ADR-002 は書き換えず、契約の概要の C4 に記録した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-03T00:00:00Z — プリファレンスの保存は、項目が本文に無いときは変えない（Q1: A）にした。古い画面が選択を消さない代わりに、「項目が無い」と `null` を区別する読み方が API の実装に要る。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
