<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->
- 2026-10-05T15:38:13Z — 要件の NFR は主に役割・権限の API について書いているため、DSL の守りを NFR1、起動時のログを NFR5 に寄せた; 枝番は単位の中で .1 から振り、寄せたことと N/A の中身（NFR1.1〜1.4・1.6・5.1・5.2）を security-requirements.md の冒頭に書いた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-10-05T22:31:56Z — 承認の場の Request Changes（Major 11 件だけを直す）で R-01・R-02 を直した; 上流の枝番と同じ番号は同じ意味でだけ使い、足す要件を NFR1.7〜・NFR2.5〜・NFR3.3・NFR5.3〜・NFR6.5〜 に振り直し、traceability.json を上流の枝番ごとの行（N/A と部分つき）にした。振り替えの対応は security-requirements.md の末尾に記録した。
- 2026-10-05T15:38:13Z — library の単位のため性能・信頼性・画面・テストの要件を tech-stack-decisions.md に書いた; 段の定義の produces_kinds で performance・reliability の文書を作らないため。扱いを両方の成果物の冒頭に書いた。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-10-05T15:38:13Z — 1回ずつの時間は PostgreSQL の1種類だけで測る（Q1: A）; 版 2 で変わるのは DSL の形と検証で対象DB の読み取りは変わらないため。3種類の DB の差は確かめない代わりに、測る時間は約3分の1になる。
- 2026-10-05T15:38:13Z — SafeYamlReader の境界は小さな上限の値で確かめ、10 MiB の本文は作らない; テストの時間とヒープを抑える代わりに、実際の上限の値での境界は呼ぶ側（role）と既存の DSL のテストに任せる。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-05T15:38:13Z — 生成する DSL の大きさは約 6.3 MB の試算で、まだ実測していない; Build and Test で測り、コメントの多い対象DB で 10 MiB に近づくときは上限を上げるかを依頼者に諮る。
