<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-18T04:30:00Z — 質問を作る前に、既存の境界テスト（targetdb・role・dsl）をコードで確かめ、事実を質問の冒頭に書いた。targetdb は書き込み・JdbcTemplate・DSL・web を止め、role に依存してよいのは audit と navigation だけ、dsl は外から使ってよい。Feasibility の段が無いため、実現の判断（3種類の DB の方言・件数の重さ・pattern の時間の上限など）を ADR-007 に、確かめる段と切り替え先とともにまとめた（project.md の学び）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-18T04:30:00Z — 業務データの行は対象DB のデータで、アプリが独自に持つデータではないため、components.md のエンティティには書かなかった（project.md の学び）。監査の行の欄の置き方は機能設計に回した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-18T04:30:00Z — 行の SQL を targetdb ではなく新しい businessdata の中の1つの部品に閉じた（DQ1: A）。targetdb の「書き込みをしない」境界テストを緩めずに済み、識別子の照合と SQL の組み立てが同じ機能の中にそろう代わりに、businessdata が3つの既存の機能に依存し、role の境界テストに依存してよい相手を足す（DQ2: A、コード生成の計画で承認を得る）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-18T04:30:00Z — 解決の口を1要求で何回呼ぶか（接続の数）、全体の件数が重い表での扱い、時差つきの型を UTC に固定する方法は NFR 要件・NFR 設計で試しを含めて決める。
