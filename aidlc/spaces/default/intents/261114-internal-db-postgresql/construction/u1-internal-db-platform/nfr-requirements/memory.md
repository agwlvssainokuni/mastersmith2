<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-11-22T12:30:00Z — 質問は性能の測り方（Q1）の1つだけにした。ほかの NFR（検査の時間・カバレッジ・依存・構造）は、要件（NFR1〜NFR9）と機能設計の決定でほぼ決まっており、数値は上流のものを使った（`team.md` の「新しく決める論点が無いときは質問を作らない」に沿う）。
- 2026-11-22T12:30:00Z — Flyway の PostgreSQL 用の部品のライセンス（Apache License 2.0）と依存（`flyway-core` のみ）は、Maven Central の POM（部品と親）で確かめた。README の `META-INF/third-party-licenses` は、jar にライセンスの文書が含まれない場合だけの置き場なので、この部品には要らないと読んだ。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-22T12:30:00Z — 上流の NFR に当たらない要件を2つ足した: NFR4.10（起動のログに内部DB の種類だけを INFO で出す）と NFR4.11・NFR4.12（接続プールの指標の継続・TRACE の漏れ）。いずれも NFR4（信頼性）の枝番に寄せ、出どころ（運用の確かめ・既存の守り）を各行に書いた。承認の場で不要なら外せる。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-22T12:30:00Z — 性能の測りは、移行と起動の時間だけ（Q1: A）にした。k6 を流す案（B）は perf の compose に PostgreSQL の選び方を足す必要があり U4 の仕事が増えるため、応答時間は測らず「測っていない」と記録する形にした。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-11-22T12:30:00Z — 外の接続が移行の表を握っているときの `lock_timeout` 2 秒の影響と、移行の接続だけ上限を外すか（NFR4.3。NFR Design で決める）。
- 2026-11-22T12:30:00Z — 機能設計のレビューの Major 1（接続プールの初期化を内部DB の `HikariDataSource` だけに限る）と Minor 5 件は、承認の場へ持ち越し中。NFR Design で名前での絞り込みと順序（`hikari.connection-init-sql` の読み込みとの前後）を決める入口がある。
- 2026-11-22T12:30:00Z — `/actuator/health` が UP になるまでの時間の測りの置き場（既存の起動確認の流用か別の1本か）は、コード生成の計画で決める。