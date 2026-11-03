<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-03T00:00:00Z — Q1: A（時点だけを送る）により、承認済みの要件 FR3.4 とストーリー AC2.3.3・AC2.4.4 の「サーバー側の拒否」は成り立たない（時差つきの値は必ず存在する時点のため）。要件・ストーリーは書き換えず、ADR-002 に差を記録した。
- 2026-11-03T00:00:00Z — 追跡の道具は絶対でなく相対の出力先を渡したため、作業フォルダを確かめてから流した（前の段の誤りの再発を避けた）。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-03T00:00:00Z — 部品は、今回手を入れる・足すものだけ16個に絞り、前の Intent までの変えない部品は載せなかった。既存のコードの部品の ID との対応は「備考」の列に置いた。
- 2026-11-03T00:00:00Z — 利用者の設定は新しい表ではなく、言語と同じ行に列を1つ足す形にした（ADR-001）。1項目だけのため。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
