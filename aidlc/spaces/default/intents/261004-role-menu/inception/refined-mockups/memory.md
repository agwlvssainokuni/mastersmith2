<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-04T11:30:00Z — classic の範囲で Ideation のラフな画面イメージと利用者の流れが無いため、画面イメージはストーリーと要件から直接作った。書く前に、ストーリーの受け入れ基準を画面（S1〜S10）とサーバー側だけのものに振り分けて照らし合わせた（前の Intent の学び）。WCAG 2.1 AA・確かめの表示・N 階層のメニューの形はストーリーと前の Intent で決まっているとして質問にせず、画面の組み立ての8問にした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-04T11:30:00Z — 利用者の管理の画面（既存）の詳細に、その人のロールを読み取りで足す（RQ2: A）。要件に無いが、AC2.2.7 の出どころの表示を利用者の側からも見られるようにしたもので、新しい操作は足さない。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-04T11:30:00Z — 権限の設定の左の木と、割り当ての候補の一覧、確かめの結果の行の開閉は make-you-chic-ui に部品が無いため frontend の側で作る（木は src/shared/）。入れ子のサイドバーは上流への追加を先に依頼する。足りない点の一覧を make-you-chic-ui-request.md に置き、取り込みは Domain Design・Functional Design で確定する。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-04T11:30:00Z — make-you-chic-ui への入れ子のサイドバーの依頼が、サイドバーを使う Bolt までに間に合うか。間に合わないときに自前に切り替える範囲は Delivery Planning で決める。
