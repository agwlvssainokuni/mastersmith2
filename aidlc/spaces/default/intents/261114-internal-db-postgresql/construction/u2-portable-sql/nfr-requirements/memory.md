<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-11-22T04:05:00Z — 質問は作らなかった。この単位の NFR は、上流の NFR1〜NFR9 と、機能設計で決まったこと（Q1〜Q3: A、捨ての試し F1〜F11）で決まり、新しく決める論点が無いため（`project.md` の「新しく決める論点が無いときは質問を作らない」）。PostgreSQL の値は測らない（U1 が起動の基準を持つ）ことも、新しい決めではなく NFR1 の「測るかは NFR 要件の段で決める」に沿った記録。
- 2026-11-22T04:05:00Z — 機能設計の審査の Major 2 件（`@Query` のインターフェースから `doReturningWork` を呼べない・`doWork` で flush と例外の型が変わる）は、承認の場へ持ち越す決まりのため、直さずに `tech-stack-decisions.md` の「実装の場所の選び方」に NFR 設計への持ち越しとして書いた。

## Deviations
- 2026-11-22T04:05:00Z — 観測の要件（`observability-requirements.md`）は、上流に観測だけの NFR が無いため、NFR2・NFR4 の番号を使って書いた（新しい観測の口は足さない）。

## Tradeoffs
- 2026-11-22T04:05:00Z — 行がまだ無い最初の 1 回だけ文が 2 本になる（今の `MERGE` は 1 本）代わりに、行が既にある通常の書き込みは文 1 本のまま（NFR1.2）。H2 の後勝ちを保つための代価。

## Open questions
- 2026-11-22T04:05:00Z — `Session.doWork` が flush を行わない前提と、`doWork` の例外の型は、NFR 設計とコード生成の最初のテストで確かめる。
