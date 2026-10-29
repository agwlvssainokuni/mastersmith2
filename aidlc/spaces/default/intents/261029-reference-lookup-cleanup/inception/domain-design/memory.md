<!-- INVARIANT: examples are single-line HTML comments so a fresh template parses to total=0 (MEMORY_EMPTY). Do NOT un-comment or split across lines. t100 guards this. -->
> This file is kept up to date automatically while the stage runs. Add observations at the review step, not by editing here directly.

## Interpretations
- 行の SQL の部品（RowSqlExecutor・RowDialect）を独立した部品 RowSqlGateway に数えた。BusinessData と ReferenceLookup の呼び合いの循環を避けるため。前の Intent の記録（BusinessData の責務）と数え方が変わる。
- US4.2・US4.3（コンテナの版そろえ・perf の文書）は設定・文書のため部品にせず、traceability では Deferred にした。
<!-- example: 2026-05-29T10:14:32Z — chose REST over GraphQL; the consuming team only needs CRUD, revisit if subscriptions land -->

## Deviations
- Feasibility の段が無いため、実現できるかの判断を ADR-006 にまとめた（project.md の学びどおり）。
<!-- example: 2026-05-29T10:14:32Z — skipped the optional caching layer the stage prose suggested; the dataset is small enough that it adds risk -->

## Tradeoffs
- 更新の確かめは読み取りの接続で先に行う（Q3: A）。確かめと保存の間のすき間は外部キー違反の変換に任せる。
<!-- example: 2026-05-29T10:14:32Z — picked TDD over BDD this run; the team is unit-first and the domain is well-understood -->

## Open questions
- レビュー R-01: 既存の search は先に COUNT を流す作りで、上限+1件読みを渡すだけでは足りない。ADR-002・006 の見立てを機能設計・コード生成で確かめる。
- レビュー R-02: 値のカラムが主キーでないとき、参照先が主キーの無いテーブル・ビューのときの 1 件引きの方針を機能設計で決める。
- R-03〜R-05（Minor）は承認の場で扱う。
<!-- example: 2026-05-29T10:14:32Z — confirm the retention window with compliance before the next stage hardens the schema -->
