<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-21T04:35:00Z — 288736d・22de65f は単独の CI の run を持たず、まとめて push された先頭の 31d434c の run（1回目で成功、検査の job 22分42秒）で確かめたと読んだ。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-21T04:30:00Z — 05a9b37 の CI（run 39261104974、B3 を含む push）の1回目が PasswordChangePage.test.tsx の「drops the answer when the screen is left while sending」で落ちていたことが、Build and Test とコード生成の段の記録から抜けていた。同じテストは3回落ちており、team.md の「二度目に落ちたら直すまで進まない」に当たった後に B1・B4・B5 を統合していた。テストは b7291d3 で直り、その後は落ちていない。依頼者の決定で、承認済みの記録は書き換えず、この段の ci-config.md 6.2 に差を記録した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-21T04:40:00Z — 開いたままの Dependabot のプルリクエスト 15 件（#54〜#68、版の更新だけ）は、依頼者の決定で次の Intent の最初に手元でまとめて更新して verify を通す。High 以上の知らせが出たら、その前に取り込む。
