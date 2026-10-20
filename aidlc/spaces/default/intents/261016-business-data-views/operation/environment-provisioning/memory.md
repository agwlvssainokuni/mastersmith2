<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-21T06:30:00Z — 前の Intent の内部DB の複写2件（~/.mastersmith-backup/ の …-pre-deps-ui-carryover.tgz・…-after-deps-ui-carryover.tgz）が権限 644 のままだった（置き場は 700）。依頼者の決定で 600 に直し、中身は開いていない。この Intent で作る複写も 600 にする。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-21T06:35:00Z — 片付けは使われていないビルドのキャッシュだけ（700.8MB）にし、前の Intent までの戻し先のタグ付きのイメージは残した（Q1: A）。戻し先を残す代わりに、PC のディスクの使用は 92% のまま残る。戻しの練習を並べると VM のメモリの余裕は約 100 MiB しか無いため、練習の間は lgtm を起動しない。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
