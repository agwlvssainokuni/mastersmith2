<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-09-27T05:03:38Z — 狭い幅の確かめ方を U5 の質問（Q1）にした; U4 の tech-stack-decisions.md は狭い幅を検査に足すかを B5 の計画で依頼者に確かめるとしていた。U5 はこの Intent で最も横に広い表（8 列）を持ち、横に動く領域へのキーボードの届き方は実際のブラウザでしか確かめられないため、B5 の計画を待たずにこの段で決める形にした。
- 2026-09-27T05:03:38Z — 招待の画面の時間の目標を Q2 にした; U4 の NFR6.1 はログインの画面だけに時間の目標を置き、ログインした後の画面には無い。前の Intent の DSL の管理画面の前例（NFR1.18、E2E で測って記録し関門にしない）に合わせる案を推奨にした。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-09-27T05:10:31Z — U4 の NFR7.3（既定の幅だけ）に幅 375px の6組を足した（NFR7.4）; 依頼者の決定（Q1: B）で、U4 が B5 の計画に回していた点をこの段で決めた。U4 の文書は書き換えず、tech-stack-decisions.md の上流との差に追加として書いた。
- 2026-09-27T05:10:31Z — 画面の側のセキュリティと依存の要件を NFR9 の枝番に寄せた; 要件に当たる ID が無いため、U4・U8 の前例と同じく NFR9.1〜NFR9.5 とし、security-requirements.md の上流との差と traceability.json の NFR9 の target に書いた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-09-27T05:03:38Z — 画面の側に要求の時間切れを置かない形を要点 3 として記録した; 既存の ApiClient は時間切れを持たず、待ちの上限はサーバーの SMTP の時間切れに任せる。受け手が遅れ続ける既知の限界（U1 の NFR6.3）の間は招待の Modal が閉じられないまま続くことを、画面の側でも引き継ぐ代わりに、設計を増やさない。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-09-27T05:10:31Z — 画面の時間の測定（NFR6.1・NFR6.2）と行ありの検査（NFR7.3・NFR7.4）は、E2E の WAR で招待を使える設定に頼る; 今の frontend/playwright.config.ts は SMTP とベース URL を渡しておらず、招待を置けない。渡し方は infrastructure-design の持ち主として前提に書いた（U6 の E2E-1 と同じ前提）。
