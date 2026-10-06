<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-06T14:31:11Z — 基盤の変更が無い library の単位のため、cicd-pipeline.md は新しい設計ではなく、既存の CI と verify の段（0〜9）に足すテストを対応づけた記録として書いた（project.md の学び）
- 2026-10-06T14:31:11Z — traceability.json は NFR 設計の 23 の ID をすべて並べ、基盤（段・タスク・置き場）に当たるものを OK、コードの作りだけで決まるものを理由と確かめの段つきの N/A にした（前の Intent の library の単位と同じ形）

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-06T14:31:11Z — ESLint の決まりのテストの置き場を、logical-components.md 2節の『コード生成で決める』から、Vitest の今の対象の frontend/src/ の下の *.test.ts（node の実行環境）に絞った。差は cicd-pipeline.md 12節に記録した

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-06T14:31:11Z — ESLint の決まりのテストを src/ の下に置く形を選んだ。Vitest の設定を広げずに verify と CI で毎回流れる代わりに、設定のテストが画面のソースの木に混ざる

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-06T14:31:11Z — verify の時間の増え方（ApiAccessConsistencyIT が既存の起動の文脈を使い回せるか）は Build and Test の実測を待つ
