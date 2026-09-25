<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-25T14:26:47Z — 監査の記録の形は project.md の DECIDED（共通化は後続の Intent）で決まっているため質問にしなかった。Feasibility の段が範囲に無いため、実現できるかの判断はこの段の ADR にまとめる（project.md の学び）。既存の部品は前の Intent の部品名で呼ぶ。
- 2026-09-25T14:40:25Z — Feasibility の段が無いため、java-mustache-processor の取り込み・JVM の中の SMTP の受け手・H2 での招待中の一意を ADR-010 にまとめ、確かめる段と切り替え先を書いた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-25T14:40:25Z — traceability.json をシェルで書いたため記録された書き込みが無く、レビューの依頼が拒まれた。Write の道具で書き直して通した。成果物はシェルではなくファイルを書く道具で書く。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-25T14:40:25Z — 招待メールは要求の中で、招待の確定の後にトランザクションの外で送り結果を別の短いトランザクションで記録する形（ADR-009）にした。送信の待ちで接続を持たない代わりに、応答時間に SMTP の時間が乗り、送信の途中で止まると結果が残らない。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
