<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-25T04:41:00Z — 監視の確かめの1回目で、k6 の書き込みの場面（待ちが無い）が 2 VU でも毎秒 1,500〜2,200 件になり、lgtm の中の受け手（otelcol）と Tempo がメモリの上限（1.5GiB）で止められて、以後の指標・ログ・トレースが届かなかった。2回目は 1 VU で繰り返しごとに 1〜2 秒待つ台本で送り直した。監視の式を確かめる要求は、負荷の試験の場面ではなく、待ちを入れた少ない要求で送る。内部DB の監査の行も、1回目で 100 万行を超えた。
- 2026-10-25T04:42:00Z — 対象DB を docker pause で止めても、503 は BUSINESS_TARGET_DB_TIMEOUT ではなく、すべて BUSINESS_TARGET_DB_UNAVAILABLE（借りる待ち 3 秒の時間切れ）になった。接続の確かめに失敗して作り直す接続が借りられないため。TIMEOUT の経路は、つないだ接続で問い合わせの最中に止めないと起きない見込み。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-25T02:40:00Z — develop の 9292432 の CI の1回目が frontend/src/features/auth/logoutIntent.test.tsx「leaves the intent while staying on the same path and clears it on the next path change」で落ちた（2回目の試行で通過）。同じテストは B7 のレビューの直しの後の verify でも落ちており二度目のため、team.md の決まりと依頼者の決定 A で、この段の途中で短命のブランチ fix/261016-business-data-views-logout-intent で直した。手順の入れ替え（最初の描画の効果の前に印を立てる）で CI と同じ形（peekLogoutIntent が偽）を3回とも起こし、最初の描画の「印を消す」効果が印より後に流れることが原因と確かめた。直しは、印を立てる前に効果が流れ終わったことを待つ形（テストだけの変更）。負荷で描画が分かれて効果が遅れること自体は起こせていない（見立て）。直した後は単独 10 回・画面のテストの全件 1 回が通過。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-25T04:40:00Z — 業務データの 503 は既存の ms-5xx-ratio（ほかの要求で割合が薄まる）と ms-pool-pending（60 秒ごとの1点で取りこぼす）では条件つきでしか拾えなかったため、依頼者の決定 F1: A で、状態コードで数える警報 ms-biz-target-db（業務データの API の 503 が 5 分に1件以上、for 0s）を足した。設計の「新しい警報は足さない」との差。対象DB の時間切れの累計で数える案（B）は、問い合わせの時間切れ（TIMEOUT）を数えないため選ばなかった。止めてから firing まで 2分48秒（送信の周期 60 秒と、新しい系列の最初の点が increase に数えられないため）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
