<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-22T11:40:00Z — U3 の決まりの文の順（管理者のみ→公開→ログイン必須）と、U1 の差し込み口の並び（U1・U2 の公開が管理者のみより先）は、パスが重ならないため同じ結果になると解釈し、U1 の並びに合わせた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-22T11:40:00Z — 確定回答 Q1 により、BR6.1 に無い code `REQUEST_REJECTED`（400）を加えた; 要求の検査の拒否もエラー応答の共通の形（U1 の決まり 5.1）にそろえるため。security-design.md 5章に明記。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-22T11:45:00Z — レビューの指摘により、401 の理由の受け渡しを「U2 の認証の失敗の例外の区分、例外が無ければ TOKEN_MISSING」と決めた; 要求の属性は Spring Security の失敗の経路で設定の漏れが起きやすく、例外なら入口の処理に必ず届くため。
- 2026-09-22T11:40:00Z — 401 の理由（TOKEN_EXPIRED かどうか）を U2 から U3 に渡す形を、U2 の設計が決めていなかったため U3 の設計で約束として書いた; Code Generation の計画で U2・U3 の両方に書く。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
