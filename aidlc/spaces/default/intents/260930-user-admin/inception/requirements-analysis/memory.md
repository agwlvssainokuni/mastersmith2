<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-30T17:00:00Z — 依頼文の4つの操作に、Q2 B で管理者による氏名・言語の変更が加わったため、その監査（F1）と自分自身の変更（F2）を追加の質問で確かめた。Q10 B の操作は「ロックの解除」ではなく「失敗回数を 0 に戻す（ロック中なら解除を兼ねる）」として要件にした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-30T17:00:00Z — 「変えるものが無い」操作（すでに管理者の利用者に印を付ける など）は、成功として受け付けず業務の誤りとして拒否し監査に残す形を [assumption] A2 とした。同時の操作で後の人に知らせられる代わりに、画面の操作の失敗が増える。承認の場で確かめる。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-30T17:00:00Z — 応答時間（1 秒）は質問で尋ねず [assumption] として要件に置き、NFR 要件の段で件数の想定と測り方を決める。
