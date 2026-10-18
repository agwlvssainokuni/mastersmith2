<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-18T16:00:00Z — 配備先が PC の上のコンテナのため、この段は compose・perf・verify・CI・E2E・手元の監視の範囲に読み替え、質問を作る前に colima の VM・動いているコンテナ・perf の compose の上限・見本の PG の pg_hba と権限を読み取りで調べた（project.md の学び）。
- 2026-10-18T19:00:00Z — U2 の Q7: A で perf の MySQL 1792m を短い捨ての試しで確かめ、プールを埋めた状態で上限に張りつき（anon 90%）、プール以外の量も見立ての約 614 MiB でなく約 840 MiB と分かったため、追加の質問 F1 で 2304m にした。U1 の文書の値は承認の場でそろえる。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-18T16:30:00Z — U4 と U5 の答えを1回の依頼者の返事で尋ねたため、記録は U4 だけが受け付けられ、U5 の Q1 はもう一度尋ねて記録した（答えの記録は返事1回につき1つ）。
- 2026-10-18T19:10:00Z — 捨ての試しの結果の見出しを「## Q7: A の…」にしたため、Q7 と重なる見出しとしてまとめの確認が受け付けられず、見出しを直した。
- 2026-10-18T20:30:00Z — U3 の Q3: A で、管理者のパスワードをプロセスに入れず docker compose exec で入る形にした。NFR 要件の承認の場の決定 U3 R-01（プロセスの環境変数で渡す）の文面との差として記録した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-18T21:00:00Z — 各単位のレビューは READY だったが Major が残った（U1: 通信の待ちの計算に list・detail が入らない・k6 の setup の前提の担当、U2: E2E の一時のロールの道（U3 で答えた）、U3: TEMPORARY の取り上げの時期・15432 を開ける安全性の確かめの時期、U5: memory.stat の列の順・試しの回の設定の確かめ方）。U4 は Minor だけ。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-18T21:01:00Z — U1 の基盤の設計の perf の MySQL の値と根拠（1792m・プール以外 約 614 MiB）を、U2 の試しの結果（2304m）にそろえる必要がある。
- 2026-10-19T06:00:00Z — 承認の場の決定（Approve）: U1 の文書の perf の MySQL の値と根拠（1792m・プール以外 約 614 MiB）は直さず、U2 の試しの結果の 2304m（プール 768M）を正とする。各単位のレビューの Major（U1 R-01 通信の待ちの計算・R-02 k6 の setup の前提、U2 R-01 E2E の一時のロールの道（U3 の設計で答えた）、U3 R-01 TEMPORARY の取り上げの時期・R-02 15432 を開ける安全性の確かめを最初に開ける Bolt へ前倒し、U5 R-01 memory.stat の列の順・R-02 試しの回の設定の確かめ方）は文書を直さず、レビューの推奨の方向でコード生成の計画で直す。
