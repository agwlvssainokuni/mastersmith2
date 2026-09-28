<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-29T00:56:00Z — 監査の記録の確かめと、戻しの練習に使う配備の後のバックアップを、1回のアプリの停止（約7秒）でまとめて取った。複写を ACCESS_MODE_DATA=r で開き、配備の時刻以降の audit_events を種類ごとに数えた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-29T00:55:00Z — 初期管理者の既存の INFO のログにメールアドレスが出ることを知らずに、起動のログの確かめの出力に値を一度表示した。起動のログを確かめるときは、値を出さずに、ロガーと項目の名前と件数だけを出す形で見る。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
