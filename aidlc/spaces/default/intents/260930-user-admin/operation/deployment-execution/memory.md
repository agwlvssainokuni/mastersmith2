<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->

## Deviations
- 2026-10-03T13:05Z — スモークテストの S4 で、行の「操作」のメニューが右にはみ出して欠ける問題が見つかった（原因は Dropdown を既定の bottom-start で使っていること。E2E 120 の「はみ出し」の確かめは画面全体の横のスクロールだけを見ていて拾えなかった）。依頼者の決定 B で、既知の不具合として後の Intent で直す。
- 2026-10-03T13:06Z — S5（監査の確かめ）で内部DB のボリュームを複写する操作が、個人に関する値の取り扱いとして実行の許可の仕組みに止められた。アプリは止めておらず、動いたまま。依頼者に扱いを確かめる。
- 2026-10-03T13:10Z — S5 の監査の確かめで、配備の後に管理の操作（USER_ADMIN_GRANTED 2・USER_ADMIN_REVOKED 1・USER_SUSPENDED 1・USER_RESUMED 1）と LOGIN_FAILED 1 が記録されていた。予定（管理の操作はしない）との差。依頼者が「自分が行った」と答えた（メニューのはみ出しの確かめなどで画面を操作した）。停止中の利用者は 0 人。
