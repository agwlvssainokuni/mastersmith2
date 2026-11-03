<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-03T00:00:00Z — 全体の合計で判定している7パッケージのカバレッジの実測（約5分）は、この段では行わなかった。手を入れる見込みのパッケージが無い（アイコンは `WebConfig` の静的な配信に載り `common.web` に手を入れない）と読んだため。コード生成の計画の段で確かめ直し、手を入れるなら実測する。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-03T00:00:00Z — 危険を先にし（Q1: A）、U3（対象DB の確かめ）を先頭、U5（上流を待つ）を最後に置いた。U3 の確かめの結果が外れると B4 以降の計画の見直しが要る代わりに、手戻りの大きい不明を先に潰せ、上流を待つ時間を最も長く取れる。
- 2026-11-03T00:00:00Z — 統合の前の E2E が、ほぼすべての Bolt で要る（`frontend/index.html`・`frontend/public/`・`frontend/src` の本体、業務データ・`navigation` の本体に触れるため）。Bolt ごとに時間がかかる代わりに、`team.md` の決まりを崩さない。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
