<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-05T15:55:46Z — 上流の枝番と同じ番号は同じ意味でだけ使い、足す要件は上流の最後の枝番の次から振った; dsl-v2 の NFR 要件のレビュー（R-01）で、同じ番号を別の意味に使って N/A と有効が食い違った反省から。対応表を各成果物の冒頭に置いた。
- 2026-10-05T15:55:46Z — NFR2.3（要求ごとの権限の読み出しの重さ）を「部分」と読んだ; U4 の解決が要求ごとに呼ぶ groupIdsOfUser はこの単位の口のため、その問い合わせの数だけをこの単位で持ち、解決全体の時間は U4 に残した。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-05T15:55:46Z — 要件 NFR2.2 に無いグループの管理の API に p95 1 秒を置き、NFR2.5 として足した; 依頼者の Q1: A による。利用者の管理の API と同じ目標と道具（k6）で、台本はコード生成（B3）、流すのは Performance Validation。
- 2026-10-05T15:55:46Z — traceability.json は段の定義の例（NFR1〜NFR6 の行）より細かく、上流の枝番ごとにも行を立てた; dsl-v2 のレビューの R-02 で、当たらない枝番が OK の中に混ざると要件の網羅の連鎖が誤ると指摘されたため。OK の target には、この単位で定義した ID だけを書いた。
- 2026-10-05T22:30:06Z — 承認の場の Request Changes（Major 11 件だけを直す）で R-01・R-02 を直した; NFR2.5 は操作ごとに1回の繰り返しに要求1つの場面に分けて iteration_duration の p95 と checks の率 1 で判定し、NFR2.7 は GroupConnectionUsageIT（書き込み 2・読み取り 1）と、上限 10 で 4 VU（尽きない）・20 VU（待ちが起きる）の期待を数で置いた。場面の名前をそろえるため tech-stack-decisions.md の引き継ぎの1行と scalability の NFR2.8 の場面名も直した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-05T15:55:46Z — 違反の後の失敗の監査は TransactionTemplate を2回順に実行する形にした（R-11）; REQUIRES_NEW の入れ子より接続を同時に2本以上持たず接続プールの見積もりが変わらない代わりに、業務処理の方法の中でトランザクションの組み立てを明示する手間が増える。
- 2026-10-05T15:55:46Z — 件数の上限は強制せず、詳細の全件返しのままメンバー 1,000 人で目標を確かめる（Q2: A）; 契約 C6 と機能設計を変えずに済む代わりに、目安を大きく超える規模では応答が大きくなりうる。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-05T15:55:46Z — 同じ名前の同時の作成・変更で、重なった側が違反になるか待ちの上限切れになるかは未確定; NFR 設計の捨ての試しのコード（NFR3.7）で確かめ、GroupBusyApiIT・GroupConflictAuditIT の期待をその結果に合わせる。
- 2026-10-05T15:55:46Z — 入口の判定の後の窓（NFR1.10）を U4 role も受け入れるかは U4 で決める; グループと同じ考え方を推奨として引き継いだ。
