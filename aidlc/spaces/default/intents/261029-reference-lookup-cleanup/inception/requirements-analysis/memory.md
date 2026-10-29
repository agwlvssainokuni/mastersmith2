<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-29T14:30:00Z — 8問（Q1〜Q8）をすべて推奨の A で確定。Q1 の「超えたら案内する誤り」は項目ごとの状態として読み、フォーム全体の定義の取得は失敗にしないと前提 A2 に置いた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-29T14:30:00Z — 決定の要約の道具（review-brief summary）が今回も動かず、答えのまとめだけで確認した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-29T14:30:00Z — 一覧・詳細のラベル併記（Q5）を範囲の外にし、参照先をまとめて読む処理と対象DB の負荷を避けた。代わりに、一覧では参照の値が ID のまま出る。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-29T14:30:00Z — レビューの Major 2件（FR1.3 の入力不可と FR3.1 の更新時の再確認の衝突、LOOKUP の権限を欠くときの扱い）は承認の場へ持ち越した（advisory）。
