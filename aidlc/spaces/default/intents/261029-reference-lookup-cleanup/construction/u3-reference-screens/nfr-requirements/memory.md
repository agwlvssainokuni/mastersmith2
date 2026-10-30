<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-10-30T00:00:00Z — ui の単位のため成果物は performance・security・tech-stack・traceability の4つ。NFR1・NFR4〜NFR7・NFR9 を security-requirements.md に節で分けて書き、NFR3（接続）と NFR8（運用）は N/A とした。枝番は .41 から（NFR1.41〜1.47・2.41〜2.45・4.41〜4.45・5.41〜5.43・6.41〜6.42・7.41・9.41、計 29）。
- 2026-10-30T00:00:00Z — Q1: A により画面の側の時間の目標は置かず、NFR2.41 は「検索中」→結果の切り替えを画面のテストで確かめる要件にした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-30T00:00:00Z — NFR 設計に回した論点: 作成の画面を開いた直後の D13 の誤りの role="alert" の読み上げ（NFR4.45）、ラベルの1件読みの同時の数、200 件の radio の描画、検索の「時間がかかっています」の知らせ（NFR2.42・NFR2.43）。
