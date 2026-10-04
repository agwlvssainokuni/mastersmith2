<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-04T04:34:20Z — CI Pipeline・Infrastructure Design の段が無いため、前の Intent（261003-user-admin-followup）の配備の手順を正として今回の差だけを書いた。今回の差の中心は、配備の起動で救済が働きうること（初期管理者のパスワードが .env の値に戻り、ログイン中の端末がログインし直しになる）で、入れ替えの前にアプリを止めて内部DB を複写し（バックアップを兼ねる）、初期管理者の停止と印の有無を数え、依頼者に画面でパスワードを変えたかを尋ねる手順にした（Q1: A）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-04T04:34:20Z — 配備の前の E2E は 153 件 passed だったが、救済の WARN 0 件を件数で確かめる計画（D5・D7、R-05）は果たせなかった。Playwright の webServer.stdout が ignore で、E2E のアプリの標準出力が残らないため。E2E は毎回一時の内部DB に初期管理者を新しく作るため救済が働きえないことと、初期管理者のログインを含む全件の通過で裏付けた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-04T04:34:20Z — 救済で変わった利用者の状態は、イメージだけの戻しでは元に戻らない。元に戻すには配備の前の複写を戻す必要があり、その後のデータと監査の行も失う。そのため、入れ替えの前に救済の見込みを確かめて了承を得る形を選んだ。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
