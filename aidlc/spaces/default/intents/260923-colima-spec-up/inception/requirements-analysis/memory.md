<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T07:45:00Z — Q2 の D（「A か B」の上限の変更と JVM の口）は上限の既定値が決まらないため、追加の質問 Q5 で既定 1g・手元 2g を確定した; 選択肢の中に別の選択肢を含む形は、回答の後に必ずどちらかを確かめる。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T07:45:00Z — 要約の確認の前に出す決定の要約（review-brief summary）が道具の不具合で出せず、要約を直接示して確認した; 確認の記録（summary-confirmation）は通常どおり残した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T07:45:00Z — VM の拡張だけでは F3 が直らない（mem_limit 1g が固定）ことをコードの調査で示し、依頼の文言（VM の性能を上げて直す）より広い変更（上限と JVM の設定の口）を質問で選んでもらった; 依頼の文言に合わせて VM だけにする案（E）も選択肢に残した。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-23T07:45:00Z — CPU 4 でログインの p95 が 1 秒以内に収まるか、2g で F3 が起きなくなるかは未測定。Build and Test（FR5）で確かめ、届かなければ相談する。
