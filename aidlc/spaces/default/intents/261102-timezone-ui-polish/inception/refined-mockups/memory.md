<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-03T00:00:00Z — Q7 A・Q8 A により、承認済みのストーリーの AC4.3.1（項目ごとに違うアイコン）は3項目に限り、AC4.4.2（業務メニューの3種類の既定）は上流の取り込み後に満たす形になった。ストーリーは書き換えず、mockups.md の13節に差を記録した。
- 2026-11-03T00:00:00Z — ストーリーの段で作業フォルダを移したまま相対パスで道具を流し、誤った場所にファイルを作った（依頼者の指摘で削除）。この段では絶対パスと `cd` 後の確かめで書いた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-03T00:00:00Z — タイムゾーンの選択は検索つきの自作ではなく `Select`（Q1 A）にした。部品の動作を再利用できる代わりに、400 件超の一覧を文字入力で探す形になる。
- 2026-11-03T00:00:00Z — 入力は `datetime-local` ではなく `TextInput`（Q3 A）にした。選んだタイムゾーンと秒の端数を扱える代わりに、利用者が書式を入れる。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
