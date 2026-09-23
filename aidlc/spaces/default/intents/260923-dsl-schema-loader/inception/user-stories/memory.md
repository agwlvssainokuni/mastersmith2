<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T10:34:33Z — ダウンロードを Should にした答え（Q5: B）が、要件でプレビューに YAML の本文を表示しないこととの組み合わせで、既定の DSL を取り出して直す流れを壊すため、追加の質問 F1 で確かめ、プレビュー中の DSL のダウンロードだけを Must にした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T10:34:33Z — mob の3人の意見のうち、判断が分かれる4点（US1.1 を分けて16件にするか、受け付けなかった投入の監査、同じ内容の再適用、適用と破棄の前の確認）だけを依頼者に尋ね、残りの指摘（受け入れ基準の境界・判定の仕方・依存の循環の解消・監査の基準を各操作へ移す・履歴の書き込みを適用へ移す など）はそのまま取り込んだ。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T10:34:33Z — 合否の判定の仕方（状態コードと code、「変わらない」「含まれない」の確かめ方、3種類の DB、性能の測り方、画面の共通の決まり）を、各受け入れ基準に繰り返さず「前提と読み方」に一度だけ書いた。基準は短くなるが、読む人は前提を合わせて読む必要がある。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
