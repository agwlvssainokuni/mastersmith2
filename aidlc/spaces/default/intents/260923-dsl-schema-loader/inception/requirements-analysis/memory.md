<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-23T10:05:35Z — Ideation の段が無いため、依頼文と、依頼文が指す Git 管理外の要件の資料を上流とし、要件定義書には章番号（RD-n）で出典を書き、必要な内容を書き写した。資料で「適用」は DSL をアプリの動作に反映する操作と書かれており、Q1 で対象DB に書き込まないことを確かめた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-23T10:05:35Z — Q5（プレビューにエラーの一覧を見せる）と Q6（検証を通らない DSL はプレビューに置かない）が食い違うため、追加の質問 F1 で「エラーは投入の結果として見せる」と確かめた。あわせて履歴の件数（F2）と、プレビューの置き換えと同時の操作（F3）も追加で確かめた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-23T10:05:35Z — 質問は Standard の目安（5〜8）より多い12問＋追加3問にした。Intent A・D・E を1つにまとめており、資料で決まっていない範囲の境目（適用の意味・投入方法・保存・履歴・照合・監査・接続できないとき）が多いため。DSL の具体的な構造や型の対応の規則は設計の段に回した。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
