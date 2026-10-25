<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-25T09:50:00Z — MySQL の bizSearchFirst・bizSearchLast は2回目だけ満たさず、1回目の書き込みで biz_new が約 524 MB に増え表がバッファープールを超えたことが原因と確かめ、作り直した3回目（設計の前提の状態）で満たしたため「満たす」と読んだ。承認の場で確かめる。流す順（書き込みの後に読み取り）が読み取りの結果を悪い側へ寄せる。
- 2026-10-25T09:55:00Z — Not Met 4件（U1-NFR1.1・1.3・3.2・2.4）はどれも MySQL・MariaDB の部分一致と MariaDB の深い OFFSET による。部分一致は索引を使えず、1要求で件数とページの2回表の全部を読み、読み取りのプール 5 に 10 VU で借りる待ちが 3 秒を超えると 503 になる。PG は全場面が満たした。対象DB の anon の最大は MariaDB 85.01%・MySQL 90.3%（OOMKilled false・oom_kill 0）。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-25T07:00:00Z — 試験の始める前の確かめで、手元の WAR の SHA-256 が配備の WAR（6426111d…）と違った（ac9c58d4…）。make-you-chic-ui の固定先を ecb07e9 に上げた後の verify が、新しい Vite で WAR を作り直したため（見立て）。依頼者の決定 A で、配備したイメージ mastersmith:local（中の WAR は 6426111d…）に試験のタグを付けて使い、README の手順 0（WAR を Dockerfile で写して作る）との差を load-test-plan.md に書く。main の先頭（固定先 ecb07e9）と動いている版（b0a1e79 で組んだ画面）が厳密には一致しないことは、Feedback & Optimization で扱う。
- 2026-10-25T08:00:00Z — perf/README.md の 2B で種を流す mysql の道具の文字の組が latin1 のため、MySQL の種の日本語が二重に符号化されて入り、bizSearchContains の setup が 0 件で止まった。表の大きさも誤り（859.5 MB、正しくは 1,017,806,848 B）、それを元に決めた 1152M（前の決定 A）も誤りだった。リポジトリの外の写しで --default-character-set=utf8mb4 を付けて流し直し、追加の質問 F1: A でプール 1280M・上限 2688m（決まりより上限 128 MiB 少ない）にした。種を入れた後は、目印の語の当たり・最長の文字数を数えてから表の大きさを測る。
- 2026-10-25T09:59:22Z — 配備したアプリを止めた時間は約 4 時間 27 分で、見込み（3〜4 時間）を超えた。種の誤り・MySQL の決定の待ち・MySQL の3回目のため。MySQL の決定を待つ間に MariaDB を先に流した（計画の順 PG → MySQL → MariaDB との差）。


## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
