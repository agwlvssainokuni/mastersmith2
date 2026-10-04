<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-04T07:20:00Z — 依頼文の F・I は参考資料の要件のドラフトの Intent の記号と読み、ドラフトを上流にするかを Q1 で確かめた（A: 使う）。ドラフトは F のロールを業務データの権限に限り、管理画面は管理者の印で別に決めるとしており、Practices Discovery が見込んだ「印をロールに置き換える」形とは違うため Q2 で確かめ、ドラフトどおり別系統（A）になった。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-04T07:20:00Z — Q4 で依頼者は、権限の対象を対象DB のメタデータではなく適用済みの DSL から出す形（A）を選び、ドラフトの 9.1 と違う。そのため DSL を適用し直して消えた対象の設定の扱いを追加の質問 F1 で確かめた（名前で持ち続けて画面に出す）。import の YAML に今の DSL に無い名前があるときも同じ扱いを前提 A3 とした。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-04T07:20:00Z — 業務データの一覧・詳細の画面（J・K）がまだ無いため、権限は設定・解決・後の Intent が使う口とメニューの出し分けまでにした（Q5: B）。テーブルを指すメニューの項目は準備中の画面へ移す（Q8: A）。業務データへの適用の確かめは J・K に残る。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-04T07:20:00Z — グループ経由のロールと直接のロールの和を作業ロールの候補とする読み（前提 A2）、ロールとグループの数の上限（A6）、拒否した操作と export を監査に残すか（FR12.2・FR12.4）は後の段で確かめる。
