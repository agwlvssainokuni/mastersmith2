<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-10-21T02:30:00Z — 依頼者の決定: eslintImportRules.test.ts の直し（上限を延ばさず、vitest の project を分けてこのファイルを先に1つで流す）を受け入れた。人工の強い負荷の下でだけ落ちた画面のテスト（registration.test.tsx の lazy の3件・PreferencesPage.test.tsx の4件など、findBy の 1 秒）は記録だけにし、CI や手元で落ちたら team.md の決まりで扱う。
- 2026-10-21T01:00:00Z — 依頼者の決定: RETENTION_TIME=0 は取り込まず、試しの結果（peak / max が約 0.13〜0.16 下がる、既定でも Met）を記録して後の Intent で決める（BR4.2・BR4.3）。eslintImportRules.test.ts（B3 の試しと B7 の verify で同じ形の時間切れが2度）は team.md の決まりどおりこの段で直す（短命のブランチ、再現の試みは 1 時間まで、二段で確かめる）。AC3.4.5（読み取りとその拒否が監査に残らない）は結合テストを足す。README の make-you-chic-ui の固定先と perf/README の「Performance Validation の段が無い」の文は直し、承認済みの U1・U2 の traceability の target のずれは書き換えずにこの段の記録に書く。
