<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-21T00:20:00Z — 配備の後の複写で ROLE_PERMISSION_CHANGED が期待の 1 に対し 2 件だった。依頼者に尋ねると権限の設定の保存を2回押していたため、操作と合うと判定した（期待の件数は保存した回数）。ほかの種類は期待どおりで、FAILURE・UNKNOWN は 0 件、BUSINESS_ROW_* の3列はすべて空でない。乙は消され、ロールの数と初期管理者の割り当て・作業ロールの保存は前と同じ。
- 2026-10-21T00:06:00Z — 入れ替えでアプリが止まっていたのは 23:48:18Z〜23:49:11Z の約 53 秒（見込みの 3〜4 分より短い）。WAR の SHA-256 は作り直す前と同じ 6426111d… で、B7 の後にアプリのコードが変わっていないことと合う。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-21T00:40:00Z — main への取り込みの前の CI の確かめで、7f2575c（CI Pipeline の段の記録のコミット）の CI が osvScan で失敗していた（vendor/make-you-chic-ui の lockfile の vite@8.2.1、GHSA-587h-7wmj-h5pm、成果物を作る道具の High）。上流の main も 8.2.1 のまま。frontend の Vite は 8.3.4 で当たらない。依頼者の決定「直してから取り込む」で main への取り込みを止め、make-you-chic-ui-request-6.md の依頼を出して、上流の直しの固定先の更新・verify・E2E・CI を通してから取り込む。
- 2026-10-21T00:00:00Z — 入れ替えの前の複写を数える道具の問い合わせで、users の主キーの列の名前を id と誤り、H2 の誤りの表示に初期管理者のメールアドレス（example.com の予約のドメイン）が一度出た。列の名前を user_id に直して数え直した。決まり（個人に関する値を表示しない）との差。
- 2026-10-21T00:05:00Z — 初期管理者にグループ経由のロールの割り当て3つと作業ロールの保存が前からあったため、依頼者の決定でスモークテストを合わせ直した: 確かめ用のロールは乙だけを作り（甲は作らない）、今の作業ロール → 乙 → 元の作業ロールと切り替え、片付けで乙の割り当てを外して乙を消す。監査の期待は ROLE_CREATED 1・ROLE_PERMISSION_CHANGED 1・ROLE_ASSIGNED 1・WORK_ROLE_SWITCHED 2・BUSINESS_ROW_* 各1・（消すとき）ROLE_UNASSIGNED 1・ROLE_DELETED 1。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
