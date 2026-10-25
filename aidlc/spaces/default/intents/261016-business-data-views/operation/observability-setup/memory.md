<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-25T02:40:00Z — develop の 9292432 の CI の1回目が frontend/src/features/auth/logoutIntent.test.tsx「leaves the intent while staying on the same path and clears it on the next path change」で落ちた（2回目の試行で通過）。同じテストは B7 のレビューの直しの後の verify でも落ちており二度目のため、team.md の決まりと依頼者の決定 A で、この段の途中で短命のブランチ fix/261016-business-data-views-logout-intent で直した。手順の入れ替え（最初の描画の効果の前に印を立てる）で CI と同じ形（peekLogoutIntent が偽）を3回とも起こし、最初の描画の「印を消す」効果が印より後に流れることが原因と確かめた。直しは、印を立てる前に効果が流れ終わったことを待つ形（テストだけの変更）。負荷で描画が分かれて効果が遅れること自体は起こせていない（見立て）。直した後は単独 10 回・画面のテストの全件 1 回が通過。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
