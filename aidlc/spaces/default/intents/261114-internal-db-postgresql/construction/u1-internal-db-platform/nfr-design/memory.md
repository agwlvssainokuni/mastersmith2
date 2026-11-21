<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs.

## Interpretations
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

- 2026-11-22T13:10:00Z — 質問は移行の接続の `lock_timeout`（Q1）の 1 つだけにした。ほかは NFR Requirements と機能設計でほぼ決まっていたため、設計の決定（DD-1〜DD-7）として書いた。サーキットブレーカー・キャッシュ・水平のスケールは、1 インスタンス・開発者の PC 上の前提で当たらないため、採らなかった形に理由を書いた。

## Deviations
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->
- 2026-11-22T13:10:00Z — 機能設計のレビューの指摘 R-01（接続プールの対象の絞り込み）・R-03（種類の判断の置き場）・R-04（EMPTY・UNSUPPORTED を報告する部品）には、機能設計の文書を書き換えず、この段の設計の決定（reliability-design.md の DD-1・DD-2・DD-3）で答えた。機能設計の文書の書き直しは承認の場で行う。
- 2026-11-22T13:10:00Z — NFR Requirements のレビューの Minor（NFR4.11・NFR4.12 の定義の置き場、NFR4.10 の由来）は、承認の場まで文書を直さず、この段では NFR4.10 を reliability-design.md と observability-design.md の両方に設計した。

## Tradeoffs
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->
- 2026-11-22T13:10:00Z — 移行の接続の待ちは、2 秒のまま（質問 Q1: A）。別の接続や Flyway の `initSql` で長い上限にする案（B）は、抜け道の設定が増え、起動が最大 30 秒止まって見えるため採らなかった。
- 2026-11-22T13:10:00Z — Hikari のログは絞らず、起動の失敗のテストが全ロガーの出力に URL・資格情報が無いことを確かめる網にした（実測で出ないため。絞ると動作中の警告も隠れる）。

## Open questions
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
- 2026-11-22T13:10:00Z — `HikariDataSource` の setter が最初の接続の前なら効くという前提（機能設計の T8）は、まだ確かめていない。コード生成の最初の単体テストで確かめ、崩れたら依頼者に諮る。
- 2026-11-22T13:10:00Z — 外の接続に握られたときの失敗が 2 秒台で `MIGRATION_FAILED` になることは見込みで、PostgreSQL の結合テストで実測する。