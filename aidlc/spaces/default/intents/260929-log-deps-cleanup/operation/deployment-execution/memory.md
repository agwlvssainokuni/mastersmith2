<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-29T20:47:01Z — FR1 の裏付けは、起動のログを JSON として読み、初期管理者の INFO のキー（maskedEmail の有無・email の無し）と、.env の初期管理者のメールアドレスの値のログの中の件数（値は表示しない）だけで行った。監査の確かめは README の手順（アプリを止めて複写し読み取りで開く）で、配備の後のバックアップを兼ねた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-29T20:47:01Z — 配備の後の監査にログインより前の LOGGED_OUT が1件あった。ブラウザに残っていた前のセッションのログアウトと見立てたが確かめていない。
